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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.packet.IPv4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionNetworkTypeOfService;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.protocol.action.OFActionVirtualLanPriorityCodePoint;
import org.openflow.util.HexString;

/**
 * Represents static flow entries to be maintained by the controller on the 
 * switches. 
 */
@LogMessageCategory("Static Flow Pusher")
public class StaticFlowEntries {
    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntries.class);
    
    private static class SubActionStruct {
        OFAction action;
        int      len;
    }
    
    private static byte[] zeroMac = new byte[] {0x0, 0x0, 0x0, 0x0, 0x0, 0x0};
    
    /**
     * This function generates a random hash for the bottom half of the cookie
     * 
     * @param fm
     * @param userCookie
     * @param name
     * @return A cookie that encodes the application ID and a hash
     */
    public static long computeEntryCookie(OFFlowMod fm, int userCookie, String name) {
        // flow-specific hash is next 20 bits LOOK! who knows if this 
        /*
        int prime = 211;
        int flowHash = 2311;
        for (int i=0; i < name.length(); i++)
            flowHash = flowHash * prime + (int)name.charAt(i);
        */
        // For now we use 0 so we can do a mass delete by cookie
        return AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, 0);
    }
    
    /**
     * Sets defaults for an OFFlowMod
     * @param fm The OFFlowMod to set defaults for
     * @param entryName The name of the entry. Used to compute the cookie.
     */
    public static void initDefaultFlowMod(OFFlowMod fm, String entryName) {
        fm.setIdleTimeout((short) 0);   // infinite
        fm.setHardTimeout((short) 0);   // infinite
        fm.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        fm.setCommand((short) 0);
        fm.setFlags((short) 0);
        fm.setOutPort(OFPort.OFPP_NONE.getValue());
        fm.setCookie(computeEntryCookie(fm, 0, entryName));  
        fm.setPriority(Short.MAX_VALUE);
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
        OFMatch match = fm.getMatch();
        entry.put(StaticFlowEntryPusher.COLUMN_NAME, name);
        entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, sw);
        entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, Boolean.toString(true));
        entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, Short.toString(fm.getPriority()));
        entry.put(StaticFlowEntryPusher.COLUMN_WILDCARD, Integer.toString(match.getWildcards()));
        
        if ((fm.getActions() != null) && (fm.getActions().size() > 0))
        	entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, StaticFlowEntries.flowModActionsToString(fm.getActions()));
        
        if (match.getInputPort() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, Short.toString(match.getInputPort()));
        
        if (!Arrays.equals(match.getDataLayerSource(), zeroMac))
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, HexString.toHexString(match.getDataLayerSource()));

        if (!Arrays.equals(match.getDataLayerDestination(), zeroMac))
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, HexString.toHexString(match.getDataLayerDestination()));
        
        if (match.getDataLayerVirtualLan() != -1)
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, Short.toString(match.getDataLayerVirtualLan()));
        
        if (match.getDataLayerVirtualLanPriorityCodePoint() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, Short.toString(match.getDataLayerVirtualLanPriorityCodePoint()));
        
        if (match.getDataLayerType() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, Short.toString(match.getDataLayerType()));
        
        if (match.getNetworkTypeOfService() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_TOS, Short.toString(match.getNetworkTypeOfService()));
        
        if (match.getNetworkProtocol() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, Short.toString(match.getNetworkProtocol()));
        
        if (match.getNetworkSource() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, IPv4.fromIPv4Address(match.getNetworkSource()));
        
        if (match.getNetworkDestination() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, IPv4.fromIPv4Address(match.getNetworkDestination()));
        
        if (match.getTransportSource() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, Short.toString(match.getTransportSource()));
        
        if (match.getTransportDestination() != 0)
        	entry.put(StaticFlowEntryPusher.COLUMN_TP_DST, Short.toString(match.getTransportDestination()));
        
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
                    sb.append("output=" + Short.toString(((OFActionOutput)a).getPort()));
                    break;
                case OPAQUE_ENQUEUE:
                    int queue = ((OFActionEnqueue)a).getQueueId();
                    short port = ((OFActionEnqueue)a).getPort();
                    sb.append("enqueue=" + Short.toString(port) + ":0x" + String.format("%02x", queue));
                    break;
                case STRIP_VLAN:
                    sb.append("strip-vlan");
                    break;
                case SET_VLAN_ID:
                    sb.append("set-vlan-id=" + 
                        Short.toString(((OFActionVirtualLanIdentifier)a).getVirtualLanIdentifier()));
                    break;
                case SET_VLAN_PCP:
                    sb.append("set-vlan-priority=" +
                        Byte.toString(((OFActionVirtualLanPriorityCodePoint)a).getVirtualLanPriorityCodePoint()));
                    break;
                case SET_DL_SRC:
                    sb.append("set-src-mac=" + 
                        HexString.toHexString(((OFActionDataLayerSource)a).getDataLayerAddress()));
                    break;
                case SET_DL_DST:
                    sb.append("set-dst-mac=" + 
                        HexString.toHexString(((OFActionDataLayerDestination)a).getDataLayerAddress()));
                    break;
                case SET_NW_TOS:
                    sb.append("set-tos-bits=" +
                        Byte.toString(((OFActionNetworkTypeOfService)a).getNetworkTypeOfService()));
                    break;
                case SET_NW_SRC:
                    sb.append("set-src-ip=" +
                        IPv4.fromIPv4Address(((OFActionNetworkLayerSource)a).getNetworkAddress()));
                    break;
                case SET_NW_DST:
                    sb.append("set-dst-ip=" +
                        IPv4.fromIPv4Address(((OFActionNetworkLayerDestination)a).getNetworkAddress()));
                    break;
                case SET_TP_SRC:
                    sb.append("set-src-port=" +
                        Short.toString(((OFActionTransportLayerSource)a).getTransportPort()));
                    break;
                case SET_TP_DST:
                    sb.append("set-dst-port=" +
                        Short.toString(((OFActionTransportLayerDestination)a).getTransportPort()));
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
        int actionsLength = 0;
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
                    actionsLength += subaction_struct.len;
                }
            }
        }
        log.debug("action {}", actions);
        
        flowMod.setActions(actions);
        flowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH + actionsLength);
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
            OFActionOutput action = new OFActionOutput();
            action.setMaxLength((short) Short.MAX_VALUE);
            short port = OFPort.OFPP_NONE.getValue();
            if (n.group(1) != null) {
                try {
                    port = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    log.debug("Invalid port in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else if (n.group(2) != null)
                port = OFPort.OFPP_ALL.getValue();
            else if (n.group(3) != null)
                port = OFPort.OFPP_CONTROLLER.getValue();
            else if (n.group(4) != null)
                port = OFPort.OFPP_LOCAL.getValue();
            else if (n.group(5) != null)
                port = OFPort.OFPP_IN_PORT.getValue();
            else if (n.group(6) != null)
                port = OFPort.OFPP_NORMAL.getValue();
            else if (n.group(7) != null)
                port = OFPort.OFPP_FLOOD.getValue();
            action.setPort(port);
            log.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionOutput.MINIMUM_LENGTH;
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
            short portnum = 0;
            if (n.group(1) != null) {
                try {
                    portnum = get_short(n.group(1));
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
            
            OFActionEnqueue action = new OFActionEnqueue();
            action.setPort(portnum);
            action.setQueueId(queueid);
            log.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionEnqueue.MINIMUM_LENGTH;
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
            OFActionStripVirtualLan action = new OFActionStripVirtualLan();
            log.debug("action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionStripVirtualLan.MINIMUM_LENGTH;
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
                    short vlanid = get_short(n.group(1));
                    OFActionVirtualLanIdentifier action = new OFActionVirtualLanIdentifier();
                    action.setVirtualLanIdentifier(vlanid);
                    log.debug("  action {}", action);

                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
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
                    byte prior = get_byte(n.group(1));
                    OFActionVirtualLanPriorityCodePoint action = new OFActionVirtualLanPriorityCodePoint();
                    action.setVirtualLanPriorityCodePoint(prior);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionVirtualLanPriorityCodePoint.MINIMUM_LENGTH;
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
            byte[] macaddr = get_mac_addr(n, subaction, log);
            if (macaddr != null) {
                OFActionDataLayerSource action = new OFActionDataLayerSource();
                action.setDataLayerAddress(macaddr);
                log.debug("action {}", action);

                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionDataLayerSource.MINIMUM_LENGTH;
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
            byte[] macaddr = get_mac_addr(n, subaction, log);            
            if (macaddr != null) {
                OFActionDataLayerDestination action = new OFActionDataLayerDestination();
                action.setDataLayerAddress(macaddr);
                log.debug("  action {}", action);
                
                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionDataLayerDestination.MINIMUM_LENGTH;
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
                    OFActionNetworkTypeOfService action = new OFActionNetworkTypeOfService();
                    action.setNetworkTypeOfService(tosbits);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionNetworkTypeOfService.MINIMUM_LENGTH;
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
            int ipaddr = get_ip_addr(n, subaction, log);
            OFActionNetworkLayerSource action = new OFActionNetworkLayerSource();
            action.setNetworkAddress(ipaddr);
            log.debug("  action {}", action);

            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionNetworkLayerSource.MINIMUM_LENGTH;
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
            int ipaddr = get_ip_addr(n, subaction, log);
            OFActionNetworkLayerDestination action = new OFActionNetworkLayerDestination();
            action.setNetworkAddress(ipaddr);
            log.debug("action {}", action);
 
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionNetworkLayerDestination.MINIMUM_LENGTH;
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
                    short portnum = get_short(n.group(1));
                    OFActionTransportLayerSource action = new OFActionTransportLayerSource();
                    action.setTransportPort(portnum);
                    log.debug("action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionTransportLayerSource.MINIMUM_LENGTH;;
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
                    short portnum = get_short(n.group(1));
                    OFActionTransportLayerDestination action = new OFActionTransportLayerDestination();
                    action.setTransportPort(portnum);
                    log.debug("action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionTransportLayerDestination.MINIMUM_LENGTH;;
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
        return (int)Integer.decode(str);
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

