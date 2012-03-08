package net.floodlightcontroller.core.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The service registry for an IFloodlightProvider.
 * @author alexreimers
 */
public class FloodlightModuleContext implements IFloodlightModuleContext {
	protected Map<Class<? extends IFloodlightService>, IFloodlightService> serviceMap;
	protected Map<Class<? extends IFloodlightModule>, Map<String, String>> configParams;
	
	/**
	 * Creates the ModuleContext for use with this IFloodlightProvider.
	 * This will be used as a module registry for all IFloodlightModule(s).
	 */
	public FloodlightModuleContext() {
		serviceMap = 
		        new HashMap<Class<? extends IFloodlightService>,
		                              IFloodlightService>();
		configParams =
		        new HashMap<Class<? extends IFloodlightModule>,
		                        Map<String, String>>();
	}
	
	/**
	 * Adds a IFloodlightModule for this Context.
	 * @param clazz the service class
	 * @param module The IFloodlightModule to add to the registry
	 */
	public void addService(Class<? extends IFloodlightService> clazz, 
	                       IFloodlightService service) {
		serviceMap.put(clazz, service);
	}
	
	@SuppressWarnings("unchecked")
    @Override
	public <T extends IFloodlightService> T getServiceImpl(Class<T> service) {
	    IFloodlightService s = serviceMap.get(service);
		return (T)s;
	}
	

	/**
	 * Gets all the loaded services
	 * @return The colleciton of loaded services
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getAllServices() {
	    return serviceMap.keySet();
	}
	
	/**
	 * Gets the configuration parameter map for a module
	 * @param module The module to get the configuration map for, usually yourself
	 * @return A map containing all the configuration parameters for the module, may be empty
	 */
	@Override
	public Map<String, String> getConfigParams(IFloodlightModule module) {
	    Map<String, String> retMap = configParams.get(module.getClass());
	    if (retMap == null) {
	        // Return an empty map if none exists so the module does not
	        // need to null check the map
	        retMap = new HashMap<String, String>();
	        configParams.put(module.getClass(), retMap);
	    }
	    return retMap;
	}
	
	/**
	 * Adds a configuration parameter for a module
	 * @param mod The fully qualified module name to add the parameter to
	 * @param key The configuration parameter key
	 * @param value The configuration parameter value
	 */
	public void addConfigParam(IFloodlightModule mod, String key, String value) {
	    Map<String, String> moduleParams = configParams.get(mod.getClass());
	    moduleParams.put(key, value);
	}
	
	/**
	 * We initialize empty configuration maps for each module to be loaded.
	 * This way each module doens't have to null check their map.
	 * @param moduleSet The modules to initialize maps for
	 */
	public void createConfigMaps(Set<IFloodlightModule> moduleSet) {
	    for (IFloodlightModule mod : moduleSet) {
	        Map<String, String> moduleParams = new HashMap<String, String>();
	        configParams.put(mod.getClass(), moduleParams);
	    }
	}
 }
