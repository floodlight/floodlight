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

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.web.ControllerSwitchesResource;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListStaticEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticEntriesResource.class);
    
    @Get("json")
    public SFPEntryMap ListStaticFlowEntries() {
        IStaticEntryPusherService sfpService =
                (IStaticEntryPusherService)getContext().getAttributes().
                    get(IStaticEntryPusherService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("switch");
        if (log.isDebugEnabled())
            log.debug("Listing all static flow/group entires for switch: " + param);
        
        if (param.toLowerCase().equals("all")) {
            return new SFPEntryMap(sfpService.getEntries());
        } else {
            try {
                Map<String, Map<String, OFMessage>> retMap = new HashMap<String, Map<String, OFMessage>>();
                retMap.put(param, sfpService.getEntries(DatapathId.of(param)));
                return new SFPEntryMap(retMap);
                
            } catch (NumberFormatException e){
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, ControllerSwitchesResource.DPID_ERROR);
            }
        }
        return null;
    }
}
