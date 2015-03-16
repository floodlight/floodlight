package net.floodlightcontroller.accesscontrollist.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class ACLWebRoutable implements RestletRoutable {


    /**
     * Create the Restlet router and bind to the proper resources.
     */
	@Override
	public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/rules/json", ACLRuleResource.class);
        router.attach("/clear/json", ClearACRulesResource.class);
        return router;
	}

    /**
     * Set the base path for the ACL
     */
	@Override
	public String basePath() {
        return "/wm/acl";
	}

}
