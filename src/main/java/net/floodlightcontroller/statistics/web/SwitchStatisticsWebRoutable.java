package net.floodlightcontroller.statistics.web;

import net.floodlightcontroller.restserver.RestletRoutable;


import org.restlet.Context;
import org.restlet.routing.Router;

public class SwitchStatisticsWebRoutable implements RestletRoutable {
	protected static final String DPID_STR = "dpid";
	protected static final String PORT_STR = "port";
	
	@Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/bandwidth/{" + DPID_STR + "}/{" + PORT_STR + "}/json", BandwidthResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    @Override
    public String basePath() {
        return "/wm/statistics";
    }
}