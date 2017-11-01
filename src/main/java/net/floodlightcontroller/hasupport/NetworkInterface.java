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

import java.util.Map;

/**
 * This interface acts as an abstraction between the actual election algorithm
 * used by the HAController in order to pick a leader; and the underlying
 * network topology that connects all the nodes together. If the network needs
 * to be modified, the methods in this interface need to be implemented in order
 * for the election algorithm to work. The connectDict mentioned here keeps
 * state of the underlying network connections between the nodes (ON/OFF).
 * Depending on this, the election algorithm can decide whether to include this
 * node in the election process.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface NetworkInterface {

	public enum ElectionState {
		CONNECT, ELECT, COORDINATE, SPIN
	};

	/**
	 * Holds the state of the network connection for a particular node in the
	 * server configuration.
	 */

	public enum netState {
		ON, OFF
	};

	/**
	 * This function is used to spin in the CONNECT state of the election
	 * algorithm, until a majority (>51%) of the nodes are connected, the
	 * algorithm is locked in this state until the majority condition is
	 * satisfied.
	 */

	public ElectionState blockUntilConnected();

	/**
	 * This function is used to TRY connecting to the nodes that are not yet
	 * connected but are present in the server configuration. It updates the
	 * connectDict to reflect the current state of the underlying network.
	 *
	 * connectDict : HashMap which holds the <portnumber, state> of all the
	 * nodes.
	 *
	 * @return : Updated unmodifiable copy of the connectDict
	 */

	public Map<String, netState> checkForNewConnections();

	/**
	 * This is the connectClients() function which is used to TRY connecting to
	 * all the client nodes currently present in the connectSet and store the
	 * successfully connected clients in a dictionary called connectDict.
	 *
	 * connectSet : A set that holds a list of the configured nodes from the
	 * server configuration
	 *
	 * @return : An Unmodifiable copy of the HashMap which holds the
	 *         <portnumber, state> of all the nodes. (connectDict)
	 */

	public Map<String, netState> connectClients();

	/**
	 * This function is used to test if the connections in the connectDict are
	 * still active and expire stale connections from the connectDict.
	 *
	 * connectDict : HashMap which holds the <portnumber, state> of all the
	 * nodes.
	 *
	 * @return : Updated unmodifiable copy of the connectDict
	 */

	public Map<String, netState> expireOldConnections();

	/**
	 * Get an unmodifiable version of the connectDict.
	 *
	 * @return : Updated unmodifiable copy of the connectDict
	 */

	public Map<String, netState> getConnectDict();

	/**
	 * This is the recv() function which is used to receive a message from any
	 * other node.
	 *
	 * @param receivingPort
	 *            : netaddr that is waiting to receive a message.
	 * @return : Message that was received.
	 */

	public String recv(String receivingPort);

	/**
	 * This is the send function which is used to send a message from one node
	 * to another using the network.
	 *
	 * @param clientPort
	 *            : Destination client netaddr.
	 * @param message
	 *            : Message that needs to be sent.
	 * @return : Return code, success/fail.
	 */

	public Boolean send(String clientPort, String message);

	/**
	 * Translate the socketDict into the connectDict, meaning set ON/OFF for the
	 * client ports that are connected, so that the election algorithm knows if
	 * it can send a message to the particular client or not.
	 */

	public void updateConnectDict();

}
