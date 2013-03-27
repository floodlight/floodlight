package net.floodlightcontroller.debugcounter;

import java.util.Collection;
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
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }

    @Override
    public
            void
            init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {

    }

    @Override
    public void startUp(FloodlightModuleContext context) {

    }

    @Override
    public void updateCounter(String moduleCounterName) {

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

}
