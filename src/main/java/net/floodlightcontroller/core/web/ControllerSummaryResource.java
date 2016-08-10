/**
*    Copyright 2012, Big Switch Networks, Inc. 
*    Originally created by Shudong Zhou, Big Switch Networks
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

import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.core.IFloodlightProviderService;

/**
 * Get summary counters registered by all modules
 * @author shudongz
 */
public class ControllerSummaryResource extends ServerResource {
    @Get("json")
    public Map<String, Object> retrieve() {
        IFloodlightProviderService floodlightProvider = 
            (IFloodlightProviderService)getContext().getAttributes().
                get(IFloodlightProviderService.class.getCanonicalName());
        return floodlightProvider.getControllerInfo("summary");
    }

}
