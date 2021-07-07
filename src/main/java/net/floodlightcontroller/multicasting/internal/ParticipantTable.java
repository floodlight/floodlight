package net.floodlightcontroller.multicasting.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.IPAddress;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.python.google.common.collect.ImmutableSet;

import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.util.RWSync;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * Participant table that maps b/w participant group address, device interfaces
 * and attachment points.
 * 
 * Also maps group address to options.
 * 
 */
public class ParticipantTable {
    private class GroupIntf {
        private final ParticipantGroupAddress groupAddress;
        private final MacVlanPair intf;
        protected GroupIntf(ParticipantGroupAddress groupAddress, MacVlanPair intf) {
            this.groupAddress = groupAddress;
            this.intf = intf;
        }
        protected ParticipantGroupAddress getParticipantGroupAddress() {
            return groupAddress;
        }
        protected MacVlanPair getIntf() {
            return intf;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            GroupIntf that = (GroupIntf) o;
            if (groupAddress == null || that.groupAddress == null || 
                    !groupAddress.equals(that.groupAddress)) {
                return false;
            }
            if (intf == null || that.intf == null || 
                    !intf.equals(that.intf)) {
                return false;
            }
            return true;
        }
        @Override
        public int hashCode() {
            int result = groupAddress.hashCode();
            result = 31 * result + intf.hashCode();
            return result;
        }
    }
    
    private final Map<ParticipantGroupAddress, Set<MacVlanPair>> groupAddressToIntfMap;
    private final Map<MacVlanPair, Set<ParticipantGroupAddress>> intfToGroupAddressMap;

    private final Map<GroupIntf, Set<NodePortTuple>> groupIntfToApMap;
    
    // Options
    private final Map<ParticipantGroupAddress, 
        ParticipantGroupOptions> groupAddressToGroupOptionsMap;

    // Reader-Writer Synchronization Class & Object
    private final RWSync rwSync;

    public ParticipantTable() {
        groupAddressToIntfMap = new HashMap<ParticipantGroupAddress, Set<MacVlanPair>>();
        intfToGroupAddressMap = new HashMap<MacVlanPair, Set<ParticipantGroupAddress>>();
        
        groupIntfToApMap = new HashMap<GroupIntf, Set<NodePortTuple>>();
        
        groupAddressToGroupOptionsMap = new HashMap<ParticipantGroupAddress, 
                ParticipantGroupOptions>();
        
        rwSync = new RWSync();
    }
    
    public void add(ParticipantGroupAddress groupAddress, MacVlanPair intf, 
            NodePortTuple ap) {
        if (groupAddress == null || intf == null || ap == null) {
            return;
        }
        
        Set<MacVlanPair> intfSet;
        Set<ParticipantGroupAddress> groupAddressSet;
        Set<NodePortTuple> apSet;
        GroupIntf groupIntf = new GroupIntf(groupAddress, intf);

        rwSync.writeLock();
        
        intfSet = groupAddressToIntfMap.get(groupAddress);
        if (intfSet == null) {
            intfSet = new HashSet<MacVlanPair>();
            groupAddressToIntfMap.put(groupAddress, intfSet);
        }
        intfSet.add(intf);
        
        groupAddressSet = intfToGroupAddressMap.get(intf);
        if (groupAddressSet == null) {
            groupAddressSet = new HashSet<ParticipantGroupAddress>();
            intfToGroupAddressMap.put(intf, groupAddressSet);
        }
        groupAddressSet.add(groupAddress);
        
        apSet = groupIntfToApMap.get(groupIntf);
        if (apSet == null) {
            apSet = new HashSet<NodePortTuple>();
            groupIntfToApMap.put(groupIntf, apSet);
        }
        apSet.add(ap);
        
        rwSync.writeUnlock();
    }
    
    public void remove(ParticipantGroupAddress groupAddress, MacVlanPair intf, 
            NodePortTuple ap) {
        if (groupAddress == null || intf == null || ap == null) {
            return;
        }
        
        Set<MacVlanPair> intfSet;
        Set<ParticipantGroupAddress> groupAddressSet;
        Set<NodePortTuple> apSet;
        GroupIntf groupIntf = new GroupIntf(groupAddress, intf);

        rwSync.writeLock();
        
        apSet = groupIntfToApMap.get(groupIntf);
        if (apSet != null) {
            apSet.remove(ap);
            if (apSet.isEmpty()) {
                groupIntfToApMap.remove(groupIntf);
                
                groupAddress = groupIntf.getParticipantGroupAddress();
                intf = groupIntf.getIntf();
                
                intfSet = groupAddressToIntfMap.get(groupAddress);
                if (intfSet != null) {
                    intfSet.remove(intf);
                    if (intfSet.isEmpty()) {
                        groupAddressToIntfMap.remove(groupAddress);
                        
                        // Also remove options
                        // groupAddressToGroupOptionsMap.remove(groupAddress);
                    }
                }
                
                groupAddressSet = intfToGroupAddressMap.get(intf);
                if (groupAddressSet != null) {
                    groupAddressSet.remove(groupAddress);
                    if (groupAddressSet.isEmpty()) {
                        intfToGroupAddressMap.remove(intf);
                    }
                }
            }
        }
        
        rwSync.writeUnlock();
    }
    
    public Boolean contains(ParticipantGroupAddress groupAddress, MacVlanPair intf) {
        if (groupAddress == null || intf == null) {
            return false;
        }
        
        Boolean result;
        GroupIntf groupIntf = new GroupIntf(groupAddress, intf);
        
        rwSync.readLock();
        
        result = groupIntfToApMap.containsKey(groupIntf);
        
        rwSync.readUnlock();

        return result;
    }
    
    public Set<NodePortTuple> getAttachmentPoints(
            ParticipantGroupAddress groupAddress, MacVlanPair intf) {
        Set<NodePortTuple> result;
        GroupIntf groupIntf = new GroupIntf(groupAddress, intf);
        
        rwSync.readLock();
        
        result = groupIntfToApMap.get(groupIntf);
        result = (result == null) ? ImmutableSet.of() : 
            new HashSet<NodePortTuple>(result);
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public Set<MacVlanPair> getIntfs(ParticipantGroupAddress groupAddress) {
        if (groupAddress == null) {
            return ImmutableSet.of();
        }
        
        Set<MacVlanPair> result;
        
        rwSync.readLock();
        
        result = groupAddressToIntfMap.get(groupAddress);
        result = (result == null) ? ImmutableSet.of() : 
            new HashSet<MacVlanPair>(result);
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public Set<ParticipantGroupAddress> getGroupAddresses(MacVlanPair intf) {
        if (intf == null) {
            return ImmutableSet.of();
        }
        
        Set<ParticipantGroupAddress> result;
        
        rwSync.readLock();
        
        result = intfToGroupAddressMap.get(intf);
        result = (result == null) ? ImmutableSet.of() : 
            new HashSet<ParticipantGroupAddress>(result);
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public Set<MacVlanPair> getAllIntfs() {
        Set<MacVlanPair> result;
        
        rwSync.readLock();
        
        result = new HashSet<MacVlanPair>(intfToGroupAddressMap.keySet());
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public Set<ParticipantGroupAddress> getAllGroupAddresses() {
        Set<ParticipantGroupAddress> result;
        
        rwSync.readLock();
        
        result = new HashSet<ParticipantGroupAddress>(
                groupAddressToIntfMap.keySet());
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public Boolean hasIntf(MacVlanPair intf) {
        if (intf == null) {
            return false;
        }
        
        Boolean result;
        
        rwSync.readLock();
        
        result = intfToGroupAddressMap.containsKey(intf);
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public Boolean hasGroupAddress(ParticipantGroupAddress groupAddress) {
        if (groupAddress == null) {
            return false;
        }
        
        Boolean result;
        
        rwSync.readLock();
        
        result = groupAddressToIntfMap.containsKey(groupAddress);
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public void deleteGroupAddress(ParticipantGroupAddress groupAddress) {
        if (groupAddress == null) {
            return;
        }
        
        Set<MacVlanPair> intfSet = getIntfs(groupAddress);
        for (MacVlanPair intf: intfSet) {
            Set<NodePortTuple> apSet = getAttachmentPoints(groupAddress, intf);
            for (NodePortTuple ap: apSet) {
                remove(groupAddress, intf, ap);
            }
        }
    }
    
    public void deleteIntf(MacVlanPair intf) {
        if (intf == null) {
            return;
        }
        
        Set<ParticipantGroupAddress> groupAddressSet = getGroupAddresses(intf);
        for (ParticipantGroupAddress groupAddress: groupAddressSet) {
            Set<NodePortTuple> apSet = getAttachmentPoints(groupAddress, intf);
            for (NodePortTuple ap: apSet) {
                remove(groupAddress, intf, ap);
            }
        }
    }
    
    public void clearTable(boolean clearGroupOptions) {
        rwSync.writeLock();
        
        groupAddressToIntfMap.clear();
        intfToGroupAddressMap.clear();
        
        groupIntfToApMap.clear();
        
		if (clearGroupOptions) {
			groupAddressToGroupOptionsMap.clear();
		}
        
        rwSync.writeUnlock();
    }
    
    public ParticipantGroupOptions getGroupOptions(
            ParticipantGroupAddress groupAddress) {
        if (groupAddress == null) {
            return null;
        }
        
        ParticipantGroupOptions result;
        
        rwSync.readLock();
        
        result = groupAddressToGroupOptionsMap.get(groupAddress);
        
        rwSync.readUnlock();
        
        return result;
    }
    
    public void setGroupOptions(ParticipantGroupAddress groupAddress, 
            ParticipantGroupOptions groupOptions) {
        if (groupAddress == null) {
            return;
        }
        
        rwSync.writeLock();
        
        if (groupOptions == null) {
            groupAddressToGroupOptionsMap.remove(groupAddress);
        }
        else {
            groupAddressToGroupOptionsMap.put(groupAddress, groupOptions);
        }
        
        rwSync.writeUnlock();
    }
    
    public ParticipantGroupAddress queryGroupAddress(MacAddress macAddress, 
            VlanVid vlanVid, IPAddress<?> ipAddress, TransportPort port) {
        if (macAddress == null || vlanVid == null || ipAddress == null || port == null) {
            return null;
        }
        
        ParticipantGroupAddress result;
        
        rwSync.readLock();
        
        // CASE 1
        result = new ParticipantGroupAddress(macAddress, vlanVid, ipAddress, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 2
        result = new ParticipantGroupAddress(macAddress, vlanVid, ipAddress, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 3
        result = new ParticipantGroupAddress(macAddress, vlanVid, null, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 4
        result = new ParticipantGroupAddress(macAddress, null, ipAddress, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 5
        result = new ParticipantGroupAddress(null, vlanVid, ipAddress, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 6
        result = new ParticipantGroupAddress(macAddress, vlanVid, null, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 7
        result = new ParticipantGroupAddress(macAddress, null, ipAddress, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 8
        result = new ParticipantGroupAddress(macAddress, null, null, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 9
        result = new ParticipantGroupAddress(null, vlanVid, ipAddress, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 10
        result = new ParticipantGroupAddress(null, vlanVid, null, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 11
        result = new ParticipantGroupAddress(null, null, ipAddress, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 12
        result = new ParticipantGroupAddress(macAddress, null, null, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 13
        result = new ParticipantGroupAddress(null, vlanVid, null, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 14
        result = new ParticipantGroupAddress(null, null, ipAddress, null);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        // CASE 15
        result = new ParticipantGroupAddress(null, null, null, port);
        if (groupAddressToIntfMap.containsKey(result)) {
            return result;
        }
        
        rwSync.readUnlock();
        
        return null;
    }
}