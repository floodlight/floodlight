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

import org.projectfloodlight.openflow.types.DatapathId;

/**
 * Stores the endpoints of a path, in this case datapath ids
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class PathId implements Cloneable, Comparable<PathId> {
    protected DatapathId src;
    protected DatapathId dst;

    public PathId(DatapathId src, DatapathId dst) {
        super();
        this.src = src;
        this.dst = dst;
    }

    public DatapathId getSrc() {
        return src;
    }

    public void setSrc(DatapathId src) {
        this.src = src;
    }

    public DatapathId getDst() {
        return dst;
    }

    public void setDst(DatapathId dst) {
        this.dst = dst;
    }

    @Override
    public int hashCode() {
        final int prime = 2417;
        Long result = new Long(1);
        result = prime * result + ((dst == null) ? 0 : dst.hashCode());
        result = prime * result + ((src == null) ? 0 : src.hashCode());
        return result.hashCode(); 
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PathId other = (PathId) obj;
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

    @Override
    public String toString() {
        return "RouteId [src=" + this.src.toString() + " dst="
                + this.dst.toString() + "]";
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public int compareTo(PathId o) {
        int result = src.compareTo(o.getSrc());
        if (result != 0)
            return result;
        return dst.compareTo(o.getDst());
    }
}
