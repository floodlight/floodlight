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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.nio.channels.ClosedChannelException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFController;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFMessageListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchFilter;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFMessageFilterManager;
import net.floodlightcontroller.core.internal.OFChannelState.HandshakeState;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.core.web.JacksonCustomConverter;
import net.floodlightcontroller.core.web.RestletRoutable;
import static net.floodlightcontroller.counter.CounterValue.CounterType;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounter;
import net.floodlightcontroller.counter.CounterStore.NetworkLayer;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.learningswitch.LearningSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.perfmon.PktinProcessingTime;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageExceptionHandler;
import net.floodlightcontroller.storage.IStorageSource;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.storage.web.StorageWebRoutable;
import net.floodlightcontroller.topology.ITopologyAware;
import net.floodlightcontroller.topology.internal.TopologyImpl;

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
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U32;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The main controller class.  Handles all setup and network listeners
 */
public class Controller
    extends Application
    implements IFloodlightProvider, IOFController {
    
    protected static Logger log = LoggerFactory.getLogger(Controller.class);
    
    protected BasicFactory factory;
    protected ConcurrentMap<OFType, 
                            ListenerDispatcher<OFType, 
                                               IOFMessageListener>> messageListeners;
    protected ConcurrentHashMap<Long, IOFSwitch> switches;
    protected Set<IOFSwitchListener> switchListeners;
    protected BlockingQueue<Update> updates;
    protected CounterStore counterStore;

    protected ScheduledExecutorService executor = 
            Executors.newScheduledThreadPool(5);
    
    protected IStorageSource storageSource;
    protected TopologyImpl topology;
    protected DeviceManagerImpl deviceManager;
    protected LearningSwitch learningSwitch;
    protected OFMessageFilterManager messageFilterManager;
    protected PktinProcessingTime pktinProcTime;
    private StaticFlowEntryPusher staticFlowEntryPusher;
    
    protected List<RestletRoutable> restlets;

    protected static final int REST_PORT = 8080;
    protected static final int OPENFLOW_PORT = 6633;
    
    protected static final String CONTROLLER_TABLE_NAME = "controller_controller";
    protected static final String CONTROLLER_ID = "id";
    
    protected static final String SWITCH_TABLE_NAME = "controller_switch";
    protected static final String SWITCH_DATAPATH_ID = "dpid";
    protected static final String SWITCH_SOCKET_ADDRESS = "socket_address";
    protected static final String SWITCH_IP = "ip";
    protected static final String SWITCH_CONTROLLER_ID = "controller_id";
    protected static final String SWITCH_ACTIVE = "active";
    protected static final String SWITCH_CORE_SWITCH = "core_switch";
    protected static final String SWITCH_CONNECTED_SINCE = "connected_since";
    protected static final String SWITCH_CAPABILITIES = "capabilities";
    protected static final String SWITCH_BUFFERS = "buffers";
    protected static final String SWITCH_TABLES = "tables";
    protected static final String SWITCH_ACTIONS = "actions";
    
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
    
    protected class Update {
        public IOFSwitch sw;
        public boolean added;

        public Update(IOFSwitch sw, boolean added) {
            this.sw = sw;
            this.added = added;
        }
    }

    public Controller() {
        this.messageListeners =
            new ConcurrentHashMap<OFType, 
                                  ListenerDispatcher<OFType, 
                                                     IOFMessageListener>>();
        this.switchListeners = new CopyOnWriteArraySet<IOFSwitchListener>();
        this.updates = new LinkedBlockingQueue<Update>();
        this.restlets = new ArrayList<RestletRoutable>();
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
        public void channelConnected(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
            log.info("New switch connection from {}",
                     e.getChannel().getRemoteAddress());
            
            sw = new OFSwitchImpl();
            sw.setChannel(e.getChannel());
            sw.setFloodlightProvider(Controller.this);
            
            e.getChannel().write(factory.getMessage(OFType.HELLO));
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx,
                                        ChannelStateEvent e) throws Exception {
            if (sw != null && sw.getFeaturesReply() != null)
                removeSwitch(sw);
            log.info("Disconnected switch {}", sw);
        }

        @Override
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
            }
        }

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
                throws Exception {
            OFMessage m = factory.getMessage(OFType.ECHO_REQUEST);
            e.getChannel().write(m);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            try {
                if (e.getMessage() instanceof OFMessage) {
                    processOFMessage((OFMessage)e.getMessage());
                }
            } catch (Exception ex) {
                Channels.fireExceptionCaught(ctx, ex);
            }
        }
        
        /**
         * Process the request for the switch description
         */
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
                                storageSource.getRow(SWITCH_TABLE_NAME, swid);
                        for (Iterator<IResultSet> it = 
                                resultSet.iterator(); it.hasNext();) {
                            // In case of multiple rows, use the status
                            // in last row?
                            Map<String, Object> row = it.next().getRow();
                            if (row.containsKey(SWITCH_CORE_SWITCH)) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Reading SWITCH_IS_CORE_SWITCH " + 
                                              "config for switch={}, is-core={}",
                                              sw, row.get(SWITCH_CORE_SWITCH));
                                }
                                String ics = 
                                        (String)row.get(SWITCH_CORE_SWITCH);
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
                if (state.hasDescription && state.hasGetConfigReply) {
                    addSwitch(sw);
                    state.hsState = HandshakeState.READY;
                }
            }
            catch (InterruptedException ex) {
                // Ignore
            }
            catch (TimeoutException ex) {
                // Ignore
            } catch (Exception ex) {
                log.error("Exception in reading description " + 
                          " during handshake - {}", ex);
            }
        }

        /**
         * Send initial switch setup information that we need before adding
         * the switch
         * @throws IOException
         */
        void sendHelloConfiguration() throws IOException {
            // Send initial Features Request
            sw.write(factory.getMessage(OFType.FEATURES_REQUEST),
                    null);
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

            // Get Description to set switch specific flags
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.DESC);
            req.setLengthU(req.getLengthU());
            Future<List<OFStatistics>> dfuture = 
                    sw.getStatistics(req);
            sw.setAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE,
                    dfuture);

            // Delete all pre-existing flows
            sw.clearAllFlowMods();
        }
        
        /**
         * Dispatch an Openflow message from a switch to the appropriate
         * handler.
         * @param m The message to process
         * @throws IOException
         * @throws SwitchStateException 
         */
        protected void processOFMessage(OFMessage m)
                throws IOException, SwitchStateException {
            switch (m.getType()) {
                case HELLO:
                    log.debug("HELLO from {}", sw);
                    if (state.hsState.equals(HandshakeState.START)) {
                        state.hsState = HandshakeState.HELLO;
                        sendHelloConfiguration();
                    } else {
                        throw new SwitchStateException("Unexpected HELLO from " + sw);
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
                    log.debug("Features Reply from {}", sw);
                    if (state.hsState.equals(HandshakeState.HELLO)) {
                        sw.setFeaturesReply((OFFeaturesReply) m);
                        sendFeatureReplyConfiguration();
                        state.hsState = HandshakeState.FEATURES_REPLY;
                        // uncomment to enable "dumb" switches like cbench
                        // state.hsState = HandshakeState.READY;
                        // addSwitch(sw);
                    } else {
                        String em = "Unexpected FEATURES_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    break;
                case GET_CONFIG_REPLY:
                    if (!state.hsState.equals(HandshakeState.FEATURES_REPLY)) {
                        String em = "Unexpected GET_CONFIG_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    OFGetConfigReply cr = (OFGetConfigReply) m;
                    if (cr.getMissSendLength() == (short)0xffff) {
                        log.debug("Config Reply from {} confirms " + 
                                  "miss length set to 0xffff", sw);
                    } else {
                        log.warn("Config Reply from {} has " +
                                 "miss length set to {}", 
                                 sw, cr.getMissSendLength());                        
                    }
                    state.hasGetConfigReply = true;
                    if (state.hasDescription && state.hasGetConfigReply) {
                        addSwitch(sw);
                        state.hsState = HandshakeState.READY;
                    }
                    break;
                case ERROR:
                    OFError error = (OFError) m;
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
                /*
                 * "Trivial" server to test raw low-level throughput
                case PACKET_IN:
                    OFPacketIn pi = (OFPacketIn)m;

                    OFFlowMod fm = 
                            (OFFlowMod)factory.getMessage(OFType.FLOW_MOD);
                    OFMatch match = new OFMatch();
                    match.loadFromPacket(pi.getPacketData(), pi.getInPort());
                    fm.setBufferId(pi.getBufferId());
                    fm.setMatch(match);
                    sw.write(fm, null);
                    break; 
                */
                case PORT_STATUS:
                    boolean swadded = 
                        state.hsState.equals(HandshakeState.READY);
                    handlePortStatusMessage(sw, (OFPortStatus)m, swadded);
                    // fall through
                default:
                    if (!state.hsState.equals(HandshakeState.READY)) {
                        log.debug("Ignoring message type {} received " + 
                                  "from switch {} before switch is " + 
                                  "fully configured.", m.getType(), sw);
                        break;
                    }
                    handleMessage(sw, m, null);
                    break;
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
    }
    
    protected void updateCounters(IOFSwitch sw, OFMessage m, Ethernet eth) {
        OFPacketIn packet = (OFPacketIn)m;
        
        /* Extract the etherType and protocol field for IPv4 packet.
         */
        String etherType = String.format("%04x", eth.getEtherType());
        
        /*
         * Valid EtherType must be greater than or equal to 0x0600
         * It is V1 Ethernet Frame if EtherType < 0x0600
         */
        if (eth.getEtherType() < 0x0600) {
            etherType = "0599";
        }

        if (TypeAliases.l3TypeAliasMap != null && 
            TypeAliases.l3TypeAliasMap.containsKey(etherType)) {
            etherType = TypeAliases.l3TypeAliasMap.get(etherType);
        } else {
            etherType = "L3_" + etherType;
        }
        String switchIdHex = sw.getStringId();
   
        String packetName = m.getType().toClass().getName();
        packetName = packetName.substring(packetName.lastIndexOf('.')+1); 
        
        // Construct both port and switch counter for the packet_in
        String portCounterName =
                CounterStore.createCounterName(switchIdHex, 
                                               packet.getInPort(),
                                               packetName);
        String switchCounterName =
                CounterStore.createCounterName(switchIdHex, 
                                               -1,
                                               packetName);
        
        String portL3CategoryCounterName = 
                CounterStore.createCounterName(switchIdHex, 
                                               packet.getInPort(),
                                               packetName, 
                                               etherType, 
                                               NetworkLayer.L3);
        String switchL3CategoryCounterName =
                CounterStore.createCounterName(switchIdHex, 
                                               -1, 
                                               packetName, 
                                               etherType, 
                                               NetworkLayer.L3);
        
        try {
            ICounter portCounter = 
                    counterStore.getCounter(portCounterName);
            if (portCounter == null) {
                portCounter = 
                        counterStore.createCounter(portCounterName, 
                                                   CounterType.LONG);
            }
            ICounter switchCounter = 
                    counterStore.getCounter(switchCounterName);
            if (switchCounter == null) {
                switchCounter = 
                        counterStore.createCounter(switchCounterName, 
                                                   CounterType.LONG);
            }
            ICounter portL3Counter = 
                    counterStore.getCounter(portL3CategoryCounterName);
            if (portL3Counter == null) {
                portL3Counter = 
                        counterStore.createCounter(portL3CategoryCounterName,
                                                   CounterType.LONG);
            }
            ICounter switchL3Counter = 
                    counterStore.getCounter(switchL3CategoryCounterName);
            if (switchL3Counter == null) {
                switchL3Counter = 
                        counterStore.createCounter(switchL3CategoryCounterName,
                                                   CounterType.LONG);
            }
            portCounter.increment();
            switchCounter.increment();
            portL3Counter.increment();
            switchL3Counter.increment();
            
            if (etherType.compareTo(CounterStore.L3ET_IPV4) == 0) {
                IPv4 ipV4 = (IPv4)eth.getPayload();
                String l4Type = String.format("%02x", ipV4.getProtocol());
                if (TypeAliases.l4TypeAliasMap != null && 
                    TypeAliases.l4TypeAliasMap.containsKey(l4Type)) {
                    l4Type = "L4_" + TypeAliases.l4TypeAliasMap.get(l4Type);
                } else {
                    l4Type = "L4_" + l4Type;
                }
                String portL4CategoryCounterName =
                        CounterStore.createCounterName(switchIdHex, 
                                                       packet.getInPort(), 
                                                       packetName, 
                                                       l4Type, 
                                                       NetworkLayer.L4);
                String switchL4CategoryCounterName = 
                        CounterStore.createCounterName(switchIdHex, 
                                                       -1, 
                                                       packetName, 
                                                       l4Type, 
                                                       NetworkLayer.L4);
                
                ICounter portL4Counter = 
                        counterStore.getCounter(portL4CategoryCounterName);
                if (portL4Counter == null) {
                    portL4Counter = 
                            counterStore.createCounter(portL4CategoryCounterName, 
                                                       CounterType.LONG);
                }
                ICounter switchL4Counter = 
                        counterStore.getCounter(switchL4CategoryCounterName);
                if (switchL4Counter == null) {
                    switchL4Counter = 
                            counterStore.createCounter(switchL4CategoryCounterName, 
                                                       CounterType.LONG);
                }
                portL4Counter.increment();
                switchL4Counter.increment();
            }

        }
        catch (IllegalArgumentException e) {
            log.error("Invalid Counter, " + portCounterName + 
                      " or " + switchCounterName);
        }
    }
    
    /**
     * Handle replies to certain OFMessages, and pass others off to listeners
     * @param sw The switch for the message
     * @param m The message
     * @param bContext The floodlight context. If null then floodlight context would
     * be allocated in this function
     * @throws IOException
     */
    protected void handleMessage(IOFSwitch sw, OFMessage m,
                                 FloodlightContext bContext)
            throws IOException {
        Ethernet eth = null;

        switch (m.getType()) {
            case PACKET_IN:
                OFPacketIn pi = (OFPacketIn)m;
                eth = new Ethernet();
                eth.deserialize(pi.getPacketData(), 0,
                                pi.getPacketData().length);
                updateCounters(sw, m, eth);

                // fall through to default case...
            default:
                
                List<IOFMessageListener> listeners = null;
                if (messageListeners.containsKey(m.getType())) {
                    listeners = messageListeners.get(m.getType()).
                            getOrderedListeners();
                }
                        
                if (listeners != null) {
                    // Check if floodlight context is passed from the calling 
                    // function, if so use that floodlight context, otherwise 
                    // allocate one
                    FloodlightContext bc;
                    if (bContext == null) {
                        bc = new FloodlightContext();
                    } else {
                        bc = bContext;
                    }
                    if (eth != null) {
                        IFloodlightProvider.bcStore.put(bc, 
                                IFloodlightProvider.CONTEXT_PI_PAYLOAD, 
                                eth);
                    }
                    
                    // Get the starting time (overall and per-component) of 
                    // the processing chain for this packet if performance
                    // monitoring is turned on
                    long startTime_ns = pktinProcTime.getStartTimeOnePkt();
                    long compStartTime_ns;                       
                    Command cmd;
                    for (IOFMessageListener listener : listeners) {
                        if (listener instanceof IOFSwitchFilter) {
                            if (!((IOFSwitchFilter)listener).isInterested(sw)) {
                                continue;
                            }
                        }

                        // Get the start time of processing current packet for this listener
                        compStartTime_ns = 
                                pktinProcTime.getStartTimeOneComponent();
                        cmd = listener.receive(sw, m, bc);
                        pktinProcTime.
                            updateCumulativeTimeOneComp(compStartTime_ns,
                                                        listener.getId());
                        
                        if (Command.STOP.equals(cmd)) {
                            break;
                        }
                    }
                    pktinProcTime.updateCumulativeTimeTotal(startTime_ns);
                } else {
                    log.error("Unhandled OF Message: {} from {}", m, sw);
                }
                break;
        }
    }
    
    /**
     * Log an OpenFlow error message from a switch
     * @param sw The switch that sent the error
     * @param error The error message
     */
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
     * Adds a switch that has connected and returned a features reply, then
     * calls all related listeners
     * @param sw the new switch
     */
    protected void addSwitch(IOFSwitch sw) {
        IOFSwitch oldSw = this.switches.put(sw.getId(), sw);
        if (sw == oldSw) {
            // Note == for object equality, not .equals for value
            log.info("New add switch for pre-existing switch {}", sw);
            return;
        }
        
        log.info("Switch handshake successful: {}", sw);

        if (oldSw != null) {
            log.error("New switch connection {} for already-connected switch {}",
                      sw, oldSw);
            oldSw.setConnected(false);
            if (sw.getFeaturesReply() != null)
                    updateInactiveSwitchInfo(sw);

            // we need to clean out old switch state definitively 
            // before adding the new switch
            if (switchListeners != null) {
                for (IOFSwitchListener listener : switchListeners) {
                    listener.removedSwitch(oldSw);
                }
            }
        }
        
        updateActiveSwitchInfo(sw);
        Update update = new Update(sw, true);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    /**
     * Removes a disconnected switch and calls all related listeners
     * @param sw the switch that has disconnected
     */
    protected void removeSwitch(IOFSwitch sw) {
        if (!this.switches.remove(sw.getId(), sw) ||
            (sw.isConnected() == false)) {
            log.debug("Not removing switch {}; already removed", sw);
            return;
        }
        
        sw.setConnected(false);
        updateInactiveSwitchInfo(sw);
        Update update = new Update(sw, false);
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
        
        if (log.isDebugEnabled()) {
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

    @Override
    public synchronized void removeOFMessageListener(OFType type,
                                                     IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd = 
            messageListeners.get(type);
        if (ldd != null) {
            ldd.removeListener(listener);
        }
    }
    
    public void removeOFMessageListeners(OFType type) {
        messageListeners.remove(type);
    }

    @Override
    public Map<Long, IOFSwitch> getSwitches() {
        return this.switches;
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
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg,
                                   FloodlightContext bc) {
        // We need to make sure the switch is still connected
        if (!switches.containsKey(sw.getId())) return false;
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
    public ScheduledExecutorService getScheduledExecutor() {
        return executor;
    }

    @Override
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
    
    // *************
    // IOFController
    // *************
    
    @Override
    public String getControllerId() {
        return "localhost";
    }
    
    // ***********
    // Application
    // ***********

    @Override
    public Restlet createInboundRoot() {
        Context context = getContext();
        initRestContext(context);
        
        Router baseRouter = new Router(context);
        baseRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);
        for (RestletRoutable rr : restlets) {
            baseRouter.attach(rr.basePath(), rr.getRestlet(context));
        }

        Filter slashFilter = new Filter() {            
            @Override
            protected int beforeHandle(Request request, Response response) {
                Reference ref = request.getResourceRef();
                String originalPath = ref.getPath();
                if (originalPath.contains("//"))
                {
                    String newPath = originalPath.replaceAll("/+", "/");
                    ref.setPath(newPath);
                }
                return Filter.CONTINUE;
            }

        };
        slashFilter.setNext(baseRouter);
        
        return slashFilter;
    }
    
    // **************
    // Initialization
    // **************
    
    /**
     * Call after init() has run, but before this.run()
     * @throws IOException
     */
    
    protected void startUp() throws IOException {
        // Connect to the storage source to update info about this controller
        // node, and wait indefinitely if the storage source is unavailable;
        // don't want switches to connect before we have a database
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
        log.info("Connected to storage source");
        
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        
        this.factory = new BasicFactory();
    }

    protected void shutDown() {
        try {
            removeControllerInfo();
        } catch (StorageException e) {
        }
        
        log.info("Shutdown complete");
    }

    protected void setStorageSource(IStorageSource storageSource) {
        this.storageSource = storageSource;
        IStorageExceptionHandler handler = 
                new TerminationStorageExceptionHandler(this);
        storageSource.setExceptionHandler(handler);
        storageSource.createTable(CONTROLLER_TABLE_NAME, null);
        storageSource.createTable(SWITCH_TABLE_NAME, null);
        storageSource.createTable(PORT_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(CONTROLLER_TABLE_NAME,
                                             CONTROLLER_ID);
        storageSource.setTablePrimaryKeyName(SWITCH_TABLE_NAME,
                                             SWITCH_DATAPATH_ID);
        storageSource.setTablePrimaryKeyName(PORT_TABLE_NAME, PORT_ID);
    }

    protected void updateAllInactiveSwitchInfo() {
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
//        String listenAddress = getListenAddress();
//        controllerInfo.put(CONTROLLER_LISTEN_ADDRESS, listenAddress);
//        int listenPort = getListenPort();
//        controllerInfo.put(CONTROLLER_LISTEN_PORT, listenPort);
//        Date startupDate = new Date();
//        controllerInfo.put(CONTROLLER_LAST_STARTUP, startupDate);
//        controllerInfo.put(CONTROLLER_ACTIVE, Boolean.TRUE);
        storageSource.updateRow(CONTROLLER_TABLE_NAME, controllerInfo);
    }
    
    protected void removeControllerInfo() {
        // Update the controller info in the storage source to be inactive
//        Map<String, Object> controllerInfo = new HashMap<String, Object>();
//        String id = getControllerId();
//        controllerInfo.put(CONTROLLER_ID, id);
//        controllerInfo.put(CONTROLLER_ACTIVE, Boolean.FALSE);
//        storageSource.updateRow(CONTROLLER_TABLE_NAME, controllerInfo);
        
        updateAllInactiveSwitchInfo();
    }
    
    protected void updateActiveSwitchInfo(IOFSwitch sw) {
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
        OFFeaturesReply featuresReply = sw.getFeaturesReply();
        long capabilities = U32.f(featuresReply.getCapabilities());
        switchInfo.put(SWITCH_CAPABILITIES, capabilities);
        long buffers = U32.f(featuresReply.getBuffers());
        switchInfo.put(SWITCH_BUFFERS, buffers);
        long tables = U32.f(featuresReply.getTables());
        switchInfo.put(SWITCH_TABLES, tables);
        long actions = U32.f(featuresReply.getActions());
        switchInfo.put(SWITCH_ACTIONS, actions);
        switchInfo.put(SWITCH_ACTIVE, Boolean.TRUE);
        
        // Update the switch
        storageSource.updateRowAsync(SWITCH_TABLE_NAME, switchInfo);
        
        // Update the ports
        for (OFPhysicalPort port: sw.getPorts().values()) {
            updatePortInfo(sw, port);
        }
    }
    
    protected void updateInactiveSwitchInfo(IOFSwitch sw) {
        // Update the controller info in the storage source to be inactive
        Map<String, Object> switchInfo = new HashMap<String, Object>();
        String datapathIdString = sw.getStringId();
        switchInfo.put(SWITCH_DATAPATH_ID, datapathIdString);
        //switchInfo.put(SWITCH_CONNECTED_SINCE, null);
        switchInfo.put(SWITCH_ACTIVE, Boolean.FALSE);
        storageSource.updateRowAsync(SWITCH_TABLE_NAME, switchInfo);
    }

    protected void updatePortInfo(IOFSwitch sw, OFPhysicalPort port) {
        String datapathIdString = sw.getStringId();
        Map<String, Object> portInfo = new HashMap<String, Object>();
        int portNumber = U16.f(port.getPortNumber());
        String id = datapathIdString + ":" + portNumber;
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
    
    protected void removePortInfo(IOFSwitch sw, short portNumber) {
        String datapathIdString = sw.getStringId();
        String id = datapathIdString + ":" + portNumber;
        storageSource.deleteRowAsync(PORT_TABLE_NAME, id);
    }

    /**
     * Tell controller that we're ready to accept switches loop
     * @throws IOException 
     */
    protected void run() {
        try {            
            // Start listening for REST requests
            final Component component = new Component();
            component.getServers().add(Protocol.HTTP, REST_PORT);
            component.getDefaultHost().attach(this);
            
            component.start();

            // Start listening for switch connections
            //int threads = 
            //       Runtime.getRuntime().availableProcessors() * 2 + 1;
            //int threads = 1;
            //long maxMem = Runtime.getRuntime().maxMemory() * 1 / 3;
            //long memPerChannel = 2 * 1024 * 1024;

            final ServerBootstrap bootstrap = new ServerBootstrap(
                    new NioServerSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));

            bootstrap.setOption("reuseAddr", true);
            bootstrap.setOption("child.keepAlive", true);
            bootstrap.setOption("child.tcpNoDelay", true);

            //OrderedMemoryAwareThreadPoolExecutor pipelineExecutor =
            //        new OrderedMemoryAwareThreadPoolExecutor(
            //                threads, memPerChannel, maxMem,
            //                1000, TimeUnit.SECONDS,
            //                Executors.defaultThreadFactory());

            ChannelPipelineFactory pfact = 
                    new OpenflowPipelineFactory(this, null);
            bootstrap.setPipelineFactory(pfact);
            InetSocketAddress sa = new InetSocketAddress(OPENFLOW_PORT);
            final ChannelGroup cg = new DefaultChannelGroup();
            cg.add(bootstrap.bind(sa));
            
            log.info("Listening for switch connections on {}", sa);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // main loop
        while (true) {
            try {
                Update update = updates.take();
                log.debug("Dispatching switch update {} {}",
                          update.sw, update.added);
                if (switchListeners != null) {
                    for (IOFSwitchListener listener : switchListeners) {
                        if (update.added)
                            listener.addedSwitch(update.sw);
                        else
                            listener.removedSwitch(update.sw);
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (StorageException e) {
                log.error("Storage exception in controller " + 
                          "updates loop; terminating process",
                          e);
                return;
            } catch (Exception e) {
                log.error("Exception in controller updates loop", e);
            }
        }
    }
    
    /**
     * Initialize all of the controller's components; override me for
     * new components
     */
    protected void init() {
        topology = new TopologyImpl();
        deviceManager = new DeviceManagerImpl();
        storageSource = new MemoryStorageSource();
        learningSwitch = new LearningSwitch();
        counterStore = new CounterStore();
        messageFilterManager = new OFMessageFilterManager();
        pktinProcTime = new PktinProcessingTime();
        
        topology.setFloodlightProvider(this);
        topology.setStorageSource(storageSource);
         
        deviceManager.setFloodlightProvider(this);
        deviceManager.setStorageSource(storageSource);
        deviceManager.setTopology(topology);
        
        messageFilterManager.init(this);
        
        staticFlowEntryPusher = new StaticFlowEntryPusher();
        staticFlowEntryPusher.setFloodlightProvider(this);
        
        learningSwitch.setFloodlightProvider(this);
        learningSwitch.setCounterStore(counterStore);
        
        // call this explicitly because it does setup
        this.setStorageSource(storageSource);        
        
        HashSet<ITopologyAware> topologyAware = new HashSet<ITopologyAware>();
        topologyAware.add(deviceManager);
        topology.setTopologyAware(topologyAware);

        restlets.add(new CoreWebRoutable());
        restlets.add(new StorageWebRoutable());
        JacksonCustomConverter.replaceConverter();
    }
    
    /**
     * Initialize the rest context
     */
    protected void initRestContext(Context context) {
        context.getAttributes().put("floodlightProvider", this);
        context.getAttributes().put("counterStore", counterStore);
        context.getAttributes().put("storageSource", storageSource);
        context.getAttributes().put("deviceManager", deviceManager);
        context.getAttributes().put("messageFilterManager",
                                                        messageFilterManager);
        context.getAttributes().put("pktinProcessingTime", pktinProcTime);
        context.getAttributes().put("staticFlowEntryPusher", 
                                                        staticFlowEntryPusher);
        context.getAttributes().put("topology", topology);
    }
    
    /**
     * Startup all of the controller's components
     */
    protected void startupComponents() {
        // now, do our own init
        try {
            log.debug("Doing controller internal setup");
            this.startUp();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        log.debug("Starting topology service");
        topology.startUp();
        log.debug("Starting deviceManager service");
        deviceManager.startUp();
        // no need to do storageSource.startUp()
        log.debug("Starting counterStore service");
        counterStore.startUp();
        log.debug("Starting learningSwitch service");
        learningSwitch.startUp();
        
        log.debug("Starting messageFilter service");
        messageFilterManager.startUp();
  
        log.debug("Starting staticFlowEntryPusher service");
        staticFlowEntryPusher.startUp();
    }
    
    /** 
     * Main function entry point; override init() for adding modules
     * @param args
     */
    
    public static void main(String args[]) throws Exception {
        System.setProperty("org.restlet.engine.loggerFacadeClass", 
                           "org.restlet.ext.slf4j.Slf4jLoggerFacade");
        
        Controller controller = new Controller();
        controller.init();
        controller.startupComponents();
        controller.run();
    }
}
