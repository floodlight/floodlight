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

package net.floodlightcontroller.staticentry.web;

import java.io.IOException;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.staticentry.StaticEntries;
import net.floodlightcontroller.staticentry.StaticEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.util.MatchUtils;

/**
 * Pushes a static flow entry to the storage source
 */
public class StaticEntryPusherResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(StaticEntryPusherResource.class);

	/**
	 * Validates if all the mandatory fields are set properly while adding an IPv6 flow
	 * @param Map containing the fields of the flow
	 * @return state indicating whether a flow is valid or not
	 */
	private int checkFlow(Map<String, Object> rows) {    
		//Declaring & Initializing flags
		int state = 0;
		boolean dl_type = false;
		boolean nw_proto = false;
		boolean nw_layer = false;
		boolean icmp6_type = false;
		boolean icmp6_code = false;
		boolean nd_target = false;
		boolean nd_sll = false;
		boolean nd_tll = false; 
		boolean ip6 = false;
		boolean ip4 = false;

		int eth_type = -1;
		int nw_protocol = -1;
		int icmp_type = -1;
		
		//Determine the dl_type if set
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_TYPE))) {
			if (((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_TYPE))).startsWith("0x")) {
				eth_type = Integer.parseInt(((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_TYPE))).replaceFirst("0x", ""), 16);
				dl_type = true;
			} else {
				eth_type = Integer.parseInt((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_TYPE)));
				dl_type = true;
			}
			if (eth_type == 0x86dd) { /* or 34525 */
				ip6 = true;
				dl_type = true;
			} else if (eth_type == 0x800 || /* or 2048 */
					eth_type == 0x806 || /* or 2054 */
					eth_type == 0x8035) { /* or 32821*/
				ip4 = true;
				dl_type = true;
			}	
			//else {
			//	state = 2;    
			//	return state;
			//}
		}
		final String ipv4_dst_str = StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV4_DST);
		final String ipv4_src_str = StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV4_SRC);
		if (rows.containsKey(ipv4_dst_str) ||
				rows.containsKey(ipv4_src_str)) {
			nw_layer = true;
			ip4 = true;
		}
		
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ICMPV4_CODE)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ICMPV4_TYPE)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ARP_THA)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ARP_SHA)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ARP_TPA)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ARP_SPA)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ARP_OP))) {
			ip4 = true;
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV6_FLABEL)) || 
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV6_SRC)) ||
				rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV6_DST))) {
			nw_layer = true;
			ip6 = true;
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IP_PROTO))) {
			nw_proto = true;
			if (((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.IP_PROTO))).startsWith("0x")) {
				nw_protocol = Integer.parseInt(((String) rows.get(StaticEntryPusher
						.matchFieldToColumnName(MatchFields.IP_PROTO))).replaceFirst("0x", ""), 16);
			} else {
				nw_protocol = Integer.parseInt((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.IP_PROTO)));
			}
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ICMPV6_CODE))) {
			icmp6_code = true;
			ip6 = true;
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.ICMPV6_TYPE))) {
			icmp6_type = true;
			ip6 = true;
			if (((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.ICMPV6_TYPE))).startsWith("0x")) {
				icmp_type = Integer.parseInt(((String) rows.get(StaticEntryPusher
						.matchFieldToColumnName(MatchFields.ICMPV6_TYPE))).replaceFirst("0x", ""), 16);
			} else {
				icmp_type = Integer.parseInt((String) rows.get(StaticEntryPusher.matchFieldToColumnName(MatchFields.ICMPV6_TYPE)));
			}
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV6_ND_SLL))) {
			nd_sll = true;
			ip6 = true;
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV6_ND_TLL))) {
			nd_tll = true;
			ip6 = true;
		}
		if (rows.containsKey(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV6_ND_TARGET))) {
			nd_target = true;
			ip6 = true;
		}    

		if (nw_layer == true || nw_proto == true) {
			if (dl_type == true) {
				if (!(ip4 == true || ip6 == true)) {
					//invalid dl_type
					state = 2;    
					return state;
				}
			}
			else {
				//dl_type not set
				state = 1;    
				return state;
			}
		}
		if (icmp6_type == true || icmp6_code == true ) {
			if (nw_proto == true) {
				if (nw_protocol != 0x3A) { /* or 58 */
					//invalid nw_proto
					state = 4;    
					return state;
				}
			}
			else {
				//nw_proto not set
				state = 3;    
				return state;
			}
		}

		if (nd_sll == true || nd_tll == true || nd_target == true) {
			if (icmp6_type == true) {
				//icmp_type must be set to 135/136 to set ipv6_nd_target
				if (nd_target == true) {
					if (!(icmp_type == 135 || icmp_type == 136)) { /* or 0x87 / 0x88 */
						//invalid icmp6_type
						state = 6;
						return state;
					}
				}
				//icmp_type must be set to 136 to set ipv6_nd_tll
				else if (nd_tll == true) {
					if (!(icmp_type == 136)) {
						//invalid icmp6_type
						state = 6;
						return state;
					}
				}
				//icmp_type must be set to 135 to set ipv6_nd_sll
				else if (nd_sll == true) {
					if (!(icmp_type == 135)) {
						//invalid icmp6_type
						state = 6;
						return state;
					}
				}
			}
			else {
				//icmp6_type not set
				state = 5;    
				return state;
			}
		}

		int result = checkActions(rows);

		if ((ip4 == true && ip6 == true) || (result == -1) ||
				(result == 1 && ip6 == true) || (result == 2 && ip4 == true)) {
			//ipv4 & ipv6 conflict
			state = 7;    
			return state;
		}
		
		if (rows.containsKey(StaticEntryPusher.Columns.COLUMN_SWITCH)) {
			try {
				DatapathId.of((String) rows.get(StaticEntryPusher.Columns.COLUMN_SWITCH));
			} catch (Exception e) {
				state = 9;
			}
		} else {
			state = 8;
		}
		return state;
	}

	/**
	 * Validates actions/instructions
	 * 
	 * -1 --> IPv4/IPv6 conflict
	 * 0 --> no IPv4 or IPv6 actions
	 * 1 --> IPv4 only actions
	 * 2 --> IPv6 only actions
	 * 
	 * @param Map containing the fields of the flow
	 * @return state indicating whether a flow is valid or not
	 */
	public static int checkActions(Map<String, Object> entry) {

		boolean ip6 = false;
		boolean ip4 = false;
		String actions = null;

		if (entry.containsKey(StaticEntryPusher.Columns.COLUMN_ACTIONS) || 
				entry.containsKey(StaticEntryPusher.intructionToColumnName(OFInstructionType.APPLY_ACTIONS)) ||
				entry.containsKey(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_ACTIONS))) {
			if (entry.containsKey(StaticEntryPusher.Columns.COLUMN_ACTIONS)) {
				actions = (String) entry.get(StaticEntryPusher.Columns.COLUMN_ACTIONS);
			}
			else if (entry.containsKey(StaticEntryPusher.intructionToColumnName(OFInstructionType.APPLY_ACTIONS))) {
				actions = (String) entry.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.APPLY_ACTIONS));
			}
			else if (entry.containsKey(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_ACTIONS))) {
				actions = (String) entry.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_ACTIONS));
			}

			if (actions.contains(MatchUtils.STR_ICMPV6_CODE) || actions.contains(MatchUtils.STR_ICMPV6_TYPE) ||
					actions.contains(MatchUtils.STR_IPV6_DST) || actions.contains(MatchUtils.STR_IPV6_SRC) || 
					actions.contains(MatchUtils.STR_IPV6_FLOW_LABEL) || actions.contains(MatchUtils.STR_IPV6_ND_SLL) ||
					actions.contains(MatchUtils.STR_IPV6_ND_TARGET) || actions.contains(MatchUtils.STR_IPV6_ND_TLL)) {
				ip6 = true;
			}
			if (actions.contains(MatchUtils.STR_NW_SRC) || actions.contains(MatchUtils.STR_NW_DST) || 
					actions.contains(MatchUtils.STR_ARP_OPCODE) || actions.contains(MatchUtils.STR_ARP_SHA) || 
					actions.contains(MatchUtils.STR_ARP_DHA) || actions.contains(MatchUtils.STR_ARP_SPA) || 
					actions.contains(MatchUtils.STR_ARP_DPA) || actions.contains(MatchUtils.STR_ICMP_CODE) || 
					actions.contains(MatchUtils.STR_ICMP_TYPE)) {
				ip4 = true;
			}
		}

		if (ip6 == false && ip4 == false) {
			return 0; // no actions involving ipv4 or ipv6
		} else if (ip6 == false && ip4 == true) {
			return 1; //ipv4
		} else if (ip6 == true && ip4 == false) {
			return 2; //ipv6
		} else {
			return -1; // conflict of ipv4 and ipv6 actions
		}
	}

	/**
	 * Takes a Static Entry Pusher string in JSON format and parses it into
	 * our database schema then pushes it to the database.
	 * @param fmJson The Static Flow Pusher entry in JSON format.
	 * @return A string status message
	 */
	@Post
	public String store(String json) {
		IStorageSourceService storageSource =
				(IStorageSourceService)getContext().getAttributes().
				get(IStorageSourceService.class.getCanonicalName());

		Map<String, Object> rowValues;
		try {
			rowValues = StaticEntries.jsonToStorageEntry(json);
			String status = null;

			int state = checkFlow(rowValues);
			if (state == 1) {
				status = "Warning! Must specify eth_type of IPv4/IPv6 to " +
						"match on IPv4/IPv6 fields! The flow has been discarded.";
				log.error(status);
			} else if (state == 2) {
				status = "Warning! eth_type not recognized! The flow has been discarded.";
				log.error(status);
			} else if (state == 3) {
				status = "Warning! Must specify ip_proto to match! The flow has been discarded.";
				log.error(status);
			} else if (state == 4) {
				status = "Warning! ip_proto invalid! The flow has been discarded.";
				log.error(status);
			} else if (state == 5) {
				status = "Warning! Must specify icmp6_type to match! The flow has been discarded.";
				log.error(status);
			} else if (state == 6) {
				status = "Warning! icmp6_type invalid! The flow has been discarded.";
				log.error(status);
			} else if (state == 7) {
				status = "Warning! IPv4 & IPv6 fields cannot be specified in the same flow! The flow has been discarded.";
				log.error(status);
			} else if (state == 8) {
				status = "Warning! Must specify switch DPID in flow. The flow has been discarded.";
				log.error(status);
			} else if (state == 9) {
				status = "Warning! Switch DPID invalid! The flow has been discarded.";
				log.error(status);
			} else if (state == 0) {
				status = "Entry pushed";            
				storageSource.insertRowAsync(StaticEntryPusher.TABLE_NAME, rowValues);
			}
			return ("{\"status\" : \"" + status + "\"}");
		} catch (IOException e) {
			log.error("Error parsing push flow mod request: " + json, e);
			return "{\"status\" : \"Error! Could not parse flow mod, see log for details.\"}";
		}        
	}

	@Delete
	public String del(String json) {
		IStorageSourceService storageSource =
				(IStorageSourceService)getContext().getAttributes().
				get(IStorageSourceService.class.getCanonicalName());
		String fmName = null;
		if (json == null) {
			return "{\"status\" : \"Error! No data posted.\"}";
		}
		try {
			fmName = StaticEntries.getEntryNameFromJson(json);
			if (fmName == null) {
				return "{\"status\" : \"Error deleting entry, no name provided\"}";
			}
		} catch (IOException e) {
			log.error("Error deleting flow mod request: " + json, e);
			return "{\"status\" : \"Error deleting entry, see log for details\"}";
		}

		storageSource.deleteRowAsync(StaticEntryPusher.TABLE_NAME, fmName);
		return "{\"status\" : \"Entry " + fmName + " deleted\"}";
	}
}
