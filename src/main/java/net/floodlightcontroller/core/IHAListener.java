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

public interface IHAListener extends IListener<HAListenerTypeMarker> {

    /**
     * This notification is fired if the controller's initial role was SLAVE
     * and the controller is now transitioning to MASTER.
     * Modules need to read their initial role in startUp from floodlight
     * provider.
     */
    public void transitionToMaster();

    /**
     * Gets called when the IP addresses of the controller nodes in the
     * controller cluster change. All parameters map controller ID to
     * the controller's IP.
     *
     * @param curControllerNodeIPs The current mapping of controller IDs to IP
     * @param addedControllerNodeIPs These IPs were added since the last update
     * @param removedControllerNodeIPs These IPs were removed since the last update
     */
    public void controllerNodeIPsChanged(
            Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs
            );
}
