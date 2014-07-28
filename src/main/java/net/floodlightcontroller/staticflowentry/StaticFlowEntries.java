/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.staticflowentry;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.util.AppCookie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionEnqueue;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwTos;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionStripVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetTpSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanVid;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanPcp;

/**
 * Represents static flow entries to be maintained by the controller on the 
 * switches. 
 */
@LogMessageCategory("Static Flow Pusher")
public class StaticFlowEntries {
    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntries.class);
    
    private static class SubActionStruct {
        OFAction action;
    }
        
    /**
     * This function generates a random hash for the bottom half of the cookie
     * 
     * @param fm
     * @param userCookie
     * @param name
     * @return A cookie that encodes the application ID and a hash
     */
    public static U64 computeEntryCookie(OFFlowMod fm, int userCookie, String name) {
        // flow-specific hash is next 20 bits LOOK! who knows if this 
        int prime = 211;
        int flowHash = 2311;
        for (int i=0; i < name.length(); i++)
            flowHash = flowHash * prime + (int)name.charAt(i);

        return AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, flowHash);
    }
    
    /**
     * Sets defaults for an OFFlowMod
     * @param fm The OFFlowMod to set defaults for
     * @param entryName The name of the entry. Used to compute the cookie.
     */
    public static void initDefaultFlowMod(OFFlowMod fm, String entryName) {
        fm = fm.createBuilder().setIdleTimeout((short) 0)   // infinite
        .setHardTimeout((short) 0)  // infinite
        .setBufferId(OFBufferId.NO_BUFFER)
        .setOutPort(OFPort.ANY) 
        .setCookie(computeEntryCookie(fm, 0, entryName))
        .setPriority(Integer.MAX_VALUE)
        .build();
    }
    
    /**
     * Gets the entry name of a flow mod
     * @param fmJson The OFFlowMod in a JSON representation
     * @return The name of the OFFlowMod, null if not found
     * @throws IOException If there was an error parsing the JSON
     */
    public static String getEntryNameFromJson(String fmJson) throws IOException{
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(fmJson);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            
            if (n == "name")
                return jp.getText();
        }
        
        return null;
    }
    
    /**
     * Parses an OFFlowMod (and it's inner OFMatch) to the storage entry format.
     * @param fm The FlowMod to parse
     * @param sw The switch the FlowMod is going to be installed on
     * @param name The name of this static flow entry
     * @return A Map representation of the storage entry 
     */
    public static Map<String, Object> flowModToStorageEntry(OFFlowMod fm, String sw, String name) {
        Map<String, Object> entry = new HashMap<String, Object>();
        Match match = fm.getMatch();
        entry.put(StaticFlowEntryPusher.COLUMN_NAME, name);
        entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, sw);
        entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, Boolean.toString(true));
        entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, Integer.toString(fm.getPriority()));
        //entry.put(StaticFlowEntryPusher.COLUMN_WILDCARD, Integer.toString(match.getWildcards())); TODO @Ryan what to do about wildcards?
        
        if ((fm.getActions() != null) && (fm.getActions().size() > 0))
        	entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, StaticFlowEntries.flowModActionsToString(fm.getActions()));
        
        if (match.get(MatchField.IN_PORT).getPortNumber() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, Integer.toString(match.get(MatchField.IN_PORT).getPortNumber()));
        
        if (!match.get(MatchField.ETH_SRC).equals(MacAddress.of(0)))
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, match.get(MatchField.ETH_SRC).toString());

        if (!match.get(MatchField.ETH_DST).equals(MacAddress.of(0)))
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, match.get(MatchField.ETH_DST).toString());
        
        if (match.get(MatchField.VLAN_VID).getVlan() != -1)
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, match.get(MatchField.VLAN_VID).toString());
        
        if (match.get(MatchField.VLAN_PCP).getValue() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, Byte.toString(match.get(MatchField.VLAN_PCP).getValue()));
        
        if (match.get(MatchField.ETH_TYPE).getValue() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, match.get(MatchField.ETH_TYPE).toString());
        
        if (match.get(MatchField.IP_ECN).getEcnValue() != 0 && match.get(MatchField.IP_DSCP).getDscpValue() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_TOS, // TOS = [DSCP bits 0-5] + [ECN bits 6-7] --> bitwise OR to get TOS byte
        			Byte.toString((byte) (match.get(MatchField.IP_ECN).getEcnValue() | match.get(MatchField.IP_DSCP).getDscpValue())));
        
        if (match.get(MatchField.IP_PROTO).getIpProtocolNumber() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, Short.toString(match.get(MatchField.IP_PROTO).getIpProtocolNumber()));
        
        if (match.get(MatchField.IPV4_SRC).getInt() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, match.get(MatchField.IPV4_SRC).toString());
        
        if (match.get(MatchField.IPV4_DST).getInt() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, match.get(MatchField.IPV4_DST).toString());
        
        if (match.get(MatchField.TCP_SRC).getPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, match.get(MatchField.TCP_SRC).toString());
        else if (match.get(MatchField.UDP_SRC).getPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, match.get(MatchField.UDP_SRC).toString());
        else if (match.get(MatchField.SCTP_SRC).getPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, match.get(MatchField.SCTP_SRC).toString());
        
        if (match.get(MatchField.TCP_DST).getPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_DST, match.get(MatchField.TCP_DST).toString());
        else if (match.get(MatchField.UDP_DST).getPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, match.get(MatchField.UDP_DST).toString());
        else if (match.get(MatchField.SCTP_DST).getPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, match.get(MatchField.SCTP_DST).toString());
        
        return entry;
    }
    
    /**
     * Returns a String representation of all the openflow actions.
     * @param fmActions A list of OFActions to encode into one string
     * @return A string of the actions encoded for our database
     */
    @LogMessageDoc(level="ERROR",
            message="Could not decode action {action}",
            explanation="A static flow entry contained an invalid action",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private static String flowModActionsToString(List<OFAction> fmActions) {
        StringBuilder sb = new StringBuilder();
        for (OFAction a : fmActions) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            switch(a.getType()) {
                case OUTPUT:
                    sb.append("output=" + ((OFActionOutput)a).getPort().toString());
                    break;
                case ENQUEUE:
                    long queue = ((OFActionEnqueue)a).getQueueId();
                    OFPort port = ((OFActionEnqueue)a).getPort();
                    sb.append("enqueue=" + Integer.toString(port.getPortNumber()) + ":0x" + String.format("%02x", queue));
                    break;
                case STRIP_VLAN:
                    sb.append("strip-vlan");
                    break;
                case SET_VLAN_VID:
                    sb.append("set-vlan-id=" + 
                        ((OFActionSetVlanVid)a).getVlanVid().toString());
                    break;
                case SET_VLAN_PCP:
                    sb.append("set-vlan-priority=" +
                        Byte.toString(((OFActionSetVlanPcp)a).getVlanPcp().getValue()));
                    break;
                case SET_DL_SRC:
                    sb.append("set-src-mac=" + 
                        ((OFActionSetDlSrc)a).getDlAddr().toString());
                    break;
                case SET_DL_DST:
                    sb.append("set-dst-mac=" + 
                        ((OFActionSetDlDst)a).getDlAddr().toString());
                    break;
                case SET_NW_TOS:
                    sb.append("set-tos-bits=" +
                        Short.toString(((OFActionSetNwTos)a).getNwTos()));
                    break;
                case SET_NW_SRC:
                    sb.append("set-src-ip=" +
                        ((OFActionSetNwSrc)a).getNwAddr().toString());
                    break;
                case SET_NW_DST:
                    sb.append("set-dst-ip=" +
                        ((OFActionSetNwDst)a).getNwAddr().toString());
                    break;
                case SET_TP_SRC:
                    sb.append("set-src-port=" +
                        ((OFActionSetTpSrc)a).getTpPort().toString());
                    break;
                case SET_TP_DST:
                    sb.append("set-dst-port=" +
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
     * Turns a JSON formatted Static Flow Pusher string into a storage entry
     * Expects a string in JSON along the lines of:
     *        {
     *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
     *            "name":         "flow-mod-1",
     *            "cookie":       "0",
     *            "priority":     "32768",
     *            "ingress-port": "1",
     *            "actions":      "output=2",
     *        }
     * @param fmJson The JSON formatted static flow pusher entry
     * @return The map of the storage entry
     * @throws IOException If there was an error parsing the JSON
     */
    public static Map<String, Object> jsonToStorageEntry(String fmJson) throws IOException {
        Map<String, Object> entry = new HashMap<String, Object>();
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(fmJson);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            
            if (n == "name")
                entry.put(StaticFlowEntryPusher.COLUMN_NAME, jp.getText());
            else if (n == "switch")
                entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, jp.getText());
            else if (n == "actions")
                entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, jp.getText());
            else if (n == "priority")
                entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, jp.getText());
            else if (n == "active")
                entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, jp.getText());
            else if (n == "wildcards")
                entry.put(StaticFlowEntryPusher.COLUMN_WILDCARD, jp.getText());
            else if (n == "ingress-port")
                entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, jp.getText());
            else if (n == "src-mac")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, jp.getText());
            else if (n == "dst-mac")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, jp.getText());
            else if (n == "vlan-id")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, jp.getText());
            else if (n == "vlan-priority")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, jp.getText());
            else if (n == "ether-type")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, jp.getText());
            else if (n == "tos-bits")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_TOS, jp.getText());
            else if (n == "protocol")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, jp.getText());
            else if (n == "src-ip")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, jp.getText());
            else if (n == "dst-ip")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, jp.getText());
            else if (n == "src-port")
                entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, jp.getText());
            else if (n == "dst-port")
                entry.put(StaticFlowEntryPusher.COLUMN_TP_DST, jp.getText());
        }
        
        return entry;
    }
    
    /**
     * Parses OFFlowMod actions from strings.
     * @param flowMod The OFFlowMod to set the actions for
     * @param actionstr The string containing all the actions
     * @param log A logger to log for errors.
     */
    @LogMessageDoc(level="ERROR",
            message="Unexpected action '{action}', '{subaction}'",
            explanation="A static flow entry contained an invalid action",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public static void parseActionString(OFFlowMod flowMod, String actionstr, Logger log) {
        List<OFAction> actions = new LinkedList<OFAction>();
        if (actionstr != null) {
            actionstr = actionstr.toLowerCase();
            for (String subaction : actionstr.split(",")) {
                String action = subaction.split("[=:]")[0];
                SubActionStruct subaction_struct = null;
                
                if (action.equals("output")) {
                    subaction_struct = StaticFlowEntries.decode_output(subaction, log);
                }
                else if (action.equals("enqueue")) {
                    subaction_struct = decode_enqueue(subaction, log);
                }
                else if (action.equals("strip-vlan")) {
                    subaction_struct = decode_strip_vlan(subaction, log);
                }
                else if (action.equals("set-vlan-id")) {
                    subaction_struct = decode_set_vlan_id(subaction, log);
                }
                else if (action.equals("set-vlan-priority")) {
                    subaction_struct = decode_set_vlan_priority(subaction, log);
                }
                else if (action.equals("set-src-mac")) {
                    subaction_struct = decode_set_src_mac(subaction, log);
                }
                else if (action.equals("set-dst-mac")) {
                    subaction_struct = decode_set_dst_mac(subaction, log);
                }
                else if (action.equals("set-tos-bits")) {
                    subaction_struct = decode_set_tos_bits(subaction, log);
                }
                else if (action.equals("set-src-ip")) {
                    subaction_struct = decode_set_src_ip(subaction, log);
                }
                else if (action.equals("set-dst-ip")) {
                    subaction_struct = decode_set_dst_ip(subaction, log);
                }
                else if (action.equals("set-src-port")) {
                    subaction_struct = decode_set_src_port(subaction, log);
                }
                else if (action.equals("set-dst-port")) {
                    subaction_struct = decode_set_dst_port(subaction, log);
                }
                else {
                    log.error("Unexpected action '{}', '{}'", action, subaction);
                }
                
                if (subaction_struct != null) {
                    actions.add(subaction_struct.action);
                }
            }
        }
        log.debug("action {}", actions);
        
        flowMod.createBuilder().setActions(actions).build();
    } 
    
    @LogMessageDoc(level="ERROR",
            message="Invalid subaction: '{subaction}'",
            explanation="A static flow entry contained an invalid subaction",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private static SubActionStruct decode_output(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("output=(?:((?:0x)?\\d+)|(all)|(controller)|(local)|(ingress-port)|(normal)|(flood))").matcher(subaction);
        if (n.matches()) {
            OFActionOutput.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput();
            OFPort port = OFPort.ANY;
            if (n.group(1) != null) {
                try {
                    port = OFPort.of(get_short(n.group(1)));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid port in: '{}' (error ignored)", subaction);
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
            
            sa = new SubActionStruct();
            sa.action = ab.build();
        }
        else {
            log.error("Invalid subaction: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    private static SubActionStruct decode_enqueue(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("enqueue=(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(subaction);
        if (n.matches()) {
            OFPort port = OFPort.of(0);
            if (n.group(1) != null) {
                try {
                    port = OFPort.of(get_short(n.group(1)));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid port-num in: '{}' (error ignored)", subaction);
                    return null;
                }
            }

            int queueid = 0;
            if (n.group(2) != null) {
                try {
                    queueid = get_int(n.group(2));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid queue-id in: '{}' (error ignored)", subaction);
                    return null;
               }
            }
            
            OFActionEnqueue.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildEnqueue();
            ab.setPort(port);
            ab.setQueueId(queueid);
            log.debug("action {}", ab.build());
            
            sa = new SubActionStruct();
            sa.action = ab.build();
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    private static SubActionStruct decode_strip_vlan(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("strip-vlan").matcher(subaction);
        
        if (n.matches()) {
            OFActionStripVlan a = OFFactories.getFactory(OFVersion.OF_13).actions().stripVlan();
            log.debug("action {}", a);
            
            sa = new SubActionStruct();
            sa.action = a;
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_vlan_id(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-vlan-id=((?:0x)?\\d+)").matcher(subaction);
        
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    VlanVid vlanid = VlanVid.ofVlan(get_short(n.group(1)));
                    OFActionSetVlanVid.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetVlanVid();
                    ab.setVlanVid(vlanid);
                    log.debug("  action {}", ab.build());

                    sa = new SubActionStruct();
                    sa.action = ab.build();
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid VLAN in: {} (error ignored)", subaction);
                    return null;
                }
            }          
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_vlan_priority(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-vlan-priority=((?:0x)?\\d+)").matcher(subaction); 
        
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    VlanPcp prior = VlanPcp.of(get_byte(n.group(1)));
                    OFActionSetVlanPcp.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetVlanPcp();
                    ab.setVlanPcp(prior);
                    log.debug("  action {}", ab.build());
                    
                    sa = new SubActionStruct();
                    sa.action = ab.build();
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid VLAN priority in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_src_mac(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction); 

        if (n.matches()) {
            MacAddress macaddr = MacAddress.of(get_mac_addr(n, subaction, log));
            if (macaddr != null) {
                OFActionSetDlSrc.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetDlSrc();
                ab.setDlAddr(macaddr);
                log.debug("action {}", ab.build());

                sa = new SubActionStruct();
                sa.action = ab.build();
            }            
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_mac(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction);
        
        if (n.matches()) {
            MacAddress macaddr = MacAddress.of(get_mac_addr(n, subaction, log));            
            if (macaddr != null) {
                OFActionSetDlDst.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetDlDst();
                ab.setDlAddr(macaddr);
                log.debug("  action {}", ab.build());
                
                sa = new SubActionStruct();
                sa.action = ab.build();
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_tos_bits(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-tos-bits=((?:0x)?\\d+)").matcher(subaction); 

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    byte tosbits = get_byte(n.group(1));
                    OFActionSetNwTos.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetNwTos();
                    ab.setNwTos(tosbits);
                    log.debug("  action {}", ab.build());
                    
                    sa = new SubActionStruct();
                    sa.action = ab.build();
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_src_ip(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);

        if (n.matches()) {
            IPv4Address ipaddr = IPv4Address.of(get_ip_addr(n, subaction, log));
            OFActionSetNwSrc.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetNwSrc();
            ab.setNwAddr(ipaddr);
            log.debug("  action {}", ab.build());

            sa = new SubActionStruct();
            sa.action = ab.build();
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_ip(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);

        if (n.matches()) {
            IPv4Address ipaddr = IPv4Address.of(get_ip_addr(n, subaction, log));
            OFActionSetNwDst.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetNwDst();
            ab.setNwAddr(ipaddr);
            log.debug("action {}", ab.build());
 
            sa = new SubActionStruct();
            sa.action = ab.build();
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_src_port(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-src-port=((?:0x)?\\d+)").matcher(subaction); 

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    TransportPort portnum = TransportPort.of(get_short(n.group(1)));
                    OFActionSetTpSrc.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetTpSrc();
                    ab.setTpPort(portnum);
                    log.debug("action {}", ab.build());
                    
                    sa = new SubActionStruct();
                    sa.action = ab.build();
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid src-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_port(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n = Pattern.compile("set-dst-port=((?:0x)?\\d+)").matcher(subaction);

        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    TransportPort portnum = TransportPort.of(get_short(n.group(1)));
                    OFActionSetTpDst.Builder ab = OFFactories.getFactory(OFVersion.OF_13).actions().buildSetTpDst();
                    ab.setTpPort(portnum);
                    log.debug("action {}", ab.build());
                    
                    sa = new SubActionStruct();
                    sa.action = ab.build();
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static byte[] get_mac_addr(Matcher n, String subaction, Logger log) {
        byte[] macaddr = new byte[6];
        
        for (int i=0; i<6; i++) {
            if (n.group(i+1) != null) {
                try {
                    macaddr[i] = get_byte("0x" + n.group(i+1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid src-mac in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else { 
                log.debug("Invalid src-mac in: '{}' (null, error ignored)", subaction);
                return null;
            }
        }
        
        return macaddr;
    }
    
    private static int get_ip_addr(Matcher n, String subaction, Logger log) {
        int ipaddr = 0;

        for (int i=0; i<4; i++) {
            if (n.group(i+1) != null) {
                try {
                    ipaddr = ipaddr<<8;
                    ipaddr = ipaddr | get_int(n.group(i+1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid src-ip in: '{}' (error ignored)", subaction);
                    return 0;
                }
            }
            else {
                log.debug("Invalid src-ip in: '{}' (null, error ignored)", subaction);
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

