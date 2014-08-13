/**
*    Copyright 2012 Big Switch Networks, Inc. 
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

package net.floodlightcontroller.devicemanager.web;

import java.io.IOException;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.Device;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize a device object
 */
public class DeviceSerializer extends JsonSerializer<Device> {

    @Override
    public void serialize(Device device, JsonGenerator jGen,
                          SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("entityClass", device.getEntityClass().getName());
        
        jGen.writeArrayFieldStart("mac");
        jGen.writeString(device.getMACAddress().toString());
        jGen.writeEndArray();

        jGen.writeArrayFieldStart("ipv4");
        for (IPv4Address ip : device.getIPv4Addresses())
            jGen.writeString(ip.toString());
        jGen.writeEndArray();

        jGen.writeArrayFieldStart("vlan");
        for (VlanVid vlan : device.getVlanId())
            if (vlan.getVlan() >= 0)
                jGen.writeString(vlan.toString());
        jGen.writeEndArray();
        jGen.writeArrayFieldStart("attachmentPoint");
        for (SwitchPort ap : device.getAttachmentPoints(true)) {
            serializer.defaultSerializeValue(ap, jGen);
        }
        jGen.writeEndArray();

        jGen.writeNumberField("lastSeen", device.getLastSeen().getTime());
        
        String dhcpClientName = device.getDHCPClientName();
        if (dhcpClientName != null) {
            jGen.writeStringField("dhcpClientName", dhcpClientName);
        }

        jGen.writeEndObject();
    }

}
