package net.floodlightcontroller.firewall;

import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFirewallService extends IFloodlightService {

    /**
     * Enables/disables the firewall.
     * @param enable Whether to enable or disable the firewall.
     */
    public void enableFirewall(boolean enable);

    /**
     * Returns operational status of the firewall
     * @return boolean enabled;
     */
    public boolean isEnabled();
 
    /**
     * Returns all of the firewall rules
     * @return List of all rules
     */
    public List<FirewallRule> getRules();
    
    /**
     * Returns the subnet mask
     * @return subnet mask
     */
    public String getSubnetMask();
    
    /**
     * Sets the subnet mask
     * @param newMask The new subnet mask
     */
    public void setSubnetMask(String newMask);

    /**
     * Returns all of the firewall rules in storage
     * for debugging and unit-testing purposes
     * @return List of all rules in storage
     */
    public List<Map<String, Object>> getStorageRules();

    /**
     * Adds a new Firewall rule
     */
    public void addRule(FirewallRule rule);

    /**
     * Deletes a Firewall rule
     */
    public void deleteRule(int ruleid);
}
