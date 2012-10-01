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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
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
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchFilter;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.OFChannelState.HandshakeState;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;

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
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFSetConfig;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFSwitchConfig;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U32;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;
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
    // The activeSwitches map contains only those switches that are actively
    // being controlled by us -- it doesn't contain switches that are
    // in the slave role
    protected ConcurrentHashMap<Long, IOFSwitch> activeSwitches;
    // connectedSwitches contains all connected switches, including ones where
    // we're a slave controller. We need to keep track of them so that we can
    // send role request messages to switches when our role changes to master
    // We add a switch to this set after it successfully completes the
    // handshake. Access to this Set needs to be synchronized with roleChanger
    protected HashSet<OFSwitchImpl> connectedSwitches;
    
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
    // A helper that handles sending and timeout handling for role requests
    protected RoleChanger roleChanger;
    
    // Start time of the controller
    protected long systemStartTime;
    
    // Storage table names
    protected static final String CONTROLLER_TABLE_NAME = "controller_controller";
    protected static final String CONTROLLER_ID = "id";
    
    protected static final String SWITCH_TABLE_NAME = "controller_switch";
    protected static final String SWITCH_DATAPATH_ID = "dpid";
    protected static final String SWITCH_SOCKET_ADDRESS = "socket_address";
    protected static final String SWITCH_IP = "ip";
    protected static final String SWITCH_CONTROLLER_ID = "controller_id";
    protected static final String SWITCH_ACTIVE = "active";
    protected static final String SWITCH_CONNECTED_SINCE = "connected_since";
    protected static final String SWITCH_CAPABILITIES = "capabilities";
    protected static final String SWITCH_BUFFERS = "buffers";
    protected static final String SWITCH_TABLES = "tables";
    protected static final String SWITCH_ACTIONS = "actions";

    protected static final String SWITCH_CONFIG_TABLE_NAME = "controller_switchconfig";
    protected static final String SWITCH_CONFIG_CORE_SWITCH = "core_switch";
    
    protected static final String PORT_TABLE_NAME = "controller_port";
    protected static final String PORT_ID = "id";
    protected static final String PORT_SWITCH = "switch_id";
    protected static final String PORT_NUMBER = "number";
    protected static final String PORT_HARDWARE_ADDRESS = "hardware_address";
    protected static final String PORT_NAME = "name";
    protected static final String PORT_CONFIG = "config";
    protected static final String PORT_STATE = "state";
    protected static final String PORT_CURRENT_FEATURES = "current_features";
    protected static final String PORT_ADVERTISED_FEATURES = "advertised_features";
    protected static final String PORT_SUPPORTED_FEATURES = "supported_features";
    protected static final String PORT_PEER_FEATURES = "peer_features";
    
    protected static final String CONTROLLER_INTERFACE_TABLE_NAME = "controller_controllerinterface";
    protected static final String CONTROLLER_INTERFACE_ID = "id";
    protected static final String CONTROLLER_INTERFACE_CONTROLLER_ID = "controller_id";
    protected static final String CONTROLLER_INTERFACE_TYPE = "type";
    protected static final String CONTROLLER_INTERFACE_NUMBER = "number";
    protected static final String CONTROLLER_INTERFACE_DISCOVERED_IP = "discovered_ip";
    
    
    
    // Perf. related configuration
    protected static final int SEND_BUFFER_SIZE = 4 * 1024 * 1024;
    protected static final int BATCH_MAX_SIZE = 100;
    protected static final boolean ALWAYS_DECODE_ETH = true;

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
            if (log.isTraceEnabled()) {
                log.trace("Dispatching HA Role update newRole = {}, oldRole = {}",
                          newRole, oldRole);
            }
            if (haListeners != null) {
                for (IHAListener listener : haListeners) {
                        listener.roleChanged(oldRole, newRole);
                }
            }
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
        if (role == Role.MASTER && this.role == Role.SLAVE) {
            // Reset db state to Inactive for all switches. 
            updateAllInactiveSwitchInfo();
        }
        
        // Need to synchronize to ensure a reliable ordering on role request
        // messages send and to ensure the list of connected switches is stable
        // RoleChanger will handle the actual sending of the message and 
        // timeout handling
        // @see RoleChanger
        synchronized(roleChanger) {
            if (role.equals(this.role)) {
                log.debug("Ignoring role change: role is already {}", role);
                return;
            }

            Role oldRole = this.role;
            this.role = role;
            
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
        protected OFSwitchImpl sw;
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
            log.info("New switch connection from {}",
                     e.getChannel().getRemoteAddress());
            
            sw = new OFSwitchImpl();
            sw.setChannel(e.getChannel());
            sw.setFloodlightProvider(Controller.this);
            sw.setThreadPoolService(threadPool);
            
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(factory.getMessage(OFType.HELLO));
            e.getChannel().write(msglist);

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
                    recommendation=LogMessageDoc.GENERIC_ACTION),
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

                for (OFMessage ofm : msglist) {
                    try {
                        processOFMessage(ofm);
                    }
                    catch (Exception ex) {
                        // We are the last handler in the stream, so run the 
                        // exception through the channel again by passing in 
                        // ctx.getChannel().
                        Channels.fireExceptionCaught(ctx.getChannel(), ex);
                    }
                }

                // Flush all flow-mods/packet-out generated from this "train"
                OFSwitchImpl.flush_all();
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
        void processSwitchDescReply() {
            try {
                // Read description, if it has been updated
                @SuppressWarnings("unchecked")
                Future<List<OFStatistics>> desc_future =
                    (Future<List<OFStatistics>>)sw.
                        getAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE);
                List<OFStatistics> values = 
                        desc_future.get(0, TimeUnit.MILLISECONDS);
                if (values != null) {
                    OFDescriptionStatistics description = 
                            new OFDescriptionStatistics();
                    ChannelBuffer data = 
                            ChannelBuffers.buffer(description.getLength());
                    for (OFStatistics f : values) {
                        f.writeTo(data);
                        description.readFrom(data);
                        break; // SHOULD be a list of length 1
                    }
                    sw.setAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA, 
                                    description);
                    sw.setSwitchProperties(description);
                    data = null;

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
                sw.removeAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE);
                state.hasDescription = true;
                checkSwitchReady();
            }
            catch (InterruptedException ex) {
                // Ignore
            }
            catch (TimeoutException ex) {
                // Ignore
            } catch (Exception ex) {
                log.error("Exception in reading description " + 
                          " during handshake", ex);
            }
        }

        /**
         * Send initial switch setup information that we need before adding
         * the switch
         * @throws IOException
         */
        void sendHelloConfiguration() throws IOException {
            // Send initial Features Request
            sw.write(factory.getMessage(OFType.FEATURES_REQUEST), null);
        }
        
        /**
         * Send the configuration requests we can only do after we have
         * the features reply
         * @throws IOException
         */
        void sendFeatureReplyConfiguration() throws IOException {
            // Ensure we receive the full packet via PacketIn
            OFSetConfig config = (OFSetConfig) factory
                    .getMessage(OFType.SET_CONFIG);
            config.setMissSendLength((short) 0xffff)
            .setLengthU(OFSwitchConfig.MINIMUM_LENGTH);
            sw.write(config, null);
            sw.write(factory.getMessage(OFType.GET_CONFIG_REQUEST),
                    null);

            // Get Description to set switch-specific flags
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.DESC);
            req.setLengthU(req.getLengthU());
            Future<List<OFStatistics>> dfuture = 
                    sw.getStatistics(req);
            sw.setAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE,
                    dfuture);

        }
        
        protected void checkSwitchReady() {
            if (state.hsState == HandshakeState.FEATURES_REPLY &&
                    state.hasDescription && state.hasGetConfigReply) {
                
                state.hsState = HandshakeState.READY;
                
                synchronized(roleChanger) {
                    // We need to keep track of all of the switches that are connected
                    // to the controller, in any role, so that we can later send the 
                    // role request messages when the controller role changes.
                    // We need to be synchronized while doing this: we must not 
                    // send a another role request to the connectedSwitches until
                    // we were able to add this new switch to connectedSwitches 
                    // *and* send the current role to the new switch.
                    connectedSwitches.add(sw);
                    
                    if (role != null) {
                        // Send a role request if role support is enabled for the controller
                        // This is a probe that we'll use to determine if the switch
                        // actually supports the role request message. If it does we'll
                        // get back a role reply message. If it doesn't we'll get back an
                        // OFError message. 
                        // If role is MASTER we will promote switch to active
                        // list when we receive the switch's role reply messages
                        log.debug("This controller's role is {}, " + 
                                "sending initial role request msg to {}",
                                role, sw);
                        Collection<OFSwitchImpl> swList = new ArrayList<OFSwitchImpl>(1);
                        swList.add(sw);
                        roleChanger.submitRequest(swList, role);
                    } 
                    else {
                        // Role supported not enabled on controller (for now)
                        // automatically promote switch to active state. 
                        log.debug("This controller's role is null, " + 
                                "not sending role request msg to {}",
                                role, sw);
                        // Need to clear FlowMods before we add the switch
                        // and dispatch updates otherwise we have a race condition.
                        sw.clearAllFlowMods();
                        addSwitch(sw);
                        state.firstRoleReplyReceived = true;
                    }
                }
            }
        }
                
        /* Handle a role reply message we received from the switch. Since
         * netty serializes message dispatch we don't need to synchronize 
         * against other receive operations from the same switch, so no need
         * to synchronize addSwitch(), removeSwitch() operations from the same
         * connection. 
         * FIXME: However, when a switch with the same DPID connects we do
         * need some synchronization. However, handling switches with same
         * DPID needs to be revisited anyways (get rid of r/w-lock and synchronous
         * removedSwitch notification):1
         * 
         */
        @LogMessageDoc(level="ERROR",
                message="Invalid role value in role reply message",
                explanation="Was unable to set the HA role (master or slave) " +
                		"for the controller.",
                recommendation=LogMessageDoc.CHECK_CONTROLLER)
        protected void handleRoleReplyMessage(OFVendor vendorMessage,
                                    OFRoleReplyVendorData roleReplyVendorData) {
            // Map from the role code in the message to our role enum
            int nxRole = roleReplyVendorData.getRole();
            Role role = null;
            switch (nxRole) {
                case OFRoleVendorData.NX_ROLE_OTHER:
                    role = Role.EQUAL;
                    break;
                case OFRoleVendorData.NX_ROLE_MASTER:
                    role = Role.MASTER;
                    break;
                case OFRoleVendorData.NX_ROLE_SLAVE:
                    role = Role.SLAVE;
                    break;
                default:
                    log.error("Invalid role value in role reply message");
                    sw.getChannel().close();
                    return;
            }
            
            log.debug("Handling role reply for role {} from {}. " +
                      "Controller's role is {} ", 
                      new Object[] { role, sw, Controller.this.role} 
                      );
            
            sw.deliverRoleReply(vendorMessage.getXid(), role);
            
            boolean isActive = activeSwitches.containsKey(sw.getId());
            if (!isActive && sw.isActive()) {
                // Transition from SLAVE to MASTER.
                
                // Some switches don't seem to update us with port
                // status messages while in slave role.
                readSwitchPortStateFromStorage(sw);                
                // Only add the switch to the active switch list if 
                // we're not in the slave role. Note that if the role 
                // attribute is null, then that means that the switch 
                // doesn't support the role request messages, so in that
                // case we're effectively in the EQUAL role and the 
                // switch should be included in the active switch list.
                addSwitch(sw);
                log.debug("Added master switch {} to active switch list",
                         HexString.toHexString(sw.getId()));
                    
                if (!state.firstRoleReplyReceived) {
                    // This is the first role-reply message we receive from
                    // this switch or roles were disabled when the switch
                    // connected: 
                    // Delete all pre-existing flows for new connections to 
                    // the master
                    //
                    // FIXME: Need to think more about what the test should 
                    // be for when we flush the flow-table? For example, 
                    // if all the controllers are temporarily in the backup 
                    // role (e.g. right after a failure of the master 
                    // controller) at the point the switch connects, then 
                    // all of the controllers will initially connect as 
                    // backup controllers and not flush the flow-table. 
                    // Then when one of them is promoted to master following
                    // the master controller election the flow-table
                    // will still not be flushed because that's treated as 
                    // a failover event where we don't want to flush the 
                    // flow-table. The end result would be that the flow 
                    // table for a newly connected switch is never
                    // flushed. Not sure how to handle that case though...
                    sw.clearAllFlowMods();
                    log.debug("First role reply from master switch {}, " +
                              "clear FlowTable to active switch list",
                             HexString.toHexString(sw.getId()));
                }
            } 
            else if (isActive && !sw.isActive()) {
                // Transition from MASTER to SLAVE: remove switch 
                // from active switch list. 
                log.debug("Removed slave switch {} from active switch" +
                          " list", HexString.toHexString(sw.getId()));
                removeSwitch(sw);
            }
            
            // Indicate that we have received a role reply message. 
            state.firstRoleReplyReceived = true;
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
                            handleRoleReplyMessage(vendorMessage, 
                                                   roleReplyVendorData);
                            break;
                        default:
                            log.warn("Unhandled Nicira VENDOR message; " +
                                     "data type = {}", dataType);
                            break;
                    }
                    break;
                default:
                    log.warn("Unhandled VENDOR message; vendor id = {}", vendor);
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
                    		"controller"),
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
                        sendHelloConfiguration();
                    } else {
                        throw new SwitchStateException("Unexpected HELLO from " 
                                                       + sw);
                    }
                    break;
                case ECHO_REQUEST:
                    OFEchoReply reply =
                        (OFEchoReply) factory.getMessage(OFType.ECHO_REPLY);
                    reply.setXid(m.getXid());
                    sw.write(reply, null);
                    break;
                case ECHO_REPLY:
                    break;
                case FEATURES_REPLY:
                    if (log.isTraceEnabled())
                        log.trace("Features Reply from {}", sw);
                    
                    sw.setFeaturesReply((OFFeaturesReply) m);
                    if (state.hsState.equals(HandshakeState.HELLO)) {
                        sendFeatureReplyConfiguration();
                        state.hsState = HandshakeState.FEATURES_REPLY;
                        // uncomment to enable "dumb" switches like cbench
                        // state.hsState = HandshakeState.READY;
                        // addSwitch(sw);
                    } else {
                        // return results to rest api caller
                        sw.deliverOFFeaturesReply(m);
                        // update database */
                        updateActiveSwitchInfo(sw);
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
                    boolean shouldLogError = true;
                    // TODO: should we check that firstRoleReplyReceived is false,
                    // i.e., check only whether the first request fails?
                    if (sw.checkFirstPendingRoleRequestXid(error.getXid())) {
                        boolean isBadVendorError =
                            (error.getErrorType() == OFError.OFErrorType.
                                    OFPET_BAD_REQUEST.getValue()) &&
                            (error.getErrorCode() == OFError.
                                    OFBadRequestCode.
                                        OFPBRC_BAD_VENDOR.ordinal());
                        // We expect to receive a bad vendor error when 
                        // we're connected to a switch that doesn't support 
                        // the Nicira vendor extensions (i.e. not OVS or 
                        // derived from OVS). So that's not a real error 
                        // case and we don't want to log those spurious errors.
                        shouldLogError = !isBadVendorError;
                        if (isBadVendorError) {
                            if (state.firstRoleReplyReceived && (role != null)) {
                                log.warn("Received ERROR from sw {} that "
                                          +"indicates roles are not supported "
                                          +"but we have received a valid "
                                          +"role reply earlier", sw);
                            }
                            state.firstRoleReplyReceived = true;
                            sw.deliverRoleRequestNotSupported(error.getXid());
                            synchronized(roleChanger) {
                                if (sw.role == null && Controller.this.role==Role.SLAVE) {
                                    // the switch doesn't understand role request
                                    // messages and the current controller role is
                                    // slave. We need to disconnect the switch. 
                                    // @see RoleChanger for rationale
                                    sw.getChannel().close();
                                }
                                else if (sw.role == null) {
                                    // Controller's role is master: add to
                                    // active 
                                    // TODO: check if clearing flow table is
                                    // right choice here.
                                    // Need to clear FlowMods before we add the switch
                                    // and dispatch updates otherwise we have a race condition.
                                    // TODO: switch update is async. Won't we still have a potential
                                    //       race condition? 
                                    sw.clearAllFlowMods();
                                    addSwitch(sw);
                                }
                            }
                        }
                        else {
                            // TODO: Is this the right thing to do if we receive
                            // some other error besides a bad vendor error? 
                            // Presumably that means the switch did actually
                            // understand the role request message, but there 
                            // was some other error from processing the message.
                            // OF 1.2 specifies a OFPET_ROLE_REQUEST_FAILED
                            // error code, but it doesn't look like the Nicira 
                            // role request has that. Should check OVS source 
                            // code to see if it's possible for any other errors
                            // to be returned.
                            // If we received an error the switch is not
                            // in the correct role, so we need to disconnect it. 
                            // We could also resend the request but then we need to
                            // check if there are other pending request in which
                            // case we shouldn't resend. If we do resend we need
                            // to make sure that the switch eventually accepts one
                            // of our requests or disconnect the switch. This feels
                            // cumbersome. 
                            sw.getChannel().close();
                        }
                    }
                    // Once we support OF 1.2, we'd add code to handle it here.
                    //if (error.getXid() == state.ofRoleRequestXid) {
                    //}
                    if (shouldLogError)
                        logError(sw, error);
                    break;
                case STATS_REPLY:
                    if (state.hsState.ordinal() < 
                        HandshakeState.FEATURES_REPLY.ordinal()) {
                        String em = "Unexpected STATS_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    sw.deliverStatisticsReply(m);
                    if (sw.hasAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE)) {
                        processSwitchDescReply();
                    }
                    break;
                case PORT_STATUS:
                    // We want to update our port state info even if we're in 
                    // the slave role, but we only want to update storage if 
                    // we're the master (or equal).
                    boolean updateStorage = state.hsState.
                                                equals(HandshakeState.READY) &&
                                                (sw.getRole() != Role.SLAVE);
                    handlePortStatusMessage(sw, (OFPortStatus)m, updateStorage);
                    shouldHandleMessage = true;
                    break;

                default:
                    shouldHandleMessage = true;
                    break;
            }
            
            if (shouldHandleMessage) {
                sw.getListenerReadLock().lock();
                try {
                    if (sw.isConnected()) {
                        if (!state.hsState.equals(HandshakeState.READY)) {
                            log.debug("Ignoring message type {} received " + 
                                      "from switch {} before switch is " + 
                                      "fully configured.", m.getType(), sw);
                        }
                        // Check if the controller is in the slave role for the 
                        // switch. If it is, then don't dispatch the message to 
                        // the listeners.
                        // TODO: Should we dispatch messages that we expect to 
                        // receive when we're in the slave role, e.g. port 
                        // status messages? Since we're "hiding" switches from 
                        // the listeners when we're in the slave role, then it 
                        // seems a little weird to dispatch port status messages
                        // to them. On the other hand there might be special 
                        // modules that care about all of the connected switches
                        // and would like to receive port status notifications.
                        else if (sw.getRole() == Role.SLAVE) {
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

    // ****************
    // Message handlers
    // ****************
    
    protected void handlePortStatusMessage(IOFSwitch sw,
                                           OFPortStatus m,
                                           boolean updateStorage) {
        short portNumber = m.getDesc().getPortNumber();
        OFPhysicalPort port = m.getDesc();
        if (m.getReason() == (byte)OFPortReason.OFPPR_MODIFY.ordinal()) {
            sw.setPort(port);
            if (updateStorage)
                updatePortInfo(sw, port);
            log.debug("Port #{} modified for {}", portNumber, sw);
        } else if (m.getReason() == (byte)OFPortReason.OFPPR_ADD.ordinal()) {
            sw.setPort(port);
            if (updateStorage)
                updatePortInfo(sw, port);
            log.debug("Port #{} added for {}", portNumber, sw);
        } else if (m.getReason() == 
                   (byte)OFPortReason.OFPPR_DELETE.ordinal()) {
            sw.deletePort(portNumber);
            if (updateStorage)
                removePortInfo(sw, portNumber);
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
                		"the controller"),
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
                    counterStore.updatePacketInCounters(sw, m, eth);
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
    protected void addSwitch(IOFSwitch sw) {
        // TODO: is it safe to modify the HashMap without holding 
        // the old switch's lock?
        OFSwitchImpl oldSw = (OFSwitchImpl) this.activeSwitches.put(sw.getId(), sw);
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
                
                updateInactiveSwitchInfo(oldSw);
    
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
                oldSw.getChannel().close();
            }
            finally {
                oldSw.getListenerWriteLock().unlock();
            }
        }
        
        updateActiveSwitchInfo(sw);
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
        
        updateInactiveSwitchInfo(sw);
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

    protected void updateAllInactiveSwitchInfo() {
        if (role == Role.SLAVE) {
            return;
        }
        String controllerId = getControllerId();
        String[] switchColumns = { SWITCH_DATAPATH_ID,
                                   SWITCH_CONTROLLER_ID,
                                   SWITCH_ACTIVE };
        String[] portColumns = { PORT_ID, PORT_SWITCH };
        IResultSet switchResultSet = null;
        try {
            OperatorPredicate op = 
                    new OperatorPredicate(SWITCH_CONTROLLER_ID,
                                          OperatorPredicate.Operator.EQ,
                                          controllerId);
            switchResultSet = 
                    storageSource.executeQuery(SWITCH_TABLE_NAME,
                                               switchColumns,
                                               op, null);
            while (switchResultSet.next()) {
                IResultSet portResultSet = null;
                try {
                    String datapathId =
                            switchResultSet.getString(SWITCH_DATAPATH_ID);
                    switchResultSet.setBoolean(SWITCH_ACTIVE, Boolean.FALSE);
                    op = new OperatorPredicate(PORT_SWITCH, 
                                               OperatorPredicate.Operator.EQ,
                                               datapathId);
                    portResultSet = 
                            storageSource.executeQuery(PORT_TABLE_NAME,
                                                       portColumns,
                                                       op, null);
                    while (portResultSet.next()) {
                        portResultSet.deleteRow();
                    }
                    portResultSet.save();
                }
                finally {
                    if (portResultSet != null)
                        portResultSet.close();
                }
            }
            switchResultSet.save();
        }
        finally {
            if (switchResultSet != null)
                switchResultSet.close();
        }
    }
    
    protected void updateControllerInfo() {
        updateAllInactiveSwitchInfo();
        
        // Write out the controller info to the storage source
        Map<String, Object> controllerInfo = new HashMap<String, Object>();
        String id = getControllerId();
        controllerInfo.put(CONTROLLER_ID, id);
        storageSource.updateRow(CONTROLLER_TABLE_NAME, controllerInfo);
    }
    
    protected void updateActiveSwitchInfo(IOFSwitch sw) {
        if (role == Role.SLAVE) {
            return;
        }
        // Obtain the row info for the switch
        Map<String, Object> switchInfo = new HashMap<String, Object>();
        String datapathIdString = sw.getStringId();
        switchInfo.put(SWITCH_DATAPATH_ID, datapathIdString);
        String controllerId = getControllerId();
        switchInfo.put(SWITCH_CONTROLLER_ID, controllerId);
        Date connectedSince = sw.getConnectedSince();
        switchInfo.put(SWITCH_CONNECTED_SINCE, connectedSince);
        Channel channel = sw.getChannel();
        SocketAddress socketAddress = channel.getRemoteAddress();
        if (socketAddress != null) {
            String socketAddressString = socketAddress.toString();
            switchInfo.put(SWITCH_SOCKET_ADDRESS, socketAddressString);
            if (socketAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress =
                        (InetSocketAddress)socketAddress;
                InetAddress inetAddress = inetSocketAddress.getAddress();
                String ip = inetAddress.getHostAddress();
                switchInfo.put(SWITCH_IP, ip);
            }
        }
        
        // Write out the switch features info
        long capabilities = U32.f(sw.getCapabilities());
        switchInfo.put(SWITCH_CAPABILITIES, capabilities);
        long buffers = U32.f(sw.getBuffers());
        switchInfo.put(SWITCH_BUFFERS, buffers);
        long tables = U32.f(sw.getTables());
        switchInfo.put(SWITCH_TABLES, tables);
        long actions = U32.f(sw.getActions());
        switchInfo.put(SWITCH_ACTIONS, actions);
        switchInfo.put(SWITCH_ACTIVE, Boolean.TRUE);
        
        // Update the switch
        storageSource.updateRowAsync(SWITCH_TABLE_NAME, switchInfo);
        
        // Update the ports
        for (OFPhysicalPort port: sw.getPorts()) {
            updatePortInfo(sw, port);
        }
    }
    
    protected void updateInactiveSwitchInfo(IOFSwitch sw) {
        if (role == Role.SLAVE) {
            return;
        }
        log.debug("Update DB with inactiveSW {}", sw);
        // Update the controller info in the storage source to be inactive
        Map<String, Object> switchInfo = new HashMap<String, Object>();
        String datapathIdString = sw.getStringId();
        switchInfo.put(SWITCH_DATAPATH_ID, datapathIdString);
        //switchInfo.put(SWITCH_CONNECTED_SINCE, null);
        switchInfo.put(SWITCH_ACTIVE, Boolean.FALSE);
        storageSource.updateRowAsync(SWITCH_TABLE_NAME, switchInfo);
    }

    protected void updatePortInfo(IOFSwitch sw, OFPhysicalPort port) {
        if (role == Role.SLAVE) {
            return;
        }
        String datapathIdString = sw.getStringId();
        Map<String, Object> portInfo = new HashMap<String, Object>();
        int portNumber = U16.f(port.getPortNumber());
        String id = datapathIdString + "|" + portNumber;
        portInfo.put(PORT_ID, id);
        portInfo.put(PORT_SWITCH, datapathIdString);
        portInfo.put(PORT_NUMBER, portNumber);
        byte[] hardwareAddress = port.getHardwareAddress();
        String hardwareAddressString = HexString.toHexString(hardwareAddress);
        portInfo.put(PORT_HARDWARE_ADDRESS, hardwareAddressString);
        String name = port.getName();
        portInfo.put(PORT_NAME, name);
        long config = U32.f(port.getConfig());
        portInfo.put(PORT_CONFIG, config);
        long state = U32.f(port.getState());
        portInfo.put(PORT_STATE, state);
        long currentFeatures = U32.f(port.getCurrentFeatures());
        portInfo.put(PORT_CURRENT_FEATURES, currentFeatures);
        long advertisedFeatures = U32.f(port.getAdvertisedFeatures());
        portInfo.put(PORT_ADVERTISED_FEATURES, advertisedFeatures);
        long supportedFeatures = U32.f(port.getSupportedFeatures());
        portInfo.put(PORT_SUPPORTED_FEATURES, supportedFeatures);
        long peerFeatures = U32.f(port.getPeerFeatures());
        portInfo.put(PORT_PEER_FEATURES, peerFeatures);
        storageSource.updateRowAsync(PORT_TABLE_NAME, portInfo);
    }
    
    /**
     * Read switch port data from storage and write it into a switch object
     * @param sw the switch to update
     */
    protected void readSwitchPortStateFromStorage(OFSwitchImpl sw) {
        OperatorPredicate op = 
                new OperatorPredicate(PORT_SWITCH, 
                                      OperatorPredicate.Operator.EQ,
                                      sw.getStringId());
        IResultSet portResultSet = 
                storageSource.executeQuery(PORT_TABLE_NAME,
                                           null, op, null);
        //Map<Short, OFPhysicalPort> oldports = 
        //        new HashMap<Short, OFPhysicalPort>();
        //oldports.putAll(sw.getPorts());

        while (portResultSet.next()) {
            try {
                OFPhysicalPort p = new OFPhysicalPort();
                p.setPortNumber((short)portResultSet.getInt(PORT_NUMBER));
                p.setName(portResultSet.getString(PORT_NAME));
                p.setConfig((int)portResultSet.getLong(PORT_CONFIG));
                p.setState((int)portResultSet.getLong(PORT_STATE));
                String portMac = portResultSet.getString(PORT_HARDWARE_ADDRESS);
                p.setHardwareAddress(HexString.fromHexString(portMac));
                p.setCurrentFeatures((int)portResultSet.
                                     getLong(PORT_CURRENT_FEATURES));
                p.setAdvertisedFeatures((int)portResultSet.
                                        getLong(PORT_ADVERTISED_FEATURES));
                p.setSupportedFeatures((int)portResultSet.
                                       getLong(PORT_SUPPORTED_FEATURES));
                p.setPeerFeatures((int)portResultSet.
                                  getLong(PORT_PEER_FEATURES));
                //oldports.remove(Short.valueOf(p.getPortNumber()));
                sw.setPort(p);
            } catch (NullPointerException e) {
                // ignore
            }
        }
        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.PORTCHANGED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }
    
    protected void removePortInfo(IOFSwitch sw, short portNumber) {
        if (role == Role.SLAVE) {
            return;
        }
        String datapathIdString = sw.getStringId();
        String id = datapathIdString + "|" + portNumber;
        storageSource.deleteRowAsync(PORT_TABLE_NAME, id);
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
        Role role = null;
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
        OFBasicVendorId niciraVendorId = new OFBasicVendorId(
                OFNiciraVendorData.NX_VENDOR_ID, 4);
        OFVendorId.registerVendorId(niciraVendorId);
        OFBasicVendorDataType roleRequestVendorData =
                new OFBasicVendorDataType(
                        OFRoleRequestVendorData.NXT_ROLE_REQUEST,
                        OFRoleRequestVendorData.getInstantiable());
        niciraVendorId.registerVendorDataType(roleRequestVendorData);
        OFBasicVendorDataType roleReplyVendorData =
                new OFBasicVendorDataType(
                        OFRoleReplyVendorData.NXT_ROLE_REPLY,
                        OFRoleReplyVendorData.getInstantiable());
         niciraVendorId.registerVendorDataType(roleReplyVendorData);
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
        this.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();
        this.connectedSwitches = new HashSet<OFSwitchImpl>();
        this.controllerNodeIPsCache = new HashMap<String, String>();
        this.updates = new LinkedBlockingQueue<IUpdate>();
        this.factory = new BasicFactory();
        this.providerMap = new HashMap<String, List<IInfoProvider>>();
        setConfigParams(configParams);
        this.role = getInitialRole(configParams);
        this.roleChanger = new RoleChanger();
        initVendorMessages();
        this.systemStartTime = System.currentTimeMillis();
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
        storageSource.createTable(SWITCH_TABLE_NAME, null);
        storageSource.createTable(PORT_TABLE_NAME, null);
        storageSource.createTable(CONTROLLER_INTERFACE_TABLE_NAME, null);
        storageSource.createTable(SWITCH_CONFIG_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(CONTROLLER_TABLE_NAME,
                                             CONTROLLER_ID);
        storageSource.setTablePrimaryKeyName(SWITCH_TABLE_NAME,
                                             SWITCH_DATAPATH_ID);
        storageSource.setTablePrimaryKeyName(PORT_TABLE_NAME, PORT_ID);
        storageSource.setTablePrimaryKeyName(CONTROLLER_INTERFACE_TABLE_NAME, 
                                             CONTROLLER_INTERFACE_ID);
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
       
        // Add our REST API
        restApi.addRestletRoutable(new CoreWebRoutable());
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
    @SuppressWarnings("unchecked")
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
                    else if (curIP != discoveredIP) {
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
            controllerNodeIPsCache = (HashMap<String, String>) curControllerNodeIPs.clone();
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

}
