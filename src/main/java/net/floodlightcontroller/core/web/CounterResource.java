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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.debugcounter.DebugCounterResource;

import org.restlet.resource.Get;

public class CounterResource extends CounterResourceBase {
    @Get("json")
    public Map<String, Object> retrieve() {
        String counterTitle = (String) getRequestAttributes().get(CoreWebRoutable.STR_CTR_TITLE);
        String counterModule = (String) getRequestAttributes().get(CoreWebRoutable.STR_CTR_MODULE);
        Map<String, Object> model = new HashMap<String, Object>();
        long dc;
        if (counterModule.equalsIgnoreCase(CoreWebRoutable.STR_ALL)) { // get all modules' counters
            List<DebugCounterResource> counters = this.debugCounterService.getAllCounterValues();
            if (counters != null) {
                Iterator<DebugCounterResource> it = counters.iterator();
                while (it.hasNext()) {
                    DebugCounterResource dcr = it.next();
                    String counterName = dcr.getCounterHierarchy();
                    dc = dcr.getCounterValue();
                    model.put(counterName, dc);
                }   
            }   
        } else if (counterTitle.equalsIgnoreCase(CoreWebRoutable.STR_ALL)) { // get all counters for a specifc module
            List<DebugCounterResource> counters = this.debugCounterService.getModuleCounterValues(counterModule);
            if (counters != null) {
                Iterator<DebugCounterResource> it = counters.iterator();
                while (it.hasNext()) {
                    DebugCounterResource dcr = it.next();
                    String counterName = dcr.getCounterHierarchy();
                    dc = dcr.getCounterValue();
                    model.put(counterName, dc);
                }   
            }   
        } else { // get a specific counter (or subset of counters) for a specific module
            List<DebugCounterResource> counters = this.debugCounterService.getCounterHierarchy(counterModule, counterTitle);
            if (counters != null) {
                Iterator<DebugCounterResource> it = counters.iterator();
                while (it.hasNext()) {
                    DebugCounterResource dcr = it.next();
                    String counterName = dcr.getCounterHierarchy();
                    dc = dcr.getCounterValue();
                    model.put(counterName, dc);
                }   
            }   
        }
        return model;
    }
}
