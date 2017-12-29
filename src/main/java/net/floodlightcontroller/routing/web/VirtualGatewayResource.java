package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGateway;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
public class VirtualGatewayResource extends ServerResource {

    @Get
    public Object getVirtualGateways () {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        Optional<Collection<VirtualGateway>> virtualGateways = routingService.getVirtualGateways();
        return virtualGateways.isPresent() ? virtualGateways.get() : Collections.singletonMap("INFO: ", "No virtual gateway exists yet");
    }

//    @Get
//    public Object getVirtualGateway() {
//        IRoutingService routingService =
//                (IRoutingService) getContext().getAttributes().
//                        get(IRoutingService.class.getCanonicalName());
//
//        String name = getRequestAttributes().get("gateway-name").toString();
//        Optional<VirtualGateway> virtualGateway = routingService.getVirtualGateway(name);
//
//        return virtualGateway.isPresent() ? virtualGateway : Collections.singletonMap("INFO: ", "Gateway" + name + " not found");
//    }

    @Put
    @Post
    public void addVirtualGateway(String jsonData) throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        try {
            VirtualGateway gateway = new ObjectMapper()
                    .reader(VirtualGateway.class)
                    .readValue(jsonData);
            routingService.addVirtualGateway(gateway);
        }
        catch (IOException e) {
            throw new IOException(e);
        }

    }



}
