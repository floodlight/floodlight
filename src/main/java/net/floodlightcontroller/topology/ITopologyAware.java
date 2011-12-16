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

import net.floodlightcontroller.core.IOFSwitch;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface ITopologyAware {
    /**
     * @param srcSw the source switch
     * @param srcPort the source port from the source switch
     * @param srcPortState the state of the port (i.e. STP state)
     * @param dstSw
     * @param dstPort
     */
    public void addedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
            IOFSwitch dstSw, short dstPort, int dstPortState);
    
    /**
     * @param srcSw the source switch
     * @param srcPort the source port from the source switch
     * @param srcPortState the state of the src port (i.e. STP state)
     * @param dstSw
     * @param dstPort
     * @param dstPortState
     */
    public void updatedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
            IOFSwitch dstSw, short dstPort, int dstPortState);

    /**
     * @param srcSw
     * @param srcPort
     * @param dstSw
     * @param dstPort
     */
    public void removedLink(IOFSwitch srcSw, short srcPort,
            IOFSwitch dstSw, short dstPort);
    
    /**
     * @param sw
     */
    public void updatedSwitch(IOFSwitch sw);
    
    void clusterMerged();
}
