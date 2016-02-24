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
	
	@SuppressWarnings("unchecked") 
	public static String matchValueToString(Match m, @SuppressWarnings("rawtypes") MatchField mf) {
		return m.isPartiallyMasked(mf) ? m.getMasked(mf).toString() : m.get(mf).toString();
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
				jGen.writeStringField(MatchUtils.STR_IN_PORT, matchValueToString(m, mf));
				break;
			case IN_PHY_PORT:
				jGen.writeStringField(MatchUtils.STR_IN_PHYS_PORT, matchValueToString(m, mf));
				break;
			case ARP_OP:
				jGen.writeStringField(MatchUtils.STR_ARP_OPCODE, matchValueToString(m, mf));
				break;
			case ARP_SHA:
				jGen.writeStringField(MatchUtils.STR_ARP_SHA, matchValueToString(m, mf));
				break;
			case ARP_SPA:
				jGen.writeStringField(MatchUtils.STR_ARP_SPA, matchValueToString(m, mf));
				break;
			case ARP_THA:
				jGen.writeStringField(MatchUtils.STR_ARP_DHA, matchValueToString(m, mf));
				break;
			case ARP_TPA:
				jGen.writeStringField(MatchUtils.STR_ARP_DPA, matchValueToString(m, mf));
				break;
			case ETH_TYPE: // TODO Remove this "0x" when Loxigen is updated.
				jGen.writeStringField(MatchUtils.STR_DL_TYPE, m.isPartiallyMasked(mf) ?
						"0x" + m.getMasked(mf).toString() : "0x" + m.get(mf).toString());
				break;
			case ETH_SRC:
				jGen.writeStringField(MatchUtils.STR_DL_SRC, matchValueToString(m, mf));
				break;
			case ETH_DST:
				jGen.writeStringField(MatchUtils.STR_DL_DST, matchValueToString(m, mf));
				break;
			case VLAN_VID:
				jGen.writeStringField(MatchUtils.STR_DL_VLAN, matchValueToString(m, mf));
				break;
			case VLAN_PCP:
				jGen.writeStringField(MatchUtils.STR_DL_VLAN_PCP, matchValueToString(m, mf));
				break;
			case ICMPV4_TYPE:
				jGen.writeStringField(MatchUtils.STR_ICMP_TYPE, matchValueToString(m, mf));
				break;
			case ICMPV4_CODE:
				jGen.writeStringField(MatchUtils.STR_ICMP_CODE, matchValueToString(m, mf));
				break;
			case ICMPV6_TYPE:
				jGen.writeStringField(MatchUtils.STR_ICMPV6_TYPE, matchValueToString(m, mf));
				break;
			case ICMPV6_CODE:
				jGen.writeStringField(MatchUtils.STR_ICMPV6_CODE, matchValueToString(m, mf));
				break;
			case IP_DSCP:
				jGen.writeStringField(MatchUtils.STR_NW_DSCP, matchValueToString(m, mf));
				break;
			case IP_ECN:
				jGen.writeStringField(MatchUtils.STR_NW_ECN, matchValueToString(m, mf));
				break;
			case IP_PROTO:
				jGen.writeStringField(MatchUtils.STR_NW_PROTO, matchValueToString(m, mf));
				break;
			case IPV4_SRC:
				jGen.writeStringField(MatchUtils.STR_NW_SRC, matchValueToString(m, mf));
				break;
			case IPV4_DST:
				jGen.writeStringField(MatchUtils.STR_NW_DST, matchValueToString(m, mf));
				break;
			case IPV6_SRC:
				jGen.writeStringField(MatchUtils.STR_IPV6_SRC, matchValueToString(m, mf));
				break;
			case IPV6_DST:
				jGen.writeStringField(MatchUtils.STR_IPV6_DST, matchValueToString(m, mf));
				break;
			case IPV6_FLABEL:
				jGen.writeStringField(MatchUtils.STR_IPV6_FLOW_LABEL, matchValueToString(m, mf));
				break;
			case IPV6_ND_SLL:
				jGen.writeStringField(MatchUtils.STR_IPV6_ND_SSL, matchValueToString(m, mf));
				break;
			case IPV6_ND_TARGET:
				jGen.writeStringField(MatchUtils.STR_IPV6_ND_TARGET, matchValueToString(m, mf));
				break;
			case IPV6_ND_TLL:
				jGen.writeStringField(MatchUtils.STR_IPV6_ND_TTL, matchValueToString(m, mf));
				break;
			case METADATA:
				jGen.writeStringField(MatchUtils.STR_METADATA, matchValueToString(m, mf));
				break;
			case MPLS_LABEL:
				jGen.writeStringField(MatchUtils.STR_MPLS_LABEL, matchValueToString(m, mf));
				break;
			case MPLS_TC:
				jGen.writeStringField(MatchUtils.STR_MPLS_TC, matchValueToString(m, mf));
				break;
			case MPLS_BOS:
				jGen.writeStringField(MatchUtils.STR_MPLS_BOS, matchValueToString(m, mf));
				break;
			case SCTP_SRC:
				jGen.writeStringField(MatchUtils.STR_SCTP_SRC, matchValueToString(m, mf));
				break;
			case SCTP_DST:
				jGen.writeStringField(MatchUtils.STR_SCTP_DST, matchValueToString(m, mf));
				break;
			case TCP_SRC:
				jGen.writeStringField(MatchUtils.STR_TCP_SRC, matchValueToString(m, mf));
				break;
			case TCP_DST:
				jGen.writeStringField(MatchUtils.STR_TCP_DST, matchValueToString(m, mf));
				break;
			case UDP_SRC:
				jGen.writeStringField(MatchUtils.STR_UDP_SRC, matchValueToString(m, mf));
				break;
			case UDP_DST:
				jGen.writeStringField(MatchUtils.STR_UDP_DST, matchValueToString(m, mf));
				break;
			default:
				// either a BSN or unknown match type
				break;
			} // end switch of match type
		} // end while over non-wildcarded matches

		jGen.writeEndObject(); // end match
	}
}

