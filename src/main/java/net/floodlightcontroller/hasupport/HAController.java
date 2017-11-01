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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.hasupport.linkdiscovery.LDHAWorker;
import net.floodlightcontroller.hasupport.topology.TopoHAWorker;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * The HAController
 *
 * This class starts two threads, a high level thread called controller logic
 * which is responsible for managing the election process and the actual leader
 * election thread. Based on the outcome of the election, controller logic can
 * be used to assign tasks from to the followers and also specify roles for the
 * leader. Election priorities can be set, meaning, a predefined list of the
 * order in which the nodes get selected in the election can be supplied using
 * the setElectionPriorities function.
 *
 * Individual *HAWorker classes register to the workers HashMap to hold their
 * current objects, in order to facilitate calling their publish and subscribe
 * hooks dynamically.
 *
 * Possible improvements: a. Implement a better scheduling algorithm, and
 * schedule the election and controller logic threads to engineer the scheduling
 * more effectively.
 *
 * @author Bhargav Srinivasan, Om Kale
 */

public class HAController implements IFloodlightModule, IHAControllerService, IStoreListener<String>, IHAWorkerService {

	private static final Logger logger = LoggerFactory.getLogger(HAController.class);
	protected static IHAWorkerService haworker;
	protected static ILinkDiscoveryService linkserv;
	protected static ITopologyService toposerv;
	protected static IFloodlightProviderService floodlightProvider;
	protected static ISyncService syncService;
	protected static IStoreClient<String, String> storeLD;
	protected static IStoreClient<String, String> storeTopo;
	private static String controllerID;
	protected static LDHAWorker ldhaworker;
	protected static TopoHAWorker topohaworker;

	private static Map<String, String> config = new HashMap<>();
	private static Map<String, IHAWorker> workers = new HashMap<>();
	private final List<Integer> priorities = new ArrayList<>();
	private final String none = new String("none");
	private AsyncElection ael;
	private ControllerLogic cLogic;

	@Override
	public String getLeaderNonBlocking() {
		return ael.getLeader().toString();
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
		l.add(IStorageSourceService.class);
		l.add(IFloodlightProviderService.class);
		l.add(ISyncService.class);
		return l;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
		l.add(IHAWorkerService.class);
		l.add(IHAControllerService.class);
		return l;
	}

	/**
	 * Gets the specified HAWorker object.
	 */

	@Override
	public IHAWorker getService(String serviceName) {
		synchronized (workers) {
			return workers.get(serviceName);
		}
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<>();
		/**
		 * We are the class that implements the service
		 */
		m.put(IHAWorkerService.class, this);
		m.put(IHAControllerService.class, this);
		return m;
	}

	/**
	 * Returns the keys of the workers hashmap, which holds the objects
	 * corresponding to the registered HAWorker classes.
	 *
	 */

	@Override
	public Set<String> getWorkerKeys() {
		synchronized (workers) {
			return workers.keySet();
		}
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		haworker = this;
		config = context.getConfigParams(this);
		linkserv = context.getServiceImpl(ILinkDiscoveryService.class);
		toposerv = context.getServiceImpl(ITopologyService.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		syncService = context.getServiceImpl(ISyncService.class);
		controllerID = new String("C" + floodlightProvider.getControllerId());
		logger.info("Configuration parameters: {} {} ", new Object[] { config.toString(), config.get("nodeid") });
	}

	@Override
	public void keysModified(Iterator<String> keys, org.sdnplatform.sync.IStoreListener.UpdateType type) {
	}

	@Override
	public String pollForLeader() {
		try {
			Integer timeout = new Integer(60000);
			Long start = System.nanoTime();
			Long duration = new Long(0);

			while (duration <= timeout) {
				duration = (long) ((System.nanoTime() - start) / 1000000.000);
				if (!ael.getLeader().toString().equals(none)) {
					break;
				}
				TimeUnit.MILLISECONDS.sleep(25);
			}
		} catch (InterruptedException e) {
			logger.info("pollForLeader was interrupted!");
			e.printStackTrace();
		} catch (Exception e) {
			logger.info("ERROR: [HAController] pollForLeader");
			e.printStackTrace();
		}

		return ael.getLeader().toString();
	}

	@Override
	public String recv(String from) {
		return AsyncElection.getNetwork().recv(from);
	}

	/**
	 * Allows the HAWorker classes to register their class objects into the
	 * hashmap, so that the HAController can use them.
	 *
	 */

	@Override
	public void registerService(String serviceName, IHAWorker haw) {
		synchronized (workers) {
			workers.putIfAbsent(serviceName, haw);
		}

	}

	@Override
	public boolean send(String to, String msg) {
		return AsyncElection.getNetwork().send(to, msg);
	}

	@Override
	public void setElectionPriorities(ArrayList<Integer> priorities) {
		ael.setElectionPriorities(priorities);
		return;
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

		logger.info("LDHAWorker is starting...");
		try {
			HAController.syncService.registerStore("LDUpdates", Scope.GLOBAL);

			HAController.storeLD = HAController.syncService.getStoreClient("LDUpdates", String.class, String.class);
			HAController.storeLD.addStoreListener(this);
		} catch (SyncException e) {
			throw new FloodlightModuleException("Error while setting up sync service", e);
		}
		HAController.ldhaworker = new LDHAWorker(HAController.storeLD, controllerID);
		linkserv.addListener(HAController.ldhaworker);
		haworker.registerService("LDHAWorker", HAController.ldhaworker);

		logger.info("TopoHAWorker is starting...");
		try {
			HAController.syncService.registerStore("TopoUpdates", Scope.GLOBAL);

			HAController.storeTopo = HAController.syncService.getStoreClient("TopoUpdates", String.class, String.class);
			HAController.storeTopo.addStoreListener(this);
		} catch (SyncException e) {
			throw new FloodlightModuleException("Error while setting up sync service", e);
		}
		HAController.topohaworker = new TopoHAWorker(HAController.storeTopo, controllerID);
		toposerv.addListener(HAController.topohaworker);
		haworker.registerService("TopoHAWorker", HAController.topohaworker);

		/**
		 * Read config file and start the Election class with the right params.
		 */
		ScheduledExecutorService sesController = Executors.newScheduledThreadPool(10);
		ael = new AsyncElection(config.get("serverPort"), config.get("nodeid"), haworker);
		ael.setElectionPriorities((ArrayList<Integer>) priorities);
		cLogic = new ControllerLogic(ael, config.get("nodeid"));
		try {
			/**
			 * The logic behind, the timing: Election scheduled at 60 ms which
			 * schedules Network Node at 30 ms Controller Logic polls for leader
			 * every 25 ms.
			 */
			sesController.scheduleAtFixedRate(ael, 0, 60000, TimeUnit.MICROSECONDS);
			sesController.scheduleAtFixedRate(cLogic, 20000, 25000, TimeUnit.MICROSECONDS);

		} catch (Exception e) {
			sesController.shutdownNow();
			logger.info("[Election] Was interrrupted! " + e.toString());
			e.printStackTrace();
		}

		logger.info("HAController is starting...");

		return;

	}

}
