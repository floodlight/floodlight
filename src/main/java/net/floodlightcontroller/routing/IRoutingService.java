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
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Route;

public interface IRoutingService extends IFloodlightService {

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

    public void setPathMetric(PATH_METRIC metric);
    public PATH_METRIC getPathMetric();

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
     * Locates a path between src and dst
     * @param src source switch
     * @param dst destination switch
     * @return the lowest cost path
     */
    public Route getPath(DatapathId src, DatapathId dst);

    /**
     * Provides a path between srcPort on src and dstPort on dst.
     * @param src source switch
     * @param srcPort source port on source switch
     * @param dst destination switch
     * @param dstPort destination port on destination switch
     * @return the lowest cost path
     */
    public Route getPath(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort);

    /**
     * Return all possible paths up to quantity of the globally configured max.
     * @param src source switch
     * @param dst destination switch
     * @return list of paths ordered least to greatest cost
     */
    public List<Route> getPathsFast(DatapathId src, DatapathId dst);

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
    public List<Route> getPathsFast(DatapathId src, DatapathId dst, int numReqPaths);

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
    public List<Route> getPathsSlow(DatapathId src, DatapathId dst, int numReqPaths);

    /** 
     * Check if a path exists between src and dst
     * @param src source switch
     * @param dst destination switch
     * @return true if a path exists; false otherwise
     */
    public boolean pathExists(DatapathId src, DatapathId dst);
}
