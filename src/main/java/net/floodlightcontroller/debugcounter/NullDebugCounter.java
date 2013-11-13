package net.floodlightcontroller.debugcounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    public void resetCounterHierarchy(String moduleName, String counterHierarchy) {

    }

    @Override
    public void enableCtrOnDemand(String moduleName, String counterHierarchy) {

    }

    @Override
    public void disableCtrOnDemand(String moduleName, String counterHierarchy) {

    }

    @Override
    public List<DebugCounterInfo> getCounterHierarchy(String moduleName,
                                                      String counterHierarchy) {
        return Collections.emptyList();
    }

    @Override
    public List<DebugCounterInfo> getAllCounterValues() {
        return Collections.emptyList();
    }

    @Override
    public List<DebugCounterInfo> getModuleCounterValues(String moduleName) {
        return Collections.emptyList();
    }

    @Override
    public boolean containsModuleCounterHierarchy(String moduleName,
                                             String counterHierarchy) {
        return false;
    }

    @Override
    public boolean containsModuleName(String moduleName) {
        return false;
    }

    @Override
    public
            IDebugCounter
            registerCounter(String moduleName, String counterHierarchy,
                            String counterDescription,
                            CounterType counterType, String... metaData)
                                 throws MaxCountersRegistered {
        return new NullCounterImpl();
    }

    @Override
    public List<String> getModuleList() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getModuleCounterList(String moduleName) {
        return Collections.emptyList();
    }

    public class NullCounterImpl implements IDebugCounter {

        @Override
        public void updateCounterWithFlush() {

        }

        @Override
        public void updateCounterNoFlush() {

        }

        @Override
        public void updateCounterWithFlush(int incr) {
        }

        @Override
        public void updateCounterNoFlush(int incr) {

        }

        @Override
        public long getCounterValue() {
            return -1;
        }

    }
}
