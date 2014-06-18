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
import java.util.Collection;
import java.util.Arrays;
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

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeEvent;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IReadyForReconcileListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.OFSwitchBase;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.SwitchSyncRepresentation;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterException;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterType;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.debugevent.IEventUpdater;
import net.floodlightcontroller.debugevent.NullDebugEvent;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import net.floodlightcontroller.debugevent.IDebugEventService.MaxEventsRegistered;
import net.floodlightcontroller.notification.INotificationManager;
import net.floodlightcontroller.notification.NotificationManagerFactory;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.LoadMonitor;
import net.floodlightcontroller.util.TimedCache;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorExtensions;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bigswitch.floodlight.vendor.OFVendorActions;



/**
 * The main controller class.  Handles all setup and network listeners
 */
public class Controller implements IFloodlightProviderService,
            IStorageSourceListener, IInfoProvider {

    protected static final Logger log = LoggerFactory.getLogger(Controller.class);
    protected static final INotificationManager notifier =
            NotificationManagerFactory.getNotificationManager(Controller.class);

    static final String ERROR_DATABASE =
            "The controller could not communicate with the system database.";
    static final String SWITCH_SYNC_STORE_NAME =
            Controller.class.getCanonicalName() + ".stateStore";

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
    protected ListenerDispatcher<HAListenerTypeMarker,IHAListener> haListeners;
    protected Set<IReadyForReconcileListener> readyForReconcileListeners;
    protected Map<String, List<IInfoProvider>> providerMap;
    protected BlockingQueue<IUpdate> updates;

    // Module dependencies
    private IRestApiService restApi;
    private ICounterStoreService counterStore = null;
    private IDebugCounterService debugCounters;
    protected IDebugEventService debugEvents;
    private IStorageSourceService storageSource;
    private IPktInProcessingTimeService pktinProcTime;
    private IThreadPoolService threadPool;
    private ScheduledExecutorService ses;
    private ISyncService syncService;
    private IStoreClient<Long, SwitchSyncRepresentation> storeClient;

    // Configuration options
    protected String openFlowHost = null;
    protected int openFlowPort = 6633;
    protected int workerThreads = 0;


    // This controller's current role that modules can use/query to decide
    // if they should operate in master or slave mode.
    // TODO: potentially we need to get rid of this field and modules must
    // then rely on the role notifications alone...
    protected volatile Role notifiedRole;

    private static final String
            INITIAL_ROLE_CHANGE_DESCRIPTION = "Controller startup.";
    private RoleManager roleManager;
    private SwitchManager switchManager;

    private static final int DEFAULT_CONSOLIDATE_STORE_TIME_DELAY_MS =
            15*1000; // 15s
    private int consolidateStoreTimeDelayMs =
            DEFAULT_CONSOLIDATE_STORE_TIME_DELAY_MS;


    // Flag to always flush flow table on switch reconnect (HA or otherwise)
    private boolean alwaysClearFlowsOnSwActivate = false;
    private TimedCache<Long> swConnectCache;

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

    // FIXME: don't use "forwardingconfig" as table name
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

    private static final short DEFAULT_ACCESS_PRIORITY = 10;
    private static final short DEFAULT_CORE_PRIORITY = 1000;
    private short accessPriority = DEFAULT_ACCESS_PRIORITY;
    private short corePriority = DEFAULT_CORE_PRIORITY;


    // Perf. related configuration
    protected static final int SEND_BUFFER_SIZE = 128 * 1024;
    public static final int BATCH_MAX_SIZE = 100;
    protected static final boolean ALWAYS_DECODE_ETH = true;

    // Set of port name prefixes that will be classified as uplink ports,
    // hence will not be autoportfast.
    Set<String> uplinkPortPrefixSet;

    @Override
    public Set<String> getUplinkPortPrefixSet() {
        return uplinkPortPrefixSet;
    }

    public void setUplinkPortPrefixSet(Set<String> prefixSet) {
        this.uplinkPortPrefixSet = prefixSet;
    }

    // Event IDs for debug events
    protected IEventUpdater<SwitchEvent> evSwitch;

    // Load monitor for overload protection
    protected final boolean overload_drop =
        Boolean.parseBoolean(System.getProperty("overload_drop", "false"));
    protected final LoadMonitor loadmonitor = new LoadMonitor(log);

    private class NotificationSwitchListener implements IOFSwitchListener {

        @Override
        public void switchAdded(long switchId) {
            notifier.postNotification("Switch " + HexString.toHexString(switchId) + " connected.");
        }

        @Override
        public void switchRemoved(long switchId) {
            notifier.postNotification("Switch " + HexString.toHexString(switchId) + " disconnected.");
        }

        @Override
        public void switchActivated(long switchId) {
        }

        @Override
        public void switchPortChanged(long switchId, ImmutablePort port,
                                      PortChangeType type) {
            String msg = String.format("Switch %s port %s changed: %s",
                                       HexString.toHexString(switchId),
                                       port.getName(),
                                       type.toString());
            notifier.postNotification(msg);
        }

        @Override
        public void switchChanged(long switchId) {
        }
    }
    public static class Counters {
        public static final String prefix = Controller.class.getPackage().getName();
        public IDebugCounter setRoleEqual;
        public IDebugCounter setSameRole;
        public IDebugCounter setRoleMaster;
        public IDebugCounter remoteStoreNotification;
        public IDebugCounter invalidPortsChanged;
        public IDebugCounter invalidSwitchActivatedWhileSlave;
        public IDebugCounter invalidStoreEventWhileMaster;
        public IDebugCounter switchDisconnectedWhileSlave;
        public IDebugCounter switchActivated;
        public IDebugCounter errorSameSwitchReactivated; // err
        public IDebugCounter switchWithSameDpidActivated; // warn
        public IDebugCounter newSwitchActivated;   // new switch
        public IDebugCounter syncedSwitchActivated;
        public IDebugCounter readyForReconcile;
        public IDebugCounter newSwitchFromStore;
        public IDebugCounter updatedSwitchFromStore;
        public IDebugCounter switchDisconnected;
        public IDebugCounter syncedSwitchRemoved;
        public IDebugCounter unknownSwitchRemovedFromStore;
        public IDebugCounter consolidateStoreRunCount;
        public IDebugCounter consolidateStoreInconsistencies;
        public IDebugCounter storeSyncError;
        public IDebugCounter switchesNotReconnectingToNewMaster;
        public IDebugCounter switchPortChanged;
        public IDebugCounter switchOtherChange;
        public IDebugCounter dispatchMessageWhileSlave;
        public IDebugCounter dispatchMessage;  // does this cnt make sense? more specific?? per type? count stops?
        public IDebugCounter controllerNodeIpsChanged;
        public IDebugCounter messageReceived;
        public IDebugCounter messageInputThrottled;
        public IDebugCounter switchDisconnectReadTimeout;
        public IDebugCounter switchDisconnectHandshakeTimeout;
        public IDebugCounter switchDisconnectIOError;
        public IDebugCounter switchDisconnectParseError;
        public IDebugCounter switchDisconnectSwitchStateException;
        public IDebugCounter rejectedExecutionException;
        public IDebugCounter switchDisconnectOtherException;
        public IDebugCounter switchConnected;
        public IDebugCounter unhandledMessage;
        public IDebugCounter packetInWhileSwitchIsSlave;
        public IDebugCounter epermErrorWhileSwitchIsMaster;
        public IDebugCounter roleNotResentBecauseRolePending;
        public IDebugCounter roleRequestSent;
        public IDebugCounter roleReplyTimeout;
        public IDebugCounter roleReplyReceived; // expected RoleReply received
        public IDebugCounter roleReplyErrorUnsupported;
        public IDebugCounter switchCounterRegistrationFailed;

        void createCounters(IDebugCounterService debugCounters) throws CounterException {
            setRoleEqual =
                debugCounters.registerCounter(
                            prefix, "set-role-equal",
                            "Controller received a role request with role of "+
                            "EQUAL which is unusual",
                            CounterType.ALWAYS_COUNT);
            setSameRole =
                debugCounters.registerCounter(
                            prefix, "set-same-role",
                            "Controller received a role request for the same " +
                            "role the controller already had",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            setRoleMaster =
                debugCounters.registerCounter(
                            prefix, "set-role-master",
                            "Controller received a role request with role of " +
                            "MASTER. This counter can be at most 1.",
                            CounterType.ALWAYS_COUNT);

            remoteStoreNotification =
                debugCounters.registerCounter(
                            prefix, "remote-store-notification",
                            "Received a notification from the sync service " +
                            "indicating that switch information has changed",
                            CounterType.ALWAYS_COUNT);

            invalidPortsChanged =
                debugCounters.registerCounter(
                            prefix, "invalid-ports-changed",
                            "Received an unexpected ports changed " +
                            "notification while the controller was in " +
                            "SLAVE role.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            invalidSwitchActivatedWhileSlave =
                debugCounters.registerCounter(
                            prefix, "invalid-switch-activated-while-slave",
                            "Received an unexpected switchActivated " +
                            "notification while the controller was in " +
                            "SLAVE role.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            invalidStoreEventWhileMaster =
                debugCounters.registerCounter(
                            prefix, "invalid-store-event-while-master",
                            "Received an unexpected notification from " +
                            "the sync store while the controller was in " +
                            "MASTER role.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            switchDisconnectedWhileSlave =
                debugCounters.registerCounter(
                            prefix, "switch-disconnected-while-slave",
                            "A switch disconnected and the controller was " +
                            "in SLAVE role.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            switchActivated =
                debugCounters.registerCounter(
                            prefix, "switch-activated",
                            "A switch connected to this controller is now " +
                            "in MASTER role",
                            CounterType.ALWAYS_COUNT);

            errorSameSwitchReactivated = // err
                debugCounters.registerCounter(
                            prefix, "error-same-switch-reactivated",
                            "A switch that was already in active state " +
                            "was activated again. This indicates a " +
                            "controller defect",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);

            switchWithSameDpidActivated = // warn
                debugCounters.registerCounter(
                            prefix, "switch-with-same-dpid-activated",
                            "A switch with the same DPID as another switch " +
                            "connected to the controller. This can be " +
                            "caused by multiple switches configured with " +
                            "the same DPID or by a switch reconnecting very " +
                            "quickly.",
                            CounterType.COUNT_ON_DEMAND,
                            IDebugCounterService.CTR_MDATA_WARN);

            newSwitchActivated =   // new switch
                debugCounters.registerCounter(
                            prefix, "new-switch-activated",
                            "A new switch has completed the handshake as " +
                            "MASTER. The switch was not known to any other " +
                            "controller in the cluster",
                            CounterType.ALWAYS_COUNT);
            syncedSwitchActivated =
                debugCounters.registerCounter(
                            prefix, "synced-switch-activated",
                            "A switch has completed the handshake as " +
                            "MASTER. The switch was known to another " +
                            "controller in the cluster",
                            CounterType.ALWAYS_COUNT);

            readyForReconcile =
                debugCounters.registerCounter(
                            prefix, "ready-for-reconcile",
                            "Controller is ready for flow reconciliation " +
                            "after Slave to Master transition. Either all " +
                            "previously known switches are now active " +
                            "or they have timed out and have been removed." +
                            "This counter will be 0 or 1.",
                            CounterType.ALWAYS_COUNT);

            newSwitchFromStore =
                debugCounters.registerCounter(
                            prefix, "new-switch-from-store",
                            "A new switch has connected to another " +
                            "another controller in the cluster. This " +
                            "controller instance has received a sync store " +
                            "notification for it.",
                            CounterType.ALWAYS_COUNT);

            updatedSwitchFromStore =
                debugCounters.registerCounter(
                            prefix, "updated-switch-from-store",
                            "Information about a switch connected to " +
                            "another controller instance was updated in " +
                            "the sync store. This controller instance has " +
                            "received a notification for it",
                            CounterType.ALWAYS_COUNT);

            switchDisconnected =
                debugCounters.registerCounter(
                            prefix, "switch-disconnected",
                            "FIXME: switch has disconnected",
                            CounterType.ALWAYS_COUNT);

            syncedSwitchRemoved =
                debugCounters.registerCounter(
                            prefix, "synced-switch-removed",
                            "A switch connected to another controller " +
                            "instance has disconnected from the controller " +
                            "cluster. This controller instance has " +
                            "received a notification for it",
                            CounterType.ALWAYS_COUNT);

            unknownSwitchRemovedFromStore =
                debugCounters.registerCounter(
                            prefix, "unknown-switch-removed-from-store",
                            "This controller instances has received a sync " +
                            "store notification that a switch has " +
                            "disconnected but this controller instance " +
                            "did not have the any information about the " +
                            "switch", // might be less than warning
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            consolidateStoreRunCount =
                debugCounters.registerCounter(
                            prefix, "consolidate-store-run-count",
                            "This controller has transitioned from SLAVE " +
                            "to MASTER and waited for switches to reconnect. " +
                            "The controller has finished waiting and has " +
                            "reconciled switch entries in the sync store " +
                            "with live state",
                            CounterType.ALWAYS_COUNT);

            consolidateStoreInconsistencies =
                    debugCounters.registerCounter(
                                prefix, "consolidate-store-inconsistencies",
                                "During switch sync store consolidation: " +
                                "Number of switches that were in the store " +
                                "but not otherwise known plus number of " +
                                "switches that were in the store previously " +
                                "but are now missing plus number of "  +
                                "connected switches that were absent from " +
                                "the store although this controller has " +
                                "written them. A non-zero count " +
                                "indicates a brief split-brain dual MASTER " +
                                "situation during fail-over",
                                CounterType.ALWAYS_COUNT);

            storeSyncError =
                debugCounters.registerCounter(
                            prefix, "store-sync-error",
                            "Number of times a sync store operation failed " +
                            "due to a store sync exception or an entry in " +
                            "in the store had invalid data.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);

            switchesNotReconnectingToNewMaster =
                debugCounters.registerCounter(
                            prefix, "switches-not-reconnecting-to-new-master",
                            "Switches that were connected to another " +
                            "controller instance in the cluster but that " +
                            "did not reconnect to this controller after it " +
                            "transitioned to MASTER", // might be less than warning
                            CounterType.ALWAYS_COUNT);

            switchPortChanged =
                debugCounters.registerCounter(
                            prefix, "switch-port-changed",
                            "Number of times switch ports have changed",
                            CounterType.ALWAYS_COUNT);
            switchOtherChange =
                debugCounters.registerCounter(
                            prefix, "switch-other-change",
                            "Number of times other information of a switch " +
                            "has changed.",
                            CounterType.ALWAYS_COUNT);

            dispatchMessageWhileSlave =
                debugCounters.registerCounter(
                            prefix, "dispatch-message-while-slave",
                            "Number of times an OF message was received " +
                            "and supposed to be dispatched but the " +
                            "controller was in SLAVE role and the message " +
                            "was not dispatched",
                            CounterType.ALWAYS_COUNT);

            dispatchMessage =  // does this cnt make sense? more specific?? per type? count stops?
                debugCounters.registerCounter(
                            prefix, "dispatch-message",
                            "Number of times an OF message was dispatched " +
                            "to registered modules",
                            CounterType.ALWAYS_COUNT);

            controllerNodeIpsChanged =
                debugCounters.registerCounter(
                            prefix, "controller-nodes-ips-changed",
                            "IP addresses of controller nodes have changed",
                            CounterType.ALWAYS_COUNT);

        //------------------------
        // channel handler counters. Factor them out ??
            messageReceived =
                debugCounters.registerCounter(
                            prefix, "message-received",
                            "Number of OpenFlow messages received. Some of " +
                            "these might be throttled",
                            CounterType.ALWAYS_COUNT);
            messageInputThrottled =
                debugCounters.registerCounter(
                            prefix, "message-input-throttled",
                            "Number of OpenFlow messages that were " +
                            "throttled due to high load from the sender",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);
        // TODO: more counters in messageReceived ??

            switchDisconnectReadTimeout =
                debugCounters.registerCounter(
                            prefix, "switch-disconnect-read-timeout",
                            "Number of times a switch was disconnected due " +
                            "due the switch failing to send OpenFlow " +
                            "messages or responding to OpenFlow ECHOs",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);
            switchDisconnectHandshakeTimeout =
                debugCounters.registerCounter(
                            prefix, "switch-disconnect-handshake-timeout",
                            "Number of times a switch was disconnected " +
                            "because it failed to complete the handshake " +
                            "in time.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);
            switchDisconnectIOError =
                debugCounters.registerCounter(
                            prefix, "switch-disconnect-io-error",
                            "Number of times a switch was disconnected " +
                            "due to IO errors on the switch connection.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);
            switchDisconnectParseError =
                debugCounters.registerCounter(
                            prefix, "switch-disconnect-parse-error",
                           "Number of times a switch was disconnected " +
                           "because it sent an invalid packet that could " +
                           "not be parsed",
                           CounterType.ALWAYS_COUNT,
                           IDebugCounterService.CTR_MDATA_ERROR);

            switchDisconnectSwitchStateException =
                debugCounters.registerCounter(
                            prefix, "switch-disconnect-switch-state-exception",
                            "Number of times a switch was disconnected " +
                            "because it sent messages that were invalid " +
                            "given the switch connection's state.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);
            rejectedExecutionException =
                debugCounters.registerCounter(
                            prefix, "rejected-execution-exception",
                            "TODO",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);

            switchDisconnectOtherException =
                debugCounters.registerCounter(
                            prefix,  "switch-disconnect-other-exception",
                            "Number of times a switch was disconnected " +
                            "due to an exceptional situation not covered " +
                            "by other counters",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_ERROR);

            switchConnected =
                debugCounters.registerCounter(
                            prefix, "switch-connected",
                            "Number of times a new switch connection was " +
                            "established",
                            CounterType.ALWAYS_COUNT);

            unhandledMessage =
                debugCounters.registerCounter(
                            prefix, "unhandled-message",
                            "Number of times an OpenFlow message was " +
                            "received that the controller ignored because " +
                            "it was inapproriate given the switch " +
                            "connection's state.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);
                            // might be less than warning

            packetInWhileSwitchIsSlave =
                debugCounters.registerCounter(
                            prefix, "packet-in-while-switch-is-slave",
                            "Number of times a packet in was received " +
                            "from a switch that was in SLAVE role. " +
                            "Possibly inidicates inconsistent roles.",
                            CounterType.ALWAYS_COUNT);
            epermErrorWhileSwitchIsMaster =
                debugCounters.registerCounter(
                            prefix, "eperm-error-while-switch-is-master",
                            "Number of times a permission error was " +
                            "received while the switch was in MASTER role. " +
                            "Possibly inidicates inconsistent roles.",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            roleNotResentBecauseRolePending =
                debugCounters.registerCounter(
                            prefix, "role-not-resent-because-role-pending",
                            "The controller tried to reestablish a role " +
                            "with a switch but did not do so because a " +
                            "previous role request was still pending",
                            CounterType.ALWAYS_COUNT);
            roleRequestSent =
                debugCounters.registerCounter(
                            prefix, "role-request-sent",
                            "Number of times the controller sent a role " +
                            "request to a switch.",
                            CounterType.ALWAYS_COUNT);
            roleReplyTimeout =
                debugCounters.registerCounter(
                            prefix, "role-reply-timeout",
                            "Number of times a role request message did not " +
                            "receive the expected reply from a switch",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);

            roleReplyReceived = // expected RoleReply received
                debugCounters.registerCounter(
                            prefix, "role-reply-received",
                            "Number of times the controller received the " +
                            "expected role reply message from a switch",
                            CounterType.ALWAYS_COUNT);

            roleReplyErrorUnsupported =
                debugCounters.registerCounter(
                            prefix, "role-reply-error-unsupported",
                            "Number of times the controller received an " +
                            "error from a switch in response to a role " +
                            "request indicating that the switch does not " +
                            "support roles.",
                            CounterType.ALWAYS_COUNT);

            switchCounterRegistrationFailed =
                debugCounters.registerCounter(prefix,
                            "switch-counter-registration-failed",
                            "Number of times the controller failed to " +
                            "register per-switch debug counters",
                            CounterType.ALWAYS_COUNT,
                            IDebugCounterService.CTR_MDATA_WARN);
        }
    }

    private Counters counters;

    Counters getCounters() {
        return this.counters;
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
     * This class is fully thread safe. Its method can safely be called from
     * any thread.
     *
     * @author gregor
     *
     */
    private class RoleManager {
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
         * @param role initial role
         * @param roleChangeDescription initial value of the change description
         * @throws NullPointerException if role or roleChangeDescription is null
         * @throws IllegalArgumentException if role is EQUAL
         */
        public RoleManager(Role role, String roleChangeDescription) {
            if (role == null)
                throw new NullPointerException("role must not be null");
            if (role == Role.EQUAL)
                throw new IllegalArgumentException("role must not be EQUAL");
            if (roleChangeDescription == null) {
                throw new NullPointerException("roleChangeDescription must " +
                                               "not be null");
            }

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
            if (role == Role.EQUAL) {
                counters.setRoleEqual.updateCounterWithFlush();
                log.debug("Received role request for EQUAL, setting to MASTER"
                          + " instead");
                role = Role.MASTER;
            }
            if (role == this.role) {
                counters.setSameRole.updateCounterWithFlush();
                log.debug("Received role request for {} but controller is "
                        + "already {}. Ignoring it.", role, this.role);
                return;
            }
            if (this.role == Role.MASTER && role == Role.SLAVE) {
                log.info("Received role request to transition from MASTER to "
                          + " SLAVE (reason: {}). Terminating floodlight.",
                          roleChangeDescription);
                System.exit(0);
            }

            // At this point we are guaranteed that we will execute the code
            // below exactly once during the lifetime of this process! And
            // it will be a to MASTER transition
            counters.setRoleMaster.updateCounterWithFlush();
            log.info("Received role request for {} (reason: {})."
                     + " Initiating transition", role, roleChangeDescription);

            this.role = role;
            this.roleChangeDescription = roleChangeDescription;

            // TODO: we currently notify switches synchronously from the REST
            // API handler. We could (should?) do this asynchronously.
            currentRoleInfo = new RoleInfo(this.role,
                                           this.roleChangeDescription,
                                           new Date());
            Controller.this.switchManager.setRole(this.role);
            for (OFChannelHandler h: connectedChannelHandlers)
                h.sendRoleRequest(this.role);

            Controller.this.addUpdateToQueue(new HARoleUpdate(this.role));
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


    /**
     * This is a utility class to encapsulate code that deals with switch
     * life cycles. It interacts with the sync store to read/write switches
     * to/from the store and it maintains the switch maps.
     * @author gregor
     *
     */
    private class SwitchManager implements IStoreListener<Long> {
        private Role role;
        private final ConcurrentHashMap<Long,IOFSwitch> activeSwitches;
        private final ConcurrentHashMap<Long,IOFSwitch> syncedSwitches;

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
            counters.remoteStoreNotification.updateCounterWithFlush();
            while(keys.hasNext()) {
                Long key = keys.next();
                Versioned<SwitchSyncRepresentation> versionedSwitch = null;
                try {
                    versionedSwitch = storeClient.get(key);
                } catch (SyncException e) {
                    counters.storeSyncError.updateCounterWithFlush();
                    log.error("Exception while retrieving switch " +
                              HexString.toHexString(key) +
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
                SwitchSyncRepresentation storedSwitch =
                        versionedSwitch.getValue();
                IOFSwitch sw = getOFSwitchInstance(storedSwitch.getDescription());
                sw.setFeaturesReply(storedSwitch.getFeaturesReply());
                if (!key.equals(storedSwitch.getFeaturesReply().getDatapathId())) {
                    counters.storeSyncError.updateCounterWithFlush();
                    log.error("Inconsistent DPIDs from switch sync store: " +
                              "key is {} but sw.getId() says {}. Ignoring",
                              HexString.toHexString(key), sw.getStringId());
                    continue;
                }
                switchAddedToStore(sw);
            }
        }


        public synchronized void setRole(Role role) {
            this.role = role;
            Runnable consolidateStoreTask = new Runnable() {
                @Override
                public void run() {
                    consolidateStore();
                }
            };
            if ((role == Role.MASTER) &&
                    this.syncedSwitches.isEmpty())
                addUpdateToQueue(new ReadyForReconcileUpdate());

            Controller.this.ses.schedule(consolidateStoreTask,
                                         consolidateStoreTimeDelayMs,
                                         TimeUnit.MILLISECONDS);
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
        /**
         * Called when a switch is activated, i.e., when it enters master
         * role relative to this controller.
         * @param sw
         */
        public synchronized void switchActivated(IOFSwitch sw) {
            if (role != Role.MASTER) {
                counters.invalidSwitchActivatedWhileSlave.updateCounterWithFlush();
                return; // only react to switch connections when master
                // FIXME: should we disconnect the switch? When can this happen?
            }
            Long dpid = sw.getId();
            counters.switchActivated.updateCounterWithFlush();
            IOFSwitch oldSw = this.activeSwitches.put(dpid, sw);
            // Update event history
            evSwitch.updateEventWithFlush(new SwitchEvent(dpid, "connected"));

            if (oldSw == sw)  {
                // Note == for object equality, not .equals for value
                // TODO: should we wipe the flow table if
                // alwaysClearFlowsOnSwAdd is set? OTOH this case should
                // really never happen.
                counters.errorSameSwitchReactivated.updateCounterWithFlush();
                log.error("Switch {} activated but was already active", sw);
                addSwitchToStore(sw);
                return;
            }

            if (oldSw != null) {
                // This happens either when we have switches with duplicate
                // DPIDs or when a switch reconnects before we saw the
                // disconnect
                counters.switchWithSameDpidActivated.updateCounterWithFlush();
                log.warn("New switch added {} for already-added switch {}",
                          sw, oldSw);
                // We need to disconnect and remove the old switch
                // TODO: we notify switch listeners that the switch has been
                // removed and then we notify them that the new one has been
                // added. One could argue that a switchChanged notification
                // might be more appropriate in this case....
                oldSw.cancelAllStatisticsReplies();
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.REMOVED));
                oldSw.disconnectOutputStream();
                // Add the new switch and clear FlowMods
                // TODO: if this is the same switch re-connecting rather than
                // a DPID collision it would make sense to not wipe the flow
                // table.
                sw.clearAllFlowMods();
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.ADDED));
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.ACTIVATED));
                addSwitchToStore(sw);
                return;
            }

            IOFSwitch storedSwitch = this.syncedSwitches.remove(sw.getId());
            if (storedSwitch == null) {
                // The switch isn't known to the controller cluster. We
                // need to send a switchAdded notification and clear all
                // flows.
                if (!swConnectCache.update(sw.getId()))
                    sw.clearAllFlowMods();
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.ADDED));
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.ACTIVATED));
                counters.newSwitchActivated.updateCounterWithFlush();
            } else {
                // FIXME: switch was in store. check if ports or anything else
                // has changed and send update.
                if (alwaysClearFlowsOnSwActivate) {
                    sw.clearAllFlowMods();
                }
                if (sw.attributeEquals(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true)) {
                    // We have a stored switch and the newly activated switch
                    // supports roles. This indicates that the switch was
                    // previously connected as slave. Since we don't update
                    // ports while slave, we need to set the ports on the
                    // new switch from the ports on the stored switch
                    // No need to send notifications, since we've dispatched
                    // them as we receive them from the store
                    sw.setPorts(storedSwitch.getPorts());
                }
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.ACTIVATED));
                sendNotificationsIfSwitchDiffers(storedSwitch, sw);
                counters.syncedSwitchActivated.updateCounterWithFlush();
                if (this.syncedSwitches.isEmpty()) {
                    // we have just activated the last synced switch. I.e.,
                    // all previously known switch are now active. Send
                    // notification
                    // update dispatcher will increment counter
                    addUpdateToQueue(new ReadyForReconcileUpdate());
                }
            }
            addSwitchToStore(sw);
        }

        /**
         * Called when ports on the given switch have changed. Writes the
         * updated switch to the sync store and queues a switch notification
         * to listeners
         * @param sw
         */
        public synchronized void switchPortsChanged(IOFSwitch sw,
                                                    ImmutablePort port,
                                                    PortChangeType type) {
            if (role != Role.MASTER) {
                counters.invalidPortsChanged.updateCounterWithFlush();
                return;
            }
            if (!this.activeSwitches.containsKey(sw.getId())) {
                counters.invalidPortsChanged.updateCounterWithFlush();
                return;
            }
            // update switch in store
            addSwitchToStore(sw);
            // no need to count here. SwitchUpdate.dispatch will count
            // the portchanged
            SwitchUpdate update = new SwitchUpdate(sw.getId(),
                                                   SwitchUpdateType.PORTCHANGED,
                                                   port, type);
            addUpdateToQueue(update);
        }

        /**
         * Called when we receive a store notification about a new or updated
         * switch.
         * @param sw
         */
        private synchronized void switchAddedToStore(IOFSwitch sw) {
            if (role != Role.SLAVE) {
                counters.invalidStoreEventWhileMaster.updateCounterWithFlush();
                return; // only read from store if slave
            }
            Long dpid = sw.getId();

            IOFSwitch oldSw = syncedSwitches.put(dpid, sw);
            if (oldSw == null)  {
                counters.newSwitchFromStore.updateCounterWithFlush();
                addUpdateToQueue(new SwitchUpdate(dpid, SwitchUpdateType.ADDED));
            } else {
                // The switch already exists in storage, see if anything
                // has changed
                sendNotificationsIfSwitchDiffers(oldSw, sw);
                counters.updatedSwitchFromStore.updateCounterWithFlush();
            }
        }

        /**
         * Called when we receive a store notification about a switch that
         * has been removed from the sync store
         * @param dpid
         */
        private synchronized void switchRemovedFromStore(long dpid) {
            if (role != Role.SLAVE) {
                counters.invalidStoreEventWhileMaster.updateCounterWithFlush();
                return; // only read from store if slave
            }
            IOFSwitch oldSw = syncedSwitches.remove(dpid);
            if (oldSw != null) {
                counters.syncedSwitchRemoved.updateCounterWithFlush();
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.REMOVED));
            } else {
                // TODO: the switch was deleted (tombstone) before we ever
                // knew about it (or was deleted repeatedly). Can this
                // happen? When/how?
                counters.unknownSwitchRemovedFromStore.updateCounterWithFlush();
            }
        }

        public synchronized void switchDeactivated(IOFSwitch sw) {
            // ignore. we don't handle MASTER -> SLAVE transitions. We
            // expect a restart
        }

        /**
         * Called when a switch disconnects
         * @param sw
         */
        public synchronized void switchDisconnected(IOFSwitch sw) {
            if (role == Role.SLAVE) {
                counters.switchDisconnectedWhileSlave.updateCounterWithFlush();
                return; // only react to switch connections when master
            }
            long dpid = sw.getId();
            // Update event history
            // TODO: this is asymmetric with respect to connect event
            //       in switchActivated(). Should we have events on the
            //       slave as well?
            evSwitch.updateEventWithFlush(new SwitchEvent(dpid, "disconnected"));
            counters.switchDisconnected.updateCounterWithFlush();
            IOFSwitch oldSw = this.activeSwitches.get(dpid);
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
            swConnectCache.update(sw.getId());
            this.activeSwitches.remove(sw.getId());
            removeSwitchFromStore(sw.getId());
            // We cancel all outstanding statistics replies if the switch transition
            // from active. In the future we might allow statistics requests
            // from slave controllers. Then we need to move this cancelation
            // to switch disconnect
            sw.cancelAllStatisticsReplies();
            addUpdateToQueue(new SwitchUpdate(sw.getId(),
                                              SwitchUpdateType.REMOVED));
        }

        /**
         * Write the given switch to the sync store.
         * @param sw
         */
        private synchronized void addSwitchToStore(IOFSwitch sw) {
            // Add to store
            // FIXME: do we need to use a put that takes a versioned here?
            // need to verify
            try {
                storeClient.put(sw.getId(), new SwitchSyncRepresentation(sw));
            } catch (ObsoleteVersionException e) {
                // FIXME: what's the right behavior here. Can the store client
                // even throw this error? Should not since all local store
                // access is synchronized
            } catch (SyncException e) {
                counters.storeSyncError.updateCounterWithFlush();
                log.error("Could not write switch " + sw.getStringId() +
                          " to sync store:", e);
            }
        }

        /**
         * Write the given switch to the sync store if it's not already
         * there
         * TODO: should this be merged with addSwitchToStore
         * @param sw
         * @return true if the switch was absent, false otherwise
         */
        private synchronized boolean addSwitchToStoreIfAbsent(IOFSwitch sw) {
            try {
                Versioned<SwitchSyncRepresentation> versionedSSr =
                        storeClient.get(sw.getId());
                if (versionedSSr.getValue() == null) {
                    // switch is absent
                    versionedSSr.setValue(new SwitchSyncRepresentation(sw));
                    storeClient.put(sw.getId(), versionedSSr);
                    return true;
                } else {
                    return false;
                }
            } catch (ObsoleteVersionException e) {
                // FIXME: what's the right behavior here. Can the store client
                // even throw this error? Should not since all local store
                // access is synchronized
            } catch (SyncException e) {
                counters.storeSyncError.updateCounterWithFlush();
                log.error("Could not write switch " + sw.getStringId() +
                          " to sync store:", e);
            }
            return false;
        }

        /**
         * Remove the given switch from the sync store.
         * @param dpid
         */
        private synchronized void removeSwitchFromStore(long dpid) {
            try {
                storeClient.delete(dpid);
            } catch (SyncException e) {
                counters.storeSyncError.updateCounterWithFlush();
                // ObsoleteVerisonException can't happend because all
                // store modifications are synchronized
                log.error("Could not remove switch " +
                          HexString.toHexString(dpid) +
                          " from sync store:", e);
            }
        }

        /**
         * Check if the two switches differ in their ports or in other
         * fields and if they differ enqueue a switch update
         * @param oldSw
         * @param newSw
         */
        private synchronized void
                sendNotificationsIfSwitchDiffers(IOFSwitch oldSw,
                                                 IOFSwitch newSw) {
            Collection<PortChangeEvent> portDiffs =
                    oldSw.comparePorts(newSw.getPorts());
            for (PortChangeEvent ev: portDiffs) {
                SwitchUpdate update =
                        new SwitchUpdate(newSw.getId(),
                                         SwitchUpdateType.PORTCHANGED,
                                         ev.port, ev.type);
                addUpdateToQueue(update);
            }
        }
        /**
         * Remove all entries from the store that don't correspond to an
         * active switch.
         * TODO: is it a problem that this is fully synchronized
         */
        private synchronized void consolidateStore() {
            if (role == Role.SLAVE)
                return;
            boolean shouldNotifyReadyForReconcile = false;
            counters.consolidateStoreRunCount.updateCounterWithFlush();
            log.info("Consolidating synced switches after MASTER transition");
            IClosableIterator<Map.Entry<Long,Versioned<SwitchSyncRepresentation>>>
                    iter = null;
            try {
                iter = storeClient.entries();
            } catch (SyncException e) {
                counters.storeSyncError.updateCounterWithFlush();
                log.error("Failed to read switches from sync store", e);
                return;
            }
            try {
                while(iter.hasNext()) {
                    Entry<Long, Versioned<SwitchSyncRepresentation>> entry =
                            iter.next();
                    if (!this.activeSwitches.containsKey(entry.getKey())) {
                        removeSwitchFromStore(entry.getKey());
                        if (this.syncedSwitches.remove(entry.getKey()) != null) {
                            // a switch that's in the store and in synced
                            // switches but that is not active. I.e., a
                            // switch known to the old master that hasn't
                            // reconnected to this controller.
                            counters.switchesNotReconnectingToNewMaster
                                    .updateCounterWithFlush();
                            shouldNotifyReadyForReconcile = true;
                            addUpdateToQueue(new SwitchUpdate(entry.getKey(),
                                                     SwitchUpdateType.REMOVED));
                        } else {
                            // A switch was in the store but it's neither in
                            // activeSwitches nor syncedSwitches. This could
                            // happen if the old Master has added this entry
                            // to the store after this controller has
                            // stopped reacting to store notifications (due
                            // to MASTER transition)
                            counters.consolidateStoreInconsistencies
                                    .updateCounterWithFlush();
                        }
                    }
                }
            } finally {
                if (iter != null)
                    iter.close();
            }
            // In general, syncedSwitches should now be empty. However,
            // the old Master could have removed a switch from the store
            // after this controller has stopped reacting to store
            // notification (because it's now MASTER). We need to remove
            // these switches.
            Iterator<Long> it = this.syncedSwitches.keySet().iterator();
            while (it.hasNext()) {
                counters.switchesNotReconnectingToNewMaster.updateCounterWithFlush();
                counters.consolidateStoreInconsistencies.updateCounterWithFlush();
                Long dpid = it.next();
                shouldNotifyReadyForReconcile = true;
                addUpdateToQueue(new SwitchUpdate(dpid,
                                                  SwitchUpdateType.REMOVED));
                it.remove();
            }
            if (shouldNotifyReadyForReconcile) {
                // at least one previously known switch has been removed.
                addUpdateToQueue(new ReadyForReconcileUpdate());
            }

            // FIXME: do we need this final check here.
            // Now iterate through all active switches and determine if
            // any of them are missing from the sync store. This can only
            // happen if another controller has removed them (because we know
            // that we have written them to the store).
            for (IOFSwitch sw: this.activeSwitches.values()) {
                if (addSwitchToStoreIfAbsent(sw))
                    counters.consolidateStoreInconsistencies.updateCounterWithFlush();
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

        public void addSwitchEvent(long dpid, String reason, boolean flushNow) {
            if (flushNow)
                evSwitch.updateEventWithFlush(new SwitchEvent(dpid, reason));
            else
                evSwitch.updateEventNoFlush(new SwitchEvent(dpid, reason));
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

    /**
     * Update message that indicates that the controller can now start
     * flow reconciliation after a SLAVE->MASTER transition
     */
    private class ReadyForReconcileUpdate implements IUpdate {
        @Override
        public void dispatch() {
            counters.readyForReconcile.updateCounterWithFlush();
            if (readyForReconcileListeners != null) {
                for (IReadyForReconcileListener listener:
                        readyForReconcileListeners) {
                    listener.readyForReconcile();
                }
            }
        }
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
    private class SwitchUpdate implements IUpdate {
        private final long swId;
        private final SwitchUpdateType switchUpdateType;
        private final ImmutablePort port;
        private final PortChangeType changeType;


        public SwitchUpdate(long swId, SwitchUpdateType switchUpdateType) {
            this(swId, switchUpdateType, null, null);
        }
        public SwitchUpdate(long swId,
                            SwitchUpdateType switchUpdateType,
                            ImmutablePort port,
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
                log.trace("Dispatching switch update {} {}",
                        HexString.toHexString(swId), switchUpdateType);
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
                            counters.switchPortChanged.updateCounterWithFlush();
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
                            counters.switchOtherChange.updateCounterWithFlush();
                            listener.switchChanged(swId);
                            break;
                    }
                }
            }
        }
    }

    /**
     * Update message indicating controller's role has changed.
     * RoleManager, which enqueues these updates gurantees that we will
     * only have a single transition from SLAVE to MASTER.
     */
    private class HARoleUpdate implements IUpdate {
        private final Role newRole;
        public HARoleUpdate(Role newRole) {
            if (newRole != Role.MASTER)
                throw new IllegalArgumentException("Only legal role change is"
                                                   + "to MASTER. Got to "
                                                   + newRole);
            this.newRole = newRole;
        }
        @Override
        public void dispatch() {
            if (log.isDebugEnabled()) {
                log.debug("Dispatching HA Role update newRole = {}",
                          newRole);
            }
            for (IHAListener listener : haListeners.getOrderedListeners()) {
                if (log.isTraceEnabled()) {
                    log.trace("Calling HAListener {} with transitionToMaster",
                              listener.getName());
                }
                listener.transitionToMaster();
            }
            if (newRole != Role.SLAVE) {
                Controller.this.notifiedRole = newRole;
            }
        }
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
        this.storageSource = storageSource;
    }

    IStorageSourceService getStorageSourceService() {
        return this.storageSource;
    }

    void setCounterStore(ICounterStoreService counterStore) {
        this.counterStore = counterStore;
    }

    void setDebugCounter(IDebugCounterService debugCounters) {
        this.debugCounters = debugCounters;
    }

    public void setDebugEvent(IDebugEventService debugEvent) {
        this.debugEvents = debugEvent;
    }

    IDebugCounterService getDebugCounter() {
        return this.debugCounters;
    }

    void setSyncService(ISyncService syncService) {
        this.syncService = syncService;
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
     void notifyPortChanged(IOFSwitch sw,
                            ImmutablePort port,
                            PortChangeType changeType) {
         if (sw == null) {
             String msg = String.format("Switch must not be null. " +
                     "port=%s, changeType=%s", port, changeType);
             throw new NullPointerException(msg);
         }
         if (port == null) {
             String msg = String.format("Port must not be null. " +
                     "switch=%s, changeType=%s", sw, changeType);
             throw new NullPointerException(msg);
         }
         if (changeType == null) {
             String msg = String.format("ChangeType must not be null. " +
                     "switch=%s, port=%s", sw, port);
             throw new NullPointerException(msg);
         }
         this.switchManager.switchPortsChanged(sw, port, changeType);
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
    protected void handleMessage(IOFSwitch sw, OFMessage m,
                                 FloodlightContext bContext)
            throws IOException {
        Ethernet eth = null;

        if (this.notifiedRole == Role.SLAVE) {
            counters.dispatchMessageWhileSlave.updateCounterNoFlush();
            // We are SLAVE. Do not dispatch messages to listeners.
            return;
        }
        counters.dispatchMessage.updateCounterNoFlush();

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
                    pktinProcTime.recordStartTimePktIn();
                    Command cmd;
                    for (IOFMessageListener listener : listeners) {
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
        for(Entry<OFType, ListenerDispatcher<OFType, IOFMessageListener>> e :
            messageListeners.entrySet()) {
            lers.put(e.getKey(), e.getValue().getOrderedListeners());
        }
        return Collections.unmodifiableMap(lers);
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Error reinjecting OFMessage on switch {switch}",
                explanation="An I/O error occured while attempting to " +
                        "process an OpenFlow message",
                recommendation=LogMessageDoc.CHECK_SWITCH)
    })
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg,
                                   FloodlightContext bc) {
        if (sw == null)
            throw new NullPointerException("Switch must not be null");
        if (msg == null)
            throw new NullPointerException("OFMessage must not be null");

        // FIXME: Do we need to be able to inject messages from switches
        // where we're the slave controller (i.e. they're connected but
        // not active)?
        if (!sw.isActive()) return false;

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
        if (sw == null)
            throw new NullPointerException("Switch must not be null");
        if (m == null)
            throw new NullPointerException("OFMessage must not be null");
        if (bc == null)
            bc = new FloodlightContext();
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
        if (role == Role.EQUAL)
            role = Role.MASTER;

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
            InetSocketAddress sa =
            		(openFlowHost == null)
            		? new InetSocketAddress(openFlowPort)
            		: new InetSocketAddress(openFlowHost, openFlowPort);
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

        // Register the standard Vendor actions that we support
        OFVendorActions.registerStandardVendorActions();
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
        // add switch notification listener
        this.addOFSwitchListener(new NotificationSwitchListener());
        this.readyForReconcileListeners =
                new CopyOnWriteArraySet<IReadyForReconcileListener>();
        this.haListeners =
                new ListenerDispatcher<HAListenerTypeMarker, IHAListener>();
        this.driverRegistry = new NaiiveSwitchDriverRegistry();
        this.controllerNodeIPsCache = new HashMap<String, String>();
        this.updates = new LinkedBlockingQueue<IUpdate>();
        this.factory = BasicFactory.getInstance();
        this.providerMap = new HashMap<String, List<IInfoProvider>>();
        setConfigParams(configParams);
        Role initialRole = getInitialRole(configParams);
        this.notifiedRole = initialRole;
        initVendorMessages();

        String option = configParams.get("flushSwitchesOnReconnect");

        if (option != null && option.equalsIgnoreCase("true")) {
            this.setAlwaysClearFlowsOnSwActivate(true);
            log.info("Flush switches on reconnect -- Enabled.");
        } else {
            this.setAlwaysClearFlowsOnSwActivate(false);
            log.info("Flush switches on reconnect -- Disabled");
        }

        uplinkPortPrefixSet = new HashSet<String>();
        uplinkPortPrefixSet.add("eth");
        uplinkPortPrefixSet.add("bond");
        String str = configParams.get("uplinkPortPrefix");
        if (str != null) {
            List<String> items = Arrays.asList(str.split("\\s*,\\s*"));
            if (items != null) {
                for (String s: items) {
                    if (s.length() > 0) {
                        uplinkPortPrefixSet.add(s);
                    }
                }
            }
        }

        this.roleManager = new RoleManager(this.notifiedRole,
                                           INITIAL_ROLE_CHANGE_DESCRIPTION);
        this.switchManager = new SwitchManager(this.notifiedRole);
        this.counters = new Counters();
        this.swConnectCache =
                new TimedCache<Long>(100, 5*1000 );  // 5 seconds interval
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

        storageSource.createTable(FLOW_PRIORITY_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(FLOW_PRIORITY_TABLE_NAME,
                                             FLOW_COLUMN_PRIMARY_KEY);
        storageSource.addListener(FLOW_PRIORITY_TABLE_NAME, this);
        readFlowPriorityConfigurationFromStorage();

        // Startup load monitoring
        if (overload_drop) {
            this.loadmonitor.startMonitoring(
                this.threadPool.getScheduledExecutor());
        }

        // Add our REST API
        restApi.addRestletRoutable(new CoreWebRoutable());

        this.ses = threadPool.getScheduledExecutor();

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

        try {
            this.counters.createCounters(debugCounters);
        } catch (CounterException e) {
            throw new FloodlightModuleException(e.getMessage());
        }

        addInfoProvider("summary", this);

        registerControllerDebugEvents();
    }

    @LogMessageDoc(level="ERROR",
            message="failed to access storage: {reason}",
            explanation="Could not retrieve forwarding configuration",
            recommendation=LogMessageDoc.CHECK_CONTROLLER)
    private void readFlowPriorityConfigurationFromStorage() {
        try {
            Map<String, Object> row;
            IResultSet resultSet = storageSource.executeQuery(
                FLOW_PRIORITY_TABLE_NAME, FLOW_COLUMN_NAMES, null, null);
            if (resultSet == null)
                return;

            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                row = it.next().getRow();
                if (row.containsKey(FLOW_COLUMN_PRIMARY_KEY)) {
                    String primary_key = (String) row.get(FLOW_COLUMN_PRIMARY_KEY);
                    if (primary_key.equals(FLOW_VALUE_PRIMARY_KEY)) {
                        if (row.containsKey(FLOW_COLUMN_ACCESS_PRIORITY)) {
                            accessPriority =
                                Short.valueOf((String) row.get(FLOW_COLUMN_ACCESS_PRIORITY));
                        }
                        if (row.containsKey(FLOW_COLUMN_CORE_PRIORITY)) {
                            corePriority =
                                    Short.valueOf((String) row.get(FLOW_COLUMN_CORE_PRIORITY));
                        }
                    }
                }
            }
        }
        catch (StorageException e) {
            log.error("Failed to access storage for forwarding configuration: {}",
                      e.getMessage());
        }
        catch (NumberFormatException e) {
            // log error, no stack-trace
            log.error("Failed to read core or access flow priority from " +
                      "storage. Illegal number: {}", e.getMessage());
        }
    }


    private void registerControllerDebugEvents() throws FloodlightModuleException {
        if (debugEvents == null) {
            debugEvents = new NullDebugEvent();
        }
        try {
            evSwitch = debugEvents.registerEvent(
                               Counters.prefix, "switchevent",
                               "Switch connected, disconnected or port changed",
                               EventType.ALWAYS_LOG, SwitchEvent.class, 100);
        } catch (MaxEventsRegistered e) {
            throw new FloodlightModuleException("Max events registered", e);
        }
    }

    public class SwitchEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        long dpid;

        @EventColumn(name = "reason", description = EventFieldType.STRING)
        String reason;

        public SwitchEvent(long dpid, String reason) {
            this.dpid = dpid;
            this.reason = reason;
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
        this.haListeners.addListener(null,listener);
    }

    @Override
    public void removeHAListener(IHAListener listener) {
        this.haListeners.removeListener(listener);
    }

    @Override
    public void addReadyForReconcileListener(IReadyForReconcileListener l) {
        this.readyForReconcileListeners.add(l);
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
                removedControllerNodeIPs.put(removedControllerID,
                                             controllerNodeIPsCache.get(removedControllerID));
            controllerNodeIPsCache.clear();
            controllerNodeIPsCache.putAll(curControllerNodeIPs);
            counters.controllerNodeIpsChanged.updateCounterWithFlush();
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
    @LogMessageDoc(level="WARN",
            message=FLOW_PRIORITY_CHANGED_AFTER_STARTUP,
            explanation="A user has changed the priority with which access " +
                    "and core flows are installed after controller startup. " +
                    "Changing this setting will only take affect after a " +
                    "controller restart",
            recommendation="Restart controller")
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
    public void setAlwaysClearFlowsOnSwActivate(boolean value) {
        this.alwaysClearFlowsOnSwActivate = value;
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

    /**
     *  Switch Added/Deleted Events
     */
    @Override
    public void addSwitchEvent(long switchDPID, String reason, boolean flushNow) {
        switchManager.addSwitchEvent(switchDPID, reason, flushNow);
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

    void flushAll() {
        // Flush all flow-mods/packet-out/stats generated from this "train"
        OFSwitchBase.flush_all();
        counterStore.updateFlush();
        debugCounters.flushCounters();
        debugEvents.flushEvents();
    }

    short getAccessFlowPriority() {
        return accessPriority;
    }

    short getCoreFlowPriority() {
        return corePriority;
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

    /**
     * FOR TESTING ONLY
     * check if update queue is empty
     */
    boolean isUpdateQueueEmptyForTesting() {
        return this.updates.isEmpty();
    }

    /**
     * FOR TESTING ONLY
     * @param update
     */
    void setConsolidateStoreTaskDelay(int consolidateStoreTaskDelayMs) {
        this.consolidateStoreTimeDelayMs = consolidateStoreTaskDelayMs;
    }

    /**
     * FOR TESTING ONLY
     * returns the store listener so we can send events to the listener
     */
    IStoreListener<Long> getStoreListener() {
        return this.switchManager;
    }

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();

        info.put("# Switches", this.getAllSwitchDpids().size());
        return info;
    }
}
