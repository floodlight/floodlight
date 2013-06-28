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
import java.util.Arrays;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterException;
import net.floodlightcontroller.debugcounter.NullDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterType;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import net.floodlightcontroller.debugevent.IDebugEventService.MaxEventsRegistered;
import net.floodlightcontroller.debugevent.IEventUpdater;
import net.floodlightcontroller.debugevent.NullDebugEvent;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassListener;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.DeviceSyncRepresentation.SyncEntity;
import net.floodlightcontroller.devicemanager.web.DeviceRoutable;
import net.floodlightcontroller.flowcache.IFlowReconcileEngineService;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.DHCPOption;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
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

import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
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
public class DeviceManagerImpl implements
IDeviceService, IOFMessageListener, ITopologyListener,
IFloodlightModule, IEntityClassListener,
IFlowReconcileListener, IInfoProvider {
    protected static Logger logger =
            LoggerFactory.getLogger(DeviceManagerImpl.class);


    protected IFloodlightProviderService floodlightProvider;
    protected ITopologyService topology;
    protected IStorageSourceService storageSource;
    protected IRestApiService restApi;
    protected IThreadPoolService threadPool;
    protected IFlowReconcileService flowReconcileMgr;
    protected IFlowReconcileEngineService flowReconcileEngine;
    protected IDebugCounterService debugCounters;
    private ISyncService syncService;
    private IStoreClient<String,DeviceSyncRepresentation> storeClient;
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

    /**
     * Debug Events
     */
    private IDebugEventService debugEvents;
    private IEventUpdater<DeviceEvent> evDevice;

    private boolean isMaster = false;

    static final String DEVICE_SYNC_STORE_NAME =
            DeviceManagerImpl.class.getCanonicalName() + ".stateStore";

    /**
     * Time interval between writes of entries for the same device to
     * the sync store.
     */
    static final int DEFAULT_SYNC_STORE_WRITE_INTERVAL_MS =
            5*60*1000; // 5 min
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

            long oldSw = oldAP.getSw();
            short oldPort = oldAP.getPort();
            long oldDomain = topology.getL2DomainId(oldSw);
            boolean oldBD = topology.isBroadcastDomainPort(oldSw, oldPort);

            long newSw = newAP.getSw();
            short newPort = newAP.getPort();
            long newDomain = topology.getL2DomainId(newSw);
            boolean newBD = topology.isBroadcastDomainPort(newSw, newPort);

            if (oldDomain < newDomain) return -1;
            else if (oldDomain > newDomain) return 1;


            // Give preference to OFPP_LOCAL always
            if (oldPort != OFPort.OFPP_LOCAL.getValue() &&
                    newPort == OFPort.OFPP_LOCAL.getValue()) {
                return -1;
            } else if (oldPort == OFPort.OFPP_LOCAL.getValue() &&
                    newPort != OFPort.OFPP_LOCAL.getValue()) {
                return 1;
            }

            // We expect that the last seen of the new AP is higher than
            // old AP, if it is not, just reverse and send the negative
            // of the result.
            if (oldAP.getActiveSince() > newAP.getActiveSince())
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


            if ((newAP.getActiveSince() > oldAP.getLastSeen() + activeOffset) ||
                    (newAP.getLastSeen() > oldAP.getLastSeen() +
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
    public IDevice findDevice(long macAddress, Short vlan,
                              Integer ipv4Address, Long switchDPID,
                              Integer switchPort)
                              throws IllegalArgumentException {
        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
        Entity e = new Entity(macAddress, vlan, ipv4Address, switchDPID,
                              switchPort, null);
        if (!allKeyFieldsPresent(e, entityClassifier.getKeyFields())) {
            throw new IllegalArgumentException("Not all key fields specified."
                      + " Required fields: " + entityClassifier.getKeyFields());
        }
        return findDeviceByEntity(e);
    }

    @Override
    public IDevice findClassDevice(IEntityClass entityClass, long macAddress,
                                  Short vlan, Integer ipv4Address)
                                  throws IllegalArgumentException {
        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
        Entity e = new Entity(macAddress, vlan, ipv4Address,
                              null, null, null);
        if (entityClass == null ||
                !allKeyFieldsPresent(e, entityClass.getKeyFields())) {
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
    public Iterator<? extends IDevice> queryDevices(Long macAddress,
                                                    Short vlan,
                                                    Integer ipv4Address,
                                                    Long switchDPID,
                                                    Integer switchPort) {
        DeviceIndex index = null;
        if (secondaryIndexMap.size() > 0) {
            EnumSet<DeviceField> keys =
                    getEntityKeys(macAddress, vlan, ipv4Address,
                                  switchDPID, switchPort);
            index = secondaryIndexMap.get(keys);
        }

        Iterator<Device> deviceIterator = null;
        if (index == null) {
            // Do a full table scan
            deviceIterator = deviceMap.values().iterator();
        } else {
            // index lookup
            Entity entity = new Entity((macAddress == null ? 0 : macAddress),
                                       vlan,
                                       ipv4Address,
                                       switchDPID,
                                       switchPort,
                                       null);
            deviceIterator =
                    new DeviceIndexInterator(this, index.queryByEntity(entity));
        }

        DeviceIterator di =
                new DeviceIterator(deviceIterator,
                                   null,
                                   macAddress,
                                   vlan,
                                   ipv4Address,
                                   switchDPID,
                                   switchPort);
        return di;
    }

    @Override
    public Iterator<? extends IDevice> queryClassDevices(IEntityClass entityClass,
                                                         Long macAddress,
                                                         Short vlan,
                                                         Integer ipv4Address,
                                                         Long switchDPID,
                                                         Integer switchPort) {
        ArrayList<Iterator<Device>> iterators =
                new ArrayList<Iterator<Device>>();
        ClassState classState = getClassState(entityClass);

        DeviceIndex index = null;
        if (classState.secondaryIndexMap.size() > 0) {
            EnumSet<DeviceField> keys =
                    getEntityKeys(macAddress, vlan, ipv4Address,
                                  switchDPID, switchPort);
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
                                          switchDPID, switchPort);
            } else {
                // scan the entire class
                iter = new DeviceIndexInterator(this, index.getAll());
            }
        } else {
            // index lookup
            Entity entity =
                    new Entity((macAddress == null ? 0 : macAddress),
                               vlan,
                               ipv4Address,
                               switchDPID,
                               switchPort,
                               null);
            iter = new DeviceIndexInterator(this,
                                            index.queryByEntity(entity));
        }
        iterators.add(iter);

        return new MultiIterator<Device>(iterators.iterator());
    }

    protected Iterator<Device> getDeviceIteratorForQuery(Long macAddress,
                                                        Short vlan,
                                                        Integer ipv4Address,
                                                        Long switchDPID,
                                                        Integer switchPort) {
        DeviceIndex index = null;
        if (secondaryIndexMap.size() > 0) {
            EnumSet<DeviceField> keys =
                getEntityKeys(macAddress, vlan, ipv4Address,
                            switchDPID, switchPort);
            index = secondaryIndexMap.get(keys);
        }

        Iterator<Device> deviceIterator = null;
        if (index == null) {
            // Do a full table scan
            deviceIterator = deviceMap.values().iterator();
        } else {
            // index lookup
            Entity entity = new Entity((macAddress == null ? 0 : macAddress),
                                vlan,
                                ipv4Address,
                                switchDPID,
                                switchPort,
                                null);
            deviceIterator =
                new DeviceIndexInterator(this, index.queryByEntity(entity));
        }

        DeviceIterator di =
            new DeviceIterator(deviceIterator,
                                null,
                                macAddress,
                                vlan,
                                ipv4Address,
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
    public void addSuppressAPs(long swId, short port) {
        suppressAPs.add(new SwitchPort(swId, port));
    }

    @Override
    public void removeSuppressAPs(long swId, short port) {
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

    // ***************
    // IDeviceListener
    // ***************
    private class DeviceDebugEventLogger implements IDeviceListener {
        @Override
        public String getName() {
            return "deviceDebugEventLogger";
        }

        @Override
        public boolean isCallbackOrderingPrereq(String type, String name) {
            return false;
        }

        @Override
        public boolean isCallbackOrderingPostreq(String type, String name) {
            return false;
        }

        @Override
        public void deviceAdded(IDevice device) {
            generateDeviceEvent(device, "host-added");
        }

        @Override
        public void deviceRemoved(IDevice device) {
            generateDeviceEvent(device, "host-removed");
        }

        @Override
        public void deviceMoved(IDevice device) {
            generateDeviceEvent(device, "host-moved");
        }

        @Override
        public void deviceIPV4AddrChanged(IDevice device) {
            generateDeviceEvent(device, "host-ipv4-addr-changed");
        }

        @Override
        public void deviceVlanChanged(IDevice device) {
            generateDeviceEvent(device, "host-vlan-changed");
        }

        private void generateDeviceEvent(IDevice device, String reason) {
            List<Integer> ipv4Addresses =
                new ArrayList<Integer>(Arrays.asList(device.getIPv4Addresses()));
            List<SwitchPort> oldAps =
                new ArrayList<SwitchPort>(Arrays.asList(device.getOldAP()));
            List<SwitchPort> currentAps =
                    new ArrayList<SwitchPort>(Arrays.asList(device.getAttachmentPoints()));
            List<Short> vlanIds =
                    new ArrayList<Short>(Arrays.asList(device.getVlanId()));

            evDevice.updateEventNoFlush(
                    new DeviceEvent(device.getMACAddress(),
                                    ipv4Addresses,
                                    oldAps,
                                    currentAps,
                                    vlanIds, reason));
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
                cntIncoming.updateCounterNoFlush();
                return this.processPacketInMessage(sw,
                                                   (OFPacketIn) msg, cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }

    // ***************
    // IFlowReconcileListener
    // ***************
    @Override
    public Command reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList) {
        ListIterator<OFMatchReconcile> iter = ofmRcList.listIterator();
        while (iter.hasNext()) {
            OFMatchReconcile ofm = iter.next();

            // Remove the STOPPed flow.
            if (Command.STOP == reconcileFlow(ofm)) {
                iter.remove();
            }
        }

        if (ofmRcList.size() > 0) {
            return Command.CONTINUE;
        } else {
            return Command.STOP;
        }
    }

    protected Command reconcileFlow(OFMatchReconcile ofm) {
        cntReconcileRequest.updateCounterNoFlush();
        // Extract source entity information
        Entity srcEntity =
                getEntityFromFlowMod(ofm.ofmWithSwDpid, true);
        if (srcEntity == null) {
            cntReconcileNoSource.updateCounterNoFlush();
            return Command.STOP;
       }

        // Find the device by source entity
        Device srcDevice = findDeviceByEntity(srcEntity);
        if (srcDevice == null)  {
            cntReconcileNoSource.updateCounterNoFlush();
            return Command.STOP;
        }
        // Store the source device in the context
        fcStore.put(ofm.cntx, CONTEXT_SRC_DEVICE, srcDevice);

        // Find the device matching the destination from the entity
        // classes of the source.
        Entity dstEntity = getEntityFromFlowMod(ofm.ofmWithSwDpid, false);
        Device dstDevice = null;
        if (dstEntity != null) {
            dstDevice = findDestByEntity(srcDevice.getEntityClass(), dstEntity);
            if (dstDevice != null)
                fcStore.put(ofm.cntx, CONTEXT_DST_DEVICE, dstDevice);
            else
                cntReconcileNoDest.updateCounterNoFlush();
        } else {
            cntReconcileNoDest.updateCounterNoFlush();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Reconciling flow: match={}, srcEntity={}, srcDev={}, "
                         + "dstEntity={}, dstDev={}",
                         new Object[] {ofm.ofmWithSwDpid.getOfMatch(),
                                       srcEntity, srcDevice,
                                       dstEntity, dstDevice } );
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
        l.add(IFlowReconcileService.class);
        l.add(IEntityClassifierService.class);
        l.add(ISyncService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext fmc) throws FloodlightModuleException {
        this.perClassIndices =
                new HashSet<EnumSet<DeviceField>>();
        addIndex(true, EnumSet.of(DeviceField.IPV4));

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
        this.flowReconcileMgr = fmc.getServiceImpl(IFlowReconcileService.class);
        this.flowReconcileEngine = fmc.getServiceImpl(IFlowReconcileEngineService.class);
        this.entityClassifier = fmc.getServiceImpl(IEntityClassifierService.class);
        this.debugCounters = fmc.getServiceImpl(IDebugCounterService.class);
        this.debugEvents = fmc.getServiceImpl(IDebugEventService.class);
        this.syncService = fmc.getServiceImpl(ISyncService.class);
        this.deviceSyncManager = new DeviceSyncManager();
        this.haListenerDelegate = new HAListenerDelegate();
        registerDeviceManagerDebugCounters();
        registerDeviceManagerDebugEvents();
        this.addListener(new DeviceDebugEventLogger());
    }

    private void registerDeviceManagerDebugEvents() throws FloodlightModuleException {
        if (debugEvents == null) {
            debugEvents = new NullDebugEvent();
        }
        try {
            evDevice =
                debugEvents.registerEvent(PACKAGE, "hostevent",
                                          "Host added, removed, updated, or moved",
                                          EventType.ALWAYS_LOG, DeviceEvent.class, 500);
        } catch (MaxEventsRegistered e) {
            throw new FloodlightModuleException("Max events registered", e);
        }
    }

    @Override
    public void startUp(FloodlightModuleContext fmc)
            throws FloodlightModuleException {
        isMaster = (floodlightProvider.getRole() == Role.MASTER);
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
        flowReconcileMgr.addFlowReconcileListener(this);
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
            debugCounters = new NullDebugCounter();
        }
        try {
            cntIncoming = debugCounters.registerCounter(PACKAGE, "incoming",
                "All incoming packets seen by this module", CounterType.ALWAYS_COUNT);
            cntReconcileRequest = debugCounters.registerCounter(PACKAGE,
                "reconcile-request",
                "Number of flows that have been received for reconciliation by " +
                "this module",
                CounterType.ALWAYS_COUNT);
            cntReconcileNoSource = debugCounters.registerCounter(PACKAGE,
                "reconcile-no-source-device",
                "Number of flow reconcile events that failed because no source " +
                "device could be identified",
                CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN); // is this really a IDebugCounterService.CTR_MDATA_WARNing
            cntReconcileNoDest = debugCounters.registerCounter(PACKAGE,
                "reconcile-no-dest-device",
                "Number of flow reconcile events that failed because no " +
                "destination device could be identified",
                CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN); // is this really a IDebugCounterService.CTR_MDATA_WARNing
            cntInvalidSource = debugCounters.registerCounter(PACKAGE,
                "invalid-source",
                "Number of packetIns that were discarded because the source " +
                "MAC was invalid (broadcast, multicast, or zero)",
                CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN);
            cntNoSource = debugCounters.registerCounter(PACKAGE, "no-source-device",
                 "Number of packetIns that were discarded because the " +
                 "could not identify a source device. This can happen if a " +
                 "packet is not allowed, appears on an illegal port, does not " +
                 "have a valid address space, etc.",
                 CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN);
            cntInvalidDest = debugCounters.registerCounter(PACKAGE,
                "invalid-dest",
                "Number of packetIns that were discarded because the dest " +
                "MAC was invalid (zero)",
                 CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN);
            cntNoDest = debugCounters.registerCounter(PACKAGE, "no-dest-device",
                 "Number of packetIns that did not have an associated " +
                 "destination device. E.g., because the destination MAC is " +
                 "broadcast/multicast or is not yet known to the controller.",
                 CounterType.ALWAYS_COUNT);
            cntDhcpClientNameSnooped = debugCounters.registerCounter(PACKAGE,
                 "dhcp-client-name-snooped",
                 "Number of times a DHCP client name was snooped from a " +
                 "packetIn.",
                 CounterType.ALWAYS_COUNT);
            cntDeviceOnInternalPortNotLearned = debugCounters.registerCounter(
                 PACKAGE,
                 "device-on-internal-port-not-learned",
                 "Number of times packetIn was received on an internal port and" +
                 "no source device is known for the source MAC. The packetIn is " +
                 "discarded.",
                 CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN);
            cntPacketNotAllowed = debugCounters.registerCounter(PACKAGE,
                 "packet-not-allowed",
                 "Number of times a packetIn was not allowed due to spoofing " +
                 "protection configuration.",
                 CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN); // is this really a IDebugCounterService.CTR_MDATA_WARNing?
            cntNewDevice = debugCounters.registerCounter(PACKAGE, "new-device",
                 "Number of times a new device was learned",
                 CounterType.ALWAYS_COUNT);
            cntPacketOnInternalPortForKnownDevice = debugCounters.registerCounter(
                 PACKAGE,
                 "packet-on-internal-port-for-known-device",
                 "Number of times a packetIn was received on an internal port " +
                 "for a known device.",
                 CounterType.ALWAYS_COUNT);
            cntNewEntity = debugCounters.registerCounter(PACKAGE, "new-entity",
                 "Number of times a new entity was learned for an existing device",
                 CounterType.ALWAYS_COUNT);
            cntDeviceChanged = debugCounters.registerCounter(PACKAGE, "device-changed",
                 "Number of times device properties have changed",
                 CounterType.ALWAYS_COUNT);
            cntDeviceMoved = debugCounters.registerCounter(PACKAGE, "device-moved",
                 "Number of times devices have moved",
                 CounterType.ALWAYS_COUNT);
            cntCleanupEntitiesRuns = debugCounters.registerCounter(PACKAGE,
                 "cleanup-entities-runs",
                 "Number of times the entity cleanup task has been run",
                 CounterType.ALWAYS_COUNT);
            cntEntityRemovedTimeout = debugCounters.registerCounter(PACKAGE,
                 "entity-removed-timeout",
                 "Number of times entities have been removed due to timeout " +
                 "(entity has been inactive for " + ENTITY_TIMEOUT/1000 + "s)",
                 CounterType.ALWAYS_COUNT);
            cntDeviceDeleted = debugCounters.registerCounter(PACKAGE, "device-deleted",
                 "Number of devices that have been removed due to inactivity",
                 CounterType.ALWAYS_COUNT);
            cntDeviceReclassifyDelete = debugCounters.registerCounter(PACKAGE,
                 "device-reclassify-delete",
                 "Number of devices that required reclassification and have been " +
                 "temporarily delete for reclassification",
                 CounterType.ALWAYS_COUNT);
            cntDeviceStrored = debugCounters.registerCounter(PACKAGE, "device-stored",
                 "Number of device entries written or updated to the sync store",
                 CounterType.ALWAYS_COUNT);
            cntDeviceStoreThrottled = debugCounters.registerCounter(PACKAGE,
                 "device-store-throttled",
                 "Number of times a device update to the sync store was " +
                 "requested but not performed because the same device entities " +
                 "have recently been updated already",
                 CounterType.ALWAYS_COUNT);
            cntDeviceRemovedFromStore = debugCounters.registerCounter(PACKAGE,
                 "device-removed-from-store",
                 "Number of devices that were removed from the sync store " +
                 "because the local controller removed the device due to " +
                 "inactivity",
                 CounterType.ALWAYS_COUNT);
            cntSyncException = debugCounters.registerCounter(PACKAGE, "sync-exception",
                 "Number of times an operation on the sync store resulted in " +
                 "sync exception",
                 CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN); // it this an error?
            cntDevicesFromStore = debugCounters.registerCounter(PACKAGE,
                 "devices-from-store",
                 "Number of devices that were read from the sync store after " +
                 "the local controller transitioned from SLAVE to MASTER",
                 CounterType.ALWAYS_COUNT);
            cntConsolidateStoreRuns = debugCounters.registerCounter(PACKAGE,
                 "consolidate-store-runs",
                 "Number of times the task to consolidate entries in the " +
                 "store witch live known devices has been run",
                 CounterType.ALWAYS_COUNT);
            cntConsolidateStoreDevicesRemoved = debugCounters.registerCounter(PACKAGE,
                 "consolidate-store-devices-removed",
                 "Number of times a device has been removed from the sync " +
                 "store because no corresponding live device is known. " +
                 "This indicates a remote controller still writing device " +
                 "entries despite the local controller being MASTER or an " +
                 "incosistent store update from the local controller.",
                 CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN);
            cntTransitionToMaster = debugCounters.registerCounter(PACKAGE,
                 "transition-to-master",
                 "Number of times this controller has transitioned from SLAVE " +
                 "to MASTER role. Will be 0 or 1.",
                 CounterType.ALWAYS_COUNT);
        } catch (CounterException e) {
            throw new FloodlightModuleException(e.getMessage());
        }
    }

    // ***************
    // IHAListener
    // ***************

    protected class HAListenerDelegate implements IHAListener {
        @Override
        public void transitionToMaster() {
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
            // TODO Auto-generated method stub
            return false;
        }
    }


    // ****************
    // Internal methods
    // ****************

    protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                             FloodlightContext cntx) {
        Ethernet eth =
                IFloodlightProviderService.bcStore.
                get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // Extract source entity information
        Entity srcEntity =
                getSourceEntityFromPacket(eth, sw.getId(), pi.getInPort());
        if (srcEntity == null) {
            cntInvalidSource.updateCounterNoFlush();
            return Command.STOP;
        }

        // Learn from ARP packet for special VRRP settings.
        // In VRRP settings, the source MAC address and sender MAC
        // addresses can be different.  In such cases, we need to learn
        // the IP to MAC mapping of the VRRP IP address.  The source
        // entity will not have that information.  Hence, a separate call
        // to learn devices in such cases.
        learnDeviceFromArpResponseData(eth, sw.getId(), pi.getInPort());

        // Learn/lookup device information
        Device srcDevice = learnDeviceByEntity(srcEntity);
        if (srcDevice == null) {
            cntNoSource.updateCounterNoFlush();
            return Command.STOP;
        }

        // Store the source device in the context
        fcStore.put(cntx, CONTEXT_SRC_DEVICE, srcDevice);

        // Find the device matching the destination from the entity
        // classes of the source.
        if (eth.getDestinationMAC().toLong() == 0) {
            cntInvalidDest.updateCounterNoFlush();
            return Command.STOP;
        }
        Entity dstEntity = getDestEntityFromPacket(eth);
        Device dstDevice = null;
        if (dstEntity != null) {
            dstDevice =
                    findDestByEntity(srcDevice.getEntityClass(), dstEntity);
            if (dstDevice != null)
                fcStore.put(cntx, CONTEXT_DST_DEVICE, dstDevice);
            else
                cntNoDest.updateCounterNoFlush();
        } else {
            cntNoDest.updateCounterNoFlush();
        }

       if (logger.isTraceEnabled()) {
           logger.trace("Received PI: {} on switch {}, port {} *** eth={}" +
                        " *** srcDev={} *** dstDev={} *** ",
                        new Object[] { pi, sw.getStringId(), pi.getInPort(), eth,
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
                cntDhcpClientNameSnooped.updateCounterNoFlush();
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
    public boolean isValidAttachmentPoint(long switchDPID,
                                             int switchPort) {
        if (topology.isAttachmentPointPort(switchDPID,
                                           (short)switchPort) == false)
            return false;

        if (suppressAPs.contains(new SwitchPort(switchDPID, switchPort)))
            return false;

        return true;
    }

    /**
     * Get sender IP address from packet if the packet is an ARP
     * packet and if the source MAC address matches the ARP packets
     * sender MAC address.
     * @param eth
     * @param dlAddr
     * @return
     */
    private int getSrcNwAddr(Ethernet eth, long dlAddr) {
        if (eth.getPayload() instanceof ARP) {
            ARP arp = (ARP) eth.getPayload();
            if ((arp.getProtocolType() == ARP.PROTO_TYPE_IP) &&
                    (Ethernet.toLong(arp.getSenderHardwareAddress()) == dlAddr)) {
                return IPv4.toIPv4Address(arp.getSenderProtocolAddress());
            }
        }
        return 0;
    }

    /**
     * Parse an entity from an {@link Ethernet} packet.
     * @param eth the packet to parse
     * @param sw the switch on which the packet arrived
     * @param pi the original packetin
     * @return the entity from the packet
     */
    protected Entity getSourceEntityFromPacket(Ethernet eth,
                                             long swdpid,
                                             int port) {
        byte[] dlAddrArr = eth.getSourceMACAddress();
        long dlAddr = Ethernet.toLong(dlAddrArr);

        // Ignore broadcast/multicast source
        if ((dlAddrArr[0] & 0x1) != 0)
            return null;
        // Ignore 0 source mac
        if (dlAddr == 0)
            return null;

        short vlan = eth.getVlanID();
        int nwSrc = getSrcNwAddr(eth, dlAddr);
        return new Entity(dlAddr,
                          ((vlan >= 0) ? vlan : null),
                          ((nwSrc != 0) ? nwSrc : null),
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
                                            long swdpid,
                                            int port) {

        if (!(eth.getPayload() instanceof ARP)) return;
        ARP arp = (ARP) eth.getPayload();

        byte[] dlAddrArr = eth.getSourceMACAddress();
        long dlAddr = Ethernet.toLong(dlAddrArr);

        byte[] senderHardwareAddr = arp.getSenderHardwareAddress();
        long senderAddr = Ethernet.toLong(senderHardwareAddr);

        if (dlAddr == senderAddr) return;

        // Ignore broadcast/multicast source
        if ((senderHardwareAddr[0] & 0x1) != 0)
            return;
        // Ignore zero sender mac
        if (senderAddr == 0)
            return;

        short vlan = eth.getVlanID();
        int nwSrc = IPv4.toIPv4Address(arp.getSenderProtocolAddress());

        Entity e =  new Entity(senderAddr,
                ((vlan >= 0) ? vlan : null),
                ((nwSrc != 0) ? nwSrc : null),
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
        byte[] dlAddrArr = eth.getDestinationMACAddress();
        long dlAddr = Ethernet.toLong(dlAddrArr);
        short vlan = eth.getVlanID();
        int nwDst = 0;

        // Ignore broadcast/multicast destination
        if ((dlAddrArr[0] & 0x1) != 0)
            return null;
        // Ignore zero dest mac
        if (dlAddr == 0)
            return null;

        if (eth.getPayload() instanceof IPv4) {
            IPv4 ipv4 = (IPv4) eth.getPayload();
            nwDst = ipv4.getDestinationAddress();
        }

        return new Entity(dlAddr,
                          ((vlan >= 0) ? vlan : null),
                          ((nwDst != 0) ? nwDst : null),
                          null,
                          null,
                          null);
    }

    /**
     * Parse an entity from an OFMatchWithSwDpid.
     * @param ofmWithSwDpid
     * @return the entity from the packet
     */
    private Entity getEntityFromFlowMod(OFMatchWithSwDpid ofmWithSwDpid,
                boolean isSource) {
        byte[] dlAddrArr = ofmWithSwDpid.getOfMatch().getDataLayerSource();
        int nwSrc = ofmWithSwDpid.getOfMatch().getNetworkSource();
        if (!isSource) {
            dlAddrArr = ofmWithSwDpid.getOfMatch().getDataLayerDestination();
            nwSrc = ofmWithSwDpid.getOfMatch().getNetworkDestination();
        }

        long dlAddr = Ethernet.toLong(dlAddrArr);

        // Ignore broadcast/multicast source
        if ((dlAddrArr[0] & 0x1) != 0)
            return null;

        Long swDpid = null;
        Short inPort = null;

        if (isSource) {
            swDpid = ofmWithSwDpid.getSwitchDataPathId();
            inPort = ofmWithSwDpid.getOfMatch().getInputPort();
        }

        /**for the new flow cache design, the flow mods retrived are not always
         * from the source, learn AP should be disabled --meiyang*/
        boolean learnap = false;
        /**
         * if (swDpid == null ||
            inPort == null ||
            !isValidAttachmentPoint(swDpid, inPort)) {
            // If this is an internal port or we otherwise don't want
            // to learn on these ports.  In the future, we should
            // handle this case by labeling flows with something that
            // will give us the entity class.  For now, we'll do our
            // best assuming attachment point information isn't used
            // as a key field.
            learnap = false;
        }
        */

        short vlan = ofmWithSwDpid.getOfMatch().getDataLayerVirtualLan();
        return new Entity(dlAddr,
                          ((vlan >= 0) ? vlan : null),
                          ((nwSrc != 0) ? nwSrc : null),
                          (learnap ? swDpid : null),
                          (learnap ? (int)inPort : null),
                          new Date());
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
                deviceKey =
                        classState.classIndex.findByEntity(entity);
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
                    deviceKey =
                            classState.classIndex.findByEntity(entity);
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
                if (entity.hasSwitchPort() &&
                        !topology.isAttachmentPointPort(entity.getSwitchDPID(),
                                                 entity.getSwitchPort().shortValue())) {
                    cntDeviceOnInternalPortNotLearned.updateCounterNoFlush();
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
                    cntPacketNotAllowed.updateCounterNoFlush();
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
                cntNewDevice.updateCounterNoFlush();
                if (logger.isDebugEnabled()) {
                    logger.debug("New device created: {} deviceKey={}, entity={}",
                                 new Object[]{device, deviceKey, entity});
                }
                // generate new device update
                deviceUpdates =
                        updateUpdates(deviceUpdates,
                                      new DeviceUpdate(device, ADD, null));

                break;
            }
            // if it gets here, we have a pre-existing Device for this Entity
            if (!isEntityAllowed(entity, device.getEntityClass())) {
                cntPacketNotAllowed.updateCounterNoFlush();
                if (logger.isDebugEnabled()) {
                    logger.info("PacketIn is not allowed {} {}",
                                device.getEntityClass().getName(), entity);
                }
                return null;
            }
            // If this is not an attachment point port we don't learn the new entity
            // and don't update indexes. But we do allow the device to continue up
            // the chain.
            if (entity.hasSwitchPort() &&
                    !topology.isAttachmentPointPort(entity.getSwitchDPID(),
                                                 entity.getSwitchPort().shortValue())) {
                cntPacketOnInternalPortForKnownDevice.updateCounterNoFlush();
                break;
            }
            int entityindex = -1;
            if ((entityindex = device.entityIndex(entity)) >= 0) {
                // Entity already exists
                // update timestamp on the found entity
                Date lastSeen = entity.getLastSeenTimestamp();
                if (lastSeen == null) {
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
                EnumSet<DeviceField> changedFields =
                        findChangedFields(device, entity);

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
                cntNewEntity.updateCounterNoFlush();
                if (changedFields.size() > 0) {
                    cntDeviceChanged.updateCounterNoFlush();
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
                boolean moved =
                        device.updateAttachmentPoint(entity.getSwitchDPID(),
                                entity.getSwitchPort().shortValue(),
                                entity.getLastSeenTimestamp().getTime());
                // TODO: use update mechanism instead of sending the
                // notification directly
                if (moved) {
                    // we count device moved events in sendDeviceMovedNotification()
                    sendDeviceMovedNotification(device);
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
                EnumSet.of(DeviceField.IPV4,
                           DeviceField.VLAN,
                           DeviceField.SWITCH);

        if (newEntity.getIpv4Address() == null)
            changedFields.remove(DeviceField.IPV4);
        if (newEntity.getVlan() == null)
            changedFields.remove(DeviceField.VLAN);
        if (newEntity.getSwitchDPID() == null ||
                newEntity.getSwitchPort() == null)
            changedFields.remove(DeviceField.SWITCH);

        if (changedFields.size() == 0) return changedFields;

        for (Entity entity : device.getEntities()) {
            if (newEntity.getIpv4Address() == null ||
                    (entity.getIpv4Address() != null &&
                    entity.getIpv4Address().equals(newEntity.getIpv4Address())))
                changedFields.remove(DeviceField.IPV4);
            if (newEntity.getVlan() == null ||
                    (entity.getVlan() != null &&
                    entity.getVlan().equals(newEntity.getVlan())))
                changedFields.remove(DeviceField.VLAN);
            if (newEntity.getSwitchDPID() == null ||
                    newEntity.getSwitchPort() == null ||
                    (entity.getSwitchDPID() != null &&
                    entity.getSwitchPort() != null &&
                    entity.getSwitchDPID().equals(newEntity.getSwitchDPID()) &&
                    entity.getSwitchPort().equals(newEntity.getSwitchPort())))
                changedFields.remove(DeviceField.SWITCH);
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
            if (update.change == DeviceUpdate.Change.DELETE)
                deviceSyncManager.removeDevice(update.device);
            else
                deviceSyncManager.storeDevice(update.device);
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
                            case IPV4:
                                listener.deviceIPV4AddrChanged(update.device);
                                break;
                            case SWITCH:
                            case PORT:
                                //listener.deviceMoved(update.device);
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
                case IPV4:
                    if (e.ipv4Address == null) return false;
                    break;
                case SWITCH:
                    if (e.switchDPID == null) return false;
                    break;
                case PORT:
                    if (e.switchPort == null) return false;
                    break;
                case VLAN:
                    // FIXME: vlan==null is ambiguous: it can mean: not present
                    // or untagged
                    //if (e.vlan == null) return false;
                    break;
                default:
                    // we should never get here. unless somebody extended
                    // DeviceFields
                    throw new IllegalStateException();
            }
        }
        return true;
    }

    private LinkedList<DeviceUpdate>
    updateUpdates(LinkedList<DeviceUpdate> list, DeviceUpdate update) {
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
        cntCleanupEntitiesRuns.updateCounterWithFlush();

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
                    if (e.getLastSeenTimestamp() != null &&
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

                cntEntityRemovedTimeout.updateCounterWithFlush();
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
                        cntDeviceChanged.updateCounterWithFlush();
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
                        cntDeviceDeleted.updateCounterWithFlush();
                    }
                    deviceUpdates.add(update);
                }
                processUpdates(deviceUpdates);
                break;
            }
        }
        // Since cleanupEntities() is not called in the packet-in pipeline,
        // debugEvents need to be flushed explicitly
        debugEvents.flushEvents();
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

    private EnumSet<DeviceField> getEntityKeys(Long macAddress,
                                               Short vlan,
                                               Integer ipv4Address,
                                               Long switchDPID,
                                               Integer switchPort) {
        // FIXME: vlan==null is a valid search. Need to handle this
        // case correctly. Note that the code will still work correctly.
        // But we might do a full device search instead of using an index.
        EnumSet<DeviceField> keys = EnumSet.noneOf(DeviceField.class);
        if (macAddress != null) keys.add(DeviceField.MAC);
        if (vlan != null) keys.add(DeviceField.VLAN);
        if (ipv4Address != null) keys.add(DeviceField.IPV4);
        if (switchDPID != null) keys.add(DeviceField.SWITCH);
        if (switchPort != null) keys.add(DeviceField.PORT);
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

    // TODO: FIX THIS.
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
    protected Device allocateDevice(Device device, Set <Entity> entities) {
        List <AttachmentPoint> newPossibleAPs =
                new ArrayList<AttachmentPoint>();
        List <AttachmentPoint> newAPs =
                new ArrayList<AttachmentPoint>();
        for (Entity entity : entities) {
            if (entity.switchDPID != null && entity.switchPort != null) {
                AttachmentPoint aP =
                        new AttachmentPoint(entity.switchDPID.longValue(),
                                    entity.switchPort.shortValue(), 0);
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
    }

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
        // Since topologyChanged() does not occur in the packet-in pipeline,
        // debugEvents need to be flushed explicitly
        debugEvents.flushEvents();
    }

    /**
     * Send update notifications to listeners
     * @param updates the updates to process.
     */
    protected void sendDeviceMovedNotification(Device d) {
        cntDeviceMoved.updateCounterNoFlush();
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

        cntDeviceReclassifyDelete.updateCounterNoFlush();
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
        // Since reclassifyDevices() is not called in the packet-in pipeline,
        // debugEvents need to be flushed explicitly
        debugEvents.flushEvents();
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
        private final ConcurrentMap<Long, Long> lastWriteTimes =
                new ConcurrentHashMap<Long, Long>();

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
            if (last == null ||
                    now - last > intervalNs) {
                writeUpdatedDeviceToStorage(d);
                lastWriteTimes.put(d.getDeviceKey(), now);
            } else {
                cntDeviceStoreThrottled.updateCounterWithFlush();
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
                cntDeviceRemovedFromStore.updateCounterWithFlush();
                storeClient.delete(DeviceSyncRepresentation.computeKey(d));
            } catch(ObsoleteVersionException e) {
                // FIXME
            } catch (SyncException e) {
                cntSyncException.updateCounterWithFlush();
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
                cntDeviceRemovedFromStore.updateCounterWithFlush();
                storeClient.delete(dev.getValue().getKey(),
                                   dev.getVersion());
            } catch(ObsoleteVersionException e) {
                // Key was locally modified by another thread.
                // Do not delete and ignore.
            } catch(SyncException e) {
                cntSyncException.updateCounterWithFlush();
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
            cntTransitionToMaster.updateCounterWithFlush();
            IClosableIterator<Map.Entry<String,Versioned<DeviceSyncRepresentation>>>
                    iter = null;
            try {
                iter = storeClient.entries();
            } catch (SyncException e) {
                cntSyncException.updateCounterWithFlush();
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
                    cntDevicesFromStore.updateCounterWithFlush();
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
                cntDeviceStrored.updateCounterWithFlush();
                // FIXME: use a versioned put
                DeviceSyncRepresentation storeDevice =
                        new DeviceSyncRepresentation(device);
                storeClient.put(storeDevice.getKey(), storeDevice);
            } catch (ObsoleteVersionException e) {
                // FIXME: what's the right behavior here. Can the store client
                // even throw this error?
            } catch (SyncException e) {
                cntSyncException.updateCounterWithFlush();
                logger.error("Could not write device " + device +
                          " to sync store:", e);
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
            cntConsolidateStoreRuns.updateCounterWithFlush();
            if (logger.isDebugEnabled()) {
                logger.debug("Running consolidateStore.");
            }
            IClosableIterator<Map.Entry<String,Versioned<DeviceSyncRepresentation>>>
                    iter = null;
            try {
                iter = storeClient.entries();
            } catch (SyncException e) {
                cntSyncException.updateCounterWithFlush();
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
                            IDevice d = findDevice(se.macAddress, se.vlan,
                                                   se.ipv4Address,
                                                   se.switchDPID,
                                                   se.switchPort);
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
                        cntConsolidateStoreDevicesRemoved.updateCounterWithFlush();
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

    /**
     * Device Event Class used to log Device related events
     */
    private class DeviceEvent {
        @EventColumn(name = "MAC", description = EventFieldType.MAC)
        private final long macAddress;
        @EventColumn(name = "IPs", description = EventFieldType.LIST_IPV4)
        private final List<Integer> ipv4Addresses;
        @EventColumn(name = "Old Attachment Points",
                     description = EventFieldType.LIST_ATTACHMENT_POINT)
        private final List<SwitchPort> oldAttachmentPoints;
        @EventColumn(name = "Current Attachment Points",
                     description = EventFieldType.LIST_ATTACHMENT_POINT)
        private final List<SwitchPort> currentAttachmentPoints;
        @EventColumn(name = "VLAN IDs", description = EventFieldType.LIST_OBJECT)
        private final List<Short> vlanIds;
        @EventColumn(name = "Reason", description = EventFieldType.STRING)
        private final String reason;

        public DeviceEvent(long macAddress, List<Integer> ipv4Addresses,
                List<SwitchPort> oldAttachmentPoints,
                List<SwitchPort> currentAttachmentPoints,
                List<Short> vlanIds, String reason) {
            super();
            this.macAddress = macAddress;
            this.ipv4Addresses = ipv4Addresses;
            this.oldAttachmentPoints = oldAttachmentPoints;
            this.currentAttachmentPoints = currentAttachmentPoints;
            this.vlanIds = vlanIds;
            this.reason = reason;
        }
    }
}
