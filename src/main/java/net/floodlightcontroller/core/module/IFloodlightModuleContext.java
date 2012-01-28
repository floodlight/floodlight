package net.floodlightcontroller.core.module;

import net.floodlightcontroller.core.IFloodlightService;
	
public interface IFloodlightModuleContext {	
    /**
     * Retrieves a casted version of a module from the registry.
     * @param name The IFloodlightService object type
     * @return The IFloodlightService
     * @throws FloodlightModuleException If the module was not found 
     * or a ClassCastException was encountered.
     */
    public <T extends IFloodlightService> T getServiceImpl(Class<T> service);
}
