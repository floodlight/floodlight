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

package net.floodlightcontroller.hasupport;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Controller's Logic
 *
 * This thread ensures that the election class is polled every "pollTime"
 * seconds such that it checks if a new leader is present in the network. Once
 * you get the leader, you can do role based programming in this thread; meaning
 * you can specify functions that the leader of the network should do, and
 * separate functions that the followers do. Currently, the leader is used to
 * manage network-wide publishing and subscribing of updates across all nodes.
 *
 * Possible extensions: a. Offer "Leader Role" and "Follower Role" as a service
 * which other modules can access in order to be able to do role based
 * programming.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class ControllerLogic implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ControllerLogic.class);

	private final AsyncElection ael;
	private final String none = "none";
	private final String cid;
	private final String controllerID;

	private final Integer timeout = new Integer(60000);
	private final Integer pollTime = new Integer(5);
	private Integer ticks = new Integer(0);
	private boolean timeoutFlag;
	private Map<String,String> leaderChange = new HashMap<>();

	public ControllerLogic(AsyncElection ae, String cID) {
		ael = ae;
		controllerID = cID;
		cid = new String("C" + cID);
	}

	@Override
	public void run() {
		logger.info("[ControllerLogic] Running...");
		try {
			String leader;
			leaderChange.put("leader",none);
			while (!Thread.currentThread().isInterrupted()) {

				leader = ael.getLeader();

				if (!leader.equals(none)) {
					if(!leaderChange.get("leader").equals(leader)){
						logger.info("[ControllerLogic] Getting Leader: " + ael.getLeader().toString());
						leaderChange.put("leader",leader);
					}
				}

				/**
				 * First try to get the leader:
				 */
				if (leader.equals(none)) {
					/**
					 * Functions if you are neither a leader nor a follower and
					 * you are active.
					 */

					timeoutFlag = true;
					Long start = System.nanoTime();
					Long duration = new Long(0);

					while (duration <= timeout) {
						duration = (long) ((System.nanoTime() - start) / 1000000.000);
						if (!ael.getLeader().toString().equals(none)) {
							timeoutFlag = false;
							logger.info("[ControllerLogic] Got Leader: " + ael.getLeader().toString() + " Elapsed :"
									+ duration.toString());
							break;
						}
						TimeUnit.MILLISECONDS.sleep(25);
					}

					/**
					 * If you can't get the leader within the specified timeout,
					 * then default to controller 1 as the leader.
					 */

					if (timeoutFlag) {
						logger.info("[ControllerLogic] Election timed out, setting Controller 1 as LEADER!");
						ael.setTempLeader(new String("1"));
						ael.setLeader(new String("1"));
					}

				} else {
					/**
					 * Role based functions:
					 */

					if (leader.equals(controllerID)) {
						/**
						 * Role based functions: Leader functions
						 */

						/**
						 * LEADER initiates publish and subscribe
						 */
						// logger.info("[ControllerLogic] Calling Hooks...");

						/**
						 * Publish, meaning ask all nodes to call publish hook
						 */
						ael.publishQueue();
						/**
						 * Subscribe, ask all nodes to subscribe to the leader
						 * can be modified to subscribe to updates from all
						 * other nodes as well by calling this in a loop.
						 */
						ael.subscribeQueue(cid);

					} else {
						/**
						 * Role based function: Follower functions
						 */

					}

					/**
					 * If the election times out, then call your own publish and
					 * subscribe hooks
					 */
					if (timeoutFlag) {
						for (String wrkr : AsyncElection.haworker.getWorkerKeys()) {
							AsyncElection.haworker.getService(wrkr).publishHook();
							AsyncElection.haworker.getService(wrkr).subscribeHook(cid);
						}

					}

					TimeUnit.SECONDS.sleep(pollTime);

					/**
					 * Uncomment this: memory usage/too many files
					 */
					if (ticks > 5) {
						System.gc();
						ticks = 0;
					}

					ticks += 1;
				}

			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
