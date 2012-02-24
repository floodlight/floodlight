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
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifier;
import net.floodlightcontroller.devicemanager.IEntityClassifier.EntityField;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.ITopologyListener;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPortStatus;
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
        IDeviceManagerService, IOFMessageListener,
        IOFSwitchListener, ITopologyListener, 
        IStorageSourceListener, IFloodlightModule {  
    protected static Logger logger = 
        LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected IFloodlightProviderService floodlightProvider;
    protected ITopologyService topology;
    protected IStorageSourceService storageSource;
    
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
     * This is the primary entity index that maps entities to device IDs.
     */
    protected ConcurrentHashMap<IndexedEntity, Long> primaryIndex;
    
    /**
     * The primary key fields used in the primary index
     */
    protected Set<EntityField> primaryKeyFields;
    protected EntityField[] primaryKeyFieldsArr;
    
    /**
     * This map contains state for each of the {@ref IEntityClass} 
     * that exist
     */
    protected ConcurrentHashMap<IEntityClass, ClassState> classStateMap;

    /**
     * The entity classifier currently in use
     */
    IEntityClassifier entityClassifier;
    
    /**
     * Used to cache state about specific entity classes
     */
    protected class ClassState {
        /**
         * True if the key field set matches the primary key fields.
         */
        protected boolean keyFieldsMatchPrimary;
        
        /**
         * An array version of the key fields
         */
        protected EntityField[] keyFieldsArr;
        
        /**
         * The class index
         */
        protected ConcurrentHashMap<IndexedEntity, Long> classIndex;

        /**
         * Allocate a new {@link ClassState} object for the class
         * @param clazz the class to use for the state
         */
        public ClassState(IEntityClass clazz) {
            Set<EntityField> keyFields = clazz.getKeyFields();
            keyFieldsMatchPrimary = primaryKeyFields.equals(keyFields);
            keyFieldsArr = new EntityField[keyFields.size()];
            keyFieldsArr = keyFields.toArray(keyFieldsArr);
            if (!keyFieldsMatchPrimary)
                classIndex = new ConcurrentHashMap<IndexedEntity, Long>();
        }
    }
    
    /**
     * Device manager event listeners
     */
    protected Set<IDeviceManagerAware> deviceManagerAware;
    
    // *********************
    // IDeviceManagerService
    // *********************

    @Override
    public void setEntityClassifier(IEntityClassifier classifier) {
        entityClassifier = classifier;
        primaryKeyFields = classifier.getKeyFields();
        primaryKeyFieldsArr = new EntityField[primaryKeyFields.size()];
        primaryKeyFieldsArr = primaryKeyFields.toArray(primaryKeyFieldsArr);
    }
    
    @Override
    public void flushEntityCache(IEntityClass entityClass, 
                                 boolean reclassify) {
        // TODO Auto-generated method stub
    }

    @Override
    public IDevice getDevice(Long deviceKey) {
        return deviceMap.get(deviceKey);
    }

    @Override
    public IDevice findDevice(long macAddress, Short vlan, 
                              Integer ipv4Address, Long switchDPID, 
                              Integer switchPort) {
        if (vlan != null && vlan.shortValue() < 0)
            vlan = null;
        return findDeviceByEntity(new Entity(macAddress, vlan, 
                                             ipv4Address, switchDPID, 
                                             switchPort, null));
    }

    @Override
    public IDevice findDestDevice(IDevice source, long macAddress,
                                  Short vlan, Integer ipv4Address) {
        if (vlan != null && vlan.shortValue() < 0)
            vlan = null;
        return findDestByEntity(source,
                                new Entity(macAddress, 
                                           vlan, 
                                           ipv4Address, 
                                           null, 
                                           null,
                                           null));
    }

    @Override
    public Collection<? extends IDevice> getAllDevices() {
        return Collections.unmodifiableCollection(deviceMap.values());
    }
    
    @Override
    public void addListener(IDeviceManagerAware listener) {
        deviceManagerAware.add(listener);
    }
    
    // ******************
    // IOFMessageListener
    // ******************

    @Override
    public String getName() {
        return "devicemanager";
    }

    @Override
    public int getId() {
        return IOFMessageListener.FlListenerID.DEVICEMANAGERIMPL;
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
    public Command receive(IOFSwitch sw, OFMessage msg, 
                           FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw, 
                                                   (OFPacketIn) msg, cntx);
            case PORT_STATUS:
                return this.processPortStatusMessage(sw, 
                                                     (OFPortStatus) msg);
        }

        logger.error("received an unexpected message {} from switch {}", 
                     msg, sw);
        return Command.CONTINUE;
    }
    
    // **********************
    // IStorageSourceListener
    // **********************
    
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        // TODO Auto-generated method stub
        
    }

    // **************
    // ITopologyAware
    // **************
    
    @Override
    public void addedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
                          IOFSwitch dstSw, short dstPort, int dstPortState) {
        // Nothing to do
    }

    @Override
    public void updatedLink(IOFSwitch srcSw, short srcPort,
                            int srcPortState, IOFSwitch dstSw,
                            short dstPort, int dstPortState) {
        // TODO Auto-generated method stub
    }

    @Override
    public void removedLink(IOFSwitch srcSw, short srcPort, IOFSwitch dstSw,
                            short dstPort) {
        // TODO Auto-generated method stub
    }

    @Override
    public void clusterMerged() {
        // TODO Auto-generated method stub
    }

    // *****************
    // IOFSwitchListener
    // *****************
    
    @Override
    public void updatedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
    }


    @Override
    public void addedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
    }

    // *****************
    // IFloodlightModule
    // *****************
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDeviceManagerService.class);
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
        m.put(IDeviceManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStorageSourceService.class);
        l.add(ITopologyService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext fmc) {
        this.deviceManagerAware = new HashSet<IDeviceManagerAware>();
        
        this.floodlightProvider = 
                fmc.getServiceImpl(IFloodlightProviderService.class);
        this.storageSource =
                fmc.getServiceImpl(IStorageSourceService.class);
        this.topology =
                fmc.getServiceImpl(ITopologyService.class);
    }
    
    @Override
    public void startUp(FloodlightModuleContext fmc) {
        if (entityClassifier == null)
            setEntityClassifier(new DefaultEntityClassifier());
        
        deviceMap = new ConcurrentHashMap<Long, Device>();
        primaryIndex = new ConcurrentHashMap<IndexedEntity, Long>();
        classStateMap = 
                new ConcurrentHashMap<IEntityClass, ClassState>();
        
        if (topology != null) {
            // Register to get updates from topology
            topology.addListener(this);
        } else {
            logger.error("Could add not toplogy listener");
        }
        
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        floodlightProvider.addOFSwitchListener(this);
        
        // XXX - TODO entity aging timer
    }
    
    // ****************
    // Internal methods
    // ****************
    
    protected Command processPortStatusMessage(IOFSwitch sw, OFPortStatus ps) {
        // XXX - TODO
        return null;        
    }

    /**
     * This method is called for every packet-in and should be optimized for
     * performance.
     * @param sw
     * @param pi
     * @param cntx
     * @return
     */
    protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, 
                                             FloodlightContext cntx) {
        try {
            Ethernet eth = 
                    IFloodlightProviderService.bcStore.
                    get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

            // Extract source entity information
            Entity srcEntity = 
                    getSourceEntityFromPacket(eth, sw, pi.getInPort());
            if (srcEntity == null)
                return Command.STOP;
            
            if (isGratArp(eth)) {
                // XXX - TODO - Clear attachment points from other clusters
            }
            
            // Learn/lookup device information
            Device srcDevice = learnDeviceByEntity(srcEntity);
            if (srcDevice == null)
                return Command.STOP;
            
            // Store the source device in the context
            fcStore.put(cntx, CONTEXT_SRC_DEVICE, srcDevice);
            
            // Find the device matching the destination from the entity
            // classes of the source.
            Entity dstEntity = getDestEntityFromPacket(eth);
            if (dstEntity != null) {
                Device dstDevice = 
                        findDestByEntity(srcDevice, dstEntity);
                if (dstDevice != null)
                    fcStore.put(cntx, CONTEXT_DST_DEVICE, dstDevice);
            }
                
            return Command.CONTINUE;

        } finally {
            processUpdates();
        }
        
    }
    
    private void processUpdates() {
        // XXX - TODO
    }
    
    /**
     * Check whether the port is a physical port. We should not learn 
     * attachment points on "special" ports.
     * @param port the port to check
     * @return
     */
    private boolean isValidInputPort(short port) {
        return ((int)port & 0xff00) != 0xff00 ||
                     port == (short)0xfffe;
    }
    
    private boolean isGratArp(Ethernet eth) {
        if (eth.getPayload() instanceof ARP) {
            ARP arp = (ARP) eth.getPayload();
            if (arp.isGratuitous()) {
                return true;
            }
        }
        return false;
    }

    private int getSrcNwAddr(Ethernet eth, long dlAddr) {
        if (eth.getPayload() instanceof ARP) {
            ARP arp = (ARP) eth.getPayload();
            if ((arp.getProtocolType() == ARP.PROTO_TYPE_IP) &&
                (Ethernet.toLong(arp.getSenderHardwareAddress()) == dlAddr)) {
                return IPv4.toIPv4Address(arp.getSenderProtocolAddress());
            }
        } else if (eth.getPayload() instanceof IPv4) {
            IPv4 ipv4 = (IPv4) eth.getPayload();
            if (ipv4.getPayload() instanceof UDP) {
                UDP udp = (UDP)ipv4.getPayload();
                if (udp.getPayload() instanceof DHCP) {
                    DHCP dhcp = (DHCP)udp.getPayload();
                    if (dhcp.getOpCode() == DHCP.OPCODE_REPLY) {
                        return ipv4.getSourceAddress();
                    }
                }
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
    private Entity getSourceEntityFromPacket(Ethernet eth, 
                                             IOFSwitch sw, 
                                             int port) {
        byte[] dlAddrArr = eth.getSourceMACAddress();
        long dlAddr = Ethernet.toLong(dlAddrArr);

        // Ignore broadcast/multicast source
        if ((dlAddrArr[0] & 0x1) != 0)
            return null;

        boolean learnap = true;
        if (topology.isInternal(sw, (short)port) ||
            !isValidInputPort((short)port)) {
            // If this is an internal port or we otherwise don't want
            // to learn on these ports.  In the future, we should
            // handle this case by labeling flows with something that
            // will give us the entity class.  For now, we'll do our
            // best assuming attachment point information isn't used
            // as a key field.
            learnap = false;
        }
       
        short vlan = eth.getVlanID();
        int nwSrc = getSrcNwAddr(eth, dlAddr);
        return new Entity(dlAddr,
                          ((vlan >= 0) ? vlan : null),
                          ((nwSrc != 0) ? nwSrc : null),
                          (learnap ? sw.getId() : null),
                          (learnap ? port : null),
                          new Date());
    }
    
    /**
     * Get a (partial) entity for the destination from the packet. 
     * @param eth
     * @return
     */
    private Entity getDestEntityFromPacket(Ethernet eth) {
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
     * Look up a {@link Device} based on the provided {@link Entity}.
     * @param entity the entity to search for
     * @return The {@link Device} object if found
     */
    protected Device findDeviceByEntity(Entity entity) {
        IndexedEntity ie = new IndexedEntity(primaryKeyFieldsArr, entity);
        Long deviceKey = primaryIndex.get(ie);
        if (deviceKey == null)
            return null;
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
    protected Device findDestByEntity(IDevice source,
                                      Entity dstEntity) {
        Device dstDevice = findDeviceByEntity(dstEntity);

        //if (dstDevice == null) {
            // This could happen because:
            // 1) no destination known, or a broadcast destination
            // 2) if we have attachment point key fields since 
            // attachment point information isn't available for
            // destination devices.
            // For the second case, we'll need to match up the 
            // destination device with the class of the source 
            // device.  
            /*
                ArrayList<Device> candidates = new ArrayList<Device>();
                for (IEntityClass clazz : srcDevice.getEntityClasses()) {
                    Device c = findDeviceInClassByEntity(clazz, dstEntity);
                    if (c != null)
                        candidates.add(c);
                }
                if (candidates.size() == 1) {
                    dstDevice = candidates.get(0);
                } else if (candidates.size() > 1) {
                    // ambiguous device.  A higher-order component will 
                    // need to deal with it by assigning priority
                    // XXX - TODO
                }
             */

        //}

        return dstDevice;
    }

    /**
     * Look up a {@link Device} within a particular entity class based on 
     * the provided {@link Entity}.
     * @param clazz the entity class to search for the entity
     * @param entity the entity to search for
     * @return The {@link Device} object if found
     */
    protected Device findDeviceInClassByEntity(IEntityClass clazz,
                                               Entity entity) {
        // XXX - TODO
        throw new UnsupportedOperationException();
    }
 
    /**
     * Look up a {@link Device} based on the provided {@link Entity}.  Also learns
     * based on the new entity, and will update existing devices as required. 
     * 
     * @param entity the {@link Entity}
     * @return The {@link Device} object if found
     */
    protected Device learnDeviceByEntity(Entity entity) {
        IndexedEntity ie = new IndexedEntity(primaryKeyFieldsArr, entity);
        ArrayList<Long> deleteQueue = null;
        Device device = null;
        
        // we may need to restart the learning process if we detect
        // concurrent modification.  Note that we ensure that at least
        // one thread should always succeed so we don't get into infinite
        // starvation loops
        while (true) {
            // Look up the fully-qualified entity to see if it already
            // exists in the primary entity index.
            Long deviceKey = primaryIndex.get(ie);
            Collection<IEntityClass> classes = null;
            
            if (deviceKey == null) {
                // If the entity does not exist in the primary entity index, 
                // use the entity classifier for find the classes for the 
                // entity. Look up the entity in each of the returned classes'
                // class entity indexes.
                classes = entityClassifier.classifyEntity(entity);
                for (IEntityClass clazz : classes) {
                    // ensure that a class state exists for the class if
                    // needed
                    ClassState classState;
                    try {
                        classState = getClassState(clazz);
                    } catch (ConcurrentModificationException e) {
                        continue;
                    }
                        
                    if (classState.classIndex != null) {
                        IndexedEntity sie = 
                                new IndexedEntity(classState.keyFieldsArr, 
                                                  entity);
                        deviceKey = classState.classIndex.get(sie);
                    }
                }
            }
            if (deviceKey != null) {
                // If the primary or secondary index contains the entity, 
                // update the entity timestamp, then use resulting device 
                // key to look up the device in the device map, and
                // use the referenced Device below.
                entity.setLastSeenTimestamp(new Date());
                device = deviceMap.get(deviceKey);
                if (device == null)
                    continue;
            } else {
                // If the secondary index does not contain the entity, 
                // create a new Device object containing the entity, and 
                // generate a new device ID
                synchronized (deviceKeyLock) {
                    deviceKey = Long.valueOf(deviceKeyCounter++);
                }
                device = new Device(deviceKey, entity, classes);
                
                // Add the new device to the primary map with a simple put
                deviceMap.put(deviceKey, device);
                
                if (!updateIndices(device, deviceKey)) {
                    if (deleteQueue == null)
                        deleteQueue = new ArrayList<Long>();
                    deleteQueue.add(deviceKey);
                    continue;
                }
                break;
            }
            
            if (device.containsEntity(entity)) {
                break;
            } else {
                Device newDevice = new Device(device, entity, classes);
                // XXX - TODO
                // When adding an entity, any existing entities on the
                // same OpenFlow switch cluster but a different attachment
                // point should be removed. If an entity being removed 
                // contains an IP address but the new entity does not contain
                // that IP, then a new entity should be added containing the
                // IP (including updating the entity caches), preserving the 
                // old timestamp of the entity.
                
                // XXX - TODO Handle port channels
                
                // XXX - TODO Handle broadcast domains
                
                // XXX - TODO Prevent flapping of entities
                
                boolean res = deviceMap.replace(deviceKey, device, newDevice);
                // If replace returns false, restart the process from the 
                // beginning (this implies another thread concurrently 
                // modified this Device).
                if (!res)
                    continue;
                
                device = newDevice;
                
                if (!updateIndices(device, deviceKey)) {
                    continue;
                }
                break;
            }
        }
        
        if (deleteQueue != null) {
            for (Long l : deleteQueue) {
                deviceMap.remove(l);
            }
        }
        return device;
    }
    
    /**
     * Get the secondary index for a class.  Will return null if the 
     * secondary index was created concurrently in another thread. 
     * @param clazz the class for the index
     * @return
     */
    private ClassState getClassState(IEntityClass clazz) 
                                      throws ConcurrentModificationException {
        ClassState classState = classStateMap.get(clazz);
        if (classState != null) return classState;
        
        classState = new ClassState(clazz);
        ClassState r = classStateMap.putIfAbsent(clazz, classState);
        if (r != null) {
            // concurrent add; restart
            throw new ConcurrentModificationException();
        }
        return classState;
    }
    
    /**
     * Update both the primary and class indices for the provided device.
     * If the update fails because of a concurrent update, will return false.
     * @param device the device to update
     * @param deviceKey the device key for the device
     * @return true if the update succeeded, false otherwise.
     */
    private boolean updateIndices(Device device, Long deviceKey) {
        if (!updateIndex(device, deviceKey, 
                         primaryIndex, primaryKeyFieldsArr)) {
            return false;
        }
        for (IEntityClass clazz : device.getEntityClasses()) {
            ClassState classState;
            try {
                classState = getClassState(clazz); 
            } catch (ConcurrentModificationException e) {
                return false;
            }

            if (classState.classIndex != null) {
                if (!updateIndex(device, 
                                 deviceKey, 
                                 classState.classIndex, 
                                 classState.keyFieldsArr))
                    return false;
            }
        }
        // XXX - TODO handle indexed views into data
        return true;
    }                     
    
    /**
     * Attempt to update an index with the entities in the provided
     * {@link Device}.  If the update fails because of a concurrent update,
     * will return false.
     * @param device the device to update
     * @param deviceKey the device key for the device
     * @param index the index to update
     * @param keyFields the key fields to use for the index
     * @return true if the update succeeded, false otherwise.
     */
    private boolean updateIndex(Device device, 
                                Long deviceKey,
                                ConcurrentHashMap<IndexedEntity, 
                                                  Long> index, 
                                EntityField[] keyFields) {
        for (Entity e : device.entities) {
            IndexedEntity ie = new IndexedEntity(keyFields, e);
            Long ret = index.putIfAbsent(ie, deviceKey);
            if (ret != null && !ret.equals(deviceKey)) {
                // If the return value is non-null, then fail the insert 
                // (this implies that a device using this entity has 
                // already been created in another thread).
                return false;
            }
        }
        
        return true;
    }
}
