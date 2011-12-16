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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.IDeviceManager;
import net.floodlightcontroller.packet.Ethernet;

public class MockDeviceManager implements IDeviceManager {
    protected Map<Long, Device> devices;

    public MockDeviceManager() {
        devices = new HashMap<Long, Device>();
    }
    
    @Override
    public Device getDeviceByDataLayerAddress(byte[] address) {
        return devices.get(Ethernet.toLong(address));
    }
    
    @Override
    public Device getDeviceByDataLayerAddress(long address) {       
        return devices.get(new Long(address));
    }

    @Override
    public Device getDeviceByIPv4Address(Integer address) {
        Iterator<Entry<Long, Device>> it = devices.entrySet().iterator();
        while (it.hasNext()) {
            Device d = it.next().getValue();
            if (null != d && null != d.getNetworkAddress(address))
                return d;
        }
        return null;
    }

    @Override
    public void invalidateDeviceAPsByIPv4Address(Integer address) {
        Iterator<Entry<Long, Device>> it = devices.entrySet().iterator();
        while (it.hasNext()) {
            Device d = it.next().getValue();
            if (null != d && null != d.getNetworkAddress(address))
                d.getAttachmentPoints().clear();
        }
    }
    
    public void addDevices(List<Device> devices) {
        ListIterator<Device> lit = devices.listIterator();
        while (lit.hasNext()) {
            Device d = lit.next();
            this.devices.put(d.getDataLayerAddressAsLong(), d);
        }
    }

    public void addDevice(Device device) {
        this.devices.put(device.getDataLayerAddressAsLong(), device);
    }

    public void clearDevices() {
        this.devices.clear();
    }
    
    @Override
    public List<Device> getDevices() {
        List<Device> devices = new ArrayList<Device>();
        Iterator<Entry<Long, Device>> it = this.devices.entrySet().iterator();
        while (it.hasNext()) {
            devices.add(it.next().getValue());
        }
        return devices;
    }
}
