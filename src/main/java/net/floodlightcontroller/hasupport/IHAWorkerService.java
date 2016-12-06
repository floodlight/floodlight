package net.floodlightcontroller.hasupport;

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IHAWorkerService extends IFloodlightService {
	
	public void registerService(String serviceName, IHAWorker haw);
	
	public IHAWorker getService(String serviceName);
	
	public Set<String> getWorkerKeys();

}
