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
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public
            void
            init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCounter(String moduleCounterName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void flushCounters() {
        // TODO Auto-generated method stub

    }

    @Override
    public void resetCounter(String moduleCounterName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void resetAllCounters() {
        // TODO Auto-generated method stub

    }

    @Override
    public void resetAllModuleCounters(String moduleName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void enableCtrOnDemand(String moduleCounterName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void disableCtrOnDemand(String moduleCounterName) {
        // TODO Auto-generated method stub

    }

    @Override
    public DebugCounterInfo getCounterValue(String moduleCounterName) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<DebugCounterInfo> getAllCounterValues() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<DebugCounterInfo> getModuleCounterValues() {
        // TODO Auto-generated method stub
        return null;
    }

}
