package net.floodlightcontroller.topology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.web.TopologyWebRoutable;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Topology manager is responsible for maintaining the controller's notion
 * of the network graph, as well as implementing tools for finding routes 
 * through the topology.
 */
public class TopologyManager implements 
        IFloodlightModule, ITopologyService, 
        IRoutingService, ILinkDiscoveryListener,
        IOFMessageListener, IHAListener {

    protected static Logger log = LoggerFactory.getLogger(TopologyManager.class);

    public static final String CONTEXT_TUNNEL_ENABLED = 
            "com.bigswitch.floodlight.topologymanager.tunnelEnabled";

    /** 
     * Set of ports for each switch 
     */
    protected Map<Long, Set<Short>> switchPorts; 

    /**
     * Set of links organized by node port tuple
     */
    protected Map<NodePortTuple, Set<Link>> switchPortLinks;

    /**
     * set of links that are broadcast domain links.
     */
    protected Map<NodePortTuple, Set<Link>> portBroadcastDomainLinks;

    /**
     * set of tunnel links
     */
    protected Map<NodePortTuple, Set<Link>> tunnelLinks; 
    protected ILinkDiscoveryService linkDiscovery;
    protected IThreadPoolService threadPool;
    protected IFloodlightProviderService floodlightProvider;
    protected IRestApiService restApi;

    // Modules that listen to our updates
    protected ArrayList<ITopologyListener> topologyAware;

    protected BlockingQueue<LDUpdate> ldUpdates;
    protected List<LDUpdate> appliedUpdates;
    
    // These must be accessed using getCurrentInstance(), not directly
    protected TopologyInstance currentInstance;
    protected TopologyInstance currentInstanceWithoutTunnels;
    
    protected SingletonTask newInstanceTask;
    private Date lastUpdateTime;

    /**
     * Thread for recomputing topology.  The thread is always running, 
     * however the function applyUpdates() has a blocking call.
     */
    protected class NewInstanceWorker implements Runnable {
        @Override 
        public void run() {
            try {
                applyUpdates();
                createNewInstance();
                lastUpdateTime = new Date();
                informListeners();
            }
            catch (Exception e) {
                log.error("Error in topology instance task thread", e);
            }
        }
    }

    // **********************
    // ILinkDiscoveryListener
    // **********************

    @Override
    public void linkDiscoveryUpdate(LDUpdate update) {
        boolean scheduleFlag = false;
        // if there's no udpates in the queue, then
        // we need to schedule an update.
        if (ldUpdates.peek() == null)
            scheduleFlag = true;

        if (log.isTraceEnabled()) {
            log.trace("Queuing update: {}", update);
        }
        ldUpdates.add(update);

        if (scheduleFlag) {
            newInstanceTask.reschedule(1, TimeUnit.MICROSECONDS);
        }
    }
    
    // ****************
    // ITopologyService
    // ****************

    //
    // ITopologyService interface methods
    //
    @Override
    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public void addListener(ITopologyListener listener) {
        topologyAware.add(listener);
    }

    @Override 
    public boolean isAttachmentPointPort(long switchid, short port) {
        return isAttachmentPointPort(switchid, port, true);
    }

    @Override
    public boolean isAttachmentPointPort(long switchid, short port, 
                                         boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);

        // if the port is not attachment point port according to
        // topology instance, then return false
        if (ti.isAttachmentPointPort(switchid, port) == false)
                return false;

        // Check whether the port is a physical port. We should not learn
        // attachment points on "special" ports.
        if ((port & 0xff00) == 0xff00 && port != (short)0xfffe) return false;

        // Make sure that the port is enabled.
        IOFSwitch sw = floodlightProvider.getSwitches().get(switchid);
        if (sw == null) return false;
        return (sw.portEnabled(port));
    }

    public long getOpenflowDomainId(long switchId) {
        return getOpenflowDomainId(switchId, true);
    }

    public long getOpenflowDomainId(long switchId, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getOpenflowDomainId(switchId);
    }

    @Override
    public long getL2DomainId(long switchId) {
        return getL2DomainId(switchId, true);
    }

    @Override
    public long getL2DomainId(long switchId, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getL2DomainId(switchId);
    }

    @Override
    public boolean inSameOpenflowDomain(long switch1, long switch2) {
        return inSameOpenflowDomain(switch1, switch2, true);
    }

    @Override
    public boolean inSameOpenflowDomain(long switch1, long switch2,
                                        boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.inSameOpenflowDomain(switch1, switch2);
    }

    @Override
    public boolean isAllowed(long sw, short portId) {
        return isAllowed(sw, portId, true);
    }

    @Override
    public boolean isAllowed(long sw, short portId, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isAllowed(sw, portId);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public boolean isIncomingBroadcastAllowed(long sw, short portId) {
        return isIncomingBroadcastAllowed(sw, portId, true);
    }

    public boolean isIncomingBroadcastAllowed(long sw, short portId,
                                              boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isIncomingBroadcastAllowedOnSwitchPort(sw, portId);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /** Get all the ports connected to the switch */
    @Override
    public Set<Short> getPorts(long sw) {
        return getPorts(sw, true);
    }

    /** Get all the ports connected to the switch */
    @Override
    public Set<Short> getPorts(long sw, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getPorts(sw);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /** Get all the ports on the target switch (targetSw) on which a 
     * broadcast packet must be sent from a host whose attachment point
     * is on switch port (src, srcPort).
     */
    public Set<Short> getBroadcastPorts(long targetSw, 
                                        long src, short srcPort) {
        return getBroadcastPorts(targetSw, src, srcPort, true);
    }

    /** Get all the ports on the target switch (targetSw) on which a 
     * broadcast packet must be sent from a host whose attachment point
     * is on switch port (src, srcPort).
     */
    public Set<Short> getBroadcastPorts(long targetSw, 
                                        long src, short srcPort,
                                        boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getBroadcastPorts(targetSw, src, srcPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        // Use this function to redirect traffic if needed.
        return getOutgoingSwitchPort(src, srcPort, dst, dstPort, true);
    }
    
    @Override
    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort,
                                               boolean tunnelEnabled) {
        // Use this function to redirect traffic if needed.
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getOutgoingSwitchPort(src, srcPort,
                                                     dst, dstPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        return getIncomingSwitchPort(src, srcPort, dst, dstPort, true);
    }

    @Override
    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort,
                                               boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getIncomingSwitchPort(src, srcPort,
                                                     dst, dstPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * Checks if the two switchports belong to the same broadcast domain.
     */
    @Override
    public boolean isInSameBroadcastDomain(long s1, short p1, long s2,
                                           short p2) {
        return isInSameBroadcastDomain(s1, p1, s2, p2, true);

    }

    @Override
    public boolean isInSameBroadcastDomain(long s1, short p1,
                                           long s2, short p2,
                                           boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.inSameBroadcastDomain(s1, p1, s2, p2);

    }


    /**
     * Checks if the switchport is a broadcast domain port or not.
     */
    @Override
    public boolean isBroadcastDomainPort(long sw, short port) {
        return isBroadcastDomainPort(sw, port, true);
    }

    @Override
    public boolean isBroadcastDomainPort(long sw, short port,
                                         boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isBroadcastDomainPort(new NodePortTuple(sw, port));
    }


    /**
     * Checks if the new attachment point port is consistent with the
     * old attachment point port.
     */
    @Override
    public boolean isConsistent(long oldSw, short oldPort,
                                long newSw, short newPort) {
        return isConsistent(oldSw, oldPort,
                                            newSw, newPort, true);
    }

    @Override
    public boolean isConsistent(long oldSw, short oldPort,
                                long newSw, short newPort,
                                boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isConsistent(oldSw, oldPort, newSw, newPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * Checks if the two switches are in the same Layer 2 domain.
     */
    @Override
    public boolean inSameL2Domain(long switch1, long switch2) {
        return inSameL2Domain(switch1, switch2, true);
    }

    @Override
    public boolean inSameL2Domain(long switch1, long switch2,
                                  boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.inSameL2Domain(switch1, switch2);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple getAllowedOutgoingBroadcastPort(long src,
                                                         short srcPort,
                                                         long dst,
                                                         short dstPort) {
        return getAllowedOutgoingBroadcastPort(src, srcPort,
                                               dst, dstPort, true);
    }

    @Override
    public NodePortTuple getAllowedOutgoingBroadcastPort(long src,
                                                         short srcPort,
                                                         long dst,
                                                         short dstPort,
                                                         boolean tunnelEnabled){
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getAllowedOutgoingBroadcastPort(src, srcPort,
                                                  dst, dstPort);
    }
    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple 
    getAllowedIncomingBroadcastPort(long src, short srcPort) {
        return getAllowedIncomingBroadcastPort(src,srcPort, true);
    }

    @Override
    public NodePortTuple 
    getAllowedIncomingBroadcastPort(long src, short srcPort,
                                    boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getAllowedIncomingBroadcastPort(src,srcPort);
    }

    @Override
    public Set<NodePortTuple> getBroadcastDomainPorts() {
        return portBroadcastDomainLinks.keySet();
    }

    @Override
    public Set<NodePortTuple> getTunnelPorts() {
        return tunnelLinks.keySet();
    }

    @Override
    public Set<NodePortTuple> getBlockedPorts() {
        Set<NodePortTuple> bp;
        Set<NodePortTuple> blockedPorts =
                new HashSet<NodePortTuple>();

        // As we might have two topologies, simply get the union of
        // both of them and send it.
        bp = getCurrentInstance(true).getBlockedPorts();
        if (bp != null)
            blockedPorts.addAll(bp);

        bp = getCurrentInstance(false).getBlockedPorts();
        if (bp != null)
            blockedPorts.addAll(bp);

        return blockedPorts;
    }

    @Override
    public List<LDUpdate> getLastLinkUpdates() {
    	return appliedUpdates;
    }
    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////

    // ***************
    // IRoutingService
    // ***************

    @Override
    public Route getRoute(long src, long dst) {
        return getRoute(src, dst, true);
    }

    @Override
    public Route getRoute(long src, long dst, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getRoute(src, dst);
    }

    @Override
    public Route getRoute(long src, short srcPort, long dst, short dstPort) {
        return getRoute(src, srcPort, dst, dstPort, true);
    }

    @Override
    public Route getRoute(long src, short srcPort, long dst, short dstPort, 
                          boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getRoute(src, srcPort, dst, dstPort);
    }

    @Override
    public boolean routeExists(long src, long dst) {
        return routeExists(src, dst, true);
    }

    @Override
    public boolean routeExists(long src, long dst, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.routeExists(src, dst);
    }


    // ******************
    // IOFMessageListener
    // ******************

    @Override
    public String getName() {
        return "topology";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return "linkdiscovery".equals(name);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw, 
                                                   (OFPacketIn) msg, cntx);
            default:
            	break;
        }

        log.error("received an unexpected message {} from switch {}", 
                  msg, sw);
        return Command.CONTINUE;
    }

    // ***************
    // IHAListener
    // ***************

    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    log.debug("Re-computing topology due " +
                            "to HA change from SLAVE->MASTER");
                    newInstanceTask.reschedule(1, TimeUnit.MILLISECONDS);
                }
                break;
            case SLAVE:
                log.debug("Clearing topology due to " +
                        "HA change to SLAVE");
                clearCurrentTopology();
                break;
            default:
            	break;
        }
    }

    @Override
    public void controllerNodeIPsChanged(
                          Map<String, String> curControllerNodeIPs,
                          Map<String, String> addedControllerNodeIPs,
                          Map<String, String> removedControllerNodeIPs) {
        // no-op
    }

    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
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
        l.add(IThreadPoolService.class);
        l.add(IFloodlightProviderService.class);
        l.add(ICounterStoreService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        linkDiscovery = context.getServiceImpl(ILinkDiscoveryService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        floodlightProvider = 
                context.getServiceImpl(IFloodlightProviderService.class);
        restApi = context.getServiceImpl(IRestApiService.class);

        switchPorts = new HashMap<Long,Set<Short>>();
        switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        portBroadcastDomainLinks = new HashMap<NodePortTuple, Set<Link>>();
        tunnelLinks = new HashMap<NodePortTuple, Set<Link>>();
        topologyAware = new ArrayList<ITopologyListener>();
        ldUpdates = new LinkedBlockingQueue<LDUpdate>();
        appliedUpdates = new ArrayList<LDUpdate>();
        clearCurrentTopology();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        newInstanceTask = new SingletonTask(ses, new NewInstanceWorker());
        linkDiscovery.addListener(this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addHAListener(this);
        addRestletRoutable();
    }

    protected void addRestletRoutable() {
        restApi.addRestletRoutable(new TopologyWebRoutable());
    }

    // ****************
    // Internal methods
    // ****************
    /**
     * If the packet-in switch port is disabled for all data traffic, then
     * the packet will be dropped.  Otherwise, the packet will follow the
     * normal processing chain.
     * @param sw
     * @param pi
     * @param cntx
     * @return
     */
    protected Command dropFilter(long sw, OFPacketIn pi,
                                             FloodlightContext cntx) {
        Command result = Command.CONTINUE;
        short port = pi.getInPort();

        // If the input port is not allowed for data traffic, drop everything.
        // BDDP packets will not reach this stage.
        if (isAllowed(sw, port) == false) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring packet because of topology " +
                        "restriction on switch={}, port={}", sw, port);
                result = Command.STOP;
            }
        }

        // if sufficient information is available, then drop broadcast
        // packets here as well.
        return result;
    }

    /** 
     * TODO This method must be moved to a layer below forwarding
     * so that anyone can use it.
     * @param packetData
     * @param sw
     * @param ports
     * @param cntx
     */
    public void doMultiActionPacketOut(byte[] packetData, IOFSwitch sw, 
                                       Set<Short> ports,
                                       FloodlightContext cntx) {

        if (ports == null) return;
        if (packetData == null || packetData.length <= 0) return;

        OFPacketOut po = 
                (OFPacketOut) floodlightProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_OUT);

        List<OFAction> actions = new ArrayList<OFAction>();
        for(short p: ports) {
            actions.add(new OFActionOutput(p, (short) 0));
        }

        // set actions
        po.setActions(actions);
        // set action length
        po.setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH * 
                ports.size()));
        // set buffer-id to BUFFER_ID_NONE
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        // set in-port to OFPP_NONE
        po.setInPort(OFPort.OFPP_NONE.getValue());

        // set packet data
        po.setPacketData(packetData);

        // compute and set packet length.
        short poLength = (short)(OFPacketOut.MINIMUM_LENGTH + 
                po.getActionsLength() + 
                packetData.length);

        po.setLength(poLength);

        try {
            //counterStore.updatePktOutFMCounterStore(sw, po);
            if (log.isTraceEnabled()) {
                log.trace("write broadcast packet on switch-id={} " + 
                        "interaces={} packet-data={} packet-out={}",
                        new Object[] {sw.getId(), ports, packetData, po});
            }
            sw.write(po, cntx);

        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }


    /**
     * The BDDP packets are forwarded out of all the ports out of an
     * openflowdomain.  Get all the switches in the same openflow
     * domain as the sw (disabling tunnels).  Then get all the 
     * external switch ports and send these packets out.
     * @param sw
     * @param pi
     * @param cntx
     */
    protected void doFloodBDDP(long pinSwitch, OFPacketIn pi, 
                               FloodlightContext cntx) {

        TopologyInstance ti = getCurrentInstance(false);

        Set<Long> switches = ti.getSwitchesInOpenflowDomain(pinSwitch);

        if (switches == null)
        {
            // indicates no links are connected to the switches
            switches = new HashSet<Long>();
            switches.add(pinSwitch);
        }

        for(long sid: switches) {
            IOFSwitch sw = floodlightProvider.getSwitches().get(sid);
            if (sw == null) continue;
            Set<Short> ports = new HashSet<Short>();
            if (sw.getPorts() == null) continue;
            ports.addAll(sw.getPorts().keySet());

            // all the ports known to topology // without tunnels.
            // out of these, we need to choose only those that are 
            // broadcast port, otherwise, we should eliminate.
            Set<Short> portsKnownToTopo = ti.getPorts(sid);

            if (portsKnownToTopo != null) {
                for(short p: portsKnownToTopo) {
                    NodePortTuple npt = 
                            new NodePortTuple(sid, p);
                    if (ti.isBroadcastDomainPort(npt) == false) {
                        ports.remove(p);
                    }
                }
            }

            // remove the incoming switch port
            if (pinSwitch == sid) {
                ports.remove(pi.getInPort());
            }

            // we have all the switch ports to which we need to broadcast.
            doMultiActionPacketOut(pi.getPacketData(), sw, ports, cntx);
        }

    }

    protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, 
                                             FloodlightContext cntx) {

        // get the packet-in switch.
        Ethernet eth = 
                IFloodlightProviderService.bcStore.
                get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        if (eth.getEtherType() == Ethernet.TYPE_BDDP) {
            doFloodBDDP(sw.getId(), pi, cntx);
        } else {
            return dropFilter(sw.getId(), pi, cntx);
        }
        return Command.STOP;
    }


    public void applyUpdates() {

        appliedUpdates.clear();
        LDUpdate update = null;
        while (ldUpdates.peek() != null) {
            try {
                update = ldUpdates.take();
            } catch (Exception e) {
                log.error("Error reading link discovery update.", e);
            }
            if (log.isTraceEnabled()) {
                log.trace("Applying update: {}", update);
            }
            if (update.getOperation() == UpdateOperation.LINK_UPDATED) {
                addOrUpdateLink(update.getSrc(), update.getSrcPort(),
                                update.getDst(), update.getDstPort(),
                                update.getType());
            } else if (update.getOperation() == UpdateOperation.LINK_REMOVED){
                removeLink(update.getSrc(), update.getSrcPort(), 
                           update.getDst(), update.getDstPort());
            } else if (update.getOperation() == UpdateOperation.SWITCH_REMOVED) {
                removeSwitch(update.getSrc());
            } else if (update.getOperation() == UpdateOperation.PORT_DOWN) {
                removeSwitchPort(update.getSrc(), update.getSrcPort());
            }

            // Add to the list of applied updates.
            appliedUpdates.add(update);
        }
    }

    /**
     * This function computes a new topology.
     */
    /**
     * This function computes a new topology intance.
     * It ignores links connected to all broadcast domain ports
     * and tunnel ports.
     */
    public void createNewInstance() {
        Set<NodePortTuple> blockedPorts = new HashSet<NodePortTuple>();

        Map<NodePortTuple, Set<Link>> openflowLinks;
        openflowLinks = 
                new HashMap<NodePortTuple, Set<Link>>(switchPortLinks);

        // Remove all tunnel links.
        for(NodePortTuple npt: tunnelLinks.keySet()) {
            if (openflowLinks.get(npt) != null)
                openflowLinks.remove(npt);
        }

        // Remove all broadcast domain links.
        for(NodePortTuple npt: portBroadcastDomainLinks.keySet()) {
            if (openflowLinks.get(npt) != null)
                openflowLinks.remove(npt);
        }

        TopologyInstance nt = new TopologyInstance(switchPorts, 
                                                   blockedPorts,
                                                   openflowLinks, 
                                                   portBroadcastDomainLinks.keySet(), 
                                                   tunnelLinks.keySet());
        nt.compute();
        // We set the instances with and without tunnels to be identical.
        // If needed, we may compute them differently.
        currentInstance = nt;
        currentInstanceWithoutTunnels = nt;
    }


    public void informListeners() {
        for(int i=0; i<topologyAware.size(); ++i) {
            ITopologyListener listener = topologyAware.get(i);
            listener.topologyChanged();
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

    public boolean removeSwitchPort(long sw, short port) {

        Set<Link> linksToRemove = new HashSet<Link>();
        NodePortTuple npt = new NodePortTuple(sw, port);
        if (switchPortLinks.containsKey(npt) == false) return false;

        linksToRemove.addAll(switchPortLinks.get(npt));
        for(Link link: linksToRemove) {
            removeLink(link);
        }
        return true;
    }

    public boolean removeSwitch(long sid) {
        // Delete all the links in the switch, switch and all 
        // associated data should be deleted.
        if (switchPorts.containsKey(sid) == false) return false;

        Set<Link> linksToRemove = new HashSet<Link>();
        for(Short p: switchPorts.get(sid)) {
            NodePortTuple n1 = new NodePortTuple(sid, p);
            linksToRemove.addAll(switchPortLinks.get(n1));
        }

        if (linksToRemove.isEmpty()) return false;

        for(Link link: linksToRemove) {
            removeLink(link);
        }
        return true;
    }

    private boolean addLinkToStructure(Map<NodePortTuple, 
                                       Set<Link>> s, Link l) {
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

    private boolean removeLinkFromStructure(Map<NodePortTuple, 
                                            Set<Link>> s, Link l) {

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

    public void addOrUpdateLink(long srcId, short srcPort, long dstId, 
                                short dstPort, LinkType type) {
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
        } else if (type.equals(LinkType.DIRECT_LINK)) {
            removeLinkFromStructure(tunnelLinks, link);
            removeLinkFromStructure(portBroadcastDomainLinks, link);
        }
    }

    public void removeLink(Link link)  {
        removeLinkFromStructure(portBroadcastDomainLinks, link);
        removeLinkFromStructure(tunnelLinks, link);
        removeLinkFromStructure(switchPortLinks, link);

        NodePortTuple srcNpt = 
                new NodePortTuple(link.getSrc(), link.getSrcPort());
        NodePortTuple dstNpt = 
                new NodePortTuple(link.getDst(), link.getDstPort());

        // Remove switch ports if there are no links through those switch ports
        if (switchPortLinks.get(srcNpt) == null) {
            if (switchPorts.get(srcNpt.getNodeId()) != null)
                switchPorts.get(srcNpt.getNodeId()).remove(srcNpt.getPortId());
        }
        if (switchPortLinks.get(dstNpt) == null) {
            if (switchPorts.get(dstNpt.getNodeId()) != null)
                switchPorts.get(dstNpt.getNodeId()).remove(dstNpt.getPortId());
        }

        // Remove the node if no ports are present
        if (switchPorts.get(srcNpt.getNodeId())!=null && 
                switchPorts.get(srcNpt.getNodeId()).isEmpty()) {
            switchPorts.remove(srcNpt.getNodeId());
        }
        if (switchPorts.get(dstNpt.getNodeId())!=null && 
                switchPorts.get(dstNpt.getNodeId()).isEmpty()) {
            switchPorts.remove(dstNpt.getNodeId());
        }
    }

    public void removeLink(long srcId, short srcPort, 
                           long dstId, short dstPort) {
        Link link = new Link(srcId, srcPort, dstId, dstPort);
        removeLink(link);
    }

    public void clear() {
        switchPorts.clear();
        switchPortLinks.clear();
        portBroadcastDomainLinks.clear();
        tunnelLinks.clear();
        appliedUpdates.clear();
    }

    /**
    * Clears the current topology. Note that this does NOT
    * send out updates.
    */
    private void clearCurrentTopology() {
        this.clear();
        createNewInstance();
        lastUpdateTime = new Date();
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

    public TopologyInstance getCurrentInstance(boolean tunnelEnabled) {
        if (tunnelEnabled)
            return currentInstance;
        else return this.currentInstanceWithoutTunnels;
    }

    public TopologyInstance getCurrentInstance() {
        return this.getCurrentInstance(true);
    }
}

