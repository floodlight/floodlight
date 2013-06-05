package net.floodlightcontroller.servicechaining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ServiceChaining module encapsulates properties of service chains and their member nodes
 *
 * @author kjiang
 *
 */
public class ServiceChain {
    // Tenant
    private String tenant;

    // Service Chain name
    private String name;

    // Source network
    private String srcBvsName;

    // Destination network
    private String dstBvsName;

    // Service Description
    private String description;

    // Ordered list of service Nodes
    private List<ServiceNode> nodes;

    /**
     * Constructor to create a NetworkService
     *
     * @param name
     * @param vMac
     * @param vIp
     */
    public ServiceChain(String tenant, String name, String description,
            String srcBvsName, String dstBvsName) {
        this.tenant = tenant;
        this.name = name;
        this.description = description;
        this.srcBvsName = srcBvsName;
        this.dstBvsName = dstBvsName;
        this.nodes = new ArrayList<ServiceNode>();
    }

    /**
     * A getter for service tenant
     * @return
     */
    public String getTenant() {
        return tenant;
    }

    /**
     * A getter for service name
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * A getter for service description
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * A getter for source BVS
     */
    public String getSourceBvs() {
        return srcBvsName;
    }

    /**
     * A getter for destination BVS
     */
    public String getDestinationBvs() {
        return dstBvsName;
    }

    /**
     * A getter returns an unmodifiable map of service nodes.
     * @return
     */
    public List<ServiceNode> getServiceNodes() {
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Add a service node to the end of the node list
     */
    public boolean addNode(ServiceNode node) {
        try {
            return nodes.add(node);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Remove a service node from the node list
     */
    public boolean removeNode(ServiceNode node) {
        try {
            return nodes.remove(node);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ServiceChain [tenant=" + tenant + ", name=" + name
                + ", srcBvsName=" + srcBvsName + ", dstBvsName=" + dstBvsName
                + ", description=" + description + "]";
    }
}
