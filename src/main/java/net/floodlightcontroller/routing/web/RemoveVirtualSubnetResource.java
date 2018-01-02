package net.floodlightcontroller.routing.web;

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualSubnet;
import org.restlet.resource.Delete;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/2/18
 */
public class RemoveVirtualSubnetResource extends ServerResource{
    @Delete
    public Object removeVirtualSubnet() throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes()
                        .get(IRoutingService.class.getCanonicalName());

        String name = (String) getRequestAttributes().get("subnet-name");

        Optional<Collection<VirtualSubnet>> subnets = routingService.getAllVirtualSubnets();
        if (!subnets.isPresent()) {
            return Collections.singletonMap("INFO: ", "No virtual subnet exists yet");
        }

        if (name.equals("all")) {
            routingService.removeAllVirtualSubnets();
            return Collections.singletonMap("INFO: ", "All virtual subnet removed");
        }
        else {
            if (routingService.removeVirtualSubnet(name)) {
                return Collections.singletonMap("INFO: ", "Virtual subnet '" + name + "' removed");
            }
            else {
                return Collections.singletonMap("INFO: ", "Virtual subnet '"  + name + "' not found");
            }
        }
    }

}
