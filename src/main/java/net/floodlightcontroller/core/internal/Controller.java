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

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchFilter;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFSwitchBase;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.SwitchSyncRepresentation;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.LoadMonitor;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorExtensions;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The main controller class.  Handles all setup and network listeners
 */
public class Controller implements IFloodlightProviderService,
            IStorageSourceListener {

    protected static Logger log = LoggerFactory.getLogger(Controller.class);

    static final String ERROR_DATABASE =
            "The controller could not communicate with the system database.";
    static final String SWITCH_SYNC_STORE_NAME =
            "net.floodlightcontroller.core.SwitchSyncStore";

    protected BasicFactory factory;
    protected ConcurrentMap<OFType,
                            ListenerDispatcher<OFType,IOFMessageListener>>
                                messageListeners;

    // OFSwitch driver binding map and order
    private ISwitchDriverRegistry driverRegistry;

    // The controllerNodeIPsCache maps Controller IDs to their IP address.
    // It's only used by handleControllerNodeIPsChanged
    protected HashMap<String, String> controllerNodeIPsCache;

    protected Set<IOFSwitchListener> switchListeners;
    protected Set<IHAListener> haListeners;
    protected Map<String, List<IInfoProvider>> providerMap;
    protected BlockingQueue<IUpdate> updates;

    // Module dependencies
    private IRestApiService restApi;
    private ICounterStoreService counterStore = null;
    private IDebugCounterService debugCounter;
    private IFlowCacheService bigFlowCacheMgr;
    private IStorageSourceService storageSource;
    private IPktInProcessingTimeService pktinProcTime;
    private IThreadPoolService threadPool;
    private ScheduledExecutorService ses;
    private IDebugCounterService debugCouAnterService;
    private ISyncService syncService;
    private IStoreClient<Long, SwitchSyncRepresentation> storeClient;

    // Configuration options
    protected int openFlowPort = 6633;
    protected int workerThreads = 0;


    private MessageDispatchGuard messageDispatchGuard;

    // This controller's current role that modules can use/query to decide
    // if they should operate in master or slave mode.
    // TODO: potentially we need to get rid of this field and modules must
    // then rely on the role notifications alone...
    protected volatile Role notifiedRole;

    private static final String
            INITIAL_ROLE_CHANGE_DESCRIPTION = "Controller startup.";
    private RoleManager roleManager;
    private SwitchManager switchManager;


    // Flag to always flush flow table on switch reconnect (HA or otherwise)
    private boolean alwaysClearFlowsOnSwAdd = false;

    // Storage table names
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

    // Perf. related configuration
    protected static final int SEND_BUFFER_SIZE = 4 * 1024 * 1024;
    public static final int BATCH_MAX_SIZE = 100;
    protected static final boolean ALWAYS_DECODE_ETH = true;


    private static long ROLE_FLAP_DAMPEN_TIME_MS = 2*1000; // 2 sec

    // Load monitor for overload protection
    protected final boolean overload_drop =
        Boolean.parseBoolean(System.getProperty("overload_drop", "false"));
    protected final LoadMonitor loadmonitor = new LoadMonitor(log);



    /**
     * A utility class for guarding message dispatch to IOFMessage listeners
     * especially during role transitions.
     *
     * The general goal we want to achieve is that IOFMessages are only
     * dispatched to listeners if the listeners / modules have been notified
     * to be in MASTER role. This guard helps ensure that no more messages
     * are in the pipeline before notifying modules.
     *
     * The dispatch method must use acquireDispatchGuardAndCheck() and check
     * its return value before calling the listeners. It also needs to
     * releaseDispatchGuard() after the listeners have been called. Release
     * should happen in a finally clause!
     *
     * @author gregor
     *
     */
    private class MessageDispatchGuard {
        /* We implement this using read/write lock. The dispatching method
         * acquires the readlock, thus allowing multiple threads to
         * dispatch at the same time. After acquiring the read-lock a user
         * checks if dispatching is allowed. The lock is release after the
         * dispatch is complete.
         *
         * When dispatching is enabled/disabled we acquire the write-lock, thus
         * ensuring that no more messages are currently in the pipeline. Once
         * we have the lock, the status can be changed.
         */
        private final ReentrantReadWriteLock lock;
        private boolean dispatchEnabled;

        /**
         * @param dispatchAllowed if dispatching messages is allowed after
         * instantiation
         */
        public MessageDispatchGuard(boolean dispatchAllowed) {
            this.dispatchEnabled = dispatchAllowed;
            lock = new ReentrantReadWriteLock();
        }

        /**
         * message dispatching will be enabled. This method will block until
         * nobody is holding the guard lock
         */
        public void enableDispatch() {
            lock.writeLock().lock();
            try {
                this.dispatchEnabled = true;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * message dispatch will be disabled. This method will block until
         * nobody is holding the guard lock, i.e., until all messages are
         * drained fromt the pipeline
         */
        public void disableDispatch() {
            lock.writeLock().lock();
            try {
                this.dispatchEnabled = false;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Acquire the guard lock and return true if dispatching is enabled.
         * Acquire the guard lock and return true if dispatching is enabled.
         * Calls
         * to this method should immediately be followed by a try-finally block
         * and the finally block should call releaseDispatchGuard()
         *
         * @return true if dispatch is enabled
         */
        public boolean acquireDispatchGuardAndCheck() {
            lock.readLock().lock();
            return this.dispatchEnabled;
        }

        /**
         * Release the guard lock.
         */
        public void releaseDispatchGuard() {
            lock.readLock().unlock();
        }
    }


    /**
     * A utility class to manage the <i>controller roles</i>.
     *
     * A utility class to manage the <i>controller roles</i>  as opposed
     * to the switch roles. The class manages the controllers current role,
     * handles role change requests, and maintains the list of connected
     * switch(-channel) so it can notify the switches of role changes.
     *
     * We need to ensure that every connected switch is always send the
     * correct role. Therefore, switch add, sending of the intial role, and
     * changing role need to use mutexes to ensure this. This has the ugly
     * side-effect of requiring calls between controller and OFChannelHandler
     *
     * This class will also dampen multiple role request if they happen too
     * fast. The first request will be send immediately. But if more requests
     * are received within ROLE_FLAP_DAMPEN_TIME_MS the requests will be
     * delayed by a SingletonTask
     *
     * This class is fully thread safe. Its method can safely be called from
     * any thread.
     *
     * @author gregor
     *
     */
    private class RoleManager {
        private long lastRoleChangeTimeMillis;
        // This role represents the role that has been set by setRole. This
        // role might or might now have been notified to listeners just yet.
        // This is updated by setRole. doSetRole() will use this value as
        private Role role;
        private String roleChangeDescription;

        // The current role info. This is updated /after/ dampening
        // switches and
        // listener notifications have been enqueued (but potentially before
        // they have been dispatched)
        private RoleInfo currentRoleInfo;
        private final Set<OFChannelHandler> connectedChannelHandlers;

        /**
         * This SingletonTask performs actually sends the role request
         * to the channels.
         */
        private final SingletonTask changerTask;


        /**
         * @param role initial role
         * @param roleChangeDescription initial value of the change description
         * @throws NullPointerException if role or roleChangeDescription is null
         */
        public RoleManager(Role role, String roleChangeDescription) {
            if (role == null)
                throw new NullPointerException("role must not be null");
            if (roleChangeDescription == null) {
                throw new NullPointerException("roleChangeDescription must " +
                                               "not be null");
            }

            this.changerTask =
                    new SingletonTask(Controller.this.ses, new Runnable() {
                        @Override
                        public void run() {
                            doSetRole();
                        }
                    });
            this.role = role;
            this.roleChangeDescription = roleChangeDescription;
            this.connectedChannelHandlers = new HashSet<OFChannelHandler>();
            this.currentRoleInfo = new RoleInfo(this.role,
                                           this.roleChangeDescription,
                                           new Date());
        }

        /**
         * Add a newly connected OFChannelHandler. The channel handler is added
         * we send the current role to the channel handler. All subsequent role
         * changes will be send to all connected
         * @param h The OFChannelHandler to add
         */
        public synchronized void
                addOFChannelHandlerAndSendRole(OFChannelHandler h) {
            connectedChannelHandlers.add(h);
            h.sendRoleRequest(this.role);
        }

        /**
         * Remove OFChannelHandler. E.g., due do disconnect.
         * @param h The OFChannelHandler to remove.
         */
        public synchronized void removeOFChannelHandler(OFChannelHandler h) {
            connectedChannelHandlers.remove(h);
        }

        /**
         * Re-assert a role for the given channel handler.
         *
         * The caller specifies the role that should be reasserted. We only
         * reassert the role if the controller's current role matches the
         * reasserted role and there is no role request for the reasserted role
         * pending.
         * @param h The OFChannelHandler on which we should reassert.
         * @param role The role to reassert
         */
        public synchronized void reassertRole(OFChannelHandler h, Role role) {
            // check if the requested reassertion actually makes sense
            if (this.role != role)
                return;
            h.sendRoleRequestIfNotPending(this.role);
        }

        /**
         * Set the controller's new role and notify switches.
         *
         * This method updates the controllers current role and notifies all
         * connected switches of the new role is different from the current
         * role. We dampen calls to this method. See class description for
         * details.
         *
         * @param role The new role.
         * @param roleChangeDescription A textual description of why the role
         * was changed. For information purposes only.
         * @throws NullPointerException if role or roleChangeDescription is null
         */
        public synchronized void setRole(Role role, String roleChangeDescription) {
            if (role == null)
                throw new NullPointerException("role must not be null");
            if (roleChangeDescription == null) {
                throw new NullPointerException("roleChangeDescription must " +
                                               "not be null");
            }
            long delay;
            if (role == this.role) {
                log.debug("Received role request for {} but controller is "
                        + "already {}. Ingoring it.", role, this.role);
                return;
            }
            this.role = role;
            this.roleChangeDescription = roleChangeDescription;

            long now = System.currentTimeMillis();
            long timeSinceLastRoleChange = now - lastRoleChangeTimeMillis;
            if (timeSinceLastRoleChange < ROLE_FLAP_DAMPEN_TIME_MS) {
                // the last time the role was changed was less than
                // ROLE_FLAP_DAMPEN_TIME_MS in the past. We delay the
                // next notification to switches by ROLE_FLAP_DAMPEN_TIME_MS
                delay = ROLE_FLAP_DAMPEN_TIME_MS;
                if (log.isDebugEnabled()) {
                    log.debug("Last role change was {} ms ago, delaying role" +
                            " delaying role change to {}",
                            lastRoleChangeTimeMillis, role);
                }
            } else {
                // last role change was longer than ROLE_FLAP_DAMPEN_TIME_MS
                // ago. Notify switches immediately.
                delay = 0;
            }
            lastRoleChangeTimeMillis = now;
            changerTask.reschedule(delay, TimeUnit.MILLISECONDS);
        }

        /**
         * The internal method that actually sends the notification to
         * the switches and that enqueues the role update to HAListeners.
         * Also updates the RoleInfo we return to REST callers.
         */
        private synchronized void doSetRole() {
            currentRoleInfo = new RoleInfo(this.role,
                                           this.roleChangeDescription,
                                           new Date());
            for (OFChannelHandler h: connectedChannelHandlers)
                h.sendRoleRequest(this.role);

            Controller.this.addUpdateToQueue(new HARoleUpdate(role));
        }

        /**
         * Return the RoleInfo object describing the current role.
         *
         * Return the RoleInfo object describing the current role. The
         * RoleInfo object is used by REST API users. We need to return
         * a defensive copy.
         * @return the current RoleInfo object
         */
        public synchronized RoleInfo getRoleInfo() {
            return new RoleInfo(currentRoleInfo);
        }
    }


    private class SwitchManager implements IStoreListener<Long> {
        private Role role;
        private ConcurrentHashMap<Long,IOFSwitch> activeSwitches;
        private ConcurrentHashMap<Long,IOFSwitch> syncedSwitches;

        public SwitchManager(Role role) {
            this.role = role;
            this.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();
            this.syncedSwitches = new ConcurrentHashMap<Long, IOFSwitch>();
        }

        @Override
        public void keysModified(Iterator<Long> keys, UpdateType type) {
            if (type == UpdateType.LOCAL) {
                // We only care for remote updates
                return;
            }
            while(keys.hasNext()) {
                Long key = keys.next();
                Versioned<SwitchSyncRepresentation> versionedSwitch = null;
                try {
                    versionedSwitch = storeClient.get(key);
                } catch (SyncException e) {
                    log.error("Exception while retrieving switch " +
                              HexString.toHexString(key) +
                              " from sync store. Skipping", e);
                    continue;
                }
                // versionedSwtich won't be null. storeClient.get() always
                // returns a non-null or throws an exception
                if (versionedSwitch.getValue() == null) {
                    // The switch doesn't exist in storage. Skip.
                    continue;
                }
                SwitchSyncRepresentation storedSwitch =
                        versionedSwitch.getValue();
                IOFSwitch sw = getOFSwitchInstance(storedSwitch.getDescription());
                sw.setFeaturesReply(storedSwitch.getFeaturesReply());
                if (!key.equals(storedSwitch.getFeaturesReply().getDatapathId())) {
                    log.error("Inconsistent DPIDs from switch sync store: " +
                              "key is {} but sw.getId() says {}. Ignoring",
                              HexString.toHexString(key), sw.getStringId());
                }
                addSwitchFromStore(sw);
            }
        }


        public synchronized void setRole(Role role) {
            this.role = role;
        }

        @LogMessageDoc(level="ERROR",
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
        /**
         * Called when a switch is activated, i.e., when it enters master
         * role relative to this controller.
         * @param sw
         */
        public synchronized void switchActivated(IOFSwitch sw) {
            if (role == Role.SLAVE)
                return; // only react to switch connections when master
            Long dpid = sw.getId();

            IOFSwitch oldSw = this.activeSwitches.put(dpid, sw);
            addSwitchToStore(sw);

            if (oldSw == sw)  {
                // Note == for object equality, not .equals for value
                // TODO: should we wipe the flow table if
                // alwaysClearFlowsOnSwAdd is set? OTOH this case should
                // really never happen.
                log.info("New switchActivated for pre-existing switch {}", sw);
                return;
            }

            if (oldSw != null) {
                // This happens either when we have switches with duplicate
                // DPIDs or when a switch reconnects before we saw the
                // disconnect
                log.error("New switch added {} for already-added switch {}",
                          sw, oldSw);
                // We need to disconnect and remove the old switch
                // TODO: we notify switch listeners that the switch has been
                // removed and then we notify them that the new one has been
                // added. One could argue that a switchChanged notification
                // might be more appropriate in this case....
                oldSw.cancelAllStatisticsReplies();
                addUpdateToQueue(new SwitchUpdate(oldSw, SwitchUpdateType.REMOVED));
                oldSw.disconnectOutputStream();
                // Add the new switch and clear FlowMods
                // TODO: if this is the same switch re-connecting rather than
                // a DPID collision it would make sense to not wipe the flow
                // table.
                sw.clearAllFlowMods();
                addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.ADDED));
                addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.ACTIVATED));
                return;
            }

            IOFSwitch storedSwitch = this.syncedSwitches.get(sw.getId());
            if (storedSwitch == null) {
                // The switch isn't known to the controller cluster. We
                // need to send a switchAdded notification and clear all
                // flows.
                // TODO: if we switch was recently (seconds) connected we
                // might decide to not wipe the flow table.
                sw.clearAllFlowMods();
                addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.ADDED));
                addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.ACTIVATED));
                this.syncedSwitches.remove(sw.getId());
            } else {
                // FIXME: switch was in store. check if ports or anything else
                // has changed and send update.
                if (alwaysClearFlowsOnSwAdd)
                    sw.clearAllFlowMods();
                addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.ACTIVATED));
                sendNotificationsIfSwitchDiffers(oldSw, sw);
            }
        }


        public synchronized void addSwitchFromStore(IOFSwitch sw) {
            if (role != Role.SLAVE)
                return; // only read from store if slave
            Long dpid = sw.getId();

            IOFSwitch oldSw = syncedSwitches.put(dpid, sw);
            if (oldSw == null)  {
                addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.ADDED));
            } else {
                // The switch already exists in storage, see if anything
                // has changed
                sendNotificationsIfSwitchDiffers(oldSw, sw);
            }
        }

        public synchronized void switchDeactivated(IOFSwitch sw) {
            // ignore. we don't handle MASTER -> SLAVE transitions. We
            // expect a restart
        }

        public synchronized void switchDisconnected(IOFSwitch sw) {
            if (role == Role.SLAVE)
                return; // only react to switch connections when master
            IOFSwitch oldSw = this.activeSwitches.get(sw.getId());
            if (oldSw != sw) {
                // This can happen if the disconnected switch was inactive
                // (SLAVE) then oldSw==null. Or if we previously had the
                // "added switch for already added switch case".
                // Either way we shouldn't notify or do anything else
                log.debug("removeSwitch called for switch {} but have {} in"
                          + " activeSwitches map. Ignoring", sw, oldSw);
                return;
            }
            log.debug("removeSwitch {}", sw);
            this.activeSwitches.remove(sw.getId());
            removeSwitchFromStore(sw);
            // We cancel all outstanding statistics replies if the switch transition
            // from active. In the future we might allow statistics requests
            // from slave controllers. Then we need to move this cancelation
            // to switch disconnect
            sw.cancelAllStatisticsReplies();
            addUpdateToQueue(new SwitchUpdate(sw, SwitchUpdateType.REMOVED));
        }

        private synchronized void addSwitchToStore(IOFSwitch sw) {
            // Add to store
            // FIXME: do we need to use a put that takes a versioned here?
            // need to verify
            try {
                storeClient.put(sw.getId(), new SwitchSyncRepresentation(sw));
            } catch (ObsoleteVersionException e) {
                // FIXME: what's the right behavior here. Can the store client
                // even throw this error?
            } catch (SyncException e) {
                log.error("Could not write switch " + sw.getStringId() +
                          " to sync store:", e);
            }
        }

        private synchronized void removeSwitchFromStore(IOFSwitch sw) {
            try {
                storeClient.delete(sw.getId());
            } catch (SyncException e) {
                log.error("Could not remove switch " + sw.getStringId() +
                          " from sync store:", e);
            }
        }

        private synchronized void sendNotificationsIfSwitchDiffers(IOFSwitch sw1,
                                                      IOFSwitch sw2) {
            // FIXME
            Set<OFPhysicalPort> sw1Ports =
                    new HashSet<OFPhysicalPort>(sw1.getPorts());
            Set<OFPhysicalPort> sw2Ports =
                    new HashSet<OFPhysicalPort>(sw1.getPorts());
            if (! sw1Ports.equals(sw2Ports)) {
                addUpdateToQueue(
                        new SwitchUpdate(sw2, SwitchUpdateType.PORTCHANGED));
            }
            if (false) {
                // FIXME: IF ANYTHING ELSE HAS CHANGED
                addUpdateToQueue(
                        new SwitchUpdate(sw2, SwitchUpdateType.OTHERCHANGE));
            }
        }

        // FIXME: remove this method
        public Map<Long,IOFSwitch> getAllSwitchMap() {
            // this.syncedSwitches will be empty after the master transition
            Map<Long,IOFSwitch> switches =
                    new HashMap<Long, IOFSwitch>(this.syncedSwitches);
            if (this.role != Role.SLAVE)
                switches.putAll(this.activeSwitches);
            return switches;
        }

        public Set<Long> getAllSwitchDpids() {
            // this.syncedSwitches will be empty after the master transition
            Set<Long> dpids = new HashSet<Long>(this.syncedSwitches.keySet());
            if (this.role != Role.SLAVE)
                dpids.addAll(this.activeSwitches.keySet());
            return dpids;
        }

        public IOFSwitch getSwitch(long dpid) {
            if (this.role == Role.SLAVE)
                return this.syncedSwitches.get(dpid);
            // MASTER: if the switch is found in the active map return
            // otherwise look up the switch in the bigSync map. The bigSync map
            // wil be cleared after the transition is complete.
            IOFSwitch sw = this.activeSwitches.get(dpid);
            if (sw != null)
                return sw;
            return this.syncedSwitches.get(dpid);
        }


    }


    /**
     *  Updates handled by the main loop
     */
    interface IUpdate {
        /**
         * Calls the appropriate listeners
         */
        public void dispatch();
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
        public IOFSwitch sw;
        public SwitchUpdateType switchUpdateType;
        public SwitchUpdate(IOFSwitch sw, SwitchUpdateType switchUpdateType) {
            this.sw = sw;
            this.switchUpdateType = switchUpdateType;
        }
        @Override
        public void dispatch() {
            if (log.isTraceEnabled()) {
                log.trace("Dispatching switch update {} {}",
                        sw, switchUpdateType);
            }
            if (switchListeners != null) {
                for (IOFSwitchListener listener : switchListeners) {
                    switch(switchUpdateType) {
                        case ADDED:
                            listener.switchAdded(sw.getId());
                            break;
                        case REMOVED:
                            listener.switchRemoved(sw.getId());
                            break;
                        case PORTCHANGED:
                            listener.switchPortChanged(sw.getId());
                            break;
                        case ACTIVATED:
                            listener.switchActivated(sw.getId());
                            break;
                        case DEACTIVATED:
                            // ignore
                            break;
                        case OTHERCHANGE:
                            listener.switchChanged(sw.getId());
                            break;
                    }
                }
            }
        }
    }

    /**
     * Update message indicating controller's role has changed
     */
    private class HARoleUpdate implements IUpdate {
        private Role newRole;
        public HARoleUpdate(Role newRole) {
            this.newRole = newRole;
        }
        @Override
        public void dispatch() {
            if (log.isDebugEnabled()) {
                log.debug("Dispatching HA Role update newRole = {}",
                          newRole);
            }
            if (newRole == Role.SLAVE) {
                messageDispatchGuard.disableDispatch();
                Controller.this.notifiedRole = newRole;
            }
            if (haListeners != null) {
                for (IHAListener listener : haListeners) {
                        listener.roleChanged(newRole);
                }
            }
            if (newRole != Role.SLAVE) {
                messageDispatchGuard.enableDispatch();
                Controller.this.notifiedRole = newRole;
            }
        }
    }

    /**
     * Update message indicating
     * IPs of controllers in controller cluster have changed.
     */
    private class HAControllerNodeIPUpdate implements IUpdate {
        public Map<String,String> curControllerNodeIPs;
        public Map<String,String> addedControllerNodeIPs;
        public Map<String,String> removedControllerNodeIPs;
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
                for (IHAListener listener: haListeners) {
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
        this.storageSource = storageSource;
    }

    IStorageSourceService getStorageSourceService() {
        return this.storageSource;
    }

    void setCounterStore(ICounterStoreService counterStore) {
        this.counterStore = counterStore;
    }

    void setDebugCounter(IDebugCounterService debugCounter) {
        this.debugCounter = debugCounter;
    }

    void setSyncService(ISyncService syncService) {
        this.syncService = syncService;
    }

    void setFlowCacheMgr(IFlowCacheService flowCacheMgr) {
        this.bigFlowCacheMgr = flowCacheMgr;
    }

    void setPktInProcessingService(IPktInProcessingTimeService pits) {
        this.pktinProcTime = pits;
    }

    void setRestApiService(IRestApiService restApi) {
        this.restApi = restApi;
    }

    void setThreadPoolService(IThreadPoolService tp) {
        this.threadPool = tp;
    }

    IThreadPoolService getThreadPoolService() {
        return this.threadPool;
    }

    @Override
    public Role getRole() {
        // FIXME:
        return notifiedRole;
    }

    @Override
    public RoleInfo getRoleInfo() {
        return roleManager.getRoleInfo();
    }

    @Override
    public void setRole(Role role, String roleChangeDescription) {
        roleManager.setRole(role, roleChangeDescription);
    }


    // ****************
    // Message handlers
    // ****************

    /**
     * Indicates that ports on the given switch have changed. Enqueue a
     * switch update.
     * @param sw
     */
    protected void notifyPortChanged(IOFSwitch sw) {
        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.PORTCHANGED);
        addUpdateToQueue(update);
    }

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
     * Handle and dispatch a message to IOFMessageListeners.
     *
     * Handle and dispatch a message to IOFMessageListeners. Dispatching
     * of messages if protected by messageDispatchGuard. We only dispatch
     * messages to listeners if the controller's role is MASTER.
     *
     * @param sw The switch sending the message
     * @param m The message the switch sent
     * @param flContext The floodlight context to use for this message. If
     * null, a new context will be allocated.
     * @throws IOException
     */
    protected void handleMessage(IOFSwitch sw, OFMessage m,
                                 FloodlightContext flContext)
            throws IOException {
        boolean dispatchAllowed;

        dispatchAllowed = messageDispatchGuard.acquireDispatchGuardAndCheck();
        try {
            if (dispatchAllowed)
                handleMessageUnprotected(sw, m, flContext);
        } finally {
            messageDispatchGuard.releaseDispatchGuard();
        }
    }

    /**
     * Internal backend for message dispatch. Does the actual works.
     * see handleMessage() for parameters
     *
     * Caller needs to hold messageDispatchGuard!
     *
     * FIXME: this method and the ChannelHandler disagree on which messages
     * should be dispatched and which shouldn't
     */
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Ignoring PacketIn (Xid = {xid}) because the data" +
                        " field is empty.",
                explanation="The switch sent an improperly-formatted PacketIn" +
                        " message",
                recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="WARN",
                message="Unhandled OF Message: {} from {}",
                explanation="The switch sent a message not handled by " +
                        "the controller")
    })
    protected void handleMessageUnprotected(IOFSwitch sw, OFMessage m,
                                 FloodlightContext bContext)
            throws IOException {
        Ethernet eth = null;

        switch (m.getType()) {
            case PACKET_IN:
                OFPacketIn pi = (OFPacketIn)m;

                if (pi.getPacketData().length <= 0) {
                    log.error("Ignoring PacketIn (Xid = " + pi.getXid() +
                              ") because the data field is empty.");
                    return;
                }

                if (Controller.ALWAYS_DECODE_ETH) {
                    eth = new Ethernet();
                    eth.deserialize(pi.getPacketData(), 0,
                            pi.getPacketData().length);
                    counterStore.updatePacketInCountersLocal(sw, m, eth);
                }
                // fall through to default case...

            default:

                List<IOFMessageListener> listeners = null;
                if (messageListeners.containsKey(m.getType())) {
                    listeners = messageListeners.get(m.getType()).
                            getOrderedListeners();
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
                    pktinProcTime.bootstrap(listeners);
                    pktinProcTime.recordStartTimePktIn();
                    Command cmd;
                    for (IOFMessageListener listener : listeners) {
                        if (listener instanceof IOFSwitchFilter) {
                            if (!((IOFSwitchFilter)listener).isInterested(sw)) {
                                continue;
                            }
                        }

                        pktinProcTime.recordStartTimeComp(listener);
                        cmd = listener.receive(sw, m, bc);
                        pktinProcTime.recordEndTimeComp(listener);

                        if (Command.STOP.equals(cmd)) {
                            break;
                        }
                    }
                    pktinProcTime.recordEndTimePktIn(sw, m, bc);
                } else {
                    if (m.getType() != OFType.BARRIER_REPLY)
                        log.warn("Unhandled OF Message: {} from {}", m, sw);
                    else
                        log.debug("Received a Barrier Reply, no listeners for it");
                }

                if ((bContext == null) && (bc != null)) flcontext_free(bc);
        }
    }




    void switchActivated(IOFSwitch sw) {
        this.switchManager.switchActivated(sw);
    }

    void switchDeactivated(IOFSwitch sw) {
        this.switchManager.switchDeactivated(sw);
    }

    void switchDisconnected(IOFSwitch sw) {
        this.switchManager.switchDisconnected(sw);
    }

    // ***************
    // IFloodlightProvider
    // ***************

    /**
     * Forward to RoleManager
     * @param h
     */
    void addSwitchChannelAndSendInitialRole(OFChannelHandler h) {
        roleManager.addOFChannelHandlerAndSendRole(h);
    }

    /**
     * Forwards to RoleManager
     * @param h
     */
    void removeSwitchChannel(OFChannelHandler h) {
        roleManager.removeOFChannelHandler(h);
    }

    /**
     * Forwards to RoleManager
     * @param h
     * @param role
     */
    void reassertRole(OFChannelHandler h, Role role) {
        roleManager.reassertRole(h, role);
    }

    // FIXME: remove this method
    @Override
    public Map<Long,IOFSwitch> getAllSwitchMap() {
        return this.switchManager.getAllSwitchMap();
    }

    @Override
    public Set<Long> getAllSwitchDpids() {
        return this.switchManager.getAllSwitchDpids();
    }

    @Override
    public IOFSwitch getSwitch(long dpid) {
        return this.switchManager.getSwitch(dpid);
    }

    @Override
    public void addOFSwitchListener(IOFSwitchListener listener) {
        this.switchListeners.add(listener);
    }

    @Override
    public void removeOFSwitchListener(IOFSwitchListener listener) {
        this.switchListeners.remove(listener);
    }

    @Override
    public synchronized void addOFMessageListener(OFType type,
                                                  IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd =
            messageListeners.get(type);
        if (ldd == null) {
            ldd = new ListenerDispatcher<OFType, IOFMessageListener>();
            messageListeners.put(type, ldd);
        }
        ldd.addListener(type, listener);
    }

    @Override
    public synchronized void removeOFMessageListener(OFType type,
                                                     IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd =
            messageListeners.get(type);
        if (ldd != null) {
            ldd.removeListener(listener);
        }
    }

    private void logListeners() {
        for (Map.Entry<OFType,
                       ListenerDispatcher<OFType,
                                          IOFMessageListener>> entry
             : messageListeners.entrySet()) {

            OFType type = entry.getKey();
            ListenerDispatcher<OFType, IOFMessageListener> ldd =
                    entry.getValue();

            StringBuffer sb = new StringBuffer();
            sb.append("OFListeners for ");
            sb.append(type);
            sb.append(": ");
            for (IOFMessageListener l : ldd.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            log.debug(sb.toString());
        }
    }

    public void removeOFMessageListeners(OFType type) {
        messageListeners.remove(type);
    }

    @Override
    public Map<OFType, List<IOFMessageListener>> getListeners() {
        Map<OFType, List<IOFMessageListener>> lers =
            new HashMap<OFType, List<IOFMessageListener>>();
        for(Entry<OFType, ListenerDispatcher<OFType, IOFMessageListener>> e :
            messageListeners.entrySet()) {
            lers.put(e.getKey(), e.getValue().getOrderedListeners());
        }
        return Collections.unmodifiableMap(lers);
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(message="Failed to inject OFMessage {message} onto " +
                "a null switch",
                explanation="Failed to process a message because the switch " +
                " is no longer connected."),
        @LogMessageDoc(level="ERROR",
                message="Error reinjecting OFMessage on switch {switch}",
                explanation="An I/O error occured while attempting to " +
                        "process an OpenFlow message",
                recommendation=LogMessageDoc.CHECK_SWITCH)
    })
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg,
                                   FloodlightContext bc) {
        if (sw == null) {
            log.info("Failed to inject OFMessage {} onto a null switch", msg);
            return false;
        }

        // FIXME: Do we need to be able to inject messages to switches
        // where we're the slave controller (i.e. they're connected but
        // not active)?
        if (sw.isActive()) return false;

        try {
            // Pass Floodlight context to the handleMessages()
            handleMessage(sw, msg, bc);
        } catch (IOException e) {
            log.error("Error reinjecting OFMessage on switch {}",
                      sw.getStringId());
            return false;
        }
        return true;
    }

    @Override
    @LogMessageDoc(message="Calling System.exit",
                   explanation="The controller is terminating")
    public synchronized void terminate() {
        log.info("Calling System.exit");
        System.exit(1);
    }

    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg) {
        // call the overloaded version with floodlight context set to null
        return injectOfMessage(sw, msg, null);
    }

    @Override
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m,
                                      FloodlightContext bc) {
        if (log.isTraceEnabled()) {
            String str = OFMessage.getDataAsString(sw, m, bc);
            log.trace("{}", str);
        }

        List<IOFMessageListener> listeners = null;
        if (messageListeners.containsKey(m.getType())) {
            listeners =
                    messageListeners.get(m.getType()).getOrderedListeners();
        }

        if (listeners != null) {
            for (IOFMessageListener listener : listeners) {
                if (listener instanceof IOFSwitchFilter) {
                    if (!((IOFSwitchFilter)listener).isInterested(sw)) {
                        continue;
                    }
                }
                if (Command.STOP.equals(listener.receive(sw, m, bc))) {
                    break;
                }
            }
        }
    }

    @Override
    public BasicFactory getOFMessageFactory() {
        return factory;
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
    @LogMessageDocs({
        @LogMessageDoc(message="Controller role set to {role}",
                explanation="Setting the initial HA role to "),
        @LogMessageDoc(level="ERROR",
                message="Invalid current role value: {role}",
                explanation="An invalid HA role value was read from the " +
                            "properties file",
                recommendation=LogMessageDoc.CHECK_CONTROLLER)
    })
    protected Role getInitialRole(Map<String, String> configParams) {
        Role role = Role.MASTER;
        String roleString = configParams.get("role");
        if (roleString == null) {
            String rolePath = configParams.get("rolepath");
            if (rolePath != null) {
                Properties properties = new Properties();
                try {
                    properties.load(new FileInputStream(rolePath));
                    roleString = properties.getProperty("floodlight.role");
                }
                catch (IOException exc) {
                    // Don't treat it as an error if the file specified by the
                    // rolepath property doesn't exist. This lets us enable the
                    // HA mechanism by just creating/setting the floodlight.role
                    // property in that file without having to modify the
                    // floodlight properties.
                }
            }
        }

        if (roleString != null) {
            // Canonicalize the string to the form used for the enum constants
            roleString = roleString.trim().toUpperCase();
            try {
                role = Role.valueOf(roleString);
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
    @LogMessageDocs({
        @LogMessageDoc(message="Listening for switch connections on {address}",
                explanation="The controller is ready and listening for new" +
                        " switch connections"),
        @LogMessageDoc(message="Storage exception in controller " +
                        "updates loop; terminating process",
                explanation=ERROR_DATABASE,
                recommendation=LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Exception in controller updates loop",
                explanation="Failed to dispatch controller event",
                recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    public void run() {
        if (log.isDebugEnabled()) {
            logListeners();
        }

        try {
           final ServerBootstrap bootstrap = createServerBootStrap();

            bootstrap.setOption("reuseAddr", true);
            bootstrap.setOption("child.keepAlive", true);
            bootstrap.setOption("child.tcpNoDelay", true);
            bootstrap.setOption("child.sendBufferSize", Controller.SEND_BUFFER_SIZE);

            ChannelPipelineFactory pfact =
                    new OpenflowPipelineFactory(this, null);
            bootstrap.setPipelineFactory(pfact);
            InetSocketAddress sa = new InetSocketAddress(openFlowPort);
            final ChannelGroup cg = new DefaultChannelGroup();
            cg.add(bootstrap.bind(sa));

            log.info("Listening for switch connections on {}", sa);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // main loop
        while (true) {
            try {
                IUpdate update = updates.take();
                update.dispatch();
            } catch (InterruptedException e) {
                log.error("Received interrupted exception in updates loop;" +
                          "terminating process");
                terminate();
            } catch (StorageException e) {
                log.error("Storage exception in controller " +
                          "updates loop; terminating process", e);
                terminate();
            } catch (Exception e) {
                log.error("Exception in controller updates loop", e);
            }
        }
    }

    private ServerBootstrap createServerBootStrap() {
        if (workerThreads == 0) {
            return new ServerBootstrap(
                    new NioServerSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));
        } else {
            return new ServerBootstrap(
                    new NioServerSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool(), workerThreads));
        }
    }

    private void setConfigParams(Map<String, String> configParams) {
        String ofPort = configParams.get("openflowport");
        if (ofPort != null) {
            this.openFlowPort = Integer.parseInt(ofPort);
        }
        log.debug("OpenFlow port set to {}", this.openFlowPort);
        String threads = configParams.get("workerthreads");
        if (threads != null) {
            this.workerThreads = Integer.parseInt(threads);
        }
        log.debug("Number of worker threads set to {}", this.workerThreads);

    }

    private void initVendorMessages() {
        // Configure openflowj to be able to parse the role request/reply
        // vendor messages.
        OFNiciraVendorExtensions.initialize();
    }

    /**
     * Initialize internal data structures
     */
    public void init(Map<String, String> configParams) {
        // These data structures are initialized here because other
        // module's startUp() might be called before ours
        this.messageListeners =
                new ConcurrentHashMap<OFType,
                                      ListenerDispatcher<OFType,
                                                         IOFMessageListener>>();
        this.switchListeners = new CopyOnWriteArraySet<IOFSwitchListener>();
        this.haListeners = new CopyOnWriteArraySet<IHAListener>();
        this.driverRegistry = new NaiiveSwitchDriverRegistry();
        this.controllerNodeIPsCache = new HashMap<String, String>();
        this.updates = new LinkedBlockingQueue<IUpdate>();
        this.factory = BasicFactory.getInstance();
        this.providerMap = new HashMap<String, List<IInfoProvider>>();
        setConfigParams(configParams);
        Role initialRole = getInitialRole(configParams);
        // FIXME: we should initialize RoleManager here but we need to wait
        // until we have the scheduled executor service. GRR.
        //this.roleManager = new RoleManager(initialRole,
        //                                   INITIAL_ROLE_CHANGE_DESCRIPTION);
        this.messageDispatchGuard =
                new MessageDispatchGuard(initialRole != Role.SLAVE);
        this.notifiedRole = initialRole;
        initVendorMessages();

        String option = configParams.get("flushSwitchesOnReconnect");

        if (option != null && option.equalsIgnoreCase("true")) {
            this.setAlwaysClearFlowsOnSwAdd(true);
            log.info("Flush switches on reconnect -- Enabled.");
        } else {
            this.setAlwaysClearFlowsOnSwAdd(false);
            log.info("Flush switches on reconnect -- Disabled");
        }
     }

    /**
     * Startup all of the controller's components
     */
    @LogMessageDoc(message="Waiting for storage source",
                explanation="The system database is not yet ready",
                recommendation="If this message persists, this indicates " +
                        "that the system database has failed to start. " +
                        LogMessageDoc.CHECK_CONTROLLER)
    public void startupComponents() throws FloodlightModuleException {
        // Create the table names we use
        storageSource.createTable(CONTROLLER_TABLE_NAME, null);
        storageSource.createTable(CONTROLLER_INTERFACE_TABLE_NAME, null);
        storageSource.createTable(SWITCH_CONFIG_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(CONTROLLER_TABLE_NAME,
                                             CONTROLLER_ID);
        storageSource.addListener(CONTROLLER_INTERFACE_TABLE_NAME, this);

        // Startup load monitoring
        if (overload_drop) {
            this.loadmonitor.startMonitoring(
                this.threadPool.getScheduledExecutor());
        }

        // Add our REST API
        restApi.addRestletRoutable(new CoreWebRoutable());

        this.ses = threadPool.getScheduledExecutor();
        this.roleManager = new RoleManager(this.notifiedRole,
                                           INITIAL_ROLE_CHANGE_DESCRIPTION);
        this.switchManager = new SwitchManager(this.notifiedRole);
        try {
            this.syncService.registerStore(SWITCH_SYNC_STORE_NAME, Scope.LOCAL);
            this.storeClient = this.syncService
                    .getStoreClient(SWITCH_SYNC_STORE_NAME,
                                    Long.class,
                                    SwitchSyncRepresentation.class);
            this.storeClient.addStoreListener(this.switchManager);
        } catch (SyncException e) {
            throw new FloodlightModuleException("Error while setting up sync service", e);
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
        this.haListeners.add(listener);
    }

    @Override
    public void removeHAListener(IHAListener listener) {
        this.haListeners.remove(listener);
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
            IResultSet res = storageSource.executeQuery(CONTROLLER_INTERFACE_TABLE_NAME,
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
                removedControllerNodeIPs.put(removedControllerID, controllerNodeIPsCache.get(removedControllerID));
            controllerNodeIPsCache.clear();
            controllerNodeIPsCache.putAll(curControllerNodeIPs);
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

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(CONTROLLER_INTERFACE_TABLE_NAME)) {
            handleControllerNodeIPChanges();
        }

    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(CONTROLLER_INTERFACE_TABLE_NAME)) {
            handleControllerNodeIPChanges();
        }
    }

    @Override
    public long getSystemStartTime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        return rb.getStartTime();
    }

    @Override
    public void setAlwaysClearFlowsOnSwAdd(boolean value) {
        this.alwaysClearFlowsOnSwAdd = value;
    }

    public boolean getAlwaysClearFlowsOnSwAdd() {
        return this.alwaysClearFlowsOnSwAdd;
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
    public void addOFSwitchDriver(String manufacturerDescriptionPrefix,
                                  IOFSwitchDriver driver) {
        driverRegistry.addSwitchDriver(manufacturerDescriptionPrefix, driver);
    }

    /**
     * Forward to the registry to get an IOFSwitch instance.
     * @param desc
     * @return
     */
    IOFSwitch getOFSwitchInstance(OFDescriptionStatistics desc) {
        return driverRegistry.getOFSwitchInstance(desc);
    }


    void flushAll() {
        // Flush all flow-mods/packet-out/stats generated from this "train"
        OFSwitchBase.flush_all();
        counterStore.updateFlush();
        bigFlowCacheMgr.updateFlush();
        debugCounter.flushCounters();
    }

    /**
     * FOR TESTING ONLY.
     * Dispatch all updates in the update queue until queue is empty
     */
    void processUpdateQueueForTesting() {
        while(!updates.isEmpty()) {
            IUpdate update = updates.poll();
            if (update != null)
                update.dispatch();
        }
    }

    @LogMessageDoc(level="WARN",
            message="Failure adding update {} to queue",
            explanation="The controller tried to add an internal notification" +
                        " to its message queue but the add failed.",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private void addUpdateToQueue(IUpdate update) {
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            // This should never happen
            log.error("Failure adding update {} to queue.", update);
        }
    }

}
