package net.floodlightcontroller.util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.projectfloodlight.openflow.protocol.OFActionCopyField;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFOxmList;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionEnqueue;
import org.projectfloodlight.openflow.protocol.action.OFActionExperimenter;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
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
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmActsetOutput;
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
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6Exthdr;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6Flabel;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6NdSll;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6NdTarget;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6NdTll;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmIpv6Src;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMetadata;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMplsBos;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMplsLabel;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmMplsTc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmPacketType;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmSctpDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmSctpSrc;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmTcpDst;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmTcpFlags;
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
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonParser;

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
    private static final Logger log = LoggerFactory.getLogger(ActionUtils.class);

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
    public static final String STR_FIELD_COPY = "copy_field";
    public static final String STR_METER = "meter";
    public static final String STR_EXPERIMENTER = "experimenter";
    public static final String STR_NOT_APPLICABLE = "n/a";

    /* OF1.3 set-field operations are defined as any OF1.3 match.
     * We will borrow MatchUtils's String definitions of all OF1.3
     * set-field operations to be consistent.
     */

    private static final JsonFactory jsonFactory = new JsonFactory();
    private static final String JSON_EMPTY_OBJECT = "{}";


    /**
     * Returns a String representation of all the OpenFlow actions.
     * @param actions; A list of OFActions to encode into one string
     * @return A dpctl-style string of the actions
     */
    public static String actionsToString(List<OFAction> actions) {
        StringBuilder sb = new StringBuilder();
        for (OFAction a : actions) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            switch(a.getType()) {
            case OUTPUT:
                sb.append(STR_OUTPUT).append("=").append(ActionUtils.portToString(((OFActionOutput)a).getPort()));
                break;
            case ENQUEUE:
                long queue = ((OFActionEnqueue)a).getQueueId();
                sb.append(STR_ENQUEUE).append("=").append(portToString(((OFActionEnqueue)a).getPort())).append(":0x").append(String.format("%02x", queue));
                break;
            case STRIP_VLAN:
                sb.append(STR_VLAN_STRIP);
                break;
            case POP_VLAN:
                sb.append(STR_VLAN_POP);
                break;
            case PUSH_VLAN:
                sb.append(STR_VLAN_PUSH).append("=").append(Integer.toString(((OFActionPushVlan)a).getEthertype().getValue()));
                break;
            case SET_VLAN_VID:
                sb.append(STR_VLAN_SET_VID).append("=").append(Short.toString(((OFActionSetVlanVid)a).getVlanVid().getVlan()));
                break;
            case SET_VLAN_PCP:
                sb.append(STR_VLAN_SET_PCP).append("=").append(Byte.toString(((OFActionSetVlanPcp)a).getVlanPcp().getValue()));
                break;
            case SET_QUEUE:
                sb.append(STR_QUEUE_SET).append("=").append(Long.toString(((OFActionSetQueue)a).getQueueId()));
            case SET_DL_SRC:
                sb.append(STR_DL_SRC_SET).append("=").append( ((OFActionSetDlSrc)a).getDlAddr().toString());
                break;
            case SET_DL_DST:
                sb.append(STR_DL_DST_SET).append("=").append(((OFActionSetDlDst)a).getDlAddr().toString());
                break;
            case SET_NW_ECN:
                sb.append(STR_NW_ECN_SET).append("=").append(Byte.toString(((OFActionSetNwEcn)a).getNwEcn().getEcnValue()));
                break;
            case SET_NW_TOS:
                sb.append(STR_NW_TOS_SET).append("=").append(Short.toString(((OFActionSetNwTos)a).getNwTos()));
                break;
            case SET_NW_TTL:
                sb.append(STR_NW_TTL_SET).append("=").append(Short.toString(((OFActionSetNwTtl)a).getNwTtl()));
                break;
            case DEC_NW_TTL:
                sb.append(STR_NW_TTL_DEC);
                break;
            case SET_MPLS_LABEL:
                sb.append(STR_MPLS_LABEL_SET).append("=").append(Long.toString(((OFActionSetMplsLabel)a).getMplsLabel()));
                break;
            case SET_MPLS_TC:
                sb.append(STR_MPLS_TC_SET).append("=").append(Short.toString(((OFActionSetMplsTc)a).getMplsTc()));
                break;
            case SET_MPLS_TTL:
                sb.append(STR_MPLS_TTL_SET).append("=").append(Short.toString(((OFActionSetMplsTtl)a).getMplsTtl()));
                break;
            case DEC_MPLS_TTL:
                sb.append(STR_MPLS_TTL_DEC);
                break;
            case PUSH_MPLS:
                sb.append(STR_MPLS_PUSH).append("=").append(Integer.toString(((OFActionPushMpls)a).getEthertype().getValue()));
                break;
            case POP_MPLS:
                sb.append(STR_MPLS_POP).append("=").append(Integer.toString(((OFActionPopMpls)a).getEthertype().getValue()));
                break;
            case SET_NW_SRC:
                sb.append(STR_NW_SRC_SET).append("=").append(((OFActionSetNwSrc)a).getNwAddr().toString());
                break;
            case SET_NW_DST:
                sb.append(STR_NW_DST_SET).append("=").append(((OFActionSetNwDst)a).getNwAddr().toString());
                break;
            case SET_TP_SRC:
                sb.append(STR_TP_SRC_SET).append("=").append(((OFActionSetTpSrc)a).getTpPort().toString());
                break;
            case SET_TP_DST:
                sb.append(STR_TP_DST_SET).append("=").append(((OFActionSetTpDst)a).getTpPort().toString());
                break;
            case COPY_TTL_IN:
                sb.append(STR_TTL_IN_COPY);
                break;
            case COPY_TTL_OUT:
                sb.append(STR_TTL_OUT_COPY);
                break;
            case PUSH_PBB:
                sb.append(STR_PBB_PUSH).append("=").append(Integer.toString(((OFActionPushPbb)a).getEthertype().getValue()));
                break;
            case POP_PBB:
                sb.append(STR_PBB_POP);
                break;
            case EXPERIMENTER:
                sb.append(STR_EXPERIMENTER).append("=").append(Long.toString(((OFActionExperimenter)a).getExperimenter()));
                break;
            case GROUP:
                sb.append(STR_GROUP).append("=").append(Integer.toString(((OFActionGroup)a).getGroup().getGroupNumber()));
                break;
            case SET_FIELD:
                log.debug("Got Set-Field action. Setting {}", ((OFActionSetField)a));
                /* ARP */
                if (((OFActionSetField)a).getField() instanceof OFOxmArpOp) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ARP_OPCODE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmArpOp) ((OFActionSetField) a).getField()).getValue().getOpcode()));
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpSha) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ARP_SHA)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmArpSha) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpTha) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ARP_DHA)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmArpTha) ((OFActionSetField) a).getField()).getValue().toString());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpSpa) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ARP_SPA)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmArpSpa) ((OFActionSetField) a).getField()).getValue().toString()); // ipaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmArpTpa) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ARP_DPA)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmArpTpa) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdSll) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_ND_SLL)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6NdSll) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTll) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_ND_TLL)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6NdTll) ((OFActionSetField) a).getField()).getValue().toString()); // macaddress formats string already
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6NdTarget) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_ND_TARGET)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6NdTarget) ((OFActionSetField) a).getField()).getValue().toString()); 
                }
                /* DATA LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmEthType) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_DL_TYPE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmEthType) ((OFActionSetField) a).getField()).getValue().getValue()));
                } else if (((OFActionSetField)a).getField() instanceof OFOxmEthSrc) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_DL_SRC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmEthSrc) ((OFActionSetField) a).getField()).getValue().toString());
                } else if (((OFActionSetField)a).getField() instanceof OFOxmEthDst) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_DL_DST)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmEthDst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmVlanVid) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_DL_VLAN)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmVlanVid) ((OFActionSetField) a).getField()).getValue().getVlan())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmVlanPcp) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_DL_VLAN_PCP)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Byte.toString(((OFOxmVlanPcp) ((OFActionSetField) a).getField()).getValue().getValue())); 
                } 
                /* ICMP */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Code) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ICMP_CODE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmIcmpv4Code) ((OFActionSetField) a).getField()).getValue().getCode())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv4Type) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ICMP_TYPE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmIcmpv4Type) ((OFActionSetField) a).getField()).getValue().getType())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Code) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ICMPV6_CODE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmIcmpv6Code) ((OFActionSetField) a).getField()).getValue().getRaw())); 
                }  else if (((OFActionSetField)a).getField() instanceof OFOxmIcmpv6Type) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ICMPV6_TYPE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmIcmpv6Type) ((OFActionSetField) a).getField()).getValue().getRaw())); 
                }
                /* NETWORK LAYER */
                else if (((OFActionSetField)a).getField() instanceof OFOxmIpProto) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_NW_PROTO)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmIpProto) ((OFActionSetField) a).getField()).getValue().getIpProtocolNumber())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Src) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_NW_SRC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv4Src) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv4Dst) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_NW_DST)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv4Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Src) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_SRC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6Src) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Dst) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_DST)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6Dst) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Flabel) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_FLOW_LABEL)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6Flabel) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpv6Exthdr) {                		
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_IPV6_EXTHDR)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(((OFOxmIpv6Exthdr) ((OFActionSetField) a).getField()).getValue().toString()); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpEcn) { 
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_NW_ECN)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Byte.toString(((OFOxmIpEcn) ((OFActionSetField) a).getField()).getValue().getEcnValue())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmIpDscp) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_NW_DSCP)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Byte.toString(((OFOxmIpDscp) ((OFActionSetField) a).getField()).getValue().getDscpValue())); 
                } 
                /* TRANSPORT LAYER, TCP, UDP, and SCTP */
                else if (((OFActionSetField)a).getField() instanceof OFOxmTcpSrc) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_TCP_SRC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmTcpSrc) ((OFActionSetField) a).getField()).getValue().getPort())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmTcpDst) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_TCP_DST)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmTcpDst) ((OFActionSetField) a).getField()).getValue().getPort())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmUdpSrc) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_UDP_SRC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmUdpSrc) ((OFActionSetField) a).getField()).getValue().getPort())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmUdpDst) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_UDP_DST)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmUdpDst) ((OFActionSetField) a).getField()).getValue().getPort())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmSctpSrc) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_SCTP_SRC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmSctpSrc) ((OFActionSetField) a).getField()).getValue().getPort())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmSctpDst) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_SCTP_DST)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmSctpDst) ((OFActionSetField) a).getField()).getValue().getPort())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmTcpFlags) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_TCP_FLAGS)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmTcpFlags) ((OFActionSetField) a).getField()).getValue().getValue())); 
                }
                /* MPLS */
                else if (((OFActionSetField)a).getField() instanceof OFOxmMplsLabel) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_MPLS_LABEL)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Long.toString(((OFOxmMplsLabel) ((OFActionSetField) a).getField()).getValue().getValue())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmMplsTc) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_MPLS_TC)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Short.toString(((OFOxmMplsTc) ((OFActionSetField) a).getField()).getValue().getValue())); 
                } else if (((OFActionSetField)a).getField() instanceof OFOxmMplsBos) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_MPLS_BOS)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Boolean.toString(((OFOxmMplsBos) ((OFActionSetField) a).getField()).getValue().getValue())); 
                }
                /* ACTSET_OUTPUT */
                else if (((OFActionSetField)a).getField() instanceof OFOxmActsetOutput) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_ACTSET_OUTPUT)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmActsetOutput) ((OFActionSetField) a).getField()).getValue().getPortNumber())); 
                }
                /* PACKET_TYPE */
                else if (((OFActionSetField)a).getField() instanceof OFOxmPacketType) { // TODO hard-coded "/" as delimiter...fix this
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_PACKET_TYPE)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Integer.toString(((OFOxmPacketType) ((OFActionSetField) a).getField()).getValue().getNamespace())) 
                    .append("/")
                    .append(Integer.toString(((OFOxmPacketType) ((OFActionSetField) a).getField()).getValue().getNsType())); 
                }
                /* METADATA */
                else if (((OFActionSetField)a).getField() instanceof OFOxmMetadata) {
                    sb.append(STR_FIELD_SET).append("=").append(MatchUtils.STR_METADATA)
                    .append(MatchUtils.SET_FIELD_DELIM)
                    .append(Long.toString(((OFOxmMetadata) ((OFActionSetField) a).getField()).getValue().getValue().getValue())); 
                } else {
                    log.error("Could not decode Set-Field action field: {}", ((OFActionSetField) a));
                }
                break;
            case COPY_FIELD:
                sb.append(STR_FIELD_COPY).append("=").append(copyFieldToJson((OFActionCopyField) a));
                break;
            case METER:
                sb.append(STR_METER).append("=").append(Long.toString(((OFActionMeter)a).getMeterId()));
                break;
            default:
                log.error("Could not decode action: {}", a);
                break;
            }

        }

        return sb.toString();
    }

    public static List<OFAction> fromString(String s, OFVersion v) {
        List<OFAction> actions = new LinkedList<OFAction>();
        OFFactory f = OFFactories.getFactory(v);
        if (s != null && !s.trim().isEmpty()) {
            s = s.toLowerCase();
            String[] bigStringSplit = s.split(","); // split into separate action=value or action=key@value pairs

            String[] tmp;
            ArrayDeque<String[]> actionToDecode = new ArrayDeque<String[]>();
            for (int i = 0; i < bigStringSplit.length; i++) {
                tmp = bigStringSplit[i].split("="); // split into separate [action, value] or [action, key@value] singles
                if (tmp.length != 2) {
                    log.debug("Token " + bigStringSplit[i] + " does not have form 'key=value' parsing " + s);
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
                        a = decode_output(pair, v);
                        break;
                    case STR_ENQUEUE:
                        a = decode_enqueue(pair, v);
                        break;
                    case STR_DL_SRC_SET:
                        a = decode_set_src_mac(pair, v);
                        break;
                    case STR_DL_DST_SET:
                        a = decode_set_dst_mac(pair, v);
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
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildArpOp()
                                    .setValue(ArpOpcode.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_ARP_SHA:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildArpSha().setValue(MacAddress.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_ARP_DHA:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildArpTha().setValue(MacAddress.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_ARP_SPA:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildArpSpa().setValue(IPv4Address.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_ARP_DPA:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildArpTpa().setValue(IPv4Address.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_IPV6_ND_SLL:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv6NdSll().setValue(MacAddress.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_IPV6_ND_TLL:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv6NdTll().setValue(MacAddress.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_IPV6_ND_TARGET:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv6NdTarget().setValue(IPv6Address.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_DL_TYPE:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildEthType()
                                    .setValue(EthType.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_DL_SRC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildEthSrc().setValue(MacAddress.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_DL_DST:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildEthDst().setValue(MacAddress.of(actionData[1])).build())
                            .build();
                            break;
                        case MatchUtils.STR_DL_VLAN:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildVlanVid()
                                    .setValue(OFVlanVidMatch.ofVlan(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_DL_VLAN_PCP:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildVlanPcp()
                                    .setValue(VlanPcp.of(ParseUtils.parseHexOrDecByte(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_ICMP_CODE:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIcmpv4Code()
                                    .setValue(ICMPv4Code.of(ParseUtils.parseHexOrDecShort(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_ICMP_TYPE:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIcmpv4Type()
                                    .setValue(ICMPv4Type.of(ParseUtils.parseHexOrDecShort(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_ICMPV6_CODE:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIcmpv6Code()
                                    .setValue(U8.of(ParseUtils.parseHexOrDecShort(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_ICMPV6_TYPE:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIcmpv6Type()
                                    .setValue(U8.of(ParseUtils.parseHexOrDecShort(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_NW_PROTO:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpProto()
                                    .setValue(IpProtocol.of(ParseUtils.parseHexOrDecShort(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_NW_SRC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv4Src().setValue(IPv4Address.of(actionData[1])).build())
                            .build();						
                            break;
                        case MatchUtils.STR_NW_DST:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv4Dst().setValue(IPv4Address.of(actionData[1])).build())
                            .build();						
                            break;
                        case MatchUtils.STR_IPV6_SRC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv6Src().setValue(IPv6Address.of(actionData[1])).build())
                            .build();						
                            break;
                        case MatchUtils.STR_IPV6_DST:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv6Dst().setValue(IPv6Address.of(actionData[1])).build())
                            .build();						
                            break;
                        case MatchUtils.STR_IPV6_FLOW_LABEL:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpv6Flabel()
                                    .setValue(IPv6FlowLabel.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();			
                            break;
                        case MatchUtils.STR_NW_ECN:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpEcn()
                                    .setValue(IpEcn.of(ParseUtils.parseHexOrDecByte(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_NW_DSCP:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildIpDscp()
                                    .setValue(IpDscp.of(ParseUtils.parseHexOrDecByte(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_SCTP_SRC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildSctpSrc()
                                    .setValue(TransportPort.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();	
                            break;
                        case MatchUtils.STR_SCTP_DST:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildSctpDst()
                                    .setValue(TransportPort.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();	
                            break;
                        case MatchUtils.STR_TCP_SRC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildTcpSrc()
                                    .setValue(TransportPort.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();	
                            break;
                        case MatchUtils.STR_TCP_DST:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildTcpDst()
                                    .setValue(TransportPort.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();	
                            break;
                        case MatchUtils.STR_UDP_SRC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildUdpSrc()
                                    .setValue(TransportPort.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();	
                            break;
                        case MatchUtils.STR_UDP_DST:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildUdpDst()
                                    .setValue(TransportPort.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();	
                            break;
                        case MatchUtils.STR_MPLS_LABEL:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildMplsLabel()
                                    .setValue(U32.of(ParseUtils.parseHexOrDecLong(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_MPLS_TC:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildMplsTc()
                                    .setValue(U8.of(ParseUtils.parseHexOrDecShort(actionData[1])))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_MPLS_BOS:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildMplsBos()
                                    .setValue(OFBooleanValue.of(Boolean.parseBoolean(actionData[1])))
                                    .build()) // interprets anything other than "true" as false
                            .build();
                            break;
                        case MatchUtils.STR_METADATA:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildMetadata()
                                    .setValue(OFMetadata.of(U64.of(ParseUtils.parseHexOrDecLong(actionData[1]))))
                                    .build())
                            .build();
                            break;
                        case MatchUtils.STR_ACTSET_OUTPUT:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildActsetOutput()
                                    .setValue(portFromString(actionData[1]))
                                    .build())
                            .build();

                            break;
                        case MatchUtils.STR_TCP_FLAGS:
                            a = f.actions().buildSetField()
                            .setField(f.oxms().buildTcpFlags()
                                    .setValue(U16.of(ParseUtils.parseHexOrDecInt(actionData[1])))
                                    .build())
                            .build();
                            break;
                        default:
                            log.error("Unexpected OF1.2+ setfield '{}'", actionData);
                            break;
                        }					
                        break;
                    case STR_GROUP:
                        a = f.actions().buildGroup()
                        .setGroup(GroupUtils.groupIdFromString(pair))
                        .build();	
                        break;
                    case STR_MPLS_LABEL_SET:
                        a = f.actions().buildSetMplsLabel()
                        .setMplsLabel(ParseUtils.parseHexOrDecLong(pair))
                        .build();			
                        break;
                    case STR_MPLS_POP:
                        a = f.actions().buildPopMpls()
                        .setEthertype(EthType.of(ParseUtils.parseHexOrDecInt(pair)))
                        .build();
                        break;
                    case STR_MPLS_PUSH:
                        a = f.actions().buildPushMpls()
                        .setEthertype(EthType.of(ParseUtils.parseHexOrDecInt(pair)))
                        .build();		
                        break;
                    case STR_MPLS_TC_SET:
                        a = f.actions().buildSetMplsTc()
                        .setMplsTc(ParseUtils.parseHexOrDecShort(pair))
                        .build();	
                        break;
                    case STR_MPLS_TTL_DEC:
                        a = f.actions().decMplsTtl();
                        break;
                    case STR_MPLS_TTL_SET:
                        a = f.actions().buildSetMplsTtl()
                        .setMplsTtl(ParseUtils.parseHexOrDecShort(pair))
                        .build();	
                        break;
                    case STR_NW_TOS_SET:
                        a = decode_set_tos_bits(pair, v); // should only be used by OF1.0
                        break;
                    case STR_NW_SRC_SET:
                        a = decode_set_src_ip(pair, v);
                        break;
                    case STR_NW_DST_SET:
                        a = decode_set_dst_ip(pair, v);
                        break;
                    case STR_NW_ECN_SET: // loxi does not support DSCP set for OF1.1
                        a = f.actions().buildSetNwEcn() 
                        .setNwEcn(IpEcn.of(ParseUtils.parseHexOrDecByte(pair)))
                        .build();		
                        break;
                    case STR_NW_TTL_DEC:
                        a = f.actions().decNwTtl();
                        break;
                    case STR_NW_TTL_SET:
                        a = f.actions().buildSetNwTtl()
                        .setNwTtl(ParseUtils.parseHexOrDecShort(pair))
                        .build();
                        break;
                    case STR_PBB_POP:
                        a = f.actions().popPbb();
                        break;
                    case STR_PBB_PUSH:
                        a = f.actions().buildPushPbb()
                        .setEthertype(EthType.of(ParseUtils.parseHexOrDecInt(pair)))
                        .build();				
                        break;
                    case STR_QUEUE_SET:
                        a = f.actions().buildSetQueue()
                        .setQueueId(ParseUtils.parseHexOrDecLong(pair))
                        .build();	
                        break;
                    case STR_TP_SRC_SET:
                        a = decode_set_src_port(pair, v);
                        break;
                    case STR_TP_DST_SET:
                        a = decode_set_dst_port(pair, v);
                        break;
                    case STR_TTL_IN_COPY:
                        a = f.actions().copyTtlIn();
                        break;
                    case STR_TTL_OUT_COPY:
                        a = f.actions().copyTtlOut();
                        break;
                    case STR_VLAN_POP:
                        a = f.actions().popVlan();
                        break;
                    case STR_VLAN_PUSH:
                        a = f.actions().buildPushVlan()
                        .setEthertype(EthType.of(ParseUtils.parseHexOrDecInt(pair)))
                        .build();
                        break;
                    case STR_VLAN_STRIP:
                        a = f.actions().stripVlan();
                        break;
                    case STR_VLAN_SET_VID:
                        a = decode_set_vlan_id(pair, v);
                        break;
                    case STR_VLAN_SET_PCP:
                        a = decode_set_vlan_priority(pair, v);
                        break;
                    case STR_METER:
                        a = f.actions().buildMeter()
                        .setMeterId(ParseUtils.parseHexOrDecLong(pair))
                        .build();
                        break;
                    case STR_FIELD_COPY:
                        a = (OFAction) copyFieldFromJson(pair, f.getVersion());
                        break;
                    default:
                        log.error("Unexpected action key '{}'", keyPair);
                        break;
                    }

                } catch (Exception e) {
                    log.error("Illegal Action: {}", e.getMessage());
                }
                if (a != null) {
                    actions.add(a);
                }
            }
        } else {
            log.debug("actions not found --> drop");
        }	
        return actions;
    }

    /**
     * Append OFActionCopyField object to an existing JsonGenerator.
     * This method assumes the field name of the action has been
     * written already, if required. The appended data will
     * be formatted as follows:
     *   {
     *       "src_field":"name",
     *       "dst_field":"name",
     *       "src_offset_bits":"bits",
     *       "dst_offset_bits":"bits",
     *       "num_bits":"bits"
     *   }
     * @param jsonGen
     * @param c
     */
    public static void copyFieldToJson(JsonGenerator jsonGen, OFActionCopyField c) {
        jsonGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);

        try {
            jsonGen.writeStartObject();
            Iterator<OFOxm<?>> i = c.getOxmIds().iterator();
            if (i.hasNext()) {
                jsonGen.writeStringField("src_field" , OXMUtils.oxmIdToString(U32.of(i.next().getCanonical().getTypeLen())));
            } else {
                log.error("either src_field or dst_field or both not set in {}", c);
            }
            if (i.hasNext()) {
                jsonGen.writeStringField("dst_field" , OXMUtils.oxmIdToString(U32.of(i.next().getCanonical().getTypeLen())));
            } else {
                log.error("either src_field or dst_field not set in {}", c);
            }
            if (i.hasNext()) {
                log.warn("OFOxmList should only have src_field followed by dst_field. Extra field {}", i.next());
            }
            jsonGen.writeNumberField("src_offset_bits", c.getSrcOffset());
            jsonGen.writeNumberField("dst_offset_bits", c.getDstOffset());
            jsonGen.writeNumberField("num_bits", c.getNBits());
            jsonGen.writeEndObject();
            jsonGen.close();
        } catch (IOException e) {
            log.error("Error composing OFActionCopyField JSON object. {}", e.getMessage());
            return;
        }
    }

    /**
     * Convert OFActionCopyField object to a JSON string.
     * This method assumes the field name of the action has been
     * written already, if required. The appended data will
     * be formatted as follows:
     *   {
     *       "src_field":"name",
     *       "dst_field":"name",
     *       "src_offset_bits":"bits",
     *       "dst_offset_bits":"bits",
     *       "num_bits":"bits"
     *   }
     * @param jsonGen
     * @param c
     */
    public static String copyFieldToJson(OFActionCopyField c) {
        Writer w = new StringWriter();
        JsonGenerator jsonGen;
        try {
            jsonGen = jsonFactory.createGenerator(w);
        } catch (IOException e) {
            log.error("Could not instantiate JSON Generator. {}", e.getMessage());
            return JSON_EMPTY_OBJECT;
        }

        copyFieldToJson(jsonGen, c);

        return w.toString(); /* overridden impl returns contents of Writer's StringBuffer */
    }

    /**
     * Convert a JSON string to an OFActionCopyField object.
     * The format of the input JSON is expected to be:
     *   {
     *       "src_field":"name",
     *       "dst_field":"name",
     *       "src_offset_bits":"bits",
     *       "dst_offset_bits":"bits",
     *       "num_bits":"bits"
     *   }
     * @param json
     * @param v
     * @return
     */
    public static OFActionCopyField copyFieldFromJson(String json, OFVersion v) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        if (v == null) {
            throw new IllegalArgumentException("OFVersion cannot be null");
        }

        final JsonParser jp;
        try {
            jp = jsonFactory.createParser(json);
        } catch (IOException e) {
            log.error("Could not create JSON parser for OFActionCopyField {}", json);
            return null;
        }
        try {
            if (jp.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }

            OFActionCopyField.Builder b = OFFactories.getFactory(v).buildActionCopyField();
            OFOxm<?> srcField = null;
            OFOxm<?> dstField = null;

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                String key = jp.getCurrentName().toLowerCase().trim();
                jp.nextToken();
                String value = jp.getText().toLowerCase().trim();
                switch (key) {
                case "src_field":
                    srcField = OXMUtils.oxmStringToOxm(value, v);
                    break;
                case "dst_field":
                    dstField = OXMUtils.oxmStringToOxm(value, v);
                    break;
                case "src_offset_bits":
                    b.setSrcOffset(ParseUtils.parseHexOrDecInt(value));
                    break;
                case "dst_offset_bits":
                    b.setDstOffset(ParseUtils.parseHexOrDecInt(value));
                    break;
                case "num_bits":
                    b.setNBits(ParseUtils.parseHexOrDecInt(value));
                    break;
                default:
                    log.warn("Unexpected OFActionCopyField key {}", key);
                    break;
                }
            }
            if (srcField == null || dstField == null) {
                log.error("Src and dst OXMs must be specified. Got {} and {}, respectively", srcField, dstField);
                return null;
            } else {
                b.setOxmIds(OFOxmList.of(srcField, dstField));
                return b.build();
            }
        } catch (IOException e) {
            log.error("Could not parse: {}", json);
            log.error("JSON parse error message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses OFFlowMod actions from strings.
     * @param fmb The OFFlowMod.Builder to set the actions for
     * @param s The string containing all the actions
     * @param log A logger to log for errors.
     */
    public static void fromString(OFFlowMod.Builder fmb, String s) {
        List<OFAction> actions = fromString(s, fmb.getVersion());
        log.debug("actions: {}", actions);
        fmb.setActions(actions);
        return;
    }

    public static OFPort portFromString(String s) {
        return MatchUtils.portFromString(s);
    }

    public static String portToString(OFPort p) {
        return MatchUtils.portToString(p);
    }

    /**
     * Parse string and numerical port representations.
     * The key and delimiter for the action should be omitted, and only the
     * data should be presented to this decoder. Data can be any signed integer
     * or hex (w/leading 0x prefix) as a string or the special string port
     * STR_PORT_* as defined in {@link MatchUtils}.
     * 
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionOutput decode_output(String actionToDecode, OFVersion version) {
        OFActionOutput.Builder ab = OFFactories.getFactory(version).actions().buildOutput();
        OFPort port = portFromString(actionToDecode);
        if (port == null) {
            log.error("Could not parse output port {}", actionToDecode);
            return null;
        } else {
            ab.setPort(port);
            ab.setMaxLen(Integer.MAX_VALUE);
            log.debug("action {}", ab);
            return ab.build();
        } 
    }

    /**
     * Parse enqueue actions.
     * The key and delimiter for the action should be omitted, and only the
     * data should be presented to this decoder. Data with a leading 0x is permitted.
     *
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionEnqueue decode_enqueue(String actionToDecode, OFVersion version) {
        Matcher n = Pattern.compile("(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(actionToDecode);
        if (n.matches()) {
            OFPort port;
            if (n.group(1) != null) {
                port = portFromString(n.group(1));
                if (port == null) {
                    log.error("Invalid port {}", n.group(1));
                    return null;
                }
            } else {
                log.error("Missing port number for enqueue action");
                return null;
            }

            int queueid = 0;
            if (n.group(2) != null) {
                try {
                    queueid = ParseUtils.parseHexOrDecInt(n.group(2));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid queue-id in: '{}' (error ignored)", actionToDecode);
                    return null;
                }
            }
            OFActionEnqueue a = OFFactories.getFactory(version).actions().buildEnqueue()
                    .setPort(port)
                    .setQueueId(queueid)
                    .build();
            log.debug("action {}", a);
            return a;
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
     * @return
     */
    private static OFActionSetVlanVid decode_set_vlan_id(String actionToDecode, OFVersion version) {
        Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode);
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    VlanVid vlanid = VlanVid.ofVlan(ParseUtils.parseHexOrDecShort(n.group(1)));
                    OFActionSetVlanVid a = OFFactories.getFactory(version).actions().buildSetVlanVid()
                            .setVlanVid(vlanid)
                            .build();
                    log.debug("action {}", a);
                    return a;
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
     * @return
     */
    private static OFActionSetVlanPcp decode_set_vlan_priority(String actionToDecode, OFVersion version) {
        Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode); 
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    OFActionSetVlanPcp a = OFFactories.getFactory(version).actions().buildSetVlanPcp()
                            .setVlanPcp(VlanPcp.of(ParseUtils.parseHexOrDecByte(n.group(1))))
                            .build();
                    log.debug("action {}", a);
                    return a;
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
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionSetDlSrc decode_set_src_mac(String actionToDecode, OFVersion version) {
        try {
            OFActionSetDlSrc a = OFFactories.getFactory(version).actions().buildSetDlSrc()
                    .setDlAddr(MacAddress.of(actionToDecode))
                    .build();
            log.debug("action {}", a);
            return a;
        }
        catch (Exception e) {
            log.debug("Invalid action: '{}'", actionToDecode);
            return null;
        }
    }

    /**
     * Parse set_dl_dst actions.
     * The key and delimiter for the action should be omitted, and only the
     * data should be presented to this decoder.
     * 
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionSetDlDst decode_set_dst_mac(String actionToDecode, OFVersion version) {
        try {
            OFActionSetDlDst a = OFFactories.getFactory(version).actions().buildSetDlDst()
                    .setDlAddr(MacAddress.of(actionToDecode))
                    .build();
            log.debug("action {}", a);
            return a;
        }
        catch (Exception e) {
            log.debug("Invalid action: '{}'", actionToDecode);
            return null;
        }
    }

    /**
     * Parse set_tos actions.
     * The key and delimiter for the action should be omitted, and only the
     * data should be presented to this decoder. A leading 0x is permitted.
     * 
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionSetNwTos decode_set_tos_bits(String actionToDecode, OFVersion version) {
        Matcher n = Pattern.compile("((?:0x)?\\d+)").matcher(actionToDecode); 
        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    OFActionSetNwTos a = OFFactories.getFactory(version).actions().buildSetNwTos()
                            .setNwTos(ParseUtils.parseHexOrDecByte(n.group(1)))
                            .build();
                    log.debug("action {}", a);
                    return a;
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
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionSetNwSrc decode_set_src_ip(String actionToDecode, OFVersion version) {
        try {
            OFActionSetNwSrc a = OFFactories.getFactory(version).actions().buildSetNwSrc()
                    .setNwAddr(IPv4Address.of(actionToDecode))
                    .build();
            log.debug("action {}", a);
            return a;
        } catch (Exception e) {
            log.debug("Invalid action: '{}'", actionToDecode);
            return null;
        }
    }

    /**
     * Parse set_nw_dst actions.
     * The key and delimiter for the action should be omitted, and only the
     * data should be presented to this decoder.
     * 
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFActionSetNwDst decode_set_dst_ip(String actionToDecode, OFVersion version) {
        try {
            OFActionSetNwDst a = OFFactories.getFactory(version).actions().buildSetNwDst()
                    .setNwAddr(IPv4Address.of(actionToDecode))
                    .build();
            log.debug("action {}", a);
            return a;
        } catch (Exception e) {
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
     * @return
     */
    private static OFActionSetTpSrc decode_set_src_port(String actionToDecode, OFVersion version) {
        try {
            OFActionSetTpSrc a = OFFactories.getFactory(version).actions().buildSetTpSrc()
                    .setTpPort(TransportPort.of(Integer.parseInt(actionToDecode)))
                    .build();
            log.debug("action {}", a);
            return a;
        } 
        catch (NumberFormatException e) {
            log.debug("Invalid src-port in: {} (error ignored)", actionToDecode);
            return null;
        }
    }

    /**
     * Parse set_tp_dst actions.
     * The key and delimiter for the action should be omitted, and only the
     * data should be presented to this decoder. A leading 0x is permitted.
     * 
     * @param actionToDecode; The action as a string to decode
     * @param version; The OF version to create the action for
     * @return
     */
    private static OFAction decode_set_dst_port(String actionToDecode, OFVersion version) {
        try {
            OFActionSetTpDst a = OFFactories.getFactory(version).actions().buildSetTpDst()
                    .setTpPort(TransportPort.of(Integer.parseInt(actionToDecode)))
                    .build();
            log.debug("action {}", a);
            return a;
        }
        catch (NumberFormatException e) {
            log.debug("Invalid dst-port in: {} (error ignored)", actionToDecode);
            return null;
        }
    }
}