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

package net.floodlightcontroller.virtualnetwork;

import java.util.Collection;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;

public interface IVirtualNetworkService extends IFloodlightService {
    /**
     * Creates a new virtual network. This can also be called
     * to modify a virtual network. To update a network you specify the GUID
     * and the fields you want to update.
     * @param network The network name. Must be unique.
     * @param guid The ID of the network. Must be unique.
     * @param gateway The IP address of the network gateway, null if none.
     */
    public void createNetwork(String guid, String network, Integer gateway);
    
    /**
     * Deletes a virtual network.
     * @param guid The ID (not name) of virtual network to delete.
     */
    public void deleteNetwork(String guid);
    
    /**
     * Adds a host to a virtual network. If a mapping already exists the
     * new one will override the old mapping.
     * @param mac The MAC address of the host to add.
     * @param network The network to add the host to.
     * @param port The logical port name to attach the host to. Must be unique.
     */
    public void addHost(MACAddress mac, String network, String port); 
    
    /**
     * Deletes a host from a virtual network. Either the MAC or Port must
     * be specified.
     * @param mac The MAC address to delete.
     * @param port The logical port the host is attached to.
     */
    public void deleteHost(MACAddress mac, String port);
    
    /**
     * Return list of all virtual networks.
     * @return Collection <VirtualNetwork>
     */
    public Collection <VirtualNetwork> listNetworks();
}
