package net.floodlightcontroller.hasupport;

import java.util.List;

/**
 * Maintain a queue to filter out duplicates before pulling 
 * or pushing data into syncDB 
 * @author omkale
 *
 */
public interface IFilterQueue {
	
	public boolean enqueueForward(String value);
	
	public boolean dequeueForward();
	
	public void subscribe(String controllerID);
	
	public boolean enqueueReverse(String value);
	
	public List<String> dequeueReverse();
	

}
