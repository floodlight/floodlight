/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.linkdiscovery.Link;

public class Cluster {
    protected DatapathId id; // the lowest id of the nodes
    protected Map<DatapathId, Set<Link>> links; // set of links connected to a node.

    public Cluster() {
        id = DatapathId.NONE;
        links = new HashMap<DatapathId, Set<Link>>();
    }

    public DatapathId getId() {
        return id;
    }

    public void setId(DatapathId id) {
        this.id = id;
    }

    public Map<DatapathId, Set<Link>> getLinks() {
        return links;
    }

    public Set<DatapathId> getNodes() {
        return links.keySet();
    }

    void add(DatapathId n) {
        if (links.containsKey(n) == false) {
            links.put(n, new HashSet<Link>());
			if (id == DatapathId.NONE || n.getLong() < id.getLong()) 
				id = n ;
        }
    }

    void addLink(Link l) {
        add(l.getSrc());
        links.get(l.getSrc()).add(l);

        add(l.getDst());
        links.get(l.getDst()).add(l);
     }

    @Override 
    public int hashCode() {
        return (int) (id.getLong() + id.getLong() >>>32);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        Cluster other = (Cluster) obj;
        return (this.id.equals(other.id));
    }
    
    public String toString() {
        return "[Cluster id=" + id.toString() + ", " + links.keySet() + "]";
    }
}
