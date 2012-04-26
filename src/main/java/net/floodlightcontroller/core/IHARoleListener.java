package net.floodlightcontroller.core;

import net.floodlightcontroller.core.IFloodlightProviderService.Role;

public interface IHARoleListener {
    /**
     * Gets called when the controller changes role (i.e. Master -> Slave).
     * Note that oldRole CAN be null.
     * @param oldRole The controller's old role
     * @param newRole The controller's new role
     */
    public void roleChanged(Role oldRole, Role newRole);
}
