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

package net.floodlightcontroller.loadbalancer;

import java.io.IOException;

import org.projectfloodlight.openflow.types.IPv4Address;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class LBMemberSerializer extends JsonSerializer<LBMember>{

    @Override
    public void serialize(LBMember member, JsonGenerator jGen,
                          SerializerProvider serializer) throws IOException,
                                                  JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("id", member.id);
        jGen.writeStringField("address", String.valueOf(IPv4Address.of(member.address)));
        jGen.writeStringField("port", Short.toString(member.port));
        jGen.writeStringField("poolId", member.poolId);
        jGen.writeStringField("vipId", member.vipId);
        jGen.writeStringField("weight", Short.toString(member.weight));
        if(member.status == 0){
        	jGen.writeStringField("status", "Alive");
        }
        if(member.status == 1){
        	jGen.writeStringField("status", "Active");
        }
        if(member.status == -1){
        	jGen.writeStringField("status", "Inactive");
        }
        
        jGen.writeEndObject();
    }

}
