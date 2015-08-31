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

import net.floodlightcontroller.core.IListener;

/**
 * Implementors of this interface can receive updates from DeviceManager about
 * the state of devices under its control.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IDeviceListener extends IListener<String> {
    /**
     * Called when a new Device is found
     * @param device the device that changed
     */
    public void deviceAdded(IDevice device);

    /**
     * Called when a Device is removed, this typically occurs when the port the
     * Device is attached to goes down, or the switch it is attached to is
     * removed.
     * @param device the device that changed
     */
    public void deviceRemoved(IDevice device);

    /**
     * Called when a Device has moved to a new location on the network. Note
     * that either the switch or the port or both has changed.
     *
     * @param device the device that changed
     */
    public void deviceMoved(IDevice device);
    
    /**
     * Called when a network address has been added or remove from a device
     * 
     * @param device the device that changed
     */
    public void deviceIPV4AddrChanged(IDevice device);
    
    /**
     * Called when a network address has been added or remove from a device
     * 
     * @param device the device that changed
     */
    public void deviceIPV6AddrChanged(IDevice device);
    
    /**
     * Called when a VLAN tag for the device has been added or removed
     * @param device the device that changed
     */
    public void deviceVlanChanged(IDevice device);
}
