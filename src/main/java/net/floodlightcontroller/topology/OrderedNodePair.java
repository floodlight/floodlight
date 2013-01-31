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

public class OrderedNodePair {
    private long src;
    private long dst;

    public OrderedNodePair(long s, long d) {
        src = s;
        dst = d;
    }

    public long getSrc() {
        return src;
    }

    public long getDst() {
        return dst;
    }

    @Override
    public int hashCode() {
        final int prime = 2417;
        int result = 1;
        result = prime * result + (int) (dst ^ (dst >>> 32));
        result = prime * result + (int) (src ^ (src >>> 32));
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
        OrderedNodePair other = (OrderedNodePair) obj;
        if (dst != other.dst)
            return false;
        if (src != other.src)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "OrderedNodePair [src=" + src + ", dst=" + dst + "]";
    }
}
