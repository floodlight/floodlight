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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
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
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.IRoutingEngine;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSource;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.topology.BroadcastDomain;
import net.floodlightcontroller.topology.ITopology;
import net.floodlightcontroller.topology.ITopologyAware;
import net.floodlightcontroller.topology.LinkInfo;
import net.floodlightcontroller.topology.LinkTuple;
import net.floodlightcontroller.topology.SwitchCluster;
import net.floodlightcontroller.topology.SwitchPortTuple;
import net.floodlightcontroller.util.ClusterDFS;
import net.floodlightcontroller.util.EventHistory;
import net.floodlightcontroller.util.EventHistory.EvAction;

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

    //
    private static final String LLDP_STANDARD_DST_MAC_STRING = "01:80:c2:00:00:00";
    // BigSwitch OUI is 5C:16:C7, so 5D:16:C7 is the multicast version
    private static final String LLDP_BSN_DST_MAC_STRING = "5d:16:c7:00:00:01";


    protected IFloodlightProvider floodlightProvider;
    protected IStorageSource storageSource;
    protected IRoutingEngine routingEngine;

    /**
     * Map from link to the most recent time it was verified functioning
     */
    protected Map<LinkTuple, LinkInfo> links;
    protected int lldpFrequency = 15 * 1000; // sending frequency
    protected int lldpTimeout = 35 * 1000; // timeout
    LLDPTLV controllerTLV;
    protected ReentrantReadWriteLock lock;

    /**
     * Map from a id:port to the set of links containing it as an endpoint
     */
    protected Map<SwitchPortTuple, Set<LinkTuple>> portLinks;

    /**
     * Set of link tuples over which multicast LLDPs are received
     * and unicast LLDPs are not received.
     */
    protected Map<SwitchPortTuple, Set<LinkTuple>> portBroadcastDomainLinks;

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
    protected Set<BroadcastDomain> broadcastDomains;
    protected Map<SwitchPortTuple, BroadcastDomain> portGroupMap;
    protected Map<IOFSwitch, BroadcastDomain> switchGroupMap;


    public static enum UpdateOperation {ADD, UPDATE, REMOVE,
                                        SWITCH_UPDATED, CLUSTER_MERGED};

    public int getLldpFrequency() {
        return lldpFrequency;
    }

    public int getLldpTimeout() {
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
        this.links = new HashMap<LinkTuple, LinkInfo>();
        this.portLinks = new HashMap<SwitchPortTuple, Set<LinkTuple>>();
        this.portBroadcastDomainLinks = new HashMap<SwitchPortTuple, Set<LinkTuple>>();
        this.switchLinks = new HashMap<IOFSwitch, Set<LinkTuple>>();
        this.evHistTopologySwitch =
            new EventHistory<EventHistoryTopologySwitch>("Topology: Switch");
        this.evHistTopologyLink =
            new EventHistory<EventHistoryTopologyLink>("Topology: Link");
        this.evHistTopologyCluster =
            new EventHistory<EventHistoryTopologyCluster>("Topology: Cluster");

        setControllerTLV();
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
                log.trace("Running timeoutLinksTimer");
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
                    log.error("Exception in timeoutLinksTimer", e);
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

    protected void sendLLDPs(IOFSwitch sw, OFPhysicalPort port, String dstMacAddress) {

        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packet out of swich: {}, port: {}",
                    sw.getStringId(), port.getPortNumber());
        }

        Ethernet ethernet = new Ethernet()
            .setSourceMACAddress(new byte[6])
            .setDestinationMACAddress(dstMacAddress)
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

        // Add the controller identifier to the TLV value.
        lldp.getOptionalTLVList().add(controllerTLV);

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
        if (port != null) {
            sendLLDPs(sw, port, LLDP_BSN_DST_MAC_STRING);
            sendLLDPs(sw, port, LLDP_STANDARD_DST_MAC_STRING);
        }
    }

    protected void sendLLDPs(IOFSwitch sw) {
        if (sw.getEnabledPorts() != null) {
            for (OFPhysicalPort port : sw.getEnabledPorts()) {
                sendLLDPs(sw, port, LLDP_BSN_DST_MAC_STRING);
                sendLLDPs(sw, port, LLDP_STANDARD_DST_MAC_STRING);
            }
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

    protected void setControllerTLV() {
        //Setting the controllerTLVValue based on current nano time,
        //controller's IP address, and the network interface object hash
        //the corresponding IP address.

        final int prime = 7867;
        InetAddress localIPAddress = null;
        NetworkInterface localInterface = null;

        byte[] controllerTLVValue = new byte[] {0, 0, 0, 0, 0, 0, 0, 0};  // 8 byte value.
        ByteBuffer bb = ByteBuffer.allocate(10);

        try{
            localIPAddress = java.net.InetAddress.getLocalHost();
            localInterface = NetworkInterface.getByInetAddress(localIPAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Long result = System.nanoTime();
        if (localIPAddress != null)
            result = result * prime + IPv4.toIPv4Address(localIPAddress.getHostAddress());
        if (localInterface != null)
            result = result * prime + localInterface.hashCode();
        bb.putLong(result);


        bb.rewind();
        bb.get(controllerTLVValue, 0, 8);

        this.controllerTLV = new LLDPTLV().setType((byte) 0x0c).setLength((short) 8).setValue(controllerTLVValue);
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

    private Command handleLldp(LLDP lldp, IOFSwitch sw, OFPacketIn pi, boolean isStandard, FloodlightContext cntx) {
        // If this is a malformed LLDP, or not from us, exit
        if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3)
            return Command.CONTINUE;

        ByteBuffer portBB = ByteBuffer.wrap(lldp.getPortId().getValue());
        portBB.position(1);
        Short remotePort = portBB.getShort();
        IOFSwitch remoteSwitch = null;
        boolean myLLDP = false;

        // Verify this LLDP packet matches what we're looking for
        for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
            if (lldptlv.getType() == 127 && lldptlv.getLength() == 12 &&
                    lldptlv.getValue()[0] == 0x0 && lldptlv.getValue()[1] == 0x26 &&
                    lldptlv.getValue()[2] == (byte)0xe1 && lldptlv.getValue()[3] == 0x0) {
                ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
                remoteSwitch = floodlightProvider.getSwitches().get(dpidBB.getLong(4));
            }
            if (lldptlv.getType() == 12 && lldptlv.getLength() == 8 &&
                    lldptlv.getValue()[0] == controllerTLV.getValue()[0] &&
                    lldptlv.getValue()[1] == controllerTLV.getValue()[1] &&
                    lldptlv.getValue()[2] == controllerTLV.getValue()[2] &&
                    lldptlv.getValue()[3] == controllerTLV.getValue()[3] &&
                    lldptlv.getValue()[4] == controllerTLV.getValue()[4] &&
                    lldptlv.getValue()[5] == controllerTLV.getValue()[5] &&
                    lldptlv.getValue()[6] == controllerTLV.getValue()[6] &&
                    lldptlv.getValue()[7] == controllerTLV.getValue()[7]){
                ByteBuffer.wrap(lldptlv.getValue());
                myLLDP = true;
            }
        }

        if (myLLDP == false) {
            // This is not the LLDP sent by this controller.
            // If the LLDP message has multicast bit set, then we need to broadcast
            // the packet as a regular packet.
            Ethernet eth = IFloodlightProvider.bcStore.get(cntx, IFloodlightProvider.CONTEXT_PI_PAYLOAD);
            if (eth.isMulticast()) {
                if (log.isTraceEnabled())
                    log.trace("Received a multicast LLDP packet from a different controller, allowing the packet to follow normal processing chain.");
                return Command.CONTINUE;
            }
            if (log.isTraceEnabled()) {
                log.trace("Received a unicast LLDP packet from a different controller, stop processing the packet here.");
            }
            return Command.STOP;
        }

        if (remoteSwitch == null) {
            // Ignore LLDPs not generated by Floodlight, or from a switch that has recently
            // disconnected, or from a switch connected to another Floodlight instance
            if (log.isDebugEnabled()) {
                log.debug("Received LLDP from remote switch not connected to the controller");
            }
            return Command.STOP;
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


        Integer srcPortStateObj = Integer.valueOf(srcPortState);
        Integer dstPortStateObj = Integer.valueOf(dstPortState);
        Long unicastValidTime = null;
        Long multicastValidTime = null;

        if (isStandard)
            unicastValidTime = System.currentTimeMillis();
        else
            multicastValidTime = System.currentTimeMillis();

        LinkInfo newLinkInfo =
                new LinkInfo(unicastValidTime, multicastValidTime, srcPortStateObj, dstPortStateObj);

        addOrUpdateLink(lt, newLinkInfo);

        // Consume this message
        return Command.STOP;
    }

    private Command handleBpdu(BPDU bpdu, IOFSwitch sw, OFPacketIn pi) {
        // TODO - handle STP here
        return Command.STOP;
    }

    protected Command handlePacketIn(IOFSwitch sw, OFPacketIn pi,
                                     FloodlightContext cntx) {
        Ethernet eth =
            IFloodlightProvider.bcStore.get(cntx,
                                        IFloodlightProvider.CONTEXT_PI_PAYLOAD);

        if (eth.getPayload() instanceof LLDP)  {
            String dstMacString = HexString.toHexString(eth.getDestinationMACAddress());
            if (dstMacString.equals(LLDP_STANDARD_DST_MAC_STRING)) {
                return handleLldp((LLDP) eth.getPayload(), sw, pi, true, cntx);
            } else {
                return handleLldp((LLDP) eth.getPayload(), sw, pi, false, cntx);
            }
        }

        if (eth.getPayload() instanceof BPDU)
            return handleBpdu((BPDU) eth.getPayload(), sw, pi);

        return Command.CONTINUE;
    }

    protected void addOrUpdateLink(LinkTuple lt, LinkInfo newLinkInfo) {
        lock.writeLock().lock();
        try {
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

                // Add to portNOFLinks if the unicast valid time is null
                if (newLinkInfo.getUnicastValidTime() == null)
                    addLinkToBroadcastDomain(lt);

                writeLink(lt, newLinkInfo);
                updateOperation = UpdateOperation.ADD;
                linkChanged = true;

                log.info("Added link {}", lt);
                // Add to event history
                evHistTopoLink(lt.getSrc().getSw().getId(),
                                lt.getDst().getSw().getId(),
                                lt.getSrc().getPort(),
                                lt.getDst().getPort(),
                                newLinkInfo.getSrcPortState(), newLinkInfo.getDstPortState(),
                                EvAction.LINK_ADDED, "LLDP Recvd");
            } else {
                // Since the link info is already there, we need to
                // update the right fields.
                if (newLinkInfo.getUnicastValidTime() == null) {
                    // This is due to a multicast LLDP, so copy the old unicast
                    // value.
                    if (oldLinkInfo.getUnicastValidTime() != null) {
                        newLinkInfo.setUnicastValidTime(oldLinkInfo.getUnicastValidTime());
                    }
                } else if (newLinkInfo.getMulticastValidTime() == null) {
                    // This is due to a unicast LLDP, so copy the old multicast
                    // value.
                    if (oldLinkInfo.getMulticastValidTime() != null) {
                        newLinkInfo.setMulticastValidTime(oldLinkInfo.getMulticastValidTime());
                    }
                }

                Long oldTime = oldLinkInfo.getUnicastValidTime();
                Long newTime = newLinkInfo.getUnicastValidTime();
                // the link has changed its state between openflow and non-openflow
                // if the unicastValidTimes are null or not null
                if (oldTime != null & newTime == null) {
                    // openflow -> non-openflow transition
                    // we need to add the link tuple to the portNOFLinks
                    addLinkToBroadcastDomain(lt);
                    linkChanged = true;
                } else if (oldTime == null & newTime != null) {
                    // non-openflow -> openflow transition
                    // we need to remove the link from the portNOFLinks
                    removeLinkFromBroadcastDomain(lt);
                    linkChanged = true;
                }

                // Only update the port states if they've changed
                if (newLinkInfo.getSrcPortState().intValue() != oldLinkInfo.getSrcPortState().intValue() ||
                        newLinkInfo.getDstPortState().intValue() != oldLinkInfo.getDstPortState().intValue())
                    linkChanged = true;

                if (!linkChanged) {
                    // Keep the same broadcast state
                    newLinkInfo.setBroadcastState(
                                            oldLinkInfo.getBroadcastState());
                }

                // Write changes to storage. This will always write the updated
                // valid time, plus the port states if they've changed (i.e. if
                // they weren't set to null in the previous block of code.
                writeLinkInfo(lt, newLinkInfo);

                if (linkChanged) {
                    updateOperation = UpdateOperation.UPDATE;
                    if (log.isTraceEnabled()) {
                        log.trace("Updated link {}", lt);
                    }
                    log.info("Updated link {}", lt);
                    // Add to event history
                    evHistTopoLink(lt.getSrc().getSw().getId(),
                                    lt.getDst().getSw().getId(),
                                    lt.getSrc().getPort(),
                                    lt.getDst().getPort(),
                                    newLinkInfo.getSrcPortState(), newLinkInfo.getDstPortState(),
                                    EvAction.LINK_PORT_STATE_UPDATED,
                                    "LLDP Recvd");
                }
            }

            if (linkChanged) {
                updates.add(new Update(lt, newLinkInfo.getSrcPortState(), newLinkInfo.getDstPortState(), updateOperation));
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
     * Removes links from memory and storage.
     * @param links The List of @LinkTuple to delete.
     */
    protected void deleteLinks(List<LinkTuple> links, String reason) {
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

                removeLinkFromBroadcastDomain(lt);

                this.links.remove(lt);
                updates.add(new Update(lt, 0, 0, UpdateOperation.REMOVE));

                // Update Event History
                evHistTopoLink(lt.getSrc().getSw().getId(),
                        lt.getDst().getSw().getId(),
                        lt.getSrc().getPort(),
                        lt.getDst().getPort(),
                        0, 0, // Port states
                        EvAction.LINK_DELETED, reason);
                removeLink(lt);


                if (log.isTraceEnabled()) {
                    log.trace("Deleted link {}", lt);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Handles an OFPortStatus message from a switch. We will add or
     * delete LinkTupes as well re-compute the topology if needed.
     * @param sw The IOFSwitch that sent the port status message
     * @param ps The OFPortStatus message
     * @return The Command to continue or stop after we process this message
     */
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
                    if (log.isDebugEnabled()) {
                        log.debug("handlePortStatus: Switch {} port #{} " +
                                  "reason {}; removing links",
                                  new Object[] {HexString.toHexString(sw.getId()),
                                                ps.getDesc().getPortNumber(),
                                                ps.getReason()});
                    }
                    eraseList.addAll(this.portLinks.get(tuple));
                    deleteLinks(eraseList, "Port Status Changed");
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
                            writeLinkInfo(link, linkInfo);
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

    /**
     * We send out LLDP messages when a switch is added to discover the topology
     * @param sw The IOFSwitch that connected to the controller
     */
    @Override
    public void addedSwitch(IOFSwitch sw) {
        // It's probably overkill to send LLDP from all switches, but we don't
        // know which switches might be connected to the new switch.
        // Need to optimize when supporting a large number of switches.
        sendLLDPs();
        // Update event history
        evHistTopoSwitch(sw, EvAction.SWITCH_CONNECTED, "None");
    }

    /**
     * When a switch disconnects we remove any links from our map and re-compute
     * the topology.
     * @param sw The IOFSwitch that disconnected from the controller
     */
    @Override
    public void removedSwitch(IOFSwitch sw) {
        // Update event history
        evHistTopoSwitch(sw, EvAction.SWITCH_DISCONNECTED, "None");
        List<LinkTuple> eraseList = new ArrayList<LinkTuple>();
        lock.writeLock().lock();
        try {
            if (switchLinks.containsKey(sw)) {
                // add all tuples with an endpoint on this switch to erase list
                eraseList.addAll(switchLinks.get(sw));
                deleteLinks(eraseList, "Switch Removed");
                updateClusters();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Iterates though @SwitchCluster links and then deletes ones
     * that have timed out. The timeout is set by lldpTimeout.
     * If links are deleted updateClusters() is then called.
     */
    protected void timeoutLinks() {
        List<LinkTuple> eraseList = new ArrayList<LinkTuple>();
        Long curTime = System.currentTimeMillis();
        boolean linkChanged = false;

        // reentrant required here because deleteLink also write locks
        lock.writeLock().lock();
        try {
            Iterator<Entry<LinkTuple, LinkInfo>> it =
                                            this.links.entrySet().iterator();
            while (it.hasNext()) {
                Entry<LinkTuple, LinkInfo> entry = it.next();
                LinkTuple lt = entry.getKey();
                Long uTime = entry.getValue().getUnicastValidTime();
                Long mTime = entry.getValue().getMulticastValidTime();

                // Timeout the unicast and multicast LLDP valid times
                // independently.
                if ((uTime != null) && (uTime + this.lldpTimeout < curTime)){
                    entry.getValue().setUnicastValidTime(null);
                    uTime = null;
                    if (mTime != null)
                        addLinkToBroadcastDomain(lt);
                    // Note that even if mTime becomes null later on,
                    // the link would be deleted, which would trigger updateClusters().
                    linkChanged = true;
                }
                if ((mTime != null) && (mTime + this.lldpTimeout < curTime)) {
                    entry.getValue().setMulticastValidTime(null);
                    mTime = null;
                    // if uTime is not null, then link will remain as openflow
                    // link. If uTime is null, it will be deleted.  So, we
                    // don't care about linkChanged flag here.
                    removeLinkFromBroadcastDomain(lt);
                }
                // Add to the erase list only if both the unicast and multicast
                // times are null.
                if (uTime == null && mTime == null){
                    eraseList.add(entry.getKey());
                }
            }

            // if any link was deleted or any link was changed.
            if ((eraseList.size() > 0) || linkChanged) {
                deleteLinks(eraseList, "LLDP timeout");
                updateClusters();
            }
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
     * Sets the IFloodlightProvider for this TopologyImpl.
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    /**
     * Checks to see if a SwitchPortTuple is internal. A SwitchPortTuple is
     * defined as internal if the switch is a core switch if only switches that
     * are in the same SwitchCluster are connected to it.
     * @param idPort The SwitchPortTuple to check
     * @return True if it is internal, false otherwise
     */
    @Override
    public boolean isInternal(SwitchPortTuple idPort) {
        lock.readLock().lock();
        boolean result = false;

        // A SwitchPortTuple is internal if the switch is a core switch
        // or the current switch and the switch connected on the switch
        // port tuple are in the same cluster.

        try {
            // If it is a core switch, then return true
            if (idPort.getSw().hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH))
                result = true;

            else {
                if (portBroadcastDomainLinks.get(idPort) != null) {
                    // There is at least one link here, so declare as not internal.
                    return false;
                }

                Set<LinkTuple> ltSet = this.portLinks.get(idPort);
                // The assumption is there could be at most two links for this
                // switch port: one incoming and one outgoing to the same
                // other switch.  So, verifying one of these two links is
                // sufficient.
                if (ltSet != null) {
                    for(LinkTuple lt: ltSet) {
                        Long c1 = lt.getSrc().getSw().getSwitchClusterId();
                        Long c2 = lt.getDst().getSw().getSwitchClusterId();
                        result = c1.equals(c2);
                        break;
                    }
                }
            }
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

    public Map<SwitchPortTuple, Set<LinkTuple>> getPortBroadcastDomainLinks() {
        return portBroadcastDomainLinks;
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

    protected void updateClusters() {
        if (!lock.isWriteLockedByCurrentThread()) {
            log.error("Expected lock in updateClusters()");
            return;
        }

        updateOpenFlowClusters();
        updateBroadcastDomains();
    }

    protected void addLinkToBroadcastDomain(LinkTuple lt) {
        if (!portBroadcastDomainLinks.containsKey(lt.getSrc()))
            portBroadcastDomainLinks.put(lt.getSrc(), new HashSet<LinkTuple>());
        portBroadcastDomainLinks.get(lt.getSrc()).add(lt);

        if (!portBroadcastDomainLinks.containsKey(lt.getDst()))
            portBroadcastDomainLinks.put(lt.getDst(), new HashSet<LinkTuple>());
        portBroadcastDomainLinks.get(lt.getDst()).add(lt);
    }

    protected void removeLinkFromBroadcastDomain(LinkTuple lt) {

        if (portBroadcastDomainLinks.containsKey(lt.getSrc())) {
            portBroadcastDomainLinks.get(lt.getSrc()).remove(lt);
            if (portBroadcastDomainLinks.get(lt.getSrc()).isEmpty())
                portBroadcastDomainLinks.remove(lt.getSrc());
        }

        if (portBroadcastDomainLinks.containsKey(lt.getDst())) {
            portBroadcastDomainLinks.get(lt.getDst()).remove(lt);
            if (portBroadcastDomainLinks.get(lt.getDst()).isEmpty())
                portBroadcastDomainLinks.remove(lt.getDst());
        }
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This function computes the link aggregation groups.
     * Switch ports that connect to  non-openflow domains are
     * grouped together based on whether there's a broadcast
     * leak from one to another or not.  We will currently
     * ignore directionality of the links from the switch ports.
     *
     * Assuming link tuple as undirected, the goal is to simply
     * compute connected components.
     */
    protected void updateBroadcastDomains() {
        Map<SwitchPortTuple, Set<LinkTuple>> pbdLinks =  getPortBroadcastDomainLinks();
        Set<SwitchPortTuple> visitedSwt = new HashSet<SwitchPortTuple>();
        Set<BroadcastDomain> bdSets = new HashSet<BroadcastDomain>();

        // Do a breadth first search to get all the connected components
        for(SwitchPortTuple swt: pbdLinks.keySet()) {
            if (visitedSwt.contains(swt)) continue;

            // create an array list and add the first switch port.
            Queue<SwitchPortTuple> queue = new LinkedBlockingQueue<SwitchPortTuple>();
            BroadcastDomain bd = new BroadcastDomain();
            visitedSwt.contains(swt);
            queue.add(swt);
            bd.add(swt);
            while(queue.peek() != null) {
                SwitchPortTuple currSwt = queue.remove();

                for(LinkTuple lt: pbdLinks.get(currSwt)) {
                    SwitchPortTuple otherSwt;
                    if (lt.getSrc().equals(currSwt))
                        otherSwt = lt.getDst();
                    else otherSwt = lt.getSrc();

                    if (visitedSwt.contains(otherSwt) == false) {
                        queue.add(otherSwt);
                        visitedSwt.add(otherSwt);
                        bd.add(otherSwt);
                    }
                }
            }

            // Add this broadcast domain to the set of broadcast domains.
            bdSets.add(bd);
        }

        //Replace the current broadcast domains in the topology.
        broadcastDomains = bdSets;

        if (bdSets.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("No broadcast domains exist.");
            }
        } else {
            log.warn("Broadcast domains found in the network.  There could be potential looping issues.");
            log.warn("{}", broadcastDomains.toString());
        }
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This function divides the network into clusters. Every cluster is
     * a strongly connected component. The network may contain unidirectional
     * links.  The function calls dfsTraverse for performing depth first
     * search and cluster formation.
     *
     * The computation of strongly connected components is based on
     * Tarjan's algorithm.  For more details, please see the Wikipedia
     * link below.
     *
     * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     */
    protected void updateOpenFlowClusters() {
        // NOTE: This function assumes that the caller has already acquired
        // a write lock on the "lock" data member.

        if (log.isTraceEnabled()) {
            log.trace("Computing topology cluster info");
        }

        // Initialize all the new structures.
        switchClusterMap = new HashMap<IOFSwitch, SwitchCluster>();
        clusters = new HashSet<SwitchCluster>();
        Map<Long, IOFSwitch>  switches = floodlightProvider.getSwitches();
        Map<IOFSwitch, ClusterDFS> dfsList = new HashMap<IOFSwitch, ClusterDFS>();

        if (switches.keySet() != null) {
            for (Long key: switches.keySet()) {
                IOFSwitch sw = switches.get(key);
                ClusterDFS cdfs = new ClusterDFS();
                dfsList.put(sw, cdfs);
            }

            // Get a set of switch keys in a set
            Set<IOFSwitch> currSet = new HashSet<IOFSwitch>();

            for (Long key: switches.keySet()) {
                IOFSwitch sw = switches.get(key);
                ClusterDFS cdfs = dfsList.get(sw);
                if (cdfs == null) {
                    log.error("Do DFS object for key found.");
                }else if (!cdfs.isVisited()) {
                    this.dfsTraverse(0, 1, sw, switches, dfsList, currSet, clusters);
                }
            }
        }

        updates.add(new Update(UpdateOperation.CLUSTER_MERGED));
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This algorithm computes the depth first search (DFS) travesral of the
     * switches in the network, computes the lowpoint, and creates clusters
     * (of strongly connected components).
     *
     * The computation of strongly connected components is based on
     * Tarjan's algorithm.  For more details, please see the Wikipedia
     * link below.
     *
     * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     *
     * The initialization of lowpoint and the check condition for when a
     * cluster should be formed is modified as we do not remove switches that
     * are already part of a cluster.
     *
     * @param parentIndex: DFS index of the parent node
     * @param currIndex: DFS index to be assigned to a newly visited node
     * @param currSw: Object for the current switch
     * @param switches: HashMap of switches in the network.
     * @param dfsList: HashMap of DFS data structure for each switch
     * @param currSet: Set of nodes in the current cluster in formation
     * @param clusters: Set of already formed clusters
     * @return long: DSF index to be used when a new node is visited
     */
    protected long dfsTraverse (long parentIndex, long currIndex,
            IOFSwitch currSw, Map<Long, IOFSwitch> switches,
            Map<IOFSwitch, ClusterDFS> dfsList, Set <IOFSwitch> currSet,
            Set <SwitchCluster> clusters) {

        //Get the DFS object corresponding to the current switch
        ClusterDFS currDFS = dfsList.get(currSw);
        // Get all the links corresponding to this switch
        Set<LinkTuple> links = switchLinks.get(currSw);

        //Assign the DFS object with right values.
        currDFS.setVisited(true);
        currDFS.setDfsIndex(currIndex);
        currDFS.setParentDFSIndex(parentIndex);
        currIndex++;

        // Traverse the graph through every outgoing link.
        if (links != null) {
            for(LinkTuple lt: links) {
                IOFSwitch dstSw = lt.getDst().getSw();

                // ignore incoming links.
                if (dstSw == currSw) continue;

                // ignore outgoing links if it is blocked.
                if (linkStpBlocked(lt)) continue;

                // Get the DFS object corresponding to the dstSw
                ClusterDFS dstDFS = dfsList.get(dstSw);

                if (dstDFS.getDfsIndex() < currDFS.getDfsIndex()) {
                    // could be a potential lowpoint
                    if (dstDFS.getDfsIndex() < currDFS.getLowpoint())
                        currDFS.setLowpoint(dstDFS.getDfsIndex());

                } else if (!dstDFS.isVisited()) {
                    // make a DFS visit
                    currIndex = dfsTraverse(currDFS.getDfsIndex(), currIndex, dstSw,
                            switches, dfsList, currSet, clusters);

                    // update lowpoint after the visit
                    if (dstDFS.getLowpoint() < currDFS.getLowpoint())
                        currDFS.setLowpoint(dstDFS.getLowpoint());
                }
                // else, it is a node already visited with a higher
                // dfs index, just ignore.
            }
        }

        // Add current node to currSet.
        currSet.add(currSw);

        // Cluster computation.
        // If the node's lowpoint is greater than its parent's DFS index,
        // we need to form a new cluster with all the switches in the
        // currSet.
        if (currDFS.getLowpoint() > currDFS.getParentDFSIndex()) {
            // The cluster thus far forms a strongly connected component.
            // create a new switch cluster and the switches in the current
            // set to the switch cluster.
            SwitchCluster sc = new SwitchCluster(this);
            for(IOFSwitch sw: currSet){
                sc.add(sw);
                switchClusterMap.put(sw, sc);
            }
            // delete all the nodes in the current set.
            currSet.clear();
            // add the newly formed switch clusters to the cluster set.
            clusters.add(sc);
        }

        return currIndex;
    }

    public Set<IOFSwitch> getSwitchesInCluster(IOFSwitch sw) {
        SwitchCluster cluster = switchClusterMap.get(sw);
        if (cluster == null){
            return null;
        }
        return cluster.getSwitches();
    }

    /**
     * Returns the SwitchCluster that contains the switch.
     * @param sw The IOFSwitch to get
     * @return The SwitchCluster that it is part of
     */
    public SwitchCluster getSwitchCluster(IOFSwitch sw) {
        return switchClusterMap.get(sw);
    }

    /**
     * Checks if two IOFSwitches are in the same SwitchCluster
     * @return True if they are in the same cluster, false otherwise
     */
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
    /**
     * Deletes all links from storage
     */
    void clearAllLinks() {
        storageSource.deleteRowsAsync(LINK_TABLE_NAME, null);
    }

    /**
     * Gets the storage key for a LinkTuple
     * @param lt The LinkTuple to get
     * @return The storage key as a String
     */
    private String getLinkId(LinkTuple lt) {
        String srcDpid = lt.getSrc().getSw().getStringId();
        String dstDpid = lt.getDst().getSw().getStringId();
        return srcDpid + "-" + lt.getSrc().getPort() + "-" +
            dstDpid + "-" + lt.getDst().getPort();
    }

    /**
     * Writes a LinkTuple and corresponding LinkInfo to storage
     * @param lt The LinkTuple to write
     * @param linkInfo The LinkInfo to write
     */
    void writeLink(LinkTuple lt, LinkInfo linkInfo) {
        if (linkInfo.getUnicastValidTime() != null) {
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
            rowValues.put(LINK_VALID_TIME, linkInfo.getUnicastValidTime());

            storageSource.updateRowAsync(LINK_TABLE_NAME, rowValues);
        }
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

    public void writeLinkInfo(LinkTuple lt, LinkInfo linkInfo) {
        if (linkInfo.getUnicastValidTime() != null) {
            Map<String, Object> rowValues = new HashMap<String, Object>();
            String id = getLinkId(lt);
            rowValues.put(LINK_ID, id);
            //LinkInfo linkInfo = links.get(lt);
            if (linkInfo.getUnicastValidTime() != null)
                rowValues.put(LINK_VALID_TIME, linkInfo.getUnicastValidTime());
            if (linkInfo.getSrcPortState() != null) {
                if (linkInfo != null && linkInfo.isBroadcastBlocked()) {
                    if (log.isTraceEnabled()) {
                        log.trace("writeLinkInfo, link {}, info {}, srcPortState Blocked",
                                  lt, linkInfo);
                    }
                    rowValues.put(LINK_SRC_PORT_STATE,
                                  OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace("writeLinkInfo, link {}, info {}",
                                  new Object[]{ lt, linkInfo});
                    }
                    rowValues.put(LINK_SRC_PORT_STATE, linkInfo.getSrcPortState());
                }
            }
            if (linkInfo.getDstPortState() != null) {
                if (linkInfo != null && linkInfo.isBroadcastBlocked()) {
                    if (log.isTraceEnabled()) {
                        log.trace("writeLinkInfo, link {}, info {}, dstPortState Blocked",
                                  lt, linkInfo);
                    }
                    rowValues.put(LINK_DST_PORT_STATE,
                                  OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace("writeLinkInfo, link {}, info {}",
                                  new Object[]{ lt, linkInfo});
                    }
                    rowValues.put(LINK_DST_PORT_STATE, linkInfo.getDstPortState());
                }
            }
            storageSource.updateRowAsync(LINK_TABLE_NAME, id, rowValues);
        }
    }

    /**
     * Removes a link from storage using an asynchronous call.
     * @param lt The LinkTuple to delete.
     */
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
     * Register a topology aware component
     * @param topoAwareComponent
     */
    public void addTopologyAware(ITopologyAware topoAwareComponent) {
        // TODO make this a copy on write set or lock it somehow
        this.topologyAware.add(topoAwareComponent);
    }

    /**
     * Deregister a topology aware component
     * @param topoAwareComponent
     */
    public void removeTopologyAware(ITopologyAware topoAwareComponent) {
        // TODO make this a copy on write set or lock it somehow
        this.topologyAware.remove(topoAwareComponent);
    }

    /**
     * Sets the IStorageSource to use for ITology
     * @param storageSource the storage source to use
     */
    public void setStorageSource(IStorageSource storageSource) {
        this.storageSource = storageSource;
        storageSource.createTable(LINK_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(LINK_TABLE_NAME, LINK_ID);
    }

    /**
     * Gets the storage source for this ITopology
     * @return The IStorageSource ITopology is writing to
     */
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

    // ****************************************************
    // Topology Manager's Event History members and methods
    // ****************************************************

    // Topology Manager event history
    public EventHistory<EventHistoryTopologySwitch>  evHistTopologySwitch;
    public EventHistory<EventHistoryTopologyLink>    evHistTopologyLink;
    public EventHistory<EventHistoryTopologyCluster> evHistTopologyCluster;
    public EventHistoryTopologySwitch  evTopoSwitch;
    public EventHistoryTopologyLink    evTopoLink;
    public EventHistoryTopologyCluster evTopoCluster;

    // Switch Added/Deleted
    private void evHistTopoSwitch(IOFSwitch sw, EvAction actn, String reason) {
        if (evTopoSwitch == null) {
            evTopoSwitch = new EventHistoryTopologySwitch();
        }
        evTopoSwitch.dpid     = sw.getId();
        if ((sw.getChannel() != null) &&
            (SocketAddress.class.isInstance(
                sw.getChannel().getRemoteAddress()))) {
            evTopoSwitch.ipv4Addr =
                ((InetSocketAddress)(sw.getChannel().
                        getRemoteAddress())).getAddress().getAddress();
            evTopoSwitch.l4Port   =
                (short)(((InetSocketAddress)(sw.getChannel().
                        getRemoteAddress())).getPort());
        } else {
            byte[] zeroIpa = new byte[] {(byte)0, (byte)0, (byte)0, (byte)0};
            evTopoSwitch.ipv4Addr = zeroIpa;
            evTopoSwitch.l4Port = 0;
        }
        evTopoSwitch.reason   = reason;
        evTopoSwitch = evHistTopologySwitch.put(evTopoSwitch, actn);
    }

    private void evHistTopoLink(long srcDpid, long dstDpid, short srcPort,
            short dstPort, int srcPortState, int dstPortState,
            EvAction actn, String reason) {
        if (evTopoLink == null) {
            evTopoLink = new EventHistoryTopologyLink();
        }
        evTopoLink.srcSwDpid = srcDpid;
        evTopoLink.dstSwDpid = dstDpid;
        evTopoLink.srcSwport = srcPort;
        evTopoLink.dstSwport = dstPort;
        evTopoLink.srcPortState = srcPortState;
        evTopoLink.dstPortState = dstPortState;
        evTopoLink.reason    = reason;
        evTopoLink = evHistTopologyLink.put(evTopoLink, actn);
    }

    public void evHistTopoCluster(long dpid, long clusterIdOld,
                    long clusterIdNew, EvAction action, String reason) {
        if (evTopoCluster == null) {
            evTopoCluster = new EventHistoryTopologyCluster();
        }
        evTopoCluster.dpid         = dpid;
        evTopoCluster.clusterIdOld = clusterIdOld;
        evTopoCluster.clusterIdNew = clusterIdNew;
        evTopoCluster.reason       = reason;
        evTopoCluster = evHistTopologyCluster.put(evTopoCluster, action);
    }
}
