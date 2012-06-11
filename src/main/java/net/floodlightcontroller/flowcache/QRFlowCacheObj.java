package net.floodlightcontroller.flowcache;


import org.openflow.protocol.OFMatchWithSwDpid;

/**
 * Used in BigFlowCacheQueryResp as query result.
 * Used to return one flow when queried by one of the big flow cache APIs.
 * One of these QRFlowCacheObj is returned for each combination of
 * priority and action.
 *
 * @author subrata
 */
public class QRFlowCacheObj {

    /** The open flow match object. */
    public OFMatchWithSwDpid ofmWithSwDpid;
    /** The flow-mod priority. */
    public short   priority;
    /** flow-mod cookie */
    public long    cookie;
    /** The action - PERMIT or DENY. */
    public byte    action;
    /** The reserved byte to align with 8 bytes. */
    public byte    reserved;

    /**
     * Instantiates a new flow cache query object.
     *
     * @param priority the priority
     * @param action the action
     */
    public QRFlowCacheObj(short priority, byte action, long cookie) {
        ofmWithSwDpid = new OFMatchWithSwDpid();
        this.action   = action;
        this.priority = priority;
        this.cookie   = cookie;
    }

    /**
     * Populate a given OFMatchReconcile object from the values of this
     * class.
     *
     * @param ofmRc the given OFMatchReconcile object
     * @param appInstName the application instance name
     * @param rcAction the reconcile action
     */
    public   void toOFMatchReconcile(OFMatchReconcile ofmRc,
                            String appInstName, OFMatchReconcile.ReconcileAction rcAction) {
        ofmRc.ofmWithSwDpid   = ofmWithSwDpid; // not copying
        ofmRc.appInstName     = appInstName;
        ofmRc.rcAction        = rcAction;
        ofmRc.priority        = priority;
        ofmRc.cookie          = cookie;
        ofmRc.action          = action;
    }
    
    @Override
    public String toString() {
        String str = "ofmWithSwDpid: " + this.ofmWithSwDpid.toString() + " ";
        str += "priority: " + this.priority + " ";
        str += "cookie: " + this.cookie + " ";
        str += "action: " + this.action + " ";
        str += "reserved: " + this.reserved + " ";
        return str;
    }
}
