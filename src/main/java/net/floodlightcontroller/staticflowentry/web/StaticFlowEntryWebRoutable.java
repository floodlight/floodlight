package net.floodlightcontroller.staticflowentry.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class StaticFlowEntryWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/json", StaticFlowEntryPusherResource.class);
        router.attach("/clear/{switch}/json", ClearStaticFlowEntriesResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    @Override
    public String basePath() {
        return "/wm/staticflowentrypusher";
    }
}
