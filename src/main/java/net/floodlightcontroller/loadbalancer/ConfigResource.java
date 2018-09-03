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

package net.floodlightcontroller.loadbalancer;

import java.util.Collections;

import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

public class ConfigResource extends ServerResource{

	@Post
	@Get
	@Put
	public Object config() {
		ILoadBalancerService lbs = (ILoadBalancerService) getContext().getAttributes().get(ILoadBalancerService.class.getCanonicalName());
		
		if (getReference().getPath().contains(LoadBalancerWebRoutable.ENABLE_STR)) {
			int status = lbs.healthMonitoring(true);
			if(status == -1){
				throw new ResourceException(409);
			} else
				return Collections.singletonMap("health monitors", "enabled");
		}

		if (getReference().getPath().contains(LoadBalancerWebRoutable.DISABLE_STR)) {
			int status = lbs.healthMonitoring(false);
			if(status == -1){
				throw new ResourceException(409);
			} else
				return Collections.singletonMap("health monitors", "disabled");
		}

		if (getReference().getPath().contains(LoadBalancerWebRoutable.MONITORS_STR)) {
			String period = (String) getRequestAttributes().get("period");
			try{
				int val = Integer.valueOf(period);
				return lbs.setMonitorsPeriod(val);
			}catch(Exception e) {
				if (getReference().getPath().contains(LoadBalancerWebRoutable.PERIOD_STR)) {
					return lbs.getMonitorsPeriod();
			
				} else
					throw new ResourceException(400);

			}	
		}
		
		return Collections.singletonMap("ERROR", "Unimplemented configuration option.");
	}
	
	@Delete
	public Object clearLbData() {
		ILoadBalancerService lbs = (ILoadBalancerService) getContext().getAttributes().get(ILoadBalancerService.class.getCanonicalName());
		
		if (getReference().getPath().contains(LoadBalancerWebRoutable.CLEAR_STR)) {
			return lbs.clearAllLb();

		} else
			return Collections.singletonMap("ERROR", "Unimplemented configuration option.");
	}
}