package net.floodlightcontroller.loadbalancer;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

public class LBMonitor {
    protected String id;
    protected String name;
    protected short type;
    protected short delay;
    protected short timeout;
    protected short attemptsBeforeDeactivation;
    
    protected String netId;
    protected int address;
    protected byte protocol;
    protected short port;

    //protected path??
    
    protected short adminState;
    protected short status;

    public LBMonitor() {
        id = null;
        name = null;
        type = 0;
        delay = 0;
        timeout = 0;
        attemptsBeforeDeactivation = 0;
        netId = null;
        address = 0;
        protocol = 0;
        port = 0;
        adminState = 0;
        status = 0;
        
    }
    
}
