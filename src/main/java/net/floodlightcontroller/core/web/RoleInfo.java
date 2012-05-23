package net.floodlightcontroller.core.web;

import net.floodlightcontroller.core.IFloodlightProviderService.Role;

public class RoleInfo {
    protected String role;
    
    public RoleInfo() {
    }
    
    public RoleInfo(String role) {
        setRole(role);
    }
    
    public RoleInfo(Role role) {
        this.role = (role != null) ? role.name() : "DISABLED";
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
}