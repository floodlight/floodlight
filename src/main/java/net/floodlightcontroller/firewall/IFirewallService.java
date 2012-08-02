package net.floodlightcontroller.firewall;

import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFirewallService extends IFloodlightService {

	/**
     * Enables the Firewall module
     */
	public void enableFirewall();
	
	/**
     * Disables the Firewall module
     */
	public void disableFirewall();
	
	/**
     * Returns all of the firewall rules
     * @return List of all rules
     */
	public List<FirewallRule> getRules();
}
