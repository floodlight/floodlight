package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.multicasting.internal.ParticipantGroupAddress;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * Listener Interface for Multicasting
 * 
 */ 
public interface IMulticastListener {
    void ParticipantAdded(ParticipantGroupAddress pgAddress, MacVlanPair intf, NodePortTuple ap);
    
    void ParticipantRemoved(ParticipantGroupAddress pgAddress, MacVlanPair intf, NodePortTuple ap);
    
    void ParticipantsReset();
}
