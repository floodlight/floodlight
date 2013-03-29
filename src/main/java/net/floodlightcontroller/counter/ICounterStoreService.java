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

package net.floodlightcontroller.counter;

import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMessage;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.CounterStore.NetworkLayer;
import net.floodlightcontroller.packet.Ethernet;

public interface ICounterStoreService extends IFloodlightService {

	public final static String CONTROLLER_NAME = "controller";
    public final static String TitleDelimitor = "__";

    /** Broadcast and multicast */
    public final static String BROADCAST = "broadcast";
    public final static String MULTICAST = "multicast";
    public final static String UNICAST = "unicast";

    /** L2 EtherType subCategories */
    public final static String L3ET_IPV4 = "L3_IPv4";

    /**
     * Update packetIn counters
     *
     * @param sw
     * @param m
     * @param eth
     */
    public void updatePacketInCountersLocal(IOFSwitch sw, OFMessage m, Ethernet eth);

    /**
     * This method can only be used to update packetOut and flowmod counters
     *
     * @param sw
     * @param ofMsg
     */
    public void updatePktOutFMCounterStoreLocal(IOFSwitch sw, OFMessage ofMsg);

    /**
     * Flush Local Counter Updates
     *
     */
    public void updateFlush();

    /**
     * Retrieve a list of subCategories by counterName.
     * null if nothing.
     */
    public List<String> getAllCategories(String counterName,
                                         NetworkLayer layer);

    /**
     * Create a new ICounter and set the title.  Note that the title must be
     * unique, otherwise this will throw an IllegalArgumentException.
     *
     * @param key
     * @param type
     * @return
     */
    public ICounter createCounter(String key, CounterValue.CounterType type);

    /**
     * Retrieves a counter with the given title, or null if none can be found.
     */
    public ICounter getCounter(String key);

    /**
     * Returns an immutable map of title:counter with all of the counters in the store.
     *
     * (Note - this method may be slow - primarily for debugging/UI)
     */
    public Map<String, ICounter> getAll();
}
