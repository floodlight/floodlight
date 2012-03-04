/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
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

/**
 * Floodlight component to find shortest paths based on dijkstra's algorithm
 */
package net.floodlightcontroller.routing.dijkstra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.topology.ILinkDiscovery;
import net.floodlightcontroller.topology.ILinkDiscoveryListener;
import net.floodlightcontroller.topology.ILinkDiscoveryService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.util.LRUHashMap;

/**
 * Floodlight component to find shortest paths based on dijkstra's algorithm
 *
 * @author Mandeep Dhami (mandeep.dhami@bigswitch.com)
 */
public class RoutingImpl 
    implements IRoutingService, ILinkDiscoveryListener, ITopologyListener, IFloodlightModule {
    
    public static final int MAX_LINK_WEIGHT = 1000;
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    public static final int PATH_CACHE_SIZE = 1000;

    protected static Logger log = LoggerFactory.getLogger(RoutingImpl.class); 
    protected ReentrantReadWriteLock lock;
    protected HashMap<Long, HashMap<Link, Link>> network;
    protected HashMap<Long, HashMap<Long, Link>> nexthoplinkmaps;
    protected LRUHashMap<RouteId, Route> pathcache;
    protected ILinkDiscoveryService topology;
   
    @Override
    public boolean routeExists(long srcId, long dstId) {
        // self route check
        if (srcId == dstId)
            return true;

        // Check if next hop exists
        lock.readLock().lock();
        HashMap<Long, Link> nexthoplinks = nexthoplinkmaps.get(srcId);
        boolean exists = (nexthoplinks!=null) && (nexthoplinks.get(dstId)!=null);
        lock.readLock().unlock();

        return exists;
    }

    @Override
    public BroadcastTree getBroadcastTreeForCluster(long clusterId) {
        // We calculate clusterId to be the DPID of the switch
        // in that cluster.  We use the clusterId as the switchid to 
        // get the broadcast tree.
        HashMap<Long, Link> links = nexthoplinkmaps.get(clusterId);
        if (links == null) {
            return null;
        }
        return new BroadcastTree(links, null);
    }

    public void clear() {
        lock.writeLock().lock();
        network.clear();
        pathcache.clear();
        nexthoplinkmaps.clear();
        lock.writeLock().unlock();
    }

    @Override
    public Route getRoute(Long srcId, Long dstId) {
        lock.readLock().lock();

        RouteId id = new RouteId(srcId, dstId);
        Route result = null;
        if (pathcache.containsKey(id)) {
            result = pathcache.get(id);
        } else {
            result = buildroute(id, srcId, dstId);
            pathcache.put(id, result);
        }

        lock.readLock().unlock();
        if (log.isDebugEnabled()) {
            log.debug("getRoute: {} -> {}", id, result);
        }
        return result;
    }

    private Route buildroute(RouteId id, Long srcId, Long dstId) {
        LinkedList<Link> path = null;
        HashMap<Long, Link> nexthoplinks = nexthoplinkmaps.get(srcId);
        
        if (!network.containsKey(srcId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not in the network)
            log.debug("buildroute: Standalone switch: {}", srcId);
            
            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []
            if (srcId.equals(dstId)) path = new LinkedList<Link>();
        } else if ((nexthoplinks!=null) && (nexthoplinks.get(dstId)!=null)) {
            // A valid path exits, calculate it
            path = new LinkedList<Link>();
            while (!srcId.equals(dstId)) {
                Link l = nexthoplinks.get(dstId);
                path.addFirst(l);
                dstId = l.getSrc();
            }
        }
        // else, no path exists, and path equals null

        Route result = null;
        if (path != null) result = new Route(id, path);
        if (log.isDebugEnabled()) {
            log.debug("buildroute: {}", result);
        }
        return result;
    }

    @Override
    public void addedLink(long srcSw, short srcPort, int srcPortState,
            long dstSw, short dstPort, int dstPortState, ILinkDiscovery.LinkType type)
    {
        updatedLink(srcSw, srcPort, srcPortState, dstSw, dstPort, dstPortState, type);
    }
    
    @Override
    public void updatedLink(long src, short srcPort, int srcPortState,
            long dst, short dstPort, int dstPortState, ILinkDiscovery.LinkType type)
    {
        boolean added = (((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue()) &&
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue()));
        update(src, srcPort, dst, dstPort, added);
    }
    
    @Override
    public void removedLink(long src, short srcPort, long dst, short dstPort)
    {
        update(src, srcPort, dst, dstPort, false);
    }    

    public void update(Long srcId, Short srcPort, Long dstId, Short dstPort, boolean added) {
        lock.writeLock().lock();
        boolean network_updated = false;
       
        HashMap<Link, Link> src = network.get(srcId);
        if (src == null) {
            log.debug("update: new node: {}", srcId);
            src = new HashMap<Link, Link>();
            network.put(srcId, src);
            network_updated = true;
        }

        HashMap<Link, Link> dst = network.get(dstId);
        if (dst == null) {
            log.debug("update: new node: {}", dstId);
            dst = new HashMap<Link, Link>();
            network.put(dstId, dst);
            network_updated = true;
        }

        Link srcLink = new Link(srcId, srcPort, dstId, dstPort);
        if (added) {
            if (src.containsKey(srcPort)) {
                log.debug("update: unexpected link add request - srcPort in use src, link: {}, {}", srcId, src.get(srcPort));
            }
            log.debug("update: added link: {}, {}", srcId, srcLink);
            src.put(srcLink, srcLink);
            network_updated = true;
        }
        else {
            // Only remove if that link actually exists.
            if (src.containsKey(srcLink)) {
                if (log.isDebugEnabled()) {
                    String temp = HexString.toHexString(srcId).concat("/");
                    temp = temp.concat(Integer.toString(srcPort));
                    temp = temp.concat("-->");
                    temp = temp.concat(HexString.toHexString(dstId));
                    temp = temp.concat("/");
                    temp = temp.concat(Integer.toString(dstPort));
                    log.debug("update: removed link: {}", temp);
                }
                src.remove(srcLink);
                network_updated = true;
            }
            else {
                log.debug("update: unexpected link delete request (ignored) - src, port: {}, {}", srcId, srcPort);
                log.debug("update: current port value is being kept: {}", src.get(srcPort));
            }
        }
       
        if (network_updated) {
            recalculate();
            log.debug("update: dijkstra recalulated");
        } else {
            log.debug("update: dijkstra not recalculated");
        }
        
        lock.writeLock().unlock();
        return;
    }

    private void recalculate() {
        pathcache.clear();
        nexthoplinkmaps.clear();
        
        for (Long node : network.keySet()) {
            BroadcastTree nexthop = dijkstra(node);
            nexthoplinkmaps.put(node, nexthop.getLinks());
        }
        return;
    }
    
    private class NodeDist implements Comparable<NodeDist> {
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
                return (int)(o.node - this.node);
            }
            return o.dist - this.dist;
        }
    }
    
    private BroadcastTree dijkstra(Long src) {
        HashMap<Long, Link> nexthoplinks = new HashMap<Long, Link>();
        HashMap<Long, Long> nexthopnodes = new HashMap<Long, Long>();
        HashMap<Long, Integer> dist = new HashMap<Long, Integer>();
        for (Long node: network.keySet()) {
            nexthoplinks.put(node, null);
            nexthopnodes.put(node, null);
            dist.put(node, MAX_PATH_WEIGHT);
        }
        
        HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
        PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
        nodeq.add(new NodeDist(src, 0));
        dist.put(src, 0);
        while (nodeq.peek() != null) {
            NodeDist n = nodeq.poll();
            Long cnode = n.getNode();
            int cdist = n.getDist();
            if (cdist >= MAX_PATH_WEIGHT) break;
            if (seen.containsKey(cnode)) continue;
            seen.put(cnode, true);
            
            HashMap<Link,Link> ports = network.get(cnode);
            for (Link linkspec : ports.keySet()) {
                Link link = ports.get(linkspec);
                Long neighbor = link.getDst();
                int ndist = cdist + 1; // the weight of the link, always 1 in current version of floodlight.
                if (ndist < dist.get(neighbor)) {
                    dist.put(neighbor, ndist);
                    nexthoplinks.put(neighbor, link);
                    nexthopnodes.put(neighbor, cnode);
                    nodeq.add(new NodeDist(neighbor, ndist));
                }
            }
        }
        
        BroadcastTree ret = new BroadcastTree(nexthoplinks, null);
        return ret;
    }
    
    @Override
    public void toplogyChanged() {
        // no-op
    }

    @Override
    public void updatedSwitch(long sw) {
        // Ignored by RoutingImpl
    }

    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IRoutingService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(IRoutingService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
                                             throws FloodlightModuleException {
        topology = context.getServiceImpl(ILinkDiscoveryService.class);
        lock = new ReentrantReadWriteLock();
        network = new HashMap<Long, HashMap<Link, Link>>();
        nexthoplinkmaps = new HashMap<Long, HashMap<Long, Link>>();
        pathcache = new LRUHashMap<RouteId, Route>(PATH_CACHE_SIZE);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // Register to get updates from topology
        topology.addListener(this);
    }
}
