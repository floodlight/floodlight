package net.floodlightcontroller.loadbalancer;

import java.util.ArrayList;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

public class LBVip {
    protected String id;
    protected String name;
    protected String netId;
    protected int address;
    protected byte protocol;
    protected short lbMethod;
    protected short port;
    protected ArrayList<String> pools;
    protected boolean sessionPersistence;
    protected int connectionLimit;
    protected short adminState;
    protected short status;

}
