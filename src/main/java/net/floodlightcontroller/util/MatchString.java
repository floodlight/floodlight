package net.floodlightcontroller.util;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;


/**
 * Represents an ofp_match structure
 * 
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 * 
 */
public class MatchString {
	
    /* List of Strings for marshalling and unmarshalling to human readable forms */
    final public static String STR_IN_PORT = "in_port";
    final public static String STR_DL_DST = "dl_dst";
    final public static String STR_DL_SRC = "dl_src";
    final public static String STR_DL_TYPE = "dl_type";
    final public static String STR_DL_VLAN = "dl_vlan";
    final public static String STR_DL_VLAN_PCP = "dl_vpcp";
    final public static String STR_NW_DST = "nw_dst";
    final public static String STR_NW_SRC = "nw_src";
    final public static String STR_NW_PROTO = "nw_proto";
    final public static String STR_NW_TOS = "nw_tos";
    final public static String STR_TP_DST = "tp_dst";
    final public static String STR_TP_SRC = "tp_src";

    /**
     * Output a dpctl-styled string, i.e., only list the elements that are not
     * wildcarded
     * 
     * A match-everything OFMatch outputs "OFMatch[]"
     * 
     * @return 
     *         "OFMatch[dl_src:00:20:01:11:22:33,nw_src:192.168.0.0/24,tp_dst:80]"
     */
    /*public String toString(Match match) {
        String str = "";

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
        return "OFMatch[" + str + "]";
    }*/

    /**
     * Based on the method from OFMatch in openflowj 1.0.
     * Set this Match's parameters based on a comma-separated key=value pair
     * dpctl-style string, e.g., from the output of OFMatch.toString() <br>
     * <p>
     * Supported keys/values include <br>
     * <p>
     * <TABLE border=1>
     * <TR>
     * <TD>KEY(s)
     * <TD>VALUE
     * </TR>
     * <TR>
     * <TD>"in_port","input_port"
     * <TD>integer
     * </TR>
     * <TR>
     * <TD>"dl_src","eth_src", "dl_dst","eth_dst"
     * <TD>hex-string
     * </TR>
     * <TR>
     * <TD>"dl_type", "dl_vlan", "dl_vlan_pcp"
     * <TD>integer
     * </TR>
     * <TR>
     * <TD>"nw_src", "nw_dst", "ip_src", "ip_dst"
     * <TD>CIDR-style netmask
     * </TR>
     * <TR>
     * <TD>"tp_src","tp_dst"
     * <TD>integer (max 64k)
     * </TR>
     * </TABLE>
     * <p>
     * The CIDR-style netmasks assume 32 netmask if none given, so:
     * "128.8.128.118/32" is the same as "128.8.128.118"
     * 
     * @param match
     *            a key=value comma separated string, e.g.
     *            "in_port=5,ip_dst=192.168.0.0/16,tp_src=80"
     * @throws IllegalArgumentException
     *             on unexpected key or value
     */

    public static Match fromString(String match, OFVersion ofVersion) throws IllegalArgumentException {
        if (match.equals("") || match.equalsIgnoreCase("any")
                || match.equalsIgnoreCase("all") || match.equals("[]"))
            match = "Match[]";
        String[] tokens = match.split("[\\[,\\]]");
        String[] values;
        int initArg = 0;
        if (tokens[0].equals("Match"))
            initArg = 1;
        
        Match.Builder mb = OFFactories.getFactory(ofVersion).buildMatch();
        
        int i;
        for (i = initArg; i < tokens.length; i++) {
            values = tokens[i].split("=");
            if (values.length != 2)
                throw new IllegalArgumentException("Token " + tokens[i]
                        + " does not have form 'key=value' parsing " + match);
            values[0] = values[0].toLowerCase(); // try to make this case insens
            if (values[0].equals(STR_IN_PORT) || values[0].equals("input_port")) {
                mb.setExact(MatchField.IN_PORT, OFPort.of(Integer.valueOf(values[1])));
            } else if (values[0].equals(STR_DL_DST) || values[0].equals("eth_dst")) {
                mb.setExact(MatchField.ETH_DST, MacAddress.of(values[1]));
            } else if (values[0].equals(STR_DL_SRC) || values[0].equals("eth_src")) {
                mb.setExact(MatchField.ETH_SRC, MacAddress.of(values[1]));
            } else if (values[0].equals(STR_DL_TYPE) || values[0].equals("eth_type")) {
                if (values[1].startsWith("0x")) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.of(Integer.valueOf(values[1].replaceFirst("0x", ""), 16)));
                } else {
                    mb.setExact(MatchField.ETH_TYPE, EthType.of(Integer.valueOf(values[1])));
                }
            } else if (values[0].equals(STR_DL_VLAN)) {
                if (values[1].contains("0x")) {
                    mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(Integer.valueOf(values[1].replaceFirst("0x", ""), 16)));
                } else {
                    mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(Integer.valueOf(values[1])));
                }
            } else if (values[0].equals(STR_DL_VLAN_PCP)) {
                mb.setExact(MatchField.VLAN_PCP, VlanPcp.of(U8.t(Short.valueOf(values[1]))));
            } else if (values[0].equals(STR_NW_DST) || values[0].equals("ip_dst")) {
                mb.setMasked(MatchField.IPV4_DST, IPv4AddressWithMask.of(values[1]));
            } else if (values[0].equals(STR_NW_SRC) || values[0].equals("ip_src")) { 
                mb.setMasked(MatchField.IPV4_SRC, IPv4AddressWithMask.of(values[1]));
            } else if (values[0].equals(STR_NW_PROTO)) {
                mb.setExact(MatchField.IP_PROTO, IpProtocol.of(Short.valueOf(values[1])));
            } else if (values[0].equals(STR_NW_TOS)) {
                mb.setExact(MatchField.IP_ECN, IpEcn.of(U8.t(Short.valueOf(values[1]))));
                mb.setExact(MatchField.IP_DSCP, IpDscp.of(U8.t(Short.valueOf(values[1]))));
            } else if (values[0].equals(STR_TP_DST)) {
            	if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.TCP)) {
                    mb.setExact(MatchField.TCP_DST, TransportPort.of(Integer.valueOf(values[1])));
            	} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.UDP)) {
                    mb.setExact(MatchField.UDP_DST, TransportPort.of(Integer.valueOf(values[1])));
            	} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.SCTP)) {
                    mb.setExact(MatchField.SCTP_DST, TransportPort.of(Integer.valueOf(values[1])));
            	}	
            } else if (values[0].equals(STR_TP_SRC)) {
            	if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.TCP)) {
                    mb.setExact(MatchField.TCP_SRC, TransportPort.of(Integer.valueOf(values[1])));
            	} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.UDP)) {
                    mb.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.valueOf(values[1])));
            	} else if (mb.get(MatchField.IP_PROTO).equals(IpProtocol.SCTP)) {
                    mb.setExact(MatchField.SCTP_SRC, TransportPort.of(Integer.valueOf(values[1])));
            	}	
            } else {
                throw new IllegalArgumentException("unknown token " + tokens[i] + " parsing " + match);
            }
        }
        return mb.build();
    }
}