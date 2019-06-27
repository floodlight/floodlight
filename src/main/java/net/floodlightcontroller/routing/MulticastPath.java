package net.floodlightcontroller.routing;

import com.google.common.collect.ImmutableSet;

import net.floodlightcontroller.topology.MulticastGroupId;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * MulticastPath implementation
 * 
 */
public class MulticastPath implements Comparable<MulticastPath> {
    protected MulticastPathId id;
    private final Map<DatapathId, Path> swIdToPathMap;	// Map of mgSwId and Path it belongs to
    private final Map<DatapathId, Set<OFPort>> swIdToApPortMap;	// Map of mgSwId and Set of attachmentPointPorts
    
    protected int pathIndex;
    
    public MulticastPath(MulticastPathId id) {
        super();
        this.id = id;  
        this.swIdToPathMap = new HashMap<DatapathId, Path>();
        this.swIdToApPortMap = new HashMap<DatapathId, Set<OFPort>>();
        this.pathIndex = 0; // useful if multipath multicast routing available
    }
    
    public MulticastPath(DatapathId src, MulticastGroupId mgId) {
        super();
        this.id = new MulticastPathId(src, mgId);
        this.swIdToPathMap = new HashMap<DatapathId, Path>();
        this.swIdToApPortMap = new HashMap<DatapathId, Set<OFPort>>();
        this.pathIndex = 0; // useful if multipath multicast routing available
    }
    
    public MulticastPathId getId() {
        return id;
    }
    
    public void setId(MulticastPathId id) {
        this.id = id;
    }
    
    public void add(DatapathId mgSwId, Set<OFPort> edgePorts, Path path) {
    	if (mgSwId == null || edgePorts == null || path == null) {
    		return;
    	}
    	
    	swIdToPathMap.put(mgSwId, path);
    	swIdToApPortMap.put(mgSwId, edgePorts);
    }
    
    public void remove(DatapathId mgSwId) {
    	if (mgSwId == null) {
    		return;
    	}
    	
    	swIdToPathMap.remove(mgSwId);
    	swIdToApPortMap.remove(mgSwId);
    }
    
    public void remove(Path path) {
    	if (path == null) {
    		return;
    	}
    	
    	DatapathId mgSwId = path.getId().getDst();
    	swIdToPathMap.remove(mgSwId);
    	swIdToApPortMap.remove(mgSwId);
    }
    
    public DatapathId getRoot() {
    	return id.getSrc();
    }
    
    public MulticastGroupId getMgId() {
    	return id.getMulticastGroupId();
    }
    
    public Collection<Path> getAllPaths() {
        return Collections.unmodifiableCollection(swIdToPathMap.values());
    }
    
    public Set<DatapathId> getAllMgSwIds() {
    	 return Collections.unmodifiableSet(swIdToPathMap.keySet());
    }
    
    public Path getPath(DatapathId mgSwId) {
    	if (mgSwId == null) {
    		return null;
    	}
    	
    	return swIdToPathMap.get(mgSwId);
    }
    
    public DatapathId getMgSwId(Path path) {
    	if (path == null) {
    		return null;
    	}
    	
    	DatapathId mgSwId = path.getId().getDst();
    	return mgSwId;
    }
    
    public Set<OFPort> getApPorts(DatapathId mgSwId) {
    	if (mgSwId == null) {
    		return ImmutableSet.of();
    	}
    	
    	Set<OFPort> result = swIdToApPortMap.get(mgSwId);
    	return (result == null) ? ImmutableSet.of() : Collections.unmodifiableSet(result);
    }
    
    public boolean hasPath(Path path) {
    	if (path == null) {
    		return false;
    	}
    	
    	DatapathId mgSwId = path.getId().getDst();
    	return swIdToPathMap.containsKey(mgSwId);
    }
    
    public boolean hasMgSwId(DatapathId mgSwId) {
    	if (mgSwId == null) {
    		return false;
    	}
    	
    	return swIdToPathMap.containsKey(mgSwId);
    }
    
    public boolean isEmpty() {
    	return swIdToPathMap.isEmpty();
    }
    
    public void clear() {
    	swIdToPathMap.clear();
    }
    
    public int getMulticastPathIndex() {
        return pathIndex;
    }
    
    public void setMulticastPathIndex(int pathIndex) {
        this.pathIndex = pathIndex;
    }
    
    public int getHopCount() { 
        int hopCount = 0;
        for (Path path: swIdToPathMap.values()) {
        	hopCount += path.getHopCount();
        }
        return hopCount;
    }
    
    public U64 getLatency() { 
    	U64 latency = U64.ZERO;
        for (Path path:swIdToPathMap.values()) {
        	if (path.getLatency() != null) { // By default Path doesn't initialize U64 latency
        		latency.add(path.getLatency());
        	}
        }
        return latency;
    }
    
    public int getCost() {
        int cost = 0;
        for (Path path: swIdToPathMap.values()) {
        	cost += path.getCost();
        }
        return cost;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + pathIndex;
        result = prime * result + swIdToPathMap.hashCode();
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
        MulticastPath other = (MulticastPath) obj;
        if (id == null || other.id == null ||
			!id.equals(other.id)) {
        	return false;
        }
        if (pathIndex != other.pathIndex) {
        	return false;
        }
        if (!swIdToPathMap.equals(other.swIdToPathMap)) {
        	return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        return "Route [id=" + id + ", paths=" + swIdToPathMap.values() + "]";
    }
    
    @Override
    public int compareTo(MulticastPath o) {
        return ((Integer)swIdToPathMap.size()).compareTo(o.swIdToPathMap.size());
    }
}
