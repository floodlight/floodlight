package net.floodlightcontroller.devicemanager.internal;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

/**
 * Restlet Routable to handle DeviceManager API
 * @author alexreimers
 *
 */
public class DeviceManagerWebRoutable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/device/{device}/json", DeviceResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/wm/devicemanager";
    }
}
