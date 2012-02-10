package net.floodlightcontroller.core.web;

import net.floodlightcontroller.topology.internal.EventHistoryTopologyCluster;
import net.floodlightcontroller.topology.internal.TopologyImpl;
import net.floodlightcontroller.util.EventHistory;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * @author subrata
 *
 */
public class EventHistoryTopologyClusterResource extends ServerResource {

    @Get("json")
    public EventHistory<EventHistoryTopologyCluster> handleEvHistReq() {

        // Get the event history count. Last <count> events would be returned
        String evHistCount = (String)getRequestAttributes().get("count");
        int    count = EventHistory.EV_HISTORY_DEFAULT_SIZE;
        try {
            count = Integer.parseInt(evHistCount);
        }
        catch(NumberFormatException nFE) {
            // Invalid input for event count - use default value
        }

        TopologyImpl topoManager =
           (TopologyImpl)getContext().getAttributes().get("topology");

        return new EventHistory<EventHistoryTopologyCluster>(
                                topoManager.evHistTopologyCluster, count);
    }
}
