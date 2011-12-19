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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import net.floodlightcontroller.core.types.MacVlanPair;

import org.jboss.netty.channel.Channel;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IOFSwitch {
    // Attribute keys
    public static final String SWITCH_DESCRIPTION_FUTURE = "DescriptionFuture";
    public static final String SWITCH_DESCRIPTION_DATA = "DescriptionData";
    public static final String SWITCH_IS_CORE_SWITCH = "isCoreSwitch";
    public static final String PROP_FASTWILDCARDS = "FastWildcards";
    public static final String PROP_REQUIRES_L3_MATCH = "requiresL3Match";
    public static final String PROP_SUPPORTS_OFPP_TABLE = "supportsOfppTable";
    public static final String PROP_SUPPORTS_OFPP_FLOOD = "supportsOfppFlood";
    
    /**
     * Writes to the OFMessage to the output stream.
     * The message will be handed to the floodlightProvider for possible filtering
     * @param m   
     * @param bc  
     * @throws IOException  
     */
    public void write(OFMessage m, FloodlightContext bc) throws IOException; 
    
    /**
     * Writes the list of messages to the output stream
     * The message will be handed to the floodlightProvider for possible filtering
     * @param m  
     * @param bc
     * @throws IOException
     */
    public void write(List<OFMessage> msglist, FloodlightContext bc) throws IOException;
    
    /**
     * 
     * @throws IOException
     */
    public void disconnectOutputStream();

    /**
     *
     * @return
     */
    public Channel getChannel();

    /**
     * Returns the cached OFFeaturesReply message returned by the switch during
     * the initial handshake.
     * @return
     */
    public OFFeaturesReply getFeaturesReply();

    /**
     * Set the OFFeaturesReply message returned by the switch during initial
     * handshake.
     * @param featuresReply
     */
    public void setFeaturesReply(OFFeaturesReply featuresReply);
    
    /**
     * Set the SwitchProperties based on it's description
     * @param featuresReply
     */
    public void setSwitchProperties(OFDescriptionStatistics description);    

    /**
     * Get list of all enabled ports. This will typically be different from
     * the list of ports in the OFFeaturesReply, since that one is a static
     * snapshot of the ports at the time the switch connected to the controller
     * whereas this port list also reflects the port status messages that have
     * been received.
     * @return Unmodifiable list of ports
     */
    public List<OFPhysicalPort> getEnabledPorts();

    /**
     * Retrieve the port object by the port number. The port object
     * is the one that reflects the port status updates that have been
     * received, not the one from the features reply.
     * @param portNumber
     * @return port object
     */
    public OFPhysicalPort getPort(short portNumber);

    /**
     * Add or modify a switch port. This is called by the core controller
     * code in response to a OFPortStatus message. It should not typically be
     * called by other floodlight applications.
     * @param port
     */
    public void setPort(OFPhysicalPort port);

    /**
     * Delete a port for the switch. This is called by the core controller
     * code in response to a OFPortStatus message. It should not typically be
     * called by other floodlight applications.
     * @param portNumber
     */
    public void deletePort(short portNumber);
    
    /**
     * Get the portmap
     * @return
     */
    public Map<Short, OFPhysicalPort> getPorts();

    /**
     * @param portNumber
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    public boolean portEnabled(short portNumber);

    /**
     * @param port
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    public boolean portEnabled(OFPhysicalPort port);

    /**
     * Get the datapathId of the switch
     * @return
     */
    public long getId();

    /**
     * Get a string version of the ID for this switch
     * @return
     */
    public String getStringId();
    
    /**
     * Retrieves attributes of this switch
     * @return
     */
    public Map<Object, Object> getAttributes();

    /**
     * Retrieves the date the switch connected to this controller
     * @return the date
     */
    public Date getConnectedSince();

    /**
     * Returns the next available transaction id
     * @return
     */
    public int getNextTransactionId();

    /**
     * Returns a Future object that can be used to retrieve the asynchronous
     * OFStatisticsReply when it is available.
     *
     * @param request statistics request
     * @return Future object wrapping OFStatisticsReply
     * @throws IOException 
     */
    public Future<List<OFStatistics>> getStatistics(OFStatisticsRequest request)
            throws IOException;
    
    /**
     * Adds a host to the macVlanPortMap
     * @param mac The MAC address of the host to add
     * @param vlan The VLAN that the host is on
     * @param portVal The switchport that the host is on
     */
    public void addToPortMap(Long mac, Short vlan, short portVal);
    
    /**
     * Removes a host from the macVlanPortMap 
     * @param mac The MAC address of the host to remove
     * @param vlan The VLAN that the host is on
     */
    public void removeFromPortMap(Long mac, Short vlan);
    
    /**
     * Get the port that a MAC/VLAN pair is associated with
     * @param mac The MAC address to get
     * @param vlan The VLAN number to get
     * @return The port the host is on
     */
    public Short getFromPortMap(Long mac, Short vlan);
    
    /**
     * Clear the switch table
     */
    public void clearPortMapTable();
    
    /**
     * Returns a COPY of the switch's macVlanPortMap, CAN be null.
     */
    public Map<MacVlanPair,Short> getMacVlanToPortMap();
    
    /**
     * Set the {@link net.floodlightcontroller.topology.SwitchCluster SwitchCluster}
     * ID that this switch is connected to.
     * @param id The SwitchCluster ID
     */
    public void setSwitchClusterId(Long id);
    
    /**
     * Get the {@link net.floodlightcontroller.topology.SwitchCluster SwitchCluster}
     * ID that this switch is connected to.
     */
    public Long getSwitchClusterId();
    
    /**
     * Check if the switch is still connected;
     * @return whether the switch is still disconnected
     */
    public boolean isConnected();
    
    /**
     * Set whether the switch is connected
     * @param connected whether the switch is connected
     */
    public void setConnected(boolean connected);
    
    /**
     * Deliver the statistics future reply
     * @param reply the reply to deliver
     */
    public void deliverStatisticsReply(OFMessage reply);
    
    /**
     * Cancel the statistics reply with the given transaction ID
     * @param transactionId the transaction ID
     */
    public void cancelStatisticsReply(int transactionId);

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
     * Clear all flowmods on this switch
     */
    public void clearAllFlowMods();
}
