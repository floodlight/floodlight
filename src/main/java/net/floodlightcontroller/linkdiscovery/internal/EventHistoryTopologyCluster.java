package net.floodlightcontroller.linkdiscovery.internal;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/***
 * Topology Cluster merge/split event history related classes and members
 * @author subrata
 *
 */
public class EventHistoryTopologyCluster {
    // The following fields are not stored as String to save memory
    // They should be converted to appropriate human-readable strings by 
    // the front end (e.g. in cli in Python)
    public long     dpid;
    public long     clusterIdOld; // Switch with dpid moved from cluster x to y
    public long     clusterIdNew;
    public String   reason;
    
    @JsonProperty("Switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getDpid() {
        return dpid;
    }
    @JsonProperty("OldClusterId")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getClusterIdOld() {
        return clusterIdOld;
    }
    @JsonProperty("NewClusterId")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getClusterIdNew() {
        return clusterIdNew;
    }
    @JsonProperty("Reason")
    public String getReason() {
        return reason;
    }
    
    
}
