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

import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.topology.internal.TopologyImpl;
import net.floodlightcontroller.util.EventHistory.EvAction;

/**
 * This class represents a cluster of OpenFlow switches.
 * A cluster is defined as a group of OpenFlow switches
 * that have links to each other.
 * @author alex@bigswitch.com
 */
public class SwitchCluster {
    private Long id; // the lowest DPID of any switches in this island
    private Set<Long> switches;
    private TopologyImpl topology;
    
    public SwitchCluster(TopologyImpl topologyMgr) {
        switches = new HashSet<Long>();
        id = null; // invalid
        topology = topologyMgr;
    }
    
    public Long getId() {
        return id;
    }
    
    public void add(long s) {
        switches.add(s);
        if (id == null || s < id) {
            topology.evHistTopoCluster(s, // dpid
                    (id==null)?0:id,   // old cluster id
                    s, // new cluster id
                    EvAction.CLUSTER_ID_CHANGED_FOR_CLUSTER, 
                    "Switch Added");

            id = s;
        }
    }
    
    public void remove(long s) {
        if (switches.contains(s)) {
            switches.remove(s);
            if (s == id) {
                long oldId = id;
                // Find the next lowest id
                long id = Long.MAX_VALUE;
                for (long sw : switches) {
                    if (sw < id) {
                        id = sw;
                    }
                }
                // Cluster ID changed for oldId to id
                topology.evHistTopoCluster(s,
                        oldId, id, 
                        EvAction.CLUSTER_ID_CHANGED_FOR_CLUSTER,
                        "Switch Removed");
            }
        }
    }

    public Set<Long> getSwitches() {
        return switches;
    }
}
