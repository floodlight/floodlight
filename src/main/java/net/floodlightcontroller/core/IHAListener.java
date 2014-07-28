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

package net.floodlightcontroller.core;

import java.util.Map;


/** Listener interface for the {@link HARole} of the local controller. Listeners
 *  are notified when the controller transitions to role {@link HARole#ACTIVE}.
 *  <p>
 *  <strong>NOTE:</strong> The floodlight platform currently does not support
 *  a transition to the STANDBY role.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public interface IHAListener extends IListener<HAListenerTypeMarker> {
    /**
     * This notification is fired if the controller's initial role was STANDBY
     * and the controller is now transitioning to ACTIVE.
     * Clients can query the current (and initial) role from
     * {@link IFloodlightProviderService#getRole()} (in startup).
     */
    public void transitionToActive();

    /**
     * This notification is fired if the controller's initial role was ACTIVE
     * and the controller is now transitioning to STANDBY.
     * <strong>NOTE:</strong> The floodlight platform currently terminates
     * after the transition to STANDBY. Clients should prepare for the shutdown
     * in transitionToStandby (e.g., ensure that current updates to operational
     * states are fully synced).
     */
    public void transitionToStandby();
    
    /**
     * Gets called when the IP addresses of the controller nodes in the controller cluster
     * change. All parameters map controller ID to the controller's IP.
     * 
     * @param curControllerNodeIPs
     * @param addedControllerNodeIPs
     * @param removedControllerNodeIPs
     */
    public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
    									Map<String, String> addedControllerNodeIPs,
    									Map<String, String> removedControllerNodeIPs);
}
