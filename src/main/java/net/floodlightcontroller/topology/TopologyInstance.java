/**
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.util.ClusterDFS;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.util.LRUHashMap;

/**
 * A representation of a network topology.  Used internally by 
 * {@link TopologyManager}
 */
@LogMessageCategory("Network Topology")
public class TopologyInstance {

    public static final short LT_SH_LINK = 1;
    public static final short LT_BD_LINK = 2;
    public static final short LT_TUNNEL  = 3; 

    public static final int MAX_LINK_WEIGHT = 10000;
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    public static final int PATH_CACHE_SIZE = 1000;

    protected static Logger log = LoggerFactory.getLogger(TopologyInstance.class);

    protected Map<Long, Set<Short>> switchPorts; // Set of ports for each switch
    /** Set of switch ports that are marked as blocked.  A set of blocked
     * switch ports may be provided at the time of instantiation. In addition,
     * we may add additional ports to this set.
     */
    protected Set<NodePortTuple> blockedPorts;  
    protected Map<NodePortTuple, Set<Link>> switchPortLinks; // Set of links organized by node port tuple
    /** Set of links that are blocked. */
    protected Set<Link> blockedLinks;

    protected Set<Long> switches;
    protected Set<NodePortTuple> broadcastDomainPorts;
    protected Set<NodePortTuple> tunnelPorts;

    protected Set<Cluster> clusters;  // set of openflow domains
    protected Map<Long, Cluster> switchClusterMap; // switch to OF domain map

    // States for routing
    protected Map<Long, BroadcastTree> destinationRootedTrees;
    protected Map<Long, Set<NodePortTuple>> clusterBroadcastNodePorts;
    protected Map<Long, BroadcastTree> clusterBroadcastTrees;
    protected LRUHashMap<RouteId, Route> pathcache;

    public TopologyInstance() {
        this.switches = new HashSet<Long>();
        this.switchPorts = new HashMap<Long, Set<Short>>();
        this.switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.broadcastDomainPorts = new HashSet<NodePortTuple>();
        this.tunnelPorts = new HashSet<NodePortTuple>();
        this.blockedPorts = new HashSet<NodePortTuple>();
        this.blockedLinks = new HashSet<Link>();
    }

    public TopologyInstance(Map<Long, Set<Short>> switchPorts,
                            Map<NodePortTuple, Set<Link>> switchPortLinks,
                            Set<NodePortTuple> broadcastDomainPorts)
    {
        this.switches = new HashSet<Long>(switchPorts.keySet());
        this.switchPorts = new HashMap<Long, Set<Short>>(switchPorts);
        this.switchPortLinks = new HashMap<NodePortTuple, 
                Set<Link>>(switchPortLinks);
        this.broadcastDomainPorts = new HashSet<NodePortTuple>(broadcastDomainPorts);
        this.tunnelPorts = new HashSet<NodePortTuple>();
        this.blockedPorts = new HashSet<NodePortTuple>();
        this.blockedLinks = new HashSet<Link>();

        clusters = new HashSet<Cluster>();
        switchClusterMap = new HashMap<Long, Cluster>();
    }
    public TopologyInstance(Map<Long, Set<Short>> switchPorts,
                            Set<NodePortTuple> blockedPorts,
                            Map<NodePortTuple, Set<Link>> switchPortLinks,
                            Set<NodePortTuple> broadcastDomainPorts,
                            Set<NodePortTuple> tunnelPorts){

        // copy these structures
        this.switches = new HashSet<Long>(switchPorts.keySet());
        this.switchPorts = new HashMap<Long, Set<Short>>();
        for(long sw: switchPorts.keySet()) {
            this.switchPorts.put(sw, new HashSet<Short>(switchPorts.get(sw)));
        }

        this.blockedPorts = new HashSet<NodePortTuple>(blockedPorts);
        this.switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        for(NodePortTuple npt: switchPortLinks.keySet()) {
            this.switchPortLinks.put(npt, 
                                     new HashSet<Link>(switchPortLinks.get(npt)));
        }
        this.broadcastDomainPorts = new HashSet<NodePortTuple>(broadcastDomainPorts);
        this.tunnelPorts = new HashSet<NodePortTuple>(tunnelPorts);

        blockedLinks = new HashSet<Link>();
        clusters = new HashSet<Cluster>();
        switchClusterMap = new HashMap<Long, Cluster>();
        destinationRootedTrees = new HashMap<Long, BroadcastTree>();
        clusterBroadcastTrees = new HashMap<Long, BroadcastTree>();
        clusterBroadcastNodePorts = new HashMap<Long, Set<NodePortTuple>>();
        pathcache = new LRUHashMap<RouteId, Route>(PATH_CACHE_SIZE);
    }

    public void compute() {

        // Step 1: Compute clusters ignoring broadcast domain links
        // Create nodes for clusters in the higher level topology
        // Must ignore blocked links.
        identifyOpenflowDomains();

        // Step 0: Remove all links connected to blocked ports.
        // removeLinksOnBlockedPorts();


        // Step 1.1: Add links to clusters
        // Avoid adding blocked links to clusters
        addLinksToOpenflowDomains();

        // Step 2. Compute shortest path trees in each cluster for 
        // unicast routing.  The trees are rooted at the destination.
        // Cost for tunnel links and direct links are the same.
        calculateShortestPathTreeInClusters();

        // Step 3. Compute broadcast tree in each cluster.
        // Cost for tunnel links are high to discourage use of 
        // tunnel links.  The cost is set to the number of nodes
        // in the cluster + 1, to use as minimum number of 
        // clusters as possible.
        calculateBroadcastNodePortsInClusters();

        // Step 4. print topology.
        printTopology();
    }

    public void printTopology() {
        if (log.isTraceEnabled()) {
            log.trace("-----------------------------------------------");
            log.trace("Links: {}",this.switchPortLinks);
            log.trace("broadcastDomainPorts: {}", broadcastDomainPorts);
            log.trace("tunnelPorts: {}", tunnelPorts);
            log.trace("clusters: {}", clusters);
            log.trace("destinationRootedTrees: {}", destinationRootedTrees);
            log.trace("clusterBroadcastNodePorts: {}", clusterBroadcastNodePorts);
            log.trace("-----------------------------------------------");
        }
    }

    protected void addLinksToOpenflowDomains() {
        for(long s: switches) {
            if (switchPorts.get(s) == null) continue;
            for (short p: switchPorts.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (switchPortLinks.get(np) == null) continue;
                if (isBroadcastDomainPort(np)) continue;
                for(Link l: switchPortLinks.get(np)) {
                    if (isBlockedLink(l)) continue;
                    if (isBroadcastDomainLink(l)) continue;
                    Cluster c1 = switchClusterMap.get(l.getSrc());
                    Cluster c2 = switchClusterMap.get(l.getDst());
                    if (c1 ==c2) {
                        c1.addLink(l);
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
    @LogMessageDoc(level="ERROR",
            message="No DFS object for switch {} found.",
            explanation="The internal state of the topology module is corrupt",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public void identifyOpenflowDomains() {
        Map<Long, ClusterDFS> dfsList = new HashMap<Long, ClusterDFS>();

        if (switches == null) return;

        for (Long key: switches) {
            ClusterDFS cdfs = new ClusterDFS();
            dfsList.put(key, cdfs);
        }
        Set<Long> currSet = new HashSet<Long>();

        for (Long sw: switches) {
            ClusterDFS cdfs = dfsList.get(sw);
            if (cdfs == null) {
                log.error("No DFS object for switch {} found.", sw);
            }else if (!cdfs.isVisited()) {
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

    private long dfsTraverse (long parentIndex, long currIndex, long currSw,
                              Map<Long, ClusterDFS> dfsList, Set <Long> currSet) {

        //Get the DFS object corresponding to the current switch
        ClusterDFS currDFS = dfsList.get(currSw);
        // Get all the links corresponding to this switch

        Set<Long> nodesInMyCluster = new HashSet<Long>();
        Set<Long> myCurrSet = new HashSet<Long>();

        //Assign the DFS object with right values.
        currDFS.setVisited(true);
        currDFS.setDfsIndex(currIndex);
        currDFS.setParentDFSIndex(parentIndex);
        currIndex++;

        // Traverse the graph through every outgoing link.
        if (switchPorts.get(currSw) != null){
            for(Short p: switchPorts.get(currSw)) {
                Set<Link> lset = switchPortLinks.get(new NodePortTuple(currSw, p));
                if (lset == null) continue;
                for(Link l:lset) {
                    long dstSw = l.getDst();

                    // ignore incoming links.
                    if (dstSw == currSw) continue;

                    // ignore if the destination is already added to 
                    // another cluster
                    if (switchClusterMap.get(dstSw) != null) continue;

                    // ignore the link if it is blocked.
                    if (isBlockedLink(l)) continue;

                    // ignore this link if it is in broadcast domain
                    if (isBroadcastDomainLink(l)) continue;

                    // Get the DFS object corresponding to the dstSw
                    ClusterDFS dstDFS = dfsList.get(dstSw);

                    if (dstDFS.getDfsIndex() < currDFS.getDfsIndex()) {
                        // could be a potential lowpoint
                        if (dstDFS.getDfsIndex() < currDFS.getLowpoint())
                            currDFS.setLowpoint(dstDFS.getDfsIndex());

                    } else if (!dstDFS.isVisited()) {
                        // make a DFS visit
                        currIndex = dfsTraverse(currDFS.getDfsIndex(), currIndex, dstSw,
                                                dfsList, myCurrSet);

                        if (currIndex < 0) return -1;

                        // update lowpoint after the visit
                        if (dstDFS.getLowpoint() < currDFS.getLowpoint())
                            currDFS.setLowpoint(dstDFS.getLowpoint());

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
            for(long sw: currSet){
                sc.add(sw);
                switchClusterMap.put(sw, sc);
            }
            // delete all the nodes in the current set.
            currSet.clear();
            // add the newly formed switch clusters to the cluster set.
            clusters.add(sc);
        }

        return currIndex;
    }

    /**
     *  Go through every link and identify it is a blocked link or not.
     *  If blocked, remove it from the switchport links and put them in the
     *  blocked link category.
     *
     *  Note that we do not update the tunnel ports and broadcast domain 
     *  port structures.  We need those to still answer the question if the
     *  ports are tunnel or broadcast domain ports.
     *
     *  If we add additional ports to blocked ports later on, we may simply
     *  call this method again to remove the links on the newly blocked ports.
     */
    protected void removeLinksOnBlockedPorts() {
        Iterator<NodePortTuple> nptIter;
        Iterator<Link> linkIter;

        // Iterate through all the links and all the switch ports
        // and move the links on blocked switch ports to blocked links
        nptIter = this.switchPortLinks.keySet().iterator();
        while (nptIter.hasNext()) {
            NodePortTuple npt = nptIter.next();
            linkIter = switchPortLinks.get(npt).iterator();
            while (linkIter.hasNext()) {
                Link link = linkIter.next();
                if (isBlockedLink(link)) {
                    this.blockedLinks.add(link);
                    linkIter.remove();
                }
            }
            // Note that at this point, the switchport may have
            // no links in it.  We could delete the switch port, 
            // but we will leave it as is.
        }
    }

    public Set<NodePortTuple> getBlockedPorts() {
        return this.blockedPorts;
    }

    protected Set<Link> getBlockedLinks() {
        return this.blockedLinks;
    }

    /** Returns true if a link has either one of its switch ports 
     * blocked.
     * @param l
     * @return
     */
    protected boolean isBlockedLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (isBlockedPort(n1) || isBlockedPort(n2));
    }

    protected boolean isBlockedPort(NodePortTuple npt) {
        return blockedPorts.contains(npt);
    }

    protected boolean isTunnelPort(NodePortTuple npt) {
        return tunnelPorts.contains(npt);
    }

    protected boolean isTunnelLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (isTunnelPort(n1) || isTunnelPort(n2));
    }

    public boolean isBroadcastDomainLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (isBroadcastDomainPort(n1) || isBroadcastDomainPort(n2));
    }

    public boolean isBroadcastDomainPort(NodePortTuple npt) {
        return broadcastDomainPorts.contains(npt);
    }

    protected class NodeDist implements Comparable<NodeDist> {
        private Long node;
        public Long getNode() {
            return node;
        }

        private int dist; 
        public int getDist() {
            return dist;
        }

        public NodeDist(Long node, int dist) {
            this.node = node;
            this.dist = dist;
        }

        public int compareTo(NodeDist o) {
            if (o.dist == this.dist) {
                return (int)(this.node - o.node);
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

    protected BroadcastTree dijkstra(Cluster c, Long root, 
                                     Map<Link, Integer> linkCost,
                                     boolean isDstRooted) {
        HashMap<Long, Link> nexthoplinks = new HashMap<Long, Link>();
        //HashMap<Long, Long> nexthopnodes = new HashMap<Long, Long>();
        HashMap<Long, Integer> cost = new HashMap<Long, Integer>();
        int w;

        for (Long node: c.links.keySet()) {
            nexthoplinks.put(node, null);
            //nexthopnodes.put(node, null);
            cost.put(node, MAX_PATH_WEIGHT);
        }

        HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
        PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
        nodeq.add(new NodeDist(root, 0));
        cost.put(root, 0);
        while (nodeq.peek() != null) {
            NodeDist n = nodeq.poll();
            Long cnode = n.getNode();
            int cdist = n.getDist();
            if (cdist >= MAX_PATH_WEIGHT) break;
            if (seen.containsKey(cnode)) continue;
            seen.put(cnode, true);

            for (Link link: c.links.get(cnode)) {
                Long neighbor;

                if (isDstRooted == true) neighbor = link.getSrc();
                else neighbor = link.getDst();

                // links directed toward cnode will result in this condition
                if (neighbor.equals(cnode)) continue;

                if (seen.containsKey(neighbor)) continue;

                if (linkCost == null || linkCost.get(link)==null) w = 1;
                else w = linkCost.get(link);

                int ndist = cdist + w; // the weight of the link, always 1 in current version of floodlight.
                if (ndist < cost.get(neighbor)) {
                    cost.put(neighbor, ndist);
                    nexthoplinks.put(neighbor, link);
                    //nexthopnodes.put(neighbor, cnode);
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

    protected void calculateShortestPathTreeInClusters() {
        pathcache.clear();
        destinationRootedTrees.clear();

        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = switchPorts.size() + 1;

        for(NodePortTuple npt: tunnelPorts) {
            if (switchPortLinks.get(npt) == null) continue;
            for(Link link: switchPortLinks.get(npt)) {
                if (link == null) continue;
                linkCost.put(link, tunnel_weight);
            }
        }

        for(Cluster c: clusters) {
            for (Long node : c.links.keySet()) {
                BroadcastTree tree = dijkstra(c, node, linkCost, true);
                destinationRootedTrees.put(node, tree);
            }
        }
    }

    protected void calculateBroadcastTreeInClusters() {
        for(Cluster c: clusters) {
            // c.id is the smallest node that's in the cluster
            BroadcastTree tree = destinationRootedTrees.get(c.id);
            clusterBroadcastTrees.put(c.id, tree);
        }
    }

    protected void calculateBroadcastNodePortsInClusters() {

        clusterBroadcastTrees.clear();

        calculateBroadcastTreeInClusters();

        for(Cluster c: clusters) {
            // c.id is the smallest node that's in the cluster
            BroadcastTree tree = clusterBroadcastTrees.get(c.id);
            //log.info("Broadcast Tree {}", tree);

            Set<NodePortTuple> nptSet = new HashSet<NodePortTuple>();
            Map<Long, Link> links = tree.getLinks();
            if (links == null) continue;
            for(long nodeId: links.keySet()) {
                Link l = links.get(nodeId);
                if (l == null) continue;
                NodePortTuple npt1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
                NodePortTuple npt2 = new NodePortTuple(l.getDst(), l.getDstPort());
                nptSet.add(npt1);
                nptSet.add(npt2);
            }
            clusterBroadcastNodePorts.put(c.id, nptSet);
        }
    }

    protected Route buildroute(RouteId id, long srcId, long dstId) {
        NodePortTuple npt;

        LinkedList<NodePortTuple> switchPorts =
                new LinkedList<NodePortTuple>();

        if (destinationRootedTrees == null) return null;
        if (destinationRootedTrees.get(dstId) == null) return null;

        Map<Long, Link> nexthoplinks =
                destinationRootedTrees.get(dstId).getLinks();

        if (!switches.contains(srcId) || !switches.contains(dstId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not
            // in the network)
            log.debug("buildroute: Standalone switch: {}", srcId);

            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []

        } else if ((nexthoplinks!=null) && (nexthoplinks.get(srcId)!=null)) {
            while (srcId != dstId) {
                Link l = nexthoplinks.get(srcId);

                npt = new NodePortTuple(l.getSrc(), l.getSrcPort());
                switchPorts.addLast(npt);
                npt = new NodePortTuple(l.getDst(), l.getDstPort());
                switchPorts.addLast(npt);
                srcId = nexthoplinks.get(srcId).getDst();
            }
        }
        // else, no path exists, and path equals null

        Route result = null;
        if (switchPorts != null && !switchPorts.isEmpty()) 
            result = new Route(id, switchPorts);
        if (log.isTraceEnabled()) {
            log.trace("buildroute: {}", result);
        }
        return result;
    }

    protected int getCost(long srcId, long dstId) {
        BroadcastTree bt = destinationRootedTrees.get(dstId);
        if (bt == null) return -1;
        return (bt.getCost(srcId));
    }

    /* 
     * Getter Functions
     */

    protected Set<Cluster> getClusters() {
        return clusters;
    }

    // IRoutingEngineService interfaces
    protected boolean routeExists(long srcId, long dstId) {
        BroadcastTree bt = destinationRootedTrees.get(dstId);
        if (bt == null) return false;
        Link link = bt.getLinks().get(srcId);
        if (link == null) return false;
        return true;
    }

    protected Route getRoute(long srcId, short srcPort,
                             long dstId, short dstPort, long cookie) {


        // Return null the route source and desitnation are the
        // same switchports.
        if (srcId == dstId && srcPort == dstPort)
            return null;

        List<NodePortTuple> nptList;
        NodePortTuple npt;
        Route r = getRoute(srcId, dstId, 0);
        if (r == null && srcId != dstId) return null;

        if (r != null) {
            nptList= new ArrayList<NodePortTuple>(r.getPath());
        } else {
            nptList = new ArrayList<NodePortTuple>();
        }
        npt = new NodePortTuple(srcId, srcPort);
        nptList.add(0, npt); // add src port to the front
        npt = new NodePortTuple(dstId, dstPort);
        nptList.add(npt); // add dst port to the end

        RouteId id = new RouteId(srcId, dstId);
        r = new Route(id, nptList);
        return r;
    }

    protected Route getRoute(long srcId, long dstId, long cookie) {
        RouteId id = new RouteId(srcId, dstId);
        Route result = null;
        if (pathcache.containsKey(id)) {
            result = pathcache.get(id);
        } else {
            result = buildroute(id, srcId, dstId);
            pathcache.put(id, result);
        }
        if (log.isTraceEnabled()) {
            log.trace("getRoute: {} -> {}", id, result);
        }
        return result;
    }

    protected BroadcastTree getBroadcastTreeForCluster(long clusterId){
        Cluster c = switchClusterMap.get(clusterId);
        if (c == null) return null;
        return clusterBroadcastTrees.get(c.id);
    }

    // 
    //  ITopologyService interface method helpers.
    // 

    protected boolean isInternalToOpenflowDomain(long switchid, short port) {
        return !isAttachmentPointPort(switchid, port);
    }

    public boolean isAttachmentPointPort(long switchid, short port) {
        NodePortTuple npt = new NodePortTuple(switchid, port);
        if (switchPortLinks.containsKey(npt)) return false;
        return true;
    }

    protected long getOpenflowDomainId(long switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) return switchId;
        return c.getId();
    }

    protected long getL2DomainId(long switchId) {
        return getOpenflowDomainId(switchId);
    }

    protected Set<Long> getSwitchesInOpenflowDomain(long switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) {
            // The switch is not known to topology as there
            // are no links connected to it.
            Set<Long> nodes = new HashSet<Long>();
            nodes.add(switchId);
            return nodes;
        }
        return (c.getNodes());
    }

    protected boolean inSameOpenflowDomain(long switch1, long switch2) {
        Cluster c1 = switchClusterMap.get(switch1);
        Cluster c2 = switchClusterMap.get(switch2);
        if (c1 != null && c2 != null)
            return (c1.getId() == c2.getId());
        return (switch1 == switch2);
    }

    public boolean isAllowed(long sw, short portId) {
        return true;
    }

    protected boolean
    isIncomingBroadcastAllowedOnSwitchPort(long sw, short portId) {
        if (isInternalToOpenflowDomain(sw, portId)) {
            long clusterId = getOpenflowDomainId(sw);
            NodePortTuple npt = new NodePortTuple(sw, portId);
            if (clusterBroadcastNodePorts.get(clusterId).contains(npt))
                return true;
            else return false;
        }
        return true;
    }

    public boolean isConsistent(long oldSw, short oldPort, long newSw,
                                short newPort) {
        if (isInternalToOpenflowDomain(newSw, newPort)) return true;
        return (oldSw == newSw && oldPort == newPort);
    }

    protected Set<NodePortTuple>
    getBroadcastNodePortsInCluster(long sw) {
        long clusterId = getOpenflowDomainId(sw);
        return clusterBroadcastNodePorts.get(clusterId);
    }

    public boolean inSameBroadcastDomain(long s1, short p1, long s2, short p2) {
        return false;
    }

    public boolean inSameL2Domain(long switch1, long switch2) {
        return inSameOpenflowDomain(switch1, switch2);
    }

    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        // Use this function to redirect traffic if needed.
        return new NodePortTuple(dst, dstPort);
    }

    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        // Use this function to reinject traffic from a 
        // different port if needed.
        return new NodePortTuple(src, srcPort);
    }

    public Set<Long> getSwitches() {
        return switches;
    }

    public Set<Short> getPortsWithLinks(long sw) {
        return switchPorts.get(sw);
    }

    public Set<Short> getBroadcastPorts(long targetSw, long src, short srcPort) {
        Set<Short> result = new HashSet<Short>();
        long clusterId = getOpenflowDomainId(targetSw);
        for(NodePortTuple npt: clusterBroadcastNodePorts.get(clusterId)) {
            if (npt.getNodeId() == targetSw) {
                result.add(npt.getPortId());
            }
        }
        return result;
    }

    public NodePortTuple
    getAllowedOutgoingBroadcastPort(long src, short srcPort, long dst,
                                    short dstPort) {
        // TODO Auto-generated method stub
        return null;
    }

    public NodePortTuple
    getAllowedIncomingBroadcastPort(long src, short srcPort) {
        // TODO Auto-generated method stub
        return null;
    }
}

