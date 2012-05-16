/**
*    Copyright 2011, Big Switch Networks, Inc. 
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

package net.floodlightcontroller.util;

import java.io.IOException;

import java.sql.Timestamp;


import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;


/**
 * @author subrata
 *
 */

public class EventHistoryBaseInfoJSONSerializer extends 
                                    JsonSerializer<EventHistoryBaseInfo> {

 
    /**
     * Performs the serialization of a EventHistory.BaseInfo object
     */
    @Override
    public void serialize(EventHistoryBaseInfo base_info, JsonGenerator jGen,
                    SerializerProvider serializer) 
                    throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeNumberField("Idx",    base_info.getIdx());
        Timestamp ts = new Timestamp(base_info.getTime_ms());
        String tsStr = ts.toString();
        while (tsStr.length() < 23) {
            tsStr = tsStr.concat("0");
        }
        jGen.writeStringField("Time", tsStr);
        jGen.writeStringField("State",  base_info.getState().name());
        String acStr = base_info.getAction().name().toLowerCase();
        // Capitalize the first letter
        acStr = acStr.substring(0,1).toUpperCase().concat(acStr.substring(1));
        jGen.writeStringField("Action", acStr);
        jGen.writeEndObject();
    }

    /**
     * Tells SimpleModule that we are the serializer for OFMatch
     */
    @Override
    public Class<EventHistoryBaseInfo> handledType() {
        return EventHistoryBaseInfo.class;
    }
}
