package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;


import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author srini
 *
 */

public class TopologyManager implements IFloodlightModule, ITopologyService, 
IRoutingService, ILinkDiscoveryListener {

    protected static Logger log = LoggerFactory.getLogger(TopologyManager.class);

    protected Map<Long, Set<Short>> switchPorts; // Set of ports for each switch
    protected Map<NodePortTuple, Set<Link>> switchPortLinks; // Set of links organized by node port tuple
    protected Map<NodePortTuple, Set<Link>> portBroadcastDomainLinks; // set of links that are broadcast domain links.
    protected Map<NodePortTuple, Set<Link>> tunnelLinks; // set of tunnel links
    protected ILinkDiscoveryService linkDiscovery;
    protected ArrayList<ITopologyListener> topologyAware;

    protected TopologyInstance currentInstance;

    public void recompute() {
        createNewInstance();
        informListeners();
    }

    /**
     * This function computes a new topology.
     */
    public void createNewInstance() {
        TopologyInstance nt = new TopologyInstance(switchPorts, switchPortLinks, portBroadcastDomainLinks, tunnelLinks);
        nt.compute();
        currentInstance = nt;
    }

    public void informListeners() {
        for(int i=0; i<topologyAware.size(); ++i) {
            ITopologyListener listener = topologyAware.get(i);
            listener.toplogyChanged();
        }
    }

    public void addSwitch(long sid) {
        if (switchPorts.containsKey(sid) == false) {
            switchPorts.put(sid, new HashSet<Short>());
        }
    }

    private void addPortToSwitch(long s, short p) {
        addSwitch(s);
        switchPorts.get(s).add(p);
    }

    public void removeSwitch(long sid) {
        // Delete all the links in the switch, switch and all 
        // associated data should be deleted.
        if (switchPorts.containsKey(sid) == false) return;

        Set<Link> linksToRemove = new HashSet<Link>();
        for(Short p: switchPorts.get(sid)) {
            NodePortTuple n1 = new NodePortTuple(sid, p);
            linksToRemove.addAll(switchPortLinks.get(n1));
        }

        for(Link link: linksToRemove) {
            removeLink(link);
        }
    }

    private boolean addLinkToStructure(Map<NodePortTuple, Set<Link>> s, Link l) {
        boolean result1 = false, result2 = false; 

        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());

        if (s.get(n1) == null) {
            s.put(n1, new HashSet<Link>()); 
        }
        if (s.get(n2) == null) {
            s.put(n2, new HashSet<Link>()); 
        }
        result1 = s.get(n1).add(l);
        result2 = s.get(n2).add(l);

        return (result1 && result2);
    }

    private boolean removeLinkFromStructure(Map<NodePortTuple, Set<Link>> s, Link l) {

        boolean result1 = false, result2 = false;
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());

        if (s.get(n1) != null) {
            result1 = s.get(n1).remove(l);
            if (s.get(n1).isEmpty()) s.remove(n1);
        }
        if (s.get(n2) != null) {
            result2 = s.get(n2).remove(l);
            if (s.get(n2).isEmpty()) s.remove(n2);
        }
        return result1 && result2; 
    }

    public void addOrUpdateLink(long srcId, short srcPort, long dstId, short dstPort, LinkType type) {
        Link link = new Link(srcId, srcPort, dstId, dstPort);

        addPortToSwitch(srcId, srcPort);
        addPortToSwitch(dstId, dstPort);

        addLinkToStructure(switchPortLinks, link);

        if (type.equals(LinkType.MULTIHOP_LINK)) {
            addLinkToStructure(portBroadcastDomainLinks, link);
            removeLinkFromStructure(tunnelLinks, link);
        } else if (type.equals(LinkType.TUNNEL)) {
            addLinkToStructure(tunnelLinks, link);
            removeLinkFromStructure(portBroadcastDomainLinks, link);
        }

        this.createNewInstance();
    }

    public void removeLink(Link link)  {
        removeLinkFromStructure(portBroadcastDomainLinks, link);
        removeLinkFromStructure(tunnelLinks, link);
        removeLinkFromStructure(switchPortLinks, link);

        NodePortTuple n1 = new NodePortTuple(link.getSrc(), link.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(link.getDst(), link.getDstPort());

        // Remove switch ports if there are no links through those switch ports
        if (switchPortLinks.get(n1) == null) {
            if (switchPorts.get(link.getSrc()) != null)
                switchPorts.get(link.getSrc()).remove(link.getSrcPort());
        }
        if (switchPortLinks.get(n2) == null) {
            if (switchPorts.get(link.getDst()) != null)
                switchPorts.get(link.getDst()).remove(link.getDstPort());
        }

        // Remove the node if no ports are present
        if (switchPorts.get(link.getSrc())!=null && 
                switchPorts.get(link.getSrc()).isEmpty()) {
            switchPorts.remove(link.getSrc());
        }
        if (switchPorts.get(link.getDst())!=null && 
                switchPorts.get(link.getDst()).isEmpty()) {
            switchPorts.remove(link.getDst());
        }
        this.createNewInstance();
    }

    public void removeLink(long srcId, short srcPort, long dstId, short dstPort) {
        Link link = new Link(srcId, srcPort, dstId, dstPort);
        removeLink(link);
    }

    public void clear() {
        switchPorts.clear();
        switchPortLinks.clear();
        portBroadcastDomainLinks.clear();
        tunnelLinks.clear();
    }


    /**
     * Getters.  No Setters.
     */
    public Map<Long, Set<Short>> getSwitchPorts() {
        return switchPorts;
    }

    public Map<NodePortTuple, Set<Link>> getSwitchPortLinks() {
        return switchPortLinks;
    }

    public Map<NodePortTuple, Set<Link>> getPortBroadcastDomainLinks() {
        return portBroadcastDomainLinks;
    }

    public Map<NodePortTuple, Set<Link>> getTunnelLinks() {
        return tunnelLinks;
    }

    public TopologyInstance getCurrentInstance() {
        return currentInstance;
    }

    //
    //  ILinkDiscoveryListener interface methods
    //

    public void linkDiscoveryUpdate(LDUpdate update) {
        if (update.getOperation() == UpdateOperation.ADD_OR_UPDATE) {
            boolean added = (((update.getSrcPortState() & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue()) &&
                    ((update.getDstPortState() & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue()));
            if (added) {
            addOrUpdateLink(update.getSrc(), update.getSrcPort(), 
                               update.getDst(), update.getDstPort(), 
                               update.getType());
            } else  {
                removeLink(update.getSrc(), update.getSrcPort(), update.getDst(), update.getDstPort());
            }
        } else if (update.getOperation() == UpdateOperation.REMOVE) {
            removeLink(update.getSrc(), update.getSrcPort(), update.getDst(), update.getDstPort());
        }
    }

    //
    //   IFloodlightModule interfaces
    //

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ITopologyService.class);
        l.add(IRoutingService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
        new HashMap<Class<? extends IFloodlightService>,
        IFloodlightService>();
        // We are the class that implements the service
        m.put(ITopologyService.class, this);
        m.put(IRoutingService.class, this);
        return m;

    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
    getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        linkDiscovery = context.getServiceImpl(ILinkDiscoveryService.class);
        switchPorts = new HashMap<Long,Set<Short>>();
        switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        portBroadcastDomainLinks = new HashMap<NodePortTuple, Set<Link>>();
        tunnelLinks = new HashMap<NodePortTuple, Set<Link>>();
        topologyAware = new ArrayList<ITopologyListener>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // TODO Auto-generated method stub
        linkDiscovery.addListener(this);
        this.createNewInstance();
    }

    //
    // ITopologyService interface methods
    //
    @Override
    public boolean isInternal(long switchid, short port) {
        return currentInstance.isInternal(switchid, port);
    }

    @Override
    public long getSwitchClusterId(long switchId) {
        return currentInstance.getSwitchClusterId(switchId);
    }

    @Override
    public Set<Long> getSwitchesInCluster(long switchId) {
        return currentInstance.getSwitchesInCluster(switchId);
    }

    @Override
    public boolean inSameCluster(Long switch1, Long switch2) {
        return currentInstance.inSameCluster(switch1, switch2);
    }

    @Override
    public void addListener(ITopologyListener listener) {
        topologyAware.add(listener);
    }

    @Override
    public boolean isIncomingBroadcastAllowedOnSwitchPort(long sw, short portId) {
        return currentInstance.isIncomingBroadcastAllowedOnSwitchPort(sw, portId);
    }

    //
    // IRoutingService interface methods
    //
    @Override
    public Route getRoute(long src, long dst) {
        Route r = currentInstance.getRoute(src, dst);
        return r;
    }

    @Override
    public boolean routeExists(long src, long dst) {
        return currentInstance.routeExists(src, dst);
    }

    @Override
    public BroadcastTree getBroadcastTreeForCluster(long clusterId) {
        return currentInstance.getBroadcastTreeForCluster(clusterId);
    }
}
