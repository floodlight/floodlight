package net.floodlightcontroller.simpleft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
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

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
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

public class FT implements 
IOFMessageListener, 
IFloodlightModule,
IStoreListener<String>
{

	private ISyncService syncService;
	private IStoreClient<String, Long> storeClient;
	//private IFloodlightProviderService floodlightProvider;
	
	protected static Logger logger = LoggerFactory.getLogger(FT.class);

	protected static IThreadPoolService threadPoolService;
	protected static SingletonTask testTask;
	
	protected IOFSwitchManager sm;
		
	private String controllerId;
	
	//private HashMap<String, Long> cluster;
	//private TimedCache<String> tc = new TimedCache<>(10, 20000);
	

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
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
		this.syncService = context.getServiceImpl(ISyncService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		//floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		

		Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		controllerId = configParams.get("controllerId");
			
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {


		try {
			this.syncService.registerStore("FT", Scope.GLOBAL);
			this.storeClient = this.syncService
					.getStoreClient("FT",
							String.class,
							Long.class);
			this.storeClient.put(controllerId, new Long(0L));
			this.storeClient.addStoreListener(this);
		} catch (SyncException e) {
			throw new FloodlightModuleException("Error while setting up sync service", e);
		}


		/*String obj;
		try {
			obj = this.storeClient.getValue("FT").toString();
			logger.debug("Sync retrieve: {}", obj);
			this.storeClient.put("FT", new String("INIT"));
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
				
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		testTask = new SingletonTask(ses, new Runnable() {
			int counter=0;
			Long tsOld=0L;
			@Override
			public void run() { 
				Random r = new Random();
				try {
					//storeClient.put(controllerId, new String("vl:"+r.nextInt(1000)));
					storeClient.put(controllerId, new Long(System.nanoTime()));
				} catch (SyncException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				testTask.reschedule(4, TimeUnit.SECONDS);
			}
		});
		testTask.reschedule(3, TimeUnit.SECONDS);

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
					Long ts = storeClient.get(k).getValue();
					logger.debug("REMOTE: k:{}, ts:{}", k, ts);
					//cluster.put(k, ts);
				}
				
				//logger.debug("localNodeID: {}",this.syncService.getLocalNodeId());
			} catch (SyncException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}



}
