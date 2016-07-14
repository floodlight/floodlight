package net.floodlightcontroller.routing;

import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.U64;

public interface IRoutingDecisionChangedListener {
	
    /** Notifies the listener that routing logic has changed, requiring certain past routing decisions
     * to become invalid.  The caller provides a sequence of masked values that match against
     * past values of IRoutingDecision.getDescriptor().  Services that have operated on past
     * routing decisions are then able to remove the results of past decisions, normally by deleting
     * flows.
     * 
     * @param changedDecisions Masked descriptors identifying routing decisions that are now obsolete or invalid  
     */
	public void routingDecisionChanged(Iterable<Masked<U64>> changedDecisions);
	
}
