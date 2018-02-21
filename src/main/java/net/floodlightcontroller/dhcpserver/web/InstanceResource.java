package net.floodlightcontroller.dhcpserver.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.dhcpserver.DHCPInstance;
import net.floodlightcontroller.dhcpserver.IDHCPService;
import org.restlet.data.Status;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.Optional;

public class InstanceResource extends ServerResource {

    private static final String INSTANCE_NOT_FOUND_MESSAGE = "Instance not found.";

    @Get
    public Object getInstance() {
        IDHCPService dhcpService = (IDHCPService) getContext()
                .getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get("instance-name");

        Optional<DHCPInstance> instance = dhcpService.getInstance(whichInstance);

        if (instance.isPresent()) {
            return instance.get();
        } else {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, INSTANCE_NOT_FOUND_MESSAGE);
            return null;
        }
    }

    @Put
    @Post
    // This would also overwrite/update an existing dhcp instance
    public Object updateInstance(String json) {
        IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
                .get(IDHCPService.class.getCanonicalName());

        if (json == null) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

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

            boolean getFields = checkRequiredFields(nameNode, startIPNode, endIPNode, serverIDNode, serverMacNode,
                    routerIPNode, broadcastNode, leaseTimeNode, rebindTimeNode, renewTimeNode,
                    ipforwardingNode, domainNameNode);

            if (!getFields) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
                return null;
            }

            DHCPInstance instance = mapper.reader(DHCPInstance.class).readValue(json);
            if (!dhcpService.getInstance(instance.getName()).isPresent()) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND, INSTANCE_NOT_FOUND_MESSAGE);
                return null;
            } else {
                // update an existing dhcp instance
                dhcpService.updateInstance(nameNode.asText(), instance);
                return instance;
            }

        } catch (IOException e) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "Instance object could not be deserialized.");
            return e;
        }

    }

    private boolean checkRequiredFields(JsonNode nameNode, JsonNode startIPNode, JsonNode endIPNode, JsonNode
            serverIDNode, JsonNode serverMacNode,
                                        JsonNode routerIPNode, JsonNode broadcastNode, JsonNode leaseTimeNode,
                                        JsonNode rebindTimeNode,
                                        JsonNode renewTimeNode, JsonNode ipforwardingNode, JsonNode domainNameNode) {
        return nameNode != null && startIPNode != null && endIPNode != null && serverIDNode != null && serverMacNode
                != null
                && routerIPNode != null && broadcastNode != null && leaseTimeNode != null && rebindTimeNode != null
                && renewTimeNode != null && ipforwardingNode != null && domainNameNode != null;

    }

    @Delete
    public Object deleteInstance() {
        IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
                .get(IDHCPService.class.getCanonicalName());

        String whichInstance = (String) getRequestAttributes().get("instance-name");

        if (dhcpService.deleteInstance(whichInstance)) {
            return ImmutableMap.of("deleted", whichInstance);
        } else {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, INSTANCE_NOT_FOUND_MESSAGE);
            return null;
        }
    }

}
