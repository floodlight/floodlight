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

/**
 * Implements a very simple central store for system counters
 */
package net.floodlightcontroller.counter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.CounterValue.CounterType;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author kyle
 *
 */
public class CounterStore implements IFloodlightModule, ICounterStoreService {
    protected static Logger log = LoggerFactory.getLogger(CounterStore.class);

    public enum NetworkLayer {
        L2, L3, L4
    }

    protected class CounterEntry {
        protected ICounter counter;
        String title;
    }

    protected class MutableInt {
          int value = 0;
          public void increment() { value += 1; }
          public int get() { return value; }
          public void set(int val) { value = val; }
        }

    /**
     * A map of counterName --> Counter
     */
    protected ConcurrentHashMap<String, CounterEntry> nameToCEIndex = 
            new ConcurrentHashMap<String, CounterEntry>();

    protected ICounter heartbeatCounter;
    protected ICounter randomCounter;

    protected ConcurrentHashMap<String, List<ICounter>>
        pktinCounters = new ConcurrentHashMap<String, List<ICounter>>();
    protected ConcurrentHashMap<String, List<ICounter>>
        pktoutCounters = new ConcurrentHashMap<String, List<ICounter>>();

    protected final ThreadLocal<Map<String,MutableInt>> pktin_local_buffer =
        new ThreadLocal<Map<String,MutableInt>>() {
        @Override
        protected Map<String,MutableInt> initialValue() {
            return new HashMap<String,MutableInt>();
        }
    };
    protected final ThreadLocal<Map<String,MutableInt>> pktout_local_buffer =
        new ThreadLocal<Map<String,MutableInt>>() {
        @Override
        protected Map<String,MutableInt> initialValue() {
            return new HashMap<String,MutableInt>();
        }
    };


    /**
     * Counter Categories grouped by network layers
     * NetworkLayer -> CounterToCategories
     */
    protected static Map<NetworkLayer, Map<String, List<String>>> layeredCategories = 
            new ConcurrentHashMap<NetworkLayer, Map<String, List<String>>> ();

    public void updatePacketInCounters(IOFSwitch sw, OFMessage m, Ethernet eth) {
        if (((OFPacketIn)m).getPacketData().length <= 0) {
            return;
        }

        List<ICounter> counters = this.getPacketInCounters(sw, m, eth);
        if (counters != null) {
            for (ICounter c : counters) {
                c.increment();
            }
        }
        return;
    }

    @Override
    public void updatePacketInCountersLocal(IOFSwitch sw, OFMessage m, Ethernet eth) {
        if (((OFPacketIn)m).getPacketData().length <= 0) {
            return;
        }

        String countersKey = this.getCountersKey(sw, m, eth);
        Map<String, MutableInt> pktin_buffer = this.pktin_local_buffer.get();
        MutableInt currval = pktin_buffer.get(countersKey);

        if ( currval == null ) {
            this.getPacketInCounters(sw, m, eth); // create counters as side effect (if required)
            currval = new MutableInt();
            pktin_buffer.put(countersKey, currval);
        }
        currval.increment();
        return;
    }

    /**
     * This method can only be used to update packetOut and flowmod counters
     *  NOTE: flowmod is counted per switch and for controller, not per port/proto
     *
     * @param sw
     * @param m
     */
    public void updatePktOutFMCounterStore(IOFSwitch sw, OFMessage m) {
        List<ICounter> counters = this.getPktOutFMCounters(sw, m);
        if (counters != null) {
            for (ICounter c : counters) {
                c.increment();
            }
        }
        return;
    }

    @Override
    public void updatePktOutFMCounterStoreLocal(IOFSwitch sw, OFMessage m) {
        String countersKey = this.getCountersKey(sw, m, null);
        Map<String, MutableInt> pktout_buffer = this.pktout_local_buffer.get();
        MutableInt currval = pktout_buffer.get(countersKey);

        if ( currval == null ) {
            this.getPktOutFMCounters(sw, m); // create counters as side effect (if required)
            currval = new MutableInt();
            pktout_buffer.put(countersKey, currval);
        }
        currval.increment();
        return;
    }

    @Override
    public void updateFlush() {
        Date date = new Date();
        Map<String, MutableInt> pktin_buffer = this.pktin_local_buffer.get();
        for (String key : pktin_buffer.keySet()) {
                MutableInt currval = pktin_buffer.get(key);
                int delta = currval.get();

                if (delta > 0) {
                    List<ICounter> counters = this.pktinCounters.get(key);
                    if (counters != null) {
                        for (ICounter c : counters) {
                            c.increment(date, delta);
                        }
                    }
                }
        }
        // We could do better "GC" of counters that have not been update "recently"
        pktin_buffer.clear();

        Map<String, MutableInt> pktout_buffer = this.pktout_local_buffer.get();
        for (String key : pktout_buffer.keySet()) {
                MutableInt currval = pktout_buffer.get(key);
                int delta = currval.get();

                if (delta > 0) {
                    List<ICounter> counters = this.pktoutCounters.get(key);
                    if (counters != null) {
                        for (ICounter c : counters) {
                            c.increment(date, delta);
                        }
                    }
                }
        }
        // We could do better "GC" of counters that have not been update "recently"
        pktout_buffer.clear();
    }

    protected String getCountersKey(IOFSwitch sw, OFMessage m, Ethernet eth) {
        byte mtype = m.getType().getTypeValue();
        //long swid = sw.getId();
        String swsid = sw.getStringId();
        short port = 0;
        short l3type = 0;
        byte l4type = 0;
        
        if (eth != null) {
            // Packet in counters
            // Need port and protocol level differentiation
            OFPacketIn packet = (OFPacketIn)m;
            port = packet.getInPort();
            l3type = eth.getEtherType();
            if (l3type == (short)0x0800) {
                IPv4 ipV4 = (IPv4)eth.getPayload();
                l4type = ipV4.getProtocol();
            }
        }

        /* If possible, find and return counters for this tuple
         *
         * NOTE: this can be converted to a tuple for better performance,
         * for now we are using a string representation as a the key
         */
        String countersKey =
            Byte.toString(mtype) + "-" +
            swsid + "-" + Short.toString(port) + "-" +
            Short.toString(l3type) + "-" +
            Byte.toString(l4type);
        return countersKey;
    }

    protected List<ICounter> getPacketInCounters(IOFSwitch sw, OFMessage m, Ethernet eth) {
        /* If possible, find and return counters for this tuple */
        String countersKey = this.getCountersKey(sw, m, eth);
        List<ICounter> counters =
                this.pktinCounters.get(countersKey);
        if (counters != null) {
                return counters;
        }
        
        /*
         *  Create the required counters
         */
        counters = new ArrayList<ICounter>();

        /* values for names */
        short port = ((OFPacketIn)m).getInPort();
        short l3type = eth.getEtherType();
        String switchIdHex = sw.getStringId();
        String etherType = String.format("%04x", eth.getEtherType());
        String packetName = m.getType().toClass().getName();
        packetName = packetName.substring(packetName.lastIndexOf('.')+1);

        // L2 Type
        String l2Type = null;
        if (eth.isBroadcast()) {
            l2Type = BROADCAST;
        }
        else if (eth.isMulticast()) {
            l2Type = MULTICAST;
        }
        else {
            l2Type = UNICAST;
        }

        /*
         * Use alias for L3 type
         * Valid EtherType must be greater than or equal to 0x0600
         * It is V1 Ethernet Frame if EtherType < 0x0600
         */
        if (l3type < 0x0600) {
            etherType = "0599";
        }
        if (TypeAliases.l3TypeAliasMap != null && 
            TypeAliases.l3TypeAliasMap.containsKey(etherType)) {
            etherType = TypeAliases.l3TypeAliasMap.get(etherType);
        }
        else {
            etherType = "L3_" + etherType;
        }
   
        // overall controller packet counter names
        String controllerCounterName =
            CounterStore.createCounterName(
                CONTROLLER_NAME,
                -1,
                packetName);
        counters.add(createCounter(controllerCounterName,
                                   CounterType.LONG));

        String switchCounterName =
            CounterStore.createCounterName(
                switchIdHex,
                -1,
                packetName);
        counters.add(createCounter(switchCounterName,
                                   CounterType.LONG));

        String portCounterName =
            CounterStore.createCounterName(
                switchIdHex,
                port,
                packetName);
        counters.add(createCounter(portCounterName,
                                   CounterType.LONG));

        // L2 counter names
            String controllerL2CategoryCounterName =
                CounterStore.createCounterName(
                    CONTROLLER_NAME,
                    -1,
                    packetName,
                    l2Type,
                    NetworkLayer.L2);
            counters.add(createCounter(controllerL2CategoryCounterName,
                                       CounterType.LONG));

            String switchL2CategoryCounterName =
                CounterStore.createCounterName(
                    switchIdHex,
                    -1,
                    packetName,
                    l2Type,
                    NetworkLayer.L2);
            counters.add(createCounter(switchL2CategoryCounterName,
                                       CounterType.LONG));

            String portL2CategoryCounterName =
                CounterStore.createCounterName(
                    switchIdHex,
                    port,
                    packetName,
                    l2Type,
                    NetworkLayer.L2);
            counters.add(createCounter(portL2CategoryCounterName,
                                       CounterType.LONG));

        // L3 counter names
            String controllerL3CategoryCounterName =
                CounterStore.createCounterName(
                    CONTROLLER_NAME,
                    -1,
                    packetName,
                    etherType,
                    NetworkLayer.L3);
            counters.add(createCounter(controllerL3CategoryCounterName,
                                       CounterType.LONG));

            String switchL3CategoryCounterName =
                CounterStore.createCounterName(
                    switchIdHex,
                    -1,
                    packetName,
                    etherType,
                    NetworkLayer.L3);
            counters.add(createCounter(switchL3CategoryCounterName,
                                       CounterType.LONG));

            String portL3CategoryCounterName =
                CounterStore.createCounterName(
                    switchIdHex,
                    port,
                    packetName,
                    etherType,
                    NetworkLayer.L3);
            counters.add(createCounter(portL3CategoryCounterName,
                                       CounterType.LONG));

        // L4 counters
        if (l3type == (short)0x0800) {

            // resolve protocol alias
            IPv4 ipV4 = (IPv4)eth.getPayload();
            String l4name = String.format("%02x", ipV4.getProtocol());
            if (TypeAliases.l4TypeAliasMap != null && 
                TypeAliases.l4TypeAliasMap.containsKey(l4name)) {
                l4name = TypeAliases.l4TypeAliasMap.get(l4name);
            }
            else {
                l4name = "L4_" + l4name;
            }

            // create counters
            String controllerL4CategoryCounterName =
                CounterStore.createCounterName(
                    CONTROLLER_NAME,
                    -1,
                    packetName,
                    l4name,
                    NetworkLayer.L4);
            counters.add(createCounter(controllerL4CategoryCounterName,
                                       CounterType.LONG));

            String switchL4CategoryCounterName =
                CounterStore.createCounterName(
                    switchIdHex,
                    -1,
                    packetName,
                    l4name,
                    NetworkLayer.L4);
            counters.add(createCounter(switchL4CategoryCounterName,
                                       CounterType.LONG));

            String portL4CategoryCounterName =
                CounterStore.createCounterName(
                    switchIdHex,
                    port,
                    packetName,
                    l4name,
                    NetworkLayer.L4);
            counters.add(createCounter(portL4CategoryCounterName,
                                       CounterType.LONG));
        }

        /* Add to map and return */
        this.pktinCounters.putIfAbsent(countersKey, counters);
        return this.pktinCounters.get(countersKey);
    }
    
    protected List<ICounter> getPktOutFMCounters(IOFSwitch sw, OFMessage m) {
        /* If possible, find and return counters for this tuple */
        String countersKey = this.getCountersKey(sw, m, null);
        List<ICounter> counters =
            this.pktoutCounters.get(countersKey);
        if (counters != null) {
            return counters;
        }

        /*
         *  Create the required counters
         */
        counters = new ArrayList<ICounter>();

        /* String values for names */
        String switchIdHex = sw.getStringId();
        String packetName = m.getType().toClass().getName();
        packetName = packetName.substring(packetName.lastIndexOf('.')+1);

        String controllerFMCounterName =
            CounterStore.createCounterName(
                CONTROLLER_NAME,
                -1,
                packetName);
        counters.add(createCounter(controllerFMCounterName,
                                   CounterValue.CounterType.LONG));

        String switchFMCounterName =
            CounterStore.createCounterName(
                switchIdHex,
                -1,
                packetName);
        counters.add(createCounter(switchFMCounterName,
                                   CounterValue.CounterType.LONG));

        /* Add to map and return */
        this.pktoutCounters.putIfAbsent(countersKey, counters);
        return this.pktoutCounters.get(countersKey);

    }

    /**
     * Create a title based on switch ID, portID, vlanID, and counterName
     * If portID is -1, the title represents the given switch only
     * If portID is a non-negative number, the title represents the port on the given switch
     */
    public static String createCounterName(String switchID, int portID, String counterName) {
        if (portID < 0) {
            return switchID + TitleDelimitor + counterName;
        } else {
            return switchID + TitleDelimitor + portID + TitleDelimitor + counterName;
        }
    }

    /**
     * Create a title based on switch ID, portID, vlanID, counterName, and subCategory
     * If portID is -1, the title represents the given switch only
     * If portID is a non-negative number, the title represents the port on the given switch
     * For example: PacketIns can be further categorized based on L2 etherType or L3 protocol
     */
    public static String createCounterName(String switchID, int portID, String counterName,
            String subCategory, NetworkLayer layer) {
        String fullCounterName = "";
        String groupCounterName = "";

        if (portID < 0) {
            groupCounterName = switchID + TitleDelimitor + counterName;
            fullCounterName = groupCounterName + TitleDelimitor + subCategory;
        } else {
            groupCounterName = switchID + TitleDelimitor + portID + TitleDelimitor + counterName;
            fullCounterName = groupCounterName + TitleDelimitor + subCategory;
        }

        Map<String, List<String>> counterToCategories;      
        if (layeredCategories.containsKey(layer)) {
            counterToCategories = layeredCategories.get(layer);
        } else {
            counterToCategories = new ConcurrentHashMap<String, List<String>> ();
            layeredCategories.put(layer, counterToCategories);
        }

        List<String> categories;
        if (counterToCategories.containsKey(groupCounterName)) {
            categories = counterToCategories.get(groupCounterName);
        } else {
            categories = new ArrayList<String>();
            counterToCategories.put(groupCounterName, categories);
        }

        if (!categories.contains(subCategory)) {
            categories.add(subCategory);
        }
        return fullCounterName;
    }

    @Override
    public List<String> getAllCategories(String counterName, NetworkLayer layer) {
        if (layeredCategories.containsKey(layer)) {
            Map<String, List<String>> counterToCategories = layeredCategories.get(layer);
            if (counterToCategories.containsKey(counterName)) {
                return counterToCategories.get(counterName);
            }
        }
        return null;
    }
    
    @Override
    public ICounter createCounter(String key, CounterValue.CounterType type) {
        CounterEntry ce;
        ICounter c;

        c = SimpleCounter.createCounter(new Date(), type);
        ce = new CounterEntry();
        ce.counter = c;
        ce.title = key;
        nameToCEIndex.putIfAbsent(key, ce);
        
        return nameToCEIndex.get(key).counter;
    }

    /**
     * Post construction init method to kick off the health check and random (test) counter threads
     */
    @PostConstruct
    public void startUp() {
        this.heartbeatCounter = this.createCounter("CounterStore heartbeat", CounterValue.CounterType.LONG);
        this.randomCounter = this.createCounter("CounterStore random", CounterValue.CounterType.LONG);
        //Set a background thread to flush any liveCounters every 100 milliseconds
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable() {
            public void run() {
                heartbeatCounter.increment();
                randomCounter.increment(new Date(), (long) (Math.random() * 100)); //TODO - pull this in to random timing
            }}, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public ICounter getCounter(String key) {
        CounterEntry counter = nameToCEIndex.get(key);
        if (counter != null) {
            return counter.counter;
        } else {
            return null;
        }
    }

    /* (non-Javadoc)
     * @see net.floodlightcontroller.counter.ICounterStoreService#getAll()
     */
    @Override
    public Map<String, ICounter> getAll() {
        Map<String, ICounter> ret = new ConcurrentHashMap<String, ICounter>();
        for(Map.Entry<String, CounterEntry> counterEntry : this.nameToCEIndex.entrySet()) {
            String key = counterEntry.getKey();
            ICounter counter = counterEntry.getValue().counter;
            ret.put(key, counter);
        }
        return ret;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(ICounterStoreService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(ICounterStoreService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        // no-op, no dependencies
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
                                 throws FloodlightModuleException {
        // no-op for now
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // no-op for now
    }
}
