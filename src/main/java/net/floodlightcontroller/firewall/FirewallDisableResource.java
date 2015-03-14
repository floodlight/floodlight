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

import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.data.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * Rest API endpoint to disable the firewall
 */
public class FirewallDisableResource extends FirewallResourceBase {
    private static final Logger log = LoggerFactory.getLogger(FirewallDisableResource.class);

    @Get("json")
    public Object handleRequest() {
        log.warn("call to FirewallDisableResource with method GET is not allowed. Use PUT: ");
        
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
	return "{\"status\" : \"failure\", \"details\" : \"Use PUT to disable firewall\"}";
    }

    @Put("json")
    public Object handlePut() {
        IFirewallService firewall = getFirewallService();

	firewall.enableFirewall(false);

        setStatus(Status.SUCCESS_OK);

	return "{\"status\" : \"success\", \"details\" : \"firewall stopped\"}";
    }
}
