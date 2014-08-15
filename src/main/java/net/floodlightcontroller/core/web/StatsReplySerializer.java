package net.floodlightcontroller.core.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionEnqueue;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPopMpls;
import org.projectfloodlight.openflow.protocol.action.OFActionPopPbb;
import org.projectfloodlight.openflow.protocol.action.OFActionPopVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionPushMpls;
import org.projectfloodlight.openflow.protocol.action.OFActionPushPbb;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanPcp;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanVid;
import org.projectfloodlight.openflow.protocol.action.OFActionStripVlan;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionExperimenter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmArpOp;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmArpSha;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmArpSpa;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmArpTha;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmArpTpa;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmEthDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmEthSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmEthType;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIcmpv4Code;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIcmpv4Type;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpDscp;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpEcn;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpProto;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv4Dst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv4Src;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMetadata;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMplsLabel;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMplsTc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmSctpDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmSctpSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmTcpDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmTcpSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmUdpDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmUdpSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanPcp;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVid;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class StatsReplySerializer extends JsonSerializer<Map<String, Object>> {

	@Override
	public void serialize(Map<String, Object> statsReply, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
		// Map<String, Object> ==> Map<Switch-DPID-String, List<OFStatsReply>>
		// It should be safe to cast the Object as a List of OFStatsReply's
		String dpidStr = statsReply.keySet().iterator().next(); // there's got to be a simpler way to get the key
		List<OFStatsReply> srList = (List<OFStatsReply>) statsReply.get(dpidStr);

		for (OFStatsReply sr : srList) {
			OFStatsType t = sr.getStatsType();
			switch (t) {
			case FLOW:
				List<OFFlowStatsEntry> entries = ((OFFlowStatsReply) sr).getEntries();
				for (OFFlowStatsEntry entry : entries) {
					// list flow stats/info
					jsonGenerator.writeStartObject();
					jsonGenerator.writeStringField("switch", dpidStr);
					jsonGenerator.writeStartObject();
					jsonGenerator.writeStringField("version", entry.getVersion().toString()); // return the enum name
					jsonGenerator.writeNumberField("cookie", entry.getCookie().getValue());
					jsonGenerator.writeNumberField("table-id", entry.getTableId().getValue());
					jsonGenerator.writeNumberField("packet-count", entry.getPacketCount().getValue());
					jsonGenerator.writeNumberField("byte-count", entry.getByteCount().getValue());
					jsonGenerator.writeNumberField("duration-sec", entry.getDurationSec());
					jsonGenerator.writeNumberField("priority", entry.getPriority());
					jsonGenerator.writeNumberField("idle-timeout-sec", entry.getIdleTimeout());
					jsonGenerator.writeNumberField("hard-timeout-sec", entry.getHardTimeout());
					jsonGenerator.writeNumberField("flags", entry.getFlags());
					// list flow matches
					jsonGenerator.writeObjectFieldStart("match"); // TODO does this create the object already?
					jsonGenerator.writeStartObject();
					Match m = entry.getMatch();
					if (m.get(MatchField.IN_PORT) != null) {
						jsonGenerator.writeNumberField("in-port", m.get(MatchField.IN_PORT).getPortNumber());
					}
					if (m.get(MatchField.IN_PHY_PORT) != null) {
						jsonGenerator.writeNumberField("in-phy-port", m.get(MatchField.IN_PHY_PORT).getPortNumber());
					}
					if (m.get(MatchField.ARP_OP) != null) {
						jsonGenerator.writeNumberField("arp-op", m.get(MatchField.ARP_OP).getOpcode());
					}
					if (m.get(MatchField.ARP_SHA) != null) {
						jsonGenerator.writeStringField("arp-sha", m.get(MatchField.ARP_SHA).toString());
					}
					if (m.get(MatchField.ARP_SPA) != null) {
						jsonGenerator.writeStringField("arp-spa", m.get(MatchField.ARP_SPA).toString());
					}
					if (m.get(MatchField.ARP_THA) != null) {
						jsonGenerator.writeStringField("arp-tha", m.get(MatchField.ARP_THA).toString());
					}
					if (m.get(MatchField.ARP_TPA) != null) {
						jsonGenerator.writeStringField("arp-tpa", m.get(MatchField.ARP_TPA).toString());
					}
					if (m.get(MatchField.ETH_TYPE) != null) {
						jsonGenerator.writeNumberField("eth-type", m.get(MatchField.ETH_TYPE).getValue());
					}
					if (m.get(MatchField.ETH_SRC) != null) {
						jsonGenerator.writeStringField("eth-src", m.get(MatchField.ETH_SRC).toString());
					}
					if (m.get(MatchField.ETH_DST) != null) {
						jsonGenerator.writeStringField("eth-dst", m.get(MatchField.ETH_DST).toString());
					}
					if (m.get(MatchField.VLAN_VID) != null) {
						jsonGenerator.writeNumberField("vlan-vid", m.get(MatchField.VLAN_VID).getVlan());
					}
					if (m.get(MatchField.VLAN_PCP) != null) {
						jsonGenerator.writeNumberField("vlan-pcp", m.get(MatchField.VLAN_PCP).getValue()); // TODO not sure if this will format correctly
					}
					if (m.get(MatchField.ICMPV4_TYPE) != null) {
						jsonGenerator.writeNumberField("icmpv4-type", m.get(MatchField.ICMPV4_TYPE).getType());
					}
					if (m.get(MatchField.ICMPV4_CODE) != null) {
						jsonGenerator.writeNumberField("icmpv4-code", m.get(MatchField.ICMPV4_CODE).getCode());
					}
					if (m.get(MatchField.ICMPV6_TYPE) != null) {
						jsonGenerator.writeNumberField("icmpv6-type", m.get(MatchField.ICMPV6_TYPE).getValue());
					}
					if (m.get(MatchField.ICMPV6_CODE) != null) {
						jsonGenerator.writeNumberField("icmpv6-code", m.get(MatchField.ICMPV6_CODE).getValue());
					}
					if (m.get(MatchField.IN_PORT) != null) {
						jsonGenerator.writeNumberField("in-port", m.get(MatchField.IN_PORT).getPortNumber());
					}
					if (m.get(MatchField.IP_DSCP) != null) {
						jsonGenerator.writeNumberField("ip-dscp", m.get(MatchField.IP_DSCP).getDscpValue()); // TODO not sure if this will format correctly
					}
					if (m.get(MatchField.IP_ECN) != null) {
						jsonGenerator.writeNumberField("ip-ecn", m.get(MatchField.IP_ECN).getEcnValue()); // TODO not sure if this will format correctly
					}
					if (m.get(MatchField.IP_PROTO) != null) {
						jsonGenerator.writeNumberField("ip-proto", m.get(MatchField.IP_PROTO).getIpProtocolNumber());
					}
					if (m.get(MatchField.IPV4_SRC) != null) {
						jsonGenerator.writeStringField("ipv4-src", m.get(MatchField.IPV4_SRC).toString());
					}
					if (m.get(MatchField.IPV4_DST) != null) {
						jsonGenerator.writeStringField("ipv4-dst", m.get(MatchField.IPV4_DST).toString());
					}
					if (m.get(MatchField.IPV6_SRC) != null) {
						jsonGenerator.writeStringField("ipv6-src", m.get(MatchField.IPV6_SRC).toString());
					}
					if (m.get(MatchField.IPV6_DST) != null) {
						jsonGenerator.writeStringField("ipv6-dst", m.get(MatchField.IPV6_DST).toString());
					}
					if (m.get(MatchField.IPV6_FLABEL) != null) {
						jsonGenerator.writeNumberField("ipv6-flow-label", m.get(MatchField.IPV6_FLABEL).getIPv6FlowLabelValue());
					}
					if (m.get(MatchField.IPV6_ND_SLL) != null) {
						jsonGenerator.writeNumberField("ipv6-nd-ssl", m.get(MatchField.IPV6_ND_SLL).getLong());
					}
					if (m.get(MatchField.IPV6_ND_TARGET) != null) {
						jsonGenerator.writeNumberField("ipv6-nd-target", m.get(MatchField.IPV6_ND_TARGET).getZeroCompressStart()); // TODO not sure how to format this
					}
					if (m.get(MatchField.IPV6_ND_TLL) != null) {
						jsonGenerator.writeNumberField("ipv6-nd-ttl", m.get(MatchField.IPV6_ND_TLL).getLong());
					}
					if (m.get(MatchField.METADATA) != null) {
						jsonGenerator.writeNumberField("metadata", m.get(MatchField.METADATA).getValue().getValue());
					}
					if (m.get(MatchField.MPLS_LABEL) != null) {
						jsonGenerator.writeNumberField("mpls-label", m.get(MatchField.MPLS_LABEL).getValue());
					}
					if (m.get(MatchField.MPLS_TC) != null) {
						jsonGenerator.writeNumberField("mpls-tc", m.get(MatchField.MPLS_TC).getValue());
					}
					if (m.get(MatchField.SCTP_SRC) != null) {
						jsonGenerator.writeNumberField("sctp-src", m.get(MatchField.SCTP_SRC).getPort());
					}
					if (m.get(MatchField.SCTP_DST) != null) {
						jsonGenerator.writeNumberField("sctp-dst", m.get(MatchField.SCTP_DST).getPort());
					}
					if (m.get(MatchField.TCP_SRC) != null) {
						jsonGenerator.writeNumberField("tcp-src", m.get(MatchField.TCP_SRC).getPort());
					}
					if (m.get(MatchField.TCP_DST) != null) {
						jsonGenerator.writeNumberField("tcp-dst", m.get(MatchField.TCP_DST).getPort());
					}
					if (m.get(MatchField.UDP_SRC) != null) {
						jsonGenerator.writeNumberField("udp-src", m.get(MatchField.UDP_SRC).getPort());
					}
					if (m.get(MatchField.UDP_DST) != null) {
						jsonGenerator.writeNumberField("udp-dst", m.get(MatchField.UDP_DST).getPort());
					}
					jsonGenerator.writeEndObject(); // end match

					// begin actions/instructions
					if (entry.getVersion() == OFVersion.OF_10) {
						List<OFAction> actions = entry.getActions();
						jsonGenerator.writeObjectFieldStart("actions");
						jsonGenerator.writeStartObject();
						if (actions.isEmpty()) {
							jsonGenerator.writeString("none/drop");
						}
						for (OFAction a : actions) {
							switch (a.getType()) {
							case OUTPUT:
								jsonGenerator.writeNumberField("output", ((OFActionOutput)a).getPort().getPortNumber());
								break;
							case ENQUEUE:
								jsonGenerator.writeNumberField("enqueue", ((OFActionEnqueue)a).getPort().getPortNumber());
								break;
							case SET_DL_SRC:
								jsonGenerator.writeStringField("eth-src", ((OFActionSetDlSrc)a).toString());
								break;
							case SET_DL_DST:
								jsonGenerator.writeStringField("eth-dst", ((OFActionSetDlDst)a).toString());
								break;
							case SET_VLAN_VID:
								jsonGenerator.writeNumberField("vlan-vid", ((OFActionSetVlanVid)a).getVlanVid().getVlan());
								break;
							case SET_VLAN_PCP:
								jsonGenerator.writeNumberField("vlan-pcp", ((OFActionSetVlanPcp)a).getVlanPcp().getValue());
								break;
							case STRIP_VLAN:
								jsonGenerator.writeString("strip-vlan");
								break;
							case SET_NW_SRC:
								jsonGenerator.writeStringField("ipv4-src", ((OFActionSetNwSrc)a).getNwAddr().toString());
								break;
							case SET_NW_DST:
								jsonGenerator.writeStringField("ipv4-dst", ((OFActionSetNwDst)a).getNwAddr().toString());
								break;
							case SET_TP_SRC:
								jsonGenerator.writeNumberField("tp-src", ((OFActionSetTpSrc)a).getTpPort().getPort());
								break;
							case SET_TP_DST:
								jsonGenerator.writeNumberField("tp-dst", ((OFActionSetTpDst)a).getTpPort().getPort());
								break;
							default:
								// these are all the valid OF1.0 actions
								break;
							}
						}
						jsonGenerator.writeEndObject(); // end actions
					} else {
						// handle OF1.1+ instructions with actions within
						List<OFInstruction> instructions = entry.getInstructions();
						jsonGenerator.writeArrayFieldStart("instructions");
						jsonGenerator.writeStartArray(); // array of instructions, each which have objects
						if (instructions.isEmpty()) {
							jsonGenerator.writeStringField("none", "drop");
						} else {
							for (OFInstruction i : instructions) {
								switch (i.getType()) {
								case CLEAR_ACTIONS:
									jsonGenerator.writeObjectFieldStart("clear-actions");
									break;
								case WRITE_METADATA:
									jsonGenerator.writeStartObject();
									jsonGenerator.writeNumberField("write-metadata", ((OFInstructionWriteMetadata)i).getMetadata().getValue());
									jsonGenerator.writeNumberField("mask", ((OFInstructionWriteMetadata)i).getMetadataMask().getValue());
									break;
								case EXPERIMENTER:
									jsonGenerator.writeStartObject();
									jsonGenerator.writeNumberField("experimenter", ((OFInstructionExperimenter)i).getExperimenter());
									break;
								case GOTO_TABLE:
									jsonGenerator.writeStartObject();
									jsonGenerator.writeNumberField("goto-table", ((OFInstructionGotoTable)i).getTableId().getValue());
									break;
								case METER:
									jsonGenerator.writeStartObject();
									jsonGenerator.writeNumberField("meter", ((OFInstructionMeter)i).getMeterId());
									break;
								case APPLY_ACTIONS:
									jsonGenerator.writeObjectFieldStart("apply-actions");
									serializeActions(jsonGenerator, ((OFInstructionApplyActions)i).getActions()); // side effect: jsonGenerator appended to
									break;
								case WRITE_ACTIONS:
									jsonGenerator.writeObjectFieldStart("write-actions");
									serializeActions(jsonGenerator, ((OFInstructionWriteActions)i).getActions()); // side effect: jsonGenerator appended to
								default:
									// shouldn't ever get here
									break;
								} // end switch on instruction
								jsonGenerator.writeEndObject(); // end specific instruction
							} // end for instructions
						} // end not-empty instructions (else)
					} // end process OF1.1+ actions (i.e. look through instructions)
				} // end for each OFFlowStatsReply entry
			} // end case for FLOW
		} // end for each OFStatsReply
	} // end method

	public static void serializeActions(JsonGenerator jsonGenerator, List<OFAction> actions) throws IOException, JsonProcessingException {
		jsonGenerator.writeStartObject();
		if (actions.isEmpty()) {
			jsonGenerator.writeStringField("none", "drop");
		}
		for (OFAction a : actions) { // these should only be OF1.1+ (i.e. OF1.3 supported actions, set-field with oxm's, etc)
			switch (a.getType()) {
			case OUTPUT:
				jsonGenerator.writeNumberField("output", ((OFActionOutput)a).getPort().getPortNumber());
				break;
			case SET_QUEUE:
				jsonGenerator.writeNumberField("queue", ((OFActionSetQueue)a).getQueueId());
				break;
			case GROUP:
				jsonGenerator.writeNumberField("group", ((OFActionGroup)a).getGroup().getGroupNumber());
				break;
			case PUSH_VLAN:
				jsonGenerator.writeNumberField("push-vlan", ((OFActionPushVlan)a).getEthertype().getValue());
				break;
			case PUSH_MPLS:
				jsonGenerator.writeNumberField("push-mpls", ((OFActionPushMpls)a).getEthertype().getValue());
				break;
			case PUSH_PBB:
				jsonGenerator.writeNumberField("push-pbb", ((OFActionPushPbb)a).getEthertype().getValue());
				break;
			case POP_VLAN:
				jsonGenerator.writeString("pop-vlan");
				break;
			case POP_MPLS:
				jsonGenerator.writeNumberField("pop-mpls", ((OFActionPopMpls)a).getEthertype().getValue());
				break;
			case POP_PBB:
				jsonGenerator.writeString("pop-pbb");
				break;
			case SET_FIELD:
				if (((OFActionSetField)a).getField() instanceof OFOxmArpOp) {
					jsonGenerator.writeNumberField("arp-op", ((OFOxmArpOp) ((OFActionSetField) a).getField()).getValue().getOpcode());
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpSha) {
					jsonGenerator.writeStringField("arp-sha", ((OFOxmArpSha) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpTha) {
					jsonGenerator.writeStringField("arp-tha", ((OFOxmArpTha) ((OFActionSetField) a).getField()).getValue().toString());
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpSpa) {
					jsonGenerator.writeStringField("arp-spa", ((OFOxmArpSpa) ((OFActionSetField) a).getField()).getValue().toString()); // ipaddress formats string already
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpTpa) {
					jsonGenerator.writeStringField("arp-tpa", ((OFOxmArpTpa) ((OFActionSetField) a).getField()).getValue().toString()); 
				} 
				/* DATA LAYER */
				else if (((OFActionSetField)a).getField() instanceof OFOxmEthType) {
					jsonGenerator.writeNumberField("eth-type", ((OFOxmEthType) ((OFActionSetField) a).getField()).getValue().getValue());
				} else if (((OFActionSetField)a).getField() instanceof OFOxmEthSrc) {
					jsonGenerator.writeStringField("eth-src", ((OFOxmEthSrc) ((OFActionSetField) a).getField()).getValue().toString());
				} else if (((OFActionSetField)a).getField() instanceof OFOxmEthDst) {
					jsonGenerator.writeStringField("eth-dst", ((OFOxmEthDst) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmVlanVid) {
					jsonGenerator.writeNumberField("vlan-vid", ((OFOxmVlanVid) ((OFActionSetField) a).getField()).getValue().getVlan()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmVlanPcp) {
					jsonGenerator.writeNumberField("vlan-pcp", ((OFOxmVlanPcp) ((OFActionSetField) a).getField()).getValue().getValue()); 
				} 
				/* ICMP */
				else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Code) {
					jsonGenerator.writeNumberField("icmpv4-code", ((OFOxmIcmpv4Code) ((OFActionSetField) a).getField()).getValue().getCode()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Type) {
					jsonGenerator.writeNumberField("icmpv4-type", ((OFOxmIcmpv4Type) ((OFActionSetField) a).getField()).getValue().getType()); 
				} 
				/* NETWORK LAYER */
				else if (((OFActionSetField)a).getField() instanceof OFOxmIpProto) {
					jsonGenerator.writeNumberField("ip-proto", ((OFOxmIpProto) ((OFActionSetField) a).getField()).getValue().getIpProtocolNumber()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Src) {
					jsonGenerator.writeStringField("ipv4-src", ((OFOxmIpv4Src) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Dst) {
					jsonGenerator.writeStringField("ipv4-dst", ((OFOxmIpv4Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpEcn) {
					jsonGenerator.writeNumberField("ip-ecn", ((OFOxmIpEcn) ((OFActionSetField) a).getField()).getValue().getEcnValue()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpDscp) {
					jsonGenerator.writeNumberField("ip-dscp", ((OFOxmIpDscp) ((OFActionSetField) a).getField()).getValue().getDscpValue()); 
				} 
				/* TRANSPORT LAYER, TCP, UDP, and SCTP */
				else if (((OFActionSetField)a).getField() instanceof OFOxmTcpSrc) {
					jsonGenerator.writeNumberField("tcp-src", ((OFOxmTcpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmTcpDst) {
					jsonGenerator.writeNumberField("tcp-dst", ((OFOxmTcpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmUdpSrc) {
					jsonGenerator.writeNumberField("udp-src", ((OFOxmUdpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmUdpDst) {
					jsonGenerator.writeNumberField("udp-dst", ((OFOxmUdpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmSctpSrc) {
					jsonGenerator.writeNumberField("sctp-src", ((OFOxmSctpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmSctpDst) {
					jsonGenerator.writeNumberField("sctp-dst", ((OFOxmSctpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
				}
				/* MPLS */
				else if (((OFActionSetField)a).getField() instanceof OFOxmMplsLabel) {
					jsonGenerator.writeNumberField("mpls-label", ((OFOxmMplsLabel) ((OFActionSetField) a).getField()).getValue().getValue()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmMplsTc) {
					jsonGenerator.writeNumberField("mpls-tc", ((OFOxmMplsTc) ((OFActionSetField) a).getField()).getValue().getValue()); 
				} // MPLS_BOS not implemented in loxi
				/* METADATA */
				else if (((OFActionSetField)a).getField() instanceof OFOxmMetadata) {
					jsonGenerator.writeNumberField("metadata", ((OFOxmMetadata) ((OFActionSetField) a).getField()).getValue().getValue().getValue()); 
				} else {
					// need to get a logger in here somehow log.error("Could not decode Set-Field action field: {}", ((OFActionSetField) a));
				}
			}
		}
	}
}

