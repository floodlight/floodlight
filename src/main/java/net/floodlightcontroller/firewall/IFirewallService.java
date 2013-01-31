/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by Amer Tahir
 *    
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may 
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *    
 *         http://www.apache.org/licenses/LICENSE-2.0 
 *    
 *    Unless required by applicable law or agreed to in writing, software 
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

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
