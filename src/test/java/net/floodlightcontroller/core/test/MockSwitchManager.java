package net.floodlightcontroller.core.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IAppHandshakePluginFactory;
import net.floodlightcontroller.core.internal.IOFSwitchManager;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler;
import net.floodlightcontroller.core.internal.SwitchManagerCounters;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.DebugCounterServiceImpl;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

import com.google.common.collect.ImmutableList;

public class MockSwitchManager implements IFloodlightModule, IOFSwitchManager, IOFSwitchService {

    private Map<DatapathId, OFSwitchHandshakeHandler> switchHandlers;
    private Map<DatapathId, IOFSwitch> switches;
    private final SwitchManagerCounters counters;
    //private final CopyOnWriteArrayList<IOFSwitchListener> switchListeners;

    public MockSwitchManager(){
        switchHandlers = new ConcurrentHashMap<DatapathId, OFSwitchHandshakeHandler>();
        switches = new ConcurrentHashMap<DatapathId, IOFSwitch>();
        counters = new SwitchManagerCounters(new DebugCounterServiceImpl());
        //switchListeners = new CopyOnWriteArrayList<IOFSwitchListener>();
    }

    @Override
    public void switchAdded(IOFSwitchBackend sw) {
        // do nothing

    }

    @Override
    public void switchDisconnected(IOFSwitchBackend sw) {
     // do nothing

    }

    @Override
    public void handshakeDisconnected(DatapathId dpid) {
        // do nothing
    }

    @Override
    public void notifyPortChanged(IOFSwitchBackend sw, OFPortDesc port,
                                  PortChangeType type) {
     // do nothing

    }

    @Override
    public IOFSwitchBackend
            getOFSwitchInstance(IOFConnectionBackend connection,
                                SwitchDescription description,
                                OFFactory factory, DatapathId datapathId) {
        return null;
    }

    @Override
    public void handleMessage(IOFSwitchBackend sw, OFMessage m,
                              FloodlightContext bContext) {
        // do nothing

    }
    
    @Override
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m) {
    	// do nothing
    	
    }

    public void setSwitchHandshakeHandlers(Map<DatapathId, OFSwitchHandshakeHandler> handlers) {
        this.switchHandlers = handlers;
    }
    @Override
    public ImmutableList<OFSwitchHandshakeHandler>
            getSwitchHandshakeHandlers() {
        return ImmutableList.copyOf(this.switchHandlers.values());
    }

    @Override
    public void addOFSwitchDriver(String manufacturerDescriptionPrefix,
                                  IOFSwitchDriver driver) {
        // do nothing

    }

    @Override
    public void switchStatusChanged(IOFSwitchBackend sw,
                                    SwitchStatus oldStatus,
                                    SwitchStatus newStatus) {
        // do nothing

    }

    @Override
    public int getNumRequiredConnections() {
        return 0;
    }

    @Override
    public void addSwitchEvent(DatapathId switchDpid, String reason,
                               boolean flushNow) {
        // do nothing

    }

    @Override
    public List<IAppHandshakePluginFactory> getHandshakePlugins() {
        return null;
    }

    @Override
    public SwitchManagerCounters getCounters() {
        return this.counters;
    }

    @Override
    public boolean isCategoryRegistered(LogicalOFMessageCategory category) {
        return false;
    }

    public void setSwitches(Map<DatapathId, IOFSwitch> switches) {
        this.switches = switches;
    }

    @Override
    public Map<DatapathId, IOFSwitch> getAllSwitchMap() {
        return Collections.unmodifiableMap(switches);
    }

    @Override
    public IOFSwitch getSwitch(DatapathId dpid) {
        return this.switches.get(dpid);
    }

    @Override
    public IOFSwitch getActiveSwitch(DatapathId dpid) {
        IOFSwitch sw = this.switches.get(dpid);
        if(sw != null && sw.getStatus().isVisible())
            return sw;
        else
            return null;
    }

    @Override
    public void addOFSwitchListener(IOFSwitchListener listener) {
        // do nothing
    }

    @Override
    public void removeOFSwitchListener(IOFSwitchListener listener) {
        // do nothing
    }

    @Override
    public void registerLogicalOFMessageCategory(LogicalOFMessageCategory category) {
        // do nothing
    }

    @Override
    public void registerHandshakePlugin(IAppHandshakePluginFactory plugin) {
        // do nothing

    }

    @Override
    public Set<DatapathId> getAllSwitchDpids() {
        return this.switches.keySet();
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(IOFSwitchService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
            new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(IOFSwitchService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        // do nothing
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // do nothing
    }
}
