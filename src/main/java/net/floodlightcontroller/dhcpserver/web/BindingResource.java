package net.floodlightcontroller.dhcpserver.web;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.dhcpserver.IDHCPService;

import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

public class BindingResource extends ServerResource {

	@Put
	@Post
	Map<String, String> addStaticBinding() {
		IDHCPService dhcp = (IDHCPService) getContext().getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get(DHCPServerWebRoutable.STR_INSTANCE);
        Map<String, String> rc = new HashMap<String, String>(1);
		
		rc.put("result", "DHCP static binding added for instance " + whichInstance);
		return rc;
	}
	
	@Delete
	Map<String, String> deleteStaticBinding() {
		IDHCPService dhcp = (IDHCPService) getContext().getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get(DHCPServerWebRoutable.STR_INSTANCE);
		Map<String, String> rc = new HashMap<String, String>(1);
		
		rc.put("result", "DHCP static binding deleted for instance " + whichInstance);
		return rc;
	}
	
}