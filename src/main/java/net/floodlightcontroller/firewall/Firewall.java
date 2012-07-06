package net.floodlightcontroller.firewall;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Firewall implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected IRoutingService routingEngine;
	protected static Logger logger;
	protected ArrayList<FirewallRule> rules;
	
	
	@Override
	public String getName() {
		return "firewall";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		    l.add(IFloodlightProviderService.class);
		    return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    rules = new ArrayList<FirewallRule>();
	    logger = LoggerFactory.getLogger(Firewall.class);
	    
	    // insert rule to allow ICMP traffic
	    FirewallRule rule = new FirewallRule();
	    rule.proto_type = "ICMP";
	    this.rules.add(rule);
	    // insert rule to allow TCP traffic destined to port 80
	    rule = new FirewallRule();
	    rule.proto_type = "TCP";
	    rule.proto_dstport = "80";
	    this.rules.add(rule);
	    // insert rule to allow TCP traffic originating from port 80
	    rule = new FirewallRule();
	    rule.proto_type = "TCP";
	    rule.proto_srcport = "80";
	    this.rules.add(rule);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
	        case PACKET_IN:
	            IRoutingDecision decision = null;
	            if (cntx != null) {
	            	decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
	
	            	return this.processPacketInMessage(sw,
	                                               (OFPacketIn) msg,
	                                               decision,
	                                               cntx);
	            }
		}
		
        return Command.CONTINUE;
	}
	
	protected FirewallRule matchWithRule(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		FirewallRule matched_rule = null;
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IPacket pkt = (IPacket) eth.getPayload();
		IPv4 pkt_ip = null;
		TCP pkt_tcp = null;
		UDP pkt_udp = null;
		ICMP pkt_icmp = null;
		int proto_src = -1;
		int proto_dst = -1;
		
		if (!(pkt instanceof IPv4)) {
			return null;
		}
		pkt_ip = (IPv4) pkt;
		if (pkt_ip.getPayload() instanceof TCP) {
			pkt_tcp = (TCP)pkt_ip.getPayload();
			proto_src = pkt_tcp.getSourcePort() & 0xffff;
			proto_dst = pkt_tcp.getDestinationPort() & 0xffff;
		} else if (pkt_ip.getPayload() instanceof UDP) {
			pkt_udp = (UDP)pkt_ip.getPayload();
			proto_src = pkt_udp.getSourcePort() & 0xffff;
			proto_dst = pkt_udp.getDestinationPort() & 0xffff;
		} else if (pkt_ip.getPayload() instanceof ICMP) {
			pkt_icmp = (ICMP)pkt_ip.getPayload();
		}
		
		Iterator<FirewallRule> iter = this.rules.iterator();
		
		FirewallRule rule = null;
		// iterate through list to find a matching firewall rule
		while (iter.hasNext()) {
			// get next rule from list
			rule = iter.next();
			
			// now perform matching
			
			// switchID matches?
			if (rule.switchid.length() > 0 && rule.switchid.equalsIgnoreCase(sw.getStringId()) == false) continue;
			
			// inport matches?
			if (rule.src_inport != -1 && rule.src_inport != (int)pi.getInPort()) continue;
			
			// mac address (src and dst) match?
			if (rule.src_mac != "ANY" && rule.src_mac.equalsIgnoreCase(eth.getSourceMAC().toString()) == false) continue;
			if (rule.dst_mac != "ANY" && rule.dst_mac.equalsIgnoreCase(eth.getDestinationMAC().toString()) == false) continue;
			
			// protocol type matches?
			if (!rule.proto_type.equals("ANY")) {
				if (rule.proto_type.equals("TCP") && pkt_tcp == null) continue;
				if (rule.proto_type.equals("UDP") && pkt_udp == null) continue;
				if (rule.proto_type.equals("ICMP") && pkt_icmp == null) continue;
			}
			
			// ip addresses (src and dst) match?
			/*
			if (rule.src_ip.equals("ANY") == false) {
				String[] ipparts = rule.src_ip.split("/");
				String rule_ipaddr = ipparts[0].trim();
				int rule_iprng = 32;
				if (ipparts.length == 2) {
					try {
						rule_iprng = Integer.parseInt(ipparts[1].trim());
					} catch (Exception exp) {
						rule_iprng = 32;
					}
				}
				int rule_ipint = IPv4.toIPv4Address(rule_ipaddr);
				int pkt_ipint = pkt_ip.getSourceAddress();
				if (rule_iprng < 32) {
					rule_ipint = rule_ipint >> rule_iprng;
					pkt_ipint = pkt_ipint >> rule_iprng;
					rule_ipint = rule_ipint << rule_iprng;
					pkt_ipint = pkt_ipint << rule_iprng;
				}
				//byte[] rule_ipbytes = ByteBuffer.allocate(4).putInt(rule_ipint).array();
				//byte[] pkt_ipbytes = ByteBuffer.allocate(4).putInt(pkt_ip.getSourceAddress()).array();
				//for (int i = 0; i < Math.floor(rule_iprng/8); i++) {
				//	//
				//}
			}
			*/
			
			if (pkt_tcp != null || pkt_udp != null) {
				int val = Integer.parseInt(rule.proto_srcport);
				if (val != -1 && val != proto_src) continue;
				val = Integer.parseInt(rule.proto_dstport);
				if (val != -1 && val != proto_dst) continue;
			}
			
			// match found - i.e. no "continue" statement above got executed
			matched_rule = rule;
			break;
		}
		
		return matched_rule;
	}
	
	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		IPacket pkt = (IPacket) eth.getPayload();
		if (!(pkt instanceof IPv4)) {
			// Not an IP packet, Firewall only works for IP packets, so simply ignore
			logger.info("not an IP packet, etherType {}, let it go!", eth.getEtherType() & 0xffff);
			return Command.CONTINUE;
		}
		/*
		IPv4 ipkt = (IPv4)pkt;
		int intaddr = ipkt.getSourceAddress();
		int intaddr2 = intaddr >> 8;
		intaddr2 = intaddr2 << 8;
		logger.info("IP1 {}, IP2 {}", intaddr, intaddr2);
		*/
		
		// check if we have a matching rule for this packet/flow
		// and no decision is taken yet
		if (decision == null) {
			FirewallRule rule = this.matchWithRule(sw, pi, cntx);
			if (rule == null) {
				decision = new FirewallDecision(IRoutingDecision.RoutingAction.DROP);
				decision.setWildcards(OFMatch.OFPFW_ALL
						& ~OFMatch.OFPFW_DL_SRC
	                    & ~OFMatch.OFPFW_IN_PORT
	                    & ~OFMatch.OFPFW_DL_VLAN
	                    & ~OFMatch.OFPFW_DL_DST
	                    & ~OFMatch.OFPFW_DL_TYPE
	                    & ~OFMatch.OFPFW_NW_PROTO
	                    & ~OFMatch.OFPFW_TP_SRC
	                    & ~OFMatch.OFPFW_NW_SRC_ALL
	                    & ~OFMatch.OFPFW_NW_DST_ALL);
				decision.addToContext(cntx);
				logger.info("no firewall rule found to allow this packet/flow, blocking packet/flow");
			} else {
				logger.info("rule matched, allowing traffic");
			}
		}
        
        return Command.CONTINUE;
    }

}
