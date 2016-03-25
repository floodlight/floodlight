/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.loadbalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.FlowModUtils;

/**
 * A simple load balancer module for ping, tcp, and udp flows. This module is accessed 
 * via a REST API defined close to the OpenStack Quantum LBaaS (Load-balancer-as-a-Service)
 * v1.0 API proposal. Since the proposal has not been final, no efforts have yet been 
 * made to confirm compatibility at this time. 
 * 
 * Limitations:
 * - client records and static flows not purged after use, will exhaust switch flow tables over time
 * - round robin policy among servers based on connections, not traffic volume
 * - health monitoring feature not implemented yet
 *  
 * @author kcwang
 * @edited Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 */
public class LoadBalancer implements IFloodlightModule,
    ILoadBalancerService, IOFMessageListener {

    protected static Logger log = LoggerFactory.getLogger(LoadBalancer.class);

    // Our dependencies
    protected IFloodlightProviderService floodlightProviderService;
    protected IRestApiService restApiService;
    
    protected IDebugCounterService debugCounterService;
    private IDebugCounter counterPacketOut;
    protected IDeviceService deviceManagerService;
    protected IRoutingService routingEngineService;
    protected ITopologyService topologyService;
    protected IStaticFlowEntryPusherService sfpService;
    protected IOFSwitchService switchService;
    
    protected HashMap<String, LBVip> vips;
    protected HashMap<String, LBPool> pools;
    protected HashMap<String, LBMember> members;
    protected HashMap<Integer, String> vipIpToId;
    protected HashMap<Integer, MacAddress> vipIpToMac;
    protected HashMap<Integer, String> memberIpToId;
    protected HashMap<IPClient, LBMember> clientToMember;
    
    //Copied from Forwarding with message damper routine for pushing proxy Arp 
    protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // ms. 
    protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms 
    protected static String LB_ETHER_TYPE = "0x800";
    protected static int LB_PRIORITY = 32768;
    
    // Comparator for sorting by SwitchCluster
    public Comparator<SwitchPort> clusterIdComparator =
            new Comparator<SwitchPort>() {
                @Override
                public int compare(SwitchPort d1, SwitchPort d2) {
                    DatapathId d1ClusterId = topologyService.getOpenflowDomainId(d1.getSwitchDPID());
                    DatapathId d2ClusterId = topologyService.getOpenflowDomainId(d2.getSwitchDPID());
                    return d1ClusterId.compareTo(d2ClusterId);
                }
            };

    // data structure for storing connected
    public class IPClient {
        IPv4Address ipAddress;
        IpProtocol nw_proto;
        TransportPort srcPort; // tcp/udp src port. icmp type (OFMatch convention)
        TransportPort targetPort; // tcp/udp dst port, icmp code (OFMatch convention)
        
        public IPClient() {
            ipAddress = IPv4Address.NONE;
            nw_proto = IpProtocol.NONE;
            srcPort = TransportPort.NONE;
            targetPort = TransportPort.NONE;
        }
    }
    
    @Override
    public String getName() {
        return "loadbalancer";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && 
                (name.equals("topology") || 
                 name.equals("devicemanager") ||
                 name.equals("virtualizer")));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
   }

    @Override
    public net.floodlightcontroller.core.IListener.Command
            receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return processPacketIn(sw, (OFPacketIn)msg, cntx);
            default:
                break;
        }
        log.warn("Received unexpected message {}", msg);
        return Command.CONTINUE;
    }

    private net.floodlightcontroller.core.IListener.Command processPacketIn(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        IPacket pkt = eth.getPayload(); 
 
        if (eth.isBroadcast() || eth.isMulticast()) {
            // handle ARP for VIP
            if (pkt instanceof ARP) {
                // retrieve arp to determine target IP address                                                       
                ARP arpRequest = (ARP) eth.getPayload();

                IPv4Address targetProtocolAddress = arpRequest.getTargetProtocolAddress();

                if (vipIpToId.containsKey(targetProtocolAddress.getInt())) {
                    String vipId = vipIpToId.get(targetProtocolAddress.getInt());
                    vipProxyArpReply(sw, pi, cntx, vipId);
                    return Command.STOP;
                }
            }
        } else {
            // currently only load balance IPv4 packets - no-op for other traffic 
            if (pkt instanceof IPv4) {
                IPv4 ip_pkt = (IPv4) pkt;
                
                // If match Vip and port, check pool and choose member
                int destIpAddress = ip_pkt.getDestinationAddress().getInt();
                
                if (vipIpToId.containsKey(destIpAddress)){
                    IPClient client = new IPClient();
                    client.ipAddress = ip_pkt.getSourceAddress();
                    client.nw_proto = ip_pkt.getProtocol();
                    if (ip_pkt.getPayload() instanceof TCP) {
                        TCP tcp_pkt = (TCP) ip_pkt.getPayload();
                        client.srcPort = tcp_pkt.getSourcePort();
                        client.targetPort = tcp_pkt.getDestinationPort();
                    }
                    if (ip_pkt.getPayload() instanceof UDP) {
                        UDP udp_pkt = (UDP) ip_pkt.getPayload();
                        client.srcPort = udp_pkt.getSourcePort();
                        client.targetPort = udp_pkt.getDestinationPort();
                    }
                    if (ip_pkt.getPayload() instanceof ICMP) {
                        client.srcPort = TransportPort.of(8); 
                        client.targetPort = TransportPort.of(0); 
                    }
                    
                    LBVip vip = vips.get(vipIpToId.get(destIpAddress));
                    if (vip == null)			// fix dereference violations           
                    	return Command.CONTINUE;
                    LBPool pool = pools.get(vip.pickPool(client));
                    if (pool == null)			// fix dereference violations
                    	return Command.CONTINUE;
                    LBMember member = members.get(pool.pickMember(client));
                    if(member == null)			//fix dereference violations
                    	return Command.CONTINUE;
                    
                    // for chosen member, check device manager and find and push routes, in both directions                    
                    pushBidirectionalVipRoutes(sw, pi, cntx, client, member);
                   
                    // packet out based on table rule
                    pushPacket(pkt, sw, pi.getBufferId(), (pi.getVersion().compareTo(OFVersion.OF_12) < 0) ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT), OFPort.TABLE,
                                cntx, true);

                    return Command.STOP;
                }
            }
        }
        // bypass non-load-balanced traffic for normal processing (forwarding)
        return Command.CONTINUE;
    }

    /**
     * used to send proxy Arp for load balanced service requests
     * @param IOFSwitch sw
     * @param OFPacketIn pi
     * @param FloodlightContext cntx
     * @param String vipId
     */
    
    protected void vipProxyArpReply(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, String vipId) {
        log.debug("vipProxyArpReply");
            
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // retrieve original arp to determine host configured gw IP address                                          
        if (! (eth.getPayload() instanceof ARP))
            return;
        ARP arpRequest = (ARP) eth.getPayload();
        
        // have to do proxy arp reply since at this point we cannot determine the requesting application type
        
        // generate proxy ARP reply
        IPacket arpReply = new Ethernet()
            .setSourceMACAddress(vips.get(vipId).proxyMac)
            .setDestinationMACAddress(eth.getSourceMACAddress())
            .setEtherType(EthType.ARP)
            .setVlanID(eth.getVlanID())
            .setPriorityCode(eth.getPriorityCode())
            .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(vips.get(vipId).proxyMac)
                .setSenderProtocolAddress(arpRequest.getTargetProtocolAddress())
                .setTargetHardwareAddress(eth.getSourceMACAddress())
                .setTargetProtocolAddress(arpRequest.getSenderProtocolAddress()));
                
        // push ARP reply out
        pushPacket(arpReply, sw, OFBufferId.NO_BUFFER, OFPort.ANY, (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)), cntx, true);
        log.debug("proxy ARP reply pushed as {}", IPv4.fromIPv4Address(vips.get(vipId).address));
        
        return;
    }

    /**
     * used to push any packet - borrowed routine from Forwarding
     * 
     * @param OFPacketIn pi
     * @param IOFSwitch sw
     * @param int bufferId
     * @param short inPort
     * @param short outPort
     * @param FloodlightContext cntx
     * @param boolean flush
     */    
    public void pushPacket(IPacket packet, 
                           IOFSwitch sw,
                           OFBufferId bufferId,
                           OFPort inPort,
                           OFPort outPort, 
                           FloodlightContext cntx,
                           boolean flush) {
        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} inPort={} outPort={}", 
                      new Object[] {sw, inPort, outPort});
        }

        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(sw.getOFFactory().actions().buildOutput().setPort(outPort).setMaxLen(Integer.MAX_VALUE).build());

        pob.setActions(actions);
        
        // set buffer_id, in_port
        pob.setBufferId(bufferId);
        pob.setInPort(inPort);

        // set data - only if buffer_id == -1
        if (pob.getBufferId() == OFBufferId.NO_BUFFER) {
            if (packet == null) {
                log.error("BufferId is not set and packet data is null. " +
                          "Cannot send packetOut. " +
                        "srcSwitch={} inPort={} outPort={}",
                        new Object[] {sw, inPort, outPort});
                return;
            }
            byte[] packetData = packet.serialize();
            pob.setData(packetData);
        }

        counterPacketOut.increment();
        sw.write(pob.build());
    }

    /**
     * used to find and push in-bound and out-bound routes using StaticFlowEntryPusher
     * @param IOFSwitch sw
     * @param OFPacketIn pi
     * @param FloodlightContext cntx
     * @param IPClient client
     * @param LBMember member
     */
    protected void pushBidirectionalVipRoutes(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, IPClient client, LBMember member) {
        
        // borrowed code from Forwarding to retrieve src and dst device entities
        // Check if we have the location of the destination
        IDevice srcDevice = null;
        IDevice dstDevice = null;
        
        // retrieve all known devices
        Collection<? extends IDevice> allDevices = deviceManagerService.getAllDevices();
        
        for (IDevice d : allDevices) {
            for (int j = 0; j < d.getIPv4Addresses().length; j++) {
                    if (srcDevice == null && client.ipAddress.equals(d.getIPv4Addresses()[j]))
                        srcDevice = d;
                    if (dstDevice == null && member.address == d.getIPv4Addresses()[j].getInt()) {
                        dstDevice = d;
                        member.macString = dstDevice.getMACAddressString();
                    }
                    if (srcDevice != null && dstDevice != null)
                        break;
            }
        }  
        
        // srcDevice and/or dstDevice is null, no route can be pushed
        if (srcDevice == null || dstDevice == null) return;
        
        DatapathId srcIsland = topologyService.getOpenflowDomainId(sw.getId());

        if (srcIsland == null) {
            log.debug("No openflow island found for source {}/{}", 
                      sw.getId().toString(), pi.getInPort());
            return;
        }
        
        // Validate that we have a destination known on the same island
        // Validate that the source and destination are not on the same switchport
        boolean on_same_island = false;
        boolean on_same_if = false;
        for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
            DatapathId dstSwDpid = dstDap.getSwitchDPID();
            DatapathId dstIsland = topologyService.getOpenflowDomainId(dstSwDpid);
            if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
                on_same_island = true;
                if ((sw.getId().equals(dstSwDpid)) && ((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)).equals(dstDap.getPort()))) {
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

        // following Forwarding's same routing routine, retrieve both in-bound and out-bound routes for
        // all clusters.
        while ((iSrcDaps < srcDaps.length) && (iDstDaps < dstDaps.length)) {
            SwitchPort srcDap = srcDaps[iSrcDaps];
            SwitchPort dstDap = dstDaps[iDstDaps];
            DatapathId srcCluster = 
                    topologyService.getOpenflowDomainId(srcDap.getSwitchDPID());
            DatapathId dstCluster = 
                    topologyService.getOpenflowDomainId(dstDap.getSwitchDPID());

            int srcVsDest = srcCluster.compareTo(dstCluster);
            if (srcVsDest == 0) {
                if (!srcDap.equals(dstDap) && 
                        (srcCluster != null) && 
                        (dstCluster != null)) {
                    Route routeIn = 
                            routingEngineService.getRoute(srcDap.getSwitchDPID(),
                                                   srcDap.getPort(),
                                                   dstDap.getSwitchDPID(),
                                                   dstDap.getPort(), U64.of(0));
                    Route routeOut = 
                            routingEngineService.getRoute(dstDap.getSwitchDPID(),
                                                   dstDap.getPort(),
                                                   srcDap.getSwitchDPID(),
                                                   srcDap.getPort(), U64.of(0));

                    // use static flow entry pusher to push flow mod along in and out path
                    // in: match src client (ip, port), rewrite dest from vip ip/port to member ip/port, forward
                    // out: match dest client (ip, port), rewrite src from member ip/port to vip ip/port, forward
                    
                    if (routeIn != null) {
                        pushStaticVipRoute(true, routeIn, client, member, sw);
                    }
                    
                    if (routeOut != null) {
                        pushStaticVipRoute(false, routeOut, client, member, sw);
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
        return;
    }
    
    /**
     * used to push given route using static flow entry pusher
     * @param boolean inBound
     * @param Route route
     * @param IPClient client
     * @param LBMember member
     * @param long pinSwitch
     */
    public void pushStaticVipRoute(boolean inBound, Route route, IPClient client, LBMember member, IOFSwitch pinSwitch) {
        List<NodePortTuple> path = route.getPath();
        if (path.size() > 0) {
           for (int i = 0; i < path.size(); i+=2) {
               DatapathId sw = path.get(i).getNodeId();
               String entryName;
               Match.Builder mb = pinSwitch.getOFFactory().buildMatch();
               ArrayList<OFAction> actions = new ArrayList<OFAction>();
               
               OFFlowMod.Builder fmb = pinSwitch.getOFFactory().buildFlowAdd();

               fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
               fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
               fmb.setBufferId(OFBufferId.NO_BUFFER);
               fmb.setOutPort(OFPort.ANY);
               fmb.setCookie(U64.of(0));  
               fmb.setPriority(FlowModUtils.PRIORITY_MAX);
               
               
               if (inBound) {
                   entryName = "inbound-vip-"+ member.vipId+"-client-"+client.ipAddress
                		   +"-srcport-"+client.srcPort+"-dstport-"+client.targetPort
                           +"-srcswitch-"+path.get(0).getNodeId()+"-sw-"+sw;
                   mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
                   .setExact(MatchField.IP_PROTO, client.nw_proto)
                   .setExact(MatchField.IPV4_SRC, client.ipAddress)
                   .setExact(MatchField.IN_PORT, path.get(i).getPortId());
                   if (client.nw_proto.equals(IpProtocol.TCP)) {
                	   mb.setExact(MatchField.TCP_SRC, client.srcPort);
                   } else if (client.nw_proto.equals(IpProtocol.UDP)) {
                	   mb.setExact(MatchField.UDP_SRC, client.srcPort);
                   } else if (client.nw_proto.equals(IpProtocol.SCTP)) {
                	   mb.setExact(MatchField.SCTP_SRC, client.srcPort);
                   } else if (client.nw_proto.equals(IpProtocol.ICMP)) {
                	   /* no-op */
                   } else {
                	   log.error("Unknown IpProtocol {} detected during inbound static VIP route push.", client.nw_proto);
                   }
                   

                   if (sw.equals(pinSwitch.getId())) {
                       if (pinSwitch.getOFFactory().getVersion().compareTo(OFVersion.OF_12) < 0) { 
                    	   actions.add(pinSwitch.getOFFactory().actions().setDlDst(MacAddress.of(member.macString)));
                    	   actions.add(pinSwitch.getOFFactory().actions().setNwDst(IPv4Address.of(member.address)));
                    	   actions.add(pinSwitch.getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
                       } else { // OXM introduced in OF1.2
                    	   actions.add(pinSwitch.getOFFactory().actions().setField(pinSwitch.getOFFactory().oxms().ethDst(MacAddress.of(member.macString))));
                    	   actions.add(pinSwitch.getOFFactory().actions().setField(pinSwitch.getOFFactory().oxms().ipv4Dst(IPv4Address.of(member.address))));
                    	   actions.add(pinSwitch.getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
                       }
                   } else {
                	   //fix concurrency errors
                	   try{
                		   actions.add(switchService.getSwitch(path.get(i+1).getNodeId()).getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
                	   }
                	   catch(NullPointerException e){
                		   log.error("Fail to install loadbalancer flow rules to offline switch {}.", path.get(i+1).getNodeId());
                	   }
                   }
               } else {
                   entryName = "outbound-vip-"+ member.vipId+"-client-"+client.ipAddress
                		   +"-srcport-"+client.srcPort+"-dstport-"+client.targetPort
                           +"-srcswitch-"+path.get(0).getNodeId()+"-sw-"+sw;
                   mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
                   .setExact(MatchField.IP_PROTO, client.nw_proto)
                   .setExact(MatchField.IPV4_DST, client.ipAddress)
                   .setExact(MatchField.IN_PORT, path.get(i).getPortId());
                   if (client.nw_proto.equals(IpProtocol.TCP)) {
                	   mb.setExact(MatchField.TCP_DST, client.srcPort);
                   } else if (client.nw_proto.equals(IpProtocol.UDP)) {
                	   mb.setExact(MatchField.UDP_DST, client.srcPort);
                   } else if (client.nw_proto.equals(IpProtocol.SCTP)) {
                	   mb.setExact(MatchField.SCTP_DST, client.srcPort);
                   } else if (client.nw_proto.equals(IpProtocol.ICMP)) {
                	   /* no-op */
                   } else {
                	   log.error("Unknown IpProtocol {} detected during outbound static VIP route push.", client.nw_proto);
                   }
                   
                   if (sw.equals(pinSwitch.getId())) {
                       if (pinSwitch.getOFFactory().getVersion().compareTo(OFVersion.OF_12) < 0) { 
                    	   actions.add(pinSwitch.getOFFactory().actions().setDlSrc(vips.get(member.vipId).proxyMac));
                    	   actions.add(pinSwitch.getOFFactory().actions().setNwSrc(IPv4Address.of(vips.get(member.vipId).address)));
                    	   actions.add(pinSwitch.getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
                       } else { // OXM introduced in OF1.2
                    	   actions.add(pinSwitch.getOFFactory().actions().setField(pinSwitch.getOFFactory().oxms().ethSrc(vips.get(member.vipId).proxyMac)));
                    	   actions.add(pinSwitch.getOFFactory().actions().setField(pinSwitch.getOFFactory().oxms().ipv4Src(IPv4Address.of(vips.get(member.vipId).address))));
                    	   actions.add(pinSwitch.getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
                       }
                   } else {
                	   //fix concurrency errors
                	   try{
                		   actions.add(switchService.getSwitch(path.get(i+1).getNodeId()).getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
                	   }
                	   catch(NullPointerException e){
                		   log.error("Fail to install loadbalancer flow rules to offline switches {}.", path.get(i+1).getNodeId());
                	   }
                   }
                   
               }
               
               
               fmb.setActions(actions);
               fmb.setPriority(U16.t(LB_PRIORITY));
               fmb.setMatch(mb.build());
               sfpService.addFlow(entryName, fmb.build(), sw);
           }
        }
        return;
    }

    
    @Override
    public Collection<LBVip> listVips() {
        return vips.values();
    }

    @Override
    public Collection<LBVip> listVip(String vipId) {
        Collection<LBVip> result = new HashSet<LBVip>();
        result.add(vips.get(vipId));
        return result;
    }

    @Override
    public LBVip createVip(LBVip vip) {
        if (vip == null)
            vip = new LBVip();
        
        vips.put(vip.id, vip);
        vipIpToId.put(vip.address, vip.id);
        vipIpToMac.put(vip.address, vip.proxyMac);
        
        return vip;
    }

    @Override
    public LBVip updateVip(LBVip vip) {
        vips.put(vip.id, vip);
        return vip;
    }

    @Override
    public int removeVip(String vipId) {
        if(vips.containsKey(vipId)){
            vips.remove(vipId);
            return 0;
        } else {
            return -1;
        }
    }

    @Override
    public Collection<LBPool> listPools() {
        return pools.values();
    }

    @Override
    public Collection<LBPool> listPool(String poolId) {
        Collection<LBPool> result = new HashSet<LBPool>();
        result.add(pools.get(poolId));
        return result;
    }

    @Override
    public LBPool createPool(LBPool pool) {
        if (pool == null)
            pool = new LBPool();
        
        pools.put(pool.id, pool);
        if (pool.vipId != null && vips.containsKey(pool.vipId))
            vips.get(pool.vipId).pools.add(pool.id);
        else {
            log.error("specified vip-id must exist");
            pool.vipId = null;
            pools.put(pool.id, pool);
        }
        return pool;
    }

    @Override
    public LBPool updatePool(LBPool pool) {
        pools.put(pool.id, pool);
        return null;
    }

    @Override
    public int removePool(String poolId) {
        LBPool pool;
        if (pools != null) {
            pool = pools.get(poolId);
            if (pool == null)	// fix dereference violations
            	return -1;
            if (pool.vipId != null)
                vips.get(pool.vipId).pools.remove(poolId);
            pools.remove(poolId);
            return 0;
        } else {
            return -1;
        }
    }

    @Override
    public Collection<LBMember> listMembers() {
        return members.values();
    }

    @Override
    public Collection<LBMember> listMember(String memberId) {
        Collection<LBMember> result = new HashSet<LBMember>();
        result.add(members.get(memberId));
        return result;
        }

    @Override
    public Collection<LBMember> listMembersByPool(String poolId) {
        Collection<LBMember> result = new HashSet<LBMember>();
        
        if(pools.containsKey(poolId)) {
            ArrayList<String> memberIds = pools.get(poolId).members;
            for (int i = 0; i<memberIds.size(); i++)
                result.add(members.get(memberIds.get(i)));
        }
        return result;
    }
    
    @Override
    public LBMember createMember(LBMember member) {
        if (member == null)
            member = new LBMember();

        members.put(member.id, member);
        memberIpToId.put(member.address, member.id);

        if (member.poolId != null && pools.get(member.poolId) != null) {
            member.vipId = pools.get(member.poolId).vipId;
            if (!pools.get(member.poolId).members.contains(member.id))
                pools.get(member.poolId).members.add(member.id);
        } else
            log.error("member must be specified with non-null pool_id");
        
        return member;
    }

    @Override
    public LBMember updateMember(LBMember member) {
        members.put(member.id, member);
        return member;
    }

    @Override
    public int removeMember(String memberId) {
        LBMember member;
        member = members.get(memberId);
        
        if(member != null){
            if (member.poolId != null)
                pools.get(member.poolId).members.remove(memberId);
            members.remove(memberId);
            return 0;
        } else {
            return -1;
        }    
    }

    @Override
    public Collection<LBMonitor> listMonitors() {
        return null;
    }

    @Override
    public Collection<LBMonitor> listMonitor(String monitorId) {
        return null;
    }

    @Override
    public LBMonitor createMonitor(LBMonitor monitor) {
        return null;
    }

    @Override
    public LBMonitor updateMonitor(LBMonitor monitor) {
        return null;
    }

    @Override
    public int removeMonitor(String monitorId) {
        return 0;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILoadBalancerService.class);
        return l;
   }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(ILoadBalancerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IRestApiService.class);
        l.add(IOFSwitchService.class);
        l.add(IDeviceService.class);
        l.add(IDebugCounterService.class);
        l.add(ITopologyService.class);
        l.add(IRoutingService.class);
        l.add(IStaticFlowEntryPusherService.class);

        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {
        floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
        debugCounterService = context.getServiceImpl(IDebugCounterService.class);
        deviceManagerService = context.getServiceImpl(IDeviceService.class);
        routingEngineService = context.getServiceImpl(IRoutingService.class);
        topologyService = context.getServiceImpl(ITopologyService.class);
        sfpService = context.getServiceImpl(IStaticFlowEntryPusherService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        
        vips = new HashMap<String, LBVip>();
        pools = new HashMap<String, LBPool>();
        members = new HashMap<String, LBMember>();
        vipIpToId = new HashMap<Integer, String>();
        vipIpToMac = new HashMap<Integer, MacAddress>();
        memberIpToId = new HashMap<Integer, String>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
        restApiService.addRestletRoutable(new LoadBalancerWebRoutable());
        debugCounterService.registerModule(this.getName());
        counterPacketOut = debugCounterService.registerCounter(this.getName(), "packet-outs-written", "Packet outs written by the LoadBalancer", MetaData.WARN);
    }
}
