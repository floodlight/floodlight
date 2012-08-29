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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;

import org.openflow.protocol.OFFlowMod;
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

public class Forwarding extends ForwardingBase implements IFloodlightModule {
    protected static Logger log = LoggerFactory.getLogger(Forwarding.class);

    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
                                                       IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        if (eth.isBroadcast() || eth.isMulticast()) {
            // For now we treat multicast as broadcast
            doFlood(sw, pi, cntx);
        } else {
            doForwardFlow(sw, pi, cntx, false);
        }
        
        return Command.CONTINUE;
    }

    protected void doForwardFlow(IOFSwitch sw, OFPacketIn pi, 
                                 FloodlightContext cntx,
                                 boolean requestFlowRemovedNotifn) {    
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());

        // Check if we have the location of the destination
        IDevice dstDevice = 
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        
        if (dstDevice != null) {
            IDevice srcDevice =
                    IDeviceService.fcStore.
                        get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
            Long srcIsland = topology.getL2DomainId(sw.getId());
            
            if (srcDevice == null) {
                log.error("No device entry found for source device");
                return;
            }
            if (srcIsland == null) {
                log.error("No openflow island found for source {}/{}", 
                          HexString.toHexString(sw.getId()), pi.getInPort());
                return;
            }

            // Validate that we have a destination known on the same island
            // Validate that the source and destination are not on the same switchport
            boolean on_same_island = false;
            boolean on_same_if = false;
            for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
                long dstSwDpid = dstDap.getSwitchDPID();
                Long dstIsland = topology.getL2DomainId(dstSwDpid);
                if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
                    on_same_island = true;
                    if ((sw.getId() == dstSwDpid) &&
                        (pi.getInPort() == dstDap.getPort())) {
                        on_same_if = true;
                    }
                    break;
                }
            }
            
            if (!on_same_island) {
                // Flood since we don't know the dst device
                if (log.isTraceEnabled()) {
                    log.trace("No first hop island found for destination " + 
                              "device {}, Action = flooding", dstDevice);
                }
                doFlood(sw, pi, cntx);
                return;
            }            
            
            if (on_same_if) {
                if (log.isTraceEnabled()) {
                    log.trace("Both source and destination are on the same " + 
                              "switch/port {}/{}, Action = NOP", 
                              sw.toString(), pi.getInPort());
                }
                return;
            }

            // Install all the routes where both src and dst have attachment
            // points.  Since the lists are stored in sorted order we can 
            // traverse the attachment points in O(m+n) time
            SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
            Arrays.sort(srcDaps, clusterIdComparator);
            SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();
            Arrays.sort(dstDaps, clusterIdComparator);

            int iSrcDaps = 0, iDstDaps = 0;

            while ((iSrcDaps < srcDaps.length) && (iDstDaps < dstDaps.length)) {
                SwitchPort srcDap = srcDaps[iSrcDaps];
                SwitchPort dstDap = dstDaps[iDstDaps];
                Long srcCluster = 
                        topology.getL2DomainId(srcDap.getSwitchDPID());
                Long dstCluster = 
                        topology.getL2DomainId(dstDap.getSwitchDPID());

                int srcVsDest = srcCluster.compareTo(dstCluster);
                if (srcVsDest == 0) {
                    if (!srcDap.equals(dstDap) && 
                        (srcCluster != null) && 
                        (dstCluster != null)) {
                        Route route = 
                                routingEngine.getRoute(srcDap.getSwitchDPID(),
                                                       (short)srcDap.getPort(),
                                                       dstDap.getSwitchDPID(),
                                                       (short)dstDap.getPort());
                        if (route != null) {
                            if (log.isTraceEnabled()) {
                                log.trace("pushRoute match={} route={} " + 
                                          "destination={}:{}",
                                          new Object[] {match, route, 
                                                        dstDap.getSwitchDPID(),
                                                        dstDap.getPort()});
                            }
                            long cookie = 
                                    AppCookie.makeCookie(FORWARDING_APP_ID, 0);
                            
                            pushRoute(route, match, 0, pi, sw.getId(), cookie, 
                                      cntx, requestFlowRemovedNotifn, false,
                                      OFFlowMod.OFPFC_ADD);
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
     * @param sw The switch that receives the OFPacketIn
     * @param pi The OFPacketIn that came to the switch
     * @param cntx The FloodlightContext associated with this OFPacketIn
     */
    protected void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        if (topology.isIncomingBroadcastAllowed(sw.getId(),
                                                pi.getInPort()) == false) {
            if (log.isTraceEnabled()) {
                log.trace("doFlood, drop broadcast packet, pi={}, " + 
                          "from a blocked port, srcSwitch=[{},{}], linkInfo={}",
                          new Object[] {pi, sw.getId(),pi.getInPort()});
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

    // IFloodlightModule methods
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't export any services
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // We don't have any services
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IDeviceService.class);
        l.add(IRoutingService.class);
        l.add(ITopologyService.class);
        l.add(ICounterStoreService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        this.deviceManager = context.getServiceImpl(IDeviceService.class);
        this.routingEngine = context.getServiceImpl(IRoutingService.class);
        this.topology = context.getServiceImpl(ITopologyService.class);
        this.counterStore = context.getServiceImpl(ICounterStoreService.class);
        
        // read our config options
        Map<String, String> configOptions = context.getConfigParams(this);
        String idleTimeout = configOptions.get("idletimeout");
        if (idleTimeout != null) {
            FLOWMOD_DEFAULT_IDLE_TIMEOUT = Short.parseShort(idleTimeout);
        }
        
        String hardTimeout = configOptions.get("hardtimeout");
        if (hardTimeout != null) {
            FLOWMOD_DEFAULT_HARD_TIMEOUT = Short.parseShort(hardTimeout);
        }
        log.debug("FlowMod idle timeout set to {} seconds", FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        log.debug("FlowMod hard timeout set to {} seconds", FLOWMOD_DEFAULT_HARD_TIMEOUT);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        super.startUp();
    }
}
