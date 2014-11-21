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

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

		FirewallRule rule = jsonToFirewallRule(fmJson);
		if (rule == null) {
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

		FirewallRule rule = jsonToFirewallRule(fmJson);
		if (rule == null) {
			//TODO compose the error with a json formatter
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

	public static FirewallRule jsonToFirewallRule(String fmJson) {
		FirewallRule rule = new FirewallRule();
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		try {
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
				if (jp.getText().equals("")) {
					continue;
				}

				// This is currently only applicable for remove().  In store(), ruleid takes a random number
				if (n.equalsIgnoreCase("ruleid")) {
					try {
						rule.ruleid = Integer.parseInt(jp.getText());
					} catch (IllegalArgumentException e) {
						log.error("Unable to parse rule ID: {}", jp.getText());
					}
				}

				// This assumes user having dpid info for involved switches
				else if (n.equalsIgnoreCase("switchid")) {
					rule.any_dpid = false;
					try {
						rule.dpid = DatapathId.of(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse switch DPID: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("src-inport")) {
					rule.any_in_port = false;
					try {
						rule.in_port = OFPort.of(Integer.parseInt(jp.getText()));
					} catch (NumberFormatException e) {
						log.error("Unable to parse ingress port: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("src-mac")) {
					if (!jp.getText().equalsIgnoreCase("ANY")) {
						rule.any_dl_src = false;
						try {
							rule.dl_src = MacAddress.of(jp.getText());
						} catch (IllegalArgumentException e) {
							log.error("Unable to parse source MAC: {}", jp.getText());
							//TODO should return some error message via HTTP message
						}
					}
				}

				else if (n.equalsIgnoreCase("dst-mac")) {
					if (!jp.getText().equalsIgnoreCase("ANY")) {
						rule.any_dl_dst = false;
						try {
							rule.dl_dst = MacAddress.of(jp.getText());
						} catch (IllegalArgumentException e) {
							log.error("Unable to parse destination MAC: {}", jp.getText());
							//TODO should return some error message via HTTP message
						}
					}
				}

				else if (n.equalsIgnoreCase("dl-type")) {
					if (jp.getText().equalsIgnoreCase("ARP")) {
						rule.any_dl_type = false;
						rule.dl_type = EthType.ARP;
					} else if (jp.getText().equalsIgnoreCase("IPv4")) {
						rule.any_dl_type = false;
						rule.dl_type = EthType.IPv4;
					}
				}

				else if (n.equalsIgnoreCase("src-ip")) {
					if (!jp.getText().equalsIgnoreCase("ANY")) {
						rule.any_nw_src = false;
						rule.any_dl_type = false;
						rule.dl_type = EthType.IPv4;
						try {
							rule.nw_src_prefix_and_mask = IPv4AddressWithMask.of(jp.getText());
						} catch (IllegalArgumentException e) {
							log.error("Unable to parse source IP: {}", jp.getText());
							//TODO should return some error message via HTTP message
						}
					}
				}

				else if (n.equalsIgnoreCase("dst-ip")) {
					if (!jp.getText().equalsIgnoreCase("ANY")) {
						rule.any_nw_dst = false;
						rule.any_dl_type = false;
						rule.dl_type = EthType.IPv4;
						try {
							rule.nw_dst_prefix_and_mask = IPv4AddressWithMask.of(jp.getText());
						} catch (IllegalArgumentException e) {
							log.error("Unable to parse destination IP: {}", jp.getText());
							//TODO should return some error message via HTTP message
						}
					}
				}

				else if (n.equalsIgnoreCase("nw-proto")) {
					if (jp.getText().equalsIgnoreCase("TCP")) {
						rule.any_nw_proto = false;
						rule.nw_proto = IpProtocol.TCP;
						rule.any_dl_type = false;
						rule.dl_type = EthType.IPv4;
					} else if (jp.getText().equalsIgnoreCase("UDP")) {
						rule.any_nw_proto = false;
						rule.nw_proto = IpProtocol.UDP;
						rule.any_dl_type = false;
						rule.dl_type = EthType.IPv4;
					} else if (jp.getText().equalsIgnoreCase("ICMP")) {
						rule.any_nw_proto = false;
						rule.nw_proto = IpProtocol.ICMP;
						rule.any_dl_type = false;
						rule.dl_type = EthType.IPv4;
					}
				}

				else if (n.equalsIgnoreCase("tp-src")) {
					rule.any_tp_src = false;
					try {
						rule.tp_src = TransportPort.of(Integer.parseInt(jp.getText()));
					} catch (IllegalArgumentException e) {
						log.error("Unable to parse source transport port: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("tp-dst")) {
					rule.any_tp_dst = false;
					try {
						rule.tp_dst = TransportPort.of(Integer.parseInt(jp.getText()));
					} catch (IllegalArgumentException e) {
						log.error("Unable to parse destination transport port: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("priority")) {
					try {
						rule.priority = Integer.parseInt(jp.getText());
					} catch (IllegalArgumentException e) {
						log.error("Unable to parse priority: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("action")) {
					if (jp.getText().equalsIgnoreCase("allow") || jp.getText().equalsIgnoreCase("accept")) {
						rule.action = FirewallRule.FirewallAction.ALLOW;
					} else if (jp.getText().equalsIgnoreCase("deny") || jp.getText().equalsIgnoreCase("drop")) {
						rule.action = FirewallRule.FirewallAction.DROP;
					}
				}
			}
		} catch (IOException e) {
			log.error("Unable to parse JSON string: {}", e);
		}

		return rule;
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
