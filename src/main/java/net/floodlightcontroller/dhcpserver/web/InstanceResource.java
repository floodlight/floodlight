package net.floodlightcontroller.dhcpserver.web;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.dhcpserver.DHCPInstance;
import net.floodlightcontroller.dhcpserver.IDHCPService;

import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceResource extends ServerResource {
	private static final Logger log = LoggerFactory.getLogger(InstanceResource.class);
	
	@Get
	Collection<DHCPInstance> getInstance() {
		IDHCPService dhcp = (IDHCPService) getContext().getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get(DHCPServerWebRoutable.STR_INSTANCE);
        Collection<DHCPInstance> instances = null;
        
        if (whichInstance == null || whichInstance.isEmpty() || whichInstance.equalsIgnoreCase(DHCPServerWebRoutable.STR_ALL)) {
        	instances = dhcp.getInstances();
        } else {
        	DHCPInstance instance = dhcp.getInstance(whichInstance);
        	if (instance != null) {
        		instances = Collections.singleton(instance);
        	}
        }
        
        if (instances == null) {
        	log.error("Could not locate DHCP instance {}", whichInstance);
        	return Collections.emptySet();
        }
        return instances;
	}
	
	@Put
	@Post
	Map<String, String> addInstance(String json) {
		IDHCPService dhcp = (IDHCPService) getContext().getAttributes().get(IDHCPService.class.getCanonicalName());
		Map<String, String> rc = new HashMap<String, String>(1);
		
		rc.put("result", "DHCP instance added");
		return rc;
	}
	
	@Delete
	Map<String, String> delInstance() {
		IDHCPService dhcp = (IDHCPService) getContext().getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get(DHCPServerWebRoutable.STR_INSTANCE);
		Map<String, String> rc = new HashMap<String, String>(1);
		
		rc.put("result", "DHCP instance " + whichInstance + " deleted");
		return rc;
	}
}
