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

/**
 *
 */
package net.floodlightcontroller.linkdiscovery;

import net.floodlightcontroller.core.IOFSwitch;

/**
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class SwitchPortTuple {
    protected IOFSwitch sw;
    protected short port;

    public SwitchPortTuple(IOFSwitch sw, short port) {
        super();
        this.sw = sw;
        this.port = port;
    }
    
    public SwitchPortTuple(IOFSwitch sw, int port) {
        this(sw, (short)port);
    }

    /**
     * @return the sw
     */
    public IOFSwitch getSw() {
        return sw;
    }

    /**
     * Set the switch
     */
    public void setSw(IOFSwitch sw) {
        this.sw = sw;
    }
    
    /**
     * @return the port number
     */
    public Short getPort() {
        return port;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5557;
        int result = 1;
        result = prime * result + ((sw == null) ? 0 : sw.hashCode());
        result = prime * result + (new Short(port)).hashCode();
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof SwitchPortTuple))
            return false;
        SwitchPortTuple other = (SwitchPortTuple) obj;
        if (sw == null) {
            if (other.sw != null)
                return false;
        } else if (!sw.equals(other.sw))
            return false;
        
        if (port != other.port)
            return false;

        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SwitchPortTuple [id="
                + ((sw == null) ? "null" : sw.getStringId())
                + ", port=" + (0xffff & (int)port) + "]";
    }
}
