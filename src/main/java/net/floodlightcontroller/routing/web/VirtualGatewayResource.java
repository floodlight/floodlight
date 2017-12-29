package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGateway;
import net.floodlightcontroller.routing.VirtualGatewayInterface;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
public class VirtualGatewayResource extends ServerResource {

    @Get
    public Object getVirtualGateway() {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        String name = (String) getRequestAttributes().get("gateway-name");

        if (name != null) {
            // List virtual gateway
            Optional<VirtualGateway> virtualGateway = routingService.getVirtualGateway(name);
            return virtualGateway.isPresent() ? virtualGateway : Collections.singletonMap("INFO: ", "Gateway" + name + " not found");
        } else {
            // List all virtual gateways
            Optional<Collection<VirtualGateway>> virtualGateways = routingService.getAllVirtualGateways();
            return virtualGateways.isPresent() ? virtualGateways.get() : Collections.singletonMap("INFO: ", "No virtual gateway exists yet");
        }

    }


//    @Put
//    @Post
    // This includes overwriting an existing gateway
//    public void addVirtualGateway(String jsonData) throws IOException {
//        IRoutingService routingService =
//                (IRoutingService) getContext().getAttributes().
//                        get(IRoutingService.class.getCanonicalName());
//
//        String gatewayName = (String) getRequestAttributes().get("gateway-name");
//        String interfaceName = (String) getRequestAttributes().get("interface");
//
//        try {
//            VirtualGateway vGateway = new ObjectMapper()
//                    .reader(VirtualGateway.class)
//                    .readValue(jsonData);
//            routingService.addVirtualGateway(vGateway);
//        }
//        catch (IOException e) {
//            throw new IOException(e);
//        }
//    }



}
