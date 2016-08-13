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

package net.floodlightcontroller.linkdiscovery;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

public class Link implements Comparable<Link> {
    @JsonProperty("src-switch")
    private DatapathId src;
    @JsonProperty("src-port")
    private OFPort srcPort;
    @JsonProperty("dst-switch")
    private DatapathId dst;
    @JsonProperty("dst-port")
    private OFPort dstPort;
    @JsonProperty("latency") 
    private U64 latency; /* we intentionally exclude the latency from hashcode and equals */

    public Link(DatapathId srcId, OFPort srcPort, DatapathId dstId, OFPort dstPort, U64 latency) {
        this.src = srcId;
        this.srcPort = srcPort;
        this.dst = dstId;
        this.dstPort = dstPort;
        this.latency = latency;
    }

    /*
     * Do not use this constructor. Used primarily for JSON
     * Serialization/Deserialization
     */
    public Link() {
        super();
    }

    public DatapathId getSrc() {
        return src;
    }

    public OFPort getSrcPort() {
        return srcPort;
    }

    public DatapathId getDst() {
        return dst;
    }

    public OFPort getDstPort() {
        return dstPort;
    }
    
    public U64 getLatency() {
    	return latency;
    }

    public void setSrc(DatapathId src) {
        this.src = src;
    }

    public void setSrcPort(OFPort srcPort) {
        this.srcPort = srcPort;
    }

    public void setDst(DatapathId dst) {
        this.dst = dst;
    }

    public void setDstPort(OFPort dstPort) {
        this.dstPort = dstPort;
    }
    
    public void setLatency(U64 latency) {
    	this.latency = latency;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (dst.getLong() ^ (dst.getLong() >>> 32));
        result = prime * result + dstPort.getPortNumber();
        result = prime * result + (int) (src.getLong() ^ (src.getLong() >>> 32));
        result = prime * result + srcPort.getPortNumber();
        return result; /* do not include latency */
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
        if (!dst.equals(other.dst))
            return false;
        if (!dstPort.equals(other.dstPort))
            return false;
        if (!src.equals(other.src))
            return false;
        if (!srcPort.equals(other.srcPort))
            return false;
        return true; /* do not include latency */
    }


    @Override
    public String toString() {
        return "Link [src=" + this.src.toString() 
                + " outPort="
                + srcPort.toString()
                + ", dst=" + this.dst.toString()
                + ", inPort="
                + dstPort.toString()
                + ", latency="
                + String.valueOf(latency.getValue())
                + "]";
    }
    
    public String toKeyString() {
    	return (this.src.toString() + "|" +
    			this.srcPort.toString() + "|" +
    			this.dst.toString() + "|" +
    		    this.dstPort.toString());
    }

    @Override
    public int compareTo(Link a) {
        // compare link based on natural ordering - src id, src port, dst id, dst port
        int srcComp = this.getSrc().compareTo(a.getSrc());
        if (srcComp != 0)
            return srcComp;
        
        int srcPortComp = this.getSrcPort().compareTo(a.getSrcPort());
        if (srcPortComp != 0)
            return srcPortComp;
            
        int dstComp = this.getDst().compareTo(a.getDst());
        if (dstComp != 0)
            return dstComp;
        
        return this.getDstPort().compareTo(a.getDstPort());
    }
}