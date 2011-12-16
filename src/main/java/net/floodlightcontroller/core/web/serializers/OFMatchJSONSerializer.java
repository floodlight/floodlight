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
import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;

public class OFMatchJSONSerializer extends JsonSerializer<OFMatch> {

    /**
     * Converts an IP in a 32 bit integer to a dotted-decimal string
     * @param i The IP address in a 32 bit integer
     * @return An IP address string in dotted-decimal
     */
    private String intToIp(int i) {
        return ((i >> 24 ) & 0xFF) + "." +
               ((i >> 16 ) & 0xFF) + "." +
               ((i >>  8 ) & 0xFF) + "." +
               ( i        & 0xFF);
    }
    
    /**
     * Performs the serialization of a OFMatch object
     */
    @Override
    public void serialize(OFMatch match, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeStringField("dataLayerDestination", HexString.toHexString(match.getDataLayerDestination()));
        jGen.writeStringField("dataLayerSource", HexString.toHexString(match.getDataLayerSource()));
        jGen.writeNumberField("dataLayerType", match.getDataLayerType());
        jGen.writeNumberField("dataLayerVirtualLan", match.getDataLayerVirtualLan());
        jGen.writeNumberField("dataLayerVirtualLanPriorityCodePoint", match.getDataLayerVirtualLanPriorityCodePoint());
        jGen.writeNumberField("inputPort", match.getInputPort());
        jGen.writeStringField("networkDestination", intToIp(match.getNetworkDestination()));
        jGen.writeNumberField("networkDestinationMaskLen", match.getNetworkDestinationMaskLen());
        jGen.writeNumberField("networkProtocol", match.getNetworkProtocol());
        jGen.writeStringField("networkSource", intToIp(match.getNetworkSource()));
        jGen.writeNumberField("networkSourceMaskLen", match.getNetworkSourceMaskLen());
        jGen.writeNumberField("networkTypeOfService", match.getNetworkTypeOfService());
        jGen.writeNumberField("transportDestination", match.getTransportDestination());
        jGen.writeNumberField("transportSource", match.getTransportSource());
        jGen.writeNumberField("wildcards", match.getWildcards());
        jGen.writeEndObject();
    }
    
    /**
     * Tells SimpleModule that we are the serializer for OFMatch
     */
    @Override
    public Class<OFMatch> handledType() {
        return OFMatch.class;
    }
}
