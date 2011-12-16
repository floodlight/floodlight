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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFSwitch;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * Get a list of switches connected to the controller
 * @author readams
 */
public class ControllerSwitchesResource extends ServerResource {
    @Get("json")
    public List<Map<String, String>> retrieve() {
        List<Map<String, String>> switchIds = new ArrayList<Map<String, String>>();        

        IFloodlightProvider floodlightProvider = (IFloodlightProvider)getApplication();
        Map<Long, IOFSwitch> switches = floodlightProvider.getSwitches();

        for (IOFSwitch s: switches.values()) {
            Map<String, String> m = new HashMap<String, String>();
            m.put("dpid", s.getStringId());
            switchIds.add(m);
        }
        return switchIds;
    }
}
