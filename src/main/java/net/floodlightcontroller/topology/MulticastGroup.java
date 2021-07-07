package net.floodlightcontroller.topology;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.python.google.common.collect.ImmutableSet;

import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.util.RWSync;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * Group of Multicasting Device interfaces and their attachmentPoints
 * in a given archipelago
 * 
 */
public class MulticastGroup {
	
	// Multicast Group Id
	private final MulticastGroupId mgId;
	
	// Map of device interfaces and participant attachment points in archipelago
	private final Map<MacVlanPair, Set<NodePortTuple>> intfToApMap;
	
	// Map of attachment point and participant device interfaces in archipelago
	private final Map<NodePortTuple, Set<MacVlanPair>> apToIntfMap;
	
	// Map of switches and attachmentPoint ports connected to participant device interfaces
	private final Map<DatapathId, Set<OFPort>> swToPortsMap;
	
	// Reader-Writer Sync
	private final RWSync rwSync;
	
	public MulticastGroup(MulticastGroupId mgId) {
		this.mgId = mgId;
		
		intfToApMap = new HashMap<MacVlanPair, Set<NodePortTuple>>();
		apToIntfMap = new HashMap<NodePortTuple, Set<MacVlanPair>>();
		swToPortsMap = new HashMap<DatapathId, Set<OFPort>>();
		
		rwSync = new RWSync();
	}
	
	public MulticastGroupId getId() {
		return mgId;
	}
	
	public void add(MacVlanPair intf, NodePortTuple ap) {
		if (intf == null || ap == null) {
			return;
		}
		
		rwSync.writeLock();
		
		Set<NodePortTuple> apSet = intfToApMap.get(intf);
		if (apSet == null) {
			apSet = new HashSet<NodePortTuple>();
			intfToApMap.put(intf, apSet);
		}
		apSet.add(ap);
		
		Set<MacVlanPair> devSet = apToIntfMap.get(ap);
		if (devSet == null) {
			devSet = new HashSet<MacVlanPair>();
			apToIntfMap.put(ap, devSet);
		}
		devSet.add(intf);
		
		DatapathId swId = ap.getNodeId();
		OFPort port = ap.getPortId();
		Set<OFPort> ports = swToPortsMap.get(swId);
		if (ports == null) {
			ports = new HashSet<OFPort>();
			swToPortsMap.put(swId, ports);
		}
		ports.add(port);
		
		rwSync.writeUnlock();
	}
	
	public void remove(MacVlanPair intf) {
		if (intf == null) {
			return;
		}
		
		rwSync.writeLock();
		
		Set<NodePortTuple> apSet = intfToApMap.get(intf);
		if (apSet != null) {
			for (NodePortTuple ap: apSet) {
				Set<MacVlanPair> devSet = apToIntfMap.get(ap);
				devSet.remove(intf);
				if (devSet.isEmpty()) {
					apToIntfMap.remove(ap);
					DatapathId swId = ap.getNodeId();
					OFPort port = ap.getPortId();
					Set<OFPort> ports = swToPortsMap.get(swId);
					ports.remove(port);
					if (ports.isEmpty()) {
						swToPortsMap.remove(swId);
					}
				}
			}
			intfToApMap.remove(intf);
		}
		
		rwSync.writeUnlock();
	}
	
	public void remove(MacVlanPair intf, NodePortTuple ap) {
		if (intf == null || ap == null) {
			return;
		}
		
		rwSync.writeLock();
		
		Set<NodePortTuple> apSet = intfToApMap.get(intf);
		if (apSet != null) {
			if (apSet.contains(ap)) {
				Set<MacVlanPair> devSet = apToIntfMap.get(ap);
				devSet.remove(intf);
				if (devSet.isEmpty()) {
					apToIntfMap.remove(ap);
					DatapathId swId = ap.getNodeId();
					OFPort port = ap.getPortId();
					Set<OFPort> ports = swToPortsMap.get(swId);
					ports.remove(port);
					if (ports.isEmpty()) {
						swToPortsMap.remove(swId);
					}
				}
				apSet.remove(ap);
			}
			if (apSet.isEmpty()) {
				intfToApMap.remove(intf);
			}
		}
		
		rwSync.writeUnlock();
	}
	
	public boolean hasIntf(MacVlanPair intf) {
		if (intf == null) {
			return false;
		}
		
		boolean result;
		
		rwSync.readLock();
		
		result = intfToApMap.keySet().contains(intf);
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public boolean hasAttachmentPoint(NodePortTuple ap) {
		if (ap == null) {
			return false;
		}
		
		boolean result;
		
		rwSync.readLock();
		
		result = apToIntfMap.keySet().contains(ap);
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public Set<MacVlanPair> getAllIntfs() {
		Set<MacVlanPair> result;
		
		rwSync.readLock();
		
		result =  new HashSet<MacVlanPair>(intfToApMap.keySet());
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public Set<NodePortTuple> getAllAttachmentPoints() {
		Set<NodePortTuple> result;
		
		rwSync.readLock();
		
		result = Collections.unmodifiableSet(apToIntfMap.keySet());
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public Set<MacVlanPair> getIntfs(NodePortTuple ap) {
		if (ap == null) {
			return ImmutableSet.of();
		}
		
		Set<MacVlanPair> result;
		
		rwSync.readLock();
		
		result = apToIntfMap.get(ap);
		result = (result == null) ? ImmutableSet.of() : new HashSet<MacVlanPair>(result);
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public Set<NodePortTuple> getAttachmentPoints(MacVlanPair intf) {
		if (intf == null) {
			return ImmutableSet.of();
		}
		
		Set<NodePortTuple> result;
		
		rwSync.readLock();
		
		result = intfToApMap.get(intf);
		result = (result == null) ? ImmutableSet.of() : new HashSet<NodePortTuple>(result);
		
		rwSync.readUnlock();
		
		return (result == null) ? ImmutableSet.of() : Collections.unmodifiableSet(result);
	}
	
	public boolean hasSwitch(DatapathId swId) {
		if (swId == null) {
			return false;
		}
		
		boolean result;
		
		rwSync.readLock();
		
		result = swToPortsMap.containsKey(swId);
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public Set<DatapathId> getSwitches() {
		Set<DatapathId> result;
		
		rwSync.readLock();
		
		result =  new HashSet<DatapathId>(swToPortsMap.keySet());
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public Set<OFPort> getApPorts(DatapathId swId) {
		if (swId == null) {
			return ImmutableSet.of();
		}
		
		Set<OFPort> result;
		
		rwSync.readLock();
		
		result = swToPortsMap.get(swId);
		result = (result == null) ? ImmutableSet.of() : new HashSet<OFPort>(result);
		
		rwSync.readUnlock();
		
		return result;
	}
	
	public boolean isEmpty() {
		boolean result;
		
		rwSync.readLock();
		
		result = intfToApMap.isEmpty();
		
		rwSync.readUnlock();
		
		return result;
	}
	
    @Override
    public boolean equals(Object o) {
        if (this == o) {
        	return true;
        }
        
        if (o == null || getClass() != o.getClass()) {
        	return false;
        }

        MulticastGroup that = (MulticastGroup) o;
       
        if (mgId == null || that.mgId == null || 
        		!mgId.equals(that.mgId)) {
        	return false;
        }
        
        return true;
    }

    @Override
    public int hashCode() {
        int result = mgId.hashCode();
        return result;
    }
}
