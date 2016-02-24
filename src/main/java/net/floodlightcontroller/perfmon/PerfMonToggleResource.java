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

package net.floodlightcontroller.perfmon;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class PerfMonToggleResource extends ServerResource {
    
    @Get("json")
    public String retrieve() {
        IPktInProcessingTimeService pktinProcTime = 
                (IPktInProcessingTimeService)getContext().getAttributes().
                    get(IPktInProcessingTimeService.class.getCanonicalName());
        
        String param = ((String)getRequestAttributes().get("perfmonstate")).toLowerCase();
        if (param.equals("reset")) {
        	// We cannot reset something that is disabled, so enable it first.
        	if(!pktinProcTime.isEnabled()){
        		pktinProcTime.setEnabled(true);
        	}
            pktinProcTime.getCtb().reset();
        } else {
            if (param.equals("enable") || param.equals("true")) {
                pktinProcTime.setEnabled(true);
            } else if (param.equals("disable") || param.equals("false")) {
                pktinProcTime.setEnabled(false);
            }
        }
        setStatus(Status.SUCCESS_OK, "OK");
        return "{ \"enabled\" : " + pktinProcTime.isEnabled() + " }";
    }
}
