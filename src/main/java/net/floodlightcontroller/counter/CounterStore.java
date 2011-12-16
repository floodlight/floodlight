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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;


/**
 * @author kyle
 *
 */
public class CounterStore {
    public final static String TitleDelimitor = "__";

    /** L2 EtherType subCategories */
    public final static String L3ET_IPV4 = "L3_IPv4";

    public enum NetworkLayer {
        L3, L4
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

    /**
     * Retrieve a list of subCategories by counterName.
     * null if nothing.
     */
    public List<String> getAllCategories(String counterName, NetworkLayer layer) {
        if (layeredCategories.containsKey(layer)) {
            Map<String, List<String>> counterToCategories = layeredCategories.get(layer);
            if (counterToCategories.containsKey(counterName)) {
                return counterToCategories.get(counterName);
            }
        }
        return null;
    }
    
    /**
     * Create a new ICounter and set the title.  Note that the title must be unique, otherwise this will
     * throw an IllegalArgumentException.
     * 
     * @param title
     * @return
     */
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
    
    /**
     * Retrieves a counter with the given title, or null if none can be found.
     */
    public ICounter getCounter(String key) {
        CounterEntry counter = nameToCEIndex.get(key);
        if (counter != null) {
            return counter.counter;
        } else {
            return null;
        }
    }

    /**
     * Returns an immutable map of title:counter with all of the counters in the store.
     * 
     * (Note - this method may be slow - primarily for debugging/UI)
     */
    public Map<String, ICounter> getAll() {
        Map<String, ICounter> ret = new ConcurrentHashMap<String, ICounter>();
        for(Map.Entry<String, CounterEntry> counterEntry : this.nameToCEIndex.entrySet()) {
            String key = counterEntry.getKey();
            ICounter counter = counterEntry.getValue().counter;
            ret.put(key, counter);
        }
        return ret;
    }

}
