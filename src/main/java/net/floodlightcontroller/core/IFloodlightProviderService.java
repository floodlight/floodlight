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

import java.util.HashMap;

import java.util.List;
import java.util.Set;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.vendor.nicira.OFRoleVendorData;

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
     * The role of the controller as used by the OF 1.2 and OVS failover and
     * load-balancing mechanism.
     */
    public static enum Role {
        EQUAL(OFRoleVendorData.NX_ROLE_OTHER),
        MASTER(OFRoleVendorData.NX_ROLE_MASTER),
        SLAVE(OFRoleVendorData.NX_ROLE_SLAVE);

        private final int nxRole;

        private Role(int nxRole) {
            this.nxRole = nxRole;
        }

        private static Map<Integer,Role> nxRoleToEnum
                = new HashMap<Integer,Role>();
        static {
            for(Role r: Role.values())
                nxRoleToEnum.put(r.toNxRole(), r);
        }
        public int toNxRole() {
            return nxRole;
        }
        // Return the enum representing the given nxRole or null if no
        // such role exists
        public static Role fromNxRole(int nxRole) {
            return nxRoleToEnum.get(nxRole);
        }
    };

    /**
     * A FloodlightContextStore object that can be used to retrieve the
     * packet-in payload
     */
    public static final FloodlightContextStore<Ethernet> bcStore =
            new FloodlightContextStore<Ethernet>();

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
     * If the switch with the given DPID is known to any controller in the
     * cluster, this method returns the associated IOFSwitch instance. As such
     * the returned switches not necessarily connected or in master role for
     * the local controller.
     *
     * Multiple calls to this method with the same DPID may return different
     * IOFSwitch references. A caller must not store or otherwise rely on
     * IOFSwitch references to be constant over the lifecycle of a switch.
     *
     * @param dpid the dpid of the switch to query
     * @return the IOFSwitch instance associated with the dpid, null if no
     * switch with the dpid is known to the cluster
     */
    public IOFSwitch getSwitch(long dpid);

    /**
     * Returns a snapshot of the set DPIDs for all known switches.
     *
     * The returned set is owned by the caller: the caller can modify it at
     * will and changes to the known switches are not reflected in the returned
     * set. The caller needs to call getAllSwitchDpids() if an updated
     * version is needed.
     *
     * See {@link #getSwitch(long)} for what  "known" switch is.
     * @return the set of DPIDs of all known switches
     */
    public Set<Long> getAllSwitchDpids();

    /**
     * Return a snapshot
     * FIXME: asdf
     * @return
     */
    public Map<Long,IOFSwitch> getAllSwitchMap();

    /**
     * Get the current role of the controller
     */
    public Role getRole();

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
     * Set the role of the controller
     * @param role The new role for the controller node
     * @param changeDescription The reason or other information for this role change
     */
    public void setRole(Role role, String changeDescription);

    /**
     * Add a switch listener
     * @param listener The module that wants to listen for events
     */
    public void addOFSwitchListener(IOFSwitchListener listener);

    /**
     * Remove a switch listener
     * @param listener The The module that no longer wants to listen for events
     */
    public void removeOFSwitchListener(IOFSwitchListener listener);

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
     * Add a listener for ready-for-flow-reconcile events
     * @param l
     */
    public void addReadyForReconcileListener(IReadyForReconcileListener l);

    /**
     * Terminate the process
     */
    public void terminate();

    /**
     * Re-injects an OFMessage back into the packet processing chain
     * @param sw The switch to use for the message
     * @param msg the message to inject
     * @return True if successfully re-injected, false otherwise
     * @throws NullPointerException if switch or msg is null
     */
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg);

    /**
     * Re-injects an OFMessage back into the packet processing chain
     * @param sw The switch to use for the message
     * @param msg the message to inject
     * @param bContext a floodlight context to use if required. Can be null
     * @return True if successfully re-injected, false otherwise
     * @throws NullPointerException if switch or msg is null
     */
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg,
            FloodlightContext bContext);

    /**
     * Process written messages through the message listeners for the controller
     * @param sw The switch being written to
     * @param m the message
     * @param bc any accompanying context object. Can be null in which case a
     * new context will be allocated and passed to listeners
     * @throws NullPointerException if switch or msg is null
     */
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m,
            FloodlightContext bc);

    /**
     * Gets the BasicFactory
     * @return an OpenFlow message factory
     */
    public BasicFactory getOFMessageFactory();

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
    * Configure controller to always clear the flow table on the switch,
    * when it connects to controller. This will be true for first time switch
    * reconnect, as well as a switch re-attaching to Controller after HA
    * switch over to ACTIVE role
    */
   public void setAlwaysClearFlowsOnSwActivate(boolean value);

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
    * Adds an OFSwitch driver
    *  @param manufacturerDescriptionPrefix Register the given prefix
    * with the driver.
    * @param driver A IOFSwitchDriver instance to handle IOFSwitch instaniation
    * for the given manufacturer description prefix
    * @throws IllegalStateException If the the manufacturer description is
    * already registered
    * @throws NullPointerExeption if manufacturerDescriptionPrefix is null
    * @throws NullPointerExeption if driver is null
    */
   public void addOFSwitchDriver(String desc, IOFSwitchDriver driver);

   /**
    * Record a switch event in in-memory debug-event
    * @param switchDPID
    * @param reason Reason for this event
    * @param flushNow see debug-event flushing in IDebugEventService
    */
   public void addSwitchEvent(long switchDPID, String reason, boolean flushNow);

   /**
    * Get the set of port prefixes that will define an UPLINK port.
    * @return The set of prefixes
    */
   public Set<String> getUplinkPortPrefixSet();

}
