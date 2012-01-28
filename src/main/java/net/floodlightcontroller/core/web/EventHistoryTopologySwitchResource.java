package net.floodlightcontroller.core.web;

import net.floodlightcontroller.topology.internal.EventHistoryTopologySwitch;
import net.floodlightcontroller.topology.internal.TopologyImpl;
import net.floodlightcontroller.util.EventHistory;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * @author subrata
 *
 */
public class EventHistoryTopologySwitchResource extends ServerResource {

    @Get("json")
    public EventHistory<EventHistoryTopologySwitch> handleEvHistReq() {

        // Get the event history count. Last <count> events would be returned
        String evHistCount = (String)getRequestAttributes().get("count");

        TopologyImpl topoManager =
           (TopologyImpl)getContext().getAttributes().get("topology");

        return new EventHistory<EventHistoryTopologySwitch>(
                topoManager.evHistTopologySwitch,
                Integer.parseInt(evHistCount));
    }
}
