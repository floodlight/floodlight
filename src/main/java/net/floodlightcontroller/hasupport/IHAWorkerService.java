package net.floodlightcontroller.hasupport;

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
* IHAWorkerService
* This service can be used to obtain the objects 
* containing the several HAWorker classes, which enable
* you to call the publish and subscribe hooks of those
* HAWorkers. This enables any Floodlight module to leverage the 
* HASupport module in order to obtain the updates/state 
* information stored by these HAWorkers.
* 
* @author Bhargav Srinivasan, Om Kale
*
*/

public interface IHAWorkerService extends IFloodlightService {
	
	public void registerService(String serviceName, IHAWorker haw);
	
	public IHAWorker getService(String serviceName);
	
	public Set<String> getWorkerKeys();

}
