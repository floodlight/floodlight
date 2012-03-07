/**
 * 
 */
package net.floodlightcontroller.core.web;

import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.util.EventHistory;

import org.openflow.protocol.OFMatch;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author subrata
 *
 */
public class EventHistoryPacketInResource extends ServerResource {
    // TODO - Move this to the DeviceManager rest API
    protected static Logger log = 
            LoggerFactory.getLogger(EventHistoryPacketInResource.class);
    
    @Get("json")
    public EventHistory<OFMatch> handleEvHistReq() {
        // Get the count (number of latest events requested), zero means all
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
                return new EventHistory<OFMatch>(deviceManager.evHistDevMgrPktIn,count);
            }
        } catch (ClassCastException e) {
            log.error(e.toString());
        }
        
        return null;
    }

}
