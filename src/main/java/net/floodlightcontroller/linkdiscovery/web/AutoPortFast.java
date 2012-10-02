package net.floodlightcontroller.linkdiscovery.web;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPortFast extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(AutoPortFast.class);

    @Get("json")
    public String retrieve() {
        ILinkDiscoveryService linkDiscovery;
        linkDiscovery = (ILinkDiscoveryService)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());

        String param = ((String)getRequestAttributes().get("state")).toLowerCase();
        if (param.equals("enable") || param.equals("true")) {
            linkDiscovery.setAutoPortFastFeature(true);
        } else if (param.equals("disable") || param.equals("false")) {
            linkDiscovery.setAutoPortFastFeature(false);
        }
        setStatus(Status.SUCCESS_OK, "OK");
        if (linkDiscovery.isAutoPortFastFeature())
            return "enabled";
        else return "disabled";
    }
}
