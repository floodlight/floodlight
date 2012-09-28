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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;

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
    public static final String SWITCH_SUPPORTS_NX_ROLE = "supportsNxRole";
    public static final String SWITCH_IS_CORE_SWITCH = "isCoreSwitch";
    public static final String PROP_FASTWILDCARDS = "FastWildcards";
    public static final String PROP_REQUIRES_L3_MATCH = "requiresL3Match";
    public static final String PROP_SUPPORTS_OFPP_TABLE = "supportsOfppTable";
    public static final String PROP_SUPPORTS_OFPP_FLOOD = "supportsOfppFlood";
    public static final String PROP_SUPPORTS_NETMASK_TBL = "supportsNetmaskTbl";
    
    /**
     * Writes to the OFMessage to the output stream.
     * The message will be handed to the floodlightProvider for possible filtering
     * and processing by message listeners
     * @param m   
     * @param bc  
     * @throws IOException  
     */
    public void write(OFMessage m, FloodlightContext bc) throws IOException; 
    
    /**
     * Writes the list of messages to the output stream
     * The message will be handed to the floodlightProvider for possible filtering
     * and processing by message listeners.
     * @param msglist
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
     * FIXME: remove getChannel(). All access to the channel should be through
     *        wrapper functions in IOFSwitch
     * @return
     */
    public Channel getChannel();

    /**
     * Returns switch features from features Reply
     * @return
     */
    public int getBuffers();
    
    public int getActions();
    
    public int getCapabilities();
    
    public byte getTables();

    /**
     * Set the OFFeaturesReply message returned by the switch during initial
     * handshake.
     * @param featuresReply
     */
    public void setFeaturesReply(OFFeaturesReply featuresReply);
    
    /**
     * Set the SwitchProperties based on it's description
     * @param description
     */
    public void setSwitchProperties(OFDescriptionStatistics description);    

    /**
     * Get list of all enabled ports. This will typically be different from
     * the list of ports in the OFFeaturesReply, since that one is a static
     * snapshot of the ports at the time the switch connected to the controller
     * whereas this port list also reflects the port status messages that have
     * been received.
     * @return Unmodifiable list of ports not backed by the underlying collection
     */
    public Collection<OFPhysicalPort> getEnabledPorts();
    
    /**
     * Get list of the port numbers of all enabled ports. This will typically
     * be different from the list of ports in the OFFeaturesReply, since that
     * one is a static snapshot of the ports at the time the switch connected 
     * to the controller whereas this port list also reflects the port status
     * messages that have been received.
     * @return Unmodifiable list of ports not backed by the underlying collection
     */
    public Collection<Short> getEnabledPortNumbers();

    /**
     * Retrieve the port object by the port number. The port object
     * is the one that reflects the port status updates that have been
     * received, not the one from the features reply.
     * @param portNumber
     * @return port object
     */
    public OFPhysicalPort getPort(short portNumber);
    
    /**
     * Retrieve the port object by the port name. The port object
     * is the one that reflects the port status updates that have been
     * received, not the one from the features reply.
     * @param portName
     * @return port object
     */
    public OFPhysicalPort getPort(String portName);
    
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
     * Delete a port for the switch. This is called by the core controller
     * code in response to a OFPortStatus message. It should not typically be
     * called by other floodlight applications.
     * @param portName
     */
    public void deletePort(String portName);
    
    /**
     * Get list of all ports. This will typically be different from
     * the list of ports in the OFFeaturesReply, since that one is a static
     * snapshot of the ports at the time the switch connected to the controller
     * whereas this port list also reflects the port status messages that have
     * been received.
     * @return Unmodifiable list of ports 
     */
    public Collection<OFPhysicalPort> getPorts();

    /**
     * @param portName
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    public boolean portEnabled(short portName);
    
    /**
     * @param portNumber
     * @return Whether a port is enabled per latest port status message
     * (not configured down nor link down nor in spanning tree blocking state)
     */
    public boolean portEnabled(String portName);

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
     * Returns a Future object that can be used to retrieve the asynchronous
     * OFStatisticsReply when it is available.
     *
     * @param request statistics request
     * @return Future object wrapping OFStatisticsReply
     * @throws IOException 
     */
    public Future<OFFeaturesReply> getFeaturesReplyFromSwitch()
            throws IOException;

    /**
     * Deliver the featuresReply future reply
     * @param reply the reply to deliver
     */
    void deliverOFFeaturesReply(OFMessage reply);

    /*
     * Cancel features reply with a specific transction ID
     * @param transactionId the transaction ID
     */
    public void cancelFeaturesReply(int transactionId);

    /**
     * Check if the switch is still connected;
     * Only call while holding processMessageLock
     * @return whether the switch is still disconnected
     */
    public boolean isConnected();
    
    /**
     * Set whether the switch is connected
     * Only call while holding modifySwitchLock
     * @param connected whether the switch is connected
     */
    public void setConnected(boolean connected);
    
    /**
     * Get the current role of the controller for the switch
     * @return the role of the controller
     */
    public Role getRole();
    
    /**
     * Check if the controller is an active controller for the switch.
     * The controller is active if its role is MASTER or EQUAL.
     * @return whether the controller is active
     */
    public boolean isActive();
    
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
     * Cancel all statistics replies
     */
    public void cancelAllStatisticsReplies();

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

    /**
     * Update broadcast cache
     * @param data
     * @return true if there is a cache hit
     *         false if there is no cache hit.
     */
    public boolean updateBroadcastCache(Long entry, Short port);
    
    /**
     * Get the portBroadcastCacheHits
     * @return
     */
    public Map<Short, Long> getPortBroadcastHits();

    /**
     * Send a flow statistics request to the switch. This call returns after
     * sending the stats. request to the switch.
     * @param request flow statistics request message
     * @param xid transaction id, must be obtained by using the getXid() API.
     * @param caller the caller of the API. receive() callback of this 
     * caller would be called when the reply from the switch is received.
     * @return the transaction id for the message sent to the switch. The 
     * transaction id can be used to match the response with the request. Note
     * that the transaction id is unique only within the scope of this switch.
     * @throws IOException
     */
    public void sendStatsQuery(OFStatisticsRequest request, int xid,
                            IOFMessageListener caller) throws IOException;

    /**
     * Flush all flows queued for this switch in the current thread.
     * NOTE: The contract is limited to the current thread
     */
     public void flush();
}
