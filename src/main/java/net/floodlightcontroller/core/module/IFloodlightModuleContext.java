package net.floodlightcontroller.core.module;

import java.util.Collection;

	
public interface IFloodlightModuleContext {	
    /**
     * Retrieves a casted version of a module from the registry.
     * @param name The IFloodlightService object type
     * @return The IFloodlightService
     * @throws FloodlightModuleException If the module was not found 
     * or a ClassCastException was encountered.
     */
    public <T extends IFloodlightService> T getServiceImpl(Class<T> service);
    
    /**
     * Returns all loaded services
     * @return A collection of service classes that have been loaded
     */
    public Collection<Class<? extends IFloodlightService>> getAllServices();
}
