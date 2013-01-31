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

package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class FloodlightProvider implements IFloodlightModule {
    Controller controller;
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(IFloodlightProviderService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>,
               IFloodlightService> getServiceImpls() {
        controller = new Controller();
        
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>,
                            IFloodlightService>();
        m.put(IFloodlightProviderService.class, controller);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> dependencies =
            new ArrayList<Class<? extends IFloodlightService>>(4);
        dependencies.add(IStorageSourceService.class);
        dependencies.add(IPktInProcessingTimeService.class);
        dependencies.add(IRestApiService.class);
        dependencies.add(ICounterStoreService.class);
        dependencies.add(IFlowCacheService.class);
        dependencies.add(IThreadPoolService.class);
        return dependencies;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
       controller.setStorageSourceService(
           context.getServiceImpl(IStorageSourceService.class));
       controller.setPktInProcessingService(
           context.getServiceImpl(IPktInProcessingTimeService.class));
       controller.setCounterStore(
           context.getServiceImpl(ICounterStoreService.class));
       controller.setFlowCacheMgr(
           context.getServiceImpl(IFlowCacheService.class));
       controller.setRestApiService(
           context.getServiceImpl(IRestApiService.class));
       controller.setThreadPoolService(
           context.getServiceImpl(IThreadPoolService.class));
       controller.init(context.getConfigParams(this));
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        controller.startupComponents();
    }
}
