package net.floodlightcontroller.core.web;

import net.floodlightcontroller.core.IFloodlightProviderService;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class SystemUptimeResource extends ServerResource {
	@Get("json")
	public long retrieve() {
		IFloodlightProviderService floodlightProvider = 
			(IFloodlightProviderService)getContext().getAttributes().
			get(IFloodlightProviderService.class.getCanonicalName());
		return floodlightProvider.getSystemUptime();
	}
}
