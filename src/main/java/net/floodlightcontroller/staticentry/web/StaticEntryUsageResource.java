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

package net.floodlightcontroller.staticentry.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.web.ControllerSwitchesResource;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.protocol.ver10.OFFactoryVer10;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticEntryUsageResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(StaticEntryUsageResource.class);

	private static String usage = null;
	private static final String TYPE_NONE = "<none>";
	private static final String TYPE_NUMBER = "<number>";
	private static final String TYPE_RESERVED_PORT = "<reserved_port>";
	private static final String TYPE_MACADDR = "<MAC address>";
	private static final String TYPE_IPV4ADDR = "<IPv4 address>";
	private static final String TYPE_IPV4ADDR_MASK = "<IPv4 address[/mask]>";
	private static final String TYPE_IPV6ADDR = "<IPv6 address>";
	private static final String TYPE_IPV6ADDR_MASK = "<IPv6 address[/mask]>";
	
	private static final Match.Builder b10 = OFFactories.getFactory(OFVersion.OF_10).buildMatch();
	private static final Match.Builder b11 = OFFactories.getFactory(OFVersion.OF_11).buildMatch();
	private static final Match.Builder b12 = OFFactories.getFactory(OFVersion.OF_12).buildMatch();
	private static final Match.Builder b13 = OFFactories.getFactory(OFVersion.OF_13).buildMatch();
	private static final Match.Builder b14 = OFFactories.getFactory(OFVersion.OF_14).buildMatch();
	private static final Match.Builder b15 = OFFactories.getFactory(OFVersion.OF_15).buildMatch();

	private static final String NOTE_HEX_OR_DEC = "Can be hexadecimal (with leading 0x) or decimal.";


	private class SFPMatch {
		String key =  "";
		Set<String> value = new HashSet<String>();
		Set<String> notes = new HashSet<String>();
		Map<String, String> prereqs = null;
		Set<OFVersion> ofVersions = null;
		
		//TODO hashcode, equals, toString
	}

	private static void setSupportedOFVersions(Set<OFVersion> supportedOFVersions, MatchField<?> mf) {
		if (b10.supports(mf)) {
			supportedOFVersions.add(OFVersion.OF_10);
		}
		if (b11.supports(mf)) {
			supportedOFVersions.add(OFVersion.OF_11);
		}
		if (b12.supports(mf)) {
			supportedOFVersions.add(OFVersion.OF_12);
		}
		if (b13.supports(mf)) {
			supportedOFVersions.add(OFVersion.OF_13);
		}
		if (b14.supports(mf)) {
			supportedOFVersions.add(OFVersion.OF_14);
		}
		//TODO add in other versions as they become available
	}
	
	@Get("json")
	public Object getStaticFlowPusherUsage() {
		IStaticEntryPusherService sfpService =
				(IStaticEntryPusherService)getContext().getAttributes().
				get(IStaticEntryPusherService.class.getCanonicalName());

		Map<String, Set<? extends Object>> outer = new HashMap<String, Set<? extends Object>>();
		
		Set<SFPMatch> matches = new HashSet<SFPMatch>();
		outer.put("matches", matches);
		
		
		for (MatchFields mf : MatchFields.values()) {
			SFPMatch m = new SFPMatch();
			switch (mf) {
			case ARP_OP:
				m.key = MatchUtils.STR_ARP_OPCODE;
				m.value.add(TYPE_NUMBER);
				m.notes.add(NOTE_HEX_OR_DEC);
				setSupportedOFVersions(m.ofVersions, MatchField.ARP_OP);

				break;
			case ARP_SHA:
				break;
			case ARP_SPA:
				break;
			case ARP_THA:
				break;
			case ARP_TPA:
				break;
			case ETH_DST:
				break;
			case ETH_SRC:
				break;
			case ETH_TYPE:
				break;
			case ICMPV4_CODE:
				break;
			case ICMPV4_TYPE:
				break;
			case ICMPV6_CODE:
				break;
			case ICMPV6_TYPE:
				break;
			case IN_PORT:
				break;
			case IPV4_DST:
				break;
			case IPV4_SRC:
				break;
			case IPV6_DST:
				break;
			case IPV6_EXTHDR:
				break;
			case IPV6_FLABEL:
				break;
			case IPV6_ND_SLL:
				break;
			case IPV6_ND_TARGET:
				break;
			case IPV6_ND_TLL:
				break;
			case IPV6_SRC:
				break;
			case IP_DSCP:
				break;
			case IP_ECN:
				break;
			case IP_PROTO:
				break;
			case METADATA:
				break;
			case MPLS_BOS:
				break;
			case MPLS_LABEL:
				break;
			case MPLS_TC:
				break;
			case PBB_UCA:
				break;
			case SCTP_DST:
				break;
			case SCTP_SRC:
				break;
			case TCP_DST:
				break;
			case TCP_SRC:
				break;
			case TUNNEL_ID:
				break;
			case TUNNEL_IPV4_DST:
				break;
			case TUNNEL_IPV4_SRC:
				break;
			case UDP_DST:
				break;
			case UDP_SRC:
				break;
			case VLAN_PCP:
				break;
			case VLAN_VID:
				break;
			default:
				log.debug("Skipped match {}", mf);
				break;
			}
		}

		return usage == null ? "" : usage;
	}
}
