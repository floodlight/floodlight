package net.floodlightcontroller.firewall;

import net.floodlightcontroller.linkdiscovery.web.LinksResource;
import net.floodlightcontroller.restserver.RestletRoutable;
import net.floodlightcontroller.topology.web.BlockedPortsResource;
import net.floodlightcontroller.topology.web.BroadcastDomainResource;
import net.floodlightcontroller.topology.web.SwitchClustersResource;
import net.floodlightcontroller.topology.web.TunnelLinksResource;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class FirewallWebRoutable implements RestletRoutable {
	/**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/{op}/json", FirewallResource.class);
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
