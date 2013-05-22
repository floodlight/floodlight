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
import net.floodlightcontroller.debugcounter.DebugCounter.DebugCounterInfo;

public class NullDebugCounter implements IFloodlightModule, IDebugCounterService {


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
    public void flushCounters() {

    }

    @Override
    public void resetAllCounters() {

    }

    @Override
    public void resetAllModuleCounters(String moduleName) {

    }


    @Override
    public void resetCounterHierarchy(String moduleName, String counterName) {

    }

    @Override
    public void enableCtrOnDemand(String moduleName, String counterName) {

    }

    @Override
    public void disableCtrOnDemand(String moduleName, String counterName) {

    }

    @Override
    public List<DebugCounterInfo> getCounterHierarchy(String moduleName,
                                                      String counterName) {
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
    public boolean containsModuleCounterName(String moduleName,
                                             String counterName) {
        return false;
    }

    @Override
    public boolean containsModuleName(String moduleName) {
        return false;
    }

    @Override
    public
            int
            registerCounter(String moduleName, String counterName,
                            String counterDescription,
                            CounterType counterType, Object[] metaData)
                                                                       throws MaxCountersRegistered {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void updateCounter(int counterId, boolean flushNow) {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCounter(int counterId, int incr, boolean flushNow) {
        // TODO Auto-generated method stub

    }


}
