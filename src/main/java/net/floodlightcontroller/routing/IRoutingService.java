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

import java.util.ArrayList;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Route;

public interface IRoutingService extends IFloodlightService {

    /**
     * Provides a route between src and dst that allows tunnels. The cookie is provisioned
     * for callers of getRoute to provide additional information to influence the route
     * to be returned, if the underlying routing implementation supports choice among
     * multiple routes.
     * @param src Source switch DPID.
     * @param dst Destination switch DPID.
     * @param cookie cookie (usage determined by implementation; ignored by topology instance now).
     */
    public Route getRoute(DatapathId src, DatapathId dst, U64 cookie);

    /**
     * Provides a route between src and dst, with option to allow or
     *  not allow tunnels in the path.
     * @param src Source switch DPID.
     * @param dst Destination switch DPID.
     * @param cookie cookie (usage determined by implementation; ignored by topology instance now).
     * @param tunnelEnabled boolean option.
     */
    public Route getRoute(DatapathId src, DatapathId dst, U64 cookie, boolean tunnelEnabled);

    /**
     * Provides a route between srcPort on src and dstPort on dst.
     * @param src Source switch DPID.
     * @param srcPort Source port on source switch.
     * @param dst Destination switch DPID.
     * @param dstPort dstPort on Destination switch.
     * @param cookie cookie (usage determined by implementation; ignored by topology instance now).
     */
    public Route getRoute(DatapathId srcId, OFPort srcPort, DatapathId dstId, OFPort dstPort, U64 cookie);

    /**
     * Provides a route between srcPort on src and dstPort on dst.
     * @param src Source switch DPID.
     * @param srcPort Source port on source switch.
     * @param dst Destination switch DPID.
     * @param dstPort dstPort on Destination switch.
     * @param cookie cookie (usage determined by implementation; ignored by topology instance now).
     * @param tunnelEnabled boolean option.
     */
    public Route getRoute(DatapathId srcId, OFPort srcPort, DatapathId dstId, OFPort dstPort, U64 cookie, boolean tunnelEnabled);

    /** return all routes, if available */
    public ArrayList<Route> getRoutes(DatapathId longSrcDpid, DatapathId longDstDpid, boolean tunnelEnabled);

    /** Another version of getRoutes that uses Yen's algorithm under the hood. */
    public ArrayList<Route> getRoutes(DatapathId srcDpid, DatapathId dstDpid, Integer numOfRoutesToGet);

    /**
     *
     * This function returns K number of routes between a source and destination IF THEY EXIST IN THE ROUTECACHE.
     * If the user requests more routes than available, only the routes already stored in memory will be returned.
     * This value can be adjusted in floodlightdefault.properties.
     *
     *
     * @param srcDpid: DatapathId of the route source.
     * @param dstDpid: DatapathId of the route destination.
     * @param numOfRoutesToGet: The number of routes that you want. Must be positive integer.
     * @return ArrayList of Routes or null if bad parameters
     */
    public ArrayList<Route> getRoutesFast(DatapathId srcDpid, DatapathId dstDpid, Integer numOfRoutesToGet);

    /**
     *
     * This function returns K number of routes between a source and destination. It will attempt to retrieve
     * these routes from the routecache. If the user requests more routes than are stored, Yen's algorithm will be
     * run using the K value passed in.
     *
     *
     * @param srcDpid: DatapathId of the route source.
     * @param dstDpid: DatapathId of the route destination.
     * @param numOfRoutesToGet: The number of routes that you want. Must be positive integer.
     * @return ArrayList of Routes or null if bad parameters
     */
    public ArrayList<Route> getRoutesSlow(DatapathId srcDpid, DatapathId dstDpid, Integer numOfRoutesToGet);

    /** Check if a route exists between src and dst, including tunnel links
     *  in the path.
     */
    public boolean routeExists(DatapathId src, DatapathId dst);

    /** Check if a route exists between src and dst, with option to have
     *  or not have tunnels as part of the path.
     */
    public boolean routeExists(DatapathId src, DatapathId dst, boolean tunnelEnabled);

}
