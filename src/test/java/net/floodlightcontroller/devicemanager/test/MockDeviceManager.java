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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.packet.Ethernet;

public class MockDeviceManager implements IFloodlightModule, IDeviceManagerService {
    protected Map<Long, Device> devices;
    protected Map<Long, Long> clusters;

    public MockDeviceManager() {
        devices = new HashMap<Long, Device>();
        clusters = new HashMap<Long, Long>();
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

    public void addSwitchToCluster(long switchId, long clusterId) {
        clusters.put(switchId, clusterId);
    }

    public void clearCluster() {
        this.clusters.clear();
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

    @Override
    public boolean isDeviceKnownToCluster(long deviceId, long switchId) {
        Device device = this.devices.get(deviceId);
        if (device == null) {
            return false;
        }
        /** 
         * Iterate through all APs and check if the switch clusterID matches
         * with the given clusterId
         */
        for(DeviceAttachmentPoint dap : device.getAttachmentPoints()) {
            if (dap == null) continue;
            if (this.clusters.get(switchId) == 
                this.clusters.get(dap.getSwitchPort().getSw().getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addListener(IDeviceManagerAware listener) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public
            void
            init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // TODO Auto-generated method stub
        
    }
}
