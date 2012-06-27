package net.floodlightcontroller.core.web;

import java.util.Map;

import org.restlet.resource.Get;

import net.floodlightcontroller.core.module.ModuleLoaderResource;

public class LoadedModuleLoaderResource extends ModuleLoaderResource {
	/**
	 * Retrieves information about all modules available
	 * to Floodlight.
	 * @return Information about all modules available.
	 */
    @Get("json")
    public Map<String, Object> retrieve() {
    	return retrieveInternal(true);
    }
}
