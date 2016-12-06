package net.floodlightcontroller.hasupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * This class implements a simple self stabilizing,
 * leader election protocol which is fault tolerant
 * up to N nodes. The concept behind this implementation
 * is described in Scott D. Stoller's 1997 paper 
 * 'Leader Election in Distributed Systems with Crash Failures'
 * The ALE1 & ALE2' requirements are being followed.
 * 
 * We have an additional constraint that AT LEAST 51% of the
 * nodes must be fully connected before the election happens,
 * this is in order to ensure that there will be at least one 
 * group which will produce a majority response to elect one 
 * leader. However, the drawback of this system is that 51%
 * of the nodes have to be connected in order for the election
 * to begin. (transition from CONNECT -> ELECT)
 * 
 * FD: expireOldConnections() uses PULSE to detect failures.
 * 
 * Possible improvements:
 * Messages between nodes are being sent sequentially in a for loop,
 * this can be modified to happen in parallel.
 * 
 * @author Bhargav Srinivasan
 */

public class AsyncElection implements Runnable{
	
	private static Logger logger = LoggerFactory.getLogger(AsyncElection.class);
	public  static ZMQNode network;
	
	protected static IHAWorkerService haworker;
	
	private final String serverPort;
	ArrayList<Thread> serverThreads = new ArrayList<Thread>();
	private final ArrayList<Integer> electionPriorities = new ArrayList<Integer>();
	private final LinkedBlockingQueue<String> publishQueue = new LinkedBlockingQueue<String>();
	private final LinkedBlockingQueue<String> subscribeQueue = new LinkedBlockingQueue<String>();
	private final String controllerID;
	
	public AsyncElection(String sp, String cid) {
		this.serverPort    = sp;
		this.controllerID = cid;
		this.setlead       = new String("SETLEAD "   + this.controllerID);
		this.leadermsg     = new String("LEADER "    + this.controllerID);
		this.iwon 		   = new String("IWON "      + this.controllerID);
		this.heartbeat     = new String("HEARTBEAT " + this.controllerID);
	}
	
	public AsyncElection(String serverPort, String clientPort, String controllerID, IHAWorkerService haw){
		AsyncElection.network       = new ZMQNode(serverPort,clientPort,controllerID);
		this.serverPort    = serverPort;
		this.controllerID  = controllerID;
		this.setlead       = new String("SETLEAD "   + this.controllerID);
		this.leadermsg     = new String("LEADER "    + this.controllerID);
		this.iwon 		   = new String("IWON "      + this.controllerID);
		this.heartbeat     = new String("HEARTBEAT " + this.controllerID);
		AsyncElection.haworker      = haw;
	}
	
	/**
	 * Indicates who the current leader of the entire system is.
	 */
	private String leader             = new String("none");
	private String tempLeader         = new String("none");
	private final String none         = new String("none");
	private final String ack 		  = new String("ACK");
	private final String publish 	  = new String("BPUBLISH");
	private final String subscribe 	  = new String("KSUBSCRIBE");
	private final String pulse        = new String("PULSE");
	private final String you		  = new String("YOU?");
	private final String no			  = new String("NO");
	private final String leadok       = new String("LEADOK");
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
	

	public String getLeader(){
		final String lead; 
		synchronized (this.leader) {
			lead = this.leader;
		}
		return lead;
	}
	
	public String gettempLeader(){
		final String tempLead;
		synchronized (this.tempLeader) {
			tempLead = this.tempLeader;
		}
		return tempLead;
	}
	
	public void setLeader(String leader){
		synchronized (this.leader) { 
			this.leader = leader;
		}
		return;
	}
	
	public void setTempLeader(String tempLeader){
		synchronized(this.tempLeader) {
			this.tempLeader = tempLeader;
		}
		return;
	}
	
	public void setTimeStamp(String ts) {
		synchronized(this.timestamp) {
			this.timestamp = ts;
		}
		return;
	}
	
	public String getTimeStamp() {
		final String ts;
		synchronized(this.timestamp) {
			 ts = this.timestamp;
		}
		return ts;
	}
	
	public void setElectionPriorities(ArrayList<Integer> priorities) {
		// Set the order in which nodes are supposed to get elected.
		synchronized (electionPriorities) {
			if( (priorities.size()) > 0 && (priorities.size() == network.totalRounds+1) ) {
				electionPriorities.addAll(priorities);
			} else {
				logger.info("[Election] Could not set pritorities!");
			}
		}
		return;
	}
	
	/**
	 * Server start: Start the network and ZMQServer Threads.
	 */
	
	
	public void publishQueue(){
		synchronized (publishQueue) {
			publishQueue.offer("PUBLISH");
		}
		return;
	}
	
	public void subscribeQueue(String sub){
		synchronized (subscribeQueue) {
			subscribeQueue.offer(sub);
		}
		return;
	}
	
	public void publish(){
		try{
			// Check for new nodes to connect to, and refresh the socket connections.
			//this.connectionDict = network.checkForNewConnections();
			
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){
					
					network.send( entry.getKey(), publish );
					network.recv(entry.getKey());
					
					// If we get an ACK, that's good.
					logger.debug("[Publish] Received ACK from "+entry.getKey().toString());
				}
			}
			
			return;
			
		} catch (Exception e){
			logger.debug("[Election] Error in PUBLISH!");
			e.printStackTrace();
		}
		
	}
	
	public void subscribe(String cid){
		try{
			// Check for new nodes to connect to, and refresh the socket connections.
			//this.connectionDict = network.checkForNewConnections();
			
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){
					
					String submsg = new String(subscribe + " " + cid);
					logger.info("[Leader Subscribe] Subscribing to: {}", new Object[]{cid});
					
					network.send( entry.getKey(), submsg );
					network.recv(entry.getKey());
					
					// If we get an ACK, that's good.
					logger.debug("[Subscribe] Received ACK from "+entry.getKey().toString());
				}
			}
			
			return;
			
		} catch (Exception e){
			logger.debug("[Election] Error in SUBSCRIBE!");
			e.printStackTrace();
		}
		
	}
	
	private void sendHeartBeat(){
		// The Leader will send a HEARTBEAT message in the COORDINATE state
		// after the election and will expect a reply from a majority of
		// acceptors.
		
		
		HashSet<String> noSet = new HashSet<String>();
		try{
			
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){
					
					// If the HeartBeat is rejected, populate the noSet.
					network.send( entry.getKey(), heartbeat+" "+timestamp );
					String reply = network.recv(entry.getKey());
					
					if ( reply.equals(no) || (!reply.equals(ack+timestamp)) ){
						noSet.add(entry.getKey());
					}
					// If we get an ACK, that's good.
					logger.debug("[Election] Received HEARTBEAT ACK from "+entry.getKey().toString());
				}
			}
			
			if(noSet.size() >= network.majority){
				setLeader(none);
			}
			
			return;
			
		} catch (Exception e){
			logger.debug("[Election] Error in sendHeartBeat!");
			e.printStackTrace();
		}
		
	}
	
	private void sendIWon(){
		// The winner of the election, or the largest node that is currently active
		// in the network sends an "IWON" message in order to initiate the three phase
		// commit to set itself as the leader of the network.
		try{
			Set<String> reply = new HashSet<String>();
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){
					
					network.send(entry.getKey(), iwon+" "+timestamp);
					reply.add( network.recv(entry.getKey()) );
					logger.debug("Received reply for IWON from: "+entry.getKey().toString() + reply.toString());
					
				}
			}
			
			if( reply.contains(ack) ) {
				setTempLeader(this.controllerID);
			}
			
			return;
					
		} catch (Exception e){
			logger.debug("[Election] Error in sendIWon!");
			e.printStackTrace();
		}
		
	}
	
	private void sendLeaderMsg(){
		// Send a "LEADER" message to all nodes and try to receive "LEADOK"
		// messages from them. If count("LEADOK") > majority, then you have
		// won the election and hence become the leader.
		// Phase 2 of the three phase commit.
		
		HashSet<String> acceptors = new HashSet<String>();
		try{
			
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){
					
					network.send(entry.getKey(), leadermsg+" "+timestamp);
					String reply = network.recv(entry.getKey());
					if( reply.equals(leadok) ){
						acceptors.add(entry.getKey());
					}
				}
				
			}
			
			if( acceptors.size() >= network.majority ){
				logger.debug("[Election sendLeaderMsg] Accepted leader: "+this.controllerID+" Majority: "+network.majority+"Acceptors: "+acceptors.toString());
				setLeader(network.controllerID);
				this.currentState = ElectionState.COORDINATE;
			} else {
				logger.debug("[Election sendLeaderMsg] Did not accept leader: "+this.controllerID+" Majority: "+network.majority+"Acceptors: "+acceptors.toString());
				setLeader(none);
				this.currentState = ElectionState.ELECT;
			}
			
			return;
			
		} catch (Exception e){
			logger.debug("[Election] Error in sendLeaderMsg!");
			e.printStackTrace();
		}
		
	}
	
	private void setAsLeader(){
		// The leader will set itself as leader during each COORDINATE
		// state loop, to ensure that all nodes see it as the leader.
		// Phase 3 of the three phase commit.
		
		HashSet<String> noSet = new HashSet<String>();
		try{
			
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){
					
					// If the leader is rejected, populate the noSet.
					network.send(entry.getKey(), setlead+" "+timestamp);
					String reply = network.recv(entry.getKey());
					
					if ( reply.equals(no) ){
						noSet.add(entry.getKey());
					}
					
					// If we get an ACK, that's good.
					logger.debug("[Election] Received SETLEAD ACK from "+entry.getKey().toString());
				}
			}
			
			if(noSet.size() >= network.majority){
				setLeader(none);
			}
			
			return;
			
		} catch (Exception e){
			logger.debug("[Election] Error in setAsLeader!");
			e.printStackTrace();
		}
		
	}
	
	private void checkForLeader(){
		// Ask each node if they are the leader, you should get an
		// ACK from only one of them, if not, then reset the leader.
		HashSet<String> leaderSet = new HashSet<String>();
		String reply = new String();
		String r1 = new String();
		String r2 = new String();
		StringTokenizer st = new StringTokenizer(reply);
		
		try{
			
			for(HashMap.Entry<String, netState> entry: this.connectionDict.entrySet()){
				if( this.connectionDict.get(entry.getKey()).equals(netState.ON) ){				
					
					network.send(entry.getKey(), you+" "+timestamp);
					reply = network.recv(entry.getKey());
					st = new StringTokenizer(reply);
					r1 = st.nextToken();
					if (st.hasMoreTokens()) {
						r2 = st.nextToken();
					}
					
					if ( (!r1.equals(no)) && (r2.equals(timestamp)) ){
						leaderSet.add(r1);
					} else {
						logger.debug("[Election] Check Leader: " + reply +" from "+entry.getKey().toString());
						continue;
					}
					
				}
			}
			
			logger.debug("[Election checkForLeader] Leader Set: "+leaderSet.toString());
			
			// Remove blank objects from set, if any.
			if ( leaderSet.contains(new String("")) ){
				leaderSet.remove(new String(""));
			}
			
			// Remove none from set, if any.
			if ( leaderSet.contains(none) ){
				leaderSet.remove(none);
			}
			
			// Remove null objects from set, if any.
			if( leaderSet.contains(null) ){
				logger.debug("[Election] Leader Set contains null");
				leaderSet.remove(null);
			}
			
			
			if( leaderSet.size() == 1 ){
				 setLeader(leaderSet.stream()
										.findFirst().get()); 
			} else if ( leaderSet.size() > 1 ){
				setLeader(none);
				logger.debug("[Election checkForLeader] SPLIT BRAIN!!");
				logger.debug("[Election checkForLeader] Current Leader is none");
			} else if ( leaderSet.size() < 1 ){
				setLeader(none);
				logger.debug("[Election checkForLeader] Current Leader is none "+ this.leader.toString() );
			}
			
			return;
			
		} catch (Exception e){
			logger.debug("[Election] Error in CheckForLeader");
			e.printStackTrace();
		}
		
	}
	
	private void electionLogic(){
		// List of controllerIDs of all nodes.
		ArrayList<Integer> nodes = new ArrayList<Integer>();
		Integer maxNode = new Integer(0);
		
		if( (electionPriorities.size() > 0) && (electionPriorities.size() == network.totalRounds+1) ) { 
			nodes.addAll(electionPriorities);
		} else {
			// Generate list of total possible CIDs.
			for (Integer i = (network.totalRounds+1) ; i > 0 ; i--){
				nodes.add(i);
			}
		}
		
		logger.info(" +++++++++ [Election Logic] Nodes participating: "+nodes.toString());
		
		// TODO Something weird is going on here...
		
		// Get the node whose CID is numerically greater.
		Set<String> connectDictKeys =  this.connectionDict.keySet();
		HashSet<Integer> activeCIDs = new HashSet<Integer>();
		
		// Convert active controller ports into a Set of their IDs.
		for (String port: connectDictKeys) {
			if ( this.connectionDict.get(port) != null  && this.connectionDict.get(port).equals(netState.ON) ) {
				activeCIDs.add(network.netcontrollerIDStatic.get(port));
			}
		}
		
		activeCIDs.add(new Integer(this.controllerID));
		
		logger.info("Active controllers: "+activeCIDs.toString()+"ConnectDict Keys: "+connectDictKeys.toString());
		
		// Find the current active maxNode.
		
		for (Integer i=0 ; i< nodes.size(); i++ ) {
			if ( activeCIDs.contains(nodes.get(i)) ) {
				maxNode = nodes.get(i);
				break;
			}
		}
		
		// Edge case where you are the max node && you are ON.
		if ( this.controllerID.equals(maxNode.toString()) ){
			setLeader(maxNode.toString());
			return;
		}
		
		String maxNodePort = network.controllerIDNetStatic.get(maxNode.toString()).toString();
		
		// Check if Max Node is alive, and set it as leader if it is.
		try{
			
			for(int i=0; i < network.numberOfPulses; i++){
				
				network.send(maxNodePort, pulse);
				String reply = network.recv(maxNodePort);
				
				if ( reply.equals(ack) ){
					setLeader(maxNode.toString());
				}
			}
			
		} catch (Exception e) {
			logger.debug("[Election] Error in electionLogic!");
			e.printStackTrace();
		}
		
		return;
		
	}
	
	private void elect(){
		// Max Set election:
		// All nodes will pick the max CID which they see in the network,
		// any scenario wherein two different leaders might be picked gets resolved
		// using the checkForLeader function.
		
		// Ensure that majority are still connected.
		if( network.socketDict.size() < network.majority ){
			return;
		}
		
		// Clear leader variables.
		setTempLeader(none);
		setLeader(none);
		
		// Check if actually in elect state
		if (!(this.currentState == ElectionState.ELECT)){
			return;
		}
		
		// Node joins AFTER election:
		// To check if a node joined after election, i.e.
		// a leader is already present. Run the checkForLeader
		// function and if it returns a leader then accept the 
		// existing leader and go to the SPIN state.
		timestamp =  String.valueOf(System.nanoTime());
		this.checkForLeader();
		
		// If a leader has already been set, exit election state 
		// and SPIN.
		if(! this.leader.equals(none) ){
			this.currentState = ElectionState.SPIN;
			return;
		}
		
		// End of Node joins AFTER election.
		
		// Actual election logic.
		this.electionLogic();
		
		if( this.leader.equals(network.controllerID) ){
			logger.debug("[Election] I WON THE ELECTION!");
			timestamp =  String.valueOf(System.nanoTime());
			this.sendIWon();
			this.sendLeaderMsg();
			if(this.leader.equals(network.controllerID)) {
				this.setAsLeader();
			}
		} else if ( this.leader.equals(none) ){
			this.currentState = ElectionState.ELECT;
		} else {
			this.currentState = ElectionState.SPIN;
		}
		
		// End of Actual Election logic.
		return;
	}
	
	private void cases(){
		try {
		while(! Thread.currentThread().isInterrupted()) {
			logger.info("Current State: "+currentState.toString());
			switch(currentState){
				
				case CONNECT:
					
					// Block until a majority of the servers have connected.
					this.currentState =  network.blockUntilConnected();
					
					// Majority of the servers have connected, moving on to elect.
					break;
					
				case ELECT:
					
					// Check for new nodes to connect to, and refresh the socket connections.
					this.connectionDict = network.checkForNewConnections();
					logger.debug("[ELECT] =======SIZES+++++ {} {}", new Object[] {network.socketDict.size(), network.majority});
					// Ensure that a majority of nodes have connected, otherwise demote state.
					if( network.socketDict.size() < network.majority ){
						this.currentState = ElectionState.CONNECT;
						break;
					}
					
					//Start the election if a majority of nodes have connected.
					this.elect();
					
					// Once the election is done and the leader is confirmed,
					// proceed to the COORDINATE or FOLLOW state.
					break;
					
				case SPIN:
					
					// This is the resting state after the election.
					this.connectionDict = network.checkForNewConnections();
					
					if( this.leader.equals(none) ){
						this.currentState = ElectionState.ELECT;
						break;
					}
					
					// This is the follower state, currently there is a leader in the network.
					logger.info("+++++++++++++++ [FOLLOWER] Leader is set to: "+this.leader.toString());
					
					// Check For Leader: This function ensures that there is only one leader set for
					// the entire network. None or multiple leaders causes it to set the currentState to ELECT.
					timestamp =  String.valueOf(System.nanoTime());
					this.checkForLeader();
					
					if(! publishQueue.isEmpty() ) {
						for (String wrkr: AsyncElection.haworker.getWorkerKeys()) { 
							AsyncElection.haworker.getService(wrkr).publishHook();
						}
						publishQueue.clear();
					}
					
					if(! subscribeQueue.isEmpty() ) {
						String cid = subscribeQueue.remove();
						for (String wrkr: AsyncElection.haworker.getWorkerKeys()) { 
							AsyncElection.haworker.getService(wrkr).subscribeHook(cid);
						}
						logger.info("[Follower Subscribe] Subscribing to: {}", new Object[]{cid});
						subscribeQueue.clear();
					}
					
					TimeUnit.SECONDS.sleep(this.chill.intValue());
					
					break;
					
				case COORDINATE:
					
					// This is the resting state of the leader after the election.
					this.connectionDict = network.checkForNewConnections();
					
					if( this.leader.equals(none) ){
						this.currentState = ElectionState.ELECT;
						break;
					}
					
					// This is the follower state, currently I am the leader of the network.
					logger.info("+++++++++++++++ [LEADER] Leader is set to: "+this.leader.toString());
					
					// Keep the leader in coordinate state.
					timestamp =  String.valueOf(System.nanoTime());
					this.sendIWon();
					this.sendLeaderMsg();
					if(this.leader.equals(network.controllerID)) {
						this.setAsLeader();
					}
					
					// Keep sending a heartbeat message, and receive a majority of acceptors,
					// otherwise go to the elect state.
					timestamp =  String.valueOf(System.nanoTime());
					this.sendHeartBeat();
					
					if(! publishQueue.isEmpty() ) {
						AsyncElection.haworker.getService("LDHAWorker").publishHook();
						AsyncElection.haworker.getService("TopoHAWorker").publishHook();
						// Network-wide publish
						publish();
						publishQueue.clear();
					}
					
					if(! subscribeQueue.isEmpty() ) {
						String cid = subscribeQueue.remove();
						AsyncElection.haworker.getService("LDHAWorker").subscribeHook(cid);
						AsyncElection.haworker.getService("TopoHAWorker").subscribeHook(cid);
						// Network-wide Subscribe
						subscribe(cid);
						subscribeQueue.clear();
					}
					
					TimeUnit.SECONDS.sleep(this.chill.intValue());
					
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

	@Override
	public void run() {
		
		ScheduledExecutorService sesElection = Executors.newScheduledThreadPool(5);
		
		try{
				
			Integer noServers = new Integer(0);
			ZMQServer serverTh = new ZMQServer(this.serverPort, this, this.controllerID);
			
			if (network.totalRounds <= 1){
				noServers = 1;
			} else {
				noServers = (int) Math.ceil(Math.log10(network.totalRounds));
			}
			
			if(noServers <= 1){
				noServers =1;
			}
			
			sesElection.scheduleAtFixedRate(network, 0, 30000, TimeUnit.MICROSECONDS);
			for (Integer i=0; i < noServers; i++){
				sesElection.scheduleAtFixedRate(serverTh, 0, 30000, TimeUnit.MICROSECONDS);
			}
			
			
			logger.info("[Election] Network majority: "+network.majority.toString());
			logger.info("[Election] Get netControllerIDStatic: "+network.getnetControllerIDStatic().toString());	
			this.cases();

		} catch (Exception e){
			logger.debug("[Election] Was interrrupted! "+e.toString());
			e.printStackTrace();
			sesElection.shutdownNow();
		}
	}

}
