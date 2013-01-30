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

import net.floodlightcontroller.routing.Link;

import org.openflow.util.HexString;

public class Cluster {
    protected long id; // the lowest id of the nodes
    protected Map<Long, Set<Link>> links; // set of links connected to a node.

    public Cluster() {
        id = Long.MAX_VALUE;
        links = new HashMap<Long, Set<Link>>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Map<Long, Set<Link>> getLinks() {
        return links;
    }

    public Set<Long> getNodes() {
        return links.keySet();
    }

    void add(long n) {
        if (links.containsKey(n) == false) {
            links.put(n, new HashSet<Link>());
            if (n < id) id = n;
        }
    }

    void addLink(Link l) {
        if (links.containsKey(l.getSrc()) == false) {
            links.put(l.getSrc(), new HashSet<Link>());
            if (l.getSrc() < id) id = l.getSrc();
        }
        links.get(l.getSrc()).add(l);

        if (links.containsKey(l.getDst()) == false) {
            links.put(l.getDst(), new HashSet<Link>());
            if (l.getDst() < id) id = l.getDst();
        }
        links.get(l.getDst()).add(l);
     }

    @Override 
    public int hashCode() {
        return (int) (id + id >>>32);
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
        return (this.id == other.id);
    }
    
    public String toString() {
        return "[Cluster id=" + HexString.toHexString(id) + ", " + links.keySet() + "]";
    }
}
