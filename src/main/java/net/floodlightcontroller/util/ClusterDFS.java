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

package net.floodlightcontroller.util;

public class ClusterDFS {
    long dfsIndex;
    long parentDFSIndex;
    long lowpoint;
    boolean visited;

    public ClusterDFS() {
        visited = false;
        dfsIndex = Long.MAX_VALUE;
        parentDFSIndex = Long.MAX_VALUE;
        lowpoint = Long.MAX_VALUE;
    }

    public long getDfsIndex() {
        return dfsIndex;
    }

    public void setDfsIndex(long dfsIndex) {
        this.dfsIndex = dfsIndex;
    }

    public long getParentDFSIndex() {
        return parentDFSIndex;
    }

    public void setParentDFSIndex(long parentDFSIndex) {
        this.parentDFSIndex = parentDFSIndex;
    }

    public long getLowpoint() {
        return lowpoint;
    }

    public void setLowpoint(long lowpoint) {
        this.lowpoint = lowpoint;
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }
}
