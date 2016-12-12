package net.floodlightcontroller.hasupport;

import java.util.List;

/**
 * IFilterQueue
 * 
 * Maintain a queue to filter out duplicates before 
 * pushing data into syncDB, and maintain a queue 
 * to receive the data while retrieving it out of the 
 * syncDB. The enqueueForward method connects to the 
 * packJSON method in ISyncAdapter which then pushes the 
 * updates into the syncDB.
 * 
 * The enqueueReverse() method is called by unpackJSON, which
 * then populates the reverse queue. Then the subscribeHook() in
 * HAWorker calls the dequeueReverse() method to finally get the
 * updates.
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */
public interface IFilterQueue {
	
	public boolean enqueueForward(String value);
	
	public boolean dequeueForward();
	
	public void subscribe(String controllerID);
	
	public boolean enqueueReverse(String value);
	
	public List<String> dequeueReverse();
	

}
