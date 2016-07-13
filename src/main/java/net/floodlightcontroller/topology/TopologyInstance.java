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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.util.ClusterDFS;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A representation of a network topology.  Used internally by
 * {@link TopologyManager}
 */
public class TopologyInstance {

    public static final short LT_SH_LINK = 1;
    public static final short LT_BD_LINK = 2;
    public static final short LT_TUNNEL  = 3;

    public static final int MAX_LINK_WEIGHT = 10000;
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    public static final int PATH_CACHE_SIZE = 1000;

    protected static Logger log = LoggerFactory.getLogger(TopologyInstance.class);

    protected Map<DatapathId, Set<OFPort>> switchPorts; // Set of ports for each switch
    /** Set of switch ports that are marked as blocked.  A set of blocked
     * switch ports may be provided at the time of instantiation. In addition,
     * we may add additional ports to this set.
     */
    protected Set<NodePortTuple> blockedPorts;
    protected Map<NodePortTuple, Set<Link>> switchPortLinks; // Set of links organized by node port tuple
    /** Set of links that are blocked. */
    protected Set<Link> blockedLinks;
  
    protected Set<DatapathId> switches;
    protected Set<NodePortTuple> broadcastDomainPorts;
    protected Set<NodePortTuple> tunnelPorts;

    protected Set<Cluster> clusters;  // set of openflow domains
    protected Map<DatapathId, Cluster> switchClusterMap; // switch to OF domain map

    // States for routing
    protected Map<DatapathId, BroadcastTree> destinationRootedTrees;
  
    protected Map<DatapathId, Set<NodePortTuple>> clusterPorts;
    protected Map<DatapathId, BroadcastTree> clusterBroadcastTrees;
 
    protected Map<DatapathId, Set<NodePortTuple>> clusterBroadcastNodePorts;
	//Broadcast tree over whole topology which may be consisted of multiple clusters
    protected BroadcastTree finiteBroadcastTree;
	//Set of NodePortTuples of the finiteBroadcastTree
    protected Set<NodePortTuple> broadcastNodePorts;  
	//destinationRootedTrees over whole topology (not only intra-cluster tree)
    protected Map<DatapathId, BroadcastTree> destinationRootedFullTrees;
	//Set of all links organized by node port tuple. Note that switchPortLinks does not contain all links of multi-cluster topology.
    protected Map<NodePortTuple, Set<Link>> allLinks;
	//Set of all ports organized by DatapathId. Note that switchPorts map contains only ports with links.
	protected Map<DatapathId, Set<OFPort>> allPorts;
    //Set of all the inter-island or "external" links. Also known as portBroadcastDomainLinks in TopologyManager.
    protected Map<NodePortTuple, Set<Link>> externalLinks;
	// Maps broadcast ports to DatapathId
    protected Map<DatapathId, Set<OFPort>> broadcastPortMap;

    protected Set<Archipelago> archipelagos;

    protected class PathCacheLoader extends CacheLoader<RouteId, Route> {
        TopologyInstance ti;
        PathCacheLoader(TopologyInstance ti) {
            this.ti = ti;
        }

        @Override
        public Route load(RouteId rid) {
            return ti.buildroute(rid);
        }
    }

    // Path cache loader is defined for loading a path when it not present
    // in the cache.
    private final PathCacheLoader pathCacheLoader = new PathCacheLoader(this);
    protected LoadingCache<RouteId, Route> pathcache;

    // routecache contains n (specified in floodlightdefault.properties) routes
    // in order between every switch. Calculated using Yen's algorithm.
    protected Map<RouteId, ArrayList<Route>> routecache;
    protected static volatile int maximumRouteEntriesStored = 10;

    public TopologyInstance(Map<DatapathId, Set<OFPort>> switchPorts,
                            Set<NodePortTuple> blockedPorts,
                            Map<NodePortTuple, Set<Link>> switchPortLinks,
                            Set<NodePortTuple> broadcastDomainPorts,
                            Set<NodePortTuple> tunnelPorts, 
                            Map<NodePortTuple, Set<Link>> allLinks,
                            Map<DatapathId, Set<OFPort>> allPorts,
                            Map<NodePortTuple, Set<Link>> externalLinks) {
	
        this.switches = new HashSet<DatapathId>(switchPorts.keySet());
        this.switchPorts = new HashMap<DatapathId, Set<OFPort>>();
        for (DatapathId sw : switchPorts.keySet()) {
            this.switchPorts.put(sw, new HashSet<OFPort>(switchPorts.get(sw)));
        }
		
		this.allPorts = new HashMap<DatapathId, Set<OFPort>>();
		for (DatapathId sw : allPorts.keySet()) {
            this.allPorts.put(sw, new HashSet<OFPort>(allPorts.get(sw)));
        }

        this.blockedPorts = new HashSet<NodePortTuple>(blockedPorts);
        this.switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        for (NodePortTuple npt : switchPortLinks.keySet()) {
            this.switchPortLinks.put(npt, new HashSet<Link>(switchPortLinks.get(npt)));
        }
        
		this.allLinks = new HashMap<NodePortTuple, Set<Link>>();
        for (NodePortTuple npt : allLinks.keySet()) {
            this.allLinks.put(npt, new HashSet<Link>(allLinks.get(npt)));
        }

        this.externalLinks = new HashMap<NodePortTuple, Set<Link>>();
        for (NodePortTuple npt : externalLinks.keySet()) {
            this.externalLinks.put(npt, new HashSet<Link>(externalLinks.get(npt)));
        }

        this.archipelagos = new HashSet<Archipelago>();
        
        this.broadcastDomainPorts = new HashSet<NodePortTuple>(broadcastDomainPorts);
        this.tunnelPorts = new HashSet<NodePortTuple>(tunnelPorts);

        this.blockedLinks = new HashSet<Link>();
       
        this.clusters = new HashSet<Cluster>();
        this.switchClusterMap = new HashMap<DatapathId, Cluster>();
        this.destinationRootedTrees = new HashMap<DatapathId, BroadcastTree>();
        this.destinationRootedFullTrees= new HashMap<DatapathId, BroadcastTree>();
		this.broadcastNodePorts= new HashSet<NodePortTuple>();
		this.broadcastPortMap = new HashMap<DatapathId,Set<OFPort>>();
        this.clusterBroadcastTrees = new HashMap<DatapathId, BroadcastTree>();
        this.clusterBroadcastNodePorts = new HashMap<DatapathId, Set<NodePortTuple>>();

        pathcache = CacheBuilder.newBuilder().concurrencyLevel(4)
                    .maximumSize(1000L)
                    .build(
                            new CacheLoader<RouteId, Route>() {
                                public Route load(RouteId rid) {
                                    return pathCacheLoader.load(rid);
                                }
                            });

        this.routecache = new HashMap<RouteId, ArrayList<Route>>();

    }
	
    public void compute() {
        // Step 1: Compute clusters ignoring broadcast domain links
        // Create nodes for clusters in the higher level topology
        // Must ignore blocked links.
        identifyOpenflowDomains();

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
        
        // Step 4. Compute e2e shortest path trees on entire topology for unicast routing.
		// The trees are rooted at the destination.
        // Cost for tunnel links and direct links are the same.
		calculateAllShortestPaths();

        // Step 4.5 YENSSSSS
        calculateAllOrderedRoutes();

        // Compute the archipelagos (def: cluster of islands). An archipelago will
        // simply be a group of connected islands. Each archipelago will have its own
        // finiteBroadcastTree which will be randomly chosen.
        calculateArchipelagos();
		
		// Step 5. Compute broadcast tree for the whole topology (needed to avoid loops).
        // Cost for tunnel links are high to discourage use of
        // tunnel links.  The cost is set to the number of nodes
        // in the cluster + 1, to use as minimum number of
        // clusters as possible.
        calculateAllBroadcastNodePorts();

		// Step 6. Compute set of ports for broadcasting. Edge ports are included.
       	calculateBroadcastPortMap();
       	
        // Step 7. print topology.
        printTopology();
    }

	/*
	 * Checks if OF port is edge port
	 */
    public boolean isEdge(DatapathId sw, OFPort portId) { 
		NodePortTuple np = new NodePortTuple(sw, portId);
		if (allLinks.get(np) == null || allLinks.get(np).isEmpty()) {
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
    	if (!broadcastPortMap.containsKey(sw) || broadcastPortMap.get(sw) == null) {
    		log.debug("Could not locate broadcast ports for switch {}", sw);
    		return Collections.emptySet();
    	} else {
    		if (log.isDebugEnabled()) {
    			log.debug("Found broadcast ports {} for switch {}", broadcastPortMap.get(sw), sw);
    		}
    		return broadcastPortMap.get(sw);
    	}
    }

    public void printTopology() {
        log.debug("-----------------Topology-----------------------");
        log.debug("All Links: {}", allLinks);
		log.debug("Cluser Broadcast Trees: {}", clusterBroadcastTrees);
        log.debug("Cluster Ports: {}", clusterPorts);
        log.debug("Tunnel Ports: {}", tunnelPorts);
        log.debug("Clusters: {}", clusters);
        log.debug("Destination Rooted Full Trees: {}", destinationRootedFullTrees);
        log.debug("Cluser Broadcast Node Ports: {}", clusterBroadcastNodePorts);
        log.debug("Broadcast Ports Per Node (!!): {}", broadcastPortMap);
        log.debug("Broadcast Domain Ports: {}", broadcastDomainPorts);
        log.debug("Broadcast Node Ports: {}", broadcastDomainPorts);
        log.debug("Archipelagos: {}", archipelagos);
        log.debug("-----------------------------------------------");  
    }

    protected void addLinksToOpenflowDomains() {
        for(DatapathId s: switches) {
            if (switchPorts.get(s) == null) continue;
            for (OFPort p: switchPorts.get(s)) {
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
    public void identifyOpenflowDomains() {
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
        if (switchPorts.get(currSw) != null){
            for (OFPort p : switchPorts.get(currSw)) {
                Set<Link> lset = switchPortLinks.get(new NodePortTuple(currSw, p));
                if (lset == null) continue;
                for (Link l : lset) {
                    DatapathId dstSw = l.getDst();

                    // ignore incoming links.
                    if (dstSw.equals(currSw)) continue;

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
                switchClusterMap.put(sw, sc);
            }
            // delete all the nodes in the current set.
            currSet.clear();
            // add the newly formed switch clusters to the cluster set.
            clusters.add(sc);
        }

        return currIndex;
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

	//calculates the broadcast tree in cluster. Old version of code.
    protected BroadcastTree clusterDijkstra(Cluster c, DatapathId root,
                                     Map<Link, Integer> linkCost,
                                     boolean isDstRooted) {
    	
        HashMap<DatapathId, Link> nexthoplinks = new HashMap<DatapathId, Link>();
        HashMap<DatapathId, Integer> cost = new HashMap<DatapathId, Integer>();
        int w;

        for (DatapathId node : c.links.keySet()) {
            nexthoplinks.put(node, null);
            cost.put(node, MAX_PATH_WEIGHT);
        }

        HashMap<DatapathId, Boolean> seen = new HashMap<DatapathId, Boolean>();
        PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
        
        nodeq.add(new NodeDist(root, 0));
        cost.put(root, 0);
        
        while (nodeq.peek() != null) {
            NodeDist n = nodeq.poll();
            DatapathId cnode = n.getNode();
            
            int cdist = n.getDist();
            if (cdist >= MAX_PATH_WEIGHT) break;
            if (seen.containsKey(cnode)) continue;
            
            seen.put(cnode, true);

            for (Link link : c.links.get(cnode)) {
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

    private void calculateArchipelagos() {
        // Iterate through each external link and create/merge archipelagos based on the
        // islands that each link is connected to
        Cluster srcCluster = null;
        Cluster dstCluster = null;
        Archipelago srcArchipelago = null;
        Archipelago dstArchipelago = null;
        Set<Link> links = new HashSet<Link>();

        for (Set<Link> linkset : externalLinks.values()) {
            links.addAll(linkset);
        }
        
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
                }

                // If neither were found in an existing, then form a new archipelago.
                else if (srcArchipelago == null && dstArchipelago == null) {
                    archipelagos.add(new Archipelago().add(srcCluster).add(dstCluster));
                }

                // If only one is found in an existing, then add the one not found to the existing.
                else if (srcArchipelago != null && dstArchipelago == null) {
                    srcArchipelago.add(dstCluster);
                }

                else if (srcArchipelago == null && dstArchipelago != null) {
                    dstArchipelago.add(srcCluster);
                }

                srcCluster = null;
                dstCluster = null;
                srcArchipelago = null;
                dstArchipelago = null;
            }
        }
        
        // Choose a broadcast tree for each archipelago
        for (Archipelago a : archipelagos) {
            for (DatapathId id : destinationRootedFullTrees.keySet()) {
                if (a.isMember(id)) {
                    a.setBroadcastTree(destinationRootedFullTrees.get(id));
                    break;
                }
            }
        }
    }
    
	/*
	 * Dijkstra that calculates destination rooted trees over the entire topology.
	*/
    protected BroadcastTree dijkstra(Map<DatapathId, Set<Link>> links, DatapathId root,
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
                //log.debug("Neighbor: {}", neighbor);
                //log.debug("Cost: {}", cost.get(neighbor));
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
	 *
	 *
	 */

    protected Map<Link,Integer> initLinkCostMap() {
        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        long rawLinkSpeed;
        int linkSpeedMBps;
        int tunnel_weight = switchPorts.size() + 1;

    		/* routeMetrics:
        	 *  1: Hop Count(Default Metrics)
         	 *  2: Hop Count (Treat Tunnels same as link)
         	 *  3: Latency
         	 *  4: Link Speed (Needs to be tested)
         	*/
        switch (TopologyManager.getRouteMetricInternal()){
            case HOPCOUNT_AVOID_TUNNELS:
                if(TopologyManager.collectStatistics == true){
                    TopologyManager.statisticsService.collectStatistics(false);
                    TopologyManager.collectStatistics = false;
                }
                log.info("Using Default: Hop Count with Tunnel Bias for Metrics");
                for (NodePortTuple npt : tunnelPorts) {
                    if (allLinks.get(npt) == null) continue;
                    for (Link link : allLinks.get(npt)) {
                        if (link == null) continue;
                        linkCost.put(link, tunnel_weight);
                    }
                }
                return linkCost;

            case HOPCOUNT:
                if(TopologyManager.collectStatistics == true){
                    TopologyManager.statisticsService.collectStatistics(false);
                    TopologyManager.collectStatistics = false;
                }
                log.info("Using Hop Count without Tunnel Bias for Metrics");
                for (NodePortTuple npt : allLinks.keySet()) {
                    if (allLinks.get(npt) == null) continue;
                    for (Link link : allLinks.get(npt)) {
                        if (link == null) continue;
                        linkCost.put(link,1);
                    }
                }
                return linkCost;

            case LATENCY:
                if(TopologyManager.collectStatistics == true){
                    TopologyManager.statisticsService.collectStatistics(false);
                    TopologyManager.collectStatistics = false;
                }
                log.info("Using Latency for Route Metrics");
                for (NodePortTuple npt : allLinks.keySet()) {
                    if (allLinks.get(npt) == null) continue;
                    for (Link link : allLinks.get(npt)) {
                        if (link == null) continue;
                        if((int)link.getLatency().getValue() < 0 || (int)link.getLatency().getValue() > MAX_LINK_WEIGHT)
                            linkCost.put(link, MAX_LINK_WEIGHT);
                        else
                            linkCost.put(link,(int)link.getLatency().getValue());
                    }
                }
                return linkCost;

            case LINK_SPEED:
                if(TopologyManager.collectStatistics == false){
                    TopologyManager.statisticsService.collectStatistics(true);
                    TopologyManager.collectStatistics = true;
                }
                log.info("Using Link Speed for Route Metrics");
                for (NodePortTuple npt : allLinks.keySet()) {
                    if (allLinks.get(npt) == null) continue;
                    rawLinkSpeed = TopologyManager.statisticsService.getLinkSpeed(npt);
                    for (Link link : allLinks.get(npt)) {
                        if (link == null) continue;

                        if((rawLinkSpeed / 10^6) / 8 > 1){
                            linkSpeedMBps = (int)(rawLinkSpeed / 10^6) / 8;
                            linkCost.put(link, (1/linkSpeedMBps)*1000);
                        }
                        else
                            linkCost.put(link, MAX_LINK_WEIGHT);
                    }
                }
                return linkCost;

            default:
                if(TopologyManager.collectStatistics == true){
                    TopologyManager.statisticsService.collectStatistics(false);
                    TopologyManager.collectStatistics = false;
                }
                log.info("Invalid Selection: Using Default Hop Count with Tunnel Bias for Metrics");
                for (NodePortTuple npt : tunnelPorts) {
                    if (allLinks.get(npt) == null) continue;
                    for (Link link : allLinks.get(npt)) {
                        if (link == null) continue;
                        linkCost.put(link, tunnel_weight);
                    }
                }
                return linkCost;
        }
    }
	
    /*
	 * Modification of the calculateShortestPathTreeInClusters (dealing with whole topology, not individual clusters)
	 */
    public void calculateAllShortestPaths() {
    	this.broadcastNodePorts.clear();
    	this.destinationRootedFullTrees.clear();
    	Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = switchPorts.size() + 1;
		
        for (NodePortTuple npt : tunnelPorts) {
            if (allLinks.get(npt) == null) continue;
            for (Link link : allLinks.get(npt)) {
                if (link == null) continue;
                linkCost.put(link, tunnel_weight);
            }
        }
        
        Map<DatapathId, Set<Link>> linkDpidMap = new HashMap<DatapathId, Set<Link>>();
        for (DatapathId s : switches) {
            if (switchPorts.get(s) == null) continue;
            for (OFPort p : switchPorts.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (allLinks.get(np) == null) continue;
                for (Link l : allLinks.get(np)) {
                	if (linkDpidMap.containsKey(s)) {
                		linkDpidMap.get(s).add(l);
                	}
                	else {
                		linkDpidMap.put(s, new HashSet<Link>(Arrays.asList(l)));
                	}
                }
            }
        }   
        
        for (DatapathId node : linkDpidMap.keySet()) {
        	BroadcastTree tree = dijkstra(linkDpidMap, node, linkCost, true);
            destinationRootedFullTrees.put(node, tree);
        }
        
		//finiteBroadcastTree is randomly chosen in this implementation
//        if (this.destinationRootedFullTrees.size() > 0) {
//			this.finiteBroadcastTree = destinationRootedFullTrees.values().iterator().next();
//        }
    }

    /*
    Calculates and stores n possible routes (specified in floodlightdefault.properties) using Yen's algorithm,
    looping through every switch.
    These lists of routes are stored in routecache.
    */
    protected void calculateAllOrderedRoutes() {
        ArrayList<Route> routes;
        RouteId routeId;
        routecache.clear();

        for (DatapathId src : switches) {
            for (DatapathId dst : switches) {
                routes = getRoutes(src, dst, maximumRouteEntriesStored);
                routeId = new RouteId(src, dst);
                routecache.put(routeId, routes);
            }
        }
    }

    protected void calculateShortestPathTreeInClusters() {
        pathcache.invalidateAll();
        destinationRootedTrees.clear();

        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = switchPorts.size() + 1;

        for (NodePortTuple npt : tunnelPorts) {
            if (switchPortLinks.get(npt) == null) continue;
            for (Link link : switchPortLinks.get(npt)) {
                if (link == null) continue;
                linkCost.put(link, tunnel_weight);
            }
        }

        for (Cluster c : clusters) {
            for (DatapathId node : c.links.keySet()) {
                BroadcastTree tree = clusterDijkstra(c, node, linkCost, true);
                destinationRootedTrees.put(node, tree);
            }
        }
    }

    protected void calculateBroadcastTreeInClusters() {
        for (Cluster c : clusters) {
            // c.id is the smallest node that's in the cluster
            BroadcastTree tree = destinationRootedTrees.get(c.id);
            clusterBroadcastTrees.put(c.id, tree);
        }
    }
    
	protected Set<NodePortTuple> getAllBroadcastNodePorts() {
		return this.broadcastNodePorts;
	}

    protected void calculateAllBroadcastNodePorts() {
        if (this.destinationRootedFullTrees.size() > 0) {
            //this.finiteBroadcastTree = destinationRootedFullTrees.values().iterator().next();
            for (Archipelago a : archipelagos) {
                Map<DatapathId, Link> links = a.getBroadcastTree().getLinks();
                if (links == null) return;
                for (DatapathId nodeId : links.keySet()) {
                    Link l = links.get(nodeId);
                    if (l == null) continue;
                    NodePortTuple npt1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
                    NodePortTuple npt2 = new NodePortTuple(l.getDst(), l.getDstPort());
                    this.broadcastNodePorts.add(npt1);
                    this.broadcastNodePorts.add(npt2);
                }
            }
        }
    }

    protected void calculateBroadcastPortMap(){
		this.broadcastPortMap.clear();

		for (DatapathId sw : this.switches) {
			for (OFPort p : this.allPorts.get(sw)){
				NodePortTuple npt = new NodePortTuple(sw, p);
				if (isEdge(sw, p) || broadcastNodePorts.contains(npt)) { 
					if (broadcastPortMap.containsKey(sw)) {
                		broadcastPortMap.get(sw).add(p);
                	} else {
                		broadcastPortMap.put(sw, new HashSet<OFPort>(Arrays.asList(p)));
                	}
				}      		
			}
		}
    }
	
    protected void calculateBroadcastNodePortsInClusters() {
        clusterBroadcastTrees.clear();
        
        calculateBroadcastTreeInClusters();

        for (Cluster c : clusters) {
            // c.id is the smallest node that's in the cluster
            BroadcastTree tree = clusterBroadcastTrees.get(c.id);
            //log.info("Broadcast Tree {}", tree);

            Set<NodePortTuple> nptSet = new HashSet<NodePortTuple>();
            Map<DatapathId, Link> links = tree.getLinks();
            if (links == null) continue;
            for (DatapathId nodeId : links.keySet()) {
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

    protected Route buildroute(RouteId id) {
        NodePortTuple npt;
        DatapathId srcId = id.getSrc();
        DatapathId dstId = id.getDst();
		//set of NodePortTuples on the route
        LinkedList<NodePortTuple> sPorts = new LinkedList<NodePortTuple>();

        if (destinationRootedFullTrees == null) return null;
        if (destinationRootedFullTrees.get(dstId) == null) return null;

        Map<DatapathId, Link> nexthoplinks = destinationRootedFullTrees.get(dstId).getLinks();

        if (!switches.contains(srcId) || !switches.contains(dstId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not
            // in the network)
            log.info("buildroute: Standalone switch: {}", srcId);

            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []

        } else if ((nexthoplinks!=null) && (nexthoplinks.get(srcId) != null)) {
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

        Route result = null;
        if (sPorts != null && !sPorts.isEmpty()) {
            result = new Route(id, sPorts);
        }
        if (log.isTraceEnabled()) {
            log.trace("buildroute: {}", result);
        }
        return result;
    }

    protected Route buildroute(RouteId id, BroadcastTree tree) {
        NodePortTuple npt;
        DatapathId srcId = id.getSrc();
        DatapathId dstId = id.getDst();
        //set of NodePortTuples on the route
        LinkedList<NodePortTuple> sPorts = new LinkedList<NodePortTuple>();

        if (tree == null) return null;

        // TODO: Check if the src and dst are in the tree
        // if (destinationRootedFullTrees.get(dstId) == null) return null;

        Map<DatapathId, Link> nexthoplinks = tree.getLinks();

        if (!switches.contains(srcId) || !switches.contains(dstId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not
            // in the network)
            log.info("buildroute: Standalone switch: {}", srcId);

            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []

        } else if ((nexthoplinks!=null) && (nexthoplinks.get(srcId) != null)) {
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

        Route result = null;
        if (sPorts != null && !sPorts.isEmpty()) {
            result = new Route(id, sPorts);

        }
        if (log.isTraceEnabled()) {
            log.trace("buildroute: {}", result);
        }
        return result;
    }

    /*
     * Getter Functions
     */

    protected int getCost(DatapathId srcId, DatapathId dstId) {
        BroadcastTree bt = destinationRootedTrees.get(dstId);
        if (bt == null) return -1;
        return bt.getCost(srcId);
    }
    
    protected Set<Cluster> getClusters() {
        return clusters;
    }

    protected boolean routeExists(DatapathId srcId, DatapathId dstId) {
        BroadcastTree bt = destinationRootedTrees.get(dstId);
        if (bt == null) return false;
        Link link = bt.getLinks().get(srcId);
        if (link == null) return false;
        return true;
    }

    /*
    Function that calls Yen's algorithm and returns a list of routes
    from A to B.
	 */
    protected ArrayList<Route> getRoutes(DatapathId src, DatapathId dst, Integer K) {
        return yens(src, dst, K);
    }

    protected Map<DatapathId, Set<Link>> buildLinkDpidMap(Set<DatapathId> switches, Map<DatapathId,
            Set<OFPort>> switchPorts, Map<NodePortTuple, Set<Link>> allLinks) {

        Map<DatapathId, Set<Link>> linkDpidMap = new HashMap<DatapathId, Set<Link>>();
        for (DatapathId s : switches) {
            if (switchPorts.get(s) == null) continue;
            for (OFPort p : switchPorts.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (allLinks.get(np) == null) continue;
                for (Link l : allLinks.get(np)) {
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
     * @param K: The number of routes that you want. Must be positive integer.
     * @return ArrayList of Routes or null if bad parameters
     */
    protected ArrayList<Route> getRoutesFast(DatapathId src, DatapathId dst, Integer K) {
        // TODO: Think about using int instead of Integer
        RouteId routeId = new RouteId(src, dst);
        ArrayList<Route> routes = routecache.get(routeId);

        if (routes == null || K < 1) return null;

        if (K >= maximumRouteEntriesStored || K >= routes.size()) {
            return routes;
        }
        else {
            return new ArrayList<Route>(routes.subList(0, K));
        }
    }

    /**
     *
     * This function returns K number of routes between a source and destination. It will attempt to retrieve
     * these routes from the routecache. If the user requests more routes than are stored, Yen's algorithm will be
     * run using the K value passed in.
     *
     *
     * @param src: DatapathId of the route source.
     * @param dst: DatapathId of the route destination.
     * @param K: The number of routes that you want. Must be positive integer.
     * @return ArrayList of Routes or null if bad parameters
     */
    protected ArrayList<Route> getRoutesSlow(DatapathId src, DatapathId dst, Integer K) {
        // TODO: Think about using int instead of Integer
        RouteId routeId = new RouteId(src, dst);
        ArrayList<Route> routes = routecache.get(routeId);

        if (routes == null || K < 1) return null;

        if (K >= maximumRouteEntriesStored || K >= routes.size()) {
            return getRoutes(src, dst, K);
        }
        else {
            return new ArrayList<Route>(routes.subList(0, K));
        }
    }

    protected void setRouteCosts(Route r) {
        U64 cost = U64.ZERO;

        // Set number of hops. Assuming the list of NPTs is always even.
        r.setRouteHopCount(r.getPath().size()/2);

        for (int i = 0; i <= r.getPath().size() - 2; i = i + 2) {
            DatapathId src = r.getPath().get(i).getNodeId();
            DatapathId dst = r.getPath().get(i + 1).getNodeId();
            OFPort srcPort = r.getPath().get(i).getPortId();
            OFPort dstPort = r.getPath().get(i + 1).getPortId();
            for (Link l : allLinks.get(r.getPath().get(i))) {
                //log.debug("Iterating through the links");
                if (l.getSrc().equals(src) && l.getDst().equals(dst) &&
                        l.getSrcPort().equals(srcPort) && l.getDstPort().equals(dstPort)) {
                    log.info("Matching link found: {}", l);
                    cost = cost.add(l.getLatency());
                }
            }
        }

        r.setRouteLatency(cost);
        log.info("Total cost is {}", cost);
        log.info(r.toString());

    }

    protected ArrayList<Route> yens(DatapathId src, DatapathId dst, Integer K) {

        //log.debug("YENS ALGORITHM -----------------");
        //log.debug("Asking for routes from {} to {}", src, dst);
        //log.debug("Asking for {} routes", K);

        // Find link costs
        Map<Link, Integer> linkCost = initLinkCostMap();

        Map<DatapathId, Set<Link>> linkDpidMap = buildLinkDpidMap(switches, switchPorts, allLinks);

        Map<DatapathId, Set<Link>> copyOfLinkDpidMap = new HashMap<DatapathId, Set<Link>>(linkDpidMap);

        // A is the list of shortest paths. The number in the list at the end should be less than or equal to K
        // B is the list of possible shortest paths found in this function.
        ArrayList<Route> A = new ArrayList<Route>();
        ArrayList<Route> B = new ArrayList<Route>();

        // The number of routes requested should never be less than 1.
        if (K < 1) {
            return A;
        }

        if (!switches.contains(src) || !switches.contains(dst)) {
            return A;
        }

        // Using Dijkstra's to find the shortest path, which will also be the first path in A
        Route newroute = buildroute(new RouteId(src, dst), dijkstra(copyOfLinkDpidMap, dst, linkCost, true));

        if (newroute != null) {
            setRouteCosts(newroute);
            A.add(newroute);
        }
        else {
            log.info("No routes found in Yen's!");
            return A;
        }

        // Loop through K - 1 times to get other possible shortest paths
        for (int k = 1; k < K; k++) {
            log.info("k: {}", k);
            //log.debug("Path Length 'A.get(k-1).getPath().size()-2': {}", A.get(k - 1).getPath().size() - 2);
            // Iterate through i, which is the number of links in the most recent path added to A
            for (int i = 0; i <= A.get(k - 1).getPath().size() - 2; i = i + 2) {
                log.info("i: {}", i);
                List<NodePortTuple> path = A.get(k - 1).getPath();
                //log.debug("A(k-1): {}", A.get(k - 1).getPath());
                // The spur node is the point in the topology where Dijkstra's is called again to find another path
                DatapathId spurNode = path.get(i).getNodeId();
                // rootPath is the path along the previous shortest path that is before the spur node
                Route rootPath = new Route(new RouteId(path.get(0).getNodeId(), path.get(i).getNodeId()),
                        path.subList(0, i));


                Map<NodePortTuple, Set<Link>> allLinksCopy = new HashMap<NodePortTuple, Set<Link>>(allLinks);
                // Remove the links after the spur node that are part of other paths in A so that new paths
                // found are unique
                for (Route r : A) {
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
                copyOfLinkDpidMap = buildLinkDpidMap(switchesCopy, switchPorts, allLinksCopy);

                //log.debug("About to build route.");
                //log.debug("Switches: {}", switchesCopy);
                // Uses Dijkstra's to try to find a shortest path from the spur node to the destination
                Route spurPath = buildroute(new RouteId(spurNode, dst), dijkstra(copyOfLinkDpidMap, dst, linkCost, true));
                if (spurPath == null) {
                    //log.debug("spurPath is null");
                    continue;
                }

                // Adds the root path and spur path together to get a possible shortest path
                List<NodePortTuple> totalNpt = new LinkedList<NodePortTuple>();
                totalNpt.addAll(rootPath.getPath());
                totalNpt.addAll(spurPath.getPath());
                Route totalPath = new Route(new RouteId(src, dst), totalNpt);
                setRouteCosts(totalPath);

                log.info("Spur Node: {}", spurNode);
                log.info("Root Path: {}", rootPath);
                log.info("Spur Path: {}", spurPath);
                log.info("Total Path: {}", totalPath);
                // Adds the new path into B
                int flag = 0;
                for (Route r_B : B) {
                    for (Route r_A : A) {
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

            //log.debug("Removing shortest path from {}", B);
            // Find the shortest path in B, remove it, and put it in A
            log.info("--------------BEFORE------------------------");
            for (Route r : B) {
                log.info(r.toString());
            }
            log.info("--------------------------------------------");
            Route shortestPath = removeShortestPath(B, linkCost);
            log.info("--------------AFTER------------------------");
            for (Route r : B) {
                log.info(r.toString());
            }
            log.info("--------------------------------------------");

            if (shortestPath != null) {
                //log.debug("Adding new shortest path to {} in Yen's", shortestPath);
                A.add(shortestPath);
                log.info("A: {}", A);
            }
            else {
                //log.debug("removeShortestPath returned {}", shortestPath);
            }
        }

        // Set the route counts
        for (Route r : A) {
            r.setRouteCount(A.indexOf(r));
        }
        //log.debug("END OF YEN'S --------------------");
        return A;
    }

    protected Route removeShortestPath(ArrayList<Route> routes, Map<Link, Integer> linkCost) {
        log.debug("REMOVE SHORTEST PATH -------------");
        // If there is nothing in B, return
        if(routes == null){
            log.debug("Routes == null");
            return null;
        }
        Route shortestPath = null;
        // Set the default shortest path to the max value
        Integer shortestPathCost = Integer.MAX_VALUE;

        // Iterate through B and find the shortest path
        for (Route r : routes) {
            Integer pathCost = 0;
            // Add up the weights of each link in the path
            // TODO Get the path cost from the route object
            for (NodePortTuple npt : r.getPath()) {
                if (allLinks.get(npt) ==  null || linkCost.get(allLinks.get(npt).iterator().next()) == null) {
                    pathCost++;
                }
                else {
                    pathCost += linkCost.get(allLinks.get(npt).iterator().next());
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

	/*
	* Calculates E2E route
	*/
    protected Route getRoute(DatapathId srcId, OFPort srcPort,
            DatapathId dstId, OFPort dstPort, U64 cookie) {
        // Return null if the route source and destination are the
        // same switch ports.
        if (srcId.equals(dstId) && srcPort.equals(dstPort)) {
            return null;
        }

        List<NodePortTuple> nptList;
        NodePortTuple npt;
        Route r = getRoute(srcId, dstId, U64.of(0));
        if (r == null && !srcId.equals(dstId)) {
        	return null;
        }

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
    

    // NOTE: Return a null route if srcId equals dstId.  The null route
    // need not be stored in the cache.  Moreover, the LoadingCache will
    // throw an exception if null route is returned.
    protected Route getRoute(DatapathId srcId, DatapathId dstId, U64 cookie) {
        // Return null route if srcId equals dstId
        if (srcId.equals(dstId)) return null;

        RouteId id = new RouteId(srcId, dstId);
        Route result = null;

        try {
            //result = pathcache.get(id);
            if (!routecache.get(id).isEmpty()) {
                result = routecache.get(id).get(0);
            }
        } catch (Exception e) {
            log.warn("Could not find route from {} to {}. If the path exists, wait for the topology to settle, and it will be detected", srcId, dstId);
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

    protected boolean isInternalToOpenflowDomain(DatapathId switchid, OFPort port) {
        return !isAttachmentPointPort(switchid, port);
    }

    public boolean isAttachmentPointPort(DatapathId switchid, OFPort port) {
        NodePortTuple npt = new NodePortTuple(switchid, port);
        if (switchPortLinks.containsKey(npt)) return false;
        return true;
    }

    protected DatapathId getOpenflowDomainId(DatapathId switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) return switchId;
        return c.getId();
    }

    protected DatapathId getL2DomainId(DatapathId switchId) {
        return getOpenflowDomainId(switchId);
    }

    protected Set<DatapathId> getSwitchesInOpenflowDomain(DatapathId switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) {
            // The switch is not known to topology as there
            // are no links connected to it.
            Set<DatapathId> nodes = new HashSet<DatapathId>();
            nodes.add(switchId);
            return nodes;
        }
        return (c.getNodes());
    }

    protected boolean inSameOpenflowDomain(DatapathId switch1, DatapathId switch2) {
        Cluster c1 = switchClusterMap.get(switch1);
        Cluster c2 = switchClusterMap.get(switch2);
        if (c1 != null && c2 != null)
            return (c1.getId().equals(c2.getId()));
        return (switch1.equals(switch2));
    }

    public boolean isAllowed(DatapathId sw, OFPort portId) {
        return true;
    }

    /*
	 * Takes finiteBroadcastTree into account to prevent loops in the network
	 */
    protected boolean isIncomingBroadcastAllowedOnSwitchPort(DatapathId sw, OFPort portId) {
        if (!isEdge(sw, portId)){       
            NodePortTuple npt = new NodePortTuple(sw, portId);
            if (broadcastNodePorts.contains(npt))
                return true;
            else return false;
        }
        return true;
    }


    public boolean isConsistent(DatapathId oldSw, OFPort oldPort, DatapathId newSw, OFPort newPort) {
        if (isInternalToOpenflowDomain(newSw, newPort)) return true;
        return (oldSw.equals(newSw) && oldPort.equals(newPort));
    }

    protected Set<NodePortTuple> getBroadcastNodePortsInCluster(DatapathId sw) {
        DatapathId clusterId = getOpenflowDomainId(sw);
        return clusterBroadcastNodePorts.get(clusterId);
    }

    public boolean inSameBroadcastDomain(DatapathId s1, OFPort p1, DatapathId s2, OFPort p2) {
        return false;
    }

    public boolean inSameL2Domain(DatapathId switch1, DatapathId switch2) {
        return inSameOpenflowDomain(switch1, switch2);
    }

    public NodePortTuple getOutgoingSwitchPort(DatapathId src, OFPort srcPort,
            DatapathId dst, OFPort dstPort) {
        // Use this function to redirect traffic if needed.
        return new NodePortTuple(dst, dstPort);
    }

    public NodePortTuple getIncomingSwitchPort(DatapathId src, OFPort srcPort,
            DatapathId dst, OFPort dstPort) {
        // Use this function to reinject traffic from a
        // different port if needed.
        return new NodePortTuple(src, srcPort);
    }

    public Set<DatapathId> getSwitches() {
        return switches;
    }

    public Set<OFPort> getPortsWithLinks(DatapathId sw) {
        return switchPorts.get(sw);
    }

    public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort) {
        Set<OFPort> result = new HashSet<OFPort>();
        DatapathId clusterId = getOpenflowDomainId(targetSw);
        for (NodePortTuple npt : clusterPorts.get(clusterId)) {
            if (npt.getNodeId().equals(targetSw)) {
                result.add(npt.getPortId());
            }
        }
        return result;
    }

    public NodePortTuple getAllowedOutgoingBroadcastPort(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort) {
        return null;
    }

    public NodePortTuple getAllowedIncomingBroadcastPort(DatapathId src, OFPort srcPort) {
        return null;
    }
}