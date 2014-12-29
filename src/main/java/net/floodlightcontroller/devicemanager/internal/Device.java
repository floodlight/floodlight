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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.web.DeviceSerializer;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.SwitchPort.ErrorStatus;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * Concrete implementation of {@link IDevice}
 * @author readams
 */
@JsonSerialize(using=DeviceSerializer.class)
public class Device implements IDevice {
    protected static Logger log = LoggerFactory.getLogger(Device.class);

    private final Long deviceKey;
    protected final DeviceManagerImpl deviceManager;

    protected final Entity[] entities;
    private final IEntityClass entityClass;

    protected final String macAddressString;
    // the vlan Ids from the entities of this device
    protected final VlanVid[] vlanIds;
    protected volatile String dhcpClientName;

    /**
     * These are the old attachment points for the device that were
     * valid no more than INACTIVITY_TIME ago.
     */
    protected volatile List<AttachmentPoint> oldAPs;
    /**
     * The current attachment points for the device.
     */
    protected volatile List<AttachmentPoint> attachmentPoints;

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
    public Device(DeviceManagerImpl deviceManager, Long deviceKey,
                  Entity entity, IEntityClass entityClass) {
        this.deviceManager = deviceManager;
        this.deviceKey = deviceKey;
        this.entities = new Entity[] {entity};
        this.macAddressString = entity.getMacAddress().toString();
        this.entityClass = entityClass;
        Arrays.sort(this.entities);

        this.dhcpClientName = null;
        this.oldAPs = null;
        this.attachmentPoints = null;

        if (entity.getSwitchDPID() != null && entity.getSwitchPort() != null) {
            DatapathId sw = entity.getSwitchDPID();
            OFPort port = entity.getSwitchPort();

            if (deviceManager.isValidAttachmentPoint(sw, port)) {
                AttachmentPoint ap;
                ap = new AttachmentPoint(sw, port, entity.getLastSeenTimestamp());
                this.attachmentPoints = new ArrayList<AttachmentPoint>();
                this.attachmentPoints.add(ap);
            }
        }
        vlanIds = computeVlandIds();
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
                  String dhcpClientName,
                  Collection<AttachmentPoint> oldAPs,
                  Collection<AttachmentPoint> attachmentPoints,
                  Collection<Entity> entities,
                  IEntityClass entityClass) {
        this.deviceManager = deviceManager;
        this.deviceKey = deviceKey;
        this.dhcpClientName = dhcpClientName;
        this.entities = entities.toArray(new Entity[entities.size()]);
        this.oldAPs = null;
        this.attachmentPoints = null;
        if (oldAPs != null) {
            this.oldAPs = new ArrayList<AttachmentPoint>(oldAPs);
        }
        if (attachmentPoints != null) {
            this.attachmentPoints = new ArrayList<AttachmentPoint>(attachmentPoints);
        }
        this.macAddressString = this.entities[0].getMacAddress().toString();
        this.entityClass = entityClass;
        Arrays.sort(this.entities);
        vlanIds = computeVlandIds();
    }

    /**
     * Construct a new device consisting of the entities from the old device
     * plus an additional entity.
     * The caller needs to ensure that the additional entity is not already
     * present in the array
     * @param device the old device object
     * @param newEntity the entity to add. newEntity must be have the same
     *        entity class as device
     * @param if positive indicates the index in the entities array were the
     *        new entity should be inserted. If negative we will compute the
     *        correct insertion point
     */
    public Device(Device device, Entity newEntity, int insertionpoint) {
        this.deviceManager = device.deviceManager;
        this.deviceKey = device.deviceKey;
        this.dhcpClientName = device.dhcpClientName;

        this.entities = new Entity[device.entities.length + 1];
        if (insertionpoint < 0) {
            insertionpoint = -(Arrays.binarySearch(device.entities, newEntity) + 1);
        }
        if (insertionpoint > 0) {
            // insertion point is not the beginning:
            // copy up to insertion point
            System.arraycopy(device.entities, 0, this.entities, 0, insertionpoint);
        }
        if (insertionpoint < device.entities.length) {
            // insertion point is not the end
            // copy from insertion point
            System.arraycopy(device.entities, insertionpoint,
                             this.entities, insertionpoint + 1,
                             device.entities.length - insertionpoint);
        }
        this.entities[insertionpoint] = newEntity;
        /*
        this.entities = Arrays.<Entity>copyOf(device.entities,
                                              device.entities.length + 1);
        this.entities[this.entities.length - 1] = newEntity;
        Arrays.sort(this.entities);
        */
        this.oldAPs = null;
        if (device.oldAPs != null) {
            this.oldAPs = new ArrayList<AttachmentPoint>(device.oldAPs);
        }
        this.attachmentPoints = null;
        if (device.attachmentPoints != null) {
            this.attachmentPoints = new ArrayList<AttachmentPoint>(device.attachmentPoints);
        }

        this.macAddressString = this.entities[0].getMacAddress().toString();

        this.entityClass = device.entityClass;
        vlanIds = computeVlandIds();
    }

    private VlanVid[] computeVlandIds() {
        if (entities.length == 1) {
            if (entities[0].getVlan() != null) {
                return new VlanVid[]{ entities[0].getVlan() };
            } else {
                return new VlanVid[] { VlanVid.ofVlan(-1) };
            }
        }

        TreeSet<VlanVid> vals = new TreeSet<VlanVid>();
        for (Entity e : entities) {
            if (e.getVlan() == null)
                vals.add(VlanVid.ofVlan(-1));
            else
                vals.add(e.getVlan());
        }
        return vals.toArray(new VlanVid[vals.size()]);
    }

    /**
     * Given a list of attachment points (apList), the procedure would return
     * a map of attachment points for each L2 domain.  L2 domain id is the key.
     * @param apList
     * @return
     */
    private Map<DatapathId, AttachmentPoint> getAPMap(List<AttachmentPoint> apList) {

        if (apList == null) return null;
        ITopologyService topology = deviceManager.topology;

        // Get the old attachment points and sort them.
        List<AttachmentPoint>oldAP = new ArrayList<AttachmentPoint>();
        if (apList != null) oldAP.addAll(apList);

        // Remove invalid attachment points before sorting.
        List<AttachmentPoint>tempAP =
                new ArrayList<AttachmentPoint>();
        for(AttachmentPoint ap: oldAP) {
            if (deviceManager.isValidAttachmentPoint(ap.getSw(), ap.getPort())) {
                tempAP.add(ap);
            }
        }
        oldAP = tempAP;

        Collections.sort(oldAP, deviceManager.apComparator);

        // Map of attachment point by L2 domain Id.
        Map<DatapathId, AttachmentPoint> apMap = new HashMap<DatapathId, AttachmentPoint>();

        for(int i=0; i<oldAP.size(); ++i) {
            AttachmentPoint ap = oldAP.get(i);
            // if this is not a valid attachment point, continue
            if (!deviceManager.isValidAttachmentPoint(ap.getSw(), ap.getPort()))
                continue;

            DatapathId id = topology.getL2DomainId(ap.getSw());
            apMap.put(id, ap);
        }

        if (apMap.isEmpty()) return null;
        return apMap;
    }

    /**
     * Remove all attachment points that are older than INACTIVITY_INTERVAL
     * from the list.
     * @param apList
     * @return
     */
    private boolean removeExpiredAttachmentPoints(List<AttachmentPoint>apList) {

        List<AttachmentPoint> expiredAPs = new ArrayList<AttachmentPoint>();

        if (apList == null) return false;

        for(AttachmentPoint ap: apList) {
            if (ap.getLastSeen().getTime() + AttachmentPoint.INACTIVITY_INTERVAL < System.currentTimeMillis()) {
                expiredAPs.add(ap);
            }
        }
        if (expiredAPs.size() > 0) {
            apList.removeAll(expiredAPs);
            return true;
        } else return false;
    }

    /**
     * Get a list of duplicate attachment points, given a list of old attachment
     * points and one attachment point per L2 domain. Given a true attachment
     * point in the L2 domain, say trueAP, another attachment point in the
     * same L2 domain, say ap, is duplicate if:
     * 1. ap is inconsistent with trueAP, and
     * 2. active time of ap is after that of trueAP; and
     * 3. last seen time of ap is within the last INACTIVITY_INTERVAL
     * @param oldAPList
     * @param apMap
     * @return
     */
    List<AttachmentPoint> getDuplicateAttachmentPoints(List<AttachmentPoint>oldAPList, Map<DatapathId, AttachmentPoint>apMap) {
        ITopologyService topology = deviceManager.topology;
        List<AttachmentPoint> dupAPs = new ArrayList<AttachmentPoint>();
        long timeThreshold = System.currentTimeMillis() - AttachmentPoint.INACTIVITY_INTERVAL;

        if (oldAPList == null || apMap == null)
            return dupAPs;

        for(AttachmentPoint ap : oldAPList) {
            DatapathId id = topology.getL2DomainId(ap.getSw());
            AttachmentPoint trueAP = apMap.get(id);

            if (trueAP == null) continue;
            boolean c = (topology.isConsistent(trueAP.getSw(), trueAP.getPort(),
                                              ap.getSw(), ap.getPort()));
            boolean active = (ap.getActiveSince().after(trueAP.getActiveSince()));
            boolean last = ap.getLastSeen().getTime() > timeThreshold;
            if (!c && active && last) {
                dupAPs.add(ap);
            }
        }

        return dupAPs;
    }

    /**
     * Update the known attachment points.  This method is called whenever
     * topology changes. The method returns true if there's any change to
     * the list of attachment points -- which indicates a possible device
     * move.
     * @return
     */
    protected boolean updateAttachmentPoint() {
        boolean moved = false;
        this.oldAPs = attachmentPoints;
        if (attachmentPoints == null || attachmentPoints.isEmpty())
            return false;

        List<AttachmentPoint> apList = new ArrayList<AttachmentPoint>();
        if (attachmentPoints != null) apList.addAll(attachmentPoints);
        Map<DatapathId, AttachmentPoint> newMap = getAPMap(apList);
        if (newMap == null || newMap.size() != apList.size()) {
            moved = true;
        }

        // Prepare the new attachment point list.
        if (moved) {
            log.info("updateAttachmentPoint: ap {}  newmap {} ", attachmentPoints, newMap);
            List<AttachmentPoint> newAPList = new ArrayList<AttachmentPoint>();
            if (newMap != null) newAPList.addAll(newMap.values());
            this.attachmentPoints = newAPList;
        }

        // Set the oldAPs to null.
        return moved;
    }

    /**
     * Update the list of attachment points given that a new packet-in
     * was seen from (sw, port) at time (lastSeen).  The return value is true
     * if there was any change to the list of attachment points for the device
     * -- which indicates a device move.
     * @param sw
     * @param port
     * @param lastSeen
     * @return
     */
    protected boolean updateAttachmentPoint(DatapathId sw, OFPort port, Date lastSeen){
        ITopologyService topology = deviceManager.topology;
        List<AttachmentPoint> oldAPList;
        List<AttachmentPoint> apList;
        boolean oldAPFlag = false;

        if (!deviceManager.isValidAttachmentPoint(sw, port)) return false;
        AttachmentPoint newAP = new AttachmentPoint(sw, port, lastSeen);
        //Copy the oldAP and ap list.
        apList = new ArrayList<AttachmentPoint>();
        if (attachmentPoints != null) apList.addAll(attachmentPoints);
        oldAPList = new ArrayList<AttachmentPoint>();
        if (oldAPs != null) oldAPList.addAll(oldAPs);

        // if the sw, port is in old AP, remove it from there
        // and update the lastSeen in that object.
        if (oldAPList.contains(newAP)) {
            int index = oldAPList.indexOf(newAP);
            newAP = oldAPList.remove(index);
            newAP.setLastSeen(lastSeen);
            this.oldAPs = oldAPList;
            oldAPFlag = true;
        }

        // newAP now contains the new attachment point.

        // Get the APMap is null or empty.
        Map<DatapathId, AttachmentPoint> apMap = getAPMap(apList);
        if (apMap == null || apMap.isEmpty()) {
            apList.add(newAP);
            attachmentPoints = apList;
            // there are no old attachment points - since the device exists, this
            // may be because the host really moved (so the old AP port went down);
            // or it may be because the switch restarted (so old APs were nullified).
            // For now we will treat both cases as host moved.
            return true;
        }

        DatapathId id = topology.getL2DomainId(sw);
        AttachmentPoint oldAP = apMap.get(id);

        if (oldAP == null) { // No attachment on this L2 domain.
            apList = new ArrayList<AttachmentPoint>();
            apList.addAll(apMap.values());
            apList.add(newAP);
            this.attachmentPoints = apList;
            return true; // new AP found on an L2 island.
        }

        // There is already a known attachment point on the same L2 island.
        // we need to compare oldAP and newAP.
        if (oldAP.equals(newAP)) {
            // nothing to do here. just the last seen has to be changed.
            if (newAP.lastSeen.after(oldAP.lastSeen)) {
                oldAP.setLastSeen(newAP.lastSeen);
            }
            this.attachmentPoints =
                    new ArrayList<AttachmentPoint>(apMap.values());
            return false; // nothing to do here.
        }

        int x = deviceManager.apComparator.compare(oldAP, newAP);
        if (x < 0) {
            // newAP replaces oldAP.
            apMap.put(id, newAP);
            this.attachmentPoints =
                    new ArrayList<AttachmentPoint>(apMap.values());

            oldAPList = new ArrayList<AttachmentPoint>();
            if (oldAPs != null) oldAPList.addAll(oldAPs);
            oldAPList.add(oldAP);
            this.oldAPs = oldAPList;
            if (!topology.isInSameBroadcastDomain(oldAP.getSw(), oldAP.getPort(),
                                                  newAP.getSw(), newAP.getPort()))
                return true; // attachment point changed.
        } else  if (oldAPFlag) {
            // retain oldAP  as is.  Put the newAP in oldAPs for flagging
            // possible duplicates.
                oldAPList = new ArrayList<AttachmentPoint>();
                if (oldAPs != null) oldAPList.addAll(oldAPs);
                // Add ot oldAPList only if it was picked up from the oldAPList
                oldAPList.add(newAP);
                this.oldAPs = oldAPList;
        }
        return false;
    }

    /**
     * Delete (sw,port) from the list of list of attachment points
     * and oldAPs.
     * @param sw
     * @param port
     * @return
     */
    public boolean deleteAttachmentPoint(DatapathId sw, OFPort port) {
        AttachmentPoint ap = new AttachmentPoint(sw, port, new Date(0));
        
        if (this.oldAPs != null) {
            ArrayList<AttachmentPoint> apList = new ArrayList<AttachmentPoint>();
            apList.addAll(this.oldAPs);
            int index = apList.indexOf(ap);
            if (index > 0) {
                apList.remove(index);
                this.oldAPs = apList;
            }
        }

        if (this.attachmentPoints != null) {
            ArrayList<AttachmentPoint> apList = new ArrayList<AttachmentPoint>();
            apList.addAll(this.attachmentPoints);
            int index = apList.indexOf(ap);
            if (index > 0) {
                apList.remove(index);
                this.attachmentPoints = apList;
                return true;
            }
        }
        return false;
    }

    public boolean deleteAttachmentPoint(DatapathId sw) {
        boolean deletedFlag;
        ArrayList<AttachmentPoint> apList;
        ArrayList<AttachmentPoint> modifiedList;

        // Delete the APs on switch sw in oldAPs.
        deletedFlag = false;
        apList = new ArrayList<AttachmentPoint>();
        if (this.oldAPs != null)
            apList.addAll(this.oldAPs);
        modifiedList = new ArrayList<AttachmentPoint>();

        for(AttachmentPoint ap: apList) {
            if (ap.getSw().equals(sw)) {
                deletedFlag = true;
            } else {
                modifiedList.add(ap);
            }
        }

        if (deletedFlag) {
            this.oldAPs = modifiedList;
        }

        // Delete the APs on switch sw in attachmentPoints.
        deletedFlag = false;
        apList = new ArrayList<AttachmentPoint>();
        if (this.attachmentPoints != null)
            apList.addAll(this.attachmentPoints);
        modifiedList = new ArrayList<AttachmentPoint>();

        for(AttachmentPoint ap: apList) {
            if (ap.getSw().equals(sw)) {
                deletedFlag = true;
            } else {
                modifiedList.add(ap);
            }
        }

        if (deletedFlag) {
            this.attachmentPoints = modifiedList;
            return true;
        }

        return false;
    }

    // *******
    // IDevice
    // *******

    @Override
    public SwitchPort[] getOldAP() {
        List<SwitchPort> sp = new ArrayList<SwitchPort>();
        SwitchPort [] returnSwitchPorts = new SwitchPort[] {};
        if (oldAPs == null) return returnSwitchPorts;
        if (oldAPs.isEmpty()) return returnSwitchPorts;

        // copy ap list.
        List<AttachmentPoint> oldAPList;
        oldAPList = new ArrayList<AttachmentPoint>();

        if (oldAPs != null) oldAPList.addAll(oldAPs);
        removeExpiredAttachmentPoints(oldAPList);

        if (oldAPList != null) {
            for(AttachmentPoint ap: oldAPList) {
                SwitchPort swport = new SwitchPort(ap.getSw(),
                                                   ap.getPort());
                    sp.add(swport);
            }
        }
        return sp.toArray(new SwitchPort[sp.size()]);
    }

    @Override
    public SwitchPort[] getAttachmentPoints() {
        return getAttachmentPoints(false);
    }

    @Override
    public SwitchPort[] getAttachmentPoints(boolean includeError) {
        List<SwitchPort> sp = new ArrayList<SwitchPort>();
        SwitchPort [] returnSwitchPorts = new SwitchPort[] {};
        if (attachmentPoints == null) return returnSwitchPorts;
        if (attachmentPoints.isEmpty()) return returnSwitchPorts;

        // copy ap list.
        List<AttachmentPoint> apList = attachmentPoints;

        if (apList != null) {
            for(AttachmentPoint ap: apList) {
                SwitchPort swport = new SwitchPort(ap.getSw(),
                                                   ap.getPort());
                    sp.add(swport);
            }
        }

        if (!includeError)
            return sp.toArray(new SwitchPort[sp.size()]);

        List<AttachmentPoint> oldAPList;
        oldAPList = new ArrayList<AttachmentPoint>();

        if (oldAPs != null) oldAPList.addAll(oldAPs);

        if (removeExpiredAttachmentPoints(oldAPList))
            this.oldAPs = oldAPList;

        List<AttachmentPoint> dupList;
        // get AP map.
        Map<DatapathId, AttachmentPoint> apMap = getAPMap(apList);
        dupList = this.getDuplicateAttachmentPoints(oldAPList, apMap);
        if (dupList != null) {
            for(AttachmentPoint ap: dupList) {
                SwitchPort swport = new SwitchPort(ap.getSw(),
                                                   ap.getPort(),
                                                   ErrorStatus.DUPLICATE_DEVICE);
                    sp.add(swport);
            }
        }
        return sp.toArray(new SwitchPort[sp.size()]);
    }

    @Override
    public Long getDeviceKey() {
        return deviceKey;
    }

    @Override
    public MacAddress getMACAddress() {
        // we assume only one MAC per device for now.
        return entities[0].getMacAddress();
    }

    @Override
    public String getMACAddressString() {
        return macAddressString;
    }

    @Override
    public VlanVid[] getVlanId() {
        return Arrays.copyOf(vlanIds, vlanIds.length);
    }

    static final EnumSet<DeviceField> ipv4Fields = EnumSet.of(DeviceField.IPV4);

    @Override
    public IPv4Address[] getIPv4Addresses() {
        // XXX - TODO we can cache this result.  Let's find out if this
        // is really a performance bottleneck first though.

        TreeSet<IPv4Address> vals = new TreeSet<IPv4Address>();
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

        return vals.toArray(new IPv4Address[vals.size()]);
    }

    @Override
    public VlanVid[] getSwitchPortVlanIds(SwitchPort swp) {
        TreeSet<VlanVid> vals = new TreeSet<VlanVid>();
        for (Entity e : entities) {
            if (e.switchDPID.equals(swp.getSwitchDPID())
                    && e.switchPort.equals(swp.getPort())) {
                if (e.getVlan() == null)
                    vals.add(VlanVid.ofVlan(-1)); //TODO Update all -1 VLANs (untagged) to the new VlanVid.ZERO
                else
                    vals.add(e.getVlan());
            }
        }
        return vals.toArray(new VlanVid[vals.size()]);
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

    public String getDHCPClientName() {
        return dhcpClientName;
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
		result = prime * result
				+ ((deviceKey == null) ? 0 : deviceKey.hashCode());
		result = prime * result + Arrays.hashCode(entities);
		return result;
	}

    @Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Device other = (Device) obj;
		if (deviceKey == null) {
			if (other.deviceKey != null)
				return false;
		} else if (!deviceKey.equals(other.deviceKey))
			return false;
		if (!Arrays.equals(entities, other.entities))
			return false;
		return true;
	}

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Device [deviceKey=");
        builder.append(deviceKey);
        builder.append(", entityClass=");
        builder.append(entityClass.getName());
        builder.append(", MAC=");
        builder.append(macAddressString);
        builder.append(", IPs=[");
        boolean isFirst = true;
        for (IPv4Address ip: getIPv4Addresses()) {
            if (!isFirst)
                builder.append(", ");
            isFirst = false;
            builder.append(ip.toString());
        }
        builder.append("], APs=");
        builder.append(Arrays.toString(getAttachmentPoints(true)));
        builder.append("]");
        return builder.toString();
    }
}
