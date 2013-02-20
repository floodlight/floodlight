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
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassListener;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.web.DeviceRoutable;
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
IFlowReconcileListener, IInfoProvider, IHAListener {
    protected static Logger logger =
            LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected IFloodlightProviderService floodlightProvider;
    protected ITopologyService topology;
    protected IStorageSourceService storageSource;
    protected IRestApiService restApi;
    protected IThreadPoolService threadPool;
    protected IFlowReconcileService flowReconcileMgr;

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
    protected long deviceKeyCounter = 0;

    /**
     * Lock for incrementing the device key counter
     */
    protected Object deviceKeyLock = new Object();

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
        protected IDevice device;

        /**
         * The change that was made
         */
        protected Change change;

        /**
         * If not added, then this is the list of fields changed
         */
        protected EnumSet<DeviceField> fieldsChanged;

        public DeviceUpdate(IDevice device, Change change,
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
        return "devicemanager";
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
        // Extract source entity information
        Entity srcEntity =
                getEntityFromFlowMod(ofm.ofmWithSwDpid, true);
        if (srcEntity == null)
            return Command.STOP;

        // Find the device by source entity
        Device srcDevice = findDeviceByEntity(srcEntity);
        if (srcDevice == null)
            return Command.STOP;

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
        return l;
    }

    @Override
    public void init(FloodlightModuleContext fmc) {
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
        this.entityClassifier = fmc.getServiceImpl(IEntityClassifierService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext fmc) {
        primaryIndex = new DeviceUniqueIndex(entityClassifier.getKeyFields());
        secondaryIndexMap = new HashMap<EnumSet<DeviceField>, DeviceIndex>();

        deviceMap = new ConcurrentHashMap<Long, Device>();
        classStateMap =
                new ConcurrentHashMap<String, ClassState>();
        apComparator = new AttachmentPointComparator();

        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addHAListener(this);
        if (topology != null)
            topology.addListener(this);
        flowReconcileMgr.addFlowReconcileListener(this);
        entityClassifier.addListener(this);

        Runnable ecr = new Runnable() {
            @Override
            public void run() {
                cleanupEntities();
                entityCleanupTask.reschedule(ENTITY_CLEANUP_INTERVAL,
                                             TimeUnit.SECONDS);
            }
        };
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        entityCleanupTask = new SingletonTask(ses, ecr);
        entityCleanupTask.reschedule(ENTITY_CLEANUP_INTERVAL,
                                     TimeUnit.SECONDS);

        if (restApi != null) {
            restApi.addRestletRoutable(new DeviceRoutable());
        } else {
            logger.debug("Could not instantiate REST API");
        }
    }

    // ***************
    // IHAListener
    // ***************

    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case SLAVE:
                logger.debug("Resetting device state because of role change");
                startUp(null);
                break;
            default:
                break;
        }
    }

    @Override
    public void controllerNodeIPsChanged(
                                         Map<String, String> curControllerNodeIPs,
                                         Map<String, String> addedControllerNodeIPs,
                                         Map<String, String> removedControllerNodeIPs) {
        // no-op
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
        if (srcEntity == null)
            return Command.STOP;

        // Learn/lookup device information
        Device srcDevice = learnDeviceByEntity(srcEntity);
        if (srcDevice == null)
            return Command.STOP;

        // Store the source device in the context
        fcStore.put(cntx, CONTEXT_SRC_DEVICE, srcDevice);

        // Find the device matching the destination from the entity
        // classes of the source.
        Entity dstEntity = getDestEntityFromPacket(eth);
        Device dstDevice = null;
        if (dstEntity != null) {
            dstDevice =
                    findDestByEntity(srcDevice.getEntityClass(), dstEntity);
            if (dstDevice != null)
                fcStore.put(cntx, CONTEXT_DST_DEVICE, dstDevice);
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
        if (eth.getEtherType() != Ethernet.TYPE_IPv4)
            return;
        IPv4 ipv4 = (IPv4) eth.getPayload();
        if (ipv4.getProtocol() != IPv4.PROTOCOL_UDP)
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
     * Get sender IP address from packet if the packet is either an ARP 
     * packet. 
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

        boolean learnap = true;
        if (swDpid == null ||
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
     * @param source the source device.  The returned destination will be
     * in the same entity class as the source.
     * @param dstEntity the entity to look up
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
                }
            } else {
                // If the secondary index does not contain the entity,
                // create a new Device object containing the entity, and
                // generate a new device ID if the the entity is on an 
                // attachment point port. Otherwise ignore. 
                if (entity.hasSwitchPort() &&
                        !topology.isAttachmentPointPort(entity.getSwitchDPID(), 
                                                 entity.getSwitchPort().shortValue())) {
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
                    logger.info("PacketIn is not allowed {} {}", 
                                entityClass.getName(), entity);
                    device = null;
                    break;
                }
                synchronized (deviceKeyLock) {
                    deviceKey = Long.valueOf(deviceKeyCounter++);
                }
                device = allocateDevice(deviceKey, entity, entityClass);
                if (logger.isDebugEnabled()) {
                    logger.debug("New device created: {} deviceKey={}, entity={}",
                                 new Object[]{device, deviceKey, entity});
                }

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

                // generate new device update
                deviceUpdates =
                        updateUpdates(deviceUpdates,
                                      new DeviceUpdate(device, ADD, null));

                break;
            }

            if (!isEntityAllowed(entity, device.getEntityClass())) {
                logger.info("PacketIn is not allowed {} {}", 
                            device.getEntityClass().getName(), entity);
                return null;
            }
            // If this is an internal port we don't learn the new entity
            // and don't update indexes. We only learn on attachment point
            // ports.
            if (entity.hasSwitchPort() &&
                    !topology.isAttachmentPointPort(entity.getSwitchDPID(), 
                                                 entity.getSwitchPort().shortValue())) {
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
                // we break the loop after the else block and after checking
                // for new AP
            } else {
                // New entity for this devce
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
                
                if (changedFields.size() > 0) {
                    deviceUpdates =
                    updateUpdates(deviceUpdates,
                                  new DeviceUpdate(newDevice, CHANGE,
                                                   changedFields));
                }
                // we break the loop after checking for changed AP 
            }
            // Update attachment point (will only be hit if the device 
            // already existed and no concurrent modification
            if (entity.hasSwitchPort()) {
                boolean moved = 
                        device.updateAttachmentPoint(entity.getSwitchDPID(),
                                entity.getSwitchPort().shortValue(),
                                entity.getLastSeenTimestamp().getTime());
                // TODO: use update mechanism instead of sending the 
                // notification directly
                if (moved) {
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
     * Clean up expired entities/devices
     */
    protected void cleanupEntities () {

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

                for (Entity e : toRemove) {
                    removeEntity(e, d.getEntityClass(), d.deviceKey, toKeep);
                }

                if (toKeep.size() > 0) {
                    Device newDevice = allocateDevice(d.getDeviceKey(),
                                                      d.getDHCPClientName(),
                                                      d.oldAPs,
                                                      d.attachmentPoints,
                                                      toKeep,
                                                      d.entityClass);

                    EnumSet<DeviceField> changedFields =
                            EnumSet.noneOf(DeviceField.class);
                    for (Entity e : toRemove) {
                        changedFields.addAll(findChangedFields(newDevice, e));
                    }
                    DeviceUpdate update = null;
                    if (changedFields.size() > 0)
                        update = new DeviceUpdate(d, CHANGE, changedFields);

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
                    if (update != null)
                        deviceUpdates.add(update);
                } else {
                    DeviceUpdate update = new DeviceUpdate(d, DELETE, null);
                    if (!deviceMap.remove(d.getDeviceKey(), d)) {
                        // concurrent modification; try again
                        // need to use device that is the map now for the next
                        // iteration
                        d = deviceMap.get(d.getDeviceKey());
                        if (null != d)
                            continue;
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

    @Override
    public void addSuppressAPs(long swId, short port) {
        suppressAPs.add(new SwitchPort(swId, port));
    }

    @Override
    public void removeSuppressAPs(long swId, short port) {
        suppressAPs.remove(new SwitchPort(swId, port));
    }

    /**
     * Topology listener method.
     */
    @Override
    public void topologyChanged() {
        Iterator<Device> diter = deviceMap.values().iterator();
        List<LDUpdate> updateList = topology.getLastLinkUpdates();
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
        List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
        if (listeners != null) {
            for (IDeviceListener listener : listeners) {
                listener.deviceMoved(d);
            }
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
}
