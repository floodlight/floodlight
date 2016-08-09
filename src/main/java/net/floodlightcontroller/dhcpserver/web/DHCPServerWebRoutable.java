package net.floodlightcontroller.dhcpserver.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.routing.Router;

public class DHCPServerWebRoutable implements RestletRoutable {
	protected static final String STR_INSTANCE = "instance";
	protected static final String STR_ALL = "all";
	
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/add/instance/json", InstanceResource.class);
        router.attach("/get/instance/{" + STR_INSTANCE + "}/json", InstanceResource.class);
        router.attach("/del/instance/{" + STR_INSTANCE + "}/json", InstanceResource.class);
        router.attach("/add/static-binding/json", BindingResource.class);

        return router;
    }

    /**
     * Set the base path for the DHCP Server
     */
    @Override
    public String basePath() {
        return "/wm/dhcpserver";
    }
}