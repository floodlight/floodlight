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

package net.floodlightcontroller.devicemanager.test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceManager;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifier;
import net.floodlightcontroller.devicemanager.internal.Device;

public class MockDeviceManager implements IDeviceManager {
    protected Map<Long, Device> devices;

    public MockDeviceManager() {
        devices = new HashMap<Long, Device>();
    }
    
    public void addDevices(List<Device> devices) {
        ListIterator<Device> lit = devices.listIterator();
        while (lit.hasNext()) {
            addDevice(lit.next());
        }
    }

    public void addDevice(Device device) {
        this.devices.put(device.getMACAddress(), device);
    }

    public void clearDevices() {
        this.devices.clear();
    }

    @Override
    public void setEntityClassifier(IEntityClassifier classifier) {
        
    }

    @Override
    public void flushEntityCache(IEntityClass entityClass, 
                                 boolean reclassify) {
        
    }

    @Override
    public IDevice findDevice(long macAddress, Integer ipv4Address, 
                              Short vlan, Long switchDPID,
                              Integer switchPort) {
        return null;
    }

    @Override
    public Collection<? extends IDevice> getAllDevices() {
        // TODO Auto-generated method stub
        return null;
    }
}
