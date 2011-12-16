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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a route between two switches
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class Route implements Cloneable, Comparable<Route> {
    protected RouteId id;
    protected List<Link> path;

    public Route(RouteId id, List<Link> path) {
        super();
        this.id = id;
        this.path = path;
    }

    public Route(Long src, Long dst) {
        super();
        this.id = new RouteId(src, dst);
        this.path = new ArrayList<Link>();
    }

    /**
     * Concise way to instantiate a route.  The format of objects must be:
     *  (Short/Integer outPort, Short/Integer inPort, Long dstDpid)*
     * @param srcDpid
     * @param objects
     */
    public Route(Long srcDpid, Object... routeElements) {
        super();
        this.path = new ArrayList<Link>();
        if (routeElements.length % 3 > 0)
            throw new RuntimeException("routeElements must be a multiple of 3");
        Short outPort, inPort;

        for (int i = 0; i < routeElements.length; i += 3) {
            if (routeElements[i] instanceof Short)
                outPort = (Short) routeElements[i];
            else
                outPort = ((Integer)routeElements[i]).shortValue();

            if (routeElements[i+1] instanceof Short)
                inPort = (Short) routeElements[i+1];
            else
                inPort = ((Integer)routeElements[i+1]).shortValue();

            this.path.add(new Link(outPort, inPort, (Long) routeElements[i + 2]));
        }
        this.id = new RouteId(srcDpid, (this.path.size() == 0) ? srcDpid
                : this.path.get(this.path.size() - 1).dst);
    }

    /**
     * @return the id
     */
    public RouteId getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(RouteId id) {
        this.id = id;
    }

    /**
     * @return the path
     */
    public List<Link> getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(List<Link> path) {
        this.path = path;
    }

    @Override
    public int hashCode() {
        final int prime = 5791;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((path == null) ? 0 : path.hashCode());
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
        Route other = (Route) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Route [id=" + id + ", path=" + path + "]";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object clone() throws CloneNotSupportedException {
        Route clone = (Route) super.clone();
        clone.setId((RouteId) this.id.clone());
        clone.path = (List<Link>) ((ArrayList<Link>)this.path).clone();
        return clone;
    }

    /**
     * Compares the path lengths between Routes.
     */
    @Override
    public int compareTo(Route o) {
        return ((Integer)path.size()).compareTo(o.path.size());
    }
}
