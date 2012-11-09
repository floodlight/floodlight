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

}
