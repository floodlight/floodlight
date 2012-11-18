package net.floodlightcontroller.loadbalancer;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

public class LBStats {
    protected int bytesIn;
    protected int bytesOut;
    protected int activeConnections;
    protected int totalConnections;
    
    public LBStats() {
        bytesIn = 0;
        bytesOut = 0;
        activeConnections = 0;
        totalConnections = 0;
    }
}
