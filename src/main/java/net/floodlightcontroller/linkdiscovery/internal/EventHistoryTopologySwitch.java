package net.floodlightcontroller.linkdiscovery.internal;

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
    public byte []  ipv4Addr;
    public short    l4Port;
    public String   reason;
}
