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
import java.util.List;
import net.floodlightcontroller.core.web.serializers.StatsReplySerializer;
import net.floodlightcontroller.core.web.StatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
/**
 * Return switch statistics information for specific switches
 * @author readams
 */
public class SwitchStatisticsResource extends SwitchResourceBase {
    protected static Logger log = 
        LoggerFactory.getLogger(SwitchStatisticsResource.class);

    @Get("json")
    public StatsReply retrieve(){
        StatsReply result = new StatsReply();
        Object values = null;
        String switchId = (String) getRequestAttributes().get("switchId");
        String statType = (String) getRequestAttributes().get("statType");
        
        if (statType.equals("port")) {
            values = getSwitchStatistics(DatapathId.of(switchId), OFStatsType.PORT);
        } else if (statType.equals("queue")) {
            values = getSwitchStatistics(DatapathId.of(switchId), OFStatsType.QUEUE);
        } else if (statType.equals("flow")) {
            values = getSwitchStatistics(DatapathId.of(switchId), OFStatsType.FLOW);
        } else if (statType.equals("aggregate")) {
            values = getSwitchStatistics(DatapathId.of(switchId), OFStatsType.AGGREGATE);
        } else if (statType.equals("desc")) {
            values = getSwitchStatistics(DatapathId.of(switchId), OFStatsType.DESC);
        } else if (statType.equals("table")) {
            values = getSwitchStatistics(DatapathId.of(switchId), OFStatsType.TABLE);
        } else if (statType.equals("features")) {
            values = getSwitchFeaturesReply(switchId);
        }
        
        result.setDatapathId(switchId);
        result.setValues(values);
        result.setStatType(statType);
        return result;
    }
}
