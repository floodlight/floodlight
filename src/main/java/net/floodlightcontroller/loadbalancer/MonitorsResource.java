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

package net.floodlightcontroller.loadbalancer;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.floodlightcontroller.packet.IPv4;

import org.projectfloodlight.openflow.types.IpProtocol;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorsResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(MonitorsResource.class);
	private static final int BAD_REQUEST = 400;

	@Get("json")
	public Collection <LBMonitor> retrieve() {
		ILoadBalancerService lbs =
				(ILoadBalancerService)getContext().getAttributes().
				get(ILoadBalancerService.class.getCanonicalName());

		String monitorId = (String) getRequestAttributes().get("monitor");
		if (monitorId!=null)
			return lbs.listMonitor(monitorId);
		else
			return lbs.listMonitors();
	}

	@Put
	@Post
	public LBMonitor createMonitor(String postData) {

		LBMonitor monitor=null;
		try {
			monitor=jsonToMonitor(postData);
		} catch (IOException e) {
			log.error("Could not parse JSON {}", e.getMessage());
			throw new ResourceException(BAD_REQUEST); // Sends HTTP error message with code 400 (Bad Request).
		}

		ILoadBalancerService lbs =
				(ILoadBalancerService)getContext().getAttributes().
				get(ILoadBalancerService.class.getCanonicalName());

		String monitorId = (String) getRequestAttributes().get("monitor");
		if (monitorId != null)
			return lbs.updateMonitor(monitor);
		else

			return lbs.createMonitor(monitor);
	}


	@Delete
	public String removeMonitor() {

		String monitorId = (String) getRequestAttributes().get("monitor");

		ILoadBalancerService lbs =
				(ILoadBalancerService)getContext().getAttributes().
				get(ILoadBalancerService.class.getCanonicalName());

		int status = lbs.removeMonitor(monitorId);
		if(status == -1){
			return "{\"status\" : \"Error: Monitor cannot be deleted!\"}";
		} else{
			return "{\"status\" : \"200 OK!\"}";
		}

	}


	protected LBMonitor jsonToMonitor(String json) throws IOException {
		if (json==null) return null;

		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		LBMonitor monitor = new LBMonitor();

		try {
			jp = f.createParser(json);
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
			if (n.equals("id")) {
				monitor.id = jp.getText();
				continue;
			} 
			if (n.equals("name")) {
				monitor.name = jp.getText();
				continue;
			}
			if (n.equals("type")) {
				String tmp = jp.getText();
				if (tmp.equalsIgnoreCase("TCP")) {
					monitor.type = (byte) IpProtocol.TCP.getIpProtocolNumber();
				} else if (tmp.equalsIgnoreCase("UDP")) {
					monitor.type = (byte) IpProtocol.UDP.getIpProtocolNumber();
				} else if (tmp.equalsIgnoreCase("ICMP")) {
					monitor.type = (byte) IpProtocol.ICMP.getIpProtocolNumber();
				} 
				continue;
			}
			if (n.equals("delay")) {
				monitor.delay = Short.parseShort(jp.getText());
				continue;
			}
			if (n.equals("timeout")) {
				monitor.timeout = Short.parseShort(jp.getText());
				continue;
			}
			if (n.equals("attempts_before_deactivation")) {
				monitor.attemptsBeforeDeactivation = Short.parseShort(jp.getText());
				continue;
			}
			if (n.equals("network_id")) {
				monitor.netId = jp.getText();
				continue;
			}
			if (n.equals("pool_id")) {
				monitor.poolId = jp.getText();
				continue;
			}
			if (n.equals("address")) {
				monitor.address = IPv4.toIPv4Address(jp.getText());
				continue;
			}
			if (n.equals("protocol")) {
				monitor.protocol = Byte.parseByte(jp.getText());
				continue;
			}
			if (n.equals("port")) {
				monitor.port = Short.parseShort(jp.getText());
				continue;
			}
			if (n.equals("admin_state")) {
				monitor.adminState = Short.parseShort(jp.getText());
				continue;
			}
			if (n.equals("status")) {
				monitor.status = Short.parseShort(jp.getText());
				continue;
			}

			log.warn("Unrecognized field {} in " +
					"parsing Monitors", 
					jp.getText());
		}
		jp.close();
		return monitor;
	}
}