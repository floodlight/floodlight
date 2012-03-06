package net.floodlightcontroller.topologymanager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.openflow.util.HexString;

/**
 * 
 * @author Srinivasan Ramasubramanian, Big Switch Networks
 *
 */
public class BroadcastDomain {
    private long id;
    private Set<Long> clusters;
    private Map<Long, Set<NodePortTuple>> ports;

    public BroadcastDomain() {
        id = 0;
        clusters = new HashSet<Long>();
        ports = new HashMap<Long, Set<NodePortTuple>>();
    }

    @Override 
    public int hashCode() {
        return (int)(id ^ id>>>32);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        BroadcastDomain other = (BroadcastDomain) obj;
        return (other.id == this.id);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Set<Long> getClusterIds() {
        return clusters;
    }

    public Map<Long, Set<NodePortTuple>> getClusterPortMap() {
        return ports;
    }
    
    public Set<NodePortTuple> getPortsInCluster(long c) {
        return ports.get(c);
    }

    public Set<NodePortTuple> getPorts() {
        if (clusters == null) return null;

        Set<NodePortTuple> result = new HashSet<NodePortTuple>();
        for(long c: clusters) {
            if (ports.get(c) != null) {
                result.addAll(ports.get(c));
            }
        }

        if (result.isEmpty()) return null;
        return result;
    }

    public void add(NodePortTuple npt, Long cid) {
        clusters.add(cid);
        Set<NodePortTuple> p = ports.get(cid);
        if (p == null) {
            p = new HashSet<NodePortTuple>();
            ports.put(cid, p);
        }
        p.add(npt);
    }

    public String toString() {
        StringBuffer sb =  new StringBuffer("[BroadcastDomain:");; 

        for(Long c: clusters) {
            for(NodePortTuple npt: ports.get(c)) {
                String str = HexString.toHexString(npt.getNodeId());
                sb.append("[");
                sb.append(c);
                sb.append(",");
                sb.append(str);
                sb.append(",");
                sb.append(npt.getPortId());
                sb.append("]");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
