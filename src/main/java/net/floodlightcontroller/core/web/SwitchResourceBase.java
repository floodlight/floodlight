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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for server resources related to switches
 * @author readams
 *
 */
public class SwitchResourceBase extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(SwitchResourceBase.class);
    
    public enum REQUESTTYPE {
        OFSTATS,
        OFFEATURES
    }
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
    }
    
    @LogMessageDoc(level="ERROR",
                   message="Failure retrieving statistics from switch {switch}",
                   explanation="An error occurred while retrieving statistics" +
                   		"from the switch",
                   recommendation=LogMessageDoc.CHECK_SWITCH + " " +
                   		LogMessageDoc.GENERIC_ACTION)
    protected List<OFStatistics> getSwitchStatistics(long switchId, 
                                                     OFStatisticsType statType) {
        IFloodlightProviderService floodlightProvider = 
                (IFloodlightProviderService)getContext().getAttributes().
                    get(IFloodlightProviderService.class.getCanonicalName());
        
        IOFSwitch sw = floodlightProvider.getSwitches().get(switchId);
        Future<List<OFStatistics>> future;
        List<OFStatistics> values = null;
        if (sw != null) {
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(statType);
            int requestLength = req.getLengthU();
            if (statType == OFStatisticsType.FLOW) {
                OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
                OFMatch match = new OFMatch();
                match.setWildcards(0xffffffff);
                specificReq.setMatch(match);
                specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
                specificReq.setTableId((byte) 0xff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.AGGREGATE) {
                OFAggregateStatisticsRequest specificReq = new OFAggregateStatisticsRequest();
                OFMatch match = new OFMatch();
                match.setWildcards(0xffffffff);
                specificReq.setMatch(match);
                specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
                specificReq.setTableId((byte) 0xff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.PORT) {
                OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
                specificReq.setPortNumber((short)OFPort.OFPP_NONE.getValue());
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.QUEUE) {
                OFQueueStatisticsRequest specificReq = new OFQueueStatisticsRequest();
                specificReq.setPortNumber((short)OFPort.OFPP_ALL.getValue());
                // LOOK! openflowj does not define OFPQ_ALL! pulled this from openflow.h
                // note that I haven't seen this work yet though...
                specificReq.setQueueId(0xffffffff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.DESC ||
                       statType == OFStatisticsType.TABLE) {
                // pass - nothing todo besides set the type above
            }
            req.setLengthU(requestLength);
            try {
                future = sw.getStatistics(req);
                values = future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
        }
        return values;
    }

    protected List<OFStatistics> getSwitchStatistics(String switchId, OFStatisticsType statType) {
        return getSwitchStatistics(HexString.toLong(switchId), statType);
    }
    
    protected OFFeaturesReply getSwitchFeaturesReply(long switchId) {
        IFloodlightProviderService floodlightProvider = 
                (IFloodlightProviderService)getContext().getAttributes().
                get(IFloodlightProviderService.class.getCanonicalName());

        IOFSwitch sw = floodlightProvider.getSwitches().get(switchId);
        Future<OFFeaturesReply> future;
        OFFeaturesReply featuresReply = null;
        if (sw != null) {
            try {
                future = sw.querySwitchFeaturesReply();
                featuresReply = future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure getting features reply from switch" + sw, e);
            }
        }

        return featuresReply;
    }

    protected OFFeaturesReply getSwitchFeaturesReply(String switchId) {
        return getSwitchFeaturesReply(HexString.toLong(switchId));
    }

}
