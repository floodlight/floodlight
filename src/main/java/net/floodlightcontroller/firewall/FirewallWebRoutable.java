package net.floodlightcontroller.firewall;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.routing.Router;

public class FirewallWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/module/{op}/json", FirewallResource.class);
        router.attach("/rules/json", FirewallRulesResource.class);
        return router;
    }

    /**
     * Set the base path for the Firewall
     */
    @Override
    public String basePath() {
        return "/wm/firewall";
    }
}
