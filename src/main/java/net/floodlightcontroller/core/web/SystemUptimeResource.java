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
