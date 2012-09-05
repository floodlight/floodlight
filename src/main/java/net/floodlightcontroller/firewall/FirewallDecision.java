package net.floodlightcontroller.firewall;

import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.routing.IRoutingDecision;

public class FirewallDecision implements IRoutingDecision {

	private RoutingAction routingAction;
	private Integer wildcards;
	
	public FirewallDecision(RoutingAction action, Integer wildcards) {
		this.routingAction = action;
		this.wildcards = wildcards;
	}
	
	public FirewallDecision(RoutingAction action) {
		this.routingAction = action;
		this.wildcards = null;
	}
	
	public FirewallDecision() {
		this.routingAction = RoutingAction.NONE;
		this.wildcards = null;
	}
	
	@Override
	public void addToContext(FloodlightContext cntx) {
		IRoutingDecision.rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION, this);
	}

	@Override
	public RoutingAction getRoutingAction() {
		// TODO Auto-generated method stub
		return this.routingAction;
	}

	@Override
	public void setRoutingAction(RoutingAction action) {
		// TODO Auto-generated method stub
		this.routingAction = action;
	}

	@Override
	public SwitchPort getSourcePort() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDevice getSourceDevice() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<IDevice> getDestinationDevices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addDestinationDevice(IDevice d) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<SwitchPort> getMulticastInterfaces() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMulticastInterfaces(List<SwitchPort> lspt) {
		// TODO Auto-generated method stub

	}

	@Override
	public Integer getWildcards() {
		return this.wildcards;
	}

	@Override
	public void setWildcards(Integer wildcards) {
		this.wildcards = wildcards;
	}

}
