package net.floodlightcontroller.debugcounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class NullDebugCounter implements IFloodlightModule, IDebugCounterService {

    @Override
    public boolean registerCounter(String moduleCounterName,
                                   String counterDescription,
                                   CounterType counterType) {
        return false;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(IDebugCounterService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(IDebugCounterService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {

    }

    @Override
    public void startUp(FloodlightModuleContext context) {

    }

    @Override
    public void updateCounter(String moduleCounterName) {

    }

    @Override
    public void updateCounter(String moduleCounterName, int incr) {

    }
    
    @Override
    public void flushCounters() {

    }

    @Override
    public void resetCounter(String moduleCounterName) {

    }

    @Override
    public void resetAllCounters() {

    }

    @Override
    public void resetAllModuleCounters(String moduleName) {

    }

    @Override
    public void enableCtrOnDemand(String moduleCounterName) {

    }

    @Override
    public void disableCtrOnDemand(String moduleCounterName) {

    }

    @Override
    public DebugCounterInfo getCounterValue(String moduleCounterName) {
        return null;
    }

    @Override
    public List<DebugCounterInfo> getAllCounterValues() {
        return null;
    }

    @Override
    public List<DebugCounterInfo> getModuleCounterValues(String moduleName) {
        return null;
    }

    @Override
    public boolean containsMCName(String moduleCounterName) {
        return false;
    }

    @Override
    public boolean containsModName(String moduleName) {
        return false;
    }

}
