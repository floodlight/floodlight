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

package net.floodlightcontroller.routing;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Route;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IRoutingEngine {
    public Route getRoute(IOFSwitch src, IOFSwitch dst);

    public Route getRoute(Long srcDpid, Long dstDpid);

    /**
     * Checks if a route exists between two switches
     * @param srcId
     * @param dstId
     * @return true if at least one route exists between the switches
     */
    public boolean routeExists(Long srcId, Long dstId);

    /**
     * Get a broadcast tree with the given rootNode as the root
     * @param srcId
     * @return a broadcast tree with the given rootNode as the root, 
     *         or null if the rootNode is not in the topology
     */
    public BroadcastTree getBCTree(Long rootNode);
    
    /**
     * 
     */
    /**
     * Updates a link status
     * @param srcId
     * @param srcPort
     * @param dstId
     * @param dstPort
     * @param added true if the link is new, false if its being removed
     */
    public void update(Long srcId, Short srcPort, Long dstId,
            Short dstPort, boolean added);

    /**
     * This is merely a convenience method that calls
     * @see #update(Long, Short, Long, Short, boolean) and truncates the extra
     * bits from the ports
     * @param srcId
     * @param srcPort
     * @param dstId
     * @param dstPort
     * @param added
     */
    public void update(Long srcId, Integer srcPort, Long dstId,
            Integer dstPort, boolean added);

    /**
     * Remove all routes and reset all state. USE CAREFULLY!
     */
    public void clear();
}
