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

package net.floodlightcontroller.routing.web.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.core.types.NodePortTuple;

import java.io.IOException;

public class PathSerializer extends JsonSerializer<Path> {

    @Override
    public void serialize(Path path, JsonGenerator jGen, SerializerProvider serializer)
            throws IOException, JsonProcessingException {
        jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);

        jGen.writeStartObject();
        jGen.writeStringField("src_dpid", path.getId().getSrc().toString());
        jGen.writeStringField("dst_dpid", path.getId().getDst().toString());
        jGen.writeStringField("hop_count", Integer.toString(path.getHopCount()));
        jGen.writeNumberField("latency", path.getLatency().getValue()); // Might be an issue if value exceed what unsigned long can hold
        jGen.writeNumberField("path_index", path.getPathIndex());
        jGen.writeFieldName("path");
        jGen.writeStartArray();
        for (NodePortTuple npt : path.getPath()) {
            jGen.writeStartObject();
            jGen.writeStringField("dpid", npt.getNodeId().toString());
            jGen.writeNumberField("port", npt.getPortId().getPortNumber());
            jGen.writeEndObject();
        }
        jGen.writeEndArray();
        jGen.writeEndObject();
    }
}