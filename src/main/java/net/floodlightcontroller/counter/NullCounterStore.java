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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMessage;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.CounterStore.NetworkLayer;
import net.floodlightcontroller.counter.CounterValue.CounterType;
import net.floodlightcontroller.packet.Ethernet;

/**
 * An ICounsterStoreService implementation that does nothing.
 * This is used mainly for performance testing or if you don't
 * want to use the counterstore.
 * @author alexreimers
 *
 */
public class NullCounterStore implements IFloodlightModule,
        ICounterStoreService {

    private ICounter emptyCounter;
    private List<String> emptyList;
    private Map<String, ICounter> emptyMap;
    
    @Override
    public void updatePacketInCounters(IOFSwitch sw, OFMessage m, Ethernet eth) {
        // no-op
    }

    @Override
    public void updatePacketInCountersLocal(IOFSwitch sw, OFMessage m, Ethernet eth) {
        // no-op
    }

    @Override
    public void updatePktOutFMCounterStore(IOFSwitch sw, OFMessage ofMsg) {
        // no-op
    }

    @Override
    public void updatePktOutFMCounterStoreLocal(IOFSwitch sw, OFMessage ofMsg) {
        // no-op
    }

    @Override
    public void updateFlush() {
        // no-op
    }

    @Override
    public List<String>
            getAllCategories(String counterName, NetworkLayer layer) {
        return emptyList;
    }

    @Override
    public ICounter createCounter(String key, CounterType type) {
        return emptyCounter;
    }

    @Override
    public ICounter getCounter(String key) {
        return emptyCounter;
    }

    @Override
    public Map<String, ICounter> getAll() {
        return emptyMap;
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
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        // None, return null
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
                             throws FloodlightModuleException {
        emptyCounter = new SimpleCounter(new Date(), CounterType.LONG);
        emptyList = new ArrayList<String>();
        emptyMap = new HashMap<String, ICounter>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // no-op
    }
}
