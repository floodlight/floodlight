package org.sdnplatform.sync.internal.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.sdnplatform.sync.error.SyncException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;


/**
 * Represent the configuration of a cluster in the sync manager
 * @author readams
 */
public class ClusterConfig {
    private HashMap<Short, Node> allNodes =
            new HashMap<Short, Node>();
    private HashMap<Short, List<Node>> localDomains =
            new HashMap<Short, List<Node>>();
    private Node thisNode;

    public ClusterConfig() {
        super();
    }

    /**
     * Initialize a cluster config object using a JSON string containing
     * the nodes
     * @param nodeConfig the JSON-formatted cluster configurations
     * @param thisNodeId the node ID for the current node
     * @throws SyncException
     */
    public ClusterConfig(String nodeConfig,
                         short thisNodeId) throws SyncException {
        super();
        ObjectMapper mapper = new ObjectMapper();
        List<Node> nodes;
        try {
            nodes = mapper.readValue(nodeConfig,
                                     new TypeReference<List<Node>>() { });
        } catch (Exception e) {
            throw new SyncException("Failed to initialize sync manager", e);
        }
        init(nodes, thisNodeId);
    }

    /**
     * Initialize a cluster config using a list of nodes
     * @param nodes the nodes to use
     * @param thisNodeId the node ID for the current node
     * @throws SyncException
     */
    public ClusterConfig(List<Node> nodes, short thisNodeId)
            throws SyncException {
        init(nodes, thisNodeId);
    }

    /**
     * Get a collection containing all configured nodes
     * @return the collection of nodes
     */
    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(allNodes.values());
    }

    /**
     * A collection of the nodes in the local domain for the current node
     * @return the list of nodes
     */
    public Collection<Node> getDomainNodes() {
        return getDomainNodes(thisNode.getDomainId());
    }

    /**
     * A collection of the nodes in the local domain specified
     * @param domainId the domain ID
     * @return the list of nodes
     */
    public Collection<Node> getDomainNodes(short domainId) {
        List<Node> r = localDomains.get(domainId);
        return Collections.unmodifiableCollection(r);
    }

    /**
     * Get the {@link Node} object for the current node
     */
    public Node getNode() {
        return thisNode;
    }

    /**
     * The a list of the nodes in the local domain specified
     * @param nodeId the node ID to retrieve
     * @return the node (or null if there is no such node
     */
    public Node getNode(short nodeId) {
        return allNodes.get(nodeId);
    }

    /**
     * Add a new node to the cluster
     * @param node the {@link Node} to add
     * @throws SyncException if the node already exists
     */
    private void addNode(Node node) throws SyncException {
        Short nodeId = node.getNodeId();
        if (allNodes.get(nodeId) != null) {
            throw new SyncException("Error adding node " + node +
                    ": a node with that ID already exists");
        }
        allNodes.put(nodeId, node);

        Short domainId = node.getDomainId();
        List<Node> localDomain = localDomains.get(domainId);
        if (localDomain == null) {
            localDomains.put(domainId,
                             localDomain = new ArrayList<Node>());
        }
        localDomain.add(node);
    }

    private void init(List<Node> nodes, short thisNodeId)
            throws SyncException {
        for (Node n : nodes) {
            addNode(n);
        }
        thisNode = getNode(thisNodeId);
        if (thisNode == null) {
            throw new SyncException("Cannot set thisNode " +
                    "node: No node with ID " + thisNodeId);
        }
    }

    @Override
    public String toString() {
        return "ClusterConfig [allNodes=" + allNodes + ", thisNode="
               + thisNode.getNodeId() + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((allNodes == null) ? 0 : allNodes.hashCode());
        result = prime * result
                 + ((localDomains == null) ? 0 : localDomains.hashCode());
        result = prime * result
                 + ((thisNode == null) ? 0 : thisNode.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ClusterConfig other = (ClusterConfig) obj;
        if (allNodes == null) {
            if (other.allNodes != null) return false;
        } else if (!allNodes.equals(other.allNodes)) return false;
        if (localDomains == null) {
            if (other.localDomains != null) return false;
        } else if (!localDomains.equals(other.localDomains)) return false;
        if (thisNode == null) {
            if (other.thisNode != null) return false;
        } else if (!thisNode.equals(other.thisNode)) return false;
        return true;
    }
}
