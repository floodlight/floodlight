package net.floodlightcontroller.accesscontrollist.web;

import net.floodlightcontroller.accesscontrollist.IACLService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearACRulesResource extends ServerResource {
	protected static Logger log = LoggerFactory
			.getLogger(ClearACRulesResource.class);

    @Get
    public void ClearACRules() {
		IACLService acl = (IACLService) getContext().getAttributes().get(
				IACLService.class.getCanonicalName());
        
		acl.removeAllRules();
    }
}
