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

package net.floodlightcontroller.topology;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */

import java.util.Map;
import java.util.Set;
import net.floodlightcontroller.core.IOFSwitch;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface ITopology {
    /**
     * Query to determine if the specified switch id and port tuple are
     * connected to another switch or not.  If so, this means the link
     * is passing LLDPs properly between two OpenFlow switches.
     * @param idPort
     * @return
     */
    public boolean isInternal(SwitchPortTuple idPort);

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
     * Retrieves a set of all the switches in the same cluster as sw.
     * A cluster is a set of switches that are directly or indirectly
     * connected via Openflow switches that use the same controller
     * (not necessarily the same controller instance but any controller
     * instance in a group sharing the same network database).
     * @param sw The switch whose cluster we're obtaining
     * @return Set of switches in the cluster
     */
    public Set<IOFSwitch> getSwitchesInCluster(IOFSwitch sw);
    
    /**
     * Queries whether two switches are in the same cluster.
     * @param switch1
     * @param switch2
     * @return true if the switches are in the same cluster
     */
    public boolean inSameCluster(IOFSwitch switch1, IOFSwitch switch2);
    
    /**
     * Returns an unmodifiable map from switch id to a set of all links with it 
     * as an endpoint.
     */
    public Map<IOFSwitch, Set<LinkTuple>> getSwitchLinks();
}
