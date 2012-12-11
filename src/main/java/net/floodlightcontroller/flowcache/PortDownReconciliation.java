/**
 *    Copyright 2012, Jason Parraga, Marist College 
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
package net.floodlightcontroller.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * Flow reconciliation module that is triggered by PORT_DOWN events. This module
 * will recursively trace back all flows from the immediately affected switch
 * and remove them (specifically flows with an idle timeout that would not be
 * exhausted). Once the flows are deleted Floodlight will re-evaluate the path
 * the traffic should take with it's updated topology map.
 * 
 * @author Jason Parraga
 */

public class PortDownReconciliation implements IFloodlightModule,
    ITopologyListener, IFlowReconcileListener {
    protected static Logger log = LoggerFactory.getLogger(PortDownReconciliation.class);

    protected ITopologyService topology;
    protected IFloodlightProviderService floodlightProvider;
    protected IFlowReconcileService frm;
    protected ILinkDiscoveryService lds;
    protected Map<Link, LinkInfo> links;
    protected FloodlightContext cntx;
    protected static boolean waiting = false;
    protected int statsQueryXId;
    protected static List<OFFlowStatisticsReply> statsReply;

    // ITopologyListener
    @Override
    public void topologyChanged() {
        for (LDUpdate ldu : topology.getLastLinkUpdates()) {
            if (ldu.getOperation()
                   .equals(ILinkDiscovery.UpdateOperation.PORT_DOWN)) {

                // Get the switch ID for the OFMatchWithSwDpid object
                long affectedSwitch = floodlightProvider.getSwitches()
                                                        .get(ldu.getSrc())
                                                        .getId();

                // Create an OFMatchReconcile object
                OFMatchReconcile ofmr = new OFMatchReconcile();

                // Generate an OFMatch objects for the OFMatchWithSwDpid object
                OFMatch match = new OFMatch().setWildcards(OFMatch.OFPFW_ALL);

                // Generate the OFMatchWithSwDpid
                OFMatchWithSwDpid ofmatchsw = new OFMatchWithSwDpid(match,
                                                                    affectedSwitch);

                // Set the action to update the path to remove flows routing
                // towards the downed port
                ofmr.rcAction = OFMatchReconcile.ReconcileAction.UPDATE_PATH;

                // Set the match, with the switch dpid
                ofmr.ofmWithSwDpid = ofmatchsw;

                // Assign the downed port to the OFMatchReconcile's outPort data
                // member (I added this to
                // the OFMatchReconcile class)
                ofmr.outPort = ldu.getSrcPort();

                // Tell the reconcile manager to reconcile matching flows
                frm.reconcileFlow(ofmr);
            }
        }
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(ITopologyService.class);
        l.add(IFlowReconcileService.class);
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    @Override
    public
            void
            init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        topology = context.getServiceImpl(ITopologyService.class);
        frm = context.getServiceImpl(IFlowReconcileService.class);
        lds = context.getServiceImpl(ILinkDiscoveryService.class);
        cntx = new FloodlightContext();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        topology.addListener(this);
        frm.addFlowReconcileListener(this);
    }

    @Override
    public String getName() {
        return "portdownreconciliation";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return true;
    }

    /**
     * Base case for the reconciliation of flows. This is triggered at the
     * switch which is immediately affected by the PORT_DOWN event
     * 
     * @return the Command whether to STOP or Continue
     */
    @Override
    public net.floodlightcontroller.core.IListener.Command
            reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList) {
        if (lds != null) {
            links = new HashMap<Link, LinkInfo>();
            // Get all the switch links from the topology
            if (lds.getLinks() != null) links.putAll(lds.getLinks());

            for (OFMatchReconcile ofmr : ofmRcList) {
                // We only care about OFMatchReconcile objects that wish to
                // update the path to a switch
                if (ofmr.rcAction.equals(OFMatchReconcile.ReconcileAction.UPDATE_PATH)) {
                    // Get the switch object from the OFMatchReconcile
                    IOFSwitch sw = floodlightProvider.getSwitches()
                                                     .get(ofmr.ofmWithSwDpid.getSwitchDataPathId());

                    // Map data structure that holds the invalid matches and the
                    // ingress ports of those matches
                    Map<Short, List<OFMatch>> invalidBaseIngressAndMatches = new HashMap<Short, List<OFMatch>>();

                    // Get the invalid flows
                    List<OFFlowStatisticsReply> flows = getFlows(sw,
                                                                 ofmr.outPort);

                    // Analyze all the flows with outPorts equaling the downed
                    // port and extract OFMatch's to trace back to neighbors
                    for (OFFlowStatisticsReply flow : flows) {
                        // Create a reference to the match for ease
                        OFMatch match = flow.getMatch();

                        // Here we utilize an index of input ports which point
                        // to multiple invalid matches
                        if (invalidBaseIngressAndMatches.containsKey(match.getInputPort()))
                            // If the input port is already in the index, add
                            // the match to it's list
                            invalidBaseIngressAndMatches.get(match.getInputPort())
                                                        .add(match);
                        else {
                            // Otherwise create a new list and add it to the
                            // index
                            List<OFMatch> matches = new ArrayList<OFMatch>();
                            matches.add(match);
                            invalidBaseIngressAndMatches.put(match.getInputPort(),
                                                             matches);
                        }
                    }

                    // Remove invalid flows from the base switch, if they exist
                    if (!flows.isEmpty()) {
                        log.debug("Removing flows on switch : " + sw.getId()
                                  + " with outport: " + ofmr.outPort);
                        clearFlowMods(sw, ofmr.outPort);
                    }

                    // Create a list of neighboring switches we need to remove
                    // invalid flows from
                    Map<IOFSwitch, Map<Short, List<OFMatch>>> neighborSwitches = new HashMap<IOFSwitch, Map<Short, List<OFMatch>>>();

                    // Loop through all the links
                    for (Link link : links.keySet()) {
                        // Filter out links we care about
                        if (link.getDst() == sw.getId()) {
                            // Loop through the links to neighboring switches
                            // which have invalid flows
                            for (Entry<Short, List<OFMatch>> invalidBaseIngressAndMatch : invalidBaseIngressAndMatches.entrySet()) {
                                // Find links on the network which link to the
                                // ingress ports that have invalidly routed
                                // flows
                                if (link.getDstPort() == invalidBaseIngressAndMatch.getKey()) {
                                    Map<Short, List<OFMatch>> invalidNeighborOutportAndMatch = new HashMap<Short, List<OFMatch>>();
                                    // Insert the neighbor's outPort to the base
                                    // switch and the invalid match
                                    invalidNeighborOutportAndMatch.put(link.getSrcPort(),
                                                                       invalidBaseIngressAndMatch.getValue());
                                    // Link a neighbor switch's invalid match
                                    // and outport to their Switch object
                                    neighborSwitches.put(floodlightProvider.getSwitches()
                                                                           .get(link.getSrc()),
                                                         invalidNeighborOutportAndMatch);
                                }
                            }
                        }
                    }
                    log.debug("We have " + neighborSwitches.size()
                              + " neighboring switches to deal with!");
                    // Loop through all the switches we found to have potential
                    // issues
                    for (IOFSwitch neighborSwitch : neighborSwitches.keySet()) {
                        log.debug("NeighborSwitch ID : "
                                  + neighborSwitch.getId());
                        if (neighborSwitches.get(neighborSwitch) != null)
                                                                         deleteInvalidFlows(neighborSwitch,
                                                                                            neighborSwitches.get(neighborSwitch));
                    }
                }
                return Command.CONTINUE;
            }
        } else {
            log.error("Link Discovery Service Is Null");
        }
        return Command.CONTINUE;
    }

    /**
     * @param sw
     *            the switch object that we wish to get flows from
     * @param outPort
     *            the output action port we wish to find flows with
     * @return a list of OFFlowStatisticsReply objects or essentially flows
     */
    public List<OFFlowStatisticsReply> getFlows(IOFSwitch sw, Short outPort) {

        statsReply = new ArrayList<OFFlowStatisticsReply>();
        List<OFStatistics> values = null;
        Future<List<OFStatistics>> future;

        // Statistics request object for getting flows
        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = req.getLengthU();
        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        specificReq.setMatch(new OFMatch().setWildcards(0xffffffff));
        specificReq.setOutPort(outPort);
        specificReq.setTableId((byte) 0xff);
        req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
        requestLength += specificReq.getLength();
        req.setLengthU(requestLength);

        try {
            // System.out.println(sw.getStatistics(req));
            future = sw.getStatistics(req);
            values = future.get(10, TimeUnit.SECONDS);
            if (values != null) {
                for (OFStatistics stat : values) {
                    statsReply.add((OFFlowStatisticsReply) stat);
                }
            }
        } catch (Exception e) {
            log.error("Failure retrieving statistics from switch " + sw, e);
        }

        return statsReply;
    }

    /**
     * @param sw
     *            The switch we wish to remove flows from
     * @param outPort
     *            The specific Output Action OutPort of specific flows we wish
     *            to delete
     */
    public void clearFlowMods(IOFSwitch sw, Short outPort) {
        // Delete all pre-existing flows with the same output action port or
        // outPort
        OFMatch match = new OFMatch().setWildcards(OFMatch.OFPFW_ALL);
        OFMessage fm = ((OFFlowMod) floodlightProvider.getOFMessageFactory()
                                                      .getMessage(OFType.FLOW_MOD)).setMatch(match)
                                                                                   .setCommand(OFFlowMod.OFPFC_DELETE)
                                                                                   .setOutPort(outPort)
                                                                                   .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));
        try {
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(fm);
            sw.write(msglist, cntx);
        } catch (Exception e) {
            log.error("Failed to clear flows on switch {} - {}", this, e);
        }
    }

    /**
     * @param sw
     *            The switch we wish to remove flows from
     * @param match
     *            The specific OFMatch object of specific flows we wish to
     *            delete
     * @param outPort
     *            The specific Output Action OutPort of specific flows we wish
     *            to delete
     */
    public void clearFlowMods(IOFSwitch sw, OFMatch match, Short outPort) {
        // Delete pre-existing flows with the same match, and output action port
        // or outPort
        match.setWildcards(OFMatch.OFPFW_ALL);
        OFMessage fm = ((OFFlowMod) floodlightProvider.getOFMessageFactory()
                                                      .getMessage(OFType.FLOW_MOD)).setMatch(match)
                                                                                   .setCommand(OFFlowMod.OFPFC_DELETE)
                                                                                   .setOutPort(outPort)
                                                                                   .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));
        try {
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(fm);
            sw.write(msglist, cntx);
        } catch (Exception e) {
            log.error("Failed to clear flows on switch {} - {}", this, e);
        }
    }

    /**
     * Deletes flows with similar matches and output action ports on the
     * specified switch
     * 
     * @param sw
     *            the switch to query flows on
     * @param match
     *            the problematic OFMatch from the base switch which we wish to
     *            find and remove
     * @param outPort
     *            the output action port wanted from the flows, which follows
     *            the route to the base switch
     */
    public
            void
            deleteInvalidFlows(IOFSwitch sw,
                               Map<Short, List<OFMatch>> invalidOutportAndMatch) {
        log.debug("Deleting invalid flows on switch : " + sw.getId());

        // A map that holds the input ports and invalid matches on a switch
        Map<Short, List<OFMatch>> invalidNeighborIngressAndMatches = new HashMap<Short, List<OFMatch>>();

        for (Short outPort : invalidOutportAndMatch.keySet()) {
            // Get the flows on the switch
            List<OFFlowStatisticsReply> flows = getFlows(sw, outPort);

            // Analyze all the flows with outPorts pointing to problematic route
            for (OFFlowStatisticsReply flow : flows) {
                // Loop through all the problematic matches
                for (OFMatch match : invalidOutportAndMatch.get(outPort)) {
                    // Compare the problematic matches with the match of the
                    // flow on the switch
                    if (HexString.toHexString(flow.getMatch()
                                                  .getDataLayerDestination())
                                 .equals(HexString.toHexString(match.getDataLayerDestination()))
                        && HexString.toHexString(flow.getMatch()
                                                     .getDataLayerSource())
                                    .equals(HexString.toHexString(match.getDataLayerSource()))
                        && flow.getMatch().getDataLayerType() == match.getDataLayerType()
                        && flow.getMatch().getDataLayerVirtualLan() == match.getDataLayerVirtualLan()
                        && flow.getMatch().getNetworkDestination() == match.getNetworkDestination()
                        && flow.getMatch().getNetworkDestinationMaskLen() == match.getNetworkDestinationMaskLen()
                        && flow.getMatch().getNetworkProtocol() == match.getNetworkProtocol()
                        && flow.getMatch().getNetworkSource() == match.getNetworkSource()
                        && flow.getMatch().getNetworkSourceMaskLen() == match.getNetworkSourceMaskLen()
                        && flow.getMatch().getNetworkTypeOfService() == match.getNetworkTypeOfService()) {

                        // Here we utilize an index of input ports which point
                        // to multiple invalid matches
                        if (invalidNeighborIngressAndMatches.containsKey(match.getInputPort()))
                            // If the input port is already in the index, add
                            // the match to it's list
                            invalidNeighborIngressAndMatches.get(match.getInputPort())
                                                            .add(match);
                        else {
                            // Otherwise create a new list and add it to the
                            // index
                            List<OFMatch> matches = new ArrayList<OFMatch>();
                            matches.add(match);
                            invalidNeighborIngressAndMatches.put(match.getInputPort(),
                                                                 matches);
                        }
                        // Remove flows from the switch with the invalid match
                        // and outPort
                        clearFlowMods(sw, flow.getMatch(), outPort);
                    }
                }
            }

            // Create a list of neighboring switches we need to check for
            // invalid flows
            Map<IOFSwitch, Map<Short, List<OFMatch>>> neighborSwitches = new HashMap<IOFSwitch, Map<Short, List<OFMatch>>>();

            // Loop through all the links
            for (Link link : links.keySet()) {
                // Filter out links we care about
                if (link.getDst() == sw.getId()) {
                    // Loop through the ingressPorts that are involved in
                    // invalid flows on neighboring switches
                    for (Entry<Short, List<OFMatch>> ingressPort : invalidNeighborIngressAndMatches.entrySet()) {
                        // Filter out invalid links by matching the link
                        // destination port to our invalid flows ingress port
                        if (link.getDstPort() == ingressPort.getKey()) {
                            // Generate a match and outPort map since I don't
                            // want to create an object
                            Map<Short, List<OFMatch>> invalidNeighborOutportAndMatch = new HashMap<Short, List<OFMatch>>();
                            invalidNeighborOutportAndMatch.put(link.getSrcPort(),
                                                               ingressPort.getValue());
                            // Link a neighbor switch's invalid match and
                            // outport to their Switch object
                            neighborSwitches.put(floodlightProvider.getSwitches()
                                                                   .get(link.getSrc()),
                                                 invalidNeighborOutportAndMatch);
                        }
                    }
                }
            }
            log.debug("We have " + neighborSwitches.size()
                      + " neighbors to deal with!");

            // Loop through all the neighbor switches we found to have
            // invalid matches
            for (IOFSwitch neighborSwitch : neighborSwitches.keySet()) {
                log.debug("NeighborSwitch ID : " + neighborSwitch.getId());
                // Recursively seek out and delete invalid flows on the
                // neighbor switch
                deleteInvalidFlows(neighborSwitch,
                                   neighborSwitches.get(neighborSwitch));
            }
        }
    }
}
