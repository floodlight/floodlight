package net.floodlightcontroller.core.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleLoaderResource extends ServerResource {
    protected static Logger log = 
            LoggerFactory.getLogger(ModuleLoaderResource.class);
        
        @Get("json")
        public Map<String, Object> retrieve() {    
            Map<String, Object> model = new HashMap<String, Object>();

            Set<String> loadedModules = new HashSet<String>();
            for (Object val : getContext().getAttributes().values()) {
            	if ((val instanceof IFloodlightModule) || (val instanceof IFloodlightService)) {
            		String serviceImpl = val.getClass().getCanonicalName();
            		loadedModules.add(serviceImpl);
            		// log.debug("Tracking serviceImpl " + serviceImpl);
            	}
            }

            for (String moduleName : 
            				FloodlightModuleLoader.moduleNameMap.keySet() ) {
            	Map<String,Object> moduleInfo = new HashMap<String, Object>();

            	IFloodlightModule module = 
            				FloodlightModuleLoader.moduleNameMap.get(
            						moduleName);
            		
            	Collection<Class<? extends IFloodlightService>> deps = 
            			module.getModuleDependencies();
            	if ( deps == null)
                	deps = new HashSet<Class<? extends IFloodlightService>>();
                moduleInfo.put("depends", deps);
            	
                Collection<Class<? extends IFloodlightService>> provides = 
                		module.getModuleServices();
            	if ( provides == null)
                	provides = new HashSet<Class<? extends IFloodlightService>>();
            	moduleInfo.put("provides", provides);            		

        		moduleInfo.put("loaded", false);	// not loaded, by default

            	// check if this module is loaded directly
            	if (loadedModules.contains(module.getClass().getCanonicalName())) {
            		moduleInfo.put("loaded", true);  			
            	} else {
            		// if not, then maybe one of the services it exports is loaded
            		for (Class<? extends IFloodlightService> service : provides) {
            			String modString = module.getServiceImpls().get(service).getClass().getCanonicalName();
            			if (loadedModules.contains(modString))
                    		moduleInfo.put("loaded", true);
            			/* else 
            				log.debug("ServiceImpl not loaded " + modString); */
            		}
            	}

            	
            	model.put(moduleName, moduleInfo);
            }            
            return model;
        }      
}
