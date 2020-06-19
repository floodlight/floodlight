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


import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
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
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.statistics.FlowRuleStats;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.PortDesc;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.util.Pair;

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
	private IDebugCounter counterPacketIn;
	protected IDeviceService deviceManagerService;
	protected IRoutingService routingEngineService;
	protected ITopologyService topologyService;
	protected IStaticEntryPusherService sfpService;
	protected IOFSwitchService switchService;
	protected IStatisticsService statisticsService;
	protected IThreadPoolService threadService;

	protected HashMap<String, LBVip> vips;
	protected HashMap<String, LBPool> pools;
	protected HashMap<String, LBMember> members;
	protected HashMap<String, LBMonitor> monitors;
	protected HashMap<Integer, String> vipIpToId;
	protected HashMap<IPv4Address, MacAddress> vipIpToMac;
	protected HashMap<String, Short> memberStatus;
	protected HashMap<String, Integer> memberIdToIp;
	protected HashMap<IPClient, LBMember> clientToMember;
	protected HashMap<Pair<Match,DatapathId>,String> flowToVipId;
	protected HashMap<String, SwitchPort> memberIdToSwitchPort;

	private static ScheduledFuture<?> healthMonitoring;
	private static int healthMonitorsInterval = 10; /* (s) can be changed through NBI */

	private static final int ICMP_PAYLOAD_LENGTH = 4;

	protected static boolean isMonitoringEnabled = false;

	private static final int flowStatsInterval = 15;

	protected enum TLS {
		HTTPS(TransportPort.of(443)),
		IMAP(TransportPort.of(993)),
		POP(TransportPort.of(995)),
		SMTP(TransportPort.of(465));

		private final TransportPort port;
		TLS(TransportPort port){
			this.port = port;
		}
	}

	//Copied from Forwarding with message damper routine for pushing proxy Arp 
	protected static String LB_ETHER_TYPE = "0x800";
	protected static int LB_PRIORITY = 32768;

	// Comparator for sorting by SwitchCluster
	public Comparator<SwitchPort> clusterIdComparator =
			new Comparator<SwitchPort>() {
		@Override
		public int compare(SwitchPort d1, SwitchPort d2) {
			DatapathId d1ClusterId = topologyService.getClusterId(d1.getNodeId());
			DatapathId d2ClusterId = topologyService.getClusterId(d2.getNodeId());
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

						if(isMonitoringEnabled){
							int srcIpAddress = ip_pkt.getSourceAddress().getInt();
							ICMP icmp_pkt = (ICMP) ip_pkt.getPayload();

							if(icmp_pkt.getIcmpType() == ICMP.ECHO_REPLY){
								Data d = (Data) icmp_pkt.getPayload();
								byte[] bit =  d.getData();

								String str;
								try {
									str = new String (bit, "UTF-8");
									str = str.replaceAll("\\D+",""); // only numbers VIP ID

									for(LBMember member: members.values()){
										if(member.vipId.equals(str) && member.address == srcIpAddress){
											member.status = 1;
										}
										
										if(member.status == 0)
											member.status =-1;
										
										memberStatus.put(member.id, member.status);
										if(member.status == -1){
											log.info("Member: " + member.id + " status: Pending Response...");	
										} else {
											log.info("Member: " + member.id + " status: Active");	
										}
									}
									return Command.STOP; // switches will not have a flow rule, so members ICMP reply will come as packet-in
								
								}catch (UnsupportedEncodingException e) {
									log.error("ICMP reply payload not convertable to string" + e.getMessage());
									return Command.STOP;
								} 
							}
						}
					}

					// TLS traffic is redirect to any VIP with TLS as protocol
					LBPool pool = null;
					for(TLS protocol: TLS.values()){
						if(client.targetPort.equals(protocol.port)){ // TLS request
							for(LBVip vip: vips.values()){
								if(IpProtocol.of(vip.protocol).equals(IpProtocol.TLSP)){
									pool = pools.get(vip.pickPool(client));
									if(pool == null)
										return Command.CONTINUE;
									break;
								}
							}
						}
						break;
					}
					if(pool == null){
						LBVip vip = vips.get(vipIpToId.get(destIpAddress));
						if (vip == null)			// fix dereference violations           
							return Command.CONTINUE;

						pool = pools.get(vip.pickPool(client));
						if(pool == null)
							return Command.CONTINUE;
					}
					HashMap<String, Short> memberWeights = new HashMap<>();
					HashMap<String, U64> memberPortBandwidth = new HashMap<>();


					if(pool.lbMethod == LBPool.WEIGHTED_RR){
						for(String memberId: pool.members){
							memberWeights.put(memberId,members.get(memberId).weight);
						}
					}

					// Switch statistics collection
					if(pool.lbMethod == LBPool.STATISTICS && statisticsService != null){
						statisticsService.collectStatistics(true);
						memberPortBandwidth = collectSwitchPortBandwidth(pool);
					}

					LBMember member = members.get(pool.pickMember(client,memberPortBandwidth,memberWeights,memberStatus));
					if(member == null)			//fix dereference violations
						return Command.CONTINUE;

					log.info("Member " + IPv4Address.of(member.address) + " has been picked by the load balancer.");
					// for chosen member, check device manager and find and push routes, in both directions                    
					pushBidirectionalVipRoutes(sw, pi, cntx, client, member);

					// packet out based on table rule
					pushPacket(pkt, sw, pi.getBufferId(), (pi.getVersion().compareTo(OFVersion.OF_12) < 0) ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT), OFPort.TABLE,
							cntx, true);

					counterPacketIn.increment();
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
		List<OFAction> actions = new ArrayList<>();
		actions.add(sw.getOFFactory().actions().buildOutput().setPort(outPort).setMaxLen(Integer.MAX_VALUE).build());

		pob.setActions(actions);

		// set buffer_id, in_port
		pob.setBufferId(bufferId);
		OFMessageUtils.setInPort(pob, inPort);

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

		DatapathId srcIsland = topologyService.getClusterId(sw.getId());

		if (srcIsland == null) {
			log.debug("No openflow island found for source {}/{}", 
					sw.getId().toString(), pi.getInPort());
			return;
		}

		// Validate that we have a destination known on the same island
		// Validate that the source and destination are not on the same switchport
		boolean on_same_island = false;
		boolean on_same_if = false;
		//Switch
		for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
			DatapathId dstSwDpid = dstDap.getNodeId();
			DatapathId dstIsland = topologyService.getClusterId(dstSwDpid);
			if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
				on_same_island = true;
				if ((sw.getId().equals(dstSwDpid)) && OFMessageUtils.getInPort(pi).equals(dstDap.getPortId())) {
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

		// Destination address of client's request to set in the outbound actions
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IPacket pkt = eth.getPayload(); 
		IPv4 ip_pkt = (IPv4) pkt;

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
					topologyService.getClusterId(srcDap.getNodeId());
			DatapathId dstCluster = 
					topologyService.getClusterId(dstDap.getNodeId());

			int srcVsDest = srcCluster.compareTo(dstCluster);
			if (srcVsDest == 0) {
				if (!srcDap.equals(dstDap) && 
						(srcCluster != null) && 
						(dstCluster != null)) {
					Path routeIn = 
							routingEngineService.getPath(srcDap.getNodeId(),
									srcDap.getPortId(),
									dstDap.getNodeId(),
									dstDap.getPortId());
					Path routeOut = 
							routingEngineService.getPath(dstDap.getNodeId(),
									dstDap.getPortId(),
									srcDap.getNodeId(),
									srcDap.getPortId());

					// use static flow entry pusher to push flow mod along in and out path
					// in: match src client (ip, port), rewrite dest from vip ip/port to member ip/port, forward
					// out: match dest client (ip, port), rewrite src from member ip/port to vip ip/port, forward

					if (! routeIn.getPath().isEmpty()) {
						pushStaticVipRoute(true, routeIn, client, member, sw, ip_pkt.getDestinationAddress());
					}

					if (! routeOut.getPath().isEmpty()) {
						pushStaticVipRoute(false, routeOut, client, member, sw, ip_pkt.getDestinationAddress());
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
	 * @param Path route
	 * @param IPClient client
	 * @param LBMember member
	 * @param long pinSwitch
	 */
	public void pushStaticVipRoute(boolean inBound, Path route, IPClient client, LBMember member, IOFSwitch pinSwitch, IPv4Address destAddress) {

		List<NodePortTuple> path = route.getPath();
		if (path.size() > 0) {
			for (int i = 0; i < path.size(); i+=2) {
				DatapathId sw = path.get(i).getNodeId();
				String entryName;
				Match.Builder mb = pinSwitch.getOFFactory().buildMatch();
				ArrayList<OFAction> actions = new ArrayList<>();

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
							actions.add(pinSwitch.getOFFactory().actions().setNwSrc(destAddress));
							actions.add(pinSwitch.getOFFactory().actions().output(path.get(i+1).getPortId(), Integer.MAX_VALUE));
						} else { // OXM introduced in OF1.2								
							actions.add(pinSwitch.getOFFactory().actions().setField(pinSwitch.getOFFactory().oxms().ethSrc(vips.get(member.vipId).proxyMac)));
							actions.add(pinSwitch.getOFFactory().actions().setField(pinSwitch.getOFFactory().oxms().ipv4Src(destAddress)));
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
				counterPacketOut.increment();
				sfpService.addFlow(entryName, fmb.build(), sw);
				Pair<Match, DatapathId> pair = new Pair<>(mb.build(), sw);
				flowToVipId.put(pair, member.vipId); // used to set LBPool statistics
			}
		}

		return;
	}



	/** periodical function for health monitors
	 * Get Port Desc message from statistics collection, according to isEnabled? parameter
	 * check if the port connected to the LBMember is up or down
	 * if it is down, then change the status of the LBMember to down.
	 * if it is up, then send ICMP request to further investigate member connectivity.
	 */
	private class healthMonitorsCheck implements Runnable {
		@Override
		public void run() {
			Map<NodePortTuple, PortDesc> portDesc = new HashMap<>();
			if(statisticsService != null){
				statisticsService.collectStatistics(true);
				portDesc = statisticsService.getPortDesc();

				if(vips != null && monitors != null && members != null && pools != null){
					for(LBMonitor monitor: monitors.values()){
						if(monitor.poolId != null && pools.get(monitor.poolId) != null){ 
							LBPool pool = pools.get(monitor.poolId);
							collectSwitchPortBandwidth(pool);
							if(pool.vipId != null && vips.containsKey(pool.vipId) && !memberIdToSwitchPort.isEmpty()){
								for(NodePortTuple allNpts: portDesc.keySet()){
									for(String memberId: pool.members){
										SwitchPort sp = memberIdToSwitchPort.get(memberId);		
										if(sp !=null){
											NodePortTuple memberNpt = new NodePortTuple(sp.getNodeId(),sp.getPortId());
											if(portDesc.get(allNpts).isUp()){
												if(memberNpt.equals(allNpts)){
													members.get(memberId).status=0;
													vipMembersHealthCheck(memberNpt,members.get(memberId).macString,
															IPv4Address.of(members.get(memberId).address) ,monitor.type,pool.vipId);
												}
											} else {
												if(memberNpt.equals(allNpts)){
													members.get(memberId).status = -1;
													log.warn("Member " + memberId + " has been determined inactive by the health monitor");
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/** Send ICMP requests to the switches connected to the members.
	 * Response will come as packet in, if they are available.
	 */
	private void vipMembersHealthCheck(NodePortTuple npt, String destMac, IPv4Address destAddr, byte msgType,String vipId){
		IOFSwitch theSW = switchService.getSwitch(npt.getNodeId());

		if(msgType == IpProtocol.ICMP.getIpProtocolNumber()){
			/* The VIP ID is passed in the ICMP payload, because when the ICMP packet in reaches the controller from the member
			 the controller knows which member sent the ICMP through its IP and VIP ID */
			String icmp_data = vipId;

			// 4 bytes is the minimum payload for ICMP packet
			while(icmp_data.length() < ICMP_PAYLOAD_LENGTH){
				icmp_data += "a";
			}
			byte[] icmp_data_byte = icmp_data.getBytes(StandardCharsets.UTF_8);

			IPacket icmpRequest = new Ethernet()
					.setSourceMACAddress(vips.get(vipId).proxyMac)
					.setDestinationMACAddress(destMac)
					.setEtherType(EthType.IPv4)
					.setVlanID((short) 0)
					.setPriorityCode((byte) 0)
					.setPayload(
							new IPv4()
							.setSourceAddress(IPv4Address.of(vips.get(vipId).address))
							.setDestinationAddress(destAddr)
							.setProtocol(IpProtocol.ICMP)
							.setTtl((byte) 64)
							.setPayload(new ICMP()
									.setIcmpCode((byte) 0)
									.setIcmpType((byte) 8)
									.setPayload(new Data()
											.setData(icmp_data_byte)								
											)));

			FloodlightContext cntx = null;
			pushPacket(icmpRequest, theSW, OFBufferId.NO_BUFFER, OFPort.CONTROLLER, npt.getPortId(), cntx, true);
		}
	}

	/** Periodical function to set LBPool statistics
	 * Gets the statistics through StatisticsCollector and sets it in LBPool
	 */
	private class SetPoolStats implements Runnable {
		@Override
		public void run() {
			if(!pools.isEmpty()){
				if(!flowToVipId.isEmpty()){
					for(LBPool pool: pools.values()){
						collectSwitchPortBandwidth(pool);
						FlowRuleStats frs = null;
						ArrayList<Long> bytesOut = new ArrayList<>();
						ArrayList<Long> bytesIn = new ArrayList<>();
						for(Pair<Match,DatapathId> pair: flowToVipId.keySet()){ // from the flows set from the load balancer
							if(flowToVipId.get(pair).equals(pool.vipId)){ // determine which vip is responsible for the flow
								frs = statisticsService.getFlowStats().get(pair); // get the statistics of this flow
								if(frs != null){
									Set<DatapathId> membersDPID = new HashSet<>();
									for(SwitchPort sp: memberIdToSwitchPort.values()){	
										membersDPID.add(sp.getNodeId());
									}
									if(membersDPID.contains(pair.getValue())){ // if switch is connected to a member
										bytesIn.add(frs.getByteCount().getValue());
									} else 
										bytesOut.add(frs.getByteCount().getValue());
								}
							}
						}
						pool.setPoolStatistics(bytesIn,bytesOut,flowToVipId.size()); 
					}
				}
			}
		}
	}

	/**
	 * used to collect SwitchPortBandwidth of the members and map members to DPIDs and helper function health monitors and pool stats.
	 * LBPool pool is used to iterate over its members, to avoid iterating over all the members in the network.
	 * as some pools might not have monitors associated with.
	 */
	public HashMap<String, U64> collectSwitchPortBandwidth(LBPool pool){
		HashMap<String,U64> memberPortBandwidth = new HashMap<>();
		HashMap<Pair<IDevice,String>,String> deviceToMemberId = new HashMap<>();

		// retrieve all known devices to know which ones are attached to the members
		Collection<? extends IDevice> allDevices = deviceManagerService.getAllDevices();

		for (IDevice d : allDevices) {
			for (int j = 0; j < d.getIPv4Addresses().length; j++) {
				if(pool != null){
					for(String memberId: pool.members){
						if (members.get(memberId).address == d.getIPv4Addresses()[j].getInt()){
							Pair<IDevice,String> pair = new Pair<>(d, pool.id);
							members.get(memberId).macString = d.getMACAddressString(); // because health monitors have to know members MAC
							deviceToMemberId.put(pair, memberId);
						}
					}
				}
			}
		}
		// collect statistics of the switch ports attached to the members
		if(!deviceToMemberId.isEmpty() && statisticsService != null){
			for(Pair<IDevice, String> membersDevice: deviceToMemberId.keySet()){
				String memberId = deviceToMemberId.get(membersDevice);
				for(SwitchPort dstDap: membersDevice.getKey().getAttachmentPoints()){
					SwitchPortBandwidth bandwidthOfPort = statisticsService.getBandwidthConsumption(dstDap.getNodeId(), dstDap.getPortId());
					if(bandwidthOfPort != null) // needs time for 1st collection, this avoids nullPointerException 
						memberPortBandwidth.put(memberId, bandwidthOfPort.getBitsPerSecondRx());
					memberIdToSwitchPort.put(memberId, dstDap);
				}
			}
		}
		return memberPortBandwidth;
	}

	/*
	 * ILoadBalancerService methods
	 */

	@Override
	public Collection<LBVip> listVips() {
		return vips.values();
	}

	@Override
	public Collection<LBVip> listVip(String vipId) {
		Collection<LBVip> result = new HashSet<>();
		result.add(vips.get(vipId));
		return result;
	}

	@Override
	public LBVip createVip(LBVip vip) {
		if (vip == null)
			vip = new LBVip();

		vips.put(vip.id, vip);
		vipIpToId.put(vip.address, vip.id);
		vipIpToMac.put(IPv4Address.of(vip.address), vip.proxyMac);

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
		Collection<LBPool> result = new HashSet<>();
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
			log.error("Specified vip-id must exist, creating pool with null vipId anyway");
			pool.vipId = null;
			pools.put(pool.id, pool);
		}
		return pool;
	}

	@Override
	public LBPool updatePool(LBPool pool) {
		pools.put(pool.id, pool);
		return pool;
	}

	@Override
	public int removePool(String poolId) {
		LBPool pool;
		if (pools != null) {
			pool = pools.get(poolId);
			if (pool == null)	// fix dereference violations
				return -1;
			if (pool.vipId != null && vips.containsKey(pool.vipId))
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
		Collection<LBMember> result = new HashSet<>();
		result.add(members.get(memberId));
		return result;
	}

	@Override
	public Collection<LBMember> listMembersByPool(String poolId) {
		Collection<LBMember> result = new HashSet<>();

		if(pools.containsKey(poolId)) {
			ArrayList<String> memberIds = pools.get(poolId).members;
			if(memberIds !=null && members != null){
				for (int i = 0; i<memberIds.size(); i++)
					result.add(members.get(memberIds.get(i)));
			}
		}
		return result;
	}

	@Override
	public LBMember createMember(LBMember member) {
		if (member == null)
			member = new LBMember();

		if (member.poolId != null && pools.get(member.poolId) != null && pools.get(member.poolId).vipId !=null) {
			member.vipId = pools.get(member.poolId).vipId;
			if (!pools.get(member.poolId).members.contains(member.id))
				pools.get(member.poolId).members.add(member.id);
		} else{
			log.error("Member must be specified with existing pool_id");
			return null;
		}
		members.put(member.id, member);
		memberIdToIp.put(member.id, member.address);
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
			if (member.poolId != null && pools.containsKey(member.poolId))
				pools.get(member.poolId).members.remove(memberId);
			members.remove(memberId);
			memberIdToIp.remove(memberId);
			return 0;
		} else {
			return -1;
		}    
	}

	@Override
	public int setMemberWeight(String memberId, String weight){
		LBMember member;
		short value;
		member = members.get(memberId);

		try{
			value = Short.parseShort(weight);
		} catch(Exception e){
			log.error("Invalid value for member weight " + e.getMessage());
			return -1;
		}
		if(member != null && (value <= 10 && value >= 1)){
			member.weight = value;
			return 0;
		}
		return -1;
	}

	@Override
	public int setPriorityToMember(String poolId ,String memberId){
		if(pools.containsKey(poolId)) {
			ArrayList<String> memberIds = pools.get(poolId).members;
			if(memberIds !=null && members != null && memberIds.contains(memberId)){
				for (int i = 0; i<memberIds.size(); i++){
					if(members.get(memberIds.get(i)).id.equals(memberId)){
						members.get(memberId).weight=(short) (1 + memberIds.size()/2);
					}else
						members.get(memberIds.get(i)).weight=1;
				}
				return 0;
			}
		}
		return -1;
	}

	@Override
	public LBStats getPoolStats(String poolId){		
		if(pools != null && pools.containsKey(poolId)){
			LBStats pool_stats = pools.get(poolId).poolStats;
			if(pool_stats != null)
				return pool_stats.getStats();
		}
		return null;
	}

	@Override
	public Collection<LBMonitor> listMonitors() {
		return monitors.values();
	}

	@Override
	public Collection<LBMonitor> listMonitor(String monitorId) {
		Collection<LBMonitor> result = new HashSet<>();
		result.add(monitors.get(monitorId));
		return result;
	}

	@Override
	public Collection<LBMonitor> listMonitorsByPool(String poolId){
		Collection<LBMonitor> result = new HashSet<>();

		if(pools.containsKey(poolId)) {
			LBPool pool  = pools.get(poolId);
			if(pool.monitors != null && monitors !=null){
				for(String monitorId : pool.monitors)
					result.add(monitors.get(monitorId));
			}
		}
		return result;
	}

	@Override
	public LBMonitor createMonitor(LBMonitor monitor) {
		if (monitor == null)
			monitor = new LBMonitor();

		monitors.put(monitor.id, monitor);
		if(monitor.poolId != null){
			log.error("To associate a monitor with a pool, use associateMonitorWithPool function");
			monitor.poolId = null;
		}
		return monitor;
	}

	@Override
	public LBMonitor updateMonitor(LBMonitor monitor) {
		if(!monitors.values().isEmpty()){
			for(LBMonitor allMonitors: monitors.values()){
				if(monitor.poolId.equals(allMonitors.poolId)){
					log.error("Pool already has monitor associated with");
					return null;
				}
			}
			monitors.put(monitor.id, monitor);
			return monitor;
		}
		log.error("Monitor does not exist!");
		return null;
	}


	@Override
	public Collection<LBMonitor> associateMonitorWithPool(String poolId,LBMonitor monitor) {
		Collection<LBMonitor> result = new HashSet<>();

		// If monitor does not exist, it is created.
		if (monitor == null){
			monitor = new LBMonitor();
		}

		for(LBMonitor allMonitors: monitors.values()){
			if(Objects.equals(poolId, allMonitors.poolId)){
				log.error("Pool " + poolId + " already has monitor associated with");
				return null;
			}
		}

		if(pools.get(poolId) !=null){
			monitors.put(monitor.id, monitor);
			pools.get(poolId).monitors.add(monitor.id);
			monitor.poolId = poolId;

			// in case monitor is associated a second time without dissociating first
			ArrayList<String> monitorsInWrongPool = new ArrayList<>();
			for(String monitorId: pools.get(poolId).monitors){
				if(!Objects.equals(monitors.get(monitorId).poolId, poolId)){
					monitorsInWrongPool.add(monitorId); 	

				} else{
					result.add(monitors.get(monitorId));	
				}
			}
			if(monitorsInWrongPool !=null){
				for(String monitorId: monitorsInWrongPool){
					pools.get(poolId).monitors.remove(monitorId);
				}
			}
			return result;
		}
		return result;
	}

	@Override
	public int dissociateMonitorWithPool(String poolId,String monitorId) {
		LBPool pool;
		LBMonitor monitor;

		pool = pools.get(poolId);
		monitor = monitors.get(monitorId);

		if(pool !=null && monitor !=null && pool.monitors.contains(monitorId)){
			pool.monitors.remove(monitorId);
			monitor.poolId = null;
			return 0;
		}else{
			return -1;
		}
	}


	@Override
	public int removeMonitor(String monitorId) {
		LBMonitor monitor;
		monitor = monitors.get(monitorId);

		if(monitor != null){
			if(monitor.poolId != null && pools.containsKey(monitor.poolId))
				pools.get(monitor.poolId).monitors.remove(monitorId);
			monitors.remove(monitorId);
			return 0;
		} else {
			return -1;
		}    
	}

	@Override
	public int healthMonitoring(boolean monitor) {
		if(monitor && !isMonitoringEnabled){
			healthMonitoring = threadService.getScheduledExecutor().scheduleAtFixedRate(new healthMonitorsCheck(), healthMonitorsInterval, healthMonitorsInterval, TimeUnit.SECONDS);
			isMonitoringEnabled = true;
			log.warn("Health monitoring thread started");
			return 0;
		} else if(!monitor && isMonitoringEnabled){
			if (!healthMonitoring.cancel(false)) {
				log.error("Could not cancel health monitoring thread");
				return -1;
			} else {
				log.warn("Health monitoring thread stopped");
				isMonitoringEnabled = false;
				return 0;
			}
		}
		return 0;
	}

	@Override
	public String getMonitorsPeriod() {
		return "{\"status\" : \"Monitors' period is " + healthMonitorsInterval + " seconds \"}";
	}
	
	
	@Override
	public String setMonitorsPeriod(int period) {
		healthMonitorsInterval = period;
		return "{\"status\" : \"Monitors period changed to " + period + " seconds \"}";
	}
	
	
	@Override
	public String clearAllLb() {
		// Clear all LB objects
		monitors.clear();
		members.clear();
		pools.clear();
		vips.clear();
		return "{\"status\" : \"All Vips, Pools, Members and Monitors have been deleted \"}";
	}

	/*
	 * Floodlight module dependencies
	 */

	@Override
	public Collection<Class<? extends IFloodlightService>>
	getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<>();
		l.add(ILoadBalancerService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<>();
		m.put(ILoadBalancerService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>>
	getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		l.add(IOFSwitchService.class);
		l.add(IDeviceService.class);
		l.add(IDebugCounterService.class);
		l.add(ITopologyService.class);
		l.add(IRoutingService.class);
		l.add(IStaticEntryPusherService.class);
		l.add(IStatisticsService.class);

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
		sfpService = context.getServiceImpl(IStaticEntryPusherService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		statisticsService = context.getServiceImpl(IStatisticsService.class);
		threadService = context.getServiceImpl(IThreadPoolService.class);

		vips = new HashMap<>();
		pools = new HashMap<>();
		members = new HashMap<>();
		monitors = new HashMap<>();
		vipIpToId = new HashMap<>();
		memberStatus = new HashMap<>();
		vipIpToMac = new HashMap<>();
		memberIdToIp= new HashMap<>();
		flowToVipId = new HashMap<>();
		memberIdToSwitchPort= new HashMap<>();

		threadService.getScheduledExecutor().scheduleAtFixedRate(new SetPoolStats(), flowStatsInterval, flowStatsInterval, TimeUnit.SECONDS);

	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		restApiService.addRestletRoutable(new LoadBalancerWebRoutable());
		debugCounterService.registerModule(this.getName());
		counterPacketOut = debugCounterService.registerCounter(this.getName(), "packet-outs-written", "Packet outs written by the LoadBalancer", MetaData.WARN);
		counterPacketIn = debugCounterService.registerCounter(this.getName(), "packet-ins-received", "Packet ins received by the LoadBalancer", MetaData.WARN);
	}
}
