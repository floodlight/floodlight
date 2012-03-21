package net.floodlightcontroller.threadpool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class ThreadPool implements IThreadPoolService, IFloodlightModule {
    protected ScheduledExecutorService executor = null;
    
    // IThreadPoolService

    @Override
    public ScheduledExecutorService getScheduledExecutor() {
        return executor;
    }
    
    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(IThreadPoolService.class, this);
        // We are the class that implements the service
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        // No dependencies
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
                                 throws FloodlightModuleException {
        executor = Executors.newScheduledThreadPool(15);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // no-op
    }
}
