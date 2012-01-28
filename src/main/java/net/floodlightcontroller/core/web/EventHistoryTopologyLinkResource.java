package net.floodlightcontroller.core.web;

import net.floodlightcontroller.topology.internal.EventHistoryTopologyLink;
import net.floodlightcontroller.topology.internal.TopologyImpl;
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

        TopologyImpl topoManager =
           (TopologyImpl)getContext().getAttributes().get("topology");

        return new EventHistory<EventHistoryTopologyLink>(
                topoManager.evHistTopologyLink, Integer.parseInt(evHistCount));
    }
}
