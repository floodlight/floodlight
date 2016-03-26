package net.floodlightcontroller.simpleft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.internal.IOFConnectionListener;
import net.floodlightcontroller.core.internal.IOFSwitchManager;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.ISwitchDriverRegistry;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;
import net.floodlightcontroller.util.TimedCache;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.config.ClusterConfig;
import org.sdnplatform.sync.internal.config.IClusterConfigProvider;
import org.sdnplatform.sync.internal.config.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;

public class FT implements 
IOFMessageListener, 
IFloodlightModule,
IStoreListener<String>,
IOFSwitchListener
{

	private ISyncService syncService;
	private IStoreClient<String, String> storeClient;
	//private IFloodlightProviderService floodlightProvider;
	
	protected static Logger logger = LoggerFactory.getLogger(FT.class);

	protected static IThreadPoolService threadPoolService;
	protected static SingletonTask syncTestTask;
	protected static SingletonTask heartBeatTask;
	private final int HEARTBEAT = 3;//time to discover a crashed node
	
	protected static IOFSwitchService switchService;
	private static UtilDurable utilDurable;
			
	private String controllerId;
	
	private HashMap<Short, Integer> cluster;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return FT.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		return l;

	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = 
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		return m;

	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		//l.add(IFloodlightProviderService.class);
		l.add(IStorageSourceService.class);
		l.add(ISyncService.class);
		l.add(IThreadPoolService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
		this.syncService = context.getServiceImpl(ISyncService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		//floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		utilDurable = new UtilDurable();

		Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		controllerId = configParams.get("controllerId");
		cluster = new HashMap<>();
		
			
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {

		/**
		 * ##############################################################
		 *                          FAULT TOLERANCE BEGIN
		 * ##############################################################
		 *                           
		 */
		Collection<Node> nodes = syncService.getClusterConfig().getNodes();
		Iterator<Node> it = nodes.iterator();
		while (it.hasNext()) {
			Node node = it.next();
			if(!controllerId.equals(""+node.getNodeId())){
				cluster.put(node.getNodeId(), 0);
			}
		}		
		logger.debug("ClusterConfig: {}", cluster);
		
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		heartBeatTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() { 				
				HashMap<Short, Integer> mapC = syncService.getConnections();				
				Iterator<Short> it = cluster.keySet().iterator();
				while (it.hasNext()) {
					Short nodeId = it.next();
					if(!mapC.containsKey(nodeId)){
						logger.debug("Crashed nodeId: {}. Role request to switches...", nodeId);
						String swIds=null; 
						try {
							swIds = storeClient.get(""+nodeId).getValue();
							logger.debug("Switches managed by nodeId:{}, {}", nodeId, swIds);							
						} catch (SyncException e) {
							e.printStackTrace();
						}
						
						if(swIds!= null){
							String swId[] = swIds.split(",");
							for (int i = 0; i < swId.length; i++) {
								setSwitchRole(OFControllerRole.ROLE_MASTER, swId[i]);	
							}	
						}	
					}
				}				
				heartBeatTask.reschedule(HEARTBEAT, TimeUnit.SECONDS);
			}
		});
		heartBeatTask.reschedule(10, TimeUnit.SECONDS);
		
		
		
		/**
		 * ##############################################################
		 *                          UPDATE SWITCHES
		 * ##############################################################                          
		 */
		
		try {
			this.syncService.registerStore("FT_Switches", Scope.GLOBAL);
			this.storeClient = this.syncService
					.getStoreClient("FT_Switches",
							String.class,
							String.class);
			this.storeClient.addStoreListener(this);
		} catch (SyncException e) {
			throw new FloodlightModuleException("Error while setting up sync service", e);
		}
		
		ses = threadPoolService.getScheduledExecutor();
		syncTestTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() { 				
				try {
					storeClient.put(controllerId, getActiveSwitches());
				} catch (SyncException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				syncTestTask.reschedule(6, TimeUnit.SECONDS);
			}
		});
		syncTestTask.reschedule(6, TimeUnit.SECONDS);
		

	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}



	@Override
	public void keysModified(Iterator<String> keys,
			org.sdnplatform.sync.IStoreListener.UpdateType type) {
		// TODO Auto-generated method stub
		while(keys.hasNext()){
			String k = keys.next();
			try {
				/*logger.debug("keysModified: Key:{}, Value:{}, Type: {}", 
						new Object[] {
							k, 
							storeClient.get(k).getValue().toString(), 
							type.name()}
						);*/
				if(type.name().equals("REMOTE")){
					String swIds = storeClient.get(k).getValue();
					logger.debug("REMOTE: key:{}, Value:{}", k, swIds);
				}
			} catch (SyncException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub
		try {
			this.storeClient.put(controllerId, getActiveSwitches());
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub
		try {
			this.storeClient.put(controllerId, getActiveSwitches());
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
		try {
			this.storeClient.put(controllerId, getActiveSwitches());
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
		try {
			this.storeClient.put(controllerId, getActiveSwitches());
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public String getActiveSwitches(){
		String activeSwitches = "";
		Iterator<DatapathId> itDpid = switchService.getAllSwitchDpids().iterator();
		while (itDpid.hasNext()) {
			DatapathId dpid = itDpid.next();
			if(switchService.getActiveSwitch(dpid).isActive()){
				activeSwitches += dpid;
				if(itDpid.hasNext())
					activeSwitches += ",";	
			}
		}
		return activeSwitches;
	}

	public void setSwitchRole(OFControllerRole role, String swId){

		IOFSwitch sw = switchService.getActiveSwitch(DatapathId.of(swId));
		OFRoleReply reply=null;
		UtilDurable utilDurable = new UtilDurable();
		reply = utilDurable.setSwitchRole(sw, role);
		
		if(reply!=null){
			logger.info("DEFINED {} as {}, reply.getRole:{}!", 
					new Object[]{
					sw.getId(), 
					role,
					reply.getRole()});
		}
		else
			logger.info("Reply NULL!");
	}
	
}
