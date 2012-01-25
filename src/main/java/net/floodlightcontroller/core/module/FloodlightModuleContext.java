package net.floodlightcontroller.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.IFloodlightService;

/**
 * The service registry for an IFloodlightProvider.
 * @author alexreimers
 */
public class FloodlightModuleContext implements IFloodlightModuleContext {
	protected Map<Class<? extends IFloodlightService>, IFloodlightService> serviceMap;
	protected Collection<IFloodlightModule> modules;
	
	/**
	 * Creates the ModuleContext for use with this IFloodlightProvider.
	 * This will be used as a module registry for all IFloodlightModule(s).
	 */
	public FloodlightModuleContext() {
		serviceMap = new ConcurrentHashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		modules = new ArrayList<IFloodlightModule>();
	}
	
	/**
	 * Adds a IFloodlightModule for this Context.
	 * @param module The IFloodlightModule to add to the registry
	 * @param name The fully qualified name for the module that describes 
	 * the service it provides, i.e. "deviceManager.floodlight"
	 */
	public void addService(IFloodlightService service) {
		Class<? extends IFloodlightService> serviceClass = service.getClass();
		serviceMap.put(serviceClass, service);
	}
	
	/**
	 * Retrieves a casted version of a module from the registry.
	 * @param name The IFloodlightService object type
	 * @return The IFloodlightService
	 * @throws FloodlightModuleException If the module was not found or a ClassCastException was encountered.
	 */
	public IFloodlightService getService(Class<? extends IFloodlightService> service) {
		return serviceMap.get(service);
	}
	
	/**
	 * Add a module to the list of initialized modules
	 * @param module
	 */
	public void addModule(IFloodlightModule module) {
	    modules.add(module);
	}

	/**
	 * Get the list of initialized modules.
	 * @return the list of modules that have been initialized
	 */
	public Collection<IFloodlightModule> getModules() {
	    return modules;
	}
 }
