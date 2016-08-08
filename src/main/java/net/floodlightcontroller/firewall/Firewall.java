/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by Amer Tahir
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

package net.floodlightcontroller.firewall;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.devicemanager.IDeviceService;

import java.util.ArrayList;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.util.OFMessageUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Stateless firewall implemented as a Google Summer of Code project.
 * Configuration done through REST API
 * 
 * @author Amer Tahir
 * @edited KC Wang
 * @edited Ryan Izard
 */
public class Firewall implements IFirewallService, IOFMessageListener,
IFloodlightModule {
	private static final short APP_ID = 30;
	static {
		AppCookie.registerApp(APP_ID, "Firewall");
	}
	private static final U64 DENY_BCAST_COOKIE = AppCookie.makeCookie(APP_ID, 0xaaaaaaL);
	private static final U64 ALLOW_BCAST_COOKIE = AppCookie.makeCookie(APP_ID, 0x555555L);
	private static final U64 RULE_MISS_COOKIE = AppCookie.makeCookie(APP_ID, 0xffffffL);
	static final U64 DEFAULT_COOKIE = AppCookie.makeCookie(APP_ID, 0xffffffL);

	// service modules needed
	protected IFloodlightProviderService floodlightProvider;
	protected IStorageSourceService storageSource;
	protected IRestApiService restApi;
	protected IRoutingService routingService;
	protected static Logger logger;

	protected List<FirewallRule> rules; // protected by synchronized
	protected boolean enabled;
	protected IPv4Address subnet_mask = IPv4Address.of("255.255.255.0");

	// constant strings for storage/parsing
	public static final String TABLE_NAME = "controller_firewallrules";
	public static final String COLUMN_RULEID = "ruleid";
	public static final String COLUMN_DPID = "dpid";
	public static final String COLUMN_IN_PORT = "in_port";
	public static final String COLUMN_DL_SRC = "dl_src";
	public static final String COLUMN_DL_DST = "dl_dst";
	public static final String COLUMN_DL_TYPE = "dl_type";
	public static final String COLUMN_NW_SRC_PREFIX = "nw_src_prefix";
	public static final String COLUMN_NW_SRC_MASKBITS = "nw_src_maskbits";
	public static final String COLUMN_NW_DST_PREFIX = "nw_dst_prefix";
	public static final String COLUMN_NW_DST_MASKBITS = "nw_dst_maskbits";
	public static final String COLUMN_NW_PROTO = "nw_proto";
	public static final String COLUMN_TP_SRC = "tp_src";
	public static final String COLUMN_TP_DST = "tp_dst";
	public static final String COLUMN_WILDCARD_DPID = "wildcard_dpid";
	public static final String COLUMN_WILDCARD_IN_PORT = "any_in_port";
	public static final String COLUMN_WILDCARD_DL_SRC = "any_dl_src";
	public static final String COLUMN_WILDCARD_DL_DST = "any_dl_dst";
	public static final String COLUMN_WILDCARD_DL_TYPE = "any_dl_type";
	public static final String COLUMN_WILDCARD_NW_SRC = "any_nw_src";
	public static final String COLUMN_WILDCARD_NW_DST = "any_nw_dst";
	public static final String COLUMN_WILDCARD_NW_PROTO = "any_nw_proto";
	public static final String COLUMN_WILDCARD_TP_SRC = "any_tp_src";
	public static final String COLUMN_WILDCARD_TP_DST = "any_tp_dst";
	public static final String COLUMN_PRIORITY = "priority";
	public static final String COLUMN_ACTION = "action";
	public static String ColumnNames[] = { COLUMN_RULEID, COLUMN_DPID,
		COLUMN_IN_PORT, COLUMN_DL_SRC, COLUMN_DL_DST, COLUMN_DL_TYPE,
		COLUMN_NW_SRC_PREFIX, COLUMN_NW_SRC_MASKBITS, COLUMN_NW_DST_PREFIX,
		COLUMN_NW_DST_MASKBITS, COLUMN_NW_PROTO, COLUMN_TP_SRC,
		COLUMN_TP_DST, COLUMN_WILDCARD_DPID, COLUMN_WILDCARD_IN_PORT,
		COLUMN_WILDCARD_DL_SRC, COLUMN_WILDCARD_DL_DST,
		COLUMN_WILDCARD_DL_TYPE, COLUMN_WILDCARD_NW_SRC,
		COLUMN_WILDCARD_NW_DST, COLUMN_WILDCARD_NW_PROTO, COLUMN_PRIORITY,
		COLUMN_ACTION };

	@Override
	public String getName() {
		return "firewall";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// no prereq
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFirewallService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IFirewallService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStorageSourceService.class);
		l.add(IRestApiService.class);
		l.add(IRoutingService.class);
		return l;
	}

	/**
	 * Reads the rules from the storage and creates a sorted arraylist of
	 * FirewallRule from them.
	 * 
	 * Similar to getStorageRules(), which only reads contents for REST GET and
	 * does no parsing, checking, nor putting into FirewallRule objects
	 * 
	 * @return the sorted arraylist of FirewallRule instances (rules from
	 *         storage)
	 */
	protected ArrayList<FirewallRule> readRulesFromStorage() {
		ArrayList<FirewallRule> l = new ArrayList<FirewallRule>();

		try {
			Map<String, Object> row;

			// (..., null, null) for no predicate, no ordering
			IResultSet resultSet = storageSource.executeQuery(TABLE_NAME, ColumnNames, null, null);

			// put retrieved rows into FirewallRules
			for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
				row = it.next().getRow();
				// now, parse row
				FirewallRule r = new FirewallRule();
				if (!row.containsKey(COLUMN_RULEID) || !row.containsKey(COLUMN_DPID)) {
					logger.error( "skipping entry with missing required 'ruleid' or 'switchid' entry: {}", row);
					return l;
				}
				try {
					r.ruleid = Integer
							.parseInt((String) row.get(COLUMN_RULEID));
					r.dpid = DatapathId.of((String) row.get(COLUMN_DPID));

					for (String key : row.keySet()) {
						if (row.get(key) == null) {
							continue;
						}
						if (key.equals(COLUMN_RULEID) || key.equals(COLUMN_DPID) || key.equals("id")) {
							continue; // already handled
						} else if (key.equals(COLUMN_IN_PORT)) {
							r.in_port = OFPort.of(Integer.parseInt((String) row.get(COLUMN_IN_PORT)));
						} else if (key.equals(COLUMN_DL_SRC)) {
							r.dl_src = MacAddress.of(Long.parseLong((String) row.get(COLUMN_DL_SRC)));
						}  else if (key.equals(COLUMN_DL_DST)) {
							r.dl_dst = MacAddress.of(Long.parseLong((String) row.get(COLUMN_DL_DST)));
						} else if (key.equals(COLUMN_DL_TYPE)) {
							r.dl_type = EthType.of(Integer.parseInt((String) row.get(COLUMN_DL_TYPE)));
						} else if (key.equals(COLUMN_NW_SRC_PREFIX)) {
							r.nw_src_prefix_and_mask = IPv4AddressWithMask.of(IPv4Address.of(Integer.parseInt((String) row.get(COLUMN_NW_SRC_PREFIX))), r.nw_src_prefix_and_mask.getMask());
						} else if (key.equals(COLUMN_NW_SRC_MASKBITS)) {
							r.nw_src_prefix_and_mask = IPv4AddressWithMask.of(r.nw_src_prefix_and_mask.getValue(), IPv4Address.of(Integer.parseInt((String) row.get(COLUMN_NW_SRC_MASKBITS))));
						} else if (key.equals(COLUMN_NW_DST_PREFIX)) {
							r.nw_dst_prefix_and_mask = IPv4AddressWithMask.of(IPv4Address.of(Integer.parseInt((String) row.get(COLUMN_NW_DST_PREFIX))), r.nw_dst_prefix_and_mask.getMask());
						} else if (key.equals(COLUMN_NW_DST_MASKBITS)) {
							r.nw_dst_prefix_and_mask = IPv4AddressWithMask.of(r.nw_dst_prefix_and_mask.getValue(), IPv4Address.of(Integer.parseInt((String) row.get(COLUMN_NW_DST_MASKBITS))));
						} else if (key.equals(COLUMN_NW_PROTO)) {
							r.nw_proto = IpProtocol.of(Short.parseShort((String) row.get(COLUMN_NW_PROTO)));
						} else if (key.equals(COLUMN_TP_SRC)) {
							r.tp_src = TransportPort.of(Integer.parseInt((String) row.get(COLUMN_TP_SRC)));
						} else if (key.equals(COLUMN_TP_DST)) {
							r.tp_dst = TransportPort.of(Integer.parseInt((String) row.get(COLUMN_TP_DST)));
						} else if (key.equals(COLUMN_WILDCARD_DPID)) {
							r.any_dpid = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_DPID));
						} else if (key.equals(COLUMN_WILDCARD_IN_PORT)) {
							r.any_in_port = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_IN_PORT));
						} else if (key.equals(COLUMN_WILDCARD_DL_SRC)) {
							r.any_dl_src = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_DL_SRC));
						} else if (key.equals(COLUMN_WILDCARD_DL_DST)) {
							r.any_dl_dst = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_DL_DST));
						} else if (key.equals(COLUMN_WILDCARD_DL_TYPE)) {
							r.any_dl_type = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_DL_TYPE));
						} else if (key.equals(COLUMN_WILDCARD_NW_SRC)) {
							r.any_nw_src = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_NW_SRC));
						} else if (key.equals(COLUMN_WILDCARD_NW_DST)) {
							r.any_nw_dst = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_NW_DST));
						} else if (key.equals(COLUMN_WILDCARD_NW_PROTO)) {
							r.any_nw_proto = Boolean.parseBoolean((String) row.get(COLUMN_WILDCARD_NW_PROTO));
						} else if (key.equals(COLUMN_PRIORITY)) {
							r.priority = Integer.parseInt((String) row.get(COLUMN_PRIORITY));
						} else if (key.equals(COLUMN_ACTION)) {
							int tmp = Integer.parseInt((String) row.get(COLUMN_ACTION));
							if (tmp == FirewallRule.FirewallAction.DROP.ordinal()) {
								r.action = FirewallRule.FirewallAction.DROP;
							} else if (tmp == FirewallRule.FirewallAction.ALLOW.ordinal()) {
								r.action = FirewallRule.FirewallAction.ALLOW;
							} else {
								r.action = null;
								logger.error("action not recognized");
							}
						}
					}
				} catch (ClassCastException e) {
					logger.error("skipping rule {} with bad data : " + e.getMessage(), r.ruleid);
				}
				if (r.action != null) {
					l.add(r);
				}
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

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		storageSource = context.getServiceImpl(IStorageSourceService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		rules = new ArrayList<FirewallRule>();
		logger = LoggerFactory.getLogger(Firewall.class);

		// start disabled
		enabled = false;
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// register REST interface
		restApi.addRestletRoutable(new FirewallWebRoutable());

		// always place firewall in pipeline at bootup
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		// storage, create table and read rules
		storageSource.createTable(TABLE_NAME, null);
		storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_RULEID);
		this.rules = readRulesFromStorage();
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if (!this.enabled) {
			return Command.CONTINUE;
		}

		switch (msg.getType()) {
		case PACKET_IN:
			IRoutingDecision decision = null;
			if (cntx == null) {
				logger.warn("Firewall unable to request packet drop: FloodlightContext is null.");
			} else {
				decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
				return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
			}
			break;
		default:
			break;
		}

		return Command.CONTINUE;
	}

	@Override
	public void enableFirewall(boolean enabled) {
		if(this.enabled != enabled) {
			logger.info("Setting firewall to {}", enabled);
			this.enabled = enabled;

			List<Masked<U64>> changes = ImmutableList.of(
						Masked.of(DEFAULT_COOKIE, AppCookie.getAppFieldMask())
					);

			// Add announcement that all firewall decisions changed
			routingService.handleRoutingDecisionChange(changes);
		}
	}

	@Override
	public List<FirewallRule> getRules() {
		return this.rules;
	}

	// Only used to serve REST GET
	// Similar to readRulesFromStorage(), which actually checks and stores
	// record into FirewallRule list
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
	public String getSubnetMask() {
		return this.subnet_mask.toString();
	}

	@Override
	public void setSubnetMask(String newMask) {
		if (newMask.trim().isEmpty())
			return;
		this.subnet_mask = IPv4Address.of(newMask.trim());
	}

	@Override
	public synchronized void addRule(FirewallRule rule) {

		// generate random ruleid for each newly created rule
		// may want to return to caller if useful
		// may want to check conflict
		rule.ruleid = rule.genID();

		int i = 0;
		// locate the position of the new rule in the sorted arraylist
		for (i = 0; i < this.rules.size(); i++) {
			if (this.rules.get(i).priority >= rule.priority)
				break;
		}
		// now, add rule to the list
		if (i <= this.rules.size()) {
			this.rules.add(i, rule);
		} else {
			this.rules.add(rule);
		}
		// add rule to database
		Map<String, Object> entry = new HashMap<String, Object>();
		entry.put(COLUMN_RULEID, Integer.toString(rule.ruleid));
		entry.put(COLUMN_DPID, Long.toString(rule.dpid.getLong()));
		entry.put(COLUMN_IN_PORT, Integer.toString(rule.in_port.getPortNumber()));
		entry.put(COLUMN_DL_SRC, Long.toString(rule.dl_src.getLong()));
		entry.put(COLUMN_DL_DST, Long.toString(rule.dl_dst.getLong()));
		entry.put(COLUMN_DL_TYPE, Integer.toString(rule.dl_type.getValue()));
		entry.put(COLUMN_NW_SRC_PREFIX, Integer.toString(rule.nw_src_prefix_and_mask.getValue().getInt()));
		entry.put(COLUMN_NW_SRC_MASKBITS, Integer.toString(rule.nw_src_prefix_and_mask.getMask().getInt()));
		entry.put(COLUMN_NW_DST_PREFIX, Integer.toString(rule.nw_dst_prefix_and_mask.getValue().getInt()));
		entry.put(COLUMN_NW_DST_MASKBITS, Integer.toString(rule.nw_dst_prefix_and_mask.getMask().getInt()));
		entry.put(COLUMN_NW_PROTO, Short.toString(rule.nw_proto.getIpProtocolNumber()));
		entry.put(COLUMN_TP_SRC, Integer.toString(rule.tp_src.getPort()));
		entry.put(COLUMN_TP_DST, Integer.toString(rule.tp_dst.getPort()));
		entry.put(COLUMN_WILDCARD_DPID, Boolean.toString(rule.any_dpid));
		entry.put(COLUMN_WILDCARD_IN_PORT, Boolean.toString(rule.any_in_port));
		entry.put(COLUMN_WILDCARD_DL_SRC, Boolean.toString(rule.any_dl_src));
		entry.put(COLUMN_WILDCARD_DL_DST, Boolean.toString(rule.any_dl_dst));
		entry.put(COLUMN_WILDCARD_DL_TYPE, Boolean.toString(rule.any_dl_type));
		entry.put(COLUMN_WILDCARD_NW_SRC, Boolean.toString(rule.any_nw_src));
		entry.put(COLUMN_WILDCARD_NW_DST, Boolean.toString(rule.any_nw_dst));
		entry.put(COLUMN_WILDCARD_NW_PROTO, Boolean.toString(rule.any_nw_proto));
		entry.put(COLUMN_WILDCARD_TP_SRC, Boolean.toString(rule.any_tp_src));
		entry.put(COLUMN_WILDCARD_TP_DST, Boolean.toString(rule.any_tp_dst));
		entry.put(COLUMN_PRIORITY, Integer.toString(rule.priority));
		entry.put(COLUMN_ACTION, Integer.toString(rule.action.ordinal()));
		storageSource.insertRow(TABLE_NAME, entry);
		
		U64 singleRuleMask = AppCookie.getAppFieldMask().or(AppCookie.getUserFieldMask());
		ImmutableList.Builder<Masked<U64>> changesBuilder = ImmutableList.builder();
		Iterator<FirewallRule> iter = this.rules.iterator();
		while (iter.hasNext()) {
			FirewallRule r = iter.next();
			if (r.priority >= rule.priority) {
				changesBuilder.add(Masked.of(AppCookie.makeCookie(APP_ID, r.ruleid), singleRuleMask));
			}
		}
		changesBuilder.add(Masked.of(RULE_MISS_COOKIE, singleRuleMask));
		routingService.handleRoutingDecisionChange(changesBuilder.build());
	}

	@Override
	public synchronized void deleteRule(int ruleid) {
		Iterator<FirewallRule> iter = this.rules.iterator();
		while (iter.hasNext()) {
			FirewallRule r = iter.next();
			if (r.ruleid == ruleid) {
				// found the rule, now remove it
				iter.remove();
				break;
			}
		}
		// delete from database
		storageSource.deleteRow(TABLE_NAME, Integer.toString(ruleid));
		
		//Add announcement that the rule has been deleted
		Masked<U64> delDescriptor = Masked.of(
				AppCookie.makeCookie(APP_ID, ruleid),
				AppCookie.getAppFieldMask().or(AppCookie.getUserFieldMask()));
		
		List<Masked<U64>> changes = ImmutableList.of(delDescriptor);
		
		//Add announcement that rule is added
		// should we try to delete the flow even if not found in this.rules
		routingService.handleRoutingDecisionChange(changes);
	}

	/**
	 * Iterates over the firewall rules and tries to match them with the
	 * incoming packet (flow). Uses the FirewallRule class's matchWithFlow
	 * method to perform matching. It maintains a pair of wildcards (allow and
	 * deny) which are assigned later to the firewall's decision, where 'allow'
	 * wildcards are applied if the matched rule turns out to be an ALLOW rule
	 * and 'deny' wildcards are applied otherwise. Wildcards are applied to
	 * firewall decision to optimize flows in the switch, ensuring least number
	 * of flows per firewall rule. So, if a particular field is not "ANY" (i.e.
	 * not wildcarded) in a higher priority rule, then if a lower priority rule
	 * matches the packet and wildcards it, it can't be wildcarded in the
	 * switch's flow entry, because otherwise some packets matching the higher
	 * priority rule might escape the firewall. The reason for keeping different
	 * two different wildcards is that if a field is not wildcarded in a higher
	 * priority allow rule, the same field shouldn't be wildcarded for packets
	 * matching the lower priority deny rule (non-wildcarded fields in higher
	 * priority rules override the wildcarding of those fields in lower priority
	 * rules of the opposite type). So, to ensure that wildcards are
	 * appropriately set for different types of rules (allow vs. deny), separate
	 * wildcards are maintained. Iteration is performed on the sorted list of
	 * rules (sorted in decreasing order of priority).
	 * 
	 * @param sw
	 *            the switch instance
	 * @param pi
	 *            the incoming packet data structure
	 * @param cntx
	 *            the floodlight context
	 * @return an instance of RuleWildcardsPair that specify rule that matches
	 *         and the wildcards for the firewall decision
	 */
	protected RuleMatchPair matchWithRule(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		FirewallRule matched_rule = null;
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		AllowDropPair adp = new AllowDropPair(sw.getOFFactory());

		synchronized (rules) {
			Iterator<FirewallRule> iter = this.rules.iterator();
			FirewallRule rule = null;
			// iterate through list to find a matching firewall rule
			while (iter.hasNext()) {
				// get next rule from list
				rule = iter.next();

				// check if rule matches
				// AllowDropPair adp's allow and drop matches will modified with what matches
				if (rule.matchesThisPacket(sw.getId(), (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)), eth, adp) == true) {
					matched_rule = rule;
					break;
				}
			}
		}

		// make a pair of rule and wildcards, then return it
		RuleMatchPair rmp = new RuleMatchPair();
		rmp.rule = matched_rule;
		if (matched_rule == null) {
			/*
			 * No rule was found, so drop the packet with as specific 
			 * of a drop rule as possible as not to interfere with other
			 * firewall rules.
			 */
			Match.Builder mb = OFFactories.getFactory(pi.getVersion()).buildMatch();
			mb.setExact(MatchField.IN_PORT, (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)))
			.setExact(MatchField.ETH_SRC, eth.getSourceMACAddress())
			.setExact(MatchField.ETH_DST, eth.getDestinationMACAddress())
			.setExact(MatchField.ETH_TYPE, eth.getEtherType());

			if (mb.get(MatchField.ETH_TYPE).equals(EthType.IPv4)) {
				IPv4 ipv4 = (IPv4) eth.getPayload();
				mb.setExact(MatchField.IPV4_SRC, ipv4.getSourceAddress())
				.setExact(MatchField.IPV4_DST, ipv4.getDestinationAddress())
				.setExact(MatchField.IP_PROTO, ipv4.getProtocol());

				if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ipv4.getPayload();
					mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort())
					.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
				} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.UDP)) {
					UDP udp = (UDP) ipv4.getPayload();
					mb.setExact(MatchField.UDP_SRC, udp.getSourcePort())
					.setExact(MatchField.UDP_DST, udp.getDestinationPort());
				} else {
					// could be ICMP, which will be taken care of via IPv4 src/dst + ip proto
				}
			}
			rmp.match = mb.build();
			//rmp.match = adp.drop.build(); This inserted a "drop all" rule if no match was found (not what we want to do...)
		} else if (matched_rule.action == FirewallRule.FirewallAction.DROP) {
			rmp.match = adp.drop.build();
		} else {
			rmp.match = adp.allow.build();
		}
		return rmp;
	}

	/**
	 * Checks whether an IP address is a broadcast address or not (determines
	 * using subnet mask)
	 * 
	 * @param IPAddress
	 *            the IP address to check
	 * @return true if it is a broadcast address, false otherwise
	 */
	protected boolean isIPBroadcast(IPv4Address ip) {
		// inverted subnet mask
		IPv4Address inv_subnet_mask = subnet_mask.not();
		return ip.and(inv_subnet_mask).equals(inv_subnet_mask);
	}

	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		OFPort inPort = OFMessageUtils.getInPort(pi);

		// Allowing L2 broadcast + ARP broadcast request (also deny malformed
		// broadcasts -> L2 broadcast + L3 unicast)
		if (eth.isBroadcast() == true) {
			boolean allowBroadcast = true;
			// the case to determine if we have L2 broadcast + L3 unicast (L3 broadcast default set to /24 or 255.255.255.0)
			// don't allow this broadcast packet if such is the case (malformed packet)
			if ((eth.getPayload() instanceof IPv4) && !isIPBroadcast(((IPv4) eth.getPayload()).getDestinationAddress())) {
				allowBroadcast = false;
			}
			if (allowBroadcast == true) {
				if (logger.isTraceEnabled()) {
					logger.trace("Allowing broadcast traffic for PacketIn={}", pi);
				}

				decision = new RoutingDecision(sw.getId(), inPort, 
						IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE),
						IRoutingDecision.RoutingAction.MULTICAST);
				decision.setDescriptor(ALLOW_BCAST_COOKIE);
				decision.addToContext(cntx);
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Blocking malformed broadcast traffic for PacketIn={}", pi);
				}

				decision = new RoutingDecision(sw.getId(), inPort,
						IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE),
						IRoutingDecision.RoutingAction.DROP);
				decision.setDescriptor(DENY_BCAST_COOKIE);
				decision.addToContext(cntx);
			}
			return Command.CONTINUE;
		}
		/*
		 * ARP response (unicast) can be let through without filtering through
		 * rules by uncommenting the code below
		 */
		/*
		 * else if (eth.getEtherType() == Ethernet.TYPE_ARP) {
		 * logger.info("allowing ARP traffic"); decision = new
		 * FirewallDecision(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
		 * decision.setDescriptor(ALLOW_BCAST_COOKIE);
		 * decision.addToContext(cntx); return Command.CONTINUE; }
		 */

		// check if we have a matching rule for this packet/flow and no decision has been made yet
		if (decision == null) {
			// check if the packet we received matches an existing rule
			RuleMatchPair rmp = this.matchWithRule(sw, pi, cntx);
			FirewallRule rule = rmp.rule;

			// Drop the packet if we don't have a rule allowing or dropping it or if we explicitly drop it
			if (rule == null || rule.action == FirewallRule.FirewallAction.DROP) {
				decision = new RoutingDecision(sw.getId(), inPort, 
						IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE), 
						IRoutingDecision.RoutingAction.DROP);
				decision.setMatch(rmp.match);
				if (rule == null) {
					decision.setDescriptor(RULE_MISS_COOKIE);
				} else {
					decision.setDescriptor(AppCookie.makeCookie(APP_ID, rule.ruleid));
				}
				decision.addToContext(cntx);
				if (logger.isTraceEnabled()) {
					if (rule == null) {
						logger.trace("No firewall rule found for PacketIn={}, blocking flow", pi);
					} else if (rule.action == FirewallRule.FirewallAction.DROP) {
						logger.trace("Deny rule={} match for PacketIn={}", rule, pi);
					}
				}
				// Found a rule and the rule is not a drop, so allow the packet
			} else {
				decision = new RoutingDecision(sw.getId(), inPort, 
						IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE),
						IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
				decision.setMatch(rmp.match);
				decision.setDescriptor(AppCookie.makeCookie(APP_ID, rule.ruleid));
				decision.addToContext(cntx);
				if (logger.isTraceEnabled()) {
					logger.trace("Allow rule={} match for PacketIn={}", rule, pi);
				}
			}
		}

		return Command.CONTINUE;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

}
