package net.floodlightcontroller.core.web;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologySwitch;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
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
        int    count = EventHistory.EV_HISTORY_DEFAULT_SIZE;
        try {
            count = Integer.parseInt(evHistCount);
        }
        catch(NumberFormatException nFE) {
            // Invalid input for event count - use default value
        }

        LinkDiscoveryManager topoManager =
           (LinkDiscoveryManager)getContext().getAttributes().
               get(ILinkDiscoveryService.class.getCanonicalName());

        return new EventHistory<EventHistoryTopologySwitch>(
                                topoManager.evHistTopologySwitch, count);
    }
}
