/**
*    Copyright 2012 Big Switch Networks, Inc. 
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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;

import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;

/**
 * An index that maps key fields of an entity to device keys
 */
public abstract class DeviceIndex {
    /**
     * The key fields for this index
     */
    protected EnumSet<DeviceField> keyFields;

    /**
     * Construct a new device index using the provided key fields
     * @param keyFields the key fields to use
     */
    public DeviceIndex(EnumSet<DeviceField> keyFields) {
        super();
        this.keyFields = keyFields;
    }

    /**
     * Find all device keys in the index that match the given entity
     * on all the key fields for this index
     * @param e the entity to search for
     * @return an iterator over device keys
     */
    public abstract Iterator<Long> queryByEntity(Entity entity);
    
    /**
     * Get all device keys in the index.  If certain devices exist
     * multiple times, then these devices may be returned multiple times
     * @return an iterator over device keys
     */
    public abstract Iterator<Long> getAll();

    /**
     * Attempt to update an index with the entities in the provided
     * {@link Device}.  If the update fails because of a concurrent update,
     * will return false.
     * @param device the device to update
     * @param deviceKey the device key for the device
     * @return true if the update succeeded, false otherwise.
     */
    public abstract boolean updateIndex(Device device, Long deviceKey);

    /**
     * Add a mapping from the given entity to the given device key.  This
     * update will not fail because of a concurrent update 
     * @param device the device to update
     * @param deviceKey the device key for the device
     */
    public abstract void updateIndex(Entity entity, Long deviceKey);

    /**
     * Remove the entry for the given entity
     * @param entity the entity to remove
     */
    public abstract void removeEntity(Entity entity);

    /**
     * Remove the given device key from the index for the given entity
     * @param entity the entity to search for
     * @param deviceKey the key to remove
     */
    public abstract void removeEntity(Entity entity, Long deviceKey);
    
    /**
     * Remove the give device from the index only if this the collection
     * of others does not contain an entity that is identical on all the key
     * fields for this index.
     * @param entity the entity to search for
     * @param deviceKey the key to remove
     * @param others the others against which to check
     */
    public void removeEntityIfNeeded(Entity entity, Long deviceKey,
                                     Collection<Entity> others) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        for (Entity o : others) {
            IndexedEntity oio = new IndexedEntity(keyFields, o);
            if (oio.equals(ie)) return;
        }

        Iterator<Long> keyiter = this.queryByEntity(entity);
        while (keyiter.hasNext()) {
                Long key = keyiter.next();
                if (key.equals(deviceKey)) {
                    removeEntity(entity, deviceKey);
                    break;
                }
        }
    }

}
