package net.floodlightcontroller.core.module;

import java.util.Collection;

import net.floodlightcontroller.core.IFloodlightService;

/**
 * Defines an interface for loadable Floodlight modules.
 * 
 * At a high level, these functions are called in the following order:
 * <ol>
 * <li> getServices() : what services does this module provide
 * <li> getDependencies() : list the dependencies
 * <li> init() : internal initializations (don't touch other modules)
 * <li> startUp() : external initializations (<em>do</em> touch other modules)
 * </ol>
 * 
 * @author alexreimers
 */
public interface IFloodlightModule {
	
	/**
	 * Return the list of interfaces that this module implements.
	 * All interfaces must inherit IFloodlightService
	 * @return
	 */
	
	public Collection<Class<? extends IFloodlightService>> getServices();
	
	public Collection<IFloodlightService> getServiceImpls();
	
	/**
	 * Get a list of Modules that this module depends on.  The module system
	 * will ensure that each these dependencies is resolved before the subsequent calls to init().
	 * 
	 * @return
	 */
	
	public Collection<? extends IFloodlightService> getDependencies();
	
	/**
	 * This is a hook for each module to do its <em>internal</em> initialization, 
	 * e.g., call setService(context.getService("Service"))
	 * 
	 * All module dependencies are resolved when this is called, but not every module is initialized.
	 * 
	 * @param context
	 * @throws FloodlightModuleException
	 */
	
	void init(FloodlightModuleContext context) throws FloodlightModuleException;
	
	/**
	 * This is a hook for each module to do its <em>external</em> initializations,
	 * e.g., register for callbacks or query for state in other modules
	 * 
	 * It is expected that this function will not block and that modules that want
	 * non-event driven CPU will spawn their own threads.
	 * 
	 * @param context
	 */
	
	void startUp(FloodlightModuleContext context); 
	
	// TODO add getName() getId()
	
}
