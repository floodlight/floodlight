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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.web.serializers.PathSerializer;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a route between two switches
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
@JsonSerialize(using=PathSerializer.class)
public class Path implements Comparable<Path> {
    protected PathId id;
    protected List<NodePortTuple> switchPorts;
    protected int pathIndex;
    protected int hopCount;
    protected U64 latency;

    public Path(PathId id, List<NodePortTuple> switchPorts) {
        super();
        this.id = id;
        this.switchPorts = switchPorts;
        this.pathIndex = 0; // useful if multipath routing available
    }

    public Path(DatapathId src, DatapathId dst) {
        super();
        this.id = new PathId(src, dst);
        this.switchPorts = new ArrayList<NodePortTuple>();
        this.pathIndex = 0;
    }

    /**
     * @return the id
     */
    public PathId getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(PathId id) {
        this.id = id;
    }

    /**
     * @return the path
     */
    public List<NodePortTuple> getPath() {
        return switchPorts;
    }

    /**
     * @param path the path to set
     */
    public void setPath(List<NodePortTuple> switchPorts) {
        this.switchPorts = switchPorts;
    }

    /**
     * @param pathIndex pathIndex
     */
    public void setPathIndex(int pathIndex) {
        this.pathIndex = pathIndex;
    }
    
    /**
     * @return pathIndex
     */
    public int getPathIndex() {
        return pathIndex;
    }

    public void setHopCount(int hopCount) { 
        this.hopCount = hopCount; 
    }

    public int getHopCount() { 
        return this.hopCount;
    }

    public void setLatency(U64 latency) { 
        this.latency = latency; 
    }

    public U64 getLatency() { 
        return this.latency; 
    }
    
    @Override
    public int hashCode() {
        final int prime = 5791;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((switchPorts == null) ? 0 : switchPorts.hashCode());
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
        Path other = (Path) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (switchPorts == null) {
            if (other.switchPorts != null)
                return false;
        } else if (!switchPorts.equals(other.switchPorts))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Route [id=" + id + ", switchPorts=" + switchPorts + "]";
    }

    /**
     * Compares the path lengths.
     */
    @Override
    public int compareTo(Path o) {
        return ((Integer)switchPorts.size()).compareTo(o.switchPorts.size());
    }
}
