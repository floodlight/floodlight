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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The service registry for an IFloodlightProvider.
 * @author alexreimers
 */
public class FloodlightModuleContext implements IFloodlightModuleContext {
    protected Map<Class<? extends IFloodlightService>, IFloodlightService> serviceMap;
    protected Map<Class<? extends IFloodlightModule>, Map<String, String>> configParams;
    protected Collection<IFloodlightModule> moduleSet;
    
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
     * @param service The IFloodlightService to add to the registry
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
    
    @Override
    public Collection<IFloodlightModule> getAllModules() {
        return moduleSet;
    }
    
    public void addModules(Collection<IFloodlightModule> modSet) {
        if (this.moduleSet == null) 
            this.moduleSet = new ArrayList<IFloodlightModule>();
        this.moduleSet.addAll(modSet);
    }

    @Override
    public Map<String, String> getConfigParams(IFloodlightModule module) {
        Class<? extends IFloodlightModule> clazz = module.getClass();
        return getConfigParams(clazz);
    }

    @Override
    public Map<String, String> getConfigParams(Class<? extends IFloodlightModule> clazz) {
        Map<String, String> retMap = configParams.get(clazz);
        if (retMap == null) {
            // Return an empty map if none exists so the module does not
            // need to null check the map
            retMap = new HashMap<String, String>();
            configParams.put(clazz, retMap);
        }

        // also add any configuration parameters for superclasses, but
        // only if more specific configuration does not override it
        for (Class<? extends IFloodlightModule> c : configParams.keySet()) {
            if (c.isAssignableFrom(clazz)) {
                for (Map.Entry<String, String> ent : configParams.get(c).entrySet()) {
                    if (!retMap.containsKey(ent.getKey())) {
                        retMap.put(ent.getKey(), ent.getValue());
                    }
                }
            }
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
        if (moduleParams == null) {
            moduleParams = new HashMap<String, String>();
            configParams.put(mod.getClass(), moduleParams);
        }
        moduleParams.put(key, value);
    }
 }
