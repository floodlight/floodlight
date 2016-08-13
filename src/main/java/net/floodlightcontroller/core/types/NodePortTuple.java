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

package net.floodlightcontroller.core.types;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.core.web.serializers.NodePortTupleSerializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * A NodePortTuple is similar to a SwitchPortTuple
 * but it only stores IDs instead of references
 * to the actual objects.
 * @author srini
 */

@JsonSerialize(using=NodePortTupleSerializer.class)
public class NodePortTuple implements Comparable<NodePortTuple> {
    private DatapathId nodeId; // switch DPID
    private OFPort portId; // switch port id

    /**
     * Creates a NodePortTuple
     * @param nodeId The DPID of the switch
     * @param portId The port of the switch
     */
    public NodePortTuple(DatapathId nodeId, OFPort portId) {
        this.nodeId = nodeId;
        this.portId = portId;
    }

    @JsonProperty("switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public DatapathId getNodeId() {
        return nodeId;
    }
    public void setNodeId(DatapathId nodeId) {
        this.nodeId = nodeId;
    }
    @JsonProperty("port")
    public OFPort getPortId() {
        return portId;
    }
    public void setPortId(OFPort portId) {
        this.portId = portId;
    }
    
    public String toString() {
        return "[id=" + nodeId.toString() + ", port=" + portId.toString() + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (nodeId.getLong() ^ (nodeId.getLong() >>> 32));
        result = prime * result + portId.getPortNumber();
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
        NodePortTuple other = (NodePortTuple) obj;
        if (!nodeId.equals(other.nodeId))
            return false;
        if (!portId.equals(other.portId))
            return false;
        return true;
    }

    /**
     * API to return a String value formed wtih NodeID and PortID
     * The portID is a 16-bit field, so mask it as an integer to get full
     * positive value
     * @return
     */
    public String toKeyString() {
        return (nodeId.toString()+ "|" + portId.toString());
    }

    @Override
    public int compareTo(NodePortTuple obj) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        if (this.getNodeId().getLong() < obj.getNodeId().getLong())
            return BEFORE;
        if (this.getNodeId().getLong() > obj.getNodeId().getLong())
            return AFTER;

        if (this.getPortId().getPortNumber() < obj.getPortId().getPortNumber())
            return BEFORE;
        if (this.getPortId().getPortNumber() > obj.getPortId().getPortNumber())
            return AFTER;

        return EQUAL;
    }
}
