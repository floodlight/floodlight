package net.floodlightcontroller.virtualnetwork.forwarding;

import java.io.IOException;

import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.virtualnetwork.IVirtualNetworkService;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
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
                        host.guid = jp.getText();
                    } else if (field.equals("mac")) {
                        host.mac = jp.getText();
                    }
                }
            }
        }
        
        jp.close();
    }
    
    @Put
    public void addHost(String postData) {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        HostDefinition host = new HostDefinition();
        host.port = (String) getRequestAttributes().get("port");
        try {
            jsonToHostDefinition(postData, host);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        vns.addHost(MACAddress.valueOf(host.mac), host.guid, host.port);
    }
    
    
    @Delete
    public void deleteHost() {
        String port = (String) getRequestAttributes().get("port");
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        vns.deleteHost(null, port);
    }
}
