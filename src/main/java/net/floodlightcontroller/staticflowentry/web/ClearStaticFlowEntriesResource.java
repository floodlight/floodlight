package net.floodlightcontroller.staticflowentry.web;

import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearStaticFlowEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ClearStaticFlowEntriesResource.class);
    
    @Get
    public void ClearStaticFlowEntries() {
        IStaticFlowEntryPusherService sfpService =
                (IStaticFlowEntryPusherService)getContext().getAttributes().
                    get(IStaticFlowEntryPusherService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("switch");
        if (log.isDebugEnabled())
            log.debug("Clearing all static flow entires for switch: " + param);
        
        if (param.toLowerCase().equals("all")) {
            sfpService.deleteAllFlows();
        } else {
            try {
                sfpService.deleteFlowsForSwitch(HexString.toLong(param));
            } catch (NumberFormatException e){
                log.error("Could not parse switch DPID: " + e.getMessage());
            }
        }
    }
}
