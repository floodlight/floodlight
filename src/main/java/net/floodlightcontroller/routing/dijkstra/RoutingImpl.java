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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.IRoutingEngine;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.topology.ITopologyAware;

/**
 * Floodlight component to find shortest paths based on dijkstra's algorithm
 *
 * @author Mandeep Dhami (mandeep.dhami@bigswitch.com)
 */
public class RoutingImpl implements IRoutingEngine, ITopologyAware {
    
    public static final int MAX_LINK_WEIGHT = 1000;
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    public static final int PATH_CACHE_SIZE = 1000;

    protected static Logger log = LoggerFactory.getLogger(RoutingImpl.class); 
    protected ReentrantReadWriteLock lock;
    protected HashMap<Long, HashMap<Link, Link>> network;
    protected HashMap<Long, HashMap<Long, Link>> nexthoplinkmaps;
    protected HashMap<Long, HashMap<Long, Long>> nexthopnodemaps;
    protected LRUHashMap<RouteId, Route> pathcache;
    
    public RoutingImpl() {                
        lock = new ReentrantReadWriteLock();

        network = new HashMap<Long, HashMap<Link, Link>>();
        nexthoplinkmaps = new HashMap<Long, HashMap<Long, Link>>();
        nexthopnodemaps = new HashMap<Long, HashMap<Long, Long>>();
        pathcache = new LRUHashMap<RouteId, Route>(PATH_CACHE_SIZE);
        
        log.info("Initialized Dijkstra RouterImpl");
    }
   
    @Override
    public boolean routeExists(Long srcId, Long dstId) {
        // self route check
        if (srcId.equals(dstId))
            return true;

        // Check if next hop exists
        lock.readLock().lock();
        HashMap<Long, Long> nexthopnodes = nexthopnodemaps.get(srcId);
        boolean exists = (nexthopnodes!=null) && (nexthopnodes.get(dstId)!=null);
        lock.readLock().unlock();

        return exists;
    }

    @Override
    public BroadcastTree getBCTree(Long rootNode) {
        HashMap<Long, Link> links = nexthoplinkmaps.get(rootNode);
        HashMap<Long, Long> nodes = nexthopnodemaps.get(rootNode);
        if (links == null || nodes == null) {
            return null;
        }
        return new BroadcastTree(links, nodes);
    }
    
    @Override
    public void clear() {
        lock.writeLock().lock();
        network.clear();
        pathcache.clear();
        nexthoplinkmaps.clear();
        nexthopnodemaps.clear();
        lock.writeLock().unlock();
    }

    @Override
    public Route getRoute(IOFSwitch src, IOFSwitch dst) {
        return getRoute(src.getId(), dst.getId());
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
        log.debug("getRoute: {} -> {}", id, result);
        return result;
    }

    private Route buildroute(RouteId id, Long srcId, Long dstId) {
        LinkedList<Link> path = null;
        HashMap<Long, Link> nexthoplinks = nexthoplinkmaps.get(srcId);
        HashMap<Long, Long> nexthopnodes = nexthopnodemaps.get(srcId);
        
        if (!network.containsKey(srcId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not in the network)
            log.debug("buildroute: Standalone switch: {}", srcId);
            
            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []
            if (srcId.equals(dstId)) path = new LinkedList<Link>();
        } else if ((nexthoplinks!=null) && (nexthoplinks.get(dstId)!=null) &&
                 (nexthopnodes!=null) && (nexthopnodes.get(dstId)!=null)) {
            // A valid path exits, calculate it
            path = new LinkedList<Link>();
            while (!srcId.equals(dstId)) {
                Link l = nexthoplinks.get(dstId);
                path.addFirst(l);
                dstId = nexthopnodes.get(dstId);
            }
        }
        // else, no path exists, and path equals null

        Route result = null;
        if (path != null) result = new Route(id, path);
        log.debug("buildroute: {}", result);
        return result;
    }

    @Override
    public void addedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
            IOFSwitch dstSw, short dstPort, int dstPortState)
    {
        updatedLink(srcSw, srcPort, srcPortState, dstSw, dstPort, dstPortState);
    }
    
    @Override
    public void updatedLink(IOFSwitch src, short srcPort, int srcPortState,
            IOFSwitch dst, short dstPort, int dstPortState)
    {
        boolean added = (((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue()) &&
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue()));
        update(src.getId(), srcPort, dst.getId(), dstPort, added);
    }
    
    @Override
    public void removedLink(IOFSwitch src, short srcPort, IOFSwitch dst, short dstPort)
    {
        update(src.getId(), srcPort, dst.getId(), dstPort, false);
    }    
    
    @Override
    public void update(Long srcId, Integer srcPort, Long dstId, Integer dstPort, boolean added) {
        update(srcId, srcPort.shortValue(), dstId, dstPort.shortValue(), added);
    }

    @Override
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

        if (added) {
            Link srcLink = new Link(srcPort, dstPort, dstId);
            if (src.containsKey(srcPort)) {
                log.debug("update: unexpected link add request - srcPort in use src, link: {}, {}", srcId, src.get(srcPort));
            }
            log.debug("update: added link: {}, {}", srcId, srcLink);
            src.put(srcLink, srcLink);
            network_updated = true;
        }
        else {
            // Only remove if that link actually exists.
            if (src.containsKey(srcPort) && src.get(srcPort).getDst().equals(dstId)) {
                log.debug("update: removed link: {}, {}", srcId, srcPort);
                src.remove(srcPort);
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
        nexthopnodemaps.clear();
        
        for (Long node : network.keySet()) {
            BroadcastTree nexthop = dijkstra(node);
            nexthoplinkmaps.put(node, nexthop.getLinks());
            nexthopnodemaps.put(node, nexthop.getNodes());            
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
        
        BroadcastTree ret = new BroadcastTree(nexthoplinks, nexthopnodes);
        return ret;
    }

    public void startUp() {}
    public void shutDown() {}

    @Override
    public void clusterMerged() {
        // no-op
    }

    @Override
    public void updatedSwitch(IOFSwitch sw) {
        // Ignored by RoutingImpl
    }

}
