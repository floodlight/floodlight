package net.floodlightcontroller.devicemanager.test;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;
import net.floodlightcontroller.devicemanager.internal.MockDevice;

/**
 * Mock device manager useful for unit tests
 * @author readams
 */
public class MockDeviceManager extends DeviceManagerImpl {
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
        Set<IDeviceManagerAware> listeners = deviceListeners;
        if (!processUpdates) {
            deviceListeners = Collections.<IDeviceManagerAware>emptySet();
        }
        
        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
        IDevice res =  learnDeviceByEntity(new Entity(macAddress, vlan, 
                                                      ipv4Address, switchDPID, 
                                                      switchPort, null));
        deviceListeners = listeners;
        return res;
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
                                    Collection<IEntityClass> entityClasses) {
        return new MockDevice(this, deviceKey, entity, entityClasses);
    }
    
    @Override
    protected Device allocateDevice(Device device,
                                    Entity entity, 
                                    Collection<IEntityClass> entityClasses) {
        return new MockDevice(device, entity, entityClasses);
    }
}
