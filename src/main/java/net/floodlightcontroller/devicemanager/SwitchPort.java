/**
*    Copyright 2012 Big Switch Networks, Inc. 
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

package net.floodlightcontroller.devicemanager;

/**
 * A simple switch DPID/port pair
 * @author readams
 *
 */
public class SwitchPort {
    protected long switchDPID;
    protected int port;

    /**
     * Simple constructor
     * @param switchDPID the dpid
     * @param port the port
     */
    public SwitchPort(long switchDPID, int port) {
        super();
        this.switchDPID = switchDPID;
        this.port = port;
    }
    
    // ***************
    // Getters/Setters
    // ***************

    public long getSwitchDPID() {
        return switchDPID;
    }
    public int getPort() {
        return port;
    }

    // ******
    // Object
    // ******
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + port;
        result = prime * result + (int) (switchDPID ^ (switchDPID >>> 32));
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SwitchPort other = (SwitchPort) obj;
        if (port != other.port) return false;
        if (switchDPID != other.switchDPID) return false;
        return true;
    }

    @Override
    public String toString() {
        return "SwitchPort [switchDPID=" + switchDPID + ", port=" + port
                + "]";
    }
    
}