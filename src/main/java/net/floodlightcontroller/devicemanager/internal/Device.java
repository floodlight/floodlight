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
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.TreeSet;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.openflow.util.HexString;

import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.web.DeviceSerializer;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;
import static net.floodlightcontroller.devicemanager.SwitchPort.ErrorStatus.*;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * Concrete implementation of {@link IDevice}
 * @author readams
 */
@JsonSerialize(using=DeviceSerializer.class)
public class Device implements IDevice {
    protected Long deviceKey;
    protected DeviceManagerImpl deviceManager;

    protected Entity[] entities;
    protected IEntityClass[] entityClasses;
    
    protected String macAddressString;
    
    // ************
    // Constructors
    // ************
    
    /**
     * Create a device from an entities
     * @param deviceManager the device manager for this device
     * @param deviceKey the unique identifier for this device object
     * @param entity the initial entity for the device
     * @param entityClasses the entity classes associated with the entity
     */
    public Device(DeviceManagerImpl deviceManager,
                  Long deviceKey,
                  Entity entity, 
                  Collection<IEntityClass> entityClasses) {
        this.deviceManager = deviceManager;
        this.deviceKey = deviceKey;
        this.entities = new Entity[] {entity};
        this.macAddressString = 
                HexString.toHexString(entity.getMacAddress(), 6);
        this.entityClasses = 
                entityClasses.toArray(new IEntityClass[entityClasses.size()]);
        Arrays.sort(this.entities);
    }
    
    /**
     * Create a device from a set of entities
     * @param deviceManager the device manager for this device
     * @param deviceKey the unique identifier for this device object
     * @param entities the initial entities for the device
     * @param entityClasses the entity classes associated with the entity
     */
    public Device(DeviceManagerImpl deviceManager,
                  Long deviceKey,
                  Collection<Entity> entities, 
                  IEntityClass[] entityClasses) {
        this.deviceManager = deviceManager;
        this.deviceKey = deviceKey;
        this.entities = entities.toArray(new Entity[entities.size()]);
        this.macAddressString = 
                HexString.toHexString(this.entities[0].getMacAddress(), 6);
        this.entityClasses = entityClasses;
        Arrays.sort(this.entities);
    }

    /**
     * Construct a new device consisting of the entities from the old device
     * plus an additional entity
     * @param device the old device object
     * @param newEntity the entity to add
     * @param entityClasses the entity classes associated with the entities
     */
    public Device(Device device,
                  Entity newEntity,
                  Collection<IEntityClass> entityClasses) {
        this.deviceManager = device.deviceManager;
        this.deviceKey = device.deviceKey;
        this.entities = Arrays.<Entity>copyOf(device.entities, 
                                              device.entities.length + 1);
        this.entities[this.entities.length - 1] = newEntity;
        Arrays.sort(this.entities);

        this.macAddressString = 
                HexString.toHexString(this.entities[0].getMacAddress(), 6);
        
        if (entityClasses != null &&
            entityClasses.size() > device.entityClasses.length) {
            IEntityClass[] classes = new IEntityClass[entityClasses.size()];
            this.entityClasses = 
                    entityClasses.toArray(classes);
        } else {
            // same actual array, not a copy
            this.entityClasses = device.entityClasses;
        }
    }

    // *******
    // IDevice
    // *******
    
    @Override
    public Long getDeviceKey() {
        return deviceKey;
    }
    
    @Override
    public long getMACAddress() {
        // we assume only one MAC per device for now.
        return entities[0].getMacAddress();
    }

    @Override
    public String getMACAddressString() {
        return macAddressString;
    }

    @Override
    public Short[] getVlanId() {
        if (entities.length == 1) {
            if (entities[0].getVlan() != null) {
                return new Short[]{ entities[0].getVlan() };
            } else {
                return new Short[] { Short.valueOf((short)-1) };
            }
        }

        TreeSet<Short> vals = new TreeSet<Short>();
        for (Entity e : entities) {
            if (e.getVlan() == null)
                vals.add((short)-1);
            else
                vals.add(e.getVlan());
        }
        return vals.toArray(new Short[vals.size()]);
    }

    static final EnumSet<DeviceField> ipv4Fields = EnumSet.of(DeviceField.IPV4);

    @Override
    public Integer[] getIPv4Addresses() {
        // XXX - TODO we can cache this result.  Let's find out if this
        // is really a performance bottleneck first though.
        
        if (entities.length == 1) {
            if (entities[0].getIpv4Address() != null) {
                return new Integer[]{ entities[0].getIpv4Address() };
            } else {
                return new Integer[0];
            }
        }

        TreeSet<Integer> vals = new TreeSet<Integer>();
        for (Entity e : entities) {
            if (e.getIpv4Address() == null) continue;
            
            // We have an IP address only if among the devices within the class
            // we have the most recent entity with that IP.
            boolean validIP = true;
            for (IEntityClass clazz : entityClasses) {
                Iterator<Device> devices = 
                        deviceManager.queryClassByEntity(clazz, ipv4Fields, e);
                while (devices.hasNext()) {
                    Device d = devices.next();
                    for (Entity se : d.entities) {
                        if (se.ipv4Address != null && 
                            se.ipv4Address.equals(e.ipv4Address) &&
                            se.lastSeenTimestamp != null &&
                            0 < se.lastSeenTimestamp.
                                    compareTo(e.lastSeenTimestamp)) {
                            validIP = false;
                            break;
                        }
                    }
                    if (!validIP)
                        break;
                }
                if (!validIP)
                    break;
            }

            if (validIP)
                vals.add(e.getIpv4Address());
        }
        
        return vals.toArray(new Integer[vals.size()]);
    }
    
    @Override
    public SwitchPort[] getAttachmentPoints() {
        return getAttachmentPoints(false);
    }

    @Override
    public SwitchPort[] getAttachmentPoints(boolean includeError) {
        // XXX - TODO we can cache this result.  Let's find out if this
        // is really a performance bottleneck first though.

        if (entities.length == 1) {
            Long dpid = entities[0].getSwitchDPID();
            Integer port = entities[0].getSwitchPort();
            if (dpid != null && port != null &&
                deviceManager.isValidAttachmentPoint(dpid, port)) {
                SwitchPort sp = new SwitchPort(dpid, port);
                return new SwitchPort[] { sp };
            } else {
                return new SwitchPort[0];
            }
        }

        // Find the most recent attachment point for each cluster
        Entity[] clentities = Arrays.<Entity>copyOf(entities, entities.length);
        Arrays.sort(clentities, deviceManager.apComparator);
        ArrayList<SwitchPort> blocked = null;
        ArrayList<SwitchPort> clusterBlocked = null;
        if (includeError) {
            blocked = new ArrayList<SwitchPort>();
            clusterBlocked = new ArrayList<SwitchPort>();
        }
            
        ITopologyService topology = deviceManager.topology;
        long prevCluster = 0;
        int clEntIndex = -1;
        Entity prev = null;
        long latestLastSeen = 0;
        for (int i = 0; i < clentities.length; i++) {
            Entity cur = clentities[i];
            Long dpid = cur.getSwitchDPID();
            Integer port = cur.getSwitchPort();
            if (dpid == null || port == null ||
                !deviceManager.isValidAttachmentPoint(dpid, port))
                continue;
            long curCluster = 
                    topology.getSwitchClusterId(cur.switchDPID);
            if (prevCluster != curCluster) {
                prev = null;
                latestLastSeen = 0;
                clEntIndex += 1;
                if (includeError) {
                    blocked.addAll(clusterBlocked);
                    clusterBlocked.clear();
                }
            }
            
            if (prev != null && 
                !(dpid.equals(prev.getSwitchDPID()) &&
                  port.equals(prev.getSwitchPort())) &&
                !topology.isInSameBroadcastDomain(dpid.longValue(),
                		port.shortValue(),
                        prev.getSwitchDPID().longValue(),
                        prev.getSwitchPort().shortValue())) {
                long curActive = 
                        deviceManager.apComparator.
                            getEffTS(cur, cur.getActiveSince());
                if (latestLastSeen > 0 &&
                    curActive > 0 &&
                    0 < Long.valueOf(latestLastSeen).compareTo(curActive)) {
                    // If the previous and current are both active at the same
                    // time (i.e. the last seen timestamp of previous is 
                    // greater than active timestamp of current item, we want
                    // to suppress rapid flapping between the two points. We
                    // choose arbitrarily based on criteria other than 
                    // timestamp; the compareTo for entity should fit the bill.
                    Entity block = prev;
                    if (0 < prev.compareTo(cur)) {
                        block = cur;
                        cur = prev;
                    }
                    if (includeError) {
                        boolean alreadyBlocked = false;
                        for (SwitchPort bl : clusterBlocked) {
                            if (dpid.equals(bl.getSwitchDPID()) &&
                                port.equals(bl.getPort())) {
                                alreadyBlocked = true;
                                break;
                            }
                        }
                        if (!alreadyBlocked) {
                            SwitchPort blap = 
                                    new SwitchPort(block.getSwitchDPID(), 
                                                   block.getSwitchPort(),
                                                   DUPLICATE_DEVICE);
                            clusterBlocked.add(blap);
                        }
                    }
                } else {
                    if (includeError) {
                        clusterBlocked.clear();
                    }
                    latestLastSeen = 0;
                }
            }
            
            prev = clentities[clEntIndex] = cur;
            prevCluster = curCluster;

            long prevLastSeen = 
                    deviceManager.apComparator.
                    getEffTS(prev,
                             prev.getLastSeenTimestamp());
            if (latestLastSeen < prevLastSeen)
                latestLastSeen = prevLastSeen;
        }

        if (clEntIndex < 0) {
            return new SwitchPort[0];
        }
        
        ArrayList<SwitchPort> vals = new ArrayList<SwitchPort>(clEntIndex + 1);
        for (int i = 0; i <= clEntIndex; i++) {
            Entity e = clentities[i];
            if (e.getSwitchDPID() != null &&
                e.getSwitchPort() != null) {
                SwitchPort sp = new SwitchPort(e.getSwitchDPID(), 
                                               e.getSwitchPort());
                vals.add(sp);
            }
        }
        if (includeError) {
            vals.addAll(blocked);
            vals.addAll(clusterBlocked);
        }

        return vals.toArray(new SwitchPort[vals.size()]);
    }

    @Override
    public Date getLastSeen() {
        Date d = null;
        for (int i = 0; i < entities.length; i++) {
            if (d == null ||
                entities[i].getLastSeenTimestamp().compareTo(d) > 0)
                d = entities[i].getLastSeenTimestamp();
        }
        return d;
    }
    
    // ***************
    // Getters/Setters
    // ***************

    public IEntityClass[] getEntityClasses() {
        return entityClasses;
    }

    public Entity[] getEntities() {
        return entities;
    }

    // ***************
    // Utility Methods
    // ***************
    
    /**
     * Check whether the device contains the specified entity
     * @param entity the entity to search for
     * @return the index of the entity, or <0 if not found
     */
    protected int entityIndex(Entity entity) {
        return Arrays.binarySearch(entities, entity);
    }
    
    // ******
    // Object
    // ******

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(entities);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Device other = (Device) obj;
        if (!deviceKey.equals(other.deviceKey)) return false;
        if (!Arrays.equals(entities, other.entities)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "Device [entities=" + Arrays.toString(entities) + "]";
    }
}
