package net.floodlightcontroller.hasupport;

import java.util.List;

/**
 * Maintain a queue to filter out duplicates before 
 * pushing data into syncDB, and maintain a queue 
 * to receive the data while retrieving it out of the 
 * syncDB.
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
