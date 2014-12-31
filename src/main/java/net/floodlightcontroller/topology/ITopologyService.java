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
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface ITopologyService extends IFloodlightService  {

	public void addListener(ITopologyListener listener);

	public Date getLastUpdateTime();

	/**
	 * Query to determine if devices must be learned on a given switch port.
	 */
	public boolean isAttachmentPointPort(DatapathId switchid, OFPort port);
	public boolean isAttachmentPointPort(DatapathId switchid, OFPort port,
			boolean tunnelEnabled);

	public DatapathId getOpenflowDomainId(DatapathId switchId);
	public DatapathId getOpenflowDomainId(DatapathId switchId, boolean tunnelEnabled);

	/**
	 * Returns the identifier of the L2 domain of a given switch.
	 * @param switchId The DPID of the switch in long form
	 * @return The DPID of the switch that is the key for the cluster
	 */
	public DatapathId getL2DomainId(DatapathId switchId);
	public DatapathId getL2DomainId(DatapathId switchId, boolean tunnelEnabled);

	/**
	 * Queries whether two switches are in the same cluster.
	 * @param switch1
	 * @param switch2
	 * @return true if the switches are in the same cluster
	 */
	public boolean inSameOpenflowDomain(DatapathId switch1, DatapathId switch2);
	public boolean inSameOpenflowDomain(DatapathId switch1, DatapathId switch2,
			boolean tunnelEnabled);

	public Set<DatapathId> getSwitchesInOpenflowDomain(DatapathId switchDPID);
	public Set<DatapathId> getSwitchesInOpenflowDomain(DatapathId switchDPID,
			boolean tunnelEnabled);

	/**
	 * Queries whether two switches are in the same island.
	 * Currently, island and cluster are the same. In future,
	 * islands could be different than clusters.
	 * @param switch1
	 * @param switch2
	 * @return True of they are in the same island, false otherwise
	 */
	public boolean inSameL2Domain(DatapathId switch1, DatapathId switch2);
	public boolean inSameL2Domain(DatapathId switch1, DatapathId switch2,
			boolean tunnelEnabled);

	public boolean isBroadcastDomainPort(DatapathId sw, OFPort port);
	public boolean isBroadcastDomainPort(DatapathId sw, OFPort port,
			boolean tunnelEnabled);


	public boolean isAllowed(DatapathId sw, OFPort portId);
	public boolean isAllowed(DatapathId sw, OFPort portId, boolean tunnelEnabled);

	/**
	 * Indicates if an attachment point on the new switch port is consistent
	 * with the attachment point on the old switch port or not.
	 */
	public boolean isConsistent(DatapathId oldSw, OFPort oldPort,
			DatapathId newSw, OFPort newPort);
	public boolean isConsistent(DatapathId oldSw, OFPort oldPort,
			DatapathId newSw, OFPort newPort,
			boolean tunnelEnabled);

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
	public boolean isInSameBroadcastDomain(DatapathId s1, OFPort p1,
			DatapathId s2, OFPort p2,
			boolean tunnelEnabled);

	/**
	 * Gets a list of ports on a given switch that are known to topology.
	 * @param sw The switch DPID in long
	 * @return The set of ports on this switch
	 */
	public Set<OFPort> getPortsWithLinks(DatapathId sw);
	public Set<OFPort> getPortsWithLinks(DatapathId sw, boolean tunnelEnabled);

	/** Get broadcast ports on a target switch for a given attachmentpoint
	 * point port.
	 */
	public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort);

	public Set<OFPort> getBroadcastPorts(DatapathId targetSw, DatapathId src, OFPort srcPort,
			boolean tunnelEnabled);

	/**
	 *
	 */
	public boolean isIncomingBroadcastAllowed(DatapathId sw, OFPort portId);
	public boolean isIncomingBroadcastAllowed(DatapathId sw, OFPort portId,
			boolean tunnelEnabled);


	/** Get the proper outgoing switchport for a given pair of src-dst
	 * switchports.
	 */
	public NodePortTuple getOutgoingSwitchPort(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort);


	public NodePortTuple getOutgoingSwitchPort(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort,
			boolean tunnelEnabled);


	public NodePortTuple getIncomingSwitchPort(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort);
	public NodePortTuple getIncomingSwitchPort(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort,
			boolean tunnelEnabled);

	/**
	 * If the dst is not allowed by the higher-level topology,
	 * this method provides the topologically equivalent broadcast port.
	 * @param src
	 * @param dst
	 * @return the allowed broadcast port
	 */
	public NodePortTuple
	getAllowedOutgoingBroadcastPort(DatapathId src,
			OFPort srcPort,
			DatapathId dst,
			OFPort dstPort);

	public NodePortTuple
	getAllowedOutgoingBroadcastPort(DatapathId src,
			OFPort srcPort,
			DatapathId dst,
			OFPort dstPort,
			boolean tunnelEnabled);

	/**
	 * If the src broadcast domain port is not allowed for incoming
	 * broadcast, this method provides the topologically equivalent
	 * incoming broadcast-allowed
	 * src port.
	 * @param src
	 * @param dst
	 * @return the allowed broadcast port
	 */
	public NodePortTuple
	getAllowedIncomingBroadcastPort(DatapathId src,
			OFPort srcPort);

	public NodePortTuple
	getAllowedIncomingBroadcastPort(DatapathId src,
			OFPort srcPort,
			boolean tunnelEnabled);


	/**
	 * Gets the set of ports that belong to a broadcast domain.
	 * @return The set of ports that belong to a broadcast domain.
	 */
	public Set<NodePortTuple> getBroadcastDomainPorts();
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
}
