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

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.linkdiscovery.Link;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Date;
import java.util.Map;
import java.util.Set;

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
     * Remove a listener to stop receiving topology events.
     * @param listener
     */
    public void removeListener(ITopologyListener listener);

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
	public boolean isBroadcastPort(DatapathId sw, OFPort port);

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
	 * Get broadcast ports on a target switch for a given attachment point
	 * point port.
	 * @param targetSw
	 * @param src
	 * @param srcPort
	 * @return
	 */
	public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort);

	/**
	 * Checks if the given switch+port is allowed to send or receive broadcast packets.
	 * @param sw
	 * @param portId
	 * @return
	 */
	public boolean isBroadcastAllowed(DatapathId sw, OFPort portId);

	/**
	 * Gets the set of ports that participate in the broadcast within each archipelago
	 * @return
	 */
	public Set<NodePortTuple> getAllBroadcastPorts();
	
	/**
     * Gets the set of ports that participate in the broadcast trees for the
     * archipelago in which the swtich belongs
     * @param sw
     * @return
     */
    public Set<NodePortTuple> getBroadcastPortsInArchipelago(DatapathId sw);
	
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
     * Determines if the switch+port is blocked. If blocked, it
     * should not be allowed to send/receive any traffic.
     * @param sw
     * @param portId
     * @return
     */
    public boolean isNotBlocked(DatapathId sw, OFPort portId);

	/**
	 * Returns the enabled, non quarantined ports of the given switch. Returns
	 * an empty set if switch doesn't exists, doesn't have any enabled port, or
	 * has only quarantined ports. Will never return null.
	 */
	public Set<OFPort> getPorts(DatapathId sw);
	
	/*******************************************************
	 * CLUSTER AND ARCHIPELAGO FUNCTIONS
	 *******************************************************/
	
	/**
	 * Return the ID of the domain/island/cluster this switch is
	 * a part of. The ID is the lowest switch DPID within the domain.
	 * @param switchId
	 * @return
	 */
	public DatapathId getClusterId(DatapathId switchId);
	
	/**
     * Return the ID of the archipelago this switch is
     * a part of. The ID is the lowest cluster DPID within the archipelago.
     * @param switchId
     * @return
     */
    public DatapathId getArchipelagoId(DatapathId switchId);
    
    /**
     * Return all archipelagos
     * @return
     */
    public Set<DatapathId> getArchipelagoIds();
	
	/**
	 * Determines if two switches are in the same domain/island/cluster.
	 * @param s1
	 * @param s2
	 * @return true if the switches are in the same cluster
	 */
	public boolean isInSameCluster(DatapathId s1, DatapathId s2);
	
	/**
     * Determines if two switches are in the same archipelago.
     * @param s1
     * @param s2
     * @return true if the switches are in the same archipelago
     */
    public boolean isInSameArchipelago(DatapathId s1, DatapathId s2);

	/**
	 * Gets all switches in the same domain/island/cluster as the switch provided.
	 * @param sw
	 * @return
	 */
	public Set<DatapathId> getSwitchesInCluster(DatapathId sw);
	
	/**
     * Gets all cluster IDs in the same archipelago as the switch provided.
     * @param sw
     * @return
     */
    public Set<DatapathId> getClusterIdsInArchipelago(DatapathId sw);
    
	
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
	 * Get all links that are:
	 * --external
	 * --detected via BDDP
	 * --connect two clusters
	 * @return
	 */
	public Set<Link> getExternalInterClusterLinks();
	
	/**
     * Get all links that are:
     * --internal
     * --detected via LLDP
     * --connect two clusters
     * @return
     */
    public Set<Link> getInternalInterClusterLinks();
}