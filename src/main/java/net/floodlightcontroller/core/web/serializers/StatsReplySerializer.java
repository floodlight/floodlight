/**
 *    Copyright 2011,2012 Big Switch Networks, Inc.
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

package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.List;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.core.web.OFStatsTypeStrings;
import net.floodlightcontroller.core.web.StatsReply;
import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.InstructionUtils;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;

//Use the loxigen's serializer
import org.projectfloodlight.openflow.protocol.ver13.OFPortFeaturesSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver12.OFPortFeaturesSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFPortFeaturesSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFPortFeaturesSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver13.OFPortStateSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver12.OFPortStateSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFPortStateSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFPortStateSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver13.OFPortConfigSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver12.OFPortConfigSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFPortConfigSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFPortConfigSerializerVer10;

import org.projectfloodlight.openflow.protocol.OFAggregateStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.*;
import org.projectfloodlight.openflow.protocol.oxm.*;
import org.projectfloodlight.openflow.protocol.instruction.*;
import org.projectfloodlight.openflow.protocol.action.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Serialize a DPID as colon-separated hexadecimal
 */
public class StatsReplySerializer extends JsonSerializer<StatsReply> {
    protected static Logger logger = LoggerFactory.getLogger(StatsReplySerializer.class);
    @SuppressWarnings("unchecked")
    @Override
    public void serialize(StatsReply reply, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
        // Return a nice error to user if the request we're about to serialize was bad
        if (reply.getValues() == null) {
            jGen.writeStartObject();
            jGen.writeObjectFieldStart("ERROR");
            jGen.writeStringField("1)", "Invalid DPID and/or stats/features request, or");
            jGen.writeStringField("2)", "The switch might also be disconncted from the controller");
            jGen.writeEndObject();
            jGen.writeObjectFieldStart("Valid OFStatsTypes are");
            jGen.writeStringField("1)", OFStatsTypeStrings.AGGREGATE);
            jGen.writeStringField("2)", OFStatsTypeStrings.DESC);
            jGen.writeStringField("3)", OFStatsTypeStrings.EXPERIMENTER);
            jGen.writeStringField("4)", OFStatsTypeStrings.FEATURES);
            jGen.writeStringField("5)", OFStatsTypeStrings.FLOW);
            jGen.writeStringField("6)", OFStatsTypeStrings.GROUP);
            jGen.writeStringField("7)", OFStatsTypeStrings.GROUP_DESC);
            jGen.writeStringField("8)", OFStatsTypeStrings.GROUP_FEATURES);  
            jGen.writeStringField("9)", OFStatsTypeStrings.METER);  
            jGen.writeStringField("A)", OFStatsTypeStrings.METER_CONFIG); 
            jGen.writeStringField("B)", OFStatsTypeStrings.PORT);
            jGen.writeStringField("C)", OFStatsTypeStrings.PORT_DESC);
            jGen.writeStringField("D)", OFStatsTypeStrings.QUEUE);
            jGen.writeStringField("E)", OFStatsTypeStrings.TABLE);
            jGen.writeStringField("F)", OFStatsTypeStrings.TABLE_FEATURES);
            jGen.writeEndObject(); 
            jGen.writeEndObject();
            return;
        }
        
        jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true); // IMHO this just looks nicer and is easier to read if everything is quoted
        jGen.writeStartObject();
        jGen.writeStringField("dpid", reply.getDatapathId().toString());
        switch (reply.getStatType()) {
        case PORT:
            // handle port
			serializePortReply((List<OFPortStatsReply>) reply.getValues(), jGen);
            break;
        case QUEUE:
            // handle queue
            break;
        case FLOW:
            // handle flow. Can safely cast to List<OFFlowStatsReply>.
            serializeFlowReply((List<OFFlowStatsReply>) reply.getValues(), jGen);
            break;
        case AGGREGATE:
            // handle aggregate
            serializeAggregateReply((List<OFAggregateStatsReply>) reply.getValues(), jGen);
            break;
        case DESC:
            // handle desc
            serializeDescReply((List<OFDescStatsReply>) reply.getValues(), jGen);
            break;
        case TABLE:
            // handle table
            break;
        case TABLE_FEATURES:
            // handle features
            break;
            // TODO need to handle new OF1.1+ stats reply types
        case EXPERIMENTER:
            break;
        case GROUP:
            break;
        case GROUP_DESC:
            break;
        case GROUP_FEATURES:
            break;
        case METER:
            break;
        case METER_CONFIG:
            break;
        case METER_FEATURES:
            break;
        case PORT_DESC:
			serializePortDescReply((List<OFPortDescStatsReply>) reply.getValues(), jGen);
            break;
        default:
             break;
        }   
        jGen.writeEndObject();
    }

    public void serializePortReply(List<OFPortStatsReply> portReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
		OFPortStatsReply portReply = portReplies.get(0); // we will get only one PortReply and it will contains many OFPortStatsEntry ?
        jGen.writeStringField("version", portReply.getVersion().toString()); //return the enum name
		jGen.writeFieldName("port");
		jGen.writeStartArray();
		for(OFPortStatsEntry entry : portReply.getEntries()) {
			jGen.writeStartObject();
			jGen.writeStringField("portNumber",entry.getPortNo().toString());
			jGen.writeNumberField("receivePackets", entry.getRxPackets().getValue());
			jGen.writeNumberField("transmitPackets", entry.getTxPackets().getValue());
			jGen.writeNumberField("receiveBytes", entry.getRxBytes().getValue());
			jGen.writeNumberField("transmitBytes", entry.getTxBytes().getValue());
			jGen.writeNumberField("receiveDropped", entry.getRxDropped().getValue());
			jGen.writeNumberField("transmitDropped", entry.getTxDropped().getValue());
			jGen.writeNumberField("receiveErrors", entry.getRxErrors().getValue());
			jGen.writeNumberField("transmitErrors", entry.getTxErrors().getValue());
			jGen.writeNumberField("receiveFrameErrors", entry.getRxFrameErr().getValue());
			jGen.writeNumberField("receiveOverrunErrors", entry.getRxOverErr().getValue());
			jGen.writeNumberField("receiveCRCErrors", entry.getRxCrcErr().getValue());
			jGen.writeNumberField("collisions", entry.getCollisions().getValue());
			if (OFVersion.OF_13 == entry.getVersion()) {
				jGen.writeNumberField("durationSec", entry.getDurationSec());
				jGen.writeNumberField("durationNsec", entry.getDurationNsec());
			}
			jGen.writeEndObject();
		}
		jGen.writeEndArray();
	}
    public void serializeFlowReply(List<OFFlowStatsReply> flowReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        int flowCount = 0;
        for (OFFlowStatsReply flowReply : flowReplies) { // for each flow stats reply
            List<OFFlowStatsEntry> entries = flowReply.getEntries();
            for (OFFlowStatsEntry entry : entries) { // for each flow
                // list flow stats/info
                jGen.writeObjectFieldStart("flow" + Integer.toString(flowCount++)); // need to have different object names or JSON parser might only show the last one
                jGen.writeStringField("version", entry.getVersion().toString()); // return the enum name
                jGen.writeNumberField("cookie", entry.getCookie().getValue());
                jGen.writeNumberField("table_id", entry.getTableId().getValue());
                jGen.writeNumberField("packet_count", entry.getPacketCount().getValue());
                jGen.writeNumberField("byte_count", entry.getByteCount().getValue());
                jGen.writeNumberField("duration_sec", entry.getDurationSec());
                jGen.writeNumberField("priority", entry.getPriority());
                jGen.writeNumberField("idle_timeout_sec", entry.getIdleTimeout());
                jGen.writeNumberField("hard_timeout_sec", entry.getHardTimeout());
                jGen.writeNumberField("flags", entry.getFlags());
                // list flow matches
                jGen.writeObjectFieldStart("match");
                Iterator<MatchField<?>> mi = entry.getMatch().getMatchFields().iterator(); // get iter to any match field type
                Match m = entry.getMatch();

                while (mi.hasNext()) {
                    MatchField<?> mf = mi.next();
                    switch (mf.id) {
                    case IN_PORT:
                        jGen.writeNumberField(MatchUtils.STR_IN_PORT, m.get(MatchField.IN_PORT).getPortNumber());
                        break;
                    case IN_PHY_PORT:
                        jGen.writeNumberField(MatchUtils.STR_IN_PHYS_PORT, m.get(MatchField.IN_PHY_PORT).getPortNumber());
                        break;
                    case ARP_OP:
                        jGen.writeNumberField(MatchUtils.STR_ARP_OPCODE, m.get(MatchField.ARP_OP).getOpcode());
                        break;
                    case ARP_SHA:
                        jGen.writeStringField(MatchUtils.STR_ARP_SHA, m.get(MatchField.ARP_SHA).toString());
                        break;
                    case ARP_SPA:
                        jGen.writeStringField(MatchUtils.STR_ARP_SPA, m.get(MatchField.ARP_SPA).toString());
                        break;
                    case ARP_THA:
                        jGen.writeStringField(MatchUtils.STR_ARP_DHA, m.get(MatchField.ARP_THA).toString());
                        break;
                    case ARP_TPA:
                        jGen.writeStringField(MatchUtils.STR_ARP_DPA, m.get(MatchField.ARP_TPA).toString());
                        break;
                    case ETH_TYPE:
                        jGen.writeNumberField(MatchUtils.STR_DL_TYPE, m.get(MatchField.ETH_TYPE).getValue());
                        break;
                    case ETH_SRC:
                        jGen.writeStringField(MatchUtils.STR_DL_SRC, m.get(MatchField.ETH_SRC).toString());
                        break;
                    case ETH_DST:
                        jGen.writeStringField(MatchUtils.STR_DL_DST, m.get(MatchField.ETH_DST).toString());
                        break;
                    case VLAN_VID:
                        jGen.writeNumberField(MatchUtils.STR_DL_VLAN, m.get(MatchField.VLAN_VID).getVlan());
                        break;
                    case VLAN_PCP:
                        jGen.writeNumberField(MatchUtils.STR_DL_VLAN_PCP, m.get(MatchField.VLAN_PCP).getValue());
                        break;
                    case ICMPV4_TYPE:
                        jGen.writeNumberField(MatchUtils.STR_ICMP_TYPE, m.get(MatchField.ICMPV4_TYPE).getType());
                        break;
                    case ICMPV4_CODE:
                        jGen.writeNumberField(MatchUtils.STR_ICMP_CODE, m.get(MatchField.ICMPV4_CODE).getCode());
                        break;
                    case ICMPV6_TYPE:
                        jGen.writeNumberField(MatchUtils.STR_ICMPV6_TYPE, m.get(MatchField.ICMPV6_TYPE).getValue());
                        break;
                    case ICMPV6_CODE:
                        jGen.writeNumberField(MatchUtils.STR_ICMPV6_CODE, m.get(MatchField.ICMPV6_CODE).getValue());
                        break;
                    case IP_DSCP:
                        jGen.writeNumberField(MatchUtils.STR_NW_DSCP, m.get(MatchField.IP_DSCP).getDscpValue());
                        break;
                    case IP_ECN:
                        jGen.writeNumberField(MatchUtils.STR_NW_ECN, m.get(MatchField.IP_ECN).getEcnValue());
                        break;
                    case IP_PROTO:
                        jGen.writeNumberField(MatchUtils.STR_NW_PROTO, m.get(MatchField.IP_PROTO).getIpProtocolNumber());
                        break;
                    case IPV4_SRC:
                        jGen.writeStringField(MatchUtils.STR_NW_SRC, m.get(MatchField.IPV4_SRC).toString());
                        break;
                    case IPV4_DST:
                        jGen.writeStringField(MatchUtils.STR_NW_DST, m.get(MatchField.IPV4_DST).toString());
                        break;
                    case IPV6_SRC:
                        jGen.writeStringField(MatchUtils.STR_IPV6_SRC, m.get(MatchField.IPV6_SRC).toString());
                        break;
                    case IPV6_DST:
                        jGen.writeStringField(MatchUtils.STR_IPV6_DST, m.get(MatchField.IPV6_DST).toString());
                        break;
                    case IPV6_FLABEL:
                        jGen.writeNumberField(MatchUtils.STR_IPV6_FLOW_LABEL, m.get(MatchField.IPV6_FLABEL).getIPv6FlowLabelValue());
                        break;
                    case IPV6_ND_SLL:
                        jGen.writeNumberField(MatchUtils.STR_IPV6_ND_SSL, m.get(MatchField.IPV6_ND_SLL).getLong());
                        break;
                    case IPV6_ND_TARGET:
                        jGen.writeNumberField(MatchUtils.STR_IPV6_ND_TARGET, m.get(MatchField.IPV6_ND_TARGET).getZeroCompressStart());
                        break;
                    case IPV6_ND_TLL:
                        jGen.writeNumberField(MatchUtils.STR_IPV6_ND_TTL, m.get(MatchField.IPV6_ND_TLL).getLong());
                        break;
                    case METADATA:
                        jGen.writeNumberField(MatchUtils.STR_METADATA, m.get(MatchField.METADATA).getValue().getValue());
                        break;
                    case MPLS_LABEL:
                        jGen.writeNumberField(MatchUtils.STR_MPLS_LABEL, m.get(MatchField.MPLS_LABEL).getValue());
                        break;
                    case MPLS_TC:
                        jGen.writeNumberField(MatchUtils.STR_MPLS_TC, m.get(MatchField.MPLS_TC).getValue());
                        break;
                    case SCTP_SRC:
                        jGen.writeNumberField(MatchUtils.STR_SCTP_SRC, m.get(MatchField.SCTP_SRC).getPort());
                        break;
                    case SCTP_DST:
                        jGen.writeNumberField(MatchUtils.STR_SCTP_DST, m.get(MatchField.SCTP_DST).getPort());
                        break;
                    case TCP_SRC:
                        jGen.writeNumberField(MatchUtils.STR_TCP_SRC, m.get(MatchField.TCP_SRC).getPort());
                        break;
                    case TCP_DST:
                        jGen.writeNumberField(MatchUtils.STR_TCP_DST, m.get(MatchField.TCP_DST).getPort());
                        break;
                    case UDP_SRC:
                        jGen.writeNumberField(MatchUtils.STR_UDP_SRC, m.get(MatchField.UDP_SRC).getPort());
                        break;
                    case UDP_DST:
                        jGen.writeNumberField(MatchUtils.STR_UDP_DST, m.get(MatchField.UDP_DST).getPort());
                        break;
                    default:
                        // either a BSN or unknown match type
                        break;
                    } // end switch of match type
                } // end while over non-wildcarded matches

                jGen.writeEndObject(); // end match

                // handle OF1.1+ instructions with actions within
                if (entry.getVersion() == OFVersion.OF_10) {
                    serializeActions(jGen, entry.getActions());
                } else {
                    List<OFInstruction> instructions = entry.getInstructions();
                    jGen.writeObjectFieldStart("instructions");
                    if (instructions.isEmpty()) {
                        jGen.writeStringField("none", "drop");
                    } else {
                        for (OFInstruction i : instructions) {
                            switch (i.getType()) {
                            case CLEAR_ACTIONS:
                                jGen.writeObjectFieldStart(InstructionUtils.STR_CLEAR_ACTIONS);
                                break;
                            case WRITE_METADATA:
                                jGen.writeStartObject();
                                jGen.writeNumberField(InstructionUtils.STR_WRITE_METADATA, ((OFInstructionWriteMetadata)i).getMetadata().getValue());
                                jGen.writeNumberField(InstructionUtils.STR_WRITE_METADATA + "_mask", ((OFInstructionWriteMetadata)i).getMetadataMask().getValue());
                                break;
                            case EXPERIMENTER:
                                jGen.writeStartObject();
                                jGen.writeNumberField(InstructionUtils.STR_EXPERIMENTER, ((OFInstructionExperimenter)i).getExperimenter());
                                break;
                            case GOTO_TABLE:
                                jGen.writeStartObject();
                                jGen.writeNumberField(InstructionUtils.STR_GOTO_TABLE, ((OFInstructionGotoTable)i).getTableId().getValue());
                                break;
                            case METER:
                                jGen.writeStartObject();
                                jGen.writeNumberField(InstructionUtils.STR_GOTO_METER, ((OFInstructionMeter)i).getMeterId());
                                break;
                            case APPLY_ACTIONS:
                                jGen.writeObjectFieldStart(InstructionUtils.STR_APPLY_ACTIONS);
                                serializeActions(jGen, ((OFInstructionApplyActions)i).getActions());
                                break;
                            case WRITE_ACTIONS:
                                jGen.writeObjectFieldStart(InstructionUtils.STR_WRITE_ACTIONS);
                                serializeActions(jGen, ((OFInstructionWriteActions)i).getActions());
                            default:
                                // shouldn't ever get here
                                break;
                            } // end switch on instruction
                            jGen.writeEndObject(); // end specific instruction
                        } // end for instructions
                        jGen.writeEndObject();
                    } // end process instructions (OF1.1+ only)
                } // end not-empty instructions (else)
                jGen.writeEndObject();
            } // end for each OFFlowStatsReply entry
        } // end for each OFStatsReply
    } // end method

    /**
     * Write a JSON string given a list of OFAction. Supports OF1.0 - OF1.3.
     * This is the only place actions are serialized, for any OF version. Because
     * some OF version share actions, it makes sense to have them in one place.
     * @param jsonGenerator
     * @param actions
     * @throws IOException
     * @throws JsonProcessingException
     */
    public void serializeActions(JsonGenerator jsonGenerator, List<OFAction> actions) throws IOException, JsonProcessingException {
        //jsonGenerator.writeStartObject();
        if (actions.isEmpty()) {
            jsonGenerator.writeStringField("none", "drop");
        }
        for (OFAction a : actions) {
            switch (a.getType()) {
            case OUTPUT: //TODO @Ryan need to reference the predefined string constants for each of the actions/oxms
                jsonGenerator.writeNumberField(ActionUtils.STR_OUTPUT, ((OFActionOutput)a).getPort().getPortNumber());
                break;
            /* begin OF1.0 ONLY actions */
            case SET_VLAN_VID:
                jsonGenerator.writeNumberField(ActionUtils.STR_VLAN_SET_VID, ((OFActionSetVlanVid)a).getVlanVid().getVlan());
                break;
            case SET_VLAN_PCP:
                jsonGenerator.writeNumberField(ActionUtils.STR_VLAN_SET_PCP, ((OFActionSetVlanPcp)a).getVlanPcp().getValue());
                break;
            case SET_QUEUE:
                jsonGenerator.writeNumberField(ActionUtils.STR_QUEUE_SET, ((OFActionSetQueue)a).getQueueId());
                break;
            case SET_DL_SRC:
                jsonGenerator.writeStringField(ActionUtils.STR_DL_SRC_SET, ((OFActionSetDlSrc)a).getDlAddr().toString());
                break;
            case SET_DL_DST:
                jsonGenerator.writeStringField(ActionUtils.STR_DL_DST_SET, ((OFActionSetDlDst)a).getDlAddr().toString());
                break;
            case SET_NW_SRC:
                jsonGenerator.writeStringField(ActionUtils.STR_NW_SRC_SET, ((OFActionSetNwSrc)a).getNwAddr().toString());
                break;
            case SET_NW_DST:
                jsonGenerator.writeStringField(ActionUtils.STR_NW_DST_SET, ((OFActionSetNwDst)a).getNwAddr().toString());
                break;
            case SET_NW_TOS:
                jsonGenerator.writeNumberField(ActionUtils.STR_NW_TOS_SET, ((OFActionSetNwTos)a).getNwTos());
                break;    
            case SET_TP_SRC:
                jsonGenerator.writeNumberField(ActionUtils.STR_TP_SRC_SET, ((OFActionSetTpSrc)a).getTpPort().getPort());
                break;
            case SET_TP_DST:
                jsonGenerator.writeNumberField(ActionUtils.STR_TP_DST_SET, ((OFActionSetTpDst)a).getTpPort().getPort());
                break;
            /* end OF1.0 ONLY actions; begin OF1.1+ actions */
            case ENQUEUE:
                jsonGenerator.writeNumberField(ActionUtils.STR_ENQUEUE, ((OFActionEnqueue)a).getPort().getPortNumber());
                break;
            case GROUP:
                jsonGenerator.writeNumberField(ActionUtils.STR_GROUP, ((OFActionGroup)a).getGroup().getGroupNumber());
                break;
            case STRIP_VLAN:
                jsonGenerator.writeString(ActionUtils.STR_VLAN_STRIP);
                break;
            case PUSH_VLAN:
                jsonGenerator.writeNumberField(ActionUtils.STR_VLAN_PUSH, ((OFActionPushVlan)a).getEthertype().getValue());
                break;
            case PUSH_MPLS:
                jsonGenerator.writeNumberField(ActionUtils.STR_MPLS_PUSH, ((OFActionPushMpls)a).getEthertype().getValue());
                break;
            case PUSH_PBB:
                jsonGenerator.writeNumberField(ActionUtils.STR_PBB_PUSH, ((OFActionPushPbb)a).getEthertype().getValue());
                break;
            case POP_VLAN:
                jsonGenerator.writeString(ActionUtils.STR_VLAN_POP);
                break;
            case POP_MPLS:
                jsonGenerator.writeNumberField(ActionUtils.STR_MPLS_POP, ((OFActionPopMpls)a).getEthertype().getValue());
                break;
            case POP_PBB:
                jsonGenerator.writeString(ActionUtils.STR_PBB_POP);
                break;
            case COPY_TTL_IN:
                jsonGenerator.writeString(ActionUtils.STR_TTL_IN_COPY);
                break;
            case COPY_TTL_OUT:
                jsonGenerator.writeString(ActionUtils.STR_TTL_OUT_COPY);
                break;
            case DEC_NW_TTL:
                jsonGenerator.writeString(ActionUtils.STR_NW_TTL_DEC);
                break;
            case DEC_MPLS_TTL:
                jsonGenerator.writeString(ActionUtils.STR_MPLS_TTL_DEC);
                break;
            case SET_MPLS_LABEL:
                jsonGenerator.writeNumberField(ActionUtils.STR_MPLS_LABEL_SET, ((OFActionSetMplsLabel)a).getMplsLabel());
                break;
            case SET_MPLS_TC:
                jsonGenerator.writeNumberField(ActionUtils.STR_MPLS_TC_SET, ((OFActionSetMplsTc)a).getMplsTc());
                break;
            case SET_MPLS_TTL:
                jsonGenerator.writeNumberField(ActionUtils.STR_MPLS_TTL_SET, ((OFActionSetMplsTtl)a).getMplsTtl());
                break;
            case SET_NW_ECN:
                jsonGenerator.writeNumberField(ActionUtils.STR_NW_ECN_SET, ((OFActionSetNwEcn)a).getNwEcn().getEcnValue());
                break;
            case SET_NW_TTL:
                jsonGenerator.writeNumberField(ActionUtils.STR_NW_TTL_SET, ((OFActionSetNwTtl)a).getNwTtl());
                break;
            case EXPERIMENTER:
                jsonGenerator.writeNumberField(ActionUtils.STR_EXPERIMENTER, ((OFActionExperimenter)a).getExperimenter());
                break;
            case SET_FIELD:
                if (((OFActionSetField)a).getField() instanceof OFOxmArpOp) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_ARP_OPCODE, ((OFOxmArpOp) ((OFActionSetField) a).getField()).getValue().getOpcode());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpSha) {
                    jsonGenerator.writeStringField(MatchUtils.STR_ARP_SHA, ((OFOxmArpSha) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpTha) {
                    jsonGenerator.writeStringField(MatchUtils.STR_ARP_DHA, ((OFOxmArpTha) ((OFActionSetField) a).getField()).getValue().toString());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpSpa) {
                    jsonGenerator.writeStringField(MatchUtils.STR_ARP_SPA, ((OFOxmArpSpa) ((OFActionSetField) a).getField()).getValue().toString()); // ipaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpTpa) {
                    jsonGenerator.writeStringField(MatchUtils.STR_ARP_DPA, ((OFOxmArpTpa) ((OFActionSetField) a).getField()).getValue().toString()); 
                } 
                /* DATA LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmEthType) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_DL_TYPE, ((OFOxmEthType) ((OFActionSetField) a).getField()).getValue().getValue());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmEthSrc) {
                    jsonGenerator.writeStringField(MatchUtils.STR_DL_SRC, ((OFOxmEthSrc) ((OFActionSetField) a).getField()).getValue().toString());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmEthDst) {
                    jsonGenerator.writeStringField(MatchUtils.STR_DL_DST, ((OFOxmEthDst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmVlanVid) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_DL_VLAN, ((OFOxmVlanVid) ((OFActionSetField) a).getField()).getValue().getVlan()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmVlanPcp) {
                } 
                /* ICMP */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Code) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_ICMP_CODE, ((OFOxmIcmpv4Code) ((OFActionSetField) a).getField()).getValue().getCode()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Type) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_ICMP_TYPE, ((OFOxmIcmpv4Type) ((OFActionSetField) a).getField()).getValue().getType()); 
                } 
                /* NETWORK LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIpProto) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_NW_PROTO, ((OFOxmIpProto) ((OFActionSetField) a).getField()).getValue().getIpProtocolNumber()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Src) {
                    jsonGenerator.writeStringField(MatchUtils.STR_NW_SRC, ((OFOxmIpv4Src) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Dst) {
                    jsonGenerator.writeStringField(MatchUtils.STR_NW_DST, ((OFOxmIpv4Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpEcn) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_NW_ECN, ((OFOxmIpEcn) ((OFActionSetField) a).getField()).getValue().getEcnValue()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpDscp) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_NW_DSCP, ((OFOxmIpDscp) ((OFActionSetField) a).getField()).getValue().getDscpValue()); 
                } 
                /* TRANSPORT LAYER, TCP, UDP, and SCTP */
                else if (((OFActionSetField)a).getField() instanceof OFOxmTcpSrc) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_TCP_SRC, ((OFOxmTcpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmTcpDst) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_TCP_DST, ((OFOxmTcpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmUdpSrc) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_UDP_SRC, ((OFOxmUdpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmUdpDst) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_UDP_DST, ((OFOxmUdpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmSctpSrc) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_SCTP_SRC, ((OFOxmSctpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmSctpDst) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_SCTP_DST, ((OFOxmSctpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
                }
                /* MPLS */
                else if (((OFActionSetField)a).getField() instanceof OFOxmMplsLabel) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_MPLS_LABEL, ((OFOxmMplsLabel) ((OFActionSetField) a).getField()).getValue().getValue()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmMplsTc) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_MPLS_TC, ((OFOxmMplsTc) ((OFActionSetField) a).getField()).getValue().getValue()); 
                } // MPLS_BOS not implemented in loxi
                /* METADATA */
                else if (((OFActionSetField)a).getField() instanceof OFOxmMetadata) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_METADATA, ((OFOxmMetadata) ((OFActionSetField) a).getField()).getValue().getValue().getValue()); 
                } else {
                    logger.error("Could not decode Set-Field action field: {}", ((OFActionSetField) a));
                    // need to get a logger in here somehow log.error("Could not decode Set-Field action field: {}", ((OFActionSetField) a));
                }
            } // end switch over action type
        } // end for over all actions
    } // end method

    public void serializeDescReply(List<OFDescStatsReply> descReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFDescStatsReply descReply = descReplies.get(0); // There are only one descReply from the switch
        jGen.writeObjectFieldStart("desc"); 
        jGen.writeStringField("version", descReply.getVersion().toString()); //return the enum name
        jGen.writeStringField("manufacturerDescription", descReply.getMfrDesc()); 
        jGen.writeStringField("hardwareDescription", descReply.getHwDesc()); 
        jGen.writeStringField("softwareDescription", descReply.getSwDesc()); 
        jGen.writeStringField("serialNumber", descReply.getSerialNum()); 
        jGen.writeStringField("datapathDescription", descReply.getDpDesc()); 
        jGen.writeEndObject(); // end match
    }
    public void serializeAggregateReply(List<OFAggregateStatsReply> aggregateReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFAggregateStatsReply aggregateReply = aggregateReplies.get(0); // There are only one aggregateReply from the switch
        jGen.writeObjectFieldStart("aggregate"); 
        jGen.writeStringField("version", aggregateReply.getVersion().toString()); //return the enum name
        jGen.writeNumberField("flowCount", aggregateReply.getFlowCount());
        jGen.writeNumberField("packetCount", aggregateReply.getPacketCount().getValue());
        jGen.writeNumberField("byteCount", aggregateReply.getByteCount().getValue());
        jGen.writeEndObject(); // end match
    }

    public void serializePortDescReply(List<OFPortDescStatsReply> portDescReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
		OFPortDescStatsReply portDescReply = portDescReplies.get(0); // we will get only one PortDescReply and it will contains many OFPortDescStatsEntry ?
        jGen.writeStringField("version", portDescReply.getVersion().toString()); //return the enum name
		jGen.writeFieldName("portDesc");
		jGen.writeStartArray();
		for(OFPortDesc entry : portDescReply.getEntries()) {
			jGen.writeStartObject();
			jGen.writeStringField("portNumber",entry.getPortNo().toString());
			jGen.writeStringField("hardwareAddress", entry.getHwAddr().toString());
			jGen.writeStringField("name", entry.getName());
			switch(entry.getVersion()) {
				case OF_10:
					jGen.writeNumberField("config", OFPortConfigSerializerVer10.toWireValue(entry.getConfig()));
					jGen.writeNumberField("state", OFPortStateSerializerVer10.toWireValue(entry.getState()));
					jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getCurr()));
					jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getAdvertised()));
					jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getSupported()));
					jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getPeer()));
					break;
				case OF_11:
					jGen.writeNumberField("config", OFPortConfigSerializerVer11.toWireValue(entry.getConfig()));
					jGen.writeNumberField("state", OFPortStateSerializerVer11.toWireValue(entry.getState()));
					jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getCurr()));
					jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getAdvertised()));
					jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getSupported()));
					jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getPeer()));
					break;
				case OF_12:
					jGen.writeNumberField("config", OFPortConfigSerializerVer12.toWireValue(entry.getConfig()));
					jGen.writeNumberField("state", OFPortStateSerializerVer12.toWireValue(entry.getState()));
					jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getCurr()));
					jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getAdvertised()));
					jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getSupported()));
					jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getPeer()));
					break;
				case OF_13:
					jGen.writeNumberField("config", OFPortConfigSerializerVer13.toWireValue(entry.getConfig()));
					jGen.writeNumberField("state", OFPortStateSerializerVer13.toWireValue(entry.getState()));
					jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getCurr()));
					jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getAdvertised()));
					jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getSupported()));
					jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getPeer()));
					break;
			}
			if (OFVersion.OF_10 != entry.getVersion()) {
				jGen.writeNumberField("currSpeed",entry.getCurrSpeed());
				jGen.writeNumberField("maxSpeed",entry.getMaxSpeed());
			}
			jGen.writeEndObject();
		}
		jGen.writeEndArray();
	}
} 
