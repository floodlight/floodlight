package net.floodlightcontroller.multicasting.internal;

import org.projectfloodlight.openflow.types.IPAddress;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 *
 * Destination Multicast Group Address can further be
 * classified based on L2, L3 and L4 respectively for use
 * in matching packets in Forwarding and other modules.
 * 
 */
public class ParticipantGroupAddress {
    // L2
    private final MacAddress macAddress;
    private final VlanVid vlanVid;
    
    // L3
    private final IPAddress<?> ipAddress;    // IPv4/IPv6
    
    // L4
    private final TransportPort port;    // UDP
    
    public ParticipantGroupAddress(MacAddress macAddress, VlanVid vlanVid,
            IPAddress<?> ipAddress, TransportPort port) {
        this.macAddress = macAddress;
        this.vlanVid = vlanVid;
        this.ipAddress = ipAddress;
        this.port = port;
    }
    
    public MacAddress getMacAddress() {
        return macAddress;
    }
    
    public VlanVid getVlanVid() {
        return vlanVid;
    }
    
    public IPAddress<?> getIPAddress() {
        return ipAddress;
    }
    
    public TransportPort getPort() {
        return port;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        ParticipantGroupAddress that = (ParticipantGroupAddress) o;
        
        if ((macAddress == null && that.macAddress != null) || 
            (macAddress != null && !macAddress.equals(that.macAddress))) {
            return false;
        }
        
        if ((vlanVid == null && that.vlanVid != null) || 
            (vlanVid != null && !vlanVid.equals(that.vlanVid))) {
            return false;
        }
        
        if ((ipAddress == null && that.ipAddress != null) || 
            (ipAddress != null && !ipAddress.equals(that.ipAddress))) {
            return false;
        }
        
        if ((port == null && that.port != null) || 
            (port != null && !port.equals(that.port))) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = 0;
        
        if (macAddress != null) {
            result = 31 * result + macAddress.hashCode();
        }
        
        if (vlanVid != null) {
            result = 31 * result + vlanVid.hashCode();
        }
        
        if (ipAddress != null) {
            result = 31 * result + ipAddress.hashCode();
        }
        
        if (port != null) {
            result = 31 * result + port.hashCode();
        }
        
        return result;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (macAddress != null) {
            sb.append("MacAddress: " + macAddress + ", ");
        }
        
        if (vlanVid != null) {
            sb.append("vlanVid: " + vlanVid + ", ");
        }
        
        if (ipAddress != null) {
            sb.append("IPAddress: " + ipAddress + ", ");
        }
        
        if (port != null) {
            sb.append("Port: " + port + ", ");
        }
        
        return sb.toString();
    }
}
