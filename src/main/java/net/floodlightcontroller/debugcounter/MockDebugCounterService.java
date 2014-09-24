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

public class MockDebugCounterService implements IFloodlightModule, IDebugCounterService {


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
    public boolean registerModule(String moduleName) {
        return true;
    }

    @Override
    public IDebugCounter registerCounter(String moduleName,
                                         String counterHierarchy,
                                         String counterDescription,
                                         MetaData... metaData) {
        return new MockCounterImpl();
    }

    @Override
    public boolean
    resetCounterHierarchy(String moduleName, String counterHierarchy) {
        return true;
    }

    @Override
    public void resetAllCounters() {
    }

    @Override
    public boolean resetAllModuleCounters(String moduleName) {
        return true;
    }

    @Override
    public List<DebugCounterResource>
    getCounterHierarchy(String moduleName, String counterHierarchy) {
        return Collections.emptyList();
    }

    @Override
    public List<DebugCounterResource> getAllCounterValues() {
        return Collections.emptyList();
    }

    @Override
    public List<DebugCounterResource>
    getModuleCounterValues(String moduleName) {
        return Collections.emptyList();
    }

    public static class MockCounterImpl implements IDebugCounter {
        @Override
        public void increment() {
        }

        @Override
        public void add(long incr) {
        }

        @Override
        public long getCounterValue() {
            return -1;
        }

		@Override
		public long getLastModified() {
			return -1;
		}

		@Override
		public void reset() {			
		}
    }

	@Override
	public boolean removeCounterHierarchy(String moduleName,
			String counterHierarchy) {
		return true;
	}

}
