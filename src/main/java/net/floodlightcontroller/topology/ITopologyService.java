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

import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Link;

public interface ITopologyService extends IFloodlightService  {

	/*******************************************************
	 * GENERAL TOPOLOGY FUNCTIONS
	 *******************************************************/
	
	/**
	 * Add a listener to be notified upon topology events.
	 * @param listener
	 */
	public void addListener(ITopologyListener listener);

	/**
	 * Retrieve the last time the topology was computed.
	 * @return
	 */
	public Date getLastUpdateTime();

	/*******************************************************
	 * PORT FUNCTIONS
	 *******************************************************/
	
	/**
	 * Determines if a device can be learned/located on this switch+port.
	 * @param switchid
	 * @param port
	 * @return
	 */
	public boolean isAttachmentPointPort(DatapathId switchid, OFPort port);
	
	/**
	 * Determines if a device can be learned/located on this switch+port.
	 * @param switchid
	 * @param port
	 * @return
	 */
	public boolean isAttachmentPointPort(DatapathId switchid, OFPort port, boolean tunnelEnabled);

	/**
	 * Determines whether or not a switch+port is a part of
	 * a link or is a leaf of the network.
	 * @param sw
	 * @param p
	 * @return
	 */
   	public boolean isEdge(DatapathId sw, OFPort p);
   	
	/**
	 * Get list of ports that can SEND a broadcast packet.
	 * @param sw
	 * @return
	 */
	public Set<OFPort> getSwitchBroadcastPorts(DatapathId sw);
	
	/**
	 * Checks if the switch+port is in the broadcast tree.
	 * @param sw
	 * @param port
	 * @return
	 */
	public boolean isBroadcastDomainPort(DatapathId sw, OFPort port);
	
	/**
	 * Checks if the switch+port is in the broadcast tree.
	 * @param sw
	 * @param port
	 * @param tunnelEnabled
	 * @return
	 */
	public boolean isBroadcastDomainPort(DatapathId sw, OFPort port, boolean tunnelEnabled);

	/**
	 * Determines if the switch+port is blocked. If blocked, it
	 * should not be allowed to send/receive any traffic.
	 * @param sw
	 * @param port
	 * @return
	 */
	public boolean isAllowed(DatapathId sw, OFPort portId);
	
	/**
	 * Determines if the switch+port is blocked. If blocked, it
	 * should not be allowed to send/receive any traffic.
	 * @param sw
	 * @param port
	 * @param tunnelEnabled
	 * @return
	 */
	public boolean isAllowed(DatapathId sw, OFPort portId, boolean tunnelEnabled);

	/**
	 * Indicates if an attachment point on the new switch port is consistent
	 * with the attachment point on the old switch port or not.
	 * @param oldSw
	 * @param oldPort
	 * @param newSw
	 * @param newPort
	 * @return
	 */
	public boolean isConsistent(DatapathId oldSw, OFPort oldPort, 
			DatapathId newSw, OFPort newPort);
	
	/**
	 * Indicates if an attachment point on the new switch port is consistent
	 * with the attachment point on the old switch port or not.
	 * @param oldSw
	 * @param oldPort
	 * @param newSw
	 * @param newPort
	 * @param tunnelEnabled
	 * @return
	 */
	public boolean isConsistent(DatapathId oldSw, OFPort oldPort,
			DatapathId newSw, OFPort newPort, boolean tunnelEnabled);

	/**
	 * Indicates if the two switch ports are connected to the same
	 * broadcast domain or not.
	 * @param s1
	 * @param p1
	 * @param s2
	 * @param p2
	 * @return
	 */
	public boolean isInSameBroadcastDomain(DatapathId s1, OFPort p1,
			DatapathId s2, OFPort p2);
	
	/**
	 * Indicates if the two switch ports are connected to the same
	 * broadcast domain or not.
	 * @param s1
	 * @param p1
	 * @param s2
	 * @param p2
	 * @param tunnelEnabled
	 * @return
	 */
	public boolean isInSameBroadcastDomain(DatapathId s1, OFPort p1,
			DatapathId s2, OFPort p2,
			boolean tunnelEnabled);

	/** 
	 * Get broadcast ports on a target switch for a given attachment point
	 * point port.
	 * @param targetSw
	 * @param src
	 * @param srcPort
	 * @return
	 */
	public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort);

	/** 
	 * Get broadcast ports on a target switch for a given attachment point
	 * point port.
	 * @param targetSw
	 * @param src
	 * @param srcPort
	 * @param tunnelEnabled
	 * @return
	 */
	public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort, boolean tunnelEnabled);

	/**
	 * Checks if the given switch+port is allowed to receive broadcast packets.
	 * @param sw
	 * @param portId
	 * @return
	 */
	public boolean isIncomingBroadcastAllowed(DatapathId sw, OFPort portId);
	
	/**
	 * Checks if the given switch+port is allowed to receive broadcast packets.
	 * @param sw
	 * @param portId
	 * @param tunnelEnabled
	 * @return
	 */
	public boolean isIncomingBroadcastAllowed(DatapathId sw, OFPort portId, boolean tunnelEnabled);
	
	/**
	 * If the src broadcast domain port is not allowed for incoming
	 * broadcast, this method provides the topologically equivalent
	 * incoming broadcast-allowed src port.
	 * @param src
	 * @param dst
	 * @return
	 */
	public NodePortTuple getAllowedIncomingBroadcastPort(DatapathId src, OFPort srcPort);

	/**
	 * If the src broadcast domain port is not allowed for incoming
	 * broadcast, this method provides the topologically equivalent
	 * incoming broadcast-allowed src port.
	 * @param src
	 * @param dst
	 * @param tunnelEnabled
	 * @return
	 */
	public NodePortTuple getAllowedIncomingBroadcastPort(DatapathId src, OFPort srcPort, boolean tunnelEnabled);

	/**
	 * Gets the set of ports that belong to a broadcast domain.
	 * @return
	 */
	public Set<NodePortTuple> getBroadcastDomainPorts();
	
	/**
	 * Gets the set of ports that belong to tunnels.
	 * @return
	 */
	public Set<NodePortTuple> getTunnelPorts();

	/**
	 * Returns a set of blocked ports.  The set of blocked
	 * ports is the union of all the blocked ports across all
	 * instances.
	 * @return
	 */
	public Set<NodePortTuple> getBlockedPorts();

	/**
	 * Returns the enabled, non quarantined ports of the given switch. Returns
	 * an empty set if switch doesn't exists, doesn't have any enabled port, or
	 * has only quarantined ports. Will never return null.
	 */
	public Set<OFPort> getPorts(DatapathId sw);
	
	/*******************************************************
	 * ISLAND/DOMAIN/CLUSTER FUNCTIONS
	 *******************************************************/
	
	/**
	 * Return the ID of the domain/island/cluster this switch is
	 * a part of. The ID is the lowest switch DPID within the domain.
	 * @param switchId
	 * @return
	 */
	public DatapathId getOpenflowDomainId(DatapathId switchId);
	
	/**
	 * Return the ID of the domain/island/cluster this switch is
	 * a part of. The ID is the lowest switch DPID within the domain.
	 * @param switchId
	 * @return
	 */
	public DatapathId getOpenflowDomainId(DatapathId switchId, boolean tunnelEnabled);
	
	/**
	 * Determines if two switches are in the same domain/island/cluster.
	 * @param switch1
	 * @param switch2
	 * @return true if the switches are in the same cluster
	 */
	public boolean inSameOpenflowDomain(DatapathId switch1, DatapathId switch2);
	
	/**
	 * Determines if two switches are in the same domain/island/cluster.
	 * @param switch1
	 * @param switch2
	 * @param tunnelEnabled
	 * @return true if the switches are in the same cluster
	 */
	public boolean inSameOpenflowDomain(DatapathId switch1, DatapathId switch2, boolean tunnelEnabled);

	/**
	 * Gets all switches in the same domain/island/cluster as the switch provided.
	 * @param switchDPID
	 * @return
	 */
	public Set<DatapathId> getSwitchesInOpenflowDomain(DatapathId switchDPID);
	
	/**
	 * Gets all switches in the same domain/island/cluster as the switch provided.
	 * @param switchDPID
	 * @param tunnelEnabled
	 * @return
	 */
	public Set<DatapathId> getSwitchesInOpenflowDomain(DatapathId switchDPID, boolean tunnelEnabled);
	
	/*******************************************************
	 * LINK FUNCTIONS
	 *******************************************************/
	
	/**
	 * Get all network links, including intra-cluster and inter-cluster links. 
	 * Links are grouped for each DatapathId separately.
	 * @return
	 */
	public Map<DatapathId, Set<Link>> getAllLinks();
	
	/**
	 * Gets a list of ports on a given switch that are part of known links.
	 * @param sw
	 * @return
	 */
	public Set<OFPort> getPortsWithLinks(DatapathId sw);
	
	/**
	 * Gets a list of ports on a given switch that are part of known links.
	 * @param sw
	 * @param tunnelEnabled
	 * @return
	 */
	public Set<OFPort> getPortsWithLinks(DatapathId sw, boolean tunnelEnabled);

	/*******************************************************
	 * ROUTING FUNCTIONS
	 *******************************************************/
	
	/**
	 * If trying to route a packet ingress a source switch+port to a destination
	 * switch+port, retrieve the egress source switch+port leading to the destination.
	 * @param src
	 * @param srcPort
	 * @param dst
	 * @param dstPort
	 * @return
	 */
	public NodePortTuple getOutgoingSwitchPort(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort);

	/**
	 * If trying to route a packet ingress a source switch+port to a destination
	 * switch+port, retrieve the egress source switch+port leading to the destination.
	 * @param src
	 * @param srcPort
	 * @param dst
	 * @param dstPort
	 * @param tunnelEnabled
	 * @return
	 */
	public NodePortTuple getOutgoingSwitchPort(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort, boolean tunnelEnabled);

	/**
	 * If trying to route a packet ingress a source switch+port to a destination
	 * switch+port, retrieve the ingress destination switch+port leading to the destination.
	 * @param src
	 * @param srcPort
	 * @param dst
	 * @param dstPort
	 * @return
	 */
	public NodePortTuple getIncomingSwitchPort(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort);
	
	/**
	 * If trying to route a packet ingress a source switch+port to a destination
	 * switch+port, retrieve the ingress destination switch+port leading to the destination.
	 * @param src
	 * @param srcPort
	 * @param dst
	 * @param dstPort
	 * @param tunnelEnabled
	 * @return
	 */
	public NodePortTuple getIncomingSwitchPort(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort, boolean tunnelEnabled);

	/**
	 * If the dst is not allowed by the higher-level topology,
	 * this method provides the topologically equivalent broadcast port.
	 * @param src
	 * @param dst
	 * @return the allowed broadcast port
	 */
	public NodePortTuple getAllowedOutgoingBroadcastPort(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort);
	
	/**
	 * If the dst is not allowed by the higher-level topology,
	 * this method provides the topologically equivalent broadcast port.
	 * @param src
	 * @param dst
	 * @param tunnelEnabled
	 * @return the allowed broadcast port
	 */
	public NodePortTuple getAllowedOutgoingBroadcastPort(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort, boolean tunnelEnabled);
}