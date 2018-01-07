package net.floodlightcontroller.routing;

import java.util.*;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.ITopologyManagerBackend;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * Separate path-finding and routing functionality from the 
 * topology package. It makes sense to keep much of the core
 * code in the TopologyInstance, but the TopologyManger is
 * too confusing implementing so many interfaces and doing
 * so many tasks. This is a cleaner approach IMHO.
 * 
 * All routing and path-finding functionality is visible to
 * the rest of the controller via the IRoutingService implemented
 * by the RoutingManger (this). The RoutingManger performs
 * tasks it can perform locally, such as the handling of
 * IRoutingDecisionChangedListeners, while it defers to the
 * current TopologyInstance (exposed via the ITopologyManagerBackend
 * interface) for tasks best performed by the topology
 * package, such as path-finding.
 * 
 * @author rizard
 */
public class RoutingManager implements IFloodlightModule, IRoutingService {
    private Logger log = LoggerFactory.getLogger(RoutingManager.class);
    
    private static ITopologyManagerBackend tm;
    private static L3RoutingManager l3manager;
    
    private List<IRoutingDecisionChangedListener> decisionChangedListeners;
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableSet.of(IRoutingService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(IRoutingService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableSet.of(ITopologyService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        log.debug("RoutingManager starting up");
        tm = (ITopologyManagerBackend) context.getServiceImpl(ITopologyService.class);
        l3manager = new L3RoutingManager();
        decisionChangedListeners = new ArrayList<IRoutingDecisionChangedListener>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException { }

    // L3 Routing APIs
    @Override
    public Optional<Collection<VirtualGateway>> getAllVirtualGateways() { return l3manager.getAllVirtualGateways(); }

    @Override
    public Optional<VirtualGateway> getVirtualGateway(String name) { return l3manager.getVirtualGateway(name); }

    @Override
    public void removeAllVirtualGateways() { l3manager.removeAllVirtualGateways(); }

    @Override
    public boolean removeVirtualGateway(String name) { return l3manager.removeVirtualGateway(name); }

    @Override
    public void createVirtualGateway(VirtualGateway gateway) { l3manager.createVirtualGateway(gateway); }

    @Override
    public void updateVirtualGateway(String name, MacAddress newMac) { l3manager.updateVirtualGateway(name, newMac); }

    @Override
    public Optional<Collection<VirtualGatewayInterface>> getAllGatewayInterfaces(VirtualGateway gateway) {
        return l3manager.getGatewayInterfaces(gateway);
    }

    @Override
    public Optional<VirtualGatewayInterface> getGatewayInterface(String name, VirtualGateway gateway) {
        return l3manager.getGatewayInterface(name, gateway);
    }

    @Override
    public void removeAllVirtualInterfaces(VirtualGateway gateway) {
        l3manager.removeAllVirtualInterfaces(gateway);
    }

    @Override
    public boolean removeVirtualInterface(String interfaceName, VirtualGateway gateway) {
        return l3manager.removeVirtualInterface(interfaceName, gateway);
    }

    @Override
    public void createVirtualInterface(VirtualGateway gateway, VirtualGatewayInterface intf) {
        l3manager.createVirtualInterface(gateway, intf);
    }

    @Override
    public void updateVirtualInterface(VirtualGateway gateway, VirtualGatewayInterface intf) {
        l3manager.updateVirtualInterface(gateway, intf);
    }

    @Override
    public Optional<Collection<VirtualSubnet>> getAllVirtualSubnets() {
        return l3manager.getAllVirtualSubnets();
    }

    @Override
    public Optional<VirtualSubnet> getVirtualSubnet(String name) {
        return l3manager.getVirtualSubnet(name);
    }

    @Override
    public SubnetBuildMode getCurrentSubnetBuildMode() {
        return l3manager.getCurrentSubnetMode();
    }

    @Override
    public boolean checkDPIDExist(DatapathId dpid) { return l3manager.checkDPIDExist(dpid); }

    @Override
    public boolean checkNPTExist(NodePortTuple nodePortTuple) { return l3manager.checkNPTExist(nodePortTuple); }

    @Override
    public void createVirtualSubnet(String name, IPv4Address gatewayIP, DatapathId dpid) {
        l3manager.createVirtualSubnet(name, gatewayIP, dpid);
    }

    @Override
    public void createVirtualSubnet(String name, IPv4Address gatewayIP, NodePortTuple npt) {
        l3manager.createVirtualSubnet(name, gatewayIP, npt);
    }

    @Override
    public void removeAllVirtualSubnets() { l3manager.removeAllVirtualSubnets(); }

    @Override
    public boolean removeVirtualSubnet(String name) { return l3manager.removeVirtualSubnet(name); }

    @Override
    public void updateVirtualSubnet(String name, IPv4Address gatewayIP, DatapathId dpid) {
        l3manager.updateVirtualSubnet(name, gatewayIP, dpid);
    }

    @Override
    public void updateVirtualSubnet(String name, IPv4Address gatewayIP, NodePortTuple npt) {
        l3manager.updateVirtualSubnet(name, gatewayIP, npt);
    }

    @Override
    public boolean isSameSubnet(IOFSwitch sw1, IOFSwitch sw2) {
        return l3manager.isSameSubnet(sw1, sw2);
    }

    @Override
    public boolean isSameSubnet(NodePortTuple npt1, NodePortTuple npt2) {
        return l3manager.isSameSubnet(npt1, npt2);
    }

    // Ends here

    @Override
    public void setPathMetric(PATH_METRIC metric) {
        tm.setPathMetric(metric);
    }

    @Override
    public PATH_METRIC getPathMetric() {
        return tm.getPathMetric();
    }


    @Override
    public void setMaxPathsToCompute(int max) {
        tm.setMaxPathsToCompute(max);
    }

    @Override
    public int getMaxPathsToCompute() {
        return tm.getMaxPathsToCompute();
    }

    @Override
    public Path getPath(DatapathId src, DatapathId dst) {
        return tm.getCurrentTopologyInstance().getPath(src, dst);
    }

    @Override
    public Path getPath(DatapathId src, OFPort srcPort, DatapathId dst, OFPort dstPort) {
        return tm.getCurrentTopologyInstance().getPath(src, srcPort, dst, dstPort);
    }

    @Override
    public List<Path> getPathsFast(DatapathId src, DatapathId dst) {
        return tm.getCurrentTopologyInstance().getPathsFast(src, dst, tm.getMaxPathsToCompute());
    }

    @Override
    public List<Path> getPathsFast(DatapathId src, DatapathId dst, int numReqPaths) {
        return tm.getCurrentTopologyInstance().getPathsFast(src, dst, numReqPaths);
    }

    @Override
    public List<Path> getPathsSlow(DatapathId src, DatapathId dst, int numReqPaths) {
        return tm.getCurrentTopologyInstance().getPathsSlow(src, dst, numReqPaths);
    }

    @Override
    public boolean pathExists(DatapathId src, DatapathId dst) {
        return tm.getCurrentTopologyInstance().pathExists(src, dst);
    }
    
    @Override
    public boolean forceRecompute() {
        return tm.forceRecompute();
    }

    /** 
     * Registers an IRoutingDecisionChangedListener.
     *   
     * @param listener
     * @return 
     */
    @Override
    public void addRoutingDecisionChangedListener(IRoutingDecisionChangedListener listener) {
        decisionChangedListeners.add(listener);
    }
    
    /** 
     * Deletes an IRoutingDecisionChangedListener.
     *   
     * @param listener 
     * @return
     */
    @Override
    public void removeRoutingDecisionChangedListener(IRoutingDecisionChangedListener listener) {
        decisionChangedListeners.remove(listener);
    }

    /** 
     * Listens for the event to the IRoutingDecisionChanged listener and calls routingDecisionChanged().
     *   
     * @param changedDecisions
     * @return
     */
    @Override
    public void handleRoutingDecisionChange(Iterable<Masked<U64>> changedDecisions) {
        for (IRoutingDecisionChangedListener listener : decisionChangedListeners) {
            listener.routingDecisionChanged(changedDecisions);
        }
    }
}