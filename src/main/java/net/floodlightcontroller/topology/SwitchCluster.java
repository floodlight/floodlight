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

import net.floodlightcontroller.core.IOFSwitch;

/**
 * This class represents a cluster of OpenFlow switches.
 * A cluster is defined as a group of OpenFlow switches
 * that have links to each other.
 * @author alex@bigswitch.com
 */
public class SwitchCluster {
    private Long id; // the lowest DPID of any switches in this island
    private Set<IOFSwitch> switches;
    
    public SwitchCluster() {
        switches = new HashSet<IOFSwitch>();
        id = null; // invalid
    }
    
    public Long getId() {
        return id;
    }
    
    public void add(IOFSwitch s) {
        switches.add(s);
        if (id == null || s.getId() < id) {
            id = s.getId();
            for (IOFSwitch sw : switches) {
                sw.setSwitchClusterId(id);
            }
        } else {
            s.setSwitchClusterId(id);
        }
    }
    
    public void remove(IOFSwitch s) {
        if (switches.contains(s)) {
            switches.remove(s);
            if (s.getId() == id) {
                // Find the next lowest id
                long id = Long.MAX_VALUE;
                for (IOFSwitch sw : switches) {
                    if (sw.getId() < id) {
                        id = sw.getId();
                    }
                }
            }
        }
    }
    
    public Set<IOFSwitch> getSwitches() {
        return switches;
    }
}
