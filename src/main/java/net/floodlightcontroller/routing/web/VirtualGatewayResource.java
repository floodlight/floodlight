package net.floodlightcontroller.routing.web;

import net.floodlightcontroller.routing.IRoutingService;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
public class VirtualGatewayResource extends ServerResource {

    @Get
    public Object getVirtualGateways () {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());
        return routingService.getVirtualGateways();
    }





}
