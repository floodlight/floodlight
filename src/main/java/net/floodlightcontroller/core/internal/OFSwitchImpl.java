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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.TimedCache;

import org.jboss.netty.channel.Channel;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFSwitchImpl implements IOFSwitch {
    protected static Logger log = LoggerFactory.getLogger(OFSwitchImpl.class);

    protected ConcurrentMap<Object, Object> attributes;
    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPool;
    protected Date connectedSince;
    protected OFFeaturesReply featuresReply;
    protected String stringId;
    protected Channel channel;
    protected AtomicInteger transactionIdSource;
    protected Map<Short, OFPhysicalPort> ports;
    protected Long switchClusterId;
    protected Map<MacVlanPair,Short> macVlanToPortMap;
    protected Map<Integer,OFStatisticsFuture> statsFutureMap;
    protected Map<Integer, IOFMessageListener> iofMsgListenersMap;
    protected boolean connected;
    protected Role role;
    protected TimedCache<Long> timedCache;
    protected ReentrantReadWriteLock listenerLock;
    protected ConcurrentMap<Short, Long> portBroadcastCacheHitMap;
    
    public static IOFSwitchFeatures switchFeatures;
    protected static final ThreadLocal<Map<OFSwitchImpl,List<OFMessage>>> local_msg_buffer =
            new ThreadLocal<Map<OFSwitchImpl,List<OFMessage>>>() {
            @Override
            protected Map<OFSwitchImpl,List<OFMessage>> initialValue() {
                return new HashMap<OFSwitchImpl,List<OFMessage>>();
            }
    };
    
    // for managing our map sizes
    protected static final int MAX_MACS_PER_SWITCH  = 1000;
    
    public OFSwitchImpl() {
        this.attributes = new ConcurrentHashMap<Object, Object>();
        this.connectedSince = new Date();
        this.transactionIdSource = new AtomicInteger();
        this.ports = new ConcurrentHashMap<Short, OFPhysicalPort>();
        this.switchClusterId = null;
        this.connected = true;
        this.statsFutureMap = new ConcurrentHashMap<Integer,OFStatisticsFuture>();
        this.iofMsgListenersMap = new ConcurrentHashMap<Integer,IOFMessageListener>();
        this.role = null;
        this.timedCache = new TimedCache<Long>(100, 5*1000 );  // 5 seconds interval
        this.listenerLock = new ReentrantReadWriteLock();
        this.portBroadcastCacheHitMap = new ConcurrentHashMap<Short, Long>();
        
        // Defaults properties for an ideal switch
        this.setAttribute(PROP_FASTWILDCARDS, (Integer) OFMatch.OFPFW_ALL);
        this.setAttribute(PROP_SUPPORTS_OFPP_FLOOD, new Boolean(true));
        this.setAttribute(PROP_SUPPORTS_OFPP_TABLE, new Boolean(true));
    }
    

    @Override
    public Object getAttribute(String name) {
        if (this.attributes.containsKey(name)) {
            return this.attributes.get(name);
        }
        return null;
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
        
    public Channel getChannel() {
        return this.channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    
    public void write(OFMessage m, FloodlightContext bc) throws IOException {
        Map<OFSwitchImpl,List<OFMessage>> msg_buffer_map = local_msg_buffer.get();
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

    public void write(List<OFMessage> msglist, FloodlightContext bc) throws IOException {
        for (OFMessage m : msglist) {
            if (role == Role.SLAVE) {
                switch (m.getType()) {
                    case PACKET_OUT:
                    case FLOW_MOD:
                    case PORT_MOD:
                        log.warn("Sending OF message that modifies switch state while in the slave role: {}", m.getType().name());
                        break;
                    default:
                        break;
                }
            }
            // FIXME: Debugging code should be disabled!!!
            // log.debug("Sending message type {} with xid {}", new Object[] {m.getType(), m.getXid()});
            this.floodlightProvider.handleOutgoingMessage(this, m, bc);
        }
        this.write(msglist);
    }

    public void write(List<OFMessage> msglist) throws IOException {
        this.channel.write(msglist);
    }
    
    public void disconnectOutputStream() {
        channel.close();
    }

    public OFFeaturesReply getFeaturesReply() {
        return this.featuresReply;
    }
    
    public void setSwitchClusterId(Long id) {
        this.switchClusterId = id;
    }
    
    public Long getSwitchClusterId() {
        return switchClusterId;
    }

    public synchronized void setFeaturesReply(OFFeaturesReply featuresReply) {
        this.featuresReply = featuresReply;
        for (OFPhysicalPort port : featuresReply.getPorts()) {
            ports.put(port.getPortNumber(), port);
        }
        this.switchClusterId = featuresReply.getDatapathId();
        this.stringId = HexString.toHexString(featuresReply.getDatapathId());
    }

    public synchronized List<OFPhysicalPort> getEnabledPorts() {
        List<OFPhysicalPort> result = new ArrayList<OFPhysicalPort>();
        for (OFPhysicalPort port : ports.values()) {
            if (portEnabled(port)) {
                result.add(port);
            }
        }
        return result;
    }

    public synchronized OFPhysicalPort getPort(short portNumber) {
        return ports.get(portNumber);
    }

    public synchronized void setPort(OFPhysicalPort port) {
        ports.put(port.getPortNumber(), port);
    }
    
    public Map<Short, OFPhysicalPort> getPorts() {
        return ports;
    }

    public synchronized void deletePort(short portNumber) {
        ports.remove(portNumber);
    }

    public synchronized boolean portEnabled(short portNumber) {
        return portEnabled(ports.get(portNumber));
    }
    
    public boolean portEnabled(OFPhysicalPort port) {
        if (port == null)
            return false;
        if ((port.getConfig() & OFPortConfig.OFPPC_PORT_DOWN.getValue()) > 0)
            return false;
        if ((port.getState() & OFPortState.OFPPS_LINK_DOWN.getValue()) > 0)
            return false;
        // Port STP state doesn't work with multiple VLANs, so ignore it for now
        //if ((port.getState() & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue())
        //    return false;
        return true;
    }
    
    @Override
    public long getId() {
        if (this.featuresReply == null)
            throw new RuntimeException("Features reply has not yet been set");
        return this.featuresReply.getDatapathId();
    }

    @Override
    public String getStringId() {
        return stringId;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "OFSwitchImpl [" + channel.getRemoteAddress() + " DPID[" + ((featuresReply != null) ? stringId : "?") + "]]";
    }

    @Override
    public ConcurrentMap<Object, Object> getAttributes() {
        return this.attributes;
    }

    @Override
    public Date getConnectedSince() {
        return connectedSince;
    }

    @Override
    public int getNextTransactionId() {
        return this.transactionIdSource.incrementAndGet();
    }

    @Override
    public int getXid() {
        return getNextTransactionId();
    }

    @Override
    public void sendStatsQuery(OFStatisticsRequest request, int xid,
                                IOFMessageListener caller) throws IOException {
        request.setXid(xid);
        this.iofMsgListenersMap.put(xid, caller);
        List<OFMessage> msglist = new ArrayList<OFMessage>(1);
        msglist.add(request);
        this.channel.write(msglist);
        return;
    }

    @Override
    public Future<List<OFStatistics>> getStatistics(OFStatisticsRequest request) throws IOException {
        request.setXid(getNextTransactionId());
        OFStatisticsFuture future = new OFStatisticsFuture(threadPool, this, request.getXid());
        this.statsFutureMap.put(request.getXid(), future);
        List<OFMessage> msglist = new ArrayList<OFMessage>(1);
        msglist.add(request);
        this.channel.write(msglist);
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
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
    
    public void setThreadPoolService(IThreadPoolService tp) {
        this.threadPool = tp;
    }

    @Override
    public synchronized boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    @Override
    public synchronized Role getRole() {
        return role;
    }
    
    @Override
    public synchronized void setRole(Role role) {
        this.role = role;
    }
    
    @Override
    public synchronized boolean isActive() {
        return (role != Role.SLAVE);
    }
    
    @Override
    public void setSwitchProperties(OFDescriptionStatistics description) {
        if (switchFeatures != null) {
            switchFeatures.setFromDescription(this, description);
        }
    }

    @Override
    public void clearAllFlowMods() {
        // Delete all pre-existing flows
        OFMatch match = new OFMatch().setWildcards(OFMatch.OFPFW_ALL);
        OFMessage fm = ((OFFlowMod) floodlightProvider.getOFMessageFactory()
            .getMessage(OFType.FLOW_MOD))
                .setMatch(match)
            .setCommand(OFFlowMod.OFPFC_DELETE)
            .setOutPort(OFPort.OFPP_NONE)
            .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));
        try {
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(fm);
            channel.write(msglist);
        } catch (Exception e) {
            log.error("Failed to clear all flows on switch {} - {}", this, e);
        }
    }

    @Override
    public boolean updateBroadcastCache(Long entry, Short port) {
        if (timedCache.update(entry)) {
            Long count = portBroadcastCacheHitMap.putIfAbsent(port, new Long(1));
            if (count != null) {
                count++;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<Short, Long> getPortBroadcastHits() {
    	return this.portBroadcastCacheHitMap;
    }
    

    public void flush() {
        Map<OFSwitchImpl,List<OFMessage>> msg_buffer_map = local_msg_buffer.get();
        List<OFMessage> msglist = msg_buffer_map.get(this);
        if ((msglist != null) && (msglist.size() > 0)) {
            try {
                this.write(msglist);
            } catch (IOException e) {
                // TODO: log exception
                e.printStackTrace();
            }
            msglist.clear();
        }
    }

    public static void flush_all() {
        Map<OFSwitchImpl,List<OFMessage>> msg_buffer_map = local_msg_buffer.get();
        for (OFSwitchImpl sw : msg_buffer_map.keySet()) {
            sw.flush();
        }
    }

    /**
     * Return a read lock that must be held while calling the listeners for
     * messages from the switch. Holding the read lock prevents the active
     * switch list from being modified out from under the listeners.
     * @return 
     */
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
    public Lock getListenerWriteLock() {
        return listenerLock.writeLock();
    }
}
