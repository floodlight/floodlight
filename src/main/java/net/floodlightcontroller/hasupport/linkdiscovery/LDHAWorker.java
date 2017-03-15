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

package net.floodlightcontroller.hasupport.linkdiscovery;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


import org.sdnplatform.sync.IStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.hasupport.IHAWorker;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;

/**
 * LDHAWorker
 * 
 * This is the HAWorker class that is used to push updates into the 
 * queue using the publishHook method. Also, subscribe to updates from
 * the syncDB, and display/process them if needed using subscribeHook.
 * 
 * The assemble update function is used to convert the updates into a JSON
 * for easier storage (i.e. as a string which can then be processed using 
 * Jackson) and storing them into the syncDB. JSON relational mapping is
 * performed in the SyncAdapter class, because the data in the updates are 
 * relational in nature and this improves write efficiency.
 * 
 * Forward flow:
 * 
 *  HAWorker         |             FilterQueue                  |     SyncAdapter
 * publishHook()     -> enqueueForward() -> dequeueForward()    ->  packJSON() -> syncDB.
 * 
 * Reverse Flow:
 * 
 *  HAWorker         |             FilterQueue                  |     SyncAdapter
 * subscribeHook()   ->   subscribe()                           ->  unpackJSON()
 * subscribeHook()	 <-  dequeueReverse() <- enqueueReverse()   <-  unpackJSON()  <- syncDB
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class LDHAWorker implements IHAWorker, ILinkDiscoveryListener  {
	private static final Logger logger = LoggerFactory.getLogger(LDHAWorker.class);

	protected static IStoreClient<String, String> storeLD;
	public static String controllerID;
	
	List<String> synLDUList = Collections.synchronizedList(new ArrayList<String>());
	private static LDFilterQueue myLDFilterQueue; 
	
	public LDHAWorker(IStoreClient<String, String> storeLD, String controllerID){		
		LDHAWorker.myLDFilterQueue = new LDFilterQueue(storeLD, controllerID);	
	}
	
	public LDFilterQueue getFilterQ(){
		return myLDFilterQueue;
	}
	
	/**
	 * This method is used to assemble the LDupdates into
	 * a JSON string using JSON Jackson API
	 * @return JSON string
	 */
	
	@Override
	public List<String> assembleUpdate() {
		List<String> jsonInString = new LinkedList<String>();
		LDHAUtils parser = new LDHAUtils();
		
		String preprocess = new String (synLDUList.toString());
		/**
		 * Flatten the updates and strip off leading [
		 */
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
		String chunk = new String(preprocess.toString());
		
		if(! preprocess.startsWith("]") ) {
			jsonInString = parser.parseChunk(chunk);
		}

		//logger.debug("[Assemble Update] JSON String: {}", new Object[] {jsonInString});
		return jsonInString;
	}

    /**
     * This method is called in order to start pushing updates 
     * into the syncDB
     * @return boolean value indicating success or failure
     */
	public boolean publishHook() {
		try{
			synchronized (synLDUList){
				//logger.info("[Publish] Printing Updates {}: ",new Object[]{synLDUList});
				List<String> updates = assembleUpdate();
				for(String update : updates){
					myLDFilterQueue.enqueueForward(update);
				}
				synLDUList.clear();
				myLDFilterQueue.dequeueForward();
			}
			return true;
		} catch (Exception e){
			logger.debug("[LDHAWorker] An exception occoured!");
			return false;
		}
	}

	/**
	 * This method is used to subscribe to updates from the syncDB, and 
	 * stay in sync. Can be used to unpack the updates, if needed.
	 * @return boolean value indicating success or failure
	 */

	public List<String> subscribeHook(String controllerID) {
		List<String> updates = new ArrayList<String>();
		try {
			myLDFilterQueue.subscribe(controllerID);
			updates = myLDFilterQueue.dequeueReverse();
			// logger.info("[Subscribe] LDUpdates...");
			return updates;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return updates;
	}

	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
		synchronized(synLDUList){	
			for (LDUpdate update: updateList){	
				synLDUList.add(update.toString());
			}
		}
		
	}

}
