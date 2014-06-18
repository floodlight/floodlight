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

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.openflow.util.HexString;

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
        jGen.writeStringField("dpid", HexString.toHexString(rule.dpid));
        jGen.writeNumberField("in_port", rule.in_port);
        jGen.writeStringField("dl_src",String.valueOf(MACAddress.valueOf(rule.dl_src)));
        jGen.writeStringField("dl_dst", String.valueOf(MACAddress.valueOf(rule.dl_dst)));
        jGen.writeNumberField("dl_type", rule.dl_type);
        jGen.writeStringField("nw_src_prefix", IPv4.fromIPv4Address(rule.nw_src_prefix));
        jGen.writeNumberField("nw_src_maskbits", rule.nw_src_maskbits);
        jGen.writeStringField("nw_dst_prefix", IPv4.fromIPv4Address(rule.nw_dst_prefix));
        jGen.writeNumberField("nw_dst_maskbits", rule.nw_dst_maskbits);
        jGen.writeNumberField("nw_proto", rule.nw_proto);
        jGen.writeNumberField("tp_src", rule.tp_src);
        jGen.writeNumberField("tp_dst", rule.tp_dst);
        jGen.writeBooleanField("wildcard_dpid", rule.wildcard_dpid);
        jGen.writeBooleanField("wildcard_in_port", rule.wildcard_in_port);
        jGen.writeBooleanField("wildcard_dl_src", rule.wildcard_dl_src);
        jGen.writeBooleanField("wildcard_dl_dst", rule.wildcard_dl_dst);
        jGen.writeBooleanField("wildcard_dl_type", rule.wildcard_dl_type);
        jGen.writeBooleanField("wildcard_nw_src", rule.wildcard_nw_src);
        jGen.writeBooleanField("wildcard_nw_dst", rule.wildcard_nw_dst);
        jGen.writeBooleanField("wildcard_nw_proto", rule.wildcard_nw_proto);
        jGen.writeBooleanField("wildcard_tp_src", rule.wildcard_tp_src);
        jGen.writeBooleanField("wildcard_tp_dst", rule.wildcard_tp_dst);
        jGen.writeNumberField("priority", rule.priority);
        jGen.writeStringField("action", String.valueOf(rule.action));
        
        jGen.writeEndObject();
    }

}