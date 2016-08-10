package net.floodlightcontroller.core.web;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class HealthCheckResource extends ServerResource {
    
    public static class HealthCheckInfo {
        
        protected boolean healthy;
        
        public HealthCheckInfo() {
            this.healthy = true;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }
    }
    
    @Get("json")
    public HealthCheckInfo healthCheck() {
        // Currently this is the simplest possible health check -- basically
        // just that the controller is still running and able to respond to
        // REST calls.
        // Eventually this should be more sophisticated and do things
        // like monitoring internal data structures of the controller
        // (e.g. async storage queue length).
        return new HealthCheckInfo();
    }

}
