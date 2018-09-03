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

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.*;
import net.floodlightcontroller.routing.web.RoutingWebRoutable;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.*;

import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;
import org.python.google.common.collect.ImmutableList;
import org.python.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class Forwarding extends ForwardingBase implements IFloodlightModule, IOFSwitchListener, ILinkDiscoveryListener,
        IRoutingDecisionChangedListener, IGatewayService {
    protected static final Logger log = LoggerFactory.getLogger(Forwarding.class);

    /*
     * Cookies are 64 bits:
     * Example: 0x0123456789ABCDEF
     * App ID:  0xFFF0000000000000
     * User:    0x000FFFFFFFFFFFFF
     * 
     * Of the user portion, we further subdivide into routing decision 
     * bits and flowset bits. The former relates the flow to routing
     * decisions, such as firewall allow or deny/drop. It allows for 
     * modification of the flows upon a future change in the routing 
     * decision. The latter indicates a "family" of flows or "flowset" 
     * used to complete an end-to-end connection between two devices
     * or hosts in the network. It is used to assist in the entire
     * flowset removal upon a link or port down event anywhere along
     * the path. This is required in order to allow a new path to be
     * used and a new flowset installed.
     * 
     * TODO: shrink these masks if you need to add more subfields
     * or need to allow for a larger number of routing decisions
     * or flowsets
     */

    private static final short DECISION_BITS = 24;
    private static final short DECISION_SHIFT = 0;
    private static final long DECISION_MASK = ((1L << DECISION_BITS) - 1) << DECISION_SHIFT;

    private static final short FLOWSET_BITS = 28;
    protected static final short FLOWSET_SHIFT = DECISION_BITS;
    private static final long FLOWSET_MASK = ((1L << FLOWSET_BITS) - 1) << FLOWSET_SHIFT;
    private static final long FLOWSET_MAX = (long) (Math.pow(2, FLOWSET_BITS) - 1);
    protected static FlowSetIdRegistry flowSetIdRegistry;

    private static L3RoutingManager l3manager;
    private static volatile IRoutingService.RoutingType routingType = IRoutingService.RoutingType.FORWARDING;

    private Map<OFPacketIn, Ethernet> l3cache;
    private DeviceListenerImpl deviceListener;

    protected static class FlowSetIdRegistry {
        private volatile Map<NodePortTuple, Set<U64>> nptToFlowSetIds;
        private volatile Map<U64, Set<NodePortTuple>> flowSetIdToNpts;
        
        private volatile long flowSetGenerator = -1;

        private static volatile FlowSetIdRegistry instance;

        private FlowSetIdRegistry() {
            nptToFlowSetIds = new ConcurrentHashMap<>();
            flowSetIdToNpts = new ConcurrentHashMap<>();
        }

        protected static FlowSetIdRegistry getInstance() {
            if (instance == null) {
                instance = new FlowSetIdRegistry();
            }
            return instance;
        }
        
        /**
         * Only for use by unit test to help w/ordering
         * @param seed
         */
        protected void seedFlowSetIdForUnitTest(int seed) {
            flowSetGenerator = seed;
        }
        
        protected synchronized U64 generateFlowSetId() {
            flowSetGenerator += 1;
            if (flowSetGenerator == FLOWSET_MAX) {
                flowSetGenerator = 0;
                log.warn("Flowset IDs have exceeded capacity of {}. Flowset ID generator resetting back to 0", FLOWSET_MAX);
            }
            U64 id = U64.of(flowSetGenerator << FLOWSET_SHIFT);
            log.debug("Generating flowset ID {}, shifted {}", flowSetGenerator, id);
            return id;
        }

        private void registerFlowSetId(NodePortTuple npt, U64 flowSetId) {
            if (nptToFlowSetIds.containsKey(npt)) {
                Set<U64> ids = nptToFlowSetIds.get(npt);
                ids.add(flowSetId);
            } else {
                Set<U64> ids = new HashSet<>();
                ids.add(flowSetId);
                nptToFlowSetIds.put(npt, ids);
            }  

            if (flowSetIdToNpts.containsKey(flowSetId)) {
                Set<NodePortTuple> npts = flowSetIdToNpts.get(flowSetId);
                npts.add(npt);
            } else {
                Set<NodePortTuple> npts = new HashSet<>();
                npts.add(npt);
                flowSetIdToNpts.put(flowSetId, npts);
            }
        }

        private Set<U64> getFlowSetIds(NodePortTuple npt) {
            return nptToFlowSetIds.get(npt);
        }

        private Set<NodePortTuple> getNodePortTuples(U64 flowSetId) {
            return flowSetIdToNpts.get(flowSetId);
        }

        private void removeNodePortTuple(NodePortTuple npt) {
            nptToFlowSetIds.remove(npt);

            Iterator<Set<NodePortTuple>> itr = flowSetIdToNpts.values().iterator();
            while (itr.hasNext()) {
                Set<NodePortTuple> npts = itr.next();
                npts.remove(npt);
            }
        }

        private void removeExpiredFlowSetId(U64 flowSetId, NodePortTuple avoid, Iterator<U64> avoidItr) {
            flowSetIdToNpts.remove(flowSetId);

            Iterator<Entry<NodePortTuple, Set<U64>>> itr = nptToFlowSetIds.entrySet().iterator();
            boolean removed = false;
            while (itr.hasNext()) {
                Entry<NodePortTuple, Set<U64>> e = itr.next();
                if (e.getKey().equals(avoid) && ! removed) {
                    avoidItr.remove();
                    removed = true;
                } else {
                    Set<U64> ids = e.getValue();
                    ids.remove(flowSetId);
                }
            }
        }
    }

    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        OFPort inPort = OFMessageUtils.getInPort(pi);
        NodePortTuple npt = new NodePortTuple(sw.getId(), inPort);

        if (decision != null) {
            if (log.isTraceEnabled()) {
                log.trace("Forwarding decision={} was made for PacketIn={}", decision.getRoutingAction().toString(), pi);
            }

            switch(decision.getRoutingAction()) {
                case NONE:
                    // don't do anything
                    return Command.CONTINUE;

                case FORWARD_OR_FLOOD:
                case FORWARD:
                    doL2ForwardFlow(sw, pi, decision, cntx, false);
                    return Command.CONTINUE;

                case MULTICAST:
                    // treat as broadcast
                    doFlood(sw, pi, decision, cntx);
                    return Command.CONTINUE;

                case DROP:
                    doDropFlow(sw, pi, decision, cntx);
                    return Command.CONTINUE;

                default:
                    log.error("Unexpected decision made for this packet-in={}", pi, decision.getRoutingAction());
                    return Command.CONTINUE;

            }
        }
        else { // No routing decision was found
            switch(determineRoutingType()) {
                case FORWARDING:
                    // L2 Forward to destination or flood if bcast or mcast
                    if (log.isTraceEnabled()) {
                        log.trace("No decision was made for PacketIn={}, do L2 forwarding", pi);
                    }
                    doL2Forwarding(eth, sw, pi, decision, cntx);
                    break;

                case ROUTING:
                    // TODO: IPv6 not consider for now
                    if (log.isTraceEnabled()) {
                        log.trace("No decision was made for PacketIn={}, do L3 routing", pi);
                    }

                    Optional<VirtualGatewayInstance> instance = getGatewayInstance(sw.getId());
                    if (!instance.isPresent()) {
                        instance = getGatewayInstance(npt);
                    }

                    if (!instance.isPresent()) {
                        log.info("Could not locate virtual gateway instance for DPID {}, port {}", sw.getId(), inPort);
                        break;
                    }

                    doL3Routing(eth, sw, pi, decision, cntx, instance.get(), inPort);
                    break;

                default:
                    log.error("Unexpected routing behavior for this packet-in={} on switch {}", pi, sw.getId());
                    break;
            }

        }

        return Command.CONTINUE;
    }

    /**
     * Determine the Routing Type
     *
     * @return
     */
    private IRoutingService.RoutingType determineRoutingType() {
        if (routingEngineService.isL3RoutingEnabled()) {
            return IRoutingService.RoutingType.ROUTING;
        }
        else {
            return IRoutingService.RoutingType.FORWARDING;
        }
    }

    /**
     * Determine if the message is broadcast or multicast
     *
     * @param eth Ethernet Frame that associated with this OFPacketIn
     * @return
     */
    private boolean isBroadcastOrMulticast(Ethernet eth) {
        return eth.isBroadcast() || eth.isMulticast();
    }

    /**
     * L3 routing block -- it supports routing across subnet by using "virtual gateway". It also keeps L2 forwarding
     * as Floodlight original does
     *
     * @param eth Ethernet Frame that associated with this OFPacketIn
     * @param sw Switch on which the packet was received
     * @param pi FPacketIn that came to the switch
     * @param decision Decision that caused flooding, or null
     * @param cntx FloodlightContext associated with this OFPacketIn
     * @param gatewayInstance Virtual gateway instance
     * @param inPort Ingress switch port on which the packet was received
     */
    protected void doL3Routing(Ethernet eth, IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision,
                             FloodlightContext cntx, @Nonnull VirtualGatewayInstance gatewayInstance, OFPort inPort) {

        MacAddress gatewayMac = gatewayInstance.getGatewayMac();

        if (eth.getEtherType() == EthType.IPv4) {
            IPv4Address intfIpAddress = findInterfaceIP(gatewayInstance, ((IPv4) eth.getPayload()).getDestinationAddress());
            if (intfIpAddress == null) {
                log.debug("Can not locate corresponding interface for gateway {}, check its interface configuration",
                        gatewayInstance.getName());
                return;
            }
        }

        if (isBroadcastOrMulticast(eth)) {
            // When cross-subnet, host send ARP request to gateway. Gateway need to generate ARP response to host
            if (eth.getEtherType() == EthType.ARP && ((ARP) eth.getPayload()).getOpCode().equals(ARP.OP_REQUEST)
                    && gatewayInstance.isAGatewayIntf(((ARP) eth.getPayload()).getTargetProtocolAddress())) {
                IPacket arpReply = gatewayArpReply(cntx, gatewayMac);
                pushArpReply(arpReply, sw, OFBufferId.NO_BUFFER, OFPort.ANY, inPort);
                log.debug("Virtual gateway pushing ARP reply message to source host");
            }
            else {
                doFlood(sw, pi, decision, cntx);
            }
        }
        else {
            // This also includes L2 forwarding
            doL3ForwardFlow(sw, pi, decision, cntx, gatewayInstance, false);
        }

    }


    /**
     * This function retrieves a patch from source device to destination device and then install flows(L3/L2) over the path.
     * In L3 case, virtual router caches the packet-in while it sends the ARP message to learn destination device
     *
     * @param sw
     * @param pi
     * @param decision
     * @param cntx
     * @param gateway
     * @param requestFlowRemovedNotifn
     */
    protected void doL3ForwardFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx,
                                   VirtualGatewayInstance gateway, boolean requestFlowRemovedNotifn) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        OFPort srcPort = OFMessageUtils.getInPort(pi);

        MacAddress virtualGatewayMac = gateway.getGatewayMac();
        DatapathId srcSw = sw.getId();
        IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        IDevice srcDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);

        if (dstDevice == null) {
            // Try one more time to retrieve dst device
            if (eth.getEtherType() == EthType.IPv4 && eth.getDestinationMACAddress().equals(virtualGatewayMac)) {
                dstDevice = findDstDeviceForL3Routing(((IPv4) eth.getPayload()).getDestinationAddress());
            }

            if (dstDevice == null) {
                // L3 traffic at 1st hop, virtual gateway creates & floods ARP to learn destination device
                if (eth.getEtherType() == EthType.IPv4 && eth.getDestinationMACAddress().equals(virtualGatewayMac)) {
                    log.debug("Virtual gateway creates and flood arp request packet for destination host");
                    doL3Flood(gateway, sw, pi, cntx);

                    l3cache.put(pi, eth);
                    log.debug("Add new packet-in associate with packet source {} and destination {} to cache",
                            ((IPv4) eth.getPayload()).getSourceAddress(), ((IPv4) eth.getPayload()).getDestinationAddress());
                }
                // Normal L2 traffic
                else {
                    log.debug("Destination device unknown. Flooding packet");
                    doFlood(sw, pi, decision, cntx);
                }

                return;
            }
        }

        if (srcDevice == null) {
            log.error("No device entry found for source device. Is the device manager running? If so, report bug.");
            return;
        }

        /* Some physical switches partially support or do not support ARP flows */
        if (FLOOD_ALL_ARP_PACKETS &&
                IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD).getEtherType()
                        == EthType.ARP) {
            log.debug("ARP flows disabled in Forwarding. Flooding ARP packet");
            doFlood(sw, pi, decision, cntx);
            return;
        }

        /* This packet-in is from a switch in the path before its flow was installed along the path */
        if (!topologyService.isEdge(srcSw, srcPort) && !eth.getDestinationMACAddress().equals(virtualGatewayMac)) {
            log.debug("Packet destination is known, but packet was not received on an edge port (rx on {}/{}). Flooding packet", srcSw, srcPort);
            doFlood(sw, pi, decision, cntx);
            return;
        }

        /*
         * Search for the true attachment point. The true AP is
         * not an endpoint of a link. It is a switch port w/o an
         * associated link. Note this does not necessarily hold
         * true for devices that 'live' between OpenFlow islands.
         *
         * TODO Account for the case where a device is actually
         * attached between islands (possibly on a non-OF switch
         * in between two OpenFlow switches).
         */
        SwitchPort dstAp = null;
        for (SwitchPort ap : dstDevice.getAttachmentPoints()) {
            if (topologyService.isEdge(ap.getNodeId(), ap.getPortId())) {
                dstAp = ap;
                break;
            }
        }

        /*
         * This should only happen (perhaps) when the controller is
         * actively learning a new topology and hasn't discovered
         * all links yet, or a switch was in standalone mode and the
         * packet in question was captured in flight on the dst point
         * of a link.
         */
        if (dstAp == null) {
            if (eth.getDestinationMACAddress().equals(virtualGatewayMac)) { // Try L3 Flood again
                log.debug("Virtual gateway creates arp request packet to destination host and flooding");
                doL3Flood(gateway, sw, pi, cntx);
            }
            else { // Try L2 Flood again
                log.debug("Could not locate edge attachment point for destination device {}. Flooding packet");
                doFlood(sw, pi, decision, cntx);
            }
            return;
        }

        /* Validate that the source and destination are not on the same switch port */
        if (sw.getId().equals(dstAp.getNodeId()) && srcPort.equals(dstAp.getPortId())) {
            log.debug("Both source and destination are on the same switch/port {}/{}. Dropping packet", sw.toString(), srcPort);
            return;
        }

        // All edge cases excluded, consider adding L3 logic below
        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
        U64 cookie = makeForwardingCookie(decision, flowSetId);
        Path path = routingEngineService.getPath(srcSw,
                srcPort,
                dstAp.getNodeId(),
                dstAp.getPortId());


        if (!eth.getDestinationMACAddress().equals(virtualGatewayMac)) { // Normal L2 forwarding
            Match m = createMatchFromPacket(sw, srcPort, pi, cntx);

            if (! path.getPath().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("pushRoute inPort={} route={} " +
                                    "destination={}:{}",
                            new Object[] { srcPort, path,
                                    dstAp.getNodeId(),
                                    dstAp.getPortId()});
                    log.debug("Creating flow rules on the route, match rule: {}", m);
                }

                pushRoute(path, m, pi, sw.getId(), cookie,
                        cntx, requestFlowRemovedNotifn,
                        OFFlowModCommand.ADD, false);

                /*
                 * Register this flowset with ingress and egress ports for link down
                 * flow removal. This is done after we push the path as it is blocking.
                 */
                for (NodePortTuple npt : path.getPath()) {
                    flowSetIdRegistry.registerFlowSetId(npt, flowSetId);
                }

            } /* else no path was found */
        }
        else { // L3 Routing
            boolean packetOutSent = sendPacketToLastHop(eth, dstDevice);

            // L3 rewrite on first hop (in bi-direction)
            IOFSwitch firstHop = switchService.getSwitch(srcSw);
            Match match = createMatchFromPacket(firstHop, srcPort, pi, cntx);

            if (!path.getPath().isEmpty()){
                log.debug("L3 path is {}", path.getPath());
            }

            OFPort outPort = path.getPath().get(path.getPath().size()-1).getPortId();

            buildRewriteFlows(pi, match, srcSw, outPort, cookie,
                    virtualGatewayMac, dstDevice.getMACAddress(), requestFlowRemovedNotifn);

            // Remove first hop, push routes as normal in the middle
            Path newPath = getNewPath(path);
            pushRoute(newPath, match, pi, sw.getId(), cookie,
                    cntx, requestFlowRemovedNotifn,
                    OFFlowModCommand.ADD, packetOutSent);

            /* Register flow sets */
            for (NodePortTuple npt : path.getPath()) {
                flowSetIdRegistry.registerFlowSetId(npt, flowSetId);
            }

        }
    }

    /**
     * Virtual gateway insert flows on switch to rewrite source MAC to gateway MAC, also rewrite destination MAC
     * to destination host.
     *
     * @param pi The OFPacketIn that came to the switch
     * @param match The OpenFlow Match
     * @param sw The switch that receives the OFPacketIn
     * @param outPort The outgoing port number
     * @param cookie The OpenFlow cookie
     * @param gatewayMac The Virtual Gateway MAC address
     * @param hostMac The host MAC address
     * @param requestFlowRemovedNotification The OpenFlow "flow remove flag"
     */
    protected void buildRewriteFlows(@Nonnull OFPacketIn pi, @Nonnull Match match, @Nonnull DatapathId sw,
                                     @Nonnull OFPort outPort, @Nonnull U64 cookie, @Nonnull MacAddress gatewayMac,
                                     @Nonnull MacAddress hostMac, boolean requestFlowRemovedNotification) {
        OFFactory factory = switchService.getSwitch(sw).getOFFactory();
        OFOxms oxms = factory.oxms();
        List<OFAction> actions = new ArrayList<>();
        OFFlowAdd.Builder flowAdd = factory.buildFlowAdd();

        flowAdd.setXid(pi.getXid())
                .setBufferId(OFBufferId.NO_BUFFER)
                .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
                .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(cookie)
                .setOutPort(outPort)
                .setPriority(FLOWMOD_DEFAULT_PRIORITY)
                .setMatch(match);

        if (FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG || requestFlowRemovedNotification) {
            Set<OFFlowModFlags> flags = new HashSet<>();
            flags.add(OFFlowModFlags.SEND_FLOW_REM);
            flowAdd.setFlags(flags);
        }

        OFVersion switchVersion = switchService.getSwitch(sw).getOFFactory().getVersion();
        switch (switchVersion) {
            case OF_10:
            case OF_11:
                actions.add(factory.actions().setDlSrc(gatewayMac));
                actions.add(factory.actions().setDlDst(hostMac));
                break;

            case OF_12:
            case OF_13:
            case OF_14:
            case OF_15:
                actions.add(factory.actions().setField(oxms.ethSrc(gatewayMac)));
                actions.add(factory.actions().setField(oxms.ethDst(hostMac)));
                break;

            default:
                break;

        }

        actions.add(factory.actions().output(outPort, Integer.MAX_VALUE));
        flowAdd.setActions(actions);

        if (log.isTraceEnabled()) {
            log.trace("Pushing flowmod with srcMac={} dstMac={} " +
                            "sw={} inPort={} outPort={}",
                    new Object[] { gatewayMac, hostMac,
                            sw,
                            flowAdd.getMatch().get(MatchField.IN_PORT),
                            outPort });
        }

        messageDamper.write(switchService.getSwitch(sw), flowAdd.build());
        return;
    }

    private Path getNewPath(Path oldPath) {
        oldPath.getPath().remove(oldPath.getPath().get(oldPath.getPath().size()-1));
        oldPath.getPath().remove(oldPath.getPath().get(oldPath.getPath().size()-1));
        return oldPath;
    }


    /**
     * L3 forwarding block -- it supports L2 forwarding from a source to a destination. No L3 functionality involved.
     *
     * @param eth Ethernet frame associated with this OFPacketIn
     * @param sw The switch that receives the OFPacketIn
     * @param pi The OFPacketIn that came to the switch
     * @param decision The decision that caused floodlight, or null
     * @param cntx The FloodlightContext associated with this OFPacketIn
     */
    protected void doL2Forwarding(Ethernet eth, IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        if (isBroadcastOrMulticast(eth)) {
            doFlood(sw, pi, decision, cntx);
        } else {
            doL2ForwardFlow(sw, pi, decision, cntx, false);
        }
    }

    /**
     * This function retrieves a patch from source device to destination device and then install L2 flows over the path.
     *
     * @param sw The switch on which the packet was received
     * @param pi The OFPacketIn that came to the switch
     * @param decision The decision that caused flooding, or null
     * @param cntx The FloodlightContext associated with this OFPacketIn
     * @param requestFlowRemovedNotifn The OpenFlow "flow remove flag"
     */
    protected void doL2ForwardFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx, boolean requestFlowRemovedNotifn) {
        OFPort srcPort = OFMessageUtils.getInPort(pi);
        DatapathId srcSw = sw.getId();
        IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        IDevice srcDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);

        if (dstDevice == null) {
            log.debug("Destination device unknown. Flooding packet");
            doFlood(sw, pi, decision, cntx);
            return;
        }

        if (srcDevice == null) {
            log.error("No device entry found for source device. Is the device manager running? If so, report bug.");
            return;
        }

        /* Some physical switches partially support or do not support ARP flows */
        if (FLOOD_ALL_ARP_PACKETS &&
                IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD).getEtherType()
                        == EthType.ARP) {
            log.debug("ARP flows disabled in Forwarding. Flooding ARP packet");
            doFlood(sw, pi, decision, cntx);
            return;
        }

        /* This packet-in is from a switch in the path before its flow was installed along the path */
        if (!topologyService.isEdge(srcSw, srcPort)) {
            log.debug("Packet destination is known, but packet was not received on an edge port (rx on {}/{}). Flooding packet", srcSw, srcPort);
            doFlood(sw, pi, decision, cntx);
            return;
        }

        /*
         * Search for the true attachment point. The true AP is
         * not an endpoint of a link. It is a switch port w/o an
         * associated link. Note this does not necessarily hold
         * true for devices that 'live' between OpenFlow islands.
         *
         * TODO Account for the case where a device is actually
         * attached between islands (possibly on a non-OF switch
         * in between two OpenFlow switches).
         */
        SwitchPort dstAp = null;
        for (SwitchPort ap : dstDevice.getAttachmentPoints()) {
            if (topologyService.isEdge(ap.getNodeId(), ap.getPortId())) {
                dstAp = ap;
                break;
            }
        }

        /*
         * This should only happen (perhaps) when the controller is
         * actively learning a new topology and hasn't discovered
         * all links yet, or a switch was in standalone mode and the
         * packet in question was captured in flight on the dst point
         * of a link.
         */
        if (dstAp == null) {
            log.debug("Could not locate edge attachment point for destination device {}. Flooding packet");
            doFlood(sw, pi, decision, cntx);
            return;
        }

        /* Validate that the source and destination are not on the same switch port */
        if (sw.getId().equals(dstAp.getNodeId()) && srcPort.equals(dstAp.getPortId())) {
            log.debug("Both source and destination are on the same switch/port {}/{}. Dropping packet", sw.toString(), srcPort);
            return;
        }

        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
        U64 cookie = makeForwardingCookie(decision, flowSetId);
        Path path = routingEngineService.getPath(srcSw,
                srcPort,
                dstAp.getNodeId(),
                dstAp.getPortId());

        Match m = createMatchFromPacket(sw, srcPort, pi, cntx);

        if (! path.getPath().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("pushRoute inPort={} route={} " +
                                "destination={}:{}",
                        new Object[] { srcPort, path,
                                dstAp.getNodeId(),
                                dstAp.getPortId()});
                log.debug("Creating flow rules on the route, match rule: {}", m);
            }

            pushRoute(path, m, pi, sw.getId(), cookie,
                    cntx, requestFlowRemovedNotifn,
                    OFFlowModCommand.ADD, false);

            /*
             * Register this flowset with ingress and egress ports for link down
             * flow removal. This is done after we push the path as it is blocking.
             */
            for (NodePortTuple npt : path.getPath()) {
                flowSetIdRegistry.registerFlowSetId(npt, flowSetId);
            }
        } /* else no path was found */
    }


    /**
     * Generate arp reply packet so virtual gateway can use it to response the cross-subnet ARP request sent from host
     *
     * @param cntx The FloodlightContext associated with this OFPacketIn
     * @param gatewayMac virtual gatwway MAC address
     * @return
     */
    public IPacket gatewayArpReply(FloodlightContext cntx, MacAddress gatewayMac) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        ARP arpRequest = (ARP) eth.getPayload();

        // generate ARP reply to host
        return new Ethernet()
                .setSourceMACAddress(gatewayMac)
                .setDestinationMACAddress(eth.getSourceMACAddress())
                .setEtherType(EthType.ARP)
                .setVlanID(eth.getVlanID())
                .setPriorityCode(eth.getPriorityCode())
                .setPayload(
                        new ARP()
                        .setHardwareType(ARP.HW_TYPE_ETHERNET)
                        .setProtocolType(ARP.PROTO_TYPE_IP)
                        .setOpCode(ARP.OP_REPLY)
                        .setHardwareAddressLength((byte) 6)
                        .setProtocolAddressLength((byte) 4)
                        .setSenderHardwareAddress(gatewayMac)
                        .setSenderProtocolAddress(arpRequest.getTargetProtocolAddress())
                        .setTargetHardwareAddress(eth.getSourceMACAddress())
                        .setTargetProtocolAddress(arpRequest.getSenderProtocolAddress()));

    }

    /**
     * Push arp packet to the network when virtual gateway needs to response arp request from host
     *
     * @param packet Packet will be pushed to the network as OFPacketOut
     * @param sw The switch on which the packet was received
     * @param bufferId OpenFlow BufferID
     * @param inPort incoming port number
     * @param outPort outgoing port number
     */
    public void pushArpReply(IPacket packet, IOFSwitch sw, OFBufferId bufferId, OFPort inPort, OFPort outPort) {
        if (log.isTraceEnabled()) {
            log.trace("Push ar PacketOut srcSwitch={} inPort={} outPort={}",
                    new Object[] {sw, inPort, outPort});
        }

        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();

        // Set Actions
        List<OFAction> actions = new ArrayList<>();
        actions.add(sw.getOFFactory().actions().buildOutput().setPort(outPort).setMaxLen(Integer.MAX_VALUE).build());

        pob.setActions(actions);

        // Set buffer_id, in_port
        pob.setBufferId(bufferId);
        OFMessageUtils.setInPort(pob, inPort);

        // Set Data if buffer_id set to "no buffer"
        if (pob.getBufferId() == OFBufferId.NO_BUFFER) {
            if (packet != null) {
                byte[] pktData = packet.serialize();
                pob.setData(pktData);
            }
            else {
                log.error("BufferId is not set and packet data is null. " +
                                "Cannot send packetOut. " +
                                "srcSwitch={} inPort={} outPort={}",
                        new Object[] {sw, inPort, outPort});
                return;
            }
        }

        sw.write(pob.build());
    }


    /**
     * Find corresponding virtual gateway interface based on the destination IP address
     *
     * @param gateway virtual gateway
     * @param dstIP packet destination IP
     * @return
     */
    public IPv4Address findInterfaceIP(VirtualGatewayInstance gateway, IPv4Address dstIP) {
        Optional<VirtualGatewayInterface> intf = gateway.findGatewayInft(dstIP);
        if (intf.isPresent()) {
            return intf.get().getIp();
        }
        else {
            return null;
        }
    }


    /**
     * Query device based on packet destination IP address, rather than based on MAC address
     * This is because in L3 case, the MAC address is Virtual Router MAC, which is not exist in device map
     *
     * @param dstIP packet destination IP
     * @return
     */
    private IDevice findDstDeviceForL3Routing(IPv4Address dstIP) {
        // Fetch all known devices
        Collection<? extends IDevice> allDevices = deviceManagerService.getAllDevices();

        IDevice dstDevice = null;
        for (IDevice d : allDevices) {
            for (int i = 0; i < d.getIPv4Addresses().length; ++i) {
                if (d.getIPv4Addresses()[i].equals(dstIP)) {
                    dstDevice = d;
                    break;
                }
            }
        }

        return dstDevice;
    }


    /**
     * Builds a cookie that includes routing decision information.
     *
     * @param decision The routing decision providing a descriptor, or null
     * @return A cookie with our app id and the required fields masked-in
     */
    protected U64 makeForwardingCookie(IRoutingDecision decision, U64 flowSetId) {
        long user_fields = 0;

        U64 decision_cookie = (decision == null) ? null : decision.getDescriptor();
        if (decision_cookie != null) {
            user_fields |= AppCookie.extractUser(decision_cookie) & DECISION_MASK;
        }

        if (flowSetId != null) {
            user_fields |= AppCookie.extractUser(flowSetId) & FLOWSET_MASK;
        }

        // TODO: Mask in any other required fields here

        if (user_fields == 0) {
            return DEFAULT_FORWARDING_COOKIE;
        }
        return AppCookie.makeCookie(FORWARDING_APP_ID, user_fields);
    }

    /** Called when the handleDecisionChange is triggered by an event (routing decision was changed in firewall).
     *  
     *  @param changedDecisions Masked routing descriptors for flows that should be deleted from the switch.
     */
    @Override
    public void routingDecisionChanged(Iterable<Masked<U64>> changedDecisions) {
        deleteFlowsByDescriptor(changedDecisions);
    }

    /**
     * Converts a sequence of masked IRoutingDecision descriptors into masked Forwarding cookies.
     *
     * This generates a list of masked cookies that can then be matched in flow-mod messages.
     *
     * @param maskedDescriptors A sequence of masked cookies describing IRoutingDecision descriptors
     * @return A collection of masked cookies suitable for flow-mod operations
     */
    protected Collection<Masked<U64>> convertRoutingDecisionDescriptors(Iterable<Masked<U64>> maskedDescriptors) {
        if (maskedDescriptors == null) {
            return null;
        }

        ImmutableList.Builder<Masked<U64>> resultBuilder = ImmutableList.builder();
        for (Masked<U64> maskedDescriptor : maskedDescriptors) {
            long user_mask = AppCookie.extractUser(maskedDescriptor.getMask()) & DECISION_MASK;
            long user_value = AppCookie.extractUser(maskedDescriptor.getValue()) & user_mask;

            // TODO combine in any other cookie fields you need here.

            resultBuilder.add(
                    Masked.of(
                            AppCookie.makeCookie(FORWARDING_APP_ID, user_value),
                            AppCookie.getAppFieldMask().or(U64.of(user_mask))
                            )
                    );
        }

        return resultBuilder.build();
    }

    /**
     * On all active switches, deletes all flows matching the IRoutingDecision descriptors provided
     * as arguments.
     *
     * @param descriptors The descriptors and masks describing which flows to delete.
     */
    protected void deleteFlowsByDescriptor(Iterable<Masked<U64>> descriptors) {
        Collection<Masked<U64>> masked_cookies = convertRoutingDecisionDescriptors(descriptors);

        if (masked_cookies != null && !masked_cookies.isEmpty()) {
            Map<OFVersion, List<OFMessage>> cache = Maps.newHashMap();

            for (DatapathId dpid : switchService.getAllSwitchDpids()) {
                IOFSwitch sw = switchService.getActiveSwitch(dpid);
                if (sw == null) {
                    continue;
                }

                OFVersion ver = sw.getOFFactory().getVersion();
                if (cache.containsKey(ver)) {
                    sw.write(cache.get(ver));
                } else {
                    ImmutableList.Builder<OFMessage> msgsBuilder = ImmutableList.builder();
                    for (Masked<U64> masked_cookie : masked_cookies) {
                        // Consider OpenFlow version when using cookieMask property
                        if (ver.compareTo(OFVersion.OF_10) == 0) {
                            msgsBuilder.add(
                                    sw.getOFFactory().buildFlowDelete()
                                            .setCookie(masked_cookie.getValue())
                                            // maskCookie not support in OpenFlow 1.0
                                            .build()
                            );
                        }
                        else {
                            msgsBuilder.add(
                                    sw.getOFFactory().buildFlowDelete()
                                            .setCookie(masked_cookie.getValue())
                                            .setCookieMask(masked_cookie.getMask())
                                            .build()
                            );
                        }

                    }

                    List<OFMessage> msgs = msgsBuilder.build();
                    sw.write(msgs);
                    cache.put(ver, msgs);
                }
            }
        }
    }

    /**
     * Write FlowMod to switch to drop the flow
     *
     * @param sw The switch on which the packet was received
     * @param pi The packet-in came from that switch
     * @param decision The decision that cause drop flow, or null
     * @param cntx The FloodlightContext associated with this OFPacketIn
     */
    protected void doDropFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        OFPort inPort = OFMessageUtils.getInPort(pi);
        Match m = createMatchFromPacket(sw, inPort, pi, cntx);
        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
        List<OFAction> actions = new ArrayList<>(); // set no action to drop
        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
        U64 cookie = makeForwardingCookie(decision, flowSetId); 

        /* If link goes down, we'll remember to remove this flow */
        if (! m.isFullyWildcarded(MatchField.IN_PORT)) {
            flowSetIdRegistry.registerFlowSetId(new NodePortTuple(sw.getId(), m.get(MatchField.IN_PORT)), flowSetId);
        }

        fmb.setCookie(cookie)
        .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
        .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setBufferId(OFBufferId.NO_BUFFER) 
        .setMatch(m)
        .setPriority(FLOWMOD_DEFAULT_PRIORITY);

        FlowModUtils.setActions(fmb, actions, sw);

        /* Configure for particular switch pipeline */
        if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) != 0) {
            fmb.setTableId(FLOWMOD_DEFAULT_TABLE_ID);
        }

        if (log.isDebugEnabled()) {
            log.debug("write drop flow-mod sw={} match={} flow-mod={}",
                    new Object[] { sw, m, fmb.build() });
        }
        boolean dampened = messageDamper.write(sw, fmb.build());
        log.debug("OFMessage dampened: {}", dampened);
    }


    /**
     * Instead of using the Firewall's routing decision Match, which might be as general
     * as "in_port" and inadvertently Match packets erroneously, construct a more
     * specific Match based on the deserialized OFPacketIn's payload, which has been 
     * placed in the FloodlightContext already by the Controller.
     * 
     * @param sw, the switch on which the packet was received
     * @param inPort, the ingress switch port on which the packet was received
     * @param cntx, the current context which contains the deserialized packet
     * @return a composed Match object based on the provided information
     */
    protected Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, OFPacketIn pi, FloodlightContext cntx) {
        // The packet in match will only contain the port number.
        // We need to add in specifics for the hosts we're routing between.
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        VlanVid vlan = null;      
        if (pi.getVersion().compareTo(OFVersion.OF_11) > 0 && /* 1.0 and 1.1 do not have a match */
                pi.getMatch().get(MatchField.VLAN_VID) != null) { 
            vlan = pi.getMatch().get(MatchField.VLAN_VID).getVlanVid(); /* VLAN may have been popped by switch */
        }
        if (vlan == null) {
            vlan = VlanVid.ofVlan(eth.getVlanID()); /* VLAN might still be in packet */
        }
        
        MacAddress srcMac = eth.getSourceMACAddress();
        MacAddress dstMac = eth.getDestinationMACAddress();

        Match.Builder mb = sw.getOFFactory().buildMatch();
        if (FLOWMOD_DEFAULT_MATCH_IN_PORT) {
            mb.setExact(MatchField.IN_PORT, inPort);
        }

        if (FLOWMOD_DEFAULT_MATCH_MAC) {
            if (FLOWMOD_DEFAULT_MATCH_MAC_SRC) {
                mb.setExact(MatchField.ETH_SRC, srcMac);
            }
            if (FLOWMOD_DEFAULT_MATCH_MAC_DST) {
                mb.setExact(MatchField.ETH_DST, dstMac);
            }
        }

        if (FLOWMOD_DEFAULT_MATCH_VLAN) {
            if (!vlan.equals(VlanVid.ZERO)) {
                mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
            }
        }

        // TODO Detect switch type and match to create hardware-implemented flow
        if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
            IPv4 ip = (IPv4) eth.getPayload();
            IPv4Address srcIp = ip.getSourceAddress();
            IPv4Address dstIp = ip.getDestinationAddress();

            if (FLOWMOD_DEFAULT_MATCH_IP) {
                mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
                if (FLOWMOD_DEFAULT_MATCH_IP_SRC) {
                    mb.setExact(MatchField.IPV4_SRC, srcIp);
                }
                if (FLOWMOD_DEFAULT_MATCH_IP_DST) {
                    mb.setExact(MatchField.IPV4_DST, dstIp);
                }
            }

            if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
                /*
                 * Take care of the ethertype if not included earlier,
                 * since it's a prerequisite for transport ports.
                 */
                if (!FLOWMOD_DEFAULT_MATCH_IP) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
                }

                if (ip.getProtocol().equals(IpProtocol.TCP)) {
                    TCP tcp = (TCP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
                    }
		    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0){
			 if(FLOWMOD_DEFAULT_MATCH_TCP_FLAG){
	                        mb.setExact(MatchField.TCP_FLAGS, U16.of(tcp.getFlags()));
	                 }
		    }
                    else if(sw.getSwitchDescription().getHardwareDescription().toLowerCase().contains("open vswitch") && (
                       Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) > 2  || (
                       Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) == 2 &&
                       Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[1]) >= 1 ))
		      ){
	                    if(FLOWMOD_DEFAULT_MATCH_TCP_FLAG){
	                        mb.setExact(MatchField.OVS_TCP_FLAGS, U16.of(tcp.getFlags()));
	                    }
                    }
                } else if (ip.getProtocol().equals(IpProtocol.UDP)) {
                    UDP udp = (UDP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.UDP_SRC, udp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
                    }
                }
            }
        } else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
            mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
        } else if (eth.getEtherType() == EthType.IPv6) {
            IPv6 ip = (IPv6) eth.getPayload();
            IPv6Address srcIp = ip.getSourceAddress();
            IPv6Address dstIp = ip.getDestinationAddress();

            if (FLOWMOD_DEFAULT_MATCH_IP) {
                mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);
                if (FLOWMOD_DEFAULT_MATCH_IP_SRC) {
                    mb.setExact(MatchField.IPV6_SRC, srcIp);
                }
                if (FLOWMOD_DEFAULT_MATCH_IP_DST) {
                    mb.setExact(MatchField.IPV6_DST, dstIp);
                }
            }

            if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
                /*
                 * Take care of the ethertype if not included earlier,
                 * since it's a prerequisite for transport ports.
                 */
                if (!FLOWMOD_DEFAULT_MATCH_IP) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);
                }

                if (ip.getNextHeader().equals(IpProtocol.TCP)) {
                    TCP tcp = (TCP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
                    }
		    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0){
		        if(FLOWMOD_DEFAULT_MATCH_TCP_FLAG){
			   mb.setExact(MatchField.TCP_FLAGS, U16.of(tcp.getFlags()));
		        }
		    }
                    else if(
                    sw.getSwitchDescription().getHardwareDescription().toLowerCase().contains("open vswitch") && (
                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) > 2  || (
                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) == 2 &&
                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[1]) >= 1 ))
                    ){
	                    if(FLOWMOD_DEFAULT_MATCH_TCP_FLAG){
	                        mb.setExact(MatchField.OVS_TCP_FLAGS, U16.of(tcp.getFlags()));
	                    }
                    }
                } else if (ip.getNextHeader().equals(IpProtocol.UDP)) {
                    UDP udp = (UDP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.UDP_SRC, udp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
                    }
                }
            }
        }
        return mb.build();
    }

    /**
     * Creates a OFPacketOut with the OFPacketIn data that is flooded on all ports unless
     * the port is blocked, in which case the packet will be dropped.
     * @param sw The switch that receives the OFPacketIn
     * @param pi The OFPacketIn that came to the switch
     * @param decision The decision that caused flooding, or null
     * @param cntx The FloodlightContext associated with this OFPacketIn
     */
    protected void doFlood(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        OFPort inPort = OFMessageUtils.getInPort(pi);
        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        List<OFAction> actions = new ArrayList<>();
        Set<OFPort> broadcastPorts = this.topologyService.getSwitchBroadcastPorts(sw.getId());

        if (broadcastPorts.isEmpty()) {
            log.debug("No broadcast ports found. Using FLOOD output action");
            broadcastPorts = Collections.singleton(OFPort.FLOOD);
        }

        for (OFPort p : broadcastPorts) {
            if (p.equals(inPort)) continue;
            actions.add(sw.getOFFactory().actions().output(p, Integer.MAX_VALUE));
        }
        pob.setActions(actions);

        // set buffer-id, in-port and packet-data based on packet-in
        pob.setBufferId(OFBufferId.NO_BUFFER);
        OFMessageUtils.setInPort(pob, inPort);
        pob.setData(pi.getData());

        if (log.isTraceEnabled()) {
            log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
                    new Object[] {sw, pi, pob.build()});
        }
        messageDamper.write(sw, pob.build());

        return;
    }

    /**
     * Virtual gateway creates OFPacketOut with ARP request message and flood it to network,
     * for learning destination host purpose
     *
     * @param gateway The virtual gateway
     * @param sw The switch that receives OFPacketIn
     * @param pi The OFPacketIn that came to the switch
     * @param cntx The FloodlightContext associated with this OFPacketIn
     */
    protected void doL3Flood(VirtualGatewayInstance gateway, IOFSwitch sw, OFPacketIn pi,
                             FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        MacAddress gatewayMac = gateway.getGatewayMac();

        // generate ARP request to destination host
        IPv4Address dstIP = ((IPv4) eth.getPayload()).getDestinationAddress();
        IPv4Address intfIpAddress = findInterfaceIP(gateway, dstIP);

        // Set src MAC to virtual gateway MAC, set dst MAC to broadcast
        IPacket arpPacket = new Ethernet()
                .setSourceMACAddress(gatewayMac)
                .setDestinationMACAddress(MacAddress.BROADCAST)
                .setEtherType(EthType.ARP)
                .setVlanID(eth.getVlanID())
                .setPriorityCode(eth.getPriorityCode())
                .setPayload(
                        new ARP()
                                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                                .setProtocolType(ARP.PROTO_TYPE_IP)
                                .setOpCode(ARP.OP_REQUEST)
                                .setHardwareAddressLength((byte) 6)
                                .setProtocolAddressLength((byte) 4)
                                .setSenderHardwareAddress(gatewayMac)
                                .setSenderProtocolAddress(intfIpAddress)
                                .setTargetHardwareAddress(MacAddress.BROADCAST)
                                .setTargetProtocolAddress(((IPv4) eth.getPayload()).getDestinationAddress()));

        byte[] data = arpPacket.serialize();

        OFPort inPort = OFMessageUtils.getInPort(pi);

        OFFactory factory = sw.getOFFactory();
        OFPacketOut.Builder packetOut = factory.buildPacketOut();

        // Set Actions
        List<OFAction> actions = new ArrayList<>();

        Set<OFPort> broadcastPorts = this.topologyService.getSwitchBroadcastPorts(sw.getId());
        if (broadcastPorts.isEmpty()) {
            log.debug("No broadcast ports found. Using FLOOD output action");
            broadcastPorts = Collections.singleton(OFPort.FLOOD);
        }

        for (OFPort p : broadcastPorts) {
            if (p.equals(inPort)) continue;
            actions.add(factory.actions().output(p, Integer.MAX_VALUE));
        }
        packetOut.setActions(actions);

        // set buffer-id, in-port and packet-data based on packet-in
        packetOut.setBufferId(OFBufferId.NO_BUFFER);
        OFMessageUtils.setInPort(packetOut, inPort);
        packetOut.setData(data);

        if (log.isTraceEnabled()) {
            log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
                    new Object[] {sw, pi, packetOut.build()});
        }
        messageDamper.write(sw, packetOut.build());

        return;
    }


    // IFloodlightModule methods
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> s =
                new HashSet<Class<? extends IFloodlightService>>();
        s.add(IGatewayService.class);
        return s;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IGatewayService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IRoutingService.class);
        l.add(IRestApiService.class);
        l.add(IDeviceService.class);
        l.add(IFloodlightProviderService.class);
        l.add(ITopologyService.class);
        l.add(IDebugCounterService.class);
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        super.init();
        this.floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        this.routingEngineService = context.getServiceImpl(IRoutingService.class);
        this.deviceManagerService = context.getServiceImpl(IDeviceService.class);
        this.restApiService = context.getServiceImpl(IRestApiService.class);
        this.topologyService = context.getServiceImpl(ITopologyService.class);
        this.debugCounterService = context.getServiceImpl(IDebugCounterService.class);
        this.switchService = context.getServiceImpl(IOFSwitchService.class);
        this.linkService = context.getServiceImpl(ILinkDiscoveryService.class);

        l3manager = new L3RoutingManager();
        l3cache = new ConcurrentHashMap<>();
        deviceListener = new DeviceListenerImpl();

        flowSetIdRegistry = FlowSetIdRegistry.getInstance();

        Map<String, String> configParameters = context.getConfigParams(this);
        String tmp = configParameters.get("hard-timeout");
        if (tmp != null) {
            FLOWMOD_DEFAULT_HARD_TIMEOUT = ParseUtils.parseHexOrDecInt(tmp);
            log.info("Default hard timeout set to {}.", FLOWMOD_DEFAULT_HARD_TIMEOUT);
        } else {
            log.info("Default hard timeout not configured. Using {}.", FLOWMOD_DEFAULT_HARD_TIMEOUT);
        }
        tmp = configParameters.get("idle-timeout");
        if (tmp != null) {
            FLOWMOD_DEFAULT_IDLE_TIMEOUT = ParseUtils.parseHexOrDecInt(tmp);
            log.info("Default idle timeout set to {}.", FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        } else {
            log.info("Default idle timeout not configured. Using {}.", FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        }
        tmp = configParameters.get("table-id");
        if (tmp != null) {
            FLOWMOD_DEFAULT_TABLE_ID = TableId.of(ParseUtils.parseHexOrDecInt(tmp));
            log.info("Default table ID set to {}.", FLOWMOD_DEFAULT_TABLE_ID);
        } else {
            log.info("Default table ID not configured. Using {}.", FLOWMOD_DEFAULT_TABLE_ID);
        }
        tmp = configParameters.get("priority");
        if (tmp != null) {
            FLOWMOD_DEFAULT_PRIORITY = ParseUtils.parseHexOrDecInt(tmp);
            log.info("Default priority set to {}.", FLOWMOD_DEFAULT_PRIORITY);
        } else {
            log.info("Default priority not configured. Using {}.", FLOWMOD_DEFAULT_PRIORITY);
        }
        tmp = configParameters.get("set-send-flow-rem-flag");
        if (tmp != null) {
            FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = Boolean.parseBoolean(tmp);
            log.info("Default flags will be set to SEND_FLOW_REM {}.", FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG);
        } else {
            log.info("Default flags will be empty.");
        }
        tmp = configParameters.get("match");
        if (tmp != null) {
            tmp = tmp.toLowerCase();
            if (!tmp.contains("in-port") && !tmp.contains("vlan") 
                    && !tmp.contains("mac") && !tmp.contains("ip") 
                    && !tmp.contains("transport") && !tmp.contains("flag")) {
                /* leave the default configuration -- blank or invalid 'match' value */
            } else {
                FLOWMOD_DEFAULT_MATCH_IN_PORT = tmp.contains("in-port") ? true : false;
                FLOWMOD_DEFAULT_MATCH_VLAN = tmp.contains("vlan") ? true : false;
                FLOWMOD_DEFAULT_MATCH_MAC = tmp.contains("mac") ? true : false;
                FLOWMOD_DEFAULT_MATCH_IP = tmp.contains("ip") ? true : false;
                FLOWMOD_DEFAULT_MATCH_TRANSPORT = tmp.contains("transport") ? true : false;
				FLOWMOD_DEFAULT_MATCH_TCP_FLAG = tmp.contains("flag") ? true : false;
			}
		}
		log.info("Default flow matches set to: IN_PORT=" + FLOWMOD_DEFAULT_MATCH_IN_PORT
				+ ", VLAN=" + FLOWMOD_DEFAULT_MATCH_VLAN
				+ ", MAC=" + FLOWMOD_DEFAULT_MATCH_MAC
				+ ", IP=" + FLOWMOD_DEFAULT_MATCH_IP
				+ ", FLAG=" + FLOWMOD_DEFAULT_MATCH_TCP_FLAG
                + ", TPPT=" + FLOWMOD_DEFAULT_MATCH_TRANSPORT);

        tmp = configParameters.get("detailed-match");
        if (tmp != null) {
            tmp = tmp.toLowerCase();
            if (!tmp.contains("src-mac") && !tmp.contains("dst-mac") 
                    && !tmp.contains("src-ip") && !tmp.contains("dst-ip")
                    && !tmp.contains("src-transport") && !tmp.contains("dst-transport")) {
                /* leave the default configuration -- both src and dst for layers defined above */
            } else {
                FLOWMOD_DEFAULT_MATCH_MAC_SRC = tmp.contains("src-mac") ? true : false;
                FLOWMOD_DEFAULT_MATCH_MAC_DST = tmp.contains("dst-mac") ? true : false;
                FLOWMOD_DEFAULT_MATCH_IP_SRC = tmp.contains("src-ip") ? true : false;
                FLOWMOD_DEFAULT_MATCH_IP_DST = tmp.contains("dst-ip") ? true : false;
                FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC = tmp.contains("src-transport") ? true : false;
                FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST = tmp.contains("dst-transport") ? true : false;
            }
        }
        log.info("Default detailed flow matches set to: SRC_MAC=" + FLOWMOD_DEFAULT_MATCH_MAC_SRC
                + ", DST_MAC=" + FLOWMOD_DEFAULT_MATCH_MAC_DST
                + ", SRC_IP=" + FLOWMOD_DEFAULT_MATCH_IP_SRC
                + ", DST_IP=" + FLOWMOD_DEFAULT_MATCH_IP_DST
                + ", SRC_TPPT=" + FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC
                + ", DST_TPPT=" + FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST);

        tmp = configParameters.get("flood-arp");
        if (tmp != null) {
            tmp = tmp.toLowerCase();
            if (!tmp.contains("yes") && !tmp.contains("yep") && !tmp.contains("true") && !tmp.contains("ja") && !tmp.contains("stimmt")) {
                FLOOD_ALL_ARP_PACKETS = false;
                log.info("Not flooding ARP packets. ARP flows will be inserted for known destinations");
            } else {
                FLOOD_ALL_ARP_PACKETS = true;
                log.info("Flooding all ARP packets. No ARP flows will be inserted");
            }
        }

        tmp = configParameters.get("remove-flows-on-link-or-port-down");
        if (tmp != null) {
            REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN = Boolean.parseBoolean(tmp);
        }
        if (REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN) {
            log.info("Flows will be removed on link/port down events");
        } else {
            log.info("Flows will not be removed on link/port down events");
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        super.startUp();
        switchService.addOFSwitchListener(this);
        routingEngineService.addRoutingDecisionChangedListener(this);
        restApiService.addRestletRoutable(new RoutingWebRoutable());

        deviceManagerService.addListener(this.deviceListener);

        /* Register only if we want to remove stale flows */
        if (REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN) {
            linkService.addListener(this);
        }
    }

    @Override
    public void switchAdded(DatapathId switchId) {
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        l3manager.getAllVirtualGateways().stream()
                .forEach(instance -> instance.removeSwitchFromInstance(switchId));
        log.info("Handle switchRemoved. Switch {} removed from virtual gateway instance", switchId.toString());
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw == null) {
            log.warn("Switch {} was activated but had no switch object in the switch service. Perhaps it quickly disconnected", switchId);
            return;
        }
        if (OFDPAUtils.isOFDPASwitch(sw)) {
            messageDamper.write(sw, sw.getOFFactory().buildFlowDelete()
                    .setTableId(TableId.ALL)
                    .build()
                    );
            messageDamper.write(sw, sw.getOFFactory().buildGroupDelete()
                    .setGroup(OFGroup.ANY)
                    .setGroupType(OFGroupType.ALL)
                    .build()
                    );
            messageDamper.write(sw, sw.getOFFactory().buildGroupDelete()
                    .setGroup(OFGroup.ANY)
                    .setGroupType(OFGroupType.INDIRECT)
                    .build()
                    );
            messageDamper.write(sw, sw.getOFFactory().buildBarrierRequest().build());

            List<OFPortModeTuple> portModes = new ArrayList<>();
            for (OFPortDesc p : sw.getPorts()) {
                portModes.add(OFPortModeTuple.of(p.getPortNo(), OFPortMode.ACCESS));
            }
            if (log.isWarnEnabled()) {
                log.warn("For OF-DPA switch {}, initializing VLAN {} on ports {}", new Object[] { switchId, VlanVid.ZERO, portModes});
            }
            OFDPAUtils.addLearningSwitchPrereqs(sw, VlanVid.ZERO, portModes);
        }
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {	
        /* Port down events handled via linkDiscoveryUpdate(), which passes thru all events */
    }

    @Override
    public void switchChanged(DatapathId switchId) {
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
    }

    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
        for (LDUpdate u : updateList) {
            /* Remove flows on either side if link/port went down */
            if (u.getOperation() == UpdateOperation.LINK_REMOVED ||
                    u.getOperation() == UpdateOperation.PORT_DOWN ||
                    u.getOperation() == UpdateOperation.TUNNEL_PORT_REMOVED) {
                Set<OFMessage> msgs = new HashSet<OFMessage>();

                if (u.getSrc() != null && !u.getSrc().equals(DatapathId.NONE)) {
                    IOFSwitch srcSw = switchService.getSwitch(u.getSrc());
                    /* src side of link */
                    if (srcSw != null) {
                        Set<U64> ids = flowSetIdRegistry.getFlowSetIds(
                                new NodePortTuple(u.getSrc(), u.getSrcPort()));
                        if (ids != null) {
                            Iterator<U64> i = ids.iterator();
                            while (i.hasNext()) {
                                U64 id = i.next();
                                U64 cookie = id.or(DEFAULT_FORWARDING_COOKIE);
                                U64 cookieMask = U64.of(FLOWSET_MASK).or(AppCookie.getAppFieldMask());

                                /* Delete flows matching on src port and outputting to src port */
                                msgs = buildDeleteFlows(u.getSrcPort(), msgs, srcSw, cookie, cookieMask);
                                messageDamper.write(srcSw, msgs);
                                log.debug("src: Removing flows to/from DPID={}, port={}", u.getSrc(), u.getSrcPort());
                                log.debug("src: Cookie/mask {}/{}", cookie, cookieMask);

                                /* 
                                 * Now, for each ID on this particular failed link, remove
                                 * all other flows in the network using this ID.
                                 */
                                Set<NodePortTuple> npts = flowSetIdRegistry.getNodePortTuples(id);
                                if (npts != null) {
                                    for (NodePortTuple npt : npts) {
                                        msgs.clear();
                                        IOFSwitch sw = switchService.getSwitch(npt.getNodeId());
                                        if (sw != null) {

                                            /* Delete flows matching on npt port and outputting to npt port*/
                                            msgs = buildDeleteFlows(npt.getPortId(), msgs, sw, cookie, cookieMask);
                                            messageDamper.write(sw, msgs);
                                            log.debug("src: Removing same-cookie flows to/from DPID={}, port={}", npt.getNodeId(), npt.getPortId());
                                            log.debug("src: Cookie/mask {}/{}", cookie, cookieMask);
                                        }
                                    }
                                }
                                flowSetIdRegistry.removeExpiredFlowSetId(id, new NodePortTuple(u.getSrc(), u.getSrcPort()), i);
                            }
                        }
                    }
                    flowSetIdRegistry.removeNodePortTuple(new NodePortTuple(u.getSrc(), u.getSrcPort()));
                }

                /* must be a link, not just a port down, if we have a dst switch */
                if (u.getDst() != null && !u.getDst().equals(DatapathId.NONE)) {
                    /* dst side of link */
                    IOFSwitch dstSw = switchService.getSwitch(u.getDst());
                    if (dstSw != null) {
                        Set<U64> ids = flowSetIdRegistry.getFlowSetIds(
                                new NodePortTuple(u.getDst(), u.getDstPort()));
                        if (ids != null) {
                            Iterator<U64> i = ids.iterator();
                            while (i.hasNext()) {
                                U64 id = i.next();
                                U64 cookie = id.or(DEFAULT_FORWARDING_COOKIE);
                                U64 cookieMask = U64.of(FLOWSET_MASK).or(AppCookie.getAppFieldMask());
                                /* Delete flows matching on dst port and outputting to dst port */
                                msgs = buildDeleteFlows(u.getDstPort(), msgs, dstSw, cookie, cookieMask);
                                messageDamper.write(dstSw, msgs);
                                log.debug("dst: Removing flows to/from DPID={}, port={}", u.getDst(), u.getDstPort());
                                log.debug("dst: Cookie/mask {}/{}", cookie, cookieMask);

                                /* 
                                 * Now, for each ID on this particular failed link, remove
                                 * all other flows in the network using this ID.
                                 */
                                Set<NodePortTuple> npts = flowSetIdRegistry.getNodePortTuples(id);
                                if (npts != null) {
                                    for (NodePortTuple npt : npts) {
                                        msgs.clear();
                                        IOFSwitch sw = switchService.getSwitch(npt.getNodeId());
                                        if (sw != null) {
                                            /* Delete flows matching on npt port and outputting on npt port */
                                            msgs = buildDeleteFlows(npt.getPortId(), msgs, sw, cookie, cookieMask);
                                            messageDamper.write(sw, msgs);
                                            log.debug("dst: Removing same-cookie flows to/from DPID={}, port={}", npt.getNodeId(), npt.getPortId());
                                            log.debug("dst: Cookie/mask {}/{}", cookie, cookieMask);
                                        }
                                    }
                                }
                                flowSetIdRegistry.removeExpiredFlowSetId(id, new NodePortTuple(u.getDst(), u.getDstPort()), i);
                            }
                        }
                    }
                    flowSetIdRegistry.removeNodePortTuple(new NodePortTuple(u.getDst(), u.getDstPort()));
                }
            }
        }
    }

    private Set<OFMessage> buildDeleteFlows(OFPort port, Set<OFMessage> msgs, IOFSwitch sw, U64 cookie, U64 cookieMask) {
        if(sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) == 0) {
            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    // cookie mask not supported in OpenFlow 1.0
                    .setMatch(sw.getOFFactory().buildMatch()
                            .setExact(MatchField.IN_PORT, port)
                            .build())
                    .build());

            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    // cookie mask not supported in OpenFlow 1.0
                    .setOutPort(port)
                    .build());
        }
        else {
            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    .setCookieMask(cookieMask)
                    .setMatch(sw.getOFFactory().buildMatch()
                            .setExact(MatchField.IN_PORT, port)
                            .build())
                    .build());

            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    .setCookieMask(cookieMask)
                    .setOutPort(port)
                    .build());
        }

        return msgs;

    }

    @Override
    public Collection<VirtualGatewayInstance> getGatewayInstances() { return l3manager.getAllVirtualGateways(); }

    @Override
    public Optional<VirtualGatewayInstance> getGatewayInstance(String name) { return l3manager.getVirtualGateway(name); }

    @Override
    public Optional<VirtualGatewayInstance> getGatewayInstance(DatapathId dpid) {
        return l3manager.getVirtualGateway(dpid);
    }

    @Override
    public Optional<VirtualGatewayInstance> getGatewayInstance(NodePortTuple npt) {
        return l3manager.getVirtualGateway(npt);
    }

    @Override
    public void deleteGatewayInstances() {
        log.info("All virtual gateways deleted");
        l3manager.removeAllVirtualGateways();
    }

    @Override
    public boolean deleteGatewayInstance(String name) {
        log.info("Virtual gateway {} deleted", name);
        return l3manager.removeVirtualGateway(name);
    }

    @Override
    public void addGatewayInstance(VirtualGatewayInstance gateway) {
        log.info("A new virtual gateway {} created", gateway.getName());
        l3manager.addVirtualGateway(gateway);
    }

    @Override
    public VirtualGatewayInstance updateVirtualGateway(String name, MacAddress newMac) {
        log.info("Virtual gateway {} updated", name);
        return l3manager.updateVirtualGateway(name, newMac);
    }

    @Override
    public Collection<VirtualGatewayInterface> getGatewayInterfaces(VirtualGatewayInstance gateway) {
        return l3manager.getGatewayInterfaces(gateway);
    }

    @Override
    public Optional<VirtualGatewayInterface> getGatewayInterface(String name, VirtualGatewayInstance gateway) {
        return l3manager.getGatewayInterface(name, gateway);
    }

    @Override
    public void removeAllVirtualInterfaces(VirtualGatewayInstance gateway) {
        log.info("All virtual interfaces removed from gateway {}", gateway.getName());
        l3manager.removeAllVirtualInterfaces(gateway);
    }

    @Override
    public boolean removeVirtualInterface(String interfaceName, VirtualGatewayInstance gateway) {
        log.info("Virtual gateway {} removed from gateway {}", interfaceName, gateway.getName());
        return l3manager.removeVirtualInterface(interfaceName, gateway);
    }

    @Override
    public void addVirtualInterface(VirtualGatewayInstance gateway, VirtualGatewayInterface intf) {
        log.info("A new virtual interface {} created for gateway {}", intf.getInterfaceName(), gateway.getName());
        l3manager.addVirtualInterface(gateway, intf);
    }

    @Override
    public void updateVirtualInterface(VirtualGatewayInstance gateway, VirtualGatewayInterface intf) {
        log.info("Virtual interface {} in gateway {} updated ", intf.getInterfaceName(), gateway.getName());
        l3manager.updateVirtualInterface(gateway, intf);
    }

    // IDeviceListener
    class DeviceListenerImpl implements IDeviceListener {
        @Override
        public void deviceAdded(IDevice device) {
            // Ignore
        }

        @Override
        public void deviceRemoved(IDevice device) {
            // Ignore
        }

        @Override
        public void deviceMoved(IDevice device) {
            // Ignore
        }

        @Override
        public void deviceIPV4AddrChanged(IDevice device) {
            // If the device updated IP associated with the packet in the cache, release that packet
            if (device.getIPv4Addresses() == null) return;

            for (IPv4Address ip : device.getIPv4Addresses()) {
                while (findPacketInCache(l3cache, ip).isPresent()) {
                    // send all associated packets to the destination
                    Ethernet eth = findPacketInCache(l3cache, ip).get();
                    sendPacketToLastHop(eth, device);
                    l3cache.values().remove(eth);
                }
            }

        }

        @Override
        public void deviceIPV6AddrChanged(IDevice device) {
            // Ignore
        }

        @Override
        public void deviceVlanChanged(IDevice device) {
            // Ignore
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public boolean isCallbackOrderingPrereq(String type, String name) {
            return false;
        }

        @Override
        public boolean isCallbackOrderingPostreq(String type, String name) {
            return false;
        }
    }

    private Optional<Ethernet> findPacketInCache(Map<OFPacketIn, Ethernet> l3cache, IPv4Address ip) {
        return l3cache.values().stream()
                .filter(packet -> ((IPv4) packet.getPayload()).getDestinationAddress().equals(ip))
                .findAny();
    }

    private void pushL3Packet(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, IDevice dstDevice) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        OFPort inPort = OFMessageUtils.getInPort(pi);

        if (pi.getBufferId().equals(OFBufferId.NO_BUFFER)) {
            sendPacketToLastHop(eth, dstDevice);
        }
        else {
            log.error("BufferId configured on switch. " +
                            "Cannot send packetOut. " +
                            "srcSwitch={} inPort={}",
                    new Object[] {sw, inPort});
            return;
        }

    }

    private boolean sendPacketToLastHop(Ethernet eth, IDevice destDevice) {
        SwitchPort trueAp = findTrueAttachmentPoint(destDevice.getAttachmentPoints());
        if (trueAp == null) {
            return false;
        }

        IOFSwitch sw = switchService.getSwitch(trueAp.getNodeId());
        OFPort outputPort = trueAp.getPortId();

        Optional<VirtualGatewayInstance> instance = getGatewayInstance(trueAp.getNodeId());
        if (!instance.isPresent()) {
            instance = getGatewayInstance(new NodePortTuple(trueAp.getNodeId(), trueAp.getPortId()));
        }

        if (!instance.isPresent()) {
            log.info("Could not locate virtual gateway instance for DPID {}, port {}", sw.getId(), outputPort);
            return false;
        }

        MacAddress gatewayMac = instance.get().getGatewayMac();

        IPacket outPacket = new Ethernet()
                .setSourceMACAddress(gatewayMac)
                .setDestinationMACAddress(destDevice.getMACAddress())
                .setEtherType(eth.getEtherType())
                .setVlanID(eth.getVlanID())
                .setPayload(eth.getPayload());


        OFFactory factory = sw.getOFFactory();
        OFPacketOut.Builder packetOut = factory.buildPacketOut();

        List<OFAction> actions = new ArrayList<>();
        actions.add(factory.actions().output(outputPort, Integer.MAX_VALUE));
        packetOut.setActions(actions);

        packetOut.setData(outPacket.serialize());

        if (log.isTraceEnabled()) {
            log.trace("Writing PacketOut, switch={}, output port={}, packet-out={}",
                    new Object[] {sw, outputPort, packetOut.build()});
        }
        messageDamper.write(sw, packetOut.build());
        log.debug("Push packet out the last hop switch (true attachment point)");

        return true;
    }


    private SwitchPort findTrueAttachmentPoint(SwitchPort[] aps) {
        if (aps != null) {
            for (SwitchPort ap : aps) {
                Set<OFPort> portsOnLinks = topologyService.getPortsWithLinks(ap.getNodeId());
                if (portsOnLinks == null) {
                    log.error("Error looking up ports with links from topology service for switch {}", ap.getNodeId());
                    continue;
                }

                if (!portsOnLinks.contains(ap.getPortId())) {
                    log.debug("Found 'true' attachment point of {}", ap);
                    return ap;
                } else {
                    log.trace("Attachment point {} was not the 'true' attachment point", ap);
                }
            }
        }
		/* This will catch case aps=null, empty, or no-true-ap */
        log.error("Could not locate a 'true' attachment point in {}", aps);
        return null;
    }


}
