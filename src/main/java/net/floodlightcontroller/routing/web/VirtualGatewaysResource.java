package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGateway;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class VirtualGatewaysResource extends ServerResource {

    @Get
    public Object getAllVirtualGateways () {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        Optional<Collection<VirtualGateway>> virtualGateways = routingService.getAllVirtualGateways();
        return virtualGateways.isPresent() ? virtualGateways.get() : Collections.singletonMap("INFO: ", "No virtual gateway exists yet");

    }

    @Delete
    public Object removeAllVirtualGateways () throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        Optional<Collection<VirtualGateway>> virtualGateways = routingService.removeAllVirtualGateways();
        return virtualGateways.get();
    }


}
