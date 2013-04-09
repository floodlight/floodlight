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
import java.util.Collection;

import net.floodlightcontroller.packet.IPv4;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(NetworkResource.class);
    
    public class NetworkDefinition {
        public String name = null;
        public String guid = null;
        public String gateway = null;
    }
    
    protected void jsonToNetworkDefinition(String json, NetworkDefinition network) throws IOException {
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
            else if (n.equals("network")) {
                while (jp.nextToken() != JsonToken.END_OBJECT) {
                    String field = jp.getCurrentName();
                    if (field == null) continue;
                    if (field.equals("name")) {
                        network.name = jp.getText();
                    } else if (field.equals("gateway")) {
                    	String gw = jp.getText();
                    	if ((gw != null) && (!gw.equals("null")))
                    		network.gateway = gw;
                    } else if (field.equals("id")) {
                    	network.guid = jp.getText();
                    } else {
                        log.warn("Unrecognized field {} in " +
                        		"parsing network definition", 
                        		jp.getText());
                    }
                }
            }
        }
        
        jp.close();
    }
    
    @Get("json")
    public Collection <VirtualNetwork> retrieve() {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        
        return vns.listNetworks();               
    }
    
    @Put
    @Post
    public String createNetwork(String postData) {        
        NetworkDefinition network = new NetworkDefinition();
        try {
            jsonToNetworkDefinition(postData, network);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        
        // We try to get the ID from the URI only if it's not
        // in the POST data 
        if (network.guid == null) {
	        String guid = (String) getRequestAttributes().get("network");
	        if ((guid != null) && (!guid.equals("null")))
	        	network.guid = guid;
        }
        
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        
        Integer gw = null;
        if (network.gateway != null) {
            try {
                gw = IPv4.toIPv4Address(network.gateway);
            } catch (IllegalArgumentException e) {
                log.warn("Could not parse gateway {} as IP for network {}, setting as null",
                         network.gateway, network.name);
                network.gateway = null;
            }
        }
        vns.createNetwork(network.guid, network.name, gw);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
    
    @Delete
    public String deleteNetwork() {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        String guid = (String) getRequestAttributes().get("network");
        vns.deleteNetwork(guid);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
}
