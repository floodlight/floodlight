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
import java.util.Map.Entry;

import net.floodlightcontroller.debugcounter.DebugCounterResource;
import net.floodlightcontroller.debugcounter.IDebugCounter;

import org.restlet.resource.Get;

public class CounterResource extends CounterResourceBase {
    @Get("json")
    public Map<String, Object> retrieve() {
        String counterTitle = 
            (String) getRequestAttributes().get("counterTitle");
        Map<String, Object> model = new HashMap<String,Object>();
        long dc;
        if (counterTitle.equalsIgnoreCase("all")) {
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
        } else {
            List<DebugCounterResource> counter = this.debugCounterService.getCounterHierarchy(???, counterTitle));
            long v = 0;
            if (counter != null) {
                v = counter.getCounterValue();
            } else {
                v = new CounterValue(CounterValue.CounterType.LONG);
            }   

            if (CounterValue.CounterType.LONG == v.getType()) {
                model.put(counterTitle, v.getLong());
            } else if (v.getType() == CounterValue.CounterType.DOUBLE) {
                model.put(counterTitle, v.getDouble());
            }   
        }
        return model;
    }
}
