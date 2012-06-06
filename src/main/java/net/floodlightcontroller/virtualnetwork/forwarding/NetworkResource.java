package net.floodlightcontroller.virtualnetwork.forwarding;

import java.io.IOException;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.virtualnetwork.IVirtualNetworkService;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.resource.Delete;
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
                    if (field.equals("name")) {
                        network.name = jp.getText();
                    } else if (field.equals("gateway")) {
                        network.gateway = jp.getText();
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
    
    
    @Put
    @Post
    public void createNetwork(String postData) {        
        String guid = (String) getRequestAttributes().get("network");
        NetworkDefinition network = new NetworkDefinition();
        network.guid = guid;
        
        try {
            jsonToNetworkDefinition(postData, network);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        
        Integer gw = null;
        if (network.gateway != null) {
            try {
                gw = IPv4.toIPv4Address(network.gateway);
            } catch (IllegalArgumentException e) {
                log.warn("Could not parse gateway {} as IP for network {}",
                         network.name, network.gateway);
            }
        }
        vns.createNetwork(network.guid, network.name, gw);
    }
    
    @Delete
    public void deleteNetwork() {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        String guid = (String) getRequestAttributes().get("network");
        vns.deleteNetwork(guid);
    }
}
