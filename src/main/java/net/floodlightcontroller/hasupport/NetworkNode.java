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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.python.modules.math;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Network Node (Connection Manager) 
 * Implements the NetworkInterface, which dictates 
 * the topology of how the controllers are communicating with each
 * other in order to perform role based functions, after electing a leader.
 * Currently, the topology is set to a mesh topology, however this can be
 * changed completely if needed, as long as the functions from NetworkInterface
 * are implemented. One has to ensure that the socketDict and the connectDict
 * HashMaps are populated and updated similar to the way the updateConnectDict()
 * maintains these objects. This method is called as soon as a state change to
 * even one of the sockets is detected.
 *
 * Possible improvements: a. Implement a better topology, i.e. the topology is
 * now a complete mesh, this class can be completely re-implemented, if you
 * adhere to NetworkInterface, and expose socketDict and connectionDict to
 * AsyncElection in a similar manner.
 *
 * b. Improve the existing connection manager (NetworkNode). Currently, we are
 * using the extended request-reply pattern, mentioned in the ZGuide. We could
 * identify a good alternative and implement it.
 *
 * @author Bhargav Srinivasan, Om Kale
 */

public class NetworkNode implements NetworkInterface, Runnable {

	private static final Logger logger = LoggerFactory.getLogger(NetworkNode.class);

	private NioClient clientSock;
	private final String controllerID;
	private final String serverPort;

	/**
	 * The server list holds the server port IDs of all present connections. The
	 * connectSet is a set of nodes defined in the server list which are to be
	 * connected
	 */

	private List<String> serverList = new LinkedList<>();
	private List<String> allServerList = new LinkedList<>();
	private Set<String> connectSet = new HashSet<>();

	private final String pulse = "PULSE";
	private final String ack = "ACK";

	/**
	 * Holds the connection state/ socket object for each of the client
	 * connections.
	 */

	private Map<String, NioClient> socketDict = new HashMap<>();
	private Map<String, netState> connectDict = new HashMap<>();
	private Map<String, String> controllerIDNetStatic = new HashMap<>();
	private Map<String, Integer> netcontrollerIDStatic = new HashMap<>();
	private Map<String, NioClient> allsocketDict = new HashMap<>();
	private Map<String, NioClient> delmark = new HashMap<>();

	/**
	 * Standardized sleep times for socket timeouts, number of pulses to send
	 * before expiring.
	 */

	/**
	 * Decide the socket timeout value based on how fast you think the leader
	 * should respond and also how far apart the actual nodes are placed. If you
	 * are trying to communicate with servers far away, then anything up to 10s
	 * would be a good value.
	 */

	private final Integer socketTimeout = new Integer(500);
	private final Integer linger = new Integer(0);
	private final Integer numberOfPulses = new Integer(1);
	private final Integer pollTime = new Integer(1);
	private Integer ticks = new Integer(0);

	/**
	 * Majority is a variable that holds what % of servers need to be active in
	 * order for the election to start happening. Total rounds is the number of
	 * expected failures which is set to len(serverList) beacuse we assume that
	 * any/every node can fail.
	 */

	private final Integer majority;
	private final Integer totalRounds;
	private String response = new String();

	/**
	 * Constructor needs both the backend and frontend ports and the serverList
	 * file which specifies a port number for each connected client.
	 *
	 * @param serverPort
	 * @param clientPort
	 * @param controllerID
	 */

	public NetworkNode(String serverPort, String controllerID) {
		/**
		 * The port variables needed in order to start the back-end and
		 * front-end of the queue device.
		 */
		this.serverPort = serverPort.toString();
		this.controllerID = controllerID;
		preStart();
		totalRounds = new Integer(connectSet.size());
		logger.debug("Total Rounds: " + totalRounds.toString());

		if (totalRounds >= 2) {
			majority = new Integer((int) math.ceil(new Double(0.51 * connectSet.size())));
		} else {
			majority = new Integer(1);
		}
		logger.debug(
				"Other Servers: " + connectSet.toString() + "Majority: " + majority + "ServerPort: " + this.serverPort);

	}

	/**
	 * Will first expire all connections in the socketDict and keep spinning
	 * until, > majority % nodes from the connectSet get connected.
	 */

	@Override
	public ElectionState blockUntilConnected() {

		cleanState();

		while (socketDict.size() < majority) {
			try {
				// logger.info("[Node] BlockUntil: Trying to connect...");
				this.connectClients();

				/**
				 * Flush the context to avoid too many open files 250 ticks = 4
				 * min 55 seconds
				 */
				if (ticks > 250) {
					logger.debug("[NetworkNode] Refreshing state....");
					cleanState();
					logger.debug("[NetworkNode] Refreshed state....");
					ticks = 0;
				}

				ticks += 1;
				TimeUnit.MILLISECONDS.sleep(pollTime);
			} catch (Exception e) {
				logger.debug("[NetworkNode] BlockUntil errored out: " + e.toString());
				e.printStackTrace();
			}
		}

		updateConnectDict();
		return ElectionState.ELECT;
	}

	/**
	 * This method is periodically called by the election class so that we can
	 * identify if any more of the configured nodes have become active, and if
	 * so establish connections to them and store the corresponding socket
	 * objects.
	 *
	 * @return Unmodifiable hashmap of connectDict <IP:port, ON/OFF>
	 */

	@Override
	public Map<String, netState> checkForNewConnections() {

		expireOldConnections();

		this.setConnectSet(new HashSet<>(serverList));

		doConnect();

		updateConnectDict();
		return Collections.unmodifiableMap(connectDict);
	}

	/**
	 * This method is used to completely refresh the state of the connection
	 * manager, closing all sockets, both active and inactive, and spawning new
	 * socket objects for all configured nodes. This function is called by the
	 * blockUntilConnected() method in order to refresh state every five minutes
	 * in order to avoid an excess of open files / sockets.
	 *
	 */

	public void cleanState() {

		this.setConnectSet(new HashSet<>(serverList));
		delmark = new HashMap<>();

		for (String client : connectSet) {
			allsocketDict.get(client).deleteConnection();
			allsocketDict.put(client, new NioClient(socketTimeout, linger));
		}

		for (Map.Entry<String, NioClient> entry : socketDict.entrySet()) {
			try {
				// logger.info("[Node] Closing connection:
				// "+entry.getKey().toString());
				entry.getValue().deleteConnection();
				delmark.put(entry.getKey(), entry.getValue());

			} catch (NullPointerException ne) {
				// logger.info("[Node] BlockUntil: Reply had a null
				// value"+entry.getKey().toString());
				delmark.put(entry.getKey(), entry.getValue());
				// ne.printStackTrace();
			} catch (Exception e) {
				// logger.info("[Node] Error closing connection:
				// "+entry.getKey().toString());
				delmark.put(entry.getKey(), entry.getValue());
				e.printStackTrace();
			}
		}

		for (Map.Entry<String, NioClient> entry : delmark.entrySet()) {
			socketDict.remove(entry.getKey());
		}

		this.setSocketDict(new HashMap<String, NioClient>());

		return;

	}

	/**
	 * Called by the blockUntilConnected() method in order to perform an initial
	 * connection to all the nodes and store all their socket objects in the
	 * socketDict and connectDict which are then passed to the election class.
	 *
	 * @return Unmodifiable hashmap of connectDict <IP:port, ON/OFF>
	 */

	@Override
	public Map<String, netState> connectClients() {
		// logger.info("[Node] To Connect: "+this.connectSet.toString());
		// logger.info("[Node] Connected:
		// "+this.socketDict.keySet().toString());

		doConnect();

		/**
		 * Delete the already connected connections from the ToConnect Set.
		 */
		for (Map.Entry<String, NioClient> entry : socketDict.entrySet()) {
			if (connectSet.contains(entry.getKey())) {
				connectSet.remove(entry.getKey());
				// logger.info("Discarding already connected client:
				// "+entry.getKey().toString());
			}
		}
		updateConnectDict();
		return Collections.unmodifiableMap(connectDict);
	}

	/**
	 * This method maintains the hashmap socketDict, which holds the socket
	 * objects for the current active connections. It tries to connect the nodes
	 * which have been configured but not connected yet, and adds them to the
	 * socketDict if the connection is successful. This method has been
	 * optimized to instantiate as few socket objects as possible without loss
	 * of functionality.
	 *
	 */

	public void doConnect() {

		Set<String> diffSet = new HashSet<>();
		Set<String> connectedNodes = new HashSet<>();

		for (Map.Entry<String, NioClient> entry : socketDict.entrySet()) {
			connectedNodes.add(entry.getKey());
		}

		diffSet.addAll(connectSet);
		diffSet.removeAll(connectedNodes);

		// logger.info("[Node] New connections to look for (ConnectSet -
		// Connected): "+diffSet.toString());

		/**
		 * Try connecting to all nodes that are in the diffSet and store the
		 * successful ones in the socketDict.
		 */
		String reply;
		for (String client : diffSet) {
			reply = "";
			clientSock = allsocketDict.get(client);
			try {
				// logger.info("[Node] Trying to connect to Client:
				// "+client.toString()+"Client Sock: "+clientSock.toString());
				clientSock.connectClient(client);
				clientSock.send(pulse);
				reply = clientSock.recv();

				if (reply.equals(ack)) {
					// logger.info("[Node] Client: "+client.toString()+"Client
					// Sock: "+clientSock.toString());
					if (!socketDict.containsKey(client)) {
						socketDict.put(client, clientSock);
					} else {
						// logger.info("[Node] This socket already exists,
						// refreshing: "+client.toString());
						clientSock.deleteConnection();
						socketDict.remove(client);
						socketDict.put(client, new NioClient(socketTimeout, linger));
					}
				} else {
					// logger.info("[Node] Received bad reply:
					// "+client.toString()+" "+reply);
					clientSock.deleteConnection();
					// logger.info("[Node] Closed Socket"+client.toString());
				}

			} catch (NullPointerException ne) {
				logger.debug("[NetworkNode] ConnectClients: Reply had a null value from: " + client.toString());
				// ne.printStackTrace();
			} catch (Exception e) {
				if (clientSock != null) {
					clientSock.deleteConnection();
					allsocketDict.put(client, new NioClient(socketTimeout, linger));
				}
				logger.debug("[NetworkNode] ConnectClients errored out: " + client.toString());
				// e.printStackTrace();
			}

		}

		return;

	}

	/**
	 * This method is used by the election class as a failure detector, meaning
	 * it can detect if the connected nodes are still responding, and are
	 * active. If not, it closes the corresponding socket and removes it from
	 * the socketDict & connectDict, in order to inform the election class that
	 * the following nodes are no longer active.
	 *
	 * @return Unmodifiable hashmap of connectDict <IP:port, ON/OFF>
	 */

	@Override
	public Map<String, netState> expireOldConnections() {
		// logger.info("Expiring old connections...");
		delmark = new HashMap<>();
		String reply;
		for (Map.Entry<String, NioClient> entry : socketDict.entrySet()) {
			clientSock = entry.getValue();
			reply = "";
			try {
				for (int i = 0; i < numberOfPulses; i++) {
					clientSock.send(pulse);
					reply = clientSock.recv();
				}

				if (!reply.equals(ack)) {
					// logger.info("[Node] Closing stale connection:
					// "+entry.getKey().toString());
					entry.getValue().deleteConnection();
					delmark.put(entry.getKey(), entry.getValue());
				}

			} catch (NullPointerException ne) {
				logger.debug("[NetworkNode] Expire: Reply had a null value: " + entry.getKey().toString());
				delmark.put(entry.getKey(), entry.getValue());
				// ne.printStackTrace();
			} catch (Exception e) {
				logger.debug("[NetworkNode] Expire: Exception! : " + entry.getKey().toString());
				delmark.put(entry.getKey(), entry.getValue());
				// e.printStackTrace();
			}
		}

		/**
		 * Pop out all the expired connections from socketDict.
		 */
		try {
			for (Map.Entry<String, NioClient> entry : delmark.entrySet()) {
				socketDict.remove(entry.getKey());
				if (entry.getValue() != null) {
					entry.getValue().deleteConnection();
				}
			}
		} catch (Exception e) {
			logger.debug("[NetworkNode] Error in expireOldConnections, while deleting socket");
			e.printStackTrace();
		}

		// logger.info("Expired old connections.");

		updateConnectDict();
		return Collections.unmodifiableMap(connectDict);
	}

	public List<String> getAllServerList() {
		return allServerList;
	}

	@Override
	public Map<String, netState> getConnectDict() {
		return Collections.unmodifiableMap(connectDict);
	}

	public Set<String> getConnectSet() {
		return connectSet;
	}

	public String getControllerID() {
		return controllerID;
	}

	public Map<String, String> getControllerIDNetStatic() {
		return controllerIDNetStatic;
	}

	public Integer getMajority() {
		return majority;
	}

	public Map<String, Integer> getnetControllerIDStatic() {
		return Collections.unmodifiableMap(this.getNetcontrollerIDStatic());
	}

	public Map<String, Integer> getNetcontrollerIDStatic() {
		return netcontrollerIDStatic;
	}

	public Integer getNumberOfPulses() {
		return numberOfPulses;
	}

	public List<String> getServerList() {
		return serverList;
	}

	public Map<String, NioClient> getSocketDict() {
		return socketDict;
	}

	public Integer getTotalRounds() {
		return totalRounds;
	}

	/**
	 * Parses server.config located in the resources folder in order to obtain
	 * the IP:ports of all the nodes that are configured to be a part of this
	 * network.
	 *
	 */

	public void preStart() {
		String filename = "src/main/resources/server.config";

		try {
			FileReader configFile = new FileReader(filename);
			String line = null;
			BufferedReader br = new BufferedReader(configFile);

			Integer cidIter = new Integer(1);
			while ((line = br.readLine()) != null) {
				serverList.add(new String(line.trim()));
				allServerList.add(new String(line.trim()));
				netcontrollerIDStatic.put(new String(line.trim()), cidIter);
				controllerIDNetStatic.put(cidIter.toString(), new String(line.trim()));
				cidIter += 1;
			}

			serverList.remove(serverPort);
			this.setConnectSet(new HashSet<>(serverList));

			for (String client : connectSet) {
				allsocketDict.put(client, new NioClient(socketTimeout, linger));
			}

			br.close();
			configFile.close();

		} catch (FileNotFoundException e) {
			logger.debug(
					"[NetworkNode] This file was not found! Please place the server config file in the right location.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Receives a message from the specified IP:port, if possible.
	 *
	 * @return String containing the received message.
	 */

	@Override
	public String recv(String receivingPort) {
		clientSock = socketDict.get(receivingPort);
		try {
			response = clientSock.recv();
			response.trim();
			// logger.info("[NetworkNode] Recv on port:
			// "+receivingPort.toString()+response);
			return response;
		} catch (Exception e) {
			if (clientSock.getSocketChannel() != null) {
				clientSock.deleteConnection();
			}
			logger.debug("[NetworkNode] Recv Failed on port: " + receivingPort.toString());
			return "";
		}

	}

	@Override
	public void run() {
		try {
			// logger.info("Server List: "+this.serverList.toString());
		} catch (Exception e) {
			logger.debug("[NetworkNode] Interrupted! " + e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * Sends a message to a specified client IP:port, if possible.
	 *
	 * @return boolean value that indicates success or failure.
	 */

	@Override
	public Boolean send(String clientPort, String message) {
		if (message.equals(null)) {
			return Boolean.FALSE;
		}

		clientSock = socketDict.get(clientPort);
		try {
			// logger.info("[NetworkNode] Sending: "+message+" sent through
			// port: "+clientPort.toString());
			clientSock.send(message);
			return Boolean.TRUE;

		} catch (Exception e) {
			if (clientSock.getSocketChannel() != null) {
				clientSock.deleteConnection();
			}
			logger.debug("[NetworkNode] Send Failed: " + message + " not sent through port: " + clientPort.toString());
			return Boolean.FALSE;
		}
	}

	public void setAllServerList(List<String> allServerList) {
		this.allServerList = allServerList;
	}

	public void setConnectSet(Set<String> connectSet) {
		this.connectSet = connectSet;
	}

	public void setControllerIDNetStatic(Map<String, String> controllerIDNetStatic) {
		this.controllerIDNetStatic = controllerIDNetStatic;
	}

	public void setNetcontrollerIDStatic(Map<String, Integer> netcontrollerIDStatic) {
		this.netcontrollerIDStatic = netcontrollerIDStatic;
	}

	public void setServerList(List<String> serverList) {
		this.serverList = serverList;
	}

	public void setSocketDict(Map<String, NioClient> socketDict) {
		this.socketDict = socketDict;
	}

	/**
	 * This function translates socketDict into connectDict, in order to
	 * preserve the abstraction of the underlying network from the actual
	 * election algorithm.
	 */

	@Override
	public void updateConnectDict() {
		connectDict = new HashMap<>();

		for (String seten : connectSet) {
			connectDict.put(seten, netState.OFF);
		}

		for (Map.Entry<String, NioClient> entry : socketDict.entrySet()) {
			connectDict.put(entry.getKey(), netState.ON);
		}

		// logger.info("Connect Dict: "+this.connectDict.toString());
		// logger.info("Socket Dict: "+this.socketDict.toString());

		return;

	}

}
