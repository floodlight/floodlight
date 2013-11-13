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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Return the performance monitoring data for the get rest api call
 * @author subrata
 */
public class PerfMonDataResource extends ServerResource {
    protected static Logger logger = LoggerFactory.getLogger(PerfMonDataResource.class);  
    
    @Get("json")
    public CumulativeTimeBucket handleApiQuery() {        
        IPktInProcessingTimeService pktinProcTime = 
            (IPktInProcessingTimeService)getContext().getAttributes().
                get(IPktInProcessingTimeService.class.getCanonicalName());
        
        setStatus(Status.SUCCESS_OK, "OK");
        // If the user is requesting this they must think that it is enabled, 
        // so lets enable it to prevent from erroring out
        if (!pktinProcTime.isEnabled()){
        	pktinProcTime.setEnabled(true);
        	logger.warn("Requesting performance monitor data when performance monitor is disabled. Turning it on");
        }
        // Allocate output object
        if (pktinProcTime.isEnabled()) {
            CumulativeTimeBucket ctb = pktinProcTime.getCtb();
            ctb.computeAverages();
            return ctb;
        }
        
        return null;
    }
}