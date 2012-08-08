package net.floodlightcontroller.firewall;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;


public class Firewall implements IFirewallService, IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected IRoutingService routingEngine;
	protected IStorageSourceService storageSource;
    protected IRestApiService restApi;
	protected static Logger logger;
	protected ArrayList<FirewallRule> rules;
	protected boolean enabled;
	
	public static final String TABLE_NAME = "controller_firewallrules";
    public static final String COLUMN_RULEID = "ruleid";
    public static final String COLUMN_SWITCHID = "switchid";
    public static final String COLUMN_SRC_INPORT = "src_inport";
    public static final String COLUMN_SRC_IP_PREFIX = "src_ip_prefix";
    public static final String COLUMN_SRC_IP_BITS = "src_ip_bits";
    public static final String COLUMN_SRC_MAC = "src_mac";
    public static final String COLUMN_PROTO_TYPE = "proto_type";
    public static final String COLUMN_PROTO_SRCPORT = "proto_srcport";
    public static final String COLUMN_PROTO_DSTPORT = "proto_dstport";
    public static final String COLUMN_DST_IP_PREFIX = "dst_ip_prefix";
    public static final String COLUMN_DST_IP_BITS = "dst_ip_bits";
    public static final String COLUMN_DST_MAC = "dst_mac";
    public static final String COLUMN_WILDCARD_SWITCHID = "wildcard_switchid";
    public static final String COLUMN_WILDCARD_SRC_INPORT = "wildcard_src_inport";
    public static final String COLUMN_WILDCARD_SRC_MAC = "wildcard_src_mac";
    public static final String COLUMN_WILDCARD_SRC_IP = "wildcard_src_ip";
    public static final String COLUMN_WILDCARD_PROTO_TYPE = "wildcard_proto_type";
    public static final String COLUMN_WILDCARD_DST_MAC = "wildcard_dst_mac";
    public static final String COLUMN_WILDCARD_DST_IP = "wildcard_dst_ip";
    public static final String COLUMN_PRIORITY = "priority";
    public static final String COLUMN_IS_DENYRULE = "is_denyrule";
    public static String ColumnNames[] = { COLUMN_RULEID, COLUMN_SWITCHID,
    	    COLUMN_SRC_INPORT, COLUMN_SRC_MAC, COLUMN_SRC_IP_PREFIX, COLUMN_SRC_IP_BITS,
    	    COLUMN_PROTO_TYPE, COLUMN_PROTO_SRCPORT, COLUMN_PROTO_DSTPORT,
    	    COLUMN_DST_MAC, COLUMN_DST_IP_PREFIX, COLUMN_DST_IP_BITS,
    	    COLUMN_WILDCARD_SWITCHID, COLUMN_WILDCARD_SRC_INPORT, COLUMN_WILDCARD_SRC_MAC,
    	    COLUMN_WILDCARD_SRC_IP, COLUMN_WILDCARD_PROTO_TYPE, COLUMN_WILDCARD_DST_MAC,
    	    COLUMN_WILDCARD_DST_IP, COLUMN_PRIORITY, COLUMN_IS_DENYRULE };
	
	
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
		return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFirewallService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        // We are the class that implements the service
        m.put(IFirewallService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		    l.add(IFloodlightProviderService.class);
		    l.add(IStorageSourceService.class);
	        l.add(IRestApiService.class);
		    return l;
	}
	
	public IFloodlightProviderService getFloodlightProvider() {
        return floodlightProvider;
    }

    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    public void setStorageSource(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }
    
    protected ArrayList<FirewallRule> readRulesFromStorage() {
    	ArrayList<FirewallRule> l = new ArrayList<FirewallRule>();
    	
        try {
            Map<String, Object> row;
            // null1=no predicate, null2=no ordering
            IResultSet resultSet = storageSource.executeQuery(TABLE_NAME,
                    ColumnNames, null, null);
            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                row = it.next().getRow();
                // now, parse row
                FirewallRule r = new FirewallRule();
                if (!row.containsKey(COLUMN_RULEID) || !row.containsKey(COLUMN_SWITCHID)) {
                    logger.error(
                            "skipping entry with missing required 'ruleid' or 'switchid' entry: {}",
                            row);
                    return l;
                }
                // most error checking done with ClassCastException
                try {
                    // first, snag the required entries, for debugging info
                	r.ruleid = (String)row.get(COLUMN_RULEID);
                    r.switchid = Long.parseLong((String)row.get(COLUMN_SWITCHID));
                    
                    for (String key : row.keySet()) {
                        if (row.get(key) == null)
                            continue;
                        if ( key.equals(COLUMN_RULEID) || key.equals(COLUMN_SWITCHID) || key.equals("id")) {
                            continue; // already handled
                        } else if ( key.equals(COLUMN_SRC_INPORT)) {
                        	r.src_inport = Short.parseShort((String)row.get(COLUMN_SRC_INPORT));
                        } else if ( key.equals(COLUMN_SRC_MAC)) {
                        	r.src_mac = Long.parseLong((String)row.get(COLUMN_SRC_MAC));
                        } else if ( key.equals(COLUMN_SRC_IP_PREFIX)) {
                        	r.src_ip_prefix = Integer.parseInt((String)row.get(COLUMN_SRC_IP_PREFIX));
                        } else if ( key.equals(COLUMN_SRC_IP_BITS)) {
                        	r.src_ip_bits = Integer.parseInt((String)row.get(COLUMN_SRC_IP_BITS));
                        } else if ( key.equals(COLUMN_PROTO_TYPE)) {
                        	r.proto_type = Short.parseShort((String)row.get(COLUMN_PROTO_TYPE));
                        } else if ( key.equals(COLUMN_PROTO_SRCPORT)) {
                        	r.proto_srcport = Short.parseShort((String)row.get(COLUMN_PROTO_SRCPORT));
                        } else if ( key.equals(COLUMN_PROTO_DSTPORT)) {
                        	r.proto_dstport = Short.parseShort((String)row.get(COLUMN_PROTO_DSTPORT));
                        } else if ( key.equals(COLUMN_DST_MAC)) {
                        	r.dst_mac = Long.parseLong((String)row.get(COLUMN_DST_MAC));
                        } else if ( key.equals(COLUMN_DST_IP_PREFIX)) {
                        	r.dst_ip_prefix = Integer.parseInt((String)row.get(COLUMN_DST_IP_PREFIX));
                        } else if ( key.equals(COLUMN_DST_IP_BITS)) {
                        	r.dst_ip_bits = Integer.parseInt((String)row.get(COLUMN_DST_IP_BITS));
                        } else if ( key.equals(COLUMN_WILDCARD_SWITCHID)) {
                        	r.wildcard_switchid = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SWITCHID));
                        } else if ( key.equals(COLUMN_WILDCARD_SRC_INPORT)) {
                        	r.wildcard_src_inport = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SRC_INPORT));
                        } else if ( key.equals(COLUMN_WILDCARD_SRC_MAC)) {
                        	r.wildcard_src_mac = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SRC_MAC));
                        } else if ( key.equals(COLUMN_WILDCARD_SRC_IP)) {
                        	r.wildcard_src_ip = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SRC_IP));
                        } else if ( key.equals(COLUMN_WILDCARD_PROTO_TYPE)) {
                        	r.wildcard_proto_type = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_PROTO_TYPE));
                        } else if ( key.equals(COLUMN_WILDCARD_DST_MAC)) {
                        	r.wildcard_dst_mac = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_DST_MAC));
                        } else if ( key.equals(COLUMN_WILDCARD_DST_IP)) {
                        	r.wildcard_dst_ip = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_DST_IP));
                        } else if ( key.equals(COLUMN_PRIORITY)) {
                        	r.priority = Integer.parseInt((String)row.get(COLUMN_PRIORITY));
                        } else if ( key.equals(COLUMN_IS_DENYRULE)) {
                        	r.is_denyrule = Boolean.parseBoolean((String)row.get(COLUMN_IS_DENYRULE));
                        }
                    }
                } catch (ClassCastException e) {
                    if (!r.ruleid.equals(""))
                        logger.error(
                                "skipping rule {} with bad data : "
                                        + e.getMessage(), r.ruleid);
                    else
                        logger.error("skipping rule with bad data: {} :: {} ",
                                e.getMessage(), e.getStackTrace());
                }
                l.add(r);
            }
        } catch (StorageException e) {
            logger.error("failed to access storage: {}", e.getMessage());
            // if the table doesn't exist, then wait to populate later via
            // setStorageSource()
        }
        
        // now, sort the list based on priorities
        Collections.sort(l);
    	
    	return l;
    }
    
    /**
     * used for debugging and unittests
     * @return the number of rules
     */
    public int countRules() {
        return this.rules.size();
    }

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		storageSource = context.getServiceImpl(IStorageSourceService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		rules = new ArrayList<FirewallRule>();
	    logger = LoggerFactory.getLogger(Firewall.class);
	    // start disabled
	    enabled = false;
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// initialize REST interface
        restApi.addRestletRoutable(new FirewallWebRoutable());
		// start firewall if enabled at bootup
        if (this.enabled == true) {
			floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		}
        // storage, create table and read rules
        storageSource.createTable(TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_RULEID);
        this.rules = readRulesFromStorage();
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if (this.enabled == false) return Command.CONTINUE;
		
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
	
	@Override
	public void enableFirewall() {
		// check if the firewall module is not listening for events, if not, then start listening (enable it)
		List<IOFMessageListener> listeners = floodlightProvider.getListeners().get(OFType.PACKET_IN);
		if (listeners != null && listeners.contains(this) == false) {
			// enable firewall, i.e. listen for packet-in events
			floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		}
		this.enabled = true;
	}
	
	@Override
	public void disableFirewall() {
		// check if the firewall module is listening for events, if yes, then remove it from listeners (disable it)
		List<IOFMessageListener> listeners = floodlightProvider.getListeners().get(OFType.PACKET_IN);
		if (listeners != null && listeners.contains(this) == true) {
			// disable firewall, i.e. stop listening for packet-in events
			floodlightProvider.removeOFMessageListener(OFType.PACKET_IN, this);
		}
		this.enabled = false;
	}
	
	@Override
	public List<FirewallRule> getRules() {
		return this.rules;
	}
	
	@Override
	public List<Map<String, Object>> getStorageRules() {
		ArrayList<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
		try {
			// null1=no predicate, null2=no ordering
	        IResultSet resultSet = storageSource.executeQuery(TABLE_NAME, ColumnNames, null, null);
	        for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
	        	l.add(it.next().getRow());
	        }
		} catch (StorageException e) {
            logger.error("failed to access storage: {}", e.getMessage());
            // if the table doesn't exist, then wait to populate later via
            // setStorageSource()
        }
        return l;
	}
	
	@Override
	public void addRule(FirewallRule rule) {
		rule.ruleid = UUID.randomUUID().toString();
		this.rules.add(rule);
		// now re-sort the rules
	    Collections.sort(this.rules);
	    // add rule to database
	    Map<String, Object> entry = new HashMap<String, Object>();
	    entry.put(COLUMN_RULEID, rule.ruleid);
	    entry.put(COLUMN_SWITCHID, Long.toString(rule.switchid));
	    entry.put(COLUMN_SRC_INPORT, Short.toString(rule.src_inport));
	    entry.put(COLUMN_SRC_MAC, Long.toString(rule.src_mac));
	    entry.put(COLUMN_SRC_IP_PREFIX, Integer.toString(rule.src_ip_prefix));
	    entry.put(COLUMN_SRC_IP_BITS, Integer.toString(rule.src_ip_bits));
	    entry.put(COLUMN_PROTO_TYPE, Short.toString(rule.proto_type));
	    entry.put(COLUMN_PROTO_SRCPORT, Integer.toString(rule.proto_srcport));
	    entry.put(COLUMN_PROTO_DSTPORT, Integer.toString(rule.proto_dstport));
	    entry.put(COLUMN_DST_MAC, Long.toString(rule.dst_mac));
	    entry.put(COLUMN_DST_IP_PREFIX, Integer.toString(rule.dst_ip_prefix));
	    entry.put(COLUMN_DST_IP_BITS, Integer.toString(rule.dst_ip_bits));
	    entry.put(COLUMN_WILDCARD_SWITCHID, Boolean.toString(rule.wildcard_switchid));
	    entry.put(COLUMN_WILDCARD_SRC_INPORT, Boolean.toString(rule.wildcard_src_inport));
	    entry.put(COLUMN_WILDCARD_SRC_MAC, Boolean.toString(rule.wildcard_src_mac));
	    entry.put(COLUMN_WILDCARD_SRC_IP, Boolean.toString(rule.wildcard_src_ip));
	    entry.put(COLUMN_WILDCARD_PROTO_TYPE, Boolean.toString(rule.wildcard_proto_type));
	    entry.put(COLUMN_WILDCARD_DST_MAC, Boolean.toString(rule.wildcard_dst_mac));
	    entry.put(COLUMN_WILDCARD_DST_IP, Boolean.toString(rule.wildcard_dst_ip));
	    entry.put(COLUMN_PRIORITY, Integer.toString(rule.priority));
	    entry.put(COLUMN_IS_DENYRULE, Boolean.toString(rule.is_denyrule));
	    storageSource.insertRowAsync(TABLE_NAME, entry);
	}
	
	@Override
	public void deleteRule(String ruleid) {
		boolean found = false;
		Iterator<FirewallRule> iter = this.rules.iterator();
		while (iter.hasNext()) {
			FirewallRule r = iter.next();
			if (r.ruleid.equalsIgnoreCase(ruleid)) {
				// found the rule, now remove it
				iter.remove();
				found = true;
				break;
			}
		}
		// now re-sort the rules if we deleted one
		if (found) {
			Collections.sort(this.rules);
		}
		// delete from database
		storageSource.deleteRowAsync(TABLE_NAME, ruleid);
	}
	
	protected List<Object> matchWithRule(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		FirewallRule matched_rule = null;
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IPacket pkt = (IPacket) eth.getPayload();
		IPv4 pkt_ip = null;
		TCP pkt_tcp = null;
		UDP pkt_udp = null;
		ICMP pkt_icmp = null;
		short proto_src = 0;
		short proto_dst = 0;
		int wildcards_drop = OFMatch.OFPFW_ALL;
		int wildcards_allow = OFMatch.OFPFW_ALL;
		

		if (pkt instanceof IPv4) {
			pkt_ip = (IPv4) pkt;
			if (pkt_ip.getPayload() instanceof TCP) {
				pkt_tcp = (TCP)pkt_ip.getPayload();
				proto_src = pkt_tcp.getSourcePort();
				proto_dst = pkt_tcp.getDestinationPort();
			} else if (pkt_ip.getPayload() instanceof UDP) {
				pkt_udp = (UDP)pkt_ip.getPayload();
				proto_src = pkt_udp.getSourcePort();
				proto_dst = pkt_udp.getDestinationPort();
			} else if (pkt_ip.getPayload() instanceof ICMP) {
				pkt_icmp = (ICMP)pkt_ip.getPayload();
			}
		}
		
		Iterator<FirewallRule> iter = this.rules.iterator();
		
		FirewallRule rule = null;
		// iterate through list to find a matching firewall rule
		while (iter.hasNext()) {
			// get next rule from list
			rule = iter.next();
			
			// now perform matching
			
			// switchID matches?
			if (rule.wildcard_switchid == false && rule.switchid != sw.getId()) continue;
			
			// inport matches?
			if (rule.wildcard_src_inport == false && rule.src_inport != pi.getInPort()) continue;
			if (rule.is_denyrule) {
				wildcards_allow &= ~OFMatch.OFPFW_IN_PORT;
			} else {
				wildcards_drop &= ~OFMatch.OFPFW_IN_PORT;
			}
			logger.info("switch inport matched");
			
			// mac address (src and dst) match?
			if (rule.wildcard_src_mac == false && rule.src_mac != eth.getSourceMAC().toLong()) continue;
			if (rule.is_denyrule) {
				wildcards_allow &= ~OFMatch.OFPFW_DL_SRC;
			} else {
				wildcards_drop &= ~OFMatch.OFPFW_DL_SRC;
			}
			if (rule.wildcard_dst_mac == false && rule.dst_mac != eth.getDestinationMAC().toLong()) continue;
			if (rule.is_denyrule) {
				wildcards_allow &= ~OFMatch.OFPFW_DL_DST;
			} else {
				wildcards_drop &= ~OFMatch.OFPFW_DL_DST;
			}
			logger.info("mac addresses matched");
			
			logger.info("Protocol: {}", pkt.getClass().getName());
			
			// protocol type matches?
			if (rule.wildcard_proto_type == false) {
				if (rule.proto_type == IPv4.PROTOCOL_TCP && pkt_tcp == null) continue;
				if (rule.proto_type == IPv4.PROTOCOL_UDP && pkt_udp == null) continue;
				if (rule.proto_type == IPv4.PROTOCOL_ICMP && pkt_icmp == null) continue;
				if (rule.is_denyrule) {
					wildcards_allow &= ~OFMatch.OFPFW_DL_TYPE;
					wildcards_allow &= ~OFMatch.OFPFW_NW_PROTO;
				} else {
					wildcards_drop &= ~OFMatch.OFPFW_DL_TYPE;
					wildcards_drop &= ~OFMatch.OFPFW_NW_PROTO;
				}
			} else {
				// if we have a non-IPv4 packet and packet matches SWITCH, INPORT and MAC criteria (if specified)
				// and the rule has "ANY" specified on protocol, then make decision for this packet/flow
				if (pkt_ip == null) {
					List<Object> ret = new ArrayList<Object>();
					ret.add(rule);
					if (rule.is_denyrule) {
						ret.add(new Integer(wildcards_drop));
					} else {
						ret.add(new Integer(wildcards_allow));
					}
					return ret;
				}
			}
			logger.info("protocol matched");
			
			// protocol specific fields
			
			// ip addresses (src and dst) match?
			if (rule.wildcard_src_ip == false && this.matchIPAddress(rule.src_ip_prefix, rule.src_ip_bits, pkt_ip.getSourceAddress()) == false) continue;
			if (rule.is_denyrule) {
				wildcards_allow &= ~OFMatch.OFPFW_NW_SRC_ALL;
				wildcards_allow |= (rule.src_ip_bits << OFMatch.OFPFW_NW_SRC_SHIFT);
			} else {
				wildcards_drop &= ~OFMatch.OFPFW_NW_SRC_ALL;
				wildcards_drop |= (rule.src_ip_bits << OFMatch.OFPFW_NW_SRC_SHIFT);
			}
			if (rule.wildcard_dst_ip == false && this.matchIPAddress(rule.dst_ip_prefix, rule.dst_ip_bits, pkt_ip.getDestinationAddress()) == false) continue;
			if (rule.is_denyrule) {
				wildcards_allow &= ~OFMatch.OFPFW_NW_DST_ALL;
				wildcards_allow |= (rule.dst_ip_bits << OFMatch.OFPFW_NW_DST_SHIFT);
			} else {
				wildcards_drop &= ~OFMatch.OFPFW_NW_DST_ALL;
				wildcards_drop |= (rule.dst_ip_bits << OFMatch.OFPFW_NW_DST_SHIFT);
			}
			logger.info("ip address matched");
			
			// TCP/UDP source and destination ports match?
			if (pkt_tcp != null || pkt_udp != null) {
				// does the source port match?
				if (rule.proto_srcport != 0 && rule.proto_srcport != proto_src) continue;
				if (rule.is_denyrule) {
					wildcards_allow &= ~OFMatch.OFPFW_TP_SRC;
				} else {
					wildcards_drop &= ~OFMatch.OFPFW_TP_SRC;
				}
				// does the destination port match?
				if (rule.proto_dstport != 0 && rule.proto_dstport != proto_dst) continue;
				if (rule.is_denyrule) {
					wildcards_allow &= ~OFMatch.OFPFW_TP_DST;
				} else {
					wildcards_drop &= ~OFMatch.OFPFW_TP_DST;
				}
			}
			logger.info("tcp/udp ports matched");
			
			// match found - i.e. no "continue" statement above got executed
			matched_rule = rule;
			break;
		}
		
		List<Object> ret = new ArrayList<Object>();
		ret.add(matched_rule);
		if (rule == null || rule.is_denyrule) {
			ret.add(new Integer(wildcards_drop));
		} else {
			ret.add(new Integer(wildcards_allow));
		}
		return ret;
	}
	
	protected boolean matchIPAddress(int rulePrefix, int ruleBits, int packetAddress) {
		boolean matched = true;
		
		int rule_iprng = 32 - ruleBits;
		int rule_ipint = rulePrefix;
		int pkt_ipint = packetAddress;
		// if there's a subnet range (bits to be wildcarded > 0)
		if (rule_iprng > 0) {
			// right shift bits to remove rule_iprng of LSB that are to be wildcarded
			rule_ipint = rule_ipint >> rule_iprng;
			pkt_ipint = pkt_ipint >> rule_iprng;
			// now left shift to return to normal range, except that the rule_iprng number of LSB
			// are now zeroed
			rule_ipint = rule_ipint << rule_iprng;
			pkt_ipint = pkt_ipint << rule_iprng;
		}
		// check if we have a match
		if (rule_ipint != pkt_ipint) matched = false;
		
		return matched;
	}
	
	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		if (eth.getEtherType() == Ethernet.TYPE_ARP || eth.isBroadcast() == true) {
			logger.info("allowing ARP and L2 broadcast traffic");
			return Command.CONTINUE;
		}
		
		// check if we have a matching rule for this packet/flow
		// and no decision is taken yet
		if (decision == null) {
			List<Object> match_ret = this.matchWithRule(sw, pi, cntx);
			FirewallRule rule = (FirewallRule)match_ret.get(0);
			if (rule != null) {
				String ruleInfo = "priority: " + (new Integer(rule.priority)).toString();
				ruleInfo += ", protocol: " + (new Integer(rule.proto_type)).toString();
				ruleInfo += ", deny rule? ";
				if (rule.is_denyrule) {
					ruleInfo += "yes";
				} else {
					ruleInfo += "no";
				}
				logger.info("Rule - {}", ruleInfo);
			}
			if (rule == null || rule.is_denyrule == true) {
				decision = new FirewallDecision(IRoutingDecision.RoutingAction.DROP);
				decision.setWildcards(((Integer)match_ret.get(1)).intValue());
				decision.addToContext(cntx);
				logger.info("no firewall rule found to allow this packet/flow, blocking packet/flow");
			} else {
				decision = new FirewallDecision(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
				decision.setWildcards(((Integer)match_ret.get(1)).intValue());
				decision.addToContext(cntx);
				logger.info("rule matched, allowing traffic");
			}
		}
        
        return Command.CONTINUE;
    }

}
