package net.floodlightcontroller.firewall;

import java.io.IOException;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.resource.Post;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.firewall.Firewall;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;


public class FirewallResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(FirewallResource.class);

    @Get("json")
    public Object handleRequest() {
        String op = (String) getRequestAttributes().get("op");
        IFirewallService firewall = 
                (IFirewallService)getContext().getAttributes().
                get(IFirewallService.class.getCanonicalName());

        if (op.equalsIgnoreCase("enable")) {
            firewall.enableFirewall();
            return "{\"status\" : \"success\", \"details\" : \"firewall running\"}";
        } else if (op.equalsIgnoreCase("disable")) {
            firewall.disableFirewall();
            return "{\"status\" : \"success\", \"details\" : \"firewall stopped\"}";
        } else if (op.equalsIgnoreCase("storageRules")) {
            return firewall.getStorageRules();
        } else if (op.equalsIgnoreCase("subnet-mask")) {
            return firewall.getSubnetMask();
        }

        return "{\"status\" : \"failure\", \"details\" : \"invalid operation\"}";
    }
    
    /**
     * Allows setting of subnet mask
     * @param fmJson The Subnet Mask in JSON format.
     * @return A string status message
     */
    @Post
    public String handlePost(String fmJson) {
        IFirewallService firewall = 
                (IFirewallService)getContext().getAttributes().
                get(IFirewallService.class.getCanonicalName());

        String newMask;
        try {
            newMask = jsonExtractSubnetMask(fmJson);
        } catch (IOException e) {
            log.error("Error parsing new subnet mask: " + fmJson, e);
            e.printStackTrace();
            return "{\"status\" : \"Error! Could not parse new subnet mask, see log for details.\"}";
        }
        firewall.setSubnetMask(newMask);
        return ("{\"status\" : \"subnet mask set\"}");
    }
    
    /**
     * Extracts subnet mask from a JSON string
     * @param fmJson The JSON formatted string
     * @return The subnet mask
     * @throws IOException If there was an error parsing the JSON
     */
    public static String jsonExtractSubnetMask(String fmJson) throws IOException {
        String subnet_mask = "";
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;

        try {
            jp = f.createJsonParser(fmJson);
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

            if (n == "subnet-mask") {
                subnet_mask = jp.getText();
                break;
            }
        }

        return subnet_mask;
    }
}
