package net.floodlightcontroller.core.web.serializers;

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

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionExperimenter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;

/**
 * Serialize an OFFlowMod into JSON format 
 * for output from the static flow pusher.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFFlowModSerializer extends JsonSerializer<OFFlowMod> {

    @Override
    public void serialize(OFFlowMod fm, JsonGenerator jGen, SerializerProvider serializer)
                                  throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeStringField("command", fm.getCommand().toString());
        jGen.writeStringField("buffer-id", fm.getBufferId().toString());
        jGen.writeStringField("cookie", fm.getCookie().toString());
        if (fm.getVersion() != OFVersion.OF_10) {
        	jGen.writeStringField("cookie-mask", fm.getCookieMask().toString());
        }
        jGen.writeStringField("flags", fm.getFlags().toString());
        jGen.writeNumberField("hard-timeout", fm.getHardTimeout());
        jGen.writeNumberField("idle-timeout", fm.getIdleTimeout());
        jGen.writeNumberField("priority", fm.getPriority());
        if (fm.getVersion() != OFVersion.OF_10) {
        	jGen.writeStringField("table-id", fm.getTableId().toString());
        }
        jGen.writeStringField("of-type", fm.getType().toString());
        jGen.writeStringField("of-version", fm.getVersion().toString());
        jGen.writeStringField("out-port", fm.getOutPort().toString());
        if (fm.getVersion() != OFVersion.OF_10) {
        	jGen.writeStringField("out-group", fm.getOutGroup().toString());
        }
        
        serializeMatch(fm.getMatch(), jGen);
        
        if (fm.getVersion() != OFVersion.OF_10) {
        	serializeInstructions(fm.getInstructions(), jGen);
        } else {
            serializeActions(fm.getActions(), "actions", jGen);
        }
      
        jGen.writeEndObject();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	public void serializeMatch(Match match, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if (match == null)
            jGen.writeStringField("match", "null");
        else {
            jGen.writeFieldName("match");
            jGen.writeStartArray();
            for (MatchField mf : match.getMatchFields()) {
                jGen.writeString(match.get(mf).toString());
            }
            jGen.writeEndArray();
        }
    }
    
	public void serializeInstructions(List<OFInstruction> instructions, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if (instructions == null)
            jGen.writeStringField("instructions", "null");
        else {
            jGen.writeFieldName("instructions");
            jGen.writeStartArray();
            for (OFInstruction instruction : instructions) {
            	switch (instruction.getType()) {
            	case APPLY_ACTIONS:
            		serializeActions(((OFInstructionApplyActions) instruction).getActions(), "apply-actions", jGen);
            		break;
            	case WRITE_ACTIONS:
            		serializeActions(((OFInstructionWriteActions) instruction).getActions(), "write-actions", jGen);
            		break;
            	case CLEAR_ACTIONS:
            		jGen.writeStringField("clear-actions", "n/a");
            		break;
            	case GOTO_TABLE:
            		jGen.writeStringField("goto-table", ((OFInstructionGotoTable) instruction).getTableId().toString());
            		break;
            	case METER:
            		jGen.writeNumberField("goto-meter", ((OFInstructionMeter) instruction).getMeterId());
            		break;
            	case WRITE_METADATA:
            		jGen.writeFieldName("write-metadata");
            		jGen.writeStartArray();
            		jGen.writeStringField("metadata", ((OFInstructionWriteMetadata) instruction).getMetadata().toString());
            		jGen.writeStringField("metadata-mask", ((OFInstructionWriteMetadata) instruction).getMetadataMask().toString());
            		jGen.writeEndArray();
            		break;
            	case EXPERIMENTER:
            		jGen.writeNumberField("metadata", ((OFInstructionExperimenter) instruction).getExperimenter());
            		break;
            	default:
            		jGen.writeStringField("unknown-instruction", "Could not determine instruction type during JSON output serialization");
            		break;
            	}
            }
            jGen.writeEndArray();
        }
    }
    
    public void serializeActions(List<OFAction> actions, String fieldName, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if (actions == null) {
        	jGen.writeStartObject();
            jGen.writeStringField(fieldName, "null");
            jGen.writeEndObject();
        } else {
        	jGen.writeStartObject();
            jGen.writeArrayFieldStart(fieldName);
            for (OFAction action : actions) {
                jGen.writeString(action.toString());
            }
            jGen.writeEndArray();
            jGen.writeEndObject();
        }
    }
}
