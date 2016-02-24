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
        StringBuilder sb = new StringBuilder();
        int len = actions.size();
        int pos = 0;
        for (OFAction a : actions) {
            switch (a.getType()) {
            case OUTPUT:
                sb.append(ActionUtils.STR_OUTPUT).append("=").append(((OFActionOutput)a).getPort().toString());
                break;
            /* begin OF1.0 ONLY actions */
            case SET_VLAN_VID:
                sb.append(ActionUtils.STR_VLAN_SET_VID).append("=").append(((OFActionSetVlanVid)a).getVlanVid().getVlan());
                break;
            case SET_VLAN_PCP:
                sb.append(ActionUtils.STR_VLAN_SET_PCP).append("=").append(((OFActionSetVlanPcp)a).getVlanPcp().getValue());
                break;
            case SET_QUEUE:
                sb.append(ActionUtils.STR_QUEUE_SET).append("=").append(((OFActionSetQueue)a).getQueueId());
                break;
            case SET_DL_SRC:
                sb.append(ActionUtils.STR_DL_SRC_SET).append("=").append(((OFActionSetDlSrc)a).getDlAddr().toString());
                break;
            case SET_DL_DST:
                sb.append(ActionUtils.STR_DL_DST_SET).append("=").append(((OFActionSetDlDst)a).getDlAddr().toString());
                break;
            case SET_NW_SRC:
                sb.append(ActionUtils.STR_NW_SRC_SET).append("=").append(((OFActionSetNwSrc)a).getNwAddr().toString());
                break;
            case SET_NW_DST:
                sb.append(ActionUtils.STR_NW_DST_SET).append("=").append(((OFActionSetNwDst)a).getNwAddr().toString());
                break;
            case SET_NW_TOS:
            	sb.append(ActionUtils.STR_NW_TOS_SET).append("=").append(((OFActionSetNwTos)a).getNwTos());
                break;    
            case SET_TP_SRC:
            	sb.append(ActionUtils.STR_TP_SRC_SET).append("=").append(((OFActionSetTpSrc)a).getTpPort().getPort());
                break;
            case SET_TP_DST:
            	sb.append(ActionUtils.STR_TP_DST_SET).append("=").append(((OFActionSetTpDst)a).getTpPort().getPort());
                break;
            /* end OF1.0 ONLY actions; begin OF1.1+ actions */
            case ENQUEUE:
                sb.append(ActionUtils.STR_ENQUEUE).append("=").append(((OFActionEnqueue)a).getPort().getPortNumber());
                break;
            case GROUP:
            	sb.append(ActionUtils.STR_GROUP).append("=").append(((OFActionGroup)a).getGroup().toString());
                break;
            case STRIP_VLAN:
            	sb.append(ActionUtils.STR_VLAN_STRIP).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case PUSH_VLAN:
            	sb.append(ActionUtils.STR_VLAN_PUSH).append("=").append(((OFActionPushVlan)a).getEthertype().getValue());
                break;
            case PUSH_MPLS:
            	sb.append(ActionUtils.STR_MPLS_PUSH).append("=").append(((OFActionPushMpls)a).getEthertype().getValue());
                break;
            case PUSH_PBB:
            	sb.append(ActionUtils.STR_PBB_PUSH).append("=").append(((OFActionPushPbb)a).getEthertype().getValue());
                break;
            case POP_VLAN:
            	sb.append(ActionUtils.STR_VLAN_POP).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case POP_MPLS:
            	sb.append(ActionUtils.STR_MPLS_POP).append("=").append(((OFActionPopMpls)a).getEthertype().getValue());
                break;
            case POP_PBB:
            	sb.append(ActionUtils.STR_PBB_POP).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case COPY_TTL_IN:
            	sb.append(ActionUtils.STR_TTL_IN_COPY).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case COPY_TTL_OUT:
            	sb.append(ActionUtils.STR_TTL_OUT_COPY).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case DEC_NW_TTL:
            	sb.append(ActionUtils.STR_NW_TTL_DEC).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case DEC_MPLS_TTL:
            	sb.append(ActionUtils.STR_MPLS_TTL_DEC).append("=").append(ActionUtils.STR_NOT_APPLICABLE);
                break;
            case SET_MPLS_LABEL:
            	sb.append(ActionUtils.STR_MPLS_LABEL_SET).append("=").append(((OFActionSetMplsLabel)a).getMplsLabel());
                break;
            case SET_MPLS_TC:
            	sb.append(ActionUtils.STR_MPLS_TC_SET).append("=").append(((OFActionSetMplsTc)a).getMplsTc());
                break;
            case SET_MPLS_TTL:
                sb.append(ActionUtils.STR_MPLS_TTL_SET).append("=").append(((OFActionSetMplsTtl)a).getMplsTtl());
                break;
            case SET_NW_ECN:
            	sb.append(ActionUtils.STR_NW_ECN_SET).append("=").append(((OFActionSetNwEcn)a).getNwEcn().getEcnValue());
                break;
            case SET_NW_TTL:
            	sb.append(ActionUtils.STR_NW_TTL_SET).append("=").append(((OFActionSetNwTtl)a).getNwTtl());
                break;
            case EXPERIMENTER:
            	sb.append(ActionUtils.STR_EXPERIMENTER).append("=").append(((OFActionExperimenter)a).getExperimenter());
                break;
            case SET_FIELD:
                if (((OFActionSetField)a).getField() instanceof OFOxmArpOp) {
                	sb.append(MatchUtils.STR_ARP_OPCODE).append("=").append(((OFOxmArpOp) ((OFActionSetField) a).getField()).getValue().getOpcode());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpSha) {
                	sb.append(MatchUtils.STR_ARP_SHA).append("=").append(((OFOxmArpSha) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpTha) {
                	sb.append(MatchUtils.STR_ARP_DHA).append("=").append(((OFOxmArpTha) ((OFActionSetField) a).getField()).getValue().toString());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpSpa) {
                	sb.append(MatchUtils.STR_ARP_SPA).append("=").append(((OFOxmArpSpa) ((OFActionSetField) a).getField()).getValue().toString()); // ipaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpTpa) {
                	sb.append(MatchUtils.STR_ARP_DPA).append("=").append(((OFOxmArpTpa) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdSll) {                		
                	sb.append(MatchUtils.STR_IPV6_ND_SSL).append("=").append(((OFOxmIpv6NdSll) ((OFActionSetField) a).getField()).getValue().toString());
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTll) {                		
            		sb.append(MatchUtils.STR_IPV6_ND_TTL).append("=").append(((OFOxmIpv6NdTll) ((OFActionSetField) a).getField()).getValue().toString());
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTarget) {                		
            		sb.append(MatchUtils.STR_IPV6_ND_TARGET).append("=").append(((OFOxmIpv6NdTarget) ((OFActionSetField) a).getField()).getValue().toString()); 
            	}
                /* DATA LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmEthType) {
                	sb.append(MatchUtils.STR_DL_TYPE).append("=").append(((OFOxmEthType) ((OFActionSetField) a).getField()).getValue().getValue());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmEthSrc) {
                	sb.append(MatchUtils.STR_DL_SRC).append("=").append(((OFOxmEthSrc) ((OFActionSetField) a).getField()).getValue().toString());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmEthDst) {
                	sb.append(MatchUtils.STR_DL_DST).append("=").append(((OFOxmEthDst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmVlanVid) {
                	sb.append(MatchUtils.STR_DL_VLAN).append("=").append(((OFOxmVlanVid) ((OFActionSetField) a).getField()).getValue().getVlan()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmVlanPcp) {
                } 
                /* ICMP */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Code) {
                	sb.append(MatchUtils.STR_ICMP_CODE).append("=").append(((OFOxmIcmpv4Code) ((OFActionSetField) a).getField()).getValue().getCode()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Type) {
                	sb.append(MatchUtils.STR_ICMP_TYPE).append("=").append(((OFOxmIcmpv4Type) ((OFActionSetField) a).getField()).getValue().getType()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Code) {                		
                	sb.append(MatchUtils.STR_ICMPV6_CODE).append("=").append(((OFOxmIcmpv6Code) ((OFActionSetField) a).getField()).getValue().getRaw()); 
            	}  else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Type) {                		
            		sb.append(MatchUtils.STR_ICMPV6_TYPE).append("=").append(((OFOxmIcmpv6Type) ((OFActionSetField) a).getField()).getValue().getRaw()); 
            	}
                /* NETWORK LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIpProto) {
                	sb.append(MatchUtils.STR_NW_PROTO).append("=").append(((OFOxmIpProto) ((OFActionSetField) a).getField()).getValue().getIpProtocolNumber()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Src) {
                	sb.append(MatchUtils.STR_NW_SRC).append("=").append(((OFOxmIpv4Src) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Dst) {
                	sb.append(MatchUtils.STR_NW_DST).append("=").append(((OFOxmIpv4Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Src) {                		
                	sb.append(MatchUtils.STR_IPV6_SRC).append("=").append(((OFOxmIpv6Src) ((OFActionSetField) a).getField()).getValue().toString()); 
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Dst) {                		
            		sb.append(MatchUtils.STR_IPV6_DST).append("=").append(((OFOxmIpv6Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Flabel) {                		
            		sb.append(MatchUtils.STR_IPV6_FLOW_LABEL).append("=").append(((OFOxmIpv6Flabel) ((OFActionSetField) a).getField()).getValue().toString()); 
            	} else if (((OFActionSetField)a).getField() instanceof OFOxmIpEcn) {
            		sb.append(MatchUtils.STR_NW_ECN).append("=").append(((OFOxmIpEcn) ((OFActionSetField) a).getField()).getValue().getEcnValue()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpDscp) {
                	sb.append(MatchUtils.STR_NW_DSCP).append("=").append(((OFOxmIpDscp) ((OFActionSetField) a).getField()).getValue().getDscpValue()); 
                } 
                /* TRANSPORT LAYER, TCP, UDP, and SCTP */
                else if (((OFActionSetField)a).getField() instanceof OFOxmTcpSrc) {
                	sb.append(MatchUtils.STR_TCP_SRC).append("=").append(((OFOxmTcpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmTcpDst) {
                	sb.append(MatchUtils.STR_TCP_DST).append("=").append(((OFOxmTcpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmUdpSrc) {
                	sb.append(MatchUtils.STR_UDP_SRC).append("=").append(((OFOxmUdpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmUdpDst) {
                	sb.append(MatchUtils.STR_UDP_DST).append("=").append(((OFOxmUdpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmSctpSrc) {
                	sb.append(MatchUtils.STR_SCTP_SRC).append("=").append(((OFOxmSctpSrc) ((OFActionSetField) a).getField()).getValue().getPort()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmSctpDst) {
                	sb.append(MatchUtils.STR_SCTP_DST).append("=").append(((OFOxmSctpDst) ((OFActionSetField) a).getField()).getValue().getPort()); 
                }
                /* MPLS */
                else if (((OFActionSetField)a).getField() instanceof OFOxmMplsLabel) {
                	sb.append(MatchUtils.STR_MPLS_LABEL).append("=").append(((OFOxmMplsLabel) ((OFActionSetField) a).getField()).getValue().getValue()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmMplsTc) {
                	sb.append(MatchUtils.STR_MPLS_TC).append("=").append(((OFOxmMplsTc) ((OFActionSetField) a).getField()).getValue().getValue()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmMplsBos) {
                	sb.append(MatchUtils.STR_MPLS_TC).append("=").append(((OFOxmMplsBos) ((OFActionSetField) a).getField()).getValue().toString()); 
                } 
                /* METADATA */
                else if (((OFActionSetField)a).getField() instanceof OFOxmMetadata) {
                	sb.append(MatchUtils.STR_METADATA).append("=").append(((OFOxmMetadata) ((OFActionSetField) a).getField()).getValue().getValue().getValue()); 
                } else {
                    logger.error("Could not decode Set-Field action field: {}", ((OFActionSetField) a));
                }
            } // end switch over action type
            pos++;
            if (pos < len) {
            	sb.append(",");
            }
        } // end for over all actions
        
        if (actions.isEmpty()) {
            jsonGenerator.writeStringField("none", "drop");
        } else {
        	jsonGenerator.writeStringField("actions", sb.toString());
        }
    } // end method
}
