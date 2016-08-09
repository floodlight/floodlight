package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.SwitchSyncRepresentation;
import net.floodlightcontroller.core.internal.Controller.IUpdate;
import net.floodlightcontroller.core.internal.Controller.ModuleLoaderState;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * The Switch Manager class contains most of the code involved with dealing
 * with switches. The Switch manager keeps track of the switches known to the controller,
 * their status, and any important information about the switch lifecycle. The
 * Switch Manager also provides the switch service, which allows other modules
 * to hook in switch listeners and get basic access to switch information.
 *
 * @author gregor, capveg, sovietaced, rizard
 *
 */
public class OFSwitchManager implements IOFSwitchManager, INewOFConnectionListener, 
IHAListener, IFloodlightModule, IOFSwitchService, IStoreListener<DatapathId> {
    private static final Logger log = LoggerFactory.getLogger(OFSwitchManager.class);

    private static volatile OFControllerRole role;
    private static SwitchManagerCounters counters;

    private static ISyncService syncService;
    private static IStoreClient<DatapathId, SwitchSyncRepresentation> storeClient;
    public static final String SWITCH_SYNC_STORE_NAME = OFSwitchManager.class.getCanonicalName() + ".stateStore";

    private static int tcpSendBufferSize = 4 * 1024 * 1024;
    private static int workerThreads = 16; /* perform r/w I/O on accepted connections (switches) */
    private static int bossThreads = 1; /* just listens and accepts on server socket; workers handle r/w I/O */
    private static int connectionBacklog = 1000; /* pending connections boss thread will queue to accept */
    private static int connectionTimeoutMsec = 60000; /* how long to allow TCP handshake to complete (default is 60ish secs) */
    private static TransportPort openFlowPort = TransportPort.of(6653);
    private static Set<IPv4Address> openFlowAddresses = new HashSet<IPv4Address>();	

    private static String keyStorePassword;
    private static String keyStore;

    protected static boolean clearTablesOnInitialConnectAsMaster = false;
    protected static boolean clearTablesOnEachTransitionToMaster = false;

    protected static Map<DatapathId, TableId> forwardToControllerFlowsUpToTableByDpid;
    protected static TableId forwardToControllerFlowsUpToTable = TableId.of(4); /* this should cover most HW switches that have a couple SW-based flow tables */

    protected static List<U32> ofBitmaps;
    protected static OFFactory defaultFactory;

    private static ConcurrentHashMap<DatapathId, OFSwitchHandshakeHandler> switchHandlers;
    private static ConcurrentHashMap<DatapathId, IOFSwitchBackend> switches;
    private static ConcurrentHashMap<DatapathId, IOFSwitch> syncedSwitches;

    protected static Map<DatapathId, OFControllerRole> switchInitialRole;

    private static ISwitchDriverRegistry driverRegistry;

    private Set<LogicalOFMessageCategory> logicalOFMessageCategories = new CopyOnWriteArraySet<LogicalOFMessageCategory>();
    private static final List<IAppHandshakePluginFactory> handshakePlugins = new CopyOnWriteArrayList<IAppHandshakePluginFactory>();
    private static int numRequiredConnections = -1;

    // ISwitchService
    protected static Set<IOFSwitchListener> switchListeners;

    // Module Dependencies
    private static IFloodlightProviderService floodlightProvider;
    private static IDebugCounterService debugCounterService;

    private static NioEventLoopGroup bossGroup;
    private static NioEventLoopGroup workerGroup;
    private static DefaultChannelGroup cg;

    protected static Timer timer;

    /** IHAListener Implementation **/
    @Override
    public void transitionToActive() {
        role = HARole.ACTIVE.getOFRole();
    }

    @Override
    public void transitionToStandby() {
        role = HARole.STANDBY.getOFRole();
    }

    /** IOFSwitchManager Implementation **/

    @Override public SwitchManagerCounters getCounters() {
        return counters;
    }

    private void addUpdateToQueue(IUpdate iUpdate) {
        floodlightProvider.addUpdateToQueue(iUpdate);
    }

    @Override
    public synchronized void switchAdded(IOFSwitchBackend sw) {
        DatapathId dpid = sw.getId();
        IOFSwitchBackend oldSw = switches.put(dpid, sw);

        if (oldSw == sw)  {
            // Note == for object equality, not .equals for value
            counters.errorActivatedSwitchNotPresent.increment();
            log.error("Switch {} added twice?", sw);
            return;
        } else if (oldSw != null) {
            // This happens either when we have switches with duplicate
            // DPIDs or when a switch reconnects before we saw the
            // disconnect
            counters.switchWithSameDpidActivated.increment();
            log.warn("New switch added {} for already-added switch {}", sw, oldSw);
            // We need to disconnect and remove the old switch
            oldSw.cancelAllPendingRequests();
            addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.REMOVED));
            oldSw.disconnect();
        }

        /*
         * Set some other config options for this switch.
         */
        if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
            if (forwardToControllerFlowsUpToTableByDpid.containsKey(sw.getId())) {
                sw.setMaxTableForTableMissFlow(forwardToControllerFlowsUpToTableByDpid.get(sw.getId()));
            } else {
                sw.setMaxTableForTableMissFlow(forwardToControllerFlowsUpToTable);
            }
        }
    }

    @Override
    public synchronized void switchStatusChanged(IOFSwitchBackend sw, SwitchStatus oldStatus, SwitchStatus newStatus) {
        DatapathId dpid = sw.getId();
        IOFSwitchBackend presentSw = switches.get(dpid);

        if (presentSw != sw)  {
            // Note == for object equality, not .equals for value
            counters.errorActivatedSwitchNotPresent
            .increment();
            log.debug("Switch {} status change but not present in sync manager", sw);
            return;
        }

        if(newStatus == SwitchStatus.MASTER  && role != OFControllerRole.ROLE_MASTER) {
            counters.invalidSwitchActivatedWhileSlave.increment();
            log.error("Switch {} activated but controller not MASTER", sw);
            sw.disconnect();
            return; // only react to switch connections when master
        }

        if(!oldStatus.isVisible() && newStatus.isVisible()) {
            // the switch has just become visible. Send 'add' notification to our
            // listeners
            addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.ADDED));
        } else if((oldStatus.isVisible() && !newStatus.isVisible())) {
            addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.REMOVED));
        }

        // note: no else if - both may be true
        if(oldStatus != SwitchStatus.MASTER && newStatus == SwitchStatus.MASTER ) {
            counters.switchActivated.increment();
            addUpdateToQueue(new SwitchUpdate(dpid,
                    SwitchUpdateType.ACTIVATED));
        } else if(oldStatus == SwitchStatus.MASTER && newStatus != SwitchStatus.MASTER ) {
            counters.switchDeactivated.increment();
            addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.DEACTIVATED));
        }
    }

    @Override
    public synchronized void switchDisconnected(IOFSwitchBackend sw) {
        DatapathId dpid = sw.getId();
        IOFSwitchBackend presentSw = switches.get(dpid);

        if (presentSw != sw)  {
            // Note == for object equality, not .equals for value
            counters.errorActivatedSwitchNotPresent.increment();
            log.warn("Switch {} disconnect but not present in sync manager", sw);
            return;
        }

        counters.switchDisconnected.increment();
        switches.remove(dpid);
    }

    @Override public void handshakeDisconnected(DatapathId dpid) {
        switchHandlers.remove(dpid);
    }

    public Iterable<IOFSwitch> getActiveSwitches() {
        ImmutableList.Builder<IOFSwitch> builder = ImmutableList.builder();
        for(IOFSwitch sw: switches.values()) {
            if(sw.getStatus().isControllable())
                builder.add(sw);
        }
        return builder.build();
    }

    public Map<DatapathId, IOFSwitch> getAllSwitchMap(boolean showInvisible) {
        if(showInvisible) {
            return ImmutableMap.<DatapathId, IOFSwitch>copyOf(switches);
        } else {
            ImmutableMap.Builder<DatapathId, IOFSwitch> builder = ImmutableMap.builder();
            for(IOFSwitch sw: switches.values()) {
                if(sw.getStatus().isVisible())
                    builder.put(sw.getId(), sw);
            }
            return builder.build();
        }
    }

    @Override
    public Map<DatapathId, IOFSwitch> getAllSwitchMap() {
        return getAllSwitchMap(true);
    }

    @Override
    public Set<DatapathId> getAllSwitchDpids() {
        return getAllSwitchMap().keySet();
    }

    public Set<DatapathId> getAllSwitchDpids(boolean showInvisible) {
        return getAllSwitchMap(showInvisible).keySet();
    }

    @Override
    public IOFSwitch getSwitch(DatapathId dpid) {
        return switches.get(dpid);
    }

    @Override
    public IOFSwitch getActiveSwitch(DatapathId dpid) {
        IOFSwitchBackend sw = switches.get(dpid);
        if(sw != null && sw.getStatus().isVisible())
            return sw;
        else
            return null;
    }

    enum SwitchUpdateType {
        ADDED,
        REMOVED,
        ACTIVATED,
        DEACTIVATED,
        PORTCHANGED,
        OTHERCHANGE
    }

    /**
     * Update message indicating a switch was added or removed
     */
    class SwitchUpdate implements IUpdate {
        private final DatapathId swId;
        private final SwitchUpdateType switchUpdateType;
        private final OFPortDesc port;
        private final PortChangeType changeType;

        public SwitchUpdate(DatapathId swId, SwitchUpdateType switchUpdateType) {
            this(swId, switchUpdateType, null, null);
        }

        public SwitchUpdate(DatapathId swId,
                SwitchUpdateType switchUpdateType,
                OFPortDesc port,
                PortChangeType changeType) {
            if (switchUpdateType == SwitchUpdateType.PORTCHANGED) {
                if (port == null) {
                    throw new NullPointerException("Port must not be null " +
                            "for PORTCHANGED updates");
                }
                if (changeType == null) {
                    throw new NullPointerException("ChangeType must not be " +
                            "null for PORTCHANGED updates");
                }
            } else {
                if (port != null || changeType != null) {
                    throw new IllegalArgumentException("port and changeType " +
                            "must be null for " + switchUpdateType +
                            " updates");
                }
            }
            this.swId = swId;
            this.switchUpdateType = switchUpdateType;
            this.port = port;
            this.changeType = changeType;
        }

        @Override
        public void dispatch() {
            if (log.isTraceEnabled()) {
                log.trace("Dispatching switch update {} {}", swId, switchUpdateType);
            }
            if (switchListeners != null) {
                for (IOFSwitchListener listener : switchListeners) {
                    switch(switchUpdateType) {
                    case ADDED:
                        // don't count here. We have more specific
                        // counters before the update is created
                        listener.switchAdded(swId);
                        break;
                    case REMOVED:
                        // don't count here. We have more specific
                        // counters before the update is created
                        listener.switchRemoved(swId);
                        break;
                    case PORTCHANGED:
                        counters.switchPortChanged
                        .increment();
                        listener.switchPortChanged(swId, port, changeType);
                        break;
                    case ACTIVATED:
                        // don't count here. We have more specific
                        // counters before the update is created
                        listener.switchActivated(swId);
                        break;
                    case DEACTIVATED:
                        // Called on master to slave transitions, ROLE_STATUS message.
                        listener.switchDeactivated(swId);
                        break;
                    case OTHERCHANGE:
                        counters.switchOtherChange
                        .increment();
                        listener.switchChanged(swId);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Handles a new OF Connection
     * @param IOFConnectionBackend connection an opened OF Connection
     * @param OFFeaturesReply featuresReply the features reply received for the opened connection.
     * It is needed for the rest of the switch handshake.
     */
    @Override
    public void connectionOpened(IOFConnectionBackend connection, OFFeaturesReply featuresReply) {
        DatapathId dpid = connection.getDatapathId();
        OFAuxId auxId = connection.getAuxId();

        log.debug("{} opened", connection);

        if(auxId.equals(OFAuxId.MAIN)) {

            // Create a new switch handshake handler
            OFSwitchHandshakeHandler handler =
                    new OFSwitchHandshakeHandler(connection, featuresReply, this,
                            floodlightProvider.getRoleManager(), timer);

            OFSwitchHandshakeHandler oldHandler = switchHandlers.put(dpid, handler);

            // Disconnect all the handler's connections
            if(oldHandler != null){
                log.debug("{} is a new main connection, killing old handler connections", connection);
                oldHandler.cleanup();
            }

            handler.beginHandshake();

        } else {
            OFSwitchHandshakeHandler handler = switchHandlers.get(dpid);

            if(handler != null) {
                handler.auxConnectionOpened(connection);
            }
            // Connections have arrived before the switchhandler is ready
            else {
                log.warn("{} arrived before main connection, closing connection", connection);
                connection.disconnect();
            }
        }
    }

    @Override
    public synchronized void notifyPortChanged(IOFSwitchBackend sw,
            OFPortDesc port,
            PortChangeType changeType) {
        Preconditions.checkNotNull(sw, "switch must not be null");
        Preconditions.checkNotNull(port, "port must not be null");
        Preconditions.checkNotNull(changeType, "changeType must not be null");

        if (role != OFControllerRole.ROLE_MASTER) {
            counters.invalidPortsChanged.increment();
            return;
        }
        if (!switches.containsKey(sw.getId())) {
            counters.invalidPortsChanged.increment();
            return;
        }

        if(sw.getStatus().isVisible()) {
            // no need to count here. SwitchUpdate.dispatch will count
            // the portchanged
            SwitchUpdate update = new SwitchUpdate(sw.getId(),
                    SwitchUpdateType.PORTCHANGED,
                    port, changeType);
            addUpdateToQueue(update);
        }
    }

    @Override
    public IOFSwitchBackend getOFSwitchInstance(IOFConnectionBackend connection,
            SwitchDescription description,
            OFFactory factory, DatapathId datapathId) {

        return driverRegistry.getOFSwitchInstance(connection, description, factory, datapathId);
    }

    @Override
    public void handleMessage(IOFSwitchBackend sw, OFMessage m, FloodlightContext bContext) {
        floodlightProvider.handleMessage(sw, m, bContext);
    }

    @Override
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m) {
        floodlightProvider.handleOutgoingMessage(sw, m);
    }

    @Override
    public void addOFSwitchDriver(String manufacturerDescriptionPrefix,
            IOFSwitchDriver driver) {
        driverRegistry.addSwitchDriver(manufacturerDescriptionPrefix, driver);
    }

    @Override
    public ImmutableList<OFSwitchHandshakeHandler> getSwitchHandshakeHandlers() {
        return ImmutableList.copyOf(switchHandlers.values());
    }

    @Override
    public int getNumRequiredConnections() {
        Preconditions.checkState(numRequiredConnections >= 0, "numRequiredConnections not calculated");
        return numRequiredConnections;
    }

    public Set<LogicalOFMessageCategory> getLogicalOFMessageCategories() {
        return logicalOFMessageCategories;
    }

    private int calcNumRequiredConnections() {
        if(!logicalOFMessageCategories.isEmpty()){
            // We use tree set here to maintain ordering
            TreeSet<OFAuxId> auxConnections = new TreeSet<OFAuxId>();

            for(LogicalOFMessageCategory category : logicalOFMessageCategories){
                auxConnections.add(category.getAuxId());
            }

            OFAuxId first = auxConnections.first();
            OFAuxId last = auxConnections.last();

            // Check for contiguous set (1....size())
            if(first.equals(OFAuxId.MAIN)) {
                if(last.getValue() != auxConnections.size() - 1){
                    throw new IllegalStateException("Logical OF message categories must maintain contiguous OF Aux Ids! i.e. (0,1,2,3,4,5)");
                }
                return auxConnections.size() - 1;
            } else if(first.equals(OFAuxId.of(1))) {
                if(last.getValue() != auxConnections.size()){
                    throw new IllegalStateException("Logical OF message categories must maintain contiguous OF Aux Ids! i.e. (1,2,3,4,5)");
                }
                return auxConnections.size();
            } else {
                throw new IllegalStateException("Logical OF message categories must start at 0 (MAIN) or 1");
            }
        } else {
            return 0;
        }
    }

    /** ISwitchService Implementation **/
    @Override
    public void addOFSwitchListener(IOFSwitchListener listener) {
        switchListeners.add(listener);
    }

    @Override
    public void removeOFSwitchListener(IOFSwitchListener listener) {
        switchListeners.remove(listener);
    }

    @Override
    public void registerLogicalOFMessageCategory(LogicalOFMessageCategory category) {
        logicalOFMessageCategories.add(category);
    }

    @Override
    public boolean isCategoryRegistered(LogicalOFMessageCategory category) {
        return logicalOFMessageCategories.contains(category);
    }

    @Override
    public void registerHandshakePlugin(IAppHandshakePluginFactory factory) {
        Preconditions.checkState(floodlightProvider.getModuleLoaderState() == ModuleLoaderState.INIT,
                "handshakeplugins can only be registered when the module loader is in state INIT!");
        handshakePlugins.add(factory);
    }

    @Override
    public List<IAppHandshakePluginFactory> getHandshakePlugins() {
        return handshakePlugins;
    }

    /* IFloodlightModule Implementation */
    @Override
    public Collection<Class<? extends IFloodlightService>>
    getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IOFSwitchService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IOFSwitchService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
    getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();

        l.add(IFloodlightProviderService.class);
        l.add(IDebugCounterService.class);
        l.add(ISyncService.class);

        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        // Module dependencies
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        debugCounterService = context.getServiceImpl(IDebugCounterService.class);
        syncService = context.getServiceImpl(ISyncService.class);

        // Module variables
        switchHandlers = new ConcurrentHashMap<DatapathId, OFSwitchHandshakeHandler>();
        switches = new ConcurrentHashMap<DatapathId, IOFSwitchBackend>();
        syncedSwitches = new ConcurrentHashMap<DatapathId, IOFSwitch>();
        counters = new SwitchManagerCounters(debugCounterService);
        driverRegistry = new NaiveSwitchDriverRegistry(this);

        switchListeners = new CopyOnWriteArraySet<IOFSwitchListener>();

        timer = new HashedWheelTimer();

        /* TODO
		try {
			storeClient = syncService.getStoreClient(
					SWITCH_SYNC_STORE_NAME,
					DatapathId.class,
					SwitchSyncRepresentation.class);
			storeClient.addStoreListener(this);
		} catch (UnknownStoreException e) {
			throw new FloodlightModuleException("Error while setting up sync store client", e);
		} */

        /* 
         * Get SSL config.
         * 
         * If a password is blank, the password field may or may not be specified.
         * If it is specified, an empty string will be expected for blank.
         * 
         * The path MUST be specified if SSL is enabled.
         */
        Map<String, String> configParams = context.getConfigParams(this);
        String path = configParams.get("keyStorePath");
        String pass = configParams.get("keyStorePassword");
        String useSsl = configParams.get("useSsl");

        if (useSsl == null || path == null || path.isEmpty() || 
                (!useSsl.equalsIgnoreCase("yes") && !useSsl.equalsIgnoreCase("true") &&
                        !useSsl.equalsIgnoreCase("yep") && !useSsl.equalsIgnoreCase("ja") &&
                        !useSsl.equalsIgnoreCase("stimmt")
                        )
                ) {
            log.warn("SSL disabled. Using unsecure connections between Floodlight and switches.");
            OFSwitchManager.keyStore = null;
            OFSwitchManager.keyStorePassword = null;
        } else {
            log.info("SSL enabled. Using secure connections between Floodlight and switches.");
            log.info("SSL keystore path: {}, password: {}", path, (pass == null ? "" : pass)); 
            OFSwitchManager.keyStore = path;
            OFSwitchManager.keyStorePassword = (pass == null ? "" : pass);
        }

        /*
         * Get config to define what to do when a switch connects.
         * 
         * If a field is blank or unspecified, it will default
         */
        String clearInitial = configParams.get("clearTablesOnInitialHandshakeAsMaster");
        String clearLater = configParams.get("clearTablesOnEachTransitionToMaster");

        if (clearInitial == null || clearInitial.isEmpty() || 
                (!clearInitial.equalsIgnoreCase("yes") && !clearInitial.equalsIgnoreCase("true") &&
                        !clearInitial.equalsIgnoreCase("yep") && !clearInitial.equalsIgnoreCase("ja") &&
                        !clearInitial.equalsIgnoreCase("stimmt"))) {
            log.info("Clear switch flow tables on initial handshake as master: FALSE");
            OFSwitchManager.clearTablesOnInitialConnectAsMaster = false;
        } else {
            log.info("Clear switch flow tables on initial handshake as master: TRUE");
            OFSwitchManager.clearTablesOnInitialConnectAsMaster = true;
        }

        if (clearLater == null || clearLater.isEmpty() || 
                (!clearLater.equalsIgnoreCase("yes") && !clearLater.equalsIgnoreCase("true") &&
                        !clearLater.equalsIgnoreCase("yep") && !clearLater.equalsIgnoreCase("ja") &&
                        !clearLater.equalsIgnoreCase("stimmt"))) {
            log.info("Clear switch flow tables on each transition to master: FALSE");
            OFSwitchManager.clearTablesOnEachTransitionToMaster = false;
        } else {
            log.info("Clear switch flow tables on each transition to master: TRUE");
            OFSwitchManager.clearTablesOnEachTransitionToMaster = true;
        }


        //Define initial role per switch		
        String switchesInitialState = configParams.get("switchesInitialState");
        switchInitialRole = jsonToSwitchInitialRoleMap(switchesInitialState);
        log.debug("SwitchInitialRole: {}", switchInitialRole.entrySet());

        /*
         * Get default max table for forward to controller flows. 
         * Internal default set as class variable at top of OFSwitchManager.
         */
        String defaultFlowsUpToTable = configParams.get("defaultMaxTablesToReceiveTableMissFlow");
        /* Backward compatibility */
        if (defaultFlowsUpToTable == null || defaultFlowsUpToTable.isEmpty()) {
            defaultFlowsUpToTable = configParams.get("defaultMaxTableToReceiveTableMissFlow");
        }
        if (defaultFlowsUpToTable != null && !defaultFlowsUpToTable.isEmpty()) {
            defaultFlowsUpToTable = defaultFlowsUpToTable.toLowerCase().trim();
            try {
                forwardToControllerFlowsUpToTable = TableId.of(defaultFlowsUpToTable.startsWith("0x") 
                        ? Integer.parseInt(defaultFlowsUpToTable.replaceFirst("0x", ""), 16) 
                                : Integer.parseInt(defaultFlowsUpToTable));
                log.info("Setting {} as the default max tables to receive table-miss flow", forwardToControllerFlowsUpToTable.toString());
            } catch (IllegalArgumentException e) {
                log.error("Invalid table ID {} for default max tables to receive table-miss flow. Using pre-set of {}", 
                        defaultFlowsUpToTable, forwardToControllerFlowsUpToTable.toString());
            }
        } else {
            log.info("Default max tables to receive table-miss flow not configured. Using {}", forwardToControllerFlowsUpToTable.toString());
        }

        /*
         * Get config to define which tables per switch will get a
         * default forward-to-controller flow. This can be used to
         * reduce the number of such flows if only a reduced set of
         * tables are being used.
         */
        String maxPerDpid = configParams.get("maxTablesToReceiveTableMissFlowPerDpid");
        /* Backward compatibility */
        if (maxPerDpid == null || maxPerDpid.isEmpty()) {
            maxPerDpid = configParams.get("maxTableToReceiveTableMissFlowPerDpid");
        }
        forwardToControllerFlowsUpToTableByDpid = jsonToSwitchTableIdMap(maxPerDpid);

        /*
         * Get config to determine what versions of OpenFlow we will
         * support. The versions will determine the hello's header
         * version as well as the OF1.3.1 version bitmap contents.
         */
        String protocols = configParams.get("supportedOpenFlowVersions");
        List<OFVersion> ofVersions = new ArrayList<OFVersion>();
        if (protocols != null && !protocols.isEmpty()) {
            protocols = protocols.toLowerCase();
            /* 
             * Brute-force check for all known versions. 
             */
            if (protocols.contains("1.0") || protocols.contains("10")) {
                ofVersions.add(OFVersion.OF_10);
            }
            if (protocols.contains("1.1") || protocols.contains("11")) {
                ofVersions.add(OFVersion.OF_11);
            }
            if (protocols.contains("1.2") || protocols.contains("12")) {
                ofVersions.add(OFVersion.OF_12);
            }
            if (protocols.contains("1.3") || protocols.contains("13")) {
                ofVersions.add(OFVersion.OF_13);
            }
            if (protocols.contains("1.4") || protocols.contains("14")) {
                ofVersions.add(OFVersion.OF_14);
            }
            if (protocols.contains("1.5") || protocols.contains("15")) {
                ofVersions.add(OFVersion.OF_15);
            }
            /*
             * TODO This will need to be updated if/when 
             * Loxi is updated to support > 1.5.
             * 
             * if (protocols.contains("1.6") || protocols.contains("16")) {
             *     ofVersions.add(OFVersion.OF_16);
             * }
             */
        } else {
            log.warn("Supported OpenFlow versions not specified. Using Loxi-defined {}", OFVersion.values());
            ofVersions.addAll(Arrays.asList(OFVersion.values()));
        }

        /* Sanity check */
        if (ofVersions.isEmpty()) {
            throw new IllegalStateException("OpenFlow version list should never be empty at this point. Make sure it's being populated in OFSwitchManager's init function.");
        }
        defaultFactory = computeInitialFactory(ofVersions);
        ofBitmaps = computeOurVersionBitmaps(ofVersions);

        log.debug("Computed OpenFlow version bitmap as {}", ofBitmaps);
        log.info("OpenFlow version {} will be advertised to switches. Supported fallback versions {}", defaultFactory.getVersion(), ofVersions);

        /* OpenFlow listen TCP port */
        String ofPort = configParams.get("openFlowPort");
        if (!Strings.isNullOrEmpty(ofPort)) {
            try {
                openFlowPort = TransportPort.of(Integer.parseInt(ofPort));
            } catch (Exception e) {
                log.error("Invalid OpenFlow port {}, {}", ofPort, e);
                throw new FloodlightModuleException("Invalid OpenFlow port of " + ofPort + " in config");
            }
        }

        /* Netty worker threads */
        String threads = configParams.get("workerThreads");
        if (!Strings.isNullOrEmpty(threads)) {
            workerThreads = Integer.parseInt(threads);
        }

        /* Netty boss threads */
        threads = configParams.get("bossThreads");
        if (!Strings.isNullOrEmpty(threads)) {
            bossThreads = Integer.parseInt(threads);
        }

        /* Netty TCP connection timeout */
        String timeout = configParams.get("connectionTimeoutMs");
        if (!Strings.isNullOrEmpty(timeout)) {
            connectionTimeoutMsec = Integer.parseInt(timeout);
        }

        /* Netty boss thread pending connection accept backlog */
        String backlog = configParams.get("connectionBacklog");
        if (!Strings.isNullOrEmpty(backlog)) {
            connectionBacklog = Integer.parseInt(backlog);
        }

        /* OpenFlow listen addresses */
        String addresses = configParams.get("openFlowAddresses");
        if (!Strings.isNullOrEmpty(addresses)) {
            try {
                openFlowAddresses = Collections.singleton(IPv4Address.of(addresses)); //TODO support list of addresses for multi-honed controllers
            } catch (Exception e) {
                log.error("Invalid OpenFlow address {}, {}", addresses, e);
                throw new FloodlightModuleException("Invalid OpenFlow address of " + addresses + " in config");
            }
            log.debug("OpenFlow addresses set to {}", openFlowAddresses);
        } else {
            openFlowAddresses.add(IPv4Address.NONE);
        }

        /* OpenFlow port TCP send buffer size */
        String tcpBuffer = configParams.get("tcpSendBufferSizeBytes");
        if (!Strings.isNullOrEmpty(tcpBuffer)) {
            tcpSendBufferSize = Integer.parseInt(tcpBuffer);
        }

        log.info("Listening for OpenFlow switches on {}:{}", openFlowAddresses, openFlowPort);
        log.info("OpenFlow socket config: "
                + "{} boss thread(s), "
                + "{} worker thread(s), "
                + "{} ms TCP connection timeout, "
                + "max {} connection backlog, "
                + "{} byte TCP send buffer size", 
                new Object[] {
                        bossThreads, 
                        workerThreads, 
                        connectionTimeoutMsec, 
                        connectionBacklog, 
                        tcpSendBufferSize
                });
    }

    /**
     * Find the max version supplied in the supported
     * versions list and use it as the default, which
     * will subsequently be used in our hello message
     * header's version field.
     * 
     * The factory can be later "downgraded" to a lower
     * version depending on what's computed during the
     * version-negotiation part of the handshake.
     * 
     * @param ofVersions the OpenFlow versions we support
     * @return the highest-version OFFactory we support
     */
    private OFFactory computeInitialFactory(List<OFVersion> ofVersions) {
        /* This should NEVER happen. Double-checking. */
        if (ofVersions == null || ofVersions.isEmpty()) {
            throw new IllegalStateException("OpenFlow version list should never be null or empty at this point. Make sure it's set in the OFSwitchManager.");
        }
        OFVersion highest = null;
        for (OFVersion v : ofVersions) {
            if (highest == null) {
                highest = v;
            } else if (v.compareTo(highest) > 0) {
                highest = v;
            }
        }
        /* 
         * This assumes highest != null, which
         * it won't be if the list of versions
         * is not empty.
         */
        return OFFactories.getFactory(highest);
    }

    /**
     * Based on the list of OFVersions provided as input (or from Loxi),
     * create a list of bitmaps for use in version negotiation during a
     * cross-version OpenFlow handshake where both parties support 
     * OpenFlow versions >= 1.3.1.
     * 
     * @param ofVersions the OpenFlow versions we support
     * @return list of bitmaps for the versions of OpenFlow we support
     */
    private List<U32> computeOurVersionBitmaps(List<OFVersion> ofVersions) {
        /* This should NEVER happen. Double-checking. */
        if (ofVersions == null || ofVersions.isEmpty()) {
            throw new IllegalStateException("OpenFlow version list should never be null or empty at this point. Make sure it's set in the OFSwitchManager.");
        }

        int pos = 1; /* initial bitmap in list */
        int size = 32; /* size of a U32 */
        int tempBitmap = 0; /* maintain the current bitmap we're working on */
        List<U32> bitmaps = new ArrayList<U32>();
        ArrayList<OFVersion> sortedVersions = new ArrayList<OFVersion>(ofVersions);
        Collections.sort(sortedVersions);
        for (OFVersion v : sortedVersions) {
            /* Move on to another bitmap */
            if (v.getWireVersion() > pos * size - 1 ) {
                bitmaps.add(U32.ofRaw(tempBitmap));
                tempBitmap = 0;
                pos++;
            }
            tempBitmap = tempBitmap | (1 << (v.getWireVersion() % size));
        }
        if (tempBitmap != 0) {
            bitmaps.add(U32.ofRaw(tempBitmap));
        }
        return bitmaps;
    }

    private static Map<DatapathId, TableId> jsonToSwitchTableIdMap(String json) {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        Map<DatapathId, TableId> retValue = new HashMap<DatapathId, TableId>();

        if (json == null || json.isEmpty()) {
            return retValue;
        }

        try {
            try {
                jp = f.createParser(json);
            } catch (JsonParseException e) {
                throw new IOException(e);
            }

            jp.nextToken();
            if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Expected FIELD_NAME");
                }

                String n = jp.getCurrentName();
                jp.nextToken();
                if (jp.getText().equals("")) {
                    continue;
                }

                DatapathId dpid;
                try {
                    n = n.trim();
                    dpid = DatapathId.of(n);

                    TableId tablesToGetDefaultFlow;
                    String value = jp.getText();
                    if (value != null && !value.isEmpty()) {
                        value = value.trim().toLowerCase();
                        try {
                            tablesToGetDefaultFlow = TableId.of(
                                    value.startsWith("0x") 
                                    ? Integer.parseInt(value.replaceFirst("0x", ""), 16) 
                                            : Integer.parseInt(value)
                                    ); /* will throw exception if outside valid TableId number range */
                            retValue.put(dpid, tablesToGetDefaultFlow);
                            log.debug("Setting max tables to receive table-miss flow to {} for DPID {}", 
                                    tablesToGetDefaultFlow.toString(), dpid.toString());
                        } catch (IllegalArgumentException e) { /* catches both IllegalArgumentExcpt. and NumberFormatExcpt. */
                            log.error("Invalid value of {} for max tables to receive table-miss flow for DPID {}. Using default of {}.", value, dpid.toString());
                        }
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid DPID format {} for max tables to receive table-miss flow for specific DPID. Using default for the intended DPID.", n);
                }
            }
        } catch (IOException e) {
            log.error("Using default for remaining DPIDs. JSON formatting error in max tables to receive table-miss flow for DPID input String: {}", e);
        }
        return retValue;
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        startUpBase(context);
        bootstrapNetty();
    }

    /**
     * Startup method that includes everything besides the netty boostrap.
     * This has been isolated for testing.
     * @param context floodlight module context
     * @throws FloodlightModuleException
     */
    public void startUpBase(FloodlightModuleContext context) throws FloodlightModuleException {
        // Initial Role
        role = floodlightProvider.getRole().getOFRole();

        // IRoleListener
        floodlightProvider.addHAListener(this);

        loadLogicalCategories();
    }

    /**
     * Bootstraps netty, the server that handles all openflow connections
     */
    public void bootstrapNetty() {
        try {
            bossGroup = new NioEventLoopGroup(bossThreads);
            workerGroup = new NioEventLoopGroup(workerThreads);

            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_SNDBUF, tcpSendBufferSize)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMsec)
                    .option(ChannelOption.SO_BACKLOG, connectionBacklog);

            OFChannelInitializer initializer = new OFChannelInitializer(
                    this, 
                    this, 
                    debugCounterService, 
                    timer, 
                    ofBitmaps, 
                    defaultFactory, 
                    keyStore, 
                    keyStorePassword);

            bootstrap.childHandler(initializer);

            cg = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

            Set<InetSocketAddress> addrs = new HashSet<InetSocketAddress>();
            if (openFlowAddresses.isEmpty()) {
                cg.add(bootstrap.bind(new InetSocketAddress(InetAddress.getByAddress(IPv4Address.NONE.getBytes()), openFlowPort.getPort())).channel());
            } else {
                for (IPv4Address ip : openFlowAddresses) {
                    addrs.add(new InetSocketAddress(InetAddress.getByAddress(ip.getBytes()), openFlowPort.getPort()));
                }
            }

            for (InetSocketAddress sa : addrs) {
                cg.add(bootstrap.bind(sa).channel());
                log.debug("Listening for switch connections on {}", sa);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs startup related actions for logical OF message categories.
     * Setting the categories list to immutable ensures that unsupported operation
     * exceptions will be activated if modifications are attempted.
     */
    public void loadLogicalCategories() {
        logicalOFMessageCategories = ImmutableSet.copyOf(logicalOFMessageCategories);
        numRequiredConnections = calcNumRequiredConnections();
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type, String name) {
        return false;
    }

    @Override
    public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {		
    }

    @Override
    public void keysModified(Iterator<DatapathId> keys, UpdateType type) {
        if (type == UpdateType.LOCAL) {
            // We only care for remote updates
            return;
        }
        while(keys.hasNext()) {
            DatapathId key = keys.next();
            Versioned<SwitchSyncRepresentation> versionedSwitch = null;
            try {
                versionedSwitch = storeClient.get(key);
            } catch (SyncException e) {
                log.error("Exception while retrieving switch " + key.toString() +
                        " from sync store. Skipping", e);
                continue;
            }
            if (log.isTraceEnabled()) {
                log.trace("Reveiced switch store notification: key={}, " +
                        "entry={}", key, versionedSwitch.getValue());
            }
            // versionedSwtich won't be null. storeClient.get() always
            // returns a non-null or throws an exception
            if (versionedSwitch.getValue() == null) {
                switchRemovedFromStore(key);
                continue;
            }
            SwitchSyncRepresentation storedSwitch = versionedSwitch.getValue();
            IOFSwitch sw = getSwitch(storedSwitch.getDpid());
            //TODO need to get IOFSwitchBackend setFeaturesReply(storedSwitch.getFeaturesReply(sw.getOFFactory()));
            if (!key.equals(storedSwitch.getFeaturesReply(sw.getOFFactory()).getDatapathId())) {
                log.error("Inconsistent DPIDs from switch sync store: " +
                        "key is {} but sw.getId() says {}. Ignoring",
                        key.toString(), sw.getId());
                continue;
            }
            switchAddedToStore(sw);
        }
    }

    /**
     * Called when we receive a store notification about a switch that
     * has been removed from the sync store
     * @param dpid
     */
    private synchronized void switchRemovedFromStore(DatapathId dpid) {
        if (floodlightProvider.getRole() != HARole.STANDBY) {
            return; // only read from store if slave
        }
        IOFSwitch oldSw = syncedSwitches.remove(dpid);
        if (oldSw != null) {
            addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.REMOVED));
        }
    }

    /**
     * Called when we receive a store notification about a new or updated
     * switch.
     * @param sw
     */
    private synchronized void switchAddedToStore(IOFSwitch sw) {
        if (floodlightProvider.getRole() != HARole.STANDBY) {
            return; // only read from store if slave
        }
        DatapathId dpid = sw.getId();

        IOFSwitch oldSw = syncedSwitches.put(dpid, sw);
        if (oldSw == null)  {
            addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.ADDED));
        } else {
            // The switch already exists in storage, see if anything
            // has changed
            sendNotificationsIfSwitchDiffers(oldSw, sw);
        }
    }

    /**
     * Check if the two switches differ in their ports or in other
     * fields and if they differ enqueue a switch update
     * @param oldSw
     * @param newSw
     */
    private synchronized void sendNotificationsIfSwitchDiffers(IOFSwitch oldSw, IOFSwitch newSw) {
        for (OFPortDesc oldPort : oldSw.getPorts()) {
            if (newSw.getPort(oldPort.getPortNo()) == null) { /* delete */
                SwitchUpdate update = new SwitchUpdate(newSw.getId(),
                        SwitchUpdateType.PORTCHANGED,
                        oldPort, PortChangeType.DELETE);
                addUpdateToQueue(update);
            } else { /* in common; some form of update */
                OFPortDesc newPort = newSw.getPort(oldPort.getPortNo());
                if (newPort.getState().contains(OFPortState.LINK_DOWN) && /* went down */
                        !oldPort.getState().contains(OFPortState.LINK_DOWN)) {
                    SwitchUpdate update = new SwitchUpdate(newSw.getId(),
                            SwitchUpdateType.PORTCHANGED,
                            newPort, PortChangeType.DOWN);
                    addUpdateToQueue(update);
                } else if (newPort.getState().contains(OFPortState.LIVE) && /* went up */
                        !oldPort.getState().contains(OFPortState.LIVE)) {
                    SwitchUpdate update = new SwitchUpdate(newSw.getId(),
                            SwitchUpdateType.PORTCHANGED,
                            newPort, PortChangeType.UP);
                    addUpdateToQueue(update);
                } else if (!newPort.equals(oldPort)) {
                    SwitchUpdate update = new SwitchUpdate(newSw.getId(),
                            SwitchUpdateType.PORTCHANGED,
                            newPort, PortChangeType.OTHER_UPDATE);
                    addUpdateToQueue(update);
                }
            }
        }
        for (OFPortDesc newPort : newSw.getPorts()) {
            if (oldSw.getPort(newPort.getPortNo()) == null) { /* add */
                SwitchUpdate update = new SwitchUpdate(newSw.getId(),
                        SwitchUpdateType.PORTCHANGED,
                        newPort, PortChangeType.ADD);
                addUpdateToQueue(update);
            }
        }
    }


    /**
     * Tulio Ribeiro
     * @param String json
     * @return Map<DatapathId, OFControllerRole>
     */
    private static Map<DatapathId, OFControllerRole> jsonToSwitchInitialRoleMap(String json) {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        Map<DatapathId, OFControllerRole> retValue = new HashMap<DatapathId, OFControllerRole>();

        if (json == null || json.isEmpty()) {
            return retValue;
        }

        try {
            try {
                jp = f.createParser(json);
            } catch (JsonParseException e) {
                throw new IOException(e);
            }

            jp.nextToken();
            if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Expected FIELD_NAME");
                }

                String n = jp.getCurrentName();
                jp.nextToken();
                if (jp.getText().equals("")) {
                    continue;
                }

                DatapathId dpid;
                OFControllerRole ofcr=OFControllerRole.ROLE_NOCHANGE;

                try {
                    n = n.trim();
                    dpid = DatapathId.of(n);
                    ofcr = OFControllerRole.valueOf(jp.getText());
                    retValue.put(dpid, ofcr);

                } catch (NumberFormatException e) {
                    log.error("Invalid DPID format: {}, or OFControllerRole: {}", n, ofcr);
                }
            }
        } catch (IOException e) {
            log.error("Problem: {}", e);
        }
        return retValue;
    }

    @Override
    public void addSwitchEvent(DatapathId switchDpid, String reason, boolean flushNow) {}
}