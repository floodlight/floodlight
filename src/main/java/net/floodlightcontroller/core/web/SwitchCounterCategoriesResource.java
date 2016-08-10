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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.util.HexString;
import org.restlet.resource.Get;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.counter.CounterStore.NetworkLayer;
import net.floodlightcontroller.counter.ICounterStoreService;

/**
 * Get the counter categories for a particular switch
 * @author readams
 */
public class SwitchCounterCategoriesResource extends CounterResourceBase {
    @Get("json")
    public Map<String, Object> retrieve() {
        IFloodlightProviderService floodlightProvider = 
                (IFloodlightProviderService)getContext().getAttributes().
                    get(IFloodlightProviderService.class.getCanonicalName());
        HashMap<String,Object> model = new HashMap<String,Object>();
        
        String switchID = (String) getRequestAttributes().get("switchId");
        String counterName = (String) getRequestAttributes().get("counterName");
        String layer = (String) getRequestAttributes().get("layer");

        Long[] switchDpids;
        if (switchID.equalsIgnoreCase("all")) {
            switchDpids = floodlightProvider.getSwitches().keySet().toArray(new Long[0]);
            for (Long dpid : switchDpids) {
                switchID = HexString.toHexString(dpid);

                getOneSwitchCounterCategoriesJson(model, switchID, counterName, layer);
            }
        } else {
            getOneSwitchCounterCategoriesJson(model, switchID, counterName, layer);
        }
        
        return model;
    }
    
    protected void getOneSwitchCounterCategoriesJson(Map<String, Object> model,
                                                     String switchID,
                                                     String counterName, 
                                                     String layer) {
        String fullCounterName = "";      
        NetworkLayer nl = NetworkLayer.L3;
        
        try {
            counterName = URLDecoder.decode(counterName, "UTF-8");
            layer = URLDecoder.decode(layer, "UTF-8");
            fullCounterName = switchID + ICounterStoreService.TitleDelimitor + counterName;
        } catch (UnsupportedEncodingException e) {
            //Just leave counterTitle undecoded if there is an issue - fail silently
        }

        if (layer.compareToIgnoreCase("4") == 0) {
            nl = NetworkLayer.L4;
        }
        List<String> categories = this.counterStore.getAllCategories(fullCounterName, nl);
        if (categories != null) {
            model.put(fullCounterName + "." + layer, categories);
        }
    }
}
