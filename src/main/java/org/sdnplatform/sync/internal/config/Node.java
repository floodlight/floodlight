package org.sdnplatform.sync.internal.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represent a node in the synchronization system
 * @author readams
 */
public class Node {
    /**
     * The host name to use for contacting this node from the
     * other nodes
     */
    private String hostname;

    /**
     * The TCP port to use for contacting this node from the
     * other nodes
     */
    private int port;

    /**
     * The node ID for this node
     */
    private short nodeId;

    /**
     * The ID for the local cluster domain.  Data with a local scope will
     * be shared only among nodes that share the same domain ID.
     */
    private short domainId;

    @JsonCreator
    public Node(@JsonProperty("hostname") String hostname, 
                @JsonProperty("port") int port,
                @JsonProperty("nodeId") short nodeId,
                @JsonProperty("domainId") short domainId) {
        super();
        this.hostname = hostname;
        this.port = port;
        this.nodeId = nodeId;
        this.domainId = domainId;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public short getNodeId() {
        return nodeId;
    }

    public short getDomainId() {
        return domainId;
    }

    @Override
    public String toString() {
        return "Node [hostname=" + hostname + ", port=" + port + ", nodeId="
                + nodeId + ", domainId=" + domainId + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + domainId;
        result =
                prime * result
                        + ((hostname == null) ? 0 : hostname.hashCode());
        result = prime * result + nodeId;
        result = prime * result + port;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Node other = (Node) obj;
        if (domainId != other.domainId) return false;
        if (hostname == null) {
            if (other.hostname != null) return false;
        } else if (!hostname.equals(other.hostname)) return false;
        if (nodeId != other.nodeId) return false;
        if (port != other.port) return false;
        return true;
    }
}
