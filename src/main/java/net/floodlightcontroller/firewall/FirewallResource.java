package net.floodlightcontroller.firewall;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.firewall.Firewall;


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
        }
        
        return "{\"status\" : \"failure\", \"details\" : \"invalid operation\"}";
    }
}
