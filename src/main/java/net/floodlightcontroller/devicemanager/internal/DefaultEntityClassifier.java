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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;

import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.devicemanager.IDeviceManagerService.DeviceField;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifier;

/**
 * This is a default entity classifier that simply classifies all
 * entities into a fixed entity class, with key fields of MAC and VLAN.
 * @author readams
 */
public class DefaultEntityClassifier implements IEntityClassifier {
    /**
     * A default fixed entity class
     */
    protected static class DefaultEntityClass implements IEntityClass {
        @Override
        public EnumSet<IDeviceManagerService.DeviceField> getKeyFields() {
            return keyFields;
        }
    }
    
    protected static EnumSet<DeviceField> keyFields;
    static {
        keyFields = EnumSet.of(DeviceField.MAC, DeviceField.VLAN);
    }
    protected static IEntityClass entityClass = new DefaultEntityClass();
    
    public static Collection<IEntityClass> entityClasses;
    static {
        entityClasses = new ArrayList<IEntityClass>(1);
        entityClasses.add(entityClass);
    }

    @Override
    public Collection<IEntityClass> classifyEntity(Entity entity) {
        return entityClasses;
    }

    @Override
    public Collection<IEntityClass> reclassifyEntity(Device curDevice,
                                                     Entity entity) {
        return entityClasses;
    }

    @Override
    public void deviceUpdate(Device oldDevice, 
                             Collection<Device> newDevices) {
        // no-op
    }

    @Override
    public EnumSet<DeviceField> getKeyFields() {
        return keyFields;
    }
}
