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


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.SwitchPortTuple;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Forwarding extends ForwardingBase {
    protected static Logger log = LoggerFactory.getLogger(Forwarding.class);

    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());

        // Check if we have the location of the destination
        Device dstDevice = deviceManager.getDeviceByDataLayerAddress(match.getDataLayerDestination());
        
        // TODO Optimizations -
        //  Keep attachment points sorted by clusterId so we don't need double for loops
        //  Bidirectional flow setup
        if (dstDevice != null) {
            Device srcDevice = deviceManager.getDeviceByDataLayerAddress(match.getDataLayerSource());
            Long srcIsland = sw.getSwitchClusterId();
            
            if (srcDevice == null) {
                log.error("No device entry found for source device {}", dstDevice.getDataLayerAddress());
                return Command.CONTINUE;
            }
            if (srcIsland == null) {
                log.error("No openflow island found for source device {}", dstDevice.getDataLayerAddress());
                return Command.CONTINUE;
            }
                                                
            // If we do not find the attachment point on the first hop island we need to flood
            boolean on_same_island = false;
            boolean on_same_if = false;
            for (DeviceAttachmentPoint dstDap : dstDevice.getAttachmentPoints()) {
                SwitchPortTuple dstSpt = dstDap.getSwitchPort();
                if ((dstSpt != null) && (dstSpt.getSw() != null)) {
                    Long dstIsland = dstDap.getSwitchPort().getSw().getSwitchClusterId();
                    if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
                        on_same_island = true;
                        if ((sw.getId() == dstDap.getSwitchPort().getSw().getId()) &&
                            (pi.getInPort() == dstSpt.getPort().shortValue())) {
                            on_same_if = true;
                        }
                        break;
                    }
                }
            }
            
            if (!on_same_island) {
                log.debug("No first hop island found for destination device {}", dstDevice.getDataLayerAddress());
                return Command.CONTINUE;
            }            
            if (on_same_if) {
                log.debug("Both source and destination are on the same switch/port {}/{}", sw.toString(), pi.getInPort());
                return Command.CONTINUE;
            }

            // Find all the routes in the same cluster
            for (DeviceAttachmentPoint srcDap : srcDevice.getAttachmentPoints()) {
                for (DeviceAttachmentPoint dstDap : dstDevice.getAttachmentPoints()) {
                    if (srcDap.equals(dstDap)) continue;

                    IOFSwitch srcSw = srcDap.getSwitchPort().getSw();
                    IOFSwitch dstSw = dstDap.getSwitchPort().getSw();
                    Long srcCluster = null;
                    Long dstCluster = null;
                    if ((srcSw != null) && (dstSw != null)) {
                        srcCluster = srcSw.getSwitchClusterId();
                        dstCluster = dstSw.getSwitchClusterId();
                    }
                    
                    if ((srcCluster != null) && (dstCluster != null) && (srcCluster.equals(dstCluster))) {
                        Route route = routingEngine.getRoute(srcSw.getId(), dstSw.getId());
                        if (route != null || validLocalHop(srcDap.getSwitchPort(), dstDap.getSwitchPort())) {
                            int bufferId = OFPacketOut.BUFFER_ID_NONE;
                            // Set the bufferId for the original PacketIn switch
                            /*
                            // TODO - finalize whether we need to set this or not
                            if (sw.getId() == srcDaps.getSwitchPort().getSw().getId()) {
                                bufferId = pi.getBufferId();
                            }
                            */
                            log.debug("Pushing route match={} route={} destination={}:{}", 
                                      new Object[] {match, route, dstDap.getSwitchPort().getSw(), 
                                                    dstDap.getSwitchPort().getPort()});
                            pushRoute(route, match, null,
                                      srcDap.getSwitchPort(), dstDap.getSwitchPort(),
                                      bufferId, sw, pi, cntx);
                            break;
                        }
                    }
                }
            }
        } else {
            // filter multicast destinations
            if ((match.getDataLayerDestination()[0] & 0x1) == 0) {
                log.debug("Unable to locate device with address {}",
                        HexString.toHexString(match
                                .getDataLayerDestination()));
            }
            return Command.CONTINUE;
        }

        return Command.STOP;
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
