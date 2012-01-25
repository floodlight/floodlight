package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Collection;

import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;

public class CoreModule implements IFloodlightModule {
    protected static Collection<Class<? extends IFloodlightService>> services;
    Controller controller;
    
    static {
        services = new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(IFloodlightProvider.class);
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getServices() {
        return services;
    }

    @Override
    public Collection<IFloodlightService> getServiceImpls() {
        Collection<IFloodlightService> l = new ArrayList<IFloodlightService>(1);
        controller = new Controller();
        l.add(controller);
        return l;
    }

    @Override
    public Collection<? extends IFloodlightService> getDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        controller.init();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        controller.startupComponents();
        controller.run();
    }

}
