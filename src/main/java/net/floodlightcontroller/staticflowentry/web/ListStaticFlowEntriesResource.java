package net.floodlightcontroller.staticflowentry.web;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import org.openflow.protocol.OFFlowMod;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListStaticFlowEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);
    
    @Get
    public Map<String, Map<String, OFFlowMod>> ListStaticFlowEntries() {
        IStaticFlowEntryPusherService sfpService =
                (IStaticFlowEntryPusherService)getContext().getAttributes().
                    get(IStaticFlowEntryPusherService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("switch");
        if (log.isDebugEnabled())
            log.debug("Listing all static flow entires for switch: " + param);
        
        if (param.toLowerCase().equals("all")) {
            return sfpService.getFlows();
        } else {
            try {
                Map<String, Map<String, OFFlowMod>> retMap = 
                        new HashMap<String, Map<String, OFFlowMod>>();
                retMap.put(param, sfpService.getFlows(param));
                return retMap;
                
            } catch (NumberFormatException e){
                log.error("Could not parse switch DPID: " + e.getMessage());
            }
        }
        return null;
    }
}
