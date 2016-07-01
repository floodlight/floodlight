package net.floodlightcontroller.routing;

import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.U64;

public interface IRoutingDecisionChangedListener {
	
	public void routingDecisionChanged(Iterable<Masked<U64>> event);
	
}
