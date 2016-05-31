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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.ver11.OFFlowModFlagsSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver12.OFFlowModFlagsSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver13.OFFlowModFlagsSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFFlowModFlagsSerializerVer14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialize any OFFlowMod in JSON.
 * 
 * Use automatically by Jackson via JsonSerialize(using=OFFlowModSerializer.class),
 * or use the static function within this class within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFFlowModSerializer extends JsonSerializer<OFFlowMod> {
	protected static Logger logger = LoggerFactory.getLogger(OFFlowModSerializer.class);

	@Override
	public void serialize(OFFlowMod fm, JsonGenerator jGen, SerializerProvider serializer)
			throws IOException, JsonProcessingException {
		serializeFlowMod(jGen, fm);
	}

	public static void serializeFlowMod(JsonGenerator jGen, OFFlowMod flowMod) throws IOException, JsonProcessingException {

		jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true); // IMHO this just looks nicer and is easier to read if everything is quoted

		jGen.writeStartObject();
		jGen.writeStringField("version", flowMod.getVersion().toString()); // return the enum names
		jGen.writeStringField("command", flowMod.getCommand().toString());
		jGen.writeNumberField("cookie", flowMod.getCookie().getValue());
		jGen.writeNumberField("priority", flowMod.getPriority());
		jGen.writeNumberField("idleTimeoutSec", flowMod.getIdleTimeout());
		jGen.writeNumberField("hardTimeoutSec", flowMod.getHardTimeout());
		jGen.writeStringField("outPort", flowMod.getOutPort().toString());

		switch (flowMod.getVersion()) {
		case OF_10:
			break;
		case OF_11:
			jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer11.toWireValue(flowMod.getFlags()));
			jGen.writeNumberField("cookieMask", flowMod.getCookieMask().getValue());
			jGen.writeStringField("outGroup", flowMod.getOutGroup().toString());
			jGen.writeStringField("tableId", flowMod.getTableId().toString());
			break;
		case OF_12:
			jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer12.toWireValue(flowMod.getFlags()));
			jGen.writeNumberField("cookieMask", flowMod.getCookieMask().getValue());
			jGen.writeStringField("outGroup", flowMod.getOutGroup().toString());
			jGen.writeStringField("tableId", flowMod.getTableId().toString());
			break;
		case OF_13:
			jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer13.toWireValue(flowMod.getFlags()));
			jGen.writeNumberField("cookieMask", flowMod.getCookieMask().getValue());
			jGen.writeStringField("outGroup", flowMod.getOutGroup().toString());
			break;
		case OF_14:
			jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer14.toWireValue(flowMod.getFlags()));
			jGen.writeNumberField("cookieMask", flowMod.getCookieMask().getValue());
			jGen.writeStringField("outGroup", flowMod.getOutGroup().toString());
			jGen.writeStringField("tableId", flowMod.getTableId().toString());
			break;
		case OF_15:
			jGen.writeNumberField("flags", OFFlowModFlagsSerializerVer14.toWireValue(flowMod.getFlags()));
			jGen.writeNumberField("cookieMask", flowMod.getCookieMask().getValue());
			jGen.writeStringField("outGroup", flowMod.getOutGroup().toString());
			jGen.writeStringField("tableId", flowMod.getTableId().toString());
			break;
		default:
			logger.error("Unimplemented serializer for OFVersion {}", flowMod.getVersion());
			break;
		}

		MatchSerializer.serializeMatch(jGen, flowMod.getMatch());

		// handle OF1.1+ instructions with actions within
		if (flowMod.getVersion() == OFVersion.OF_10) {
			jGen.writeObjectFieldStart("actions");
			OFActionListSerializer.serializeActions(jGen, flowMod.getActions());
			jGen.writeEndObject();
		} else {
			OFInstructionListSerializer.serializeInstructionList(jGen, flowMod.getInstructions());
		} // end not-empty instructions (else)
		jGen.writeEndObject();
	} // end method
}
