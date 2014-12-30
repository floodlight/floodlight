/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;


/**
 * This is a thin wrapper around {@link Entity} that allows overriding
 * the behavior of {@link Object#hashCode()} and {@link Object#equals(Object)}
 * so that the keying behavior in a hash map can be changed dynamically
 * @author readams
 */
public class IndexedEntity {
    protected EnumSet<DeviceField> keyFields;
    protected Entity entity;
    private int hashCode = 0;
    protected static Logger logger =
            LoggerFactory.getLogger(IndexedEntity.class);
    /**
     * Create a new {@link IndexedEntity} for the given {@link Entity} using 
     * the provided key fields.
     * @param keyFields The key fields that will be used for computing
     * {@link IndexedEntity#hashCode()} and {@link IndexedEntity#equals(Object)}
     * @param entity the entity to wrap
     */
    public IndexedEntity(EnumSet<DeviceField> keyFields, Entity entity) {
        super();
        this.keyFields = keyFields;
        this.entity = entity;
    }

    /**
     * Check whether this entity has non-null values in any of its key fields
     * @return true if any key fields have a non-null value
     */
    public boolean hasNonNullKeys() {
        for (DeviceField f : keyFields) {
            switch (f) {
                case MAC:
                    return true;
                case IPV4:
                    if (entity.ipv4Address != null) return true;
                    break;
                case SWITCH:
                    if (entity.switchDPID != null) return true;
                    break;
                case PORT:
                    if (entity.switchPort != null) return true;
                    break;
                case VLAN:
                    if (entity.vlan != null) return true;
                    break;
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() {
    	
        if (hashCode != 0) {
        	return hashCode;
        }

        final int prime = 31;
        hashCode = 1;
        for (DeviceField f : keyFields) {
            switch (f) {
                case MAC:
                    hashCode = prime * hashCode
                        + (int) (entity.macAddress.getLong() ^ 
                                (entity.macAddress.getLong() >>> 32));
                    break;
                case IPV4:
                    hashCode = prime * hashCode
                        + ((entity.ipv4Address == null) 
                            ? 0 
                            : entity.ipv4Address.hashCode());
                    break;
                case SWITCH:
                    hashCode = prime * hashCode
                        + ((entity.switchDPID == null) 
                            ? 0 
                            : entity.switchDPID.hashCode());
                    break;
                case PORT:
                    hashCode = prime * hashCode
                        + ((entity.switchPort == null) 
                            ? 0 
                            : entity.switchPort.hashCode());
                    break;
                case VLAN:
                    hashCode = prime * hashCode 
                        + ((entity.vlan == null) 
                            ? 0 
                            : entity.vlan.hashCode());
                    break;
            }
        }
        return hashCode;
    }
    
    @Override
    public boolean equals(Object obj) {
       if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        IndexedEntity other = (IndexedEntity) obj;
        
        if (!keyFields.equals(other.keyFields))
            return false;

        for (IDeviceService.DeviceField f : keyFields) {
            switch (f) {
                case MAC:
                    if (!entity.macAddress.equals(other.entity.macAddress))
                        return false;
                    break;
                case IPV4:
                    if (entity.ipv4Address == null) {
                        if (other.entity.ipv4Address != null) return false;
                    } else if (!entity.ipv4Address.equals(other.entity.ipv4Address)) return false;
                    break;
                case SWITCH:
                    if (entity.switchDPID == null) {
                        if (other.entity.switchDPID != null) return false;
                    } else if (!entity.switchDPID.equals(other.entity.switchDPID)) return false;
                    break;
                case PORT:
                    if (entity.switchPort == null) {
                        if (other.entity.switchPort != null) return false;
                    } else if (!entity.switchPort.equals(other.entity.switchPort)) return false;
                    break;
                case VLAN:
                    if (entity.vlan == null) {
                        if (other.entity.vlan != null) return false;
                    } else if (!entity.vlan.equals(other.entity.vlan)) return false;
                    break;
            }
        }
        
        return true;
    }
    
    
}
