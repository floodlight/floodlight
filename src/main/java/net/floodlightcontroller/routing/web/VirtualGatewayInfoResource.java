package net.floodlightcontroller.routing.web;

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGateway;
import org.restlet.resource.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class VirtualGatewayInfoResource extends ServerResource {

    @Get
    public Object getAllVirtualGateways () {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        String name = (String) getRequestAttributes().get("gateway-name");

        if (name.equals("all")) {
            Optional<Collection<VirtualGateway>> gateways = routingService.getAllVirtualGateways();
            return gateways.get().isEmpty() ? Collections.singletonMap("INFO: ", "No virtual gateway exists yet") : gateways.get();
        }
        else {
            Optional<VirtualGateway> gateway = routingService.getVirtualGateway(name);
            return gateway.isPresent() ?  gateway.get() : Collections.singletonMap("INFO: ", "Virtual gateway '" + name + "' not found");
        }

    }

}
