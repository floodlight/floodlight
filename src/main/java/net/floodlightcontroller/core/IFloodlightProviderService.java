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

import java.util.List;
import java.util.Set;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import io.netty.util.Timer;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.internal.RoleManager;
import net.floodlightcontroller.core.internal.Controller.IUpdate;
import net.floodlightcontroller.core.internal.Controller.ModuleLoaderState;
import net.floodlightcontroller.core.FloodlightContextStore;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;
/**
 * The interface exposed by the core bundle that allows you to interact
 * with connected switches.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IFloodlightProviderService extends
        IFloodlightService, Runnable {

    /**
     * A value stored in the floodlight context containing a parsed packet
     * representation of the payload of a packet-in message.
     */
    public static final String CONTEXT_PI_PAYLOAD =
            "net.floodlightcontroller.core.IFloodlightProvider.piPayload";

    /**
     * A FloodlightContextStore object that can be used to retrieve the
     * packet-in payload
     */
    public static final FloodlightContextStore<Ethernet> bcStore =
            new FloodlightContextStore<Ethernet>();

    /**
     * Service name used in the service directory representing
     * the OpenFlow controller-switch channel
     *
     * @see  ILocalServiceAddressTracker
     * @see  IClusterServiceAddressDirectory
     */
    public static final String SERVICE_DIRECTORY_SERVICE_NAME = "openflow";

    /**
     * Adds an OpenFlow message listener
     * @param type The OFType the component wants to listen for
     * @param listener The component that wants to listen for the message
     */
    public void addOFMessageListener(OFType type, IOFMessageListener listener);

    /**
     * Removes an OpenFlow message listener
     * @param type The OFType the component no long wants to listen for
     * @param listener The component that no longer wants to receive the message
     */
    public void removeOFMessageListener(OFType type, IOFMessageListener listener);

    /**
     * Return a non-modifiable list of all current listeners
     * @return listeners
     */
    public Map<OFType, List<IOFMessageListener>> getListeners();

    /**
     * Get the current role of the controller
     */
    public HARole getRole();

    /**
     * Get the current role of the controller
     */
    public RoleInfo getRoleInfo();

    /**
     * Get the current mapping of controller IDs to their IP addresses
     * Returns a copy of the current mapping.
     * @see IHAListener
     */
    public Map<String,String> getControllerNodeIPs();

    /**
     * Gets the ID of the controller
     */
    public String getControllerId();

    /**
     * Gets the controller addresses
     * @return the controller addresses
     */
    public Set<IPv4Address> getOFAddresses();

    /**
     * Gets the controller's openflow port
     * @return the controller's openflow port
     */
    public TransportPort getOFPort();

    /**
     * Set the role of the controller
     * @param role The new role for the controller node
     * @param changeDescription The reason or other information for this role change
     */
    public void setRole(HARole role, String changeDescription);

    /**
     * Add an update task for asynchronous, serialized execution
     *
     * @param update
     */
    public void addUpdateToQueue(IUpdate update);

    /**
     * Adds a listener for HA role events
     * @param listener The module that wants to listen for events
     */
    public void addHAListener(IHAListener listener);

    /**
     * Removes a listener for HA role events
     * @param listener The module that no longer wants to listen for events
     */
    public void removeHAListener(IHAListener listener);

    /**
     * Process written messages through the message listeners for the controller
     * @param sw The switch being written to
     * @param m the message
     * @throws NullPointerException if switch or msg is null
     */
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m);

    /**
     * Run the main I/O loop of the Controller.
     */
    @Override
    public void run();

    /**
     * Add an info provider of a particular type
     * @param type
     * @param provider
     */
    public void addInfoProvider(String type, IInfoProvider provider);

   /**
    * Remove an info provider of a particular type
    * @param type
    * @param provider
    */
   public void removeInfoProvider(String type, IInfoProvider provider);

   /**
    * Return information of a particular type (for rest services)
    * @param type
    * @return
    */
   public Map<String, Object> getControllerInfo(String type);

   /**
    * Return the controller start time in  milliseconds
    * @return
    */
   public long getSystemStartTime();

   /**
    * Get controller memory information
    */
   public Map<String, Long> getMemory();

   /**
    * returns the uptime of this controller.
    * @return
    */
   public Long getUptime();

   /**
    * Get the set of port prefixes that will define an UPLINK port.
    * @return The set of prefixes
    */
   public Set<String> getUplinkPortPrefixSet();


   public void handleMessage(IOFSwitch sw, OFMessage m,
                          FloodlightContext bContext);

   /**
    * Gets a hash wheeled timer to be used for for timeout scheduling
    * @return a hash wheeled timer
    */
   public Timer getTimer();

   /**
    * Gets the role manager
    * @return the role manager
    */
   public RoleManager getRoleManager();

   /**
    * Gets the current module loading state.
    * @return the current module loading state.
    */
   ModuleLoaderState getModuleLoaderState();

   /**
    * Gets the current number of worker threads
    * @return Used for netty setup
    */
   public int getWorkerThreads();

   // paag
   /**
    * Add a completion listener to the controller
    * 
    * @param listener
    */
   void addCompletionListener(IControllerCompletionListener listener);

   /**
    * Remove a completion listener from the controller
    * 
    * @param listener
    */
   void removeCompletionListener(IControllerCompletionListener listener);
}

