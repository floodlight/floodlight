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

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.core.web.serializers.UShortSerializer;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.openflow.util.HexString;

public class Link implements Comparable<Link> {
    private long src;
    private short srcPort;
    private long dst;
    private short dstPort;


    public Link(long srcId, short srcPort, long dstId, short dstPort) {
        this.src = srcId;
        this.srcPort = srcPort;
        this.dst = dstId;
        this.dstPort = dstPort;
    }

    // Convenience method
    public Link(long srcId, int srcPort, long dstId, int dstPort) {
        this.src = srcId;
        this.srcPort = (short) srcPort;
        this.dst = dstId;
        this.dstPort = (short) dstPort;
    }

    @JsonProperty("src-switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getSrc() {
        return src;
    }

    @JsonProperty("src-port")
    @JsonSerialize(using=UShortSerializer.class)
    public short getSrcPort() {
        return srcPort;
    }

    @JsonProperty("dst-switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getDst() {
        return dst;
    }
    @JsonProperty("dst-port")
    @JsonSerialize(using=UShortSerializer.class)
    public short getDstPort() {
        return dstPort;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (dst ^ (dst >>> 32));
        result = prime * result + dstPort;
        result = prime * result + (int) (src ^ (src >>> 32));
        result = prime * result + srcPort;
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
        if (dst != other.dst)
            return false;
        if (dstPort != other.dstPort)
            return false;
        if (src != other.src)
            return false;
        if (srcPort != other.srcPort)
            return false;
        return true;
    }


    @Override
    public String toString() {
        return "Link [src=" + HexString.toHexString(this.src) 
                + " outPort="
                + (srcPort & 0xffff)
                + ", dst=" + HexString.toHexString(this.dst)
                + ", inPort="
                + (dstPort & 0xffff)
                + "]";
    }
    
    public String toKeyString() {
    	return (HexString.toHexString(this.src) + "|" +
    			(this.srcPort & 0xffff) + "|" +
    			HexString.toHexString(this.dst) + "|" +
    		    (this.dstPort & 0xffff) );
    }

    @Override
    public int compareTo(Link a) {
        // compare link based on natural ordering - src id, src port, dst id, dst port
        if (this.getSrc() != a.getSrc())
            return (int) (this.getSrc() - a.getSrc());
        
        if (this.getSrcPort() != a.getSrcPort())
            return (int) (this.getSrc() - a.getSrc());
        
        if (this.getDst() != a.getDst())
            return (int) (this.getDst() - a.getDst());
        
        return (int) (this.getDstPort() - a.getDstPort());
    }
}

