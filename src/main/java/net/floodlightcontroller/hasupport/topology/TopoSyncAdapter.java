package net.floodlightcontroller.hasupport.topology;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.rpc.IRPCListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.hasupport.ISyncAdapter;
import net.floodlightcontroller.storage.IStorageSourceService;

/**
 * This class gets the updates from the Filter Queue
 * and puts them into the SyncDB.
 * The primary key fields are MD5 hashed and the md5hashes are
 * stored under the controller ID which published them. Now
 * each controller can exchange only the md5hashes and stay up 
 * to date, and sync the actual update if needed.
 * 
 * @author Om Kale
 *
 */


public class TopoSyncAdapter implements ISyncAdapter, IFloodlightModule, IStoreListener<String>, IRPCListener {

	protected static Logger logger = LoggerFactory.getLogger(TopoSyncAdapter.class);
	protected static ISyncService syncService;
	protected static IStoreClient<String, String> storeTopo;
	protected static IFloodlightProviderService floodlightProvider;
	
	public static String controllerId;
	private final String none = new String("none");
	private final String[] highfields = new String[]{"operation",  "latency", "timestamp"};
	private static final TopoFilterQueue myTopoFilterQueue = new TopoFilterQueue();
	
	@Override
	public void packJSON(List<String> newUpdates) {
		
		ObjectMapper myMapper = new ObjectMapper();
		TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String,String>>() {};
		HashMap<String, String> newUpdateMap = new HashMap<String, String>();
		HashMap<String, String> updateMap = new HashMap<String, String>();
		String cmd5Hash = new String();
		TopoUtils topohautils = new TopoUtils();
		
		if ( newUpdates.isEmpty() ) {
			return;
		}

		//TODO: Two cases for when newUpdate cmd5 = oldUpdate cmd5 and when not.
		
			for (String up: newUpdates) {
				try {
				
				newUpdateMap = myMapper.readValue(up.toString(), typeRef);
				cmd5Hash = topohautils.getCMD5Hash(up,newUpdateMap);
				
				//Make the high freq fields as lists.
				String operation = newUpdateMap.get(highfields[0]);
				String latency = newUpdateMap.get(highfields[1]);
				//Add timestamp field.
				
				Long ts = new Long(Instant.now().getEpochSecond());
				Long nano = new Long(Instant.now().getNano());
				
				newUpdateMap.put(highfields[0], operation);
				newUpdateMap.put(highfields[1], latency);
				newUpdateMap.put(highfields[2], ts.toString()+nano.toString());
				
				// Try to get previous update:
	        	String oldUpdates = storeTopo.getValue(cmd5Hash.toString(), none);
				
	        	if (! oldUpdates.equals(none) ) {
	        		
	        		if(oldUpdates.isEmpty()){
	        			continue;
	        		}
		        			
	        		logger.debug("+++++++++++++ Retrieving old update from Topo DB: Key:{}, Value:{} ", 
	                    new Object[] {
	                            cmd5Hash.toString(), 
	                            oldUpdates.toString()
	                        }
	                 );
				
					//parse the Json String into a Map, then query the entries.
					updateMap = myMapper.readValue(oldUpdates.toString(), typeRef);		
					
				    String oldOp = updateMap.get(highfields[0]);
				    logger.debug("++++OLD OP: {}", new Object[] {oldOp});
				    String opList = topohautils.appendUpdate(oldOp, newUpdateMap.get(highfields[0]) );
					updateMap.put(highfields[0], opList); //update high freq fields
					
					String oldLatency = updateMap.get(highfields[1]);
				    logger.debug("++++OLD LATENCY: {}", new Object[] {oldLatency});
				    String latList = topohautils.appendUpdate(oldLatency, newUpdateMap.get(highfields[1]));
					updateMap.put(highfields[1], latList); //update high freq fields
					
					String oldTimestamp = updateMap.get(highfields[2]);
					logger.debug("++++OLD TS: {}", new Object[] {oldTimestamp});
					Long ts2 = new Long(Instant.now().getEpochSecond());
					Long nano2 = new Long(Instant.now().getNano());
					String tmList = topohautils.appendUpdate(oldTimestamp, ts2.toString()+nano2.toString());
					updateMap.put(highfields[2], tmList);
					
					TopoSyncAdapter.storeTopo.put(cmd5Hash.toString(), myMapper.writeValueAsString(updateMap));
					
	        	} else {
	        		
	        		try{
	        				
	        			TopoSyncAdapter.storeTopo.put(cmd5Hash.toString(), myMapper.writeValueAsString(newUpdateMap));
	        			
	        			String collatedcmd5 = TopoSyncAdapter.storeTopo.getValue(controllerId.toString(), none);
	        			
	        			if ( collatedcmd5.equals(none) ) {
	        				collatedcmd5 = cmd5Hash;
	        				logger.debug("Collated CMD5: {} ", new Object [] {collatedcmd5.toString()});
	        			} else {
	        				logger.debug("================ Append update to HashMap ================");
	        				collatedcmd5 = topohautils.appendUpdate(collatedcmd5, cmd5Hash);
	        			}
	        			
	        			TopoSyncAdapter.storeTopo.put(controllerId, collatedcmd5);
	        			
	        		} catch (SyncException se) {
	        			// TODO Auto-generated catch block
	        			logger.debug("[TopoSync] Exception: sync packJSON!");
	        			se.printStackTrace();
	        		} catch (Exception e) {
	        			logger.debug("[TopoSync] Exception: packJSON!");
	        			e.printStackTrace();
	        		}
	        	}
		
			} catch (SyncException se) {
    			// TODO Auto-generated catch block
    			logger.debug("[TopoSync] Exception: sync packJSON!");
    			se.printStackTrace();
    		} catch (Exception e) {
    			logger.debug("[TopoSync] Exception: packJSON!");
    			e.printStackTrace();
	        }
		}
		
	}
	
	public void unpackJSON(String controllerID) {
		// 1. Get all cmd5 hashes for the particular controller ID.
		try {
			String collatedcmd5 = TopoSyncAdapter.storeTopo.getValue(controllerID, none);
			
			if (! collatedcmd5.equals(none) ) {
				
				if (collatedcmd5.endsWith(", ")) {
					collatedcmd5 = collatedcmd5.substring(0, collatedcmd5.length()-2);
				}
				
				logger.debug("[Unpack] Collated CMD5: {}", new Object[] {collatedcmd5.toString()});
				
				String[] cmd5hashes = collatedcmd5.split(", ");
				for (String cmd5: cmd5hashes) {
					String update = TopoSyncAdapter.storeTopo.getValue(cmd5, none);
					if(! update.equals(none) ) {
						logger.debug("[Unpack]: {}", new Object [] {update.toString()});
						myTopoFilterQueue.enqueueReverse(update);
					}
				}
			}
			
			return;
			
		} catch (SyncException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	

	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStorageSourceService.class);
        l.add(IFloodlightProviderService.class);
        l.add(ISyncService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		logger = LoggerFactory.getLogger(TopoSyncAdapter.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		syncService = context.getServiceImpl(ISyncService.class);
		controllerId = new String("C" + floodlightProvider.getControllerId());
        logger.info("Node Id: {}", new Object[] {controllerId});
		
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		syncService.addRPCListener(this);
		try {
            TopoSyncAdapter.syncService.registerStore("TopoUpdates", Scope.GLOBAL);
            
            TopoSyncAdapter.storeTopo = TopoSyncAdapter.syncService
            		.getStoreClient("TopoUpdates", 
            				String.class, 
            				String.class);
            TopoSyncAdapter.storeTopo.addStoreListener(this);
        } catch (SyncException e) {
            throw new FloodlightModuleException("Error while setting up sync service", e);
        }
	}

	@Override
	public void keysModified(Iterator<String> keys, org.sdnplatform.sync.IStoreListener.UpdateType type) {
		// TODO Auto-generated method stub
		while(keys.hasNext()){
	        String k = keys.next();
	        try {
	        	String val = storeTopo.get(k).getValue();
				logger.debug("+++++++++++++ Retrieving value from Topo DB: Key:{}, Value:{}, Type: {}", 
	                    new Object[] {
	                            k.toString(), 
	                            val.toString(), 
	                            type.name()
	                        }
	                    );
	        } catch (SyncException e) {
	            e.printStackTrace();
	        }
	    }

		
	}

	@Override
	public void disconnectedNode(Short nodeId) {
		// TODO Auto-generated method stub
		logger.info("Node disconnected: "+nodeId.toString());
	}

	@Override
	public void connectedNode(Short nodeId) {
		// TODO Auto-generated method stub
		logger.info("Node connected: "+nodeId.toString());
	}

}
