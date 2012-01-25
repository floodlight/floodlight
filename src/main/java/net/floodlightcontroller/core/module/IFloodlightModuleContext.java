package net.floodlightcontroller.core.module;

import net.floodlightcontroller.core.IFloodlightService;
	
public interface IFloodlightModuleContext {	
	//TODO FIX THIS COMMENT
	/**
	 * Retrieves a casted version of a module from the registry.
	 * @return The module casted to the correct type
	 */
	public IFloodlightService getService(
			Class<? extends IFloodlightService> service);
}
