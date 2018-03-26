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
import java.util.Collection;

/**
 * @author Geddings Barrineau, geddings.barrineau@bigswitch.com on 2/21/18.
 */
public class InstancesResource extends ServerResource {

    @Get
    public Object getInstances() {
        IDHCPService dhcpService = (IDHCPService) getContext()
                .getAttributes().get(IDHCPService.class.getCanonicalName());

        return dhcpService.getInstances();
    }

    @Put
    @Post
    // This would also overwrite/update an existing dhcp instance
    public Object addInstance(String json) {
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
            JsonNode routerIPNode = mapper.readTree(json).get("router-ip");
            JsonNode leaseTimeNode = mapper.readTree(json).get("lease-time");

            boolean getFields = checkRequiredFields(nameNode, startIPNode, endIPNode, serverIDNode,
                                    routerIPNode, leaseTimeNode);

            if (!getFields) {
                setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
                return null;
            }

            DHCPInstance instance = mapper.reader(DHCPInstance.class).readValue(json);
            if (!dhcpService.getInstance(instance.getName()).isPresent()) {
                // create a new dhcp instance
                dhcpService.addInstance(instance);
                setStatus(Status.SUCCESS_CREATED);
                setLocationRef(getReference().toString() + "/" + instance.getName());
                return instance;
            } else {
                // update an existing dhcp instance
                instance = dhcpService.updateInstance(nameNode.asText(), instance);
                return instance;
            }

        } catch (IOException e) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "Instance object could not be deserialized.");
            return e;
        }
    }

    private boolean checkRequiredFields(JsonNode nameNode, JsonNode startIPNode, JsonNode endIPNode,
                                        JsonNode serverIDNode, JsonNode routerIPNode, JsonNode leaseTimeNode) {
        return nameNode != null && startIPNode != null && endIPNode != null && serverIDNode != null
                && routerIPNode != null && leaseTimeNode != null;
    }

    @Delete
    public Object deleteInstances() {
        IDHCPService dhcpService = (IDHCPService) getContext()
                .getAttributes().get(IDHCPService.class.getCanonicalName());

        Collection<DHCPInstance> instances = dhcpService.getInstances();
        dhcpService.deleteAllInstances();

        return ImmutableMap.of("deleted", instances);
    }
}
