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

package net.floodlightcontroller.forwarding;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.LinkInfo;
import net.floodlightcontroller.topology.SwitchPortTuple;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Forwarding extends ForwardingBase {
    protected static Logger log = LoggerFactory.getLogger(Forwarding.class);

    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProvider.bcStore.get(cntx, 
                                                       IFloodlightProvider.CONTEXT_PI_PAYLOAD);
        if (eth.isBroadcast() || eth.isMulticast()) {
            // For now we treat multicast as broadcast
            doFlood(sw, pi, cntx);
        } else {
            doForwardFlow(sw, pi, cntx);
        }
        
        return Command.CONTINUE;
    }
    
    protected void doForwardFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {    
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort(), sw.getId());

        // Check if we have the location of the destination
        Device dstDevice = deviceManager.getDeviceByDataLayerAddress(match.getDataLayerDestination());
        
        if (dstDevice != null) {
            Device srcDevice = deviceManager.getDeviceByDataLayerAddress(match.getDataLayerSource());
            Long srcIsland = sw.getSwitchClusterId();
            
            if (srcDevice == null) {
                log.error("No device entry found for source device {}", 
                          HexString.toHexString(dstDevice.getDataLayerAddress()));
                return;
            }
            if (srcIsland == null) {
                log.error("No openflow island found for source device {}", 
                          HexString.toHexString(dstDevice.getDataLayerAddress()));
                return;
            }
                                                
            // Validate that we have a destination known on the same island
            // Validate that the source and destination are not on the same switchport
            boolean on_same_island = false;
            boolean on_same_if = false;
            for (DeviceAttachmentPoint dstDap : dstDevice.getAttachmentPoints()) {
                SwitchPortTuple dstTuple = dstDap.getSwitchPort();
                if ((dstTuple != null) && (dstTuple.getSw() != null)) {
                    Long dstIsland = dstTuple.getSw().getSwitchClusterId();
                    if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
                        on_same_island = true;
                        if ((sw.getId() == dstTuple.getSw().getId()) &&
                            (pi.getInPort() == dstTuple.getPort().shortValue())) {
                            on_same_if = true;
                        }
                        break;
                    }
                }
            }
            
            if (!on_same_island) {
                // Flood since we don't know the dst device
                if (log.isDebugEnabled()) {
                    log.debug("No first hop island found for destination device {}, Action = flooding",
                              dstDevice.getDataLayerAddress());
                }
                doFlood(sw, pi, cntx);
                return;
            }            
            
            if (on_same_if) {
                if (log.isDebugEnabled()) {
                    log.debug("Both source and destination are on the same switch/port {}/{}, Action = NOP", 
                              sw.toString(), pi.getInPort());
                }
                return;
            }

            // Install all the routes where both src and dst have attachment points
            // Since the lists are stored in sorted order we can traverse the attachment points in O(m+n) time
            DeviceAttachmentPoint[] srcDaps = 
                srcDevice.getAttachmentPointsSorted(DeviceAttachmentPoint.clusterIdComparator).toArray(new DeviceAttachmentPoint[0]);
            DeviceAttachmentPoint[] dstDaps = 
                dstDevice.getAttachmentPointsSorted(DeviceAttachmentPoint.clusterIdComparator).toArray(new DeviceAttachmentPoint[0]);
            int iSrcDaps = 0, iDstDaps = 0;
            while ((iSrcDaps < srcDaps.length) && (iDstDaps < dstDaps.length)) {
                DeviceAttachmentPoint srcDap = srcDaps[iSrcDaps];
                DeviceAttachmentPoint dstDap = dstDaps[iDstDaps];
                IOFSwitch srcSw = srcDap.getSwitchPort().getSw();
                IOFSwitch dstSw = dstDap.getSwitchPort().getSw();
                Long srcCluster = null;
                Long dstCluster = null;
                if ((srcSw != null) && (dstSw != null)) {
                    srcCluster = srcSw.getSwitchClusterId();
                    dstCluster = dstSw.getSwitchClusterId();
                }
                
                int srcVsDest = srcCluster.compareTo(dstCluster);
                if (srcVsDest == 0) {
                    if (!srcDap.equals(dstDap) && (srcCluster != null) && (dstCluster != null)) {
                        Route route = routingEngine.getRoute(srcSw.getId(), dstSw.getId());
                        if ((route != null) || validLocalHop(srcDap.getSwitchPort(), dstDap.getSwitchPort())) {
                            int bufferId = OFPacketOut.BUFFER_ID_NONE;
                            if (log.isTraceEnabled()) {
                                log.trace("pushRoute match={} route={} destination={}:{}",
                                          new Object[] {match, route, dstDap.getSwitchPort().getSw(),
                                          dstDap.getSwitchPort().getPort()});
                            }
                            pushRoute(route, match, 0,
                                      srcDap.getSwitchPort(), dstDap.getSwitchPort(), bufferId,
                                      sw, pi, cntx);
                        }
                    }
                    iSrcDaps++;
                    iDstDaps++;
                } else if (srcVsDest < 0) {
                    iSrcDaps++;
                } else {
                    iDstDaps++;
                }
            }
        } else {
            // Flood since we don't know the dst device
            doFlood(sw, pi, cntx);
        }
    }

    /**
     * Creates a OFPacketOut with the OFPacketIn data that is flooded on all ports unless 
     * the port is blocked, in which case the packet will be dropped.
     * @param pi The OFPacketIn that came to the switch
     * @param decision The Forwarding decision
     * @param cntx The FloodlightContext associated with this OFPacketIn
     */
    protected void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {               
        SwitchPortTuple srcSwTuple = new SwitchPortTuple(sw, pi.getInPort());
        LinkInfo linkInfo = topology.getLinkInfo(srcSwTuple, false);
        if (log.isTraceEnabled()) {
            log.trace("doFlood pi={} srcSwitchTuple={}, link={}",
                      new Object[] { pi, srcSwTuple, linkInfo});
        }

        if (linkInfo != null && linkInfo.isBroadcastBlocked()) {
            if (log.isDebugEnabled()) {
                log.debug("doFlood, drop broadcast packet, pi={}, from a blocked port, " +
                         "srcSwitchTuple={}, linkInfo={}", new Object[] {pi, srcSwTuple, linkInfo});
            }
            return;
        }
        
        // Set Action to flood
        OFPacketOut po = 
            (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        List<OFAction> actions = new ArrayList<OFAction>();
        if (sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)) {
            actions.add(new OFActionOutput(OFPort.OFPP_FLOOD.getValue(), (short)0));
        } else {
            actions.add(new OFActionOutput(OFPort.OFPP_ALL.getValue(), (short)0));
        }
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set buffer-id, in-port and packet-data based on packet-in
        short poLength = (short)(po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
        po.setBufferId(pi.getBufferId());
        po.setInPort(pi.getInPort());
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }
        po.setLength(poLength);
        
        try {
            if (log.isTraceEnabled()) {
                log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
                          new Object[] {sw, pi, po});
            }
            sw.write(po, cntx);
        } catch (IOException e) {
            log.error("Failure writing PacketOut switch={} packet-in={} packet-out={}",
                    new Object[] {sw, pi, po}, e);
        }            

        return;
    }
    
    @Override
    protected OFMatch wildcard(OFMatch match, IOFSwitch sw, Integer hints) {
        // use same wilcarding as the learning switch
        int wildcards = ((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue() &
            ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN &
            ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST &
            ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK;
        return match.clone().setWildcards(wildcards);
    }

    private boolean validLocalHop(SwitchPortTuple srcTuple, SwitchPortTuple dstTuple) {
        return srcTuple.getSw().getId() == dstTuple.getSw().getId() &&
               srcTuple.getPort() != dstTuple.getPort();
    }
}
