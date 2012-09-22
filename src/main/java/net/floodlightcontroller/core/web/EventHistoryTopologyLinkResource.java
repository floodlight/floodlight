package net.floodlightcontroller.core.web;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologyLink;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.util.EventHistory;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author subrata
 *
 */
public class EventHistoryTopologyLinkResource extends ServerResource {
    // TODO - Move this to the DeviceManager Rest API
    protected static Logger log = 
            LoggerFactory.getLogger(EventHistoryTopologyLinkResource.class);

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

        LinkDiscoveryManager linkDiscoveryManager =
                (LinkDiscoveryManager)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());
        if (linkDiscoveryManager != null) {
            return new EventHistory<EventHistoryTopologyLink>(
                    linkDiscoveryManager.evHistTopologyLink, count);
        }
        
        return null;
    }
}
