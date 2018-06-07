package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.routing.IGatewayService;
import net.floodlightcontroller.routing.VirtualGatewayInstance;
import org.projectfloodlight.openflow.types.MacAddress;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class GatewayInstancesResource extends ServerResource {

    @Get
    public Object getInstances() {
        IGatewayService gatewayService =
                (IGatewayService) getContext().getAttributes().
                        get(IGatewayService.class.getCanonicalName());

        return gatewayService.getGatewayInstances();

    }


    @Put
    @Post
    public Object addInstance(String json) {
        IGatewayService gatewayService = (IGatewayService) getContext().getAttributes()
                .get(IGatewayService.class.getCanonicalName());

        if (json == null) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode nameNode = mapper.readTree(json).get("gateway-name");
            JsonNode macNode = mapper.readTree(json).get("gateway-mac");

            if (nameNode == null || macNode == null) {
                setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
                return null;
            }

            VirtualGatewayInstance vGateway = mapper.reader(VirtualGatewayInstance.class).readValue(json);
            if (!gatewayService.getGatewayInstance(vGateway.getName()).isPresent()) {
                // Create new virtual gateway
                gatewayService.addGatewayInstance(vGateway);
                return vGateway;
            }
            else {
                // Update existing virtual gateway
                gatewayService.updateVirtualGateway(nameNode.asText(), MacAddress.of(macNode.asText()));
                return vGateway;
            }

        } catch (IOException e) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "Instance object could not be deserialized.");
            return e;
        }

    }


    @Delete
    public Object deleteInstances() {
        IGatewayService gatewayService =
                (IGatewayService) getContext().getAttributes().
                        get(IGatewayService.class.getCanonicalName());

        Collection<VirtualGatewayInstance> instances = gatewayService.getGatewayInstances();
        gatewayService.deleteGatewayInstances();

        return ImmutableMap.of("deleted", instances);

    }

}
