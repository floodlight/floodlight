package net.floodlightcontroller.linkdiscovery.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.routing.Router;

public class LinkDiscoveryWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/autoportfast/{state}/json", AutoPortFast.class); // enable/true or disable/false
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    @Override
    public String basePath() {
        return "/wm/linkdiscovery";
    }
}