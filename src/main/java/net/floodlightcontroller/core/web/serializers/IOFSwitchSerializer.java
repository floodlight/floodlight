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

package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.Collection;
import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFlowWildcards;
import org.projectfloodlight.openflow.protocol.OFVersion;

import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
/**
 * Serialize a IOFSwitch for more readable information
 */
public class IOFSwitchSerializer extends JsonSerializer<IOFSwitch> {

    @Override
    public void serialize(IOFSwitch sw, JsonGenerator jGen,
                          SerializerProvider serializer)
                                  throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeStringField("dpid",sw.getId().toString());
        serializeCapabilities(sw.getCapabilities(),jGen);
        serializeDescription(sw.getSwitchDescription(),jGen);
        serializeHarole(sw.getControllerRole(),jGen);
        serializeActions(sw.getActions(),jGen);
        serializeAttributes(sw.getAttributes(),jGen);
        serializePorts(sw.getPorts(),jGen);
        jGen.writeNumberField("buffers",sw.getBuffers());
        jGen.writeStringField("inetAddress",sw.getInetAddress().toString());
        jGen.writeNumberField("tables",sw.getTables());
        jGen.writeNumberField("connectedSince",sw.getConnectedSince().getTime());
        jGen.writeEndObject();
    }

    public void serializeActions(Set<OFActionType> actions, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if ( null == actions)
            jGen.writeStringField("actions","null");
        else{
            jGen.writeFieldName("actions");
            jGen.writeStartArray();
            for(OFActionType action : actions){
                jGen.writeString(action.toString());
            }
            jGen.writeEndArray();
        }
    }

    @SuppressWarnings("unchecked")
	public void serializeAttributes(Map<Object, Object> attributes, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if ( null == attributes)
            jGen.writeStringField("attributes","null");
        else{
            jGen.writeFieldName("attributes");
            jGen.writeStartObject();
            for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
                if( entry.getValue() instanceof EnumSet<?>){
                    jGen.writeFieldName(entry.getKey().toString());
                    jGen.writeStartArray();
                    //Maybe need to check other type.
                    for(OFFlowWildcards  wildcard : (EnumSet<OFFlowWildcards>)entry.getValue()){
                        jGen.writeString(wildcard.toString());
                    }
                    jGen.writeEndArray();
                }
                else
                    jGen.writeStringField(entry.getKey().toString(),entry.getValue().toString());
            }
            jGen.writeEndObject();
        }
    }
    public void serializePorts(Collection<OFPortDesc> portDecs, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
            if ( portDecs == null)
                jGen.writeStringField("ports","null");
            else{
                jGen.writeFieldName("ports");
                jGen.writeStartArray();
                for(OFPortDesc port : portDecs){
                    jGen.writeStartObject();
                    jGen.writeNumberField("PortNo",port.getPortNo().getPortNumber());
                    jGen.writeStringField("HwAddr",port.getHwAddr().toString());
                    jGen.writeStringField("Name",port.getName());
                    if ( port.getVersion() != OFVersion.OF_10){
                        jGen.writeNumberField("CurrSpeed",port.getCurrSpeed());
                        jGen.writeNumberField("MaxSpeed",port.getMaxSpeed());
                    }
                    jGen.writeFieldName("config");
                    jGen.writeStartArray();
                    for(OFPortConfig config : port.getConfig()){
                        jGen.writeString(config.toString());
                    }
                    jGen.writeEndArray();
                    jGen.writeFieldName("state");
                    jGen.writeStartArray();
                    for(OFPortState state : port.getState()){
                        jGen.writeString(state.toString());
                    }
                    jGen.writeEndArray();

                    jGen.writeFieldName("curr");
                    jGen.writeStartArray();
                    for(OFPortFeatures curr : port.getCurr()){
                        jGen.writeString(curr.toString());
                    }
                    jGen.writeEndArray();
                    jGen.writeFieldName("advertised");
                    jGen.writeStartArray();
                    for(OFPortFeatures advertised : port.getAdvertised()){
                        jGen.writeString(advertised.toString());
                    }
                    jGen.writeEndArray();
                    jGen.writeFieldName("supported");
                    jGen.writeStartArray();
                    for(OFPortFeatures support : port.getSupported()){
                        jGen.writeString(support.toString());
                    }
                    jGen.writeEndArray();
                    jGen.writeFieldName("peer");
                    jGen.writeStartArray();
                    for(OFPortFeatures peer : port.getPeer()){
                        jGen.writeString(peer.toString());
                    }
                    jGen.writeEndArray();
                    jGen.writeEndObject();
                }
                jGen.writeEndArray();
        
            }
    }
    public void serializeDescription(SwitchDescription swDescription, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if( null == swDescription)
            jGen.writeStringField("description","null");
        else{
            jGen.writeFieldName("description");
            jGen.writeStartObject();
            jGen.writeStringField("datapath",swDescription.getDatapathDescription());
            jGen.writeStringField("hardware",swDescription.getHardwareDescription());
            jGen.writeStringField("manufacturer",swDescription.getManufacturerDescription());
            jGen.writeStringField("serialNum",swDescription.getSerialNumber());
            jGen.writeStringField("software",swDescription.getSoftwareDescription());
            jGen.writeEndObject();
        }
    }
    public void serializeCapabilities(Set<OFCapabilities> ofCapabilities, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if (null == ofCapabilities)
            jGen.writeStringField("capabilities","null");
        else{
            jGen.writeFieldName("capabilities");
            jGen.writeStartArray();
            for(OFCapabilities ofCapability : ofCapabilities){
                jGen.writeString(ofCapability.toString());
            }
            jGen.writeEndArray();
        }
    }
    public void serializeHarole(OFControllerRole role, JsonGenerator jGen)
            throws IOException, JsonProcessingException {
        if ( null == role )
            jGen.writeStringField("harole","null");
        else
            jGen.writeStringField("harole",HARole.ofOFRole(role).toString());
    }
}
