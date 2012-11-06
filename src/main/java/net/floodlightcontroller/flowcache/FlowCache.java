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
