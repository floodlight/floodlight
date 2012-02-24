package net.floodlightcontroller.devicemanager.test;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;

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
     * @return the device, either new or not
     */
    public IDevice learnEntity(long macAddress, Short vlan, 
                               Integer ipv4Address, Long switchDPID, 
                               Integer switchPort) {
        return learnDeviceByEntity(new Entity(macAddress, vlan, 
                                              ipv4Address, switchDPID, 
                                              switchPort, null));
    }
}
