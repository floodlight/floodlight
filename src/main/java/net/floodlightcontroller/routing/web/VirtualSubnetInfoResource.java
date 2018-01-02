package net.floodlightcontroller.routing.web;

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualSubnet;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
public class VirtualSubnetInfoResource extends ServerResource {
    @Get
    public Object getAllVirtualSubnets() {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        String name = (String) getRequestAttributes().get("subnet-name");

        if (name.equals("all")) {
            Optional<Collection<VirtualSubnet>> subnets = routingService.getAllVirtualSubnets();
            return subnets.get().isEmpty() ? Collections.singletonMap("INFO: ", "No virtual subnet exists yet") : subnets.get();
        }
        else {
            Optional<VirtualSubnet> subnet = routingService.getVirtualSubnet(name);
            return subnet.isPresent() ?  subnet.get() : Collections.singletonMap("INFO: ", "Virtual subnet '" + name + "' not found");
        }

    }

}
