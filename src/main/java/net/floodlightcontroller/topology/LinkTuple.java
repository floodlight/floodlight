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
public class LinkTuple {
    protected SwitchPortTuple src;
    protected SwitchPortTuple dst;

    /**
     * @param src
     * @param dst
     */
    public LinkTuple(SwitchPortTuple src, SwitchPortTuple dst) {
        this.src = src;
        this.dst = dst;
    }

    public LinkTuple(IOFSwitch src, Short srcPort, IOFSwitch dst, Short dstPort) {
        this.src = new SwitchPortTuple(src, srcPort);
        this.dst = new SwitchPortTuple(dst, dstPort);
    }

    /**
     * Convenience constructor, ports are cast to shorts
     * @param srcId
     * @param srcPort
     * @param dstId
     * @param dstPort
     */
    public LinkTuple(IOFSwitch src, Integer srcPort, IOFSwitch dst, Integer dstPort) {
        this(src, srcPort.shortValue(), dst, dstPort.shortValue());
    }

    /**
     * @return the src
     */
    public SwitchPortTuple getSrc() {
        return src;
    }

    /**
     * @param src the src to set
     */
    public void setSrc(SwitchPortTuple src) {
        this.src = src;
    }

    /**
     * @return the dst
     */
    public SwitchPortTuple getDst() {
        return dst;
    }

    /**
     * @param dst the dst to set
     */
    public void setDst(SwitchPortTuple dst) {
        this.dst = dst;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 2221;
        int result = 1;
        result = prime * result + ((dst == null) ? 0 : dst.hashCode());
        result = prime * result + ((src == null) ? 0 : src.hashCode());
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
        if (!(obj instanceof LinkTuple))
            return false;
        LinkTuple other = (LinkTuple) obj;
        if (dst == null) {
            if (other.dst != null)
                return false;
        } else if (!dst.equals(other.dst))
            return false;
        if (src == null) {
            if (other.src != null)
                return false;
        } else if (!src.equals(other.src))
            return false;
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "LinkTuple [src=" + src + ",dst=" + dst + "]";
    }
}
