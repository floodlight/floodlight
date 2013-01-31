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

package net.floodlightcontroller.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.openflow.protocol.OFMatchWithSwDpid;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class FlowCache implements IFloodlightModule, IFlowCacheService {

    @Override
    public void submitFlowCacheQuery(FCQueryObj query) {}

    @Override
    public void deleteFlowCacheBySwitch(long switchDpid) {}

    @Override
    public void updateFlush() {}
    
    @Override
    public boolean addFlow(String appInstName, OFMatchWithSwDpid ofm, 
                           Long cookie, long srcSwDpid, 
                           short inPort, short priority, byte action) {
        return true;
    }

    @Override
    public boolean addFlow(FloodlightContext cntx, OFMatchWithSwDpid ofm, 
                           Long cookie, SwitchPort swPort, 
                           short priority, byte action) {
        return true;
    }

    @Override
    public boolean moveFlowToDifferentApplInstName(OFMatchReconcile ofMRc) {
        return true;
    }

    @Override
    public void deleteAllFlowsAtASourceSwitch(IOFSwitch sw) {}
    
    @Override
    public void querySwitchFlowTable(long swDpid) {}
    
    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
       l.add(IFlowCacheService.class);
       return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> 
                                                            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        m.put(IFlowCacheService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> 
                                                    getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {}

    @Override
    public void startUp(FloodlightModuleContext context) {}
}
