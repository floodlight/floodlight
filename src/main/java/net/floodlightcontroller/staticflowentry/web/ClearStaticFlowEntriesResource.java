package net.floodlightcontroller.staticflowentry.web;

import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearStaticFlowEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ClearStaticFlowEntriesResource.class);
    
    @Get
    public void IStaticFlowEntryPusherService() {
        IStaticFlowEntryPusherService sfpService =
                (IStaticFlowEntryPusherService)getContext().getAttributes().
                    get(IStaticFlowEntryPusherService.class.getCanonicalName());
        log.debug("Clearing all static flow entires");
        sfpService.deleteAllFlows();
    }
}
