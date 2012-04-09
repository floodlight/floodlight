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

package net.floodlightcontroller.routing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.devicemanager.DeviceNetworkAddress;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.linkdiscovery.SwitchPortTuple;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ForwardingBase implements IOFMessageListener, IDeviceManagerAware {
    protected static Logger log = LoggerFactory.getLogger(ForwardingBase.class);
    
    public static final short FLOWMOD_DEFAULT_HARD_TIMEOUT=5; // in seconds

    protected IFloodlightProviderService floodlightProvider;
    protected IDeviceManagerService deviceManager;
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected ICounterStoreService counterStore;
   
    // flow-mod - for use in the cookie
    public static final int FORWARDING_APP_ID = 2; // TODO: This must be managed by a global APP_ID class


    public void startUp() {
        deviceManager.addListener(this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    @Override
    public String getName() {
        return "forwarding";
    }

    // Comparator for sorting by SwitchCluster
    public Comparator<DeviceAttachmentPoint> clusterIdComparator = new Comparator<DeviceAttachmentPoint>() {
        @Override
        public int compare(DeviceAttachmentPoint d1, DeviceAttachmentPoint d2) {
            Long d1ClusterId = topology.getSwitchClusterId(d1.getSwitchPort().getSw().getId());
            Long d2ClusterId = topology.getSwitchClusterId(d2.getSwitchPort().getSw().getId());
            return d1ClusterId.compareTo(d2ClusterId);
        }
    };

    // Comparator for sorting the attachment points by last seen.
    public Comparator<DeviceAttachmentPoint> lastSeenComparator = new Comparator<DeviceAttachmentPoint>() {
        @Override
        public int compare(DeviceAttachmentPoint d1, DeviceAttachmentPoint d2) {
            return d1.getLastSeen().compareTo(d2.getLastSeen());
        }
    };

    /**
      * All subclasses must define this function if they want any specific forwarding action
     * @param sw Switch that the packet came in from
     * @param pi The packet that came in
     * @param decision Any decision made by a policy engine
     */
    public abstract Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx);

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                IRoutingDecision decision = null;
                if (cntx != null) decision = 
                    IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION); 
                
                return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
        }
        log.error("received an unexpected message {} from switch {}", msg, sw);
        return Command.CONTINUE;
    }

    /**
     * Push routes from back to front
     * @param route Route to push
     * @param match OpenFlow fields to match on
     * @param srcSwPort Source switch port for the first hop
     * @param dstSwPort Destination switch port for final hop
     * @param bufferId BufferId of the original PacketIn
     * @param cookie The cookie to set in each flow_mod
     * @param cntx The floodlight context
     * @param reqeustFlowRemovedNotifn if set to true then the switch would
     * send a flow mod removal notification when the flow mod expires
     * @param doFlush if set to true then the flow mod would be immediately
     *        written to the switch
     * @param flowModCommand flow mod. command to use, e.g. OFFlowMod.OFPFC_ADD,
     *        OFFlowMod.OFPFC_MODIFY etc.
     * @return srcSwitchIincluded True if the source switch is included in this route
     */
    public boolean pushRoute(Route route, OFMatch match, Integer wildcard_hints,
                          SwitchPortTuple srcSwPort,
                          SwitchPortTuple dstSwPort, int bufferId,
                          IOFSwitch srcSwitch, OFPacketIn pi, long cookie, 
                          FloodlightContext cntx,
                          boolean reqeustFlowRemovedNotifn,
                          boolean doFlush,
                          short   flowModCommand) {

        boolean srcSwitchIncluded = false;
        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        OFActionOutput action = new OFActionOutput();
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);
        fm.setIdleTimeout((short)5)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(cookie)
            .setCommand(flowModCommand)
            .setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        IOFSwitch sw = dstSwPort.getSw();
        ((OFActionOutput)fm.getActions().get(0)).setPort(dstSwPort.getPort());

        if (route != null) {
            for (int routeIndx = route.getPath().size() - 1; routeIndx >= 0; --routeIndx) {
                Link link = route.getPath().get(routeIndx);
                fm.setMatch(wildcard(match, sw, wildcard_hints));
                fm.getMatch().setInputPort(link.getDstPort());
                try {
                    counterStore.updatePktOutFMCounterStore(sw, fm);
                    if (log.isDebugEnabled()) {
                        log.debug("Pushing Route flowmod routeIndx={} sw={} inPort={} outPort={}",
                                  new Object[] { routeIndx, sw, fm.getMatch().getInputPort(), 
                                                 ((OFActionOutput)fm.getActions().get(0)).getPort() });
                    }
                    sw.write(fm, cntx);
                    if (doFlush) {
                        sw.flush();
                    }

                    // Push the packet out the source switch
                    if (sw.getId() == srcSwitch.getId()) {
                        pushPacket(srcSwitch, match, pi, ((OFActionOutput)fm.getActions().get(0)).getPort(), cntx);
                        srcSwitchIncluded = true;
                    }
                } catch (IOException e) {
                    log.error("Failure writing flow mod", e);
                }
                try {
                    fm = fm.clone();
                } catch (CloneNotSupportedException e) {
                    log.error("Failure cloning flow mod", e);
                }

                // setup for the next loop iteration
                ((OFActionOutput)fm.getActions().get(0)).setPort(link.getSrcPort());
                if (routeIndx > 0) {
                    sw = floodlightProvider.getSwitches().get(route.getPath().get(routeIndx-1).getDst());
                } else {
                    sw = floodlightProvider.getSwitches().get(route.getId().getSrc());
                }
                if (sw == null) {
                    if (log.isWarnEnabled()) {
                        log.warn("Unable to push route, switch at DPID {} not available",
                                (routeIndx > 0) ? HexString.toHexString(route.getPath()
                                        .get(routeIndx - 1).getDst()) : HexString
                                        .toHexString(route.getId().getSrc()));
                    }
                    return srcSwitchIncluded;
                }
            }
        }
     
        // set the original match for the first switch, and buffer id
        fm.setMatch(match);
        fm.setBufferId(bufferId);
        fm.setMatch(wildcard(match, sw, wildcard_hints));
        fm.getMatch().setInputPort(srcSwPort.getPort());
        // Set the flag to request flow-mod removal notifications only for the 
        // source switch. The removal message is used to maintain the flow
        // cache. Don't set the flag for ARP messages - TODO generalize check
        if ((reqeustFlowRemovedNotifn) &&
            (match.getDataLayerType() != Ethernet.TYPE_ARP)) {
            fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
            match.setWildcards(fm.getMatch().getWildcards());
        }

        try {
            counterStore.updatePktOutFMCounterStore(sw, fm);
            if (log.isDebugEnabled()) {
                log.debug("pushRoute flowmod sw={} inPort={} outPort={}",
                      new Object[] { sw, fm.getMatch().getInputPort(), 
                                    ((OFActionOutput)fm.getActions().get(0)).getPort() });
            }
            sw.write(fm, cntx);
            if (doFlush) {
                sw.flush();
            }

            if (sw.getId() == srcSwitch.getId()) {
                pushPacket(srcSwitch, match, pi, ((OFActionOutput)fm.getActions().get(0)).getPort(), cntx);
                srcSwitchIncluded = true;
            }
            match = fm.getMatch();
        } catch (IOException e) {
            log.error("Failure writing flow mod", e);
        }

        return srcSwitchIncluded;
    }

    protected OFMatch wildcard(OFMatch match, IOFSwitch sw, Integer wildcard_hints) {
        if (wildcard_hints != null) {
            return match.clone().setWildcards(wildcard_hints.intValue());
        }
        return match.clone();
    }

    /**
     * This function will push a packet-out to a switch.  The assumption here is that
     * the packet-in was also generated from the same switch.  Thus, if the input
     * port of the packet-in and the outport are the same, the function will not 
     * push the packet-out.
     * @param sw        switch that generated the packet-in, and from which packet-out is sent
     * @param match     OFmatch
     * @param pi        packet-in
     * @param outport   output port
     * @param cntx      context of the packet
     */
    public void pushPacket(IOFSwitch sw, OFMatch match, OFPacketIn pi, short outport, FloodlightContext cntx) {
        
        if (pi == null) {
            return;
        }
        
        // The assumption here is (sw) is the switch that generated the packet-in.
        // If the input port is the same as output port, then the packet-out should
        // be ignored.
        if (pi.getInPort() == outport) {
            if (log.isDebugEnabled()) {
                log.debug("Attemping to do packet-out to the same interface as packet-in. Dropping packet.  SrcSwitch={}, match = {}, pi={}", new Object[]{sw, match, pi});
                return;
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug("PacketOut srcSwitch={} match={} pi={}", new Object[] {sw, match, pi});
        }
        
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(outport, (short) 0));

        po.setActions(actions)
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength = (short)(po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
        
        // set buffer_id, in_port
        po.setBufferId(pi.getBufferId());
        po.setInPort(pi.getInPort());

        // set data - only if buffer_id == -1
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }

        po.setLength(poLength);
        
        try {
            counterStore.updatePktOutFMCounterStore(sw, po);
            sw.write(po, cntx);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }

    public static boolean blockHost(IFloodlightProviderService floodlightProvider, 
            SwitchPortTuple sw_tup, long host_mac, 
            short hardTimeout) {

        if ((sw_tup == null) || sw_tup.getSw() == null) {
            return false;
        }

        IOFSwitch sw = sw_tup.getSw();
        short inputPort = sw_tup.getPort().shortValue();    
        log.debug("blockHost sw={} port={} mac={}",
                new Object[] { sw, sw_tup.getPort(), new Long(host_mac) });

        // Create flow-mod based on packet-in and src-switch
        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        OFMatch match = new OFMatch();
        List<OFAction> actions = new ArrayList<OFAction>(); // Set no action to drop
        match.setDataLayerSource(Ethernet.toByteArray(host_mac))
            .setInputPort(inputPort)
            .setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_IN_PORT);
        fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
            .setHardTimeout((short)hardTimeout)
            .setIdleTimeout((short)5)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH); // +OFActionOutput.MINIMUM_LENGTH);

        try {
            log.debug("write drop flow-mod sw={} match={} flow-mod={}",
                       new Object[] {sw, match, fm});
            sw.write(fm, null);
        }
        catch (IOException e) {
            log.error("Failure writing deny flow mod", e);
            return false;
        }
        return true;

    }

    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    /**
     * @param routingEngine the routingEngine to set
     */
    public void setRoutingEngine(IRoutingService routingEngine) {
        this.routingEngine = routingEngine;
    }

    /**
     * @param deviceManager the deviceManager to set
     */
    public void setDeviceManager(IDeviceManagerService deviceManager) {
        this.deviceManager = deviceManager;
    }

    /**
     * @param topology the topology to set
     */
    public void setTopology(ITopologyService topology) {
        this.topology = topology;
    }
    
    public ICounterStoreService getCounterStore() {
        return counterStore;
    }
    
    public void setCounterStore(ICounterStoreService counterStore) {
        this.counterStore = counterStore;
    }

    @Override
    public void deviceAdded(Device device) {
        // NOOP
    }

    @Override
    public void deviceRemoved(Device device) {
        // NOOP
    }

    @Override
    public void deviceMoved(Device device, IOFSwitch oldSw, Short oldPort,
            IOFSwitch sw, Short port) {
        // Build flow mod to delete based on destination mac == device mac
        OFMatch match = new OFMatch();
        match.setDataLayerDestination(device.getDataLayerAddress());
        match.setWildcards(OFMatch.OFPFW_ALL ^ OFMatch.OFPFW_DL_DST);
        long cookie =
                AppCookie.makeCookie(FORWARDING_APP_ID, 0);
        OFMessage fm = ((OFFlowMod) floodlightProvider.getOFMessageFactory()
            .getMessage(OFType.FLOW_MOD))
            .setCommand(OFFlowMod.OFPFC_DELETE)
            .setOutPort((short) OFPort.OFPP_NONE.getValue())
            .setMatch(match)
            .setCookie(cookie)
            .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));

        // Flush to all switches
        for (IOFSwitch outSw : floodlightProvider.getSwitches().values()) {
            try {
                outSw.write(fm, null);
            } catch (IOException e) {
                log.error("Failure sending flow mod delete for moved device", e);
            }
        }
    }

    @Override
    public void deviceNetworkAddressAdded(Device device,
            DeviceNetworkAddress address) {
        
    }

    @Override
    public void deviceNetworkAddressRemoved(Device device,
            DeviceNetworkAddress address) {
        
    }
    
    @Override
    public void deviceVlanChanged(Device device) {

    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) &&
                (name.equals("topology") || name.equals("devicemanager")));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

}
