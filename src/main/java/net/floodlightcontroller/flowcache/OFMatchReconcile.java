package net.floodlightcontroller.flowcache;

import net.floodlightcontroller.core.FloodlightContext;

import org.openflow.protocol.OFMatchWithSwDpid;

/**
 * OFMatchReconcile class to indicate result of a flow-reconciliation.
 */
public class OFMatchReconcile  {
	
	/**
	 * The enum ReconcileAction. Specifies the result of reconciliation of a 
	 * flow.
	 */
	public enum ReconcileAction {

	    /** Delete the flow-mod from the switch */
	    DROP,
	    /** Leave the flow-mod as-is. */
	    NO_CHANGE,
	    /** Program this new flow mod. */
	    NEW_ENTRY,
	    /** 
	     * Reprogram the flow mod as the path of the flow might have changed,
	     * for example when a host is moved or when a link goes down. */
	    UPDATE_PATH,
	    /* Flow is now in a different BVS */
	    BVS_CHANGED,
	    /* Delete the flow-mod - used to delete, for example, drop flow-mods
	     * when the source and destination are in the same BVS after a 
	     * configuration change */
	    DELETE
	}
	
    /** The open flow match after reconciliation. */
    public OFMatchWithSwDpid ofmWithSwDpid;
    /** flow mod. priority */
    public short priority;
    /** Action of this flow-mod PERMIT or DENY */
    public byte action;
    /** flow mod. cookie */
    public long cookie;
    /** The application instance name. */
    public String appInstName;
    /**
     * The new application instance name. This is null unless the flow
     * has moved to a different BVS due to BVS config change or device
     * move to a different switch port etc.*/
    public String newAppInstName;
    /** The reconcile action. */
    public ReconcileAction rcAction;

    // The context for the reconcile action
    public FloodlightContext cntx;
    
    /**
     * Instantiates a new oF match reconcile object.
     */
    public OFMatchReconcile() {
        ofmWithSwDpid      = new OFMatchWithSwDpid();
        rcAction = ReconcileAction.NO_CHANGE;
        cntx = new FloodlightContext();
    }
    
    @Override
    public String toString() {
        return "OFMatchReconcile [" + ofmWithSwDpid + " priority=" + priority + " action=" + action + 
                " cookie=" + cookie + " appInstName=" + appInstName + " newAppInstName=" + newAppInstName + 
                " ReconcileAction=" + rcAction + "]";
    }
}
