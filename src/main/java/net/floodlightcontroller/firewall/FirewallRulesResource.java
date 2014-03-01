/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by Amer Tahir
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

package net.floodlightcontroller.firewall;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import org.openflow.util.HexString;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

public class FirewallRulesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(FirewallRulesResource.class);

    @Get("json")
    public List<FirewallRule> retrieve() {
        IFirewallService firewall =
                (IFirewallService)getContext().getAttributes().
                get(IFirewallService.class.getCanonicalName());

        return firewall.getRules();
    }

    /**
     * Takes a Firewall Rule string in JSON format and parses it into
     * our firewall rule data structure, then adds it to the firewall.
     * @param fmJson The Firewall rule entry in JSON format.
     * @return A string status message
     */
    @Post
    public String store(String fmJson) {
        IFirewallService firewall =
                (IFirewallService)getContext().getAttributes().
                get(IFirewallService.class.getCanonicalName());

        FirewallRule rule;
        try {
            rule = jsonToFirewallRule(fmJson);
        } catch (IOException e) {
            log.error("Error parsing firewall rule: " + fmJson, e);
            return "{\"status\" : \"Error! Could not parse firewall rule, see log for details.\"}";
        }
        String status = null;
        if (checkRuleExists(rule, firewall.getRules())) {
            status = "Error! A similar firewall rule already exists.";
            log.error(status);
        	return ("{\"status\" : \"" + status + "\"}");
        } else {
            // add rule to firewall
            firewall.addRule(rule);
            status = "Rule added";
        	return ("{\"status\" : \"" + status + "\", \"rule-id\" : \""+ Integer.toString(rule.ruleid) + "\"}");
        }
    }

    /**
     * Takes a Firewall Rule string in JSON format and parses it into
     * our firewall rule data structure, then deletes it from the firewall.
     * @param fmJson The Firewall rule entry in JSON format.
     * @return A string status message
     */

    @Delete
    public String remove(String fmJson) {
        IFirewallService firewall =
                (IFirewallService)getContext().getAttributes().
                get(IFirewallService.class.getCanonicalName());

        FirewallRule rule;
        try {
            rule = jsonToFirewallRule(fmJson);
        } catch (IOException e) {
            log.error("Error parsing firewall rule: " + fmJson, e);
            return "{\"status\" : \"Error! Could not parse firewall rule, see log for details.\"}";
        }
        String status = null;
        boolean exists = false;
        Iterator<FirewallRule> iter = firewall.getRules().iterator();
        while (iter.hasNext()) {
            FirewallRule r = iter.next();
            if (r.ruleid == rule.ruleid) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            status = "Error! Can't delete, a rule with this ID doesn't exist.";
            log.error(status);
        } else {
            // delete rule from firewall
            firewall.deleteRule(rule.ruleid);
            status = "Rule deleted";
        }
        return ("{\"status\" : \"" + status + "\"}");
    }

    /**
     * Turns a JSON formatted Firewall Rule string into a FirewallRule instance
     * @param fmJson The JSON formatted static firewall rule
     * @return The FirewallRule instance
     * @throws IOException If there was an error parsing the JSON
     */

    public static FirewallRule jsonToFirewallRule(String fmJson) throws IOException {
        FirewallRule rule = new FirewallRule();
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

            String tmp;

            // This is currently only applicable for remove().  In store(), ruleid takes a random number
            if (n == "ruleid") {
                rule.ruleid = Integer.parseInt(jp.getText());
            }

            // This assumes user having dpid info for involved switches
            else if (n == "switchid") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("-1") == false) {
                    // user inputs hex format dpid
                    rule.dpid = HexString.toLong(tmp);
                    rule.wildcard_dpid = false;
                }
            }

            else if (n == "src-inport") {
                rule.in_port = Short.parseShort(jp.getText());
                rule.wildcard_in_port = false;
            }

            else if (n == "src-mac") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("ANY") == false) {
                    rule.wildcard_dl_src = false;
                    rule.dl_src = Ethernet.toLong(Ethernet.toMACAddress(tmp));
                }
            }

            else if (n == "dst-mac") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("ANY") == false) {
                    rule.wildcard_dl_dst = false;
                    rule.dl_dst = Ethernet.toLong(Ethernet.toMACAddress(tmp));
                }
            }

            else if (n == "dl-type") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("ARP")) {
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_ARP;
                }
                if (tmp.equalsIgnoreCase("IPv4")) {
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_IPv4;
                }
            }

            else if (n == "src-ip") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("ANY") == false) {
                    rule.wildcard_nw_src = false;
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_IPv4;
                    int[] cidr = IPCIDRToPrefixBits(tmp);
                    rule.nw_src_prefix = cidr[0];
                    rule.nw_src_maskbits = cidr[1];
                }
            }

            else if (n == "dst-ip") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("ANY") == false) {
                    rule.wildcard_nw_dst = false;
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_IPv4;
                    int[] cidr = IPCIDRToPrefixBits(tmp);
                    rule.nw_dst_prefix = cidr[0];
                    rule.nw_dst_maskbits = cidr[1];
                }
            }

            else if (n == "nw-proto") {
                tmp = jp.getText();
                if (tmp.equalsIgnoreCase("TCP")) {
                    rule.wildcard_nw_proto = false;
                    rule.nw_proto = IPv4.PROTOCOL_TCP;
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_IPv4;
                } else if (tmp.equalsIgnoreCase("UDP")) {
                    rule.wildcard_nw_proto = false;
                    rule.nw_proto = IPv4.PROTOCOL_UDP;
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_IPv4;
                } else if (tmp.equalsIgnoreCase("ICMP")) {
                    rule.wildcard_nw_proto = false;
                    rule.nw_proto = IPv4.PROTOCOL_ICMP;
                    rule.wildcard_dl_type = false;
                    rule.dl_type = Ethernet.TYPE_IPv4;
                }
            }

            else if (n == "tp-src") {
                rule.wildcard_tp_src = false;
                rule.tp_src = Short.parseShort(jp.getText());
            }

            else if (n == "tp-dst") {
                rule.wildcard_tp_dst = false;
                rule.tp_dst = Short.parseShort(jp.getText());
            }

            else if (n == "priority") {
                rule.priority = Integer.parseInt(jp.getText());
            }

            else if (n == "action") {
                if (jp.getText().equalsIgnoreCase("allow") == true) {
                    rule.action = FirewallRule.FirewallAction.ALLOW;
                } else if (jp.getText().equalsIgnoreCase("deny") == true) {
                    rule.action = FirewallRule.FirewallAction.DENY;
                }
            }
        }

        return rule;
    }

    public static int[] IPCIDRToPrefixBits(String cidr) {
        int ret[] = new int[2];

        // as IP can also be a prefix rather than an absolute address
        // split it over "/" to get the bit range
        String[] parts = cidr.split("/");
        String cidr_prefix = parts[0].trim();
        int cidr_bits = 0;
        if (parts.length == 2) {
            try {
                cidr_bits = Integer.parseInt(parts[1].trim());
            } catch (Exception exp) {
                cidr_bits = 32;
            }
        }
        ret[0] = IPv4.toIPv4Address(cidr_prefix);
        ret[1] = cidr_bits;

        return ret;
    }

    public static boolean checkRuleExists(FirewallRule rule, List<FirewallRule> rules) {
        Iterator<FirewallRule> iter = rules.iterator();
        while (iter.hasNext()) {
            FirewallRule r = iter.next();

            // check if we find a similar rule
            if (rule.isSameAs(r)) {
                return true;
            }
        }

        // no rule matched, so it doesn't exist in the rules
        return false;
    }
}
