package net.floodlightcontroller.hasupport;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.hasupport.linkdiscovery.LDHAWorker;

/**
 * This thread ensures that the election class is polled every
 * "pollTime" seconds such that it checks if a new leader is 
 * present in the network. Once you get the leader, you can 
 * do role based programming in this thread; meaning you can 
 * specify functions that the leader of the network should do, 
 * and separate functions that the followers do. Currently, the 
 * leader is used to manage network-wide publishing and 
 * subscribing of updates across all nodes.
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */


public class ControllerLogic implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(ControllerLogic.class);
	
	private AsyncElection ael;
	private final String none = new String("none");
	private final String cid;
	private final String controllerID;
	
	private final Integer timeout     = new Integer(60000);
	private final Integer pollTime    = new Integer(5);
	private Integer ticks		      = new Integer(0);
	private boolean timeoutFlag;
	
	public static final LDHAWorker ldworker = new LDHAWorker();

	public ControllerLogic (AsyncElection ae, String cID ) {
		this.ael = ae;
		this.controllerID = cID;
		this.cid = new String ("C" + cID);
	}

	@Override
	public void run() {
		logger.info("[ControllerLogic] Running...");
		try {
			String leader;
			while (!Thread.currentThread().isInterrupted()) {
				
				leader = ael.getLeader();
				
				
				if(leader.equals(none)) {
					logger.info("[ControllerLogic] FAILED Getting Leader: "+ael.getLeader().toString());
				}
				
				// First try to get the leader:
				if ( leader.equals(none) ) {
					// Functions if you are neither a leader nor a follower and you are active.
					
					timeoutFlag = true;
					Long start = System.nanoTime();
					Long duration = new Long(0);
					
					while( duration <= timeout ) {
						duration = (long) ((System.nanoTime() - start) / 1000000.000) ;
						if(! ael.getLeader().toString().equals(none) ) {
							timeoutFlag = false;
							logger.info("[ControllerLogic] Got Leader: "+ael.getLeader().toString() + " Elapsed :"+ duration.toString());
							break;
						}
						TimeUnit.MILLISECONDS.sleep(25);
					}
					
					// If you can't get the leader within the specified timeout, 
					// then default to controller 1 as the leader.
					if(timeoutFlag) {
						logger.info("[ControllerLogic] Election timed out, setting Controller 1 as LEADER!");
						ael.setTempLeader(new String("1"));
						ael.setLeader(new String("1"));
					}
				
				} else {
					// Role based functions:
				
					if ( leader.equals(this.controllerID) ) {
						// Role based functions: Leader functions
						
						// LEADER initiates publish and subscribe
						logger.info("[ControllerLogic] Calling Hooks...");
						
						// Publish, meaning ask all nodes to call publish hook
							ael.publishQueue();
						//  Subscribe, ask all nodes to subscribe to the leader
						//  can be modified to subscribe to updates from all other 
						//	nodes as well by calling this in a loop.
							ael.subscribeQueue(cid);
	
							
					} else {
						// Role based function: Follower functions
						
						
					}
					
					// If the election times out, then call your own publish and subscribe hooks
					if (timeoutFlag) {
						for (String wrkr: AsyncElection.haworker.getWorkerKeys()) { 
							AsyncElection.haworker.getService(wrkr).publishHook();
							AsyncElection.haworker.getService(wrkr).subscribeHook(cid);
						}

					}
					
					TimeUnit.SECONDS.sleep(pollTime);
					
					// Uncomment this: memory usage/too many files
					if (ticks > 5) {
						System.gc();
						ticks = 0;
					}
					
					ticks += 1;
				}
				
			}
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
