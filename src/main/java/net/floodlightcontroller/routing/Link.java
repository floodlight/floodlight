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

import org.openflow.util.HexString;

/**
 * Represents a link between two datapaths. It is assumed that
 * Links will generally be held in a list, and that the first datapath's
 * id will be held in a different structure.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class Link {
    /**
     * Outgoing port number of the current datapath the link connects to
     */
    protected Short outPort;

    /**
     * Destination datapath id
     */
    protected Long dst;

    /**
     * Incoming port number on the dst datapath the link connects to
     */
    protected Short inPort;

    public Link(Short outPort, Short inPort, Long dst) {
        super();
        this.outPort = outPort;
        this.inPort = inPort;
        this.dst = dst;
    }

    /**
     * @return the port number of the switch this link begins on
     */
    public Short getOutPort() {
        return outPort;
    }

    /**
     * @param outPort the outPort to set
     */
    public void setOutPort(Short outPort) {
        this.outPort = outPort;
    }

    /**
     * @return the switch id of the destination switch on this link
     */
    public Long getDst() {
        return dst;
    }

    /**
     * @param dst the dst to set
     */
    public void setDst(Long dst) {
        this.dst = dst;
    }

    /**
     * @return the port number of the destination switch on this link
     */
    public Short getInPort() {
        return inPort;
    }

    /**
     * @param inPort the inPort to set
     */
    public void setInPort(Short inPort) {
        this.inPort = inPort;
    }

    @Override
    public int hashCode() {
        final int prime = 3203;
        int result = 1;
        result = prime * result + ((dst == null) ? 0 : dst.hashCode());
        result = prime * result + ((inPort == null) ? 0 : inPort.hashCode());
        result = prime * result + ((outPort == null) ? 0 : outPort.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Link other = (Link) obj;
        if (dst == null) {
            if (other.dst != null)
                return false;
        } else if (!dst.equals(other.dst))
            return false;
        if (inPort == null) {
            if (other.inPort != null)
                return false;
        } else if (!inPort.equals(other.inPort))
            return false;
        if (outPort == null) {
            if (other.outPort != null)
                return false;
        } else if (!outPort.equals(other.outPort))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Link [outPort="
                + ((outPort == null) ? "null" : (0xffff & outPort))
                + ", inPort="
                + ((inPort == null) ? "null" : (0xffff & inPort))
                + ", dst=" + HexString.toHexString(this.dst) + "]";
    }
}
