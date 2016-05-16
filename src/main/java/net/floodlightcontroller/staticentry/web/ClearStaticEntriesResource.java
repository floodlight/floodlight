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

import net.floodlightcontroller.core.web.ControllerSwitchesResource;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearStaticEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ClearStaticEntriesResource.class);
    
    @Get("json")
    public String ClearStaticEntries() {
        IStaticEntryPusherService sfpService =
                (IStaticEntryPusherService)getContext().getAttributes().
                    get(IStaticEntryPusherService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("switch");
        if (log.isDebugEnabled())
            log.debug("Clearing all static flow/group entires for switch: " + param);
        
        if (param.toLowerCase().equals("all")) {
            sfpService.deleteAllEntries();
            return "{\"status\":\"Deleted all flows/groups.\"}";
        } else {
            try {
                sfpService.deleteEntriesForSwitch(DatapathId.of(param));
                return "{\"status\":\"Deleted all flows/groups for switch " + param + ".\"}";
            } catch (NumberFormatException e){
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, 
                          ControllerSwitchesResource.DPID_ERROR);
                return "'{\"status\":\"Could not delete flows/groups requested! See controller log for details.\"}'";
            }
        }
    }
}
