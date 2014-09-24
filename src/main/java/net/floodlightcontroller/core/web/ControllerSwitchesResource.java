/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.core.web;

import java.util.Set;

import net.floodlightcontroller.core.internal.IOFSwitchService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
/**
 * Get a list of switches connected to the controller
 * @author readams
 */
public class ControllerSwitchesResource extends ServerResource {
	
	public static final String DPID_ERROR = "Invalid switch DPID string. Must be a 64-bit value in the form 00:11:22:33:44:55:66:77.";
	
    @Get("json")
    public Set<DatapathId> retrieve(){
        IOFSwitchService switchService = 
            (IOFSwitchService) getContext().getAttributes().
                get(IOFSwitchService.class.getCanonicalName());
        return switchService.getAllSwitchDpids();
    }
}
