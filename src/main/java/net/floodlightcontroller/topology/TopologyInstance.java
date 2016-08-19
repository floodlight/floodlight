/**::
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.topology;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.PathId;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.util.ClusterDFS;

import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.python.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * A representation of a network topology. Used internally by
 * {@link TopologyManager}
 */
public class TopologyInstance {

    public static final short LT_SH_LINK = 1;
    public static final short LT_BD_LINK = 2;
    public static final short LT_TUNNEL  = 3;

    public static final int MAX_LINK_WEIGHT = 10000;
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    public static final int PATH_CACHE_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(TopologyInstance.class);

    /* Global: general switch, port, link */
    private Set<DatapathId>                 switches;
    private Map<DatapathId, Set<OFPort>>    portsWithLinks; /* only ports with links */
    private Map<DatapathId, Set<OFPort>>    portsPerSwitch; /* every port on the switch */
    private Set<NodePortTuple>              portsTunnel; /* all tunnel ports in topology */
    private Set<NodePortTuple>              portsBroadcastAll; /* all broadcast ports in topology */
    private Map<DatapathId, Set<OFPort>>    portsBroadcastPerSwitch; /* broadcast ports mapped per DPID */
    private Set<NodePortTuple>              portsWithMoreThanTwoLinks; /* a.k.a. "broadcast domain" non-P2P ports */
    private Map<NodePortTuple, Set<Link>>   links; /* every link in entire topology */
    private Map<NodePortTuple, Set<Link>>   linksNonBcastNonTunnel; /* only non-broadcast and non-tunnel links */
    private Map<NodePortTuple, Set<Link>>   linksExternal; /* BDDP links b/t clusters */
    private Set<Link>                       linksNonExternalInterCluster;

    /* Blocked */
    private Set<NodePortTuple>  portsBlocked;
    private Set<Link>           linksBlocked;

    /* Per-cluster */
    private Set<Cluster>                        clusters;
    private Map<DatapathId, Set<NodePortTuple>> clusterPorts; /* ports in the cluster ID */
    private Map<DatapathId, Cluster>            clusterFromSwitch; /* cluster for each switch */

    /* Per-archipelago */
    private Set<Archipelago>                    archipelagos; /* connected clusters */
    private Map<Cluster, Archipelago>           archipelagoFromCluster;
    private Map<DatapathId, Set<NodePortTuple>> portsBroadcastPerArchipelago; /* broadcast ports in each archipelago ID */
    private Map<PathId, List<Path>>             pathcache; /* contains computed paths ordered best to worst */

    protected TopologyInstance(Map<DatapathId, Set<OFPort>> portsWithLinks,
            Set<NodePortTuple> portsBlocked,
            Map<NodePortTuple, Set<Link>> linksNonBcastNonTunnel,
            Set<NodePortTuple> portsWithMoreThanTwoLinks,
            Set<NodePortTuple> portsTunnel, 
            Map<NodePortTuple, Set<Link>> links,
            Map<DatapathId, Set<OFPort>> portsPerSwitch,
            Map<NodePortTuple, Set<Link>> linksExternal) {

        this.switches = new HashSet<DatapathId>(portsWithLinks.keySet());
        this.portsWithLinks = new HashMap<DatapathId, Set<OFPort>>();
        for (DatapathId sw : portsWithLinks.keySet()) {
            this.portsWithLinks.put(sw, new HashSet<OFPort>(portsWithLinks.get(sw)));
        }

        this.portsPerSwitch = new HashMap<DatapathId, Set<OFPort>>();
        for (DatapathId sw : portsPerSwitch.keySet()) {
            this.portsPerSwitch.put(sw, new HashSet<OFPort>(portsPerSwitch.get(sw)));
        }

        this.portsBlocked = new HashSet<NodePortTuple>(portsBlocked);
        this.linksNonBcastNonTunnel = new HashMap<NodePortTuple, Set<Link>>();
        for (NodePortTuple npt : linksNonBcastNonTunnel.keySet()) {
            this.linksNonBcastNonTunnel.put(npt, new HashSet<Link>(linksNonBcastNonTunnel.get(npt)));
        }

        this.links = new HashMap<NodePortTuple, Set<Link>>();
        for (NodePortTuple npt : links.keySet()) {
            this.links.put(npt, new HashSet<Link>(links.get(npt)));
        }

        this.linksExternal = new HashMap<NodePortTuple, Set<Link>>();
        for (NodePortTuple npt : linksExternal.keySet()) {
            this.linksExternal.put(npt, new HashSet<Link>(linksExternal.get(npt)));
        }

        this.linksNonExternalInterCluster = new HashSet<Link>();
        this.archipelagos = new HashSet<Archipelago>();

        this.portsWithMoreThanTwoLinks = new HashSet<NodePortTuple>(portsWithMoreThanTwoLinks);
        this.portsTunnel = new HashSet<NodePortTuple>(portsTunnel);

        this.linksBlocked = new HashSet<Link>();

        this.clusters = new HashSet<Cluster>();
        this.clusterFromSwitch = new HashMap<DatapathId, Cluster>();
        this.portsBroadcastAll= new HashSet<NodePortTuple>();
        this.portsBroadcastPerSwitch = new HashMap<DatapathId,Set<OFPort>>();

        this.pathcache = new HashMap<PathId, List<Path>>();

        this.portsBroadcastPerArchipelago = new HashMap<DatapathId, Set<NodePortTuple>>();

        this.archipelagoFromCluster = new HashMap<Cluster, Archipelago>();
    }

    protected void compute() {
        /*
         * Step 1: Compute clusters ignoring ports with > 2 links and 
         * blocked links.
         */
        identifyClusters();

        /*
         * Step 2: Associate non-blocked links within clusters to the cluster
         * in which they reside. The remaining links are inter-cluster links.
         */
        identifyIntraClusterLinks();

        /* 
         * Step 3: Compute the archipelagos. (Def: group of conneccted clusters)
         * Each archipelago will have its own broadcast tree, chosen by running 
         * dijkstra's algorithm from the archipelago ID switch (lowest switch 
         * DPID). We need a broadcast tree per archipelago since each 
         * archipelago is by definition isolated from all other archipelagos.
         */
        identifyArchipelagos();

        /*
         * Step 4: Use Yens algorithm to permute through each node combination
         * within each archipelago and compute multiple paths. The shortest
         * path located (i.e. first run of dijkstra's algorithm) will be used 
         * as the broadcast tree for the archipelago.
         */
        computeOrderedPaths();

        /*
         * Step 5: Determine the broadcast ports for each archipelago. These are
         * the ports that reside on the broadcast tree computed and saved when
         * performing path-finding. These are saved into multiple data structures
         * to aid in quick lookup per archipelago, per-switch, and topology-global.
         */
        computeBroadcastPortsPerArchipelago();

        /*
         * Step 6: Optionally, print topology to log for added verbosity or when debugging.
         */
        printTopology();
    }

    /*
     * Checks if OF port is edge port
     */
    public boolean isEdge(DatapathId sw, OFPort portId) { 
        NodePortTuple np = new NodePortTuple(sw, portId);
        if (links.get(np) == null || links.get(np).isEmpty()) {
            return true;
        }
        else {
            return false;
        }
    }   

    /*
     * Returns broadcast ports for the given DatapathId
     */
    public Set<OFPort> swBroadcastPorts(DatapathId sw) {
        if (!portsBroadcastPerSwitch.containsKey(sw) || portsBroadcastPerSwitch.get(sw) == null) {
            log.debug("Could not locate broadcast ports for switch {}", sw);
            return Collections.emptySet();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Found broadcast ports {} for switch {}", portsBroadcastPerSwitch.get(sw), sw);
            }
            return portsBroadcastPerSwitch.get(sw);
        }
    }

    private void printTopology() {
        log.debug("-----------------Topology-----------------------");
        log.debug("All Links: {}", links);
        log.debug("Tunnel Ports: {}", portsTunnel);
        log.debug("Clusters: {}", clusters);
        log.debug("Broadcast Ports Per Node (!!): {}", portsBroadcastPerSwitch);
        log.debug("3+ Link Ports: {}", portsWithMoreThanTwoLinks);
        log.debug("Archipelagos: {}", archipelagos);
        log.debug("-----------------------------------------------");  
    }

    private void identifyIntraClusterLinks() {
        for (DatapathId s : switches) {
            if (portsWithLinks.get(s) == null) continue;
            for (OFPort p : portsWithLinks.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (linksNonBcastNonTunnel.get(np) == null) continue;
                if (isBroadcastPort(np)) continue;
                for (Link l : linksNonBcastNonTunnel.get(np)) {
                    if (isBlockedLink(l)) continue;
                    if (isBroadcastLink(l)) continue;
                    Cluster c1 = clusterFromSwitch.get(l.getSrc());
                    Cluster c2 = clusterFromSwitch.get(l.getDst());
                    if (c1 == c2) {
                        c1.addLink(l); /* link is within cluster */
                    } else {
                        linksNonExternalInterCluster.add(l);
                    }
                }
            }
        }
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This function divides the network into clusters. Every cluster is
     * a strongly connected component. The network may contain unidirectional
     * links.  The function calls dfsTraverse for performing depth first
     * search and cluster formation.
     *
     * The computation of strongly connected components is based on
     * Tarjan's algorithm.  For more details, please see the Wikipedia
     * link below.
     *
     * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     */
    private void identifyClusters() {
        Map<DatapathId, ClusterDFS> dfsList = new HashMap<DatapathId, ClusterDFS>();

        if (switches == null) return;

        for (DatapathId key : switches) {
            ClusterDFS cdfs = new ClusterDFS();
            dfsList.put(key, cdfs);
        }

        Set<DatapathId> currSet = new HashSet<DatapathId>();

        for (DatapathId sw : switches) {
            ClusterDFS cdfs = dfsList.get(sw);
            if (cdfs == null) {
                log.error("No DFS object for switch {} found.", sw);
            } else if (!cdfs.isVisited()) {
                dfsTraverse(0, 1, sw, dfsList, currSet);
            }
        }
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This algorithm computes the depth first search (DFS) traversal of the
     * switches in the network, computes the lowpoint, and creates clusters
     * (of strongly connected components).
     *
     * The computation of strongly connected components is based on
     * Tarjan's algorithm.  For more details, please see the Wikipedia
     * link below.
     *
     * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     *
     * The initialization of lowpoint and the check condition for when a
     * cluster should be formed is modified as we do not remove switches that
     * are already part of a cluster.
     *
     * A return value of -1 indicates that dfsTraverse failed somewhere in the middle
     * of computation.  This could happen when a switch is removed during the cluster
     * computation procedure.
     *
     * @param parentIndex: DFS index of the parent node
     * @param currIndex: DFS index to be assigned to a newly visited node
     * @param currSw: ID of the current switch
     * @param dfsList: HashMap of DFS data structure for each switch
     * @param currSet: Set of nodes in the current cluster in formation
     * @return long: DSF index to be used when a new node is visited
     */
    private long dfsTraverse (long parentIndex, long currIndex, DatapathId currSw,
            Map<DatapathId, ClusterDFS> dfsList, Set<DatapathId> currSet) {

        //Get the DFS object corresponding to the current switch
        ClusterDFS currDFS = dfsList.get(currSw);

        Set<DatapathId> nodesInMyCluster = new HashSet<DatapathId>();
        Set<DatapathId> myCurrSet = new HashSet<DatapathId>();

        //Assign the DFS object with right values.
        currDFS.setVisited(true);
        currDFS.setDfsIndex(currIndex);
        currDFS.setParentDFSIndex(parentIndex);
        currIndex++;

        // Traverse the graph through every outgoing link.
        if (portsWithLinks.get(currSw) != null){
            for (OFPort p : portsWithLinks.get(currSw)) {
                Set<Link> lset = linksNonBcastNonTunnel.get(new NodePortTuple(currSw, p));
                if (lset == null) continue;
                for (Link l : lset) {
                    DatapathId dstSw = l.getDst();

                    // ignore incoming links.
                    if (dstSw.equals(currSw)) continue;

                    // ignore if the destination is already added to
                    // another cluster
                    if (clusterFromSwitch.get(dstSw) != null) continue;

                    // ignore the link if it is blocked.
                    if (isBlockedLink(l)) continue;

                    // ignore this link if it is in broadcast domain
                    if (isBroadcastLink(l)) continue;

                    // Get the DFS object corresponding to the dstSw
                    ClusterDFS dstDFS = dfsList.get(dstSw);

                    if (dstDFS.getDfsIndex() < currDFS.getDfsIndex()) {
                        // could be a potential lowpoint
                        if (dstDFS.getDfsIndex() < currDFS.getLowpoint()) {
                            currDFS.setLowpoint(dstDFS.getDfsIndex());
                        }
                    } else if (!dstDFS.isVisited()) {
                        // make a DFS visit
                        currIndex = dfsTraverse(
                                currDFS.getDfsIndex(), 
                                currIndex, dstSw, 
                                dfsList, myCurrSet);

                        if (currIndex < 0) return -1;

                        // update lowpoint after the visit
                        if (dstDFS.getLowpoint() < currDFS.getLowpoint()) {
                            currDFS.setLowpoint(dstDFS.getLowpoint());
                        }

                        nodesInMyCluster.addAll(myCurrSet);
                        myCurrSet.clear();
                    }
                    // else, it is a node already visited with a higher
                    // dfs index, just ignore.
                }
            }
        }

        nodesInMyCluster.add(currSw);
        currSet.addAll(nodesInMyCluster);

        // Cluster computation.
        // If the node's lowpoint is greater than its parent's DFS index,
        // we need to form a new cluster with all the switches in the
        // currSet.
        if (currDFS.getLowpoint() > currDFS.getParentDFSIndex()) {
            // The cluster thus far forms a strongly connected component.
            // create a new switch cluster and the switches in the current
            // set to the switch cluster.
            Cluster sc = new Cluster();
            for (DatapathId sw : currSet) {
                sc.add(sw);
                clusterFromSwitch.put(sw, sc);
            }
            // delete all the nodes in the current set.
            currSet.clear();
            // add the newly formed switch clusters to the cluster set.
            clusters.add(sc);
        }

        return currIndex;
    }

    public Set<NodePortTuple> getBlockedPorts() {
        return this.portsBlocked;
    }

    public Set<Link> getBlockedLinks() {
        return this.linksBlocked;
    }

    /** Returns true if a link has either one of its switch ports
     * blocked.
     * @param l
     * @return
     */
    public boolean isBlockedLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (isBlockedPort(n1) || isBlockedPort(n2));
    }

    public boolean isBlockedPort(NodePortTuple npt) {
        return portsBlocked.contains(npt);
    }

    public boolean isTunnelPort(NodePortTuple npt) {
        return portsTunnel.contains(npt);
    }

    public boolean isTunnelLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (isTunnelPort(n1) || isTunnelPort(n2));
    }

    public boolean isBroadcastLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (isBroadcastPort(n1) || isBroadcastPort(n2));
    }

    public boolean isBroadcastPort(NodePortTuple npt) {
        return portsBroadcastAll.contains(npt);
    }

    private class NodeDist implements Comparable<NodeDist> {
        private final DatapathId node;
        public DatapathId getNode() {
            return node;
        }

        private final int dist;
        public int getDist() {
            return dist;
        }

        public NodeDist(DatapathId node, int dist) {
            this.node = node;
            this.dist = dist;
        }

        @Override
        public int compareTo(NodeDist o) {
            if (o.dist == this.dist) {
                return (int)(this.node.getLong() - o.node.getLong());
            }
            return this.dist - o.dist;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            NodeDist other = (NodeDist) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (node == null) {
                if (other.node != null)
                    return false;
            } else if (!node.equals(other.node))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            assert false : "hashCode not designed";
        return 42;
        }

        private TopologyInstance getOuterType() {
            return TopologyInstance.this;
        }
    }

    private void identifyArchipelagos() {
        // Iterate through each external link and create/merge archipelagos based on the
        // islands that each link is connected to
        Cluster srcCluster = null;
        Cluster dstCluster = null;
        Archipelago srcArchipelago = null;
        Archipelago dstArchipelago = null;
        Set<Link> links = new HashSet<Link>();

        for (Set<Link> linkset : linksExternal.values()) {
            links.addAll(linkset);
        }
        links.addAll(linksNonExternalInterCluster);

        /* Base case of 1:1 mapping b/t clusters and archipelagos */
        if (links.isEmpty()) {
            if (!clusters.isEmpty()) {
                clusters.forEach(c -> archipelagos.add(new Archipelago().add(c)));
            }
        } else { /* Only for two or more adjacent clusters that form archipelagos */
            for (Link l : links) {
                for (Cluster c : clusters) {
                    if (c.getNodes().contains(l.getSrc())) srcCluster = c;
                    if (c.getNodes().contains(l.getDst())) dstCluster = c;
                }
                for (Archipelago a : archipelagos) {
                    // Is source cluster a part of an existing archipelago?
                    if (a.isMember(srcCluster)) srcArchipelago = a;
                    // Is destination cluster a part of an existing archipelago?
                    if (a.isMember(dstCluster)) dstArchipelago = a;
                }

                // Are they both found in an archipelago? If so, then merge the two.
                if (srcArchipelago != null && dstArchipelago != null && !srcArchipelago.equals(dstArchipelago)) {
                    srcArchipelago.merge(dstArchipelago);
                    archipelagos.remove(dstArchipelago);
                    archipelagoFromCluster.put(dstCluster, srcArchipelago);
                }

                // If neither were found in an existing, then form a new archipelago.
                else if (srcArchipelago == null && dstArchipelago == null) {
                    Archipelago a = new Archipelago().add(srcCluster).add(dstCluster);
                    archipelagos.add(a);
                    archipelagoFromCluster.put(srcCluster, a);
                    archipelagoFromCluster.put(dstCluster, a);
                }

                // If only one is found in an existing, then add the one not found to the existing.
                else if (srcArchipelago != null && dstArchipelago == null) {
                    srcArchipelago.add(dstCluster);
                    archipelagoFromCluster.put(dstCluster, srcArchipelago);
                }

                else if (srcArchipelago == null && dstArchipelago != null) {
                    dstArchipelago.add(srcCluster);
                    archipelagoFromCluster.put(srcCluster, dstArchipelago);
                }

                srcCluster = null;
                dstCluster = null;
                srcArchipelago = null;
                dstArchipelago = null;
            }
        }
    }

    /*
     * Dijkstra that calculates destination rooted trees over the entire topology.
     */
    private BroadcastTree dijkstra(Map<DatapathId, Set<Link>> links, DatapathId root,
            Map<Link, Integer> linkCost,
            boolean isDstRooted) {
        HashMap<DatapathId, Link> nexthoplinks = new HashMap<DatapathId, Link>();
        HashMap<DatapathId, Integer> cost = new HashMap<DatapathId, Integer>();
        int w;

        for (DatapathId node : links.keySet()) {
            nexthoplinks.put(node, null);
            cost.put(node, MAX_PATH_WEIGHT);
            //log.debug("Added max cost to {}", node);
        }

        HashMap<DatapathId, Boolean> seen = new HashMap<DatapathId, Boolean>();
        PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
        nodeq.add(new NodeDist(root, 0));
        cost.put(root, 0);

        //log.debug("{}", links);

        while (nodeq.peek() != null) {
            NodeDist n = nodeq.poll();
            DatapathId cnode = n.getNode();
            int cdist = n.getDist();

            if (cdist >= MAX_PATH_WEIGHT) break;
            if (seen.containsKey(cnode)) continue;
            seen.put(cnode, true);

            //log.debug("cnode {} and links {}", cnode, links.get(cnode));
            if (links.get(cnode) == null) continue;
            for (Link link : links.get(cnode)) {
                DatapathId neighbor;

                if (isDstRooted == true) {
                    neighbor = link.getSrc();
                } else {
                    neighbor = link.getDst();
                }

                // links directed toward cnode will result in this condition
                if (neighbor.equals(cnode)) continue;

                if (seen.containsKey(neighbor)) continue;

                if (linkCost == null || linkCost.get(link) == null) {
                    w = 1;
                } else {
                    w = linkCost.get(link);
                }

                int ndist = cdist + w; // the weight of the link, always 1 in current version of floodlight.
                log.debug("Neighbor: {}", neighbor);
                log.debug("Cost: {}", cost);
                log.debug("Neighbor cost: {}", cost.get(neighbor));

                if (ndist < cost.get(neighbor)) {
                    cost.put(neighbor, ndist);
                    nexthoplinks.put(neighbor, link);

                    NodeDist ndTemp = new NodeDist(neighbor, ndist);
                    // Remove an object that's already in there.
                    // Note that the comparison is based on only the node id,
                    // and not node id and distance.
                    nodeq.remove(ndTemp);
                    // add the current object to the queue.
                    nodeq.add(ndTemp);
                }
            }
        }

        BroadcastTree ret = new BroadcastTree(nexthoplinks, cost);

        return ret;
    }

    /*
     * Creates a map of links and the cost associated with each link
     */
    public Map<Link,Integer> initLinkCostMap() {
        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = portsWithLinks.size() + 1;

        switch (TopologyManager.getPathMetricInternal()){
        case HOPCOUNT_AVOID_TUNNELS:
            log.debug("Using hop count with tunnel bias for metrics");
            for (NodePortTuple npt : portsTunnel) {
                if (links.get(npt) == null) {
                    continue;
                }
                for (Link link : links.get(npt)) {
                    if (link == null) {
                        continue;
                    }
                    linkCost.put(link, tunnel_weight);
                }
            }
            return linkCost;

        case HOPCOUNT:
            log.debug("Using hop count w/o tunnel bias for metrics");
            for (NodePortTuple npt : links.keySet()) {
                if (links.get(npt) == null) {
                    continue;
                }
                for (Link link : links.get(npt)) {
                    if (link == null) {
                        continue;
                    }
                    linkCost.put(link,1);
                }
            }
            return linkCost;

        case LATENCY:
            log.debug("Using latency for path metrics");
            for (NodePortTuple npt : links.keySet()) {
                if (links.get(npt) == null) {
                    continue;
                }
                for (Link link : links.get(npt)) {
                    if (link == null) {
                        continue;
                    }
                    if ((int)link.getLatency().getValue() < 0 || 
                            (int)link.getLatency().getValue() > MAX_LINK_WEIGHT) {
                        linkCost.put(link, MAX_LINK_WEIGHT);
                    } else {
                        linkCost.put(link,(int)link.getLatency().getValue());
                    }
                }
            }
            return linkCost;

        case LINK_SPEED:
            TopologyManager.statisticsService.collectStatistics(true);
            log.debug("Using link speed for path metrics");
            for (NodePortTuple npt : links.keySet()) {
                if (links.get(npt) == null) {
                    continue;
                }
                long rawLinkSpeed = 0;
                IOFSwitch s = TopologyManager.switchService.getSwitch(npt.getNodeId());
                if (s != null) {
                    OFPortDesc p = s.getPort(npt.getPortId());
                    if (p != null) {
                        rawLinkSpeed = p.getCurrSpeed();
                    }
                }
                for (Link link : links.get(npt)) {
                    if (link == null) {
                        continue;
                    }

                    if ((rawLinkSpeed / 10^6) / 8 > 1) {
                        int linkSpeedMBps = (int)(rawLinkSpeed / 10^6) / 8;
                        linkCost.put(link, (1/linkSpeedMBps)*1000);
                    } else {
                        linkCost.put(link, MAX_LINK_WEIGHT);
                    }
                }
            }
            return linkCost;
            
        case UTILIZATION:
            TopologyManager.statisticsService.collectStatistics(true);
            log.debug("Using utilization for path metrics");
            for (NodePortTuple npt : links.keySet()) {
                if (links.get(npt) == null) continue;
                SwitchPortBandwidth spb = TopologyManager.statisticsService
                        .getBandwidthConsumption(npt.getNodeId(), npt.getPortId());
                long bpsTx = 0;
                if (spb != null) {
                    bpsTx = spb.getBitsPerSecondTx().getValue();
                }
                for (Link link : links.get(npt)) {
                    if (link == null) {
                        continue;
                    }

                    if ((bpsTx / 10^6) / 8 > 1) {
                        int cost = (int) (bpsTx / 10^6) / 8;
                        linkCost.put(link, ((1/cost)*1000));
                    } else {
                        linkCost.put(link, MAX_LINK_WEIGHT);
                    }
                }
            }
            return linkCost;

        default:
            log.debug("Invalid Selection: Using Default Hop Count with Tunnel Bias for Metrics");
            for (NodePortTuple npt : portsTunnel) {
                if (links.get(npt) == null) continue;
                for (Link link : links.get(npt)) {
                    if (link == null) continue;
                    linkCost.put(link, tunnel_weight);
                }
            }
            return linkCost;
        }
    }

    /*
     * Calculates and stores n possible paths  using Yen's algorithm,
     * looping through every switch. These lists of routes are stored 
     * in the pathcache.
     */
    private void computeOrderedPaths() {
        List<Path> paths;
        PathId pathId;
        pathcache.clear();

        for (Archipelago a : archipelagos) { /* for each archipelago */
            Set<DatapathId> srcSws = a.getSwitches();
            Set<DatapathId> dstSws = a.getSwitches();
            log.debug("SRC {}", srcSws);
            log.debug("DST {}", dstSws);

            for (DatapathId src : srcSws) { /* permute all member switches */
                for (DatapathId dst : dstSws) {
                    log.debug("Calling Yens {} {}", src, dst);
                    paths = yens(src, dst, TopologyManager.getMaxPathsToComputeInternal(),
                            getArchipelago(src), getArchipelago(dst));
                    pathId = new PathId(src, dst);
                    pathcache.put(pathId, paths);
                    log.debug("Adding paths {}", paths);
                }
            }
        }
    }

    private Path buildPath(PathId id, BroadcastTree tree) {
        NodePortTuple npt;
        DatapathId srcId = id.getSrc();
        DatapathId dstId = id.getDst();
        //set of NodePortTuples on the route
        LinkedList<NodePortTuple> sPorts = new LinkedList<NodePortTuple>();

        if (tree == null) return new Path(id, ImmutableList.of()); /* empty route */

        Map<DatapathId, Link> nexthoplinks = tree.getLinks();

        if (!switches.contains(srcId) || !switches.contains(dstId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not
            // in the network)
            log.debug("buildpath: Standalone switch: {}", srcId);

            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []

        } else if ((nexthoplinks != null) && (nexthoplinks.get(srcId) != null)) {
            while (!srcId.equals(dstId)) {
                Link l = nexthoplinks.get(srcId);
                npt = new NodePortTuple(l.getSrc(), l.getSrcPort());
                sPorts.addLast(npt);
                npt = new NodePortTuple(l.getDst(), l.getDstPort());
                sPorts.addLast(npt);
                srcId = nexthoplinks.get(srcId).getDst();
            }
        }
        // else, no path exists, and path equals null

        Path result = null;
        if (sPorts != null && !sPorts.isEmpty()) {
            result = new Path(id, sPorts);

        }
        log.trace("buildpath: {}", result);
        return result == null ? new Path(id, ImmutableList.of()) : result;
    }

    /*
     * Getter Functions
     */

    public boolean pathExists(DatapathId srcId, DatapathId dstId) {
        Archipelago srcA = getArchipelago(srcId);
        Archipelago dstA = getArchipelago(dstId);
        if (!srcA.getId().equals(dstA.getId())) {
            return false;
        }

        BroadcastTree bt = srcA.getBroadcastTree();
        if (bt == null) {
            return false;
        }

        Link link = bt.getLinks().get(srcId);
        if (link == null) {
            return false;
        }
        return true;
    }

    private Map<DatapathId, Set<Link>> buildLinkDpidMap(Set<DatapathId> switches, Map<DatapathId,
            Set<OFPort>> portsWithLinks, Map<NodePortTuple, Set<Link>> links) {

        Map<DatapathId, Set<Link>> linkDpidMap = new HashMap<DatapathId, Set<Link>>();
        for (DatapathId s : switches) {
            if (portsWithLinks.get(s) == null) continue;
            for (OFPort p : portsWithLinks.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (links.get(np) == null) continue;
                for (Link l : links.get(np)) {
                    if (switches.contains(l.getSrc()) && switches.contains(l.getDst())) {
                        if (linkDpidMap.containsKey(s)) {
                            linkDpidMap.get(s).add(l);
                        } else {
                            linkDpidMap.put(s, new HashSet<Link>(Arrays.asList(l)));
                        }
                    }
                }
            }
        }

        return linkDpidMap;
    }

    /**
     *
     * This function returns K number of routes between a source and destination IF THEY EXIST IN THE ROUTECACHE.
     * If the user requests more routes than available, only the routes already stored in memory will be returned.
     * This value can be adjusted in floodlightdefault.properties.
     *
     *
     * @param src: DatapathId of the route source.
     * @param dst: DatapathId of the route destination.
     * @param k: The number of routes that you want. Must be positive integer.
     * @return ArrayList of Routes or null if bad parameters
     */
    public List<Path> getPathsFast(DatapathId src, DatapathId dst, int k) {
        PathId routeId = new PathId(src, dst);
        List<Path> routes = pathcache.get(routeId);

        if (routes == null || k < 1) {
            return ImmutableList.of();
        }

        if (k >= TopologyManager.getMaxPathsToComputeInternal() || k >= routes.size()) {
            return routes;
        } else {
            return routes.subList(0, k);
        }
    }

    /**
     *
     * This function returns K number of paths between a source and destination. It will attempt to retrieve
     * these paths from the pathcache. If the user requests more paths than are stored, Yen's algorithm will be
     * run using the K value passed in.
     *
     *
     * @param src: DatapathId of the path source.
     * @param dst: DatapathId of the path destination.
     * @param k: The number of path that you want. Must be positive integer.
     * @return list of paths or empty
     */
    public List<Path> getPathsSlow(DatapathId src, DatapathId dst, int k) {
        PathId pathId = new PathId(src, dst);
        List<Path> paths = pathcache.get(pathId);

        if (paths == null || k < 1) return ImmutableList.of();

        if (k >= TopologyManager.getMaxPathsToComputeInternal() || k >= paths.size()) {
            return yens(src, dst, k, getArchipelago(src), getArchipelago(dst)); /* heavy computation */
        }
        else {
            return new ArrayList<Path>(paths.subList(0, k));
        }
    }

    private Archipelago getArchipelago(DatapathId d) {
        for (Archipelago a : archipelagos) {
            if (a.getSwitches().contains(d)) {
                return a;
            }
        }
        return null;
    }

    public void setPathCosts(Path p) {
        U64 cost = U64.ZERO;

        // Set number of hops. Assuming the list of NPTs is always even.
        p.setHopCount(p.getPath().size()/2);

        for (int i = 0; i <= p.getPath().size() - 2; i = i + 2) {
            DatapathId src = p.getPath().get(i).getNodeId();
            DatapathId dst = p.getPath().get(i + 1).getNodeId();
            OFPort srcPort = p.getPath().get(i).getPortId();
            OFPort dstPort = p.getPath().get(i + 1).getPortId();
            for (Link l : links.get(p.getPath().get(i))) {
                if (l.getSrc().equals(src) && l.getDst().equals(dst) &&
                        l.getSrcPort().equals(srcPort) && l.getDstPort().equals(dstPort)) {
                    log.debug("Matching link found: {}", l);
                    cost = cost.add(l.getLatency());
                }
            }
        }

        p.setLatency(cost);
        log.debug("Total cost is {}", cost);
        log.debug(p.toString());

    }

    private List<Path> yens(DatapathId src, DatapathId dst, Integer K, Archipelago aSrc, Archipelago aDst) {

        log.debug("YENS ALGORITHM -----------------");
        log.debug("Asking for paths from {} to {}", src, dst);
        log.debug("Asking for {} paths", K);

        // Find link costs
        Map<Link, Integer> linkCost = initLinkCostMap();

        Map<DatapathId, Set<Link>> linkDpidMap = buildLinkDpidMap(switches, portsWithLinks, links);

        Map<DatapathId, Set<Link>> copyOfLinkDpidMap = new HashMap<DatapathId, Set<Link>>(linkDpidMap);

        // A is the list of shortest paths. The number in the list at the end should be less than or equal to K
        // B is the list of possible shortest paths found in this function.
        List<Path> A = new ArrayList<Path>();
        List<Path> B = new ArrayList<Path>();

        // The number of paths requested should never be less than 1.
        if (K < 1) {
            return A;
        }

        /* The switch is not a member of an archipelago. It must not be connected */
        if (aSrc == null || aDst == null) {
            log.warn("One or more switches not connected. Cannot compute path b/t {} and {}", src, dst);
            return A;
        }

        if (!aSrc.equals(aDst)) {
            log.warn("Switches {} and {} not in same archipelago. Cannot compute path", src, dst);
            return A;
        }

        /* Use Dijkstra's to find the shortest path, which will also be the first path in A */
        BroadcastTree bt = dijkstra(copyOfLinkDpidMap, dst, linkCost, true);
        /* add this initial tree as our archipelago's broadcast tree (aSrc == aDst) */
        aSrc.setBroadcastTree(bt);
        /* now add the shortest path */
        log.debug("src {} dst {} tree {}", new Object[] {src, dst, bt});
        Path newroute = buildPath(new PathId(src, dst), bt); /* guaranteed to be in same tree */

        if (newroute != null && !newroute.getPath().isEmpty()) { /* should never be null, but might be empty */
            setPathCosts(newroute);
            A.add(newroute);
            log.debug("Found shortest path in Yens {}", newroute);
        }
        else {
            log.debug("No paths found in Yen's!");
            return A;
        }

        // Loop through K - 1 times to get other possible shortest paths
        for (int k = 1; k < K; k++) {
            log.trace("k: {}", k);
            if (log.isTraceEnabled()){
                log.trace("Path Length 'A.get(k-1).getPath().size()-2': {}", A.get(k - 1).getPath().size() - 2);
            }
            // Iterate through i, which is the number of links in the most recent path added to A
            for (int i = 0; i <= A.get(k - 1).getPath().size() - 2; i = i + 2) {
                log.trace("i: {}", i);
                List<NodePortTuple> path = A.get(k - 1).getPath();
                //log.debug("A(k-1): {}", A.get(k - 1).getPath());
                // The spur node is the point in the topology where Dijkstra's is called again to find another path
                DatapathId spurNode = path.get(i).getNodeId();
                // rootPath is the path along the previous shortest path that is before the spur node
                Path rootPath = new Path(new PathId(path.get(0).getNodeId(), path.get(i).getNodeId()),
                        path.subList(0, i));


                Map<NodePortTuple, Set<Link>> allLinksCopy = new HashMap<NodePortTuple, Set<Link>>(links);
                // Remove the links after the spur node that are part of other paths in A so that new paths
                // found are unique
                for (Path r : A) {
                    if (r.getPath().size() > (i + 1) && r.getPath().subList(0, i).equals(rootPath.getPath())) {
                        allLinksCopy.remove(r.getPath().get(i));
                        allLinksCopy.remove(r.getPath().get(i+1));
                    }
                }

                // Removes the root path so Dijkstra's doesn't try to go through it to find a path
                Set<DatapathId> switchesCopy = new HashSet<DatapathId>(switches);
                for (NodePortTuple npt : rootPath.getPath()) {
                    if (!npt.getNodeId().equals(spurNode)) {
                        switchesCopy.remove(npt.getNodeId());
                    }
                }

                // Builds the new topology without the parts we want removed
                copyOfLinkDpidMap = buildLinkDpidMap(switchesCopy, portsWithLinks, allLinksCopy);

                // Uses Dijkstra's to try to find a shortest path from the spur node to the destination
                Path spurPath = buildPath(new PathId(spurNode, dst), dijkstra(copyOfLinkDpidMap, dst, linkCost, true));
                if (spurPath == null || spurPath.getPath().isEmpty()) {
                    log.debug("spurPath is null");
                    continue;
                }

                // Adds the root path and spur path together to get a possible shortest path
                List<NodePortTuple> totalNpt = new LinkedList<NodePortTuple>();
                totalNpt.addAll(rootPath.getPath());
                totalNpt.addAll(spurPath.getPath());
                Path totalPath = new Path(new PathId(src, dst), totalNpt);
                setPathCosts(totalPath);

                log.trace("Spur Node: {}", spurNode);
                log.trace("Root Path: {}", rootPath);
                log.trace("Spur Path: {}", spurPath);
                log.trace("Total Path: {}", totalPath);
                // Adds the new path into B
                int flag = 0;
                for (Path r_B : B) {
                    for (Path r_A : A) {
                        if (r_B.getPath().equals(totalPath.getPath()) || r_A.getPath().equals(totalPath.getPath())) {
                            flag = 1;
                        }
                    }
                }
                if (flag == 0) {
                    B.add(totalPath);
                }

                // Restore edges and nodes to graph
            }

            // If we get out of the loop and there isn't a path in B to add to A, all possible paths have been
            // found and return A
            if (B.isEmpty()) {
                //log.debug("B list is empty in Yen's");
                break;
            }

            log.debug("Removing shortest path from {}", B);
            // Find the shortest path in B, remove it, and put it in A
            log.debug("--------------BEFORE------------------------");
            for (Path r : B) {
                log.debug(r.toString());
            }
            log.debug("--------------------------------------------");
            Path shortestPath = removeShortestPath(B, linkCost);
            log.debug("--------------AFTER------------------------");
            for (Path r : B) {
                log.debug(r.toString());
            }
            log.debug("--------------------------------------------");

            if (shortestPath != null) {
                log.debug("Adding new shortest path to {} in Yen's", shortestPath);
                A.add(shortestPath);
                log.debug("A: {}", A);
            }
            else {
                log.debug("removeShortestPath returned {}", shortestPath);
            }
        }

        // Set the route counts
        for (Path path : A) {
            path.setPathIndex(A.indexOf(path));
        }
        //log.debug("END OF YEN'S --------------------");
        return A;
    }

    private Path removeShortestPath(List<Path> routes, Map<Link, Integer> linkCost) {
        log.debug("REMOVE SHORTEST PATH -------------");
        // If there is nothing in B, return
        if(routes == null){
            log.debug("Routes == null");
            return null;
        }
        Path shortestPath = null;
        // Set the default shortest path to the max value
        Integer shortestPathCost = Integer.MAX_VALUE;

        // Iterate through B and find the shortest path
        for (Path r : routes) {
            Integer pathCost = 0;
            // Add up the weights of each link in the path
            // TODO Get the path cost from the route object
            for (NodePortTuple npt : r.getPath()) {
                if (links.get(npt) == null || linkCost.get(links.get(npt).iterator().next()) == null) {
                    pathCost++;
                }
                else {
                    pathCost += linkCost.get(links.get(npt).iterator().next());
                }
            }
            log.debug("Path {} with cost {}", r, pathCost);
            // If it is smaller than the current smallest, replace variables with the path just found
            if (pathCost < shortestPathCost) {
                log.debug("New shortest path {} with cost {}", r, pathCost);
                shortestPathCost = pathCost;
                shortestPath = r;
            }
        }

        log.debug("Remove {} from {}", shortestPath, routes);
        // Remove the route from B and return it
        routes.remove(shortestPath);

        log.debug("Shortest path: {}", shortestPath);
        return shortestPath;
    }

    /**
     * Computes end-to-end path including src/dst switch
     * ports in addition to the switches. This chains into
     * {@link #getPath(DatapathId, DatapathId)} below.
     * @param srcId
     * @param srcPort
     * @param dstId
     * @param dstPort
     * @return
     */
    public Path getPath(DatapathId srcId, OFPort srcPort,
            DatapathId dstId, OFPort dstPort) {
        Path r = getPath(srcId, dstId);
        
        /* Path cannot be null, but empty b/t 2 diff DPIDs -> not found */
        if (! srcId.equals(dstId) && r.getPath().isEmpty()) {
            return r;
        }

        /* Else, path is valid (len=0) on the same DPID or (len>0) diff DPIDs */
        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>(r.getPath());
        NodePortTuple npt = new NodePortTuple(srcId, srcPort);
        nptList.add(0, npt); // add src port to the front
        npt = new NodePortTuple(dstId, dstPort);
        nptList.add(npt); // add dst port to the end

        PathId id = new PathId(srcId, dstId);
        r = new Path(id, nptList);
        return r;
    }


    /**
     * Get the fastest path from the pathcache.
     * @param srcId
     * @param dstId
     * @return
     */
    public Path getPath(DatapathId srcId, DatapathId dstId) {
        PathId id = new PathId(srcId, dstId);

        /* Return empty route if srcId equals dstId */
        if (srcId.equals(dstId)) {
            return new Path(id, ImmutableList.of());
        }

        Path result = null;

        try {
            if (!pathcache.get(id).isEmpty()) {
                result = pathcache.get(id).get(0);
            }
        } catch (Exception e) {
            log.warn("Could not find route from {} to {}. If the path exists, wait for the topology to settle, and it will be detected", srcId, dstId);
        }

        if (log.isTraceEnabled()) {
            log.trace("getPath: {} -> {}", id, result);
        }
        return result == null ? new Path(id, ImmutableList.of()) : result;
    }

    //
    //  ITopologyService interface method helpers.
    //

    public boolean isInternalLinkInCluster(DatapathId switchid, OFPort port) {
        return !isAttachmentPointPort(switchid, port);
    }

    public boolean isAttachmentPointPort(DatapathId switchid, OFPort port) {
        NodePortTuple npt = new NodePortTuple(switchid, port);
        if (linksNonBcastNonTunnel.containsKey(npt)) {
            return false;
        }
        return true;
    }

    public DatapathId getClusterId(DatapathId switchId) {
        Cluster c = clusterFromSwitch.get(switchId);
        if (c != null) { 
            return c.getId();
        }
        return switchId;
    }

    public DatapathId getArchipelagoId(DatapathId switchId) {
        Cluster c = clusterFromSwitch.get(switchId);
        if (c != null) {
            return archipelagoFromCluster.get(c).getId();
        }
        return switchId;
    }

    public Set<DatapathId> getSwitchesInCluster(DatapathId switchId) {
        Cluster c = clusterFromSwitch.get(switchId);
        if (c != null) {
            return c.getNodes();
        }
        /* The switch is not known to topology as there are no links connected to it. */
        return ImmutableSet.of(switchId);
    }

    public boolean isInSameCluster(DatapathId switch1, DatapathId switch2) {
        Cluster c1 = clusterFromSwitch.get(switch1);
        Cluster c2 = clusterFromSwitch.get(switch2);
        if (c1 != null && c2 != null) {
            return c1.getId().equals(c2.getId());
        }
        return (switch1.equals(switch2));
    }

    public boolean isNotBlocked(DatapathId sw, OFPort portId) {
        return !isBlockedPort(new NodePortTuple(sw, portId));
    }

    /*
     * Takes finiteBroadcastTree into account to prevent loops in the network
     */
    public boolean isBroadcastAllowedOnSwitchPort(DatapathId sw, OFPort portId) {
        if (!isEdge(sw, portId)){       
            NodePortTuple npt = new NodePortTuple(sw, portId);
            if (portsBroadcastAll.contains(npt))
                return true;
            else return false;
        }
        return true;
    }

    public boolean isConsistent(DatapathId oldSw, OFPort oldPort, DatapathId newSw, OFPort newPort) {
        if (isInternalLinkInCluster(newSw, newPort)) return true;
        return (oldSw.equals(newSw) && oldPort.equals(newPort));
    }

    public boolean isInSameArchipelago(DatapathId s1, DatapathId s2) {
        for (Archipelago a : archipelagos) {
            if (a.getSwitches().contains(s1) && a.getSwitches().contains(s2)) {
                return true;
            }
        }
        return false;
    }

    public Set<DatapathId> getSwitches() {
        return switches;
    }

    public Set<OFPort> getPortsWithLinks(DatapathId sw) {
        return portsWithLinks.get(sw);
    }

    public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort) {
        Set<OFPort> result = new HashSet<OFPort>();
        DatapathId clusterId = getClusterId(targetSw);
        for (NodePortTuple npt : clusterPorts.get(clusterId)) {
            if (npt.getNodeId().equals(targetSw)) {
                result.add(npt.getPortId());
            }
        }
        return result;
    }

    public Set<Link> getInternalInterClusterLinks() {
        return linksNonExternalInterCluster;
    }

    public Set<NodePortTuple> getAllBroadcastPorts() {
        return portsBroadcastAll;
    }

    public Set<DatapathId> getClusterIdsInArchipelago(DatapathId sw) {
        Archipelago a = getArchipelago(sw);
        if (a != null) {
            return a.getClusters().stream().map(c -> c.getId()).collect(Collectors.toSet());
        }
        return ImmutableSet.of();
    }

    private void addBroadcastPortToPerSwitchMap(NodePortTuple npt) {
        if (portsBroadcastPerSwitch.containsKey(npt.getNodeId())) {
            portsBroadcastPerSwitch.get(npt.getNodeId()).add(npt.getPortId());
        } else {
            portsBroadcastPerSwitch.put(npt.getNodeId(), 
                    new HashSet<OFPort>(Arrays.asList(npt.getPortId())));
        }
    }

    private void computeBroadcastPortsPerArchipelago() {      
        Set<NodePortTuple> s;
        for (Archipelago a : archipelagos) {
            s = new HashSet<NodePortTuple>();
            for (Entry<DatapathId, Link> e : a.getBroadcastTree().getLinks().entrySet()) {
                Link l = e.getValue();
                if (l != null) { /* null indicates root node (leads nowhere "up") */
                    NodePortTuple src = new NodePortTuple(l.getSrc(), l.getSrcPort());
                    NodePortTuple dst = new NodePortTuple(l.getDst(), l.getDstPort());
                    
                    /* Accumulate for per-archipelago NPT map */
                    s.add(src);
                    s.add(dst);

                    /* Add to global NPT set */
                    this.portsBroadcastAll.add(src);
                    this.portsBroadcastAll.add(dst);

                    /* Add to per-switch NPT map */
                    addBroadcastPortToPerSwitchMap(src);
                    addBroadcastPortToPerSwitchMap(dst);
                }
            }

            /* Set accumulated per-this-archipelago NPTs */
            portsBroadcastPerArchipelago.put(a.getId(), s); /* guaranteed non-null set per archipelago */
        }

        /* Add ports that hosts connect to */
        for (DatapathId sw : this.switches) {
            for (OFPort p : this.portsPerSwitch.get(sw)) { /* includes edge and link ports */
                NodePortTuple npt = new NodePortTuple(sw, p);
                if (isEdge(sw, p)) {
                    /* Add to per-archipelago NPT map */
                    DatapathId aId = getArchipelago(sw).getId();
                    if (portsBroadcastPerArchipelago.containsKey(aId)) {
                        portsBroadcastPerArchipelago.get(aId).add(npt);
                    } else {
                        s = new HashSet<NodePortTuple>();
                        s.add(npt);
                        portsBroadcastPerArchipelago.put(aId, s);
                    }

                    /* Add to global NPT set */
                    this.portsBroadcastAll.add(npt);

                    /* Add to per-switch NPT map */
                    addBroadcastPortToPerSwitchMap(npt);
                }
            }
        }
    }

    public Set<NodePortTuple> getBroadcastPortsInArchipelago(DatapathId sw) {
        Archipelago a = getArchipelago(sw);
        if (a != null) {
            return portsBroadcastPerArchipelago.get(a.getId());
        }
        return ImmutableSet.of();
    }
    
    public Set<DatapathId> getArchipelagoIds() {
        return archipelagos.stream().map(a -> a.getId()).collect(Collectors.toSet());
    }
} 