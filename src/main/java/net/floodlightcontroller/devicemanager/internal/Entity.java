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

import javax.annotation.Nonnull;

import net.floodlightcontroller.core.web.serializers.IPv4Serializer;
import net.floodlightcontroller.core.web.serializers.IPv6Serializer;
import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.core.web.serializers.OFPortSerializer;
import net.floodlightcontroller.core.web.serializers.VlanVidSerializer;
import net.floodlightcontroller.core.web.serializers.MacSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;

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
public class Entity implements Comparable<Entity> {
    /**
     * Timeout for computing {@link Entity#activeSince}.
     * @see {@link Entity#activeSince}
     */
    protected static int ACTIVITY_TIMEOUT = 30000;
    
    protected static final Date NO_DATE = new Date(0); /* Jan 1st 1970 00:00:00 */
    
    /**
     * The MAC address associated with this entity
     */
    protected MacAddress macAddress;
    
    /**
     * The IPv4 address associated with this entity, or null if no IP learned
     * from the network observation associated with this entity
     */
    protected IPv4Address ipv4Address;
    
    /**
     * The IPv6 address associated with this entity, or null if no IP learned
     * from the network observation associated with this entity
     */
    protected IPv6Address ipv6Address;
    
    /**
     * The VLAN tag on this entity, or null if untagged
     */
    protected VlanVid vlan;
    
    /**
     * The DPID of the switch for the ingress point for this entity,
     * or null if not present
     */
    protected DatapathId switchDPID;
    
    /**
     * The port number of the switch for the ingress point for this entity,
     * or null if not present
     */
    protected OFPort switchPort;
    
    /**
     * The last time we observed this entity on the network
     */
    protected Date lastSeenTimestamp;

    /**
     * The time between {@link Entity#activeSince} and 
     * {@link Entity#lastSeenTimestamp} is a period of activity for this
     * entity where it was observed repeatedly.  If, when the entity is
     * observed, the  is longer ago than the activity timeout, 
     * {@link Entity#lastSeenTimestamp} and {@link Entity#activeSince} will 
     * be set to the current time.
     */
    protected Date activeSince;
    
    // ************
    // Constructors
    // ************
    
    /**
     * Create a new entity
     * 
     * @param macAddress
     * @param vlan
     * @param ipv4Address
     * @param ipv6Address
     * @param switchDPID
     * @param switchPort
     * @param lastSeenTimestamp
     */
    public Entity(@Nonnull MacAddress macAddress, VlanVid vlan, @Nonnull IPv4Address ipv4Address, 
    		@Nonnull IPv6Address ipv6Address, @Nonnull DatapathId switchDPID, @Nonnull OFPort switchPort, 
                  @Nonnull Date lastSeenTimestamp) {
    	if (macAddress == null) {
    		throw new IllegalArgumentException("MAC address cannot be null. Try MacAddress.NONE if intention is 'no MAC'");
    	}
    	if (ipv4Address == null) {
    		throw new IllegalArgumentException("IPv4 address cannot be null. Try IPv4Address.NONE if intention is 'no IPv4'");
    	}
    	if (ipv6Address == null) {
    		throw new IllegalArgumentException("IPv6 address cannot be null. Try IPv6Address.NONE if intention is 'no IPv6'");
    	}
    	/* VLAN can be null for 'don't care' in query searches */
    	if (switchDPID == null) {
    		throw new IllegalArgumentException("Switch DPID cannot be null. Try DatapathId.NONE if intention is 'no DPID'");
    	}
    	if (switchPort == null) {
    		throw new IllegalArgumentException("Switch port cannot be null. Try OFPort.ZERO if intention is 'no port'");
    	}
    	if (lastSeenTimestamp == null) {
    		throw new IllegalArgumentException("Last seen time stamp cannot be null. Try Entity.NO_DATE if intention is 'no time'");
    	}
    	
        this.macAddress = macAddress;
        this.ipv4Address = ipv4Address;
        this.ipv6Address = ipv6Address;
        this.vlan = vlan;
        this.switchDPID = switchDPID;
        this.switchPort = switchPort;
        this.lastSeenTimestamp = lastSeenTimestamp;
        this.activeSince = lastSeenTimestamp;
    }

    // ***************
    // Getters/Setters
    // ***************

    @JsonSerialize(using=MacSerializer.class)
    public MacAddress getMacAddress() {
        return macAddress;
    }

    @JsonSerialize(using=IPv4Serializer.class)
    public IPv4Address getIpv4Address() {
        return ipv4Address;
    }

    @JsonSerialize(using=IPv6Serializer.class)
    public IPv6Address getIpv6Address() {
        return ipv6Address;
    }
    
    @JsonSerialize(using=VlanVidSerializer.class)
    public VlanVid getVlan() {
        return vlan;
    }

    @JsonSerialize(using=DPIDSerializer.class)
    public DatapathId getSwitchDPID() {
        return switchDPID;
    }

    @JsonSerialize(using=OFPortSerializer.class)
    public OFPort getSwitchPort() {
        return switchPort;
    }
    
    @JsonIgnore
    public boolean hasSwitchPort() {
        return (switchDPID != null && !switchDPID.equals(DatapathId.NONE) && switchPort != null && !switchPort.equals(OFPort.ZERO));
    }

    public Date getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    /**
     * Set the last seen timestamp and also update {@link Entity#activeSince}
     * if appropriate
     * @param lastSeenTimestamp the new last seen timestamp
     * @see {@link Entity#activeSince}
     */
    public void setLastSeenTimestamp(Date lastSeenTimestamp) {
        if (activeSince.equals(Entity.NO_DATE) ||
        		(activeSince.getTime() + ACTIVITY_TIMEOUT) < lastSeenTimestamp.getTime())
            this.activeSince = lastSeenTimestamp;
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public Date getActiveSince() {
        return activeSince;
    }

    public void setActiveSince(Date activeSince) {
        this.activeSince = activeSince;
    }
    
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((ipv4Address == null) ? 0 : ipv4Address.hashCode());
		result = prime * result
				+ ((ipv6Address == null) ? 0 : ipv6Address.hashCode());
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
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Entity other = (Entity) obj;
		if (hashCode() != obj.hashCode())
			return false;
		if (ipv4Address == null) {
			if (other.ipv4Address != null)
				return false;
		} else if (!ipv4Address.equals(other.ipv4Address))
			return false;
		if (ipv6Address == null) {
			if (other.ipv6Address != null)
				return false;
		} else if (!ipv6Address.equals(other.ipv6Address))
			return false;
		if (macAddress == null) {
			if (other.macAddress != null)
				return false;
		} else if (!macAddress.equals(other.macAddress))
			return false;
		if (switchDPID == null) {
			if (other.switchDPID != null)
				return false;
		} else if (!switchDPID.equals(other.switchDPID))
			return false;
		if (switchPort == null) {
			if (other.switchPort != null)
				return false;
		} else if (!switchPort.equals(other.switchPort))
			return false;
		if (vlan == null) {
			if (other.vlan != null)
				return false;
		} else if (!vlan.equals(other.vlan))
			return false;
		return true;
	}

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Entity [macAddress=");
        if (macAddress != null) {
            builder.append(macAddress.toString());
        } else {
            builder.append("null");
        }
        builder.append(", ipv4Address=");
        if (ipv4Address != null) {
            builder.append(ipv4Address.toString());
        } else {
            builder.append("null");
        }
        builder.append(", ipv6Address=");
        if (ipv4Address != null) {
            builder.append(ipv6Address.toString());
        } else {
            builder.append("null");
        }
        builder.append(", vlan=");
        if (vlan != null) {
            builder.append(vlan.getVlan());
        } else {
            builder.append("null");
        }
        builder.append(", switchDPID=");
        if (switchDPID != null) {
            builder.append(switchDPID.toString());
        } else {
            builder.append("null");
        }
        builder.append(", switchPort=");
        if (switchPort != null) {
            builder.append(switchPort.getPortNumber());
        } else {
            builder.append("null");
        }
        builder.append(", lastSeenTimestamp=");
        if (lastSeenTimestamp != null) {
            builder.append(lastSeenTimestamp == null? "null" : lastSeenTimestamp.toString());
            
        } else {
            builder.append("null");
        }
        builder.append(", activeSince=");
        if (activeSince != null) {
            builder.append(activeSince == null? "null" : activeSince.toString());
        } else {
            builder.append("null");
        }
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int compareTo(Entity o) {
        if (macAddress.getLong() < o.macAddress.getLong()) return -1;
        if (macAddress.getLong() > o.macAddress.getLong()) return 1;

        int r;
        if (switchDPID == null)
            r = o.switchDPID == null ? 0 : -1;
        else if (o.switchDPID == null)
            r = 1;
        else
            r = switchDPID.compareTo(o.switchDPID);
        if (r != 0) return r;

        if (switchPort == null)
            r = o.switchPort == null ? 0 : -1;
        else if (o.switchPort == null)
            r = 1;
        else
            r = switchPort.compareTo(o.switchPort);
        if (r != 0) return r;

        if (ipv4Address == null)
            r = o.ipv4Address == null ? 0 : -1;
        else if (o.ipv4Address == null)
            r = 1;
        else
            r = ipv4Address.compareTo(o.ipv4Address);
        if (r != 0) return r;
        
        if (ipv6Address == null)
            r = o.ipv6Address == null ? 0 : -1;
        else if (o.ipv6Address == null)
            r = 1;
        else
            r = ipv6Address.compareTo(o.ipv6Address);
        if (r != 0) return r;

        if (vlan == null)
            r = o.vlan == null ? 0 : -1;
        else if (o.vlan == null)
            r = 1;
        else
            r = vlan.compareTo(o.vlan);
        if (r != 0) return r;

        return 0;
    }
}