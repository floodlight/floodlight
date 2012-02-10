/**
 * 
 */
package net.floodlightcontroller.core.web;

import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.util.EventHistory;

import org.openflow.protocol.OFMatch;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * @author subrata
 *
 */
public class EventHistoryPacketInResource extends ServerResource {

    @Get("json")
    public EventHistory<OFMatch> handleEvHistReq() {

        EventHistory<OFMatch> ofm = null;
        // Get the count (number of latest events requested), zero means all
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

        ofm =  new EventHistory<OFMatch>(deviceManager.evHistDevMgrPktIn,count);
        return ofm;
    }

}
