package net.floodlightcontroller.topologymanager;

public class NodePortTuple {
    protected long nodeId;
    protected short portId;

    public NodePortTuple(long nodeId, short portId) {
        this.nodeId = nodeId;
        this.portId = portId;
    }

    public long getNodeId() {
        return nodeId;
    }
    public void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }
    public short getPortId() {
        return portId;
    }
    public void setPortId(short portId) {
        this.portId = portId;
    }
    
    public String toString() {
        return "[id=" + new Long(nodeId) + ", port=" + new Short(portId) + "]";
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
