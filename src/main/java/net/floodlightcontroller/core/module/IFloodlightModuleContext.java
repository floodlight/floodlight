package net.floodlightcontroller.core.module;

import java.util.Collection;
import java.util.Map;

	
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
    
    /**
     * Returns all loaded modules
     * @return All Floodlight modules that are going to be loaded
     */
    public Collection<IFloodlightModule> getAllModules();
    
    /**
     * Gets module specific configuration parameters.
     * @param module The module to get the configuration parameters for
     * @return A key, value map of the configuration options
     */
    public Map<String, String> getConfigParams(IFloodlightModule module);
}
