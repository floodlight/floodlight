package net.floodlightcontroller.topology;

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface ITopologyService extends IFloodlightService  {
    /**
     * Query to determine if the specified switch id and port tuple are
     * connected to another switch or not.  If so, this means the link
     * is passing LLDPs properly between two OpenFlow switches.
     * @param switchid
     * @param port
     * @return
     */
    public boolean isInternal(long switchid, short port);

    public long getSwitchClusterId(long switchId);

    /**
     * Retrieves a set of all the switches in the same cluster as sw.
     * A cluster is a set of switches that are directly or indirectly
     * connected via Openflow switches that use the same controller
     * (not necessarily the same controller instance but any controller
     * instance in a group sharing the same network database).
     * @param sw The switch whose cluster we're obtaining
     * @return Set of switches in the cluster
     */
    public Set<Long> getSwitchesInCluster(long switchId);

    /**
     * Queries whether two switches are in the same cluster.
     * @param switch1
     * @param switch2
     * @return true if the switches are in the same cluster
     */
    public boolean inSameCluster(long switch1, long switch2);


    public void addListener(ITopologyListener listener);

    public boolean isBroadcastDomainPort(long sw, short port);

    public boolean isAllowed(long sw, short portId);

    /**
     * If the dst is not allowed by the higher-level topology, this method provides
     * the topologically equivalent broadcast port.  
     * @param src
     * @param dst
     * @return the allowed broadcast port
     */
    public NodePortTuple getAllowedOutgoingBroadcastPort(NodePortTuple src, NodePortTuple dst);
    
    public boolean isIncomingBroadcastAllowed(long sw, short portId);

    public boolean isInSameBroadcastDomain(long s1, short p1, long s2, short p2);

    public Set<Short> getPorts(long sw);

    public Set<Short> getBroadcastPorts(long targetSw, long src, short srcPort);

    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort);

    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort);
}
