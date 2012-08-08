package net.floodlightcontroller.firewall;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.firewall.Firewall;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;


public class FirewallRulesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(FirewallRulesResource.class);
    
    @Get("json")
    public Object handleRequest() {
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
            e.printStackTrace();
            return "{\"status\" : \"Error! Could not parse firewall rule, see log for details.\"}";
        }
        String status = null;
    	if (checkRuleExists(rule, firewall.getRules())) {
    		status = "Error! A similar firewall rule already exists.";
            log.error(status);
    	} else {
    		// add rule to firewall
    		firewall.addRule(rule);
    		status = "Rule added";
    	}
    	return ("{\"status\" : \"" + status + "\"}");
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
            e.printStackTrace();
            return "{\"status\" : \"Error! Could not parse firewall rule, see log for details.\"}";
        }
        String status = null;
    	boolean exists = false;
    	Iterator<FirewallRule> iter = firewall.getRules().iterator();
    	while (iter.hasNext()) {
    		FirewallRule r = iter.next();
    		if (r.ruleid.equalsIgnoreCase(rule.ruleid)) {
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
            if (n == "ruleid") {
                rule.ruleid = jp.getText();
            } else if (n == "switchid") {
            		tmp = jp.getText();
            		if (tmp.equalsIgnoreCase("-1") == false) {
            			rule.switchid = Long.parseLong(tmp);
                    	rule.wildcard_switchid = false;
            		}
            } else if (n == "src_inport") {
            	rule.src_inport = Short.parseShort(jp.getText());
            } else if (n == "src_mac") {
            	tmp = jp.getText();
            	if (tmp.equalsIgnoreCase("ANY") == false) {
            		rule.wildcard_src_mac = false;
            		rule.src_mac = Ethernet.toLong(Ethernet.toMACAddress(tmp));
            	}
            } else if (n == "src_ip") {
            	tmp = jp.getText();
            	if (tmp.equalsIgnoreCase("ANY") == false) {
            		rule.wildcard_src_ip = false;
            		int[] cidr = IPCIDRToPrefixBits(tmp);
            		rule.src_ip_prefix = cidr[0];
            		rule.src_ip_bits = cidr[1];
            	}
            } else if (n == "proto_type") {
            	tmp = jp.getText();
            	if (tmp.equalsIgnoreCase("TCP")) {
            		rule.wildcard_proto_type = false;
            		rule.proto_type = IPv4.PROTOCOL_TCP;
            	} else if (tmp.equalsIgnoreCase("UDP")) {
            		rule.wildcard_proto_type = false;
            		rule.proto_type = IPv4.PROTOCOL_UDP;
            	} else if (tmp.equalsIgnoreCase("ICMP")) {
            		rule.wildcard_proto_type = false;
            		rule.proto_type = IPv4.PROTOCOL_ICMP;
            	}
            } else if (n == "proto_srcport") {
            	rule.proto_srcport = Short.parseShort(jp.getText());
            } else if (n == "proto_dstport") {
            	rule.proto_dstport = Short.parseShort(jp.getText());
            } else if (n == "dst_mac") {
            	tmp = jp.getText();
            	if (tmp.equalsIgnoreCase("ANY") == false) {
            		rule.wildcard_dst_mac = false;
            		rule.dst_mac = Ethernet.toLong(Ethernet.toMACAddress(tmp));
            	}
            } else if (n == "dst_ip") {
            	tmp = jp.getText();
            	if (tmp.equalsIgnoreCase("ANY") == false) {
            		rule.wildcard_dst_ip = false;
            		int[] cidr = IPCIDRToPrefixBits(tmp);
            		rule.dst_ip_prefix = cidr[0];
            		rule.dst_ip_bits = cidr[1];
            	}
            } else if (n == "priority") {
            	rule.priority = Integer.parseInt(jp.getText());
            } else if (n == "is_denyrule") {
            	if (jp.getText().equalsIgnoreCase("true") == true) {
            		rule.is_denyrule = true;
            	} else {
            		rule.is_denyrule = false;
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
