/**
*    Copyright 2011,2012, Big Switch Networks, Inc. 
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

import java.util.Date;

import net.floodlightcontroller.util.MACAddress;

/**
 * An entity on the network is a visible trace of a device that corresponds
 * to a packet received from a particular interface on the edge of a network,
 * with a particular VLAN tag, and a particular MAC address, along with any
 * other packet characteristics we might want to consider as helpful for
 * disambiguating devices.
 * 
 * Entities are the most basic element of devices; devices consist of one or
 * more entities.  Entities are immutable once created, except for the last
 * seen timestamp.
 *  
 * @author readams
 *
 */
public class Entity {
     /**
     * The MAC address associated with this entity
     */
    protected MACAddress macAddress;
    
    /**
     * The IP address associated with this entity, or null if no IP learned
     * from the network observation associated with this entity
     */
    protected Integer ipv4Address;
    
    /**
     * The VLAN tag on this entity, or null if untagged
     */
    protected Short vlan;
    
    /**
     * The DPID of the switch for the ingress point for this entity,
     * or null if not present
     */
    protected Long switchDPID;
    
    /**
     * The port number of the switch for the ingress point for this entity,
     * or null if not present
     */
    protected Integer switchPort;
    
    /**
     * The last time we observed this entity on the network
     */
    protected Date lastSeenTimestamp;

    // ************
    // Constructors
    // ************
    
    /**
     * Create a new entity
     * 
     * @param macAddress
     * @param ipv4Address
     * @param vlan
     * @param switchDPID
     * @param switchPort
     * @param lastSeenTimestamp
     */
    public Entity(MACAddress macAddress, Integer ipv4Address, 
                  Short vlan, Long switchDPID, Integer switchPort, 
                  Date lastSeenTimestamp) {
        super();
        this.macAddress = macAddress;
        this.ipv4Address = ipv4Address;
        this.vlan = vlan;
        this.switchDPID = switchDPID;
        this.switchPort = switchPort;
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    // ***************
    // Getters/Setters
    // ***************

    public MACAddress getMacAddress() {
        return macAddress;
    }

    public Integer getIpv4Address() {
        return ipv4Address;
    }

    public Short getVlan() {
        return vlan;
    }

    public Long getSwitchDPID() {
        return switchDPID;
    }

    public Integer getSwitchPort() {
        return switchPort;
    }

    public Date getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(Date lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((ipv4Address == null) ? 0 : ipv4Address.hashCode());
        result = prime * result
                 + ((macAddress == null) ? 0 : macAddress.hashCode());
        result = prime * result
                 + ((switchDPID == null) ? 0 : switchDPID.hashCode());
        result = prime * result
                 + ((switchPort == null) ? 0 : switchPort.hashCode());
        result = prime * result + ((vlan == null) ? 0 : vlan.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Entity other = (Entity) obj;
        if (ipv4Address == null) {
            if (other.ipv4Address != null) return false;
        } else if (!ipv4Address.equals(other.ipv4Address)) return false;
        if (macAddress == null) {
            if (other.macAddress != null) return false;
        } else if (!macAddress.equals(other.macAddress)) return false;
        if (switchDPID == null) {
            if (other.switchDPID != null) return false;
        } else if (!switchDPID.equals(other.switchDPID)) return false;
        if (switchPort == null) {
            if (other.switchPort != null) return false;
        } else if (!switchPort.equals(other.switchPort)) return false;
        if (vlan == null) {
            if (other.vlan != null) return false;
        } else if (!vlan.equals(other.vlan)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "Entity [macAddress=" + macAddress + ", ipv4Address="
               + ipv4Address + ", vlan=" + vlan + ", switchDPID="
               + switchDPID + ", switchPort=" + switchPort + "]";
    }
    
}
