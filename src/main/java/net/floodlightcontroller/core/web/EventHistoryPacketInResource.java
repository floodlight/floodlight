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

        DeviceManagerImpl deviceManager = 
           (DeviceManagerImpl)getContext().getAttributes().get("deviceManager");

        ofm =  new EventHistory<OFMatch>(deviceManager.evHistDevMgrPktIn,
                                            Integer.parseInt(evHistCount));
        return ofm;
    }

}
