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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceManager;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifier;
import net.floodlightcontroller.devicemanager.IEntityClassifier.EntityField;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.storage.IStorageSource;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.topology.ITopology;
import net.floodlightcontroller.topology.ITopologyAware;
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
public class DeviceManagerImpl implements IDeviceManager, IOFMessageListener,
        IOFSwitchListener, ITopologyAware, IStorageSourceListener {  
    protected static Logger logger = 
        LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected IFloodlightProvider floodlightProvider;
    protected ITopology topology;
    protected IStorageSource storageSource;
    
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
    
    /**
     * This map contains secondary indices for each of the configured {@ref IEntityClass} 
     * that exist
     */
    protected ConcurrentHashMap<IEntityClass, 
                                ConcurrentHashMap<IndexedEntity, 
                                                  Long>> classIndexMap;

    /**
     * The entity classifier currently in use
     */
    IEntityClassifier entityClassifier;
    
    // **************
    // IDeviceManager
    // **************

    @Override
    public void setEntityClassifier(IEntityClassifier classifier) {
        entityClassifier = classifier;
        primaryKeyFields = classifier.getKeyFields();
    }
    
    @Override
    public void flushEntityCache(IEntityClass entityClass, 
                                 boolean reclassify) {
        // TODO Auto-generated method stub
    }

    @Override
    public IDevice findDevice(long macAddress, Integer ipv4Address, 
                              Short vlan, Long switchDPID, 
                              Integer switchPort) {
        return findDeviceByEntity(new Entity(macAddress, vlan, 
                                             ipv4Address, switchDPID, 
                                             switchPort, null));
    }

    @Override
    public Collection<? extends IDevice> getAllDevices() {
        return Collections.unmodifiableCollection(deviceMap.values());
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

    // **************
    // Initialization
    // **************

    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    public void setStorageSource(IStorageSource storageSource) {
        this.storageSource = storageSource;
    }

    public void setTopology(ITopology topology) {
        this.topology = topology;
    }
    
    public void init() {
        
    }

    public void startUp() {
        if (entityClassifier == null)
            setEntityClassifier(new DefaultEntityClassifier());
        
        deviceMap = new ConcurrentHashMap<Long, Device>();
        primaryIndex = new ConcurrentHashMap<IndexedEntity, Long>();
        classIndexMap = 
                new ConcurrentHashMap<IEntityClass, 
                                      ConcurrentHashMap<IndexedEntity, 
                                                        Long>>();
        
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        
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
                    IFloodlightProvider.bcStore.
                    get(cntx,IFloodlightProvider.CONTEXT_PI_PAYLOAD);

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
            Device dstDevice = findDeviceByEntity(dstEntity);
            if (dstDevice == null) {
                // This can only happen if we have attachment point
                // key fields since attachment point information isn't
                // available for destination devices.
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
                    // ambiguous device.  A higher-order component will need
                    // to deal with it by assigning priority
                    // XXX - TODO
                }
                */
            }
            if (dstDevice != null)
                fcStore.put(cntx, CONTEXT_DST_DEVICE, dstDevice);
            
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
        IndexedEntity ie = new IndexedEntity(primaryKeyFields, entity);
        Long deviceKey = primaryIndex.get(ie);
        if (deviceKey == null)
            return null;
        return deviceMap.get(deviceKey);
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
        IndexedEntity ie = new IndexedEntity(primaryKeyFields, entity);
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
                    // ensure that a class index exists for the class if
                    // needed
                    ConcurrentHashMap<IndexedEntity, Long> classIndex;
                    try {
                        classIndex = getClassIndex(clazz);
                    } catch (ConcurrentModificationException e) {
                        continue;
                    }
                        
                    if (classIndex != null) {
                        IndexedEntity sie = 
                                new IndexedEntity(clazz.getKeyFields(), 
                                                  entity);
                        deviceKey = classIndex.get(sie);
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
            }
            
            if (device.containsEntity(entity)) {
                break;
            } else {
                Device newDevice = new Device(device, entity, classes);
                // XXX - TODO When adding an entity, any existing entities on the
                // same OpenFlow switch cluster but a different attachment point
                // should be removed. If an entity being removed contains an 
                // IP address but the new entity does not contain that IP, 
                // then a new entity should be added containing the IP 
                // (including updating the entity caches), preserving the old 
                // timestamp of the entity.
                
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
    private ConcurrentHashMap<IndexedEntity, 
                              Long> getClassIndex(IEntityClass clazz) 
                                      throws ConcurrentModificationException {
        ConcurrentHashMap<IndexedEntity, Long> classIndex =
                classIndexMap.get(clazz);
        if (classIndex != null) return classIndex;
        
        if (primaryKeyFields.equals(clazz.getKeyFields())) {
            return null;
        }
        
        classIndex = 
                new ConcurrentHashMap<IndexedEntity, Long>();
        ConcurrentHashMap<IndexedEntity, Long> r = 
                classIndexMap.putIfAbsent(clazz, 
                                          classIndex);
        if (r != null) {
            // concurrent add; restart
            throw new ConcurrentModificationException();
        }
        return classIndex;
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
                         primaryIndex, primaryKeyFields)) {
            return false;
        }
        for (IEntityClass clazz : device.getEntityClasses()) {
            Set<EntityField> ef = clazz.getKeyFields();
            if (primaryKeyFields.equals(ef))
                continue;
            ConcurrentHashMap<IndexedEntity, Long> classIndex;
            try {
                classIndex = getClassIndex(clazz); 
            } catch (ConcurrentModificationException e) {
                return false;
            }
            if (classIndex != null &&
                !updateIndex(device, deviceKey, classIndex, ef))
                return false;
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
                                Set<EntityField> keyFields) {
        for (Entity e : device.entities) {
            IndexedEntity ie = new IndexedEntity(keyFields, e);
            Long ret = index.putIfAbsent(ie, deviceKey);
            if (ret != null) {
                // If the return value is non-null, then fail the insert 
                // (this implies that a device using this entity has 
                // already been created in another thread).
                return false;
            }
        }
        
        return true;
    }
    
}
