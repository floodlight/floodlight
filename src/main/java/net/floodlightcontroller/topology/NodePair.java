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

public class NodePair {
    private long min;
    private long max;

    public NodePair(long a, long b) {
        if (a < b) {
            min = a; 
            max = b;
        } else {
            min = b;
            max = a;
        }
    }

    public long getNode() {
        return min;
    }

    public long getOtherNode() {
        return max;
    }

    public String toString() {
        return "[" + new Long(min) + ", " + new Long(max) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (max ^ (max >>> 32));
        result = prime * result + (int) (min ^ (min >>> 32));
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
        NodePair other = (NodePair) obj;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        return true;
    }
}
