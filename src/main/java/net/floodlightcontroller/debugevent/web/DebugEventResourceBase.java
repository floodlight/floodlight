package net.floodlightcontroller.debugevent.web;


import net.floodlightcontroller.debugevent.IDebugEventService;

import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

public class DebugEventResourceBase extends ServerResource{
    protected IDebugEventService debugEvent;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        debugEvent = (IDebugEventService)getContext().getAttributes().
                get(IDebugEventService.class.getCanonicalName());
    }
}
