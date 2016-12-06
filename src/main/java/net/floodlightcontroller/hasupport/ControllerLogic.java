package net.floodlightcontroller.hasupport;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.hasupport.linkdiscovery.LDHAWorker;

public class ControllerLogic implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(ControllerLogic.class);
	
	private AsyncElection ael;
	private final String none = new String("none");
	private final String cid;
	private final String controllerID;
	
	private final Integer timeout     = new Integer(60000);
	private boolean timeoutFlag;
	
	public static final LDHAWorker ldworker = new LDHAWorker();

	public ControllerLogic (AsyncElection ae, String cID ) {
		this.ael = ae;
		this.controllerID = cID;
		this.cid = new String ("C" + cID);
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		logger.info("[ControllerLogic] +++++++++CLOGIC RUNNING +++++++++++++++++");
		try {
			String leader;
			while (!Thread.currentThread().isInterrupted()) {
				
				leader = ael.getLeader();
				
				
				if(leader.equals(none)) {
					logger.info("[ControllerLogic] FAILED Getting Leader: "+ael.getLeader().toString());
				}
				
				// 1. First try to get the leader
				if ( leader.equals(none) ) {
					
					timeoutFlag = true;
					Long start = System.nanoTime();
					Long duration = new Long(0);
					
					while( duration <= timeout ) {
						duration = (long) ((System.nanoTime() - start) / 1000000.000) ;
						if(! ael.getLeader().toString().equals(none) ) {
							timeoutFlag = false;
							logger.info("[HAController MEASURE] Got Leader: "+ael.getLeader().toString() + "Elapsed :"+ duration.toString());
							break;
						}
						TimeUnit.MILLISECONDS.sleep(25);
					}
					
					// If you can't get the leader within the specified timeout, 
					// then default to controller 1 as the leader.
					if(timeoutFlag) {
						logger.info("Election timed out, setting Controller 1 as LEADER!");
						ael.setTempLeader(new String("1"));
						ael.setLeader(new String("1"));
					}
				
				} else {
				
					if ( leader.equals(this.controllerID) ) {
						// LEADER initiates publish and subscribe
						logger.info("[LEADER] Calling Hooks...");
						
						// 2. Then Publish, meaning ask all nodes to call publish hook
							ael.publishQueue();
						// 3. Then Subscribe, ask all nodes to subscribe to the leader
						//    can be modified to subscribe to updates from all other nodes as well by calling
						//    this in a for loop.
							ael.subscribeQueue(cid);
							
							if (timeoutFlag) {
								for (String wrkr: AsyncElection.haworker.getWorkerKeys()) { 
									AsyncElection.haworker.getService(wrkr).publishHook();
									AsyncElection.haworker.getService(wrkr).subscribeHook(cid);
								}

							}
	
							
					} else {
						if(timeoutFlag) {
							for (String wrkr: AsyncElection.haworker.getWorkerKeys()) { 
								AsyncElection.haworker.getService(wrkr).publishHook();
								AsyncElection.haworker.getService(wrkr).subscribeHook(cid);
							}
						}
					}
					
					TimeUnit.SECONDS.sleep(5);
					//System.gc(); (uncomment this if you care about memory usage)
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
