package net.floodlightcontroller.core.web;

import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologyLink;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.util.EventHistory;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * @author subrata
 *
 */
public class EventHistoryTopologyLinkResource extends ServerResource {

    @Get("json")
    public EventHistory<EventHistoryTopologyLink> handleEvHistReq() {

        // Get the event history count. Last <count> events would be returned
        String evHistCount = (String)getRequestAttributes().get("count");
        int    count = EventHistory.EV_HISTORY_DEFAULT_SIZE;
        try {
            count = Integer.parseInt(evHistCount);
        }
        catch(NumberFormatException nFE) {
            // Invalid input for event count - use default value
        }

        LinkDiscoveryManager topoManager =
           (LinkDiscoveryManager)getContext().getAttributes().get("topology");

        return new EventHistory<EventHistoryTopologyLink>(
                                    topoManager.evHistTopologyLink, count);
    }
}
