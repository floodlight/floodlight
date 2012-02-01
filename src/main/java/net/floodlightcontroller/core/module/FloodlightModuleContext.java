package net.floodlightcontroller.core.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The service registry for an IFloodlightProvider.
 * @author alexreimers
 */
public class FloodlightModuleContext implements IFloodlightModuleContext {
	protected Map<Class<? extends IFloodlightService>, IFloodlightService> serviceMap;
	
	/**
	 * Creates the ModuleContext for use with this IFloodlightProvider.
	 * This will be used as a module registry for all IFloodlightModule(s).
	 */
	public FloodlightModuleContext() {
		serviceMap = 
		        new HashMap<Class<? extends IFloodlightService>,
		                              IFloodlightService>();
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

	@Override
	public Collection<Class<? extends IFloodlightService>> getAllServices() {
	    return serviceMap.keySet();
	}
 }
