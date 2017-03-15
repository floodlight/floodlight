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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.sdnplatform.sync.IStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.hasupport.IHAWorker;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * TopoHAWorker
 *
 * This is the HAWorker class that is used to push updates into the queue using
 * the publishHook method. Also, subscribe to updates from the syncDB, and
 * display/process them if needed using subscribeHook.
 *
 * The assemble update function is used to convert the updates into a JSON for
 * easier storage (i.e. as a string which can then be processed using Jackson)
 * and storing them into the syncDB. JSON relational mapping is performed in the
 * SyncAdapter class, because the data in the updates are relational in nature
 * and this improves write efficiency.
 *
 * Forward flow:
 *
 * HAWorker | FilterQueue | SyncAdapter publishHook() -> enqueueForward() ->
 * dequeueForward() -> packJSON() -> syncDB.
 *
 * Reverse Flow:
 *
 * HAWorker | FilterQueue | SyncAdapter subscribeHook() -> subscribe() ->
 * unpackJSON() subscribeHook() <- dequeueReverse() <- enqueueReverse() <-
 * unpackJSON() <- syncDB
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class TopoHAWorker implements IHAWorker, ITopologyListener {
	private static final Logger logger = LoggerFactory.getLogger(TopoHAWorker.class);
	protected static ITopologyService toposerv;

	private static TopoFilterQueue myTopoFilterQueue;
	List<String> synTopoUList = Collections.synchronizedList(new ArrayList<String>());

	public TopoHAWorker(IStoreClient<String, String> storeTopo, String controllerID) {
		TopoHAWorker.myTopoFilterQueue = new TopoFilterQueue(storeTopo, controllerID);
	}

	/**
	 * This method is used to assemble the Topo Updates into a JSON string using
	 * JSON Jackson API
	 * 
	 * @return JSON string
	 */

	@Override
	public List<String> assembleUpdate() {
		List<String> jsonInString = new LinkedList<>();
		TopoUtils parser = new TopoUtils();

		String preprocess = new String(synTopoUList.toString());
		/**
		 * Flatten the updates and strip off leading [
		 */

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		String chunk = new String(preprocess.toString());

		if (!preprocess.startsWith("]")) {
			jsonInString = parser.parseChunk(chunk);
		}

		// logger.debug("[Assemble Update] JSON String: {}", new Object[]
		// {jsonInString});
		return jsonInString;
	}

	public TopoFilterQueue getFilterQ() {
		return myTopoFilterQueue;
	}

	/**
	 * This method is called in order to start pushing updates into the syncDB
	 * 
	 * @return boolean value indicating success or failure
	 */

	@Override
	public boolean publishHook() {
		try {
			synchronized (synTopoUList) {
				// logger.info("[Publish] Printing Updates {}: ",new
				// Object[]{synTopoUList});
				List<String> updates = assembleUpdate();
				for (String update : updates) {
					myTopoFilterQueue.enqueueForward(update);
				}
				synTopoUList.clear();
				myTopoFilterQueue.dequeueForward();
			}
			return true;
		} catch (Exception e) {
			logger.debug("[TopoHAWorker] An exception occoured!");
			return false;
		}
	}

	/**
	 * This method is used to subscribe to updates from the syncDB, and stay in
	 * sync. Can be used to unpack the updates, if needed.
	 * 
	 * @return boolean value indicating success or failure
	 */

	@Override
	public List<String> subscribeHook(String controllerID) {
		List<String> updates = new ArrayList<>();
		try {
			myTopoFilterQueue.subscribe(controllerID);
			updates = myTopoFilterQueue.dequeueReverse();
			// logger.info("[Subscribe] TopoUpdates...");
			return updates;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return updates;
	}

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		synchronized (synTopoUList) {
			for (LDUpdate update : linkUpdates) {
				synTopoUList.add(update.toString());
			}
		}

	}

}
