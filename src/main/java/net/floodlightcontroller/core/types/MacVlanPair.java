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

package net.floodlightcontroller.core.types;

import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;

public class MacVlanPair {
    public MacAddress mac;
    public VlanVid vlan;
    public MacVlanPair(MacAddress mac2, VlanVid vlan2) {
        this.mac = mac2;
        this.vlan = vlan2;
    }
    
    public MacAddress getMac() {
        return mac;
    }
    
    public VlanVid getVlan() {
        return vlan;
    }
    
    public boolean equals(Object o) {
        return (o instanceof MacVlanPair) && (mac.equals(((MacVlanPair) o).mac))
            && (vlan.equals(((MacVlanPair) o).vlan));
    }
    
    public int hashCode() {
        return mac.hashCode() ^ vlan.hashCode();
    }

    public String toString() {
        return "(" + mac.toString() + ", " + vlan.toString() + ")";
    }
}
