package net.floodlightcontroller.core.web.serializers;

import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.ver14.OFOxmClassSerializerVer14;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.ICMPv4Code;
import org.projectfloodlight.openflow.types.ICMPv4Type;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IPv6FlowLabel;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBooleanValue;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.VlanVid;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * A handy class to convert OXMs (and NXMs) to and from
 * their IDs (U32=[class]+[field]+[has_mask]+[length])
 * and their string representations. The strings are
 * defined in {@link net.floodlightcontroller.util.MatchUtils},
 * with the exception of some unique NXMs and OpenFlow 1.5 OXMs 
 * not technically supported by Loxi/Floodlight yet. Any NXM
 * string is prefixed with an "nxm_" followed by the string
 * used for the corresponding OXM. For example, an ingress port
 * OXM is "in_port", while the NXM version is "nxm_in_port".
 * 
 * Any unique NXM without an OXM counterpart is assigned a string
 * to inform of the NXM. For example, NXM 33, "nxm_pkt_mark", marks
 * or matches on marked packets as Linux IP tables can do. This is
 * not supported in the OpenFlow spec, thus the NXM is assigned the
 * string "nxm_pkt_mark" within this class and does not have a string
 * predefined in {@link net.floodlightcontroller.util.MatchUtils}.
 * 
 * Any OXM with the HAS_MASKED bit set will have it's string
 * representation appended with an "_masked" or the string defined in
 * the STR_MASKED class variable below.
 * 
 * As an implementation note, a Google BiMap is used as the storage
 * mechanism. This allows fast lookup in both directions from U32 to 
 * String or from String back to U32. Note that unique keys AND values
 * are required in a BiMap.
 * 
 * @author Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 *
 */
public class OXMSerializer {	
	
	/*
	 * OXM IDs for OpenFlow versions 1.5 and below are the same, 
	 * where each version simply adds to the already populated
	 * list of OXM IDs. (IDs don't change b/t OF versions.)
	 * As such, we'll assume OF1.4 here, which includes 1.3.
	 * 
	 * TODO Later, when Loxi supports 1.5, we'll change to
	 * OFOxmClassSerializerVer15.
	 */
	
	/* String constants */
	private static final String STR_MASKED = "_masked";
	private static final String STR_NXM = "nxm_";
	
	/* Bit shifting constants */
	private static final int SHIFT_FIELD = 9;
	private static final int SHIFT_CLASS = 16;
	private static final int SHIFT_HAS_MASK = 8;
	
	/* Mask present bit */
	private static final int MASKED = (1 << SHIFT_HAS_MASK);
	
	/* Classes */
	private static final int OF_BASIC = (OFOxmClassSerializerVer14.OPENFLOW_BASIC_VAL << SHIFT_CLASS) & 0xFFffFFff;
	//TODO private static final int OF_PKT_REG = (0x8001 << SHIFT_CLASS) & 0xFFffFFff;
	private static final int NXM_0 = (OFOxmClassSerializerVer14.NXM_0_VAL << SHIFT_CLASS) & 0xFFffFFff;
	private static final int NXM_1 = (OFOxmClassSerializerVer14.NXM_1_VAL << SHIFT_CLASS) & 0xFFffFFff;
	
	/* The bi-directional map we'll use to make this process more efficient */
	private static final BiMap<U32, String> map = HashBiMap.create();
	static { 
		/*
		 * OXM header is 4 bytes as follows, where C=oxm_class,
		 * F=oxm_field, M=has_mask, and L=data_length.
		 * 
		 * CCCC CCCC CCCC CCCC FFFF FFFM LLLL LLLL
		 * 
		 * The OXM class has already been shifted to the proper
		 * position and is represented as an integer (4 bytes),
		 * but we need to shift the OXM field over. The length
		 * does not need to be shifted.
		 * 
		 */
		
		/*
		 * OpenFlow Basic OXM Definitions
		 */
		map.put(U32.ofRaw(OF_BASIC | (0 << SHIFT_FIELD) | OFPort.ZERO.getLength()), MatchUtils.STR_IN_PORT);
		map.put(U32.ofRaw(OF_BASIC | (0 << SHIFT_FIELD) | OFPort.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_IN_PORT + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (1 << SHIFT_FIELD) | OFPort.ZERO.getLength()), MatchUtils.STR_IN_PHYS_PORT);
		map.put(U32.ofRaw(OF_BASIC | (1 << SHIFT_FIELD) | OFPort.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_IN_PHYS_PORT + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (2 << SHIFT_FIELD) | OFMetadata.NONE.getLength()), MatchUtils.STR_METADATA);
		map.put(U32.ofRaw(OF_BASIC | (2 << SHIFT_FIELD) | OFMetadata.NONE.getLength() * 2 | MASKED), MatchUtils.STR_METADATA + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (3 << SHIFT_FIELD) | MacAddress.NONE.getLength()), MatchUtils.STR_DL_DST);
		map.put(U32.ofRaw(OF_BASIC | (3 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), MatchUtils.STR_DL_DST + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (4 << SHIFT_FIELD) | MacAddress.NONE.getLength()), MatchUtils.STR_DL_SRC);
		map.put(U32.ofRaw(OF_BASIC | (4 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), MatchUtils.STR_DL_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (5 << SHIFT_FIELD) | EthType.NONE.getLength()), MatchUtils.STR_DL_TYPE);
		map.put(U32.ofRaw(OF_BASIC | (5 << SHIFT_FIELD) | EthType.NONE.getLength() * 2 | MASKED), MatchUtils.STR_DL_TYPE + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (6 << SHIFT_FIELD) | VlanVid.ZERO.getLength()), MatchUtils.STR_DL_VLAN);
		map.put(U32.ofRaw(OF_BASIC | (6 << SHIFT_FIELD) | VlanVid.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_DL_VLAN + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (7 << SHIFT_FIELD) | VlanPcp.NONE.getLength()), MatchUtils.STR_DL_VLAN_PCP);
		map.put(U32.ofRaw(OF_BASIC | (7 << SHIFT_FIELD) | VlanPcp.NONE.getLength() * 2 | MASKED), MatchUtils.STR_DL_VLAN_PCP + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (8 << SHIFT_FIELD) | IpDscp.NONE.getLength()), MatchUtils.STR_NW_DSCP);
		map.put(U32.ofRaw(OF_BASIC | (8 << SHIFT_FIELD) | IpDscp.NONE.getLength() * 2 | MASKED), MatchUtils.STR_NW_DSCP + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (9 << SHIFT_FIELD) | IpEcn.NONE.getLength()), MatchUtils.STR_NW_ECN);
		map.put(U32.ofRaw(OF_BASIC | (9 << SHIFT_FIELD) | IpEcn.NONE.getLength() * 2 | MASKED), MatchUtils.STR_NW_ECN + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (10 << SHIFT_FIELD) | IpProtocol.NONE.getLength()), MatchUtils.STR_NW_PROTO);
		map.put(U32.ofRaw(OF_BASIC | (10 << SHIFT_FIELD) | IpProtocol.NONE.getLength() * 2 | MASKED), MatchUtils.STR_NW_PROTO + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (11 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), MatchUtils.STR_NW_SRC);
		map.put(U32.ofRaw(OF_BASIC | (11 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_NW_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (12 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), MatchUtils.STR_NW_DST);
		map.put(U32.ofRaw(OF_BASIC | (12 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_NW_DST + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (13 << SHIFT_FIELD) | TransportPort.NONE.getLength()), MatchUtils.STR_TCP_SRC);
		map.put(U32.ofRaw(OF_BASIC | (13 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), MatchUtils.STR_TCP_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (14 << SHIFT_FIELD) | TransportPort.NONE.getLength()), MatchUtils.STR_TCP_DST);
		map.put(U32.ofRaw(OF_BASIC | (14 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), MatchUtils.STR_TCP_DST + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (15 << SHIFT_FIELD) | TransportPort.NONE.getLength()), MatchUtils.STR_UDP_SRC);
		map.put(U32.ofRaw(OF_BASIC | (15 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), MatchUtils.STR_UDP_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (16 << SHIFT_FIELD) | TransportPort.NONE.getLength()), MatchUtils.STR_UDP_DST);
		map.put(U32.ofRaw(OF_BASIC | (16 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), MatchUtils.STR_UDP_DST + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (17 << SHIFT_FIELD) | TransportPort.NONE.getLength()), MatchUtils.STR_SCTP_SRC);
		map.put(U32.ofRaw(OF_BASIC | (17 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), MatchUtils.STR_SCTP_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (18 << SHIFT_FIELD) | TransportPort.NONE.getLength()), MatchUtils.STR_SCTP_DST);
		map.put(U32.ofRaw(OF_BASIC | (18 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), MatchUtils.STR_SCTP_DST + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (19 << SHIFT_FIELD) | ICMPv4Type.NONE.getLength()), MatchUtils.STR_ICMP_TYPE);
		map.put(U32.ofRaw(OF_BASIC | (19 << SHIFT_FIELD) | ICMPv4Type.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ICMP_TYPE + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (20 << SHIFT_FIELD) | ICMPv4Code.NONE.getLength()), MatchUtils.STR_ICMP_CODE);
		map.put(U32.ofRaw(OF_BASIC | (20 << SHIFT_FIELD) | ICMPv4Code.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ICMP_CODE + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (21 << SHIFT_FIELD) | ArpOpcode.NONE.getLength()), MatchUtils.STR_ARP_OPCODE);
		map.put(U32.ofRaw(OF_BASIC | (21 << SHIFT_FIELD) | ArpOpcode.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ARP_OPCODE + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (22 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), MatchUtils.STR_ARP_SPA);
		map.put(U32.ofRaw(OF_BASIC | (22 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ARP_SPA + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (23 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), MatchUtils.STR_ARP_DPA);
		map.put(U32.ofRaw(OF_BASIC | (23 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ARP_DPA + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (24 << SHIFT_FIELD) | MacAddress.NONE.getLength()), MatchUtils.STR_ARP_SHA);
		map.put(U32.ofRaw(OF_BASIC | (24 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ARP_SHA + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (25 << SHIFT_FIELD) | MacAddress.NONE.getLength()), MatchUtils.STR_ARP_DHA);
		map.put(U32.ofRaw(OF_BASIC | (25 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), MatchUtils.STR_ARP_DHA + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (26 << SHIFT_FIELD) | IPv6Address.NONE.getLength()), MatchUtils.STR_IPV6_SRC);
		map.put(U32.ofRaw(OF_BASIC | (26 << SHIFT_FIELD) | IPv6Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_IPV6_SRC + STR_MASKED);
		
		
		map.put(U32.ofRaw(OF_BASIC | (27 << SHIFT_FIELD) | IPv6Address.NONE.getLength()), MatchUtils.STR_IPV6_DST);
		map.put(U32.ofRaw(OF_BASIC | (27 << SHIFT_FIELD) | IPv6Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_IPV6_DST + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (28 << SHIFT_FIELD) | IPv6FlowLabel.NONE.getLength()), MatchUtils.STR_IPV6_FLOW_LABEL);
		map.put(U32.ofRaw(OF_BASIC | (28 << SHIFT_FIELD) | IPv6FlowLabel.NONE.getLength() * 2 | MASKED), MatchUtils.STR_IPV6_FLOW_LABEL + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (29 << SHIFT_FIELD) | U8.ZERO.getLength()), MatchUtils.STR_ICMPV6_TYPE);
		map.put(U32.ofRaw(OF_BASIC | (29 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_ICMPV6_TYPE + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (30 << SHIFT_FIELD) | U8.ZERO.getLength()), MatchUtils.STR_ICMPV6_CODE);
		map.put(U32.ofRaw(OF_BASIC | (30 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_ICMPV6_CODE + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (31 << SHIFT_FIELD) | IPv6Address.NONE.getLength()), MatchUtils.STR_IPV6_ND_TARGET);
		map.put(U32.ofRaw(OF_BASIC | (31 << SHIFT_FIELD) | IPv6Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_IPV6_ND_TARGET + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (32 << SHIFT_FIELD) | MacAddress.NONE.getLength()), MatchUtils.STR_IPV6_ND_SSL);
		map.put(U32.ofRaw(OF_BASIC | (32 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), MatchUtils.STR_IPV6_ND_SSL + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (33 << SHIFT_FIELD) | MacAddress.NONE.getLength()), MatchUtils.STR_IPV6_ND_TTL);
		map.put(U32.ofRaw(OF_BASIC | (33 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), MatchUtils.STR_IPV6_ND_TTL + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (34 << SHIFT_FIELD) | U32.ZERO.getLength()), MatchUtils.STR_MPLS_LABEL);
		map.put(U32.ofRaw(OF_BASIC | (34 << SHIFT_FIELD) | U32.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_MPLS_LABEL + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (35 << SHIFT_FIELD) | U8.ZERO.getLength()), MatchUtils.STR_MPLS_TC);
		map.put(U32.ofRaw(OF_BASIC | (35 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_MPLS_TC + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (36 << SHIFT_FIELD) | OFBooleanValue.TRUE.getLength()), MatchUtils.STR_MPLS_BOS);
		map.put(U32.ofRaw(OF_BASIC | (36 << SHIFT_FIELD) | OFBooleanValue.TRUE.getLength() * 2 | MASKED), MatchUtils.STR_MPLS_BOS + STR_MASKED);
		
		/*
		 * TODO in Loxi and MatchUtils
		 * map.put(U32.ofRaw(OF_BASIC | (37 << SHIFT_FIELD) | ), MatchUtils.STR_PBB_ISID);
		 */
		map.put(U32.ofRaw(OF_BASIC | (37 << SHIFT_FIELD) | 3 ), MatchUtils.STR_PBB_ISID); /* need to update after Loxi support */
		map.put(U32.ofRaw(OF_BASIC | (37 << SHIFT_FIELD) | 3 * 2 | MASKED ), MatchUtils.STR_PBB_ISID + STR_MASKED);
		
		map.put(U32.ofRaw(OF_BASIC | (38 << SHIFT_FIELD) | U64.ZERO.getLength()), MatchUtils.STR_TUNNEL_ID);
		map.put(U32.ofRaw(OF_BASIC | (38 << SHIFT_FIELD) | U64.ZERO.getLength() * 2 | MASKED), MatchUtils.STR_TUNNEL_ID + STR_MASKED);
		
		/* 
		 * TODO in MatchUtils
		 * map.put(U32.ofRaw(OF_BASIC | 39), MatchUtils.STR_IPV6_EXTHDR);
		 */
		map.put(U32.ofRaw(OF_BASIC | (39 << SHIFT_FIELD) | 2), "ipv6_exthdr"); /* need to update after Loxi support */
		map.put(U32.ofRaw(OF_BASIC | (39 << SHIFT_FIELD) | 2 * 2 | MASKED), "ipv6_exthdr" + STR_MASKED);
		
		/* note skip of ID 40 here according to spec */
		
		/* 
		 * TODO in MatchUtils
		 * map.put(U32.ofRaw(OF_BASIC | 41), MatchUtils.STR_PBB_UCA);
		 */
		map.put(U32.ofRaw(OF_BASIC | (41 << SHIFT_FIELD) | 1), "pbb_uca");
		map.put(U32.ofRaw(OF_BASIC | (41 << SHIFT_FIELD) | 1 * 2 | MASKED), "pbb_uca" + STR_MASKED);
		
		/*
		 * NXM_0 Definitions
		 */
		map.put(U32.ofRaw(NXM_0 | (0 << SHIFT_FIELD) | 2 /* old ports are len=2 */), STR_NXM + MatchUtils.STR_IN_PORT);
		map.put(U32.ofRaw(NXM_0 | (0 << SHIFT_FIELD) | 2 * 2 /* old ports are len=2 */ | MASKED), STR_NXM + MatchUtils.STR_IN_PORT + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (1 << SHIFT_FIELD) | MacAddress.NONE.getLength()), STR_NXM + MatchUtils.STR_DL_DST);
		map.put(U32.ofRaw(NXM_0 | (1 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_DL_DST + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (2 << SHIFT_FIELD) | MacAddress.NONE.getLength()), STR_NXM + MatchUtils.STR_DL_SRC);
		map.put(U32.ofRaw(NXM_0 | (2 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_DL_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (3 << SHIFT_FIELD) | EthType.NONE.getLength()), STR_NXM + MatchUtils.STR_DL_TYPE);
		map.put(U32.ofRaw(NXM_0 | (3 << SHIFT_FIELD) | EthType.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_DL_TYPE + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (4 << SHIFT_FIELD) | VlanVid.ZERO.getLength()), STR_NXM + MatchUtils.STR_DL_VLAN);
		map.put(U32.ofRaw(NXM_0 | (4 << SHIFT_FIELD) | VlanVid.ZERO.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_DL_VLAN + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (5 << SHIFT_FIELD) | 1), STR_NXM + MatchUtils.STR_NW_TOS);
		map.put(U32.ofRaw(NXM_0 | (5 << SHIFT_FIELD) | 1 * 2 | MASKED), STR_NXM + MatchUtils.STR_NW_TOS + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (6 << SHIFT_FIELD) | IpProtocol.NONE.getLength()), STR_NXM + MatchUtils.STR_NW_PROTO);
		map.put(U32.ofRaw(NXM_0 | (6 << SHIFT_FIELD) | IpProtocol.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_NW_PROTO + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (7 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), STR_NXM + MatchUtils.STR_NW_SRC);
		map.put(U32.ofRaw(NXM_0 | (7 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_NW_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (8 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), STR_NXM + MatchUtils.STR_NW_DST);
		map.put(U32.ofRaw(NXM_0 | (8 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_NW_DST + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (9 << SHIFT_FIELD) | TransportPort.NONE.getLength()), STR_NXM + MatchUtils.STR_TCP_SRC);
		map.put(U32.ofRaw(NXM_0 | (9 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_TCP_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (10 << SHIFT_FIELD) | TransportPort.NONE.getLength()), STR_NXM + MatchUtils.STR_TCP_DST);
		map.put(U32.ofRaw(NXM_0 | (10 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_TCP_DST + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (11 << SHIFT_FIELD) | TransportPort.NONE.getLength()), STR_NXM + MatchUtils.STR_UDP_SRC);
		map.put(U32.ofRaw(NXM_0 | (11 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_UDP_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (12 << SHIFT_FIELD) | TransportPort.NONE.getLength()), STR_NXM + MatchUtils.STR_UDP_DST);
		map.put(U32.ofRaw(NXM_0 | (12 << SHIFT_FIELD) | TransportPort.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_UDP_DST + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (13 << SHIFT_FIELD) | U8.ZERO.getLength()), STR_NXM + MatchUtils.STR_ICMP_TYPE);
		map.put(U32.ofRaw(NXM_0 | (13 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ICMP_TYPE + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (14 << SHIFT_FIELD) | U8.ZERO.getLength()), STR_NXM + MatchUtils.STR_ICMP_CODE);
		map.put(U32.ofRaw(NXM_0 | (14 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ICMP_CODE + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (15 << SHIFT_FIELD) | ArpOpcode.NONE.getLength()), STR_NXM + MatchUtils.STR_ARP_OPCODE);
		map.put(U32.ofRaw(NXM_0 | (15 << SHIFT_FIELD) | ArpOpcode.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ARP_OPCODE + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (16 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), STR_NXM + MatchUtils.STR_ARP_SPA);
		map.put(U32.ofRaw(NXM_0 | (16 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ARP_SPA + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_0 | (17 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), STR_NXM + MatchUtils.STR_ARP_DPA);
		map.put(U32.ofRaw(NXM_0 | (17 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ARP_DPA + STR_MASKED);

		/*
		 * NXM_1 Definitions
		 */
		map.put(U32.ofRaw(NXM_1 | (0 << SHIFT_FIELD) | 4), STR_NXM + "reg_0");
		map.put(U32.ofRaw(NXM_1 | (0 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_0" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (1 << SHIFT_FIELD) | 4), STR_NXM + "reg_1");
		map.put(U32.ofRaw(NXM_1 | (1 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_1" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (2 << SHIFT_FIELD) | 4), STR_NXM + "reg_2");
		map.put(U32.ofRaw(NXM_1 | (2 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_2" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (3 << SHIFT_FIELD) | 4), STR_NXM + "reg_3");
		map.put(U32.ofRaw(NXM_1 | (3 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_3" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (4 << SHIFT_FIELD) | 4), STR_NXM + "reg_4");
		map.put(U32.ofRaw(NXM_1 | (4 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_4" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (5 << SHIFT_FIELD) | 4), STR_NXM + "reg_5");
		map.put(U32.ofRaw(NXM_1 | (5 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_5" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (6 << SHIFT_FIELD) | 4), STR_NXM + "reg_6");
		map.put(U32.ofRaw(NXM_1 | (6 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_6" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (7 << SHIFT_FIELD) | 4), STR_NXM + "reg_7");
		map.put(U32.ofRaw(NXM_1 | (7 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "reg_7" + STR_MASKED);

		map.put(U32.ofRaw(NXM_1 | (16 << SHIFT_FIELD) | U64.ZERO.getLength()), STR_NXM + MatchUtils.STR_TUNNEL_ID);
		map.put(U32.ofRaw(NXM_1 | (16 << SHIFT_FIELD) | U64.ZERO.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_TUNNEL_ID + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (17 << SHIFT_FIELD) | MacAddress.NONE.getLength()), STR_NXM + MatchUtils.STR_ARP_SHA);
		map.put(U32.ofRaw(NXM_1 | (17 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ARP_SHA + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (18 << SHIFT_FIELD) | MacAddress.NONE.getLength()), STR_NXM + MatchUtils.STR_ARP_DHA);
		map.put(U32.ofRaw(NXM_1 | (18 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ARP_DHA + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (19 << SHIFT_FIELD) | IPv6Address.NONE.getLength()), STR_NXM + MatchUtils.STR_IPV6_SRC);
		map.put(U32.ofRaw(NXM_1 | (19 << SHIFT_FIELD) | IPv6Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_IPV6_SRC + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (20 << SHIFT_FIELD) | IPv6Address.NONE.getLength()), STR_NXM + MatchUtils.STR_IPV6_DST);
		map.put(U32.ofRaw(NXM_1 | (20 << SHIFT_FIELD) | IPv6Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_IPV6_DST + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (21 << SHIFT_FIELD) | U8.ZERO.getLength()), STR_NXM + MatchUtils.STR_ICMPV6_TYPE);
		map.put(U32.ofRaw(NXM_1 | (21 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ICMPV6_TYPE + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (22 << SHIFT_FIELD) | U8.ZERO.getLength()), STR_NXM + MatchUtils.STR_ICMPV6_CODE);
		map.put(U32.ofRaw(NXM_1 | (22 << SHIFT_FIELD) | U8.ZERO.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_ICMPV6_CODE + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (23 << SHIFT_FIELD) | IPv6Address.NONE.getLength()), STR_NXM + MatchUtils.STR_IPV6_ND_TARGET);
		map.put(U32.ofRaw(NXM_1 | (23 << SHIFT_FIELD) | IPv6Address.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_IPV6_ND_TARGET + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (24 << SHIFT_FIELD) | MacAddress.NONE.getLength()), STR_NXM + MatchUtils.STR_IPV6_ND_SSL);
		map.put(U32.ofRaw(NXM_1 | (24 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_IPV6_ND_SSL + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (25 << SHIFT_FIELD) | MacAddress.NONE.getLength()), STR_NXM + MatchUtils.STR_IPV6_ND_TTL);
		map.put(U32.ofRaw(NXM_1 | (25 << SHIFT_FIELD) | MacAddress.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_IPV6_ND_TTL + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (26 << SHIFT_FIELD) | 1), STR_NXM + "ip_frag");
		map.put(U32.ofRaw(NXM_1 | (26 << SHIFT_FIELD) | 1 * 2 | MASKED), STR_NXM + "ip_frag" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (27 << SHIFT_FIELD) | IPv6FlowLabel.NONE.getLength()), STR_NXM + MatchUtils.STR_IPV6_FLOW_LABEL);	
		map.put(U32.ofRaw(NXM_1 | (27 << SHIFT_FIELD) | IPv6FlowLabel.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_IPV6_FLOW_LABEL + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (28 << SHIFT_FIELD) | IpEcn.NONE.getLength()), STR_NXM + MatchUtils.STR_NW_ECN);
		map.put(U32.ofRaw(NXM_1 | (28 << SHIFT_FIELD) | IpEcn.NONE.getLength() * 2 | MASKED), STR_NXM + MatchUtils.STR_NW_ECN + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (29 << SHIFT_FIELD) | 1), STR_NXM + "ip_ttl");
		map.put(U32.ofRaw(NXM_1 | (29 << SHIFT_FIELD) | 1 * 2 | MASKED), STR_NXM + "ip_ttl" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (30 << SHIFT_FIELD) | 8), STR_NXM + "flow_cookie");
		map.put(U32.ofRaw(NXM_1 | (30 << SHIFT_FIELD) | 8 * 2 | MASKED), STR_NXM + "flow_cookie" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (31 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), MatchUtils.STR_TUNNEL_IPV4_SRC);
		map.put(U32.ofRaw(NXM_1 | (31 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_TUNNEL_IPV4_SRC + MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (32 << SHIFT_FIELD) | IPv4Address.NONE.getLength()), MatchUtils.STR_TUNNEL_IPV4_DST);
		map.put(U32.ofRaw(NXM_1 | (32 << SHIFT_FIELD) | IPv4Address.NONE.getLength() * 2 | MASKED), MatchUtils.STR_TUNNEL_IPV4_DST + MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (33 << SHIFT_FIELD) | 4), STR_NXM + "pkt_mark");
		map.put(U32.ofRaw(NXM_1 | (33 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "pkt_mark" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (34 << SHIFT_FIELD) | 2), STR_NXM + "tcp_flags");
		map.put(U32.ofRaw(NXM_1 | (34 << SHIFT_FIELD) | 2 * 2 | MASKED), STR_NXM + "tcp_flags" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (35 << SHIFT_FIELD) | 4), STR_NXM + "internal_dp_hash");
		map.put(U32.ofRaw(NXM_1 | (35 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "internal_dp_hash" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (36 << SHIFT_FIELD) | 4), STR_NXM + "internal_recirc_id");
		map.put(U32.ofRaw(NXM_1 | (36 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + "internal_recirc_id" + STR_MASKED);
		
		map.put(U32.ofRaw(NXM_1 | (37 << SHIFT_FIELD) | 4), STR_NXM + MatchUtils.STR_PBB_ISID);
		map.put(U32.ofRaw(NXM_1 | (37 << SHIFT_FIELD) | 4 * 2 | MASKED), STR_NXM + MatchUtils.STR_PBB_ISID + STR_MASKED);
		
	}
	
	/**
	 * Converts the string representation of an OXM ID as
	 * defined in {@link net.floodlightcontroller.util.MatchUtils.java}
	 * to its corresponding U32 OXM ID.
	 * 
	 * Any NXM (not defined in {@link net.floodlightcontroller.util.MatchUtils.java}
	 * is the same as the OXM string with an "nxm_" prefix.
	 * 
	 * Any OXM or NXM that is masked include a "_masked" postfix.
	 * 
	 * @param oxmId, the U32 defining the OXM/NXM as [class]+[field]+[has_mask]+[length]
	 * @return the String representing the OXM/NXM
	 */
	public static String oxmIdToString(U32 oxmId) {
		if (map.containsKey(oxmId)) {
			return map.get(oxmId);
		} else {
			return "Unknown OXM ID: " + oxmId.toString();
		}
	}
	
	/**
	 * Converts the string representation of an OXM ID as
	 * defined in {@link net.floodlightcontroller.util.MatchUtils.java}
	 * to its corresponding U32 OXM ID.
	 * 
	 * Any NXM (not defined in {@link net.floodlightcontroller.util.MatchUtils.java}
	 * is the same as the OXM string with an "nxm_" prefix.
	 * 
	 * Any OXM or NXM that is masked include a "_masked" postfix.
	 * 
	 * @param oxmString, the string definging the OXM/NXM
	 * @return the U32 representing the OXM/NXM as [class]+[field]+[has_mask]+[length]
	 */
	public static U32 oxmStringToId(String oxmString) {
		if (map.inverse().containsKey(oxmString)) {
			return map.inverse().get(oxmString);
		} else {
			return null;
		}
	}
}
