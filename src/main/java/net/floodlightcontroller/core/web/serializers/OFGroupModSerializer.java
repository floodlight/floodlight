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

import net.floodlightcontroller.util.GroupUtils;

import org.projectfloodlight.openflow.protocol.OFGroupMod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialize any OFGroupMod in JSON.
 * 
 * Use automatically by Jackson via JsonSerialize(using=OFGroupModSerializer.class),
 * or use the static function within this class within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFGroupModSerializer extends JsonSerializer<OFGroupMod> {
    protected static Logger logger = LoggerFactory.getLogger(OFGroupModSerializer.class);

	@Override
	public void serialize(OFGroupMod gm, JsonGenerator jGen, SerializerProvider serializer)
			throws IOException, JsonProcessingException {
		serializeGroupMod(jGen, gm);
	}

	public static void serializeGroupMod(JsonGenerator jGen, OFGroupMod groupMod) throws IOException, JsonProcessingException {
		
        jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true); // IMHO this just looks nicer and is easier to read if everything is quoted
		
		jGen.writeStartObject();
		jGen.writeStringField("version", groupMod.getVersion().toString()); // return the enum names
		jGen.writeStringField("command", groupMod.getCommand().toString());
		jGen.writeNumberField("group_number", groupMod.getGroup().getGroupNumber());
		switch (groupMod.getGroupType()) {
		case ALL:
			jGen.writeStringField(GroupUtils.GROUP_TYPE, GroupUtils.GROUP_TYPE_ALL);
			break;
		case FF:
			jGen.writeStringField(GroupUtils.GROUP_TYPE, GroupUtils.GROUP_TYPE_FF);
			break;
		case INDIRECT:
			jGen.writeStringField(GroupUtils.GROUP_TYPE, GroupUtils.GROUP_TYPE_INDIRECT);
			break;
		case SELECT:
			jGen.writeStringField(GroupUtils.GROUP_TYPE, GroupUtils.GROUP_TYPE_SELECT);
			break;
		default:
			logger.error("Omitting unknown group type {}", groupMod.getGroupType());
			break;
		}
		jGen.writeStringField("version", groupMod.getVersion().toString());
		jGen.writeNumberField("xid", groupMod.getXid());
		jGen.writeFieldName(GroupUtils.GROUP_BUCKETS);
		GroupUtils.groupBucketsToJsonArray(jGen, groupMod.getBuckets());
		jGen.writeEndObject();
	}
}