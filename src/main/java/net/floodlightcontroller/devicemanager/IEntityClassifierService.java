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

package net.floodlightcontroller.devicemanager;

import java.util.Collection;
import java.util.EnumSet;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.internal.Entity;

/**
 * A component that wishes to participate in entity classification needs to 
 * implement the IEntityClassifier interface, and register with the Device
 * Manager as an entity classifier. An entity is classified by the classifier
 * into an {@link IEntityClass} 
 * 
 * @author readams
 */
public interface IEntityClassifierService extends IFloodlightService {
    /**
    * Classify the given entity into an IEntityClass.  It is important
    * that the key fields returned by {@link IEntityClassifierService#getKeyFields()}
    * be sufficient for classifying entities.  That is, if two entities are
    * identical except for a field that is not a key field, they must be
    * assigned the same class.  Furthermore, entity classification must be
    * transitive: For all entities x, y, z, if x and y belong to a class c, and 
    * y and z belong class c, then x and z must belong to class c.
    * 
    * @param entity the entity to classify
    * @return the IEntityClass resulting from the classification.
    * @see IEntityClassifierService#getKeyFields()
    */
   IEntityClass classifyEntity(Entity entity);

   /**
    * Return the most general list of fields that should be used as key 
    * fields.  If devices differ in any fields not listed here, they can
    * never be considered a different device by any {@link IEntityClass} 
    * returned by {@link IEntityClassifierService#classifyEntity}.  The key fields
    * for an entity classifier must not change unless associated with a 
    * flush of all entity state.  The list of key fields must be the union
    * of all key fields that could be returned by
    * {@link IEntityClass#getKeyFields()}.
    * 
    * @return a set containing the fields that should not be
    * wildcarded.  May be null to indicate that all fields are key fields.
    * @see {@link IEntityClass#getKeyFields()}
    * @see {@link IEntityClassifierService#classifyEntity}
    */
   EnumSet<DeviceField> getKeyFields();

   /**
    * Reclassify the given entity into a class.  When reclassifying entities,
    * it can be helpful to take into account the current classification either
    * as an optimization or to allow flushing any cached state tied to the key
    * for that device.  The entity will be assigned to a new device with a new
    * object if the entity class returned is different from the entity class for
    * curDevice.
    * 
    * <p>Note that you must take steps to ensure you always return classes
    * in some consistent ordering.

    * @param curDevice the device currently associated with the entity
    * @param entity the entity to reclassify
    * @return the IEntityClass resulting from the classification
    */
   IEntityClass reclassifyEntity(IDevice curDevice, Entity entity);

   /**
    * Once reclassification is complete for a device, this method will be
    * called. If any entities within the device changed their classification,
    * it will split into one or more new devices for each of the entities.  If
    * two devices are merged because of a reclassification, then this will be
    * called on each of the devices, with the same device in the newDevices 
    * collection.
    * 
    * @param oldDevice the original device object
    * @param newDevices all the new devices derived from the entities of the
    * old device.  If null, the old device was unchanged.
    */
   void deviceUpdate(IDevice oldDevice, Collection<? extends IDevice> newDevices);

   /**
    * Adds a listener to listen for IEntityClassifierServices notifications
    *
    * @param listener The listener that wants the notifications
    */
   public void addListener(IEntityClassListener listener);
}

