package net.floodlightcontroller.dhcpserver.web;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

	@Get
	public Object getInstance() {
		IDHCPService dhcpService = (IDHCPService) getContext()
									.getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get("instance-name");

        if (whichInstance.equalsIgnoreCase("all")) {
        	Optional<Collection<DHCPInstance>> instances = dhcpService.getInstances();
        	return instances.get().isEmpty() ? Collections.singletonMap("INFO: ", "No DHCP instance exist yet") : instances.get();
		}
		else {
        	Optional<DHCPInstance> instance = dhcpService.getInstance(whichInstance);
        	return instance.isPresent() ? instance.get() : Collections.singletonMap("INFO: ", "DHCP instance " + whichInstance + " not found");
		}

	}
	
	@Put
	@Post
	public Object addInstance(String json) throws IOException {
		IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
									.get(IDHCPService.class.getCanonicalName());

		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode nameNode = mapper.readTree(json).get("instance-name");

			if (nameNode == null) {
				return Collections.singletonMap("INFO: ", "some fields missing");
			}

			DHCPInstance instance = mapper.reader(DHCPInstance.class).readValue(json);
			dhcpService.addInstance(instance);
			return Collections.singletonMap("INFO: ", "DHCP instance '" + instance.getName() + "' created");
		}
		catch (IOException e) {
			throw new IOException(e);
		}


	}
	
//	@Delete
//	Object delInstance() {
//		IDHCPService dhcp = (IDHCPService) getContext().getAttributes().get(IDHCPService.class.getCanonicalName());
//
//	}
}
