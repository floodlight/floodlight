package net.floodlightcontroller.virtualnetwork;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class VirtualNetworkWebRoutable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/tenants/{tenant}/networks", NetworkResource.class); // GET
        router.attach("/tenants/{tenant}/networks/{network}", NetworkResource.class); // PUT, DELETE
        router.attach("/tenants/{tenant}/networks", NetworkResource.class); // POST
        router.attach("/tenants/{tenant}/networks/{network}/ports/{port}/attachment", HostResource.class);
        router.attachDefault(NoOp.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/quantum/v1.0";
    }
}