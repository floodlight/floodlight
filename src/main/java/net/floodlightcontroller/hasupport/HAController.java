package net.floodlightcontroller.hasupport;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The HAController
 * 
 * This class starts two threads, a high level thread called
 * controller logic which is responsible for managing the election 
 * process and the actual leader election thread. Based on the outcome
 * of the election, controller logic can be used to assign tasks from 
 * to the followers and also specify roles for the leader. Election 
 * priorities can be set, meaning, a predefined list of the order in 
 * which the nodes get selected in the election can be supplied using the
 * setElectionPriorities function.
 * 
 * Possible improvements:
 * a. Implement a better scheduling algorithm, and schedule the election
 * and controller logic threads to engineer the scheduling more effectively.
 * 
 * @author Bhargav Srinivasan, Om Kale
 */

public class HAController implements IFloodlightModule, IHAControllerService {

	private static Logger logger = LoggerFactory.getLogger(HAController.class);
	protected static IHAWorkerService haworker;
	private static Map<String, String> config = new HashMap<String, String>();
	private final ArrayList<Integer> priorities = new ArrayList<Integer>();
	private final String none = new String("none");
	private AsyncElection ael;
	private ControllerLogic cLogic;
	
	public static void setSysPath(){
		try {
			final Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
			usrPathsField.setAccessible(true);
			final String[] path = (String[]) usrPathsField.get(null);
			final String[] newPaths = Arrays.copyOf(path, path.length +2);
			newPaths[newPaths.length - 2] = "lib/";
			newPaths[newPaths.length - 1] = "lib/jzmq-3.1.0.jar";
			usrPathsField.set(null, newPaths);		
		} catch (NoSuchFieldException | SecurityException 
				|  IllegalArgumentException |  IllegalAccessException e) {
			logger.debug(new String(e.toString()));
		}
		
		return;
	}	

	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IHAControllerService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IHAControllerService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
    	Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IHAWorkerService.class);
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		setSysPath();
		logger = LoggerFactory.getLogger(HAController.class);
		haworker = context.getServiceImpl(IHAWorkerService.class);
		config = context.getConfigParams(this);
		logger.info("Configuration parameters: {} {} ", new Object[] {config.toString(), config.get("nodeid")});
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
//		priorities.add(2);
//		priorities.add(1);
//		priorities.add(3);
//		priorities.add(4);
		
		//Read config file and start the Election class with the right params.
		ScheduledExecutorService sesController = Executors.newScheduledThreadPool(10);
		ael = new AsyncElection( config.get("serverPort") ,config.get("clientPort"), config.get("nodeid"), haworker );
		ael.setElectionPriorities(priorities);
		cLogic = new ControllerLogic(ael,config.get("nodeid"));
		try{
			// The logic behind, the timing: QueueDevice is the bottleneck timed at 30 ms.
			// Election scheduled at 60 ms which schedules ZMQ node at 30 ms which runs
			// QueueDevice continuously. Controller Logic polls for leader every 25 ms.
			
			sesController.scheduleAtFixedRate(ael, 0, 60000, TimeUnit.MICROSECONDS);
			sesController.scheduleAtFixedRate(cLogic, 20000, 25000, TimeUnit.MICROSECONDS);
			
		} catch (Exception e){
			sesController.shutdownNow();
			logger.info("[Election] Was interrrupted! "+e.toString());
			e.printStackTrace();
		}
		
		logger.info("HAController is starting...");
	
		return;
		
	}


	@Override
	public String getLeaderNonBlocking() {
		// TODO Auto-generated method stub
		return ael.getLeader().toString();
	}

	@Override
	public String pollForLeader() {
		// TODO Auto-generated method stub
		try {
			Integer timeout     = new Integer(60000);
			Long start 			= System.nanoTime();
			Long duration 		= new Long(0);
			
			while( duration <= timeout ) {
				duration = (long) ((System.nanoTime() - start) / 1000000.000) ;
				if(! ael.getLeader().toString().equals(none) ) {
					break;
				}
				TimeUnit.MILLISECONDS.sleep(25);	
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			logger.info("pollForLeader was interrupted!");
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.info("ERROR: [HAController] pollForLeader");
			e.printStackTrace();
		}
		
		return ael.getLeader().toString();
	}

	@Override
	public void setElectionPriorities(ArrayList<Integer> priorities) {
		// TODO Auto-generated method stub
		ael.setElectionPriorities(priorities);
		return;
	}
	
}
