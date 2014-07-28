/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.core.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Returns list of modules loaded by Floodlight.
 * @author Rob Sherwood
 */
public class ModuleLoaderResource extends ServerResource {
    protected static Logger log = 
            LoggerFactory.getLogger(ModuleLoaderResource.class);
    
    /**
     * Retrieves information about loaded modules.
     * @return Information about loaded modules.
     */
    @Get("json")
    public Map<String, Object> retrieve() {
    	return retrieveInternal(false);
    }
    
    /**
     * Retrieves all modules and their dependencies available
     * to Floodlight.
     * @param loadedOnly Whether to return all modules available or only the ones loaded.
     * @return Information about modules available or loaded.
     */
    public Map<String, Object> retrieveInternal(boolean loadedOnly) {    
        Map<String, Object> model = new HashMap<String, Object>();
        FloodlightModuleLoader floodlightModuleLoader =
                (FloodlightModuleLoader) getContext().getAttributes().
                get(FloodlightModuleLoader.class.getCanonicalName());

        Set<String> loadedModules = new HashSet<String>();
        for (Object val : getContext().getAttributes().values()) {
        	if ((val instanceof IFloodlightModule) || (val instanceof IFloodlightService)) {
        		String serviceImpl = val.getClass().getCanonicalName();
        		loadedModules.add(serviceImpl);
        		// log.debug("Tracking serviceImpl " + serviceImpl);
        	}
        }

        for (String moduleName : floodlightModuleLoader.getModuleNameMap().keySet() ) {
        	Map<String,Object> moduleInfo = new HashMap<String, Object>();

        	IFloodlightModule module = 
        			floodlightModuleLoader.getModuleNameMap().get(moduleName);
        		
        	Collection<Class<? extends IFloodlightService>> deps = 
        			module.getModuleDependencies();
        	if ( deps == null)
            	deps = new HashSet<Class<? extends IFloodlightService>>();
        	Map<String,Object> depsMap = new HashMap<String, Object> ();
        	for (Class<? extends IFloodlightService> service : deps) {
        		Object serviceImpl = getContext().getAttributes().get(service.getCanonicalName());
        		if (serviceImpl != null)
        			depsMap.put(service.getCanonicalName(), serviceImpl.getClass().getCanonicalName());
        		else
        			depsMap.put(service.getCanonicalName(), "<unresolved>");

        	}
            moduleInfo.put("depends", depsMap);
        	
            Collection<Class<? extends IFloodlightService>> provides = 
            		module.getModuleServices();
        	if ( provides == null)
            	provides = new HashSet<Class<? extends IFloodlightService>>();
        	Map<String,Object> providesMap = new HashMap<String,Object>();
        	for (Class<? extends IFloodlightService> service : provides) {
        		providesMap.put(service.getCanonicalName(), module.getServiceImpls().get(service).getClass().getCanonicalName());
        	}
        	moduleInfo.put("provides", providesMap);            		

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

        	if ((Boolean)moduleInfo.get("loaded")|| !loadedOnly )
        		model.put(moduleName, moduleInfo);
        }            
        return model;
    }
}