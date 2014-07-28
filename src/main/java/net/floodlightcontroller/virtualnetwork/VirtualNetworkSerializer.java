/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.virtualnetwork;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import org.projectfloodlight.openflow.types.MacAddress;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize a VirtualNetwork object
 * @author KC Wang
 */
public class VirtualNetworkSerializer extends JsonSerializer<VirtualNetwork> {

    @Override
    public void serialize(VirtualNetwork vNet, JsonGenerator jGen,
            SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("name", vNet.name);
        jGen.writeStringField("guid", vNet.guid);
        jGen.writeStringField("gateway", vNet.gateway);

        jGen.writeArrayFieldStart("portMac");
		Iterator<Entry<String, MacAddress>> entries = vNet.portToMac.entrySet().iterator();
		while (entries.hasNext()){
			jGen.writeStartObject();
			Entry<String, MacAddress> entry = entries.next();
			jGen.writeStringField("port",entry.getKey().toString());
			jGen.writeStringField("mac",entry.getValue().toString());
			jGen.writeEndObject();
		}
        jGen.writeEndArray();
        
        jGen.writeEndObject();
    }

}
