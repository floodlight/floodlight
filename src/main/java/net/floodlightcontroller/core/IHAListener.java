package net.floodlightcontroller.core;

import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService.Role;

public interface IHAListener {
    /**
     * Gets called when the controller changes role (i.e. Master -> Slave).
     * Note that oldRole CAN be null.
     * @param oldRole The controller's old role
     * @param newRole The controller's new role
     */
    public void roleChanged(Role oldRole, Role newRole);
    
    /**
     * Gets called when the IP addresses of the controller nodes in the 
     * controller cluster change. All parameters map controller ID to
     * the controller's IP.
     *  
     * @param curControllerNodeIPs The current mapping of controller IDs to IP
     * @param addedControllerNodeIPs These IPs were added since the last update
     * @param removedControllerNodeIPs These IPs were removed since the last update
     */
    public void controllerNodeIPsChanged(
    		Map<String, String> curControllerNodeIPs,  
    		Map<String, String> addedControllerNodeIPs,  
    		Map<String, String> removedControllerNodeIPs
    		);
}
