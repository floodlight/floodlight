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
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.core.web.OFStatsTypeStrings;
import net.floodlightcontroller.core.web.StatsReply;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFBucketCounter;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterBandStats;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterFeatures;
import org.projectfloodlight.openflow.protocol.OFMeterFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterStats;
import org.projectfloodlight.openflow.protocol.OFMeterStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyActions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyActionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplySetfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplySetfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenter;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenterMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropMatch;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropNextTables;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropNextTablesMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWildcards;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteActions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteActionsMiss;
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
import org.projectfloodlight.openflow.protocol.ver13.OFFlowModFlagsSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterBandTypeSerializerVer13;
// Use Loxigen's serializer
import org.projectfloodlight.openflow.protocol.ver13.OFPortFeaturesSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver13.OFStatsReplyFlagsSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver13.OFTableFeaturePropTypeSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver12.OFFlowModFlagsSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver12.OFPortConfigSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver12.OFPortFeaturesSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver12.OFPortStateSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver12.OFStatsReplyFlagsSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFFlowModFlagsSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver11.OFPortConfigSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver11.OFPortFeaturesSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver11.OFPortStateSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver11.OFStatsReplyFlagsSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFPortConfigSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver10.OFPortFeaturesSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver10.OFPortStateSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver10.OFStatsReplyFlagsSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver13.OFPortStateSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver13.OFPortConfigSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFFlowModFlagsSerializerVer14;
import org.projectfloodlight.openflow.protocol.ver14.OFStatsReplyFlagsSerializerVer14;
import org.projectfloodlight.openflow.protocol.OFAggregateStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.U32;
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
			jGen.writeObjectFieldStart("ERROR");
			jGen.writeStringField("   ", "An error has occurred while proccesing your request,");
			jGen.writeStringField("  *", "which might be due to one or more of the following:");
			jGen.writeStringField(" * ", "-- An invalid DPID and/or stats/features request.");
			jGen.writeStringField(" **", "-- The switch is not connected to the controller.");
			jGen.writeStringField("*  ", "-- The request specified is not supported by the switch's OpenFlow version.");
			jGen.writeEndObject();
			jGen.writeObjectFieldStart("Valid statistics and features strings are:");
			jGen.writeStringField("1)", OFStatsTypeStrings.AGGREGATE);
			jGen.writeStringField("2)", OFStatsTypeStrings.DESC);
			jGen.writeStringField("3)", OFStatsTypeStrings.EXPERIMENTER);
			jGen.writeStringField("4)", OFStatsTypeStrings.FEATURES);
			jGen.writeStringField("5)", OFStatsTypeStrings.FLOW);
			jGen.writeStringField("6)", OFStatsTypeStrings.GROUP);
			jGen.writeStringField("7)", OFStatsTypeStrings.GROUP_DESC);
			jGen.writeStringField("8)", OFStatsTypeStrings.GROUP_FEATURES);  
			jGen.writeStringField("9)", OFStatsTypeStrings.METER);  
			jGen.writeStringField("A)", OFStatsTypeStrings.METER_CONFIG); 
			jGen.writeStringField("B)", OFStatsTypeStrings.METER_FEATURES); 
			jGen.writeStringField("C)", OFStatsTypeStrings.PORT);
			jGen.writeStringField("D)", OFStatsTypeStrings.PORT_DESC);
			jGen.writeStringField("E)", OFStatsTypeStrings.QUEUE);
			jGen.writeStringField("F)", OFStatsTypeStrings.TABLE);
			jGen.writeStringField("G)", OFStatsTypeStrings.TABLE_FEATURES);
			jGen.writeEndObject(); 
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
			case QUEUE:
				// handle queue
				break;
			case FLOW:
				serializeFlowReply((List<OFFlowStatsReply>) reply.getValues(), jGen);
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
			case PORT_DESC:
				serializePortDescReply((List<OFPortDescStatsReply>) reply.getValues(), jGen);
				break;
			case EXPERIMENTER:
				break;
			default:
				break;
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
		for(OFGroupStatsEntry entry : groupReply.getEntries()) {
			jGen.writeStartObject();
			jGen.writeStringField("groupNumber",entry.getGroup().toString());               
			jGen.writeNumberField("refCount", entry.getRefCount());
			jGen.writeNumberField("packetCount", entry.getPacketCount().getValue());
			jGen.writeNumberField("byteCount", entry.getByteCount().getValue());                        
			jGen.writeFieldName("bucketCounters");
			jGen.writeStartArray();            
			for(OFBucketCounter bCounter : entry.getBucketStats()) {
				jGen.writeStartObject();
				jGen.writeNumberField("packetCount", bCounter.getPacketCount().getValue());
				jGen.writeNumberField("byteCount", bCounter.getByteCount().getValue());
				jGen.writeEndObject();
			}//end of for loop - BucketCounter
			jGen.writeEndArray();
			if (OFVersion.OF_13 == entry.getVersion()) {
				jGen.writeNumberField("durationSec", entry.getDurationSec());
				jGen.writeNumberField("durationNsec", entry.getDurationNsec());
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
		jGen.writeFieldName("groupDesc");
		jGen.writeStartArray();
		for(OFGroupDescStatsEntry entry : groupDescReply.getEntries()) {
			jGen.writeStartObject();                        
			jGen.writeStringField("groupType",entry.getGroupType().toString());
			jGen.writeStringField("groupNumber",entry.getGroup().toString());                                               
			jGen.writeFieldName("buckets");            
			jGen.writeStartArray();            
			for(OFBucket buckets : entry.getBuckets()) {            	
				jGen.writeStartObject();
				jGen.writeNumberField("weight", buckets.getWeight());
				jGen.writeNumberField("watchPortNumber", buckets.getWatchPort().getPortNumber());
				jGen.writeStringField("watchGroup", buckets.getWatchGroup().toString());            	
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

		jGen.writeFieldName("groupFeatures");
		jGen.writeStartObject();                        
		jGen.writeNumberField("capabilities",groupFeaturesReply.getCapabilities());                                                              
		jGen.writeNumberField("maxGroupsAll",groupFeaturesReply.getMaxGroupsAll());
		jGen.writeNumberField("maxGroupsSelect",groupFeaturesReply.getMaxGroupsSelect());
		jGen.writeNumberField("maxGroupsIndirect",groupFeaturesReply.getMaxGroupsIndirect());
		jGen.writeNumberField("maxGroupsFf",groupFeaturesReply.getMaxGroupsFf());
		jGen.writeNumberField("actionsAll",groupFeaturesReply.getActionsAll());
		jGen.writeNumberField("actionsSelect",groupFeaturesReply.getActionsSelect());
		jGen.writeNumberField("actionsIndirect",groupFeaturesReply.getActionsIndirect());
		jGen.writeNumberField("actionsFf",groupFeaturesReply.getActionsFf());

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
		for(OFMeterStats entry : meterReply.getEntries()) {
			jGen.writeStartObject();
			jGen.writeNumberField("meterId",entry.getMeterId());                        
			jGen.writeNumberField("flowCount", entry.getFlowCount());
			jGen.writeNumberField("packetInCount", entry.getPacketInCount().getValue());
			jGen.writeNumberField("byteInCount", entry.getByteInCount().getValue());
			jGen.writeFieldName("meterBandStats");
			jGen.writeStartArray();
			for(OFMeterBandStats bandStats : entry.getBandStats()) {
				jGen.writeStartObject();
				jGen.writeNumberField("packetBandCount", bandStats.getPacketBandCount().getValue());
				jGen.writeNumberField("byteBandCount", bandStats.getByteBandCount().getValue());
				jGen.writeEndObject();
			}//End of for loop - bandStats
			jGen.writeEndArray();          

			jGen.writeNumberField("durationSec", entry.getDurationSec());
			jGen.writeNumberField("durationNsec", entry.getDurationNsec());            
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
		jGen.writeFieldName("meterFeatures");
		jGen.writeStartObject();      

		jGen.writeNumberField("maxGroupsAll",meterFeatures.getMaxMeter());
		jGen.writeNumberField("maxGroupsSelect",meterFeatures.getBandTypes());
		jGen.writeNumberField("capabilities",meterFeatures.getCapabilities());
		jGen.writeNumberField("maxGroupsIndirect",meterFeatures.getMaxBands());
		jGen.writeNumberField("maxGroupsFf",meterFeatures.getMaxColor());

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
		jGen.writeFieldName("meterConfig");
		jGen.writeStartArray();
		for(OFMeterBand band : meterConfigReply.getEntries()) {
			jGen.writeStartObject();
			short type = (short)band.getType();
			jGen.writeNumberField("bandType",type);

			switch (type) {
			case OFMeterBandTypeSerializerVer13.DROP_VAL:
				OFMeterBandDrop bandDrop = (OFMeterBandDrop) band;
				jGen.writeNumberField("rate", bandDrop.getRate());
				jGen.writeNumberField("burstSize", bandDrop.getBurstSize());
				break;

			case OFMeterBandTypeSerializerVer13.DSCP_REMARK_VAL:
				OFMeterBandDscpRemark bandDscp = (OFMeterBandDscpRemark) band;
				jGen.writeNumberField("rate", bandDscp.getRate());
				jGen.writeNumberField("burstSize", bandDscp.getBurstSize());
				jGen.writeNumberField("precLevel", bandDscp.getPrecLevel());
				break;

			case OFMeterBandTypeSerializerVer13.EXPERIMENTER_VAL:
				OFMeterBandExperimenter bandExp = (OFMeterBandExperimenter) band;
				jGen.writeNumberField("rate", bandExp.getRate());
				jGen.writeNumberField("burstSize", bandExp.getBurstSize());
				jGen.writeNumberField("experimenter", bandExp.getExperimenter());
				break;

			default:
				// shouldn't ever get here
				break;            		
			}//end of Switch Case

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
		for(OFTableStatsEntry entry : tableReply.getEntries()) {
			jGen.writeStartObject();

			//Fields common to all OF versions
			//For OF 1.3, only these fields are applicable
			jGen.writeStringField("tableId",entry.getTableId().toString());                        
			jGen.writeNumberField("activeCount", entry.getActiveCount());
			jGen.writeNumberField("lookUpCount", entry.getLookupCount().getValue());
			jGen.writeNumberField("matchCount", entry.getMatchedCount().getValue());

			//Fields Applicable only for specific Versions
			switch (entry.getVersion()) {            
			case OF_12:
				//Fields applicable only to OF 1.2
				jGen.writeNumberField("writeSetFields", entry.getWriteSetfields().getValue());
				jGen.writeNumberField("applySetFields", entry.getApplySetfields().getValue());
				jGen.writeNumberField("metaDataMatch", entry.getMetadataMatch().getValue());
				jGen.writeNumberField("metaDataWrite", entry.getMetadataWrite().getValue());            
			case OF_11:
				//Fields applicable to OF 1.1 & 1.2
				jGen.writeStringField("match", entry.getMatch().toString());
				jGen.writeNumberField("instructions", entry.getInstructions());
				jGen.writeNumberField("writeActions", entry.getWriteActions());
				jGen.writeNumberField("applyActions", entry.getApplyActions());
				jGen.writeNumberField("config", entry.getConfig());            	
			case OF_10:
				//Fields applicable to OF 1.0, 1.1 & 1.2 
				jGen.writeStringField("name",entry.getName());                        
				jGen.writeNumberField("wildcards", entry.getWildcards());
				jGen.writeNumberField("maxEntries", entry.getMaxEntries());
				break;                   
			default:
				//no extra fields for OF_13
				break;            	
			}//End of switch case
			jGen.writeEndObject();
		}//End of for loop
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

		OFTableFeaturesStatsReply tableFeaturesReply = tableFeaturesReplies.get(0);
		jGen.writeStringField("version", tableFeaturesReply.getVersion().toString()); //return the enum name

		jGen.writeFieldName("tableFeatures");
		jGen.writeStartArray();
		for(OFTableFeatures tableFeature : tableFeaturesReply.getEntries()) {
			jGen.writeStartObject();                        
			jGen.writeNumberField("tableId", tableFeature.getTableId().getValue());
			jGen.writeStringField("name", tableFeature.getName());
			jGen.writeNumberField("metadataMatch", tableFeature.getMetadataMatch().getValue());
			jGen.writeNumberField("metadataWrite", tableFeature.getMetadataWrite().getValue());
			jGen.writeNumberField("config", tableFeature.getConfig());
			jGen.writeNumberField("maxEntries", tableFeature.getMaxEntries());

			jGen.writeFieldName("properties");
			jGen.writeStartArray();
			for (OFTableFeatureProp properties : tableFeature.getProperties()) {            	
				jGen.writeStartObject();
				short type = (short)properties.getType();
				jGen.writeNumberField("tableFeaturePropType",type);

				switch (type) {
				case OFTableFeaturePropTypeSerializerVer13.INSTRUCTIONS_VAL:
					OFTableFeaturePropInstructions propInstruct = (OFTableFeaturePropInstructions) properties;
					jGen.writeFieldName("instructions");
					jGen.writeStartArray();
					for (OFInstructionId id : propInstruct.getInstructionIds()) {
						jGen.writeStartObject();
						jGen.writeString(id.getType().toString());              			
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.INSTRUCTIONS_MISS_VAL:
					OFTableFeaturePropInstructionsMiss propInstructMiss = (OFTableFeaturePropInstructionsMiss) properties;
					jGen.writeFieldName("instructionsMiss");
					jGen.writeStartArray();
					for (OFInstructionId id : propInstructMiss.getInstructionIds()) {
						jGen.writeStartObject();
						jGen.writeString(id.getType().toString());              			
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.NEXT_TABLES_VAL:
					OFTableFeaturePropNextTables propNxtTables = (OFTableFeaturePropNextTables) properties;
					jGen.writeFieldName("nextTables");
					jGen.writeStartArray();
					for (U8 id : propNxtTables.getNextTableIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.NEXT_TABLES_MISS_VAL:
					OFTableFeaturePropNextTablesMiss propNxtTablesMiss = (OFTableFeaturePropNextTablesMiss) properties;
					jGen.writeFieldName("nextTablesMiss");
					jGen.writeStartArray();
					for (U8 id : propNxtTablesMiss.getNextTableIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.WRITE_ACTIONS_VAL:
					OFTableFeaturePropWriteActions propWrAct = (OFTableFeaturePropWriteActions) properties; 
					jGen.writeFieldName("writeActions");
					jGen.writeStartArray();
					for (OFActionId id : propWrAct.getActionIds()) {
						jGen.writeStartObject();
						jGen.writeString(id.getType().toString());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.WRITE_ACTIONS_MISS_VAL:
					OFTableFeaturePropWriteActionsMiss propWrActMiss = (OFTableFeaturePropWriteActionsMiss) properties;
					jGen.writeFieldName("writeActionsMiss");
					jGen.writeStartArray();
					for (OFActionId id : propWrActMiss.getActionIds()) {
						jGen.writeStartObject();
						jGen.writeString(id.getType().toString());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.APPLY_ACTIONS_VAL:
					OFTableFeaturePropApplyActions propAppAct = (OFTableFeaturePropApplyActions) properties;   
					jGen.writeFieldName("applyActions");
					jGen.writeStartArray();
					for (OFActionId id : propAppAct.getActionIds()) {
						jGen.writeStartObject();
						jGen.writeString(id.getType().toString());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;	
				case OFTableFeaturePropTypeSerializerVer13.APPLY_ACTIONS_MISS_VAL:
					OFTableFeaturePropApplyActionsMiss propAppActMiss = (OFTableFeaturePropApplyActionsMiss) properties;
					jGen.writeFieldName("applyActionsMiss");
					jGen.writeStartArray();
					for (OFActionId id : propAppActMiss.getActionIds()) {
						jGen.writeStartObject();
						jGen.writeString(id.getType().toString());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.MATCH_VAL:                	
					OFTableFeaturePropMatch propMatch = (OFTableFeaturePropMatch) properties;
					jGen.writeFieldName("match");
					jGen.writeStartArray();
					for (U32 id : propMatch.getOxmIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.WILDCARDS_VAL:
					OFTableFeaturePropWildcards propWildcards = (OFTableFeaturePropWildcards) properties;
					jGen.writeFieldName("wildcards");
					jGen.writeStartArray();
					for (U32 id : propWildcards.getOxmIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.WRITE_SETFIELD_VAL:
					OFTableFeaturePropWriteSetfield propWrSetfield = (OFTableFeaturePropWriteSetfield) properties;           
					jGen.writeFieldName("writeSetfield");
					jGen.writeStartArray();
					for (U32 id : propWrSetfield.getOxmIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.WRITE_SETFIELD_MISS_VAL:
					OFTableFeaturePropWriteSetfieldMiss propWrSetfieldMiss = (OFTableFeaturePropWriteSetfieldMiss) properties; 
					jGen.writeFieldName("writeSetfieldMiss");
					jGen.writeStartArray();
					for (U32 id : propWrSetfieldMiss.getOxmIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.APPLY_SETFIELD_VAL:
					OFTableFeaturePropApplySetfield propAppSetfield = (OFTableFeaturePropApplySetfield) properties;
					jGen.writeFieldName("applySetfield");
					jGen.writeStartArray();
					for (U32 id : propAppSetfield.getOxmIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.APPLY_SETFIELD_MISS_VAL:
					OFTableFeaturePropApplySetfieldMiss propAppSetfieldMiss = (OFTableFeaturePropApplySetfieldMiss) properties;                		
					jGen.writeFieldName("applySetfieldMiss");
					jGen.writeStartArray();
					for (U32 id : propAppSetfieldMiss.getOxmIds()) {
						jGen.writeStartObject();
						jGen.writeNumber(id.getValue());
						jGen.writeEndObject();
					}
					jGen.writeEndArray();
					break;
				case OFTableFeaturePropTypeSerializerVer13.EXPERIMENTER_VAL:
					OFTableFeaturePropExperimenter propExp = (OFTableFeaturePropExperimenter) properties; 
					jGen.writeFieldName("experimenter");
					jGen.writeStartObject();
					jGen.writeNumberField("subType", propExp.getSubtype());
					jGen.writeNumberField("experimenter", propExp.getExperimenter());
					jGen.writeStringField("subType", propExp.getExperimenterData().toString());
					jGen.writeEndObject();
					break;	
				case OFTableFeaturePropTypeSerializerVer13.EXPERIMENTER_MISS_VAL:
					OFTableFeaturePropExperimenterMiss propExpMiss = (OFTableFeaturePropExperimenterMiss) properties;
					jGen.writeFieldName("experimenterMiss");
					jGen.writeStartObject();
					jGen.writeNumberField("subType", propExpMiss.getSubtype());
					jGen.writeNumberField("experimenter", propExpMiss.getExperimenter());
					jGen.writeStringField("subType", propExpMiss.getExperimenterData().toString());
					jGen.writeEndObject();
					break;	
				default:
					// shouldn't ever get here
					break;            		
				}//end of Switch Case  
				jGen.writeEndObject();
			}//end of for loop - properties                                              
			jGen.writeEndObject();
		}//end of for loop - features
		jGen.writeEndArray();
	} 


	public static void serializePortReply(List<OFPortStatsReply> portReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
		OFPortStatsReply portReply = portReplies.get(0); // we will get only one PortReply and it will contains many OFPortStatsEntry ?
		jGen.writeStringField("version", portReply.getVersion().toString()); //return the enum name
		jGen.writeFieldName("port");
		jGen.writeStartArray();
		for(OFPortStatsEntry entry : portReply.getEntries()) {
			jGen.writeStartObject();
			jGen.writeStringField("portNumber",entry.getPortNo().toString());
			jGen.writeNumberField("receivePackets", entry.getRxPackets().getValue());
			jGen.writeNumberField("transmitPackets", entry.getTxPackets().getValue());
			jGen.writeNumberField("receiveBytes", entry.getRxBytes().getValue());
			jGen.writeNumberField("transmitBytes", entry.getTxBytes().getValue());
			jGen.writeNumberField("receiveDropped", entry.getRxDropped().getValue());
			jGen.writeNumberField("transmitDropped", entry.getTxDropped().getValue());
			jGen.writeNumberField("receiveErrors", entry.getRxErrors().getValue());
			jGen.writeNumberField("transmitErrors", entry.getTxErrors().getValue());
			jGen.writeNumberField("receiveFrameErrors", entry.getRxFrameErr().getValue());
			jGen.writeNumberField("receiveOverrunErrors", entry.getRxOverErr().getValue());
			jGen.writeNumberField("receiveCRCErrors", entry.getRxCrcErr().getValue());
			jGen.writeNumberField("collisions", entry.getCollisions().getValue());
			if (OFVersion.OF_13 == entry.getVersion()) {
				jGen.writeNumberField("durationSec", entry.getDurationSec());
				jGen.writeNumberField("durationNsec", entry.getDurationNsec());
			}
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
				jGen.writeStringField("tableId", entry.getTableId().toString());
				jGen.writeNumberField("packetCount", entry.getPacketCount().getValue());
				jGen.writeNumberField("byteCount", entry.getByteCount().getValue());
				jGen.writeNumberField("durationSeconds", entry.getDurationSec());
				jGen.writeNumberField("priority", entry.getPriority());
				jGen.writeNumberField("idleTimeoutSec", entry.getIdleTimeout());
				jGen.writeNumberField("hardTimeoutSec", entry.getHardTimeout());
				switch (entry.getVersion()) {
				case OF_10:
					// flags not supported
					break;
				case OF_11:
					jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer11.toWireValue(entry.getFlags()));
					break;
				case OF_12:
					jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer12.toWireValue(entry.getFlags()));
					break;
				case OF_13:
					jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer13.toWireValue(entry.getFlags()));
					break;
				case OF_14:
					jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer14.toWireValue(entry.getFlags()));
					break;
				default:
					logger.error("Could not decode OFVersion {}", entry.getVersion());
					break;
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
		jGen.writeStringField("manufacturerDescription", descReply.getMfrDesc()); 
		jGen.writeStringField("hardwareDescription", descReply.getHwDesc()); 
		jGen.writeStringField("softwareDescription", descReply.getSwDesc()); 
		jGen.writeStringField("serialNumber", descReply.getSerialNum()); 
		jGen.writeStringField("datapathDescription", descReply.getDpDesc()); 
		jGen.writeEndObject(); // end match
	}

	public static void serializeAggregateReply(List<OFAggregateStatsReply> aggregateReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
		OFAggregateStatsReply aggregateReply = aggregateReplies.get(0); // There are only one aggregateReply from the switch
		jGen.writeObjectFieldStart("aggregate"); 
		jGen.writeStringField("version", aggregateReply.getVersion().toString()); //return the enum name
		jGen.writeNumberField("flowCount", aggregateReply.getFlowCount());
		jGen.writeNumberField("packetCount", aggregateReply.getPacketCount().getValue());
		jGen.writeNumberField("byteCount", aggregateReply.getByteCount().getValue());
		switch (aggregateReply.getVersion()) {
		case OF_10:
			jGen.writeNumberField("flags", OFStatsReplyFlagsSerializerVer10.toWireValue(aggregateReply.getFlags()));
			break;
		case OF_11:
			jGen.writeNumberField("flags", OFStatsReplyFlagsSerializerVer11.toWireValue(aggregateReply.getFlags()));
			break;
		case OF_12:
			jGen.writeNumberField("flags", OFStatsReplyFlagsSerializerVer12.toWireValue(aggregateReply.getFlags()));
			break;
		case OF_13:
			jGen.writeNumberField("flags", OFStatsReplyFlagsSerializerVer13.toWireValue(aggregateReply.getFlags()));
			break;
		case OF_14:
			jGen.writeNumberField("flags", OFStatsReplyFlagsSerializerVer14.toWireValue(aggregateReply.getFlags()));
			break;
		default:
			break;
		}
		jGen.writeEndObject(); // end match
	}

	public static void serializePortDescReply(List<OFPortDescStatsReply> portDescReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
		OFPortDescStatsReply portDescReply = portDescReplies.get(0); // we will get only one PortDescReply and it will contains many OFPortDescStatsEntry ?
		jGen.writeStringField("version", portDescReply.getVersion().toString()); //return the enum name
		serializePortDesc(portDescReply.getEntries(), jGen);
	}
	
	public static void serializePortDesc(List<OFPortDesc> portDescList, JsonGenerator jGen) throws IOException, JsonProcessingException {
		jGen.writeFieldName("portDesc");
		jGen.writeStartArray();
		for(OFPortDesc entry : portDescList) {
			jGen.writeStartObject();
			jGen.writeStringField("portNumber",entry.getPortNo().toString());
			jGen.writeStringField("hardwareAddress", entry.getHwAddr().toString());
			jGen.writeStringField("name", entry.getName());
			switch(entry.getVersion()) {
			case OF_10:
				jGen.writeNumberField("config", OFPortConfigSerializerVer10.toWireValue(entry.getConfig()));
				jGen.writeNumberField("state", OFPortStateSerializerVer10.toWireValue(entry.getState()));
				jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getCurr()));
				jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getAdvertised()));
				jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getSupported()));
				jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer10.toWireValue(entry.getPeer()));
				break;
			case OF_11:
				jGen.writeNumberField("config", OFPortConfigSerializerVer11.toWireValue(entry.getConfig()));
				jGen.writeNumberField("state", OFPortStateSerializerVer11.toWireValue(entry.getState()));
				jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getCurr()));
				jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getAdvertised()));
				jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getSupported()));
				jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer11.toWireValue(entry.getPeer()));
				break;
			case OF_12:
				jGen.writeNumberField("config", OFPortConfigSerializerVer12.toWireValue(entry.getConfig()));
				jGen.writeNumberField("state", OFPortStateSerializerVer12.toWireValue(entry.getState()));
				jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getCurr()));
				jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getAdvertised()));
				jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getSupported()));
				jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer12.toWireValue(entry.getPeer()));
				break;
			case OF_13:
				jGen.writeNumberField("config", OFPortConfigSerializerVer13.toWireValue(entry.getConfig()));
				jGen.writeNumberField("state", OFPortStateSerializerVer13.toWireValue(entry.getState()));
				jGen.writeNumberField("currentFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getCurr()));
				jGen.writeNumberField("advertisedFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getAdvertised()));
				jGen.writeNumberField("supportedFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getSupported()));
				jGen.writeNumberField("peerFeatures", OFPortFeaturesSerializerVer13.toWireValue(entry.getPeer()));
				break;
			case OF_14:
				// TODO
				logger.error("OF1.4 OFPortDesc serializer not implemented");
			}
			if (OFVersion.OF_10 != entry.getVersion()) {
				jGen.writeNumberField("currSpeed",entry.getCurrSpeed());
				jGen.writeNumberField("maxSpeed",entry.getMaxSpeed());
			}
			jGen.writeEndObject();
		}
		jGen.writeEndArray();
	}
} 
