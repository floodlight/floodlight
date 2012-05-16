package net.floodlightcontroller.linkdiscovery.internal;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.core.web.serializers.IPv4Serializer;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/***
 * Topology Switch event history related classes and members
 * @author subrata
 *
 */
public class EventHistoryTopologySwitch {
    // The following fields are not stored as String to save memory
    // They should be converted to appropriate human-readable strings by 
    // the front end (e.g. in cli in Python)
    public long     dpid;
    public int  ipv4Addr;
    public int    l4Port;
    public String   reason;
    
    @JsonProperty("Switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getDpid() {
        return dpid;
    }
    @JsonProperty("IpAddr")
    @JsonSerialize(using=IPv4Serializer.class)
    public int getIpv4Addr() {
        return ipv4Addr;
    }
    @JsonProperty("Port")
    public int getL4Port() {
        return l4Port;
    }
    @JsonProperty("Reason")
    public String getReason() {
        return reason;
    }
    
    
}
