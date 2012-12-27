package net.floodlightcontroller.loadbalancer;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;
import net.floodlightcontroller.virtualnetwork.NoOp;

public class LoadBalancerWebRoutable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/vips/", VipsResource.class); // GET, POST
        router.attach("/vips/{vip}", VipsResource.class); // GET, PUT, DELETE 
        router.attach("/pools/", PoolsResource.class); // GET, POST
        router.attach("/pools/{pool}", PoolsResource.class); // GET, PUT, DELETE
        router.attach("/members/", MembersResource.class); // GET, POST
        router.attach("/members/{member}", MembersResource.class); // GET, PUT, DELETE
        router.attach("/pools/{pool}/members", PoolMemberResource.class); //GET
        router.attach("/health_monitors/", MonitorsResource.class); //GET, POST
        router.attach("/health_monitors/{monitor}", MonitorsResource.class); //GET, PUT, DELETE        
        router.attachDefault(NoOp.class);
        return router;
     }

    @Override
    public String basePath() {
        return "/quantum/v1.0";
    }

}
