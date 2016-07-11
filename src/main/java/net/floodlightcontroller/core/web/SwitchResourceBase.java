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
import net.floodlightcontroller.core.internal.IOFSwitchService;

import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.protocol.OFFeaturesRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFVersion;
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

    /**
     * Use for requests that originate from the REST server that use their context to get a
     * reference to the switch service.
     * @param switchId
     * @param statType
     * @return
     */
    @SuppressWarnings("unchecked")
    protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId,
            OFStatsType statType) {
        IOFSwitchService switchService = (IOFSwitchService) getContext().getAttributes().get(IOFSwitchService.class.getCanonicalName());

        IOFSwitch sw = switchService.getSwitch(switchId);
        ListenableFuture<?> future;
        List<OFStatsReply> values = null;
        Match match;
        if (sw != null) {
            OFStatsRequest<?> req = null;
            switch (statType) {
            case FLOW:
                match = sw.getOFFactory().buildMatch().build();
                req = sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) == 0 ? 
                        sw.getOFFactory().buildFlowStatsRequest()
                        .setMatch(match)
                        .setOutPort(OFPort.ANY)
                        .setTableId(TableId.ALL)
                        .build() :
                            sw.getOFFactory().buildFlowStatsRequest()
                            .setMatch(match)
                            .setOutPort(OFPort.ANY)
                            .setTableId(TableId.ALL)
                            .setOutGroup(OFGroup.ANY)
                            .build();
                break;
            case AGGREGATE:
                match = sw.getOFFactory().buildMatch().build();
                req = sw.getOFFactory().buildAggregateStatsRequest()
                        .setMatch(match)
                        .setOutPort(OFPort.ANY)
                        .setTableId(TableId.ALL)
                        .build();
                break;
            case PORT:
                req = sw.getOFFactory().buildPortStatsRequest()
                .setPortNo(OFPort.ANY)
                .build();
                break;
            case QUEUE:
                req = sw.getOFFactory().buildQueueStatsRequest()
                .setPortNo(OFPort.ANY)
                .setQueueId(UnsignedLong.MAX_VALUE.longValue())
                .build();
                break;
            case DESC:
                // pass - nothing todo besides set the type above
                req = sw.getOFFactory().buildDescStatsRequest()
                .build();
                break;
            case GROUP:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildGroupStatsRequest()				
                            .build();
                }
                break;
            case METER:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildMeterStatsRequest()
                            .setMeterId(OFMeterSerializerVer13.ALL_VAL)
                            .build();
                }
                break;
            case GROUP_DESC:			
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildGroupDescStatsRequest()			
                            .build();
                }
                break;
            case GROUP_FEATURES:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
                            .build();
                }
                break;
            case METER_CONFIG:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildMeterConfigStatsRequest()
                            .setMeterId(0xffFFffFF)
                            .build();
                }
                break;
            case METER_FEATURES:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
                            .build();
                }
                break;
            case TABLE:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildTableStatsRequest()
                            .build();
                }
                break;
            case TABLE_FEATURES:	
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildTableFeaturesStatsRequest()
                            .build();		
                }
                break;
            case PORT_DESC:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildPortDescStatsRequest()
                            .build();
                }
                break;
            case EXPERIMENTER:		
                log.error("Stats Request Type {} not implemented yet", statType.name());
                break;
            case BUNDLE_FEATURES:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0) {
                    req = sw.getOFFactory().buildBundleFeaturesStatsRequest()
                            .build();
                }
                break;
            case CONTROLLER_STATUS:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0) {
                    req = sw.getOFFactory().buildControllerStatusStatsRequest()
                            .build();
                }
                break;
            case FLOW_LIGHTWEIGHT:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0) {	
                    match = sw.getOFFactory().buildMatch().build();
                    req = sw.getOFFactory().buildFlowLightweightStatsRequest()
                            .setMatch(match)
                            .setOutPort(OFPort.ANY)
                            .setTableId(TableId.ALL)
                            .build();
                }
                break;
            case FLOW_MONITOR:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_14) >= 0) {
                    req = sw.getOFFactory().buildFlowMonitorRequest()
                            .build();
                }
                break;
            case QUEUE_DESC:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_14) >= 0) {
                    req = sw.getOFFactory().buildQueueDescStatsRequest()
                            .setPortNo(OFPort.ANY)
                            .setQueueId(0xffFFffFF) /* all queues */
                            .build();
                }
                break;
            case TABLE_DESC:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_14) >= 0) {
                    req = sw.getOFFactory().buildTableDescStatsRequest()
                            .build();
                }
                break;
                /* omit a default so we will know (via warning) if we miss one in the future */
            }

            try {
                if (req != null) {
                    future = sw.writeStatsRequest(req);
                    values = (List<OFStatsReply>) future.get(10, TimeUnit.SECONDS);
                }
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
