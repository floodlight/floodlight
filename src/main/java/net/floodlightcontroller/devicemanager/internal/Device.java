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
import net.floodlightcontroller.devicemanager.IDevice;

/**
 * Concrete implementation of {@link IDevice}
 * @author readams
 */
public class Device {
    protected Entity[] entities;
    
    /**
     * Create a device from a set of entities
     * @param entities the entities that make up the device
     */
    public Device(Entity... entities) {
        this.entities = entities;
    }

    /**
     * Create a device consisting of all entities from another device plus
     * the additional entities specified.
     * @param device the old device 
     * @param entities the new entities to add
     */
    public Device(Device device, Entity... entities) {
        this.entities = new Entity[device.entities.length + entities.length];
        int i = 0;
        for (; i < device.entities.length; i++) {
            this.entities[i] = device.entities[i];
        }
        for (int j = 0; j < entities.length; j++, i++) {
            this.entities[i] = entities[j];
        }
    }

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
