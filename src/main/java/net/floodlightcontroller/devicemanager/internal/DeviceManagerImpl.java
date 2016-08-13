/**
 *    Copyright 2011,2012 Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
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

package net.floodlightcontroller.devicemanager.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassListener;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.DeviceSyncRepresentation.SyncEntity;
import net.floodlightcontroller.devicemanager.web.DeviceRoutable;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.DHCPOption;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.IPv6;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.DHCP.DHCPOptionCode;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.MultiIterator;
import static net.floodlightcontroller.devicemanager.internal.
DeviceManagerImpl.DeviceUpdate.Change.*;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeviceManager creates Devices based upon MAC addresses seen in the network.
 * It tracks any network addresses mapped to the Device, and its location
 * within the network.
 * @author readams
 */
public class DeviceManagerImpl implements IDeviceService, IOFMessageListener, ITopologyListener, IFloodlightModule, IEntityClassListener, IInfoProvider {
	protected static Logger logger = LoggerFactory.getLogger(DeviceManagerImpl.class);
	protected IFloodlightProviderService floodlightProvider;
	protected ITopologyService topology;
	protected IStorageSourceService storageSource;
	protected IRestApiService restApi;
	protected IThreadPoolService threadPool;
	protected IDebugCounterService debugCounters;
	private ISyncService syncService;
	private IStoreClient<String, DeviceSyncRepresentation> storeClient;
	private DeviceSyncManager deviceSyncManager;

	/**
	 * Debug Counters
	 */
	public static final String MODULE_NAME = "devicemanager";
	public static final String PACKAGE = DeviceManagerImpl.class.getPackage().getName();
	public IDebugCounter cntIncoming;
	public IDebugCounter cntReconcileRequest;
	public IDebugCounter cntReconcileNoSource;
	public IDebugCounter cntReconcileNoDest;
	public IDebugCounter cntInvalidSource;
	public IDebugCounter cntInvalidDest;
	public IDebugCounter cntNoSource;
	public IDebugCounter cntNoDest;
	public IDebugCounter cntDhcpClientNameSnooped;
	public IDebugCounter cntDeviceOnInternalPortNotLearned;
	public IDebugCounter cntPacketNotAllowed;
	public IDebugCounter cntNewDevice;
	public IDebugCounter cntPacketOnInternalPortForKnownDevice;
	public IDebugCounter cntNewEntity;
	public IDebugCounter cntDeviceChanged;
	public IDebugCounter cntDeviceMoved;
	public IDebugCounter cntCleanupEntitiesRuns;
	public IDebugCounter cntEntityRemovedTimeout;
	public IDebugCounter cntDeviceDeleted;
	public IDebugCounter cntDeviceReclassifyDelete;
	public IDebugCounter cntDeviceStrored;
	public IDebugCounter cntDeviceStoreThrottled;
	public IDebugCounter cntDeviceRemovedFromStore;
	public IDebugCounter cntSyncException;
	public IDebugCounter cntDevicesFromStore;
	public IDebugCounter cntConsolidateStoreRuns;
	public IDebugCounter cntConsolidateStoreDevicesRemoved;
	public IDebugCounter cntTransitionToMaster;

	private boolean isMaster = false;

	static final String DEVICE_SYNC_STORE_NAME =
			DeviceManagerImpl.class.getCanonicalName() + ".stateStore";

	/**
	 * Time interval between writes of entries for the same device to
	 * the sync store.
	 */
	static final int DEFAULT_SYNC_STORE_WRITE_INTERVAL_MS = 5*60*1000; // 5 min
	private int syncStoreWriteIntervalMs = DEFAULT_SYNC_STORE_WRITE_INTERVAL_MS;

	/**
	 * Time after SLAVE->MASTER until we run the consolidate store
	 * code.
	 */
	static final int DEFAULT_INITIAL_SYNC_STORE_CONSOLIDATE_MS =
			15*1000; // 15 sec
	private int initialSyncStoreConsolidateMs =
			DEFAULT_INITIAL_SYNC_STORE_CONSOLIDATE_MS;

	/**
	 * Time interval between consolidate store runs.
	 */
	static final int DEFAULT_SYNC_STORE_CONSOLIDATE_INTERVAL_MS =
			75*60*1000; // 75 min
	private final int syncStoreConsolidateIntervalMs =
			DEFAULT_SYNC_STORE_CONSOLIDATE_INTERVAL_MS;

	/**
	 * Time in milliseconds before entities will expire
	 */
	protected static final int ENTITY_TIMEOUT = 60*60*1000;

	/**
	 * Time in seconds between cleaning up old entities/devices
	 */
	protected static final int ENTITY_CLEANUP_INTERVAL = 60*60;

	/**
	 * This is the master device map that maps device IDs to {@link Device}
	 * objects.
	 */
	protected ConcurrentHashMap<Long, Device> deviceMap;

	/**
	 * Counter used to generate device keys
	 */
	protected AtomicLong deviceKeyCounter = new AtomicLong(0);

	/**
	 * This is the primary entity index that contains all entities
	 */
	protected DeviceUniqueIndex primaryIndex;

	/**
	 * This stores secondary indices over the fields in the devices
	 */
	protected Map<EnumSet<DeviceField>, DeviceIndex> secondaryIndexMap;

	/**
	 * This map contains state for each of the {@ref IEntityClass}
	 * that exist
	 */
	protected ConcurrentHashMap<String, ClassState> classStateMap;

	/**
	 * This is the list of indices we want on a per-class basis
	 */
	protected Set<EnumSet<DeviceField>> perClassIndices;

	/**
	 * The entity classifier currently in use
	 */
	protected IEntityClassifierService entityClassifier;
	
	/**
	 * Used to cache state about specific entity classes
	 */
	protected class ClassState {

		/**
		 * The class index
		 */
		protected DeviceUniqueIndex classIndex;

		/**
		 * This stores secondary indices over the fields in the device for the
		 * class
		 */
		protected Map<EnumSet<DeviceField>, DeviceIndex> secondaryIndexMap;

		/**
		 * Allocate a new {@link ClassState} object for the class
		 * @param clazz the class to use for the state
		 */
		public ClassState(IEntityClass clazz) {
			EnumSet<DeviceField> keyFields = clazz.getKeyFields();
			EnumSet<DeviceField> primaryKeyFields =
					entityClassifier.getKeyFields();
			boolean keyFieldsMatchPrimary =
					primaryKeyFields.equals(keyFields);

			if (!keyFieldsMatchPrimary)
				classIndex = new DeviceUniqueIndex(keyFields);

			secondaryIndexMap =
					new HashMap<EnumSet<DeviceField>, DeviceIndex>();
			for (EnumSet<DeviceField> fields : perClassIndices) {
				secondaryIndexMap.put(fields,
						new DeviceMultiIndex(fields));
			}
		}
	}

	/**
	 * Device manager event listeners
	 * reclassifyDeviceListeners are notified first before reconcileDeviceListeners.
	 * This is to make sure devices are correctly reclassified before reconciliation.
	 */
	protected ListenerDispatcher<String,IDeviceListener> deviceListeners;

	/**
	 * A device update event to be dispatched
	 */
	protected static class DeviceUpdate {
		public enum Change {
			ADD, DELETE, CHANGE;
		}

		/**
		 * The affected device
		 */
		protected Device device;

		/**
		 * The change that was made
		 */
		protected Change change;

		/**
		 * If not added, then this is the list of fields changed
		 */
		protected EnumSet<DeviceField> fieldsChanged;

		public DeviceUpdate(Device device, Change change,
				EnumSet<DeviceField> fieldsChanged) {
			super();
			this.device = device;
			this.change = change;
			this.fieldsChanged = fieldsChanged;
		}

		@Override
		public String toString() {
			String devIdStr = device.getEntityClass().getName() + "::" +
					device.getMACAddressString();
			return "DeviceUpdate [device=" + devIdStr + ", change=" + change
					+ ", fieldsChanged=" + fieldsChanged + "]";
		}

	}

	/**
	 * AttachmentPointComparator
	 *
	 * Compares two attachment points and returns the latest one.
	 * It is assumed that the two attachment points are in the same
	 * L2 domain.
	 *
	 * @author srini
	 */
	protected class AttachmentPointComparator
	implements Comparator<AttachmentPoint> {
		public AttachmentPointComparator() {
			super();
		}

		@Override
		public int compare(AttachmentPoint oldAP, AttachmentPoint newAP) {
			//First compare based on L2 domain ID;

			DatapathId oldSw = oldAP.getSw();
			OFPort oldPort = oldAP.getPort();
			DatapathId oldDomain = topology.getClusterId(oldSw);
			boolean oldBD = topology.isBroadcastPort(oldSw, oldPort);

			DatapathId newSw = newAP.getSw();
			OFPort newPort = newAP.getPort();
			DatapathId newDomain = topology.getClusterId(newSw);
			boolean newBD = topology.isBroadcastPort(newSw, newPort);

			if (oldDomain.getLong() < newDomain.getLong()) return -1;
			else if (oldDomain.getLong() > newDomain.getLong()) return 1;

			// Give preference to LOCAL always
			if (oldPort != OFPort.LOCAL &&
					newPort == OFPort.LOCAL) {
				return -1;
			} else if (oldPort == OFPort.LOCAL &&
					newPort != OFPort.LOCAL) {
				return 1;
			}

			// We expect that the last seen of the new AP is higher than
			// old AP, if it is not, just reverse and send the negative
			// of the result.
			if (oldAP.getLastSeen().after(newAP.getLastSeen())) //TODO should this be lastSeen? @Ryan did change this from activeSince
				return -compare(newAP, oldAP);

			long activeOffset = 0;
			if (!topology.isConsistent(oldSw, oldPort, newSw, newPort)) {
				if (!newBD && oldBD) {
					return -1;
				}
				if (newBD && oldBD) {
					activeOffset = AttachmentPoint.EXTERNAL_TO_EXTERNAL_TIMEOUT;
				}
				else if (newBD && !oldBD){
					activeOffset = AttachmentPoint.OPENFLOW_TO_EXTERNAL_TIMEOUT;
				}

			} else {
				// The attachment point is consistent.
				activeOffset = AttachmentPoint.CONSISTENT_TIMEOUT;
			}


			if ((newAP.getActiveSince().getTime() > oldAP.getLastSeen().getTime() + activeOffset) ||
					(newAP.getLastSeen().getTime() > oldAP.getLastSeen().getTime() +
							AttachmentPoint.INACTIVITY_INTERVAL)) {
				return -1;
			}
			return 1;
		}
	}
	/**
	 * Comparator for sorting by cluster ID
	 */
	public AttachmentPointComparator apComparator;

	/**
	 * Switch ports where attachment points shouldn't be learned
	 */
	private Set<SwitchPort> suppressAPs;

	/**
	 * Periodic task to clean up expired entities
	 */
	public SingletonTask entityCleanupTask;


	/**
	 * Periodic task to consolidate entries in the store. I.e., delete
	 * entries in the store that are not known to DeviceManager
	 */
	private SingletonTask storeConsolidateTask;

	/**
	 * Listens for HA notifications
	 */
	protected HAListenerDelegate haListenerDelegate;


	// *********************
	// IDeviceManagerService
	// *********************

	@Override
	public IDevice getDevice(Long deviceKey) {
		return deviceMap.get(deviceKey);
	}

	@Override
	public IDevice findDevice(@Nonnull MacAddress macAddress, VlanVid vlan,
			@Nonnull IPv4Address ipv4Address, @Nonnull IPv6Address ipv6Address,
			@Nonnull DatapathId switchDPID, @Nonnull OFPort switchPort)
					throws IllegalArgumentException {
		if (macAddress == null) {
    		throw new IllegalArgumentException("MAC address cannot be null. Try MacAddress.NONE if intention is 'no MAC'");
    	}
    	if (ipv4Address == null) {
    		throw new IllegalArgumentException("IPv4 address cannot be null. Try IPv4Address.NONE if intention is 'no IPv4'");
    	}
    	if (ipv6Address == null) {
    		throw new IllegalArgumentException("IPv6 address cannot be null. Try IPv6Address.NONE if intention is 'no IPv6'");
    	}
    	if (vlan == null) {
    		throw new IllegalArgumentException("VLAN cannot be null. Try VlanVid.ZERO if intention is 'no VLAN / untagged'");
    	}
    	if (switchDPID == null) {
    		throw new IllegalArgumentException("Switch DPID cannot be null. Try DatapathId.NONE if intention is 'no DPID'");
    	}
    	if (switchPort == null) {
    		throw new IllegalArgumentException("Switch port cannot be null. Try OFPort.ZERO if intention is 'no port'");
    	}
		
		Entity e = new Entity(macAddress, vlan, 
				ipv4Address, ipv6Address, 
				switchDPID, switchPort, Entity.NO_DATE);
		
		/*
		 * allKeyFieldsPresent() will check if the entity key fields (e.g. MAC and VLAN)
		 * have non-"zero" values i.e. are not set to e.g. MacAddress.NONE and VlanVid.ZERO
		 */
		if (!allKeyFieldsPresent(e, entityClassifier.getKeyFields())) {
			throw new IllegalArgumentException("Not all key fields specified."
					+ " Required fields: " + entityClassifier.getKeyFields());
		}
		return findDeviceByEntity(e);
	}

	@Override
	public IDevice findClassDevice(@Nonnull IEntityClass entityClass, @Nonnull MacAddress macAddress,
			@Nonnull VlanVid vlan, @Nonnull IPv4Address ipv4Address, @Nonnull IPv6Address ipv6Address)
					throws IllegalArgumentException {
		if (entityClass == null) {
    		throw new IllegalArgumentException("Entity class cannot be null.");
    	}
		if (macAddress == null) {
    		throw new IllegalArgumentException("MAC address cannot be null. Try MacAddress.NONE if intention is 'no MAC'");
    	}
    	if (ipv4Address == null) {
    		throw new IllegalArgumentException("IPv4 address cannot be null. Try IPv4Address.NONE if intention is 'no IPv4'");
    	}
    	if (ipv6Address == null) {
    		throw new IllegalArgumentException("IPv6 address cannot be null. Try IPv6Address.NONE if intention is 'no IPv6'");
    	}
    	if (vlan == null) {
    		throw new IllegalArgumentException("VLAN cannot be null. Try VlanVid.ZERO if intention is 'no VLAN / untagged'");
    	}
    	
		Entity e = new Entity(macAddress, vlan, ipv4Address, ipv6Address, DatapathId.NONE, OFPort.ZERO, Entity.NO_DATE);
		if (!allKeyFieldsPresent(e, entityClass.getKeyFields())) {
			throw new IllegalArgumentException("Not all key fields and/or "
					+ " no source device specified. Required fields: " +
					entityClassifier.getKeyFields());
		}
		return findDestByEntity(entityClass, e);
	}

	@Override
	public Collection<? extends IDevice> getAllDevices() {
		return Collections.unmodifiableCollection(deviceMap.values());
	}

	@Override
	public void addIndex(boolean perClass,
			EnumSet<DeviceField> keyFields) {
		if (perClass) {
			perClassIndices.add(keyFields);
		} else {
			secondaryIndexMap.put(keyFields,
					new DeviceMultiIndex(keyFields));
		}
	}

	@Override
	public Iterator<? extends IDevice> queryDevices(@Nonnull MacAddress macAddress,
			VlanVid vlan,
			@Nonnull IPv4Address ipv4Address,
			@Nonnull IPv6Address ipv6Address,
			@Nonnull DatapathId switchDPID,
			@Nonnull OFPort switchPort) {
		if (macAddress == null) {
    		throw new IllegalArgumentException("MAC address cannot be null. Try MacAddress.NONE if intention is 'no MAC'");
    	}
    	if (ipv4Address == null) {
    		throw new IllegalArgumentException("IPv4 address cannot be null. Try IPv4Address.NONE if intention is 'no IPv4'");
    	}
    	if (ipv6Address == null) {
    		throw new IllegalArgumentException("IPv6 address cannot be null. Try IPv6Address.NONE if intention is 'no IPv6'");
    	}
    	/* VLAN can be null in this case, which means 'don't care' */
    	if (switchDPID == null) {
    		throw new IllegalArgumentException("Switch DPID cannot be null. Try DatapathId.NONE if intention is 'no DPID'");
    	}
    	if (switchPort == null) {
    		throw new IllegalArgumentException("Switch port cannot be null. Try OFPort.ZERO if intention is 'no port'");
    	}
		
		DeviceIndex index = null;
		if (secondaryIndexMap.size() > 0) {
			EnumSet<DeviceField> keys =
					getEntityKeys(macAddress, vlan, ipv4Address, ipv6Address,
							switchDPID, switchPort);
			index = secondaryIndexMap.get(keys);
		}

		Iterator<Device> deviceIterator = null;
		if (index == null) {
			// Do a full table scan
			deviceIterator = deviceMap.values().iterator();
		} else {
			// index lookup
			Entity entity = new Entity(macAddress,
					vlan,
					ipv4Address,
					ipv6Address,
					switchDPID,
					switchPort,
					Entity.NO_DATE);
			deviceIterator =
					new DeviceIndexInterator(this, index.queryByEntity(entity));
		}

		DeviceIterator di =
				new DeviceIterator(deviceIterator,
						null,
						macAddress,
						vlan,
						ipv4Address,
						ipv6Address,
						switchDPID,
						switchPort);
		return di;
	}

	@Override
	public Iterator<? extends IDevice> queryClassDevices(@Nonnull IEntityClass entityClass,
			@Nonnull MacAddress macAddress,
			@Nonnull VlanVid vlan,
			@Nonnull IPv4Address ipv4Address,
			@Nonnull IPv6Address ipv6Address,
			@Nonnull DatapathId switchDPID,
			@Nonnull OFPort switchPort) {
		if (macAddress == null) {
    		throw new IllegalArgumentException("MAC address cannot be null. Try MacAddress.NONE if intention is 'no MAC'");
    	}
    	if (ipv4Address == null) {
    		throw new IllegalArgumentException("IPv4 address cannot be null. Try IPv4Address.NONE if intention is 'no IPv4'");
    	}
    	if (ipv6Address == null) {
    		throw new IllegalArgumentException("IPv6 address cannot be null. Try IPv6Address.NONE if intention is 'no IPv6'");
    	}
    	/* VLAN can be null, which means 'don't care' */
    	if (switchDPID == null) {
    		throw new IllegalArgumentException("Switch DPID cannot be null. Try DatapathId.NONE if intention is 'no DPID'");
    	}
    	if (switchPort == null) {
    		throw new IllegalArgumentException("Switch port cannot be null. Try OFPort.ZERO if intention is 'no port'");
    	}
    	
		ArrayList<Iterator<Device>> iterators =
				new ArrayList<Iterator<Device>>();
		ClassState classState = getClassState(entityClass);

		DeviceIndex index = null;
		if (classState.secondaryIndexMap.size() > 0) {
			EnumSet<DeviceField> keys =
					getEntityKeys(macAddress, vlan, ipv4Address,
							ipv6Address, switchDPID, switchPort);
			index = classState.secondaryIndexMap.get(keys);
		}

		Iterator<Device> iter;
		if (index == null) {
			index = classState.classIndex;
			if (index == null) {
				// scan all devices
				return new DeviceIterator(deviceMap.values().iterator(),
						new IEntityClass[] { entityClass },
						macAddress, vlan, ipv4Address,
						ipv6Address, switchDPID, switchPort);
			} else {
				// scan the entire class
				iter = new DeviceIndexInterator(this, index.getAll());
			}
		} else {
			// index lookup
			Entity entity =
					new Entity(macAddress,
							vlan,
							ipv4Address,
							ipv6Address,
							switchDPID,
							switchPort,
							Entity.NO_DATE);
			iter = new DeviceIndexInterator(this,
					index.queryByEntity(entity));
		}
		iterators.add(iter);

		return new MultiIterator<Device>(iterators.iterator());
	}

	protected Iterator<Device> getDeviceIteratorForQuery(@Nonnull MacAddress macAddress,
			VlanVid vlan,
			@Nonnull IPv4Address ipv4Address,
			@Nonnull IPv6Address ipv6Address,
			@Nonnull DatapathId switchDPID,
			@Nonnull OFPort switchPort) {
		if (macAddress == null) {
    		throw new IllegalArgumentException("MAC address cannot be null. Try MacAddress.NONE if intention is 'no MAC'");
    	}
    	if (ipv4Address == null) {
    		throw new IllegalArgumentException("IPv4 address cannot be null. Try IPv4Address.NONE if intention is 'no IPv4'");
    	}
    	if (ipv6Address == null) {
    		throw new IllegalArgumentException("IPv6 address cannot be null. Try IPv6Address.NONE if intention is 'no IPv6'");
    	}
    	/* VLAN can be null, which means 'don't care' */
    	if (switchDPID == null) {
    		throw new IllegalArgumentException("Switch DPID cannot be null. Try DatapathId.NONE if intention is 'no DPID'");
    	}
    	if (switchPort == null) {
    		throw new IllegalArgumentException("Switch port cannot be null. Try OFPort.ZERO if intention is 'no port'");
    	}
		
		DeviceIndex index = null;
		if (secondaryIndexMap.size() > 0) {
			EnumSet<DeviceField> keys =
					getEntityKeys(macAddress, vlan, ipv4Address,
							ipv6Address, switchDPID, switchPort);
			index = secondaryIndexMap.get(keys);
		}

		Iterator<Device> deviceIterator = null;
		if (index == null) {
			// Do a full table scan
			deviceIterator = deviceMap.values().iterator();
		} else {
			// index lookup
			Entity entity = new Entity(macAddress,
					vlan,
					ipv4Address,
					ipv6Address,
					switchDPID,
					switchPort,
					Entity.NO_DATE);
			deviceIterator =
					new DeviceIndexInterator(this, index.queryByEntity(entity));
		}

		DeviceIterator di =
				new DeviceIterator(deviceIterator,
						null,
						macAddress,
						vlan,
						ipv4Address,
						ipv6Address,
						switchDPID,
						switchPort);
		return di;
	}

	@Override
	public void addListener(IDeviceListener listener) {
		deviceListeners.addListener("device", listener);
		logListeners();
	}

	@Override
	public void addSuppressAPs(DatapathId swId, OFPort port) {
		suppressAPs.add(new SwitchPort(swId, port));
	}

	@Override
	public void removeSuppressAPs(DatapathId swId, OFPort port) {
		suppressAPs.remove(new SwitchPort(swId, port));
	}

	@Override
	public Set<SwitchPort> getSuppressAPs() {
		return Collections.unmodifiableSet(suppressAPs);
	}

	private void logListeners() {
		List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
		if (listeners != null) {
			StringBuffer sb = new StringBuffer();
			sb.append("DeviceListeners: ");
			for (IDeviceListener l : listeners) {
				sb.append(l.getName());
				sb.append(",");
			}
			logger.debug(sb.toString());
		}
	}

	// *************
	// IInfoProvider
	// *************

	@Override
	public Map<String, Object> getInfo(String type) {
		if (!"summary".equals(type))
			return null;

		Map<String, Object> info = new HashMap<String, Object>();
		info.put("# hosts", deviceMap.size());
		return info;
	}

	// ******************
	// IOFMessageListener
	// ******************

	@Override
	public String getName() {
		return MODULE_NAME;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return ((type == OFType.PACKET_IN || type == OFType.FLOW_MOD)
				&& name.equals("topology"));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			cntIncoming.increment();
			return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
		default:
			break;
		}
		return Command.CONTINUE;
	}

	// *****************
	// IFloodlightModule
	// *****************

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IDeviceService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m =
		new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		// We are the class that implements the service
		m.put(IDeviceService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStorageSourceService.class);
		l.add(ITopologyService.class);
		l.add(IRestApiService.class);
		l.add(IThreadPoolService.class);
		l.add(IEntityClassifierService.class);
		l.add(ISyncService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext fmc) throws FloodlightModuleException {
		this.perClassIndices =
				new HashSet<EnumSet<DeviceField>>();
		addIndex(true, EnumSet.of(DeviceField.IPv4));
		addIndex(true, EnumSet.of(DeviceField.IPv6));

		this.deviceListeners = new ListenerDispatcher<String, IDeviceListener>();
		this.suppressAPs = Collections.newSetFromMap(
				new ConcurrentHashMap<SwitchPort, Boolean>());

		this.floodlightProvider =
				fmc.getServiceImpl(IFloodlightProviderService.class);
		this.storageSource =
				fmc.getServiceImpl(IStorageSourceService.class);
		this.topology =
				fmc.getServiceImpl(ITopologyService.class);
		this.restApi = fmc.getServiceImpl(IRestApiService.class);
		this.threadPool = fmc.getServiceImpl(IThreadPoolService.class);
		this.entityClassifier = fmc.getServiceImpl(IEntityClassifierService.class);
		this.debugCounters = fmc.getServiceImpl(IDebugCounterService.class);
		this.syncService = fmc.getServiceImpl(ISyncService.class);
		this.deviceSyncManager = new DeviceSyncManager();
		this.haListenerDelegate = new HAListenerDelegate();
		registerDeviceManagerDebugCounters();
	}

	@Override
	public void startUp(FloodlightModuleContext fmc)
			throws FloodlightModuleException {
		isMaster = (floodlightProvider.getRole() == HARole.ACTIVE);
		primaryIndex = new DeviceUniqueIndex(entityClassifier.getKeyFields());
		secondaryIndexMap = new HashMap<EnumSet<DeviceField>, DeviceIndex>();

		deviceMap = new ConcurrentHashMap<Long, Device>();
		classStateMap =
				new ConcurrentHashMap<String, ClassState>();
		apComparator = new AttachmentPointComparator();

		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addHAListener(this.haListenerDelegate);
		if (topology != null)
			topology.addListener(this);
		entityClassifier.addListener(this);

		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		Runnable ecr = new Runnable() {
			@Override
			public void run() {
				cleanupEntities();
				entityCleanupTask.reschedule(ENTITY_CLEANUP_INTERVAL,
						TimeUnit.SECONDS);
			}
		};
		entityCleanupTask = new SingletonTask(ses, ecr);
		entityCleanupTask.reschedule(ENTITY_CLEANUP_INTERVAL,
				TimeUnit.SECONDS);

		Runnable consolidateStoreRunner = new Runnable() {
			@Override
			public void run() {
				deviceSyncManager.consolidateStore();
				storeConsolidateTask.reschedule(syncStoreConsolidateIntervalMs,
						TimeUnit.MILLISECONDS);
			}
		};
		storeConsolidateTask = new SingletonTask(ses, consolidateStoreRunner);
		if (isMaster)
			storeConsolidateTask.reschedule(syncStoreConsolidateIntervalMs,
					TimeUnit.MILLISECONDS);


		if (restApi != null) {
			restApi.addRestletRoutable(new DeviceRoutable());
		} else {
			logger.debug("Could not instantiate REST API");
		}

		try {
			this.syncService.registerStore(DEVICE_SYNC_STORE_NAME, Scope.LOCAL);
			this.storeClient = this.syncService
					.getStoreClient(DEVICE_SYNC_STORE_NAME,
							String.class,
							DeviceSyncRepresentation.class);
		} catch (SyncException e) {
			throw new FloodlightModuleException("Error while setting up sync service", e);
		}
		floodlightProvider.addInfoProvider("summary", this);
	}

	private void registerDeviceManagerDebugCounters() throws FloodlightModuleException {
		if (debugCounters == null) {
			logger.error("Debug Counter Service not found.");
		}
		debugCounters.registerModule(PACKAGE);
		cntIncoming = debugCounters.registerCounter(PACKAGE, "incoming",
				"All incoming packets seen by this module");
		cntReconcileRequest = debugCounters.registerCounter(PACKAGE,
				"reconcile-request",
				"Number of flows that have been received for reconciliation by " +
				"this module");
		cntReconcileNoSource = debugCounters.registerCounter(PACKAGE,
				"reconcile-no-source-device",
				"Number of flow reconcile events that failed because no source " +
						"device could be identified", IDebugCounterService.MetaData.WARN); // is this really a IDebugCounterService.CTR_MDATA_WARNing
		cntReconcileNoDest = debugCounters.registerCounter(PACKAGE,
				"reconcile-no-dest-device",
				"Number of flow reconcile events that failed because no " +
						"destination device could be identified", IDebugCounterService.MetaData.WARN); // is this really a IDebugCounterService.CTR_MDATA_WARNing
		cntInvalidSource = debugCounters.registerCounter(PACKAGE,
				"invalid-source",
				"Number of packetIns that were discarded because the source " +
						"MAC was invalid (broadcast, multicast, or zero)", IDebugCounterService.MetaData.WARN);
		cntNoSource = debugCounters.registerCounter(PACKAGE, "no-source-device",
				"Number of packetIns that were discarded because the " +
						"could not identify a source device. This can happen if a " +
						"packet is not allowed, appears on an illegal port, does not " +
						"have a valid address space, etc.", IDebugCounterService.MetaData.WARN);
		cntInvalidDest = debugCounters.registerCounter(PACKAGE,
				"invalid-dest",
				"Number of packetIns that were discarded because the dest " +
						"MAC was invalid (zero)", IDebugCounterService.MetaData.WARN);
		cntNoDest = debugCounters.registerCounter(PACKAGE, "no-dest-device",
				"Number of packetIns that did not have an associated " +
						"destination device. E.g., because the destination MAC is " +
				"broadcast/multicast or is not yet known to the controller.");
		cntDhcpClientNameSnooped = debugCounters.registerCounter(PACKAGE,
				"dhcp-client-name-snooped",
				"Number of times a DHCP client name was snooped from a " +
				"packetIn.");
		cntDeviceOnInternalPortNotLearned = debugCounters.registerCounter(
				PACKAGE,
				"device-on-internal-port-not-learned",
				"Number of times packetIn was received on an internal port and" +
						"no source device is known for the source MAC. The packetIn is " +
						"discarded.", IDebugCounterService.MetaData.WARN);
		cntPacketNotAllowed = debugCounters.registerCounter(PACKAGE,
				"packet-not-allowed",
				"Number of times a packetIn was not allowed due to spoofing " +
						"protection configuration.", IDebugCounterService.MetaData.WARN); // is this really a IDebugCounterService.CTR_MDATA_WARNing?
		cntNewDevice = debugCounters.registerCounter(PACKAGE, "new-device",
				"Number of times a new device was learned");
		cntPacketOnInternalPortForKnownDevice = debugCounters.registerCounter(
				PACKAGE,
				"packet-on-internal-port-for-known-device",
				"Number of times a packetIn was received on an internal port " +
				"for a known device.");
		cntNewEntity = debugCounters.registerCounter(PACKAGE, "new-entity",
				"Number of times a new entity was learned for an existing device");
		cntDeviceChanged = debugCounters.registerCounter(PACKAGE, "device-changed",
				"Number of times device properties have changed");
		cntDeviceMoved = debugCounters.registerCounter(PACKAGE, "device-moved",
				"Number of times devices have moved");
		cntCleanupEntitiesRuns = debugCounters.registerCounter(PACKAGE,
				"cleanup-entities-runs",
				"Number of times the entity cleanup task has been run");
		cntEntityRemovedTimeout = debugCounters.registerCounter(PACKAGE,
				"entity-removed-timeout",
				"Number of times entities have been removed due to timeout " +
						"(entity has been inactive for " + ENTITY_TIMEOUT/1000 + "s)");
		cntDeviceDeleted = debugCounters.registerCounter(PACKAGE, "device-deleted",
				"Number of devices that have been removed due to inactivity");
		cntDeviceReclassifyDelete = debugCounters.registerCounter(PACKAGE,
				"device-reclassify-delete",
				"Number of devices that required reclassification and have been " +
				"temporarily delete for reclassification");
		cntDeviceStrored = debugCounters.registerCounter(PACKAGE, "device-stored",
				"Number of device entries written or updated to the sync store");
		cntDeviceStoreThrottled = debugCounters.registerCounter(PACKAGE,
				"device-store-throttled",
				"Number of times a device update to the sync store was " +
						"requested but not performed because the same device entities " +
				"have recently been updated already");
		cntDeviceRemovedFromStore = debugCounters.registerCounter(PACKAGE,
				"device-removed-from-store",
				"Number of devices that were removed from the sync store " +
						"because the local controller removed the device due to " +
				"inactivity");
		cntSyncException = debugCounters.registerCounter(PACKAGE, "sync-exception",
				"Number of times an operation on the sync store resulted in " +
						"sync exception", IDebugCounterService.MetaData.WARN); // it this an error?
		cntDevicesFromStore = debugCounters.registerCounter(PACKAGE,
				"devices-from-store",
				"Number of devices that were read from the sync store after " +
				"the local controller transitioned from SLAVE to MASTER");
		cntConsolidateStoreRuns = debugCounters.registerCounter(PACKAGE,
				"consolidate-store-runs",
				"Number of times the task to consolidate entries in the " +
				"store witch live known devices has been run");
		cntConsolidateStoreDevicesRemoved = debugCounters.registerCounter(PACKAGE,
				"consolidate-store-devices-removed",
				"Number of times a device has been removed from the sync " +
						"store because no corresponding live device is known. " +
						"This indicates a remote controller still writing device " +
						"entries despite the local controller being MASTER or an " +
						"incosistent store update from the local controller.", IDebugCounterService.MetaData.WARN);
		cntTransitionToMaster = debugCounters.registerCounter(PACKAGE,
				"transition-to-master",
				"Number of times this controller has transitioned from SLAVE " +
				"to MASTER role. Will be 0 or 1.");
	}

	// ***************
	// IHAListener
	// ***************

	protected class HAListenerDelegate implements IHAListener {
		@Override
		public void transitionToActive() {
			DeviceManagerImpl.this.isMaster = true;
			DeviceManagerImpl.this.deviceSyncManager.goToMaster();
		}

		@Override
		public void controllerNodeIPsChanged(
				Map<String, String> curControllerNodeIPs,
				Map<String, String> addedControllerNodeIPs,
				Map<String, String> removedControllerNodeIPs) {
			// no-op
		}

		@Override
		public String getName() {
			return DeviceManagerImpl.this.getName();
		}

		@Override
		public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
				String name) {
			return ("topology".equals(name) ||
					"bvsmanager".equals(name));
		}

		@Override
		public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
				String name) {
			return false;
		}

		@Override
		public void transitionToStandby() {
			DeviceManagerImpl.this.isMaster = false;
		}
	}


	// ****************
	// Internal methods
	// ****************

	protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		// Extract source entity information
		Entity srcEntity = getSourceEntityFromPacket(eth, sw.getId(), inPort);
		if (srcEntity == null) {
			cntInvalidSource.increment();
			return Command.STOP;
		}

		// Learn from ARP packet for special VRRP settings.
		// In VRRP settings, the source MAC address and sender MAC
		// addresses can be different.  In such cases, we need to learn
		// the IP to MAC mapping of the VRRP IP address.  The source
		// entity will not have that information.  Hence, a separate call
		// to learn devices in such cases.
		learnDeviceFromArpResponseData(eth, sw.getId(), inPort);

		// Learn/lookup device information
		Device srcDevice = learnDeviceByEntity(srcEntity);
		if (srcDevice == null) {
			cntNoSource.increment();
			return Command.STOP;
		}

		// Store the source device in the context
		fcStore.put(cntx, CONTEXT_SRC_DEVICE, srcDevice);

		// Find the device matching the destination from the entity
		// classes of the source.
		if (eth.getDestinationMACAddress().getLong() == 0) {
			cntInvalidDest.increment();
			return Command.STOP;
		}
		Entity dstEntity = getDestEntityFromPacket(eth);
		Device dstDevice = null;
		if (dstEntity != null) {
			dstDevice = findDestByEntity(srcDevice.getEntityClass(), dstEntity);
			if (dstDevice != null)
				fcStore.put(cntx, CONTEXT_DST_DEVICE, dstDevice);
			else
				cntNoDest.increment();
		} else {
			cntNoDest.increment();
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Received PI: {} on switch {}, port {} *** eth={}" +
					" *** srcDev={} *** dstDev={} *** ",
					new Object[] { pi, sw.getId().toString(), inPort, eth,
					srcDevice, dstDevice });
		}

		snoopDHCPClientName(eth, srcDevice);

		return Command.CONTINUE;
	}

	/**
	 * Snoop and record client-provided host name from DHCP requests
	 * @param eth
	 * @param srcDevice
	 */
	private void snoopDHCPClientName(Ethernet eth, Device srcDevice) {
		if (! (eth.getPayload() instanceof IPv4) )
			return;
		IPv4 ipv4 = (IPv4) eth.getPayload();
		if (! (ipv4.getPayload() instanceof UDP) )
			return;
		UDP udp = (UDP) ipv4.getPayload();
		if (!(udp.getPayload() instanceof DHCP))
			return;
		DHCP dhcp = (DHCP) udp.getPayload();
		byte opcode = dhcp.getOpCode();
		if (opcode == DHCP.OPCODE_REQUEST) {
			DHCPOption dhcpOption = dhcp.getOption(
					DHCPOptionCode.OptionCode_Hostname);
			if (dhcpOption != null) {
				cntDhcpClientNameSnooped.increment();
				srcDevice.dhcpClientName = new String(dhcpOption.getData());
			}
		}
	}

	/**
	 * Check whether the given attachment point is valid given the current
	 * topology
	 * @param switchDPID the DPID
	 * @param switchPort the port
	 * @return true if it's a valid attachment point
	 */
	public boolean isValidAttachmentPoint(DatapathId switchDPID,
			OFPort switchPort) {
		if (topology.isAttachmentPointPort(switchDPID, switchPort) == false)
			return false;

		if (suppressAPs.contains(new SwitchPort(switchDPID, switchPort)))
			return false;

		return true;
	}

	/**
	 * Get sender IPv4 address from packet if the packet is an ARP
	 * packet and if the source MAC address matches the ARP packets
	 * sender MAC address.
	 * @param eth
	 * @param dlAddr
	 * @return
	 */
	private IPv4Address getSrcIPv4AddrFromARP(Ethernet eth, MacAddress dlAddr) {
		if (eth.getPayload() instanceof ARP) {
			ARP arp = (ARP) eth.getPayload();
			if ((arp.getProtocolType() == ARP.PROTO_TYPE_IP) && (arp.getSenderHardwareAddress().equals(dlAddr))) {
				return arp.getSenderProtocolAddress();
			}
		}
		return IPv4Address.NONE;
	}
	
	/**
	 * Get sender IPv6 address from packet if the packet is ND
	 * 
	 * @param eth
	 * @param dlAddr
	 * @return
	 */
	private IPv6Address getSrcIPv6Addr(Ethernet eth) {
		if (eth.getPayload() instanceof IPv6) {
			IPv6 ipv6 = (IPv6) eth.getPayload();
			return ipv6.getSourceAddress();
		}
		return IPv6Address.NONE;
	}

	/**
	 * Parse an entity from an {@link Ethernet} packet.
	 * @param eth the packet to parse
	 * @param sw the switch on which the packet arrived
	 * @param pi the original packetin
	 * @return the entity from the packet
	 */
	protected Entity getSourceEntityFromPacket(Ethernet eth, DatapathId swdpid, OFPort port) {
		MacAddress dlAddr = eth.getSourceMACAddress();

		// Ignore broadcast/multicast source
		if (dlAddr.isBroadcast() || dlAddr.isMulticast())
			return null;
		// Ignore 0 source mac
		if (dlAddr.getLong() == 0)
			return null;

		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		IPv4Address ipv4Src = getSrcIPv4AddrFromARP(eth, dlAddr);
		IPv6Address ipv6Src = ipv4Src.equals(IPv4Address.NONE) ? getSrcIPv6Addr(eth) : IPv6Address.NONE;
		return new Entity(dlAddr,
				vlan,
				ipv4Src,
				ipv6Src,
				swdpid,
				port,
				new Date());
	}

	/**
	 * Learn device from ARP data in scenarios where the
	 * Ethernet source MAC is different from the sender hardware
	 * address in ARP data.
	 */
	protected void learnDeviceFromArpResponseData(Ethernet eth,
			DatapathId swdpid,
			OFPort port) {

		if (!(eth.getPayload() instanceof ARP)) return;
		ARP arp = (ARP) eth.getPayload();

		MacAddress dlAddr = eth.getSourceMACAddress();

		MacAddress senderAddr = arp.getSenderHardwareAddress();

		if (dlAddr.equals(senderAddr)) return; // arp request

		// Ignore broadcast/multicast source
		if (senderAddr.isBroadcast() || senderAddr.isMulticast())
			return;
		// Ignore zero sender mac
		if (senderAddr.equals(MacAddress.of(0)))
			return;

		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		IPv4Address nwSrc = arp.getSenderProtocolAddress();

		Entity e =  new Entity(senderAddr,
				vlan, /* will either be a valid tag or VlanVid.ZERO if untagged */
				nwSrc,
				IPv6Address.NONE, /* must be none for ARP */
				swdpid,
				port,
				new Date());

		learnDeviceByEntity(e);
	}

	/**
	 * Get a (partial) entity for the destination from the packet.
	 * @param eth
	 * @return
	 */
	protected Entity getDestEntityFromPacket(Ethernet eth) {
		MacAddress dlAddr = eth.getDestinationMACAddress();
		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		IPv4Address ipv4Dst = IPv4Address.NONE;
		IPv6Address ipv6Dst = IPv6Address.NONE;

		// Ignore broadcast/multicast destination
		if (dlAddr.isBroadcast() || dlAddr.isMulticast())
			return null;
		// Ignore zero dest mac
		if (dlAddr.equals(MacAddress.of(0)))
			return null;

		if (eth.getPayload() instanceof IPv4) {
			IPv4 ipv4 = (IPv4) eth.getPayload();
			ipv4Dst = ipv4.getDestinationAddress();
		} else if (eth.getPayload() instanceof IPv6) {
			IPv6 ipv6 = (IPv6) eth.getPayload();
			ipv6Dst = ipv6.getDestinationAddress();
		}
		
		return new Entity(dlAddr,
				vlan,
				ipv4Dst,
				ipv6Dst,
				DatapathId.NONE,
				OFPort.ZERO,
				Entity.NO_DATE);
	}

	/**
	 * Look up a {@link Device} based on the provided {@link Entity}. We first
	 * check the primary index. If we do not find an entry there we classify
	 * the device into its IEntityClass and query the classIndex.
	 * This implies that all key field of the current IEntityClassifier must
	 * be present in the entity for the lookup to succeed!
	 * @param entity the entity to search for
	 * @return The {@link Device} object if found
	 */
	protected Device findDeviceByEntity(Entity entity) {
		// Look up the fully-qualified entity to see if it already
		// exists in the primary entity index.
		Long deviceKey = primaryIndex.findByEntity(entity);
		IEntityClass entityClass = null;

		if (deviceKey == null) {
			// If the entity does not exist in the primary entity index,
			// use the entity classifier for find the classes for the
			// entity. Look up the entity in the returned class'
			// class entity index.
			entityClass = entityClassifier.classifyEntity(entity);
			if (entityClass == null) {
				return null;
			}
			ClassState classState = getClassState(entityClass);

			if (classState.classIndex != null) {
				deviceKey = classState.classIndex.findByEntity(entity);
			}
		}
		if (deviceKey == null) return null;
		return deviceMap.get(deviceKey);
	}

	/**
	 * Get a destination device using entity fields that corresponds with
	 * the given source device.  The source device is important since
	 * there could be ambiguity in the destination device without the
	 * attachment point information.
	 * @param reference  the source device's entity class.
	 *                   The returned destination will be
	 *                   in the same entity class as the source.
	 * @param dstEntity  the entity to look up
	 * @return an {@link Device} or null if no device is found.
	 */
	protected Device findDestByEntity(IEntityClass reference,
			Entity dstEntity) {

		// Look  up the fully-qualified entity to see if it
		// exists in the primary entity index
		Long deviceKey = primaryIndex.findByEntity(dstEntity);

		if (deviceKey == null) {
			// This could happen because:
			// 1) no destination known, or a broadcast destination
			// 2) if we have attachment point key fields since
			// attachment point information isn't available for
			// destination devices.
			// For the second case, we'll need to match up the
			// destination device with the class of the source
			// device.
			ClassState classState = getClassState(reference);
			if (classState.classIndex == null) {
				return null;
			}
			deviceKey = classState.classIndex.findByEntity(dstEntity);
		}
		if (deviceKey == null) return null;
		return deviceMap.get(deviceKey);
	}

	/**
	 * Look up a {@link Device} within a particular entity class based on
	 * the provided {@link Entity}.
	 * @param clazz the entity class to search for the entity
	 * @param entity the entity to search for
	 * @return The {@link Device} object if found
    private Device findDeviceInClassByEntity(IEntityClass clazz,
                                               Entity entity) {
        // XXX - TODO
        throw new UnsupportedOperationException();
    }
	 */

	/**
	 * Look up a {@link Device} based on the provided {@link Entity}.  Also
	 * learns based on the new entity, and will update existing devices as
	 * required.
	 *
	 * @param entity the {@link Entity}
	 * @return The {@link Device} object if found
	 */
	protected Device learnDeviceByEntity(Entity entity) {
		ArrayList<Long> deleteQueue = null;
		LinkedList<DeviceUpdate> deviceUpdates = null;
		Device device = null;

		// we may need to restart the learning process if we detect
		// concurrent modification.  Note that we ensure that at least
		// one thread should always succeed so we don't get into infinite
		// starvation loops
		while (true) {
			deviceUpdates = null;

			// Look up the fully-qualified entity to see if it already
			// exists in the primary entity index.
			Long deviceKey = primaryIndex.findByEntity(entity);
			IEntityClass entityClass = null;

			if (deviceKey == null) {
				// If the entity does not exist in the primary entity index,
				// use the entity classifier for find the classes for the
				// entity. Look up the entity in the returned class'
				// class entity index.
				entityClass = entityClassifier.classifyEntity(entity);
				if (entityClass == null) {
					// could not classify entity. No device
					device = null;
					break;
				}
				ClassState classState = getClassState(entityClass);

				if (classState.classIndex != null) {
					deviceKey = classState.classIndex.findByEntity(entity);
				}
			}
			if (deviceKey != null) {
				// If the primary or secondary index contains the entity
				// use resulting device key to look up the device in the
				// device map, and use the referenced Device below.
				device = deviceMap.get(deviceKey);
				if (device == null) {
					// This can happen due to concurrent modification
					if (logger.isDebugEnabled()) {
						logger.debug("No device for deviceKey {} while "
								+ "while processing entity {}",
								deviceKey, entity);
					}
					// if so, then try again till we don't even get the device key
					// and so we recreate the device
					continue;
				}
			} else {
				// If the secondary index does not contain the entity,
				// create a new Device object containing the entity, and
				// generate a new device ID if the the entity is on an
				// attachment point port. Otherwise ignore.
				if (entity.hasSwitchPort() && !topology.isAttachmentPointPort(entity.getSwitchDPID(), entity.getSwitchPort())) {
					cntDeviceOnInternalPortNotLearned.increment();
					if (logger.isDebugEnabled()) {
						logger.debug("Not learning new device on internal"
								+ " link: {}", entity);
					}
					device = null;
					break;
				}
				// Before we create the new device also check if
				// the entity is allowed (e.g., for spoofing protection)
				if (!isEntityAllowed(entity, entityClass)) {
					cntPacketNotAllowed.increment();
					if (logger.isDebugEnabled()) {
						logger.debug("PacketIn is not allowed {} {}",
								entityClass.getName(), entity);
					}
					device = null;
					break;
				}
				deviceKey = deviceKeyCounter.getAndIncrement();
				device = allocateDevice(deviceKey, entity, entityClass);


				// Add the new device to the primary map with a simple put
				deviceMap.put(deviceKey, device);
				// update indices
				if (!updateIndices(device, deviceKey)) {
					if (deleteQueue == null)
						deleteQueue = new ArrayList<Long>();
					deleteQueue.add(deviceKey);
					continue;
				}

				updateSecondaryIndices(entity, entityClass, deviceKey);

				// We need to count and log here. If we log earlier we could
				// hit a concurrent modification and restart the dev creation
				// and potentially count the device twice.
				cntNewDevice.increment();
				if (logger.isDebugEnabled()) {
					logger.debug("New device created: {} deviceKey={}, entity={}",
							new Object[]{device, deviceKey, entity});
				}
				// generate new device update
				deviceUpdates = updateUpdates(deviceUpdates, new DeviceUpdate(device, ADD, null));

				break;
			}
			// if it gets here, we have a pre-existing Device for this Entity
			if (!isEntityAllowed(entity, device.getEntityClass())) {
				cntPacketNotAllowed.increment();
				if (logger.isDebugEnabled()) {
					logger.info("PacketIn is not allowed {} {}",
							device.getEntityClass().getName(), entity);
				}
				return null;
			}
			// If this is not an attachment point port we don't learn the new entity
			// and don't update indexes. But we do allow the device to continue up
			// the chain.
			if (entity.hasSwitchPort() && !topology.isAttachmentPointPort(entity.getSwitchDPID(), entity.getSwitchPort())) {
				cntPacketOnInternalPortForKnownDevice.increment();
				break;
			}
			
			int entityindex = -1;
			if ((entityindex = device.entityIndex(entity)) >= 0) {
				// Entity already exists
				// update timestamp on the found entity
				Date lastSeen = entity.getLastSeenTimestamp();
				if (lastSeen.equals(Entity.NO_DATE)) {
					lastSeen = new Date();
					entity.setLastSeenTimestamp(lastSeen);
				}
				device.entities[entityindex].setLastSeenTimestamp(lastSeen);
				// we break the loop after checking for changes to the AP
			} else {
				// New entity for this device
				// compute the insertion point for the entity.
				// see Arrays.binarySearch()
				entityindex = -(entityindex + 1);
				Device newDevice = allocateDevice(device, entity, entityindex);

				// generate updates
				EnumSet<DeviceField> changedFields = findChangedFields(device, entity);

				// update the device map with a replace call
				boolean res = deviceMap.replace(deviceKey, device, newDevice);
				// If replace returns false, restart the process from the
				// beginning (this implies another thread concurrently
				// modified this Device).
				if (!res)
					continue;

				device = newDevice;
				// update indices
				if (!updateIndices(device, deviceKey)) {
					continue;
				}
				updateSecondaryIndices(entity,
						device.getEntityClass(),
						deviceKey);

				// We need to count here after all the possible "continue"
				// statements in this branch
				cntNewEntity.increment();
				if (changedFields.size() > 0) {
					cntDeviceChanged.increment();
					deviceUpdates =
							updateUpdates(deviceUpdates,
									new DeviceUpdate(newDevice, CHANGE,
											changedFields));
				}
				// we break the loop after checking for changed AP
			}
			// Update attachment point (will only be hit if the device
			// already existed and no concurrent modification)
			if (entity.hasSwitchPort()) {
				boolean moved = device.updateAttachmentPoint(entity.getSwitchDPID(),
						entity.getSwitchPort(),
						entity.getLastSeenTimestamp());
				if (moved) {
					// we count device moved events in sendDeviceMovedNotification()
					// TODO remove this. It's now done in the event handler as a result of the update above... sendDeviceMovedNotification(device);
					if (logger.isTraceEnabled()) {
						logger.trace("Device moved: attachment points {}," +
								"entities {}", device.attachmentPoints,
								device.entities);
					}
				} else {
					if (logger.isTraceEnabled()) {
						logger.trace("Device attachment point updated: " +
								"attachment points {}," +
								"entities {}", device.attachmentPoints,
								device.entities);
					}
				}
			}
			break;
		}

		if (deleteQueue != null) {
			for (Long l : deleteQueue) {
				Device dev = deviceMap.get(l);
				this.deleteDevice(dev);
			}
		}
		processUpdates(deviceUpdates);
		deviceSyncManager.storeDeviceThrottled(device);

		return device;
	}

	protected boolean isEntityAllowed(Entity entity, IEntityClass entityClass) {
		return true;
	}

	protected EnumSet<DeviceField> findChangedFields(Device device,
			Entity newEntity) {
		EnumSet<DeviceField> changedFields =
				EnumSet.of(DeviceField.IPv4,
						DeviceField.IPv6,
						DeviceField.VLAN,
						DeviceField.SWITCH);

		/*
		 * Do we really need this here?
		 *
		if (newEntity.getIpv4Address().equals(IPv4Address.NONE))
			changedFields.remove(DeviceField.IPv4);
		if (newEntity.getIpv6Address().equals(IPv6Address.NONE))
			changedFields.remove(DeviceField.IPv6);
		/*if (newEntity.getVlan().equals(VlanVid.ZERO)) TODO VLAN is ZERO here, since the actual Device and Entity must have some sort of VLAN, either untagged (ZERO) or some value 
			changedFields.remove(DeviceField.VLAN);
		if (newEntity.getSwitchDPID().equals(DatapathId.NONE) ||
				newEntity.getSwitchPort().equals(OFPort.ZERO))
			changedFields.remove(DeviceField.SWITCH); 

		if (changedFields.size() == 0) return changedFields; */

		for (Entity entity : device.getEntities()) {
			if (newEntity.getIpv4Address().equals(IPv4Address.NONE) || /* NONE means 'not in this packet' */
					entity.getIpv4Address().equals(newEntity.getIpv4Address())) /* these (below) might be defined and if they are and changed, then the device has changed */
				changedFields.remove(DeviceField.IPv4);
			if (newEntity.getIpv6Address().equals(IPv6Address.NONE) || /* NONE means 'not in this packet' */
					entity.getIpv6Address().equals(newEntity.getIpv6Address()))
				changedFields.remove(DeviceField.IPv6);
			if (entity.getVlan().equals(newEntity.getVlan())) /* these (below) must be defined in each and every packet-in, and if different signal a device field change */
				changedFields.remove(DeviceField.VLAN);
			if (newEntity.getSwitchDPID().equals(DatapathId.NONE) ||
					newEntity.getSwitchPort().equals(OFPort.ZERO) ||
					(entity.getSwitchDPID().equals(newEntity.getSwitchDPID()) &&
					entity.getSwitchPort().equals(newEntity.getSwitchPort())))
				changedFields.remove(DeviceField.SWITCH);
		}

		if (changedFields.contains(DeviceField.SWITCH)) {
			if (!isValidAttachmentPoint(newEntity.getSwitchDPID(), newEntity.getSwitchPort())) {
				changedFields.remove(DeviceField.SWITCH);
			}
		}
		
		return changedFields;
	}

	/**
	 * Send update notifications to listeners
	 * @param updates the updates to process.
	 */
	 protected void processUpdates(Queue<DeviceUpdate> updates) {
		if (updates == null) return;
		DeviceUpdate update = null;
		while (null != (update = updates.poll())) {
			if (logger.isTraceEnabled()) {
				logger.trace("Dispatching device update: {}", update);
			}
			if (update.change == DeviceUpdate.Change.DELETE) {
				deviceSyncManager.removeDevice(update.device);
			} else {
				deviceSyncManager.storeDevice(update.device);
			}
			List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
			notifyListeners(listeners, update);
		}
	 }

	 protected void notifyListeners(List<IDeviceListener> listeners, DeviceUpdate update) {
		 if (listeners == null) {
			 return;
		 }
		 for (IDeviceListener listener : listeners) {
			 switch (update.change) {
			 case ADD:
				 listener.deviceAdded(update.device);
				 break;
			 case DELETE:
				 listener.deviceRemoved(update.device);
				 break;
			 case CHANGE:
				 for (DeviceField field : update.fieldsChanged) {
					 switch (field) {
					 case IPv4:
						 listener.deviceIPV4AddrChanged(update.device);
						 break;
					 case IPv6:
						 listener.deviceIPV6AddrChanged(update.device);
						 break;
					 case SWITCH:
					 case PORT:
						 listener.deviceMoved(update.device); // TODO why was this commented out?
						 break;
					 case VLAN:
						 listener.deviceVlanChanged(update.device);
						 break;
					 default:
						 logger.debug("Unknown device field changed {}",
								 update.fieldsChanged.toString());
						 break;
					 }
				 }
				 break;
			 }
		 }
	 }

	 /**
	  * Check if the entity e has all the keyFields set. Returns false if not
	  * @param e entity to check
	  * @param keyFields the key fields to check e against
	  * @return
	  */
	 protected boolean allKeyFieldsPresent(Entity e, EnumSet<DeviceField> keyFields) {
		 for (DeviceField f : keyFields) {
			 switch (f) {
			 case MAC:
				 // MAC address is always present
				 break;
			 case IPv4:
			 case IPv6:
				 if (e.ipv4Address.equals(IPv4Address.NONE) && e.ipv6Address.equals(IPv6Address.NONE)) {
					 return false; // mutually exclusive
				 }
				 break;
			 case SWITCH:
				 if (e.switchDPID.equals(DatapathId.NONE)) {
					 return false;
				 }
				 break;
			 case PORT:
				 if (e.switchPort.equals(OFPort.ZERO)) {
					 return false;
				 }
				 break;
			 case VLAN:
				 if (e.vlan == null) { /* VLAN is null for 'don't care' or 'unspecified'. It's VlanVid.ZERO for untagged. */
					 return false; 	   /* For key field of VLAN, the VLAN **MUST** be set to either ZERO or some value. */
				 }
				 break;
			 default:
				 // we should never get here. unless somebody extended
				 // DeviceFields
				 throw new IllegalStateException();
			 }
		 }
		 return true;
	 }

	 private LinkedList<DeviceUpdate> updateUpdates(LinkedList<DeviceUpdate> list, DeviceUpdate update) {
		 if (update == null) return list;
		 if (list == null)
			 list = new LinkedList<DeviceUpdate>();
		 list.add(update);

		 return list;
	 }

	 /**
	  * Get the secondary index for a class.  Will return null if the
	  * secondary index was created concurrently in another thread.
	  * @param clazz the class for the index
	  * @return
	  */
	 private ClassState getClassState(IEntityClass clazz) {
		 ClassState classState = classStateMap.get(clazz.getName());
		 if (classState != null) return classState;

		 classState = new ClassState(clazz);
		 ClassState r = classStateMap.putIfAbsent(clazz.getName(), classState);
		 if (r != null) {
			 // concurrent add
			 return r;
		 }
		 return classState;
	 }

	 /**
	  * Update both the primary and class indices for the provided device.
	  * If the update fails because of an concurrent update, will return false.
	  * @param device the device to update
	  * @param deviceKey the device key for the device
	  * @return true if the update succeeded, false otherwise.
	  */
	 private boolean updateIndices(Device device, Long deviceKey) {
		 if (!primaryIndex.updateIndex(device, deviceKey)) {
			 return false;
		 }
		 IEntityClass entityClass = device.getEntityClass();
		 ClassState classState = getClassState(entityClass);

		 if (classState.classIndex != null) {
			 if (!classState.classIndex.updateIndex(device,
					 deviceKey))
				 return false;
		 }
		 return true;
	 }

	 /**
	  * Update the secondary indices for the given entity and associated
	  * entity classes
	  * @param entity the entity to update
	  * @param entityClass the entity class for the entity
	  * @param deviceKey the device key to set up
	  */
	 private void updateSecondaryIndices(Entity entity,
			 IEntityClass entityClass,
			 Long deviceKey) {
		 for (DeviceIndex index : secondaryIndexMap.values()) {
			 index.updateIndex(entity, deviceKey);
		 }
		 ClassState state = getClassState(entityClass);
		 for (DeviceIndex index : state.secondaryIndexMap.values()) {
			 index.updateIndex(entity, deviceKey);
		 }
	 }

	 /**
	  * Clean up expired entities/devices
	  */
	 protected void cleanupEntities () {
		 cntCleanupEntitiesRuns.increment();

		 Calendar c = Calendar.getInstance();
		 c.add(Calendar.MILLISECOND, -ENTITY_TIMEOUT);
		 Date cutoff = c.getTime();

		 ArrayList<Entity> toRemove = new ArrayList<Entity>();
		 ArrayList<Entity> toKeep = new ArrayList<Entity>();

		 Iterator<Device> diter = deviceMap.values().iterator();
		 LinkedList<DeviceUpdate> deviceUpdates =
				 new LinkedList<DeviceUpdate>();

		 while (diter.hasNext()) {
			 Device d = diter.next();

			 while (true) {
				 deviceUpdates.clear();
				 toRemove.clear();
				 toKeep.clear();
				 for (Entity e : d.getEntities()) {
					 if (!e.getLastSeenTimestamp().equals(Entity.NO_DATE) &&
							 0 > e.getLastSeenTimestamp().compareTo(cutoff)) {
						 // individual entity needs to be removed
						 toRemove.add(e);
					 } else {
						 toKeep.add(e);
					 }
				 }
				 if (toRemove.size() == 0) {
					 break;
				 }

				 cntEntityRemovedTimeout.increment();
				 for (Entity e : toRemove) {
					 removeEntity(e, d.getEntityClass(), d.getDeviceKey(), toKeep);
				 }

				 if (toKeep.size() > 0) {
					 Device newDevice = allocateDevice(d.getDeviceKey(),
							 d.getDHCPClientName(),
							 d.oldAPs,
							 d.attachmentPoints,
							 toKeep,
							 d.getEntityClass());

					 EnumSet<DeviceField> changedFields =
							 EnumSet.noneOf(DeviceField.class);
					 for (Entity e : toRemove) {
						 changedFields.addAll(findChangedFields(newDevice, e));
					 }
					 DeviceUpdate update = null;
					 if (changedFields.size() > 0) {
						 update = new DeviceUpdate(d, CHANGE, changedFields);
					 }

					 if (!deviceMap.replace(newDevice.getDeviceKey(),
							 d,
							 newDevice)) {
						 // concurrent modification; try again
						 // need to use device that is the map now for the next
						 // iteration
						 d = deviceMap.get(d.getDeviceKey());
								 if (null != d)
									 continue;
					 }
					 if (update != null) {
						 // need to count after all possibly continue stmts in
						 // this branch
						 cntDeviceChanged.increment();
						 deviceUpdates.add(update);
					 }
				 } else {
					 DeviceUpdate update = new DeviceUpdate(d, DELETE, null);
					 if (!deviceMap.remove(d.getDeviceKey(), d)) {
						 // concurrent modification; try again
						 // need to use device that is the map now for the next
						 // iteration
						 d = deviceMap.get(d.getDeviceKey());
						 if (null != d)
							 continue;
						 cntDeviceDeleted.increment();
					 }
					 deviceUpdates.add(update);
				 }
				 processUpdates(deviceUpdates);
				 break;
			 }
		 }
	 }

	 protected void removeEntity(Entity removed,
			 IEntityClass entityClass,
			 Long deviceKey,
			 Collection<Entity> others) {
		 // Don't count in this method. This method CAN BE called to clean-up
		 // after concurrent device adds/updates and thus counting here
		 // is misleading
		 for (DeviceIndex index : secondaryIndexMap.values()) {
			 index.removeEntityIfNeeded(removed, deviceKey, others);
		 }
		 ClassState classState = getClassState(entityClass);
		 for (DeviceIndex index : classState.secondaryIndexMap.values()) {
			 index.removeEntityIfNeeded(removed, deviceKey, others);
		 }

		 primaryIndex.removeEntityIfNeeded(removed, deviceKey, others);

		 if (classState.classIndex != null) {
			 classState.classIndex.removeEntityIfNeeded(removed,
					 deviceKey,
					 others);
		 }
	 }

	 /**
	  * method to delete a given device, remove all entities first and then
	  * finally delete the device itself.
	  * @param device
	  */
	 protected void deleteDevice(Device device) {
		 // Don't count in this method. This method CAN BE called to clean-up
		 // after concurrent device adds/updates and thus counting here
		 // is misleading
		 ArrayList<Entity> emptyToKeep = new ArrayList<Entity>();
		 for (Entity entity : device.getEntities()) {
			 this.removeEntity(entity, device.getEntityClass(),
					 device.getDeviceKey(), emptyToKeep);
		 }
		 if (!deviceMap.remove(device.getDeviceKey(), device)) {
			 if (logger.isDebugEnabled())
				 logger.debug("device map does not have this device -" +
						 device.toString());
		 }
	 }

	 private EnumSet<DeviceField> getEntityKeys(@Nonnull MacAddress macAddress,
			 VlanVid vlan, /* A null VLAN means 'don't care'; VlanVid.ZERO means 'untagged' */
			 @Nonnull IPv4Address ipv4Address,
			 @Nonnull IPv6Address ipv6Address,
			 @Nonnull DatapathId switchDPID,
			 @Nonnull OFPort switchPort) {
		 EnumSet<DeviceField> keys = EnumSet.noneOf(DeviceField.class);
		 if (!macAddress.equals(MacAddress.NONE)) keys.add(DeviceField.MAC);
		 if (vlan != null) keys.add(DeviceField.VLAN); /* TODO verify fix. null means 'don't care' and will conduct full search; VlanVid.ZERO means 'untagged' and only uses untagged index */
		 if (!ipv4Address.equals(IPv4Address.NONE)) keys.add(DeviceField.IPv4);
		 if (!ipv6Address.equals(IPv6Address.NONE)) keys.add(DeviceField.IPv6);
		 if (!switchDPID.equals(DatapathId.NONE)) keys.add(DeviceField.SWITCH);
		 if (!switchPort.equals(OFPort.ZERO)) keys.add(DeviceField.PORT);
		 return keys;
	 }

	 protected Iterator<Device> queryClassByEntity(IEntityClass clazz,
			 EnumSet<DeviceField> keyFields,
			 Entity entity) {
		 ClassState classState = getClassState(clazz);
		 DeviceIndex index = classState.secondaryIndexMap.get(keyFields);
		 if (index == null) return Collections.<Device>emptySet().iterator();
		 return new DeviceIndexInterator(this, index.queryByEntity(entity));
	 }

	 protected Device allocateDevice(Long deviceKey,
			 Entity entity,
			 IEntityClass entityClass) {
		 return new Device(this, deviceKey, entity, entityClass);
	 }

	 // TODO: FIX THIS. What's 'this' that needs fixing?
	 protected Device allocateDevice(Long deviceKey,
			 String dhcpClientName,
			 List<AttachmentPoint> aps,
			 List<AttachmentPoint> trueAPs,
			 Collection<Entity> entities,
			 IEntityClass entityClass) {
		 return new Device(this, deviceKey, dhcpClientName, aps, trueAPs,
				 entities, entityClass);
	 }

	 protected Device allocateDevice(Device device,
			 Entity entity,
			 int insertionpoint) {
		 return new Device(device, entity, insertionpoint);
	 }

	 //not used 
	 /* TODO then let's get rid of it?
	 protected Device allocateDevice(Device device, Set <Entity> entities) {
		 List <AttachmentPoint> newPossibleAPs =
				 new ArrayList<AttachmentPoint>();
		 List <AttachmentPoint> newAPs =
				 new ArrayList<AttachmentPoint>();
		 for (Entity entity : entities) {
			 if (entity.switchDPID != null && entity.switchPort != null) {
				 AttachmentPoint aP =
						 new AttachmentPoint(entity.switchDPID,
								 entity.switchPort, new Date(0));
				 newPossibleAPs.add(aP);
			 }
		 }
		 if (device.attachmentPoints != null) {
			 for (AttachmentPoint oldAP : device.attachmentPoints) {
				 if (newPossibleAPs.contains(oldAP)) {
					 newAPs.add(oldAP);
				 }
			 }
		 }
		 if (newAPs.isEmpty())
			 newAPs = null;
		 Device d = new Device(this, device.getDeviceKey(),
				 device.getDHCPClientName(), newAPs, null,
				 entities, device.getEntityClass());
		 d.updateAttachmentPoint();
		 return d;
	 } */

	 // *********************
	 // ITopologyListener
	 // *********************

	 /**
	  * Topology listener method.
	  */
	 @Override
	 public void topologyChanged(List<LDUpdate> updateList) {
		 Iterator<Device> diter = deviceMap.values().iterator();
		 if (updateList != null) {
			 if (logger.isTraceEnabled()) {
				 for(LDUpdate update: updateList) {
					 logger.trace("Topo update: {}", update);
				 }
			 }
		 }
		 while (diter.hasNext()) {
			 Device d = diter.next();
			 if (d.updateAttachmentPoint()) {
				 if (logger.isDebugEnabled()) {
					 logger.debug("Attachment point changed for device: {}", d);
				 }
				 sendDeviceMovedNotification(d);
			 }
		 }
	 }

	 /**
	  * Send update notifications to listeners
	  * @param updates the updates to process.
	  */
	 protected void sendDeviceMovedNotification(Device d) {
		 cntDeviceMoved.increment();
		 deviceSyncManager.storeDevice(d);
		 List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
		 if (listeners != null) {
			 for (IDeviceListener listener : listeners) {
				 listener.deviceMoved(d);
			 }
		 }
	 }

	 // *********************
	 // IEntityClassListener
	 // *********************

	 @Override
	 public void entityClassChanged (Set<String> entityClassNames) {
		 /* iterate through the devices, reclassify the devices that belong
		  * to these entity class names
		  */
		 Iterator<Device> diter = deviceMap.values().iterator();
		 while (diter.hasNext()) {
			 Device d = diter.next();
			 if (d.getEntityClass() == null ||
					 entityClassNames.contains(d.getEntityClass().getName()))
				 reclassifyDevice(d);
		 }
	 }

	 /**
	  * this method will reclassify and reconcile a device - possibilities
	  * are - create new device(s), remove entities from this device. If the
	  * device entity class did not change then it returns false else true.
	  * @param device
	  */
	 protected boolean reclassifyDevice(Device device)
	 {
		 // first classify all entities of this device
		 if (device == null) {
			 logger.debug("In reclassify for null device");
			 return false;
		 }
		 boolean needToReclassify = false;
		 for (Entity entity : device.entities) {
			 IEntityClass entityClass =
					 this.entityClassifier.classifyEntity(entity);
			 if (entityClass == null || device.getEntityClass() == null) {
				 needToReclassify = true;
				 break;
			 }
			 if (!entityClass.getName().
					 equals(device.getEntityClass().getName())) {
				 needToReclassify = true;
				 break;
			 }
		 }
		 if (needToReclassify == false) {
			 return false;
		 }

		 cntDeviceReclassifyDelete.increment();
		 LinkedList<DeviceUpdate> deviceUpdates =
				 new LinkedList<DeviceUpdate>();
		 // delete this device and then re-learn all the entities
		 this.deleteDevice(device);
		 deviceUpdates.add(new DeviceUpdate(device,
				 DeviceUpdate.Change.DELETE, null));
		 if (!deviceUpdates.isEmpty())
			 processUpdates(deviceUpdates);
		 for (Entity entity: device.entities ) {
			 this.learnDeviceByEntity(entity);
		 }

		 return true;
	 }

	 /**
	  * For testing: sets the interval between writes of the same device
	  * to the device store.
	  * @param intervalMs
	  */
	 void setSyncStoreWriteInterval(int intervalMs) {
		 this.syncStoreWriteIntervalMs = intervalMs;
	 }

	 /**
	  * For testing: sets the time between transition to MASTER and
	  * consolidate store
	  * @param intervalMs
	  */
	 void setInitialSyncStoreConsolidateMs(int intervalMs) {
		 this.initialSyncStoreConsolidateMs = intervalMs;
	 }

	 /**
	  * For testing: consolidate the store NOW
	  */
	 void scheduleConsolidateStoreNow() {
		 this.storeConsolidateTask.reschedule(0, TimeUnit.MILLISECONDS);
	 }

	 private class DeviceSyncManager  {
		 // maps (opaque) deviceKey to the time in System.nanoTime() when we
		 // last wrote the device to the sync store
		 private final ConcurrentMap<Long, Long> lastWriteTimes = new ConcurrentHashMap<Long, Long>();

		 /**
		  * Write the given device to storage if we are MASTER.
		  * Use this method if the device has significantly changed (e.g.,
		  * new AP, new IP, entities removed).
		  * @param d the device to store
		  */
		 public void storeDevice(Device d) {
			 if (!isMaster)
				 return;
			 if (d == null)
				 return;
			 long now = System.nanoTime();
			 writeUpdatedDeviceToStorage(d);
			 lastWriteTimes.put(d.getDeviceKey(), now);
		 }

		 /**
		  * Write the given device to storage if we are MASTER and if the
		  * last write for the device was more than this.syncStoreIntervalNs
		  * time ago.
		  * Use this method to updated last active times in the store.
		  * @param d the device to store
		  */
		 public void storeDeviceThrottled(Device d) {
			 long intervalNs = syncStoreWriteIntervalMs*1000L*1000L;
			 if (!isMaster)
				 return;
			 if (d == null)
				 return;
			 long now = System.nanoTime();
			 Long last = lastWriteTimes.get(d.getDeviceKey());
			 if (last == null || (now - last) > intervalNs) {
				 writeUpdatedDeviceToStorage(d);
				 lastWriteTimes.put(d.getDeviceKey(), now);
			 } else {
				 cntDeviceStoreThrottled.increment();
			 }
		 }

		 /**
		  * Remove the given device from the store. If only some entities have
		  * been removed the updated device should be written using
		  * {@link #storeDevice(Device)}
		  * @param d
		  */
		 public void removeDevice(Device d) {
			 if (!isMaster)
				 return;
			 // FIXME: could we have a problem with concurrent put to the
			 // hashMap? I.e., we write a stale entry to the map after the
			 // delete and now are left with an entry we'll never clean up
			 lastWriteTimes.remove(d.getDeviceKey());
			 try {
				 // TODO: should probably do versioned delete. OTOH, even
				 // if we accidentally delete, we'll write it again after
				 // the next entity ....
				 cntDeviceRemovedFromStore.increment();
				 storeClient.delete(DeviceSyncRepresentation.computeKey(d));
			 } catch(ObsoleteVersionException e) {
				 // FIXME
			 } catch (SyncException e) {
				 cntSyncException.increment();
				 logger.error("Could not remove device " + d + " from store", e);
			 }
		 }

		 /**
		  * Remove the given Versioned device from the store. If the device
		  * was locally modified ignore the delete request.
		  * @param syncedDeviceKey
		  */
		 private void removeDevice(Versioned<DeviceSyncRepresentation> dev) {
			 try {
				 cntDeviceRemovedFromStore.increment();
				 storeClient.delete(dev.getValue().getKey(),
						 dev.getVersion());
			 } catch(ObsoleteVersionException e) {
				 // Key was locally modified by another thread.
				 // Do not delete and ignore.
			 } catch(SyncException e) {
				 cntSyncException.increment();
				 logger.error("Failed to remove device entry for " +
						 dev.toString() + " from store.", e);
			 }
		 }

		 /**
		  * Synchronously transition from SLAVE to MASTER. By iterating through
		  * the store and learning all devices from the store
		  */
		 private void goToMaster() {
			 if (logger.isDebugEnabled()) {
				 logger.debug("Transitioning to MASTER role");
			 }
			 cntTransitionToMaster.increment();
			 IClosableIterator<Map.Entry<String,Versioned<DeviceSyncRepresentation>>>
			 iter = null;
			 try {
				 iter = storeClient.entries();
			 } catch (SyncException e) {
				 cntSyncException.increment();
				 logger.error("Failed to read devices from sync store", e);
				 return;
			 }
			 try {
				 while(iter.hasNext()) {
					 Versioned<DeviceSyncRepresentation> versionedDevice =
							 iter.next().getValue();
					 DeviceSyncRepresentation storedDevice =
							 versionedDevice.getValue();
					 if (storedDevice == null)
						 continue;
					 cntDevicesFromStore.increment();
					 for(SyncEntity se: storedDevice.getEntities()) {
						 learnDeviceByEntity(se.asEntity());
					 }
				 }
			 } finally {
				 if (iter != null)
					 iter.close();
			 }
			 storeConsolidateTask.reschedule(initialSyncStoreConsolidateMs,
					 TimeUnit.MILLISECONDS);
		 }

		 /**
		  * Actually perform the write of the device to the store
		  * FIXME: concurrent modification behavior
		  * @param device The device to write
		  */
		 private void writeUpdatedDeviceToStorage(Device device) {
			 try {
				 cntDeviceStrored.increment();
				 // FIXME: use a versioned put
				 DeviceSyncRepresentation storeDevice = new DeviceSyncRepresentation(device);
				 storeClient.put(storeDevice.getKey(), storeDevice);
			 } catch (ObsoleteVersionException e) {
				 // FIXME: what's the right behavior here. Can the store client
				 // even throw this error?
			 } catch (SyncException e) {
				 cntSyncException.increment();
				 logger.error("Could not write device " + device +
						 " to sync store:", e);
			 } catch (Exception e) {
				 logger.error("Count not write device to sync storage " + e.getMessage());
			 }
		 }

		 /**
		  * Iterate through all entries in the sync store. For each device
		  * in the store check if any stored entity matches a live device. If
		  * no entities match a live device we remove the entry from the store.
		  *
		  * Note: we do not check if all devices known to device manager are
		  * in the store. We rely on regular packetIns for that.
		  * Note: it's possible that multiple entries in the store map to the
		  * same device. We don't check or handle this case.
		  *
		  * We need to perform this check after a SLAVE->MASTER transition to
		  * get rid of all entries the old master might have written to the
		  * store after we took over. We also run it regularly in MASTER
		  * state to ensure we don't have stale entries in the store
		  */
		 private void consolidateStore() {
			 if (!isMaster)
				 return;
			 cntConsolidateStoreRuns.increment();
			 if (logger.isDebugEnabled()) {
				 logger.debug("Running consolidateStore.");
			 }
			 IClosableIterator<Map.Entry<String,Versioned<DeviceSyncRepresentation>>>
			 iter = null;
			 try {
				 iter = storeClient.entries();
			 } catch (SyncException e) {
				 cntSyncException.increment();
				 logger.error("Failed to read devices from sync store", e);
				 return;
			 }
			 try {
				 while(iter.hasNext()) {
					 boolean found = false;
					 Versioned<DeviceSyncRepresentation> versionedDevice =
							 iter.next().getValue();
					 DeviceSyncRepresentation storedDevice =
							 versionedDevice.getValue();
					 if (storedDevice == null)
						 continue;
					 for(SyncEntity se: storedDevice.getEntities()) {
						 try {
							 // Do we have a device for this entity??
									 IDevice d = findDevice(MacAddress.of(se.macAddress), VlanVid.ofVlan(se.vlan),
											 IPv4Address.of(se.ipv4Address),
											 IPv6Address.NONE,
											 DatapathId.of(se.switchDPID),
											 OFPort.of(se.switchPort));
									 if (d != null) {
										 found = true;
										 break;
									 }
						 } catch (IllegalArgumentException e) {
							 // not all key fields provided. Skip entity
						 }
					 }
					 if (!found) {
						 // We currently DO NOT have a live device that
						 // matches the current device from the store.
						 // Delete device from store.
						 if (logger.isDebugEnabled()) {
							 logger.debug("Removing device {} from store. No "
									 + "corresponding live device",
									 storedDevice.getKey());
						 }
						 cntConsolidateStoreDevicesRemoved.increment();
						 removeDevice(versionedDevice);
					 }
				 }
			 } finally {
				 if (iter != null)
					 iter.close();
			 }
		 }
	 }


	 /**
	  * For testing. Sets the syncService. Only call after init but before
	  * startUp. Used by MockDeviceManager
	  * @param syncService
	  */
	 protected void setSyncServiceIfNotSet(ISyncService syncService) {
		 if (this.syncService == null)
			 this.syncService = syncService;
	 }

	 /**
	  * For testing.
	  * @return
	  */
	 IHAListener getHAListener() {
		 return this.haListenerDelegate;
	 }
}