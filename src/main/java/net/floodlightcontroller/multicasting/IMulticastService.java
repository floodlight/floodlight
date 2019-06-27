package net.floodlightcontroller.multicasting;

import java.util.Set;

import org.projectfloodlight.openflow.types.IPAddress;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.multicasting.internal.ParticipantGroupAddress;
import net.floodlightcontroller.multicasting.internal.ParticipantGroupOptions;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * Service Interface for Multicasting
 * 
 */ 
public interface IMulticastService extends IFloodlightService {
    public void addParticipant(ParticipantGroupAddress pgAddress, MacVlanPair intf, 
            NodePortTuple ap);
    
    public void removeParticipant(ParticipantGroupAddress pgAddress, MacVlanPair intf, 
            NodePortTuple ap);
    
    public boolean hasParticipant(ParticipantGroupAddress pgAddress, MacVlanPair intf);
    
    public Set<NodePortTuple> getParticipantAPs(ParticipantGroupAddress pgAddress, 
            MacVlanPair intf);
    
    public Set<MacVlanPair> getParticipantIntfs(ParticipantGroupAddress pgAddress);
    
    public Set<ParticipantGroupAddress> getParticipantGroupAddresses(MacVlanPair intf);
    
    public Set<MacVlanPair> getAllParticipantIntfs();
    
    public Set<ParticipantGroupAddress> getAllParticipantGroupAddresses();
    
    public boolean hasParticipantIntf(MacVlanPair intf);
    
    public boolean hasParticipantGroupAddress(ParticipantGroupAddress pgAddress);
    
    public void deleteParticipantGroupAddress(ParticipantGroupAddress pgAddress);
    
    public void deleteParticipantIntf(MacVlanPair intf);
    
    public void clearAllParticipants(boolean clearPgOpts);
    
    public void setParticipantGroupOptions(ParticipantGroupAddress pgAddress, 
            ParticipantGroupOptions pgOpts);
    
    public ParticipantGroupOptions getParticipantGroupOptions(ParticipantGroupAddress pgAddress);
    
    public ParticipantGroupAddress queryParticipantGroupAddress(MacAddress macAddress, 
            VlanVid vlanVid, IPAddress<?> ipAddress, TransportPort port);
    
    public void addListener(IMulticastListener listener);

    public void removeListener(IMulticastListener listener);
}
