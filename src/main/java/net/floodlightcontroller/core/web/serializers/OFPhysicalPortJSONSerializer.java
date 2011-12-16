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

package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;

public class OFPhysicalPortJSONSerializer extends JsonSerializer<OFPhysicalPort> {

    /**
     * Performs the serialization of a OFPhysicalPort object
     */
    @Override
    public void serialize(OFPhysicalPort port, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeNumberField("advertisedFeatures", port.getAdvertisedFeatures());
        jGen.writeNumberField("config", port.getConfig());
        jGen.writeNumberField("currentFeatures", port.getCurrentFeatures());
        jGen.writeStringField("hardwareAddress", HexString.toHexString(port.getHardwareAddress()));
        jGen.writeStringField("name", port.getName());
        jGen.writeNumberField("peerFeatures", port.getPeerFeatures());
        jGen.writeNumberField("portNumber", port.getPortNumber());
        jGen.writeNumberField("state", port.getState());
        jGen.writeNumberField("supportedFeatures", port.getSupportedFeatures());
        jGen.writeEndObject();
    }
    
    /**
     * Tells SimpleModule that we are the serializer for OFPhysicalPort
     */
    @Override
    public Class<OFPhysicalPort> handledType() {
        return OFPhysicalPort.class;
    }

}
