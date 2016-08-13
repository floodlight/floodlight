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

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.web.StatsReply;

import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.types.DatapathId;
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
	public StatsReply retrieve(){

		StatsReply result = new StatsReply();
		Object values = null; // set for error detection in serializer
		String switchIdStr = (String) getRequestAttributes().get(CoreWebRoutable.STR_SWITCH_ID);
		DatapathId switchId;
		String statType = (String) getRequestAttributes().get(CoreWebRoutable.STR_STAT_TYPE);

		IOFSwitchService switchService = (IOFSwitchService) getContext().getAttributes().
				get(IOFSwitchService.class.getCanonicalName());
		
		// prevent input errors and give error to user if bad switch DPID
		try {
			switchId = DatapathId.of(switchIdStr);
		} catch (NumberFormatException | NullPointerException e) { // new Java 7 shorthand...reduces duplicated code in each catch 
			switchId = DatapathId.NONE; // set for error detection in serializer
		}
		
		// stop if the DPID is invalid or is not presently connected
		if (!switchId.equals(DatapathId.NONE) && switchService.getSwitch(switchId) != null) {			
			// at this point, the switch DPID is valid AND exists; what about the OFStatsType?
			switch (statType) {
			case OFStatsTypeStrings.PORT:
				values = getSwitchStatistics(switchId, OFStatsType.PORT);
				result.setStatType(OFStatsType.PORT);
				break;
            case OFStatsTypeStrings.PORT_DESC:
                values = getSwitchStatistics(switchId, OFStatsType.PORT_DESC);
                result.setStatType(OFStatsType.PORT_DESC);
                break;
			case OFStatsTypeStrings.QUEUE:
				values = getSwitchStatistics(switchId, OFStatsType.QUEUE);
				result.setStatType(OFStatsType.QUEUE);
				break;
			case OFStatsTypeStrings.QUEUE_DESC:
                values = getSwitchStatistics(switchId, OFStatsType.QUEUE_DESC);
                result.setStatType(OFStatsType.QUEUE_DESC);
                break;
			case OFStatsTypeStrings.FLOW:
				values = getSwitchStatistics(switchId, OFStatsType.FLOW);
				result.setStatType(OFStatsType.FLOW);
				break;
			case OFStatsTypeStrings.FLOW_LIGHTWEIGHT:
                values = getSwitchStatistics(switchId, OFStatsType.FLOW_LIGHTWEIGHT);
                result.setStatType(OFStatsType.FLOW_LIGHTWEIGHT);
                break;
			case OFStatsTypeStrings.FLOW_MONITOR:
                values = getSwitchStatistics(switchId, OFStatsType.FLOW_MONITOR);
                result.setStatType(OFStatsType.FLOW_MONITOR);
                break;
			case OFStatsTypeStrings.AGGREGATE:
				values = getSwitchStatistics(switchId, OFStatsType.AGGREGATE);
				result.setStatType(OFStatsType.AGGREGATE);
				break;
			case OFStatsTypeStrings.DESC:
				values = getSwitchStatistics(switchId, OFStatsType.DESC);
				result.setStatType(OFStatsType.DESC);
				break;			
			case OFStatsTypeStrings.GROUP:
				values = getSwitchStatistics(switchId, OFStatsType.GROUP);
				result.setStatType(OFStatsType.GROUP);
				break;
			case OFStatsTypeStrings.GROUP_DESC:
				values = getSwitchStatistics(switchId, OFStatsType.GROUP_DESC);
				result.setStatType(OFStatsType.GROUP_DESC);
				break;
			case OFStatsTypeStrings.GROUP_FEATURES:
				values = getSwitchStatistics(switchId, OFStatsType.GROUP_FEATURES);
				result.setStatType(OFStatsType.GROUP_FEATURES);
				break;
			case OFStatsTypeStrings.METER:
				values = getSwitchStatistics(switchId, OFStatsType.METER);
				result.setStatType(OFStatsType.METER);
				break;
			case OFStatsTypeStrings.METER_CONFIG:
				values = getSwitchStatistics(switchId, OFStatsType.METER_CONFIG);
				result.setStatType(OFStatsType.METER_CONFIG);
				break;
			case OFStatsTypeStrings.METER_FEATURES:
				values = getSwitchStatistics(switchId, OFStatsType.METER_FEATURES);
				result.setStatType(OFStatsType.METER_FEATURES);
				break;
			case OFStatsTypeStrings.TABLE:
				values = getSwitchStatistics(switchId, OFStatsType.TABLE);
				result.setStatType(OFStatsType.TABLE);
				break;
			case OFStatsTypeStrings.TABLE_DESC:
                values = getSwitchStatistics(switchId, OFStatsType.TABLE_DESC);
                result.setStatType(OFStatsType.TABLE_DESC);
                break;
			case OFStatsTypeStrings.TABLE_FEATURES:
				values = getSwitchStatistics(switchId, OFStatsType.TABLE_FEATURES);
				result.setStatType(OFStatsType.TABLE_FEATURES);
				break;
			case OFStatsTypeStrings.EXPERIMENTER:
				values = getSwitchFeaturesReply(switchId);
				result.setStatType(OFStatsType.EXPERIMENTER);
				break;
			case OFStatsTypeStrings.BUNDLE_FEATURES:
                values = getSwitchStatistics(switchId, OFStatsType.BUNDLE_FEATURES);
                result.setStatType(OFStatsType.BUNDLE_FEATURES);
                break;
			case OFStatsTypeStrings.CONTROLLER_STATUS:
                values = getSwitchStatistics(switchId, OFStatsType.CONTROLLER_STATUS);
                result.setStatType(OFStatsType.CONTROLLER_STATUS);
                break;  
			case OFStatsTypeStrings.FEATURES:
				values = getSwitchFeaturesReply(switchId);
				result.setStatType(null); // we will assume anything in "values" with a null stattype is "features"
			default:
				log.error("Invalid or unimplemented stat request type {}", statType);
				break;
			}
		} else {
			log.error("Invalid or disconnected switch {}", switchIdStr);
			// if there was an error, the serializer will report it
		}
		
		result.setDatapathId(switchId);
		result.setValues(values); // values can only be a List<OFStatsReply> or an OFFeaturesReply
		// if values is set to null (the default), the serializer will kick back a response to the user via the REST API
		return result;
	}
}
