package net.floodlightcontroller.util;

import java.util.ArrayDeque;
import java.util.Iterator;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.ICMPv4Code;
import org.projectfloodlight.openflow.types.ICMPv4Type;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IPv6AddressWithMask;
import org.projectfloodlight.openflow.types.IPv6FlowLabel;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBooleanValue;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.OFVlanVidMatchWithMask;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for working with Matches. Includes workarounds for
 * current Loxi limitations/bugs. 
 * 
 * Convert OFInstructions to and from dpctl/ofctl-style strings.
 * Used primarily by the static flow pusher to store and retreive
 * flow entries.
 * 
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 * 
 * Includes string methods adopted from OpenFlowJ for OpenFlow 1.0.
 * 
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 */
public class MatchUtils {
	private static final Logger log = LoggerFactory.getLogger(MatchUtils.class);

	/* List of Strings for marshalling and unmarshalling to human readable forms.
	 * Classes that convert from Match and String should reference these fields for a
	 * common string representation throughout the controller. The StaticFlowEntryPusher
	 * is one such example that references these strings. The REST API for the SFEP will
	 * expect the JSON string to be formatted using these strings for the applicable fields.
	 */
	public static final String STR_IN_PORT = "in_port";
	public static final String STR_IN_PHYS_PORT = "in_phys_port";

	public static final String STR_DL_DST = "eth_dst";
	public static final String STR_DL_SRC = "eth_src";
	public static final String STR_DL_TYPE = "eth_type";
	public static final String STR_DL_VLAN = "eth_vlan_vid";
	public static final String STR_DL_VLAN_PCP = "eth_vlan_pcp";

	public static final String STR_NW_DST = "ipv4_dst";
	public static final String STR_NW_SRC = "ipv4_src";
	public static final String STR_IPV6_DST = "ipv6_dst";
	public static final String STR_IPV6_SRC = "ipv6_src";
	public static final String STR_IPV6_FLOW_LABEL = "ipv6_label";
	public static final String STR_IPV6_ND_SSL = "ipv6_nd_ssl";
	public static final String STR_IPV6_ND_TARGET = "ipv6_nd_target";
	public static final String STR_IPV6_ND_TTL = "ipv6_nd_ttl";
	public static final String STR_NW_PROTO = "ip_proto";
	public static final String STR_NW_TOS = "ip_tos";
	public static final String STR_NW_ECN = "ip_ecn";
	public static final String STR_NW_DSCP = "ip_dscp";

	public static final String STR_SCTP_DST = "sctp_dst";
	public static final String STR_SCTP_SRC = "sctp_src";
	public static final String STR_UDP_DST = "udp_dst";
	public static final String STR_UDP_SRC = "udp_src";
	public static final String STR_TCP_DST = "tcp_dst";
	public static final String STR_TCP_SRC = "tcp_src";
	public static final String STR_TP_DST = "tp_dst"; // support for OF1.0 generic transport ports (possibly sent from the rest api). Only use these to read them in, but store them as the type of port their IpProto is set to.
	public static final String STR_TP_SRC = "tp_src";

	public static final String STR_ICMP_TYPE = "icmpv4_type";
	public static final String STR_ICMP_CODE = "icmpv4_code";
	public static final String STR_ICMPV6_TYPE = "icmpv6_type";
	public static final String STR_ICMPV6_CODE = "icmpv6_code";

	public static final String STR_ARP_OPCODE = "arp_opcode";
	public static final String STR_ARP_SHA = "arp_sha";
	public static final String STR_ARP_DHA = "arp_tha";
	public static final String STR_ARP_SPA = "arp_spa";
	public static final String STR_ARP_DPA = "arp_tpa";

	public static final String STR_MPLS_LABEL = "mpls_label";
	public static final String STR_MPLS_TC = "mpls_tc";
	public static final String STR_MPLS_BOS = "mpls_bos";

	public static final String STR_METADATA = "metadata";
	public static final String STR_TUNNEL_ID = "tunnel_id";
	public static final String STR_TUNNEL_IPV4_SRC = "tunnel_ipv4_src";
	public static final String STR_TUNNEL_IPV4_DST = "tunnel_ipv4_dst";

	public static final String STR_PBB_ISID = "pbb_isid";	

	public static final String SET_FIELD_DELIM = "->";

	/**
	 * Create a point-to-point match for two devices at the IP layer.
	 * Takes an existing match (e.g. from a PACKET_IN), and masks all
	 * MatchFields leaving behind:
	 * 		IN_PORT
	 * 		VLAN_VID
	 * 		ETH_TYPE
	 * 		ETH_SRC
	 * 		ETH_DST
	 * 		IPV4_SRC
	 * 		IPV4_DST
	 * 		IP_PROTO (might remove this)
	 * 
	 * If one of the above MatchFields is wildcarded in Match m,
	 * that MatchField will be wildcarded in the returned Match.
	 * 
	 * @param m The match to remove all L4+ MatchFields from
	 * @return A new Match object with all MatchFields masked/wildcared
	 * except for those listed above.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Match maskL4AndUp(Match m) {
		Match.Builder mb = m.createBuilder(); 
		Iterator<MatchField<?>> itr = m.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			MatchField mf = itr.next();
			// restrict MatchFields only to L3 and below: IN_PORT, ETH_TYPE, ETH_SRC, ETH_DST, IPV4_SRC, IPV4_DST, IP_PROTO (this one debatable...)
			// if a MatchField is not in the access list below, it will not be set --> it will be left wildcarded (default)
			if (mf.equals(MatchField.IN_PORT) || mf.equals(MatchField.ETH_TYPE) || mf.equals(MatchField.ETH_SRC) || mf.equals(MatchField.ETH_DST) ||
					mf.equals(MatchField.IPV4_SRC) || mf.equals(MatchField.IPV4_DST) || mf.equals(MatchField.IP_PROTO)) {
				if (m.isExact(mf)) {
					mb.setExact(mf, m.get(mf));
				} else if (m.isPartiallyMasked(mf)) {
					mb.setMasked(mf, m.getMasked(mf));
				} else {
					// it's either exact, masked, or wildcarded
					// itr only contains exact and masked MatchFields
					// we should never get here
				} 
			}
		}
		return mb.build();
	}
	
	
	/**
	 * Retains all fields in the Match parent. Converts the parent to an
	 * equivalent Match of OFVersion version. No polite check is done to verify 
	 * if MatchFields in parent are supported in a Match of OFVersion
	 * version. An exception will be thrown if there are any unsupported
	 * fields during the conversion process.
	 * 
	 * Note that a Match.Builder is returned. This is a convenience for cases
	 * where MatchFields might be modified, added, or removed prior to being
	 * built (e.g. in Forwarding/Routing between switches of different OFVersions).
	 * Simply build the returned Match.Builder if you would like to treat this
	 * function as a strict copy-to-version.
	 * 
	 * @param parent, the Match to convert
	 * @param version, the OFVersion to convert parent to
	 * @return a Match.Builder of the newly-converted Match
	 */
	@SuppressWarnings("unchecked")
	public static Match.Builder convertToVersion(Match parent, OFVersion version) {
		/* Builder retains a parent MatchField list, but list will not be used to  
		 * build the new match if the builder's set methods have been invoked; only 
		 * additions will be built, and all parent MatchFields will be ignored,  
		 * even if they were not modified by the new builder. Create a builder, and
		 * walk through m's list of non-wildcarded MatchFields. Set them all in the
		 * new builder by invoking a set method for each. This will make them persist
		 * in the Match built from this builder if the user decides to add or subtract
		 * from the MatchField list.
		 */
		Match.Builder mb = OFFactories.getFactory(version).buildMatch(); 
		Iterator<MatchField<?>> itr = parent.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			@SuppressWarnings("rawtypes")
			MatchField mf = itr.next();
			if (parent.isExact(mf)) {
				mb.setExact(mf, parent.get(mf));
			} else if (parent.isPartiallyMasked(mf)) {
				mb.setMasked(mf, parent.getMasked(mf));
			} else {
				// it's either exact, masked, or wildcarded
				// itr only contains exact and masked MatchFields
				// we should never get here
			}
		}
		return mb;
	}

	/**
	 * 
	 * Workaround for bug in Loxi:
	 * 
	 * Create a builder from an existing Match object. Unlike Match's
	 * createBuilder(), this utility function will preserve all of
	 * Match m's MatchFields, even if new MatchFields are set or modified
	 * with the builder after it is returned to the calling function.
	 * 
	 * All original MatchFields in m will be set if the build() method is 
	 * invoked upon the returned builder. After the builder is returned, if
	 * a MatchField is modified via setExact(), setMasked(), or wildcard(),
	 * the newly modified MatchField will replace the original found in m.
	 * 
	 * @param m; the match to create the builder from
	 * @return Match.Builder; the builder that can be modified, and when built,
	 * will retain all of m's MatchFields, unless you explicitly overwrite them.
	 */
	public static Match.Builder createRetentiveBuilder(Match m) {
		return convertToVersion(m, m.getVersion());
	}

	/**
	 * Create a Match builder the same OF version as Match m. The returned builder
	 * will not retain any MatchField information from Match m and will
	 * essentially return a clean-slate Match builder with no parent history. 
	 * This simple method is included as a wrapper to provide the opposite functionality
	 * of createRetentiveBuilder().
	 * 
	 * @param m; the match to create the builder from
	 * @return Match.Builder; the builder retains no history from the parent Match m
	 */
	public static Match.Builder createForgetfulBuilder(Match m) {
		return OFFactories.getFactory(m.getVersion()).buildMatch();
	}

	/**
	 * Create a duplicate Match object from Match m.
	 * 
	 * @param m; the match to copy
	 * @return Match; the new copy of Match m
	 */
	public static Match createCopy(Match m) {
		return m.createBuilder().build(); // will use parent MatchFields to produce the new Match only if the builder is never modified
	}

	/**
	 * TODO NOT IMPLEMENTED! (Marked as Deprecated for the time being.)
	 * 
	 * Returns empty string right now.
	 * Output a dpctl-styled string, i.e., only list the elements that are not wildcarded.
	 * 
	 * A match-everything Match outputs "Match[]"
	 * 
	 * @return "Match[dl_src:00:20:01:11:22:33,nw_src:192.168.0.0/24,tp_dst:80]"
	 */
	@Deprecated
	public static String toString(Match match) {
		/*String str = "";

	        match

	        // l1
	        if ((wildcards & OFPFW_IN_PORT) == 0)
	            str += "," + STR_IN_PORT + "=" + U16.f(this.inputPort);

	        // l2
	        if ((wildcards & OFPFW_DL_DST) == 0)
	            str += "," + STR_DL_DST + "="
	                    + match.);
	        if ((wildcards & OFPFW_DL_SRC) == 0)
	            str += "," + STR_DL_SRC + "="
	                    + HexString.toHexString(this.dataLayerSource);
	        if ((wildcards & OFPFW_DL_TYPE) == 0)
	            str += "," + STR_DL_TYPE + "=0x"
	                    + Integer.toHexString(U16.f(this.dataLayerType));
	        if ((wildcards & OFPFW_DL_VLAN) == 0)
	            str += "," + STR_DL_VLAN + "=0x"
	                    + Integer.toHexString(U16.f(this.dataLayerVirtualLan));
	        if ((wildcards & OFPFW_DL_VLAN_PCP) == 0)
	            str += ","
	                    + STR_DL_VLAN_PCP
	                    + "="
	                    + Integer.toHexString(U8
	                            .f(this.dataLayerVirtualLanPriorityCodePoint));

	        // l3
	        if (getNetworkDestinationMaskLen() > 0)
	            str += ","
	                    + STR_NW_DST
	                    + "="
	                    + cidrToString(networkDestination,
	                            getNetworkDestinationMaskLen());
	        if (getNetworkSourceMaskLen() > 0)
	            str += "," + STR_NW_SRC + "="
	                    + cidrToString(networkSource, getNetworkSourceMaskLen());
	        if ((wildcards & OFPFW_NW_PROTO) == 0)
	            str += "," + STR_NW_PROTO + "=" + U8.f(this.networkProtocol);
	        if ((wildcards & OFPFW_NW_TOS) == 0)
	            str += "," + STR_NW_TOS + "=" + U8.f(this.networkTypeOfService);

	        // l4
	        if ((wildcards & OFPFW_TP_DST) == 0)
	            str += "," + STR_TP_DST + "=" + U16.f(this.transportDestination);
	        if ((wildcards & OFPFW_TP_SRC) == 0)
	            str += "," + STR_TP_SRC + "=" + U16.f(this.transportSource);
	        if ((str.length() > 0) && (str.charAt(0) == ','))
	            str = str.substring(1); // trim the leading ","
	        // done
	        return "OFMatch[" + str + "]"; */
		return "";
	}

	/**
	 * Based on the method from OFMatch in openflowj 1.0.
	 * Set this Match's parameters based on a comma-separated key=value pair
	 * dpctl-style string, e.g., from the output of OFMatch.toString(). The
	 * exact syntax for each key is defined by the string constants at the top
	 * of MatchUtils.java. <br>
	 * <p>
	 * Supported keys/values include <br>
	 * <p>
	 * <TABLE border=1>
	 * <TR>
	 * <TD>KEY(s)
	 * <TD>VALUE
	 * </TR>
	 * <TR>
	 * <TD>"in_port"
	 * <TD>integer
	 * </TR>
	 * <TR>
	 * <TD>"eth_src", "eth_dst"
	 * <TD>hex-string
	 * </TR>
	 * <TR>
	 * <TD>"eth_type", "eth_vlan_vid", "eth_vlan_pcp"
	 * <TD>integer
	 * </TR>
	 * <TR>
	 * <TD>"ipv4_src", "ipv4_dst"
	 * <TD>CIDR-style netmask
	 * </TR>
	 * <TR>
	 * <TD>"tp_src","tp_dst", "tcp_src", "tcp_dst", "udp_src", "udp_dst", etc.
	 * <TD>integer (max 64k)
	 * </TR>
	 * </TABLE>
	 * <p>
	 * The CIDR-style netmasks assume 32 netmask if none given, so:
	 * "128.8.128.118/32" is the same as "128.8.128.118"
	 * 
	 * @param match
	 *            a key=value comma separated string, e.g.
	 *            "in_port=5,nw_dst=192.168.0.0/16,tp_src=80"
	 * @param the OF version to construct this match for
	 * @throws IllegalArgumentException
	 *             on unexpected key or value
	 */
	public static Match fromString(String match, OFVersion ofVersion) throws IllegalArgumentException {

		boolean ver10 = false;

		if (match.equals("") || match.equalsIgnoreCase("any") || match.equalsIgnoreCase("all") || match.equals("[]")) {
			match = "Match[]";
		}

		// Split into pairs of key=value
		String[] tokens = match.split("[\\[,\\]]");
		int initArg = 0;
		if (tokens[0].equals("Match")) {
			initArg = 1;
		}

		// Split up key=value pairs into [key, value], and insert into array-deque
		int i;
		String[] tmp;
		ArrayDeque<String[]> llValues = new ArrayDeque<String[]>();
		for (i = initArg; i < tokens.length; i++) {
			tmp = tokens[i].split("=");
			if (tmp.length != 2) {
				throw new IllegalArgumentException("Token " + tokens[i] + " does not have form 'key=value' parsing " + match);
			}
			tmp[0] = tmp[0].toLowerCase(); // try to make key parsing case insensitive
			llValues.add(tmp); // llValues contains [key, value] pairs. Create a queue of pairs to process.
		}	

		Match.Builder mb = OFFactories.getFactory(ofVersion).buildMatch();

		//Determine if the OF version is 1.0 before adding a flow
		if (ofVersion.equals(OFVersion.OF_10)) {
			ver10 = true;
		}

		while (!llValues.isEmpty()) {
			IpProtocol ipProto = null;
			String[] key_value = llValues.pollFirst(); // pop off the first element; this completely removes it from the queue.

			/* Extract the data and its mask */
			String[] dataMask = key_value[1].split("/");
			if (dataMask.length > 2) {
				throw new IllegalArgumentException("[Data, Mask] " + dataMask + " does not have form 'data/mask' or 'data'" + key_value[1]);
			} else if (dataMask.length == 1) {
				log.debug("No mask detected in Match string: {}", key_value[1]);
			} else if (dataMask.length == 2) {
				log.debug("Detected mask in Match string: {}", key_value[1]);
			}

			switch (key_value[0]) {
			case STR_IN_PORT:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IN_PORT, OFPort.ofShort(dataMask[0].contains("0x") ? U16.of(Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16)).getRaw() : U16.of(Integer.valueOf(dataMask[0])).getRaw()));
				} else {
					mb.setMasked(MatchField.IN_PORT, OFPort.ofShort(dataMask[0].contains("0x") ? U16.of(Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16)).getRaw() : U16.of(Integer.valueOf(dataMask[0])).getRaw()), 
					OFPort.ofShort(dataMask[1].contains("0x") ? U16.of(Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16)).getRaw() : U16.of(Integer.valueOf(dataMask[1])).getRaw()));
				}
				break;
			case STR_DL_DST: /* Only accept hex-string for MAC addresses */
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ETH_DST, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.ETH_DST, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_DL_SRC:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ETH_SRC, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.ETH_SRC, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_DL_TYPE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ETH_TYPE, EthType.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ETH_TYPE, EthType.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
					EthType.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
				}
				break;
			case STR_DL_VLAN:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofRawVid(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.VLAN_VID, OFVlanVidMatchWithMask.of(
						OFVlanVidMatch.ofRawVid(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])), 
						OFVlanVidMatch.ofRawVid(dataMask[1].contains("0x") ? Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[1]))));
				}
				break;
			case STR_DL_VLAN_PCP:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.VLAN_PCP, VlanPcp.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.VLAN_PCP, VlanPcp.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))), 
					VlanPcp.of(dataMask[1].contains("0x") ? U8.t(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[1]))));
				}
				break;
			case STR_NW_DST: /* Only accept dotted-decimal for IPv4 addresses */
				mb.setMasked(MatchField.IPV4_DST, IPv4AddressWithMask.of(key_value[1]));
				break;
			case STR_NW_SRC:
				mb.setMasked(MatchField.IPV4_SRC, IPv4AddressWithMask.of(key_value[1]));
				break;
			case STR_IPV6_DST: /* Only accept hex-string for IPv6 addresses */
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				mb.setMasked(MatchField.IPV6_DST, IPv6AddressWithMask.of(key_value[1]));
				break;
			case STR_IPV6_SRC:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				mb.setMasked(MatchField.IPV6_SRC, IPv6AddressWithMask.of(key_value[1]));
				break;
			case STR_IPV6_FLOW_LABEL:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IPV6_FLABEL, IPv6FlowLabel.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.IPV6_FLABEL, IPv6FlowLabel.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
					IPv6FlowLabel.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
				}
				break;
			case STR_NW_PROTO:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_PROTO, IpProtocol.of(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.IP_PROTO, IpProtocol.of(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])), 
					IpProtocol.of(dataMask[1].contains("0x") ? Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[1])));
				}
				break;
			case STR_NW_TOS:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_ECN, IpEcn.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))));
					mb.setExact(MatchField.IP_DSCP, IpDscp.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.IP_ECN, IpEcn.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))), 
							IpEcn.of(dataMask[1].contains("0x") ? U8.t(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[1]))));
					mb.setMasked(MatchField.IP_DSCP, IpDscp.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))), 
							IpDscp.of(dataMask[1].contains("0x") ? U8.t(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[1]))));
				}
				break;
			case STR_NW_ECN:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_ECN, IpEcn.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.IP_ECN, IpEcn.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))), 
							IpEcn.of(dataMask[1].contains("0x") ? U8.t(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[1]))));
				}
				break;
			case STR_NW_DSCP:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_DSCP, IpDscp.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.IP_DSCP, IpDscp.of(dataMask[0].contains("0x") ? U8.t(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[0]))), 
							IpDscp.of(dataMask[1].contains("0x") ? U8.t(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.t(Short.valueOf(dataMask[1]))));
				}
				break;
			case STR_SCTP_DST: // for transport ports, if we don't know the transport protocol yet, postpone parsing this [key, value] pair until we know. Put it at the back of the queue.
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_SCTP_SRC:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_UDP_DST:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_UDP_SRC:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_TCP_DST:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_TCP_SRC:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_TP_DST: // support for OF1.0 generic transport ports
				if ((ipProto = mb.get(MatchField.IP_PROTO)) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else if (ipProto == IpProtocol.TCP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.UDP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.SCTP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_DST, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_TP_SRC:
				if ((ipProto = mb.get(MatchField.IP_PROTO)) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				}  else if (ipProto == IpProtocol.TCP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.UDP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.SCTP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_SRC, TransportPort.of(dataMask[0].contains("0x") ? Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[0])), 
								TransportPort.of(dataMask[1].contains("0x") ? Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Integer.valueOf(dataMask[1])));
					}
				}
				break;
			case STR_ICMP_TYPE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV4_TYPE, ICMPv4Type.of(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV4_TYPE, ICMPv4Type.of(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])), 
							ICMPv4Type.of(dataMask[1].contains("0x") ? Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[1])));
				}
				break;
			case STR_ICMP_CODE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV4_CODE, ICMPv4Code.of(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV4_CODE, ICMPv4Code.of(dataMask[0].contains("0x") ? Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[0])), 
							ICMPv4Code.of(dataMask[1].contains("0x") ? Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16) : Short.valueOf(dataMask[1])));
				}
				break;
			case STR_ICMPV6_TYPE:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV6_TYPE, dataMask[0].contains("0x") ? U8.of(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV6_TYPE, dataMask[0].contains("0x") ? U8.of(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? U8.of(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[1])));
				}
				break;
			case STR_ICMPV6_CODE:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV6_CODE, dataMask[0].contains("0x") ? U8.of(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV6_CODE, dataMask[0].contains("0x") ? U8.of(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? U8.of(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[1])));
				}
				break;
			case STR_IPV6_ND_SSL:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IPV6_ND_SLL, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.IPV6_ND_SLL, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_IPV6_ND_TTL:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IPV6_ND_TLL, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.IPV6_ND_TLL, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_IPV6_ND_TARGET:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				mb.setMasked(MatchField.IPV6_ND_TARGET, IPv6AddressWithMask.of(key_value[1]));
				break;
			case STR_ARP_OPCODE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ARP_OP, dataMask[0].contains("0x") ? ArpOpcode.of(Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : ArpOpcode.of(Integer.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ARP_OP, dataMask[0].contains("0x") ? ArpOpcode.of(Integer.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : ArpOpcode.of(Integer.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? ArpOpcode.of(Integer.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : ArpOpcode.of(Integer.valueOf(dataMask[1])));
				}
				break;
			case STR_ARP_SHA:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ARP_SHA, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.ARP_SHA, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_ARP_DHA:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ARP_THA, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.ARP_THA, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_ARP_SPA:
				mb.setMasked(MatchField.ARP_SPA, IPv4AddressWithMask.of(key_value[1]));
				break;
			case STR_ARP_DPA:
				mb.setMasked(MatchField.ARP_TPA, IPv4AddressWithMask.of(key_value[1]));
				break;
			case STR_MPLS_LABEL:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.MPLS_LABEL, dataMask[0].contains("0x") ? U32.of(Long.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U32.of(Long.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.MPLS_LABEL, dataMask[0].contains("0x") ? U32.of(Long.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U32.of(Long.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? U32.of(Long.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U32.of(Long.valueOf(dataMask[1])));
				}
				break;
			case STR_MPLS_TC:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.MPLS_TC, dataMask[0].contains("0x") ? U8.of(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.MPLS_TC, dataMask[0].contains("0x") ? U8.of(Short.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? U8.of(Short.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U8.of(Short.valueOf(dataMask[1])));
				}
				break;
			case STR_MPLS_BOS:
				mb.setExact(MatchField.MPLS_BOS, key_value[1].equalsIgnoreCase("true") ? OFBooleanValue.TRUE : OFBooleanValue.FALSE);
				break;
			case STR_METADATA:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.METADATA, dataMask[0].contains("0x") ? OFMetadata.ofRaw(Long.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : OFMetadata.ofRaw(Long.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.METADATA, dataMask[0].contains("0x") ? OFMetadata.ofRaw(Long.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : OFMetadata.ofRaw(Long.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? OFMetadata.ofRaw(Long.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : OFMetadata.ofRaw(Long.valueOf(dataMask[1])));
				}
				break;
			case STR_TUNNEL_ID:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.TUNNEL_ID, dataMask[0].contains("0x") ? U64.of(Long.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U64.of(Long.valueOf(dataMask[0])));
				} else {
					mb.setMasked(MatchField.TUNNEL_ID, dataMask[0].contains("0x") ? U64.of(Long.valueOf(dataMask[0].replaceFirst("0x", ""), 16)) : U64.of(Long.valueOf(dataMask[0])), 
							dataMask[1].contains("0x") ? U64.of(Long.valueOf(dataMask[1].replaceFirst("0x", ""), 16)) : U64.of(Long.valueOf(dataMask[1])));
				}
				break;
			case STR_TUNNEL_IPV4_SRC:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.TUNNEL_IPV4_SRC, IPv4Address.of(key_value[1]));
				} else {
					mb.setMasked(MatchField.TUNNEL_IPV4_SRC, IPv4AddressWithMask.of(key_value[1]));
				}
				break;
			case STR_TUNNEL_IPV4_DST:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.TUNNEL_IPV4_DST, IPv4Address.of(key_value[1]));
				} else {
					mb.setMasked(MatchField.TUNNEL_IPV4_DST, IPv4AddressWithMask.of(key_value[1]));
				}
				break;
			case STR_PBB_ISID:
				/*TODO no-op. Not implemented.
				if (key_value[1].startsWith("0x")) {
					mb.setExact(MatchField., U64.of(Long.parseLong(key_value[1].replaceFirst("0x", ""), 16)));
				} else {
					mb.setExact(MatchField., U64.of(Long.parseLong(key_value[1])));
				} */
				break;
			default:
				throw new IllegalArgumentException("unknown token " + key_value + " parsing " + match);
			} 
		}
		return mb.build();
	}
}
