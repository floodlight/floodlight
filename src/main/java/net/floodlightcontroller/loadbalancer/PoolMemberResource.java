package net.floodlightcontroller.loadbalancer;

import java.util.Collection;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolMemberResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(PoolMemberResource.class);
    
    @Get("json")
    public Collection <LBMember> retrieve() {
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String poolId = (String) getRequestAttributes().get("pool");
        if (poolId!=null)
            return lbs.listMembersByPool(poolId);
        else
            return null;
    }
}
