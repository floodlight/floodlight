package net.floodlightcontroller.dhcpserver.web;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
	// This would also overwrite/update an existing dhcp instance
	public Object addInstance(String json) throws IOException {
		IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
									.get(IDHCPService.class.getCanonicalName());

		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
			JsonNode nameNode = mapper.readTree(json).get("name");
			JsonNode startIPNode = mapper.readTree(json).get("start-ip");
			JsonNode endIPNode = mapper.readTree(json).get("end-ip");
			JsonNode serverIDNode = mapper.readTree(json).get("server-id");
			JsonNode serverMacNode = mapper.readTree(json).get("server-mac");
			JsonNode routerIPNode = mapper.readTree(json).get("router-ip");
			JsonNode broadcastNode = mapper.readTree(json).get("broadcast-ip");
			JsonNode leaseTimeNode = mapper.readTree(json).get("lease-time");
			JsonNode rebindTimeNode = mapper.readTree(json).get("rebind-time");
			JsonNode renewTimeNode = mapper.readTree(json).get("renew-time");
			JsonNode ipforwardingNode = mapper.readTree(json).get("ip-forwarding");
			JsonNode domainNameNode = mapper.readTree(json).get("domain-name");

			boolean getFields = false;
			getFields = checkRequiredFields(nameNode, startIPNode, endIPNode, serverIDNode, serverMacNode,
							routerIPNode, broadcastNode, leaseTimeNode, rebindTimeNode, renewTimeNode,
							ipforwardingNode, domainNameNode);

			if (!getFields) {
				return Collections.singletonMap("INFO: ", "One or more required fields missing");
			}

			DHCPInstance instance = mapper.reader(DHCPInstance.class).readValue(json);
			if (!dhcpService.getInstance(instance.getName()).isPresent()) {
				// create a new dhcp instance
				dhcpService.addInstance(instance);
				return Collections.singletonMap("INFO: ", "DHCP instance '" + instance.getName() + "' created");
			}
			else {
				// update an existing dhcp instance
				dhcpService.updateInstance(nameNode.asText(), instance);
				return Collections.singletonMap("INFO: ", "DHCP instance '" + nameNode.asText() + "' updated");
			}


		}
		catch (IOException e) {
			throw new IOException(e);
		}

	}

	private boolean checkRequiredFields(JsonNode nameNode, JsonNode startIPNode, JsonNode endIPNode, JsonNode serverIDNode, JsonNode serverMacNode,
										JsonNode routerIPNode, JsonNode broadcastNode, JsonNode leaseTimeNode, JsonNode rebindTimeNode,
										JsonNode renewTimeNode, JsonNode ipforwardingNode, JsonNode domainNameNode) {
		if (nameNode == null ||startIPNode == null || endIPNode == null || serverIDNode == null || serverMacNode == null
				|| routerIPNode == null || broadcastNode == null || leaseTimeNode == null || rebindTimeNode == null
				|| renewTimeNode == null || ipforwardingNode == null || domainNameNode == null) {
			return false;
		}
		else {
			return true;
		}

	}
	
	@Delete
	public Object delInstance() {
		IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
								.get(IDHCPService.class.getCanonicalName());

		String whichInstance = (String) getRequestAttributes().get("instance-name");

		Optional<Collection<DHCPInstance>> instances = dhcpService.getInstances();
		if (!instances.isPresent()) {
			return Collections.singletonMap("INFO: ", "No dhcp instance exists yet");
		}

		if (whichInstance.equals("all")) {
			dhcpService.deleteAllInstances();
			return Collections.singletonMap("INFO: ", "All dhcp instances removed");
		}
		else {
			if (dhcpService.deleteInstance(whichInstance)) {
				return Collections.singletonMap("INFO: ", "DHCP instance '" + whichInstance + "' removed");
			}
			else {
				return Collections.singletonMap("INFO: ", "DHCP instance '"  + whichInstance + "' not found");
			}
		}
	}

}
