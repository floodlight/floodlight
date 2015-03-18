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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.staticflowentry.web.StaticFlowEntryPusherResource;
import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.InstructionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionClearActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionExperimenter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

/**
 * Represents static flow entries to be maintained by the controller on the 
 * switches. 
 */
@LogMessageCategory("Static Flow Pusher")
public class StaticFlowEntries {
	protected static Logger log = LoggerFactory.getLogger(StaticFlowEntries.class);
	private static final int INFINITE_TIMEOUT = 0;

	/**
	 * This function generates a random hash for the bottom half of the cookie
	 * 
	 * @param fm
	 * @param userCookie
	 * @param name
	 * @return A cookie that encodes the application ID and a hash
	 */
	public static U64 computeEntryCookie(int userCookie, String name) {
		// flow-specific hash is next 20 bits LOOK! who knows if this 
		int prime = 211;
		int flowHash = 2311;
		for (int i=0; i < name.length(); i++) {
			flowHash = flowHash * prime + (int)name.charAt(i);
		}

		return AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, flowHash);
	}

	/**
	 * Sets defaults for an OFFlowMod used in the StaticFlowEntryPusher
	 * @param fm The OFFlowMod to set defaults for
	 * @param entryName The name of the entry. Used to compute the cookie.
	 */
	public static void initDefaultFlowMod(OFFlowMod.Builder fmb, String entryName) {
		fmb.setIdleTimeout(INFINITE_TIMEOUT) // not setting these would also work
		.setHardTimeout(INFINITE_TIMEOUT)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setOutPort(OFPort.ANY) 
		.setCookie(computeEntryCookie(0, entryName))
		.setPriority(Integer.MAX_VALUE)
		.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM));
		return;
	}

	/**
	 * Gets the entry name of a flow mod
	 * @param fmJson The OFFlowMod in a JSON representation
	 * @return The name of the OFFlowMod, null if not found
	 * @throws IOException If there was an error parsing the JSON
	 */
	public static String getEntryNameFromJson(String fmJson) throws IOException{
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		try {
			jp = f.createJsonParser(fmJson);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
				throw new IOException("Expected FIELD_NAME");
			}

			String n = jp.getCurrentName();
			jp.nextToken();
			if (jp.getText().equals("")) 
				continue;

			if (n == StaticFlowEntryPusher.COLUMN_NAME)
				return jp.getText();
		}
		return null;
	}

	/**
	 * Parses an OFFlowMod (and it's inner Match) to the storage entry format.
	 * @param fm The FlowMod to parse
	 * @param sw The switch the FlowMod is going to be installed on
	 * @param name The name of this static flow entry
	 * @return A Map representation of the storage entry 
	 */
	public static Map<String, Object> flowModToStorageEntry(OFFlowMod fm, String sw, String name) throws Exception {
		Map<String, Object> entry = new HashMap<String, Object>();
		entry.put(StaticFlowEntryPusher.COLUMN_NAME, name);
		entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, sw);
		entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, Boolean.toString(true));
		entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, Integer.toString(fm.getPriority()));
		entry.put(StaticFlowEntryPusher.COLUMN_IDLE_TIMEOUT, Integer.toString(fm.getIdleTimeout()));
		entry.put(StaticFlowEntryPusher.COLUMN_HARD_TIMEOUT, Integer.toString(fm.getHardTimeout()));

		switch (fm.getVersion()) {
		case OF_10:
			if (fm.getActions() != null) {
				entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, ActionUtils.actionsToString(fm.getActions(), log));
			}
			break;
		case OF_11:
		case OF_12:
		case OF_13:
		case OF_14:
		default:
			// should have a table ID present
			if (fm.getTableId() != null) { // if not set, then don't worry about it. Default will be set when built and sent to switch
				entry.put(StaticFlowEntryPusher.COLUMN_TABLE_ID, Short.toString(fm.getTableId().getValue()));
			}
			// should have a list of instructions, of which apply and write actions could have sublists of actions
			if (fm.getInstructions() != null) {
				List<OFInstruction> instructions = fm.getInstructions();
				for (OFInstruction inst : instructions) {
					switch (inst.getType()) {
					case GOTO_TABLE:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_TABLE, InstructionUtils.gotoTableToString(((OFInstructionGotoTable) inst), log));
						break;
					case WRITE_METADATA:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_METADATA, InstructionUtils.writeMetadataToString(((OFInstructionWriteMetadata) inst), log));
						break;
					case WRITE_ACTIONS:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_ACTIONS, InstructionUtils.writeActionsToString(((OFInstructionWriteActions) inst), log));
						break;
					case APPLY_ACTIONS:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_APPLY_ACTIONS, InstructionUtils.applyActionsToString(((OFInstructionApplyActions) inst), log));
						break;
					case CLEAR_ACTIONS:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_CLEAR_ACTIONS, InstructionUtils.clearActionsToString(((OFInstructionClearActions) inst), log));
						break;
					case METER:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_METER, InstructionUtils.meterToString(((OFInstructionMeter) inst), log));
						break;
					case EXPERIMENTER:
						entry.put(StaticFlowEntryPusher.COLUMN_INSTR_EXPERIMENTER, InstructionUtils.experimenterToString(((OFInstructionExperimenter) inst), log));
						break;
					default:
						log.error("Could not decode OF1.1+ instruction type {}", inst); 
					}
				}
			}	
		}		

		Match match = fm.getMatch();
		// it's a shame we can't use the MatchUtils for this. It's kind of the same thing but storing in a different place.
		Iterator<MatchField<?>> itr = match.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			@SuppressWarnings("rawtypes") // this is okay here
			MatchField mf = itr.next();
			switch (mf.id) {
			case IN_PORT: // iterates over only exact/masked fields. No need to check for null entries.
				entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, Integer.toString((match.get(MatchField.IN_PORT)).getPortNumber()));
				break;
			case ETH_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, match.get(MatchField.ETH_SRC).toString());
				break;
			case ETH_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, match.get(MatchField.ETH_DST).toString());
				break;
			case VLAN_VID:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, match.get(MatchField.VLAN_VID).getVlan());
				break;
			case VLAN_PCP:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, Byte.toString(match.get(MatchField.VLAN_PCP).getValue()));
				break;
			case ETH_TYPE:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, match.get(MatchField.ETH_TYPE).getValue());
				break;
			case IP_ECN: // TOS = [DSCP bits 0-5] + [ECN bits 6-7] --> bitwise OR to get TOS byte (have separate columns now though)
				entry.put(StaticFlowEntryPusher.COLUMN_NW_ECN, Byte.toString(match.get(MatchField.IP_ECN).getEcnValue()));
				break;
			case IP_DSCP: // Even for OF1.0, loxi will break ECN and DSCP up from the API's POV. This method is only invoked by a SFP service push from another module
				entry.put(StaticFlowEntryPusher.COLUMN_NW_DSCP, Byte.toString((byte) (match.get(MatchField.IP_DSCP).getDscpValue())));
				break;
			case IP_PROTO:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, Short.toString(match.get(MatchField.IP_PROTO).getIpProtocolNumber()));
				break;
			case IPV4_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, match.get(MatchField.IPV4_SRC).toString());
				break;
			case IPV4_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, match.get(MatchField.IPV4_DST).toString());
				break;
			case TCP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_SRC, match.get(MatchField.TCP_SRC).getPort());
				break;
			case UDP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_SRC, match.get(MatchField.UDP_SRC).getPort());
				break;
			case SCTP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_SRC, match.get(MatchField.SCTP_SRC).getPort());
				break;
			case TCP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_DST, match.get(MatchField.TCP_DST).getPort());
				break;
			case UDP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_DST, match.get(MatchField.UDP_DST).getPort());
				break;
			case SCTP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_DST, match.get(MatchField.SCTP_DST).getPort());
				break;
			case ICMPV4_TYPE:
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP_TYPE, match.get(MatchField.ICMPV4_TYPE).getType());
				break;
			case ICMPV4_CODE:
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP_CODE, match.get(MatchField.ICMPV4_CODE).getCode());
				break;
			case ARP_OP:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_OPCODE, match.get(MatchField.ARP_OP).getOpcode());
				break;
			case ARP_SHA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_SHA, match.get(MatchField.ARP_SHA).toString());
				break;
			case ARP_THA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_DHA, match.get(MatchField.ARP_THA).toString());
				break;
			case ARP_SPA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_SPA, match.get(MatchField.ARP_SPA).toString());
				break;
			case ARP_TPA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_DPA, match.get(MatchField.ARP_TPA).toString());
				break;
			case IPV6_SRC:				
				entry.put(StaticFlowEntryPusher.COLUMN_NW6_SRC, match.get(MatchField.IPV6_SRC).toString());
				break;
			case IPV6_DST:			
				entry.put(StaticFlowEntryPusher.COLUMN_NW6_DST, match.get(MatchField.IPV6_DST).toString());
				break;	
			case IPV6_FLABEL:			
				entry.put(StaticFlowEntryPusher.COLUMN_IPV6_FLOW_LABEL, match.get(MatchField.IPV6_FLABEL).toString());
				break;	
			case ICMPV6_TYPE:				
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP6_TYPE, String.valueOf(match.get(MatchField.ICMPV6_TYPE).getValue()));
				break;
			case ICMPV6_CODE:				
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP6_CODE, match.get(MatchField.ICMPV6_CODE).getValue());
				break;
			case IPV6_ND_SLL:			
				entry.put(StaticFlowEntryPusher.COLUMN_ND_SLL, match.get(MatchField.IPV6_ND_SLL).toString());
				break;
			case IPV6_ND_TLL:				
				entry.put(StaticFlowEntryPusher.COLUMN_ND_TLL, match.get(MatchField.IPV6_ND_TLL).toString());
				break;	
			case IPV6_ND_TARGET:				
				entry.put(StaticFlowEntryPusher.COLUMN_ND_TARGET, match.get(MatchField.IPV6_ND_TARGET).toString());
				break;					
			case MPLS_LABEL:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_LABEL, match.get(MatchField.MPLS_LABEL).getValue());
				break;
			case MPLS_TC:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_TC, match.get(MatchField.MPLS_TC).getValue());
				break;
			case MPLS_BOS:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_BOS, match.get(MatchField.MPLS_BOS).getValue());
				break;			
			case METADATA:
				entry.put(StaticFlowEntryPusher.COLUMN_METADATA, match.get(MatchField.METADATA).getValue().getValue());
				break;
			case TUNNEL_ID:
				entry.put(StaticFlowEntryPusher.COLUMN_TUNNEL_ID, match.get(MatchField.TUNNEL_ID).getValue());
				break;				
			// case PBB_ISID not implemented in loxi
			default:
				log.error("Unhandled Match when parsing OFFlowMod: {}, {}", mf, mf.id);
				break;
			} // end switch-case
		} // end while
				
		int result = StaticFlowEntryPusherResource.checkActions(entry);
		if (result == -1)
			throw new Exception("Invalid action/instructions");
		
		return entry;
	}

	/**
	 * Turns a JSON formatted Static Flow Pusher string into a storage entry
	 * Expects a string in JSON along the lines of:
	 *        {
	 *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
	 *            "name":         "flow-mod-1",
	 *            "cookie":       "0",
	 *            "priority":     "32768",
	 *            "ingress-port": "1",
	 *            "actions":      "output=2",
	 *        }
	 * @param fmJson The JSON formatted static flow pusher entry
	 * @return The map of the storage entry
	 * @throws IOException If there was an error parsing the JSON
	 */
	public static Map<String, Object> jsonToStorageEntry(String fmJson) throws IOException {
		Map<String, Object> entry = new HashMap<String, Object>();
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		
		String tpSrcPort = "NOT_SPECIFIED";
		String tpDstPort = "NOT_SPECIFIED";
		String ipProto = "NOT_SPECIFIED";

		try {
			jp = f.createJsonParser(fmJson);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
				throw new IOException("Expected FIELD_NAME");
			}

			String n = jp.getCurrentName();
			jp.nextToken();

			// Java 7 switch-case on strings automatically checks for (deep) string equality.
			// IMHO, this makes things easier on the eyes than if, else if, else's, and it
			// seems to be more efficient than walking through a long list of if-else-ifs

			// A simplification is to make the column names the same strings as those used to
			// compose the JSON flow entry; keeps all names/keys centralized and reduces liklihood
			// for future string errors.
			switch (n) {
			case StaticFlowEntryPusher.COLUMN_NAME:
				entry.put(StaticFlowEntryPusher.COLUMN_NAME, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_SWITCH:
				entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TABLE_ID:
				entry.put(StaticFlowEntryPusher.COLUMN_TABLE_ID, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ACTIVE:
				entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_IDLE_TIMEOUT:
				entry.put(StaticFlowEntryPusher.COLUMN_IDLE_TIMEOUT, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_HARD_TIMEOUT:
				entry.put(StaticFlowEntryPusher.COLUMN_HARD_TIMEOUT, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_PRIORITY:
				entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_COOKIE: // set manually, or computed from name
				entry.put(StaticFlowEntryPusher.COLUMN_COOKIE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_IN_PORT:
				entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_VLAN:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_TYPE:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_TOS: // only valid for OF1.0; all other should specify specifics (ECN and/or DSCP bits)
				entry.put(StaticFlowEntryPusher.COLUMN_NW_TOS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_ECN:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_ECN, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_DSCP:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_DSCP, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_PROTO:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, jp.getText());
				ipProto = jp.getText();
				break;
			case StaticFlowEntryPusher.COLUMN_NW_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_SCTP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_SCTP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_UDP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_UDP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TCP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TCP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TP_SRC: // support for OF1.0 generic transport ports
				entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, jp.getText());
				tpSrcPort = jp.getText();
				break;
			case StaticFlowEntryPusher.COLUMN_TP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_TP_DST, jp.getText());
				tpDstPort = jp.getText();
				break;
			case StaticFlowEntryPusher.COLUMN_ICMP_TYPE:
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP_TYPE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ICMP_CODE:
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP_CODE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_OPCODE:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_OPCODE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_SHA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_SHA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_DHA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_DHA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_SPA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_SPA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_DPA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_DPA, jp.getText());
				break;		
			case StaticFlowEntryPusher.COLUMN_NW6_SRC:				
				entry.put(StaticFlowEntryPusher.COLUMN_NW6_SRC, jp.getText());
				break;	
			case StaticFlowEntryPusher.COLUMN_NW6_DST:				
				entry.put(StaticFlowEntryPusher.COLUMN_NW6_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_IPV6_FLOW_LABEL:								
				entry.put(StaticFlowEntryPusher.COLUMN_IPV6_FLOW_LABEL, jp.getText());
				break;	
			case StaticFlowEntryPusher.COLUMN_ICMP6_TYPE:				
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP6_TYPE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ICMP6_CODE:						
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP6_CODE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ND_SLL:				
				entry.put(StaticFlowEntryPusher.COLUMN_ND_SLL, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ND_TLL:			
				entry.put(StaticFlowEntryPusher.COLUMN_ND_TLL, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ND_TARGET:					
				entry.put(StaticFlowEntryPusher.COLUMN_ND_TARGET, jp.getText());
				break;				
			case StaticFlowEntryPusher.COLUMN_MPLS_LABEL:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_LABEL, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_MPLS_TC:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_TC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_MPLS_BOS:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_BOS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_METADATA:
				entry.put(StaticFlowEntryPusher.COLUMN_METADATA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TUNNEL_ID:
				entry.put(StaticFlowEntryPusher.COLUMN_TUNNEL_ID, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_PBB_ISID: // not supported as match in loxi right now
				entry.put(StaticFlowEntryPusher.COLUMN_PBB_ISID, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ACTIONS:
				entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, jp.getText());
				break;
				
			/* 
			 * All OF1.1+ instructions.
			 */
			case StaticFlowEntryPusher.COLUMN_INSTR_APPLY_ACTIONS:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_APPLY_ACTIONS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_INSTR_WRITE_ACTIONS:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_ACTIONS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_INSTR_CLEAR_ACTIONS:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_CLEAR_ACTIONS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_INSTR_GOTO_METER:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_METER, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_INSTR_GOTO_TABLE:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_TABLE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_INSTR_WRITE_METADATA:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_METADATA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_INSTR_EXPERIMENTER:
				entry.put(StaticFlowEntryPusher.COLUMN_INSTR_EXPERIMENTER, jp.getText());
				break;
			default:
				log.error("Could not decode field from JSON string: {}", n);
				break;
			}  
		} 
		
		// For OF1.0, transport ports are specified using generic tp_src, tp_dst type strings.
		// Once the whole json string has been parsed, find out the IpProto to properly assign the ports.
		// If IpProto not specified, print error, and make sure all TP columns are clear.
		if (ipProto.equalsIgnoreCase("tcp")) {
			if (!tpSrcPort.equals("NOT_SPECIFIED")) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_SRC);
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_SRC, tpSrcPort);
			}
			if (!tpDstPort.equals("NOT_SPECIFIED")) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_DST);
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_DST, tpDstPort);
			}
		} else if (ipProto.equalsIgnoreCase("udp")) {
			if (!tpSrcPort.equals("NOT_SPECIFIED")) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_SRC);
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_SRC, tpSrcPort);
			}
			if (!tpDstPort.equals("NOT_SPECIFIED")) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_DST);
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_DST, tpDstPort);
			}
		} else if (ipProto.equalsIgnoreCase("sctp")) {
			if (!tpSrcPort.equals("NOT_SPECIFIED")) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_SRC);
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_SRC, tpSrcPort);
			}
			if (!tpDstPort.equals("NOT_SPECIFIED")) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_DST);
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_DST, tpDstPort);
			}
		} else {
			log.debug("Got IP protocol of '{}' and tp-src of '{}' and tp-dst of '" + tpDstPort + "' via SFP REST API", ipProto, tpSrcPort);
		}

		return entry;
	}   
}

