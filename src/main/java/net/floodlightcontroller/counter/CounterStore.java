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

    /**
     * A map of counterName --> Counter
     */
    protected Map<String, CounterEntry> nameToCEIndex = 
            new ConcurrentHashMap<String, CounterEntry>();

    protected ICounter heartbeatCounter;
    protected ICounter randomCounter;

    /**
     * Counter Categories grouped by network layers
     * NetworkLayer -> CounterToCategories
     */
    protected static Map<NetworkLayer, Map<String, List<String>>> layeredCategories = 
            new ConcurrentHashMap<NetworkLayer, Map<String, List<String>>> ();

    public void updatePacketInCounters(IOFSwitch sw, OFMessage m, Ethernet eth) {
        OFPacketIn packet = (OFPacketIn)m;
        
        // Make sure there is data
        if (packet.getPacketData().length <= 0) return;
        
        /* Extract the etherType and protocol field for IPv4 packet.
         */
        String etherType = String.format("%04x", eth.getEtherType());
        
        /*
         * Valid EtherType must be greater than or equal to 0x0600
         * It is V1 Ethernet Frame if EtherType < 0x0600
         */
        if (eth.getEtherType() < 0x0600) {
            etherType = "0599";
        }

        if (TypeAliases.l3TypeAliasMap != null && 
            TypeAliases.l3TypeAliasMap.containsKey(etherType)) {
            etherType = TypeAliases.l3TypeAliasMap.get(etherType);
        } else {
            etherType = "L3_" + etherType;
        }
        String switchIdHex = sw.getStringId();
   
        String packetName = m.getType().toClass().getName();
        packetName = packetName.substring(packetName.lastIndexOf('.')+1); 
        
        // Construct controller counter for the packet_in
        String controllerCounterName =
            CounterStore.createCounterName(CONTROLLER_NAME, 
                                           -1,
                                           packetName);
    
        String controllerL3CategoryCounterName = 
            CounterStore.createCounterName(CONTROLLER_NAME, 
                                           -1,
                                           packetName, 
                                           etherType, 
                                           NetworkLayer.L3);

        String l2Type = null;
        if (eth.isBroadcast()) {
        	l2Type = BROADCAST;
        } else if (eth.isMulticast()) {
        	l2Type = MULTICAST;
        } else {
        	l2Type = UNICAST;
        }
        
        // Construct both port and switch L3 counter for the packet_in
    	String controllerL2CategoryCounterName = CounterStore.createCounterName(CONTROLLER_NAME, 
                -1,
                packetName, 
                l2Type, 
                NetworkLayer.L2);
    	String switchL2CategoryCounterName = CounterStore.createCounterName(switchIdHex, 
                -1, 
                packetName, 
                l2Type, 
                NetworkLayer.L2);
    	String portL2CategoryCounterName = CounterStore.createCounterName(switchIdHex, 
                packet.getInPort(),
                packetName, 
                l2Type, 
                NetworkLayer.L2);
        
        // Construct both port and switch L3 counter for the packet_in
        String portCounterName =
                CounterStore.createCounterName(switchIdHex, 
                                               packet.getInPort(),
                                               packetName);
        String switchCounterName =
                CounterStore.createCounterName(switchIdHex, 
                                               -1,
                                               packetName);
        
        String portL3CategoryCounterName = 
                CounterStore.createCounterName(switchIdHex, 
                                               packet.getInPort(),
                                               packetName, 
                                               etherType, 
                                               NetworkLayer.L3);
        String switchL3CategoryCounterName =
                CounterStore.createCounterName(switchIdHex, 
                                               -1, 
                                               packetName, 
                                               etherType, 
                                               NetworkLayer.L3);
        
        try {        	
        	// Controller counters
        	ICounter controllerCounter = getCounter(controllerCounterName);
            if (controllerCounter == null) {
            	controllerCounter = createCounter(controllerCounterName, 
                                               CounterType.LONG);
            }
            controllerCounter.increment();
            ICounter portCounter = getCounter(portCounterName);
            if (portCounter == null) {
                portCounter = createCounter(portCounterName, 
                                                   CounterType.LONG);
            }
            portCounter.increment();
            ICounter switchCounter = getCounter(switchCounterName);
            if (switchCounter == null) {
                switchCounter = createCounter(switchCounterName, 
                                                   CounterType.LONG);
            }
            switchCounter.increment();

            // L2 counters
            ICounter controllerL2Counter = getCounter(controllerL2CategoryCounterName);
            if (controllerL2Counter == null) {
            	controllerL2Counter = createCounter(controllerL2CategoryCounterName,
                                               CounterType.LONG);
            }
            controllerL2Counter.increment();
            ICounter switchL2Counter = getCounter(switchL2CategoryCounterName);
            if (switchL2Counter == null) {
            	switchL2Counter = createCounter(switchL2CategoryCounterName,
                                                   CounterType.LONG);
            }
            switchL2Counter.increment();
            ICounter portL2Counter = getCounter(portL2CategoryCounterName);
            if (portL2Counter == null) {
            	portL2Counter = createCounter(portL2CategoryCounterName,
                                                   CounterType.LONG);
            }
            portL2Counter.increment();
            
            // L3 counters
            ICounter controllerL3Counter = getCounter(controllerL3CategoryCounterName);
            if (controllerL3Counter == null) {
            	controllerL3Counter = createCounter(controllerL3CategoryCounterName,
                                               CounterType.LONG);
            }
            controllerL3Counter.increment();
            ICounter portL3Counter = getCounter(portL3CategoryCounterName);
            if (portL3Counter == null) {
                portL3Counter = createCounter(portL3CategoryCounterName,
                                                   CounterType.LONG);
            }
            portL3Counter.increment();
            ICounter switchL3Counter = getCounter(switchL3CategoryCounterName);
            if (switchL3Counter == null) {
                switchL3Counter = createCounter(switchL3CategoryCounterName,
                                                   CounterType.LONG);
            }
            switchL3Counter.increment();
            
            // L4 counters
            if (etherType.compareTo(CounterStore.L3ET_IPV4) == 0) {
                IPv4 ipV4 = (IPv4)eth.getPayload();
                String l4Type = String.format("%02x", ipV4.getProtocol());
                if (TypeAliases.l4TypeAliasMap != null && 
                    TypeAliases.l4TypeAliasMap.containsKey(l4Type)) {
                    l4Type = TypeAliases.l4TypeAliasMap.get(l4Type);
                } else {
                    l4Type = "L4_" + l4Type;
                }
                String controllerL4CategoryCounterName = 
                    CounterStore.createCounterName(CONTROLLER_NAME, 
                                                   -1, 
                                                   packetName, 
                                                   l4Type, 
                                                   NetworkLayer.L4);
                String portL4CategoryCounterName =
                        CounterStore.createCounterName(switchIdHex, 
                                                       packet.getInPort(), 
                                                       packetName, 
                                                       l4Type, 
                                                       NetworkLayer.L4);
                String switchL4CategoryCounterName = 
                        CounterStore.createCounterName(switchIdHex, 
                                                       -1, 
                                                       packetName, 
                                                       l4Type, 
                                                       NetworkLayer.L4);
                ICounter controllerL4Counter = getCounter(controllerL4CategoryCounterName);
                if (controllerL4Counter == null) {
                	controllerL4Counter = createCounter(controllerL4CategoryCounterName, 
                                                   CounterType.LONG);
                }
                controllerL4Counter.increment();
                ICounter portL4Counter = getCounter(portL4CategoryCounterName);
                if (portL4Counter == null) {
                    portL4Counter = createCounter(portL4CategoryCounterName, 
                                                       CounterType.LONG);
                }
                portL4Counter.increment();
                ICounter switchL4Counter = getCounter(switchL4CategoryCounterName);
                if (switchL4Counter == null) {
                    switchL4Counter = createCounter(switchL4CategoryCounterName, 
                                                       CounterType.LONG);
                }
                switchL4Counter.increment();
            }
        }
        catch (IllegalArgumentException e) {
            log.error("Invalid Counter, " + portCounterName + 
                      " or " + switchCounterName);
        }
    }
    
    /**
     * This method can only be used to update packetOut and flowmod counters
     * 
     * @param sw
     * @param ofMsg
     */
    public void updatePktOutFMCounterStore(IOFSwitch sw, OFMessage ofMsg) {
        String packetName = ofMsg.getType().toClass().getName();
        packetName = packetName.substring(packetName.lastIndexOf('.')+1);
        // flowmod is per switch and controller. portid = -1
        String controllerFMCounterName = CounterStore.createCounterName(CONTROLLER_NAME, -1, packetName);  
        try {
            ICounter counter = getCounter(controllerFMCounterName);
            if (counter == null) {
                counter = createCounter(controllerFMCounterName, CounterValue.CounterType.LONG);
            }
            counter.increment();
        } catch (IllegalArgumentException e) {
            log.error("Invalid Counter, " + controllerFMCounterName);
        }
        
        String switchFMCounterName = CounterStore.createCounterName(sw.getStringId(), -1, packetName);
        try{ 
        	ICounter counter = getCounter(switchFMCounterName);
            if (counter == null) {
                counter = createCounter(switchFMCounterName, CounterValue.CounterType.LONG);
            }
            counter.increment();
        } catch (IllegalArgumentException e) {
            log.error("Invalid Counter, " + switchFMCounterName);
        }
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

        if (!nameToCEIndex.containsKey(key)) {
            c = SimpleCounter.createCounter(new Date(), type);
            ce = new CounterEntry();
            ce.counter = c;
            ce.title = key;
            nameToCEIndex.put(key, ce);
        } else {
            throw new IllegalArgumentException("Title for counters must be unique, and there is already a counter with title " + key);
        }

        return c;
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
