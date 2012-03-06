package net.floodlightcontroller.linkdiscovery.internal;

/***
 * Topology link up/down event history related classes and members
 * @author subrata
 *
 */
public class EventHistoryTopologyLink {
    // The following fields are not stored as String to save memory
    // They should be converted to appropriate human-readable strings by 
    // the front end (e.g. in cli in Python)
    public long     srcSwDpid;
    public long     dstSwDpid;
    public int      srcPortState;
    public int      dstPortState;
    public short    srcSwport;
    public short    dstSwport;
    public String   reason;
}