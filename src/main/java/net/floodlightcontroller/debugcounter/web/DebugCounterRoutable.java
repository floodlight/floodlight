package net.floodlightcontroller.debugcounter.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class DebugCounterRoutable implements RestletRoutable {

    @Override
    public String basePath() {
        return "/wm/counters";
    }

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/{param}", DebugCounterResource.class);
        return router;
    }
}
