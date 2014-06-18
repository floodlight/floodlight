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
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LogMessageCategory("Flow Programming")
public class Forwarding extends ForwardingBase implements IFloodlightModule {
    protected static Logger log = LoggerFactory.getLogger(Forwarding.class);

    @Override
    @LogMessageDoc(level="ERROR",
                   message="Unexpected decision made for this packet-in={}",
                   explanation="An unsupported PacketIn decision has been " +
                   		"passed to the flow programming component",
                   recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision,
                                          FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                   IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // If a decision has been made we obey it
        // otherwise we just forward
        if (decision != null) {
            if (log.isTraceEnabled()) {
                log.trace("Forwaring decision={} was made for PacketIn={}",
                        decision.getRoutingAction().toString(),
                        pi);
            }

            switch(decision.getRoutingAction()) {
                case NONE:
                    // don't do anything
                    return Command.CONTINUE;
                case FORWARD_OR_FLOOD:
                case FORWARD:
                    doForwardFlow(sw, pi, cntx, false);
                    return Command.CONTINUE;
                case MULTICAST:
                    // treat as broadcast
                    doFlood(sw, pi, cntx);
                    return Command.CONTINUE;
                case DROP:
                    doDropFlow(sw, pi, decision, cntx);
                    return Command.CONTINUE;
                default:
                    log.error("Unexpected decision made for this packet-in={}",
                            pi, decision.getRoutingAction());
                    return Command.CONTINUE;
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("No decision was made for PacketIn={}, forwarding",
                        pi);
            }

            if (eth.isBroadcast() || eth.isMulticast()) {
                // For now we treat multicast as broadcast
                doFlood(sw, pi, cntx);
            } else {
                doForwardFlow(sw, pi, cntx, false);
            }
        }

        return Command.CONTINUE;
    }

    @LogMessageDoc(level="ERROR",
            message="Failure writing drop flow mod",
            explanation="An I/O error occured while trying to write a " +
            		"drop flow mod to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    protected void doDropFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        // initialize match structure and populate it using the packet
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        if (decision.getWildcards() != null) {
            match.setWildcards(decision.getWildcards());
        }

        // Create flow-mod based on packet-in and src-switch
        OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);
        List<OFAction> actions = new ArrayList<OFAction>(); // Set no action to
                                                            // drop
        long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

        fm.setCookie(cookie)
          .setHardTimeout((short) 0)
          .setIdleTimeout((short) 5)
          .setBufferId(OFPacketOut.BUFFER_ID_NONE)
          .setMatch(match)
          .setActions(actions)
          .setLengthU(OFFlowMod.MINIMUM_LENGTH); // +OFActionOutput.MINIMUM_LENGTH);

        try {
            if (log.isDebugEnabled()) {
                log.debug("write drop flow-mod sw={} match={} flow-mod={}",
                          new Object[] { sw, match, fm });
            }
            messageDamper.write(sw, fm, cntx);
        } catch (IOException e) {
            log.error("Failure writing drop flow mod", e);
        }
    }

    protected void doForwardFlow(IOFSwitch sw, OFPacketIn pi,
                                 FloodlightContext cntx,
                                 boolean requestFlowRemovedNotifn) {
        //OFMatch match = new OFMatch();
        //match.loadFromPacket(pi.getData(), pi.getInPort());
        //Match.Builder matchBuilder = sw.getOFFactory().buildMatch();
        Match match = pi.getMatch();
        // Check if we have the location of the destination
        IDevice dstDevice =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);

        // DESTINATION DEVICE = KNOWN
        if (dstDevice != null) {
            IDevice srcDevice =
                    IDeviceService.fcStore.
                        get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
            DatapathId srcIsland = topology.getL2DomainId(sw.getId());

            // If we can't find the source device, that's no good
            if (srcDevice == null) {
                log.debug("No device entry found for source device");
                return;
            }
            
            // If the source isn't connected to an OF island we control, then that's not good either
            if (srcIsland == null) {
                log.debug("No openflow island found for source {}/{}",
                          sw.getStringId(), pi.getInPort());
                return;
            }

            // Validate that we have a destination known on the same island
            // Validate that the source and destination are not on the same switch port
            boolean on_same_island = false;
            boolean on_same_if = false;
            for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
                DatapathId dstSwDpid = dstDap.getSwitchDPID();
                DatapathId dstIsland = topology.getL2DomainId(dstSwDpid);
                if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
                    on_same_island = true;
                    if ((sw.getId().equals(dstSwDpid)) &&
                        (pi.getInPort().equals(dstDap.getPort()))) {
                        on_same_if = true;
                    }
                    break;
                }
            }

            // If the two devices are not on the same L2 network, find out how to get to the destination via flooding
            if (!on_same_island) {
                if (log.isTraceEnabled()) {
                    log.trace("No first hop island found for destination " +
                              "device {}, Action = flooding", dstDevice);
                }
                doFlood(sw, pi, cntx);
                return;
            }

            // If the two devices are on the same switch port number, they should be able to communicate w/o further flows
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

                // srcCluster and dstCluster here cannot be null as
                // every switch will be at least in its own L2 domain.
                DatapathId srcCluster =
                        topology.getL2DomainId(srcDap.getSwitchDPID());
                DatapathId dstCluster =
                        topology.getL2DomainId(dstDap.getSwitchDPID());

                int srcVsDest = srcCluster.compareTo(dstCluster);
                if (srcVsDest == 0) {
                    if (!srcDap.equals(dstDap)) {
                        Route route =
                                routingEngine.getRoute(srcDap.getSwitchDPID(),
                                                       srcDap.getPort(),
                                                       dstDap.getSwitchDPID(),
                                                       dstDap.getPort(), 0); //cookie = 0, i.e., default route
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

                            // if there is prior routing decision use wildcard
                            Match.Builder wildcard_hints;
                            IRoutingDecision decision = null;
                            if (cntx != null) {
                                decision = IRoutingDecision.rtStore
                                        .get(cntx,
                                                IRoutingDecision.CONTEXT_DECISION);
                            }
                            if (decision != null) {
                                wildcard_hints = decision.getWildcards();
                            } else {
                            	// L2 only wildcard if there is no prior route decision
                                /*wildcard_hints = ((Integer) sw
                                        .getAttribute(IOFSwitch.PROP_FASTWILDCARDS))
                                        .intValue()
                                        & ~OFMatch.OFPFW_IN_PORT
                                        & ~OFMatch.OFPFW_DL_VLAN
                                        & ~OFMatch.OFPFW_DL_SRC
                                        & ~OFMatch.OFPFW_DL_DST
                                        & ~OFMatch.OFPFW_NW_SRC_MASK
                                        & ~OFMatch.OFPFW_NW_DST_MASK;*/
                            	//TODO @Ryan does the use of NO_MASK here automatically assume isFullyWildcarded is true?
                            	// dummy values set to not trigger isFullyWildcarded
                            	wildcard_hints = sw.getOFFactory().buildMatch();
                                wildcard_hints.setExact(MatchField.IN_PORT, OFPort.of(1));
                                wildcard_hints.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(1));
                                wildcard_hints.setExact(MatchField.ETH_SRC, MacAddress.BROADCAST);
                                wildcard_hints.setExact(MatchField.ETH_DST, MacAddress.BROADCAST);
                                wildcard_hints.setExact(MatchField.IPV4_SRC, IPv4Address.FULL_MASK);
                                wildcard_hints.setExact(MatchField.IPV4_DST, IPv4Address.FULL_MASK);
                            }

                            pushRoute(route, match, wildcard_hints, pi, sw.getId(), cookie,
                                      cntx, requestFlowRemovedNotifn, false,
                                      OFFlowModCommand.ADD);
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
        // END DESTINATION DEVICE = KNOWN
        } else {
        	// DESTINATION DEVICE = UNKNOWN, thus flood to hopefully/eventually find out where the destination is
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
    @LogMessageDoc(level="ERROR",
                   message="Failure writing PacketOut " +
                   		"switch={switch} packet-in={packet-in} " +
                   		"packet-out={packet-out}",
                   explanation="An I/O error occured while writing a packet " +
                   		"out message to the switch",
                   recommendation=LogMessageDoc.CHECK_SWITCH)
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
            actions.add(new OFActionOutput(OFPort.OFPP_FLOOD.getValue(),
                                           (short)0xFFFF));
        } else {
            actions.add(new OFActionOutput(OFPort.OFPP_ALL.getValue(),
                                           (short)0xFFFF));
        }
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set buffer-id, in-port and packet-data based on packet-in
        short poLength = (short)(po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(pi.getInPort());
        byte[] packetData = pi.getPacketData();
        poLength += packetData.length;
        po.setPacketData(packetData);
        po.setLength(poLength);

        try {
            if (log.isTraceEnabled()) {
                log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
                          new Object[] {sw, pi, po});
            }
            messageDamper.write(sw, po, cntx);
        } catch (IOException e) {
            log.error("Failure writing PacketOut switch={} packet-in={} packet-out={}",
                    new Object[] {sw, pi, po}, e);
        }

        return;
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
        l.add(IOFSwitchService.class);
        l.add(IDeviceService.class);
        l.add(IRoutingService.class);
        l.add(ITopologyService.class);
        l.add(ICounterStoreService.class);
        return l;
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Error parsing flow idle timeout, " +
                        "using default of {number} seconds",
                explanation="The properties file contains an invalid " +
                        "flow idle timeout",
                recommendation="Correct the idle timeout in the " +
                        "properties file."),
        @LogMessageDoc(level="WARN",
                message="Error parsing flow hard timeout, " +
                        "using default of {number} seconds",
                explanation="The properties file contains an invalid " +
                            "flow hard timeout",
                recommendation="Correct the hard timeout in the " +
                                "properties file.")
    })
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        super.init();
        this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        this.switchService = context.getServiceImpl(IOFSwitchService.class);
        this.deviceManager = context.getServiceImpl(IDeviceService.class);
        this.routingEngine = context.getServiceImpl(IRoutingService.class);
        this.topology = context.getServiceImpl(ITopologyService.class);
        this.counterStore = context.getServiceImpl(ICounterStoreService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        super.startUp();
    }
}
