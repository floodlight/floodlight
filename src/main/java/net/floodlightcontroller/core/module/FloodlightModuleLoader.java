package net.floodlightcontroller.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import net.floodlightcontroller.core.IFloodlightService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO fill this in
 * @author alexreimers
 *
 */
public class FloodlightModuleLoader {
    protected static Logger logger = 
            LoggerFactory.getLogger(FloodlightModuleLoader.class);

    protected FloodlightModuleContext floodlightModuleContext;
	protected Map<Class<? extends IFloodlightService>,
	              Collection<IFloodlightModule>> serviceMap;
	protected Map<String, IFloodlightModule> moduleNameMap;
	
	public FloodlightModuleLoader() {
	    floodlightModuleContext = new FloodlightModuleContext();
		serviceMap = 
		        new HashMap<Class<? extends IFloodlightService>,
                            Collection<IFloodlightModule>>();
		moduleNameMap = new HashMap<String, IFloodlightModule>();
	}
	
	public IFloodlightModuleContext getModules() 
	        throws FloodlightModuleException {
	    findAllModules();
	    
	    
	    return floodlightModuleContext;
	}
	
	/**
	 * Finds all IFloodlightModule(s) in the classpath.
	 */
	protected void findAllModules() throws FloodlightModuleException {
	    // Get all the current modules in the classpath
        ServiceLoader<IFloodlightModule> moduleLoader
            = ServiceLoader.load(IFloodlightModule.class);
	    // Iterate for each module, iterate through and add it's services
	    for (IFloodlightModule m : moduleLoader) {
	        logger.debug("Found module " + m.getClass().getName());

	        // Set up moduleNameMap
	        moduleNameMap.put(m.getClass().getCanonicalName(), m);

	        // Set up serviceMap
	        Collection<Class<? extends IFloodlightService>> servs =
	                m.getServices();
	        if (servs != null) {
	            for (Class<? extends IFloodlightService> s : servs) {
	                Collection<IFloodlightModule> mods = 
	                        serviceMap.get(s);
	                if (mods == null) {
	                    mods = new ArrayList<IFloodlightModule>();
	                    serviceMap.put(s, mods);
	                }
	                mods.add(m);
	            }
	        }
	    }
	}
	
	public void loadModulesFromConfig() throws FloodlightModuleException {
	    findAllModules();
	    initModule("net.floodlightcontroller.core.CoreModule");
	    startupModules();
	    
	    /*
	     * first read modules.json
	     * go through modules 1 by 1
	     *     take name of module
	     *     call loadModules();
	     *     call module.init();
	     *      
	     *  for each module:
	     *     call module.startUp();
	     * 
	     */
	}
	
	protected void startupModules() {
	    for (IFloodlightModule m : floodlightModuleContext.getModules()) {
	        m.startUp(floodlightModuleContext);
	    }
	}

    protected void initModule(String moduleName) throws FloodlightModuleException {
	    IFloodlightModule module = moduleNameMap.get(moduleName);
	    if (module == null) {
	        throw new FloodlightModuleException("Module " + 
	                                            moduleName + " not found");
	    }
	    Collection<? extends IFloodlightService> deps = 
	            module.getDependencies();
	    if (deps != null) {
	        for (IFloodlightService dep : deps) {
	            Class<? extends IFloodlightService> c = dep.getClass();
	            IFloodlightService s = floodlightModuleContext.getService(c);
	            if (s == null) {
	                Collection<IFloodlightModule> mods = serviceMap.get(dep);
	                // Make sure only one module is loaded
	                if ((mods == null) || (mods.size() == 0)) {
	                    throw new FloodlightModuleException("ERROR! Could not " + 
	                            "find IFloodlightModule that provides service " +
	                            dep.getClass().toString());
	                } else if (mods.size() == 1) {
	                    // Recursively load the module's dependencies recursively
	                    initModule(mods.iterator().next().getClass().toString());
	                } else {
	                    throw new FloodlightModuleException("ERROR! Found more " + 
	                            "than one IFloodlightModule that provides " + 
	                            "service " + dep.getClass().toString() + 
	                            ". Please resolve this in the config");
	                }
	            }
	            // else it's already loaded
	        }
	    }
	    
	    // Get the module's service impls
	    Collection<IFloodlightService> simpls =
	            module.getServiceImpls();
        
	    // init the module
        module.init(floodlightModuleContext);

        // add the module's services to the context
        floodlightModuleContext.addModule(module);
        if (simpls != null) {
            for (IFloodlightService s : simpls) {
                floodlightModuleContext.addService(s);
            }
        }
	}
}
