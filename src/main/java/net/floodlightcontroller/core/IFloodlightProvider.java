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
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;

/**
 * The interface exposed by the core bundle that allows you to interact
 * with connected switches.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IFloodlightProvider {

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
     * 
     * @param type
     * @param listener
     */
    public void addOFMessageListener(OFType type, IOFMessageListener listener);

    /**
     * 
     * @param type
     * @param listener
     */
    public void removeOFMessageListener(OFType type, IOFMessageListener listener);

    /**
     * Returns a list of all actively connected OpenFlow switches
     * @return the set of connected switches
     */
    public Map<Long, IOFSwitch> getSwitches();

    /**
     * Add a switch listener
     * @param listener
     */
    public void addOFSwitchListener(IOFSwitchListener listener);

    /**
     * Remove a switch listener
     * @param listener
     */
    public void removeOFSwitchListener(IOFSwitchListener listener);

    /**
     * Return a non-modifiable list of all current listeners
     * @return listeners
     */
    public Map<OFType, List<IOFMessageListener>> getListeners();

    /**
     * Get the master scheduled thread pool executor maintained by the
     * floodlight provider.  This can be used by other modules as a centralized
     * way to schedule tasks.
     * @return
     */
    public ScheduledExecutorService getScheduledExecutor();

    /**
     * Terminate the process
     */
    public void terminate();

    /**
     * Re-injects an OFMessage back into the packet processing chain
     * @param sw The switch to use for the message
     * @param msg the message to inject
     * @return True if successfully re-injected, false otherwise
     */
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg);

    /**
     * Re-injects an OFMessage back into the packet processing chain
     * @param sw The switch to use for the message
     * @param msg the message to inject
     * @param bContext a floodlight context to use if required
     * @return True if successfully re-injected, false otherwise
     */
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg, 
            FloodlightContext bContext);

    /**
     * Process written messages through the message listeners for the contoller
     * @param sw The switch being written to
     * @param m the message 
     * @param bc any accompanying context object
     */
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m, 
            FloodlightContext bc);

    /**
     * Gets the BasicFactory
     * @return an OpenFlow message factory
     */
    public BasicFactory getOFMessageFactory();
}
