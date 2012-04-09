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

package net.floodlightcontroller.devicemanager;

/**
 * Used to interact with DeviceManager implementations
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */

import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Used to interact with DeviceManager implementations
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IDeviceManagerService extends IFloodlightService {

    /**
     * Returns a device for the given data layer address
     * @param address
     * @return
     */
    public Device getDeviceByDataLayerAddress(byte[] address);

    /**
     * Returns a device for the given data layer address
     * @param address
     * @return
     */
    public Device getDeviceByDataLayerAddress(long address);

    /**
     * Returns a device for the given IPv4 address
     * @param address
     * @return
     */
    public Device getDeviceByIPv4Address(Integer address);

    /** 
     * Invalidate all APs of a device
     * @param address
     * @return
     */
    public void invalidateDeviceAPsByIPv4Address(Integer address);

    /**
     * Returns a list of all known devices in the system
     * @return
     */
    public List<Device> getDevices();
    
    /**
     * Adds a listener to listen for IDeviceManagerServices notifications
     * @param listener The listener that wants the notifications
     */
    public void addListener(IDeviceManagerAware listener);
    
    /**
     * Check if a device has attachment point in the same cluster
     * as the switch.
     * @param deviceId
     * @param switchId
     * @return true if yes, false otherwise.
     */
    public boolean isDeviceKnownToCluster(long deviceId, long switchId);
}
