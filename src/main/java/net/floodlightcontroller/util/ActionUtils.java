package net.floodlightcontroller.util;

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
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwTos;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanPcp;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanVid;
import org.projectfloodlight.openflow.protocol.action.OFActionStripVlan;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;

/**
 * OFAction helper functions. Use with any OpenFlowJ-Loxi Action.
 * 
 * Includes string methods refactored from StaticFlowEntryPusher
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */
public class ActionUtils {
	public static final String STR_OUTPUT = "output";
	public static final String STR_ENQUEUE = "enqueue";
	public static final String STR_VLAN_STRIP = "strip_vlan";
	public static final String STR_VLAN_POP = "pop_vlan";
	public static final String STR_VLAN_PUSH = "push_vlan";
	public static final String STR_VLAN_SET_PCP = "set_vlan_priority";
	public static final String STR_VLAN_SET_VID = "set_vlan_id";
	public static final String STR_QUEUE_SET = "set_queue";
	public static final String STR_DL_SRC_SET = "set_src_mac";
	public static final String STR_DL_DST_SET = "set_dst_mac";
	public static final String STR_NW_SRC_SET = "set_src_ip";
	public static final String STR_NW_DST_SET = "set_dst_ip";
	public static final String STR_NW_ECN_SET = "set_nw_ecn";
	public static final String STR_NW_TOS_SET = "set_tos_bits";
	public static final String STR_NW_TTL_SET = "set_ip_ttl";
	public static final String STR_NW_TTL_DEC = "dec_ip_ttl";
	public static final String STR_MPLS_LABEL_SET = "set_mpls_label";
	public static final String STR_MPLS_TC_SET = "set_mpls_tc";
	public static final String STR_MPLS_TTL_SET = "set_mpls_ttl";
	public static final String STR_MPLS_TTL_DEC = "dec_mpls_ttl";
	public static final String STR_MPLS_PUSH = "push_mpls";
	public static final String STR_MPLS_POP = "pop_mpls";
	public static final String STR_TP_SRC_SET = "set_src_port";
	public static final String STR_TP_DST_SET = "set_dst_port";
	public static final String STR_TTL_IN_COPY = "copy_ttl_in";
	public static final String STR_TTL_OUT_COPY = "copy_ttl_out";
	public static final String STR_PBB_PUSH = "push_pbb";
	public static final String STR_PBB_POP = "pop_pbb";
	public static final String STR_EXPERIMENTER = "experimenter";
	public static final String STR_GROUP = "group";
	public static final String STR_FIELD_SET = "set_field";

	/**
     * Returns a String representation of all the OpenFlow actions.
     * @param actions A list of OFActions to encode into one string
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
                    sb.append(STR_OUTPUT + "=" + ((OFActionOutput)a).getPort().toString());
                    break;
                case ENQUEUE:
                    long queue = ((OFActionEnqueue)a).getQueueId();
                    OFPort port = ((OFActionEnqueue)a).getPort();
                    sb.append(STR_ENQUEUE + "=" + Integer.toString(port.getPortNumber()) + ":0x" + String.format("%02x", queue));
                    break;
                case STRIP_VLAN:
                    sb.append(STR_VLAN_STRIP);
                    break;
                case SET_VLAN_VID:
                    sb.append(STR_VLAN_SET_VID + "=" + 
                        ((OFActionSetVlanVid)a).getVlanVid().toString());
                    break;
                case SET_VLAN_PCP:
                    sb.append(STR_VLAN_SET_PCP + "=" +
                        Byte.toString(((OFActionSetVlanPcp)a).getVlanPcp().getValue()));
                    break;
                case SET_DL_SRC:
                    sb.append(STR_DL_SRC_SET + "=" + 
                        ((OFActionSetDlSrc)a).getDlAddr().toString());
                    break;
                case SET_DL_DST:
                    sb.append(STR_DL_DST_SET + "=" + 
                        ((OFActionSetDlDst)a).getDlAddr().toString());
                    break;
                case SET_NW_TOS:
                    sb.append(STR_NW_TOS_SET + "=" +
                        Short.toString(((OFActionSetNwTos)a).getNwTos()));
                    break;
                case SET_NW_SRC:
                    sb.append(STR_NW_SRC_SET + "=" +
                        ((OFActionSetNwSrc)a).getNwAddr().toString());
                    break;
                case SET_NW_DST:
                    sb.append(STR_NW_DST_SET + "=" +
                        ((OFActionSetNwDst)a).getNwAddr().toString());
                    break;
                case SET_TP_SRC:
                    sb.append(STR_TP_SRC_SET + "=" +
                        ((OFActionSetTpSrc)a).getTpPort().toString());
                    break;
                case SET_TP_DST:
                    sb.append(STR_TP_DST_SET + "=" +
                        ((OFActionSetTpDst)a).getTpPort().toString());
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
		if (bigString != null) {
			bigString = bigString.toLowerCase();
			for (String actionToDecode : bigString.split(",")) {
				String action = actionToDecode.split("[=:]")[0];
				OFAction a = null;

				if (action.equals(STR_OUTPUT)) {
					a = decode_output(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_ENQUEUE)) {
					a = decode_enqueue(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_VLAN_STRIP)) {
					a = decode_strip_vlan(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_VLAN_SET_VID)) {
					a = decode_set_vlan_id(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_VLAN_SET_PCP)) {
					a = decode_set_vlan_priority(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_DL_SRC_SET)) {
					a = decode_set_src_mac(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_DL_DST_SET)) {
					a = decode_set_dst_mac(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_NW_TOS_SET)) {
					a = decode_set_tos_bits(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_NW_SRC_SET)) {
					a = decode_set_src_ip(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_NW_DST_SET)) {
					a = decode_set_dst_ip(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_TP_SRC_SET)) {
					a = decode_set_src_port(actionToDecode, fmb.getVersion(), log);
				}
				else if (action.equals(STR_TP_DST_SET)) {
					a = decode_set_dst_port(actionToDecode, fmb.getVersion(), log);
				}
				else {
					log.error("Unexpected action '{}', '{}'", action, actionToDecode);
				}

				if (a != null) {
					actions.add(a);
				}
			}
		}
		log.debug("action {}", actions);

		fmb.setActions(actions);
		return;
	} 

	@LogMessageDoc(level="ERROR",
			message="Invalid subaction: '{subaction}'",
			explanation="A static flow entry contained an invalid subaction",
			recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	private static OFActionOutput decode_output(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_OUTPUT + 
				"=(?:((?:0x)?\\d+)|(all)|(controller)|(local)|(ingress-port)|(normal)|(flood))").matcher(actionToDecode);
		if (n.matches()) {
			OFActionOutput.Builder ab = OFFactories.getFactory(version).actions().buildOutput();
			OFPort port = OFPort.ANY;
			if (n.group(1) != null) {
				try {
					port = OFPort.of(get_short(n.group(1)));
				}
				catch (NumberFormatException e) {
					log.debug("Invalid port in: '{}' (error ignored)", actionToDecode);
					return null;
				}
			}
			else if (n.group(2) != null)
				port = OFPort.ALL;
			else if (n.group(3) != null)
				port = OFPort.CONTROLLER;
			else if (n.group(4) != null)
				port = OFPort.LOCAL;
			else if (n.group(5) != null)
				port = OFPort.IN_PORT;
			else if (n.group(6) != null)
				port = OFPort.NORMAL;
			else if (n.group(7) != null)
				port = OFPort.FLOOD;
			ab.setPort(port);
			log.debug("action {}", ab.build());
			return ab.build();
		}
		else {
			log.error("Invalid subaction: '{}'", actionToDecode);
			return null;
		}
	}

	private static OFActionEnqueue decode_enqueue(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_ENQUEUE + "=(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(actionToDecode);
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

	private static OFActionStripVlan decode_strip_vlan(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_VLAN_STRIP).matcher(actionToDecode);
		if (n.matches()) {
			OFActionStripVlan a = OFFactories.getFactory(version).actions().stripVlan();
			log.debug("action {}", a);
			return a;
		}
		else {
			log.debug("Invalid action: '{}'", actionToDecode);
			return null;
		}
	}

	private static OFActionSetVlanVid decode_set_vlan_id(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_VLAN_SET_VID + "=((?:0x)?\\d+)").matcher(actionToDecode);
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

	private static OFActionSetVlanPcp decode_set_vlan_priority(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_VLAN_SET_PCP + "=((?:0x)?\\d+)").matcher(actionToDecode); 
		if (n.matches()) {            
			if (n.group(1) != null) {
				try {
					VlanPcp prior = VlanPcp.of(get_byte(n.group(1)));
					OFActionSetVlanPcp.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetVlanPcp();
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

	private static OFActionSetDlSrc decode_set_src_mac(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_DL_SRC_SET +
				"=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(actionToDecode); 
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

	private static OFActionSetDlDst decode_set_dst_mac(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_DL_DST_SET +
				"=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(actionToDecode);
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

	private static OFActionSetNwTos decode_set_tos_bits(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_NW_TOS_SET + "=((?:0x)?\\d+)").matcher(actionToDecode); 
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

	private static OFActionSetNwSrc decode_set_src_ip(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_NW_SRC_SET + "=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(actionToDecode);
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

	private static OFActionSetNwDst decode_set_dst_ip(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_NW_DST_SET + "=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(actionToDecode);
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

	private static OFActionSetTpSrc decode_set_src_port(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_TP_SRC_SET + "=((?:0x)?\\d+)").matcher(actionToDecode); 
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

	private static OFAction decode_set_dst_port(String actionToDecode, OFVersion version, Logger log) {
		Matcher n = Pattern.compile(STR_TP_DST_SET + "=((?:0x)?\\d+)").matcher(actionToDecode);
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

	// Parse int as decimal, hex (start with 0x or #) or octal (starts with 0)
	private static int get_int(String str) {
		return Integer.decode(str);
	}

	// Parse short as decimal, hex (start with 0x or #) or octal (starts with 0)
	private static short get_short(String str) {
		return (short)(int)Integer.decode(str);
	}

	// Parse byte as decimal, hex (start with 0x or #) or octal (starts with 0)
	private static byte get_byte(String str) {
		return Integer.decode(str).byteValue();
	}

}
