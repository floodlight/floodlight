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

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * IHAControllerService
 *
 * Exposes two of the HAController features to the user, first is to
 * getLeaderNonBlocking() which gets the current network-wide leader and returns
 * immediately and the setElectionPriorities method can be used to set
 * pre-defined priorities for the election, by supplying an ordered list of
 * Integers which hold the order in which the nodes are to be selected in the
 * election process.
 *
 * The pollForLeader method is a blocking call which is used to poll until a
 * current network-wide leader is available. This function will either timeout
 * and return "none" if there isn't a leader in the network, or will return the
 * current leader.
 *
 * We have also exposed the send and receive functions in our ZMQNode, ZMQServer
 * classes in order to let other modules send and receive messages between each
 * other. Messages from other modules must be prefixed with m: 'm<Actual
 * message>' and functions to process this message can be added to ZMQServer. (a
 * TODO section has been marked in the processServerMessage method in ZMQServer)
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface IHAControllerService extends IFloodlightService {

	/**
	 * Gets the current network-wide leader, returns "none" if no leader is
	 * currently available.
	 *
	 * @return Current network-wide leader.
	 */
	public String getLeaderNonBlocking();

	/**
	 * Gets the current network-wide leader, if available, it returns
	 * immidiately, otherwise it polls every 25ms for 60s to try and get the
	 * leader. If unsuccessful, returns "none".
	 *
	 * @return Current network-wide leader.
	 */

	public String pollForLeader();

	/**
	 * Receive a message from any other active controller. The "from" address
	 * must be filled in with the 'clientPort' variable (mentioned in the
	 * properties file) of the controller to which you want receive a message
	 * from. The recv timeout is currently 500ms, which can be modified in
	 * NetworkNode, HAServer.
	 *
	 * You must implement the message handler the HAServer class as mentioned
	 * above.
	 *
	 *
	 * @param from
	 *            : 'clientPort' of the controller you want to receive from.
	 * @return : Received message.
	 */

	public String recv(String from);

	/**
	 * Send a message to any other active controller. The "to" address must be
	 * filled in with the 'clientPort' variable (mentioned in the properties
	 * file) of the controller to which you want to send a message to.
	 *
	 * The send timeout is currently 500ms, which can be modified in
	 * NetworkNode, HAServer.
	 *
	 * Your message must be prefixed with the letter 'm': 'm<actual message>'
	 * and you must handle your message with an implementation in the HAServer
	 * class.
	 *
	 *
	 * @param to
	 *            : 'clientPort' of the controller you want to send this message
	 *            to
	 * @param msg
	 *            : Message in the format 'm<actual message>'
	 * @return : true if send succeeds, false otherwise.
	 */

	public boolean send(String to, String msg);

	/**
	 * Allows you to set the current priorities for active controllers in the
	 * network to be picked as the leader. Pass an ordered ArrayList of integers
	 * to this method, consisting of nodeids mentioned in the properties files,
	 * in the desired order.
	 *
	 * @param priorities
	 *            : Ordered ArrayList of nodeids
	 */

	public void setElectionPriorities(ArrayList<Integer> priorities);

}
