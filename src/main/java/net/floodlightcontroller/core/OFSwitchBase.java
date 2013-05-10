/**
*    Copyright 2012, Big Switch Networks, Inc.
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

package net.floodlightcontroller.core;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.core.internal.OFFeaturesReplyFuture;
import net.floodlightcontroller.core.internal.OFStatisticsFuture;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterType;
import net.floodlightcontroller.debugcounter.NullDebugCounter;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.util.TimedCache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.jboss.netty.channel.Channel;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the internal representation of an openflow switch.
 */
public abstract class OFSwitchBase implements IOFSwitch {
    // TODO: should we really do logging in the class or should we throw
    // exception that can then be handled by callers?
    protected static final Logger log = LoggerFactory.getLogger(OFSwitchBase.class);

    protected ConcurrentMap<Object, Object> attributes;
    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPool;
    protected IDebugCounterService debugCounters;
    protected Date connectedSince;

    /* Switch features from initial featuresReply */
    protected int capabilities;
    protected int buffers;
    protected int actions;
    protected byte tables;
    protected long datapathId;
    protected String stringId;

    /**
     * Members hidden from subclasses
     */
    private Channel channel;
    private final AtomicInteger transactionIdSource;
    // Lock to protect modification of the port maps. We only need to
    // synchronize on modifications. For read operations we are fine since
    // we rely on ConcurrentMaps which works for our use case.
    protected Object portLock;
    // Map port numbers to the appropriate OFPhysicalPort
    protected ConcurrentHashMap<Short, OFPhysicalPort> portsByNumber;
    // Map port names to the appropriate OFPhyiscalPort
    // XXX: The OF spec doesn't specify if port names need to be unique but
    //      according it's always the case in practice.
    private final ConcurrentHashMap<String, OFPhysicalPort> portsByName;
    private final Map<Integer,OFStatisticsFuture> statsFutureMap;
    private final Map<Integer, IOFMessageListener> iofMsgListenersMap;
    private final Map<Integer,OFFeaturesReplyFuture> featuresFutureMap;
    private volatile boolean connected;
    private volatile Role role;
    private final TimedCache<Long> timedCache;
    private final ReentrantReadWriteLock listenerLock;
    private final ConcurrentMap<Short, AtomicLong> portBroadcastCacheHitMap;

    // Private members for throttling
    private boolean writeThrottleEnabled = false;
    protected boolean packetInThrottleEnabled = false; // used by test
    private int packetInRateThresholdHigh = Integer.MAX_VALUE;
    private int packetInRateThresholdLow = 1;
    private int packetInRatePerMacThreshold = Integer.MAX_VALUE;
    private int packetInRatePerPortThreshold = Integer.MAX_VALUE;
    private long messageCount = 0;
    private long messageCountUniqueOFMatch = 0;
    private long lastMessageTime;
    private int currentRate = 0;
    private TimedCache<OFMatch> ofMatchCache;
    private TimedCache<Long> macCache;
    private TimedCache<Long> macBlockedCache;
    private TimedCache<Short> portCache;
    private TimedCache<Short> portBlockedCache;

    protected OFDescriptionStatistics description;

    private boolean debugCountersRegistered;

    protected final static ThreadLocal<Map<IOFSwitch,List<OFMessage>>> local_msg_buffer =
            new ThreadLocal<Map<IOFSwitch,List<OFMessage>>>() {
        @Override
        protected Map<IOFSwitch,List<OFMessage>> initialValue() {
            return new HashMap<IOFSwitch,List<OFMessage>>();
        }
    };

    public static final int OFSWITCH_APP_ID = 5;

    public OFSwitchBase() {
        this.stringId = null;
        this.attributes = new ConcurrentHashMap<Object, Object>();
        this.connectedSince = new Date();
        this.transactionIdSource = new AtomicInteger();
        this.portLock = new Object();
        this.portsByNumber = new ConcurrentHashMap<Short, OFPhysicalPort>();
        this.portsByName = new ConcurrentHashMap<String, OFPhysicalPort>();
        this.connected = false;
        this.statsFutureMap = new ConcurrentHashMap<Integer,OFStatisticsFuture>();
        this.featuresFutureMap = new ConcurrentHashMap<Integer,OFFeaturesReplyFuture>();
        this.iofMsgListenersMap = new ConcurrentHashMap<Integer,IOFMessageListener>();
        this.role = null;
        this.timedCache = new TimedCache<Long>(100, 5*1000 );  // 5 seconds interval
        this.listenerLock = new ReentrantReadWriteLock();
        this.portBroadcastCacheHitMap = new ConcurrentHashMap<Short, AtomicLong>();
        this.description = new OFDescriptionStatistics();
        this.lastMessageTime = System.currentTimeMillis();

        // Defaults properties for an ideal switch
        this.setAttribute(PROP_FASTWILDCARDS, OFMatch.OFPFW_ALL);
        this.setAttribute(PROP_SUPPORTS_OFPP_FLOOD, Boolean.valueOf(true));
        this.setAttribute(PROP_SUPPORTS_OFPP_TABLE, Boolean.valueOf(true));
    }


    @Override
    public boolean attributeEquals(String name, Object other) {
        Object attr = this.attributes.get(name);
        if (attr == null)
            return false;
        return attr.equals(other);
    }


    @Override
    public Object getAttribute(String name) {
        // returns null if key doesn't exist
        return this.attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        this.attributes.put(name, value);
        return;
    }

    @Override
    public Object removeAttribute(String name) {
        return this.attributes.remove(name);
    }

    @Override
    public boolean hasAttribute(String name) {
        return this.attributes.containsKey(name);
    }

    @Override
    @JsonIgnore
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    // For driver subclass to set throttling
    protected void enableWriteThrottle(boolean enable) {
        this.writeThrottleEnabled = enable;
    }

    @Override
    public boolean isWriteThrottleEnabled() {
        return this.writeThrottleEnabled;
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Drop throttled OF message to switch {switch}",
                explanation="The controller is sending more messages" +
                "than the switch can handle. Some messages are dropped" +
                "to prevent switch outage",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })
    public void writeThrottled(OFMessage m, FloodlightContext bc)
            throws IOException {
        if (channel == null || !isConnected())
            return;
        /**
         * By default, channel uses an unbounded send queue. Enable throttling
         * prevents the queue from growing big.
         *
         * channel.isWritable() returns true when queue length is less than
         * high water mark (64 kbytes). Once exceeded, isWritable() becomes
         * false after queue length drops below low water mark (32 kbytes).
         */
        if (!writeThrottleEnabled || channel.isWritable()) {
            write(m, bc);
        } else {
            // Let logback duplicate filtering take care of excessive logs
            debugCounters.updateCounter(stringId + "-writeDrops");
            log.warn("Drop throttled OF message to switch {}", this);
        }
    }

    @Override
    public void writeThrottled(List<OFMessage> msglist, FloodlightContext bc)
            throws IOException {
        if (!writeThrottleEnabled || channel.isWritable()) {
            write(msglist, bc);
        } else {
            // Let logback duplicate filtering take care of excessive logs
            debugCounters.updateCounter(stringId + "-writeDrops",
                    msglist.size());
            log.warn("Drop throttled OF messages to switch {}", this);
        }
    }

    @Override
    public void write(OFMessage m, FloodlightContext bc) {
        if (channel == null || !isConnected())
            return;
            //throws IOException {
        Map<IOFSwitch,List<OFMessage>> msg_buffer_map = local_msg_buffer.get();
        List<OFMessage> msg_buffer = msg_buffer_map.get(this);
        if (msg_buffer == null) {
            msg_buffer = new ArrayList<OFMessage>();
            msg_buffer_map.put(this, msg_buffer);
        }

        this.floodlightProvider.handleOutgoingMessage(this, m, bc);
        msg_buffer.add(m);

        if ((msg_buffer.size() >= Controller.BATCH_MAX_SIZE) ||
            ((m.getType() != OFType.PACKET_OUT) && (m.getType() != OFType.FLOW_MOD))) {
            this.write(msg_buffer);
            msg_buffer.clear();
        }
    }
    @Override
    @LogMessageDoc(level="WARN",
                   message="Sending OF message that modifies switch " +
                           "state while in the slave role: {switch}",
                   explanation="An application has sent a message to a switch " +
                           "that is not valid when the switch is in a slave role",
                   recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public void write(List<OFMessage> msglist,
                      FloodlightContext bc) {
        if (channel == null || !isConnected())
            return;
        for (OFMessage m : msglist) {
            if (role == Role.SLAVE) {
                switch (m.getType()) {
                    case PACKET_OUT:
                    case FLOW_MOD:
                    case PORT_MOD:
                        log.warn("Sending OF message that modifies switch " +
                                 "state while in the slave role: {}",
                                 m.getType().name());
                        break;
                    default:
                        break;
                }
            }
            this.floodlightProvider.handleOutgoingMessage(this, m, bc);
        }
        this.write(msglist);
    }

    /**
     * Not callable by writers, but allow IOFSwitch implementation to override
     * @param msglist
     * @throws IOException
     */
    protected void write(List<OFMessage> msglist) {
        if (channel == null || !isConnected())
            return;
        this.channel.write(msglist);
    }

    @Override
    public void disconnectOutputStream() {
        if (channel == null)
            return;
        channel.close();
    }

    @Override
    @JsonIgnore
    public void setFeaturesReply(OFFeaturesReply featuresReply) {
        synchronized(portLock) {
            if (stringId == null) {
                /* ports are updated via port status message, so we
                 * only fill in ports on initial connection.
                 */
                for (OFPhysicalPort port : featuresReply.getPorts()) {
                    setPort(port);
                }
            }
            this.datapathId = featuresReply.getDatapathId();
            this.capabilities = featuresReply.getCapabilities();
            this.buffers = featuresReply.getBuffers();
            this.actions = featuresReply.getActions();
            this.tables = featuresReply.getTables();
            this.stringId = HexString.toHexString(this.datapathId);
        }
    }

    @Override
    @JsonIgnore
    public Collection<OFPhysicalPort> getEnabledPorts() {
        List<OFPhysicalPort> result =
                new ArrayList<OFPhysicalPort>(portsByNumber.size());
        for (OFPhysicalPort port : portsByNumber.values()) {
            if (portEnabled(port)) {
                result.add(port);
            }
        }
        return result;
    }

    @Override
    @JsonIgnore
    public Collection<Short> getEnabledPortNumbers() {
        List<Short> result =
                new ArrayList<Short>(portsByNumber.size());
        for (OFPhysicalPort port : portsByNumber.values()) {
            if (portEnabled(port)) {
                result.add(port.getPortNumber());
            }
        }
        return result;
    }

    @Override
    public OFPhysicalPort getPort(short portNumber) {
        return portsByNumber.get(portNumber);
    }

    @Override
    public OFPhysicalPort getPort(String portName) {
        return portsByName.get(portName);
    }

    @Override
    @JsonIgnore
    public void setPort(OFPhysicalPort port) {
        synchronized(portLock) {
            portsByNumber.put(port.getPortNumber(), port);
            portsByName.put(port.getName(), port);
        }
    }

    @Override
    @JsonProperty("ports")
    public Collection<OFPhysicalPort> getPorts() {
        return Collections.unmodifiableCollection(portsByNumber.values());
    }

    @Override
    public void deletePort(short portNumber) {
        synchronized(portLock) {
            portsByName.remove(portsByNumber.get(portNumber).getName());
            portsByNumber.remove(portNumber);
        }
    }

    @Override
    public void deletePort(String portName) {
        synchronized(portLock) {
            portsByNumber.remove(portsByName.get(portName).getPortNumber());
            portsByName.remove(portName);
        }
    }

    @Override
    public boolean portEnabled(short portNumber) {
        if (portsByNumber.get(portNumber) == null) return false;
        return portEnabled(portsByNumber.get(portNumber));
    }

    @Override
    public boolean portEnabled(String portName) {
        if (portsByName.get(portName) == null) return false;
        return portEnabled(portsByName.get(portName));
    }

    @Override
    public boolean portEnabled(OFPhysicalPort port) {
        if (port == null)
            return false;
        if ((port.getConfig() & OFPortConfig.OFPPC_PORT_DOWN.getValue()) > 0)
            return false;
        if ((port.getState() & OFPortState.OFPPS_LINK_DOWN.getValue()) > 0)
            return false;
        // Port STP state doesn't work with multiple VLANs, so ignore it for now
        // if ((port.getState() & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue())
        //    return false;
        return true;
    }

    @Override
    @JsonSerialize(using=DPIDSerializer.class)
    @JsonProperty("dpid")
    public long getId() {
        if (this.stringId == null)
            throw new RuntimeException("Features reply has not yet been set");
        return this.datapathId;
    }

    @JsonIgnore
    @Override
    public String getStringId() {
        return stringId;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String channelString =
                (channel != null) ? channel.getRemoteAddress().toString() :
                                    "?";
        return "OFSwitchBase [" + channelString + " DPID[" + ((stringId != null) ? stringId : "?") + "]]";
    }

    @Override
    public ConcurrentMap<Object, Object> getAttributes() {
        return this.attributes;
    }

    @Override
    public Date getConnectedSince() {
        return connectedSince;
    }

    @JsonIgnore
    @Override
    public int getNextTransactionId() {
        return this.transactionIdSource.incrementAndGet();
    }

    @Override
    public void sendStatsQuery(OFStatisticsRequest request, int xid,
                                IOFMessageListener caller) throws IOException {
        request.setXid(xid);
        this.iofMsgListenersMap.put(xid, caller);
        List<OFMessage> msglist = new ArrayList<OFMessage>(1);
        msglist.add(request);
        this.write(msglist);
        return;
    }

    @Override
    public Future<List<OFStatistics>> queryStatistics(OFStatisticsRequest request) throws IOException {
        request.setXid(getNextTransactionId());
        OFStatisticsFuture future = new OFStatisticsFuture(threadPool, this, request.getXid());
        this.statsFutureMap.put(request.getXid(), future);
        List<OFMessage> msglist = new ArrayList<OFMessage>(1);
        msglist.add(request);
        this.write(msglist);
        return future;
    }

    @Override
    public void deliverStatisticsReply(OFMessage reply) {
        OFStatisticsFuture future = this.statsFutureMap.get(reply.getXid());
        if (future != null) {
            future.deliverFuture(this, reply);
            // The future will ultimately unregister itself and call
            // cancelStatisticsReply
            return;
        }
        /* Transaction id was not found in statsFutureMap.check the other map */
        IOFMessageListener caller = this.iofMsgListenersMap.get(reply.getXid());
        if (caller != null) {
            caller.receive(this, reply, null);
        }
    }

    @Override
    public void cancelStatisticsReply(int transactionId) {
        if (null ==  this.statsFutureMap.remove(transactionId)) {
            this.iofMsgListenersMap.remove(transactionId);
        }
    }

    @Override
    public void cancelAllStatisticsReplies() {
        /* we don't need to be synchronized here. Even if another thread
         * modifies the map while we're cleaning up the future will eventuall
         * timeout */
        for (OFStatisticsFuture f : statsFutureMap.values()) {
            f.cancel(true);
        }
        statsFutureMap.clear();
        iofMsgListenersMap.clear();
    }


    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    @JsonIgnore
    public void setFloodlightProvider(
            IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    @Override
    @JsonIgnore
    public void setThreadPoolService(IThreadPoolService tp) {
        this.threadPool = tp;
    }

    @Override
    @JsonIgnore
    public void setDebugCounterService(IDebugCounterService debugCounters) {
        this.debugCounters = debugCounters;
        registerOverloadCounters();
    }

    @JsonIgnore
    @Override
    public boolean isConnected() {
        // no lock needed since we use volatile
        return connected;
    }

    @JsonIgnore
    @Override
    public boolean isActive() {
        // no lock needed since we use volatile
        return isConnected() && this.role == Role.MASTER;
    }

    @Override
    @JsonIgnore
    public void setConnected(boolean connected) {
        // No lock needed since we use volatile
        this.connected = connected;
    }

    @Override
    public Role getHARole() {
        return role;
    }

    @JsonIgnore
    @Override
    public void setHARole(Role role) {
        this.role = role;
    }

    @Override
    public void clearAllFlowMods() {
        if (channel == null || !isConnected())
            return;
        // Delete all pre-existing flows
        OFMatch match = new OFMatch().setWildcards(OFMatch.OFPFW_ALL);
        OFMessage fm = ((OFFlowMod) floodlightProvider.getOFMessageFactory()
            .getMessage(OFType.FLOW_MOD))
                .setMatch(match)
            .setCommand(OFFlowMod.OFPFC_DELETE)
            .setOutPort(OFPort.OFPP_NONE)
            .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));
        fm.setXid(getNextTransactionId());
        OFMessage barrierMsg = floodlightProvider.getOFMessageFactory().getMessage(
                OFType.BARRIER_REQUEST);
        barrierMsg.setXid(getNextTransactionId());
        List<OFMessage> msglist = new ArrayList<OFMessage>(2);
        msglist.add(fm);
        msglist.add(barrierMsg);
        channel.write(msglist);
    }

    @Override
    public boolean updateBroadcastCache(Long entry, Short port) {
        if (timedCache.update(entry)) {
            AtomicLong count = portBroadcastCacheHitMap.get(port);
            if(count == null) {
                AtomicLong newCount = new AtomicLong(0);
                AtomicLong retrieved;
                if((retrieved = portBroadcastCacheHitMap.putIfAbsent(port, newCount)) == null ) {
                    count = newCount;
                } else {
                    count = retrieved;
                }
            }
            count.incrementAndGet();
            return true;
        } else {
            return false;
        }
    }

    @Override
    @JsonIgnore
    public Map<Short, Long> getPortBroadcastHits() {
        Map<Short, Long> res = new HashMap<Short, Long>();
        for (Map.Entry<Short, AtomicLong> entry : portBroadcastCacheHitMap.entrySet()) {
            res.put(entry.getKey(), entry.getValue().get());
        }
        return res;
    }

    @Override
    public void flush() {
        Map<IOFSwitch,List<OFMessage>> msg_buffer_map = local_msg_buffer.get();
        List<OFMessage> msglist = msg_buffer_map.get(this);
        if ((msglist != null) && (msglist.size() > 0)) {
            /* ============================ BIG CAVEAT ===============================
             * This code currently works, but relies on undocumented behavior of
             * netty.
             *
             * The method org.jboss.netty.channel.Channel.write(Object)
             * (invoked from this.write(List<OFMessage> msg) is currently
             * documented to be <emph>asynchronous</emph>. If the method /were/ truely
             * asynchronous, this would break our code (because we are clearing the
             * msglist right after calling write.
             *
             * For now, Netty actually invokes the conversion pipeline before doing
             * anything asynchronous, so we are safe. But we should probably change
             * that behavior.
             */
            this.write(msglist);
            msglist.clear();
        }
    }

    public static void flush_all() {
        Map<IOFSwitch,List<OFMessage>> msg_buffer_map = local_msg_buffer.get();
        for (IOFSwitch sw : msg_buffer_map.keySet()) {
            sw.flush();
        }
    }

    /**
     * Return a read lock that must be held while calling the listeners for
     * messages from the switch. Holding the read lock prevents the active
     * switch list from being modified out from under the listeners.
     * @return
     */
    @Override
    @JsonIgnore
    public Lock getListenerReadLock() {
        return listenerLock.readLock();
    }

    /**
     * Return a write lock that must be held when the controllers modifies the
     * list of active switches. This is to ensure that the active switch list
     * doesn't change out from under the listeners as they are handling a
     * message from the switch.
     * @return
     */
    @Override
    @JsonIgnore
    public Lock getListenerWriteLock() {
        return listenerLock.writeLock();
    }

    /**
     * Get the IP Address for the switch
     * @return the inet address
     */
    @Override
    @JsonSerialize(using=ToStringSerializer.class)
    public SocketAddress getInetAddress() {
        if (channel == null)
            return null;
        return channel.getRemoteAddress();
    }

    @Override
    public Future<OFFeaturesReply> querySwitchFeaturesReply()
            throws IOException {
        OFMessage request =
                floodlightProvider.getOFMessageFactory().
                    getMessage(OFType.FEATURES_REQUEST);
        request.setXid(getNextTransactionId());
        OFFeaturesReplyFuture future =
                new OFFeaturesReplyFuture(threadPool, this, request.getXid());
        this.featuresFutureMap.put(request.getXid(), future);
        List<OFMessage> msglist = new ArrayList<OFMessage>(1);
        msglist.add(request);
        this.write(msglist);
        return future;
    }

    @Override
    public void deliverOFFeaturesReply(OFMessage reply) {
        OFFeaturesReplyFuture future = this.featuresFutureMap.get(reply.getXid());
        if (future != null) {
            future.deliverFuture(this, reply);
            // The future will ultimately unregister itself and call
            // cancelFeaturesReply
            return;
        }
        log.error("Switch {}: received unexpected featureReply", this);
    }

    @Override
    public void cancelFeaturesReply(int transactionId) {
        this.featuresFutureMap.remove(transactionId);
    }


    @Override
    public int getBuffers() {
        return buffers;
    }


    @Override
    public int getActions() {
        return actions;
    }


    @Override
    public int getCapabilities() {
        return capabilities;
    }


    @Override
    public byte getTables() {
        return tables;
    }

    @Override
    public OFDescriptionStatistics getDescriptionStatistics() {
        return new OFDescriptionStatistics(description);
    }


    @Override
    public void setFloodlightProvider(Controller controller) {
        floodlightProvider = controller;
    }


    /**
     * For switch drivers to set thresholds, all rates in per second
     * @param pktInHigh - above this start throttling
     * @param pktInLow  - below this stop throttling
     * @param pktInPerMac  - block host if unique pktIn rate reaches this
     * @param pktInPerPort - block port if unique pktIn rate reaches this
     */
    @JsonIgnore
    protected void setInputThrottleThresholds(int pktInHigh, int pktInLow,
            int pktInPerMac, int pktInPerPort) {
        packetInRateThresholdHigh = pktInHigh;
        packetInRateThresholdLow = pktInLow;
        packetInRatePerMacThreshold = pktInPerMac;
        packetInRatePerPortThreshold = pktInPerPort;
    }

    /**
     * Return if switch has exceeded the high threshold of packet in rate.
     * @return
     */
    @Override
    public boolean isOverloaded() {
        return packetInThrottleEnabled;
    }

    /**
     * Determine if this message should be dropped.
     *
     * We compute the current rate by taking a timestamp every 100 messages.
     * Could change to a more complex scheme if more accuracy is needed.
     *
     * Enable throttling if the rate goes above packetInRateThresholdHigh
     * Disable throttling when the rate drops below packetInRateThresholdLow
     *
     * While throttling is enabled, we do the following:
     *  - Remove duplicate packetIn's mapped to the same OFMatch
     *  - After filtering, if packetIn rate per host (mac) is above
     *    packetInRatePerMacThreshold, push a flow mod to block mac on port
     *  - After filtering, if packetIn rate per port is above
     *    packetInRatePerPortThreshold, push a flow mod to block port
     *  - Allow blocking flow mods have a hard timeout and expires automatically
     *
     * TODO: keep a history of all events related in input throttling
     *
     * @param ofm
     * @return
     */
    @Override
    public boolean inputThrottled(OFMessage ofm) {
        if (ofm.getType() != OFType.PACKET_IN) {
            return false;
        }
        // Compute current packet in rate
        messageCount++;
        if (messageCount % 100 == 0) {
            long now = System.currentTimeMillis();
            if (now != lastMessageTime) {
                currentRate = (int) (100000 / (now - lastMessageTime));
                lastMessageTime = now;
            } else {
                currentRate = Integer.MAX_VALUE;
            }
        }
        if (!packetInThrottleEnabled) {
            if (currentRate <= packetInRateThresholdHigh) {
                return false; // most common case
            }
            enablePacketInThrottle();
        } else if (currentRate < packetInRateThresholdLow) {
            disablePacketInThrottle();
            return false;
        }

        // Now we are in the slow path where we need to do filtering
        // First filter based on OFMatch
        OFPacketIn pin = (OFPacketIn)ofm;
        OFMatch match = new OFMatch();
        match.loadFromPacket(pin.getPacketData(), pin.getInPort());
        if (ofMatchCache.update(match)) {
            debugCounters.updateCounter(stringId + "-pktinDrops");
            return true;
        }

        // We have packet in with a distinct flow, check per mac rate
        messageCountUniqueOFMatch++;
        if ((messageCountUniqueOFMatch % packetInRatePerMacThreshold) == 1) {
            checkPerSourceMacRate(pin);
        }

        // Check per port rate
        if ((messageCountUniqueOFMatch % packetInRatePerPortThreshold) == 1) {
            checkPerPortRate(pin);
        }
        return false;
    }

    /**
     * We rely on the fact that packet in processing is single threaded
     * per switch, so no locking is necessary.
     */
    private void disablePacketInThrottle() {
        ofMatchCache = null;
        macCache = null;
        macBlockedCache = null;
        portCache = null;
        portBlockedCache = null;
        packetInThrottleEnabled = false;
        log.info("Packet in rate is {}, disable throttling on {}",
                currentRate, this);
    }

    private void enablePacketInThrottle() {
        ofMatchCache = new TimedCache<OFMatch>(2048, 5000); // 5 second interval
        macCache = new TimedCache<Long>(64, 1000 );  // remember last second
        macBlockedCache = new TimedCache<Long>(256, 5000 );  // 5 second interval
        portCache = new TimedCache<Short>(16, 1000 );  // rememeber last second
        portBlockedCache = new TimedCache<Short>(64, 5000 );  // 5 second interval
        packetInThrottleEnabled = true;
        messageCountUniqueOFMatch = 0;
        log.info("Packet in rate is {}, enable throttling on {}",
                currentRate, this);
    }

    private void registerOverloadCounters() {
        if (debugCountersRegistered) {
            return;
        }
        if (debugCounters == null) {
            log.error("Debug Counter Service not found");
            debugCounters = new NullDebugCounter();
            debugCountersRegistered = true;
            return;
        }
        debugCounters.registerCounter(stringId + "-pktinDrops",
                "Packet in throttle drop count", CounterType.ALWAYS_COUNT);
        debugCounters.registerCounter(stringId + "-writeDrops",
                "Switch write throttle drop count", CounterType.ALWAYS_COUNT);
    }

    /**
     * Check if we have sampled this mac in the last second.
     * Since we check every packetInRatePerMacThreshold packets,
     * the presence of the mac in the macCache means the rate is
     * above the threshold in a statistical sense.
     *
     * Take care not to block topology probing packets. Also don't
     * push blocking flow mod if we have already done so within the
     * last 5 seconds.
     *
     * @param pin
     * @return
     */
    private void checkPerSourceMacRate(OFPacketIn pin) {
        byte[] data = pin.getPacketData();
        byte[] mac = Arrays.copyOfRange(data, 6, 12);
        MACAddress srcMac = MACAddress.valueOf(mac);
        short ethType = (short) (((data[12] & 0xff) << 8) + (data[13] & 0xff));
        if (ethType != Ethernet.TYPE_LLDP && ethType != Ethernet.TYPE_BSN &&
                macCache.update(srcMac.toLong())) {
            // Check if we already pushed a flow in the last 5 seconds
            if (macBlockedCache.update(srcMac.toLong())) {
                return;
            }
            // write out drop flow per srcMac
            int port = pin.getInPort();
            SwitchPort swPort = new SwitchPort(getId(), port);
            ForwardingBase.blockHost(floodlightProvider,
                    swPort, srcMac.toLong(), (short) 5,
                    AppCookie.makeCookie(OFSWITCH_APP_ID, 0));
            log.info("Excessive packet in from {} on {}, block host for 5 sec",
                    srcMac.toString(), swPort);
        }
    }

    /**
     * Works in a similar way as checkPerSourceMacRate().
     *
     * TODO Don't block ports with links?
     *
     * @param pin
     * @return
     */
    private void checkPerPortRate(OFPacketIn pin) {
        Short port = pin.getInPort();
        if (portCache.update(port)) {
            // Check if we already pushed a flow in the last 5 seconds
            if (portBlockedCache.update(port)) {
                return;
            }
            // write out drop flow per port
            SwitchPort swPort = new SwitchPort(getId(), port);
            ForwardingBase.blockHost(floodlightProvider,
                    swPort, -1L, (short) 5,
                    AppCookie.makeCookie(OFSWITCH_APP_ID, 1));
            log.info("Excessive packet in from {}, block port for 5 sec",
                    swPort);
        }
    }
}
