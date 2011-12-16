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

import net.floodlightcontroller.core.IOFSwitch;

/**
 * Implementors of this interface can receive updates from DeviceManager about
 * the state of devices under its control.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IDeviceManagerAware {
    /**
     * Called when a new Device is found
     * @param device
     */
    public void deviceAdded(Device device);

    /**
     * Called when a Device is removed, this typically occurs when the port the
     * Device is attached to goes down, or the switch it is attached to is
     * removed.
     * @param device
     */
    public void deviceRemoved(Device device);

    /**
     * Called when a Device has moved to a new location on the network. Note
     * that either the switch or the port or both has changed.
     *
     * @param device the device
     * @param oldSw the old switch
     * @param oldPort the port on the old switch
     * @param sw the current switch
     * @param port the current port on the current switch
     */
    public void deviceMoved(Device device,
                            IOFSwitch oldSw, Short oldPort,
                            IOFSwitch sw, Short port);
    
    /**
     * Called when a network address has been added to a device
     * 
     * @param device the device
     * @param address the new network address
     */
    public void deviceNetworkAddressAdded(Device device, 
                                          DeviceNetworkAddress address);

    /**
     * Called when a network address has been removed from a device
     * 
     * @param device the device
     * @param address the old network address
     */
    public void deviceNetworkAddressRemoved(Device device,
                                            DeviceNetworkAddress address);
    
    /**
     * Called when the VLAN on the device changed
     * @param device the device that changed
     */
    public void deviceVlanChanged(Device device);
}
