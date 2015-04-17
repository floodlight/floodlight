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

    
public interface IFloodlightModuleContext {    
    /**
     * Retrieves a casted version of a module from the registry.
     * @param name The IFloodlightService object type
     * @return The IFloodlightService
     * @throws FloodlightModuleException If the module was not found 
     * or a ClassCastException was encountered.
     */
    public <T extends IFloodlightService> T getServiceImpl(Class<T> service);
    
    /**
     * Returns all loaded services
     * @return A collection of service classes that have been loaded
     */
    public Collection<Class<? extends IFloodlightService>> getAllServices();
    
    /**
     * Gets module specific configuration parameters.
     * @param module The module to get the configuration parameters for
     * @return A key, value map of the configuration options
     */
    public Map<String, String> getConfigParams(IFloodlightModule module);

    /**
     * Gets module specific configuration parameters.
     * @param clazz The class of the module to get configuration parameters for
     * @return A key, value map of the configuration options
     */
    public Map<String, String> getConfigParams(Class<? extends
                                                     IFloodlightModule> clazz);
}
