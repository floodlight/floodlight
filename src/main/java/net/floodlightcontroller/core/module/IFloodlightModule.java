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
import java.util.Map;

/**
 * Defines an interface for loadable Floodlight modules.
 * 
 * At a high level, these functions are called in the following order:
 * <ol>
 * <li> getServices() : what services does this module provide
 * <li> getDependencies() : list the dependencies
 * <li> init() : internal initializations (<em>don't</em> touch other modules)
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
    
    public Collection<Class<? extends IFloodlightService>> getModuleServices();
    
    /**
     * Instantiate (as needed) and return objects that implement each
     * of the services exported by this module.  The map returned maps
     * the implemented service to the object.  The object could be the
     * same object or different objects for different exported services.
     * @return The map from service interface class to service implementation
     */
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls();
    
    /**
     * Get a list of Modules that this module depends on.  The module system
     * will ensure that each these dependencies is resolved before the 
     * subsequent calls to init().
     * @return The Collection of IFloodlightServices that this module depends
     *         on.
     */
    
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies();
    
    /**
     * This is a hook for each module to do its <em>internal</em> initialization, 
     * e.g., call setService(context.getService("Service"))
     * 
     * All module dependencies are resolved when this is called, but not every module 
     * is initialized.
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
     * @throws FloodlightModuleException 
     */
    
    void startUp(FloodlightModuleContext context) throws FloodlightModuleException;
}
