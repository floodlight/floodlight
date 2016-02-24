/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

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
