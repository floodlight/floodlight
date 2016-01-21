/**
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

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.internal.OFConnection;
import net.floodlightcontroller.core.internal.TableFeatures;
import net.floodlightcontroller.core.web.serializers.IOFSwitchSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
/**
 * An openflow switch connecting to the controller.  This interface offers
 * methods for interacting with switches using OpenFlow, and retrieving
 * information about the switches.
 */
@JsonSerialize(using=IOFSwitchSerializer.class)
public interface IOFSwitch extends IOFMessageWriter {
    // Attribute keys
    // These match our YANG schema, make sure they are in sync
    public static final String SWITCH_DESCRIPTION_FUTURE = "description-future";
    public static final String SWITCH_DESCRIPTION_DATA = "description-data";
    public static final String SWITCH_SUPPORTS_NX_ROLE = "supports-nx-role";
    public static final String PROP_FASTWILDCARDS = "fast-wildcards";
    public static final String PROP_REQUIRES_L3_MATCH = "requires-l3-match";
    public static final String PROP_SUPPORTS_OFPP_TABLE = "supports-ofpp-table";
    public static final String PROP_SUPPORTS_OFPP_FLOOD = "supports-ofpp-flood";
    public static final String PROP_SUPPORTS_NETMASK_TBL = "supports-netmask-table";
    public static final String PROP_SUPPORTS_BSN_SET_TUNNEL_DST_ACTION =
            "supports-set-tunnel-dst-action";
    public static final String PROP_SUPPORTS_NX_TTL_DECREMENT = "supports-nx-ttl-decrement";

    public enum SwitchStatus {
       /** this switch is in the process of being handshaked. Initial State. */
       HANDSHAKE(false),
       /** the OF channel to this switch is currently in SLAVE role - the switch will not accept
        *  state-mutating messages from this controller node.
        */
       SLAVE(true),
       /** the OF channel to this switch is currently in MASTER (or EQUALS) role - the switch is
        *  controllable from this controller node.
        */
       MASTER(true),
       /** the switch has been sorted out and quarantined by the handshake. It does not show up
        *  in normal switch listings
        */
       QUARANTINED(false),
       /** the switch has disconnected, and will soon be removed from the switch database */
       DISCONNECTED(false);

       private final boolean visible;

       SwitchStatus(boolean visible) {
        this.visible = visible;
       }

       /** wether this switch is currently visible for normal operation */
       public boolean isVisible() {
            return visible;
       }

       /** wether this switch is currently ready to be controlled by this controller */
       public boolean isControllable() {
            return this == MASTER;
       }
    }

    SwitchStatus getStatus();

    /**
     * Returns switch features from features Reply
     * @return
     */
    long getBuffers();

    /**
     * Disconnect all the switch's channels and mark the switch as disconnected
     */
    void disconnect();

    Set<OFActionType> getActions();

    Set<OFCapabilities> getCapabilities();

    /**
     * Get the specific TableIds according to the ofp_table_features.
     * Not all switches have sequential TableIds, so this will give the
     * specific TableIds used by the switch.
     * @return
     */
    Collection<TableId> getTables();

    /**
     * @return a copy of the description statistics for this switch
     */
    SwitchDescription getSwitchDescription();

    /**
     * Get the IP address of the remote (switch) end of the connection
     * @return the inet address
     */
    SocketAddress getInetAddress();

    /**
     * Get list of all enabled ports. This will typically be different from
     * the list of ports in the OFFeaturesReply, since that one is a static
     * snapshot of the ports at the time the switch connected to the controller
     * whereas this port list also reflects the port status messages that have
     * been received.
     * @return Unmodifiable list of ports not backed by the underlying collection
     */
    Collection<OFPortDesc> getEnabledPorts();

    /**
     * Get list of the port numbers of all enabled ports. This will typically
     * be different from the list of ports in the OFFeaturesReply, since that
     * one is a static snapshot of the ports at the time the switch connected
     * to the controller whereas this port list also reflects the port status
     * messages that have been received.
     * @return Unmodifiable list of ports not backed by the underlying collection
     */
    Collection<OFPort> getEnabledPortNumbers();

    /**
     * Retrieve the port object by the port number. The port object
     * is the one that reflects the port status updates that have been
     * received, not the one from the features reply.
     * @param portNumber
     * @return port object
     */
    OFPortDesc getPort(OFPort portNumber);

    /**
     * Retrieve the port object by the port name. The port object
     * is the one that reflects the port status updates that have been
     * received, not the one from the features reply.
     * Port names are case insentive
     * @param portName
     * @return port object
     */
    OFPortDesc getPort(String portName);

    /**
     * Get list of all ports. This will typically be different from
     * the list of ports in the OFFeaturesReply, since that one is a static
     * snapshot of the ports at the time the switch connected to the controller
     * whereas this port list also reflects the port status messages that have
     * been received.
     * @return Unmodifiable list of ports
     */
    Collection<OFPortDesc> getPorts();

    /**
     * This is mainly for the benefit of the DB code which currently has the
     * requirement that list elements be sorted by key. Hopefully soon the
     * DB will handle the sorting automatically or not require it at all, in
     * which case we could get rid of this method.
     * @return
     */
    Collection<OFPortDesc> getSortedPorts();

    /**
     * @param portNumber
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    boolean portEnabled(OFPort portNumber);

    /**
     * @param portNumber
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    boolean portEnabled(String portName);

    /**
     * Check is switch is connected
     * @return Whether or not this switch is connected
     */
    boolean isConnected();

    /**
     * Retrieves the date the switch connected to this controller
     * @return the date
     */
    Date getConnectedSince();

    /**
     * Get the datapathId of the switch
     * @return
     */
    DatapathId getId();

    /**
     * Retrieves attributes of this switch
     * @return
     */
    Map<Object, Object> getAttributes();

    /**
     * Check if the switch is active. I.e., the switch is connected to this
     * controller and is in master role
     * @return
     */
    boolean isActive();

    /**
     * Get the current role of the controller for the switch
     * @return the role of the controller
     */
    OFControllerRole getControllerRole();

    /**
     * Checks if a specific switch property exists for this switch
     * @param name name of property
     * @return value for name
     */
    boolean hasAttribute(String name);

    /**
     * Set properties for switch specific behavior
     * @param name name of property
     * @return value for name
     */
    Object getAttribute(String name);

    /**
     * Check if the given attribute is present and if so whether it is equal
     * to "other"
     * @param name the name of the attribute to check
     * @param other the object to compare the attribute against.
     * @return true iff the specified attribute is set and equals() the given
     * other object.
     */
    boolean attributeEquals(String name, Object other);

    /**
     * Set properties for switch specific behavior
     * @param name name of property
     * @param value value for name
     */
    void setAttribute(String name, Object value);

    /**
     * Set properties for switch specific behavior
     * @param name name of property
     * @return current value for name or null (if not present)
     */
    Object removeAttribute(String name);

    /**
     * Returns a factory object that can be used to create OpenFlow messages.
     * @return
     */
    OFFactory getOFFactory();

    /**
     * Gets the OF connections for this switch instance
     * @return Collection of IOFConnection
     */
    ImmutableList<IOFConnection> getConnections();

    /**
     * Writes a message to the connection specified by the logical OFMessage category
     * @param m an OF Message
     * @param category the category of the OF Message to be sent
     * @return true upon success; false upon failure
     */
    boolean write(OFMessage m, LogicalOFMessageCategory category);

    /**
     * Writes a message list to the connection specified by the logical OFMessage category
     * @param msglist an OF Message list
     * @param category the category of the OF Message list to be sent
     * @return list of failed messages, if any; success denoted by empty list
     */
    Iterable<OFMessage> write(Iterable<OFMessage> msglist, LogicalOFMessageCategory category);

    /**
     * Get a connection specified by the logical OFMessage category
     * @param category the category for the connection the user desires
     * @return an OF Connection
     */
    OFConnection getConnectionByCategory(LogicalOFMessageCategory category);

    /** write a Stats (Multipart-) request, register for all corresponding reply messages.
     * Returns a Future object that can be used to retrieve the List of asynchronous
     * OFStatsReply messages when it is available.
     *
     * @param request stats request
     * @param category the category for the connection that this request should be written to
     * @return Future object wrapping OFStatsReply
     *         If the connection is not currently connected, will
     *         return a Future that immediately fails with a @link{SwitchDisconnectedException}.
     */
    <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(OFStatsRequest<REPLY> request, LogicalOFMessageCategory category);

    /** write an OpenFlow Request message, register for a single corresponding reply message
     *  or error message.
     *
     * @param request
     * @param categiry the category for the connection that this request should be written to
     * @return a Future object that can be used to retrieve the asynchrounous
     *         response when available.
     *
     *         If the connection is not currently connected, will
     *         return a Future that immediately fails with a @link{SwitchDisconnectedException}.
     */
    <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request, LogicalOFMessageCategory category);
    
    /**
     * Get the features of a particular switch table. The features are cached from
     * the initial handshake, or, if applicable, from a more recent 
     * OFTableFeaturesStatsRequest/Reply sent by a user module.
     * 
     * @param table, The table of which to get features.
     * @return The table features or null if no features are known for the table requested.
     */
    public TableFeatures getTableFeatures(TableId table);

    /**
     * Get the number of tables as returned by the ofp_features_reply.
     * @return
     */
	short getNumTables();
 
	/**
	 * Get the one-way latency from the switch to the controller.
	 * @return milliseconds
	 */
	public U64 getLatency();
}