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

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.TreeSet;

import org.openflow.util.HexString;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;

/**
 * Concrete implementation of {@link IDevice}
 * @author readams
 */
public class Device implements IDevice {
    protected Long deviceKey;
    
    protected Entity[] entities;
    protected IEntityClass[] entityClasses;
    
    protected String macAddressString;
    
    // ************
    // Constructors
    // ************
    
    /**
     * Create a device from a set of entities
     * @param deviceKey the unique identifier for this device object
     * @param entity the initial entity for the device
     * @param entityClasses the entity classes associated with the entity
     */
    public Device(Long deviceKey,
                  Entity entity, 
                  Collection<IEntityClass> entityClasses) {
        this.deviceKey = deviceKey;
        this.entities = new Entity[] {entity};
        this.macAddressString = 
                HexString.toHexString(entity.getMacAddress(), 6);
        this.entityClasses = 
                entityClasses.toArray(new IEntityClass[entityClasses.size()]);
        Arrays.sort(this.entities);
    }

    /**
     * Construct a new device with the given key and containing the provided
     * entities and entity classes
     * @param device the old device object
     * @param entities the entities for the device
     * @param entityClasses the entity classes associated with the entities
     */
    public Device(Device device,
                  Collection<Entity> entities,
                  Collection<IEntityClass> entityClasses) {
        this.deviceKey = device.getDeviceKey();
        this.entities = entities.toArray(new Entity[entities.size()]);
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

    @Override
    public Integer[] getIPv4Addresses() {
        if (entities.length == 1) {
            if (entities[0].getIpv4Address() != null) {
                return new Integer[]{ entities[0].getIpv4Address() };
            } else {
                return new Integer[0];
            }
        }

        TreeSet<Integer> vals = new TreeSet<Integer>();
        for (Entity e : entities) {
            if (e.getIpv4Address() != null)
                vals.add(e.getIpv4Address());
        }
        return vals.toArray(new Integer[vals.size()]);
    }

    @Override
    public SwitchPort[] getAttachmentPoints() {
        if (entities.length == 1) {
            if (entities[0].getSwitchDPID() != null &&
                entities[0].getSwitchPort() != null) {
                SwitchPort sp = new SwitchPort(entities[0].getSwitchDPID(), 
                                               entities[0].getSwitchPort());
                return new SwitchPort[] { sp };
            } else {
                return new SwitchPort[0];
            }
        }

        HashSet<SwitchPort> vals = new HashSet<SwitchPort>();
        for (Entity e : entities) {
            if (e.getSwitchDPID() != null &&
                e.getSwitchPort() != null) {
                SwitchPort sp = new SwitchPort(e.getSwitchDPID(), 
                                               e.getSwitchPort());
                vals.add(sp);
            }
        }
        return vals.toArray(new SwitchPort[vals.size()]);
    }

    @Override
    public Date getLastSeen() {
        Date d = entities[0].getLastSeenTimestamp();
        for (int i = 1; i < entities.length; i++) {
            if (entities[i].getLastSeenTimestamp().compareTo(d) < 0)
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
     * @return true the index of the entity, or <0 if not found
     */
    public int containsEntity(Entity entity) {
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
        if (!Arrays.equals(entities, other.entities)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "Device [entities=" + Arrays.toString(entities) + "]";
    }
}
