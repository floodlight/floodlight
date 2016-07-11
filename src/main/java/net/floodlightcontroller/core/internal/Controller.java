/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.core.ControllerId;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IControllerCompletionListener;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.FloodlightModuleLoader;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.LoadMonitor;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.config.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

/**
 * The main controller class
 */
public class Controller implements IFloodlightProviderService, IStorageSourceListener, IInfoProvider {
    protected static final Logger log = LoggerFactory.getLogger(Controller.class);

    /* OpenFlow message listeners and dispatchers */
    protected static ConcurrentMap<OFType, ListenerDispatcher<OFType,IOFMessageListener>> messageListeners;
    protected static ConcurrentLinkedQueue<IControllerCompletionListener> completionListeners;
    
    /* 
     * The controllerNodeIPsCache maps Controller IDs to their IP address.
     * It's only used by handleControllerNodeIPsChanged 
     */
    protected static HashMap<String, String> controllerNodeIPsCache;

    protected static ListenerDispatcher<HAListenerTypeMarker,IHAListener> haListeners;
    protected static Map<String, List<IInfoProvider>> providerMap;
    protected static BlockingQueue<IUpdate> updates;
    protected static ControllerCounters counters;
    
    /* Module Loader State */
    private static ModuleLoaderState moduleLoaderState;
    public enum ModuleLoaderState {
        INIT, STARTUP, COMPLETE
    }

    /* Module dependencies */
    private static IStorageSourceService storageSourceService;
    private static IOFSwitchService switchService;
    private static IDebugCounterService debugCounterService;
    private static IRestApiService restApiService;
    private static IPktInProcessingTimeService pktinProcTimeService;
    private static IThreadPoolService threadPoolService;
    private static ISyncService syncService;
    private static IShutdownService shutdownService;

    /*
     * The id for this controller node. Should be unique for each controller
     * node in a controller cluster.
     */
    protected String controllerId = "my-floodlight-controller";

    /*
     *  This controller's current role that modules can use/query to decide
     * if they should operate in ACTIVE / STANDBY
     */
    protected volatile HARole notifiedRole;

    private static final String INITIAL_ROLE_CHANGE_DESCRIPTION = "Controller startup.";
    
    /*
     * NOTE: roleManager is not 'final' because it's initialized at run time
     * based on parameters that are only available in init()
     */
    private static RoleManager roleManager;
    protected static boolean shutdownOnTransitionToStandby = false;

    /* Storage table names */
    protected static final String CONTROLLER_TABLE_NAME = "controller_controller";
    protected static final String CONTROLLER_ID = "id";

    protected static final String SWITCH_CONFIG_TABLE_NAME = "controller_switchconfig";
    protected static final String SWITCH_CONFIG_CORE_SWITCH = "core_switch";

    protected static final String CONTROLLER_INTERFACE_TABLE_NAME = "controller_controllerinterface";
    protected static final String CONTROLLER_INTERFACE_ID = "id";
    protected static final String CONTROLLER_INTERFACE_CONTROLLER_ID = "controller_id";
    protected static final String CONTROLLER_INTERFACE_TYPE = "type";
    protected static final String CONTROLLER_INTERFACE_NUMBER = "number";
    protected static final String CONTROLLER_INTERFACE_DISCOVERED_IP = "discovered_ip";

    private static final String FLOW_PRIORITY_TABLE_NAME = "controller_forwardingconfig";
    private static final String FLOW_COLUMN_PRIMARY_KEY = "id";
    private static final String FLOW_VALUE_PRIMARY_KEY = "forwarding";
    private static final String FLOW_COLUMN_ACCESS_PRIORITY = "access_priority";
    private static final String FLOW_COLUMN_CORE_PRIORITY = "core_priority";
    private static final String[] FLOW_COLUMN_NAMES = new String[] {
            FLOW_COLUMN_PRIMARY_KEY,
            FLOW_COLUMN_ACCESS_PRIORITY,
            FLOW_COLUMN_CORE_PRIORITY
    };
    
    protected static boolean alwaysDecodeEth = true;

    @Override
    public ModuleLoaderState getModuleLoaderState(){
        return moduleLoaderState;
    }

    // Load monitor for overload protection
    protected final boolean overload_drop = Boolean.parseBoolean(System.getProperty("overload_drop", "false"));
    protected final LoadMonitor loadmonitor = new LoadMonitor(log);
                
    /**
     *  Updates handled by the main loop
     */
    public interface IUpdate {
        /**
         * Calls the appropriate listeners
         */
        public void dispatch();
    }
        
    /**
     * Update message indicating
     * IPs of controllers in controller cluster have changed.
     */
    private class HAControllerNodeIPUpdate implements IUpdate {
        public final Map<String,String> curControllerNodeIPs;
        public final Map<String,String> addedControllerNodeIPs;
        public final Map<String,String> removedControllerNodeIPs;
        public HAControllerNodeIPUpdate(
                HashMap<String,String> curControllerNodeIPs,
                HashMap<String,String> addedControllerNodeIPs,
                HashMap<String,String> removedControllerNodeIPs) {
            this.curControllerNodeIPs = curControllerNodeIPs;
            this.addedControllerNodeIPs = addedControllerNodeIPs;
            this.removedControllerNodeIPs = removedControllerNodeIPs;
        }
        @Override
        public void dispatch() {
            if (log.isTraceEnabled()) {
                log.trace("Dispatching HA Controller Node IP update "
                        + "curIPs = {}, addedIPs = {}, removedIPs = {}",
                        new Object[] { curControllerNodeIPs, addedControllerNodeIPs,
                            removedControllerNodeIPs }
                        );
            }
            if (haListeners != null) {
                for (IHAListener listener: haListeners.getOrderedListeners()) {
                    listener.controllerNodeIPsChanged(curControllerNodeIPs,
                            addedControllerNodeIPs, removedControllerNodeIPs);
                }
            }
        }
    }

    // ***************
    // Getters/Setters
    // ***************

    void setStorageSourceService(IStorageSourceService storageSource) {
        storageSourceService = storageSource;
    }

    IStorageSourceService getStorageSourceService() {
        return storageSourceService;
    }
    
    IShutdownService getShutdownService() {
    	return shutdownService;
    }
    
    void setShutdownService(IShutdownService shutdownService) {
    	Controller.shutdownService = shutdownService;
    }
    
    void setDebugCounter(IDebugCounterService debugCounters) {
        debugCounterService = debugCounters;
    }

    IDebugCounterService getDebugCounter() {
        return debugCounterService;
    }

    void setSyncService(ISyncService syncService) {
        Controller.syncService = syncService;
    }
    void setPktInProcessingService(IPktInProcessingTimeService pits) {
        Controller.pktinProcTimeService = pits;
    }
    
    void setRestApiService(IRestApiService restApi) {
        Controller.restApiService = restApi;
    }

    
    void setThreadPoolService(IThreadPoolService tp) {
        threadPoolService = tp;
    }
    IThreadPoolService getThreadPoolService() {
        return threadPoolService;
    }

    
    public void setSwitchService(IOFSwitchService switchService) {
       Controller.switchService = switchService;
    }
    public IOFSwitchService getSwitchService() {
        return Controller.switchService;
    }

    @Override
    public HARole getRole() {
        return notifiedRole;
    }

    @Override
    public RoleInfo getRoleInfo() {
        return roleManager.getRoleInfo();
    }

    @Override
    public void setRole(HARole role, String changeDescription) {
        roleManager.setRole(role, changeDescription);
    }

    // ****************
    // Message handlers
    // ****************
    
    // Handler for SwitchPortsChanged was here (notifyPortChanged). Handled in OFSwitchManager

    /**
     * flcontext_cache - Keep a thread local stack of contexts
     */
    protected static final ThreadLocal<Stack<FloodlightContext>> flcontext_cache =
        new ThreadLocal <Stack<FloodlightContext>> () {
            @Override
            protected Stack<FloodlightContext> initialValue() {
                return new Stack<FloodlightContext>();
            }
        };

    /**
     * flcontext_alloc - pop a context off the stack, if required create a new one
     * @return FloodlightContext
     */
    protected static FloodlightContext flcontext_alloc() {
        FloodlightContext flcontext = null;

        if (flcontext_cache.get().empty()) {
            flcontext = new FloodlightContext();
        }
        else {
            flcontext = flcontext_cache.get().pop();
        }

        return flcontext;
    }

    /**
     * flcontext_free - Free the context to the current thread
     * @param flcontext
     */
    protected void flcontext_free(FloodlightContext flcontext) {
        flcontext.getStorage().clear();
        flcontext_cache.get().push(flcontext);
    }


    /**
     *
     * Handle and dispatch a message to IOFMessageListeners.
     *
     * We only dispatch messages to listeners if the controller's role is MASTER.
     *
     * @param sw The switch sending the message
     * @param m The message the switch sent
     * @param flContext The floodlight context to use for this message. If
     * null, a new context will be allocated.
     * @throws IOException
     *
     * FIXME: this method and the ChannelHandler disagree on which messages
     * should be dispatched and which shouldn't
     */
    @Override
    public void handleMessage(IOFSwitch sw, OFMessage m,
                                 FloodlightContext bContext) {
        Ethernet eth = null;
        log.trace("Dispatching OFMessage to listeners.");
        if (this.notifiedRole == HARole.STANDBY) {
            counters.dispatchMessageWhileStandby.increment();
            // We are SLAVE. Do not dispatch messages to listeners.
            return;
        }
        counters.dispatchMessage.increment();

        switch (m.getType()) {
            case PACKET_IN:
            	counters.packetIn.increment();
                OFPacketIn pi = (OFPacketIn)m;

                if (pi.getData().length <= 0) {
                    log.error("Ignoring PacketIn (Xid = " + pi.getXid() + ") because the data field is empty.");
                    return;
                }

                if (alwaysDecodeEth) {
                    eth = new Ethernet();
                    eth.deserialize(pi.getData(), 0, pi.getData().length);
                }
                // fall through to default case...

            default:

                List<IOFMessageListener> listeners = null;
                if (messageListeners.containsKey(m.getType())) {
                    listeners = messageListeners.get(m.getType()).getOrderedListeners();
                }

                FloodlightContext bc = null;
                if (listeners != null) {
                    // Check if floodlight context is passed from the calling
                    // function, if so use that floodlight context, otherwise
                    // allocate one
                    if (bContext == null) {
                        bc = flcontext_alloc();
                    } else {
                        bc = bContext;
                    }
                    if (eth != null) {
                        IFloodlightProviderService.bcStore.put(bc,
                                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                                eth);
                    }

                    // Get the starting time (overall and per-component) of
                    // the processing chain for this packet if performance
                    // monitoring is turned on
                    pktinProcTimeService.bootstrap(listeners);
                    pktinProcTimeService.recordStartTimePktIn();
                    Command cmd;
                    for (IOFMessageListener listener : listeners) {
                        pktinProcTimeService.recordStartTimeComp(listener);
                        cmd = listener.receive(sw, m, bc);
                        pktinProcTimeService.recordEndTimeComp(listener);

                        if (Command.STOP.equals(cmd)) {
                            break;
                        }
                    }
                    pktinProcTimeService.recordEndTimePktIn(sw, m, bc);
                }
                // paag
                // And just before we exit the controller loop we see if anyone
                // is interested in knowing that we are exiting the loop
                for (IControllerCompletionListener listener : completionListeners)
                	listener.onMessageConsumed(sw, m, bc);
                
                if ((bContext == null) && (bc != null)) flcontext_free(bc);
        }
    }
    
    // ***************
    // IFloodlightProvider
    // ***************

    /**
     * Forwards to RoleManager
     * @param ofSwitchHandshakeHandler
     * @param role
     */
    void reassertRole(OFSwitchHandshakeHandler ofSwitchHandshakeHandler, HARole role) {
        roleManager.reassertRole(ofSwitchHandshakeHandler, role);
    }

    @Override
    public String getControllerId() {
        return controllerId;
    }

    @Override
    public synchronized void addCompletionListener(IControllerCompletionListener listener) {
    	completionListeners.add(listener);
    }
    
    @Override
    public synchronized void removeCompletionListener(IControllerCompletionListener listener) {
    	String listenerName = listener.getName();
    	if (completionListeners.remove(listener)) {
    		log.debug("Removing completion listener {}" , listenerName);
    	} else {
    		log.warn("Trying to remove unknown completion listener {}" , listenerName);
    	}
    	listenerName=null; // help gc
    }
    
    @Override
    public synchronized void addOFMessageListener(OFType type, IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd =
            messageListeners.get(type);
        if (ldd == null) {
            ldd = new ListenerDispatcher<OFType, IOFMessageListener>();
            messageListeners.put(type, ldd);
        }
        ldd.addListener(type, listener);
    }

    @Override
    public synchronized void removeOFMessageListener(OFType type, IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd =
            messageListeners.get(type);
        if (ldd != null) {
            ldd.removeListener(listener);
        }
    }

    private void logListeners() {
        for (Map.Entry<OFType, ListenerDispatcher<OFType, IOFMessageListener>> entry : messageListeners.entrySet()) {
            OFType type = entry.getKey();
            ListenerDispatcher<OFType, IOFMessageListener> ldd = entry.getValue();

            StringBuilder sb = new StringBuilder();
            sb.append("OFListeners for ");
            sb.append(type);
            sb.append(": ");
            for (IOFMessageListener l : ldd.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            log.debug(sb.toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("HAListeners: ");
        for (IHAListener l: haListeners.getOrderedListeners()) {
            sb.append(l.getName());
            sb.append(", ");
        }
        log.debug(sb.toString());
    }

    public void removeOFMessageListeners(OFType type) {
        messageListeners.remove(type);
    }

    @Override
    public Map<OFType, List<IOFMessageListener>> getListeners() {
        Map<OFType, List<IOFMessageListener>> lers =
            new HashMap<OFType, List<IOFMessageListener>>();
        for(Entry<OFType, ListenerDispatcher<OFType, IOFMessageListener>> e : messageListeners.entrySet()) {
            lers.put(e.getKey(), e.getValue().getOrderedListeners());
        }
        return Collections.unmodifiableMap(lers);
    }

    @Override
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m) {
        if (sw == null)
            throw new NullPointerException("Switch must not be null");
        if (m == null)
            throw new NullPointerException("OFMessage must not be null");

        FloodlightContext bc = new FloodlightContext();

        List<IOFMessageListener> listeners = null;
        if (messageListeners.containsKey(m.getType())) {
            listeners = messageListeners.get(m.getType()).getOrderedListeners();
        }

        if (listeners != null) {
            for (IOFMessageListener listener : listeners) {
                if (Command.STOP.equals(listener.receive(sw, m, bc))) {
                    break;
                }
            }
        }
    }

    // **************
    // Initialization
    // **************

    /**
     * Sets the initial role based on properties in the config params.
     * It looks for two different properties.
     * If the "role" property is specified then the value should be
     * either "EQUAL", "MASTER", or "SLAVE" and the role of the
     * controller is set to the specified value. If the "role" property
     * is not specified then it looks next for the "role.path" property.
     * In this case the value should be the path to a property file in
     * the file system that contains a property called "floodlight.role"
     * which can be one of the values listed above for the "role" property.
     * The idea behind the "role.path" mechanism is that you have some
     * separate heartbeat and master controller election algorithm that
     * determines the role of the controller. When a role transition happens,
     * it updates the current role in the file specified by the "role.path"
     * file. Then if floodlight restarts for some reason it can get the
     * correct current role of the controller from the file.
     * @param configParams The config params for the FloodlightProvider service
     * @return A valid role if role information is specified in the
     *         config params, otherwise null
     */
    protected HARole getInitialRole(Map<String, String> configParams) {
        HARole role = HARole.STANDBY;
        String roleString = configParams.get("role");
        if (roleString != null) {
            try {
                role = HARole.valueOfBackwardsCompatible(roleString);
            }
            catch (IllegalArgumentException exc) {
                log.error("Invalid current role value: {}", roleString);
            }
        }

        log.info("Controller role set to {}", role);
        return role;
    }

    /**
     * Tell controller that we're ready to accept switches loop
     * @throws IOException
     */
    @Override
    public void run() {
        moduleLoaderState = ModuleLoaderState.COMPLETE;

        if (log.isDebugEnabled()) {
            logListeners();
        }

        while (true) {
            try {
                IUpdate update = updates.take();
                update.dispatch();
            } catch (InterruptedException e) {
                log.error("Received interrupted exception in updates loop;" +
                          "terminating process");
                log.info("Calling System.exit");
                System.exit(1);
            } catch (StorageException e) {
                log.error("Storage exception in controller " +
                          "updates loop; terminating process", e);
                log.info("Calling System.exit");
                System.exit(1);
            } catch (Exception e) {
                log.error("Exception in controller updates loop", e);
            }
        }
    }
    
    private void setConfigParams(Map<String, String> configParams) throws FloodlightModuleException {
        /**
         * Tulio Ribeiro
         */
        String controllerId = configParams.get("controllerId");
        if (!Strings.isNullOrEmpty(controllerId)) {
            this.controllerId = controllerId;
        }        
        log.info("ControllerId set to {}", this.controllerId);
        
        String shutdown = configParams.get("shutdownOnTransitionToStandby");
        if (!Strings.isNullOrEmpty(shutdown)) {
            try {
                shutdownOnTransitionToStandby = Boolean.parseBoolean(shutdown.trim());
            } catch (Exception e) {
                log.error("Could not parse 'shutdownOnTransitionToStandby' of {}. Using default setting of {}", 
                        shutdown, shutdownOnTransitionToStandby);
            }
        }        
        log.info("Shutdown when controller transitions to STANDBY HA role: {}", shutdownOnTransitionToStandby);
        
        String decodeEth = configParams.get("deserializeEthPacketIns");
        if (!Strings.isNullOrEmpty(decodeEth)) {
        	try {
        		alwaysDecodeEth = Boolean.parseBoolean(decodeEth.trim());
        	} catch (Exception e) {
        		log.error("Could not parse 'deserializeEthPacketIns' of {}. Using default setting of {}", decodeEth, alwaysDecodeEth);
        	}
        }        
        if (alwaysDecodeEth) {
	        log.warn("Controller will automatically deserialize all Ethernet packet-in messages. "
	        		+ "Set 'deserializeEthPacketIns' to 'FALSE' if this feature is not "
	        		+ "required or when benchmarking core performance");
        } else {
            log.info("Controller will not automatically deserialize all Ethernet packet-in messages. "
            		+ "Set 'deserializeEthPacketIns' to 'TRUE' to enable this feature");
        }
    }

    /**
     * Initialize internal data structures
     */
    public void init(Map<String, String> configParams) throws FloodlightModuleException {

        moduleLoaderState = ModuleLoaderState.INIT;

        // These data structures are initialized here because other
        // module's startUp() might be called before ours        
        messageListeners = new ConcurrentHashMap<OFType, ListenerDispatcher<OFType, IOFMessageListener>>();
        haListeners = new ListenerDispatcher<HAListenerTypeMarker, IHAListener>();
        controllerNodeIPsCache = new HashMap<String, String>();
        updates = new LinkedBlockingQueue<IUpdate>();
        providerMap = new HashMap<String, List<IInfoProvider>>();
        completionListeners = new ConcurrentLinkedQueue<IControllerCompletionListener>();
        
        setConfigParams(configParams);

        HARole initialRole = getInitialRole(configParams);
        notifiedRole = initialRole;
        shutdownService = new ShutdownServiceImpl();

        roleManager = new RoleManager(this, shutdownService,
                                           notifiedRole,
                                           INITIAL_ROLE_CHANGE_DESCRIPTION);
        // Switch Service Startup
        switchService.registerLogicalOFMessageCategory(LogicalOFMessageCategory.MAIN);
        counters = new ControllerCounters(debugCounterService);
     }

    /**
     * Startup all of the controller's components
     * @param floodlightModuleLoader
     */
    public void startupComponents(FloodlightModuleLoader floodlightModuleLoader) throws FloodlightModuleException {

        moduleLoaderState = ModuleLoaderState.STARTUP;

        // Create the table names we use
        storageSourceService.createTable(CONTROLLER_TABLE_NAME, null);
        storageSourceService.createTable(CONTROLLER_INTERFACE_TABLE_NAME, null);
        storageSourceService.createTable(SWITCH_CONFIG_TABLE_NAME, null);
        storageSourceService.setTablePrimaryKeyName(CONTROLLER_TABLE_NAME, CONTROLLER_ID);
        storageSourceService.addListener(CONTROLLER_INTERFACE_TABLE_NAME, this);

        storageSourceService.createTable(FLOW_PRIORITY_TABLE_NAME, null);
        storageSourceService.setTablePrimaryKeyName(FLOW_PRIORITY_TABLE_NAME, FLOW_COLUMN_PRIMARY_KEY);
        storageSourceService.addListener(FLOW_PRIORITY_TABLE_NAME, this);
        readFlowPriorityConfigurationFromStorage(); // 
        
        // Startup load monitoring
        if (overload_drop) {
            this.loadmonitor.startMonitoring(threadPoolService.getScheduledExecutor());
        }

        // Add our REST API
        restApiService.addRestletRoutable(new CoreWebRoutable());
                
        try {
            syncService.registerStore(OFSwitchManager.SWITCH_SYNC_STORE_NAME, Scope.LOCAL);
        } catch (SyncException e) {
            throw new FloodlightModuleException("Error while setting up sync service", e);
        }
        
        addInfoProvider("summary", this);
    }
    
    private void readFlowPriorityConfigurationFromStorage() {
        try {
            Map<String, Object> row;
            IResultSet resultSet = storageSourceService.executeQuery(
                FLOW_PRIORITY_TABLE_NAME, FLOW_COLUMN_NAMES, null, null);
            if (resultSet == null)
                return;

            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                row = it.next().getRow();
                if (row.containsKey(FLOW_COLUMN_PRIMARY_KEY)) {
                    String primary_key = (String) row.get(FLOW_COLUMN_PRIMARY_KEY);
                    if (primary_key.equals(FLOW_VALUE_PRIMARY_KEY)) {
                        if (row.containsKey(FLOW_COLUMN_ACCESS_PRIORITY)) {
                            // Not used anymore DEFAULT_ACCESS_PRIORITY = Short.valueOf((String) row.get(FLOW_COLUMN_ACCESS_PRIORITY));
                        }
                        if (row.containsKey(FLOW_COLUMN_CORE_PRIORITY)) {
                            // Not used anymore DEFAULT_CORE_PRIORITY = Short.valueOf((String) row.get(FLOW_COLUMN_CORE_PRIORITY));
                        }
                    }
                }
            }
        }
        catch (StorageException e) {
            log.error("Failed to access storage for forwarding configuration: {}", e.getMessage());
        }
        catch (NumberFormatException e) {
            // log error, no stack-trace
            log.error("Failed to read core or access flow priority from " +
                      "storage. Illegal number: {}", e.getMessage());
        }
    }

    @Override
    public void addInfoProvider(String type, IInfoProvider provider) {
        if (!providerMap.containsKey(type)) {
            providerMap.put(type, new ArrayList<IInfoProvider>());
        }
        providerMap.get(type).add(provider);
    }

    @Override
    public void removeInfoProvider(String type, IInfoProvider provider) {
        if (!providerMap.containsKey(type)) {
            log.debug("Provider type {} doesn't exist.", type);
            return;
        }
        providerMap.get(type).remove(provider);
    }

    @Override
    public Map<String, Object> getControllerInfo(String type) {
        if (!providerMap.containsKey(type)) return null;

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (IInfoProvider provider : providerMap.get(type)) {
            result.putAll(provider.getInfo(type));
        }
        return result;
    }

    @Override
    public void addHAListener(IHAListener listener) {
        haListeners.addListener(null,listener);
    }

    @Override
    public void removeHAListener(IHAListener listener) {
        haListeners.removeListener(listener);
    }

    /**
     * Handle changes to the controller nodes IPs and dispatch update.
     */
    protected void handleControllerNodeIPChanges() {
        HashMap<String,String> curControllerNodeIPs = new HashMap<String,String>();
        HashMap<String,String> addedControllerNodeIPs = new HashMap<String,String>();
        HashMap<String,String> removedControllerNodeIPs =new HashMap<String,String>();
        String[] colNames = { CONTROLLER_INTERFACE_CONTROLLER_ID,
                           CONTROLLER_INTERFACE_TYPE,
                           CONTROLLER_INTERFACE_NUMBER,
                           CONTROLLER_INTERFACE_DISCOVERED_IP };
        synchronized(controllerNodeIPsCache) {
            // We currently assume that interface Ethernet0 is the relevant
            // controller interface. Might change.
            // We could (should?) implement this using
            // predicates, but creating the individual and compound predicate
            // seems more overhead then just checking every row. Particularly,
            // since the number of rows is small and changes infrequent
            IResultSet res = storageSourceService.executeQuery(CONTROLLER_INTERFACE_TABLE_NAME,
                    colNames,null, null);
            while (res.next()) {
                if (res.getString(CONTROLLER_INTERFACE_TYPE).equals("Ethernet") &&
                        res.getInt(CONTROLLER_INTERFACE_NUMBER) == 0) {
                    String controllerID = res.getString(CONTROLLER_INTERFACE_CONTROLLER_ID);
                    String discoveredIP = res.getString(CONTROLLER_INTERFACE_DISCOVERED_IP);
                    String curIP = controllerNodeIPsCache.get(controllerID);

                    curControllerNodeIPs.put(controllerID, discoveredIP);
                    if (curIP == null) {
                        // new controller node IP
                        addedControllerNodeIPs.put(controllerID, discoveredIP);
                    }
                    else if (!curIP.equals(discoveredIP)) {
                        // IP changed
                        removedControllerNodeIPs.put(controllerID, curIP);
                        addedControllerNodeIPs.put(controllerID, discoveredIP);
                    }
                }
            }
            // Now figure out if rows have been deleted. We can't use the
            // rowKeys from rowsDeleted directly, since the tables primary
            // key is a compound that we can't disassemble
            Set<String> curEntries = curControllerNodeIPs.keySet();
            Set<String> removedEntries = controllerNodeIPsCache.keySet();
            removedEntries.removeAll(curEntries);
            for (String removedControllerID : removedEntries)
                removedControllerNodeIPs.put(removedControllerID,
                                             controllerNodeIPsCache.get(removedControllerID));
            controllerNodeIPsCache.clear();
            controllerNodeIPsCache.putAll(curControllerNodeIPs);
            //counters.controllerNodeIpsChanged.updateCounterWithFlush();
            HAControllerNodeIPUpdate update = new HAControllerNodeIPUpdate(
                                curControllerNodeIPs, addedControllerNodeIPs,
                                removedControllerNodeIPs);
            if (!removedControllerNodeIPs.isEmpty() || !addedControllerNodeIPs.isEmpty()) {
                addUpdateToQueue(update);
            }
        }
    }
    
    @Override
    public Map<String, String> getControllerNodeIPs() {
        // We return a copy of the mapping so we can guarantee that
        // the mapping return is the same as one that will be (or was)
        // dispatched to IHAListeners
        HashMap<String,String> retval = new HashMap<String,String>();
        synchronized(controllerNodeIPsCache) {
            retval.putAll(controllerNodeIPsCache);
        }
        return retval;
    }
    
    private static final String FLOW_PRIORITY_CHANGED_AFTER_STARTUP =
            "Flow priority configuration has changed after " +
            "controller startup. Restart controller for new " +
            "configuration to take effect.";
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(CONTROLLER_INTERFACE_TABLE_NAME)) {
            handleControllerNodeIPChanges();
        } else if (tableName.equals(FLOW_PRIORITY_TABLE_NAME)) {
            log.warn(FLOW_PRIORITY_CHANGED_AFTER_STARTUP);
        }
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(CONTROLLER_INTERFACE_TABLE_NAME)) {
            handleControllerNodeIPChanges();
        } else if (tableName.equals(FLOW_PRIORITY_TABLE_NAME)) {
            log.warn(FLOW_PRIORITY_CHANGED_AFTER_STARTUP);
        }
    }

    @Override
    public long getSystemStartTime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        return rb.getStartTime();
    }

    @Override
    public Map<String, Long> getMemory() {
        Map<String, Long> m = new HashMap<String, Long>();
        Runtime runtime = Runtime.getRuntime();
        m.put("total", runtime.totalMemory());
        m.put("free", runtime.freeMemory());
        return m;
    }
    
    @Override
    public Long getUptime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        return rb.getUptime();
    }

    @Override
    public void addUpdateToQueue(IUpdate update) {
        try {
            updates.put(update);
        } catch (InterruptedException e) {
            // This should never happen
            log.error("Failure adding update {} to queue.", update);
        }
    }

    /**
     * FOR TESTING ONLY.
     * Dispatch all updates in the update queue until queue is empty
     */
    void processUpdateQueueForTesting() {
        while (!updates.isEmpty()) {
            IUpdate update = updates.poll();
            if (update != null)
                update.dispatch();
        }
    }

    /**
     * FOR TESTING ONLY
     * check if update queue is empty
     */
    boolean isUpdateQueueEmptyForTesting() {
        return updates.isEmpty();
    }

    /**
     * FOR TESTING ONLY
     */
    void resetModuleState() {
        moduleLoaderState = ModuleLoaderState.INIT;
    }

    /**
     * FOR TESTING ONLY
     * sets module loader state
     */
    void setModuleLoaderStateForTesting(ModuleLoaderState state) {
        moduleLoaderState = state;
    }

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();

        info.put("# Switches", switchService.getAllSwitchDpids().size());
        return info;
    }

    protected void setNotifiedRole(HARole newRole) {
        notifiedRole = newRole;
    }

    @Override
    public RoleManager getRoleManager() {
        return roleManager;
    }

    public Optional<ControllerId> getId() {
        short nodeId = syncService.getLocalNodeId();
        if(nodeId == ClusterConfig.NODE_ID_UNCONFIGURED)
            return Optional.absent();
        else
            return Optional.of(ControllerId.of(nodeId));
    }
    
    public ControllerCounters getCounters() {
        return counters;
    }
}