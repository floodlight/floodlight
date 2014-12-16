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

import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.ver13.OFFlowModFlagsSerializerVer13;
// Use Loxigen's serializer
import org.projectfloodlight.openflow.protocol.ver13.OFPortFeaturesSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver12.OFFlowModFlagsSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver12.OFPortFeaturesSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFFlowModFlagsSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver11.OFPortFeaturesSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFFlowModFlagsSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver10.OFPortFeaturesSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver13.OFPortStateSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver12.OFPortStateSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFPortStateSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFPortStateSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver13.OFPortConfigSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFFlowModFlagsSerializerVer14;
import org.projectfloodlight.openflow.protocol.ver12.OFPortConfigSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver11.OFPortConfigSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver10.OFPortConfigSerializerVer10;
import org.projectfloodlight.openflow.protocol.OFAggregateStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
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
            jGen.writeStringField("1)", "Invalid DPID and/or stats/features request, or");
            jGen.writeStringField("2)", "The switch might also be disconncted from the controller");
            jGen.writeEndObject();
            jGen.writeObjectFieldStart("Valid OFStatsTypes are");
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
            jGen.writeStringField("B)", OFStatsTypeStrings.PORT);
            jGen.writeStringField("C)", OFStatsTypeStrings.PORT_DESC);
            jGen.writeStringField("D)", OFStatsTypeStrings.QUEUE);
            jGen.writeStringField("E)", OFStatsTypeStrings.TABLE);
            jGen.writeStringField("F)", OFStatsTypeStrings.TABLE_FEATURES);
            jGen.writeEndObject(); 
            jGen.writeEndObject();
            return;
        }
        
        jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true); // IMHO this just looks nicer and is easier to read if everything is quoted
        jGen.writeStartObject();
        //jGen.writeStringField("dpid", reply.getDatapathId().toString());
        switch (reply.getStatType()) {
        case PORT:
            // handle port
            serializePortReply((List<OFPortStatsReply>) reply.getValues(), jGen);
            break;
        case QUEUE:
            // handle queue
            break;
        case FLOW:
            // handle flow. Can safely cast to List<OFFlowStatsReply>.
            serializeFlowReply((List<OFFlowStatsReply>) reply.getValues(), jGen);
            break;
        case AGGREGATE:
            // handle aggregate
            serializeAggregateReply((List<OFAggregateStatsReply>) reply.getValues(), jGen);
            break;
        case DESC:
            // handle desc
            serializeDescReply((List<OFDescStatsReply>) reply.getValues(), jGen);
            break;
        case TABLE:
            // handle table
            break;
        case TABLE_FEATURES:
            // handle features
            break;
            // TODO need to handle new OF1.1+ stats reply types
        case EXPERIMENTER:
            break;
        case GROUP:
            break;
        case GROUP_DESC:
            break;
        case GROUP_FEATURES:
            break;
        case METER:
            break;
        case METER_CONFIG:
            break;
        case METER_FEATURES:
            break;
        case PORT_DESC:
            serializePortDescReply((List<OFPortDescStatsReply>) reply.getValues(), jGen);
            break;
        default:
             break;
        }   
        jGen.writeEndObject();
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
        for (OFFlowStatsReply flowReply : flowReplies) { // for each flow stats reply
            //Dose the switch will reply multiple OFFlowStatsReply ?
            //Or we juse need to use the first item of the list.
            List<OFFlowStatsEntry> entries = flowReply.getEntries();
            jGen.writeFieldName("flows");
            jGen.writeStartArray();
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
                		jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer10.toWireValue(entry.getFlags()));
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
                	OFActionListSerializer.serializeActions(jGen, entry.getActions());
                } else {
                   OFInstructionListSerializer.serializeInstructionList(jGen, entry.getInstructions());
                }
                    
                jGen.writeEndObject();
            } // end for each OFFlowStatsReply entry
            jGen.writeEndArray();
        } // end for each OFStatsReply
    } // end method

    public static void serializeDescReply(List<OFDescStatsReply> descReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFDescStatsReply descReply = descReplies.get(0); // There are only one descReply from the switch
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
        jGen.writeEndObject(); // end match
    }

    public static void serializePortDescReply(List<OFPortDescStatsReply> portDescReplies, JsonGenerator jGen) throws IOException, JsonProcessingException{
        OFPortDescStatsReply portDescReply = portDescReplies.get(0); // we will get only one PortDescReply and it will contains many OFPortDescStatsEntry ?
        jGen.writeStringField("version", portDescReply.getVersion().toString()); //return the enum name
        jGen.writeFieldName("portDesc");
        jGen.writeStartArray();
        for(OFPortDesc entry : portDescReply.getEntries()) {
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
