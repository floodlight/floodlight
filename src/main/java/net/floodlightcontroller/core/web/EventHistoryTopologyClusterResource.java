package net.floodlightcontroller.core.web;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologyCluster;
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
public class EventHistoryTopologyClusterResource extends ServerResource {
    // TODO - Move this to the LinkDiscovery rest API
    protected static Logger log = 
            LoggerFactory.getLogger(EventHistoryTopologyClusterResource.class);

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

        try {
            LinkDiscoveryManager topoManager =
               (LinkDiscoveryManager)getContext().getAttributes().
                   get(ILinkDiscoveryService.class.getCanonicalName());
            if (topoManager != null) {
                return new EventHistory<EventHistoryTopologyCluster>(
                        topoManager.evHistTopologyCluster, count);
            }
        } catch (ClassCastException e) {
            log.error(e.toString());
        }
        
        return null;
    }
}
