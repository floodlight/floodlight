package net.floodlightcontroller.linkdiscovery.internal;

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
}
