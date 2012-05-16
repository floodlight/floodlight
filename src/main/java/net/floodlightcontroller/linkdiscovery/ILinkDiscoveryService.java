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

package net.floodlightcontroller.linkdiscovery;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */

import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface ILinkDiscoveryService extends IFloodlightService {
    /**
     * Get the link that either sources from the port or terminates
     * at the port. isSrcPort determines which of the two links is
     * returned.
     * @param idPort
     * @param isSrcPort true for link that sources from idPort.
     * @return linkTuple
     */
    public LinkInfo getLinkInfo(SwitchPortTuple idPort, boolean isSrcPort);
    
    /**
     * Retrieves a map of all known link connections between OpenFlow switches
     * and the associated info (valid time, port states) for the link.
     * @return
     */
    public Map<LinkTuple, LinkInfo> getLinks();
    
    /**
     * Returns an unmodifiable map from switch id to a set of all links with it 
     * as an endpoint.
     */
    public Map<IOFSwitch, Set<LinkTuple>> getSwitchLinks();
    
    /**
     * Adds a listener to listen for ILinkDiscoveryService messages
     * @param listener The listener that wants the notifications
     */
    public void addListener(ILinkDiscoveryListener listener);
    
    /**
     * Retrieves a set of all switch ports on which lldps are suppressed.
     * @return
     */
    public Set<SwitchPortTuple> getSuppressLLDPsInfo();
    
    /**
     * Adds a switch port to suppress lldp set
     * @param sw
     * @param port
     */
    public void AddToSuppressLLDPs(IOFSwitch sw, short port);
    
    /**
     * Removes a switch port from suppress lldp set
     * @param sw
     * @param port
     */
    public void RemoveFromSuppressLLDPs(IOFSwitch sw, short port);
}
