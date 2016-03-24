package net.floodlightcontroller.simpleft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
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
	private IStoreClient<String, Object> storeClient;
	
	protected FloodlightModuleContext[] moduleContexts;
	protected SyncManager[] syncManagers;
	protected final static ObjectMapper mapper = new ObjectMapper();
	protected String nodeString;
	ArrayList<Node> nodes;

	ThreadPool tp;



	protected static Logger logger = LoggerFactory.getLogger(FT.class);

	protected static IThreadPoolService threadPoolService;
	protected static SingletonTask testTask;	

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
		l.add(IFloodlightProviderService.class);
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
			
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {


		try {
			this.syncService.registerStore("NIB", Scope.GLOBAL);
			this.storeClient = this.syncService
					.getStoreClient("NIB",
							String.class,
							Object.class);
			this.storeClient.put("NIB", new String("Algo aqui..."));
			this.storeClient.addStoreListener(this);
		} catch (SyncException e) {
			throw new FloodlightModuleException("Error while setting up sync service", e);
		}


		//logger.debug("Iniciado SYNC, testing now:");
		String obj;
		try {
			obj = this.storeClient.getValue("NIB").toString();
			logger.debug("Sync retrieve: {}", obj);
			this.storeClient.put("NIB", new String("B"));
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
				
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		testTask = new SingletonTask(ses, new Runnable() {
			int counter=0;
			@Override
			public void run() { 
				Random r = new Random();
				
				try {
					storeClient.put("NIB", new String("vl:"+r.nextInt(1000)));
					//storeClient.put("NIB", new String(""+counter++));
					
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
				logger.debug("keysModified: Key:{}, Value:{}, Type: {}", 
						new Object[] {k, storeClient.get(k).getValue().toString(), type.name()}
						);
				//logger.debug("localNodeID: {}",this.syncService.getLocalNodeId());
			} catch (SyncException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

}
