package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.List;

import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionEnqueue;
import org.projectfloodlight.openflow.protocol.action.OFActionExperimenter;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPopMpls;
import org.projectfloodlight.openflow.protocol.action.OFActionPushMpls;
import org.projectfloodlight.openflow.protocol.action.OFActionPushPbb;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActionSetMplsLabel;
import org.projectfloodlight.openflow.protocol.action.OFActionSetMplsTc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetMplsTtl;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwEcn;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwTos;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwTtl;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanPcp;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanVid;
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

import org.projectfloodlight.openflow.protocol.oxm.OFOxmIcmpv6Code;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIcmpv6Type;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpDscp;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpEcn;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpProto;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv4Dst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv4Src;

import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6Dst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6Flabel;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6NdSll;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6NdTarget;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6NdTll;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6Src;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMetadata;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMplsBos;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize any List of OFAction in JSON.
 * 
 * Use automatically by Jackson via JsonSerialize(using=OFActionListSerializer.class),
 * or use the static function within this class within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFActionListSerializer extends JsonSerializer<List<OFAction>> {
    protected static Logger logger = LoggerFactory.getLogger(OFActionListSerializer.class);
	
	@Override
	public void serialize(List<OFAction> actions, JsonGenerator jGen, SerializerProvider serializer) throws IOException,
			JsonProcessingException {
		jGen.writeStartObject();
		serializeActions(jGen, actions);
		jGen.writeEndObject();
	}
    
	/**
     * Write a JSON string given a list of OFAction. Supports OF1.0 - OF1.3.
     * This is the only place actions are serialized, for any OF version. Because
     * some OF version share actions, it makes sense to have them in one place.
     * @param jsonGenerator
     * @param actions
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeActions(JsonGenerator jsonGenerator, List<OFAction> actions) throws IOException, JsonProcessingException {
        if (actions.isEmpty()) {
            jsonGenerator.writeStringField("none", "drop");
        }
        for (OFAction a : actions) {
            switch (a.getType()) {
            case OUTPUT:
                jsonGenerator.writeStringField(ActionUtils.STR_OUTPUT, ((OFActionOutput)a).getPort().toString());
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
                jsonGenerator.writeStringField(ActionUtils.STR_GROUP, ((OFActionGroup)a).getGroup().toString());
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
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdSll) {                		
                	jsonGenerator.writeStringField(MatchUtils.STR_IPV6_ND_SSL, ((OFOxmIpv6NdSll) ((OFActionSetField) a).getField()).getValue().toString());
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTll) {                		
            		jsonGenerator.writeStringField(MatchUtils.STR_IPV6_ND_TTL, ((OFOxmIpv6NdTll) ((OFActionSetField) a).getField()).getValue().toString());
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTarget) {                		
            		jsonGenerator.writeStringField(MatchUtils.STR_IPV6_ND_TARGET, ((OFOxmIpv6NdTarget) ((OFActionSetField) a).getField()).getValue().toString()); 
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
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Code) {                		
                	jsonGenerator.writeNumberField(MatchUtils.STR_ICMPV6_CODE, ((OFOxmIcmpv6Code) ((OFActionSetField) a).getField()).getValue().getRaw()); 
            	}  else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Type) {                		
            		jsonGenerator.writeNumberField(MatchUtils.STR_ICMPV6_TYPE, ((OFOxmIcmpv6Type) ((OFActionSetField) a).getField()).getValue().getRaw()); 
            	}
                /* NETWORK LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIpProto) {
                    jsonGenerator.writeNumberField(MatchUtils.STR_NW_PROTO, ((OFOxmIpProto) ((OFActionSetField) a).getField()).getValue().getIpProtocolNumber()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Src) {
                    jsonGenerator.writeStringField(MatchUtils.STR_NW_SRC, ((OFOxmIpv4Src) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Dst) {
                    jsonGenerator.writeStringField(MatchUtils.STR_NW_DST, ((OFOxmIpv4Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Src) {                		
                	jsonGenerator.writeStringField(MatchUtils.STR_IPV6_SRC, ((OFOxmIpv6Src) ((OFActionSetField) a).getField()).getValue().toString()); 
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Dst) {                		
            		jsonGenerator.writeStringField(MatchUtils.STR_IPV6_DST, ((OFOxmIpv6Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Flabel) {                		
            		jsonGenerator.writeStringField(MatchUtils.STR_IPV6_FLOW_LABEL, ((OFOxmIpv6Flabel) ((OFActionSetField) a).getField()).getValue().toString()); 
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
                } else if (((OFActionSetField)a).getField() instanceof OFOxmMplsBos) {
                    jsonGenerator.writeStringField(MatchUtils.STR_MPLS_TC, ((OFOxmMplsBos) ((OFActionSetField) a).getField()).getValue().toString()); 
                } 
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
}
