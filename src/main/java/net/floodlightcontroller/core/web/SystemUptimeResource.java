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

import net.floodlightcontroller.core.IFloodlightProviderService;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;



public class SystemUptimeResource extends ServerResource {
	
	public class UptimeRest {
		long systemUptimeMsec;

		public long getSystemUptimeMsec() {
			return systemUptimeMsec;
		}
	}
	
	@Get("json")
	public UptimeRest retrieve() {
		IFloodlightProviderService floodlightProvider = 
			(IFloodlightProviderService)getContext().getAttributes().
			get(IFloodlightProviderService.class.getCanonicalName());
		
		UptimeRest uptime = new UptimeRest();
		uptime.systemUptimeMsec = 
		   System.currentTimeMillis() - floodlightProvider.getSystemStartTime();
		
		return (uptime);
	}
}
