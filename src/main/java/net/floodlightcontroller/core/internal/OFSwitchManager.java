package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

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
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.Controller.IUpdate;
import net.floodlightcontroller.core.internal.Controller.ModuleLoaderState;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.rest.SwitchRepresentation;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import net.floodlightcontroller.debugevent.IEventCategory;
import net.floodlightcontroller.debugevent.MockDebugEventService;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.TableId;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * The Switch Manager class contains most of the code involved with dealing
 * with switches. The Switch manager keeps track of the switches known to the controller,
 * their status, and any important information about the switch lifecycle. The
 * Switch Manager also provides the switch service, which allows other modules
 * to hook in switch listeners and get basic access to switch information.
 *
 * @author gregor, capveg, sovietaced
 *
 */
public class OFSwitchManager implements IOFSwitchManager, INewOFConnectionListener, IHAListener, IFloodlightModule, IOFSwitchService, IStoreListener<DatapathId> {
	private static final Logger log = LoggerFactory.getLogger(OFSwitchManager.class);

	private volatile OFControllerRole role;
	private SwitchManagerCounters counters;

	private ISyncService syncService;
	private IStoreClient<DatapathId, SwitchSyncRepresentation> storeClient;
	public static final String SWITCH_SYNC_STORE_NAME = OFSwitchManager.class.getCanonicalName() + ".stateStore";

	private static String keyStorePassword;
	private static String keyStore;
	private static boolean useSsl = false;

	protected static boolean clearTablesOnInitialConnectAsMaster = false;
	protected static boolean clearTablesOnEachTransitionToMaster = false;

	protected static Map<DatapathId, TableId> forwardToControllerFlowsUpToTableByDpid;
	protected static TableId forwardToControllerFlowsUpToTable = TableId.of(4); /* this should cover most HW switches that have a couple SW-based flow tables */

	private ConcurrentHashMap<DatapathId, OFSwitchHandshakeHandler> switchHandlers;
	private ConcurrentHashMap<DatapathId, IOFSwitchBackend> switches;
	private ConcurrentHashMap<DatapathId, IOFSwitch> syncedSwitches;


	private ISwitchDriverRegistry driverRegistry;

	private Set<LogicalOFMessageCategory> logicalOFMessageCategories = new CopyOnWriteArraySet<LogicalOFMessageCategory>();
	private final List<IAppHandshakePluginFactory> handshakePlugins = new CopyOnWriteArrayList<IAppHandshakePluginFactory>();
	private int numRequiredConnections = -1;
	// Event IDs for debug events
	protected IEventCategory<SwitchEvent> evSwitch;

	// ISwitchService
	protected Set<IOFSwitchListener> switchListeners;

	// Module Dependencies
	IFloodlightProviderService floodlightProvider;
	IDebugEventService debugEventService;
	IDebugCounterService debugCounterService;

	/** IHAListener Implementation **/
	@Override
	public void transitionToActive() {
		this.role = HARole.ACTIVE.getOFRole();
	}

	@Override
	public void transitionToStandby() {
		this.role = HARole.STANDBY.getOFRole();
	}

	/** IOFSwitchManager Implementation **/

	@Override public SwitchManagerCounters getCounters() {
		return this.counters;
	}

	private void addUpdateToQueue(IUpdate iUpdate) {
		floodlightProvider.addUpdateToQueue(iUpdate);
	}

	@Override
	public synchronized void switchAdded(IOFSwitchBackend sw) {
		DatapathId dpid = sw.getId();
		IOFSwitchBackend oldSw = this.switches.put(dpid, sw);
		// Update event history
		evSwitch.newEventWithFlush(new SwitchEvent(dpid, "connected"));

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
			// TODO: we notify switch listeners that the switch has been
			// removed and then we notify them that the new one has been
			// added. One could argue that a switchChanged notification
			// might be more appropriate in this case....
			oldSw.cancelAllPendingRequests();
			addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.REMOVED));
			oldSw.disconnect();
		}

		/*
		 * Set other config options for this switch.
		 */
		if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
			if (forwardToControllerFlowsUpToTableByDpid.containsKey(sw.getId())) {
				sw.setMaxTableForTableMissFlow(forwardToControllerFlowsUpToTableByDpid.get(sw.getId()));
			} else {
				sw.setMaxTableForTableMissFlow(forwardToControllerFlowsUpToTable);
			}
		}
	}

	@LogMessageDocs({
		@LogMessageDoc(level="ERROR",
				message="Switch {switch} activated but was already active",
				explanation="A switch that was already activated was " +
						"activated again. This should not happen.",
						recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG
				),
				@LogMessageDoc(level="WARN",
				message="New switch added {switch} for already-added switch {switch}",
				explanation="A switch with the same DPID as another switch " +
						"connected to the controller.  This can be caused by " +
						"multiple switches configured with the same DPID, or " +
						"by a switch reconnected very quickly after " +
						"disconnecting.",
						recommendation="If this happens repeatedly, it is likely there " +
								"are switches with duplicate DPIDs on the network.  " +
								"Reconfigure the appropriate switches.  If it happens " +
								"very rarely, then it is likely this is a transient " +
								"network problem that can be ignored."
						)
	})
	@Override
	public synchronized void switchStatusChanged(IOFSwitchBackend sw, SwitchStatus oldStatus, SwitchStatus newStatus) {
		DatapathId dpid = sw.getId();
		IOFSwitchBackend presentSw = this.switches.get(dpid);

		if (presentSw != sw)  {
			// Note == for object equality, not .equals for value
			counters.errorActivatedSwitchNotPresent
			.increment();
			log.debug("Switch {} status change but not present in sync manager", sw);
			return;
		}
		evSwitch.newEventWithFlush(new SwitchEvent(dpid,
				String.format("%s -> %s",
						oldStatus,
						newStatus)));

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
		IOFSwitchBackend presentSw = this.switches.get(dpid);

		if (presentSw != sw)  {
			// Note == for object equality, not .equals for value
			counters.errorActivatedSwitchNotPresent.increment();
			log.warn("Switch {} disconnect but not present in sync manager", sw);
			return;
		}

		counters.switchDisconnected.increment();
		this.switches.remove(dpid);
	}

	@Override public void handshakeDisconnected(DatapathId dpid) {
		this.switchHandlers.remove(dpid);
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
		return this.switches.get(dpid);
	}

	@Override
	public IOFSwitch getActiveSwitch(DatapathId dpid) {
		IOFSwitchBackend sw = this.switches.get(dpid);
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
						// ignore
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
							floodlightProvider.getRoleManager(), floodlightProvider.getTimer());

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
	public void addSwitchEvent(DatapathId dpid, String reason, boolean flushNow) {
		if (flushNow)
			evSwitch.newEventWithFlush(new SwitchEvent(dpid, reason));
		else
			evSwitch.newEventNoFlush(new SwitchEvent(dpid, reason));
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
		if (!this.switches.containsKey(sw.getId())) {
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
		return this.driverRegistry.getOFSwitchInstance(connection, description, factory, datapathId);
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
		this.driverRegistry.addSwitchDriver(manufacturerDescriptionPrefix, driver);
	}

	@Override
	public ImmutableList<OFSwitchHandshakeHandler> getSwitchHandshakeHandlers() {
		return ImmutableList.copyOf(this.switchHandlers.values());
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
		if(!this.logicalOFMessageCategories.isEmpty()){
			// We use tree set here to maintain ordering
			TreeSet<OFAuxId> auxConnections = new TreeSet<OFAuxId>();

			for(LogicalOFMessageCategory category : this.logicalOFMessageCategories){
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
		this.switchListeners.add(listener);
	}

	@Override
	public void removeOFSwitchListener(IOFSwitchListener listener) {
		this.switchListeners.remove(listener);
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
	public SwitchRepresentation getSwitchRepresentation(DatapathId dpid) {
		IOFSwitch sw = this.switches.get(dpid);
		OFSwitchHandshakeHandler handler = this.switchHandlers.get(dpid);

		if(sw != null && handler != null) {
			return new SwitchRepresentation(sw, handler);
		}
		return null;
	}

	@Override
	public List<SwitchRepresentation> getSwitchRepresentations() {

		List<SwitchRepresentation> representations = new ArrayList<SwitchRepresentation>();

		for(DatapathId dpid : this.switches.keySet()) {
			SwitchRepresentation representation = getSwitchRepresentation(dpid);
			if(representation != null) {
				representations.add(representation);
			}
		}
		return representations;
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
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();

		l.add(IFloodlightProviderService.class);
		l.add(IDebugEventService.class);
		l.add(IDebugCounterService.class);
		l.add(ISyncService.class);

		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// Module dependencies
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		debugEventService = context.getServiceImpl(IDebugEventService.class);
		debugCounterService = context.getServiceImpl(IDebugCounterService.class);
		syncService = context.getServiceImpl(ISyncService.class);

		// Module variables
		switchHandlers = new ConcurrentHashMap<DatapathId, OFSwitchHandshakeHandler>();
		switches = new ConcurrentHashMap<DatapathId, IOFSwitchBackend>();
		syncedSwitches = new ConcurrentHashMap<DatapathId, IOFSwitch>();
		floodlightProvider.getTimer();
		counters = new SwitchManagerCounters(debugCounterService);
		driverRegistry = new NaiveSwitchDriverRegistry(this);

		this.switchListeners = new CopyOnWriteArraySet<IOFSwitchListener>();

		/* TODO @Ryan
		try {
			this.storeClient = this.syncService.getStoreClient(
					SWITCH_SYNC_STORE_NAME,
					DatapathId.class,
					SwitchSyncRepresentation.class);
			this.storeClient.addStoreListener(this);
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
			OFSwitchManager.useSsl = false;
			OFSwitchManager.keyStore = null;
			OFSwitchManager.keyStorePassword = null;
		} else {
			log.info("SSL enabled. Using secure connections between Floodlight and switches.");
			log.info("SSL keystore path: {}, password: {}", path, (pass == null ? "" : pass)); 
			OFSwitchManager.useSsl = true;
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

		/*
		 * Get default max table for forward to controller flows. 
		 * Internal default set as class variable at top of OFSwitchManager.
		 */
		String defaultFlowsUpToTable = configParams.get("defaultMaxTableToReceiveTableMissFlow");
		if (defaultFlowsUpToTable != null && !defaultFlowsUpToTable.isEmpty()) {
			defaultFlowsUpToTable = defaultFlowsUpToTable.toLowerCase().trim();
			try {
				forwardToControllerFlowsUpToTable = TableId.of(defaultFlowsUpToTable.startsWith("0x") 
						? Integer.parseInt(defaultFlowsUpToTable.replaceFirst("0x", ""), 16) 
								: Integer.parseInt(defaultFlowsUpToTable));
				log.info("Setting {} as the default max table to receive table-miss flow", forwardToControllerFlowsUpToTable.toString());
			} catch (IllegalArgumentException e) {
				log.error("Invalid table ID {} for default max table to receive table-miss flow. Using pre-set of {}", 
						defaultFlowsUpToTable, forwardToControllerFlowsUpToTable.toString());
			}
		} else {
			log.info("Default max table to receive table-miss flow not configured. Using {}", forwardToControllerFlowsUpToTable.toString());
		}

		/*
		 * Get config to define which tables per switch will get a
		 * default forward-to-controller flow. This can be used to
		 * reduce the number of such flows if only a reduced set of
		 * tables are being used.
		 * 
		 * By default, 
		 */
		forwardToControllerFlowsUpToTableByDpid = jsonToSwitchTableIdMap(configParams.get("maxTableToReceiveTableMissFlowPerDpid"));
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
				jp = f.createJsonParser(json);
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
							log.info("Setting max table to receive table-miss flow to {} for DPID {}", 
									tablesToGetDefaultFlow.toString(), dpid.toString());
						} catch (IllegalArgumentException e) { /* catches both IllegalArgumentExcpt. and NumberFormatExcpt. */
							log.error("Invalid value of {} for max table to receive table-miss flow for DPID {}. Using default of {}.", value, dpid.toString());
						}
					}
				} catch (NumberFormatException e) {
					log.error("Invalid DPID format {} for max table to receive table-miss flow for specific DPID. Using default for the intended DPID.", n);
				}
			}
		} catch (IOException e) {
			log.error("Using default for remaining DPIDs. JSON formatting error in max table to receive table-miss flow for DPID input String: {}", e);
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

		registerDebugEvents();
	}

	/**
	 * Bootstraps netty, the server that handles all openflow connections
	 */
	public void bootstrapNetty() {
		try {
			final ServerBootstrap bootstrap = createServerBootStrap();

			bootstrap.setOption("reuseAddr", true);
			bootstrap.setOption("child.keepAlive", true);
			bootstrap.setOption("child.tcpNoDelay", true);
			bootstrap.setOption("child.sendBufferSize", Controller.SEND_BUFFER_SIZE);

			ChannelPipelineFactory pfact = useSsl ? new OpenflowPipelineFactory(this, floodlightProvider.getTimer(), this, debugCounterService, keyStore, keyStorePassword) :
				new OpenflowPipelineFactory(this, floodlightProvider.getTimer(), this, debugCounterService);

			bootstrap.setPipelineFactory(pfact);
			InetSocketAddress sa = new InetSocketAddress(floodlightProvider.getOFPort());
			final ChannelGroup cg = new DefaultChannelGroup();
			cg.add(bootstrap.bind(sa));

			log.info("Listening for switch connections on {}", sa);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Helper that bootstrapNetty.
	 * @return
	 */
	private ServerBootstrap createServerBootStrap() {
		if (floodlightProvider.getWorkerThreads() == 0) {
			return new ServerBootstrap(
					new NioServerSocketChannelFactory(
							Executors.newCachedThreadPool(),
							Executors.newCachedThreadPool()));
		} else {
			return new ServerBootstrap(
					new NioServerSocketChannelFactory(
							Executors.newCachedThreadPool(),
							Executors.newCachedThreadPool(), floodlightProvider.getWorkerThreads()));
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

	/**
	 * Registers an event handler with the debug event service
	 * for switch events.
	 * @throws FloodlightModuleException
	 */
	private void registerDebugEvents() throws FloodlightModuleException {
		if (debugEventService == null) {
			debugEventService = new MockDebugEventService();
		}
		evSwitch = debugEventService.buildEvent(SwitchEvent.class)
				.setModuleName(this.counters.getPrefix())
				.setEventName("switch-event")
				.setEventDescription("Switch connected, disconnected or port changed")
				.setEventType(EventType.ALWAYS_LOG)
				.setBufferCapacity(100)
				.register();
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
			//TODO @Ryan need to get IOFSwitchBackend setFeaturesReply(storedSwitch.getFeaturesReply(sw.getOFFactory()));
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
		} else {
			// TODO: the switch was deleted (tombstone) before we ever
			// knew about it (or was deleted repeatedly). Can this
			// happen? When/how?
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
		/*TODO @Ryan Collection<PortChangeEvent> portDiffs = oldSw.comparePorts(newSw.getPorts());
        for (PortChangeEvent ev: portDiffs) {
            SwitchUpdate update = new SwitchUpdate(newSw.getId(),
                                     SwitchUpdateType.PORTCHANGED,
                                     ev.port, ev.type);
            addUpdateToQueue(update);
        }*/
	}
}
