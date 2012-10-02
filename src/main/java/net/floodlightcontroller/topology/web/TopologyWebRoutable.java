package net.floodlightcontroller.topology.web;

import org.restlet.Context;
import org.restlet.routing.Router;

import net.floodlightcontroller.linkdiscovery.web.LinksResource;
import net.floodlightcontroller.restserver.RestletRoutable;

public class TopologyWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/links/json", LinksResource.class);
        router.attach("/tunnellinks/json", TunnelLinksResource.class);
        router.attach("/switchclusters/json", SwitchClustersResource.class);
        router.attach("/broadcastdomainports/json", BroadcastDomainPortsResource.class);
        router.attach("/enabledports/json", EnabledPortsResource.class);
        router.attach("/blockedports/json", BlockedPortsResource.class);
        router.attach("/route/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/json", RouteResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    @Override
    public String basePath() {
        return "/wm/topology";
    }
}
