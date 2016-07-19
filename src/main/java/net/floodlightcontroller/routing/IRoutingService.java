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

package net.floodlightcontroller.routing;

import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Path;

public interface IRoutingService extends IFloodlightService {

    /**
     * The metric used to compute paths across the topology
     * 
     * @author rizard
     */
    public enum PATH_METRIC { 
        LATENCY("latency"), 
        HOPCOUNT("hopcount"), 
        HOPCOUNT_AVOID_TUNNELS("hopcount_avoid_tunnels"), 
        UTILIZATION("utilization"), 
        LINK_SPEED("link_speed");
        
        String name;

        private PATH_METRIC(String s) {
            name = s;
        }
        
        public String getMetricName() {
            return name;
        }
    };

    /**
     * Set the metric used when computing paths
     * across the topology.
     * @param metric
     */
    public void setPathMetric(PATH_METRIC metric);
    
    /**
     * Get the metric being used to compute paths
     * across the topology.
     * @return
     */
    public PATH_METRIC getPathMetric();
    
    /** 
     * Register the RDCListener 
     * @param listener - The module that wants to listen for events
     */
    public void addRoutingDecisionChangedListener(IRoutingDecisionChangedListener listener);
    
    /** 
     * Remove the RDCListener
     * @param listener - The module that wants to stop listening for events
     */
    public void removeRoutingDecisionChangedListener(IRoutingDecisionChangedListener listener);
    
    /** 
     * Notifies listeners that routing logic has changed, requiring certain past routing decisions
     * to become invalid.  The caller provides a sequence of masked values that match against
     * past values of IRoutingDecision.getDescriptor().  Services that have operated on past
     * routing decisions are then able to remove the results of past decisions, normally by deleting
     * flows.
     * 
     * @param changedDecisions Masked descriptors identifying routing decisions that are now obsolete or invalid  
     */
    public void handleRoutingDecisionChange(Iterable<Masked<U64>> changedDecisions);

    /**
     * Do not compute more than max paths by default (fast).
     * @param max
     */
    public void setMaxPathsToCompute(int max);

    /**
     * Get the max paths that are computed by default (fast).
     * @return
     */
    public int getMaxPathsToCompute();
    
    /** 
     * Check if a path exists between src and dst
     * @param src source switch
     * @param dst destination switch
     * @return true if a path exists; false otherwise
     */
    public boolean pathExists(DatapathId src, DatapathId dst);

    /**
     * Locates a path between src and dst
     * @param src source switch
     * @param dst destination switch
     * @return the lowest cost path
     */
    public Path getPath(DatapathId src, DatapathId dst);

    /**
     * Provides a path between srcPort on src and dstPort on dst.
     * @param src source switch
     * @param srcPort source port on source switch
     * @param dst destination switch
     * @param dstPort destination port on destination switch
     * @return the lowest cost path
     */
    public Path getPath(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort);

    /**
     * Return all possible paths up to quantity of the globally configured max.
     * @param src source switch
     * @param dst destination switch
     * @return list of paths ordered least to greatest cost
     */
    public List<Path> getPathsFast(DatapathId src, DatapathId dst);

    /**
     * This function returns K number of paths between a source and destination 
     * **if they exist in the pathcache**. If the caller requests more paths than 
     * available, only the paths already stored in memory will be returned.
     * 
     * See {@link #getPathsSlow(DatapathId, DatapathId, int)} to compute 
     * additional paths in real-time.
     * 
     * The number of paths returned will be the min(numReqPaths, maxConfig),
     * where maxConfig is the configured ceiling on paths to precompute.
     *
     * @param src source switch
     * @param dst destination switch
     * @param numReqPaths the requested quantity of paths
     * @return list of paths ordered least to greatest cost
     */
    public List<Path> getPathsFast(DatapathId src, DatapathId dst, int numReqPaths);

    /**
     * This function returns K number of paths between a source and destination.
     * It will attempt to retrieve these paths from the pathcache. If the caller 
     * requests more paths than are stored, Yen's algorithm will be re-run in an
     * attempt to located the desired quantity of paths (which can be expensive).
     * 
     * See {@link #getPathsFast(DatapathId, DatapathId, int)} or  
     * {@link #getPathsFast(DatapathId, DatapathId)} to retrieve the
     * precomputed paths without the risk of additional overhead.
     * 
     * The number of paths returned will be the min(numReqPaths, availablePaths),
     * where availablePaths is the permutation of all possible paths in the topology
     * from src to dst.
     * 
     * @param src source switch
     * @param dst destination switch
     * @param numReqPaths the requested quantity of paths
     * @return list of paths ordered least to greatest cost
     */
    public List<Path> getPathsSlow(DatapathId src, DatapathId dst, int numReqPaths);
    
    /**
     * Recompute paths now, regardless of whether or not there was a change in the
     * topology. This should be called if {@link #setPathMetric(PATH_METRIC)} was
     * invoked to change the PATH_METRIC **and we want the new metric to take effect
     * now** for future getPathsFast() or getPath() function calls. This will allow other 
     * modules using IRoutingService path-finding to use paths based on the new metric
     * if the other modules only use the "fast" path-finding API. 
     * 
     * One can use {@link IRoutingService#getPathsSlow(DatapathId, DatapathId, int)} if there is no
     * urgency for the new metric to take effect and yet one would still like to see 
     * the paths using the new metric once or so. In this case, one need not invoke {@link #forceRecompute()}.
     * 
     * @return true upon success; false otherwise
     */
    public boolean forceRecompute();
}
