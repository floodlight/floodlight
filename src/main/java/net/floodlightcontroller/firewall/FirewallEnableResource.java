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

import org.restlet.resource.Post;
import org.restlet.resource.Get;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * Rest API endpoint to enable the firewall
 *
 * Contrary to best practices it changes the state on both GET and POST
 * We should disable this behavior for GET as soon as we can be sure
 * that no clients depend on this behavior.
 */
public class FirewallEnableResource extends FirewallResourceBase {
    private static final Logger log = LoggerFactory.getLogger(FirewallEnableResource.class);

    @Get("json")
    public Object handleRequest() {
	log.warn("REST call to FirewallEnableResource with method GET is depreciated.  Use POST: ");
	
	return handlePost();
    }

    @Post("json")
    public Object handlePost() {
        IFirewallService firewall = getFirewallService();

	firewall.enableFirewall(true);

	return "{\"status\" : \"success\", \"details\" : \"firewall running\"}";
    }
}

