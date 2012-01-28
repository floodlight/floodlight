package net.floodlightcontroller.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.Set;

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
    protected Map<IFloodlightModule,
                  Collection<Class<? extends 
                                   IFloodlightService>>> moduleServiceMap;
	protected Map<String, IFloodlightModule> moduleNameMap;
	
	public FloodlightModuleLoader() {
	    floodlightModuleContext = new FloodlightModuleContext();
		serviceMap = 
		        new HashMap<Class<? extends IFloodlightService>,
                            Collection<IFloodlightModule>>();
		moduleServiceMap = 
		        new HashMap<IFloodlightModule,
                            Collection<Class<? extends 
                                       IFloodlightService>>>();
		moduleNameMap = new HashMap<String, IFloodlightModule>();
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
	        if (logger.isDebugEnabled()) {
	            logger.debug("Found module " + m.getClass().getName());
	        }

	        // Set up moduleNameMap
	        moduleNameMap.put(m.getClass().getCanonicalName(), m);

	        // Set up serviceMap
	        Collection<Class<? extends IFloodlightService>> servs =
	                m.getServices();
	        if (servs != null) {
	            moduleServiceMap.put(m, servs);
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
	
	public IFloodlightModuleContext loadModulesFromConfig() 
	        throws FloodlightModuleException {
	    logger.debug("Starting module loader");
	    findAllModules();
	    
	    Set<IFloodlightModule> moduleSet = new HashSet<IFloodlightModule>();
	    Map<Class<? extends IFloodlightService>, IFloodlightModule> moduleMap =
                new HashMap<Class<? extends IFloodlightService>,
                            IFloodlightModule>();
	    
	    calculateModuleDeps(moduleMap, moduleSet, "net.floodlightcontroller.core.CoreModule");
	    calculateModuleDeps(moduleMap, moduleSet, "net.floodlightcontroller.forwarding.Forwarding");
	    calculateModuleDeps(moduleMap, moduleSet, "net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher");
	    calculateModuleDeps(moduleMap, moduleSet, "net.floodlightcontroller.core.OFMessageFilterManager");
	    
	    initModules(moduleSet);
	    startupModules(moduleSet);
	    
	    return floodlightModuleContext;
	}
	
	/**
	 * Add a module to the set of modules to load and register its services
	 * @param moduleMap the module map
	 * @param moduleSet the module set
	 * @param module the module to add
	 */
	protected void addModule(Map<Class<? extends IFloodlightService>, 
                                           IFloodlightModule> moduleMap,
                            Set<IFloodlightModule> moduleSet,
                            IFloodlightModule module) {
        if (!moduleSet.contains(module)) {
            Collection<Class<? extends IFloodlightService>> servs =
                    moduleServiceMap.get(module);
            if (servs != null) {
                for (Class<? extends IFloodlightService> c : servs)
                    moduleMap.put(c, module);
            }
            moduleSet.add(module);
        }
	}
	
	/**
	 * Add a module and all its transitive dependencies
	 * @param moduleMap the module map
	 * @param moduleSet the module set
	 * @param moduleName the module name
	 * @throws FloodlightModuleException
	 */
    protected void calculateModuleDeps(Map<Class<? extends IFloodlightService>, 
                                           IFloodlightModule> moduleMap,
                                       Set<IFloodlightModule> moduleSet,
                                       String moduleName) throws FloodlightModuleException {

        IFloodlightModule module = moduleNameMap.get(moduleName);
        if (module == null) {
            throw new FloodlightModuleException("Module " + 
                    moduleName + " not found");
        }

        addModule(moduleMap, moduleSet, module);
        
        Collection<Class<? extends IFloodlightService>> deps = 
                module.getDependencies();

        if (deps != null) {
            for (Class<? extends IFloodlightService> c : deps) {
                IFloodlightModule m = moduleMap.get(c);
                if (m == null) {
                    Collection<IFloodlightModule> mods = serviceMap.get(c);
                    // Make sure only one module is loaded
                    if ((mods == null) || (mods.size() == 0)) {
                        throw new FloodlightModuleException("ERROR! Could not " +
                                "find an IFloodlightModule that provides service " +
                                c.toString());
                    } else if (mods.size() == 1) {
                        IFloodlightModule mod = mods.iterator().next();
                        calculateModuleDeps(moduleMap, moduleSet,
                                            mod.getClass().getCanonicalName());
                    } else {
                        throw new FloodlightModuleException("ERROR! Found more " + 
                                "than one IFloodlightModule that provides " + 
                                "service " + c.toString() + 
                                ". Please resolve this in the config");
                    }
                }
            }
        } 
    }

    /**
     * Allocate service implementations and then init all the modules
     * @param moduleMap
     * @throws FloodlightModuleException
     */
    protected void initModules(Set<IFloodlightModule> moduleSet) 
                                           throws FloodlightModuleException {
        for (IFloodlightModule module : moduleSet) {            
            // Get the module's service instance(s)
            Map<Class<? extends IFloodlightService>, 
                IFloodlightService> simpls = module.getServiceImpls();

            // add its services to the context
            if (simpls != null) {
                for (Entry<Class<? extends IFloodlightService>, 
                        IFloodlightService> s : simpls.entrySet()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Setting " + s.getValue() + 
                                     "  as provider for " + 
                                     s.getKey().getCanonicalName());
                    }
                    floodlightModuleContext.addService(s.getKey(),
                                                       s.getValue());
                }
            }
        }
        for (IFloodlightModule module : moduleSet) {
            // init the module
            if (logger.isDebugEnabled()) {
                logger.debug("Initializing " + 
                             module.getClass().getCanonicalName());
            }
            module.init(floodlightModuleContext);
        }
    }
    
    /**
     * Call each loaded module's startup method
     * @param moduleSet the module set to start up
     */
    protected void startupModules(Set<IFloodlightModule> moduleSet) {
        for (IFloodlightModule m : moduleSet) {
            if (logger.isDebugEnabled()) {
                logger.debug("Starting " + 
                             m.getClass().getCanonicalName());
            }
            m.startUp(floodlightModuleContext);
        }
    }
}
