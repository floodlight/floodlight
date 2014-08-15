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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.projectfloodlight.openflow.util.HexString;
import net.floodlightcontroller.core.web.StatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.match.*;
import org.projectfloodlight.openflow.protocol.oxm.*;
import org.projectfloodlight.openflow.protocol.instruction.*;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.action.*;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.*;

/**
 * Serialize a DPID as colon-separated hexadecimal
 */
public class StatsReplySerializer extends JsonSerializer<StatsReply> {
    @Override
    public void serialize(StatsReply reply, JsonGenerator jGen,
                          SerializerProvider serializer)
                                  throws IOException, JsonProcessingException{
        jGen.writeStartObject();
        jGen.writeStringField("dpid",reply.getDatapathId());
        serializeReplyOF13(reply.getValues(),reply.getStatType(),jGen);
        jGen.writeEndObject();
    }
    public void serializeReplyOF13(Object values,String statType,JsonGenerator jGen)
                    throws IOException, JsonProcessingException{
        if(statType.equals("flow")){
            serializeFlowOF13(((List<OFFlowStatsReply>)values),jGen);
        }
    }
    public void serializeFlowOF13(List<OFFlowStatsReply> flowReplies,JsonGenerator jGen)
            throws IOException, JsonProcessingException{

            jGen.writeFieldName("replies");
            jGen.writeStartArray();
            for(OFFlowStatsReply flowReply: flowReplies){
                jGen.writeStartObject();
                jGen.writeFieldName("entries");
                jGen.writeStartArray();
                for(OFFlowStatsEntry flowEntry: flowReply.getEntries()){
                    jGen.writeStartObject();
                    jGen.writeNumberField("tableId",flowEntry.getTableId().getValue());
                    jGen.writeNumberField("durationSec",flowEntry.getDurationSec());
                    jGen.writeNumberField("durationNsec",flowEntry.getDurationNsec());
                    jGen.writeNumberField("priority",flowEntry.getPriority());
                    jGen.writeNumberField("idleTimeout",flowEntry.getIdleTimeout());
                    jGen.writeNumberField("hardTimeout",flowEntry.getHardTimeout());
                    jGen.writeNumberField("packetCount",flowEntry.getPacketCount().getValue());
                    jGen.writeNumberField("byteCount",flowEntry.getByteCount().getValue());
                    jGen.writeNumberField("cookie",flowEntry.getCookie().getValue());
                    jGen.writeNumberField("flags",flowEntry.getFlags());
                    jGen.writeFieldName("match");
                    jGen.writeStartObject();
                    Iterator<OFOxm<?>> iterator = ((OFMatchV3)(flowEntry.getMatch())).getOxmList().iterator();
                    while(iterator.hasNext()) {
                        OFOxm tmp = iterator.next();
                        serializeSetFieldOF13(tmp.getMatchField().id,tmp.getMatchField().getName(),tmp,jGen);
                    }
                    jGen.writeEndObject();
                    jGen.writeFieldName("instructions");
                    jGen.writeStartObject();
                    for(OFInstruction instruction:  flowEntry.getInstructions()){
                        serializeInstructionOF13(instruction,instruction.getType(),jGen);
                    }
                    jGen.writeEndObject();
                    jGen.writeEndObject();
                }

                jGen.writeEndArray();
                jGen.writeStringField("flags",flowReply.getFlags().toString());
                jGen.writeNumberField("xid",flowReply.getXid());
                jGen.writeEndObject();
            }
            jGen.writeEndArray();
    }
    public void serializeInstructionOF13(OFInstruction instruction, OFInstructionType type, JsonGenerator jGen)
        throws IOException, JsonProcessingException{
        if(type == OFInstructionType.APPLY_ACTIONS){
            jGen.writeFieldName(instruction.getType().toString());
            jGen.writeStartArray();
            for(OFAction action  :((OFInstructionApplyActions)instruction).getActions()){
                serializeActionOF13(action,action.getType(),jGen);
            }
            jGen.writeEndArray();
        }
        else if (type == OFInstructionType.CLEAR_ACTIONS){
            jGen.writeFieldName(instruction.getType().toString());
        }
        else if (type == OFInstructionType.WRITE_ACTIONS){
            jGen.writeFieldName(instruction.getType().toString());
            jGen.writeStartArray();
            for(OFAction action  :((OFInstructionWriteActions)instruction).getActions()){
                serializeActionOF13(action,action.getType(),jGen);
            }
            jGen.writeEndArray();
        }
        else if (type == OFInstructionType.GOTO_TABLE){
            jGen.writeFieldName(instruction.getType().toString());
            jGen.writeStartObject();
            jGen.writeNumberField("tableId",((OFInstructionGotoTable)instruction).getTableId().getValue());
            jGen.writeEndObject();
        }
        else if (type == OFInstructionType.WRITE_METADATA){
            jGen.writeFieldName(instruction.getType().toString());
            jGen.writeStartObject();
            jGen.writeNumberField("metadata",((OFInstructionWriteMetadata)instruction).getMetadata().getValue());
            jGen.writeNumberField("metadataMask",((OFInstructionWriteMetadata)instruction).getMetadataMask().getValue());
            jGen.writeEndObject();
        }
        else if (type == OFInstructionType.METER){
            jGen.writeFieldName(instruction.getType().toString());
            jGen.writeStartObject();
            jGen.writeNumberField("meterId",((OFInstructionMeter)instruction).getMeterId());
            jGen.writeEndObject();
            
        }
    }

    
    public void serializeActionOF13(OFAction action, OFActionType type, JsonGenerator jGen)
        throws IOException, JsonProcessingException{
        jGen.writeStartObject();
        if(type == OFActionType.OUTPUT){
            jGen.writeNumberField(type.toString(),((OFActionOutput)action).getPort().getPortNumber());
        }
        else if (type == OFActionType.SET_VLAN_VID){
            jGen.writeNumberField(type.toString(),((OFActionSetVlanVid)action).getVlanVid().getVlan());
        }
        else if (type == OFActionType.SET_VLAN_PCP){
            jGen.writeNumberField(type.toString(),((OFActionSetVlanPcp)action).getVlanPcp().getValue());
        }
        else if (type == OFActionType.STRIP_VLAN){
            jGen.writeStringField(type.toString(),"True");
        }
        else if (type == OFActionType.SET_DL_SRC){
            jGen.writeStringField(type.toString(),((OFActionSetDlSrc)action).getDlAddr().toString());
        }
        else if (type == OFActionType.SET_DL_DST){
            jGen.writeStringField(type.toString(),((OFActionSetDlDst)action).getDlAddr().toString());
        }
        else if (type == OFActionType.SET_NW_SRC){
            jGen.writeStringField(type.toString(),((OFActionSetNwSrc)action).getNwAddr().toString());

        }
        else if (type == OFActionType.SET_NW_DST){
            jGen.writeStringField(type.toString(),((OFActionSetNwDst)action).getNwAddr().toString());

        }
        else if (type == OFActionType.SET_TP_SRC){
            jGen.writeNumberField(type.toString(),((OFActionSetTpSrc)action).getTpPort().getPort());

        }
        else if (type == OFActionType.SET_TP_DST){
            jGen.writeNumberField(type.toString(),((OFActionSetTpDst)action).getTpPort().getPort());
        }
        else if (type == OFActionType.ENQUEUE){
            jGen.writeNumberField(type.toString()+" ID:",((OFActionEnqueue)action).getQueueId());
            jGen.writeNumberField(type.toString()+" Port",((OFActionEnqueue)action).getPort().getPortNumber());
        }
        else if (type == OFActionType.SET_NW_ECN){
            jGen.writeStringField(type.toString(),((OFActionSetNwEcn)action).getNwEcn().toString());
        }
        else if (type == OFActionType.COPY_TTL_IN){
            jGen.writeStringField(type.toString(),"True");
        }
        else if (type == OFActionType.COPY_TTL_OUT){
            jGen.writeStringField(type.toString(),"True");
        }
        else if (type == OFActionType.SET_MPLS_LABEL){
            jGen.writeNumberField(type.toString(),((OFActionSetMplsLabel)action).getMplsLabel());
        }
        else if (type == OFActionType.SET_MPLS_TC){
            jGen.writeNumberField(type.toString(),((OFActionSetMplsTc)action).getMplsTc());
        }
        else if (type == OFActionType.SET_MPLS_TTL){
            jGen.writeNumberField(type.toString(),((OFActionSetMplsTtl)action).getMplsTtl());
        }
        else if (type == OFActionType.DEC_MPLS_TTL){
            jGen.writeStringField(type.toString(),"True");
        }
        else if (type == OFActionType.PUSH_MPLS){
            jGen.writeStringField(type.toString(),((OFActionPushMpls)action).getEthertype().toString());
        }
        else if (type == OFActionType.POP_MPLS){
            jGen.writeStringField(type.toString(),((OFActionPopMpls)action).getEthertype().toString());
        }
        else if (type == OFActionType.SET_QUEUE){
            jGen.writeNumberField(type.toString(),((OFActionSetQueue)action).getQueueId());
        }
        else if (type == OFActionType.GROUP){
            jGen.writeNumberField(type.toString(),((OFActionGroup)action).getGroup().getGroupNumber());
        }
        else if (type == OFActionType.SET_NW_TTL){
            jGen.writeNumberField(type.toString(),((OFActionSetNwTtl)action).getNwTtl());
        }
        else if (type == OFActionType.DEC_NW_TTL){
            jGen.writeStringField(type.toString(),"True");
        }
        else if (type == OFActionType.SET_FIELD){
            OFOxm<?> tmp = ((OFActionSetField)action).getField();
            serializeSetFieldOF13(tmp.getMatchField().id,"SET_"+tmp.getMatchField().getName().toUpperCase(),tmp,jGen);
        }
        else if (type == OFActionType.PUSH_PBB){
            jGen.writeStringField(type.toString(),((OFActionPushPbb)action).getEthertype().toString());
        }
        else if (type == OFActionType.POP_PBB){
            jGen.writeStringField(type.toString(),"True");
        }
        jGen.writeEndObject();
    }
    public void serializeSetFieldOF13(MatchFields id, String type,OFOxm<?> oxm, JsonGenerator jGen)
        throws IOException, JsonProcessingException{
        if( id == MatchFields.IN_PORT){
                  jGen.writeNumberField(type,((OFPort)oxm.getValue()).getPortNumber());
        }
        else if( id == MatchFields.IN_PHY_PORT){
                  jGen.writeNumberField(type,((OFPort)oxm.getValue()).getPortNumber());
        }
        else if( id == MatchFields.METADATA){
                  jGen.writeNumberField(type,((OFMetadata)oxm.getValue()).getValue().getValue());
        }
        else if( id == MatchFields.ETH_DST){
                  jGen.writeStringField(type,((MacAddress)oxm.getValue()).toString());
        }
        else if( id == MatchFields.ETH_SRC){
                  jGen.writeStringField(type,((MacAddress)oxm.getValue()).toString());
        }
        else if( id == MatchFields.ETH_TYPE){
                  jGen.writeStringField(type,((EthType)oxm.getValue()).toString());
        }
        else if( id == MatchFields.VLAN_VID){
                  jGen.writeNumberField(type,((OFVlanVidMatch)oxm.getValue()).getVlan());
        }
        else if( id == MatchFields.VLAN_PCP){
                  jGen.writeNumberField(type,((VlanPcp)oxm.getValue()).getValue());
        }
        else if( id == MatchFields.IP_DSCP){
                  jGen.writeNumberField(type,((IpDscp)oxm.getValue()).getDscpValue());
        }
        else if( id == MatchFields.IP_ECN){
                  jGen.writeStringField(type,((IpEcn)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IP_PROTO){
                  jGen.writeStringField(type,((IpProtocol)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV4_SRC){
                  jGen.writeStringField(type,((IPv4Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV4_DST){
                  jGen.writeStringField(type,((IPv4Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.TCP_SRC){
                  jGen.writeNumberField(type,((TransportPort)oxm.getValue()).getPort());
        }
        else if( id == MatchFields.TCP_DST){
                  jGen.writeNumberField(type,((TransportPort)oxm.getValue()).getPort());
        }
        else if( id == MatchFields.UDP_SRC){
                  jGen.writeNumberField(type,((TransportPort)oxm.getValue()).getPort());
        }
        else if( id == MatchFields.UDP_DST){
                  jGen.writeNumberField(type,((TransportPort)oxm.getValue()).getPort());
        }
        else if( id == MatchFields.SCTP_SRC){
                  jGen.writeNumberField(type,((TransportPort)oxm.getValue()).getPort());
        }
        else if( id == MatchFields.SCTP_DST){
                  jGen.writeNumberField(type,((TransportPort)oxm.getValue()).getPort());
        }
        else if( id == MatchFields.ICMPV4_TYPE){
                  jGen.writeNumberField(type,((ICMPv4Type)oxm.getValue()).getType());
        }
        else if( id == MatchFields.ICMPV4_CODE){
                  jGen.writeNumberField(type,((ICMPv4Code)oxm.getValue()).getCode());
        }
        else if( id == MatchFields.ARP_OP){
                  jGen.writeNumberField(type,((ArpOpcode)oxm.getValue()).getOpcode());
        }
        else if( id == MatchFields.ARP_SPA){
                  jGen.writeStringField(type,((IPv4Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.ARP_TPA){
                  jGen.writeStringField(type,((IPv4Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.ARP_SHA){
                  jGen.writeStringField(type,((MacAddress)oxm.getValue()).toString());
        }
        else if( id == MatchFields.ARP_THA){
                  jGen.writeStringField(type,((MacAddress)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV6_SRC){
                  jGen.writeStringField(type,((IPv6Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV6_DST){
                  jGen.writeStringField(type,((IPv6Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV6_FLABEL){
                  jGen.writeNumberField(type,((IPv6FlowLabel)oxm.getValue()).getIPv6FlowLabelValue());
        }
        else if( id == MatchFields.ICMPV6_TYPE){
                  jGen.writeNumberField(type,((U8)oxm.getValue()).getValue());
        }
        else if( id == MatchFields.ICMPV6_CODE){
                  jGen.writeNumberField(type,((U8)oxm.getValue()).getValue());
        }
        else if( id == MatchFields.IPV6_ND_TARGET){
                  jGen.writeStringField(type,((IPv6Address)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV6_ND_SLL){
                  jGen.writeStringField(type,((MacAddress)oxm.getValue()).toString());
        }
        else if( id == MatchFields.IPV6_ND_TLL){
                  jGen.writeStringField(type,((MacAddress)oxm.getValue()).toString());
        }
        else if( id == MatchFields.MPLS_LABEL){
                  jGen.writeNumberField(type,((U32)oxm.getValue()).getValue());
        }
        else if( id == MatchFields.MPLS_TC){
                  jGen.writeNumberField(type,((U8)oxm.getValue()).getValue());
        }
/* openflowj-0.3.5 don't support it.
        else if( id == MatchFields.TUNNEL_ID){
                  jGen.writeNumberField(type,((U64)oxm.getValue()).getValue());
        }
*/
        else if( id == MatchFields.BSN_IN_PORTS_128){
                  jGen.writeStringField(type.toUpperCase(),((OFBitMask128)oxm.getValue()).toString());
        }
        else if( id == MatchFields.BSN_LAG_ID){
                  jGen.writeNumberField(type.toUpperCase(),((LagId)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_VRF){
                  jGen.writeNumberField(type.toUpperCase(),((VRF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_GLOBAL_VRF_ALLOWED){
                  jGen.writeNumberField(type.toUpperCase(),((OFBooleanValue)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_L3_INTERFACE_CLASS_ID){
                  jGen.writeNumberField(type.toUpperCase(),((ClassId)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_L3_SRC_CLASS_ID){
                  jGen.writeNumberField(type.toUpperCase(),((ClassId)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_L3_DST_CLASS_ID){
                  jGen.writeNumberField(type.toUpperCase(),((ClassId)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_EGR_PORT_GROUP_ID){
                  jGen.writeNumberField(type.toUpperCase(),((ClassId)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF0){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF1){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF2){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF3){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF4){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF5){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF6){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_UDF7){
                  jGen.writeNumberField(type.toUpperCase(),((UDF)oxm.getValue()).getInt());
        }
        else if( id == MatchFields.BSN_TCP_FLAGS){
                  jGen.writeNumberField(type.toUpperCase(),((U16)oxm.getValue()).getValue());
        }
/* openflowj-0.3.5 don't support it.
        else if( id == MatchFields.BSN_VLAN_XLATE_PORT_GROUP_ID){
                  jGen.writeNumberField(type.toUpperCase(),((ClassId)oxm.getValue()).getInt());
        }    
*/
    }
}
