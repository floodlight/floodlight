package net.floodlightcontroller.hasupport;

import java.util.ArrayList;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * IHAControllerService
 * 
 * Exposes two of the HAController features to the user, 
 * first is to getLeaderNonBlocking() which gets the current network-wide
 * leader and returns immediately  and the setElectionPriorities method 
 * can be used to set pre-defined priorities for the election, by supplying an 
 * ordered list of Integers which hold the order in which the nodes are to be 
 * selected in the election process.
 * 
 * The pollForLeader method is a blocking call which is used to poll until
 * a current network-wide leader is available. This function will either 
 * timeout and return "none" if there isn't a leader in the network, or 
 * will return the current leader.
 * 
 * We have also exposed the send and receive functions in our ZMQNode, ZMQServer
 * classes in order to let other modules send and receive messages between 
 * each other. Messages from other modules must be prefixed with m: 'm<Actual message>'
 * and functions to process this message can be added to ZMQServer. (a TODO section has 
 * been marked in the processServerMessage method in ZMQServer)
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface IHAControllerService extends IFloodlightService {
	
	public String getLeaderNonBlocking();
	
	public String pollForLeader();
	
	public void setElectionPriorities(ArrayList<Integer> priorities);
	
	public boolean send(String to, String msg);
	
	public String recv(String from);

}
