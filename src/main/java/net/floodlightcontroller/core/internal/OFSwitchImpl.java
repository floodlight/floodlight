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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.types.MacVlanPair;

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
import org.openflow.util.LRULinkedHashMap;
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
    protected IFloodlightProvider floodlightProvider;
    protected Date connectedSince;
    protected OFFeaturesReply featuresReply;
    protected String stringId;
    protected Channel channel;
    protected AtomicInteger transactionIdSource;
    protected Map<Short, OFPhysicalPort> ports;
    protected Long switchClusterId;
    protected Map<MacVlanPair,Short> macVlanToPortMap;
    protected Map<Integer,OFStatisticsFuture> statsFutureMap;
    protected boolean connected;
    
    public static IOFSwitchFeatures switchFeatures;
    
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
        this.floodlightProvider.handleOutgoingMessage(this, m, bc);
        this.channel.write(m);
    }
    
    public void write(List<OFMessage> msglist, FloodlightContext bc) throws IOException {
        for (OFMessage m : msglist) {
            this.floodlightProvider.handleOutgoingMessage(this, m, bc);
            this.channel.write(m);
        }
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
    public Future<List<OFStatistics>> getStatistics(OFStatisticsRequest request) throws IOException {
        request.setXid(getNextTransactionId());
        OFStatisticsFuture future = new OFStatisticsFuture(floodlightProvider, this, request.getXid());
        this.statsFutureMap.put(request.getXid(), future);
        this.floodlightProvider.addOFSwitchListener(future);
        this.channel.write(request);
        return future;
    }
    
    @Override
    public void deliverStatisticsReply(OFMessage reply) {
        OFStatisticsFuture future = this.statsFutureMap.get(reply.getXid());
        if (future != null) {
            future.deliverFuture(this, reply);
            // The future will ultimately unregister itself and call
            // cancelStatisticsReply
        }
    }

    @Override
    public void cancelStatisticsReply(int transactionId) {
        this.statsFutureMap.remove(transactionId);
    }
    
    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    @Override
    public synchronized void addToPortMap(Long mac, Short vlan, short portVal) {
        if (vlan == (short) 0xffff) {
            // OFMatch.loadFromPacket sets VLAN ID to 0xffff if the packet contains no VLAN tag;
            // for our purposes that is equivalent to the default VLAN ID 0
            vlan = 0;
        }
        
        if (macVlanToPortMap == null) {
            this.macVlanToPortMap = new LRULinkedHashMap<MacVlanPair,Short>(MAX_MACS_PER_SWITCH);
        }
        macVlanToPortMap.put(new MacVlanPair(mac, vlan), portVal);
    }

    @Override
    public synchronized void removeFromPortMap(Long mac, Short vlan) {
        if (vlan == (short) 0xffff) {
            vlan = 0;
        }
        if (macVlanToPortMap != null)
            macVlanToPortMap.remove(new MacVlanPair(mac, vlan));
    }

    @Override
    public synchronized Short getFromPortMap(Long mac, Short vlan) {
        if (vlan == (short) 0xffff) {
            vlan = 0;
        }
        if (macVlanToPortMap != null)
            return macVlanToPortMap.get(new MacVlanPair(mac, vlan));
        else
            return null;
    }

    @Override
    public synchronized void clearPortMapTable() {
        if (macVlanToPortMap != null)
            macVlanToPortMap.clear();
    }

    @Override
    public synchronized Map<MacVlanPair, Short> getMacVlanToPortMap() {
        // Note that this function intentionally returns a copy
        if (macVlanToPortMap != null)
            return new ConcurrentHashMap<MacVlanPair, Short>(macVlanToPortMap);
        else
            return null;
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
            channel.write(fm);
        } catch (Exception e) {
            log.error("Failed to clear all flows on switch {} - {}", this, e);
        }
    }
}
