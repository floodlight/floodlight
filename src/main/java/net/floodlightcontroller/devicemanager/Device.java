/**
*    Copyright 2011, Big Switch Networks, Inc. 
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

package net.floodlightcontroller.devicemanager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.topology.SwitchPortTuple;

import org.openflow.util.HexString;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class Device {
    protected byte[] dataLayerAddress;
    protected String dlAddrString;
    protected Short vlanId;
    protected Map<Integer, DeviceNetworkAddress> networkAddresses;
    protected Map<SwitchPortTuple, DeviceAttachmentPoint> attachmentPoints;
    protected Map<SwitchPortTuple, DeviceAttachmentPoint> oldAttachmentPoints;
    protected Date lastSeen;
    protected Date lastSeenInStorage;
    protected Date lastWrittenToStorage;

    private static long LAST_SEEN_STORAGE_UPDATE_INTERVAL = 1000 * 60 * 5; // 5 minutes
    
    public static void setStorageUpdateInterval(int intervalInMs) {
        LAST_SEEN_STORAGE_UPDATE_INTERVAL = intervalInMs;
    }
    
    public Device() {
        this.networkAddresses = new ConcurrentHashMap<Integer, DeviceNetworkAddress>();
        this.attachmentPoints = new ConcurrentHashMap<SwitchPortTuple, DeviceAttachmentPoint>();
        this.oldAttachmentPoints = new ConcurrentHashMap<SwitchPortTuple, DeviceAttachmentPoint>();
        this.lastSeen = new Date();
        this.lastSeenInStorage = null;
        this.lastWrittenToStorage = null;
    }
    
    /**
     * Copy constructor
     * @param device
     */
    public Device(Device device) {
        this.dataLayerAddress = device.dataLayerAddress;
        this.dlAddrString = device.dlAddrString;
        setLastSeen(device.getLastSeen());
        setVlanId(device.getVlanId());
        setNetworkAddresses(device.getNetworkAddresses());
        setAttachmentPoints(device.getAttachmentPoints());
        lastWrittenToStorage = device.lastWrittenToStorage;
        lastSeenInStorage = device.lastSeenInStorage;
        
        oldAttachmentPoints = 
                new ConcurrentHashMap<SwitchPortTuple, DeviceAttachmentPoint>();
        for (Entry<SwitchPortTuple, DeviceAttachmentPoint> e : 
             device.oldAttachmentPoints.entrySet()) {
            oldAttachmentPoints.put(e.getKey(), e.getValue());
        }
    }
    
    public Device(byte[] dataLayerAddress) {
        this();
        setDataLayerAddress(dataLayerAddress);
    }
    
    public Device(byte[] dataLayerAddress, Date lastSeen) {
        this(dataLayerAddress);
        if (lastSeen != null) {
            this.lastSeen = lastSeen;
        }
    }

    public Short getVlanId() {
        return vlanId;
    }

    public void setVlanId(Short vlanId) {
        this.vlanId = vlanId;
    }

    public Date getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * @return the dataLayerAddress
     */
    public byte[] getDataLayerAddress() {
        return dataLayerAddress;
    }
    
    /**
     * @return the dataLayerAddress as a long
     */
    public long getDataLayerAddressAsLong() {
        return Ethernet.toLong(dataLayerAddress);
    }

    /**
     * @param dataLayerAddress the dataLayerAddress to set
     */
    public void setDataLayerAddress(byte[] dataLayerAddress) {
        this.dataLayerAddress = dataLayerAddress;
        this.dlAddrString = HexString.toHexString(dataLayerAddress);
    }

    /**
     * Get a string version of the data layer address
     * @return ths string
     */
    public String getDlAddrString() {
        return dlAddrString;
    }

    /**
     * @return the swPorts
     */
    public Collection<DeviceAttachmentPoint> getAttachmentPoints() {
        return attachmentPoints.values();
    }
    
    public Map<SwitchPortTuple, DeviceAttachmentPoint> 
                                                getAttachmentPointsMap() {
        return attachmentPoints;
    }
    
    public Map<SwitchPortTuple, DeviceAttachmentPoint> 
                                                getOldAttachmentPointsMap() {
        return oldAttachmentPoints;
    }
    
    public Collection<DeviceAttachmentPoint> getAttachmentPointsSorted(Comparator<DeviceAttachmentPoint> c) {
        List<DeviceAttachmentPoint> daps = new ArrayList<DeviceAttachmentPoint>(attachmentPoints.values());
        Collections.sort(daps, c);
        return daps;
    }

    public DeviceAttachmentPoint getAttachmentPoint(SwitchPortTuple switchPort) {
        return attachmentPoints.get(switchPort);
    }
    
    public void addAttachmentPoint(DeviceAttachmentPoint attachmentPoint) {
        if (attachmentPoint != null) {
            attachmentPoints.put(attachmentPoint.getSwitchPort(), attachmentPoint);
            if (attachmentPoint.getLastSeen().after(lastSeen)) {
                lastSeen = attachmentPoint.getLastSeen();
            }
        }
    }
    
    /**
     * Clears current attachment points.
     */
    public void clearAttachmentPoints() {
        attachmentPoints.clear();
    }
    
    public void addAttachmentPoint(SwitchPortTuple switchPort, Date lastSeen) {
        DeviceAttachmentPoint attachmentPoint = new DeviceAttachmentPoint(switchPort, lastSeen);
        addAttachmentPoint(attachmentPoint);
    }
   
    public DeviceAttachmentPoint removeAttachmentPoint(DeviceAttachmentPoint attachmentPoint) {
        return attachmentPoints.remove(attachmentPoint.getSwitchPort());
    }
    
    public DeviceAttachmentPoint removeAttachmentPoint(SwitchPortTuple switchPort) {
        return attachmentPoints.remove(switchPort);
    }
    
    public Set<DeviceAttachmentPoint> removeAttachmentPointsForSwitch(IOFSwitch sw) {
        Set<DeviceAttachmentPoint> removedPoints = new HashSet<DeviceAttachmentPoint>();
        for (SwitchPortTuple swt : attachmentPoints.keySet()) {
            if (swt.getSw().getId() == sw.getId()) {
                removedPoints.add(attachmentPoints.remove(swt));
            }
        }
        return removedPoints;
    }
    
    /**
     * @param attachmentPoints the new collection of attachment points for the device
     */
    public void setAttachmentPoints(Collection<DeviceAttachmentPoint> attachmentPoints) {
        this.attachmentPoints = new ConcurrentHashMap<SwitchPortTuple, DeviceAttachmentPoint>();
        for (DeviceAttachmentPoint attachmentPoint: attachmentPoints) {
            assert(attachmentPoint.getSwitchPort() != null);
            addAttachmentPoint(attachmentPoint);
        }
    }
    
    public Collection<DeviceAttachmentPoint> getOldAttachmentPoints() {
        return oldAttachmentPoints.values();
    }

    public DeviceAttachmentPoint getOldAttachmentPoint(SwitchPortTuple switchPort) {
        return oldAttachmentPoints.get(switchPort);
    }
    
    public void addOldAttachmentPoint(DeviceAttachmentPoint attachmentPoint) {
        oldAttachmentPoints.put(attachmentPoint.getSwitchPort(), attachmentPoint);
    }
    
    public DeviceAttachmentPoint removeOldAttachmentPoint(DeviceAttachmentPoint attachmentPoint) {
        return oldAttachmentPoints.remove(attachmentPoint.getSwitchPort());
    }

    /**
     * @return the networkAddresses
     */
    public Collection<DeviceNetworkAddress> getNetworkAddresses() {
        return networkAddresses.values();
    }

    public Map<Integer, DeviceNetworkAddress> getNetworkAddressesMap() {
        return networkAddresses;
    }
    
    public DeviceNetworkAddress getNetworkAddress(Integer networkAddress) {
        return networkAddresses.get(networkAddress);
    }
    
    public void addNetworkAddress(DeviceNetworkAddress networkAddress) {
        networkAddresses.put(networkAddress.getNetworkAddress(), networkAddress);
        if (networkAddress.getLastSeen().after(lastSeen)) {
            lastSeen = networkAddress.getLastSeen();
        }
    }
    
    public void addNetworkAddress(Integer networkAddress, Date lastSeen) {
        if (networkAddress != 0) {
            DeviceNetworkAddress deviceNetworkAddress = 
                new DeviceNetworkAddress(networkAddress, lastSeen);
            addNetworkAddress(deviceNetworkAddress);
        }
    }

    public DeviceNetworkAddress removeNetworkAddress(Integer networkAddress) {
        return networkAddresses.remove(networkAddress);
    }

    public DeviceNetworkAddress removeNetworkAddress(DeviceNetworkAddress networkAddress) {
        return networkAddresses.remove(networkAddress.getNetworkAddress());
    }
    
    /**
     * @param networkAddresses the networkAddresses to set
     */
    public void setNetworkAddresses(Collection<DeviceNetworkAddress> networkAddresses) {
        // FIXME: Should we really be exposing this method? Who would use it?
        this.networkAddresses = new ConcurrentHashMap<Integer, DeviceNetworkAddress>();
        for (DeviceNetworkAddress networkAddress: networkAddresses) {
            assert(networkAddress.getNetworkAddress() != null);
            addNetworkAddress(networkAddress);
        }
    }
    
    public void lastSeenWrittenToStorage(Date lastWrittenDate) {
        lastSeenInStorage = lastSeen;
        lastWrittenToStorage = lastWrittenDate;
    }
    
    public boolean shouldWriteLastSeenToStorage() {
        return (lastSeen != lastSeenInStorage) && ((lastWrittenToStorage == null) ||
                (lastSeen.getTime() >= lastWrittenToStorage.getTime() + LAST_SEEN_STORAGE_UPDATE_INTERVAL));
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(dataLayerAddress);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Device))
            return false;
        Device other = (Device) obj;
        return Arrays.equals(dataLayerAddress, other.dataLayerAddress);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Device [dataLayerAddress=" + 
                dlAddrString +
                ", attachmentPoints=" + attachmentPoints + ", networkAddresses="
                + IPv4.fromIPv4AddressCollection(networkAddresses.keySet()) + "]";
    }

    public Date getLastSeenInStorage() {
        return lastSeenInStorage;
    }

    public Date getLastWrittenToStorage() {
        return lastWrittenToStorage;
    }
}
