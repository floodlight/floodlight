package net.floodlightcontroller.loadbalancer;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

public class LBPool {
    protected String id;
    protected String name;
    protected String netId;
    protected byte protocol;
    protected LBMember[] members;
    protected LBMonitor[] monitors;
    protected short adminState;
    protected short status;    
    
}
