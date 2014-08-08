/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by Amer Tahir
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

package net.floodlightcontroller.firewall;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize a FirewallRule object
 * Implemented to output easily readable MAC, IP addresses
 * @author Jason Parraga
 */
public class FirewallRuleSerializer extends JsonSerializer<FirewallRule> {

    @Override
    public void serialize(FirewallRule rule, JsonGenerator jGen,
            SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeNumberField("ruleid", rule.ruleid);
        jGen.writeStringField("dpid", rule.dpid.toString());
        jGen.writeNumberField("in_port", rule.in_port.getPortNumber());
        jGen.writeStringField("dl_src", rule.dl_src.toString());
        jGen.writeStringField("dl_dst", rule.dl_dst.toString());
        jGen.writeNumberField("dl_type", rule.dl_type.getValue());
        jGen.writeStringField("nw_src_prefix", rule.nw_src_prefix_and_mask.getValue().toString());
        jGen.writeNumberField("nw_src_maskbits", rule.nw_src_prefix_and_mask.getMask().asCidrMaskLength());
        jGen.writeStringField("nw_dst_prefix", rule.nw_dst_prefix_and_mask.getValue().toString());
        jGen.writeNumberField("nw_dst_maskbits", rule.nw_dst_prefix_and_mask.getMask().asCidrMaskLength());
        jGen.writeNumberField("nw_proto", rule.nw_proto.getIpProtocolNumber());
        jGen.writeNumberField("tp_src", rule.tp_src.getPort());
        jGen.writeNumberField("tp_dst", rule.tp_dst.getPort());
        jGen.writeBooleanField("any_dpid", rule.any_dpid);
        jGen.writeBooleanField("any_in_port", rule.any_in_port);
        jGen.writeBooleanField("any_dl_src", rule.any_dl_src);
        jGen.writeBooleanField("any_dl_dst", rule.any_dl_dst);
        jGen.writeBooleanField("any_dl_type", rule.any_dl_type);
        jGen.writeBooleanField("any_nw_src", rule.any_nw_src);
        jGen.writeBooleanField("any_nw_dst", rule.any_nw_dst);
        jGen.writeBooleanField("any_nw_proto", rule.any_nw_proto);
        jGen.writeBooleanField("any_tp_src", rule.any_tp_src);
        jGen.writeBooleanField("any_tp_dst", rule.any_tp_dst);
        jGen.writeNumberField("priority", rule.priority);
        jGen.writeStringField("action", String.valueOf(rule.action));
        
        jGen.writeEndObject();
    }

}