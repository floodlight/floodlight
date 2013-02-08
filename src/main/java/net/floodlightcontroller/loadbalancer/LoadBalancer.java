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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionNetworkTypeOfService;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.protocol.action.OFActionVirtualLanPriorityCodePoint;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
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
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.util.OFMessageDamper;

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
 */
public class LoadBalancer implements IFloodlightModule,
    ILoadBalancerService, IOFMessageListener {

    protected static Logger log = LoggerFactory.getLogger(LoadBalancer.class);

    // Our dependencies
    protected IFloodlightProviderService floodlightProvider;
    protected IRestApiService restApi;
    
    protected ICounterStoreService counterStore;
    protected OFMessageDamper messageDamper;
    protected IDeviceService deviceManager;
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected IStaticFlowEntryPusherService sfp;
    
    protected HashMap<String, LBVip> vips;
    protected HashMap<String, LBPool> pools;
    protected HashMap<String, LBMember> members;
    protected HashMap<Integer, String> vipIpToId;
    protected HashMap<Integer, MACAddress> vipIpToMac;
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
                    Long d1ClusterId = 
                            topology.getL2DomainId(d1.getSwitchDPID());
                    Long d2ClusterId = 
                            topology.getL2DomainId(d2.getSwitchDPID());
                    return d1ClusterId.compareTo(d2ClusterId);
                }
            };

    // data structure for storing connected
    public class IPClient {
        int ipAddress;
        byte nw_proto;
        short srcPort; // tcp/udp src port. icmp type (OFMatch convention)
        short targetPort; // tcp/udp dst port, icmp code (OFMatch convention)
        
        public IPClient() {
            ipAddress = 0;
            nw_proto = 0;
            srcPort = -1;
            targetPort = -1;
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

    private net.floodlightcontroller.core.IListener.Command
            processPacketIn(IOFSwitch sw, OFPacketIn pi,
                            FloodlightContext cntx) {
        
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        IPacket pkt = eth.getPayload();
 
        if (eth.isBroadcast() || eth.isMulticast()) {
            // handle ARP for VIP
            if (eth.getEtherType() == Ethernet.TYPE_ARP) {
                // retrieve arp to determine target IP address                                                       
                ARP arpRequest = (ARP) eth.getPayload();

                int targetProtocolAddress = IPv4.toIPv4Address(arpRequest
                                                               .getTargetProtocolAddress());

                if (vipIpToId.containsKey(targetProtocolAddress)) {
                    String vipId = vipIpToId.get(targetProtocolAddress);                    
                    vipProxyArpReply(sw, pi, cntx, vipId);
                    return Command.STOP;
                }
            }
        } else {
            // currently only load balance IPv4 packets - no-op for other traffic 
            if (pkt instanceof IPv4) {
                IPv4 ip_pkt = (IPv4) pkt;                
                
                // If match Vip and port, check pool and choose member
                int destIpAddress = ip_pkt.getDestinationAddress();
                
                if (vipIpToId.containsKey(destIpAddress)){
                    IPClient client = new IPClient();
                    client.ipAddress = ip_pkt.getSourceAddress();
                    client.nw_proto = ip_pkt.getProtocol();
                    if (client.nw_proto == IPv4.PROTOCOL_TCP) {
                        TCP tcp_pkt = (TCP) ip_pkt.getPayload();
                        client.srcPort = tcp_pkt.getSourcePort();
                        client.targetPort = tcp_pkt.getDestinationPort();
                    }
                    if (client.nw_proto == IPv4.PROTOCOL_UDP) {
                        UDP udp_pkt = (UDP) ip_pkt.getPayload();
                        client.srcPort = udp_pkt.getSourcePort();
                        client.targetPort = udp_pkt.getDestinationPort();
                    }
                    if (client.nw_proto == IPv4.PROTOCOL_ICMP) {
                        client.srcPort = 8; 
                        client.targetPort = 0; 
                    }
                    
                    LBVip vip = vips.get(vipIpToId.get(destIpAddress));
                    LBPool pool = pools.get(vip.pickPool(client));
                    LBMember member = members.get(pool.pickMember(client));

                    // for chosen member, check device manager and find and push routes, in both directions                    
                    pushBidirectionalVipRoutes(sw, pi, cntx, client, member);
                   
                    // packet out based on table rule
                    pushPacket(pkt, sw, pi.getBufferId(), pi.getInPort(), OFPort.OFPP_TABLE.getValue(),
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
        ARP arpRequest = (ARP) eth.getPayload();
        
        // have to do proxy arp reply since at this point we cannot determine the requesting application type
        byte[] vipProxyMacBytes = vips.get(vipId).proxyMac.toBytes();
        
        // generate proxy ARP reply
        IPacket arpReply = new Ethernet()
            .setSourceMACAddress(vipProxyMacBytes)
            .setDestinationMACAddress(eth.getSourceMACAddress())
            .setEtherType(Ethernet.TYPE_ARP)
            .setVlanID(eth.getVlanID())
            .setPriorityCode(eth.getPriorityCode())
            .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(vipProxyMacBytes)
                .setSenderProtocolAddress(
                        arpRequest.getTargetProtocolAddress())
                .setTargetHardwareAddress(
                        eth.getSourceMACAddress())
                .setTargetProtocolAddress(
                        arpRequest.getSenderProtocolAddress()));
                
        // push ARP reply out
        pushPacket(arpReply, sw, OFPacketOut.BUFFER_ID_NONE, OFPort.OFPP_NONE.getValue(),
                   pi.getInPort(), cntx, true);
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
                           int bufferId,
                           short inPort,
                           short outPort, 
                           FloodlightContext cntx,
                           boolean flush) {
        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} inPort={} outPort={}", 
                      new Object[] {sw, inPort, outPort});
        }

        OFPacketOut po =
                (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                                .getMessage(OFType.PACKET_OUT);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(outPort, (short) 0xffff));

        po.setActions(actions)
          .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength =
                (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

        // set buffer_id, in_port
        po.setBufferId(bufferId);
        po.setInPort(inPort);

        // set data - only if buffer_id == -1
        if (po.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            if (packet == null) {
                log.error("BufferId is not set and packet data is null. " +
                          "Cannot send packetOut. " +
                        "srcSwitch={} inPort={} outPort={}",
                        new Object[] {sw, inPort, outPort});
                return;
            }
            byte[] packetData = packet.serialize();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }

        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            messageDamper.write(sw, po, cntx, flush);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
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
        Collection<? extends IDevice> allDevices = deviceManager
                .getAllDevices();
        
        for (IDevice d : allDevices) {
            for (int j = 0; j < d.getIPv4Addresses().length; j++) {
                    if (srcDevice == null && client.ipAddress == d.getIPv4Addresses()[j])
                        srcDevice = d;
                    if (dstDevice == null && member.address == d.getIPv4Addresses()[j]) {
                        dstDevice = d;
                        member.macString = dstDevice.getMACAddressString();
                    }
                    if (srcDevice != null && dstDevice != null)
                        break;
            }
        }   
        
        Long srcIsland = topology.getL2DomainId(sw.getId());

        if (srcIsland == null) {
            log.debug("No openflow island found for source {}/{}", 
                      sw.getStringId(), pi.getInPort());
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
            Long srcCluster = 
                    topology.getL2DomainId(srcDap.getSwitchDPID());
            Long dstCluster = 
                    topology.getL2DomainId(dstDap.getSwitchDPID());

            int srcVsDest = srcCluster.compareTo(dstCluster);
            if (srcVsDest == 0) {
                if (!srcDap.equals(dstDap) && 
                        (srcCluster != null) && 
                        (dstCluster != null)) {
                    Route routeIn = 
                            routingEngine.getRoute(srcDap.getSwitchDPID(),
                                                   (short)srcDap.getPort(),
                                                   dstDap.getSwitchDPID(),
                                                   (short)dstDap.getPort(), 0);
                    Route routeOut = 
                            routingEngine.getRoute(dstDap.getSwitchDPID(),
                                                   (short)dstDap.getPort(),
                                                   srcDap.getSwitchDPID(),
                                                   (short)srcDap.getPort(), 0);

                    // use static flow entry pusher to push flow mod along in and out path
                    // in: match src client (ip, port), rewrite dest from vip ip/port to member ip/port, forward
                    // out: match dest client (ip, port), rewrite src from member ip/port to vip ip/port, forward
                    
                    if (routeIn != null) {                            
                        pushStaticVipRoute(true, routeIn, client, member, sw.getId());
                    }
                        
                    if (routeOut != null) {                            
                        pushStaticVipRoute(false, routeOut, client, member, sw.getId());
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
    public void pushStaticVipRoute(boolean inBound, Route route, IPClient client, LBMember member, long pinSwitch) {
        List<NodePortTuple> path = route.getPath();
        if (path.size()>0) {
           for (int i = 0; i < path.size(); i+=2) {
               
               long sw = path.get(i).getNodeId();
               String swString = HexString.toHexString(path.get(i).getNodeId());
               String entryName;
               String matchString = null;
               String actionString = null;
               
               OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory()
                       .getMessage(OFType.FLOW_MOD);

               fm.setIdleTimeout((short) 0);   // infinite
               fm.setHardTimeout((short) 0);   // infinite
               fm.setBufferId(OFPacketOut.BUFFER_ID_NONE);
               fm.setCommand((short) 0);
               fm.setFlags((short) 0);
               fm.setOutPort(OFPort.OFPP_NONE.getValue());
               fm.setCookie((long) 0);  
               fm.setPriority(Short.MAX_VALUE);
               
               if (inBound) {
                   entryName = "inbound-vip-"+ member.vipId+"-client-"+client.ipAddress+"-port-"+client.targetPort
                           +"-srcswitch-"+path.get(0).getNodeId()+"-sw-"+sw;
                   matchString = "nw_src="+IPv4.fromIPv4Address(client.ipAddress)+","
                               + "nw_proto="+String.valueOf(client.nw_proto)+","
                               + "tp_src="+String.valueOf(client.srcPort & 0xffff)+","
                               + "dl_type="+LB_ETHER_TYPE+","
                               + "in_port="+String.valueOf(path.get(i).getPortId());

                   if (sw == pinSwitch) {
                       actionString = "set-dst-ip="+IPv4.fromIPv4Address(member.address)+"," 
                                + "set-dst-mac="+member.macString+","
                                + "output="+path.get(i+1).getPortId();
                   } else {
                       actionString =
                               "output="+path.get(i+1).getPortId();
                   }
               } else {
                   entryName = "outbound-vip-"+ member.vipId+"-client-"+client.ipAddress+"-port-"+client.targetPort
                           +"-srcswitch-"+path.get(0).getNodeId()+"-sw-"+sw;
                   matchString = "nw_dst="+IPv4.fromIPv4Address(client.ipAddress)+","
                               + "nw_proto="+String.valueOf(client.nw_proto)+","
                               + "tp_dst="+String.valueOf(client.srcPort & 0xffff)+","
                               + "dl_type="+LB_ETHER_TYPE+","
                               + "in_port="+String.valueOf(path.get(i).getPortId());

                   if (sw == pinSwitch) {
                       actionString = "set-src-ip="+IPv4.fromIPv4Address(vips.get(member.vipId).address)+","
                               + "set-src-mac="+vips.get(member.vipId).proxyMac.toString()+","
                               + "output="+path.get(i+1).getPortId();
                   } else {
                       actionString = "output="+path.get(i+1).getPortId();
                   }
                   
               }
               
               parseActionString(fm, actionString, log);

               fm.setPriority(U16.t(LB_PRIORITY));

               OFMatch ofMatch = new OFMatch();
               try {
                   ofMatch.fromString(matchString);
               } catch (IllegalArgumentException e) {
                   log.debug(
                             "ignoring flow entry {} on switch {} with illegal OFMatch() key: "
                                     + matchString, entryName, swString);
               }
        
               fm.setMatch(ofMatch);
               sfp.addFlow(entryName, fm, swString);

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
        return result;    }

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
        if (pool==null)
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
        if(pools!=null){
            pool = pools.get(poolId);
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
            for (int i=0; i<memberIds.size(); i++)
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<LBMonitor> listMonitor(String monitorId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public LBMonitor createMonitor(LBMonitor monitor) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public LBMonitor updateMonitor(LBMonitor monitor) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int removeMonitor(String monitorId) {
        // TODO Auto-generated method stub
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
        l.add(ICounterStoreService.class);
        l.add(IDeviceService.class);
        l.add(ITopologyService.class);
        l.add(IRoutingService.class);
        l.add(IStaticFlowEntryPusherService.class);

        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        counterStore = context.getServiceImpl(ICounterStoreService.class);
        deviceManager = context.getServiceImpl(IDeviceService.class);
        routingEngine = context.getServiceImpl(IRoutingService.class);
        topology = context.getServiceImpl(ITopologyService.class);
        sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
        
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY, 
                                            EnumSet.of(OFType.FLOW_MOD),
                                            OFMESSAGE_DAMPER_TIMEOUT);
        
        vips = new HashMap<String, LBVip>();
        pools = new HashMap<String, LBPool>();
        members = new HashMap<String, LBMember>();
        vipIpToId = new HashMap<Integer, String>();
        vipIpToMac = new HashMap<Integer, MACAddress>();
        memberIpToId = new HashMap<Integer, String>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApi.addRestletRoutable(new LoadBalancerWebRoutable());
    }

    // Utilities borrowed from StaticFlowEntries
    
    private static class SubActionStruct {
        OFAction action;
        int      len;
    }
    
    /**
     * Parses OFFlowMod actions from strings.
     * @param flowMod The OFFlowMod to set the actions for
     * @param actionstr The string containing all the actions
     * @param log A logger to log for errors.
     */
    public static void parseActionString(OFFlowMod flowMod, String actionstr, Logger log) {
        List<OFAction> actions = new LinkedList<OFAction>();
        int actionsLength = 0;
        if (actionstr != null) {
            actionstr = actionstr.toLowerCase();
            for (String subaction : actionstr.split(",")) {
                String action = subaction.split("[=:]")[0];
                SubActionStruct subaction_struct = null;
                
                if (action.equals("output")) {
                    subaction_struct = decode_output(subaction, log);
                }
                else if (action.equals("enqueue")) {
                    subaction_struct = decode_enqueue(subaction, log);
                }
                else if (action.equals("strip-vlan")) {
                    subaction_struct = decode_strip_vlan(subaction, log);
                }
                else if (action.equals("set-vlan-id")) {
                    subaction_struct = decode_set_vlan_id(subaction, log);
                }
                else if (action.equals("set-vlan-priority")) {
                    subaction_struct = decode_set_vlan_priority(subaction, log);
                }
                else if (action.equals("set-src-mac")) {
                    subaction_struct = decode_set_src_mac(subaction, log);
                }
                else if (action.equals("set-dst-mac")) {
                    subaction_struct = decode_set_dst_mac(subaction, log);
                }
                else if (action.equals("set-tos-bits")) {
                    subaction_struct = decode_set_tos_bits(subaction, log);
                }
                else if (action.equals("set-src-ip")) {
                    subaction_struct = decode_set_src_ip(subaction, log);
                }
                else if (action.equals("set-dst-ip")) {
                    subaction_struct = decode_set_dst_ip(subaction, log);
                }
                else if (action.equals("set-src-port")) {
                    subaction_struct = decode_set_src_port(subaction, log);
                }
                else if (action.equals("set-dst-port")) {
                    subaction_struct = decode_set_dst_port(subaction, log);
                }
                else {
                    log.error("Unexpected action '{}', '{}'", action, subaction);
                }
                
                if (subaction_struct != null) {
                    actions.add(subaction_struct.action);
                    actionsLength += subaction_struct.len;
                }
            }
        }
        log.debug("action {}", actions);
        
        flowMod.setActions(actions);
        flowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH + actionsLength);
    } 
    
    private static SubActionStruct decode_output(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("output=(?:((?:0x)?\\d+)|(all)|(controller)|(local)|(ingress-port)|(normal)|(flood))").matcher(subaction);
        if (n.matches()) {
            OFActionOutput action = new OFActionOutput();
            action.setMaxLength((short) Short.MAX_VALUE);
            short port = OFPort.OFPP_NONE.getValue();
            if (n.group(1) != null) {
                try {
                    port = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid port in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else if (n.group(2) != null)
                port = OFPort.OFPP_ALL.getValue();
            else if (n.group(3) != null)
                port = OFPort.OFPP_CONTROLLER.getValue();
            else if (n.group(4) != null)
                port = OFPort.OFPP_LOCAL.getValue();
            else if (n.group(5) != null)
                port = OFPort.OFPP_IN_PORT.getValue();
            else if (n.group(6) != null)
                port = OFPort.OFPP_NORMAL.getValue();
            else if (n.group(7) != null)
                port = OFPort.OFPP_FLOOD.getValue();
            action.setPort(port);
            log.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionOutput.MINIMUM_LENGTH;
        }
        else {
            log.error("Invalid subaction: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    private static SubActionStruct decode_enqueue(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("enqueue=(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(subaction);
        if (n.matches()) {
            short portnum = 0;
            if (n.group(1) != null) {
                try {
                    portnum = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid port-num in: '{}' (error ignored)", subaction);
                    return null;
                }
            }

            int queueid = 0;
            if (n.group(2) != null) {
                try {
                    queueid = get_int(n.group(2));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid queue-id in: '{}' (error ignored)", subaction);
                    return null;
               }
            }
            
            OFActionEnqueue action = new OFActionEnqueue();
            action.setPort(portnum);
            action.setQueueId(queueid);
            log.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionEnqueue.MINIMUM_LENGTH;
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    private static SubActionStruct decode_strip_vlan(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("strip-vlan").matcher(subaction);
        
        if (n.matches()) {
            OFActionStripVirtualLan action = new OFActionStripVirtualLan();
            log.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionStripVirtualLan.MINIMUM_LENGTH;
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_vlan_id(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-vlan-id=((?:0x)?\\d+)").matcher(subaction);
        
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    short vlanid = get_short(n.group(1));
                    OFActionVirtualLanIdentifier action = new OFActionVirtualLanIdentifier();
                    action.setVirtualLanIdentifier(vlanid);
                    log.debug("  action {}", action);

                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid VLAN in: {} (error ignored)", subaction);
                    return null;
                }
            }          
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_vlan_priority(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-vlan-priority=((?:0x)?\\d+)").matcher(subaction); 
        
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    byte prior = get_byte(n.group(1));
                    OFActionVirtualLanPriorityCodePoint action = new OFActionVirtualLanPriorityCodePoint();
                    action.setVirtualLanPriorityCodePoint(prior);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionVirtualLanPriorityCodePoint.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid VLAN priority in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_src_mac(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction); 

        if (n.matches()) {
            byte[] macaddr = get_mac_addr(n, subaction, log);
            if (macaddr != null) {
                OFActionDataLayerSource action = new OFActionDataLayerSource();
                action.setDataLayerAddress(macaddr);
                log.debug("action {}", action);

                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionDataLayerSource.MINIMUM_LENGTH;
            }            
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_mac(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction);
        
        if (n.matches()) {
            byte[] macaddr = get_mac_addr(n, subaction, log);            
            if (macaddr != null) {
                OFActionDataLayerDestination action = new OFActionDataLayerDestination();
                action.setDataLayerAddress(macaddr);
                log.debug("  action {}", action);
                
                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionDataLayerDestination.MINIMUM_LENGTH;
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_tos_bits(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-tos-bits=((?:0x)?\\d+)").matcher(subaction); 

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    byte tosbits = get_byte(n.group(1));
                    OFActionNetworkTypeOfService action = new OFActionNetworkTypeOfService();
                    action.setNetworkTypeOfService(tosbits);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionNetworkTypeOfService.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_src_ip(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);

        if (n.matches()) {
            int ipaddr = get_ip_addr(n, subaction, log);
            OFActionNetworkLayerSource action = new OFActionNetworkLayerSource();
            action.setNetworkAddress(ipaddr);
            log.debug("  action {}", action);

            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionNetworkLayerSource.MINIMUM_LENGTH;
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_ip(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);

        if (n.matches()) {
            int ipaddr = get_ip_addr(n, subaction, log);
            OFActionNetworkLayerDestination action = new OFActionNetworkLayerDestination();
            action.setNetworkAddress(ipaddr);
            log.debug("action {}", action);
 
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionNetworkLayerDestination.MINIMUM_LENGTH;
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_src_port(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-port=((?:0x)?\\d+)").matcher(subaction); 

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    short portnum = get_short(n.group(1));
                    OFActionTransportLayerSource action = new OFActionTransportLayerSource();
                    action.setTransportPort(portnum);
                    log.debug("action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionTransportLayerSource.MINIMUM_LENGTH;;
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid src-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_port(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-port=((?:0x)?\\d+)").matcher(subaction);

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    short portnum = get_short(n.group(1));
                    OFActionTransportLayerDestination action = new OFActionTransportLayerDestination();
                    action.setTransportPort(portnum);
                    log.debug("action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionTransportLayerDestination.MINIMUM_LENGTH;;
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static byte[] get_mac_addr(Matcher n, String subaction, Logger log) {
        byte[] macaddr = new byte[6];
        
        for (int i=0; i<6; i++) {
            if (n.group(i+1) != null) {
                try {
                    macaddr[i] = get_byte("0x" + n.group(i+1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid src-mac in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else { 
                log.debug("Invalid src-mac in: '{}' (null, error ignored)", subaction);
                return null;
            }
        }
        
        return macaddr;
    }
    
    private static int get_ip_addr(Matcher n, String subaction, Logger log) {
        int ipaddr = 0;

        for (int i=0; i<4; i++) {
            if (n.group(i+1) != null) {
                try {
                    ipaddr = ipaddr<<8;
                    ipaddr = ipaddr | get_int(n.group(i+1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid src-ip in: '{}' (error ignored)", subaction);
                    return 0;
                }
            }
            else {
                log.debug("Invalid src-ip in: '{}' (null, error ignored)", subaction);
                return 0;
            }
        }
        
        return ipaddr;
    }
    
    // Parse int as decimal, hex (start with 0x or #) or octal (starts with 0)
    private static int get_int(String str) {
        return (int)Integer.decode(str);
    }
   
    // Parse short as decimal, hex (start with 0x or #) or octal (starts with 0)
    private static short get_short(String str) {
        return (short)(int)Integer.decode(str);
    }
   
    // Parse byte as decimal, hex (start with 0x or #) or octal (starts with 0)
    private static byte get_byte(String str) {
        return Integer.decode(str).byteValue();
    }

    
}
