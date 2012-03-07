/**
 * 
 */
package net.floodlightcontroller.core.web;

import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.EventHistoryAttachmentPoint;
import net.floodlightcontroller.util.EventHistory;

import org.restlet.resource.ServerResource;
import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author subrata
 *
 */
public class EventHistoryAttachmentPointResource extends ServerResource {
    // TODO - Move this to the DeviceManager Rest API
    protected static Logger log = 
            LoggerFactory.getLogger(EventHistoryAttachmentPointResource.class);
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

        try {
            DeviceManagerImpl deviceManager = 
               (DeviceManagerImpl)getContext().getAttributes().
                   get(IDeviceManagerService.class.getCanonicalName());
            if (deviceManager != null) {
                return new EventHistory<EventHistoryAttachmentPoint>(
                        deviceManager.evHistDevMgrAttachPt, count);
            }
        } catch (ClassCastException e) {
            log.error(e.toString());
        }

        return null;
    }
}
