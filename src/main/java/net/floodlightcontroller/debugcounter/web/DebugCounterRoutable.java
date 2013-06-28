package net.floodlightcontroller.debugcounter.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class DebugCounterRoutable implements RestletRoutable {

    @Override
    public String basePath() {
        return "/wm/debugcounter";
    }

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/{param1}/{param2}/{param3}/{param4}/", DebugCounterResource.class);
        router.attach("/{param1}/{param2}/{param3}/{param4}", DebugCounterResource.class);
        router.attach("/{param1}/{param2}/{param3}/", DebugCounterResource.class);
        router.attach("/{param1}/{param2}/{param3}", DebugCounterResource.class);
        router.attach("/{param1}/{param2}/", DebugCounterResource.class);
        router.attach("/{param1}/{param2}", DebugCounterResource.class);
        router.attach("/{param1}/", DebugCounterResource.class);
        router.attach("/{param1}", DebugCounterResource.class);
        router.attach("/", DebugCounterResource.class);
        router.attach("", DebugCounterResource.class);
        return router;
    }
}
