package net.floodlightcontroller.statistics.web;

import net.floodlightcontroller.restserver.RestletRoutable;


import org.restlet.Context;
import org.restlet.routing.Router;

public class SwitchStatisticsWebRoutable implements RestletRoutable {
	protected static final String DPID_STR = "dpid";
	protected static final String PORT_STR = "port";
	protected static final String FLOW_STR = "flow";
	protected static final String ENABLE_STR = "enable";
	protected static final String DISABLE_STR = "disable";
	
	@Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/bandwidth/{" + DPID_STR + "}/{" + PORT_STR + "}/json", BandwidthResource.class);
        router.attach("/flow/{" + DPID_STR + "}/json", FlowResource.class);
        router.attach("/portdesc/{" + DPID_STR + "}/{" + PORT_STR + "}/json", PortDescResource.class);
        router.attach("/config/{" + ENABLE_STR + "}/json", ConfigResource.class);
        router.attach("/config/{" + DISABLE_STR + "}/json", ConfigResource.class);
        router.attach("/config/{" + PORT_STR + "}/{period}/json", ConfigResource.class);
        router.attach("/config/{" + FLOW_STR + "}/{period}/json", ConfigResource.class);
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