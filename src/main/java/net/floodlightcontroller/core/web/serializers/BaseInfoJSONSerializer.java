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

package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.Date;

import net.floodlightcontroller.util.EventHistory.BaseInfo;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

/**
 * @author subrata
 *
 */

public class BaseInfoJSONSerializer extends 
                                    JsonSerializer<BaseInfo> {

 
    /**
     * Performs the serialization of a EventHistory.BaseInfo object
     */
    @Override
    public void serialize(BaseInfo base_info, JsonGenerator jGen,
                    SerializerProvider serializer) 
                    throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeNumberField("Idx",    base_info.getIdx());
        jGen.writeStringField("Time",   
                            convertNanoSecondsToStr(base_info.getTime_ns()));
        jGen.writeStringField("State",  base_info.getState().name());
        jGen.writeStringField("Action", base_info.getAction().name());
        jGen.writeEndObject();
    }

    /**
     * Tells SimpleModule that we are the serializer for OFMatch
     */
    @Override
    public Class<BaseInfo> handledType() {
        return BaseInfo.class;
    }
    
    public String convertNanoSecondsToStr(long nanoSeconds) {
        long millisecs = nanoSeconds / 1000000;
        String timeStr = (new Date(millisecs)).toString();
        return timeStr;
    }
}
