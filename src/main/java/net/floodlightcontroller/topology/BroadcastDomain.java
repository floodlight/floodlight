package net.floodlightcontroller.topology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.openflow.util.HexString;

import net.floodlightcontroller.core.IOFSwitch;

/**
 * 
 * @author Srinivasan Ramasubramanian, Big Switch Networks
 *
 */
public class BroadcastDomain {
    private Long id;
    private Set<Long> clusterIds;
    private Map<Long, Set<Long>> clusterSwitchMap;
    private Map<Long, Set<Short>> switchPortMap;

    public BroadcastDomain() {
        id = null;
        clusterIds = new HashSet<Long>();
        clusterSwitchMap = new HashMap<Long,Set<Long>>();
        switchPortMap = new HashMap<Long, Set<Short>>();
    }

    public Long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = new Long(id);
    }

    public Set<Long> getClustersIds() {
        return clusterIds;
    }

    public Map<Long, Set<Long>> getClusterSwitchMap() {
        return clusterSwitchMap;
    }

    public Set<Short> getPorts(IOFSwitch sw) {
        return switchPortMap.get(sw.getId());
    }

    public void add(SwitchPortTuple spt, long clusterId) {
        // Add the switch to the set
        Long cid = clusterId;
        clusterIds.add(cid);
        Set<Long> switchIds = clusterSwitchMap.get(cid);
        if (switchIds == null) {
            switchIds = new HashSet<Long>();
            clusterSwitchMap.put(cid, switchIds);
        }

        long sid = spt.getSw().getId();
        switchIds.add(sid);

        Set<Short> ports = switchPortMap.get(sid);
        // If ports don't exist for that switch, create one
        if (ports == null){
            ports = new HashSet<Short>();
            switchPortMap.put(spt.getSw().getId(), ports);
        }
        // Add the port to the group
        ports.add(spt.getPort());
    }

    public boolean contains(SwitchPortTuple swt) {
        // no ports of that switch is this domain
        if (switchPortMap.get(swt.getSw()) == null)
            return false;
        return (switchPortMap.get(swt.getSw().getId()).contains(swt.getPort()));
    }
    
    public String toString() {
        StringBuffer sb =  new StringBuffer("[BroadcastDomain:");; 

        for(Long c: clusterIds) {
            for(Long s: clusterSwitchMap.get(c)) {
                String str = HexString.toHexString(s);
                for(Short p: switchPortMap.get(s)) {
                    sb.append("[");
                    sb.append(c);
                    sb.append(",");
                    sb.append(str);
                    sb.append(",");
                    sb.append(p);
                    sb.append("]");
                }
            }
        }

        sb.append("]");
        return sb.toString();
    }
}
