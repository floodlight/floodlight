package net.floodlightcontroller.topology;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.multicasting.internal.ParticipantGroupAddress;

public class MulticastGroupId {
	private final ParticipantGroupAddress groupAddress;
	private final DatapathId archId;
	
	public MulticastGroupId(ParticipantGroupAddress groupAddress, DatapathId archId) {
		this.groupAddress = groupAddress;
		this.archId = archId;
	}
	
	public ParticipantGroupAddress getGroupAddress() {
		return groupAddress;
	}
	
	public DatapathId getArchipelagoId() {
		return archId;
	}
	
    @Override
    public boolean equals(Object o) {
        if (this == o) {
        	return true;
        }
        
        if (o == null || getClass() != o.getClass()) {
        	return false;
        }

        MulticastGroupId that = (MulticastGroupId) o;
       
        if (groupAddress == null || that.groupAddress == null || 
        		!groupAddress.equals(that.groupAddress)) {
        	return false;
        }
        
        if (archId == null || that.archId == null || 
        		!archId.equals(that.archId)) {
        	return false;
        }
        
        return true;
    }

    @Override
    public int hashCode() {
        int result = groupAddress.hashCode();
        result = 31 * result + archId.hashCode();
        return result;
    }
}
