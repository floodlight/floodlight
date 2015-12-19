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

package net.floodlightcontroller.staticflowentry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.web.StaticFlowEntryWebRoutable;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.InstructionUtils;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowRemovedReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.ver10.OFFlowRemovedReasonSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver11.OFFlowRemovedReasonSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver12.OFFlowRemovedReasonSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver13.OFFlowRemovedReasonSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFFlowRemovedReasonSerializerVer14;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This module is responsible for maintaining a set of static flows on
 * switches. This is just a big 'ol dumb list of flows and something external
 * is responsible for ensuring they make sense for the network.
 */
public class StaticFlowEntryPusher
implements IOFSwitchListener, IFloodlightModule, IStaticFlowEntryPusherService, IStorageSourceListener, IOFMessageListener {
	protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryPusher.class);
	public static final String StaticFlowName = "staticflowentry";

	public static final int STATIC_FLOW_APP_ID = 10;
	static {
		AppCookie.registerApp(STATIC_FLOW_APP_ID, StaticFlowName);
	}

	public static final String TABLE_NAME = "controller_staticflowtableentry";
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_SWITCH = "switch";
	public static final String COLUMN_TABLE_ID = "table";
	public static final String COLUMN_ACTIVE = "active";
	public static final String COLUMN_IDLE_TIMEOUT = "idle_timeout";
	public static final String COLUMN_HARD_TIMEOUT = "hard_timeout";
	public static final String COLUMN_PRIORITY = "priority";
	public static final String COLUMN_COOKIE = "cookie";

	// Common location for Match Strings. Still the same, but relocated.
	public static final String COLUMN_IN_PORT = MatchUtils.STR_IN_PORT;

	public static final String COLUMN_DL_SRC = MatchUtils.STR_DL_SRC;
	public static final String COLUMN_DL_DST = MatchUtils.STR_DL_DST;
	public static final String COLUMN_DL_VLAN = MatchUtils.STR_DL_VLAN;
	public static final String COLUMN_DL_VLAN_PCP = MatchUtils.STR_DL_VLAN_PCP;
	public static final String COLUMN_DL_TYPE = MatchUtils.STR_DL_TYPE;

	public static final String COLUMN_NW_TOS = MatchUtils.STR_NW_TOS;
	public static final String COLUMN_NW_ECN = MatchUtils.STR_NW_ECN;
	public static final String COLUMN_NW_DSCP = MatchUtils.STR_NW_DSCP;
	public static final String COLUMN_NW_PROTO = MatchUtils.STR_NW_PROTO;
	public static final String COLUMN_NW_SRC = MatchUtils.STR_NW_SRC; // includes CIDR-style netmask, e.g. "128.8.128.0/24"
	public static final String COLUMN_NW_DST = MatchUtils.STR_NW_DST;

	public static final String COLUMN_SCTP_SRC = MatchUtils.STR_SCTP_SRC;
	public static final String COLUMN_SCTP_DST = MatchUtils.STR_SCTP_DST;
	public static final String COLUMN_UDP_SRC = MatchUtils.STR_UDP_SRC;
	public static final String COLUMN_UDP_DST = MatchUtils.STR_UDP_DST;
	public static final String COLUMN_TCP_SRC = MatchUtils.STR_TCP_SRC;
	public static final String COLUMN_TCP_DST = MatchUtils.STR_TCP_DST;
	public static final String COLUMN_TP_SRC = MatchUtils.STR_TP_SRC; // support for OF1.0 generic transport ports (possibly sent from the rest api). Only use these to read them in, but store them as the type of port their IpProto is set to.
	public static final String COLUMN_TP_DST = MatchUtils.STR_TP_DST;

	/* newly added matches for OF1.3 port start here */
	public static final String COLUMN_ICMP_TYPE = MatchUtils.STR_ICMP_TYPE;
	public static final String COLUMN_ICMP_CODE = MatchUtils.STR_ICMP_CODE;

	public static final String COLUMN_ARP_OPCODE = MatchUtils.STR_ARP_OPCODE;
	public static final String COLUMN_ARP_SHA = MatchUtils.STR_ARP_SHA;
	public static final String COLUMN_ARP_DHA = MatchUtils.STR_ARP_DHA;
	public static final String COLUMN_ARP_SPA = MatchUtils.STR_ARP_SPA;
	public static final String COLUMN_ARP_DPA = MatchUtils.STR_ARP_DPA;

	/* IPv6 related columns */
	public static final String COLUMN_NW6_SRC = MatchUtils.STR_IPV6_SRC;
	public static final String COLUMN_NW6_DST = MatchUtils.STR_IPV6_DST;
	public static final String COLUMN_IPV6_FLOW_LABEL = MatchUtils.STR_IPV6_FLOW_LABEL;
	public static final String COLUMN_ICMP6_TYPE = MatchUtils.STR_ICMPV6_TYPE;
	public static final String COLUMN_ICMP6_CODE = MatchUtils.STR_ICMPV6_CODE;
	public static final String COLUMN_ND_SLL = MatchUtils.STR_IPV6_ND_SSL;
	public static final String COLUMN_ND_TLL = MatchUtils.STR_IPV6_ND_TTL;
	public static final String COLUMN_ND_TARGET = MatchUtils.STR_IPV6_ND_TARGET;	

	public static final String COLUMN_MPLS_LABEL = MatchUtils.STR_MPLS_LABEL;
	public static final String COLUMN_MPLS_TC = MatchUtils.STR_MPLS_TC;
	public static final String COLUMN_MPLS_BOS = MatchUtils.STR_MPLS_BOS;

	public static final String COLUMN_METADATA = MatchUtils.STR_METADATA;
	public static final String COLUMN_TUNNEL_ID = MatchUtils.STR_TUNNEL_ID;

	public static final String COLUMN_PBB_ISID = MatchUtils.STR_PBB_ISID;
	/* end newly added matches */

	public static final String COLUMN_ACTIONS = "actions";

	public static final String COLUMN_INSTR_GOTO_TABLE = InstructionUtils.STR_GOTO_TABLE; // instructions are each getting their own column, due to write and apply actions, which themselves contain a variable list of actions
	public static final String COLUMN_INSTR_WRITE_METADATA = InstructionUtils.STR_WRITE_METADATA;
	public static final String COLUMN_INSTR_WRITE_ACTIONS = InstructionUtils.STR_WRITE_ACTIONS;
	public static final String COLUMN_INSTR_APPLY_ACTIONS = InstructionUtils.STR_APPLY_ACTIONS;
	public static final String COLUMN_INSTR_CLEAR_ACTIONS = InstructionUtils.STR_CLEAR_ACTIONS;
	public static final String COLUMN_INSTR_GOTO_METER = InstructionUtils.STR_GOTO_METER;
	public static final String COLUMN_INSTR_EXPERIMENTER = InstructionUtils.STR_EXPERIMENTER;

	public static String ColumnNames[] = { COLUMN_NAME, COLUMN_SWITCH,
		COLUMN_TABLE_ID, COLUMN_ACTIVE, COLUMN_IDLE_TIMEOUT, COLUMN_HARD_TIMEOUT, // table id is new for OF1.3 as well
		COLUMN_PRIORITY, COLUMN_COOKIE, COLUMN_IN_PORT,
		COLUMN_DL_SRC, COLUMN_DL_DST, COLUMN_DL_VLAN, COLUMN_DL_VLAN_PCP,
		COLUMN_DL_TYPE, COLUMN_NW_TOS, COLUMN_NW_PROTO, COLUMN_NW_SRC,
		COLUMN_NW_DST, COLUMN_TP_SRC, COLUMN_TP_DST,
		/* newly added matches for OF1.3 port start here */
		COLUMN_SCTP_SRC, COLUMN_SCTP_DST, 
		COLUMN_UDP_SRC, COLUMN_UDP_DST, COLUMN_TCP_SRC, COLUMN_TCP_DST,
		COLUMN_ICMP_TYPE, COLUMN_ICMP_CODE, 
		COLUMN_ARP_OPCODE, COLUMN_ARP_SHA, COLUMN_ARP_DHA, 
		COLUMN_ARP_SPA, COLUMN_ARP_DPA,

		/* IPv6 related matches */
		COLUMN_NW6_SRC, COLUMN_NW6_DST, COLUMN_ICMP6_TYPE, COLUMN_ICMP6_CODE, 
		COLUMN_IPV6_FLOW_LABEL, COLUMN_ND_SLL, COLUMN_ND_TLL, COLUMN_ND_TARGET,		
		COLUMN_MPLS_LABEL, COLUMN_MPLS_TC, COLUMN_MPLS_BOS, 
		COLUMN_METADATA, COLUMN_TUNNEL_ID, COLUMN_PBB_ISID,
		/* end newly added matches */
		COLUMN_ACTIONS,
		/* newly added instructions for OF1.3 port start here */
		COLUMN_INSTR_GOTO_TABLE, COLUMN_INSTR_WRITE_METADATA,
		COLUMN_INSTR_WRITE_ACTIONS, COLUMN_INSTR_APPLY_ACTIONS,
		COLUMN_INSTR_CLEAR_ACTIONS, COLUMN_INSTR_GOTO_METER,
		COLUMN_INSTR_EXPERIMENTER
		/* end newly added instructions */
	};

	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IStorageSourceService storageSourceService;
	protected IRestApiService restApiService;

	private IHAListener haListener;

	// Map<DPID, Map<Name, FlowMod>>; FlowMod can be null to indicate non-active
	protected Map<String, Map<String, OFFlowMod>> entriesFromStorage;
	// Entry Name -> DPID of Switch it's on
	protected Map<String, String> entry2dpid;

	// Class to sort FlowMod's by priority, from lowest to highest
	class FlowModSorter implements Comparator<String> {
		private String dpid;
		public FlowModSorter(String dpid) {
			this.dpid = dpid;
		}
		@Override
		public int compare(String o1, String o2) {
			OFFlowMod f1 = entriesFromStorage.get(dpid).get(o1);
			OFFlowMod f2 = entriesFromStorage.get(dpid).get(o2);
			if (f1 == null || f2 == null) // sort active=false flows by key
				return o1.compareTo(o2);
			return U16.of(f1.getPriority()).getValue() - U16.of(f2.getPriority()).getValue();
		}
	};

	/**
	 * used for debugging and unittests
	 * @return the number of static flow entries as cached from storage
	 */
	public int countEntries() {
		int size = 0;
		if (entriesFromStorage == null)
			return 0;
		for (String ofswitch : entriesFromStorage.keySet())
			size += entriesFromStorage.get(ofswitch).size();
		return size;
	}

	public IFloodlightProviderService getFloodlightProvider() {
		return floodlightProviderService;
	}

	public void setFloodlightProvider(IFloodlightProviderService floodlightProviderService) {
		this.floodlightProviderService = floodlightProviderService;
	}

	public void setStorageSource(IStorageSourceService storageSourceService) {
		this.storageSourceService = storageSourceService;
	}

	/**
	 * Reads from our entriesFromStorage for the specified switch and
	 * sends the FlowMods down to the controller in <b>sorted</b> order.
	 *
	 * Sorted is important to maintain correctness of the switch:
	 * if a packet would match both a lower and a higher priority
	 * rule, then we want it to match the higher priority or nothing,
	 * but never just the lower priority one.  Inserting from high to
	 * low priority fixes this.
	 *
	 * TODO consider adding a "block all" flow mod and then removing it
	 * while starting up.
	 *
	 * @param sw The switch to send entries to
	 */
	protected void sendEntriesToSwitch(DatapathId switchId) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		if (sw == null)
			return;
		String stringId = sw.getId().toString();

		if ((entriesFromStorage != null) && (entriesFromStorage.containsKey(stringId))) {
			Map<String, OFFlowMod> entries = entriesFromStorage.get(stringId);
			List<String> sortedList = new ArrayList<String>(entries.keySet());
			// weird that Collections.sort() returns void
			Collections.sort( sortedList, new FlowModSorter(stringId));
			for (String entryName : sortedList) {
				OFFlowMod flowMod = entries.get(entryName);
				if (flowMod != null) {
					if (log.isDebugEnabled()) {
						log.debug("Pushing static entry {} for {}", stringId, entryName);
					}
					writeFlowModToSwitch(sw, flowMod);
				}
			}
		}
	}

	/**
	 * Used only for bundle-local indexing
	 *
	 * @param map
	 * @return
	 */

	protected Map<String, String> computeEntry2DpidMap(
			Map<String, Map<String, OFFlowMod>> map) {
		Map<String, String> ret = new ConcurrentHashMap<String, String>();
		for(String dpid : map.keySet()) {
			for( String entry: map.get(dpid).keySet())
				ret.put(entry, dpid);
		}
		return ret;
	}

	/**
	 * Read entries from storageSource, and store them in a hash
	 *
	 * @return
	 */
	private Map<String, Map<String, OFFlowMod>> readEntriesFromStorage() {
		Map<String, Map<String, OFFlowMod>> entries = new ConcurrentHashMap<String, Map<String, OFFlowMod>>();
		try {
			Map<String, Object> row;
			// null1=no predicate, null2=no ordering
			IResultSet resultSet = storageSourceService.executeQuery(TABLE_NAME, ColumnNames, null, null);
			for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
				row = it.next().getRow();
				parseRow(row, entries);
			}
		} catch (StorageException e) {
			log.error("failed to access storage: {}", e.getMessage());
			// if the table doesn't exist, then wait to populate later via
			// setStorageSource()
		}
		return entries;
	}

	/**
	 * Take a single row, turn it into a flowMod, and add it to the
	 * entries{$dpid}.{$entryName}=FlowMod
	 *
	 * IF an entry is in active, mark it with FlowMod = null
	 *
	 * @param row
	 * @param entries
	 */
	void parseRow(Map<String, Object> row, Map<String, Map<String, OFFlowMod>> entries) {
		String switchName = null;
		String entryName = null;

		StringBuffer matchString = new StringBuffer();
		OFFlowMod.Builder fmb = null; 

		if (!row.containsKey(COLUMN_SWITCH) || !row.containsKey(COLUMN_NAME)) {
			log.debug("skipping entry with missing required 'switch' or 'name' entry: {}", row);
			return;
		}
		// most error checking done with ClassCastException
		try {
			// first, snag the required entries, for debugging info
			switchName = (String) row.get(COLUMN_SWITCH);
			entryName = (String) row.get(COLUMN_NAME);
			if (!entries.containsKey(switchName)) {
				entries.put(switchName, new HashMap<String, OFFlowMod>());
			}

			// get the correct builder for the OF version supported by the switch
			try {
				fmb = OFFactories.getFactory(switchService.getSwitch(DatapathId.of(switchName)).getOFFactory().getVersion()).buildFlowModify();
			} catch (NullPointerException e) {
				/* switch was not connected/known */
				storageSourceService.deleteRowAsync(TABLE_NAME, entryName);
				log.error("Deleting entry {}. Switch {} was not connected to the controller, and we need to know the OF protocol version to compose the flow mod.", entryName, switchName);
				return;
			}

			StaticFlowEntries.initDefaultFlowMod(fmb, entryName);

			for (String key : row.keySet()) {
				if (row.get(key) == null) {
					continue;
				}

				if (key.equals(COLUMN_SWITCH) || key.equals(COLUMN_NAME) || key.equals("id")) {
					continue; // already handled
				}

				if (key.equals(COLUMN_ACTIVE)) {
					if  (!Boolean.valueOf((String) row.get(COLUMN_ACTIVE))) {
						log.debug("skipping inactive entry {} for switch {}", entryName, switchName);
						entries.get(switchName).put(entryName, null);  // mark this an inactive
						return;
					}
				} else if (key.equals(COLUMN_HARD_TIMEOUT)) {
					fmb.setHardTimeout(Integer.valueOf((String) row.get(COLUMN_HARD_TIMEOUT)));
				} else if (key.equals(COLUMN_IDLE_TIMEOUT)) {
					fmb.setIdleTimeout(Integer.valueOf((String) row.get(COLUMN_IDLE_TIMEOUT)));
				} else if (key.equals(COLUMN_TABLE_ID)) {
					if (fmb.getVersion().compareTo(OFVersion.OF_10) > 0) {
						fmb.setTableId(TableId.of(Integer.parseInt((String) row.get(key)))); // support multiple flow tables for OF1.1+
					} else {
						log.error("Table not supported in OpenFlow 1.0");
					}
				} else if (key.equals(COLUMN_ACTIONS)) {
					ActionUtils.fromString(fmb, (String) row.get(COLUMN_ACTIONS), log);
				} else if (key.equals(COLUMN_COOKIE)) {
					fmb.setCookie(StaticFlowEntries.computeEntryCookie(Integer.valueOf((String) row.get(COLUMN_COOKIE)), entryName));
				} else if (key.equals(COLUMN_PRIORITY)) {
					fmb.setPriority(U16.t(Integer.valueOf((String) row.get(COLUMN_PRIORITY))));
				} else if (key.equals(COLUMN_INSTR_APPLY_ACTIONS)) {
					InstructionUtils.applyActionsFromString(fmb, (String) row.get(COLUMN_INSTR_APPLY_ACTIONS), log);
				} else if (key.equals(COLUMN_INSTR_CLEAR_ACTIONS)) {
					InstructionUtils.clearActionsFromString(fmb, (String) row.get(COLUMN_INSTR_CLEAR_ACTIONS), log);
				} else if (key.equals(COLUMN_INSTR_EXPERIMENTER)) {
					InstructionUtils.experimenterFromString(fmb, (String) row.get(COLUMN_INSTR_EXPERIMENTER), log);
				} else if (key.equals(COLUMN_INSTR_GOTO_METER)) {
					InstructionUtils.meterFromString(fmb, (String) row.get(COLUMN_INSTR_GOTO_METER), log);
				} else if (key.equals(COLUMN_INSTR_GOTO_TABLE)) {
					InstructionUtils.gotoTableFromString(fmb, (String) row.get(COLUMN_INSTR_GOTO_TABLE), log);
				} else if (key.equals(COLUMN_INSTR_WRITE_ACTIONS)) {
					InstructionUtils.writeActionsFromString(fmb, (String) row.get(COLUMN_INSTR_WRITE_ACTIONS), log);
				} else if (key.equals(COLUMN_INSTR_WRITE_METADATA)) {
					InstructionUtils.writeMetadataFromString(fmb, (String) row.get(COLUMN_INSTR_WRITE_METADATA), log);
				} else { // the rest of the keys are for Match().fromString()
					if (matchString.length() > 0) {
						matchString.append(",");
					}
					matchString.append(key + "=" + row.get(key).toString());
				}
			}
		} catch (ClassCastException e) {
			if (entryName != null && switchName != null) {
				log.warn("Skipping entry {} on switch {} with bad data : " + e.getMessage(), entryName, switchName);
			} else {
				log.warn("Skipping entry with bad data: {} :: {} ", e.getMessage(), e.getStackTrace());
			}
		}

		String match = matchString.toString();

		try {
			fmb.setMatch(MatchUtils.fromString(match, fmb.getVersion()));
		} catch (IllegalArgumentException e) {
			log.error(e.toString());
			log.error("Ignoring flow entry {} on switch {} with illegal OFMatch() key: " + match, entryName, switchName);
			return;
		} catch (Exception e) {
			log.error("OF version incompatible for the match: " + match);
			e.printStackTrace();
			return;
		}

		entries.get(switchName).put(entryName, fmb.build()); // add the FlowMod message to the table
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		log.debug("Switch {} connected; processing its static entries",
				switchId.toString());
		sendEntriesToSwitch(switchId);
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// do NOT delete from our internal state; we're tracking the rules,
		// not the switches
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// no-op
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// no-op
	}

	@Override
	public void switchPortChanged(DatapathId switchId,
			OFPortDesc port,
			PortChangeType type) {
		// no-op
	}


	@Override
	public void rowsModified(String tableName, Set<Object> rowKeys) {
		// This handles both rowInsert() and rowUpdate()
		log.debug("Modifying Table {}", tableName);
		HashMap<String, Map<String, OFFlowMod>> entriesToAdd =
				new HashMap<String, Map<String, OFFlowMod>>();
		// build up list of what was added
		for (Object key: rowKeys) {
			IResultSet resultSet = storageSourceService.getRow(tableName, key);
			Iterator<IResultSet> it = resultSet.iterator();
			while (it.hasNext()) {
				Map<String, Object> row = it.next().getRow();
				parseRow(row, entriesToAdd);
			}
		}
		// batch updates by switch and blast them out
		for (String dpid : entriesToAdd.keySet()) {
			if (!entriesFromStorage.containsKey(dpid))
				entriesFromStorage.put(dpid, new HashMap<String, OFFlowMod>());

			List<OFMessage> outQueue = new ArrayList<OFMessage>();

			/* For every flow per dpid, decide how to "add" the flow. */
			for (String entry : entriesToAdd.get(dpid).keySet()) {
				OFFlowMod newFlowMod = entriesToAdd.get(dpid).get(entry);
				OFFlowMod oldFlowMod = null;

				String dpidOldFlowMod = entry2dpid.get(entry);
				if (dpidOldFlowMod != null) {
					oldFlowMod = entriesFromStorage.get(dpidOldFlowMod).remove(entry);
				}

				/* Modify, which can be either a Flow MODIFY_STRICT or a Flow DELETE_STRICT with a side of Flow ADD */
				if (oldFlowMod != null && newFlowMod != null) { 
					/* MODIFY_STRICT b/c the match is still the same */
					if (oldFlowMod.getMatch().equals(newFlowMod.getMatch())
							&& oldFlowMod.getCookie().equals(newFlowMod.getCookie())
							&& oldFlowMod.getPriority() == newFlowMod.getPriority()
							&& dpidOldFlowMod.equalsIgnoreCase(dpid)) {
						log.debug("ModifyStrict SFP Flow");
						entriesFromStorage.get(dpid).put(entry, newFlowMod);
						entry2dpid.put(entry, dpid);
						newFlowMod = FlowModUtils.toFlowModifyStrict(newFlowMod);
						outQueue.add(newFlowMod);
						/* DELETE_STRICT and then ADD b/c the match is now different */
					} else {
						log.debug("DeleteStrict and Add SFP Flow");
						oldFlowMod = FlowModUtils.toFlowDeleteStrict(oldFlowMod);
						OFFlowAdd addTmp = FlowModUtils.toFlowAdd(newFlowMod);
						/* If the flow's dpid and the current switch we're looking at are the same, add to the queue. */
						if (dpidOldFlowMod.equals(dpid)) {
							outQueue.add(oldFlowMod);
							outQueue.add(addTmp); 
							/* Otherwise, go ahead and send the flows now (since queuing them will send to the wrong switch). */
						} else {
							writeOFMessageToSwitch(DatapathId.of(dpidOldFlowMod), oldFlowMod);
							writeOFMessageToSwitch(DatapathId.of(dpid), FlowModUtils.toFlowAdd(newFlowMod)); 
						}
						entriesFromStorage.get(dpid).put(entry, addTmp);
						entry2dpid.put(entry, dpid);			
					}
					/* Add a brand-new flow with ADD */
				} else if (newFlowMod != null && oldFlowMod == null) {
					log.debug("Add SFP Flow");
					OFFlowAdd addTmp = FlowModUtils.toFlowAdd(newFlowMod);
					entriesFromStorage.get(dpid).put(entry, addTmp);
					entry2dpid.put(entry, dpid);
					outQueue.add(addTmp);
					/* Something strange happened, so remove the flow */
				} else if (newFlowMod == null) { 
					entriesFromStorage.get(dpid).remove(entry);
					entry2dpid.remove(entry);
				}
			}
			/* Batch-write all queued messages to the switch */
			writeOFMessagesToSwitch(DatapathId.of(dpid), outQueue);
		}
	}

	@Override
	public void rowsDeleted(String tableName, Set<Object> rowKeys) {
		if (log.isDebugEnabled()) {
			log.debug("Deleting from table {}", tableName);
		}

		for(Object obj : rowKeys) {
			if (!(obj instanceof String)) {
				log.debug("Tried to delete non-string key {}; ignoring", obj);
				continue;
			}
			deleteStaticFlowEntry((String) obj);
		}
	}

	private void deleteStaticFlowEntry(String entryName) {
		String dpid = entry2dpid.remove(entryName);

		if (dpid == null) {
			// assume state has been cleared by deleteFlowsForSwitch() or
			// deleteAllFlows()
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Sending delete flow mod for flow {} for switch {}", entryName, dpid);
		}

		// send flow_mod delete
		if (switchService.getSwitch(DatapathId.of(dpid)) != null) {
			OFFlowDeleteStrict flowMod = FlowModUtils.toFlowDeleteStrict(entriesFromStorage.get(dpid).get(entryName));

			if (entriesFromStorage.containsKey(dpid) && entriesFromStorage.get(dpid).containsKey(entryName)) {
				entriesFromStorage.get(dpid).remove(entryName);
			} else {
				log.debug("Tried to delete non-existent entry {} for switch {}", entryName, dpid);
				return;
			}

			writeFlowModToSwitch(DatapathId.of(dpid), flowMod);
		} else {
			log.debug("Not sending flow delete for disconnected switch.");
		}
		return;
	}

	/**
	 * Writes a list of OFMessages to a switch
	 * @param dpid The datapath ID of the switch to write to
	 * @param messages The list of OFMessages to write.
	 */
	private void writeOFMessagesToSwitch(DatapathId dpid, List<OFMessage> messages) {
		IOFSwitch ofswitch = switchService.getSwitch(dpid);
		if (ofswitch != null) {  // is the switch connected
			if (log.isDebugEnabled()) {
				log.debug("Sending {} new entries to {}", messages.size(), dpid);
			}
			ofswitch.write(messages);
		}
	}

	/**
	 * Writes a single OFMessage to a switch
	 * @param dpid The datapath ID of the switch to write to
	 * @param message The OFMessage to write.
	 */
	private void writeOFMessageToSwitch(DatapathId dpid, OFMessage message) {
		IOFSwitch ofswitch = switchService.getSwitch(dpid);
		if (ofswitch != null) {  // is the switch connected
			if (log.isDebugEnabled()) {
				log.debug("Sending 1 new entries to {}", dpid.toString());
			}
			ofswitch.write(message);
		}
	}

	/**
	 * Writes an OFFlowMod to a switch. It checks to make sure the switch
	 * exists before it sends
	 * @param dpid The data  to write the flow mod to
	 * @param flowMod The OFFlowMod to write
	 */
	private void writeFlowModToSwitch(DatapathId dpid, OFFlowMod flowMod) {
		IOFSwitch ofSwitch = switchService.getSwitch(dpid);
		if (ofSwitch == null) {
			if (log.isDebugEnabled()) {
				log.debug("Not deleting key {} :: switch {} not connected", dpid.toString());
			}
			return;
		}
		writeFlowModToSwitch(ofSwitch, flowMod);
	}

	/**
	 * Writes an OFFlowMod to a switch
	 * @param sw The IOFSwitch to write to
	 * @param flowMod The OFFlowMod to write
	 */
	private void writeFlowModToSwitch(IOFSwitch sw, OFFlowMod flowMod) {
		sw.write(flowMod);
	}
	@Override
	public String getName() {
		return StaticFlowName;
	}

	/**
	 * Handles a flow removed message from a switch. If the flow was removed
	 * and we did not explicitly delete it we re-install it. If we explicitly
	 * removed the flow we stop the processing of the flow removed message.
	 * @param sw The switch that sent the flow removed message.
	 * @param msg The flow removed message.
	 * @param cntx The associated context.
	 * @return Whether to continue processing this message.
	 */
	public Command handleFlowRemoved(IOFSwitch sw, OFFlowRemoved msg, FloodlightContext cntx) {
		U64 cookie = msg.getCookie();
		/**
		 * This is just to sanity check our assumption that static flows
		 * never expire.
		 */
		if (AppCookie.extractApp(cookie) == STATIC_FLOW_APP_ID) {
			OFFlowRemovedReason reason = null;
			switch (msg.getVersion()) {
			case OF_10:
				reason = OFFlowRemovedReasonSerializerVer10.ofWireValue((byte) msg.getReason());
				break;
			case OF_11:
				reason = OFFlowRemovedReasonSerializerVer11.ofWireValue((byte) msg.getReason());
				break;
			case OF_12:
				reason = OFFlowRemovedReasonSerializerVer12.ofWireValue((byte) msg.getReason());
				break;
			case OF_13:
				reason = OFFlowRemovedReasonSerializerVer13.ofWireValue((byte) msg.getReason());
				break;
			case OF_14:
				reason = OFFlowRemovedReasonSerializerVer14.ofWireValue((byte) msg.getReason());
				break;
			default:
				log.debug("OpenFlow version {} unsupported for OFFlowRemovedReasonSerializerVerXX", msg.getVersion());
				break;
			}
			if (reason != null) {
				if (OFFlowRemovedReason.DELETE == reason) {
					log.error("Got a FlowRemove message for a infinite " + 
							"timeout flow: {} from switch {}", msg, sw);
				} else if (OFFlowRemovedReason.HARD_TIMEOUT == reason || OFFlowRemovedReason.IDLE_TIMEOUT == reason) {
					/* Remove the Flow from the DB since it timed out */
					log.debug("Received an IDLE or HARD timeout for an SFP flow. Removing it from the SFP DB.");
					/* 
					 * Lookup the flow based on the flow contents. We do not know/care about the name of the 
					 * flow based on this message, but we can get the table values for this switch and search.
					 */
					String flowToRemove = null;
					Map<String, OFFlowMod> flowsByName = getFlows(sw.getId());
					for (Map.Entry<String, OFFlowMod> entry : flowsByName.entrySet()) {
						if (msg.getCookie().equals(entry.getValue().getCookie()) &&
								(msg.getVersion().compareTo(OFVersion.OF_12) < 0 ? true : msg.getHardTimeout() == entry.getValue().getHardTimeout()) &&
								msg.getIdleTimeout() == entry.getValue().getIdleTimeout() &&
								msg.getMatch().equals(entry.getValue().getMatch()) &&
								msg.getPriority() == entry.getValue().getPriority() &&
								(msg.getVersion().compareTo(OFVersion.OF_10) == 0 ? true : msg.getTableId().equals(entry.getValue().getTableId()))
								) {
							flowToRemove = entry.getKey();
							break;
						}
					}

					log.debug("Flow to Remove: {}", flowToRemove);

					/*
					 * Remove the flow. This will send the delete message to the switch,
					 * since we cannot tell the storage listener rowsdeleted() that we
					 * are only removing our local DB copy of the flow and that it actually
					 * timed out on the switch and is already gone. The switch will silently
					 * discard the delete message in this case.
					 * 
					 * TODO: We should come up with a way to convey to the storage listener
					 * the reason for the flow being removed.
					 */
					if (flowToRemove != null) {
						deleteFlow(flowToRemove);
					}
				}
				/* Stop the processing chain since we sent or asked for the delete message. */
				return Command.STOP;
			}
		}
		/* Continue the processing chain, since we did not send the delete. */
		return Command.CONTINUE;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case FLOW_REMOVED:
			return handleFlowRemoved(sw, (OFFlowRemoved) msg, cntx);
		default:
			return Command.CONTINUE;
		}
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;  // no dependency for non-packet in
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;  // no dependency for non-packet in
	}

	// IFloodlightModule

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IStaticFlowEntryPusherService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m =
		new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		m.put(IStaticFlowEntryPusherService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		l.add(IStorageSourceService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		storageSourceService = context.getServiceImpl(IStorageSourceService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		haListener = new HAListenerDelegate();
	} 

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.FLOW_REMOVED, this);
		switchService.addOFSwitchListener(this);
		floodlightProviderService.addHAListener(this.haListener);
		// assumes no switches connected at startup()
		storageSourceService.createTable(TABLE_NAME, null);
		storageSourceService.setTablePrimaryKeyName(TABLE_NAME, COLUMN_NAME);
		storageSourceService.addListener(TABLE_NAME, this);
		entriesFromStorage = readEntriesFromStorage();
		entry2dpid = computeEntry2DpidMap(entriesFromStorage);
		restApiService.addRestletRoutable(new StaticFlowEntryWebRoutable());
	}

	// IStaticFlowEntryPusherService methods

	@Override
	public void addFlow(String name, OFFlowMod fm, DatapathId swDpid) {
		try {
			Map<String, Object> fmMap = StaticFlowEntries.flowModToStorageEntry(fm, swDpid.toString(), name);
			storageSourceService.insertRowAsync(TABLE_NAME, fmMap);
		} catch (Exception e) {
			log.error("Error! Check the fields specified for the flow.Make sure IPv4 fields are not mixed with IPv6 fields or all "
					+ "mandatory fields are specified. ");
		}
	}

	@Override
	public void deleteFlow(String name) {
		storageSourceService.deleteRowAsync(TABLE_NAME, name);
	}

	@Override
	public void deleteAllFlows() {
		for (String entry : entry2dpid.keySet()) {
			deleteFlow(entry);
		}

		/*
        FIXME: Since the OF spec 1.0 is not clear on how
        to match on cookies. Once all switches come to a
        common implementation we can possibly re-enable this
        fix.

        // Send a delete for each switch
        Set<String> swSet = new HashSet<String>();
        for (String dpid : entry2dpid.values()) {
            // Avoid sending duplicate deletes
            if (!swSet.contains(dpid)) {
                swSet.add(dpid);
                sendDeleteByCookie(HexString.toLong(dpid));
            }
        }

        // Clear our map
        entry2dpid.clear();

        // Clear our book keeping map
        for (Map<String, OFFlowMod> eMap : entriesFromStorage.values()) {
            eMap.clear();
        }

        // Reset our DB
        storageSource.deleteMatchingRowsAsync(TABLE_NAME, null);
		 */
	}

	@Override
	public void deleteFlowsForSwitch(DatapathId dpid) {
		String sDpid = dpid.toString();

		for (Entry<String, String> e : entry2dpid.entrySet()) {
			if (e.getValue().equals(sDpid))
				deleteFlow(e.getKey());
		}

		/*
        FIXME: Since the OF spec 1.0 is not clear on how
        to match on cookies. Once all switches come to a
        common implementation we can possibly re-enable this
        fix.
        //sendDeleteByCookie(dpid);

        String sDpid = HexString.toHexString(dpid);
        // Clear all internal flows for this switch
        Map<String, OFFlowMod> sMap = entriesFromStorage.get(sDpid);
        if (sMap != null) {
            for (String entryName : sMap.keySet()) {
                entry2dpid.remove(entryName);
                // Delete from DB
                deleteFlow(entryName);
            }
            sMap.clear();
        } else {
            log.warn("Map of storage entries for switch {} was null", sDpid);
        }
		 */
	}

	/**
	 * Deletes all flows installed by static flow pusher on a given switch.
	 * We send a delete flow mod with the static flow pusher app ID in the cookie.
	 * Since OF1.0 doesn't support masking based on the cookie we have to
	 * disable having flow specific cookies.
	 * @param dpid The DPID of the switch to clear all it's flows.
	 */
	/*
    FIXME: Since the OF spec 1.0 is not clear on how
    to match on cookies. Once all switches come to a
    common implementation we can possibly re-enable this
    fix.
    private void sendDeleteByCookie(long dpid) {
        if (log.isDebugEnabled())
            log.debug("Deleting all static flows on switch {}", HexString.toHexString(dpid));

        IOFSwitch sw = floodlightProvider.getSwitch(dpid);
        if (sw == null) {
            log.warn("Tried to delete static flows for non-existant switch {}",
                    HexString.toHexString(dpid));
            return;
        }

        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().
                getMessage(OFType.FLOW_MOD);
        OFMatch ofm = new OFMatch();
        fm.setMatch(ofm);
        fm.setCookie(AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, 0));
        fm.setCommand(OFFlowMod.OFPFC_DELETE);
        fm.setOutPort(OFPort.OFPP_NONE);

        try {
            sw.write(fm, null);
            sw.flush();
        } catch (IOException e1) {
            log.error("Error deleting all flows for switch {}:\n {}",
                    HexString.toHexString(dpid), e1.getMessage());
            return;
        }
    }
	 */

	@Override
	public Map<String, Map<String, OFFlowMod>> getFlows() {
		return entriesFromStorage;
	}

	@Override
	public Map<String, OFFlowMod> getFlows(DatapathId dpid) {
		return entriesFromStorage.get(dpid.toString());
	}

	// IHAListener

	private class HAListenerDelegate implements IHAListener {
		@Override
		public void transitionToActive() {
			log.debug("Re-reading static flows from storage due " +
					"to HA change from STANDBY->ACTIVE");
			entriesFromStorage = readEntriesFromStorage();
			entry2dpid = computeEntry2DpidMap(entriesFromStorage);
		}

		@Override
		public void controllerNodeIPsChanged(
				Map<String, String> curControllerNodeIPs,
				Map<String, String> addedControllerNodeIPs,
				Map<String, String> removedControllerNodeIPs) {
			// ignore
		}

		@Override
		public String getName() {
			return StaticFlowEntryPusher.this.getName();
		}

		@Override
		public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
				String name) {
			return false;
		}

		@Override
		public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
				String name) {
			return false;
		}

		@Override
		public void transitionToStandby() {	
			log.debug("Controller is now in STANDBY role. Clearing static flow entries from store.");
			deleteAllFlows();
		}
	}
}
