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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.openflow.util.HexString;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.web.DeviceSerializer;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
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
    protected IEntityClass entityClass;

    protected String macAddressString;

    protected List<AttachmentPoint> attachmentPoints;
    // ************
    // Constructors
    // ************

    /**
     * Create a device from an entities
     * @param deviceManager the device manager for this device
     * @param deviceKey the unique identifier for this device object
     * @param entity the initial entity for the device
     * @param entityClass the entity classes associated with the entity
     */
    public Device(DeviceManagerImpl deviceManager,
                  Long deviceKey,
                  Entity entity,
                  IEntityClass entityClass) {
        this.deviceManager = deviceManager;
        this.deviceKey = deviceKey;
        this.entities = new Entity[] {entity};
        this.macAddressString =
                HexString.toHexString(entity.getMacAddress(), 6);
        this.entityClass = entityClass;
        Arrays.sort(this.entities);
        this.attachmentPoints = null;

        if (entity.getSwitchDPID() != null &&
                entity.getSwitchPort() != null){
            long sw = entity.getSwitchDPID();
            short port = entity.getSwitchPort().shortValue();

            if (deviceManager.isValidAttachmentPoint(sw, port)) {

                AttachmentPoint ap = 
                        new AttachmentPoint(sw, port,
                                            entity.getLastSeenTimestamp().getTime());
                this.attachmentPoints = new ArrayList<AttachmentPoint>();
                this.attachmentPoints.add(ap);
            }
        }
    }

    private Map<Long, AttachmentPoint> getAPMap() {

        if (attachmentPoints == null) return null;
        ITopologyService topology = deviceManager.topology;

        // Get the old attachment points and sort them.
        List<AttachmentPoint>oldAP =
                new ArrayList<AttachmentPoint>(attachmentPoints);

        Collections.sort(oldAP, deviceManager.apComparator);

        // Map of attachment point by L2 domain Id.
        Map<Long, AttachmentPoint> apMap = new HashMap<Long, AttachmentPoint>();

        for(int i=0; i<oldAP.size(); ++i) {
            AttachmentPoint ap = oldAP.get(i);
            // if this is not a valid attachment point, continue
            if (!deviceManager.isValidAttachmentPoint(ap.getSw(),
                                                     ap.getPort()))
                continue;

            long id = topology.getL2DomainId(ap.getSw());
            AttachmentPoint possibleDuplicate = apMap.put(id, ap);
            if (possibleDuplicate == null) continue;

            // Add logic to check for duplicate device.
        }

        if (apMap.isEmpty()) return null;
        return apMap;
    }

    protected boolean updateAttachmentPoint(long sw, short port, long lastSeen){
        ITopologyService topology = deviceManager.topology;

        // 
        if (!deviceManager.isValidAttachmentPoint(sw, port)) return false;

        AttachmentPoint newAP = new AttachmentPoint(sw, port, lastSeen);
        Map<Long, AttachmentPoint> apMap = getAPMap();

        if (apMap == null || apMap.isEmpty()) {
            // Device just got added.
            List<AttachmentPoint> apList = new ArrayList<AttachmentPoint>();
            apList.add(newAP);
            this.attachmentPoints = apList;
            return true;  // device added
        }

        long id = topology.getL2DomainId(sw);
        AttachmentPoint oldAP = apMap.get(id);

        if (oldAP == null) // No attachment on this L2 domain.
        {
            List<AttachmentPoint> apList = new ArrayList<AttachmentPoint>();
            apList.addAll(apMap.values());
            apList.add(newAP);
            this.attachmentPoints = apList;
            return true; // new AP found on an L2 island.
        }

        // There is already a known attachment point on the same L2 island.
        // we need to compare oldAP and newAP.
        if (oldAP.equals(newAP)) {
            // nothing to do here. just the last seen has to be changed.
            if (newAP.lastSeen > oldAP.lastSeen)
                apMap.put(id, newAP);
            this.attachmentPoints =
                    new ArrayList<AttachmentPoint>(apMap.values());
            return false; // nothing to do here.
        }

        int x = deviceManager.apComparator.compare(oldAP, newAP);
        if (x > 0) {
            // newAP replaces oldAP.
            apMap.put(id, newAP);
            this.attachmentPoints =
                    new ArrayList<AttachmentPoint>(apMap.values());
            return true; // attachment point changed.
        }

        return false; // something weird.
    }

    public boolean deleteAttachmentPoint(long sw, short port) {
        AttachmentPoint ap = new AttachmentPoint(sw, port, 0);

        if (this.attachmentPoints == null) return false;

        ArrayList<AttachmentPoint> apList = new ArrayList<AttachmentPoint>();
        apList.addAll(this.attachmentPoints);
        int index = apList.indexOf(ap);
        if (index < 0) return false;

        apList.remove(index);
        this.attachmentPoints = apList;
        return true;
    }

    @Override
    public SwitchPort[] getAttachmentPoints() {
        return getAttachmentPoints(false);
    }

    @Override
    public SwitchPort[] getAttachmentPoints(boolean includeError) {

        Map<Long, AttachmentPoint> apMap = getAPMap();
        if (apMap == null) return null;
        if (apMap.isEmpty()) return null;

        List<SwitchPort> sp = new ArrayList<SwitchPort>();
        for(AttachmentPoint ap: apMap.values()) {
            SwitchPort swport = new SwitchPort(ap.getSw(),
                                               ap.getPort());
            sp.add(swport);
        }
        return sp.toArray(new SwitchPort[sp.size()]);
    }

    /**
     * Create a device from a set of entities
     * @param deviceManager the device manager for this device
     * @param deviceKey the unique identifier for this device object
     * @param entities the initial entities for the device
     * @param entityClass the entity class associated with the entities
     */
    public Device(DeviceManagerImpl deviceManager,
                  Long deviceKey,
                  Collection<AttachmentPoint> attachmentPoints,
                  Collection<Entity> entities,
                  IEntityClass entityClass) {
        this.deviceManager = deviceManager;
        this.deviceKey = deviceKey;
        this.entities = entities.toArray(new Entity[entities.size()]);
        if (attachmentPoints == null) {
            this.attachmentPoints = null;
        } else {
            this.attachmentPoints =
                    new ArrayList<AttachmentPoint>(attachmentPoints);
        }
        this.macAddressString =
                HexString.toHexString(this.entities[0].getMacAddress(), 6);
        this.entityClass = entityClass;
        Arrays.sort(this.entities);
    }

    /**
     * Construct a new device consisting of the entities from the old device
     * plus an additional entity
     * @param device the old device object
     * @param newEntity the entity to add. newEntity must be have the same
     *        entity class as device
     */
    public Device(Device device,
                  Entity newEntity) {
        this.deviceManager = device.deviceManager;
        this.deviceKey = device.deviceKey;
        this.entities = Arrays.<Entity>copyOf(device.entities,
                                              device.entities.length + 1);
        this.entities[this.entities.length - 1] = newEntity;
        Arrays.sort(this.entities);

        if (device.attachmentPoints != null) {
            this.attachmentPoints =
                    new ArrayList<AttachmentPoint>(device.attachmentPoints);
        } else 
            this.attachmentPoints = null;

        this.macAddressString =
                HexString.toHexString(this.entities[0].getMacAddress(), 6);

        this.entityClass = device.entityClass;
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

        TreeSet<Integer> vals = new TreeSet<Integer>();
        for (Entity e : entities) {
            if (e.getIpv4Address() == null) continue;

            // We have an IP address only if among the devices within the class
            // we have the most recent entity with that IP.
            boolean validIP = true;
            Iterator<Device> devices =
                    deviceManager.queryClassByEntity(entityClass, ipv4Fields, e);
            while (devices.hasNext()) {
                Device d = devices.next();
                if (deviceKey.equals(d.getDeviceKey())) 
                    continue;
                for (Entity se : d.entities) {
                    if (se.getIpv4Address() != null &&
                            se.getIpv4Address().equals(e.getIpv4Address()) &&
                            se.getLastSeenTimestamp() != null &&
                            0 < se.getLastSeenTimestamp().
                            compareTo(e.getLastSeenTimestamp())) {
                        validIP = false;
                        break;
                    }
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
    public Short[] getSwitchPortVlanIds(SwitchPort swp) {
        TreeSet<Short> vals = new TreeSet<Short>();
        for (Entity e : entities) {
            if (e.switchDPID == swp.getSwitchDPID() 
                    && e.switchPort == swp.getPort()) {
                if (e.getVlan() == null)
                    vals.add(Ethernet.VLAN_UNTAGGED);
                else
                    vals.add(e.getVlan());
            }
        }
        return vals.toArray(new Short[vals.size()]);
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

    @Override
    public IEntityClass getEntityClass() {
        return entityClass;
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
        return "Device [entityClass=" + entityClass.getName() +
                " entities=" + Arrays.toString(entities) + "]";
    }
}
