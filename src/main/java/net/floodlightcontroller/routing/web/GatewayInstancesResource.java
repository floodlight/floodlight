package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGatewayInstance;
import org.projectfloodlight.openflow.types.MacAddress;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class GatewayInstancesResource extends ServerResource {

    @Get
    public Object getInstances() {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        return routingService.getGatewayInstances();

    }


    @Put
    @Post
    public Object addInstance(String json) {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

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
            if (!routingService.getGatewayInstance(vGateway.getName()).isPresent()) {
                // Create new virtual gateway
                routingService.addGatewayInstance(vGateway);
                return vGateway;
            }
            else {
                // Update existing virtual gateway
                routingService.updateVirtualGateway(nameNode.asText(), MacAddress.of(macNode.asText()));
                return vGateway;
            }

        } catch (IOException e) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "Instance object could not be deserialized.");
            return e;
        }

    }


    @Delete
    public Object deleteInstances() {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        Collection<VirtualGatewayInstance> instances = routingService.getGatewayInstances();
        routingService.deleteGatewayInstances();

        return ImmutableMap.of("deleted", instances);

    }

}
