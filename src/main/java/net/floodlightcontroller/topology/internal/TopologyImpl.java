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

package net.floodlightcontroller.topology.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.packet.BPDU;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.IRoutingEngine;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSource;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.topology.ITopology;
import net.floodlightcontroller.topology.ITopologyAware;
import net.floodlightcontroller.topology.LinkInfo;
import net.floodlightcontroller.topology.LinkTuple;
import net.floodlightcontroller.topology.SwitchCluster;
import net.floodlightcontroller.topology.SwitchPortTuple;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class sends out LLDP messages containing the sending switch's datapath
 * id as well as the outgoing port number.  Received LLrescDP messages that 
 * match a known switch cause a new LinkTuple to be created according to the
 * invariant rules listed below.  This new LinkTuple is also passed to routing
 * if it exists to trigger updates.
 *
 * This class also handles removing links that are associated to switch ports
 * that go down, and switches that are disconnected.
 *
 * Invariants:
 *  -portLinks and switchLinks will not contain empty Sets outside of
 *   critical sections
 *  -portLinks contains LinkTuples where one of the src or dst 
 *   SwitchPortTuple matches the map key
 *  -switchLinks contains LinkTuples where one of the src or dst 
 *   SwitchPortTuple's id matches the switch id
 *  -Each LinkTuple will be indexed into switchLinks for both 
 *   src.id and dst.id, and portLinks for each src and dst
 *  -The updates queue is only added to from within a held write lock
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class TopologyImpl implements IOFMessageListener, IOFSwitchListener, 
                                            IStorageSourceListener, ITopology {
    protected static Logger log = LoggerFactory.getLogger(TopologyImpl.class);

    // Names of table/fields for links in the storage API
    private static final String LINK_TABLE_NAME = "controller_link";
    private static final String LINK_ID = "id";
    private static final String LINK_SRC_SWITCH = "src_switch_id";
    private static final String LINK_SRC_PORT = "src_port";
    private static final String LINK_SRC_PORT_STATE = "src_port_state";
    private static final String LINK_DST_SWITCH = "dst_switch_id";
    private static final String LINK_DST_PORT = "dst_port";
    private static final String LINK_DST_PORT_STATE = "dst_port_state";
    private static final String LINK_VALID_TIME = "valid_time";

    private static final String SWITCH_TABLE_NAME = "controller_switch";
    private static final String SWITCH_CORE_SWITCH = "core_switch";

    protected IFloodlightProvider floodlightProvider;
    protected IStorageSource storageSource;
    protected IRoutingEngine routingEngine;

    /**
     * Map from link to the most recent time it was verified functioning
     */
    protected Map<LinkTuple, LinkInfo> links;
    protected Long lldpFrequency = 15L * 1000; // sending frequency
    protected Long lldpTimeout = 35L * 1000; // timeout
    protected ReentrantReadWriteLock lock;

    /**
     * Map from a id:port to the set of links containing it as an endpoint
     */
    protected Map<SwitchPortTuple, Set<LinkTuple>> portLinks;
    SingletonTask loopDetectTask;

    protected volatile boolean shuttingDown = false;

    /**
     * Map from switch id to a set of all links with it as an endpoint
     */
    protected Map<IOFSwitch, Set<LinkTuple>> switchLinks;
    protected Set<ITopologyAware> topologyAware;
    protected BlockingQueue<Update> updates;
    protected Thread updatesThread;

    protected Map<IOFSwitch, SwitchCluster> switchClusterMap;
    protected Set<SwitchCluster> clusters;

    public static enum UpdateOperation {ADD, UPDATE, REMOVE, 
                                        SWITCH_UPDATED, CLUSTER_MERGED};

    public Long getLldpFrequency() {
        return lldpFrequency;
    }

    public Long getLldpTimeout() {
        return lldpTimeout;
    }

    public Map<SwitchPortTuple, Set<LinkTuple>> getPortLinks() {
        return portLinks;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public Map<IOFSwitch, SwitchCluster> getSwitchClusterMap() {
        return switchClusterMap;
    }

    public Set<SwitchCluster> getClusters() {
        return clusters;
    }

    protected class Update {
        public IOFSwitch src;
        public short srcPort;
        public int srcPortState;
        public IOFSwitch dst;
        public short dstPort;
        public int dstPortState;
        public UpdateOperation operation;

        public Update(IOFSwitch src, short srcPort, int srcPortState,
                      IOFSwitch dst, short dstPort, int dstPortState, 
                      UpdateOperation operation) {
            this.src = src;
            this.srcPort = srcPort;
            this.srcPortState = srcPortState;
            this.dst = dst;
            this.dstPort = dstPort;
            this.dstPortState = dstPortState;
            this.operation = operation;
        }

        public Update(LinkTuple lt, int srcPortState, 
                      int dstPortState, UpdateOperation operation) {
            this(lt.getSrc().getSw(), lt.getSrc().getPort(),
                 srcPortState, lt.getDst().getSw(), lt.getDst().getPort(),
                 dstPortState, operation);
        }

        // For updtedSwitch(sw)
        public Update(IOFSwitch sw) {
            this.operation = UpdateOperation.SWITCH_UPDATED;
            this.src = sw;
        }

        // Should only be used for CLUSTER_MERGED operations
        public Update(UpdateOperation operation) {
            this.operation = operation;
        }
    }

    public TopologyImpl() {
        this.lock = new ReentrantReadWriteLock();
        this.updates = new LinkedBlockingQueue<Update>();
    }

    private void doUpdatesThread() throws InterruptedException {
        do {
            Update update = updates.take();
            if (topologyAware != null) {
                for (ITopologyAware ta : topologyAware) {
                    if (log.isDebugEnabled()) {
                        log.debug("Dispatching topology update {} {} {} {} {}",
                                  new Object[]{update.operation,
                                               update.src, update.srcPort,
                                               update.dst, update.dstPort});
                    }
                    switch (update.operation) {
                        case ADD:
                            ta.addedLink(update.src, update.srcPort, 
                                    update.srcPortState,
                                    update.dst, update.dstPort, 
                                    update.dstPortState);
                            break;
                        case UPDATE:
                            ta.updatedLink(update.src, update.srcPort, 
                                    update.srcPortState,
                                    update.dst, update.dstPort, 
                                    update.dstPortState);
                            break;
                        case REMOVE:
                            ta.removedLink(update.src, update.srcPort, 
                                    update.dst, update.dstPort);
                            break;
                        case SWITCH_UPDATED:
                            ta.updatedSwitch(update.src);
                            break;
                        case CLUSTER_MERGED:
                            ta.clusterMerged();
                            break;
                    }
                }
            }
        } while (updates.peek() != null);
        detectLoop();
    }

    public void startUp() {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        floodlightProvider.addOFSwitchListener(this);
        links = new HashMap<LinkTuple, LinkInfo>();
        portLinks = new HashMap<SwitchPortTuple, Set<LinkTuple>>();
        switchLinks = new HashMap<IOFSwitch, Set<LinkTuple>>();

        ScheduledExecutorService ses = floodlightProvider.getScheduledExecutor();

        Runnable lldpSendTimer = new Runnable() {
            @Override
            public void run() {
                try {
                    sendLLDPs();

                    if (!shuttingDown) {
                        ScheduledExecutorService ses = 
                            floodlightProvider.getScheduledExecutor();
                                    ses.schedule(this, lldpFrequency, 
                                                        TimeUnit.MILLISECONDS);
                    }
                } catch (StorageException e) {
                    log.error("Storage exception in LLDP send timer; " + 
                              "terminating process", e);
                    floodlightProvider.terminate();
                } catch (Exception e) {
                    log.error("Exception in LLDP send timer", e);
                }
            }   
        };
        ses.schedule(lldpSendTimer, 1000, TimeUnit.MILLISECONDS);

        Runnable timeoutLinksTimer = new Runnable() {
            @Override
            public void run() {
                try {
                    timeoutLinks();
                    if (!shuttingDown) {
                        ScheduledExecutorService ses = 
                            floodlightProvider.getScheduledExecutor();
                        ses.schedule(this, lldpTimeout, TimeUnit.MILLISECONDS);
                    }
                } catch (StorageException e) {
                    log.error("Storage exception in link timer; " + 
                              "terminating process", e);
                    floodlightProvider.terminate();
                } catch (Exception e) {
                    log.error("Exception in link timer", e);
                }
            }
        };
        ses.schedule(timeoutLinksTimer, 1000, TimeUnit.MILLISECONDS);

        updatesThread = new Thread(new Runnable () {
            @Override
            public void run() {
                while (true) {
                    try {
                        doUpdatesThread();
                    } catch (InterruptedException e) {
                        return;
                    } 
                }
            }}, "Topology Updates");
        updatesThread.start();

        try {
            storageSource.addListener(SWITCH_TABLE_NAME, this);
        }
        catch (StorageException ex) {
            log.error("Error in installing listener for switch table - {}", SWITCH_TABLE_NAME);
            // floodlightProvider.terminate();  // For now, log this error and continue
        }
    }

    protected void shutDown() {
        shuttingDown = true;
        floodlightProvider.removeOFSwitchListener(this);
        floodlightProvider.removeOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.removeOFMessageListener(OFType.PORT_STATUS, this);
        updatesThread.interrupt();
    }

    /**
     *  Detect loops in the openflow clusters and construct spanning trees
     *  for broadcast
     */
    protected void detectLoop() {
        // No need to detect loop if routingEngine is not available.
        if (routingEngine == null) return;
        
        if (clusters == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (SwitchCluster cluster: clusters) {
                long clusterId = cluster.getId().longValue();
                BroadcastTree clusterTree = routingEngine.getBCTree(clusterId);
                detectLoopInCluster(clusterTree, cluster);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    protected void detectLoopInCluster(BroadcastTree clusterTree, SwitchCluster cluster) {
        if (cluster == null) {
            log.debug("detectLoopInCluster, Empty cluster");
            return;
        }
        if (clusterTree == null) {
            log.debug("detectLoopInCluster, Empty broadcast tree rooted from {}", 
                      HexString.toHexString(cluster.getId()));
            return;
        }
        HashMap<Long, Long> treeNodes = clusterTree.getNodes();
        for (IOFSwitch sw : cluster.getSwitches()) {
            if (!switchLinks.containsKey(sw)) {
                log.error("detectLoopInCluster, No link for switch {} in cluster {}",
                          sw, HexString.toHexString(cluster.getId()));
                continue;
            }
            for (LinkTuple linktp : switchLinks.get(sw)) {
                SwitchPortTuple srcNode = linktp.getSrc();
                SwitchPortTuple dstNode = linktp.getDst();

                if (srcNode == null || dstNode == null) {
                    continue;
                }
                LinkInfo linkInfo = links.get(linktp);

                Long nextSrcNode = treeNodes.get(srcNode.getSw().getId());
                Long nextDstNode = treeNodes.get(dstNode.getSw().getId());
                // The link is blocked if neither (src, dst) nor (dst, src)
                // pair is in the broadcast treeNodes.
                if ((nextSrcNode != null && 
                     nextSrcNode.longValue() == dstNode.getSw().getId()) ||
                    (nextDstNode != null && 
                     nextDstNode.longValue() == srcNode.getSw().getId())) {
                    if (log.isDebugEnabled()) {
                        log.debug("detectLoopInCluster, root={}, mark " + 
                                  "link {} broadcast state to FORWARD", 
                                  HexString.toHexString(cluster.getId()),
                                  linktp);
                    }
                    linkInfo.setBroadcastState(LinkInfo.PortBroadcastState.PBS_FORWARD);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("detectLoopInCluster, root={}, mark " + 
                                  "link {} broadcast state to BLOCK", 
                                  HexString.toHexString(cluster.getId()), 
                                  linktp);
                    }
                    linkInfo.setBroadcastState(LinkInfo.PortBroadcastState.PBS_BLOCK);
                }
                writeLink(linktp, linkInfo);
            }
        }
    }

    protected void sendLLDPs(IOFSwitch sw, OFPhysicalPort port) {

        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packet out of swich: {}, port: {}",
                    sw.getStringId(), port.getPortNumber());
        }

        Ethernet ethernet = new Ethernet()
            .setSourceMACAddress(new byte[6])
            .setDestinationMACAddress("01:80:c2:00:00:00")
            .setEtherType(Ethernet.TYPE_LLDP);
        // using "nearest customer bridge" MAC address for broadest possible propagation
        // through provider and TPMR bridges (see IEEE 802.1AB-2009 and 802.1Q-2011),
        // in particular the Linux bridge which behaves mostly like a provider bridge

        LLDP lldp = new LLDP();
        ethernet.setPayload(lldp);
        byte[] chassisId = new byte[] {4, 0, 0, 0, 0, 0, 0}; // filled in later
        byte[] portId = new byte[] {2, 0, 0}; // filled in later
        lldp.setChassisId(new LLDPTLV().setType((byte) 1).setLength((short) 7).setValue(chassisId));
        lldp.setPortId(new LLDPTLV().setType((byte) 2).setLength((short) 3).setValue(portId));
        lldp.setTtl(new LLDPTLV().setType((byte) 3).setLength((short) 2).setValue(new byte[] {0, 0x78}));

        // OpenFlow OUI - 00-26-E1
        byte[] dpidTLVValue = new byte[] {0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127).setLength((short) 12).setValue(dpidTLVValue);
        lldp.getOptionalTLVList().add(dpidTLV);

        byte[] dpidArray = new byte[8];
        ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
        ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);


        Long dpid = sw.getId();
        dpidBB.putLong(dpid);

        // set the ethernet source mac to last 6 bytes of dpid
        System.arraycopy(dpidArray, 2, ethernet.getSourceMACAddress(), 0, 6);
        // set the chassis id's value to last 6 bytes of dpid
        System.arraycopy(dpidArray, 2, chassisId, 1, 6);
        // set the optional tlv to the full dpid
        System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

        if (port.getPortNumber() == OFPort.OFPP_LOCAL.getValue())
            return;

        // set the portId to the outgoing port
        portBB.putShort(port.getPortNumber());
        log.trace("Sending LLDP out of interface: {}/{}",
                                sw.toString(), port.toString());

        // serialize and wrap in a packet out
        byte[] data = ethernet.serialize();
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(OFPort.OFPP_NONE);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(port.getPortNumber(), (short) 0));
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set data
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLength() + data.length);
        po.setPacketData(data);

        // send
        try {
            sw.write(po, null);
        } catch (IOException e) {
            log.error("Failure sending LLDP out port {} on switch {}",
                    new Object[]{ port.getPortNumber(), sw }, e);
        }

    }

    protected void sendLLDPs(SwitchPortTuple swt) {
        IOFSwitch sw = swt.getSw();

        if (sw == null) return;

        OFPhysicalPort port = sw.getPort(swt.getPort());
        if (port != null)
            sendLLDPs(sw, port);
    }

    protected void sendLLDPs(IOFSwitch sw) {
        for (OFPhysicalPort port : sw.getEnabledPorts()) {
            sendLLDPs(sw, port);
        }
    }

    protected void sendLLDPs() {
        log.trace("Sending LLDP packets out of all the enabled ports");

        Map<Long, IOFSwitch> switches = floodlightProvider.getSwitches();
        for (Entry<Long, IOFSwitch> entry : switches.entrySet()) {
            IOFSwitch sw = entry.getValue();
            sendLLDPs(sw);
        }
    }

    @Override
    public String getName() {
        return "topology";
    }

    @Override
    public int getId() {
        return FlListenerID.TOPOLOGYIMPL;
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.handlePacketIn(sw, (OFPacketIn) msg, cntx);
            case PORT_STATUS:
                return this.handlePortStatus(sw, (OFPortStatus) msg);
        }
        
        log.error("Received an unexpected message {} from switch {}", msg, sw);
        return Command.CONTINUE;
    }

    private Command handleLldp(LLDP lldp, IOFSwitch sw, OFPacketIn pi) {
        // If this is a malformed LLDP, or not from us, exit
        if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3)
            return Command.CONTINUE;

        ByteBuffer portBB = ByteBuffer.wrap(lldp.getPortId().getValue());
        portBB.position(1);
        Short remotePort = portBB.getShort();
        IOFSwitch remoteSwitch = null;

        // Verify this LLDP packet matches what we're looking for
        for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
            if (lldptlv.getType() == 127 && lldptlv.getLength() == 12 &&
                    lldptlv.getValue()[0] == 0x0 && lldptlv.getValue()[1] == 0x26 &&
                    lldptlv.getValue()[2] == (byte)0xe1 && lldptlv.getValue()[3] == 0x0) {
                ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
                remoteSwitch = floodlightProvider.getSwitches().get(dpidBB.getLong(4));
                break;
            }
        }

        if (remoteSwitch == null) {
            // Ignore LLDPs not generated by Floodlight, or from a switch that has recently
            // disconnected, or from a switch connected to another Floodlight instance
            return Command.CONTINUE;
        }
        
        if (!remoteSwitch.portEnabled(remotePort)) {
            log.info("Ignoring link with disabled source port: switch {} port {}", remoteSwitch, remotePort);
            return Command.STOP;
        }
        if (!sw.portEnabled(pi.getInPort())) {
            log.info("Ignoring link with disabled dest port: switch {} port {}", sw, pi.getInPort());
            return Command.STOP;
        }

        OFPhysicalPort physicalPort = remoteSwitch.getPort(remotePort);
        int srcPortState = (physicalPort != null) ? physicalPort.getState() : 0;
        physicalPort = sw.getPort(remotePort);
        int dstPortState = (physicalPort != null) ? physicalPort.getState() : 0;

        // Store the time of update to this link, and push it out to routingEngine
        LinkTuple lt = new LinkTuple(new SwitchPortTuple(remoteSwitch, remotePort),
                new SwitchPortTuple(sw, pi.getInPort()));
        addOrUpdateLink(lt, srcPortState, dstPortState);

        // Consume this message
        return Command.STOP;
    }
    
    private Command handleBpdu(BPDU bpdu, IOFSwitch sw, OFPacketIn pi) {
        // TODO - fill this in.
        return Command.STOP;
    }
    
    protected Command handlePacketIn(IOFSwitch sw, OFPacketIn pi, 
                                     FloodlightContext cntx) {
        Ethernet eth = 
            IFloodlightProvider.bcStore.get(cntx, 
                                        IFloodlightProvider.CONTEXT_PI_PAYLOAD);

        if (eth.getPayload() instanceof LLDP)
            return handleLldp((LLDP) eth.getPayload(), sw, pi);
        
        if (eth.getPayload() instanceof BPDU)
            return handleBpdu((BPDU) eth.getPayload(), sw, pi);
        
        return Command.CONTINUE;
    }

    //TODO - we can optimize the updating clusters on a link added
    protected void addOrUpdateLink(LinkTuple lt, int srcPortState, 
                                                            int dstPortState) {
        lock.writeLock().lock();
        try {
            Integer srcPortStateObj = Integer.valueOf(srcPortState);
            Integer dstPortStateObj = Integer.valueOf(dstPortState);
            
            Long validTime = System.currentTimeMillis();
            
            LinkInfo newLinkInfo = 
                    new LinkInfo(validTime, srcPortStateObj, dstPortStateObj);
            LinkInfo oldLinkInfo = links.put(lt, newLinkInfo);
            
            UpdateOperation updateOperation = null;
            boolean linkChanged = false;
            
            if (oldLinkInfo == null) {
                // index it by switch source
                if (!switchLinks.containsKey(lt.getSrc().getSw()))
                    switchLinks.put(lt.getSrc().getSw(), 
                                                    new HashSet<LinkTuple>());
                switchLinks.get(lt.getSrc().getSw()).add(lt);

                // index it by switch dest
                if (!switchLinks.containsKey(lt.getDst().getSw()))
                    switchLinks.put(lt.getDst().getSw(), 
                                                    new HashSet<LinkTuple>());
                switchLinks.get(lt.getDst().getSw()).add(lt);

                // index both ends by switch:port
                if (!portLinks.containsKey(lt.getSrc()))
                    portLinks.put(lt.getSrc(), new HashSet<LinkTuple>());
                portLinks.get(lt.getSrc()).add(lt);

                if (!portLinks.containsKey(lt.getDst()))
                    portLinks.put(lt.getDst(), new HashSet<LinkTuple>());
                portLinks.get(lt.getDst()).add(lt);

                writeLink(lt, newLinkInfo);
                updateOperation = UpdateOperation.ADD;
                linkChanged = true;

                if (log.isTraceEnabled()) {
                    log.trace("Added link {}", lt);
                }
                log.info("Added link {}", lt);
            } else {
                // Only update the port states if they've changed
                if (srcPortState == oldLinkInfo.getSrcPortState().intValue())
                    srcPortStateObj = null;
                if (dstPortState == oldLinkInfo.getDstPortState().intValue())
                    dstPortStateObj = null;

                // Check if either of the port states changed to see if we need 
                // to send an update and recompute the clusters below.
                linkChanged = (srcPortStateObj != null) || 
                                                    (dstPortStateObj != null);
                if (!linkChanged) {
                    // Keep the same broadcast state
                    newLinkInfo.setBroadcastState(
                                            oldLinkInfo.getBroadcastState());
                }

                // Write changes to storage. This will always write the updated 
                // valid time, plus the port states if they've changed (i.e. if 
                // they weren't set to null in the previous block of code.
                writeLinkInfo(lt, validTime, srcPortStateObj, dstPortStateObj);

                if (linkChanged) {
                    updateOperation = UpdateOperation.UPDATE;
                    if (log.isTraceEnabled()) {
                        log.trace("Updated link {}", lt);
                    }
                    log.info("Updated link {}", lt);
                }
            }

            if (linkChanged) {
                updates.add(new Update(lt, srcPortState, dstPortState, updateOperation));
                updateClusters();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<IOFSwitch, Set<LinkTuple>> getSwitchLinks() {
        return this.switchLinks;
    }

    /**
     *
     * @param links
     */
    protected void deleteLinks(List<LinkTuple> links) {
        lock.writeLock().lock();
        try {
            for (LinkTuple lt : links) {
                this.switchLinks.get(lt.getSrc().getSw()).remove(lt);
                this.switchLinks.get(lt.getDst().getSw()).remove(lt);
                if (this.switchLinks.containsKey(lt.getSrc().getSw()) &&
                        this.switchLinks.get(lt.getSrc().getSw()).isEmpty())
                    this.switchLinks.remove(lt.getSrc().getSw());
                if (this.switchLinks.containsKey(lt.getDst().getSw()) &&
                        this.switchLinks.get(lt.getDst().getSw()).isEmpty())
                    this.switchLinks.remove(lt.getDst().getSw());

                this.portLinks.get(lt.getSrc()).remove(lt);
                if (this.portLinks.get(lt.getSrc()).isEmpty())
                    this.portLinks.remove(lt.getSrc());
                this.portLinks.get(lt.getDst()).remove(lt);
                if (this.portLinks.get(lt.getDst()).isEmpty())
                    this.portLinks.remove(lt.getDst());

                this.links.remove(lt);
                updates.add(new Update(lt, 0, 0, UpdateOperation.REMOVE));

                removeLink(lt);

                if (log.isTraceEnabled()) {
                    log.trace("Deleted link {}", lt);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected Command handlePortStatus(IOFSwitch sw, OFPortStatus ps) {
        if (log.isDebugEnabled()) {
            log.debug("handlePortStatus: Switch {} port #{} reason {}; " + 
                      "config is {} state is {}",
                      new Object[] {sw.getStringId(),
                                    ps.getDesc().getPortNumber(),
                                    ps.getReason(),
                                    ps.getDesc().getConfig(),
                                    ps.getDesc().getState()});
        }

        SwitchPortTuple tuple = 
                        new SwitchPortTuple(sw, ps.getDesc().getPortNumber());
        boolean link_deleted  = false;

        lock.writeLock().lock();
        try {
            boolean topologyChanged = false;

            // if ps is a delete, or a modify where the port is down or 
            // configured down
            if ((byte)OFPortReason.OFPPR_DELETE.ordinal() == ps.getReason() ||
                ((byte)OFPortReason.OFPPR_MODIFY.ordinal() == 
                                ps.getReason() && !portEnabled(ps.getDesc()))) {

                List<LinkTuple> eraseList = new ArrayList<LinkTuple>();
                if (this.portLinks.containsKey(tuple)) {
                    log.debug("handlePortStatus: Switch {} port #{} " +
                             "reason {}; removing links",
                             new Object[] {HexString.toHexString(sw.getId()),
                                            ps.getDesc().getPortNumber(),
                                            ps.getReason()});
                    eraseList.addAll(this.portLinks.get(tuple));
                    deleteLinks(eraseList);
                    topologyChanged = true;
                    link_deleted    = true;
                }
            } else if (ps.getReason() == 
                                    (byte)OFPortReason.OFPPR_MODIFY.ordinal()) {
                // If ps is a port modification and the port state has changed 
                // that affects links in the topology
                if (this.portLinks.containsKey(tuple)) {
                    for (LinkTuple link: this.portLinks.get(tuple)) {
                        LinkInfo linkInfo = links.get(link);
                        assert(linkInfo != null);
                        Integer updatedSrcPortState = null;
                        Integer updatedDstPortState = null;
                        if (link.getSrc().equals(tuple) && 
                                (linkInfo.getSrcPortState() != 
                                                    ps.getDesc().getState())) {
                            updatedSrcPortState = ps.getDesc().getState();
                            linkInfo.setSrcPortState(updatedSrcPortState);
                        }
                        if (link.getDst().equals(tuple) && 
                                (linkInfo.getDstPortState() != 
                                                    ps.getDesc().getState())) {
                            updatedDstPortState = ps.getDesc().getState();
                            linkInfo.setDstPortState(updatedDstPortState);
                        }
                        if ((updatedSrcPortState != null) || 
                                                (updatedDstPortState != null)) {
                            writeLinkInfo(link, null, updatedSrcPortState, 
                                                        updatedDstPortState);
                            topologyChanged = true;
                        }
                    }
                }
            } 

            if (topologyChanged) {
                updateClusters();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("handlePortStatus: Switch {} port #{} reason {};"+
                            " no links to update/remove",
                              new Object[] {HexString.toHexString(sw.getId()),
                                            ps.getDesc().getPortNumber(),
                                            ps.getReason()});
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (!link_deleted) {
            // Send LLDP right away when port state is changed for faster 
            // cluster-merge. If it is a link delete then there is not need
            // to send the LLDPs right away and instead we wait for the LLDPs
            // to be sent on the timer as it is normally done
            sendLLDPs(); // do it outside the write-lock
        }
        return Command.CONTINUE;
    }

    @Override
    public void addedSwitch(IOFSwitch sw) {
        sendLLDPs();
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        List<LinkTuple> eraseList = new ArrayList<LinkTuple>();
        lock.writeLock().lock();
        try {
            if (switchLinks.containsKey(sw)) {
                // add all tuples with an endpoint on this switch to erase list
                eraseList.addAll(switchLinks.get(sw));
                deleteLinks(eraseList);
                updateClusters();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected void timeoutLinks() {
        List<LinkTuple> eraseList = new ArrayList<LinkTuple>();
        Long curTime = System.currentTimeMillis();

        // reentrant required here because deleteLink also write locks
        lock.writeLock().lock();
        try {
            Iterator<Entry<LinkTuple, LinkInfo>> it = 
                                            this.links.entrySet().iterator();
            while (it.hasNext()) {
                Entry<LinkTuple, LinkInfo> entry = it.next();
                if (entry.getValue().getValidTime() + 
                                                this.lldpTimeout < curTime) {
                    eraseList.add(entry.getKey());
                }
            }
    
            deleteLinks(eraseList);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private boolean portEnabled(OFPhysicalPort port) {
        if (port == null)
            return false;
        if ((OFPortConfig.OFPPC_PORT_DOWN.getValue() & port.getConfig()) > 0)
            return false;
        if ((OFPortState.OFPPS_LINK_DOWN.getValue() & port.getState()) > 0)
            return false;
        // Port STP state doesn't work with multiple VLANs, so ignore it for now
        //if ((port.getState() & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue())
        //    return false;
        return true;
    }

    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    @Override
    public boolean isInternal(SwitchPortTuple idPort) {
        lock.readLock().lock();
        boolean result;
        try {
            result = this.portLinks.containsKey(idPort) || 
                idPort.getSw().hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH);
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    @Override
    public LinkInfo getLinkInfo(SwitchPortTuple idPort, boolean isSrcPort) {
        Set<LinkTuple> links = this.portLinks.get(idPort);
        if (links == null) {
            return null;
        }
        
        LinkTuple retLink = null;
        for (LinkTuple link : links) {
            if (log.isTraceEnabled()) {
                log.trace("getLinkInfo: check link {} against swPortTuple {}",
                          link, idPort);
            }
            if (link.getSrc().equals(idPort) && isSrcPort) {
                retLink = link;
            } else if (link.getDst().equals(idPort) && !isSrcPort) {
                retLink = link;
            }
        }

        LinkInfo linkInfo = null;
        if (retLink != null) {
            linkInfo = this.links.get(retLink);
        } else {
            log.debug("getLinkInfo: No link out of {} links is from port {}, "+
                    "isSrcPort {}", 
                    new Object[] {links.size(), idPort, isSrcPort});
        }

        return linkInfo;
    }

    @Override
    public Map<LinkTuple, LinkInfo> getLinks() {
        lock.readLock().lock();
        Map<LinkTuple, LinkInfo> result;
        try {
            result = new HashMap<LinkTuple, LinkInfo>(links);
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    private boolean linkStpBlocked(LinkTuple tuple) {
        LinkInfo linkInfo = links.get(tuple);
        if (linkInfo == null)
            return false;
        return linkInfo.linkStpBlocked();
    }

    private void traverseCluster(Set<LinkTuple> links, SwitchCluster cluster) {
        // NOTE: This function assumes that the caller has already acquired
        // a write lock on the "lock" data member.
        // FIXME: To handle large networks we probably should recode this to not
        // use recursion to avoid stack overflow.
        // FIXME: See if we can optimize not creating a new SwitchCluster object for
        // each switch.
        for (LinkTuple link: links) {
            // FIXME: Is this the right check for handling STP correctly?
            if (!linkStpBlocked(link)) {
                IOFSwitch dstSw = link.getDst().getSw();
                if (switchClusterMap.get(dstSw) == null) {
                    cluster.add(dstSw);
                    switchClusterMap.put(dstSw, cluster);
                    Set<LinkTuple> dstLinks = switchLinks.get(dstSw);
                    traverseCluster(dstLinks, cluster);
                }
            }
        }
    }

    protected void updateClusters() {
        // NOTE: This function assumes that the caller has already acquired
        // a write lock on the "lock" data member.
        if (!lock.isWriteLockedByCurrentThread()) {
            log.error("Expected lock in updateClusters()");
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Updating topology cluster info");
        }

        switchClusterMap = new HashMap<IOFSwitch, SwitchCluster>();
        clusters = new HashSet<SwitchCluster>();
        Map<Long, IOFSwitch> switches = floodlightProvider.getSwitches();
        Set<Long> switchKeys = new HashSet<Long>(switches.keySet());
        for (Map.Entry<IOFSwitch, Set<LinkTuple>> entry: switchLinks.entrySet()) {
            IOFSwitch sw = entry.getKey();
            switchKeys.remove(sw.getId());
            if (switchClusterMap.get(sw) == null) {
                SwitchCluster cluster = new SwitchCluster();
                cluster.add(sw);
                switchClusterMap.put(sw, cluster);
                clusters.add(cluster);
                traverseCluster(entry.getValue(), cluster);
            }
        }
        // switchKeys contains switches that have no links to other switches
        // Each of these switches would be in their own one-switch cluster
        for (Long key: switchKeys) {
            IOFSwitch sw = switches.get(key);
            if (sw != null)
                sw.setSwitchClusterId(sw.getId());
        }

        updates.add(new Update(UpdateOperation.CLUSTER_MERGED));
    }

    public Set<IOFSwitch> getSwitchesInCluster(IOFSwitch sw) {
        SwitchCluster cluster = switchClusterMap.get(sw);
        if (cluster == null){
            return null;
        }
        return cluster.getSwitches();
    }

    public SwitchCluster getSwitchCluster(IOFSwitch sw) {
        return switchClusterMap.get(sw);
    }

    public boolean inSameCluster(IOFSwitch switch1, IOFSwitch switch2) {
        if (switchClusterMap != null) {
            lock.readLock().lock();
            try {
                SwitchCluster cluster1 = switchClusterMap.get(switch1);
                SwitchCluster cluster2 = switchClusterMap.get(switch2);
                return (cluster1 != null) && (cluster1 == cluster2);
            }
            finally {
                lock.readLock().unlock();
            }
        }
        return false;
    }

    // STORAGE METHODS
    
    void clearAllLinks() {
        storageSource.deleteRowsAsync(LINK_TABLE_NAME, null);
    }

    private String getLinkId(LinkTuple lt) {
        String srcDpid = lt.getSrc().getSw().getStringId();
        String dstDpid = lt.getDst().getSw().getStringId();
        return srcDpid + "-" + lt.getSrc().getPort() + "-" +
            dstDpid + "-" + lt.getDst().getPort();
    }
    
    void writeLink(LinkTuple lt, LinkInfo linkInfo) {
        Map<String, Object> rowValues = new HashMap<String, Object>();
        
        String id = getLinkId(lt);
        rowValues.put(LINK_ID, id);
        String srcDpid = lt.getSrc().getSw().getStringId();
        rowValues.put(LINK_SRC_SWITCH, srcDpid);
        rowValues.put(LINK_SRC_PORT, lt.getSrc().getPort());
        if (linkInfo.isBroadcastBlocked()) {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, srcPortState Blocked", 
                          lt, linkInfo);
            }
            rowValues.put(LINK_SRC_PORT_STATE, 
                          OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
        } else {
            log.trace("writeLink, link {}, info {}, srcPortState {}", 
                    new Object[]{ lt, linkInfo, linkInfo.getSrcPortState() });
            rowValues.put(LINK_SRC_PORT_STATE, linkInfo.getSrcPortState());
        }
        String dstDpid = lt.getDst().getSw().getStringId();
        rowValues.put(LINK_DST_SWITCH, dstDpid);
        rowValues.put(LINK_DST_PORT, lt.getDst().getPort());
        if (linkInfo.isBroadcastBlocked()) {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, dstPortState Blocked",
                          lt, linkInfo);
            }
            rowValues.put(LINK_DST_PORT_STATE, 
                          OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
        } else {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, dstPortState {}", 
                          new Object[]{ lt, linkInfo,
                                        linkInfo.getDstPortState() });
            }
            rowValues.put(LINK_DST_PORT_STATE, linkInfo.getDstPortState());
        }
        rowValues.put(LINK_VALID_TIME, linkInfo.getValidTime());
        
        storageSource.updateRowAsync(LINK_TABLE_NAME, rowValues);
    }

    public Long readLinkValidTime(LinkTuple lt) {
        // FIXME: We're not currently using this right now, but if we start
        // to use this again, we probably shouldn't use it in its current
        // form, because it's doing synchronous storage calls. Depending
        // on the context this may still be OK, but if it's being called
        // on the packet in processing thread it should be reworked to
        // use asynchronous storage calls.
        Long validTime = null;
        IResultSet resultSet = null;
        try {
            String[] columns = { LINK_VALID_TIME };
            String id = getLinkId(lt);
            resultSet = storageSource.executeQuery(LINK_TABLE_NAME, columns,
                    new OperatorPredicate(LINK_ID, OperatorPredicate.Operator.EQ, id), null);
            if (resultSet.next())
                validTime = resultSet.getLong(LINK_VALID_TIME);
        }
        finally {
            if (resultSet != null)
                resultSet.close();
        }
        return validTime;
    }

    public void writeLinkInfo(LinkTuple lt, Long validTime, 
                              Integer srcPortState, Integer dstPortState) {
        Map<String, Object> rowValues = new HashMap<String, Object>();
        String id = getLinkId(lt);
        rowValues.put(LINK_ID, id);
        LinkInfo linkInfo = links.get(lt);
        if (validTime != null)
            rowValues.put(LINK_VALID_TIME, validTime);
        if (srcPortState != null) {
            if (linkInfo != null && linkInfo.isBroadcastBlocked()) {
                if (log.isTraceEnabled()) {
                    log.trace("writeLinkInfo, link {}, info {}, srcPortState Blocked",
                              lt, linkInfo);
                }
                rowValues.put(LINK_SRC_PORT_STATE, 
                              OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("writeLinkInfo, link {}, info {}, srcPortState {}", 
                              new Object[]{ lt, linkInfo, srcPortState });
                }
                rowValues.put(LINK_SRC_PORT_STATE, srcPortState);
            }
        }
        if (dstPortState != null) {
            if (linkInfo != null && linkInfo.isBroadcastBlocked()) {
                if (log.isTraceEnabled()) {
                    log.trace("writeLinkInfo, link {}, info {}, dstPortState Blocked",
                              lt, linkInfo);
                }
                rowValues.put(LINK_DST_PORT_STATE,
                              OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("writeLinkInfo, link {}, info {}, sdstPortState {}", 
                              new Object[]{ lt, linkInfo, dstPortState });
                }
                rowValues.put(LINK_DST_PORT_STATE, dstPortState);
            }
        }
        storageSource.updateRowAsync(LINK_TABLE_NAME, id, rowValues);
    }
    
    void removeLink(LinkTuple lt) {
        String id = getLinkId(lt);
        storageSource.deleteRowAsync(LINK_TABLE_NAME, id);
    }
    
    /**
     * @param topologyAware the topologyAware to set
     */
    public void setTopologyAware(Set<ITopologyAware> topologyAware) {
        // TODO make this a copy on write set or lock it somehow
        this.topologyAware = topologyAware;
    }

    /**
     * @param storageSource the storage source to use for persisting link info
     */
    public void setStorageSource(IStorageSource storageSource) {
        this.storageSource = storageSource;
        storageSource.createTable(LINK_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(LINK_TABLE_NAME, LINK_ID);
    }

    public IStorageSource getStorageSource() {
        return storageSource;
    }

    /**
     * @param storageSource the storage source to use for persisting link info
     */
    public void setRoutingEngine(IRoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
        storageSource.createTable(LINK_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(LINK_TABLE_NAME, LINK_ID);
    }
    
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {

        Map<Long, IOFSwitch> switches = floodlightProvider.getSwitches();
        ArrayList<IOFSwitch> updated_switches = new ArrayList<IOFSwitch>();
        for(Object key: rowKeys) {
            Long swId = new Long(HexString.toLong((String)key));
            if (switches.containsKey(swId)) {
                IOFSwitch sw = switches.get(swId);
                boolean curr_status = sw.hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH);
                boolean new_status =  false;
                IResultSet resultSet = null;

                try {
                    resultSet = storageSource.getRow(tableName, key);
                    for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                        // In case of multiple rows, use the status in last row?
                        Map<String, Object> row = it.next().getRow();
                        if (row.containsKey(SWITCH_CORE_SWITCH)) {
                            new_status = ((String)row.get(SWITCH_CORE_SWITCH)).equals("true");
                        }
                    }
                }
                finally {
                    if (resultSet != null)
                        resultSet.close();
                }

                if (curr_status != new_status) {
                    updated_switches.add(sw);
                }
            } else {
                log.debug("Update for switch which has no entry in switch " + 
                          "list (dpid={}), a delete action.", (String)key);
            }
        }

        for (IOFSwitch sw : updated_switches) {
            // Set SWITCH_IS_CORE_SWITCH to it's inverse value
            if (sw.hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH)) {
                sw.removeAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH);
                log.debug("SWITCH_IS_CORE_SWITCH set to False for {}", sw);
            }
            else {
                sw.setAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH, new Boolean(true));
                log.debug("SWITCH_IS_CORE_SWITCH set to True for {}", sw);
            }
            updates.add(new Update(sw));
        }

    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        // Ignore delete events, the switch delete will do the right thing on it's own
    }

}
