package net.floodlightcontroller.debugcounter;

import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

public class DebugCounterResourceBase extends ServerResource {

    protected IDebugCounterService debugCounter;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        debugCounter = (IDebugCounterService)getContext().getAttributes().
                get(IDebugCounterService.class.getCanonicalName());
    }
}
