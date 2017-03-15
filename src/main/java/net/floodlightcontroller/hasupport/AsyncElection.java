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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.hasupport.NetworkInterface.ElectionState;
import net.floodlightcontroller.hasupport.NetworkInterface.netState;

/**
 * The Election class
 *
 * This class implements a simple self stabilizing, leader election protocol
 * which is fault tolerant up to N nodes. The concept behind this implementation
 * is described in Scott D. Stoller's 1997 paper 'Leader Election in Distributed
 * Systems with Crash Failures' The ALE1 & ALE2' requirements are being
 * followed.
 *
 * We have an additional constraint that AT LEAST 51% of the nodes must be fully
 * connected before the election happens, this is in order to ensure that there
 * will be at least one group which will produce a majority response to elect
 * one leader. However, the drawback of this system is that 51% of the nodes
 * have to be connected in order for the election to begin. (transition from
 * CONNECT -> ELECT)
 *
 * FD: expireOldConnections() uses PULSE to detect failures.
 *
 * Possible improvements: a. Messages between nodes are being sent sequentially
 * in a for loop, this can be modified to happen in parallel.
 *
 * b. Find out about the Raft leader election algorithm and implement it, and
 * see if it can offer better performance.
 *
 * @author Bhargav Srinivasan, Om Kale
 */

public class AsyncElection implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(AsyncElection.class);
	private static NetworkNode network;

	protected static IHAWorkerService haworker;

	public static NetworkNode getNetwork() {
		return network;
	}

	public static void setNetwork(NetworkNode network) {
		AsyncElection.network = network;
	}

	private final String serverPort;
	private final List<Integer> electionPriorities = new ArrayList<>();
	private final Queue<String> publishQueue = new LinkedBlockingQueue<>();

	private final Queue<String> subscribeQueue = new LinkedBlockingQueue<>();

	private final String controllerID;

	/**
	 * Indicates who the current leader of the entire system is.
	 */
	private String leader = "none";
	private String tempLeader = "none";
	private final String none = "none";
	private final String ack = "ACK";
	private final String publish = "BPUBLISH";
	private final String subscribe = "KSUBSCRIBE";
	private final String pulse = "PULSE";
	private final String you = "YOU?";
	private final String no = "NO";
	private final String leadok = "LEADOK";
	private final String iwon;
	private final String setlead;
	private final String leadermsg;
	private final String heartbeat;

	private ElectionState currentState = ElectionState.CONNECT;

	private Map<String, netState> connectionDict;

	/**
	 * Standardized sleep time for spinning in the rest state.
	 */

	private final Integer chill = new Integer(5);

	private String timestamp = new String();

	public AsyncElection(String sp, String cid) {
		serverPort = sp;
		controllerID = cid;
		setlead = "SETLEAD " + controllerID;
		leadermsg = "LEADER " + controllerID;
		iwon = "IWON " + controllerID;
		heartbeat = "HEARTBEAT " + controllerID;
	}

	public AsyncElection(String serverPort, String controllerID, IHAWorkerService haw) {
		AsyncElection.setNetwork(new NetworkNode(serverPort, controllerID));
		this.serverPort = serverPort;
		this.controllerID = controllerID;
		setlead = "SETLEAD " + this.controllerID;
		leadermsg = "LEADER " + this.controllerID;
		iwon = "IWON " + this.controllerID;
		heartbeat = "HEARTBEAT " + this.controllerID;
		AsyncElection.haworker = haw;
	}

	/**
	 * These are the different possible states the controller can be in during
	 * the election process.
	 */

	private void cases() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				// logger.info("[AsyncElection] Current State:
				// "+currentState.toString());
				switch (currentState) {

				case CONNECT:

					/**
					 * Block until a majority of the servers have connected.
					 */
					currentState = network.blockUntilConnected();

					/**
					 * Majority of the servers have connected, moving on to
					 * elect.
					 */
					break;

				case ELECT:

					/**
					 * Check for new nodes to connect to, and refresh the socket
					 * connections.
					 */
					connectionDict = network.checkForNewConnections();

					// logger.info("[ELECT] =======SIZES+++++ {} {}", new
					// Object[] {network.socketDict.size(), network.majority});

					/**
					 * Ensure that a majority of nodes have connected, otherwise
					 * demote state.
					 */
					if (network.getSocketDict().size() < network.getMajority()) {
						currentState = ElectionState.CONNECT;
						break;
					}

					/**
					 * Start the election if a majority of nodes have connected.
					 */
					this.elect();

					/**
					 * Once the election is done and the leader is confirmed,
					 * proceed to the COORDINATE or FOLLOW state.
					 */
					break;

				case SPIN:

					/**
					 * This is the resting state after the election.
					 */
					connectionDict = network.checkForNewConnections();

					/**
					 * Check For Leader: This function ensures that there is
					 * only one leader set for the entire network. None or
					 * multiple leaders causes it to set the currentState to
					 * ELECT.
					 */
					timestamp = String.valueOf(System.nanoTime());
					this.checkForLeader();

					if (leader.equals(none)) {
						currentState = ElectionState.ELECT;
						break;
					}

					/**
					 * This is the follower state, currently there is a leader
					 * in the network.
					 */
					// logger.info("+++++++++++++++ [FOLLOWER] Leader is set to:
					// "+this.leader.toString());

					if (!publishQueue.isEmpty()) {
						for (String wrkr : AsyncElection.haworker.getWorkerKeys()) {
							AsyncElection.haworker.getService(wrkr).publishHook();
						}
						publishQueue.clear();
					}

					if (!subscribeQueue.isEmpty()) {
						String cid = subscribeQueue.remove();
						for (String wrkr : AsyncElection.haworker.getWorkerKeys()) {
							AsyncElection.haworker.getService(wrkr).subscribeHook(cid);
						}
						// logger.info("[Follower Subscribe] Subscribing to:
						// {}", new Object[]{cid});
						subscribeQueue.clear();
					}

					TimeUnit.SECONDS.sleep(chill.intValue());

					break;

				case COORDINATE:

					/**
					 * This is the resting state of the leader after the
					 * election.
					 */
					connectionDict = network.checkForNewConnections();

					/**
					 * Keep sending a heartbeat message, and receive a majority
					 * of acceptors, otherwise go to the elect state.
					 */
					timestamp = String.valueOf(System.nanoTime());
					this.sendHeartBeat();

					if (leader.equals(none)) {
						currentState = ElectionState.ELECT;
						break;
					}

					/**
					 * This is the follower state, currently I am the leader of
					 * the network.
					 */
					// logger.info("+++++++++++++++ [LEADER] Leader is set to:
					// "+this.leader.toString());

					/**
					 * Keep the leader in coordinate state.
					 */
					timestamp = String.valueOf(System.nanoTime());
					this.sendIWon();
					this.sendLeaderMsg();
					if (leader.equals(network.getControllerID())) {
						this.setAsLeader();
					}

					if (!publishQueue.isEmpty()) {
						AsyncElection.haworker.getService("LDHAWorker").publishHook();
						AsyncElection.haworker.getService("TopoHAWorker").publishHook();
						/**
						 * Network-wide publish
						 */
						publish();
						publishQueue.clear();
					}

					if (!subscribeQueue.isEmpty()) {
						String cid = subscribeQueue.remove();
						AsyncElection.haworker.getService("LDHAWorker").subscribeHook(cid);
						AsyncElection.haworker.getService("TopoHAWorker").subscribeHook(cid);
						/**
						 * Network-wide Subscribe
						 */
						subscribe(cid);
						subscribeQueue.clear();
					}

					TimeUnit.SECONDS.sleep(chill.intValue());

					break;

				}

			}

		} catch (InterruptedException ie) {
			logger.debug("[Election] Exception in cases!");
			ie.printStackTrace();
		} catch (Exception e) {
			logger.debug("[Election] Error in cases!");
			e.printStackTrace();
		}

	}

	/**
	 * Ask each node if they are the leader, you should get an ACK from only one
	 * of them, if not, then reset the leader.
	 */

	private void checkForLeader() {

		HashSet<String> leaderSet = new HashSet<>();
		String reply = new String();
		String r1 = new String();
		String r2 = new String();
		StringTokenizer st = new StringTokenizer(reply);

		try {

			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					network.send(entry.getKey(), you + " " + timestamp);
					reply = network.recv(entry.getKey());
					st = new StringTokenizer(reply);
					r1 = st.nextToken();
					if (st.hasMoreTokens()) {
						r2 = st.nextToken();
					}

					if ((!r1.equals(no)) && (r2.equals(timestamp))) {
						leaderSet.add(r1);
					} else {
						// logger.info("[AsyncElection] Check Leader: " + reply
						// +" from "+entry.getKey().toString());
						continue;
					}

				}
			}

			// logger.info("[AsyncElection checkForLeader] Leader Set:
			// "+leaderSet.toString());

			/**
			 * Remove blank objects from set, if any.
			 */
			if (leaderSet.contains(new String(""))) {
				leaderSet.remove(new String(""));
			}

			/**
			 * Remove none from set, if any.
			 */
			if (leaderSet.contains(none)) {
				leaderSet.remove(none);
			}

			/**
			 * Remove null objects from set, if any.
			 */
			if (leaderSet.contains(null)) {
				// logger.info("[Election] Leader Set contains null");
				leaderSet.remove(null);
			}

			if (leaderSet.size() == 1) {
				setLeader(leaderSet.stream().findFirst().get());
			} else if (leaderSet.size() > 1) {
				setLeader(none);
				// logger.info("[Election checkForLeader] SPLIT BRAIN!!");
				// logger.info("[Election checkForLeader] Current Leader is
				// none");
			} else if (leaderSet.size() < 1) {
				setLeader(none);
				// logger.info("[Election checkForLeader] Current Leader is none
				// "+ this.leader.toString() );
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in CheckForLeader");
			e.printStackTrace();
		}

	}

	/**
	 * Election: All nodes will pick the max CID which they see in the network,
	 * any scenario wherein two different leaders might be picked gets resolved
	 * using the checkForLeader function.
	 */

	private void elect() {

		/**
		 * Ensure that majority are still connected.
		 */
		if (network.getSocketDict().size() < network.getMajority()) {
			return;
		}

		/**
		 * Clear leader variables.
		 */
		setTempLeader(none);
		setLeader(none);

		/**
		 * Check if actually in elect state
		 */
		if (!(currentState == ElectionState.ELECT)) {
			return;
		}

		/**
		 * Node joins AFTER election: To check if a node joined after election,
		 * i.e. a leader is already present. Run the checkForLeader function and
		 * if it returns a leader then accept the existing leader and go to the
		 * SPIN state.
		 */

		timestamp = String.valueOf(System.nanoTime());
		this.checkForLeader();

		/**
		 * If a leader has already been set, exit election state and SPIN.
		 */
		if (!leader.equals(none)) {
			currentState = ElectionState.SPIN;
			return;
		}

		/**
		 * End of Node joins AFTER election.
		 */

		/**
		 * Actual election logic.
		 */
		this.electionLogic();

		if (leader.equals(network.getControllerID())) {
			// logger.info("[Election] I WON THE ELECTION!");
			timestamp = String.valueOf(System.nanoTime());
			this.sendIWon();
			this.sendLeaderMsg();
			if (leader.equals(network.getControllerID())) {
				this.setAsLeader();
			}
		} else if (leader.equals(none)) {
			currentState = ElectionState.ELECT;
		} else {
			currentState = ElectionState.SPIN;
		}

		/**
		 * End of Actual Election logic.
		 */
		return;
	}

	/**
	 * This is the logic that will be performed by the controller in order to
	 * pick a leader. It is an operation which is performed on the priorities
	 * array provided it supplied earlier, otherwise it constructs an array
	 * which contains all configured nodes in the descending order of their
	 * controller IDs.
	 */

	private void electionLogic() {
		/**
		 * List of controllerIDs of all nodes.
		 */
		ArrayList<Integer> nodes = new ArrayList<>();
		Integer maxNode = new Integer(0);

		if ((electionPriorities.size() > 0) && (electionPriorities.size() == network.getTotalRounds() + 1)) {
			nodes.addAll(electionPriorities);
		} else {
			/**
			 * Generate list of total possible CIDs.
			 */
			for (Integer i = (network.getTotalRounds() + 1); i > 0; i--) {
				nodes.add(i);
			}
		}

		// logger.info("[AsyncElection ElectionLogic] Nodes participating:
		// "+nodes.toString());

		/**
		 * Get the node whose CID is numerically greater.
		 */
		Set<String> connectDictKeys = connectionDict.keySet();
		HashSet<Integer> activeCIDs = new HashSet<>();

		/**
		 * Convert active controller ports into a Set of their IDs.
		 */
		for (String port : connectDictKeys) {
			if (connectionDict.get(port) != null && connectionDict.get(port).equals(netState.ON)) {
				activeCIDs.add(network.getNetcontrollerIDStatic().get(port));
			}
		}

		activeCIDs.add(new Integer(controllerID));

		// logger.info("[AsyncElection ElectionLogic] Active controllers:
		// "+activeCIDs.toString()+" ConnectDict Keys:
		// "+connectDictKeys.toString());

		/**
		 * Find the current active maxNode.
		 */

		for (Integer i = 0; i < nodes.size(); i++) {
			if (activeCIDs.contains(nodes.get(i))) {
				maxNode = nodes.get(i);
				break;
			}
		}

		/**
		 * Edge case where you are the max node && you are ON.
		 */
		if (controllerID.equals(maxNode.toString())) {
			setLeader(maxNode.toString());
			return;
		}

		String maxNodePort = network.getControllerIDNetStatic().get(maxNode.toString()).toString();

		/**
		 * Check if Max Node is alive, and set it as leader if it is.
		 */
		try {

			for (int i = 0; i < network.getNumberOfPulses(); i++) {

				network.send(maxNodePort, pulse);
				String reply = network.recv(maxNodePort);

				if (reply.equals(ack)) {
					setLeader(maxNode.toString());
				}
			}

		} catch (Exception e) {
			// logger.debug("[AsyncElection] Error in electionLogic!");
			e.printStackTrace();
		}

		return;

	}

	/**
	 * Gets the current network-wide leader.
	 * 
	 * @return Current network-wide leader.
	 */

	public String getLeader() {
		final String lead;
		synchronized (leader) {
			lead = leader;
		}
		return lead;
	}


	/**
	 * Used by the leader election protocol, internal function
	 * 
	 * @return Temp Leader
	 */

	public String gettempLeader() {
		final String tempLead;
		synchronized (tempLeader) {
			tempLead = tempLeader;
		}
		return tempLead;
	}

	/**
	 * Gets the timestamp variable before sending/receiving messages
	 * 
	 * @return String timestamp
	 */

	public String getTimeStamp() {
		final String ts;
		synchronized (timestamp) {
			ts = timestamp;
		}
		return ts;
	}

	/**
	 * Used by the leader to initiate a network-wide publish.
	 */
	public void publish() {
		try {
			/**
			 * Check for new nodes to connect to, and refresh the socket
			 * connections. this.connectionDict =
			 * network.checkForNewConnections();
			 */

			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					network.send(entry.getKey(), publish);
					network.recv(entry.getKey());

					/**
					 * If we get an ACK, that's good. logger.debug("[Publish]
					 * Received ACK from "+entry.getKey().toString());
					 */
				}
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in PUBLISH!");
			e.printStackTrace();
		}

	}

	/**
	 * Adds a control message instructing the controller to call publsihHook
	 */
	public void publishQueue() {
		synchronized (publishQueue) {
			publishQueue.offer("PUBLISH");
		}
		return;
	}

	@Override
	public void run() {

		ScheduledExecutorService sesElection = Executors.newScheduledThreadPool(5);

		try {

			Integer noServers = new Integer(0);
			HAServer serverTh = new HAServer(serverPort, this, controllerID);

			if (network.getTotalRounds() <= 1) {
				noServers = 1;
			} else {
				noServers = (int) Math.ceil(Math.log10(network.getTotalRounds()));
			}

			if (noServers <= 1) {
				noServers = 1;
			}

			sesElection.scheduleAtFixedRate(network, 0, 30000, TimeUnit.MICROSECONDS);
			for (Integer i = 0; i < noServers; i++) {
				sesElection.scheduleAtFixedRate(serverTh, 0, 30000, TimeUnit.MICROSECONDS);
			}

			// logger.info("[Election] Network majority:
			// "+network.majority.toString());
			// logger.info("[Election] Get netControllerIDStatic:
			// "+network.getnetControllerIDStatic().toString());
			this.cases();

		} catch (Exception e) {
			// logger.debug("[AsyncElection] Was interrrupted! "+e.toString());
			e.printStackTrace();
			sesElection.shutdownNow();
		}
	}

	/**
	 * The Leader will send a HEARTBEAT message in the COORDINATE state after
	 * the election and will expect a reply from a majority of acceptors.
	 */

	private void sendHeartBeat() {

		HashSet<String> noSet = new HashSet<>();
		try {

			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					/**
					 * If the HeartBeat is rejected, populate the noSet.
					 */
					network.send(entry.getKey(), heartbeat + " " + timestamp);
					String reply = network.recv(entry.getKey());

					if (reply.equals(no) || (!reply.equals(ack + timestamp))) {
						noSet.add(entry.getKey());
					}
					// If we get an ACK, that's good.
					// logger.info("[Election] Received HEARTBEAT reply from
					// "+entry.getKey().toString()+" saying: "+reply);
				}
			}

			if (noSet.size() >= network.getMajority()) {
				setLeader(none);
				currentState = ElectionState.ELECT;
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in sendHeartBeat!");
			e.printStackTrace();
		}

	}

	/**
	 * The winner of the election, or the largest node that is currently active
	 * in the network sends an "IWON" message in order to initiate the three
	 * phase commit to set itself as the leader of the network. Phase 1 of the
	 * three phase commit.
	 */

	private void sendIWon() {

		try {
			Set<String> reply = new HashSet<>();
			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					network.send(entry.getKey(), iwon + " " + timestamp);
					reply.add(network.recv(entry.getKey()));
					// logger.info("Received reply for IWON from:
					// "+entry.getKey().toString() + reply.toString());

				}
			}

			if (reply.contains(ack)) {
				setTempLeader(controllerID);
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in sendIWon!");
			e.printStackTrace();
		}

	}

	/**
	 * Send a "LEADER" message to all nodes and try to receive "LEADOK" messages
	 * from them. If count("LEADOK") > majority, then you have won the election
	 * and hence become the leader. Phase 2 of the three phase commit.
	 */

	private void sendLeaderMsg() {

		HashSet<String> acceptors = new HashSet<>();
		try {

			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					network.send(entry.getKey(), leadermsg + " " + timestamp);
					String reply = network.recv(entry.getKey());
					if (reply.equals(leadok)) {
						acceptors.add(entry.getKey());
					}
				}

			}

			if (acceptors.size() >= network.getMajority()) {
				// logger.info("[Election sendLeaderMsg] Accepted leader:
				// "+this.controllerID+" Majority:
				// "+network.majority+"Acceptors: "+acceptors.toString());
				setLeader(network.getControllerID());
				currentState = ElectionState.COORDINATE;
			} else {
				// logger.info("[Election sendLeaderMsg] Did not accept leader:
				// "+this.controllerID+" Majority:
				// "+network.majority+"Acceptors: "+acceptors.toString());
				setLeader(none);
				currentState = ElectionState.ELECT;
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in sendLeaderMsg!");
			e.printStackTrace();
		}

	}

	/**
	 * The leader will set itself as leader during each COORDINATE state loop,
	 * to ensure that all nodes see it as the leader. Phase 3 of the three phase
	 * commit.
	 */

	private void setAsLeader() {

		HashSet<String> noSet = new HashSet<>();
		try {

			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					/**
					 * If the leader is rejected, populate the noSet.
					 */
					network.send(entry.getKey(), setlead + " " + timestamp);
					String reply = network.recv(entry.getKey());

					if (reply.equals(no)) {
						noSet.add(entry.getKey());
					}

					// If we get an ACK, that's good.
					// logger.info("[Election] Received SETLEAD ACK from
					// "+entry.getKey().toString());
				}
			}

			if (noSet.size() >= network.getMajority()) {
				setLeader(none);
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in setAsLeader!");
			e.printStackTrace();
		}

	}

	/**
	 * Set the order in which nodes are supposed to get elected.
	 * 
	 * @param priorities
	 *            is an ordered arraylist of integers which contain the order in
	 *            which the controllers should be picked as leader. (optional)
	 */

	public void setElectionPriorities(ArrayList<Integer> priorities) {
		synchronized (electionPriorities) {
			if ((priorities.size()) > 0 && (priorities.size() == network.getTotalRounds() + 1)) {
				electionPriorities.addAll(priorities);
			} else {
				logger.info("[AsyncElection] Priorities are not set.");
			}
		}
		return;
	}

	/**
	 * Set the leader variable for this node.
	 * 
	 * @param String
	 *            leader
	 */

	public void setLeader(String leader) {
		synchronized (this.leader) {
			this.leader = leader;
		}
		return;
	}

	/**
	 * Set the temp leader. Internal function.
	 * 
	 * @param String
	 *            tempLeader
	 */

	public void setTempLeader(String tempLeader) {
		synchronized (this.tempLeader) {
			this.tempLeader = tempLeader;
		}
		return;
	}

	/**
	 * Sets the timestamp variable before sending/receiving messages.
	 * 
	 * @param String
	 *            timestamp
	 */

	public void setTimeStamp(String ts) {
		synchronized (timestamp) {
			timestamp = ts;
		}
		return;
	}

	/**
	 * Used by the leader to initiate a network-wide subscribe to the specified
	 * controller ID
	 * 
	 * @param Controller
	 *            ID you wish to subscribe to.
	 */
	public void subscribe(String cid) {
		try {
			/**
			 * Check for new nodes to connect to, and refresh the socket
			 * connections. this.connectionDict =
			 * network.checkForNewConnections();
			 */

			for (HashMap.Entry<String, netState> entry : connectionDict.entrySet()) {
				if (connectionDict.get(entry.getKey()).equals(netState.ON)) {

					String submsg = new String(subscribe + " " + cid);
					// logger.info("[Leader Subscribe] Subscribing to: {}", new
					// Object[]{cid});

					network.send(entry.getKey(), submsg);
					network.recv(entry.getKey());

					// If we get an ACK, that's good.
					// logger.info("[Subscribe] Received ACK from
					// "+entry.getKey().toString());
				}
			}

			return;

		} catch (Exception e) {
			logger.debug("[Election] Error in SUBSCRIBE!");
			e.printStackTrace();
		}

	}

	/**
	 * Adds a control message instructing the controller to call subscribeHook
	 */
	public void subscribeQueue(String sub) {
		synchronized (subscribeQueue) {
			subscribeQueue.offer(sub);
		}
		return;
	}

}
