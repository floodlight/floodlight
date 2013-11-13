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

import net.floodlightcontroller.util.MACAddress;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Put;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HostResource extends org.restlet.resource.ServerResource {
    protected static Logger log = LoggerFactory.getLogger(HostResource.class);
    
    public class HostDefinition {
        String port = null; // Logical port name
        String guid = null; // Network ID
        String mac = null; // MAC Address
        String attachment = null; // Attachment name
    }
    
    protected void jsonToHostDefinition(String json, HostDefinition host) throws IOException {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(json);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            else if (n.equals("attachment")) {
                while (jp.nextToken() != JsonToken.END_OBJECT) {
                    String field = jp.getCurrentName();
                    if (field.equals("id")) {
                        host.attachment = jp.getText();
                    } else if (field.equals("mac")) {
                        host.mac = jp.getText();
                    }
                }
            }
        }
        
        jp.close();
    }
    
    @Put
    public String addHost(String postData) {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        HostDefinition host = new HostDefinition();
        host.port = (String) getRequestAttributes().get("port");
        host.guid = (String) getRequestAttributes().get("network");
        try {
            jsonToHostDefinition(postData, host);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        vns.addHost(MACAddress.valueOf(host.mac), host.guid, host.port);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
    
    
    @Delete
    public String deleteHost() {
        String port = (String) getRequestAttributes().get("port");
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        vns.deleteHost(null, port);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
}
