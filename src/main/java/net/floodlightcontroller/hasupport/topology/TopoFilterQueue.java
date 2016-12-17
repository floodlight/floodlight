/**
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.hasupport.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.floodlightcontroller.hasupport.IFilterQueue;

/**
 * Two Queues to store Topology Updates
 * 
 * Filters out duplicates up to a specified 
 * capacity.
 * 
 * Possible improvements:
 * a. Implement a data structure which can eliminate duplicates 
 * completely, without a threshold on the amount of filtering 
 * it can do which is currently limited by the mapCapacity.
 * 
 * @author Bhargav Srinivasan, Om Kale
 */

public class TopoFilterQueue implements IFilterQueue {
	
	protected static Logger logger = LoggerFactory.getLogger(TopoFilterQueue.class);
	private static final TopoSyncAdapter TopoSyncAdapter = new TopoSyncAdapter();
	
	public static LinkedBlockingQueue<String> filterQueue = new LinkedBlockingQueue<>();
	public static HashMap<String, String> myMap = new HashMap<String, String>();
	private final Integer mapCapacity = new Integer(1073741000);
	public static LinkedBlockingQueue<String> reverseFilterQueue = new LinkedBlockingQueue<>();
	
	public TopoFilterQueue(){}
	
	/**
	 * This method hashes the Topology updates received in form of JSON string 
	 * using md5 hashing and store them in the filter queue and in a map 
	 * if not already present.
	 * @return boolean value indicating success or failure
	 */	 
	
	@Override
	public boolean enqueueForward(String value) {
		try {
			String newMD5 = new String();
			TopoUtils myMD5 = new TopoUtils();
			newMD5 = myMD5.calculateMD5Hash(value);
			
			if (TopoFilterQueue.myMap.size() >= mapCapacity) {
				//logger.debug("[TopoFilterQ Clear] Clearing TopoFilterQ's Map");
				TopoFilterQueue.myMap.clear();
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
	 * This method pushes the Topology updates from the filter 
	 * queue into the syncAdapter
	 * @return boolean value indicating success or failure
	 */
	
	@Override
	public boolean dequeueForward() {
		try {
			ArrayList<String> TopoUpds = new ArrayList<String>();
			if(! filterQueue.isEmpty() ) {
				filterQueue.drainTo(TopoUpds);
			}
			if(! TopoUpds.isEmpty() ) {
				//logger.debug("[FilterQ] The update after drain: {} ", new Object [] {TopoUpds.toString()});
				TopoSyncAdapter.packJSON(TopoUpds);
				return true;
			} else {
				//logger.debug("[FilterQ] The linked list is empty");
				return false;
			}	
		} catch (Exception e){
			logger.info("[FilterQ] Dequeue Forward failed!");
			e.printStackTrace();
		}
		
		return false;
	}
	
	/**
	 * This method is used by the subscribeHook to initiate 
	 * the retrieval of updates from the syncDB. This method
	 * returns only after unpackJSON has finished executing.
	 */
	
	@Override
	public void subscribe(String controllerID) {
		TopoSyncAdapter.unpackJSON(controllerID);
		return;
	}

	/**
	 * This method is called by the syncDB in order to enqueue the 
	 * updates that it received from the syncDB.
	 * @return boolean value indicating success.
	 */
	
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
			logger.info("[ReverseFilterQ] Exception: enqueueFwd!");
			e.printStackTrace();
			return true;
		}
		
	}

	/**
	 * This method is used by the subscribeHook in HAWorker, in 
	 * order to finally obtain the updates from the syncDB, in order
	 * to display/process them.
	 * 
	 * @return List<String> of updates in JSON format, which can be
	 * parsed using Jackson.
	 */
	
	@Override
	public List<String> dequeueReverse() {
		ArrayList<String> TopoUpds = new ArrayList<String>();
		try {
			if(! reverseFilterQueue.isEmpty() ) {
				reverseFilterQueue.drainTo(TopoUpds);
			}
			
			if(! TopoUpds.isEmpty() ) {
				//logger.debug("[ReverseFilterQ] The update after drain: {} ", new Object [] {TopoUpds.toString()});
				return TopoUpds;
			} else {
				//logger.debug("[ReverseFilterQ] The linked list is empty");
			}
			return TopoUpds;
			
		} catch (Exception e){
			logger.debug("[ReverseFilterQ] Dequeue Forward failed!");
			e.printStackTrace();
		}
		
		return TopoUpds;
		
	}

}