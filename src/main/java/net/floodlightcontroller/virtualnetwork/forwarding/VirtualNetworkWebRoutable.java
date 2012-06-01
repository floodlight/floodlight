package net.floodlightcontroller.virtualnetwork.forwarding;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class VirtualNetworkWebRoutable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/tenants/{tenant}/networks/{network}", NetworkResource.class);
        router.attach("/tenants/{tenant}/networks/{network}/ports/{port}/attachment", HostResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/quantum/1.0";
    }
}