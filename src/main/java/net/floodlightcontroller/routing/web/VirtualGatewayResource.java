package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGatewayInstance;
import org.projectfloodlight.openflow.types.MacAddress;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
public class VirtualGatewayResource extends ServerResource {

    @Delete
    public Object removeVirtualGateway () throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes()
                        .get(IRoutingService.class.getCanonicalName());

        String name = (String) getRequestAttributes().get("gateway-name");

        Optional<Collection<VirtualGatewayInstance>> virtualGateways = routingService.getAllVirtualGateways();
        if (!virtualGateways.isPresent()) {
            return Collections.singletonMap("INFO: ", "No virtual gateway exists yet");
        }

        if (name.equals("all")) {
            routingService.removeAllVirtualGateways();
            return Collections.singletonMap("INFO: ", "All virtual gateway removed");
        }
        else {
            if (routingService.removeVirtualGateway(name)) {
                return Collections.singletonMap("INFO: ", "Virtual gateway '" + name + "' removed");
            }
            else {
                return Collections.singletonMap("INFO: ", "Virtual gateway '"  + name + "' not found");
            }
        }

    }

    @Put
    @Post
    // This includes overwriting an existing gateway
    public Object createVirtualGateway(String jsonData) throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes()
                        .get(IRoutingService.class.getCanonicalName());

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode nameNode = mapper.readTree(jsonData).get("gateway-name");
            JsonNode macNode = mapper.readTree(jsonData).get("gateway-mac");

            if (nameNode == null || macNode == null) {
                return Collections.singletonMap("INFO: ", "some fields missing");
            }

            VirtualGatewayInstance vGateway = mapper.reader(VirtualGatewayInstance.class).readValue(jsonData);
            if (!routingService.getVirtualGateway(vGateway.getName()).isPresent()) {
                // Create new virtual gateway
                routingService.addVirtualGatewayInstance(vGateway);
                return vGateway;
            }
            else {
                // Update existing virtual gateway
                routingService.updateVirtualGateway(nameNode.asText(), MacAddress.of(macNode.asText()));
                return vGateway;
            }
        }
        catch (IOException e) {
            throw new IOException(e);
        }

    }

}
