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

package net.floodlightcontroller.linkdiscovery;

import java.util.Map;
import java.util.Set;

import org.openflow.protocol.OFPacketOut;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.NodePortTuple;


public interface ILinkDiscoveryService extends IFloodlightService {

    /**
     * Returns if a given switchport is a tunnel endpoint or not
     */
    public boolean isTunnelPort(long sw, short port);

    /**
     * Retrieves a map of all known link connections between OpenFlow switches
     * and the associated info (valid time, port states) for the link.
     */
    public Map<Link, LinkInfo> getLinks();

    /**
     * Retrieves the link info for a given link
     * @param link link for which the link info should be returned
     * @return the link info for the given link
     */
    public LinkInfo getLinkInfo(Link link);

    /**
     * Returns link type of a given link
     * @param info
     * @return
     */
    public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info);

    /**
     * Returns OFPacketOut which contains the LLDP data corresponding
     * to switchport (sw, port). PacketOut does not contain actions.
     * PacketOut length includes the minimum length and data length.
     */
    public OFPacketOut generateLLDPMessage(long sw, short port,
                                           boolean isStandard,
                                           boolean isReverse);

    /**
     * Returns an unmodifiable map from switch id to a set of all links with it
     * as an endpoint.
     */
    public Map<Long, Set<Link>> getSwitchLinks();

    /**
     * Adds a listener to listen for ILinkDiscoveryService messages
     * @param listener The listener that wants the notifications
     */
    public void addListener(ILinkDiscoveryListener listener);

    /**
     * Retrieves a set of all switch ports on which lldps are suppressed.
     */
    public Set<NodePortTuple> getSuppressLLDPsInfo();

    /**
     * Adds a switch port to suppress lldp set. LLDPs and BDDPs will not be sent
     * out, and if any are received on this port then they will be dropped.
     */
    public void AddToSuppressLLDPs(long sw, short port);

    /**
     * Removes a switch port from suppress lldp set
     */
    public void RemoveFromSuppressLLDPs(long sw, short port);

    /**
     * Get the set of quarantined ports on a switch
     */
    public Set<Short> getQuarantinedPorts(long sw);

    /**
     * Get the status of auto port fast feature.
     */
    public boolean isAutoPortFastFeature();

    /**
     * Set the state for auto port fast feature.
     * @param autoPortFastFeature
     */
    public void setAutoPortFastFeature(boolean autoPortFastFeature);

    /**
     * Get the map of node-port tuples from link DB
     */
    public Map<NodePortTuple, Set<Link>> getPortLinks();

    /**
     * addMACToIgnoreList is a service provided by LinkDiscovery to ignore
     * certain packets early in the packet-in processing chain. Since LinkDiscovery
     * is first in the packet-in processing pipeline, it can efficiently drop these
     * packets. Currently these packets are identified only by their source MAC address.
     *
     * Add a MAC address range to ignore list. All packet ins from this range
     * will be dropped - use with care!
     * @param mac The base MAC address that is to be ignored
     * @param ignoreBits The number of LSBs to ignore. A value of 0 will add
     *        only one MAC address 'mac' to ignore list. A value of 48 will add
     *        ALL MAC addresses to the ignore list. This will cause a drop of
     *        ALL packet ins.
     */
    public void addMACToIgnoreList(long mac, int ignoreBits);
}
