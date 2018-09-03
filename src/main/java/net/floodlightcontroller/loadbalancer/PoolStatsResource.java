package net.floodlightcontroller.loadbalancer;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolStatsResource extends ServerResource {
protected static Logger log = LoggerFactory.getLogger(PoolStatsResource.class);
    
    @Get("json")
    public LBStats retrieve() {
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String poolId = (String) getRequestAttributes().get("pool");
        if (poolId!=null)
            return lbs.getPoolStats(poolId);
        else
            return null;
    }
}