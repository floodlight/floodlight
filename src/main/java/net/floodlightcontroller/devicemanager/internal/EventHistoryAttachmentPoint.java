package net.floodlightcontroller.devicemanager.internal;

/***
 * Attachment-Point Event history related classes and members
 * @author subrata
 *
 */
public class EventHistoryAttachmentPoint {
    public String   reason;
    // The following fields are not stored as String to save memory
    // They shoudl be converted to appropriate human-readable strings by 
    // the front end (e.g. in cli in python)
    public long     mac;
    public short    vlan;
    public short    port;
    public long     dpid;

    public long getMac() {
        return mac;
    }
    public short getVlan() {
        return vlan;
    }
    public short getPort() {
        return port;
    }
    public long getDpid() {
        return dpid;
    }
    public String getReason() {
        return reason;
    }
}