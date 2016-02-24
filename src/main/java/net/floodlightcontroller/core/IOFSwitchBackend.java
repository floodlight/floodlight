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

package net.floodlightcontroller.core;

import java.util.Collection;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionsReply;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.types.TableId;

import net.floodlightcontroller.util.OrderedCollection;

/**
 * An openflow switch connecting to the controller.  This interface offers
 * methods for interacting with switches using OpenFlow, and retrieving
 * information about the switches.
 */
public interface IOFSwitchBackend extends IOFSwitch {
    /**
     * Set the netty Channel this switch instance is associated with
     * Called immediately after instantiation
     * @param channel
     */
    void registerConnection(IOFConnectionBackend connection);

    /**
     * Remove the netty Channels associated with this switch
     * @param channel
     */
    void removeConnections();

    /**
     * Remove the netty Channel belonging to the specified connection
     * @param connection
     */
    void removeConnection(IOFConnectionBackend connection);

    /**
     * Set the OFFeaturesReply message returned by the switch during initial
     * handshake.
     * @param featuresReply
     */
    void setFeaturesReply(OFFeaturesReply featuresReply);

    /**
     * Add or modify a switch port.
     * This is called by the core controller
     * code in response to a OFPortStatus message. It should not typically be
     * called by other floodlight applications.
     *
     * OFPPR_MODIFY and OFPPR_ADD will be treated as equivalent. The OpenFlow
     * spec is not clear on whether portNames are portNumbers are considered
     * authoritative identifiers. We treat portNames <-> portNumber mappings
     * as fixed. If they change, we delete all previous conflicting ports and
     * add all new ports.
     *
     * @param ps the port status message
     * @return the ordered Collection of changes "applied" to the old ports
     * of the switch according to the PortStatus message. A single PortStatus
     * message can result in multiple changes.
     * If portName <-> portNumber mappings have
     * changed, the iteration order ensures that delete events for old
     * conflicting appear before before events adding new ports
     */
    OrderedCollection<PortChangeEvent> processOFPortStatus(OFPortStatus ps);
    
    /**
     * Add or modify a switch table.
     * This is called by the core controller code in response to an OFTableFeaturesReply message.
     * It should not typically be called by other Floodlight modules or applications.
     * 
     * @param tf, The table features to be updated.
     */
    void processOFTableFeatures(List<OFTableFeaturesStatsReply> replies);

    /**
     * Compute the changes that would be required to replace the old ports
     * of this switch with the new ports
     * @param ports new ports to set
     * @return the ordered collection of changes "applied" to the old ports
     * of the switch in order to set them to the new set.
     * If portName <-> portNumber mappings have
     * changed, the iteration order ensures that delete events for old
     * conflicting appear before before events adding new ports
     */
    OrderedCollection<PortChangeEvent>
            comparePorts(Collection<OFPortDesc> ports);

    /**
     * Replace the ports of this switch with the given ports.
     * @param ports new ports to set
     * @return the ordered collection of changes "applied" to the old ports
     * of the switch in order to set them to the new set.
     * If portName <-> portNumber mappings have
     * changed, the iteration order ensures that delete events for old
     * conflicting appear before before events adding new ports
     */
    OrderedCollection<PortChangeEvent>
            setPorts(Collection<OFPortDesc> ports);

    /***********************************************
     * The following method can be overridden by
     * specific types of switches
     ***********************************************
     */

    /**
     * Set the SwitchProperties based on it's description
     * @param description
     */
    void setSwitchProperties(SwitchDescription description);

    /**
     * Set the flow table full flag in the switch
     */
    void setTableFull(boolean isFull);

    /**
     * Start this switch driver's sub handshake. This might be a no-op but
     * this method must be called at least once for the switch to be become
     * ready.
     * This method must only be called from the I/O thread
     * @throws SwitchDriverSubHandshakeAlreadyStarted if the sub-handshake has
     * already been started
     */
    void startDriverHandshake();

    /**
     * Check if the sub-handshake for this switch driver has been completed.
     * This method can only be called after startDriverHandshake()
     *
     * This methods must only be called from the I/O thread
     * @return true if the sub-handshake has been completed. False otherwise
     * @throws SwitchDriverSubHandshakeNotStarted if startDriverHandshake() has
     * not been called yet.
     */
    boolean isDriverHandshakeComplete();

    /**
     * Pass the given OFMessage to the driver as part of this driver's
     * sub-handshake. Must not be called after the handshake has been completed
     * This methods must only be called from the I/O thread
     * @param m The message that the driver should process
     * @throws SwitchDriverSubHandshakeCompleted if isDriverHandshake() returns
     * false before this method call
     * @throws SwitchDriverSubHandshakeNotStarted if startDriverHandshake() has
     * not been called yet.
     */
    void processDriverHandshakeMessage(OFMessage m);

    void setPortDescStats(OFPortDescStatsReply portDescStats);

    /**
     * Cancel all pending request
     */
    void cancelAllPendingRequests();

    /** the the current HA role of this switch */
    void setControllerRole(OFControllerRole role);

    void setStatus(SwitchStatus switchStatus);

    /**
     * Updates the switch's mapping of controller connections
     * @param controllerCxnsReply the controller connections message sent from the switch
     */
    void updateControllerConnections(OFBsnControllerConnectionsReply controllerCxnsReply);

    /**
     * Determines whether there is another master controller that the switches are
     * connected to by looking at the controller connections.
     * @return true if another viable master exists
     */
    boolean hasAnotherMaster();
    
    /**
     * In OF1.3+ switches, the table miss behavior is defined by a flow.
     * We assume the default behavior is to forward to the controller, but
     * not all tables need that behavior if a limited set of tables are used.
     * So, we can cap the number of tables we set this flow in to reduce
     * clutter in API output and to reduce memory consumption on the switch.
     * 
     * This gets the TableId cap set for this particular switch.
     * 
     * @return, the highest TableId that should receive a table-miss flow
     */
    TableId getMaxTableForTableMissFlow();
    
    /**
     * In OF1.3+ switches, the table miss behavior is defined by a flow.
     * We assume the default behavior is to forward to the controller, but
     * not all tables need that behavior if a limited set of tables are used.
     * So, we can cap the number of tables we set this flow in to reduce
     * clutter in API output and to reduce memory consumption on the switch.
     * 
     * This sets the TableId cap set for this particular switch. If the max
     * desired is higher than the number of tables this switch supports, the
     * max table supported will be used:
     * 
     * set_max_table = max_supported <= max ? max_supported-1 : max
     * 
 	 * @param max, the highest TableId that should receive a table-miss flow
     * @return the TableId set as the highest
     */
    TableId setMaxTableForTableMissFlow(TableId max);
}
