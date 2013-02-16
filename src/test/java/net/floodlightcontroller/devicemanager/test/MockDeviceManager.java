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

package net.floodlightcontroller.devicemanager.test;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.AttachmentPoint;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;

/**
 * Mock device manager useful for unit tests
 * @author readams
 */
public class MockDeviceManager extends DeviceManagerImpl {
    /**
     * Set a new IEntityClassifier
     * Use this as a quick way to use a particular entity classifier in a 
     * single test without having to setup the full FloodlightModuleContext
     * again.
     * @param ecs 
     */
    public void setEntityClassifier(IEntityClassifierService ecs) {
        this.entityClassifier = ecs;
        this.startUp(null);
    }
    
    /**
     * Learn a device using the given characteristics. 
     * @param macAddress the MAC
     * @param vlan the VLAN (can be null)
     * @param ipv4Address the IP (can be null)
     * @param switchDPID the attachment point switch DPID (can be null)
     * @param switchPort the attachment point switch port (can be null)
     * @param processUpdates if false, will not send updates.  Note that this 
     * method is not thread safe if this is false
     * @return the device, either new or not
     */
    public IDevice learnEntity(long macAddress, Short vlan, 
                               Integer ipv4Address, Long switchDPID, 
                               Integer switchPort,
                               boolean processUpdates) {
        List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
        if (!processUpdates) {
            deviceListeners.clearListeners();
        }
        
        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
        IDevice res =  learnDeviceByEntity(new Entity(macAddress, vlan, 
                                                      ipv4Address, switchDPID, 
                                                      switchPort, new Date()));
        // Restore listeners
        if (listeners != null) {
            for (IDeviceListener listener : listeners) {
                deviceListeners.addListener("device", listener);
            }
        }
        return res;
    }
    
    @Override
    public void deleteDevice(Device device) {
        super.deleteDevice(device);
    }
    
    /**
     * Learn a device using the given characteristics. 
     * @param macAddress the MAC
     * @param vlan the VLAN (can be null)
     * @param ipv4Address the IP (can be null)
     * @param switchDPID the attachment point switch DPID (can be null)
     * @param switchPort the attachment point switch port (can be null)
     * @return the device, either new or not
     */
    public IDevice learnEntity(long macAddress, Short vlan, 
                               Integer ipv4Address, Long switchDPID, 
                               Integer switchPort) {
        return learnEntity(macAddress, vlan, ipv4Address, 
                           switchDPID, switchPort, true);
    }

    @Override
    protected Device allocateDevice(Long deviceKey,
                                    Entity entity, 
                                    IEntityClass entityClass) {
        return new MockDevice(this, deviceKey, entity, entityClass);
    }
    
    @Override
    protected Device allocateDevice(Long deviceKey,
                                    String dhcpClientName,
                                    List<AttachmentPoint> aps,
                                    List<AttachmentPoint> trueAPs,
                                    Collection<Entity> entities,
                                    IEntityClass entityClass) {
        return new MockDevice(this, deviceKey, aps, trueAPs, entities, entityClass);
    }
    
    @Override
    protected Device allocateDevice(Device device,
                                    Entity entity,
                                    int insertionpoint) {
        return new MockDevice(device, entity, insertionpoint);
    }
}
