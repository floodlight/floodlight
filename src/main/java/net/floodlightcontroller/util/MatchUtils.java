package net.floodlightcontroller.util;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
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
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.OFVlanVidMatchWithMask;
import org.projectfloodlight.openflow.types.PacketType;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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

	/* These are special port IDs */
	public static final String STR_PORT_LOCAL = "local";
	public static final String STR_PORT_CONTROLLER = "controller";
	public static final String STR_PORT_ALL = "all";
	public static final String STR_PORT_IN_PORT = STR_IN_PORT;
	public static final String STR_PORT_FLOOD = "flood";
	public static final String STR_PORT_NORMAL = "normal";
	public static final String STR_PORT_TABLE = "table";
	public static final String STR_PORT_MAX = "max";
	public static final String STR_PORT_ANY = "any";


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
	public static final String STR_IPV6_EXTHDR = "ipv6_exthdr";
	public static final String STR_IPV6_ND_SLL = "ipv6_nd_sll";
	public static final String STR_IPV6_ND_TARGET = "ipv6_nd_target";
	public static final String STR_IPV6_ND_TLL = "ipv6_nd_tll";
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
	public static final String STR_TP_DST = "tp_dst"; // support for OF1.0 generic transport ports
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

	public static final String STR_PBB_ISID = "pbb_isid"; //TODO support when Loxi does
	public static final String STR_PBB_UCA = "pbb_uca";

	public static final String STR_TCP_FLAGS = "tcp_flags";
	public static final String STR_ACTSET_OUTPUT = "actset_output";
	public static final String STR_PACKET_TYPE = "packet_type";

	public static final String SET_FIELD_DELIM = "->";

	private static Map<MatchFields, MatchField<?>> ALL_MATCH_FIELDS;
	private static Map<MatchFields, Set<OFVersion>> SUPPORTED_OFVERSIONS;

	private static final Match.Builder v10 = OFFactories.getFactory(OFVersion.OF_10).buildMatch();
	private static final Match.Builder v11 = OFFactories.getFactory(OFVersion.OF_11).buildMatch();
	private static final Match.Builder v12 = OFFactories.getFactory(OFVersion.OF_12).buildMatch();
	private static final Match.Builder v13 = OFFactories.getFactory(OFVersion.OF_13).buildMatch();
	private static final Match.Builder v14 = OFFactories.getFactory(OFVersion.OF_14).buildMatch();
	private static final Match.Builder v15 = OFFactories.getFactory(OFVersion.OF_15).buildMatch();

	private static void initGlobals() {
		Map<MatchFields, MatchField<?>> tmpAll = new HashMap<MatchFields, MatchField<?>>();
		Map<MatchFields, Set<OFVersion>> tmpVer = new HashMap<MatchFields, Set<OFVersion>>();
		for (Field field : MatchField.ARP_OP.getClass().getFields()) {
			if (log.isTraceEnabled()) {
				log.trace("Checking MatchField {}", field.getName());
			}
			if (field.getType() == MatchField.class) {
				try {
					/* null b/c static final field we're trying to access */
					MatchField<?> f = (MatchField<?>) field.get(null);
					synchronized (MatchUtils.class) {
						tmpAll.put(f.id, f); /* !MUST! set this before SUPPORTED_OFVERSIONS */
						tmpVer.put(f.id, getSupportedOFVersions(f.id));
					}
					if (log.isDebugEnabled()) {
						log.debug("Added MatchField {} to list of all MatchFields", f.getName());
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					log.error("Could not add MatchField {} to list of all MatchFields", field.getName());
				}
			}
		}
		ALL_MATCH_FIELDS = ImmutableMap.copyOf(tmpAll);
		SUPPORTED_OFVERSIONS = ImmutableMap.copyOf(tmpVer);
	}

	public static Set<OFVersion> getSupportedOFVersions(MatchFields m) {
		if (ALL_MATCH_FIELDS == null || SUPPORTED_OFVERSIONS == null) {
			initGlobals();
		}

		Set<OFVersion> v = SUPPORTED_OFVERSIONS.get(m);
		if (v != null) {
			return v; /* should already be unmodifiable */
		}

		v = new HashSet<OFVersion>();
		if (v10.supports(getMatchField(m))) {
			v.add(OFVersion.OF_10);
		}
		if (v11.supports(getMatchField(m))) {
			v.add(OFVersion.OF_11);
		}
		if (v12.supports(getMatchField(m))) {
			v.add(OFVersion.OF_12);
		}
		if (v13.supports(getMatchField(m))) {
			v.add(OFVersion.OF_13);
		}
		if (v14.supports(getMatchField(m))) {
			v.add(OFVersion.OF_14);
		}
		if (v15.supports(getMatchField(m))) {
			v.add(OFVersion.OF_15);
		}

		SUPPORTED_OFVERSIONS.put(m, ImmutableSet.copyOf(v));
		return SUPPORTED_OFVERSIONS.get(m);
	}

	public static MatchField<?> getMatchField(MatchFields m) {
		if (ALL_MATCH_FIELDS == null || SUPPORTED_OFVERSIONS == null) {
			initGlobals();
		}
		return ALL_MATCH_FIELDS.get(m);
	}

	/**
	 * Get the string name of a MatchField as it's exposed through
	 * the REST API of Floodlight. This is, unfortunately, not the
	 * same name as the ones used internally within MatchField.
	 * @param mf
	 * @return
	 */
	public static String getMatchFieldName(MatchFields mf) {
		String key = "";
		switch (mf) {
		case ARP_OP:
			key = STR_ARP_OPCODE;
			break;
		case ARP_SHA:
			key = STR_ARP_SHA;
			break;
		case ARP_SPA:
			key = STR_ARP_SPA;
			break;
		case ARP_THA:
			key = STR_ARP_DHA;
			break;
		case ARP_TPA:
			key = STR_ARP_DPA;
			break;
		case ETH_DST:
			key = STR_DL_DST;
			break;
		case ETH_SRC:
			key = STR_DL_SRC;
			break;
		case ETH_TYPE:
			key = STR_DL_TYPE;
			break;
		case ICMPV4_CODE:
			key = STR_ICMP_CODE;
			break;
		case ICMPV4_TYPE:
			key = STR_ICMP_TYPE;
			break;
		case ICMPV6_CODE:
			key = STR_ICMPV6_CODE;
			break;
		case ICMPV6_TYPE:
			key = STR_ICMPV6_TYPE;
			break;
		case IN_PHY_PORT:
			key = STR_IN_PHYS_PORT;
			break;
		case IN_PORT:
			key = STR_IN_PORT;
			break;
		case IPV4_DST:
			key = STR_NW_DST;
			break;
		case IPV4_SRC:
			key = STR_NW_SRC;
			break;
		case IPV6_DST:
			key = STR_IPV6_DST;
			break;
		case IPV6_EXTHDR:
			key = STR_IPV6_EXTHDR;
			break;
		case IPV6_FLABEL:
			key = STR_IPV6_FLOW_LABEL;
			break;
		case IPV6_ND_SLL:
			key = STR_IPV6_ND_SLL;
			break;
		case IPV6_ND_TARGET:
			key = STR_IPV6_ND_TARGET;
			break;
		case IPV6_ND_TLL:
			key = STR_IPV6_ND_TLL;
			break;
		case IPV6_SRC:
			key = STR_IPV6_SRC;
			break;
		case IP_DSCP:
			key = STR_NW_DSCP;
			break;
		case IP_ECN:
			key = STR_NW_ECN;
			break;
		case IP_PROTO:
			key = STR_NW_PROTO;
			break;
		case METADATA:
			key = STR_METADATA;
			break;
		case MPLS_BOS:
			key = STR_MPLS_BOS;
			break;
		case MPLS_LABEL:
			key = STR_MPLS_LABEL;
			break;
		case MPLS_TC:
			key = STR_MPLS_TC;
			break;
		case PBB_UCA:
			key = STR_PBB_UCA;
			break;
		case SCTP_DST:
			key = STR_SCTP_DST;
			break;
		case SCTP_SRC:
			key = STR_SCTP_SRC;
			break;
		case TCP_DST:
			key = STR_TCP_DST;
			break;
		case TCP_SRC:
			key = STR_TCP_SRC;
			break;
		case TUNNEL_ID:
			key = STR_TUNNEL_ID;
			break;
		case TUNNEL_IPV4_DST:
			key = STR_TUNNEL_IPV4_DST;
			break;
		case TUNNEL_IPV4_SRC:
			key = STR_TUNNEL_IPV4_SRC;
			break;
		case UDP_DST:
			key = STR_UDP_DST;
			break;
		case UDP_SRC:
			key = STR_UDP_SRC;
			break;
		case VLAN_PCP:
			key = STR_DL_VLAN_PCP;
			break;
		case VLAN_VID:
			key = STR_DL_VLAN;
			break;
		case PACKET_TYPE:
			key = STR_PACKET_TYPE;
			break;
		case TCP_FLAGS:
			key = STR_TCP_FLAGS;
			break;
		case ACTSET_OUTPUT:
			key = STR_ACTSET_OUTPUT;
			break;
		/* NOTE: keep BSN MatchFields to eliminate need for default case.
		   Unaccounted for fields will then produce warning in future */
		case BSN_EGR_PORT_GROUP_ID: 
		case BSN_GLOBAL_VRF_ALLOWED:
		case BSN_INGRESS_PORT_GROUP_ID:
		case BSN_INNER_ETH_DST:
		case BSN_INNER_ETH_SRC:
		case BSN_INNER_VLAN_VID:
		case BSN_IN_PORTS_128:
		case BSN_IN_PORTS_512:
		case BSN_L2_CACHE_HIT:
		case BSN_L3_DST_CLASS_ID:
		case BSN_L3_INTERFACE_CLASS_ID:
		case BSN_L3_SRC_CLASS_ID:
		case BSN_LAG_ID:
		case BSN_TCP_FLAGS:
		case BSN_UDF0:
		case BSN_UDF1:
		case BSN_UDF2:
		case BSN_UDF3:
		case BSN_UDF4:
		case BSN_UDF5:
		case BSN_UDF6:
		case BSN_UDF7:
		case BSN_VFI:
		case BSN_VLAN_XLATE_PORT_GROUP_ID:
		case BSN_VRF:
		case BSN_VXLAN_NETWORK_ID:
			log.warn("Ignoring BSN MatchField {}", mf);
			break;
		}
		return key;
	}

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
					mb.setMasked((MatchField<?>) mf, m.getMasked(mf));
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
		while (itr.hasNext()) {
			@SuppressWarnings("rawtypes")
			MatchField mf = itr.next();
			if (parent.isExact(mf)) {
				mb.setExact(mf, parent.get(mf));
			} else if (parent.isPartiallyMasked(mf)) {
				mb.setMasked((MatchField<?>) mf, parent.getMasked(mf));
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
	 * TODO NOT IMPLEMENTED!
	 * 
	 * Returns empty string right now.
	 */
	@Deprecated
	public static String toString(Match match) {
		return "";
	}

	/**
	 * Based on the method from OFMatch in openflowj 1.0.
	 * Set this Match's parameters based on a comma-separated key=value pair
	 * dpctl-style string, e.g., from the output of OFMatch.toString(). The
	 * exact syntax for each key is defined by the string constants at the top
	 * of MatchUtils.java. <br>
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
				dataMask[0] = dataMask[0].trim().toLowerCase();
				log.debug("No mask detected in Match string: {}", key_value[1]);
			} else if (dataMask.length == 2) {
				dataMask[0] = dataMask[0].trim().toLowerCase();
				dataMask[1] = dataMask[1].trim().toLowerCase();
				log.debug("Detected mask in Match string: {}", key_value[1]);
			}

			switch (key_value[0]) {
			case STR_IN_PORT:
				if (dataMask.length == 1) {
				    mb.setExact(MatchField.IN_PORT, portFromString(dataMask[0]));
				} else {
					mb.setMasked(MatchField.IN_PORT, portFromString(dataMask[0]), portFromString(dataMask[1]));
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
					mb.setExact(MatchField.ETH_TYPE, EthType.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ETH_TYPE, EthType.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
							EthType.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
				}
				break;
			case STR_DL_VLAN:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofRawVid(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.VLAN_VID, OFVlanVidMatchWithMask.of(
							OFVlanVidMatch.ofRawVid(ParseUtils.parseHexOrDecShort(dataMask[0])), 
							OFVlanVidMatch.ofRawVid(ParseUtils.parseHexOrDecShort(dataMask[1]))));
				}
				break;
			case STR_DL_VLAN_PCP:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.VLAN_PCP, VlanPcp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.VLAN_PCP, VlanPcp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))), 
							VlanPcp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[1]))));
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
					mb.setExact(MatchField.IPV6_FLABEL, IPv6FlowLabel.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
				} else {
					mb.setMasked(MatchField.IPV6_FLABEL, IPv6FlowLabel.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
							IPv6FlowLabel.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
				}
				break;
			case STR_NW_PROTO:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_PROTO, IpProtocol.of(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.IP_PROTO, IpProtocol.of(ParseUtils.parseHexOrDecShort(dataMask[0])), 
							IpProtocol.of(ParseUtils.parseHexOrDecShort(dataMask[1])));
				}
				break;
			case STR_NW_TOS:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_ECN, IpEcn.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))));
					mb.setExact(MatchField.IP_DSCP, IpDscp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.IP_ECN, IpEcn.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))), 
							IpEcn.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[1]))));
					mb.setMasked(MatchField.IP_DSCP, IpDscp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))), 
							IpDscp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[1]))));
				}
				break;
			case STR_NW_ECN:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_ECN, IpEcn.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.IP_ECN, IpEcn.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))), 
							IpEcn.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[1]))));
				}
				break;
			case STR_NW_DSCP:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IP_DSCP, IpDscp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))));
				} else {
					mb.setMasked(MatchField.IP_DSCP, IpDscp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[0]))), 
							IpDscp.of(U8.t(ParseUtils.parseHexOrDecShort(dataMask[1]))));
				}
				break;
			case STR_SCTP_DST: // for transport ports, if we don't know the transport protocol yet, postpone parsing this [key, value] pair until we know. Put it at the back of the queue.
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_SCTP_SRC:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_UDP_DST:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_UDP_SRC:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_TCP_DST:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_TCP_SRC:
				if (mb.get(MatchField.IP_PROTO) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else {
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_TP_DST: // support for OF1.0 generic transport ports
				if ((ipProto = mb.get(MatchField.IP_PROTO)) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				} else if (ipProto == IpProtocol.TCP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.UDP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.SCTP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_DST, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_TP_SRC:
				if ((ipProto = mb.get(MatchField.IP_PROTO)) == null) {
					llValues.add(key_value); // place it back if we can't proceed yet
				}  else if (ipProto == IpProtocol.TCP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.TCP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.TCP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.UDP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.UDP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.UDP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				} else if (ipProto == IpProtocol.SCTP){
					if (dataMask.length == 1) {
						mb.setExact(MatchField.SCTP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
					} else {
						mb.setMasked(MatchField.SCTP_SRC, TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
								TransportPort.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
					}
				}
				break;
			case STR_ICMP_TYPE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV4_TYPE, ICMPv4Type.of(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV4_TYPE, ICMPv4Type.of(ParseUtils.parseHexOrDecShort(dataMask[0])), 
							ICMPv4Type.of(ParseUtils.parseHexOrDecShort(dataMask[1])));
				}
				break;
			case STR_ICMP_CODE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV4_CODE, ICMPv4Code.of(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV4_CODE, ICMPv4Code.of(ParseUtils.parseHexOrDecShort(dataMask[0])), 
							ICMPv4Code.of(ParseUtils.parseHexOrDecShort(dataMask[1])));
				}
				break;
			case STR_ICMPV6_TYPE:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV6_TYPE, U8.of(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV6_TYPE, U8.of(ParseUtils.parseHexOrDecShort(dataMask[0])), 
					        U8.of(ParseUtils.parseHexOrDecShort(dataMask[1])));
				}
				break;
			case STR_ICMPV6_CODE:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ICMPV6_CODE, U8.of(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ICMPV6_CODE, U8.of(ParseUtils.parseHexOrDecShort(dataMask[0])), 
					    U8.of(ParseUtils.parseHexOrDecShort(dataMask[1])));
				}
				break;
			case STR_IPV6_ND_SLL:
				if (ver10 == true) {
					throw new IllegalArgumentException("OF Version incompatible");
				}
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IPV6_ND_SLL, MacAddress.of(dataMask[0]));
				} else {
					mb.setMasked(MatchField.IPV6_ND_SLL, MacAddress.of(dataMask[0]), MacAddress.of(dataMask[1]));
				}
				break;
			case STR_IPV6_ND_TLL:
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
			case STR_IPV6_EXTHDR:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.IPV6_EXTHDR, U16.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
				} else {
					mb.setMasked(MatchField.IPV6_EXTHDR, U16.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
					        U16.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
				}
				break;
			case STR_ARP_OPCODE:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ARP_OP, ArpOpcode.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
				} else {
					mb.setMasked(MatchField.ARP_OP, ArpOpcode.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
							ArpOpcode.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
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
					mb.setExact(MatchField.MPLS_LABEL, U32.of(ParseUtils.parseHexOrDecLong(dataMask[0])));
				} else {
					mb.setMasked(MatchField.MPLS_LABEL, U32.of(ParseUtils.parseHexOrDecLong(dataMask[0])), 
							U32.of(ParseUtils.parseHexOrDecLong(dataMask[1])));
				}
				break;
			case STR_MPLS_TC:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.MPLS_TC, U8.of(ParseUtils.parseHexOrDecShort(dataMask[0])));
				} else {
					mb.setMasked(MatchField.MPLS_TC, U8.of(ParseUtils.parseHexOrDecShort(dataMask[0])), 
							U8.of(ParseUtils.parseHexOrDecShort(dataMask[1])));
				}
				break;
			case STR_MPLS_BOS:
				mb.setExact(MatchField.MPLS_BOS, OFBooleanValue.of(Boolean.parseBoolean(key_value[1])));
				break;
			case STR_METADATA:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.METADATA, OFMetadata.ofRaw(ParseUtils.parseHexOrDecLong(dataMask[0])));
				} else {
					mb.setMasked(MatchField.METADATA, OFMetadata.ofRaw(ParseUtils.parseHexOrDecLong(dataMask[0])), 
							OFMetadata.ofRaw(ParseUtils.parseHexOrDecLong(dataMask[1])));
				}
				break;
			case STR_TUNNEL_ID:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.TUNNEL_ID, U64.of(ParseUtils.parseHexOrDecLong(dataMask[0])));
				} else {
					mb.setMasked(MatchField.TUNNEL_ID, U64.of(ParseUtils.parseHexOrDecLong(dataMask[0])), 
							U64.of(ParseUtils.parseHexOrDecLong(dataMask[1])));
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
				log.warn("Ignoring unimplemented OXM {}", key_value[0]);
				/*TODO no-op. Not implemented. 
				if (key_value[1].startsWith("0x")) {
					mb.setExact(MatchField.pb, U64.of(Long.parseLong(key_value[1].replaceFirst("0x", ""), 16)));
				} else {
					mb.setExact(MatchField., U64.of(Long.parseLong(key_value[1])));
				} */
				break;
			case STR_PBB_UCA:
				mb.setExact(MatchField.PBB_UCA, OFBooleanValue.of(Boolean.parseBoolean(key_value[1])));
				break;
			case STR_TCP_FLAGS:
				if (dataMask.length == 1) {
					mb.setExact(MatchField.TCP_FLAGS, U16.of(ParseUtils.parseHexOrDecInt(dataMask[0])));
				} else {
					mb.setMasked(MatchField.TCP_FLAGS, U16.of(ParseUtils.parseHexOrDecInt(dataMask[0])), 
							U16.of(ParseUtils.parseHexOrDecInt(dataMask[1])));
				}
				break;
			case STR_ACTSET_OUTPUT: 
				/* TODO when loxi bug fixed if (!mb.supports(MatchField.ACTSET_OUTPUT)) {
					log.warn("Match {} unsupported in OpenFlow version {}", MatchField.ACTSET_OUTPUT, ofVersion);
					break;
				} else {
					log.warn("Why are we here?");
				}*/
				if (dataMask.length == 1) {
					mb.setExact(MatchField.ACTSET_OUTPUT, portFromString(dataMask[0]));
				} else {
					mb.setMasked(MatchField.ACTSET_OUTPUT, portFromString(dataMask[0]), portFromString(dataMask[1]));
				}
				break;
			case STR_PACKET_TYPE:
				if (dataMask.length != 2) {
					log.error("Ignoring invalid PACKET_TYPE OXM. Must specify namespace and namespace type in the form 'ns/nstype'");
				} else {
					mb.setExact(MatchField.PACKET_TYPE, PacketType.of(ParseUtils.parseHexOrDecInt(dataMask[0]), 
					        ParseUtils.parseHexOrDecInt(dataMask[1])));
				}
				break;
			default:
				throw new IllegalArgumentException("unknown token " + key_value + " parsing " + match);
			} 
		}
		return mb.build();
	}
		
	public static OFPort portFromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Port string cannot be null");
        }
        
        s = s.trim().toLowerCase();
        switch (s) {
        case MatchUtils.STR_PORT_ALL:
            return OFPort.ALL;
        case MatchUtils.STR_PORT_CONTROLLER:
            return OFPort.CONTROLLER;
        case MatchUtils.STR_PORT_FLOOD:
            return OFPort.FLOOD;
        case MatchUtils.STR_PORT_IN_PORT:
            return OFPort.IN_PORT;
        case MatchUtils.STR_PORT_LOCAL:
            return OFPort.LOCAL;
        case MatchUtils.STR_PORT_NORMAL:
            return OFPort.NORMAL;
        case MatchUtils.STR_PORT_TABLE:
            return OFPort.TABLE;
        case MatchUtils.STR_PORT_MAX:
            return OFPort.MAX;
        case MatchUtils.STR_PORT_ANY:
            return OFPort.ANY;
        default:
            log.debug("Port {} was not a special port string. Parsing as raw int or hex", s);
        }

        try {
            return OFPort.of(U32.of(ParseUtils.parseHexOrDecLong(s)).getRaw());
        } catch (NumberFormatException e) {
            log.error("Could not parse port '{}'", s);
            return null;
        }
    }
	
	public static String portToString(OFPort p) {
        if (p == null) {
            throw new IllegalArgumentException("Port cannot be null");
        }
        
        if (p.equals(OFPort.ALL)) {
            return STR_PORT_ALL;
        } 
        if (p.equals(OFPort.ANY)) {
            return STR_PORT_ANY;
        }
        if (p.equals(OFPort.CONTROLLER)) {
            return STR_PORT_CONTROLLER;
        }
        if (p.equals(OFPort.FLOOD)) {
            return STR_PORT_FLOOD;
        }
        if (p.equals(OFPort.IN_PORT)) {
            return STR_PORT_IN_PORT;
        }
        if (p.equals(OFPort.LOCAL)) {
            return STR_PORT_LOCAL;
        }
        if (p.equals(OFPort.NORMAL)) {
            return STR_PORT_NORMAL;
        }
        if (p.equals(OFPort.MAX)) {
            return STR_PORT_MAX;
        }
        if (p.equals(OFPort.TABLE)) {
            return STR_PORT_TABLE;
        }
        return Integer.toString(p.getPortNumber());
    }
}