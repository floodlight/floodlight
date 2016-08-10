package net.floodlightcontroller.topology;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.linkdiscovery.SwitchPortTuple;

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
    
    /**
     * Creates a NodePortTuple from the same information
     * in a SwitchPortTuple
     * @param swt
     */
    public NodePortTuple(SwitchPortTuple swt) {
        if (swt.getSw() != null)
            this.nodeId = swt.getSw().getId();
        else
            this.nodeId = 0;
        this.portId = swt.getPort();
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
}
