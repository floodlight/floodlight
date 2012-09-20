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
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
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
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.TimedCache;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.ser.ToStringSerializer;
import org.jboss.netty.channel.Channel;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the internal representation of an openflow switch.
 */
public class OFSwitchImpl implements IOFSwitch {
    // TODO: should we really do logging in the class or should we throw
    // exception that can then be handled by callers?
    protected static Logger log = LoggerFactory.getLogger(OFSwitchImpl.class);

    private static final String HA_CHECK_SWITCH = 
            "Check the health of the indicated switch.  If the problem " +
            "persists or occurs repeatedly, it likely indicates a defect " +
            "in the switch HA implementation.";
    
    protected ConcurrentMap<Object, Object> attributes;
    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPool;
    protected Date connectedSince;
    protected OFFeaturesReply featuresReply;
    protected String stringId;
    protected Channel channel;
    protected AtomicInteger transactionIdSource;
    protected Map<Short, OFPhysicalPort> ports;
    protected Map<Integer,OFStatisticsFuture> statsFutureMap;
    protected Map<Integer, IOFMessageListener> iofMsgListenersMap;
    protected boolean connected;
    protected Role role;
    protected TimedCache<Long> timedCache;
    protected ReentrantReadWriteLock listenerLock;
    protected ConcurrentMap<Short, Long> portBroadcastCacheHitMap;
    /**
     * When sending a role request message, the role request is added
     * to this queue. If a role reply is received this queue is checked to 
     * verify that the reply matches the expected reply. We require in order
     * delivery of replies. That's why we use a Queue. 
     * The RoleChanger uses a timeout to ensure we receive a timely reply.
     * 
     * Need to synchronize on this instance if a request is sent, received, 
     * checked. 
     */
    protected LinkedList<PendingRoleRequestEntry> pendingRoleRequests;
    
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
    
    protected static class PendingRoleRequestEntry {
        protected int xid;
        protected Role role;
        // cookie is used to identify the role "generation". roleChanger uses
        protected long cookie;
        public PendingRoleRequestEntry(int xid, Role role, long cookie) {
            this.xid = xid;
            this.role = role;
            this.cookie = cookie;
        }
    }
    
    public OFSwitchImpl() {
        this.attributes = new ConcurrentHashMap<Object, Object>();
        this.connectedSince = new Date();
        this.transactionIdSource = new AtomicInteger();
        this.ports = new ConcurrentHashMap<Short, OFPhysicalPort>();
        this.connected = true;
        this.statsFutureMap = new ConcurrentHashMap<Integer,OFStatisticsFuture>();
        this.iofMsgListenersMap = new ConcurrentHashMap<Integer,IOFMessageListener>();
        this.role = null;
        this.timedCache = new TimedCache<Long>(100, 5*1000 );  // 5 seconds interval
        this.listenerLock = new ReentrantReadWriteLock();
        this.portBroadcastCacheHitMap = new ConcurrentHashMap<Short, Long>();
        this.pendingRoleRequests = new LinkedList<OFSwitchImpl.PendingRoleRequestEntry>();
        
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
        
    @JsonIgnore
    public Channel getChannel() {
        return this.channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    
    @Override
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

    @Override
    @LogMessageDoc(level="WARN",
                   message="Sending OF message that modifies switch " +
                           "state while in the slave role: {switch}",
                   explanation="An application has sent a message to a switch " +
                   		"that is not valid when the switch is in a slave role",
                   recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public void write(List<OFMessage> msglist, 
                      FloodlightContext bc) throws IOException {
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

    public void write(List<OFMessage> msglist) throws IOException {
        this.channel.write(msglist);
    }
    
    public void disconnectOutputStream() {
        channel.close();
    }

    @JsonIgnore
    public OFFeaturesReply getFeaturesReply() {
        return this.featuresReply;
    }
    
    public synchronized void setFeaturesReply(OFFeaturesReply featuresReply) {
        this.featuresReply = featuresReply;
        for (OFPhysicalPort port : featuresReply.getPorts()) {
            ports.put(port.getPortNumber(), port);
        }
        this.stringId = HexString.toHexString(featuresReply.getDatapathId());
    }

    @JsonIgnore
    public synchronized List<Short> getEnabledPorts() {
        List<Short> result = new ArrayList<Short>();
        for (OFPhysicalPort port : ports.values()) {
            if (portEnabled(port)) {
                result.add(port.getPortNumber());
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
    
    @JsonIgnore
    public Map<Short, OFPhysicalPort> getPorts() {
        return ports;
    }
    
    @JsonProperty("ports")
    public Collection<OFPhysicalPort> getPortCollection() {
        return ports.values();
    }

    public synchronized void deletePort(short portNumber) {
        ports.remove(portNumber);
    }

    public synchronized boolean portEnabled(short portNumber) {
        if (ports.get(portNumber) == null) return false;
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
    @JsonSerialize(using=DPIDSerializer.class)
    @JsonProperty("dpid")
    public long getId() {
        if (this.featuresReply == null)
            throw new RuntimeException("Features reply has not yet been set");
        return this.featuresReply.getDatapathId();
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

    @JsonIgnore
    @Override
    public synchronized boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    @Override
    public Role getRole() {
        return role;
    }
    
    @JsonIgnore
    @Override
    public boolean isActive() {
        return (role != Role.SLAVE);
    }
    
    @Override
    public void setSwitchProperties(OFDescriptionStatistics description) {
        if (switchFeatures != null) {
            switchFeatures.setFromDescription(this, description);
        }
    }

    @Override
    @LogMessageDoc(level="ERROR",
                   message="Failed to clear all flows on switch {switch}",
                   explanation="An I/O error occured while trying to clear " +
                   		"flows on the switch.",
                   recommendation=LogMessageDoc.CHECK_SWITCH)
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
            log.error("Failed to clear all flows on switch " + this, e);
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
    @JsonIgnore
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
    @JsonIgnore
    public Lock getListenerWriteLock() {
        return listenerLock.writeLock();
    }

    /**
     * Get the IP Address for the switch
     * @return the inet address
     */
    @JsonSerialize(using=ToStringSerializer.class)
    public SocketAddress getInetAddress() {
        return channel.getRemoteAddress();
    }
    
    /**
     * Send NX role request message to the switch requesting the specified role.
     * 
     * This method should ONLY be called by @see RoleChanger.submitRequest(). 
     * 
     * After sending the request add it to the queue of pending request. We
     * use the queue to later verify that we indeed receive the correct reply.
     * @param sw switch to send the role request message to
     * @param role role to request
     * @param cookie an opaque value that will be stored in the pending queue so
     *        RoleChanger can check for timeouts.
     * @return transaction id of the role request message that was sent
     */
    protected int sendNxRoleRequest(Role role, long cookie)
            throws IOException {
        synchronized(pendingRoleRequests) {
            // Convert the role enum to the appropriate integer constant used
            // in the NX role request message
            int nxRole = 0;
            switch (role) {
                case EQUAL:
                    nxRole = OFRoleVendorData.NX_ROLE_OTHER;
                    break;
                case MASTER:
                    nxRole = OFRoleVendorData.NX_ROLE_MASTER;
                    break;
                case SLAVE:
                    nxRole = OFRoleVendorData.NX_ROLE_SLAVE;
                    break;
                default:
                    log.error("Invalid Role specified for switch {}."
                              + " Disconnecting.", this);
                    // TODO: should throw an error
                    return 0;
            }
            
            // Construct the role request message
            OFVendor roleRequest = (OFVendor)floodlightProvider.
                    getOFMessageFactory().getMessage(OFType.VENDOR);
            int xid = this.getNextTransactionId();
            roleRequest.setXid(xid);
            roleRequest.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
            OFRoleRequestVendorData roleRequestData = new OFRoleRequestVendorData();
            roleRequestData.setRole(nxRole);
            roleRequest.setVendorData(roleRequestData);
            roleRequest.setLengthU(OFVendor.MINIMUM_LENGTH + 
                                   roleRequestData.getLength());
            
            // Send it to the switch
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(roleRequest);
            // FIXME: should this use this.write() in order for messages to
            // be processed by handleOutgoingMessage()
            this.channel.write(msglist);
            
            pendingRoleRequests.add(new PendingRoleRequestEntry(xid, role, cookie));
            return xid;
        }
    }
    
    /** 
     * Deliver a RoleReply message to this switch. Checks if the reply 
     * message matches the expected reply (head of the pending request queue). 
     * We require in-order delivery of replies. If there's any deviation from
     * our expectations we disconnect the switch. 
     * 
     * We must not check the received role against the controller's current
     * role because there's no synchronization but that's fine @see RoleChanger
     * 
     * Will be called by the OFChannelHandler's receive loop
     * 
     * @param xid Xid of the reply message
     * @param role The Role in the the reply message
     */
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: received unexpected role reply for " +
                        "Role {role}" + 
                        " Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=HA_CHECK_SWITCH),                           
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: expected role reply with " +
                        "Xid {xid}, got {xid}. Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=HA_CHECK_SWITCH),                           
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: expected role reply with " +
                        "Role {role}, got {role}. Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=HA_CHECK_SWITCH),                           
    })
    protected void deliverRoleReply(int xid, Role role) {
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.poll();
            if (head == null) {
                // Maybe don't disconnect if the role reply we received is 
                // for the same role we are already in. 
                log.error("Switch {}: received unexpected role reply for Role {}" + 
                          " Disconnecting switch", this, role );
                this.channel.close();
            }
            else if (head.xid != xid) {
                // check xid before role!!
                log.error("Switch {}: expected role reply with " +
                       "Xid {}, got {}. Disconnecting switch",
                       new Object[] { this, head.xid, xid } );
                this.channel.close();
            }
            else if (head.role != role) {
                log.error("Switch {}: expected role reply with " +
                       "Role {}, got {}. Disconnecting switch",
                       new Object[] { this, head.role, role } );
                this.channel.close();
            }
            else {
                log.debug("Received role reply message from {}, setting role to {}",
                          this, role);
                if (this.role == null && getAttribute(SWITCH_SUPPORTS_NX_ROLE) == null) {
                    // The first role reply we received. Set the attribute
                    // that the switch supports roles
                    setAttribute(SWITCH_SUPPORTS_NX_ROLE, true);
                }
                this.role = role;
            }
        }
    }
    
    /** 
     * Checks whether the given xid matches the xid of the first pending
     * role request. 
     * @param xid
     * @return 
     */
    protected boolean checkFirstPendingRoleRequestXid (int xid) {
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.peek();
            if (head == null)
                return false;
            else 
                return head.xid == xid;
        }
    }
    
    /**
     * Checks whether the given request cookie matches the cookie of the first 
     * pending request
     * @param cookie
     * @return
     */
    protected boolean checkFirstPendingRoleRequestCookie(long cookie) {
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.peek();
            if (head == null)
                return false;
            else 
                return head.cookie == cookie;
        }
    }
    
    /**
     * Called if we receive a vendor error message indicating that roles
     * are not supported by the switch. If the xid matches the first pending
     * one, we'll mark the switch as not supporting roles and remove the head.
     * Otherwise we ignore it.
     * @param xid
     */
    protected void deliverRoleRequestNotSupported(int xid) {
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.poll();
            this.role = null;
            if (head!=null && head.xid == xid) {
                setAttribute(SWITCH_SUPPORTS_NX_ROLE, false);
            }
            else {
                this.channel.close();
            }
        }
    }
}
