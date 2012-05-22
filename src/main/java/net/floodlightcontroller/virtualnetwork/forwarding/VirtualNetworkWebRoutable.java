package net.floodlightcontroller.virtualnetwork.forwarding;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class VirtualNetworkWebRoutable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        //router.attach("/tenants/{tenant}/networks", NetworkResource.java);
        //router.attach("/");
        return router;
    }

    @Override
    public String basePath() {
        return "/quantum/1.0";
    }
}
