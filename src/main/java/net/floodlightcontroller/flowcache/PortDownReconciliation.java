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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.flowcache.PriorityPendingQueue.EventPriority;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMatchWithSwDpid;

/**
 * Flow reconciliation module that is triggered by PORT_DOWN events. This module
 * will recursively trace back all flows from the immediately affected switch
 * and remove them (specifically flows with an idle timeout that would not be
 * exhausted). Once the flows are deleted Floodlight will re-evaluate the path
 * the traffic should take with it's updated topology map.
 *
 * @author Jason Parraga
 */

@Deprecated
public class PortDownReconciliation implements IFloodlightModule,
    ITopologyListener, IFlowReconcileListener {
    protected static Logger log = LoggerFactory.getLogger(PortDownReconciliation.class);

    protected ITopologyService topology;
    protected IOFSwitchService switchService;
    protected IFlowReconcileService frm;
    protected ILinkDiscoveryService lds;
    protected Map<Link, LinkInfo> links;
    protected FloodlightContext cntx;
    protected static boolean waiting = false;
    protected int statsQueryXId;
    protected static List<OFFlowStatsReply> statsReply;

    // ITopologyListener
    @Override
    public void topologyChanged(List<LDUpdate> appliedUpdates) {
        for (LDUpdate ldu : appliedUpdates) {
            if (ldu.getOperation()
                   .equals(ILinkDiscovery.UpdateOperation.PORT_DOWN)) {

                // Get the switch ID for the OFMatchWithSwDpid object
                IOFSwitch affectedSwitch = switchService.getSwitch(ldu.getSrc());

                // Create an OFMatchReconcile object
                OFMatchReconcile ofmr = new OFMatchReconcile();

                // Generate an OFMatch objects for the OFMatchWithSwDpid object
                Match match = affectedSwitch.getOFFactory().buildMatch().build(); // nothing specific set, so all wildcarded

                // Generate the OFMatchWithSwDpid
                OFMatchWithSwDpid ofmatchsw = new OFMatchWithSwDpid(match, affectedSwitch.getId());

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
                frm.reconcileFlow(ofmr, EventPriority.HIGH);
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
        switchService = context.getServiceImpl(IOFSwitchService.class);
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
    public net.floodlightcontroller.core.IListener.Command reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList) {
        if (lds != null) {
            links = new HashMap<Link, LinkInfo>();
            // Get all the switch links from the topology
            if (lds.getLinks() != null) links.putAll(lds.getLinks());

            for (OFMatchReconcile ofmr : ofmRcList) {
                // We only care about OFMatchReconcile objects that wish to
                // update the path to a switch
                if (ofmr.rcAction.equals(OFMatchReconcile.ReconcileAction.UPDATE_PATH)) {
                    // Get the switch object from the OFMatchReconcile
                    IOFSwitch sw = switchService.getSwitch(ofmr.ofmWithSwDpid.getDpid());

                    // Map data structure that holds the invalid matches and the
                    // ingress ports of those matches
                    Map<OFPort, List<Match>> invalidBaseIngressAndMatches = new HashMap<OFPort, List<Match>>();

                    // Get the invalid flows
                    List<OFFlowStatsReply> flows = getFlows(sw, ofmr.outPort);

                    // Analyze all the flows with outPorts equaling the downed
                    // port and extract OFMatch's to trace back to neighbors
                    for (OFFlowStatsReply flow : flows) {
                    	// Create a reference to the match for ease
                    	for (OFFlowStatsEntry entry : flow.getEntries()) {
                    		Match match = entry.getMatch();

                    		// Here we utilize an index of input ports which point
                    		// to multiple invalid matches
                    		if (invalidBaseIngressAndMatches.containsKey(match.get(MatchField.IN_PORT)))
                    			// If the input port is already in the index, add
                    			// the match to it's list
                    			invalidBaseIngressAndMatches.get(match.get(MatchField.IN_PORT))
                    			.add(match);
                    		else {
                    			// Otherwise create a new list and add it to the
                    			// index
                    			List<Match> matches = new ArrayList<Match>();
                    			matches.add(match);
                    			invalidBaseIngressAndMatches.put(match.get(MatchField.IN_PORT), matches);
                    		}
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
                    Map<IOFSwitch, Map<OFPort, List<Match>>> neighborSwitches = new HashMap<IOFSwitch, Map<OFPort, List<Match>>>();

                    // Loop through all the links
                    for (Link link : links.keySet()) {
                        // Filter out links we care about
                        if (link.getDst() == sw.getId()) {
                            // Loop through the links to neighboring switches
                            // which have invalid flows
                            for (Entry<OFPort, List<Match>> invalidBaseIngressAndMatch : invalidBaseIngressAndMatches.entrySet()) {
                                // Find links on the network which link to the
                                // ingress ports that have invalidly routed
                                // flows
                                if (link.getDstPort() == invalidBaseIngressAndMatch.getKey()) {
                                    Map<OFPort, List<Match>> invalidNeighborOutportAndMatch = new HashMap<OFPort, List<Match>>();
                                    // Insert the neighbor's outPort to the base
                                    // switch and the invalid match
                                    invalidNeighborOutportAndMatch.put(link.getSrcPort(),
                                                                       invalidBaseIngressAndMatch.getValue());
                                    // Link a neighbor switch's invalid match
                                    // and outport to their Switch object
                                    neighborSwitches.put(switchService.getSwitch(link.getSrc()), invalidNeighborOutportAndMatch);
                                }
                            }
                        }
                    }
                    log.debug("We have " + neighborSwitches.size()
                              + " neighboring switches to deal with!");
                    // Loop through all the switches we found to have potential
                    // issues
                    for (IOFSwitch neighborSwitch : neighborSwitches.keySet()) {
                        log.debug("NeighborSwitch ID : " + neighborSwitch.getId());
                        if (neighborSwitches.get(neighborSwitch) != null)
                             deleteInvalidFlows(neighborSwitch, neighborSwitches.get(neighborSwitch));
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
    public List<OFFlowStatsReply> getFlows(IOFSwitch sw, OFPort outPort) {

        statsReply = new ArrayList<OFFlowStatsReply>();
        List<OFFlowStatsReply> values = null;
        Future<List<OFFlowStatsReply>> future;

        // Statistics request object for getting flows
        OFFlowStatsRequest req = sw.getOFFactory().buildFlowStatsRequest()
        		.setMatch(sw.getOFFactory().buildMatch().build())
        		.setOutPort(outPort)
        		.setTableId(TableId.ALL)
        		.build();

        try {
            // System.out.println(sw.getStatistics(req));
            future = sw.writeStatsRequest(req);
            values = future.get(10, TimeUnit.SECONDS);
            if (values != null) {
                for (OFFlowStatsReply stat : values) {
                    statsReply.add(stat);
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
    public void clearFlowMods(IOFSwitch sw, OFPort outPort) {
    	// Delete all pre-existing flows with the same output action port or
    	// outPort
    	Match match = sw.getOFFactory().buildMatch().build();
    	OFFlowDelete fm = sw.getOFFactory().buildFlowDelete()
    			.setMatch(match)
    			.setOutPort(outPort)
    			.build();
    	try {
    		sw.write(fm);
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
    public void clearFlowMods(IOFSwitch sw, Match match, OFPort outPort) {
        // Delete pre-existing flows with the same match, and output action port
        // or outPort
        OFFlowDelete fm = sw.getOFFactory().buildFlowDelete()
        		.setMatch(match)
        		.setOutPort(outPort)
        		.build();
        try {
            sw.write(fm);
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
    public void deleteInvalidFlows(IOFSwitch sw, Map<OFPort, List<Match>> invalidOutportAndMatch) {
        log.debug("Deleting invalid flows on switch : " + sw.getId());

        // A map that holds the input ports and invalid matches on a switch
        Map<OFPort, List<Match>> invalidNeighborIngressAndMatches = new HashMap<OFPort, List<Match>>();

        for (OFPort outPort : invalidOutportAndMatch.keySet()) {
            // Get the flows on the switch
            List<OFFlowStatsReply> flows = getFlows(sw, outPort);

            // Analyze all the flows with outPorts pointing to problematic route
            for (OFFlowStatsReply flow : flows) {
            	for (OFFlowStatsEntry entry : flow.getEntries()) {
            		// Loop through all the problematic matches
            		for (Match match : invalidOutportAndMatch.get(outPort)) {
            			// Compare the problematic matches with the match of the
            			// flow on the switch
            			if (entry.getMatch().get(MatchField.ETH_DST).equals(match.get(MatchField.ETH_DST))
            				&& entry.getMatch().get(MatchField.ETH_SRC).equals(match.get(MatchField.ETH_SRC))
            				&& entry.getMatch().get(MatchField.ETH_TYPE).equals(match.get(MatchField.ETH_TYPE))
            				&& entry.getMatch().get(MatchField.VLAN_VID).equals(match.get(MatchField.VLAN_VID))
            				&& entry.getMatch().get(MatchField.IPV4_DST).equals(match.get(MatchField.IPV4_DST))
            				&& entry.getMatch().get(MatchField.IP_PROTO).equals(match.get(MatchField.IP_PROTO))
            				&& entry.getMatch().get(MatchField.IPV4_SRC).equals(match.get(MatchField.IPV4_SRC))
            				&& entry.getMatch().get(MatchField.IP_DSCP).equals(match.get(MatchField.IP_DSCP)) // dscp and ecn replace tos
            				&& entry.getMatch().get(MatchField.IP_ECN).equals(match.get(MatchField.IP_ECN))) {

            					// Here we utilize an index of input ports which point
            					// to multiple invalid matches
            					if (invalidNeighborIngressAndMatches.containsKey(match.get(MatchField.IN_PORT)))
            						// If the input port is already in the index, add
            						// the match to it's list
            						invalidNeighborIngressAndMatches.get(match.get(MatchField.IN_PORT))
            						.add(match);
            					else {
            						// Otherwise create a new list and add it to the
            						// index
            						List<Match> matches = new ArrayList<Match>();
            						matches.add(match);
            						invalidNeighborIngressAndMatches.put(match.get(MatchField.IN_PORT), matches);
            					}
            					// Remove flows from the switch with the invalid match
            					// and outPort
            					clearFlowMods(sw, entry.getMatch(), outPort);
            				}
            		}
            	}
            }

            // Create a list of neighboring switches we need to check for
            // invalid flows
            Map<IOFSwitch, Map<OFPort, List<Match>>> neighborSwitches = new HashMap<IOFSwitch, Map<OFPort, List<Match>>>();

            // Loop through all the links
            for (Link link : links.keySet()) {
                // Filter out links we care about
                if (link.getDst().equals(sw.getId())) {
                    // Loop through the ingressPorts that are involved in
                    // invalid flows on neighboring switches
                    for (Entry<OFPort, List<Match>> ingressPort : invalidNeighborIngressAndMatches.entrySet()) {
                        // Filter out invalid links by matching the link
                        // destination port to our invalid flows ingress port
                        if (link.getDstPort().equals(ingressPort.getKey())) {
                            // Generate a match and outPort map since I don't
                            // want to create an object
                            Map<OFPort, List<Match>> invalidNeighborOutportAndMatch = new HashMap<OFPort, List<Match>>();
                            invalidNeighborOutportAndMatch.put(link.getSrcPort(),
                                                               ingressPort.getValue());
                            // Link a neighbor switch's invalid match and
                            // outport to their Switch object
                            neighborSwitches.put(switchService.getSwitch(link.getSrc()), invalidNeighborOutportAndMatch);
                        }
                    }
                }
            }
            log.debug("We have " + neighborSwitches.size() + " neighbors to deal with!");

            // Loop through all the neighbor switches we found to have
            // invalid matches
            for (IOFSwitch neighborSwitch : neighborSwitches.keySet()) {
                log.debug("NeighborSwitch ID : " + neighborSwitch.getId());
                // Recursively seek out and delete invalid flows on the
                // neighbor switch
                deleteInvalidFlows(neighborSwitch, neighborSwitches.get(neighborSwitch));
            }
        }
    }
}
