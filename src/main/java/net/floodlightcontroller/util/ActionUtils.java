package net.floodlightcontroller.util;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
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
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;

/**
 * OFAction helper functions. Use with any OpenFlowJ-Loxi Action.
 * String utility functions for converting OFActions to and from
 * dpctl/ofctl-style strings, which is primarily used by the
 * static flow pusher.
 * 
 * Includes string methods refactored from StaticFlowEntryPusher
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */
public class ActionUtils {
	/* OF1.3 ACTIONS (includes OF1.0) */
	public static final String STR_OUTPUT = "output";
	public static final String STR_ENQUEUE = "enqueue";
	public static final String STR_VLAN_STRIP = "strip_vlan";
	public static final String STR_VLAN_POP = "pop_vlan";
	public static final String STR_VLAN_PUSH = "push_vlan";
	public static final String STR_VLAN_SET_PCP = "set_vlan_pcp";
	public static final String STR_VLAN_SET_VID = "set_vlan_vid";
	public static final String STR_QUEUE_SET = "set_queue";
	public static final String STR_DL_SRC_SET = "set_eth_src";
	public static final String STR_DL_DST_SET = "set_eth_dst";
	public static final String STR_NW_SRC_SET = "set_ipv4_src";
	public static final String STR_NW_DST_SET = "set_ipv4_dst";
	public static final String STR_NW_ECN_SET = "set_ip_ecn";
	public static final String STR_NW_TOS_SET = "set_ip_tos";
	public static final String STR_NW_TTL_SET = "set_ip_ttl";
	public static final String STR_NW_TTL_DEC = "dec_ip_ttl";
	public static final String STR_TTL_IN_COPY = "copy_ip_ttl_in";
	public static final String STR_TTL_OUT_COPY = "copy_ip_ttl_out";
	public static final String STR_MPLS_LABEL_SET = "set_mpls_label";
	public static final String STR_MPLS_TC_SET = "set_mpls_tc";
	public static final String STR_MPLS_TTL_SET = "set_mpls_ttl";
	public static final String STR_MPLS_TTL_DEC = "dec_mpls_ttl";
	public static final String STR_MPLS_PUSH = "push_mpls";
	public static final String STR_MPLS_POP = "pop_mpls";
	public static final String STR_TP_SRC_SET = "set_tp_src";
	public static final String STR_TP_DST_SET = "set_tp_dst";
	public static final String STR_PBB_PUSH = "push_pbb";
	public static final String STR_PBB_POP = "pop_pbb";
	public static final String STR_GROUP = "group";
	public static final String STR_FIELD_SET = "set_field";
	public static final String STR_EXPERIMENTER = "experimenter";
	public static final String STR_NOT_APPLICABLE = "n/a";

	/* OF1.3 set-field operations are defined as any OF1.3 match.
	 * We will borrow MatchUtils's String definitions of all OF1.3
	 * set-field operations to be consistent.
	 */

	/**
	 * Returns a String representation of all the OpenFlow actions.
	 * @param actions; A list of OFActions to encode into one string
	 * @return A dpctl-style string of the actions
	 */
	@LogMessageDoc(level="ERROR",
			message="Could not decode action {action}",
			explanation="A static flow entry contained an invalid action",
			recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	public static String actionsToString(List<OFAction> actions, Logger log) {
		StringBuilder sb = new StringBuilder();
		for (OFAction a : actions) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			switch(a.getType()) {
			case OUTPUT:
				sb.append(STR_OUTPUT + "=" + Integer.toString(((OFActionOutput)a).getPort().getPortNumber()));
				break;
			case ENQUEUE:
				long queue = ((OFActionEnqueue)a).getQueueId();
				OFPort port = ((OFActionEnqueue)a).getPort();
				sb.append(STR_ENQUEUE + "=" + Integer.toString(port.getPortNumber()) + ":0x" + String.format("%02x", queue));
				break;
			case STRIP_VLAN:
				sb.append(STR_VLAN_STRIP);
				break;
			case POP_VLAN:
				sb.append(STR_VLAN_POP);
				break;
			case PUSH_VLAN:
				sb.append(STR_VLAN_PUSH + "=" + Integer.toString(((OFActionPushVlan)a).getEthertype().getValue()));
				break;
			case SET_VLAN_VID:
				sb.append(STR_VLAN_SET_VID + "=" + Short.toString(((OFActionSetVlanVid)a).getVlanVid().getVlan()));
				break;
			case SET_VLAN_PCP:
				sb.append(STR_VLAN_SET_PCP + "=" + Byte.toString(((OFActionSetVlanPcp)a).getVlanPcp().getValue()));
				break;
			case SET_QUEUE:
				sb.append(STR_QUEUE_SET + "=" + Long.toString(((OFActionSetQueue)a).getQueueId()));
			case SET_DL_SRC:
				sb.append(STR_DL_SRC_SET + "=" +  ((OFActionSetDlSrc)a).getDlAddr().toString());
				break;
			case SET_DL_DST:
				sb.append(STR_DL_DST_SET + "=" + ((OFActionSetDlDst)a).getDlAddr().toString());
				break;
			case SET_NW_ECN:
				sb.append(STR_NW_ECN_SET + "=" + Byte.toString(((OFActionSetNwEcn)a).getNwEcn().getEcnValue()));
				break;
			case SET_NW_TOS:
				sb.append(STR_NW_TOS_SET + "=" + Short.toString(((OFActionSetNwTos)a).getNwTos()));
				break;
			case SET_NW_TTL:
				sb.append(STR_NW_TTL_SET + "=" + Short.toString(((OFActionSetNwTtl)a).getNwTtl()));
				break;
			case DEC_NW_TTL:
				sb.append(STR_NW_TTL_DEC);
				break;
			case SET_MPLS_LABEL:
				sb.append(STR_MPLS_LABEL_SET + "=" + Long.toString(((OFActionSetMplsLabel)a).getMplsLabel()));
				break;
			case SET_MPLS_TC:
				sb.append(STR_MPLS_TC_SET + "=" + Short.toString(((OFActionSetMplsTc)a).getMplsTc()));
				break;
			case SET_MPLS_TTL:
				sb.append(STR_MPLS_TTL_SET + "=" + Short.toString(((OFActionSetMplsTtl)a).getMplsTtl()));
				break;
			case DEC_MPLS_TTL:
				sb.append(STR_MPLS_TTL_DEC);
				break;
			case PUSH_MPLS:
				sb.append(STR_MPLS_PUSH + "=" + Integer.toString(((OFActionPushMpls)a).getEthertype().getValue()));
				break;
			case POP_MPLS:
				sb.append(STR_MPLS_POP + "=" + Integer.toString(((OFActionPopMpls)a).getEthertype().getValue()));
				break;
			case SET_NW_SRC:
				sb.append(STR_NW_SRC_SET + "=" + ((OFActionSetNwSrc)a).getNwAddr().toString());
				break;
			case SET_NW_DST:
				sb.append(STR_NW_DST_SET + "=" + ((OFActionSetNwDst)a).getNwAddr().toString());
				break;
			case SET_TP_SRC:
				sb.append(STR_TP_SRC_SET + "=" + ((OFActionSetTpSrc)a).getTpPort().toString());
				break;
			case SET_TP_DST:
				sb.append(STR_TP_DST_SET + "=" + ((OFActionSetTpDst)a).getTpPort().toString());
				break;
			case COPY_TTL_IN:
				sb.append(STR_TTL_IN_COPY);
				break;
			case COPY_TTL_OUT:
				sb.append(STR_TTL_OUT_COPY);
				break;
			case PUSH_PBB:
				sb.append(STR_PBB_PUSH + "=" + Integer.toString(((OFActionPushPbb)a).getEthertype().getValue()));
				break;
			case POP_PBB:
				sb.append(STR_PBB_POP);
				break;
			case EXPERIMENTER:
				sb.append(STR_EXPERIMENTER + "=" + Long.toString(((OFActionExperimenter)a).getExperimenter()));
				break;
			case GROUP:
				sb.append(STR_GROUP + "=" + Integer.toString(((OFActionGroup)a).getGroup().getGroupNumber()));
				break;
			case SET_FIELD:
				log.debug("Got Set-Field action. Setting " + ((OFActionSetField)a));
				/* ARP */
				if (((OFActionSetField)a).getField() instanceof OFOxmArpOp) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ARP_OPCODE + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmArpOp) ((OFActionSetField) a).getField()).getValue().getOpcode()));
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpSha) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ARP_SHA + MatchUtils.SET_FIELD_DELIM + ((OFOxmArpSha) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpTha) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ARP_DHA + MatchUtils.SET_FIELD_DELIM + ((OFOxmArpTha) ((OFActionSetField) a).getField()).getValue().toString());
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpSpa) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ARP_SPA + MatchUtils.SET_FIELD_DELIM + ((OFOxmArpSpa) ((OFActionSetField) a).getField()).getValue().toString()); // ipaddress formats string already
				} else if (((OFActionSetField)a).getField() instanceof OFOxmArpTpa) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ARP_DPA + MatchUtils.SET_FIELD_DELIM + ((OFOxmArpTpa) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdSll) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_IPV6_ND_SSL + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv6NdSll) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTll) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_IPV6_ND_TTL + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv6NdTll) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTarget) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_IPV6_ND_TARGET + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv6NdTarget) ((OFActionSetField) a).getField()).getValue().toString()); 
				}
				/* DATA LAYER */
				else if (((OFActionSetField)a).getField() instanceof OFOxmEthType) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_DL_TYPE + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmEthType) ((OFActionSetField) a).getField()).getValue().getValue()));
				} else if (((OFActionSetField)a).getField() instanceof OFOxmEthSrc) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_DL_SRC + MatchUtils.SET_FIELD_DELIM + ((OFOxmEthSrc) ((OFActionSetField) a).getField()).getValue().toString());
				} else if (((OFActionSetField)a).getField() instanceof OFOxmEthDst) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_DL_DST + MatchUtils.SET_FIELD_DELIM + ((OFOxmEthDst) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmVlanVid) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_DL_VLAN + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmVlanVid) ((OFActionSetField) a).getField()).getValue().getVlan())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmVlanPcp) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_DL_VLAN_PCP + MatchUtils.SET_FIELD_DELIM + Byte.toString(((OFOxmVlanPcp) ((OFActionSetField) a).getField()).getValue().getValue())); 
				} 
				/* ICMP */
				else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Code) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ICMP_CODE + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmIcmpv4Code) ((OFActionSetField) a).getField()).getValue().getCode())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Type) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ICMP_TYPE + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmIcmpv4Type) ((OFActionSetField) a).getField()).getValue().getType())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Code) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ICMPV6_CODE + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmIcmpv6Code) ((OFActionSetField) a).getField()).getValue().getRaw())); 
				}  else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Type) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_ICMPV6_TYPE + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmIcmpv6Type) ((OFActionSetField) a).getField()).getValue().getRaw())); 
				}
				/* NETWORK LAYER */
				else if (((OFActionSetField)a).getField() instanceof OFOxmIpProto) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_NW_PROTO + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmIpProto) ((OFActionSetField) a).getField()).getValue().getIpProtocolNumber())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Src) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_NW_SRC + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv4Src) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Dst) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_NW_DST + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv4Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Src) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_IPV6_SRC + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv6Src) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Dst) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_IPV6_DST + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv6Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Flabel) {                		
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_IPV6_FLOW_LABEL + MatchUtils.SET_FIELD_DELIM + ((OFOxmIpv6Flabel) ((OFActionSetField) a).getField()).getValue().toString()); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpEcn) { //TODO @Ryan ECN and DSCP need to have their own columns for OF1.3....
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_NW_ECN + MatchUtils.SET_FIELD_DELIM + Byte.toString(((OFOxmIpEcn) ((OFActionSetField) a).getField()).getValue().getEcnValue())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmIpDscp) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_NW_DSCP + MatchUtils.SET_FIELD_DELIM + Byte.toString(((OFOxmIpDscp) ((OFActionSetField) a).getField()).getValue().getDscpValue())); 
				} 
				/* TRANSPORT LAYER, TCP, UDP, and SCTP */
				else if (((OFActionSetField)a).getField() instanceof OFOxmTcpSrc) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_TCP_SRC + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmTcpSrc) ((OFActionSetField) a).getField()).getValue().getPort())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmTcpDst) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_TCP_DST + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmTcpDst) ((OFActionSetField) a).getField()).getValue().getPort())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmUdpSrc) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_UDP_SRC + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmUdpSrc) ((OFActionSetField) a).getField()).getValue().getPort())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmUdpDst) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_UDP_DST + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmUdpDst) ((OFActionSetField) a).getField()).getValue().getPort())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmSctpSrc) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_SCTP_SRC + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmSctpSrc) ((OFActionSetField) a).getField()).getValue().getPort())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmSctpDst) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_SCTP_DST + MatchUtils.SET_FIELD_DELIM + Integer.toString(((OFOxmSctpDst) ((OFActionSetField) a).getField()).getValue().getPort())); 
				}
				/* MPLS */
				else if (((OFActionSetField)a).getField() instanceof OFOxmMplsLabel) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_MPLS_LABEL + MatchUtils.SET_FIELD_DELIM + Long.toString(((OFOxmMplsLabel) ((OFActionSetField) a).getField()).getValue().getValue())); 
				} else if (((OFActionSetField)a).getField() instanceof OFOxmMplsTc) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_MPLS_TC + MatchUtils.SET_FIELD_DELIM + Short.toString(((OFOxmMplsTc) ((OFActionSetField) a).getField()).getValue().getValue())); 
				} // MPLS_BOS not implemented in loxi
				/* METADATA */
				else if (((OFActionSetField)a).getField() instanceof OFOxmMetadata) {
					sb.append(STR_FIELD_SET + "=" + MatchUtils.STR_METADATA + MatchUtils.SET_FIELD_DELIM + Long.toString(((OFOxmMetadata) ((OFActionSetField) a).getField()).getValue().getValue().getValue())); 
				} else {
					log.error("Could not decode Set-Field action field: {}", ((OFActionSetField) a));
				}
				break;
			default:
				log.error("Could not decode action: {}", a);
				break;
			}

		}

		return sb.toString();
	}

	/**
	 * Parses OFFlowMod actions from strings.
	 * @param fmb The OFFlowMod.Builder to set the actions for
	 * @param bigString The string containing all the actions
	 * @param log A logger to log for errors.
	 */
	@LogMessageDoc(level="ERROR",
			message="Unexpected action '{action}', '{subaction}'",
			explanation="A static flow entry contained an invalid action",
			recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	public static void fromString(OFFlowMod.Builder fmb, String bigString, Logger log) {
		List<OFAction> actions = new LinkedList<OFAction>();
		if (bigString != null && !bigString.trim().isEmpty()) {
			bigString = bigString.toLowerCase();
			String[] bigStringSplit = bigString.split(","); // split into separate action=value or action=key@value pairs

			String[] tmp;
			ArrayDeque<String[]> actionToDecode = new ArrayDeque<String[]>();
			for (int i = 0; i < bigStringSplit.length; i++) {
				tmp = bigStringSplit[i].split("="); // split into separate [action, value] or [action, key@value] singles
				if (tmp.length != 2) {
					log.debug("Token " + bigStringSplit[i] + " does not have form 'key=value' parsing " + bigString);
				}
				actionToDecode.add(tmp); // actionToDecode contains [key, value] pairs. Create a queue of pairs to process.
			}	

			while (!actionToDecode.isEmpty()) {
				String[] keyPair = actionToDecode.pollFirst();
				String key;
				String pair;
				if (keyPair.length != 2) {
					log.debug("[Key, Value] {} does not have form 'key=value' parsing, which is okay for some actions e.g. 'pop_vlan'.", keyPair);
					key = keyPair[0]; // could the be case of a constant actions (e.g. copy_ttl_in)
					pair = "";
				} else {
					key = keyPair[0];
					pair = keyPair[1];
				}

				OFAction a = null;
				try {
					switch (key) {
					case STR_OUTPUT:
						a = decode_output(pair, fmb.getVersion(), log);
						break;
					case STR_ENQUEUE:
						a = decode_enqueue(pair, fmb.getVersion(), log);
						break;
					case STR_DL_SRC_SET:
						a = decode_set_src_mac(pair, fmb.getVersion(), log);
						break;
					case STR_DL_DST_SET:
						a = decode_set_dst_mac(pair, fmb.getVersion(), log);
						break;
					case STR_EXPERIMENTER:
						//no-op. Not implemented
						log.error("OFAction EXPERIMENTER not implemented.");
						break;
					case STR_FIELD_SET: /* ONLY OF1.1+ should get in here. These should only be header fields valid within a set-field. */
						String[] actionData = pair.split(MatchUtils.SET_FIELD_DELIM);
						if (actionData.length != 2) {
							throw new IllegalArgumentException("[Action, Data] " + keyPair + " does not have form 'action=data'" + actionData);
						}

						switch (actionData[0]) {
						case MatchUtils.STR_ARP_OPCODE:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildArpOp().setValue(ArpOpcode.of(Integer.parseInt(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildArpOp().setValue(ArpOpcode.of(Integer.parseInt(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_ARP_SHA:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildArpSha().setValue(MacAddress.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_ARP_DHA:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildArpTha().setValue(MacAddress.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_ARP_SPA:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildArpSpa().setValue(IPv4Address.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_ARP_DPA:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildArpTpa().setValue(IPv4Address.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_IPV6_ND_SSL:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6NdSll().setValue(MacAddress.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_IPV6_ND_TTL:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6NdTll().setValue(MacAddress.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_IPV6_ND_TARGET:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6NdTarget().setValue(IPv6Address.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_DL_TYPE:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildEthType().setValue(EthType.of(Integer.parseInt(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildEthType().setValue(EthType.of(Integer.parseInt(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_DL_SRC:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildEthSrc().setValue(MacAddress.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_DL_DST:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildEthDst().setValue(MacAddress.of(actionData[1])).build())
							.build();
							break;
						case MatchUtils.STR_DL_VLAN:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildVlanVid().setValue(OFVlanVidMatch.ofVlan(Integer.parseInt(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildVlanVid().setValue(OFVlanVidMatch.ofVlan(Integer.parseInt(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_DL_VLAN_PCP:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildVlanPcp().setValue(VlanPcp.of(Byte.parseByte(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildVlanPcp().setValue(VlanPcp.of(Byte.parseByte(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_ICMP_CODE:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv4Code().setValue(ICMPv4Code.of(Short.parseShort(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv4Code().setValue(ICMPv4Code.of(Short.parseShort(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_ICMP_TYPE:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv4Type().setValue(ICMPv4Type.of(Short.parseShort(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv4Type().setValue(ICMPv4Type.of(Short.parseShort(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_ICMPV6_CODE:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv6Code().setValue(U8.of(Short.parseShort(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv6Code().setValue(U8.of(Short.parseShort(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_ICMPV6_TYPE:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv6Type().setValue(U8.of(Short.parseShort(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIcmpv6Type().setValue(U8.of(Short.parseShort(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_NW_PROTO:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpProto().setValue(IpProtocol.of(Short.parseShort(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpProto().setValue(IpProtocol.of(Short.parseShort(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_NW_SRC:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv4Src().setValue(IPv4Address.of(actionData[1])).build())
							.build();						
							break;
						case MatchUtils.STR_NW_DST:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv4Dst().setValue(IPv4Address.of(actionData[1])).build())
							.build();						
							break;
						case MatchUtils.STR_IPV6_SRC:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6Src().setValue(IPv6Address.of(actionData[1])).build())
							.build();						
							break;
						case MatchUtils.STR_IPV6_DST:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6Dst().setValue(IPv6Address.of(actionData[1])).build())
							.build();						
							break;
						case MatchUtils.STR_IPV6_FLOW_LABEL:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6Flabel().setValue(IPv6FlowLabel.of(Integer.parseInt(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();			
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpv6Flabel().setValue(IPv6FlowLabel.of(Integer.parseInt(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_NW_ECN:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpEcn().setValue(IpEcn.of(Byte.parseByte(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpEcn().setValue(IpEcn.of(Byte.parseByte(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_NW_DSCP:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpDscp().setValue(IpDscp.of(Byte.parseByte(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildIpDscp().setValue(IpDscp.of(Byte.parseByte(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_SCTP_SRC:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildSctpSrc().setValue(TransportPort.of(Integer.parseInt(actionData[1]))).build())
							.build();	
							break;
						case MatchUtils.STR_SCTP_DST:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildSctpDst().setValue(TransportPort.of(Integer.parseInt(actionData[1]))).build())
							.build();	
							break;
						case MatchUtils.STR_TCP_SRC:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildTcpSrc().setValue(TransportPort.of(Integer.parseInt(actionData[1]))).build())
							.build();	
							break;
						case MatchUtils.STR_TCP_DST:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildTcpDst().setValue(TransportPort.of(Integer.parseInt(actionData[1]))).build())
							.build();	
							break;
						case MatchUtils.STR_UDP_SRC:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildUdpSrc().setValue(TransportPort.of(Integer.parseInt(actionData[1]))).build())
							.build();	
							break;
						case MatchUtils.STR_UDP_DST:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildUdpDst().setValue(TransportPort.of(Integer.parseInt(actionData[1]))).build())
							.build();	
							break;
						case MatchUtils.STR_MPLS_LABEL:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMplsLabel().setValue(U32.of(Long.parseLong(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMplsLabel().setValue(U32.of(Long.parseLong(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_MPLS_TC:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMplsTc().setValue(U8.of(Short.parseShort(actionData[1].replaceFirst("0x", ""), 16))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMplsTc().setValue(U8.of(Short.parseShort(actionData[1]))).build())
										.build();
							}
							break;
						case MatchUtils.STR_MPLS_BOS:
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
							.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMplsBos().setValue(OFBooleanValue.of(Boolean.parseBoolean(actionData[1]))).build()) // interprets anything other than "true" as false
							.build();
							break;
						case MatchUtils.STR_METADATA:
							if (actionData[1].startsWith("0x")) {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMetadata().setValue(OFMetadata.of(U64.of(Long.parseLong(actionData[1].replaceFirst("0x", ""), 16)))).build())
										.build();
							} else {
								a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetField()
										.setField(OFFactories.getFactory(fmb.getVersion()).oxms().buildMetadata().setValue(OFMetadata.of(U64.of(Long.parseLong(actionData[1])))).build())
										.build();
							}
							break;
						default:
							log.error("UNEXPECTED OF1.3 SET-FIELD '{}'", actionData);
							break;
						}					
						break;
					case STR_GROUP:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildGroup()
									.setGroup(OFGroup.of(Integer.parseInt(pair.replaceFirst("0x", ""), 16)))
									.build();	
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildGroup()
									.setGroup(OFGroup.of(Integer.parseInt(pair)))
									.build();		
						}
						break;
					case STR_MPLS_LABEL_SET:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetMplsLabel()
									.setMplsLabel(Long.parseLong(pair.replaceFirst("0x", ""), 16))
									.build();			
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetMplsLabel()
									.setMplsLabel(Long.parseLong(pair))
									.build();					
						}
						break;
					case STR_MPLS_POP:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPopMpls()
									.setEthertype(EthType.of(Integer.parseInt(pair.replaceFirst("0x", ""), 16)))
									.build();
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPopMpls()
									.setEthertype(EthType.of(Integer.parseInt(pair)))
									.build();	
						}
						break;
					case STR_MPLS_PUSH:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPushMpls()
									.setEthertype(EthType.of(Integer.parseInt(pair.replaceFirst("0x", ""), 16)))
									.build();		
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPushMpls()
									.setEthertype(EthType.of(Integer.parseInt(pair)))
									.build();			
						}
						break;
					case STR_MPLS_TC_SET:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetMplsTc()
									.setMplsTc(Short.parseShort(pair.replaceFirst("0x", ""), 16))
									.build();	
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetMplsTc()
									.setMplsTc(Short.parseShort(pair))
									.build();			
						}
						break;
					case STR_MPLS_TTL_DEC:
						a = OFFactories.getFactory(fmb.getVersion()).actions().decMplsTtl();
						break;
					case STR_MPLS_TTL_SET:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetMplsTtl()
									.setMplsTtl(Short.parseShort(pair.replaceFirst("0x", ""), 16))
									.build();	
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetMplsTtl()
									.setMplsTtl(Short.parseShort(pair))
									.build();				
						}
						break;
					case STR_NW_TOS_SET:
						a = decode_set_tos_bits(pair, fmb.getVersion(), log); // should only be used by OF1.0
						break;
					case STR_NW_SRC_SET:
						a = decode_set_src_ip(pair, fmb.getVersion(), log);
						break;
					case STR_NW_DST_SET:
						a = decode_set_dst_ip(pair, fmb.getVersion(), log);
						break;
					case STR_NW_ECN_SET: // loxi does not support DSCP set for OF1.1
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetNwEcn()
									.setNwEcn(IpEcn.of(Byte.parseByte(pair.replaceFirst("0x", ""), 16)))
									.build();		
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetNwEcn()
									.setNwEcn(IpEcn.of(Byte.parseByte(pair)))
									.build();							
						}
						break;
					case STR_NW_TTL_DEC:
						a = OFFactories.getFactory(fmb.getVersion()).actions().decNwTtl();
						break;
					case STR_NW_TTL_SET:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetNwTtl()
									.setNwTtl(Short.parseShort(pair.replaceFirst("0x", ""), 16))
									.build();
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetNwTtl()
									.setNwTtl(Short.parseShort(pair))
									.build();						
						}
						break;
					case STR_PBB_POP:
						a = OFFactories.getFactory(fmb.getVersion()).actions().popPbb();
						break;
					case STR_PBB_PUSH:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPushPbb()
									.setEthertype(EthType.of(Integer.parseInt(pair.replaceFirst("0x", ""), 16)))
									.build();				
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPushPbb()
									.setEthertype(EthType.of(Integer.parseInt(pair)))
									.build();					
						}
						break;
					case STR_QUEUE_SET:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetQueue()
									.setQueueId(Long.parseLong(pair.replaceFirst("0x", ""), 16))
									.build();	
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildSetQueue()
									.setQueueId(Long.parseLong(pair))
									.build();					
						}
						break;
					case STR_TP_SRC_SET:
						a = decode_set_src_port(pair, fmb.getVersion(), log);
						break;
					case STR_TP_DST_SET:
						a = decode_set_dst_port(pair, fmb.getVersion(), log);
						break;
					case STR_TTL_IN_COPY:
						a = OFFactories.getFactory(fmb.getVersion()).actions().copyTtlIn();
						break;
					case STR_TTL_OUT_COPY:
						a = OFFactories.getFactory(fmb.getVersion()).actions().copyTtlOut();
						break;
					case STR_VLAN_POP:
						a = OFFactories.getFactory(fmb.getVersion()).actions().popVlan();
						break;
					case STR_VLAN_PUSH:
						if (pair.startsWith("0x")) {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPushVlan()
									.setEthertype(EthType.of(Integer.parseInt(pair.replaceFirst("0x", ""), 16)))
									.build();
						} else {
							a = OFFactories.getFactory(fmb.getVersion()).actions().buildPushVlan()
									.setEthertype(EthType.of(Integer.parseInt(pair)))
									.build();		
						}
						break;
					case STR_VLAN_STRIP:
						a = OFFactories.getFactory(fmb.getVersion()).actions().stripVlan();
						break;
					case STR_VLAN_SET_VID:
						a = decode_set_vlan_id(pair, fmb.getVersion(), log);
						break;
					case STR_VLAN_SET_PCP:
						a = decode_set_vlan_priority(pair, fmb.getVersion(), log);
						break;
					default:
						log.error("UNEXPECTED ACTION KEY '{}'", keyPair);
						break;
					}

				} catch (Exception e) {
					log.error("Illegal Action: " + e.getMessage());
				}
				if (a != null) {
					actions.add(a);
				}
			}
			log.debug("actions: {}", actions);
			fmb.setActions(actions);
		} else {
			log.debug("actions not found --> drop");
		}		
		return;
	} 

	/**
	 * Parse string and numerical port representations.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. Data can be any signed integer
	 * as a string or the strings 'controller', 'local', 'ingress-port', 'normal',
	 * or 'flood'.
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	@LogMessageDoc(level="ERROR",
			message="Invalid subaction: '{subaction}'",
			explanation="A static flow entry contained an invalid subaction",
			recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	private static OFActionOutput decode_output(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("((all)|(controller)|(local)|(ingress-port)|(normal)|(flood))").matcher(actionToDecode);
		OFActionOutput.Builder ab = OFFactories.getFactory(version).actions().buildOutput();
		OFPort port = OFPort.ANY;
		if (n.matches()) {
			if (n.group(1) != null && n.group(1).equals("all")) 
				port = OFPort.ALL;
			else if (n.group(1) != null && n.group(1).equals("controller"))
				port = OFPort.CONTROLLER;
			else if (n.group(1) != null && n.group(1).equals("local"))
				port = OFPort.LOCAL;
			else if (n.group(1) != null && n.group(1).equals("ingress-port"))
				port = OFPort.IN_PORT;
			else if (n.group(1) != null && n.group(1).equals("normal"))
				port = OFPort.NORMAL;
			else if (n.group(1) != null && n.group(1).equals("flood"))
				port = OFPort.FLOOD;
			ab.setPort(port);
			ab.setMaxLen(Integer.MAX_VALUE);
			log.debug("action {}", ab.build());
			return ab.build();
		}
		else {
			try {
				port = OFPort.of(Integer.parseInt(actionToDecode));
				ab.setPort(port);
				ab.setMaxLen(Integer.MAX_VALUE);
				return ab.build();
			} catch (NumberFormatException e) {
				log.error("Could not parse Integer port: '{}'", actionToDecode);
				return null;
			}
		}
	}

	/**
	 * Parse enqueue actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. Data with a leading 0x is permitted.
	 *
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionEnqueue decode_enqueue(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(actionToDecode);
		if (n.matches()) {
			OFPort port = OFPort.of(0);
			if (n.group(1) != null) {
				try {
					port = OFPort.of(get_short(n.group(1)));
				}
				catch (NumberFormatException e) {
					log.debug("Invalid port-num in: '{}' (error ignored)", actionToDecode);
					return null;
				}
			}

			int queueid = 0;
			if (n.group(2) != null) {
				try {
					queueid = get_int(n.group(2));
				}
				catch (NumberFormatException e) {
					log.debug("Invalid queue-id in: '{}' (error ignored)", actionToDecode);
					return null;
				}
			}
			OFActionEnqueue.Builder ab = OFFactories.getFactory(version).actions().buildEnqueue();
			ab.setPort(port);
			ab.setQueueId(queueid);
			log.debug("action {}", ab.build());
			return ab.build();
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
	}

	/**
	 * Parse set_vlan_id actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. Data with a leading 0x is permitted.
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetVlanVid decode_set_vlan_id(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode);
		if (n.matches()) {            
			if (n.group(1) != null) {
				try {
					VlanVid vlanid = VlanVid.ofVlan(get_short(n.group(1)));
					OFActionSetVlanVid.Builder ab = OFFactories.getFactory(version).actions().buildSetVlanVid();
					ab.setVlanVid(vlanid);
					log.debug("action {}", ab.build());
					return ab.build();
				}
				catch (NumberFormatException e) {
					log.debug("Invalid VLAN in: {} (error ignored)", actionToDecode);
					return null;
				}
			}          
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * Parse set_vlan_pcp actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. Data with a leading 0x is permitted.
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetVlanPcp decode_set_vlan_priority(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode); 
		if (n.matches()) {            
			if (n.group(1) != null) {
				try {
					VlanPcp prior = VlanPcp.of(get_byte(n.group(1)));
					OFActionSetVlanPcp.Builder ab = OFFactories.getFactory(version).actions().buildSetVlanPcp();
					ab.setVlanPcp(prior);
					log.debug("action {}", ab.build());
					return ab.build();
				}
				catch (NumberFormatException e) {
					log.debug("Invalid VLAN priority in: {} (error ignored)", actionToDecode);
					return null;
				}
			}
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * Parse set_dl_src actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder.
	 * 
	 * TODO should consider using MacAddress's built-in parser....
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetDlSrc decode_set_src_mac(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(actionToDecode); 
		if (n.matches()) {
			MacAddress macaddr = MacAddress.of(get_mac_addr(n, actionToDecode, log));
			if (macaddr != null) {
				OFActionSetDlSrc.Builder ab = OFFactories.getFactory(version).actions().buildSetDlSrc();
				ab.setDlAddr(macaddr);
				log.debug("action {}", ab.build());
				return ab.build();
			}            
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * Parse set_dl_dst actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder.
	 * 
	 * TODO should consider using MacAddress's built-in parser....
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetDlDst decode_set_dst_mac(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(actionToDecode);
		if (n.matches()) {
			MacAddress macaddr = MacAddress.of(get_mac_addr(n, actionToDecode, log));            
			if (macaddr != null) {
				OFActionSetDlDst.Builder ab = OFFactories.getFactory(version).actions().buildSetDlDst();
				ab.setDlAddr(macaddr);
				log.debug("action {}", ab.build());
				return ab.build();
			}
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * Parse set_tos actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. A leading 0x is permitted.
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetNwTos decode_set_tos_bits(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode); 
		if (n.matches()) {
			if (n.group(1) != null) {
				try {
					byte tosbits = get_byte(n.group(1));
					OFActionSetNwTos.Builder ab = OFFactories.getFactory(version).actions().buildSetNwTos();
					ab.setNwTos(tosbits);
					log.debug("action {}", ab.build());
					return ab.build();
				}
				catch (NumberFormatException e) {
					log.debug("Invalid dst-port in: {} (error ignored)", actionToDecode);
					return null;
				}
			}
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * Parse set_nw_src actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder.
	 * 
	 * TODO should consider using IPv4AddressWithMask's built-in parser....
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetNwSrc decode_set_src_ip(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(actionToDecode);
		if (n.matches()) {
			IPv4Address ipaddr = IPv4Address.of(get_ip_addr(n, actionToDecode, log));
			OFActionSetNwSrc.Builder ab = OFFactories.getFactory(version).actions().buildSetNwSrc();
			ab.setNwAddr(ipaddr);
			log.debug("action {}", ab.build());
			return ab.build();
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
	}

	/**
	 * Parse set_nw_dst actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder.
	 * 
	 * TODO should consider using IPv4AddressWithMask's built-in parser....
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetNwDst decode_set_dst_ip(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(actionToDecode);
		if (n.matches()) {
			IPv4Address ipaddr = IPv4Address.of(get_ip_addr(n, actionToDecode, log));
			OFActionSetNwDst.Builder ab = OFFactories.getFactory(version).actions().buildSetNwDst();
			ab.setNwAddr(ipaddr);
			log.debug("action {}", ab.build());
			return ab.build();
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
	}

	/**
	 * Parse set_tp_src actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. A leading 0x is permitted.
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFActionSetTpSrc decode_set_src_port(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode); 
		if (n.matches()) {
			if (n.group(1) != null) {
				try {
					TransportPort portnum = TransportPort.of(get_short(n.group(1)));
					OFActionSetTpSrc.Builder ab = OFFactories.getFactory(version).actions().buildSetTpSrc();
					ab.setTpPort(portnum);
					log.debug("action {}", ab.build());
					return ab.build();
				} 
				catch (NumberFormatException e) {
					log.debug("Invalid src-port in: {} (error ignored)", actionToDecode);
					return null;
				}
			}
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * Parse set_tp_dst actions.
	 * The key and delimiter for the action should be omitted, and only the
	 * data should be presented to this decoder. A leading 0x is permitted.
	 * 
	 * @param actionToDecode; The action as a string to decode
	 * @param version; The OF version to create the action for
	 * @param log
	 * @return
	 */
	private static OFAction decode_set_dst_port(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode);
		if (n.matches()) {
			if (n.group(1) != null) {
				try {
					TransportPort portnum = TransportPort.of(get_short(n.group(1)));
					OFActionSetTpDst.Builder ab = OFFactories.getFactory(version).actions().buildSetTpDst();
					ab.setTpPort(portnum);
					log.debug("action {}", ab.build());
					return ab.build();
				}
				catch (NumberFormatException e) {
					log.debug("Invalid dst-port in: {} (error ignored)", actionToDecode);
					return null;
				}
			}
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
		return null;
	}

	/**
	 * This is out of date and should be replaced with the built-in parser of MacAddress
	 * @param n
	 * @param actionToDecode
	 * @param log
	 * @return
	 */
	private static byte[] get_mac_addr(Matcher n, String actionToDecode, Logger log) {
		byte[] macaddr = new byte[6];     
		for (int i=0; i<6; i++) {
			if (n.group(i+1) != null) {
				try {
					macaddr[i] = get_byte("0x" + n.group(i+1));
				}
				catch (NumberFormatException e) {
					log.debug("Invalid src-mac in: '{}' (error ignored)", actionToDecode);
					return null;
				}
			}
			else { 
				log.debug("Invalid src-mac in: '{}' (null, error ignored)", actionToDecode);
				return null;
			}
		}  
		return macaddr;
	}

	/**
	 * This is out of date and should be replaced with the built-in parser of IPv4AddressWithMask
	 * @param n
	 * @param actionToDecode
	 * @param log
	 * @return
	 */
	private static int get_ip_addr(Matcher n, String actionToDecode, Logger log) {
		int ipaddr = 0;
		for (int i=0; i<4; i++) {
			if (n.group(i+1) != null) {
				try {
					ipaddr = ipaddr<<8;
					ipaddr = ipaddr | get_int(n.group(i+1));
				}
				catch (NumberFormatException e) {
					log.debug("Invalid src-ip in: '{}' (error ignored)", actionToDecode);
					return 0;
				}
			}
			else {
				log.debug("Invalid src-ip in: '{}' (null, error ignored)", actionToDecode);
				return 0;
			}
		}
		return ipaddr;
	}

	/**
	 * Parse int as decimal, hex (start with 0x or #) or octal (starts with 0)
	 * @param str
	 * @return
	 */
	private static int get_int(String str) {
		return Integer.decode(str);
	}

	/**
	 * Parse short as decimal, hex (start with 0x or #) or octal (starts with 0)
	 * @param str
	 * @return
	 */
	private static short get_short(String str) {
		return (short)(int)Integer.decode(str);
	}

	/**
	 * Parse byte as decimal, hex (start with 0x or #) or octal (starts with 0)
	 * @param str
	 * @return
	 */
	private static byte get_byte(String str) {
		return Integer.decode(str).byteValue();
	}

}
