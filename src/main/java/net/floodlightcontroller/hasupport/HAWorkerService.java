package net.floodlightcontroller.hasupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * HAWorkerService
 * This class allows the individual *HAWorker classes
 * to register to the workers HashMap to hold their current
 * objects, in order to facilitate calling their publish and
 * subscribe hooks dynamically.
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class HAWorkerService implements IHAWorkerService, IFloodlightModule {
	
	public static HashMap<String,IHAWorker> workers = new HashMap<>();

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IHAWorkerService.class);
		return l;
	}
	
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IHAWorkerService.class, this);
		return m;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		return;
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		return;
	}

	@Override
	public void registerService(String serviceName, IHAWorker haw) {
		// TODO Auto-generated method stub
		synchronized (workers) {
			workers.putIfAbsent(serviceName, haw);
		}

	}

	@Override
	public IHAWorker getService(String serviceName) {
		// TODO Auto-generated method stub
		synchronized (workers) {
			return workers.get(serviceName);
		}
	}
	
	public Set<String> getWorkerKeys() {
		// TODO Auto-generated method stub
		synchronized (workers) {
			return workers.keySet();
		}
	}

}
