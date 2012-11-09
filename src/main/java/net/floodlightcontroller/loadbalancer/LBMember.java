package net.floodlightcontroller.loadbalancer;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

public class LBMember {
    protected String id;
    protected int address;
    protected short port;
    
    protected int connectionLimit;
    protected short adminState;
    protected short status;

}
