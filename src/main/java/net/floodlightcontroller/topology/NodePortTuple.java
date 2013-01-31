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

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.core.web.serializers.UShortSerializer;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.openflow.util.HexString;

/**
 * A NodePortTuple is similar to a SwitchPortTuple
 * but it only stores IDs instead of references
 * to the actual objects.
 * @author srini
 */
public class NodePortTuple {
    protected long nodeId; // switch DPID
    protected short portId; // switch port id

    /**
     * Creates a NodePortTuple
     * @param nodeId The DPID of the switch
     * @param portId The port of the switch
     */
    public NodePortTuple(long nodeId, short portId) {
        this.nodeId = nodeId;
        this.portId = portId;
    }

    public NodePortTuple(long nodeId, int portId) {
        this.nodeId = nodeId;
        this.portId = (short) portId;
    }

    @JsonProperty("switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getNodeId() {
        return nodeId;
    }
    public void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }
    @JsonProperty("port")
    @JsonSerialize(using=UShortSerializer.class)
    public short getPortId() {
        return portId;
    }
    public void setPortId(short portId) {
        this.portId = portId;
    }
    
    public String toString() {
        return "[id=" + HexString.toHexString(nodeId) + ", port=" + new Short(portId) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (nodeId ^ (nodeId >>> 32));
        result = prime * result + portId;
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
        if (nodeId != other.nodeId)
            return false;
        if (portId != other.portId)
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
        return (HexString.toHexString(nodeId)+ "|" + (portId & 0xffff));
    }
}
