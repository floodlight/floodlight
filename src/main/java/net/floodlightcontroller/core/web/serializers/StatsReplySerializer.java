/**
 *    Copyright 2011,2012 Big Switch Networks, Inc.
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

package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.core.web.OFStatsTypeStrings;
import net.floodlightcontroller.core.web.StatsReply;
import net.floodlightcontroller.util.OXMUtils;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFBucketCounter;
import org.projectfloodlight.openflow.protocol.OFBundleFeatureFlags;
import org.projectfloodlight.openflow.protocol.OFBundleFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFControllerStatusEntry;
import org.projectfloodlight.openflow.protocol.OFControllerStatusStatsReply;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowLightweightStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowLightweightStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowMonitorReply;
import org.projectfloodlight.openflow.protocol.OFFlowMonitorReplyEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupCapabilities;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterBandStats;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterFeatures;
import org.projectfloodlight.openflow.protocol.OFMeterFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterStats;
import org.projectfloodlight.openflow.protocol.OFMeterStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortDescProp;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFQueueDesc;
import org.projectfloodlight.openflow.protocol.OFQueueDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFQueueStatsEntry;
import org.projectfloodlight.openflow.protocol.OFQueueStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsProp;
import org.projectfloodlight.openflow.protocol.OFPortStatsPropEthernet;
import org.projectfloodlight.openflow.protocol.OFPortStatsPropExperimenter;
import org.projectfloodlight.openflow.protocol.OFPortStatsPropOptical;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFStatsReplyFlags;
import org.projectfloodlight.openflow.protocol.OFTableConfig;
import org.projectfloodlight.openflow.protocol.OFTableDesc;
import org.projectfloodlight.openflow.protocol.OFTableDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyActions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyActionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyCopyfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyCopyfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplySetfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplySetfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenter;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenterMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropMatch;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropNextTables;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropNextTablesMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropOxmValues;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropTableSyncFrom;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWildcards;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteActions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteActionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteCopyfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteCopyfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteSetfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteSetfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFTableStatsEntry;
import org.projectfloodlight.openflow.protocol.OFTableStatsReply;
import org.projectfloodlight.openflow.protocol.actionid.OFActionId;
import org.projectfloodlight.openflow.protocol.instructionid.OFInstructionId;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDscpRemark;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandExperimenter;
import org.projectfloodlight.openflow.protocol.stat.StatField;
import org.projectfloodlight.openflow.protocol.ver15.OFMeterBandTypeSerializerVer15;
import org.projectfloodlight.openflow.protocol.ver15.OFPortDescPropTypeSerializerVer15;
import org.projectfloodlight.openflow.protocol.ver15.OFPortStatsPropTypeSerializerVer15;
import org.projectfloodlight.openflow.protocol.ver15.OFTableFeaturePropTypeSerializerVer15;
import org.projectfloodlight.openflow.protocol.OFAggregateStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.U8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialize any OFStatsReply or OFFeaturesReply in JSON
 * wrapped by a StatsReply object.
 * 
 * Use automatically by Jackson via JsonSerialize(using=StatsReplySerializer.class),
 * or use the static functions within this class to serializer a specific OFStatType
 * within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class StatsReplySerializer extends JsonSerializer<StatsReply> {
    protected static Logger logger = LoggerFactory.getLogger(StatsReplySerializer.class);
    @SuppressWarnings("unchecked")
    @Override
    public void serialize(StatsReply reply, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
        // Return a nice error to user if the request we're about to serialize was bad
        if (reply.getValues() == null) {
            jGen.writeStartObject();
            jGen.writeStringField("   ", "An error has occurred while proccesing your request,");
            jGen.writeStringField("  *", "which might be due to one or more of the following:");
            jGen.writeStringField(" * ", "-- An invalid DPID and/or stats/features request.");
            jGen.writeStringField(" **", "-- The switch is not connected to the controller.");
            jGen.writeStringField("*  ", "-- The request specified is not supported by the switch's OpenFlow version.");
            jGen.writeArrayFieldStart("Valid statistics and features are");
            for (Field f : OFStatsTypeStrings.class.getFields()) {
                try {
                    jGen.writeString((String) f.get(null)); /* expect all static String types */
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    logger.warn("Caught unexpected, possible non-string field in OFStatsTypeStrings class");
                }
            }
            jGen.writeEndArray();
            jGen.writeEndObject(); 
            return;
        }

        jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true); // IMHO this just looks nicer and is easier to read if everything is quoted
        jGen.writeStartObject();

        if (reply.getStatType() == null) { // must be an OFFeaturesReply. getValues() was already checked for null above.
            serializeFeaturesReply((OFFeaturesReply) reply.getValues(), jGen);
        } else {
            switch (reply.getStatType()) {
            case PORT:
                serializePortReply((List<OFPortStatsReply>) reply.getValues(), jGen);
                break;
            case PORT_DESC:
                serializePortDescReply((List<OFPortDescStatsReply>) reply.getValues(), jGen);
                break;
            case QUEUE:
                serializeQueueReply((List<OFQueueStatsReply>) reply.getValues(), jGen);
                break;
            case QUEUE_DESC:
                serializeQueueDescReply((List<OFQueueDescStatsReply>) reply.getValues(), jGen);
                break;
            case FLOW:
                serializeFlowReply((List<OFFlowStatsReply>) reply.getValues(), jGen);
                break;
            case FLOW_LIGHTWEIGHT:
                serializeFlowLightweightReply((List<OFFlowLightweightStatsReply>) reply.getValues(), jGen);
                break;
            case FLOW_MONITOR:
                serializeFlowMonitorReply((List<OFFlowMonitorReply>) reply.getValues(), jGen);
                break;
            case AGGREGATE:
                serializeAggregateReply((List<OFAggregateStatsReply>) reply.getValues(), jGen);
                break;
            case DESC:
                serializeDescReply((List<OFDescStatsReply>) reply.getValues(), jGen);
                break;            
            case GROUP:
                serializeGroupReply((List<OFGroupStatsReply>) reply.getValues(), jGen);            
                break;        
            case GROUP_DESC:
                serializeGroupDescReply((List<OFGroupDescStatsReply>) reply.getValues(), jGen);
                break;
            case GROUP_FEATURES:
                serializeGroupFeaturesReply((List<OFGroupFeaturesStatsReply>) reply.getValues(), jGen);
                break;
            case METER:
                serializeMeterReply((List<OFMeterStatsReply>) reply.getValues(), jGen);
                break;
            case METER_CONFIG:
                serializeMeterConfigReply((List<OFMeterConfigStatsReply>) reply.getValues(), jGen);
                break;
            case METER_FEATURES:
                serializeMeterFeaturesReply((List<OFMeterFeaturesStatsReply>) reply.getValues(), jGen);
                break;     
            case TABLE:
                serializeTableReply((List<OFTableStatsReply>) reply.getValues(), jGen);
                break;
            case TABLE_FEATURES:
                serializeTableFeaturesReply((List<OFTableFeaturesStatsReply>) reply.getValues(), jGen);
                break;
            case TABLE_DESC:
                serializeTableDescReply((List<OFTableDescStatsReply>) reply.getValues(), jGen);
                break;
            case BUNDLE_FEATURES:
                serializeBundleFeaturesReply((List<OFBundleFeaturesStatsReply>) reply.getValues(), jGen);
                break;
            case CONTROLLER_STATUS:
                serializeControllerStatusReply((List<OFControllerStatusStatsReply>) reply.getValues(), jGen);
                break;
            case EXPERIMENTER:
                logger.warn("Unimplemented {} stats reply serializer", reply.getStatType());
                break;
                /* omit default to alert (via warning) if we miss any in the future */
            }   
        }
        jGen.writeEndObject();
    }

    public static void serializeFeaturesReply(OFFeaturesReply fr, JsonGenerator jGen) throws IOException, JsonProcessingException {
        /* Common to All OF Versions */			
        jGen.writeStringField("capabilities", fr.getCapabilities().toString());
        jGen.writeStringField("dpid", fr.getDatapathId().toString());
        jGen.writeNumberField("buffers", fr.getNBuffers());
        jGen.writeNumberField("tables", fr.getNTables());
        jGen.writeStringField("version", fr.getVersion().toString());

        if (fr.getVersion().compareTo(OFVersion.OF_13) < 0) { // OF1.3+ break this out into port_config
            serializePortDesc(fr.getPorts(), jGen);
        }
        if (fr.getVersion().compareTo(OFVersion.OF_10) == 0) {
            String actions = "[";
            for (OFActionType action : fr.getActions()) {
                actions =  actions + action.toString() + ", ";
            }
            actions = actions.substring(0, actions.length() - 2); // remove ending space+comma
            actions = actions + "]";
            jGen.writeStringField("actions", actions);
        }
    }

    public static void serializeTableDescReply(List<OFTableDescStatsReply> trl, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        Set<OFTableDesc> entries = new HashSet<OFTableDesc>();
        for (OFTableDescStatsReply r : trl) {
            entries.addAll(r.getEntries());
        }
        if (!entries.isEmpty()) {
            jGen.writeStringField("version", entries.iterator().next().getVersion().toString()); /* common to all */
            jGen.writeFieldName("tables");
            jGen.writeStartArray();
            for (OFTableDesc e : entries) {
                jGen.writeNumberField("table_id", e.getTableId().getValue());
                jGen.writeFieldName("config");
                jGen.writeStartArray();
                for (OFTableConfig c : e.getConfig()) {
                    jGen.writeString(c.name());
                }
                jGen.writeEndArray();
                /* TODO properties */
            }
            jGen.writeEndArray();
        }
    }

    public static void serializeBundleFeaturesReply(List<OFBundleFeaturesStatsReply> bsr, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        if (!bsr.isEmpty()) {
            OFBundleFeaturesStatsReply b =  bsr.iterator().next();
            jGen.writeStringField("version", b.getVersion().toString());
            jGen.writeFieldName("capabilities");
            jGen.writeStartArray();
            for (OFBundleFeatureFlags f : b.getCapabilities()) {
                jGen.writeString(f.name());
            }
            jGen.writeEndArray();
            /* TODO properties */
        }
    }

    public static void serializeControllerStatusReply(List<OFControllerStatusStatsReply> csr, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        Set<OFControllerStatusEntry> entries = new HashSet<OFControllerStatusEntry>();
        for (OFControllerStatusStatsReply r : csr) {
            entries.addAll(r.getControllerStatus());
        }
        if (!entries.isEmpty()) {
            jGen.writeStringField("version", entries.iterator().next().getVersion().toString()); /* common to all */
            jGen.writeFieldName("controller_status");
            jGen.writeStartArray();
            for (OFControllerStatusEntry e : entries) {
                jGen.writeStartObject();
                jGen.writeNumberField("controller_id", e.getShortId());
                jGen.writeStringField("channel_status", e.getChannelStatus().name());
                jGen.writeStringField("reason", e.getReason().name());
                jGen.writeStringField("role", e.getRole().name());
                /* TODO properties */
                jGen.writeEndObject();
            }
            jGen.writeEndArray();
        }
    }

    public static void serializeQueueReply(List<OFQueueStatsReply> qrl, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        Set<OFQueueStatsEntry> entries = new HashSet<OFQueueStatsEntry>();
        for (OFQueueStatsReply r : qrl) {
            entries.addAll(r.getEntries());
        }
        if (!entries.isEmpty()) {
            jGen.writeStringField("version", entries.iterator().next().getVersion().toString()); /* common to all */
            jGen.writeFieldName("queues");
            jGen.writeStartArray();
            for (OFQueueStatsEntry e : entries) {
                jGen.writeNumberField("duration_nsec", e.getDurationNsec());
                jGen.writeNumberField("duration_sec", e.getDurationSec());
                jGen.writeNumberField("queue_id", e.getQueueId());
                jGen.writeNumberField("port", e.getPortNo().getPortNumber());
                jGen.writeNumberField("tx_bytes", e.getTxBytes().getValue());
                jGen.writeNumberField("tx_errors", e.getTxErrors().getValue());
                jGen.writeNumberField("tx_errors", e.getTxPackets().getValue());
                /* TODO properties */
            }
            jGen.writeEndArray();
        }
    }

    public static void serializeQueueDescReply(List<OFQueueDescStatsReply> qrl, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        Set<OFQueueDesc> entries = new HashSet<OFQueueDesc>();
        for (OFQueueDescStatsReply r : qrl) {
            entries.addAll(r.getEntries());
        }
        if (!entries.isEmpty()) {
            jGen.writeStringField("version", entries.iterator().next().getVersion().toString()); /* common to all */
            jGen.writeFieldName("queues");
            jGen.writeStartArray();
            for (OFQueueDesc e : entries) {
                jGen.writeNumberField("queue_id", e.getQueueId());
                jGen.writeNumberField("port", e.getPortNo());
                /* TODO properties */
            }
            jGen.writeEndArray();
        }
    }

    public static void serializeFlowLightweightReply(List<OFFlowLightweightStatsReply> frl, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        Set<OFFlowLightweightStatsEntry> entries = new HashSet<OFFlowLightweightStatsEntry>();
        for (OFFlowLightweightStatsReply r : frl) {
            entries.addAll(r.getEntries());
        }
        if (!entries.isEmpty()) {
            jGen.writeStringField("version", entries.iterator().next().getVersion().toString()); /* common to all */
            jGen.writeFieldName("flows");
            jGen.writeStartArray();
            for (OFFlowLightweightStatsEntry e : entries) {
                jGen.writeStartObject();
                jGen.writeNumberField("priority", e.getPriority());
                jGen.writeNumberField("table_id", e.getTableId().getValue());
                MatchSerializer.serializeMatch(jGen, e.getMatch());
                jGen.writeFieldName("stats");
                jGen.writeStartObject();
                for (StatField<?> f : e.getStats().getStatFields()) {
                    switch (f.id) {
                    case BYTE_COUNT:
                    case IDLE_TIME:
                    case PACKET_COUNT:
                    case DURATION:
                        jGen.writeNumberField(f.getName(), ((U64) e.getStats().get(f)).getValue());
                        break;
                    case FLOW_COUNT:
                        jGen.writeNumberField(f.getName(), ((U32) e.getStats().get(f)).getValue());
                        break;
                        /* no default so we see a warning in future */
                    }
                }
                jGen.writeEndObject(); /* end stats */
                jGen.writeEndObject(); /* end entry */
            }
            jGen.writeEndArray();
        }
    }

    public static void serializeFlowMonitorReply(List<OFFlowMonitorReply> fmr, JsonGenerator jGen) throws IOException, JsonProcessingException {		
        Set<OFFlowMonitorReplyEntry> entries = new HashSet<OFFlowMonitorReplyEntry>(); 
        for (OFFlowMonitorReply r : fmr) { 
            entries.addAll(r.getEntries());
        }
        if (!entries.isEmpty()) {
            jGen.writeStringField("version", entries.iterator().next().getVersion().toString()); /* common to all */
            jGen.writeFieldName("events");
            jGen.writeStartArray();
            for (OFFlowMonitorReplyEntry e : entries) {
                jGen.writeString(e.getEvent().name());
            }
            jGen.writeEndArray();
        }
    }

    /***
     * Serializes the Group Statistics Reply
     * @author Naveen
     * @param groupReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeGroupReply(List<OFGroupStatsReply> groupReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFGroupStatsReply groupReply = groupReplies.get(0); // we will get only one GroupReply and it will contains many OFGroupStatsEntry
        jGen.writeStringField("version", groupReply.getVersion().toString()); //return the enum name
        jGen.writeFieldName("group");
        jGen.writeStartArray();
        for (OFGroupStatsEntry entry : groupReply.getEntries()) {
            jGen.writeStartObject();
            jGen.writeStringField("group_number",entry.getGroup().toString());               
            jGen.writeNumberField("reference_count", entry.getRefCount());
            jGen.writeNumberField("packet_count", entry.getPacketCount().getValue());
            jGen.writeNumberField("byte_count", entry.getByteCount().getValue());                        
            jGen.writeFieldName("bucket_counters");
            jGen.writeStartArray();            
            for (OFBucketCounter bCounter : entry.getBucketStats()) {
                jGen.writeStartObject();
                jGen.writeNumberField("packet_count", bCounter.getPacketCount().getValue());
                jGen.writeNumberField("byte_count", bCounter.getByteCount().getValue());
                jGen.writeEndObject();
            }//end of for loop - BucketCounter
            jGen.writeEndArray();
            if (OFVersion.OF_13 == entry.getVersion()) {
                jGen.writeNumberField("duration_sec", entry.getDurationSec());
                jGen.writeNumberField("duration_nsec", entry.getDurationNsec());
            }
            jGen.writeEndObject();
        }//end of for loop - groupStats
        jGen.writeEndArray();
    }

    /***
     * Serializes Group Desc Reply
     * @author Naveen
     * @param groupDescReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeGroupDescReply(List<OFGroupDescStatsReply> groupDescReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFGroupDescStatsReply groupDescReply = groupDescReplies.get(0);
        jGen.writeStringField("version", groupDescReply.getVersion().toString()); //return the enum name
        jGen.writeFieldName("group_desc");
        jGen.writeStartArray();
        for (OFGroupDescStatsEntry entry : groupDescReply.getEntries()) {
            jGen.writeStartObject();                        
            jGen.writeStringField("group_type",entry.getGroupType().toString());
            jGen.writeStringField("group_number",entry.getGroup().toString());                                               
            jGen.writeFieldName("buckets");            
            jGen.writeStartArray();            
            for (OFBucket buckets : entry.getBuckets()) {            	
                jGen.writeStartObject();
                jGen.writeNumberField("weight", buckets.getWeight());
                jGen.writeNumberField("watch_port", buckets.getWatchPort().getPortNumber());
                jGen.writeStringField("watch_group", buckets.getWatchGroup().toString());            	
                OFActionListSerializer.serializeActions(jGen, buckets.getActions());            	
                jGen.writeEndObject();
            }//End of for loop - buckets
            jGen.writeEndArray();//end of buckets            
            jGen.writeEndObject();//end of group Desc iteration
        }//End of for loop - GroupDescStats
        jGen.writeEndArray();//end of group Desc
    }

    /***
     * Serializes Group Feature Reply 
     * @author Naveen
     * @param groupFeaturesReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeGroupFeaturesReply(List<OFGroupFeaturesStatsReply> groupFeaturesReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{

        OFGroupFeaturesStatsReply groupFeaturesReply = groupFeaturesReplies.get(0);
        jGen.writeStringField("version", groupFeaturesReply.getVersion().toString()); //return the enum name

        jGen.writeFieldName("group_features");
        jGen.writeStartObject();                        
        jGen.writeArrayFieldStart("capabilities");
        for (OFGroupCapabilities c : groupFeaturesReply.getCapabilities()) {
            jGen.writeString(c.toString());
        }
        jGen.writeEndArray();
        jGen.writeNumberField("max_groups_all", groupFeaturesReply.getMaxGroupsAll());
        jGen.writeNumberField("max_groups_select", groupFeaturesReply.getMaxGroupsSelect());
        jGen.writeNumberField("max_groups_indirect", groupFeaturesReply.getMaxGroupsIndirect());
        jGen.writeNumberField("max_groups_ff", groupFeaturesReply.getMaxGroupsFf());
        jGen.writeNumberField("actions_all", groupFeaturesReply.getActionsAll());
        jGen.writeNumberField("actions_select", groupFeaturesReply.getActionsSelect());
        jGen.writeNumberField("actions_indirect", groupFeaturesReply.getActionsIndirect());
        jGen.writeNumberField("actions_ff", groupFeaturesReply.getActionsFf());

        jGen.writeEndObject();//end of group Feature
    }

    /***
     * Serializes the Meter Statistics Reply
     * @author Naveen
     * @param meterReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeMeterReply(List<OFMeterStatsReply> meterReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFMeterStatsReply meterReply = meterReplies.get(0); // we will get only one meterReply and it will contains many OFMeterStatsEntry ?
        jGen.writeStringField("version", meterReply.getVersion().toString()); //return the enum name
        jGen.writeFieldName("meter");
        jGen.writeStartArray();
        for (OFMeterStats entry : meterReply.getEntries()) {
            jGen.writeStartObject();
            jGen.writeNumberField("meter_id", entry.getMeterId());                        
            jGen.writeNumberField("flow_count", entry.getFlowCount());
            jGen.writeNumberField("packet_in_count", entry.getPacketInCount().getValue());
            jGen.writeNumberField("byte_in_count", entry.getByteInCount().getValue());
            jGen.writeFieldName("meter_band_stats");
            jGen.writeStartArray();
            for (OFMeterBandStats bandStats : entry.getBandStats()) {
                jGen.writeStartObject();
                jGen.writeNumberField("packet_band_count", bandStats.getPacketBandCount().getValue());
                jGen.writeNumberField("byte_band_count", bandStats.getByteBandCount().getValue());
                jGen.writeEndObject();
            }//End of for loop - bandStats
            jGen.writeEndArray();          

            jGen.writeNumberField("duration_sec", entry.getDurationSec());
            jGen.writeNumberField("duration_nssec", entry.getDurationNsec());            
            jGen.writeEndObject();
        }//End of for loop - MeterStats
        jGen.writeEndArray();
    }

    /***
     * Serializes Meter Feature Reply
     * @author Naveen
     * @param meterFeaturesReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeMeterFeaturesReply(List<OFMeterFeaturesStatsReply> meterFeaturesReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFMeterFeaturesStatsReply meterFeaturesReply = meterFeaturesReplies.get(0);
        jGen.writeStringField("version", meterFeaturesReply.getVersion().toString()); //return the enum name

        OFMeterFeatures meterFeatures = meterFeaturesReply.getFeatures();
        jGen.writeFieldName("meter_features");
        jGen.writeStartObject();      

        jGen.writeNumberField("max_meters", meterFeatures.getMaxMeter());
        jGen.writeNumberField("band_types", meterFeatures.getBandTypes());
        jGen.writeNumberField("capabilities", meterFeatures.getCapabilities());
        jGen.writeNumberField("max_bands", meterFeatures.getMaxBands());
        jGen.writeNumberField("max_colors", meterFeatures.getMaxColor());

        jGen.writeEndObject();//end of group Feature
    }

    /***
     * Serializes Meter Config Reply
     * @author Naveen 
     * @param meterConfigReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeMeterConfigReply(List<OFMeterConfigStatsReply> meterConfigReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFMeterConfigStatsReply meterConfigReply = meterConfigReplies.get(0);
        jGen.writeStringField("version", meterConfigReply.getVersion().toString()); //return the enum name
        jGen.writeFieldName("meter_config");
        jGen.writeStartArray();
        for (OFMeterConfig config : meterConfigReply.getEntries()) {
            jGen.writeStartObject();
            jGen.writeNumberField("meter_id", config.getMeterId());
            jGen.writeArrayFieldStart("flags");
            for (OFMeterFlags f : config.getFlags()) {
                jGen.writeString(f.toString());
            }
            jGen.writeEndArray();
            jGen.writeFieldName("meter_bands");
            jGen.writeStartArray();
            for (OFMeterBand band : config.getEntries()) {
                jGen.writeStartObject();
                int type = band.getType();
                jGen.writeNumberField("band_type", type);

                switch (type) {
                case OFMeterBandTypeSerializerVer15.DROP_VAL:
                    OFMeterBandDrop bandDrop = (OFMeterBandDrop) band;
                    jGen.writeNumberField("rate", bandDrop.getRate());
                    jGen.writeNumberField("burst_size", bandDrop.getBurstSize());
                    break;

                case OFMeterBandTypeSerializerVer15.DSCP_REMARK_VAL:
                    OFMeterBandDscpRemark bandDscp = (OFMeterBandDscpRemark) band;
                    jGen.writeNumberField("rate", bandDscp.getRate());
                    jGen.writeNumberField("burst_size", bandDscp.getBurstSize());
                    jGen.writeNumberField("prec_level", bandDscp.getPrecLevel());
                    break;

                case OFMeterBandTypeSerializerVer15.EXPERIMENTER_VAL:
                    OFMeterBandExperimenter bandExp = (OFMeterBandExperimenter) band;
                    jGen.writeNumberField("rate", bandExp.getRate());
                    jGen.writeNumberField("burst_size", bandExp.getBurstSize());
                    jGen.writeNumberField("experimenter", bandExp.getExperimenter());
                    break;

                default:
                    // shouldn't ever get here
                    break;            		
                }//end of Switch Case

                jGen.writeEndObject();
            }//end of for loop
            jGen.writeEndArray();
            jGen.writeEndObject();
        }//end of for loop
        jGen.writeEndArray();
    }

    /***
     * Serializes Table Statistics
     * @author Naveen
     * @param tableReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeTableReply(List<OFTableStatsReply> tableReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{

        OFTableStatsReply tableReply = tableReplies.get(0); // we will get only one tableReply and it will contains many OFTableStatsEntry ?
        jGen.writeStringField("version", tableReply.getVersion().toString()); //return the enum name
        jGen.writeFieldName("table");
        jGen.writeStartArray();
        for (OFTableStatsEntry entry : tableReply.getEntries()) {
            jGen.writeStartObject();

            //Fields common to all OF versions
            //For OF 1.3, only these fields are applicable
            jGen.writeStringField("table_id",entry.getTableId().toString());                        
            jGen.writeNumberField("active_count", entry.getActiveCount());
            jGen.writeNumberField("lookup_count", entry.getLookupCount().getValue());
            jGen.writeNumberField("match_count", entry.getMatchedCount().getValue());

            //Fields Applicable only for specific Versions
            switch (entry.getVersion()) {   
            case OF_15:
            case OF_14:
                break;
            case OF_13:
            case OF_12:
                //Fields applicable only to OF 1.2+
                jGen.writeNumberField("write_set_fields", entry.getWriteSetfields().getValue());
                jGen.writeNumberField("apply_set_fields", entry.getApplySetfields().getValue());
                jGen.writeNumberField("metadata_match", entry.getMetadataMatch().getValue());
                jGen.writeNumberField("metadata_write", entry.getMetadataWrite().getValue());            
            case OF_11:
                //Fields applicable to OF 1.1 & 1.2
                jGen.writeStringField("match", entry.getMatch().toString());
                jGen.writeNumberField("instructions", entry.getInstructions());
                jGen.writeNumberField("write_actions", entry.getWriteActions());
                jGen.writeNumberField("apply_actions", entry.getApplyActions());
                jGen.writeNumberField("config", entry.getConfig());            	
            case OF_10:
                //Fields applicable to OF 1.0, 1.1 & 1.2 
                jGen.writeStringField("name",entry.getName());                        
                jGen.writeNumberField("wildcards", entry.getWildcards());
                jGen.writeNumberField("max_entries", entry.getMaxEntries());
                break;                   
            default:
                break;            	
            } //End of switch case
            jGen.writeEndObject();
        } //End of for loop
        jGen.writeEndArray();
    }

    /***
     * Serializes Table Features Reply
     * @author Naveen
     * @param tableFeaturesReplies
     * @param jGen
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeTableFeaturesReply(List<OFTableFeaturesStatsReply> tableFeaturesReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{

        jGen.writeFieldName("tableFeatures");
        jGen.writeStartArray();
        for (OFTableFeaturesStatsReply tableFeaturesReply : tableFeaturesReplies) {

            for (OFTableFeatures tableFeature : tableFeaturesReply.getEntries()) {
                jGen.writeStartObject();    
                jGen.writeStringField("version", tableFeature.getVersion().toString());
                jGen.writeNumberField("table_id", tableFeature.getTableId().getValue());
                jGen.writeStringField("name", tableFeature.getName());
                jGen.writeNumberField("metadata_match", tableFeature.getMetadataMatch().getValue());
                jGen.writeNumberField("metadata_write", tableFeature.getMetadataWrite().getValue());
                jGen.writeNumberField("config", tableFeature.getConfig());
                jGen.writeNumberField("max_entries", tableFeature.getMaxEntries());

                jGen.writeFieldName("properties");
                jGen.writeStartArray();
                for (OFTableFeatureProp properties : tableFeature.getProperties()) {            	
                    jGen.writeStartObject();

                    int type = properties.getType();
                    switch (type) {
                    case OFTableFeaturePropTypeSerializerVer15.INSTRUCTIONS_VAL:
                        OFTableFeaturePropInstructions propInstruct = (OFTableFeaturePropInstructions) properties;
                        jGen.writeFieldName("instructions");
                        jGen.writeStartArray();
                        for (OFInstructionId id : propInstruct.getInstructionIds()) {
                            jGen.writeString(id.getType().toString());              			
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.INSTRUCTIONS_MISS_VAL:
                        OFTableFeaturePropInstructionsMiss propInstructMiss = (OFTableFeaturePropInstructionsMiss) properties;
                        jGen.writeFieldName("instructions_miss");
                        jGen.writeStartArray();
                        for (OFInstructionId id : propInstructMiss.getInstructionIds()) {
                            jGen.writeString(id.getType().toString());              			
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.NEXT_TABLES_VAL:
                        OFTableFeaturePropNextTables propNxtTables = (OFTableFeaturePropNextTables) properties;
                        jGen.writeFieldName("next_tables");
                        jGen.writeStartArray();
                        for (U8 id : propNxtTables.getNextTableIds()) {
                            jGen.writeNumber(id.getValue());
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.NEXT_TABLES_MISS_VAL:
                        OFTableFeaturePropNextTablesMiss propNxtTablesMiss = (OFTableFeaturePropNextTablesMiss) properties;
                        jGen.writeFieldName("next_tables_miss");
                        jGen.writeStartArray();
                        for (U8 id : propNxtTablesMiss.getNextTableIds()) {
                            jGen.writeNumber(id.getValue());
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WRITE_ACTIONS_VAL:
                        OFTableFeaturePropWriteActions propWrAct = (OFTableFeaturePropWriteActions) properties; 
                        jGen.writeFieldName("write_actions");
                        jGen.writeStartArray();
                        for (OFActionId id : propWrAct.getActionIds()) {
                            jGen.writeString(id.getType().toString());
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WRITE_ACTIONS_MISS_VAL:
                        OFTableFeaturePropWriteActionsMiss propWrActMiss = (OFTableFeaturePropWriteActionsMiss) properties;
                        jGen.writeFieldName("write_actions_miss");
                        jGen.writeStartArray();
                        for (OFActionId id : propWrActMiss.getActionIds()) {
                            jGen.writeString(id.getType().toString());
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.APPLY_ACTIONS_VAL:
                        OFTableFeaturePropApplyActions propAppAct = (OFTableFeaturePropApplyActions) properties;   
                        jGen.writeFieldName("apply_actions");
                        jGen.writeStartArray();
                        for (OFActionId id : propAppAct.getActionIds()) {
                            jGen.writeString(id.getType().toString());
                        }
                        jGen.writeEndArray();
                        break;	
                    case OFTableFeaturePropTypeSerializerVer15.APPLY_ACTIONS_MISS_VAL:
                        OFTableFeaturePropApplyActionsMiss propAppActMiss = (OFTableFeaturePropApplyActionsMiss) properties;
                        jGen.writeFieldName("apply_actions_miss");
                        jGen.writeStartArray();
                        for (OFActionId id : propAppActMiss.getActionIds()) {
                            jGen.writeString(id.getType().toString());
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.MATCH_VAL:                	
                        OFTableFeaturePropMatch propMatch = (OFTableFeaturePropMatch) properties;
                        jGen.writeFieldName("match");
                        jGen.writeStartArray();
                        for (U32 id : propMatch.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WILDCARDS_VAL:
                        OFTableFeaturePropWildcards propWildcards = (OFTableFeaturePropWildcards) properties;
                        jGen.writeFieldName("wildcards");
                        jGen.writeStartArray();
                        for (U32 id : propWildcards.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WRITE_SETFIELD_VAL:
                        OFTableFeaturePropWriteSetfield propWrSetfield = (OFTableFeaturePropWriteSetfield) properties;           
                        jGen.writeFieldName("write_set_field");
                        jGen.writeStartArray();
                        for (U32 id : propWrSetfield.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WRITE_SETFIELD_MISS_VAL:
                        OFTableFeaturePropWriteSetfieldMiss propWrSetfieldMiss = (OFTableFeaturePropWriteSetfieldMiss) properties; 
                        jGen.writeFieldName("write_set_field_miss");
                        jGen.writeStartArray();
                        for (U32 id : propWrSetfieldMiss.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.APPLY_SETFIELD_VAL:
                        OFTableFeaturePropApplySetfield propAppSetfield = (OFTableFeaturePropApplySetfield) properties;
                        jGen.writeFieldName("apply_set_field");
                        jGen.writeStartArray();
                        for (U32 id : propAppSetfield.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.APPLY_SETFIELD_MISS_VAL:
                        OFTableFeaturePropApplySetfieldMiss propAppSetfieldMiss = (OFTableFeaturePropApplySetfieldMiss) properties;                		
                        jGen.writeFieldName("apply_set_field_miss");
                        jGen.writeStartArray();
                        for (U32 id : propAppSetfieldMiss.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.EXPERIMENTER_VAL:
                        OFTableFeaturePropExperimenter propExp = (OFTableFeaturePropExperimenter) properties; 
                        jGen.writeFieldName("experimenter");
                        jGen.writeStartObject();
                        jGen.writeNumberField("sub_type", propExp.getSubtype());
                        jGen.writeNumberField("experimenter", propExp.getExperimenter());
                        jGen.writeStringField("data", propExp.getExperimenterData().toString());
                        jGen.writeEndObject();
                        break;	
                    case OFTableFeaturePropTypeSerializerVer15.EXPERIMENTER_MISS_VAL:
                        OFTableFeaturePropExperimenterMiss propExpMiss = (OFTableFeaturePropExperimenterMiss) properties;
                        jGen.writeFieldName("experimenter_miss");
                        jGen.writeStartObject();
                        jGen.writeNumberField("sub_type", propExpMiss.getSubtype());
                        jGen.writeNumberField("experimenter", propExpMiss.getExperimenter());
                        jGen.writeStringField("data", propExpMiss.getExperimenterData().toString());
                        jGen.writeEndObject();
                        break;	
                    case OFTableFeaturePropTypeSerializerVer15.APPLY_COPYFIELD_MISS_VAL:
                        OFTableFeaturePropApplyCopyfieldMiss propApplyCopyfieldMiss = (OFTableFeaturePropApplyCopyfieldMiss) properties;           
                        jGen.writeFieldName("apply_copy_field_miss");
                        jGen.writeStartArray();
                        for (U32 id : propApplyCopyfieldMiss.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.APPLY_COPYFIELD_VAL:
                        OFTableFeaturePropApplyCopyfield propApplyCopyfield = (OFTableFeaturePropApplyCopyfield) properties;           
                        jGen.writeFieldName("apply_copy_field");
                        jGen.writeStartArray();
                        for (U32 id : propApplyCopyfield.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WRITE_COPYFIELD_MISS_VAL:
                        OFTableFeaturePropWriteCopyfieldMiss propWriteCopyfieldMiss = (OFTableFeaturePropWriteCopyfieldMiss) properties;           
                        jGen.writeFieldName("write_copy_field_miss");
                        jGen.writeStartArray();
                        for (U32 id : propWriteCopyfieldMiss.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.WRITE_COPYFIELD_VAL:
                        OFTableFeaturePropWriteCopyfield propWriteCopyfield = (OFTableFeaturePropWriteCopyfield) properties;           
                        jGen.writeFieldName("write_copy_field");
                        jGen.writeStartArray();
                        for (U32 id : propWriteCopyfield.getOxmIds()) {
                            jGen.writeString(OXMUtils.oxmIdToString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.TABLE_SYNC_FROM_VAL:
                        OFTableFeaturePropTableSyncFrom propTableSyncFrom = (OFTableFeaturePropTableSyncFrom) properties;           
                        jGen.writeFieldName("write_sync_table_from");
                        jGen.writeStartArray();
                        for (U8 id : propTableSyncFrom.getTableIds()) {
                            jGen.writeString(id.toString());
                        }
                        jGen.writeEndArray();
                        break;
                    case OFTableFeaturePropTypeSerializerVer15.PACKET_TYPES_VAL:
                        OFTableFeaturePropOxmValues propOxmValues = (OFTableFeaturePropOxmValues) properties; /* TODO name mismatch? */        
                        jGen.writeFieldName("packet_types");
                        jGen.writeStartArray();
                        for (byte id : propOxmValues.getOxmValues()) {
                            jGen.writeString(Byte.toString(id));
                        }
                        jGen.writeEndArray();
                        break;
                    default:
                        logger.warn("Unexpected OFTableFeaturePropType value {}", type);
                        break;            		
                    }//end of Switch Case  
                    jGen.writeEndObject();
                }//end of for loop - properties                                              
                jGen.writeEndArray();
                jGen.writeEndObject();
            }//end of for loop - features
        } //end of looping through REQ_MORE flagged message loop
        jGen.writeEndArray();
    } 

    public static void serializePortReply(List<OFPortStatsReply> portReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{		
        jGen.writeFieldName("port_reply");
        jGen.writeStartArray();
        for (OFPortStatsReply portReply : portReplies) {
            jGen.writeStartObject();
            jGen.writeStringField("version", portReply.getVersion().toString()); //return the enum name          
            jGen.writeFieldName("port");
            jGen.writeStartArray();
            for (OFPortStatsEntry entry : portReply.getEntries()) {
                jGen.writeStartObject();
                jGen.writeStringField("port_number",entry.getPortNo().toString());
                jGen.writeNumberField("receive_packets", entry.getRxPackets().getValue());
                jGen.writeNumberField("transmit_packets", entry.getTxPackets().getValue());
                jGen.writeNumberField("receive_bytes", entry.getRxBytes().getValue());
                jGen.writeNumberField("transmit_bytes", entry.getTxBytes().getValue());
                jGen.writeNumberField("receive_dropped", entry.getRxDropped().getValue());
                jGen.writeNumberField("transmit_dropped", entry.getTxDropped().getValue());
                jGen.writeNumberField("receive_errors", entry.getRxErrors().getValue());
                jGen.writeNumberField("transmit_errors", entry.getTxErrors().getValue());
                if (entry.getVersion().compareTo(OFVersion.OF_13) <= 0) {
                    jGen.writeNumberField("receive_frame_errors", entry.getRxFrameErr().getValue());
                    jGen.writeNumberField("receive_overrun_errors", entry.getRxOverErr().getValue());
                    jGen.writeNumberField("receive_CRC_errors", entry.getRxCrcErr().getValue());
                    jGen.writeNumberField("collisions", entry.getCollisions().getValue());
                }
                if (entry.getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    jGen.writeNumberField("duration_sec", entry.getDurationSec());
                    jGen.writeNumberField("duration_nsec", entry.getDurationNsec());
                }
                if (entry.getVersion().compareTo(OFVersion.OF_14) >= 0) {
                    jGen.writeFieldName("properties");
                    jGen.writeStartArray();
                    for (OFPortStatsProp p : entry.getProperties()) {
                        jGen.writeStartObject();
                        if (p instanceof OFPortStatsPropEthernet) {
                            jGen.writeStringField("type", OFPortStatsPropTypeSerializerVer15.ofWireValue((short)p.getType()).name());
                            jGen.writeNumberField("collisions", ((OFPortStatsPropEthernet) p).getCollisions().getValue());
                            jGen.writeNumberField("rx_crc_error", ((OFPortStatsPropEthernet) p).getRxCrcErr().getValue());
                            jGen.writeNumberField("rx_frame_error", ((OFPortStatsPropEthernet) p).getRxFrameErr().getValue());
                            jGen.writeNumberField("rx_over_error", ((OFPortStatsPropEthernet) p).getRxOverErr().getValue());
                        } else if (p instanceof OFPortStatsPropOptical) {
                            jGen.writeStringField("type", OFPortStatsPropTypeSerializerVer15.ofWireValue((short)p.getType()).name());
                            jGen.writeNumberField("bias_current", ((OFPortStatsPropOptical) p).getBiasCurrent());
                            jGen.writeNumberField("flags", ((OFPortStatsPropOptical) p).getFlags());
                            jGen.writeNumberField("rx_freq_lambda", ((OFPortStatsPropOptical) p).getRxFreqLmda());
                            jGen.writeNumberField("rx_grid_span", ((OFPortStatsPropOptical) p).getRxGridSpan());
                            jGen.writeNumberField("rx_offset", ((OFPortStatsPropOptical) p).getRxOffset());
                            jGen.writeNumberField("rx_power", ((OFPortStatsPropOptical) p).getRxPwr());
                            jGen.writeNumberField("rx_temp", ((OFPortStatsPropOptical) p).getTemperature());
                            jGen.writeNumberField("tx_freq_lambda", ((OFPortStatsPropOptical) p).getTxFreqLmda());
                            jGen.writeNumberField("tx_grid_span", ((OFPortStatsPropOptical) p).getTxGridSpan());
                            jGen.writeNumberField("tx_offset", ((OFPortStatsPropOptical) p).getTxOffset());
                            jGen.writeNumberField("tx_power", ((OFPortStatsPropOptical) p).getTxPwr());
                        } else if (p instanceof OFPortStatsPropExperimenter) {
                            jGen.writeStringField("type", OFPortStatsPropTypeSerializerVer15.ofWireValue((short)p.getType()).name());
                        }
                        jGen.writeEndObject();
                    }
                    jGen.writeEndArray();
                }
                jGen.writeEndObject();
            }
            jGen.writeEndArray();
            jGen.writeEndObject();
        }
        jGen.writeEndArray();
    }

    public static void serializeFlowReply(List<OFFlowStatsReply> flowReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        /* start the array before each reply */
        jGen.writeFieldName("flows"); 
        jGen.writeStartArray();
        for (OFFlowStatsReply flowReply : flowReplies) { // for each flow stats reply
            List<OFFlowStatsEntry> entries = flowReply.getEntries();
            for (OFFlowStatsEntry entry : entries) { // for each flow
                jGen.writeStartObject();
                // list flow stats/info
                jGen.writeStringField("version", entry.getVersion().toString()); // return the enum name
                jGen.writeNumberField("cookie", entry.getCookie().getValue());
                jGen.writeStringField("table_id", entry.getTableId().toString());
                jGen.writeNumberField("packet_count", entry.getPacketCount().getValue());
                jGen.writeNumberField("byte_count", entry.getByteCount().getValue());
                jGen.writeNumberField("duration_sec", entry.getDurationSec());
                jGen.writeNumberField("duration_nsec", entry.getDurationNsec());
                jGen.writeNumberField("priority", entry.getPriority());
                jGen.writeNumberField("idle_timeout_s", entry.getIdleTimeout());
                jGen.writeNumberField("hard_timeout_s", entry.getHardTimeout());
                if (entry.getVersion().compareTo(OFVersion.OF_10) != 0) {
                    jGen.writeArrayFieldStart("flags");
                    for (OFFlowModFlags f : entry.getFlags()) {
                        jGen.writeString(f.toString());
                    }
                    jGen.writeEndArray();
                }

                MatchSerializer.serializeMatch(jGen, entry.getMatch());

                // handle OF1.1+ instructions with actions within
                if (entry.getVersion() == OFVersion.OF_10) {
                    jGen.writeObjectFieldStart("actions");
                    OFActionListSerializer.serializeActions(jGen, entry.getActions());
                    jGen.writeEndObject();
                } else {
                    OFInstructionListSerializer.serializeInstructionList(jGen, entry.getInstructions());
                }

                jGen.writeEndObject();
            } // end for each OFFlowStatsReply entry */
        } // end for each OFStatsReply
        //jGen.writeEndObject();
        jGen.writeEndArray();
    } // end method

    public static void serializeDescReply(List<OFDescStatsReply> descReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFDescStatsReply descReply = descReplies.get(0); // There is only one descReply from the switch
        jGen.writeObjectFieldStart("desc"); 
        jGen.writeStringField("version", descReply.getVersion().toString()); //return the enum name
        jGen.writeStringField("manufacturer_description", descReply.getMfrDesc()); 
        jGen.writeStringField("hardware_description", descReply.getHwDesc()); 
        jGen.writeStringField("software_description", descReply.getSwDesc()); 
        jGen.writeStringField("serial_number", descReply.getSerialNum()); 
        jGen.writeStringField("datapath_description", descReply.getDpDesc()); 
        jGen.writeEndObject(); // end match
    }

    public static void serializeAggregateReply(List<OFAggregateStatsReply> aggregateReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFAggregateStatsReply aggregateReply = aggregateReplies.get(0); // There are only one aggregateReply from the switch
        jGen.writeObjectFieldStart("aggregate"); 
        jGen.writeStringField("version", aggregateReply.getVersion().toString()); //return the enum name
        jGen.writeNumberField("flow_count", aggregateReply.getFlowCount());
        jGen.writeNumberField("packet_count", aggregateReply.getPacketCount().getValue());
        jGen.writeNumberField("byte_count", aggregateReply.getByteCount().getValue());
        jGen.writeArrayFieldStart("flags");
        for (OFStatsReplyFlags f : aggregateReply.getFlags()) {
            jGen.writeString(f.toString());
        }
        jGen.writeEndArray();
        jGen.writeEndObject(); // end match
    }

    public static void serializePortDescReply(List<OFPortDescStatsReply> portDescReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFPortDescStatsReply portDescReply = portDescReplies.get(0); // we will get only one PortDescReply and it will contains many OFPortDescStatsEntry ?
        jGen.writeStringField("version", portDescReply.getVersion().toString()); //return the enum name
        serializePortDesc(portDescReply.getEntries(), jGen);
    }

    public static void serializePortDesc(List<OFPortDesc> portDescList, JsonGenerator jGen) throws IOException, JsonProcessingException {
        jGen.writeFieldName("port_desc");
        jGen.writeStartArray();
        for(OFPortDesc entry : portDescList) {
            jGen.writeStartObject();
            jGen.writeStringField("port_number",entry.getPortNo().toString());
            jGen.writeStringField("hardware_address", entry.getHwAddr().toString());
            jGen.writeStringField("name", entry.getName());
            jGen.writeArrayFieldStart("config");
            for (OFPortConfig e : entry.getConfig()) {
                jGen.writeString(e.toString());
            }
            jGen.writeEndArray();
            jGen.writeArrayFieldStart("state");
            for (OFPortState e : entry.getState()) {
                jGen.writeString(e.toString());
            }
            jGen.writeEndArray();

            if (entry.getVersion().compareTo(OFVersion.OF_13) <= 0) {
                jGen.writeArrayFieldStart("current_features");
                for (OFPortFeatures e : entry.getCurr()) {
                    jGen.writeString(e.toString());
                }
                jGen.writeEndArray();
                jGen.writeArrayFieldStart("advertised_features");
                for (OFPortFeatures e : entry.getAdvertised()) {
                    jGen.writeString(e.toString());
                }
                jGen.writeEndArray();
                jGen.writeArrayFieldStart("supported_features");
                for (OFPortFeatures e : entry.getSupported()) {
                    jGen.writeString(e.toString());
                }
                jGen.writeEndArray();
                jGen.writeArrayFieldStart("peer_features");
                for (OFPortFeatures e : entry.getPeer()) {
                    jGen.writeString(e.toString());
                }
                jGen.writeEndArray();
            } else if (entry.getVersion().compareTo(OFVersion.OF_14) >= 0) {
                jGen.writeArrayFieldStart("properties");
                for (OFPortDescProp e : entry.getProperties()) {
                    jGen.writeString(OFPortDescPropTypeSerializerVer15.ofWireValue((short) e.getType()).toString());
                }
                jGen.writeEndArray();
            }

            if (entry.getVersion().compareTo(OFVersion.OF_11) >= 0 && 
                    entry.getVersion().compareTo(OFVersion.OF_13) <= 0) {
                jGen.writeNumberField("curr_speed",entry.getCurrSpeed());
                jGen.writeNumberField("max_speed",entry.getMaxSpeed());
            }
            jGen.writeEndObject();
        }
        jGen.writeEndArray();
    }
}