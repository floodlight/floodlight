package net.floodlightcontroller.topology.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.core.web.RestletRoutable;

public class TopologyWebRouteable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/links", LinksResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/wm/topology";
    }

}
