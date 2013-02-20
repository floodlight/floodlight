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
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.OFChannelState.HandshakeState;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.counter.ICounterStoreService;
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
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelUpstreamHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFError.OFBadActionCode;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.OFError.OFFlowModFailedCode;
import org.openflow.protocol.OFError.OFHelloFailedCode;
import org.openflow.protocol.OFError.OFPortModFailedCode;
import org.openflow.protocol.OFError.OFQueueOpFailedCode;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFGetConfigReply;
import org.openflow.protocol.OFGetConfigRequest;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFSetConfig;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFSwitchConfig;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFNiciraVendorExtensions;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The main controller class.  Handles all setup and network listeners
 */
public class Controller implements IFloodlightProviderService,
            IStorageSourceListener {

    protected static Logger log = LoggerFactory.getLogger(Controller.class);

    private static final String ERROR_DATABASE =
            "The controller could not communicate with the system database.";

    protected BasicFactory factory;
    protected ConcurrentMap<OFType,
                            ListenerDispatcher<OFType,IOFMessageListener>>
                                messageListeners;
    // OFSwitch driver binding map and order
    protected Map<String, IOFSwitchDriver>switchBindingMap;
    protected List<String> switchDescSortedList;

    // The activeSwitches map contains only those switches that are actively
    // being controlled by us -- it doesn't contain switches that are
    // in the slave role
    protected ConcurrentHashMap<Long, IOFSwitch> activeSwitches;
    // connectedSwitches contains all connected switches, including ones where
    // we're a slave controller. We need to keep track of them so that we can
    // send role request messages to switches when our role changes to master
    // We add a switch to this set after it successfully completes the
    // handshake. Access to this Set needs to be synchronized with roleChanger
    protected HashSet<IOFSwitch> connectedSwitches;

    // The controllerNodeIPsCache maps Controller IDs to their IP address.
    // It's only used by handleControllerNodeIPsChanged
    protected HashMap<String, String> controllerNodeIPsCache;

    protected Set<IOFSwitchListener> switchListeners;
    protected Set<IHAListener> haListeners;
    protected Map<String, List<IInfoProvider>> providerMap;
    protected BlockingQueue<IUpdate> updates;

    // Module dependencies
    protected IRestApiService restApi;
    protected ICounterStoreService counterStore = null;
    protected IFlowCacheService bigFlowCacheMgr;
    protected IStorageSourceService storageSource;
    protected IPktInProcessingTimeService pktinProcTime;
    protected IThreadPoolService threadPool;

    // Configuration options
    protected int openFlowPort = 6633;
    protected int workerThreads = 0;
    // The id for this controller node. Should be unique for each controller
    // node in a controller cluster.
    protected String controllerId = "localhost";

    // The current role of the controller.
    // If the controller isn't configured to support roles, then this is null.
    protected Role role;
    // This is the role of the controller based on HARoleChange notifications
    // we have sent. I.e., this field reflects the last role notification
    // we have sent to the listeners. On a transition to slave we first set
    // this role and then notify, on a transition to master we first notify
    // and then set the role. We then use it to make sure we don't forward
    // OF messages while the modules are in slave role. 
    // The pendingRole is a role change just received, but not sent out
    // notifications yet.
    protected Role pendingRole;protected volatile Role notifiedRole;
    // A helper that handles sending and timeout handling for role requests
    protected RoleChanger roleChanger;
    protected SingletonTask roleChangeDamper;

    // Start time of the controller
    protected long systemStartTime;

    // Flag to always flush flow table on switch reconnect (HA or otherwise)
    protected boolean alwaysClearFlowsOnSwAdd = false;

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

    // Load monitor for overload protection
    protected final boolean overload_drop =
        Boolean.parseBoolean(System.getProperty("overload_drop", "false"));
    protected final LoadMonitor loadmonitor = new LoadMonitor(log);

    /**
     *  Updates handled by the main loop
     */
    protected interface IUpdate {
        /**
         * Calls the appropriate listeners
         */
        public void dispatch();
    }
    public enum SwitchUpdateType {
        ADDED,
        REMOVED,
        PORTCHANGED
    }
    /**
     * Update message indicating a switch was added or removed
     */
    protected class SwitchUpdate implements IUpdate {
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
                            listener.addedSwitch(sw);
                            break;
                        case REMOVED:
                            listener.removedSwitch(sw);
                            break;
                        case PORTCHANGED:
                            listener.switchPortChanged(sw.getId());
                            break;
                    }
                }
            }
        }
    }

    /**
     * Update message indicating controller's role has changed
     */
    protected class HARoleUpdate implements IUpdate {
        public Role oldRole;
        public Role newRole;
        public HARoleUpdate(Role newRole, Role oldRole) {
            this.oldRole = oldRole;
            this.newRole = newRole;
        }
        @Override
        public void dispatch() {
            // Make sure that old and new roles are different.
            if (oldRole == newRole) {
                if (log.isTraceEnabled()) {
                    log.trace("HA role update ignored as the old and " +
                              "new roles are the same. newRole = {}" +
                              "oldRole = {}", newRole, oldRole);
                }
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Dispatching HA Role update newRole = {}, oldRole = {}",
                          newRole, oldRole);
            }
            // Set notified role to slave before notifying listeners. This
            // stops OF messages from being sent to listeners 
            if (newRole == Role.SLAVE)
                Controller.this.notifiedRole = newRole;
            if (haListeners != null) {
                for (IHAListener listener : haListeners) {
                        listener.roleChanged(oldRole, newRole);
                }
            }
            // Set notified role to master/equal after notifying listeners. 
            // We now forward messages again
            if (newRole != Role.SLAVE)
                Controller.this.notifiedRole = newRole;
        }
    }

    /**
     * Update message indicating
     * IPs of controllers in controller cluster have changed.
     */
    protected class HAControllerNodeIPUpdate implements IUpdate {
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

    public void setStorageSourceService(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }

    public void setCounterStore(ICounterStoreService counterStore) {
        this.counterStore = counterStore;
    }

    public void setFlowCacheMgr(IFlowCacheService flowCacheMgr) {
        this.bigFlowCacheMgr = flowCacheMgr;
    }

    public void setPktInProcessingService(IPktInProcessingTimeService pits) {
        this.pktinProcTime = pits;
    }

    public void setRestApiService(IRestApiService restApi) {
        this.restApi = restApi;
    }

    public void setThreadPoolService(IThreadPoolService tp) {
        this.threadPool = tp;
    }

    @Override
    public Role getRole() {
        synchronized(roleChanger) {
            return role;
        }
    }

    @Override
    public void setRole(Role role) {
        if (role == null) throw new NullPointerException("Role can not be null.");

        // If role is changed in quick succession for some reason,
        // the 2 second delay will dampen the frequency.
        this.pendingRole = role;
        roleChangeDamper.reschedule(2000, TimeUnit.MILLISECONDS);
    }

    protected void doSetRole() {
        // Need to synchronize to ensure a reliable ordering on role request
        // messages send and to ensure the list of connected switches is stable
        // RoleChanger will handle the actual sending of the message and
        // timeout handling
        // @see RoleChanger
        synchronized(roleChanger) {
            if (pendingRole.equals(this.role)) {
                log.debug("Ignoring role change: role is already {}", role);
                return;
            }

            Role oldRole = this.role;
            this.role = pendingRole;

            log.debug("Submitting role change request to role {}", role);
            roleChanger.submitRequest(connectedSwitches, role);

            // Enqueue an update for our listeners.
            try {
                this.updates.put(new HARoleUpdate(role, oldRole));
            } catch (InterruptedException e) {
                log.error("Failure adding update to queue", e);
            }
        }
    }



    // **********************
    // ChannelUpstreamHandler
    // **********************

    /**
     * Return a new channel handler for processing a switch connections
     * @param state The channel state object for the connection
     * @return the new channel handler
     */
    protected ChannelUpstreamHandler getChannelHandler(OFChannelState state) {
        return new OFChannelHandler(state);
    }

    /**
     * Channel handler deals with the switch connection and dispatches
     * switch messages to the appropriate locations.
     * @author readams
     */
    protected class OFChannelHandler
        extends IdleStateAwareChannelUpstreamHandler {
        protected IOFSwitch sw;
        protected Channel channel;
        protected OFChannelState state;

        public OFChannelHandler(OFChannelState state) {
            this.state = state;
        }

        @Override
        @LogMessageDoc(message="New switch connection from {ip address}",
                       explanation="A new switch has connected from the " +
                                "specified IP address")
        public void channelConnected(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
            channel = e.getChannel();
            log.info("New switch connection from {}",
                     channel.getRemoteAddress());
            sendHandShakeMessage(OFType.HELLO);
        }

        @Override
        @LogMessageDoc(message="Disconnected switch {switch information}",
                       explanation="The specified switch has disconnected.")
        public void channelDisconnected(ChannelHandlerContext ctx,
                                        ChannelStateEvent e) throws Exception {
            if (sw != null && state.hsState == HandshakeState.READY) {
                if (activeSwitches.containsKey(sw.getId())) {
                    // It's safe to call removeSwitch even though the map might
                    // not contain this particular switch but another with the
                    // same DPID
                    removeSwitch(sw);
                }
                synchronized(roleChanger) {
                    connectedSwitches.remove(sw);
                    roleChanger.removePendingRequests(sw);
                }
                sw.setConnected(false);
            }
            log.info("Disconnected switch {}", sw);
        }

        @Override
        @LogMessageDocs({
            @LogMessageDoc(level="ERROR",
                    message="Disconnecting switch {switch} due to read timeout",
                    explanation="The connected switch has failed to send any " +
                                "messages or respond to echo requests",
                    recommendation=LogMessageDoc.CHECK_SWITCH),
            @LogMessageDoc(level="ERROR",
                    message="Disconnecting switch {switch}: failed to " +
                            "complete handshake",
                    explanation="The switch did not respond correctly " +
                                "to handshake messages",
                    recommendation=LogMessageDoc.CHECK_SWITCH),
            @LogMessageDoc(level="ERROR",
                    message="Disconnecting switch {switch} due to IO Error: {}",
                    explanation="There was an error communicating with the switch",
                    recommendation=LogMessageDoc.CHECK_SWITCH),
            @LogMessageDoc(level="ERROR",
                    message="Disconnecting switch {switch} due to switch " +
                            "state error: {error}",
                    explanation="The switch sent an unexpected message",
                    recommendation=LogMessageDoc.CHECK_SWITCH),
            @LogMessageDoc(level="ERROR",
                    message="Disconnecting switch {switch} due to " +
                            "message parse failure",
                    explanation="Could not parse a message from the switch",
                    recommendation=LogMessageDoc.CHECK_SWITCH),
            @LogMessageDoc(level="ERROR",
                    message="Terminating controller due to storage exception",
                    explanation=ERROR_DATABASE,
                    recommendation=LogMessageDoc.CHECK_CONTROLLER),
            @LogMessageDoc(level="ERROR",
                    message="Could not process message: queue full",
                    explanation="OpenFlow messages are arriving faster than " +
                                " the controller can process them.",
                    recommendation=LogMessageDoc.CHECK_CONTROLLER),
            @LogMessageDoc(level="ERROR",
                    message="Error while processing message " +
                            "from switch {switch} {cause}",
                    explanation="An error occurred processing the switch message",
                    recommendation=LogMessageDoc.GENERIC_ACTION)
        })
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (e.getCause() instanceof ReadTimeoutException) {
                // switch timeout
                log.error("Disconnecting switch {} due to read timeout", sw);
                ctx.getChannel().close();
            } else if (e.getCause() instanceof HandshakeTimeoutException) {
                log.error("Disconnecting switch {}: failed to complete handshake",
                          sw);
                ctx.getChannel().close();
            } else if (e.getCause() instanceof ClosedChannelException) {
                //log.warn("Channel for sw {} already closed", sw);
            } else if (e.getCause() instanceof IOException) {
                log.error("Disconnecting switch {} due to IO Error: {}",
                          sw, e.getCause().getMessage());
                ctx.getChannel().close();
            } else if (e.getCause() instanceof SwitchStateException) {
                log.error("Disconnecting switch {} due to switch state error: {}",
                          sw, e.getCause().getMessage());
                ctx.getChannel().close();
            } else if (e.getCause() instanceof MessageParseException) {
                log.error("Disconnecting switch " + sw +
                          " due to message parse failure",
                          e.getCause());
                ctx.getChannel().close();
            } else if (e.getCause() instanceof StorageException) {
                log.error("Terminating controller due to storage exception",
                          e.getCause());
                terminate();
            } else if (e.getCause() instanceof RejectedExecutionException) {
                log.warn("Could not process message: queue full");
            } else {
                log.error("Error while processing message from switch " + sw,
                          e.getCause());
                ctx.getChannel().close();
            }
        }

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
                throws Exception {
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(factory.getMessage(OFType.ECHO_REQUEST));
            e.getChannel().write(msglist);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            if (e.getMessage() instanceof List) {
                @SuppressWarnings("unchecked")
                List<OFMessage> msglist = (List<OFMessage>)e.getMessage();

                LoadMonitor.LoadLevel loadlevel;
                int packets_dropped = 0;
                int packets_allowed = 0;
                int lldps_allowed = 0;

                if (overload_drop) {
                    loadlevel = loadmonitor.getLoadLevel();
                }
                else {
                    loadlevel = LoadMonitor.LoadLevel.OK;
                }

                for (OFMessage ofm : msglist) {
                    try {
                        if (overload_drop &&
                            !loadlevel.equals(LoadMonitor.LoadLevel.OK)) {
                            switch (ofm.getType()) {
                            case PACKET_IN:
                                switch (loadlevel) {
                                case VERYHIGH:
                                    // Drop all packet-ins, including LLDP/BDDPs
                                    packets_dropped++;
                                    continue;
                                case HIGH:
                                    // Drop all packet-ins, except LLDP/BDDPs
                                    byte[] data = ((OFPacketIn)ofm).getPacketData();
                                    if (data.length > 14) {
                                        if (((data[12] == (byte)0x88) &&
                                             (data[13] == (byte)0xcc)) ||
                                            ((data[12] == (byte)0x89) &&
                                             (data[13] == (byte)0x42))) {
                                            lldps_allowed++;
                                            packets_allowed++;
                                            break;
                                        }
                                    }
                                    packets_dropped++;
                                    continue;
                                default:
                                    // Load not high, go ahead and process msg
                                    packets_allowed++;
                                    break;
                                }
                                break;
                            default:
                                // Process all non-packet-ins
                                packets_allowed++;
                                break;
                            }
                        }

                        // Do the actual packet processing
                        processOFMessage(ofm);

                    }
                    catch (Exception ex) {
                        // We are the last handler in the stream, so run the
                        // exception through the channel again by passing in
                        // ctx.getChannel().
                        Channels.fireExceptionCaught(ctx.getChannel(), ex);
                    }
                }

                if (loadlevel != LoadMonitor.LoadLevel.OK) {
                    if (log.isDebugEnabled()) {
                        log.debug(
                            "Overload: Detected {}, packets dropped={}",
                            loadlevel.toString(), packets_dropped);
                        log.debug(
                            "Overload: Packets allowed={} (LLDP/BDDPs allowed={})",
                            packets_allowed, lldps_allowed);
                    }
                }

                // Flush all flow-mods/packet-out/stats generated from this "train"
                OFSwitchBase.flush_all();
                counterStore.updateFlush();
                bigFlowCacheMgr.updateFlush();
            }
        }

        /**
         * Process the request for the switch description
         */
        @LogMessageDoc(level="ERROR",
                message="Exception in reading description " +
                        " during handshake {exception}",
                explanation="Could not process the switch description string",
                recommendation=LogMessageDoc.CHECK_SWITCH)
        void processSwitchDescReply(OFStatisticsReply m) {
            try {
                // Read description, if it has been updated
                OFDescriptionStatistics description =
                        new OFDescriptionStatistics();
                ChannelBuffer data =
                        ChannelBuffers.buffer(description.getLength());
                OFStatistics f = m.getFirstStatistics();
                f.writeTo(data);
                description.readFrom(data);
                state.description = description;
                state.hasDescription = true;
                checkSwitchReady();
            }
            catch (Exception ex) {
                log.error("Exception in reading description " +
                          " during handshake", ex);
            }
        }

        /**
         * Send initial switch setup information that we need before adding
         * the switch
         * @throws IOException
         */
        private void sendHandShakeMessage(OFType type) throws IOException {
            // Send initial Features Request
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(factory.getMessage(type));
            channel.write(msglist);
        }

        /**
         * Send the configuration requests we can only do after we have
         * the features reply
         * @throws IOException
         */
        private void sendFeatureReplyConfiguration() throws IOException {
            List<OFMessage> msglist = new ArrayList<OFMessage>(3);

            // Ensure we receive the full packet via PacketIn
            OFSetConfig configSet = (OFSetConfig) factory
                    .getMessage(OFType.SET_CONFIG);
            configSet.setMissSendLength((short) 0xffff)
                .setLengthU(OFSwitchConfig.MINIMUM_LENGTH);
            configSet.setXid(-4);
            msglist.add(configSet);

            // Verify (need barrier?)
            OFGetConfigRequest configReq = (OFGetConfigRequest)
                    factory.getMessage(OFType.GET_CONFIG_REQUEST);
            configReq.setXid(-3);
            msglist.add(configReq);

            // Get Description to set switch-specific flags
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.DESC);
            req.setXid(-2);  // something "large"
            req.setLengthU(req.getLengthU());
            msglist.add(req);

            channel.write(msglist);
        }

        protected void checkSwitchReady() {
            if (!state.switchBindingDone) {
                bindSwitchToDriver();
            }

            if (state.hsState == HandshakeState.FEATURES_REPLY &&
                    state.switchBindingDone) {

                state.hsState = HandshakeState.READY;

                // replay queued port status messages
                for (OFMessage m : state.queuedOFMessages) {
                    try {
                        processOFMessage(m);
                    } catch (Exception e) {
                        log.error("Failed to process delayed OFMessage {} {}",
                                m, e.getCause());
                    }
                }

                state.queuedOFMessages.clear();
                synchronized(roleChanger) {
                    // We need to keep track of all of the switches that are connected
                    // to the controller, in any role, so that we can later send the
                    // role request messages when the controller role changes.
                    // We need to be synchronized while doing this: we must not
                    // send a another role request to the connectedSwitches until
                    // we were able to add this new switch to connectedSwitches
                    // *and* send the current role to the new switch.
                    connectedSwitches.add(sw);

                    // Send a role request.
                    // This is a probe that we'll use to determine if the switch
                    // actually supports the role request message. If it does we'll
                    // get back a role reply message. If it doesn't we'll get back an
                    // OFError message.
                    // If role is MASTER we will promote switch to active
                    // list when we receive the switch's role reply messages
                    if (log.isDebugEnabled())
                        log.debug("This controller's role is {}, " +
                                "sending initial role request msg to {}",
                                role, sw);
                    Collection<IOFSwitch> swList = new ArrayList<IOFSwitch>(1);
                    swList.add(sw);
                    roleChanger.submitRequest(swList, role);
                }
            }
        }

        protected void bindSwitchToDriver() {
            if (!state.hasGetConfigReply) {
                log.debug("Waiting for config reply from switch {}",
                        channel.getRemoteAddress());
                return;
            }
            if (!state.hasDescription) {
                log.debug("Waiting for switch description from switch {}",
                        channel.getRemoteAddress());
                return;
            }

            for (String desc : switchDescSortedList) {
                if (state.description.getManufacturerDescription()
                        .startsWith(desc)) {
                    sw = switchBindingMap.get(desc)
                            .getOFSwitchImpl(desc, state.description);
                    if (sw != null) {
                        break;
                    }
                }
            }
            if (sw == null) {
                sw = new OFSwitchImpl();
            }

            // set switch information
            sw.setChannel(channel);
            sw.setFloodlightProvider(Controller.this);
            sw.setThreadPoolService(threadPool);
            sw.setFeaturesReply(state.featuresReply);
            sw.setSwitchProperties(state.description);
            readPropertyFromStorage();

            log.info("Switch {} bound to class {}",
                    HexString.toHexString(sw.getId()), sw.getClass().getName());
            log.info("{}", state.description);

            state.featuresReply = null;
            state.description = null;
            state.switchBindingDone = true;
        }

       private void readPropertyFromStorage() {
            // At this time, also set other switch properties from storage
            boolean is_core_switch = false;
            IResultSet resultSet = null;
            try {
                String swid = sw.getStringId();
                resultSet =
                        storageSource.getRow(SWITCH_CONFIG_TABLE_NAME, swid);
                for (Iterator<IResultSet> it =
                        resultSet.iterator(); it.hasNext();) {
                    // In case of multiple rows, use the status
                    // in last row?
                    Map<String, Object> row = it.next().getRow();
                    if (row.containsKey(SWITCH_CONFIG_CORE_SWITCH)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Reading SWITCH_IS_CORE_SWITCH " +
                                    "config for switch={}, is-core={}",
                                    sw, row.get(SWITCH_CONFIG_CORE_SWITCH));
                        }
                        String ics =
                                (String)row.get(SWITCH_CONFIG_CORE_SWITCH);
                        is_core_switch = ics.equals("true");
                    }
                }
            }
            finally {
                if (resultSet != null)
                    resultSet.close();
            }
            if (is_core_switch) {
                sw.setAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH,
                        new Boolean(true));
            }
        }

        protected boolean handleVendorMessage(OFVendor vendorMessage) {
            boolean shouldHandleMessage = false;
            int vendor = vendorMessage.getVendor();
            switch (vendor) {
                case OFNiciraVendorData.NX_VENDOR_ID:
                    OFNiciraVendorData niciraVendorData =
                        (OFNiciraVendorData)vendorMessage.getVendorData();
                    int dataType = niciraVendorData.getDataType();
                    switch (dataType) {
                        case OFRoleReplyVendorData.NXT_ROLE_REPLY:
                            OFRoleReplyVendorData roleReplyVendorData =
                                    (OFRoleReplyVendorData) niciraVendorData;
                            roleChanger.handleRoleReplyMessage(sw,
                                    vendorMessage, roleReplyVendorData);
                            break;
                        default:
                            log.warn("Unhandled Nicira VENDOR message; " +
                                     "data type = {}", dataType);
                            break;
                    }
                    break;
                default:
                    shouldHandleMessage = true;
                    break;
            }

            return shouldHandleMessage;
        }

        /**
         * Dispatch an Openflow message from a switch to the appropriate
         * handler.
         * @param m The message to process
         * @throws IOException
         * @throws SwitchStateException
         */
        @LogMessageDocs({
            @LogMessageDoc(level="WARN",
                    message="Config Reply from {switch} has " +
                            "miss length set to {length}",
                    explanation="The controller requires that the switch " +
                            "use a miss length of 0xffff for correct " +
                            "function",
                    recommendation="Use a different switch to ensure " +
                            "correct function"),
            @LogMessageDoc(level="WARN",
                    message="Received ERROR from sw {switch} that "
                            +"indicates roles are not supported "
                            +"but we have received a valid "
                            +"role reply earlier",
                    explanation="The switch sent a confusing message to the" +
                            "controller")
        })
        protected void processOFMessage(OFMessage m)
                throws IOException, SwitchStateException {
            boolean shouldHandleMessage = false;

            switch (m.getType()) {
                case HELLO:
                    if (log.isTraceEnabled())
                        log.trace("HELLO from {}", sw);

                    if (state.hsState.equals(HandshakeState.START)) {
                        state.hsState = HandshakeState.HELLO;
                        sendHandShakeMessage(OFType.FEATURES_REQUEST);
                    } else {
                        throw new SwitchStateException("Unexpected HELLO from "
                                                       + sw);
                    }
                    break;
                case ECHO_REQUEST:
                    OFEchoReply reply =
                        (OFEchoReply) factory.getMessage(OFType.ECHO_REPLY);
                    reply.setXid(m.getXid());
                    List<OFMessage> msglist = new ArrayList<OFMessage>(1);
                    msglist.add(reply);
                    channel.write(msglist);
                    break;
                case ECHO_REPLY:
                    break;
                case FEATURES_REPLY:
                    if (log.isTraceEnabled())
                        log.trace("Features Reply from {}", sw);

                    if (state.hsState.equals(HandshakeState.HELLO)) {
                        sendFeatureReplyConfiguration();
                        state.featuresReply = (OFFeaturesReply) m;
                        state.hsState = HandshakeState.FEATURES_REPLY;
                    } else {
                        // return results to rest api caller
                        sw.setFeaturesReply((OFFeaturesReply) m);
                        sw.deliverOFFeaturesReply(m);
                    }
                    break;
                case GET_CONFIG_REPLY:
                    if (log.isTraceEnabled())
                        log.trace("Get config reply from {}", sw);

                    if (!state.hsState.equals(HandshakeState.FEATURES_REPLY)) {
                        String em = "Unexpected GET_CONFIG_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    OFGetConfigReply cr = (OFGetConfigReply) m;
                    if (cr.getMissSendLength() == (short)0xffff) {
                        log.trace("Config Reply from {} confirms " +
                                  "miss length set to 0xffff", sw);
                    } else {
                        log.warn("Config Reply from {} has " +
                                 "miss length set to {}",
                                 sw, cr.getMissSendLength() & 0xffff);
                    }
                    state.hasGetConfigReply = true;
                    checkSwitchReady();
                    break;
                case VENDOR:
                    shouldHandleMessage = handleVendorMessage((OFVendor)m);
                    break;
                case ERROR:
                    // TODO: we need better error handling. Especially for
                    // request/reply style message (stats, roles) we should have
                    // a unified way to lookup the xid in the error message.
                    // This will probable involve rewriting the way we handle
                    // request/reply style messages.
                    OFError error = (OFError) m;

                    if (roleChanger.checkFirstPendingRoleRequestXid(
                            sw, error.getXid())) {
                        roleChanger.deliverRoleRequestError(sw, error);
                    } else if (error.getErrorCode() ==
                            OFErrorType.OFPET_BAD_REQUEST.getValue() &&
                            error.getErrorType() ==
                            OFBadRequestCode.OFPBRC_EPERM.ordinal() &&
                            role.equals(Role.MASTER)) {
                        // We are the master and the switch returned permission
                        // error. Send a role change request in case switch set
                        // the master to someone else.
                        // Only send if there are no pending requests.
                        synchronized(roleChanger) {
                            if (roleChanger.pendingRequestMap.get(sw) == null) {
                                log.info("Tell switch {} who is the master", sw);
                                roleChanger.submitRequest(Collections.singleton(sw), role);
                            }
                        }
                    } else {
                        logError(sw, error);

                        // allow registered listeners to receive error messages
                        shouldHandleMessage = true;
                    }
                    break;
                case STATS_REPLY:
                    if (state.hsState.ordinal() <
                        HandshakeState.FEATURES_REPLY.ordinal()) {
                        String em = "Unexpected STATS_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    if (sw == null) {
                        processSwitchDescReply((OFStatisticsReply) m);
                    } else {
                        sw.deliverStatisticsReply(m);
                    }
                    break;
                case PORT_STATUS:
                    if (sw != null) {
                        handlePortStatusMessage(sw, (OFPortStatus)m);
                        shouldHandleMessage = true;
                    } else {
                        // Queue till we complete driver binding
                        state.queuedOFMessages.add(m);
                    }
                    break;

                default:
                    shouldHandleMessage = true;
                    break;
            }

            if (shouldHandleMessage) {
                // WARNING: sw is null if handshake is not complete
                if (!state.hsState.equals(HandshakeState.READY)) {
                    log.debug("Ignoring message type {} received " +
                              "from switch {} before switch is " +
                              "fully configured.", m.getType(), sw);
                } else {
                    sw.getListenerReadLock().lock();
                    try {

                        if (sw.isConnected()) {
                            // Only dispatch message if the switch is in the
                            // activeSwitch map and if the switches role is 
                            // not slave and the modules are not in slave
                            // TODO: Should we dispatch messages that we expect to
                            // receive when we're in the slave role, e.g. port
                            // status messages? Since we're "hiding" switches from
                            // the listeners when we're in the slave role, then it
                            // seems a little weird to dispatch port status messages
                            // to them. On the other hand there might be special
                            // modules that care about all of the connected switches
                            // and would like to receive port status notifications.
                            if (sw.getHARole() == Role.SLAVE || 
                                    notifiedRole == Role.SLAVE ||
                                    !activeSwitches.containsKey(sw.getId())) {
                                // Don't log message if it's a port status message
                                // since we expect to receive those from the switch
                                // and don't want to emit spurious messages.
                                if (m.getType() != OFType.PORT_STATUS) {
                                    log.debug("Ignoring message type {} received " +
                                            "from switch {} while in the slave role.",
                                            m.getType(), sw);
                                }
                            } else {
                                handleMessage(sw, m, null);
                            }
                        }
                    }
                    finally {
                        sw.getListenerReadLock().unlock();
                    }
                }
            }
        }
    }

    // ****************
    // Message handlers
    // ****************

    protected void handlePortStatusMessage(IOFSwitch sw, OFPortStatus m) {
        short portNumber = m.getDesc().getPortNumber();
        OFPhysicalPort port = m.getDesc();
        if (m.getReason() == (byte)OFPortReason.OFPPR_MODIFY.ordinal()) {
            sw.setPort(port);
            log.debug("Port #{} modified for {}", portNumber, sw);
        } else if (m.getReason() == (byte)OFPortReason.OFPPR_ADD.ordinal()) {
            sw.setPort(port);
            log.debug("Port #{} added for {}", portNumber, sw);
        } else if (m.getReason() ==
                   (byte)OFPortReason.OFPPR_DELETE.ordinal()) {
            sw.deletePort(portNumber);
            log.debug("Port #{} deleted for {}", portNumber, sw);
        }
        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.PORTCHANGED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
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
     * Handle replies to certain OFMessages, and pass others off to listeners
     * @param sw The switch for the message
     * @param m The message
     * @param bContext The floodlight context. If null then floodlight context would
     * be allocated in this function
     * @throws IOException
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
                    log.warn("Unhandled OF Message: {} from {}", m, sw);
                }

                if ((bContext == null) && (bc != null)) flcontext_free(bc);
        }
    }

    /**
     * Log an OpenFlow error message from a switch
     * @param sw The switch that sent the error
     * @param error The error message
     */
    @LogMessageDoc(level="ERROR",
            message="Error {error type} {error code} from {switch}",
            explanation="The switch responded with an unexpected error" +
                    "to an OpenFlow message from the controller",
            recommendation="This could indicate improper network operation. " +
                    "If the problem persists restarting the switch and " +
                    "controller may help."
            )
    protected void logError(IOFSwitch sw, OFError error) {
        int etint = 0xffff & error.getErrorType();
        if (etint < 0 || etint >= OFErrorType.values().length) {
            log.error("Unknown error code {} from sw {}", etint, sw);
        }
        OFErrorType et = OFErrorType.values()[etint];
        switch (et) {
            case OFPET_HELLO_FAILED:
                OFHelloFailedCode hfc =
                    OFHelloFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[] {et, hfc, sw});
                break;
            case OFPET_BAD_REQUEST:
                OFBadRequestCode brc =
                    OFBadRequestCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[] {et, brc, sw});
                break;
            case OFPET_BAD_ACTION:
                OFBadActionCode bac =
                    OFBadActionCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[] {et, bac, sw});
                break;
            case OFPET_FLOW_MOD_FAILED:
                OFFlowModFailedCode fmfc =
                    OFFlowModFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[] {et, fmfc, sw});
                break;
            case OFPET_PORT_MOD_FAILED:
                OFPortModFailedCode pmfc =
                    OFPortModFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[] {et, pmfc, sw});
                break;
            case OFPET_QUEUE_OP_FAILED:
                OFQueueOpFailedCode qofc =
                    OFQueueOpFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[] {et, qofc, sw});
                break;
            default:
                break;
        }
    }

    /**
     * Add a switch to the active switch list and call the switch listeners.
     * This happens either when a switch first connects (and the controller is
     * not in the slave role) or when the role of the controller changes from
     * slave to master.
     *
     * FIXME: remove shouldReadSwitchPortStateFromStorage argument once
     * performance problems are solved. We should call it always or never.
     *
     * @param sw the switch that has been added
     */
    // TODO: need to rethink locking and the synchronous switch update.
    //       We can / should also handle duplicate DPIDs in connectedSwitches
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
    protected void addSwitch(IOFSwitch sw, boolean shouldClearFlowMods) {
        // TODO: is it safe to modify the HashMap without holding
        // the old switch's lock?
        IOFSwitch oldSw = this.activeSwitches.put(sw.getId(), sw);
        if (sw == oldSw) {
            // Note == for object equality, not .equals for value
            log.info("New add switch for pre-existing switch {}", sw);
            return;
        }



        if (oldSw != null) {
            oldSw.getListenerWriteLock().lock();
            try {
                log.error("New switch added {} for already-added switch {}",
                          sw, oldSw);
                // Set the connected flag to false to suppress calling
                // the listeners for this switch in processOFMessage
                oldSw.setConnected(false);

                oldSw.cancelAllStatisticsReplies();

                // we need to clean out old switch state definitively
                // before adding the new switch
                // FIXME: It seems not completely kosher to call the
                // switch listeners here. I thought one of the points of
                // having the asynchronous switch update mechanism was so
                // the addedSwitch and removedSwitch were always called
                // from a single thread to simplify concurrency issues
                // for the listener.
                if (switchListeners != null) {
                    for (IOFSwitchListener listener : switchListeners) {
                        listener.removedSwitch(oldSw);
                    }
                }
                // will eventually trigger a removeSwitch(), which will cause
                // a "Not removing Switch ... already removed debug message.
                // TODO: Figure out a way to handle this that avoids the
                // spurious debug message.
                oldSw.disconnectOutputStream();
            }
            finally {
                oldSw.getListenerWriteLock().unlock();
            }
        }

        if (shouldClearFlowMods)
            sw.clearAllFlowMods();

        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.ADDED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    /**
     * Remove a switch from the active switch list and call the switch listeners.
     * This happens either when the switch is disconnected or when the
     * controller's role for the switch changes from master to slave.
     * @param sw the switch that has been removed
     */
    protected void removeSwitch(IOFSwitch sw) {
        // No need to acquire the listener lock, since
        // this method is only called after netty has processed all
        // pending messages
        log.debug("removeSwitch: {}", sw);
        if (!this.activeSwitches.remove(sw.getId(), sw) || !sw.isConnected()) {
            log.debug("Not removing switch {}; already removed", sw);
            return;
        }
        // We cancel all outstanding statistics replies if the switch transition
        // from active. In the future we might allow statistics requests
        // from slave controllers. Then we need to move this cancelation
        // to switch disconnect
        sw.cancelAllStatisticsReplies();

        // FIXME: I think there's a race condition if we call updateInactiveSwitchInfo
        // here if role support is enabled. In that case if the switch is being
        // removed because we've been switched to being in the slave role, then I think
        // it's possible that the new master may have already been promoted to master
        // and written out the active switch state to storage. If we now execute
        // updateInactiveSwitchInfo we may wipe out all of the state that was
        // written out by the new master. Maybe need to revisit how we handle all
        // of the switch state that's written to storage.

        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.REMOVED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    // ***************
    // IFloodlightProvider
    // ***************

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
    public Map<Long, IOFSwitch> getSwitches() {
        if (activeSwitches == null) return null;
        return Collections.unmodifiableMap(this.activeSwitches);
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
        // FIXME: Don't we need synchronization logic here so we're holding
        // the listener read lock when we call handleMessage? After some
        // discussions it sounds like the right thing to do here would be to
        // inject the message as a netty upstream channel event so it goes
        // through the normal netty event processing, including being
        // handled
        if (!activeSwitches.containsKey(sw.getId())) return false;

        try {
            // Pass Floodlight context to the handleMessages()
            handleMessage(sw, msg, bc);
        } catch (IOException e) {
            log.error("Error reinjecting OFMessage on switch {}",
                      HexString.toHexString(sw.getId()));
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

    @Override
    public String getControllerId() {
        return controllerId;
    }

    // **************
    // Initialization
    // **************

    protected void updateControllerInfo() {
        // Write out the controller info to the storage source
        Map<String, Object> controllerInfo = new HashMap<String, Object>();
        String id = getControllerId();
        controllerInfo.put(CONTROLLER_ID, id);
        storageSource.updateRow(CONTROLLER_TABLE_NAME, controllerInfo);
    }


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
                return;
            } catch (StorageException e) {
                log.error("Storage exception in controller " +
                          "updates loop; terminating process", e);
                return;
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

    public void setConfigParams(Map<String, String> configParams) {
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
        String controllerId = configParams.get("controllerid");
        if (controllerId != null) {
            this.controllerId = controllerId;
        }
        log.debug("ControllerId set to {}", this.controllerId);
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
        this.switchBindingMap =
                new ConcurrentHashMap<String, IOFSwitchDriver>();
        this.switchDescSortedList = new ArrayList<String>();
        this.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();
        this.connectedSwitches = new HashSet<IOFSwitch>();
        this.controllerNodeIPsCache = new HashMap<String, String>();
        this.updates = new LinkedBlockingQueue<IUpdate>();
        this.factory = new BasicFactory();
        this.providerMap = new HashMap<String, List<IInfoProvider>>();
        setConfigParams(configParams);
        this.role = getInitialRole(configParams);
        this.notifiedRole = this.role;
        this.roleChanger = new RoleChanger(this);
        initVendorMessages();
        this.systemStartTime = System.currentTimeMillis();

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
    public void startupComponents() {
        // Create the table names we use
        storageSource.createTable(CONTROLLER_TABLE_NAME, null);
        storageSource.createTable(CONTROLLER_INTERFACE_TABLE_NAME, null);
        storageSource.createTable(SWITCH_CONFIG_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(CONTROLLER_TABLE_NAME,
                                             CONTROLLER_ID);
        storageSource.addListener(CONTROLLER_INTERFACE_TABLE_NAME, this);

        while (true) {
            try {
                updateControllerInfo();
                break;
            }
            catch (StorageException e) {
                log.info("Waiting for storage source");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                }
            }
        }

        // Startup load monitoring
        if (overload_drop) {
            this.loadmonitor.startMonitoring(
                this.threadPool.getScheduledExecutor());
        }

        // Add our REST API
        restApi.addRestletRoutable(new CoreWebRoutable());

        // Start role change task
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        roleChangeDamper = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                doSetRole();
            }
        });
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
                try {
                    this.updates.put(update);
                } catch (InterruptedException e) {
                    log.error("Failure adding update to queue", e);
                }
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
        return (this.systemStartTime);
    }

    @Override
    public void setAlwaysClearFlowsOnSwAdd(boolean value) {
        this.alwaysClearFlowsOnSwAdd = value;
    }

    public boolean getAlwaysClearFlowsOnSwAdd() {
        return this.alwaysClearFlowsOnSwAdd;
    }

    @Override
    public void addOFSwitchDriver(String description, IOFSwitchDriver driver) {
        IOFSwitchDriver existingDriver = switchBindingMap.get(description);
        if (existingDriver != null) {
            log.warn("Failed to add OFSwitch driver for {}, " +
                     "already registered", description);
            return;
        }
        switchBindingMap.put(description, driver);

        // Sort so we match the longest string first
        int index = -1;
        for (String desc : switchDescSortedList) {
            if (description.compareTo(desc) > 0) {
                index = switchDescSortedList.indexOf(desc);
                switchDescSortedList.add(index, description);
                break;
            }
        }
        if (index == -1) {  // append to list
            switchDescSortedList.add(description);
        }
    }
}
