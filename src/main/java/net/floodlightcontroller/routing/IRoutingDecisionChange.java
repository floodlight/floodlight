package net.floodlightcontroller.routing;

import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.U64;

public interface IRoutingDecisionChange {
	
	public Iterable<Masked<U64>> getDescriptor();

}
