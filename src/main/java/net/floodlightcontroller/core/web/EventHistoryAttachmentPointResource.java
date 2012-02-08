/**
 * 
 */
package net.floodlightcontroller.core.web;

import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.EventHistoryAttachmentPoint;
import net.floodlightcontroller.util.EventHistory;

import org.restlet.resource.ServerResource;
import org.restlet.resource.Get;

/**
 * @author subrata
 *
 */
public class EventHistoryAttachmentPointResource extends ServerResource {
    /***
     * Event History Names:
     *     (a) attachment-point
     *     (b) host-network-address
     *     (c) switch-connect
     *     (d) switch-link
     *     (g) packet-ins
     *     (h) packet-outs
     *     (i) error (for floodlight)
     *     (l) route-computation
     *     (n) pktin-drops
     */

    @Get("json")
    public EventHistory<EventHistoryAttachmentPoint> handleEvHistReq() {

        // Get the event history count. Last <count> events would be returned
        String evHistCount = (String)getRequestAttributes().get("count");
        int    count = EventHistory.EV_HISTORY_DEFAULT_SIZE;
        try {
            count = Integer.parseInt(evHistCount);
        }
        catch(NumberFormatException nFE) {
            // Invalid input for event count - use default value
        }

        DeviceManagerImpl deviceManager = 
           (DeviceManagerImpl)getContext().getAttributes().get("deviceManager");

        return new EventHistory<EventHistoryAttachmentPoint>(
                deviceManager.evHistDevMgrAttachPt, count);
    }
}
