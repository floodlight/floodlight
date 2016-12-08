package net.floodlightcontroller.hasupport.linkdiscovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.floodlightcontroller.hasupport.IFilterQueue;

/**
 * A Queue to store LDupdates
 * 
 * Also filters out duplicates up to a specified 
 * capacity.
 * 
 * Possible improvement:
 * Implement a data structure which can eliminate duplicates 
 * completely, without a threshold on the amount of filtering 
 * it can do which is currently limited by the mapCapacity.
 * 
 * @author Bhargav Srinivasan, Om Kale
 */

public class LDFilterQueue implements IFilterQueue {
	
	protected static Logger logger = LoggerFactory.getLogger(LDFilterQueue.class);
	private static final LDSyncAdapter syncAdapter = new LDSyncAdapter();
	
	public static LinkedBlockingQueue<String> filterQueue = new LinkedBlockingQueue<>();
	public static HashMap<String, String> myMap = new HashMap<String, String>();
	private final Integer mapCapacity = new Integer(1073741000);
	
	public static LinkedBlockingQueue<String> reverseFilterQueue = new LinkedBlockingQueue<>();
	
	public LDFilterQueue(){}
	
	/**
	 * This function hashes the LDupdates received in form of json string 
	 * using md5 hashing and store them in the filter queue and in a map 
	 * if not already present
	 */	 
	
	@Override
	public boolean enqueueForward(String value) {
		try {
			String newMD5 = new String();
			LDHAUtils myMD5 = new LDHAUtils();
			newMD5 = myMD5.calculateMD5Hash(value);
			
			if (LDFilterQueue.myMap.size() >= mapCapacity) {
				//logger.debug("[LDFilterQ Clear] Clearing LDFilterQ's Map");
				LDFilterQueue.myMap.clear();
			}
			
			//logger.debug("[FilterQ] The MD5: {} The Value {}", new Object [] {newMD5,value});
			if( (!myMap.containsKey(newMD5)) && (!value.equals(null)) ){
				filterQueue.offer(value);
				myMap.put(newMD5, value);
			}
			return true;
	
		} 
		catch (Exception e){
			logger.debug("[FilterQ] Exception: enqueueFwd!");
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * This function pushes the LDupdates from the filter 
	 * queue into the syncAdapter
	 */
	
	
	@Override
	public boolean dequeueForward() {
		try {
			ArrayList<String> LDupds = new ArrayList<String>();
			if(! filterQueue.isEmpty() ) {
				filterQueue.drainTo(LDupds);
			}
			if(! LDupds.isEmpty() ) {
				//logger.debug("[FilterQ] The update after drain: {} ", new Object [] {LDupds.toString()});
				syncAdapter.packJSON(LDupds);
				return true;
			} else {
				//logger.debug("[FilterQ] The linked list is empty");
				return false;
			}	
		} catch (Exception e){
			logger.debug("[FilterQ] Dequeue Forward failed!");
			e.printStackTrace();
		}
		
		return false;
	}
	
	@Override
	public void subscribe(String controllerID) {
		syncAdapter.unpackJSON(controllerID);
		return;
	}

	@Override
	public boolean enqueueReverse(String value) {
		try {
			//logger.debug("[ReverseFilterQ] The Value {}", new Object [] {value});
			if( (!value.equals(null)) ){
				reverseFilterQueue.offer(value);
			}
			return true;
		} 
		catch (Exception e){
			//logger.info("[ReverseFilterQ] Exception: enqueueFwd!");
			e.printStackTrace();
			return true;
		}
		
	}

	@Override
	public List<String> dequeueReverse() {
		ArrayList<String> LDupds = new ArrayList<String>();
		try {
			if(! reverseFilterQueue.isEmpty() ) {
				reverseFilterQueue.drainTo(LDupds);
			}
			
			if(! LDupds.isEmpty() ) {
				//logger.debug("[ReverseFilterQ] The update after drain: {} ", new Object [] {LDupds.toString()});
				return LDupds;
			} else {
				//logger.debug("[ReverseFilterQ] The linked list is empty");
			}
			return LDupds;
			
		} catch (Exception e){
			logger.debug("[ReverseFilterQ] Dequeue Forward failed!");
			e.printStackTrace();
		}
		
		return LDupds;
		
	}

}
