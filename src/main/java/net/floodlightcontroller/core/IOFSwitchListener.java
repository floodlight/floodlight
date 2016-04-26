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

package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

/**
 * Switch lifecycle notifications.
 *
 * These updates /happen-after/ the corresponding changes have been
 * committed. I.e., the changes are visible when
 * {@link IFloodlightProviderService#getSwitch(long)}
 * {@link IFloodlightProviderService#getAllSwitchDpids()}
 * {@link IFloodlightProviderService#getAllSwitchMap()}
 * or any method on the IOFSwitch returned by these methods are
 * called from the notification method or after it.
 *
 * Note however, that additional changes could have been committed before
 * the notification for which the notification is still pending. E.g.,
 * {@link IFloodlightProviderService#getSwitch(long)} might return null after
 * a switchAdded() (which happens if the switch has been added and then
 * removed and the remove hasn't been dispatched yet).
 *
 * These lifecycle notification methods are called by a single thread and they
 * will always be called by the same thread.
 * The calls are always in order.
 *
 */
public interface IOFSwitchListener {
    /**
     * Fired when switch becomes known to the controller cluster. I.e.,
     * the switch is connected at some controller in the cluster
     * @param switchId the datapath Id of the new switch
     */
    public void switchAdded(DatapathId switchId);

    /**
     * Fired when a switch disconnects from the cluster,
     * @param switchId the datapath Id of the switch
     */
    public void switchRemoved(DatapathId switchId);

    /**
     * Fired when a switch becomes active *on the local controller*, I.e.,
     * the switch is connected to the local controller and is in MASTER mode
     * @param switchId the datapath Id of the switch
     */
    public void switchActivated(DatapathId switchId);

    /**
     * Fired when a port on a known switch changes.
     *
     * A user of this notification needs to take care if the port and type
     * information is used directly and if the collection of ports has been
     * queried as well. This notification will only be dispatched after the
     * the port changes have been committed to the IOFSwitch instance. However,
     * if a user has previously called {@link IOFSwitch#getPorts()} or related
     * method a subsequent update might already be present in the information
     * returned by getPorts.
     * @param switchId
     * @param port
     * @param type
     */
    public void switchPortChanged(DatapathId switchId,
                                  OFPortDesc port,
                                  PortChangeType type);

    /**
     * Fired when any non-port related information (e.g., attributes,
     * features) change after a switchAdded
     * TODO: currently unused
     * @param switchId
     */
    public void switchChanged(DatapathId switchId);
    
    
    /**
     * Fired when receive a ROLE_STATUS message, 
     * TODO: At Master to Slave transitions, switch become deactivated. 
     * @param switchId
     */
    public void switchDeactivated(DatapathId switchId);
    
    
}
