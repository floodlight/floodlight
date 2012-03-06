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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IInfoProvider;
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
import net.floodlightcontroller.util.MultiIterator;

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
        IStorageSourceListener, IFloodlightModule,
        IInfoProvider {  
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
    protected ConcurrentHashMap<IEntityClass, ClassState> classStateMap;

    /**
     * This is the list of indices we want on a per-class basis
     */
    protected Map<EnumSet<DeviceField>, Boolean> perClassIndices;
    
    /**
     * The entity classifier currently in use
     */
    IEntityClassifier entityClassifier;
    
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
            for (Map.Entry<EnumSet<DeviceField>,Boolean> keys : 
                 perClassIndices.entrySet()) {
                secondaryIndexMap.put(keys.getKey(), 
                                      DeviceIndex.getInstance(keys.getKey(), 
                                                              keys.getValue()));
            }
        }
    }
   
    /**
     * Device manager event listeners
     */
    protected Set<IDeviceManagerAware> deviceListeners;

    /**
     * A device update event to be dispatched
     */
    protected static class DeviceUpdate {
        /**
         * The affected device
         */
        protected IDevice device;
        
        /**
         * True if the device was added
         */
        protected boolean added;
        
        /**
         * If not added, then this is the list of fields changed
         */
        protected EnumSet<DeviceField> fieldsChanged;
        
        public DeviceUpdate(IDevice device, boolean added,
                            EnumSet<DeviceField> fieldsChanged) {
            super();
            this.device = device;
            this.added = added;
            this.fieldsChanged = fieldsChanged;
        }        
    }
    
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
                              Integer switchPort) {
        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
        return findDeviceByEntity(new Entity(macAddress, vlan, 
                                             ipv4Address, switchDPID, 
                                             switchPort, null));
    }

    @Override
    public IDevice findDestDevice(IDevice source, long macAddress,
                                  Short vlan, Integer ipv4Address) {
        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
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
    public void addIndex(boolean perClass, boolean unique,
                         EnumSet<DeviceField> keyFields) {
        if (perClass) {
            perClassIndices.put(keyFields, unique);
        } else {
            secondaryIndexMap.put(keyFields, 
                                  DeviceIndex.getInstance(keyFields, unique));
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
    public Iterator<? extends IDevice> queryClassDevices(IDevice reference,
                                                         Long macAddress,
                                                         Short vlan,
                                                         Integer ipv4Address,
                                                         Long switchDPID,
                                                         Integer switchPort) {
        IEntityClass[] entityClasses = reference.getEntityClasses();
        ArrayList<Iterator<Device>> iterators = 
                new ArrayList<Iterator<Device>>();
        for (IEntityClass clazz : entityClasses) {
            ClassState classState = getClassState(clazz);
            
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
                                              entityClasses, 
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
        }

        return new MultiIterator<Device>(iterators.iterator());
    }

    @Override
    public void addListener(IDeviceManagerAware listener) {
        deviceListeners.add(listener);
    }

    @Override
    public void setEntityClassifier(IEntityClassifier classifier) {
        entityClassifier = classifier;
        primaryIndex = new DeviceUniqueIndex(classifier.getKeyFields());
        secondaryIndexMap = new HashMap<EnumSet<DeviceField>, DeviceIndex>();
    }
    
    @Override
    public void flushEntityCache(IEntityClass entityClass, 
                                 boolean reclassify) {
        // TODO Auto-generated method stub
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
    public int getId() {
        return IOFMessageListener.FlListenerID.DEVICEMANAGERIMPL;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("topology"));
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

    // *****************
    // ITopologyListener
    // *****************

    @Override
    public void toplogyChanged() {
        // TODO Auto-generated method stub
        
    }
    
    // *****************
    // IOFSwitchListener
    // *****************

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
        this.perClassIndices =
                new HashMap<EnumSet<DeviceField>, Boolean>();
        this.deviceListeners = new HashSet<IDeviceManagerAware>();
        
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
        classStateMap = 
                new ConcurrentHashMap<IEntityClass, ClassState>();
        
        if (topology != null) {
            // Register to get updates from topology
            topology.addListener(this);
        } else {
            logger.error("Could not add topology listener");
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
        Ethernet eth = 
                IFloodlightProviderService.bcStore.
                get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // Extract source entity information
        Entity srcEntity = 
                getSourceEntityFromPacket(eth, sw, pi.getInPort());
        if (srcEntity == null)
            return Command.STOP;

        if (isGratArp(eth) ||
            isBroadcastDHCPReq(eth)) {
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
    }
    
    /**
     * Check whether the port is a physical port. We should not learn 
     * attachment points on "special" ports.
     * @param port the port to check
     * @return true if the port is a valid input port
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
    
    private boolean isBroadcastDHCPReq(Ethernet eth) {
        return ((eth.getPayload() instanceof DHCP) && (eth.isBroadcast()));
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
        if (topology.isInternal(sw.getId(), (short)port) ||
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
        Long deviceKey =  primaryIndex.findByEntity(entity);
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
            Collection<IEntityClass> classes = null;
            
            if (deviceKey == null) {
                // If the entity does not exist in the primary entity index, 
                // use the entity classifier for find the classes for the 
                // entity. Look up the entity in each of the returned classes'
                // class entity indexes.
                classes = entityClassifier.classifyEntity(entity);
                for (IEntityClass clazz : classes) {
                    ClassState classState = getClassState(clazz);
                        
                    if (classState.classIndex != null) {
                        deviceKey = 
                                classState.classIndex.findByEntity(entity);
                    }
                }
            }
            if (deviceKey != null) {
                // If the primary or secondary index contains the entity, 
                // update the entity timestamp, then use resulting device 
                // key to look up the device in the device map, and
                // use the referenced Device below.
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
                
                updateSecondaryIndices(entity, classes, deviceKey);
                
                deviceUpdates = 
                        updateUpdates(deviceUpdates,
                                      new DeviceUpdate(device, true, null));
                
                break;
            }
            
            int entityindex = -1;
            if ((entityindex = device.containsEntity(entity)) >= 0) {
                // update timestamp on the found entity
                device.entities[entityindex].setLastSeenTimestamp(new Date());
                break;
            } else {
                // When adding an entity, any existing entities on the
                // same OpenFlow switch cluster but a different attachment
                // point should be removed. If an entity being removed 
                // contains non-key fields that the new entity does not contain, 
                // then a new entity should be added containing the
                // field (including updating the entity caches), preserving the 
                // old timestamp of the entity.

                Entity[] entities = device.getEntities();
                ArrayList<Entity> newEntities = null;
                ArrayList<Entity> removedEntities = null;
                EnumSet<DeviceField> changedFields = 
                        findChangedFields(device, entity);
                
                // iterate backwards since we want indices for removed entities
                // to update in reverse order
                for (int i = entities.length - 1; i >= 0; i--) {
                    // XXX - TODO Handle port channels                    
                    // XXX - TODO Handle broadcast domains
                    // XXX - TODO Prevent flapping of entities
                    // XXX - TODO Handle unique indices

                    Long edpid = entities[i].getSwitchDPID();
                    Long cdpid = entity.getSwitchDPID();
                    
                    // Remove attachment points in the same cluster 
                    if (edpid != null && cdpid != null &&
                        topology != null && 
                        topology.inSameCluster(edpid, cdpid)) {
                        // XXX - TODO don't delete entities; we should just
                        // filter out the attachment points on read
                        removedEntities = 
                                updateEntityList(removedEntities, entities[i]);

                        changedFields.add(DeviceField.SWITCH);
                        
                        Entity shim = makeShimEntity(entity, entities[i], 
                                                     device.getEntityClasses());
                        newEntities = updateEntityList(newEntities, shim);
                        continue;
                    }
                    
                    newEntities = updateEntityList(newEntities, entities[i]);
                }
                newEntities = updateEntityList(newEntities, entity);
                removeEntities(removedEntities, device.getEntityClasses());
                
                Device newDevice = new Device(device,
                                              newEntities, classes);

                updateSecondaryIndices(entity, 
                                       newDevice.getEntityClasses(), 
                                       deviceKey);
                for (Entity e : newEntities) {
                    updateSecondaryIndices(e, 
                                           newDevice.getEntityClasses(), 
                                           deviceKey);                    
                }
                
                deviceUpdates = 
                        updateUpdates(deviceUpdates,
                                      new DeviceUpdate(device, false, 
                                                       changedFields));
                
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
        
        processUpdates(deviceUpdates);

        return device;
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
            if (newEntity.getIpv4Address() != null &&
                entity.getIpv4Address() != null &&
                entity.getIpv4Address().equals(newEntity.getIpv4Address()))
                changedFields.remove(DeviceField.IPV4);
            if (newEntity.getVlan() != null &&
                entity.getVlan() != null &&
                entity.getVlan().equals(newEntity.getVlan()))
                changedFields.remove(DeviceField.VLAN);
            if (newEntity.getSwitchDPID() != null &&
                entity.getSwitchDPID() != null &&
                newEntity.getSwitchPort() != null &&
                entity.getSwitchPort() != null &&
                entity.getSwitchDPID().equals(newEntity.getSwitchDPID()) &&
                entity.getSwitchPort().equals(newEntity.getSwitchPort()))
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
            for (IDeviceManagerAware listener : deviceListeners) {
                if (update.added) {
                    listener.deviceAdded(update.device);
                } else {
                    for (DeviceField field : update.fieldsChanged) {
                        switch (field) {
                            case IPV4:
                                listener.deviceIPV4AddrChanged(update.device);
                                break;
                            case SWITCH:
                            case PORT:
                                listener.deviceMoved(update.device);
                                break;
                            case VLAN:
                                listener.deviceVlanChanged(update.device);
                                break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * If there's information that would be lost about a device because
     * we're removing an old entity, construct a "shim" entity to provide
     * that information
     * @param newentity the entity being added
     * @param rementity the entity that's being removed
     * @param classes the entity classes
     * @return the entity, or null if no entity needed
     */
    private Entity makeShimEntity(Entity newentity, Entity rementity,
                                  IEntityClass[] classes) {
        Set<DeviceField> nonkey = null;
    
        for (IEntityClass clazz : classes) {
            if (nonkey == null)
                nonkey = EnumSet.complementOf(clazz.getKeyFields());
            else
                nonkey.removeAll(clazz.getKeyFields());
        }

        Integer ipv4Address = null;
        Short vlan = null;

        for (DeviceField f : nonkey) {
            switch (f) {
                case IPV4:
                    if (rementity.getIpv4Address() != null &&
                        !rementity.getIpv4Address()
                        .equals(newentity.getIpv4Address()))
                        ipv4Address = rementity.getIpv4Address();
                    break;
                case VLAN:
                    if (rementity.getVlan() != null &&
                        !rementity.getVlan()
                        .equals(newentity.getVlan()))
                        vlan = rementity.getVlan();
                    break;
                case MAC:
                case PORT:
                case SWITCH:
                    break;
                default:
                    logger.warn("Unexpected device field {}", f);
            }
        }
        
        Entity shim = null;        
        if (ipv4Address != null || vlan != null) {
            shim = new Entity(rementity.getMacAddress(), vlan, 
                              ipv4Address, null, null, 
                              rementity.getLastSeenTimestamp());
        }
        return shim;
    }
    
    private ArrayList<Entity> updateEntityList(ArrayList<Entity> list,
                                               Entity entity) {
        if (entity == null) return list;
        if (list == null)
            list = new ArrayList<Entity>();
        list.add(entity);
        
        return list;
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
        ClassState classState = classStateMap.get(clazz);
        if (classState != null) return classState;
        
        classState = new ClassState(clazz);
        ClassState r = classStateMap.putIfAbsent(clazz, classState);
        if (r != null) {
            // concurrent add
            return r;
        }
        return classState;
    }
    
    /**
     * Update both the primary and class indices for the provided device.
     * If the update fails because of aEn concurrent update, will return false.
     * @param device the device to update
     * @param deviceKey the device key for the device
     * @return true if the update succeeded, false otherwise.
     */
    private boolean updateIndices(Device device, Long deviceKey) {
        if (!primaryIndex.updateIndex(device, deviceKey)) {
            return false;
        }
        for (IEntityClass clazz : device.getEntityClasses()) {
            ClassState classState = getClassState(clazz); 

            if (classState.classIndex != null) {
                if (!classState.classIndex.updateIndex(device, 
                                                       deviceKey))
                    return false;
            }
        }
        // XXX - TODO handle indexed views into data
        return true;
    }
    
    /**
     * Update the secondary indices for the given entity and associated
     * entity classes
     * @param entity the entity to update
     * @param entityClasses the entity classes for the entity
     * @param deviceKey the device key to set up
     */
    private void updateSecondaryIndices(Entity entity, 
                                        Collection<IEntityClass> entityClasses, 
                                        Long deviceKey) {
        for (DeviceIndex index : secondaryIndexMap.values()) {
            index.updateIndex(entity, deviceKey);
        }
        for (IEntityClass clazz : entityClasses) {
            ClassState state = getClassState(clazz);
            for (DeviceIndex index : state.secondaryIndexMap.values()) {
                index.updateIndex(entity, deviceKey);
            }
        }
    }
    
    /**
     * Update the secondary indices for the given entity and associated
     * entity classes
     * @param entity the entity to update
     * @param entityClasses the entity classes for the entity
     * @param deviceKey the device key to set up
     */
    private void updateSecondaryIndices(Entity entity, 
                                        IEntityClass[] entityClasses, 
                                        Long deviceKey) {
        updateSecondaryIndices(entity, Arrays.asList(entityClasses), deviceKey);
    }

    private void removeEntities(ArrayList<Entity> removed, 
                                IEntityClass[] classes) {
        if (removed == null) return;
        for (Entity rem : removed) {
            primaryIndex.removeEntity(rem);
            
            for (IEntityClass clazz : classes) {
                ClassState classState = getClassState(clazz);

                if (classState.classIndex != null) {
                    classState.classIndex.removeEntity(rem);
                }
            }
        }
        // XXX - TODO handle indexed views into data
    }

    private EnumSet<DeviceField> getEntityKeys(Long macAddress,
                                               Short vlan, 
                                               Integer ipv4Address,
                                               Long switchDPID,
                                               Integer switchPort) {
        EnumSet<DeviceField> keys = EnumSet.noneOf(DeviceField.class);
        if (macAddress != null) keys.add(DeviceField.MAC);
        if (vlan != null) keys.add(DeviceField.VLAN);
        if (ipv4Address != null) keys.add(DeviceField.IPV4);
        if (switchDPID != null) keys.add(DeviceField.SWITCH);
        if (switchPort != null) keys.add(DeviceField.PORT);
        return keys;
    }
}
