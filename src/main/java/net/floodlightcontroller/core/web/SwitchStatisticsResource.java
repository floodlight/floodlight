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
import java.util.Map;

import org.openflow.protocol.statistics.OFStatisticsType;
import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Return switch statistics information for specific switches
 * @author readams
 */
public class SwitchStatisticsResource extends SwitchResourceBase {
    protected static Logger log = 
        LoggerFactory.getLogger(SwitchStatisticsResource.class);

    @Get("json")
    public Map<String, Object> retrieve() {
        HashMap<String,Object> result = new HashMap<String,Object>();
        Object values = null;
        
        String switchId = (String) getRequestAttributes().get("switchId");
        String statType = (String) getRequestAttributes().get("statType");
        
        if (statType.equals("port")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.PORT);
        } else if (statType.equals("queue")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.QUEUE);
        } else if (statType.equals("flow")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.FLOW);
        } else if (statType.equals("aggregate")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.AGGREGATE);
        } else if (statType.equals("desc")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.DESC);
        } else if (statType.equals("table")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.TABLE);
        } else if (statType.equals("features")) {
            values = getSwitchFeaturesReply(switchId);
        }

        result.put(switchId, values);
        return result;
    }
}
