package net.floodlightcontroller.hasupport;


import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightModule;

/**
* IHAWorker
* 
* This interface describes the functions which are 
* necessary if you want to implement a new HAWorker class
* in addition to the Topology, Link Discovery etc. HAWorkers
* that already exist. The publish hook connects to the IFilterQueue's
* enqueueForward, and dequeueForward method in order to populate the 
* queue and push to the syncDB. Similarly, the subscribe hook uses the 
* dequeueReverse method to retrieve updates from the syncDB.
* 
* @author Bhargav Srinivasan, Om Kale
*
*/

public interface IHAWorker extends IFloodlightModule {
	
	public List<String> assembleUpdate();
	
	public boolean publishHook();
	
	public boolean subscribeHook(String controllerID);
	
	

}
