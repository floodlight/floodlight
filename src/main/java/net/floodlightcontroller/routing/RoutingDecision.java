package net.floodlightcontroller.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;


public class RoutingDecision implements IRoutingDecision {

    protected RoutingAction action;
    protected Integer wildcards;
    protected SwitchPort srcPort;
    protected IDevice srcDevice;
    protected List<IDevice> destDevices;
    protected List<SwitchPort> broadcastIntertfaces;

    public RoutingDecision(long swDipd,
                                  short inPort,
                                  IDevice srcDevice,
                                  RoutingAction action) {
        this.srcPort = new SwitchPort(swDipd, inPort);
        this.srcDevice = srcDevice;
        this.destDevices = 
                Collections.synchronizedList(new ArrayList<IDevice>());
        this.broadcastIntertfaces = 
                Collections.synchronizedList(new ArrayList<SwitchPort>());
        this.action = action;
        this.wildcards = null;
    }
    
    @Override
    public RoutingAction getRoutingAction() {
        return this.action;
    }
    
    @Override
    public void setRoutingAction(RoutingAction action) {
        this.action = action;
    }
    
    @Override
    public SwitchPort getSourcePort() {
        return this.srcPort;
    }
    
    @Override
    public IDevice getSourceDevice() {
        return this.srcDevice;
    }
    
    @Override
    public List<IDevice> getDestinationDevices() {
        return this.destDevices;
    }
    
    @Override
    public void addDestinationDevice(IDevice d) {
        if (!destDevices.contains(d)) {
            destDevices.add(d);
        }
    }
    
    @Override
    public void setMulticastInterfaces(List<SwitchPort> lspt) {
        this.broadcastIntertfaces = lspt;
    }
    
    @Override
    public List<SwitchPort> getMulticastInterfaces() {
        return this.broadcastIntertfaces;
    }
    
    @Override
    public Integer getWildcards() {
        return this.wildcards;
    }
    
    @Override
    public void setWildcards(Integer wildcards) {
        this.wildcards = wildcards;
    }
   
    @Override
    public void addToContext(FloodlightContext cntx) {
        rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION, this);
    }
    
    public String toString() {
        return "action " + action +
               " wildcard " +
               ((wildcards == null) ? null : "0x"+Integer.toHexString(wildcards.intValue()));
    }
}
