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
import org.projectfloodlight.openflow.types.Masked;
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

    /** Check if a route exists between src and dst, including tunnel links
     *  in the path.
     */
    public boolean routeExists(DatapathId src, DatapathId dst);

    /** Check if a route exists between src and dst, with option to have
     *  or not have tunnels as part of the path.
     */
    public boolean routeExists(DatapathId src, DatapathId dst, boolean tunnelEnabled);
    
    /** Register the RDCListener 
     * @param listener - The module that wants to listen for events
     */
    public void addRoutingDecisionChangedListener(IRoutingDecisionChangedListener listener);
    
    /** Remove the RDCListener
     * @param listener - The module that wants to stop listening for events
     */
    public void removeRoutingDecisionChangedListener(IRoutingDecisionChangedListener listener);
    
    /** Notifies listeners that routing logic has changed, requiring certain past routing decisions
     * to become invalid.  The caller provides a sequence of masked values that match against
     * past values of IRoutingDecision.getDescriptor().  Services that have operated on past
     * routing decisions are then able to remove the results of past decisions, normally by deleting
     * flows.
     * 
     * @param changedDecisions Masked descriptors identifying routing decisions that are now obsolete or invalid  
     */
    public void handleRoutingDecisionChange(Iterable<Masked<U64>> changedDecisions);

}
