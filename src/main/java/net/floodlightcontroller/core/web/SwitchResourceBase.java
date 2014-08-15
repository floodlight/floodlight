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

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.internal.IOFSwitchService;

import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.protocol.OFFeaturesRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;

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
    protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId,
                                                     OFStatsType statType) {
        IOFSwitchService switchService =
                (IOFSwitchService) getContext().getAttributes().
                    get(IOFSwitchService.class.getCanonicalName());

        IOFSwitch sw = switchService.getSwitch(switchId);
        ListenableFuture<?> future;
        List<OFStatsReply> values = null;
        if (sw != null) {
        	OFStatsRequest<?> req;
            if (statType == OFStatsType.FLOW) {
            	Match match = sw.getOFFactory().buildMatch().build();
                req = sw.getOFFactory().buildFlowStatsRequest()
                		.setMatch(match)
                		.setOutPort(OFPort.ANY)
                		.setTableId(TableId.ALL)
                		.build();
            } else if (statType == OFStatsType.AGGREGATE) {
            	Match match = sw.getOFFactory().buildMatch().build();
                req = sw.getOFFactory().buildAggregateStatsRequest()
                		.setMatch(match)
                		.setOutPort(OFPort.ANY)
                		.setTableId(TableId.ALL)
                		.build();
            } else if (statType == OFStatsType.PORT) {
                req = sw.getOFFactory().buildPortStatsRequest()
                		.setPortNo(OFPort.ANY)
                		.build();
            } else if (statType == OFStatsType.QUEUE) {
            	req = sw.getOFFactory().buildQueueStatsRequest()
                		.setPortNo(OFPort.ANY)
                		.setQueueId(UnsignedLong.MAX_VALUE.longValue())
                		.build();
            } else if (statType == OFStatsType.DESC ||
                       statType == OFStatsType.TABLE) {
                // pass - nothing todo besides set the type above
            	req = sw.getOFFactory().buildDescStatsRequest()
            			.build();
            } else {
            	//TODO @Ryan what to do about no matches in the if...elseif statements?
            	req = sw.getOFFactory().buildDescStatsRequest().build();
            }
            try {
                future = sw.writeStatsRequest(req);
                values = (List<OFStatsReply>) future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
        }
        return values;
    }

    protected List<OFStatsReply> getSwitchStatistics(String switchId, OFStatsType statType) {
        return getSwitchStatistics(DatapathId.of(switchId), statType);
    }

    protected OFFeaturesReply getSwitchFeaturesReply(DatapathId switchId) {
        IOFSwitchService switchService =
                (IOFSwitchService) getContext().getAttributes().
                get(IOFSwitchService.class.getCanonicalName());

        IOFSwitch sw = switchService.getSwitch(switchId);
        Future<OFFeaturesReply> future;
        OFFeaturesReply featuresReply = null;
        //TODO @Ryan The only thing to set in an OFFeaturesRequest is the XID. I'm not sure
        // if it matters what I set it to. Will it have a default value of the next available?
        OFFeaturesRequest featuresRequest = sw.getOFFactory().buildFeaturesRequest().build();
        if (sw != null) {
            try {
                future = sw.writeRequest(featuresRequest);
                featuresReply = future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure getting features reply from switch" + sw, e);
            }
        }

        return featuresReply;
    }

    protected OFFeaturesReply getSwitchFeaturesReply(String switchId) {
        return getSwitchFeaturesReply(DatapathId.of(switchId));
    }

}