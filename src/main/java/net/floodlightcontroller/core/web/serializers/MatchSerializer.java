package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.Iterator;

import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize any Match in JSON.
 * 
 * Use automatically by Jackson via JsonSerialize(using=MatchSerializer.class),
 * or use the static function within this class within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class MatchSerializer extends JsonSerializer<Match> {
	protected static Logger logger = LoggerFactory.getLogger(OFActionListSerializer.class);

	@Override
	public void serialize(Match match, JsonGenerator jGen, SerializerProvider serializer) throws IOException,
	JsonProcessingException {
		serializeMatch(jGen, match);
	}

	public static void serializeMatch(JsonGenerator jGen, Match match) throws IOException, JsonProcessingException {
		// list flow matches
		jGen.writeObjectFieldStart("match");
		Iterator<MatchField<?>> mi = match.getMatchFields().iterator(); // get iter to any match field type
		Match m = match;

		while (mi.hasNext()) {
			MatchField<?> mf = mi.next();
			switch (mf.id) {
			case IN_PORT:
				jGen.writeStringField(MatchUtils.STR_IN_PORT, m.get(MatchField.IN_PORT).toString());
				break;
			case IN_PHY_PORT:
				jGen.writeStringField(MatchUtils.STR_IN_PHYS_PORT, m.get(MatchField.IN_PHY_PORT).toString());
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
			case MPLS_BOS:
				jGen.writeStringField(MatchUtils.STR_MPLS_BOS, m.get(MatchField.MPLS_BOS).toString());
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
	}
}

