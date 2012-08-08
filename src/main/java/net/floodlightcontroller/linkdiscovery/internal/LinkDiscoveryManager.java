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

package net.floodlightcontroller.linkdiscovery.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.SwitchType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.packet.BDDP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.NodePortTuple;
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
 */
public class LinkDiscoveryManager
implements IOFMessageListener, IOFSwitchListener, 
IStorageSourceListener, ILinkDiscoveryService,
IFloodlightModule, IInfoProvider, IHAListener {
    protected static Logger log = LoggerFactory.getLogger(LinkDiscoveryManager.class);

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
    private static final String LINK_TYPE = "link_type";

    private static final String SWITCH_CONFIG_TABLE_NAME = "controller_switchconfig";
    private static final String SWITCH_CONFIG_CORE_SWITCH = "core_switch";

    protected IFloodlightProviderService floodlightProvider;
    protected IStorageSourceService storageSource;
    protected IThreadPoolService threadPool;


    private static final byte[] LLDP_STANDARD_DST_MAC_STRING = 
            HexString.fromHexString("01:80:c2:00:00:0e");
    private static final long LINK_LOCAL_MASK  = 0xfffffffffff0L;
    private static final long LINK_LOCAL_VALUE = 0x0180c2000000L;

    // BigSwitch OUI is 5C:16:C7, so 5D:16:C7 is the multicast version
    // private static final String LLDP_BSN_DST_MAC_STRING = "5d:16:c7:00:00:01";
    private static final String LLDP_BSN_DST_MAC_STRING = "ff:ff:ff:ff:ff:ff";


    // Direction TLVs are used to indicate if the LLDPs were sent 
    // periodically or in response to a recieved LLDP
    private static final byte TLV_DIRECTION_TYPE = 0x73;
    private static final short TLV_DIRECTION_LENGTH = 1;  // 1 byte
    private static final byte TLV_DIRECTION_VALUE_FORWARD[] = {0x01};
    private static final byte TLV_DIRECTION_VALUE_REVERSE[] = {0x02};
    private static final LLDPTLV forwardTLV 
    = new LLDPTLV().
    setType((byte)TLV_DIRECTION_TYPE).
    setLength((short)TLV_DIRECTION_LENGTH).
    setValue(TLV_DIRECTION_VALUE_FORWARD);

    private static final LLDPTLV reverseTLV 
    = new LLDPTLV().
    setType((byte)TLV_DIRECTION_TYPE).
    setLength((short)TLV_DIRECTION_LENGTH).
    setValue(TLV_DIRECTION_VALUE_REVERSE);

    protected SingletonTask discoveryTask;
    protected final int DISCOVERY_TASK_INTERVAL = 1; 
    protected final int LINK_TIMEOUT = 35; // timeout as part of LLDP process.
    protected final int LLDP_TO_ALL_INTERVAL = 15 ; //15 seconds.
    protected long lldpClock = 0;

    // This value is intentionally kept higher than LLDP_TO_ALL_INTERVAL.
    // If we want to identify link failures faster, we could decrease this
    // value to a small number, say 1 or 2 sec.
    protected final int LLDP_TO_KNOWN_INTERVAL= 20; // LLDP frequency for known links

    protected LLDPTLV controllerTLV;
    protected ReentrantReadWriteLock lock;
    int lldpTimeCount = 0;

    /**
     * Map from link to the most recent time it was verified functioning
     */
    protected Map<Link, LinkInfo> links;

    /**
     * Map from switch id to a set of all links with it as an endpoint
     */
    protected Map<Long, Set<Link>> switchLinks;

    /**
     * Map from a id:port to the set of links containing it as an endpoint
     */
    protected Map<NodePortTuple, Set<Link>> portLinks;
    protected Set<NodePortTuple> suppressLLDPs;

    /**
     * Set of link tuples over which multicast LLDPs are received
     * and unicast LLDPs are not received.
     */
    protected Map<NodePortTuple, Set<Link>> portBroadcastDomainLinks;

    protected volatile boolean shuttingDown = false;

    /* topology aware components are called in the order they were added to the
     * the array */
    protected ArrayList<ILinkDiscoveryListener> linkDiscoveryAware;
    protected BlockingQueue<LDUpdate> updates;
    protected Thread updatesThread;

    protected Role currentRole;

    /** 
     * Get the LLDP sending period in seconds.
     * @return LLDP sending period in seconds.
     */
    public int getLldpFrequency() {
        return LLDP_TO_KNOWN_INTERVAL;
    }

    /**
     * Get the LLDP timeout value in seconds
     * @return LLDP timeout value in seconds
     */
    public int getLldpTimeout() {
        return LINK_TIMEOUT;
    }

    public Map<NodePortTuple, Set<Link>> getPortLinks() {
        return portLinks;
    }

    public Set<NodePortTuple> getSuppressLLDPsInfo() {
        return suppressLLDPs;
    }

    /**
     * Add a switch port to the suppressed LLDP list.
     * Remove any known links on the switch port.
     */
    public void AddToSuppressLLDPs(long sw, short port)
    {
        NodePortTuple npt = new NodePortTuple(sw, port);
        this.suppressLLDPs.add(npt);
        deleteLinksOnPort(npt, "LLDP suppressed.");
    }

    /**
     * Remove a switch port from the suppressed LLDP list.
     * Discover links on that switchport.
     */
    public void RemoveFromSuppressLLDPs(long sw, short port) 
    {
        NodePortTuple npt = new NodePortTuple(sw, port);
        this.suppressLLDPs.remove(npt);
        discover(npt);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info) {
        if (info.getUnicastValidTime() != null) {
            return ILinkDiscovery.LinkType.DIRECT_LINK;
        } else if (info.getMulticastValidTime() != null) {
            return ILinkDiscovery.LinkType.MULTIHOP_LINK;
        }
        return ILinkDiscovery.LinkType.INVALID_LINK;
    }

    private void doUpdatesThread() throws InterruptedException {
        do {
            LDUpdate update = updates.take();

            if (linkDiscoveryAware != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Dispatching link discovery update {} {} {} {} {} for {}",
                              new Object[]{update.getOperation(),
                                           HexString.toHexString(update.getSrc()), update.getSrcPort(),
                                           HexString.toHexString(update.getDst()), update.getDstPort(),
                                           linkDiscoveryAware});
                }
                try {
                    for (ILinkDiscoveryListener lda : linkDiscoveryAware) { // order maintained
                        lda.linkDiscoveryUpdate(update);
                    }
                }
                catch (Exception e) {
                    log.error("Error in link discovery updates loop", e);
                }
            }
        } while (updates.peek() != null);
    }

    private boolean isLLDPSuppressed(long sw, short portNumber) {
        return this.suppressLLDPs.contains(new NodePortTuple(sw, portNumber));
    }

    protected void discoverLinks() {

        // timeout known links.
        timeoutLinks();

        //increment LLDP clock
        lldpClock = (lldpClock + 1)% LLDP_TO_ALL_INTERVAL;

        if (lldpClock == 0) {
            discoverOnAllPorts();
            return;
        } else {
            discoverOnKnownLinkPorts();
        }
    }

    /** 
     * Send LLDP and BDDP on known 
     */
    protected void discoverOnKnownLinkPorts() {
        // Copy the port set.
        Set<NodePortTuple> nptSet = new HashSet<NodePortTuple>();
        nptSet.addAll(portLinks.keySet());

        // Send LLDP from each of them.
        for(NodePortTuple npt: nptSet) {
            discover(npt);
        }
    }

    protected void discover(NodePortTuple npt) {
        discover(npt.getNodeId(), npt.getPortId());
    }

    protected void discover(long sw, short port) {
        // Send standard lldp first
        sendDiscoveryMessage(sw, port, true, false);
        // Then send the bddp following that.
        sendDiscoveryMessage(sw, port, false, false);
    }

    /**
     * Send link discovery message out of a given switch port.
     * The discovery message may be a standard LLDP or a modified
     * LLDP, where the dst mac address is set to :ff.  
     * 
     * TODO: The modified LLDP will updated in the future and may
     * use a different eth-type.
     * @param sw
     * @param port
     * @param isStandard   indicates standard or modified LLDP
     * @param isReverse    indicates whether the LLDP was sent as a response
     */
    protected void sendDiscoveryMessage(long sw, short port,
                             boolean isStandard,
                             boolean isReverse) {

        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
        	return;
        }
        OFPhysicalPort ofpPort = iofSwitch.getPort(port);

        if (isLLDPSuppressed(sw, port)) {
            /* Dont send LLDPs out of this port as suppressLLDPs set
             * 
             */
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packet out of swich: {}, port: {}",
                      sw, port);
        }
        LLDP lldp;
        Ethernet ethernet;

        if (isStandard) {
            ethernet = new Ethernet()
            .setSourceMACAddress(ofpPort.getHardwareAddress())
            .setDestinationMACAddress(LLDP_STANDARD_DST_MAC_STRING)
            .setEtherType(Ethernet.TYPE_LLDP);
            lldp = new LLDP();
        } else {
            ethernet = new Ethernet()
            .setSourceMACAddress(ofpPort.getHardwareAddress())
            .setDestinationMACAddress(LLDP_BSN_DST_MAC_STRING)
            .setEtherType(Ethernet.TYPE_BDDP);
            lldp = new BDDP();
        }
        // using "nearest customer bridge" MAC address for broadest possible propagation
        // through provider and TPMR bridges (see IEEE 802.1AB-2009 and 802.1Q-2011),
        // in particular the Linux bridge which behaves mostly like a provider bridge


        ethernet.setPayload(lldp);
        byte[] chassisId = new byte[] {4, 0, 0, 0, 0, 0, 0}; // filled in later
        byte[] portId = new byte[] {2, 0, 0}; // filled in later
        byte[] ttlValue = new byte[] {0, 0x78};
        lldp.setChassisId(new LLDPTLV().setType((byte) 1).setLength((short) chassisId.length).setValue(chassisId));
        lldp.setPortId(new LLDPTLV().setType((byte) 2).setLength((short) portId.length).setValue(portId));
        lldp.setTtl(new LLDPTLV().setType((byte) 3).setLength((short) ttlValue.length).setValue(ttlValue));

        // OpenFlow OUI - 00-26-E1
        byte[] dpidTLVValue = new byte[] {0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127).setLength((short) dpidTLVValue.length).setValue(dpidTLVValue);
        lldp.getOptionalTLVList().add(dpidTLV);

        // Add the controller identifier to the TLV value.
        lldp.getOptionalTLVList().add(controllerTLV);
        if (isReverse) {
            lldp.getOptionalTLVList().add(reverseTLV);
        }else {
            lldp.getOptionalTLVList().add(forwardTLV);
        }

        byte[] dpidArray = new byte[8];
        ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
        ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);


        Long dpid = sw;
        dpidBB.putLong(dpid);

        // set the ethernet source mac to last 6 bytes of dpid
        System.arraycopy(dpidArray, 2, ethernet.getSourceMACAddress(), 0, 6);
        // set the chassis id's value to last 6 bytes of dpid
        System.arraycopy(dpidArray, 2, chassisId, 1, 6);
        // set the optional tlv to the full dpid
        System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

        if (port == OFPort.OFPP_LOCAL.getValue())
            return;

        // set the portId to the outgoing port
        portBB.putShort(port);
        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP out of interface: {}/{}",
                      HexString.toHexString(sw), port);
        }

        // serialize and wrap in a packet out
        byte[] data = ethernet.serialize();
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(OFPort.OFPP_NONE);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(port, (short) 0));
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set data
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLength() + data.length);
        po.setPacketData(data);

        // send
        try {
            iofSwitch.write(po, null);
            iofSwitch.flush();
        } catch (IOException e) {
            log.error("Failure sending LLDP out port {} on switch {}",
                      new Object[]{ port, HexString.toHexString(sw) }, e);
        }

    }

    /**
     * Send LLDPs to all switch-ports
     */
    protected void discoverOnAllPorts() {
        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packets out of all the enabled ports on switch {}");
        }
        Set<Long> switches = floodlightProvider.getSwitches().keySet();
        // Send standard LLDPs
        for (long sw: switches) {
            IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
            if (iofSwitch.getEnabledPorts() != null) {
                for (OFPhysicalPort p : iofSwitch.getEnabledPorts()) {
                    // sends only forward LLDPs and BDDPs
                    sendDiscoveryMessage(sw, p.getPortNumber(), true, false);
                }
            }
        }

        for (long sw: switches) {
            IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
            if (iofSwitch.getEnabledPorts() != null) {
                for (OFPhysicalPort p : iofSwitch.getEnabledPorts()) {
                    // sends only forward LLDPs and BDDPs
                    sendDiscoveryMessage(sw, p.getPortNumber(), false, false);
                }
            }
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

        long result = System.nanoTime();
        if (localIPAddress != null)
            result = result * prime + IPv4.toIPv4Address(localIPAddress.getHostAddress());
        if (localInterface != null)
            result = result * prime + localInterface.hashCode();
        // set the first 4 bits to 0.
        result = result & (0x0fffffffffffffffL);

        bb.putLong(result);

        bb.rewind();
        bb.get(controllerTLVValue, 0, 8);

        this.controllerTLV = new LLDPTLV().setType((byte) 0x0c).setLength((short) controllerTLVValue.length).setValue(controllerTLVValue);
    }



    @Override
    public String getName() {
        return "linkdiscovery";
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.handlePacketIn(sw.getId(), (OFPacketIn) msg, cntx);
            case PORT_STATUS:
                return this.handlePortStatus(sw.getId(), (OFPortStatus) msg);
        }

        log.error("Received an unexpected message {} from switch {}", msg, sw);
        return Command.CONTINUE;
    }

    private Command handleLldp(LLDP lldp, long sw, OFPacketIn pi, boolean isStandard, FloodlightContext cntx) {
        // If LLDP is suppressed on this port, ignore received packet as well
        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            log.warn("LLDP received on switch {} that is not known to controller.",
                     HexString.toHexString(sw));
        }

        if (isLLDPSuppressed(sw, pi.getInPort()))
            return Command.STOP;

        // If this is a malformed LLDP, or not from us, exit
        if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3)
            return Command.CONTINUE;

        long myId = ByteBuffer.wrap(controllerTLV.getValue()).getLong();
        long otherId = 0;
        boolean myLLDP = false;
        Boolean isReverse = null;

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
            } else if (lldptlv.getType() == 12 && lldptlv.getLength() == 8){
                otherId = ByteBuffer.wrap(lldptlv.getValue()).getLong();
                if (myId == otherId)
                    myLLDP = true;
            } else if (lldptlv.getType() == TLV_DIRECTION_TYPE &&
                    lldptlv.getLength() == TLV_DIRECTION_LENGTH) {
                if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_FORWARD[0])
                    isReverse = false;
                else if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_REVERSE[0])
                    isReverse = true;
            }
        }

        if (myLLDP == false) {
            // This is not the LLDP sent by this controller.
            // If the LLDP message has multicast bit set, then we need to broadcast
            // the packet as a regular packet.
            if (isStandard) {
                if (log.isTraceEnabled()) {
                    log.trace("Getting standard LLDP from a different controller and quelching it.");
                }
                return Command.STOP;
            }
            else if (myId < otherId)  {
                if (log.isTraceEnabled()) {
                    log.trace("Getting BDDP packets from a different controller" +
                            "and letting it go through normal processing chain.");
                }
                return Command.CONTINUE;
            }
        }


        if (remoteSwitch == null) {
            // Ignore LLDPs not generated by Floodlight, or from a switch that has recently
            // disconnected, or from a switch connected to another Floodlight instance
            if (log.isTraceEnabled()) {
                log.trace("Received LLDP from remote switch not connected to the controller");
            }
            return Command.STOP;
        }

        if (!remoteSwitch.portEnabled(remotePort)) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with disabled source port: switch {} port {}", remoteSwitch, remotePort);
            }
            return Command.STOP;
        }
        if (suppressLLDPs.contains(new NodePortTuple(remoteSwitch.getId(), 
                                                     remotePort))) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with suppressed src port: switch {} port {}",
                      remoteSwitch, remotePort);
            }
            return Command.STOP;
        }
        if (!iofSwitch.portEnabled(pi.getInPort())) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with disabled dest port: switch {} port {}", sw, pi.getInPort());
            }
            return Command.STOP;
        }

        OFPhysicalPort physicalPort = remoteSwitch.getPort(remotePort);
        int srcPortState = (physicalPort != null) ? physicalPort.getState() : 0;
        physicalPort = iofSwitch.getPort(pi.getInPort());
        int dstPortState = (physicalPort != null) ? physicalPort.getState() : 0;

        // Store the time of update to this link, and push it out to routingEngine
        Link lt = new Link(remoteSwitch.getId(), remotePort, iofSwitch.getId(), pi.getInPort());


        Long lastLldpTime = null;
        Long lastBddpTime = null;

        Long firstSeenTime = System.currentTimeMillis();

        if (isStandard)
            lastLldpTime = System.currentTimeMillis();
        else
            lastBddpTime = System.currentTimeMillis();

        LinkInfo newLinkInfo =
                new LinkInfo(firstSeenTime, lastLldpTime, lastBddpTime,
                             srcPortState, dstPortState);

        addOrUpdateLink(lt, newLinkInfo);

        // Check if reverse link exists. 
        // If it doesn't exist and if the forward link was seen 
        // first seen within a small interval, send probe on the 
        // reverse link.

        newLinkInfo = links.get(lt);
        if (newLinkInfo != null && isReverse == false) {
            Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
                                        lt.getSrc(), lt.getSrcPort());
            LinkInfo reverseInfo = links.get(reverseLink);
            if (reverseInfo == null) {
                // the reverse link does not exist.
                if (newLinkInfo.getFirstSeenTime() > System.currentTimeMillis() - LINK_TIMEOUT) {
                    this.sendDiscoveryMessage(lt.getDst(), lt.getDstPort(), isStandard, true);
                }
            }
        }

        // Consume this message
        return Command.STOP;
    }

    protected Command handlePacketIn(long sw, OFPacketIn pi,
                                     FloodlightContext cntx) {
        Ethernet eth = 
                IFloodlightProviderService.bcStore.get(cntx, 
                                                       IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        if(eth.getEtherType() == Ethernet.TYPE_BDDP) {
            return handleLldp((LLDP) eth.getPayload(), sw, pi, false, cntx);
        } else if (eth.getEtherType() == Ethernet.TYPE_LLDP)  {
            return handleLldp((LLDP) eth.getPayload(), sw, pi, true, cntx);
        } else if (eth.getEtherType() < 1500) {
            long destMac = eth.getDestinationMAC().toLong();
            if ((destMac & LINK_LOCAL_MASK) == LINK_LOCAL_VALUE){
                if (log.isTraceEnabled()) {
                    log.trace("Ignoring packet addressed to 802.1D/Q " +
                            "reserved address.");
                }
                return Command.STOP;
            }
        }
        return Command.CONTINUE;
    }

    protected UpdateOperation getUpdateOperation(int srcPortState,
                                                 int dstPortState) {
        boolean added =
                (((srcPortState &
                   OFPortState.OFPPS_STP_MASK.getValue()) !=
                   OFPortState.OFPPS_STP_BLOCK.getValue()) &&
                ((dstPortState &
                  OFPortState.OFPPS_STP_MASK.getValue()) !=
                  OFPortState.OFPPS_STP_BLOCK.getValue()));

        if (added) return UpdateOperation.LINK_UPDATED;
        return UpdateOperation.LINK_REMOVED;
    }

    protected UpdateOperation getUpdateOperation(int srcPortState) {
        boolean portUp = ((srcPortState &
                OFPortState.OFPPS_STP_MASK.getValue()) !=
                OFPortState.OFPPS_STP_BLOCK.getValue());

        if (portUp) return UpdateOperation.PORT_UP;
        else return UpdateOperation.PORT_DOWN;
    }

    @Override
    public LinkType getLinkType(LinkInfo info) {

        if (info.getUnicastValidTime() != null)
            return LinkType.DIRECT_LINK;
        else if (info.getMulticastValidTime() != null)
            return LinkType.MULTIHOP_LINK;

        return LinkType.INVALID_LINK;
    }

    protected boolean addOrUpdateLink(Link lt, LinkInfo newInfo) {

        NodePortTuple srcNpt, dstNpt;
        boolean linkChanged = false;

        lock.writeLock().lock();
        try {
            // put the new info.  if an old info exists, it will be returned.
            LinkInfo oldInfo = links.put(lt, newInfo);
            if (oldInfo != null &&
                    oldInfo.getFirstSeenTime() < newInfo.getFirstSeenTime())
                newInfo.setFirstSeenTime(oldInfo.getFirstSeenTime());

            if (log.isTraceEnabled()) {
                log.trace("addOrUpdateLink: {} {}", 
                          lt, 
                          (newInfo.getMulticastValidTime()!=null) ? "multicast" : "unicast");
            }

            UpdateOperation updateOperation = null;
            linkChanged = false;

            srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
            dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

            if (oldInfo == null) {
                // index it by switch source
                if (!switchLinks.containsKey(lt.getSrc()))
                    switchLinks.put(lt.getSrc(), new HashSet<Link>());
                switchLinks.get(lt.getSrc()).add(lt);

                // index it by switch dest
                if (!switchLinks.containsKey(lt.getDst()))
                    switchLinks.put(lt.getDst(), new HashSet<Link>());
                switchLinks.get(lt.getDst()).add(lt);

                // index both ends by switch:port
                if (!portLinks.containsKey(lt.getSrc()))
                    portLinks.put(srcNpt, new HashSet<Link>());
                portLinks.get(srcNpt).add(lt);

                if (!portLinks.containsKey(lt.getDst()))
                    portLinks.put(dstNpt, new HashSet<Link>());
                portLinks.get(dstNpt).add(lt);

                // Add to portNOFLinks if the unicast valid time is null
                if (newInfo.getUnicastValidTime() == null)
                    addLinkToBroadcastDomain(lt);

                writeLink(lt, newInfo);
                updateOperation = UpdateOperation.LINK_UPDATED;
                linkChanged = true;

                // Add to event history
                evHistTopoLink(lt.getSrc(),
                               lt.getDst(),
                               lt.getSrcPort(),
                               lt.getDstPort(),
                               newInfo.getSrcPortState(), newInfo.getDstPortState(),
                               getLinkType(lt, newInfo),
                               EvAction.LINK_ADDED, "LLDP Recvd");
            } else {
                // Since the link info is already there, we need to
                // update the right fields.
                if (newInfo.getUnicastValidTime() == null) {
                    // This is due to a multicast LLDP, so copy the old unicast
                    // value.
                    if (oldInfo.getUnicastValidTime() != null) {
                        newInfo.setUnicastValidTime(oldInfo.getUnicastValidTime());
                    }
                } else if (newInfo.getMulticastValidTime() == null) {
                    // This is due to a unicast LLDP, so copy the old multicast
                    // value.
                    if (oldInfo.getMulticastValidTime() != null) {
                        newInfo.setMulticastValidTime(oldInfo.getMulticastValidTime());
                    }
                }

                Long oldTime = oldInfo.getUnicastValidTime();
                Long newTime = newInfo.getUnicastValidTime();
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
                if (newInfo.getSrcPortState().intValue() !=
                        oldInfo.getSrcPortState().intValue() ||
                        newInfo.getDstPortState().intValue() !=
                        oldInfo.getDstPortState().intValue())
                    linkChanged = true;

                // Write changes to storage. This will always write the updated
                // valid time, plus the port states if they've changed (i.e. if
                // they weren't set to null in the previous block of code.
                writeLink(lt, newInfo);

                if (linkChanged) {
                    updateOperation = getUpdateOperation(newInfo.getSrcPortState(),
                                                         newInfo.getDstPortState());
                    if (log.isTraceEnabled()) {
                        log.trace("Updated link {}", lt);
                    }
                    // Add to event history
                    evHistTopoLink(lt.getSrc(),
                                   lt.getDst(),
                                   lt.getSrcPort(),
                                   lt.getDstPort(),
                                   newInfo.getSrcPortState(), newInfo.getDstPortState(),
                                   getLinkType(lt, newInfo),
                                   EvAction.LINK_PORT_STATE_UPDATED,
                                   "LLDP Recvd");
                }
            }

            if (linkChanged) {
                // find out if the link was added or removed here.
                updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                         lt.getDst(), lt.getDstPort(),
                                         getLinkType(lt, newInfo),
                                         updateOperation));
            }
        } finally {
            lock.writeLock().unlock();
        }

        return linkChanged;
    }

    public Map<Long, Set<Link>> getSwitchLinks() {
        return this.switchLinks;
    }

    /**
     * Removes links from memory and storage.
     * @param links The List of @LinkTuple to delete.
     */
    protected void deleteLinks(List<Link> links, String reason) {
        NodePortTuple srcNpt, dstNpt;

        lock.writeLock().lock();
        try {
            for (Link lt : links) {
                srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
                dstNpt  =new NodePortTuple(lt.getDst(), lt.getDstPort());

                switchLinks.get(lt.getSrc()).remove(lt);
                switchLinks.get(lt.getDst()).remove(lt);
                if (switchLinks.containsKey(lt.getSrc()) &&
                        switchLinks.get(lt.getSrc()).isEmpty())
                    this.switchLinks.remove(lt.getSrc());
                if (this.switchLinks.containsKey(lt.getDst()) &&
                        this.switchLinks.get(lt.getDst()).isEmpty())
                    this.switchLinks.remove(lt.getDst());

                if (this.portLinks.get(srcNpt) != null) {
                    this.portLinks.get(srcNpt).remove(lt);
                    if (this.portLinks.get(srcNpt).isEmpty())
                        this.portLinks.remove(srcNpt);
                }
                if (this.portLinks.get(dstNpt) != null) {
                    this.portLinks.get(dstNpt).remove(lt);
                    if (this.portLinks.get(dstNpt).isEmpty())
                        this.portLinks.remove(dstNpt);
                }

                this.links.remove(lt);
                updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                         lt.getDst(), lt.getDstPort(),
                                         null, UpdateOperation.LINK_REMOVED));

                // Update Event History
                evHistTopoLink(lt.getSrc(),
                               lt.getDst(),
                               lt.getSrcPort(),
                               lt.getDstPort(),
                               0, 0, // Port states
                               ILinkDiscovery.LinkType.INVALID_LINK,
                               EvAction.LINK_DELETED, reason);

                // remove link from storage.
                removeLinkFromStorage(lt);


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
    protected Command handlePortStatus(long sw, OFPortStatus ps) {

        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) return Command.CONTINUE;

        if (log.isTraceEnabled()) {
            log.trace("handlePortStatus: Switch {} port #{} reason {}; " +
                    "config is {} state is {}",
                    new Object[] {iofSwitch.getStringId(),
                                  ps.getDesc().getPortNumber(),
                                  ps.getReason(),
                                  ps.getDesc().getConfig(),
                                  ps.getDesc().getState()});
        }

        short port = ps.getDesc().getPortNumber();
        NodePortTuple npt = new NodePortTuple(sw, port);
        boolean linkDeleted  = false;
        boolean linkInfoChanged = false;

        lock.writeLock().lock();
        try {
            // if ps is a delete, or a modify where the port is down or
            // configured down
            if ((byte)OFPortReason.OFPPR_DELETE.ordinal() == ps.getReason() ||
                    ((byte)OFPortReason.OFPPR_MODIFY.ordinal() ==
                    ps.getReason() && !portEnabled(ps.getDesc()))) {
                deleteLinksOnPort(npt, "Port Status Changed");
                LDUpdate update = new LDUpdate(sw, port, UpdateOperation.PORT_DOWN);
                updates.add(update);
                linkDeleted = true;
                } 
            else if (ps.getReason() ==
                    (byte)OFPortReason.OFPPR_MODIFY.ordinal()) {
                // If ps is a port modification and the port state has changed
                // that affects links in the topology

                if (this.portLinks.containsKey(npt)) {
                    for (Link lt: this.portLinks.get(npt)) {
                        LinkInfo linkInfo = links.get(lt);
                        assert(linkInfo != null);
                        Integer updatedSrcPortState = null;
                        Integer updatedDstPortState = null;
                        if (lt.getSrc() == npt.getNodeId() && 
                                lt.getSrcPort() == npt.getPortId() &&
                                (linkInfo.getSrcPortState() !=
                                ps.getDesc().getState())) {
                            updatedSrcPortState = ps.getDesc().getState();
                            linkInfo.setSrcPortState(updatedSrcPortState);
                        }
                        if (lt.getDst() == npt.getNodeId() &&
                                lt.getDstPort() == npt.getPortId() &&
                                (linkInfo.getDstPortState() !=
                                ps.getDesc().getState())) {
                            updatedDstPortState = ps.getDesc().getState();
                            linkInfo.setDstPortState(updatedDstPortState);
                        }
                        if ((updatedSrcPortState != null) ||
                                (updatedDstPortState != null)) {
                            // The link is already known to link discovery
                            // manager and the status has changed, therefore
                            // send an LDUpdate.
                            UpdateOperation operation =
                                    getUpdateOperation(linkInfo.getSrcPortState(),
                                                       linkInfo.getDstPortState());
                            updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                                     lt.getDst(), lt.getDstPort(),
                                                     getLinkType(lt, linkInfo),
                                                     operation));
                            writeLink(lt, linkInfo);
                            linkInfoChanged = true;
                        }
                    }
                }

                UpdateOperation operation =
                        getUpdateOperation(ps.getDesc().getState());
                updates.add(new LDUpdate(sw, port, operation));
            }

            if (!linkDeleted && !linkInfoChanged){
                if (log.isTraceEnabled()) {
                    log.trace("handlePortStatus: Switch {} port #{} reason {};"+
                            " no links to update/remove",
                            new Object[] {HexString.toHexString(sw),
                                          ps.getDesc().getPortNumber(),
                                          ps.getReason()});
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (!linkDeleted) {
            // Send LLDP right away when port state is changed for faster
            // cluster-merge. If it is a link delete then there is not need
            // to send the LLDPs right away and instead we wait for the LLDPs
            // to be sent on the timer as it is normally done
            // do it outside the write-lock
            // sendLLDPTask.reschedule(1000, TimeUnit.MILLISECONDS);
            discover(npt);
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
        NodePortTuple npt;

        if (sw.getEnabledPorts() != null) {
            for (OFPhysicalPort p : sw.getEnabledPorts()) {
                npt = new NodePortTuple(sw.getId(), p.getPortNumber());
                discover(npt);
            }
        }
        // Update event history
        evHistTopoSwitch(sw, EvAction.SWITCH_CONNECTED, "None");
    }

    /**
     * When a switch disconnects we remove any links from our map and notify.
     * @param The id of the switch
     */
    @Override
    public void removedSwitch(IOFSwitch iofSwitch) {
        // Update event history
        long sw = iofSwitch.getId();
        evHistTopoSwitch(iofSwitch, EvAction.SWITCH_DISCONNECTED, "None");
        List<Link> eraseList = new ArrayList<Link>();
        lock.writeLock().lock();
        try {
            if (switchLinks.containsKey(sw)) {
                if (log.isTraceEnabled()) {
                    log.trace("Handle switchRemoved. Switch {}; removing links {}",
                              HexString.toHexString(sw), switchLinks.get(sw));
                }
                // add all tuples with an endpoint on this switch to erase list
                eraseList.addAll(switchLinks.get(sw));
                deleteLinks(eraseList, "Switch Removed");

                // Send a switch removed update
                LDUpdate update = new LDUpdate(sw, null, UpdateOperation.SWITCH_REMOVED);
                updates.add(update);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 
     * Delete links incident on a given switch port.
     * @param npt
     * @param reason
     */
    protected void deleteLinksOnPort(NodePortTuple npt, String reason) {
        List<Link> eraseList = new ArrayList<Link>();
        if (this.portLinks.containsKey(npt)) {
            if (log.isTraceEnabled()) {
                log.trace("handlePortStatus: Switch {} port #{} " +
                        "removing links {}",
                        new Object[] {HexString.toHexString(npt.getNodeId()),
                                      npt.getPortId(),
                                      this.portLinks.get(npt)});
            }
            eraseList.addAll(this.portLinks.get(npt));
            deleteLinks(eraseList, reason);
        }
    }

    /** 
     * Iterates through the list of links and deletes if the
     * last discovery message reception time exceeds timeout values.
     */
    protected void timeoutLinks() {
        List<Link> eraseList = new ArrayList<Link>();
        Long curTime = System.currentTimeMillis();
        boolean linkChanged = false;

        // reentrant required here because deleteLink also write locks
        lock.writeLock().lock();
        try {
            Iterator<Entry<Link, LinkInfo>> it =
                    this.links.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Link, LinkInfo> entry = it.next();
                Link lt = entry.getKey();
                LinkInfo info = entry.getValue();

                // Timeout the unicast and multicast LLDP valid times
                // independently.
                if ((info.getUnicastValidTime() != null) && 
                        (info.getUnicastValidTime() + (this.LINK_TIMEOUT * 1000) < curTime)){
                    info.setUnicastValidTime(null);

                    if (info.getMulticastValidTime() != null)
                        addLinkToBroadcastDomain(lt);
                    // Note that even if mTime becomes null later on,
                    // the link would be deleted, which would trigger updateClusters().
                    linkChanged = true;
                }
                if ((info.getMulticastValidTime()!= null) && 
                        (info.getMulticastValidTime()+ (this.LINK_TIMEOUT * 1000) < curTime)) {
                    info.setMulticastValidTime(null);
                    // if uTime is not null, then link will remain as openflow
                    // link. If uTime is null, it will be deleted.  So, we
                    // don't care about linkChanged flag here.
                    removeLinkFromBroadcastDomain(lt);
                    linkChanged = true;
                }
                // Add to the erase list only if the unicast
                // time is null.
                if (info.getUnicastValidTime() == null && 
                        info.getMulticastValidTime() == null){
                    eraseList.add(entry.getKey());
                } else if (linkChanged) {
                    UpdateOperation operation;
                    operation = getUpdateOperation(info.getSrcPortState(),
                                                   info.getDstPortState());
                    updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                             lt.getDst(), lt.getDstPort(),
                                             getLinkType(lt, info),
                                             operation));
                }
            }

            // if any link was deleted or any link was changed.
            if ((eraseList.size() > 0) || linkChanged) {
                deleteLinks(eraseList, "LLDP timeout");
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
        // if ((port.getState() & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue())
        //    return false;
        return true;
    }

    public Map<NodePortTuple, Set<Link>> getPortBroadcastDomainLinks() {
        return portBroadcastDomainLinks;
    }

    @Override
    public Map<Link, LinkInfo> getLinks() {
        lock.readLock().lock();
        Map<Link, LinkInfo> result;
        try {
            result = new HashMap<Link, LinkInfo>(links);
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    protected void addLinkToBroadcastDomain(Link lt) {

        NodePortTuple srcNpt, dstNpt;
        srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
        dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

        if (!portBroadcastDomainLinks.containsKey(lt.getSrc()))
            portBroadcastDomainLinks.put(srcNpt, new HashSet<Link>());
        portBroadcastDomainLinks.get(srcNpt).add(lt);

        if (!portBroadcastDomainLinks.containsKey(lt.getDst()))
            portBroadcastDomainLinks.put(dstNpt, new HashSet<Link>());
        portBroadcastDomainLinks.get(dstNpt).add(lt);
    }

    protected void removeLinkFromBroadcastDomain(Link lt) {

        NodePortTuple srcNpt, dstNpt;
        srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
        dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

        if (portBroadcastDomainLinks.containsKey(srcNpt)) {
            portBroadcastDomainLinks.get(srcNpt).remove(lt);
            if (portBroadcastDomainLinks.get(srcNpt).isEmpty())
                portBroadcastDomainLinks.remove(srcNpt);
        }

        if (portBroadcastDomainLinks.containsKey(dstNpt)) {
            portBroadcastDomainLinks.get(dstNpt).remove(lt);
            if (portBroadcastDomainLinks.get(dstNpt).isEmpty())
                portBroadcastDomainLinks.remove(dstNpt);
        }
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
    private String getLinkId(Link lt) {
        return HexString.toHexString(lt.getSrc()) +
                "-" + lt.getSrcPort() + "-" +
                HexString.toHexString(lt.getDst())+
                "-" + lt.getDstPort();
    }

    private Link getLinkTupleFromId(String id)
    {
        String[] tokens = id.split("-");
        Link lt = new Link(HexString.toLong(tokens[0]), Short.parseShort(tokens[1]), HexString.toLong(tokens[2]), Short.parseShort(tokens[3]));
        return lt;
    }

    /**
     * Writes a LinkTuple and corresponding LinkInfo to storage
     * @param lt The LinkTuple to write
     * @param linkInfo The LinkInfo to write
     */
    void writeLink(Link lt, LinkInfo linkInfo) {
        if (currentRole == Role.SLAVE) {
            return;
        }
        LinkType type = getLinkType(lt, linkInfo);

        // Write only direct links.  Do not write links to external
        // L2 network.
        // if (type != LinkType.DIRECT_LINK && type != LinkType.TUNNEL) {
        //    return;
        // }

        Map<String, Object> rowValues = new HashMap<String, Object>();
        String id = getLinkId(lt);
        rowValues.put(LINK_ID, id);
        rowValues.put(LINK_VALID_TIME, linkInfo.getUnicastValidTime());
        String srcDpid = HexString.toHexString(lt.getSrc());
        rowValues.put(LINK_SRC_SWITCH, srcDpid);
        rowValues.put(LINK_SRC_PORT, lt.getSrcPort());

        if (type == LinkType.DIRECT_LINK)
            rowValues.put(LINK_TYPE, "internal");
        else if (type == LinkType.MULTIHOP_LINK) 
            rowValues.put(LINK_TYPE, "external");
        else if (type == LinkType.TUNNEL) 
            rowValues.put(LINK_TYPE, "tunnel"); 
        else rowValues.put(LINK_TYPE, "invalid");

        if (linkInfo.linkStpBlocked()) {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, srcPortState Blocked",
                          lt, linkInfo);
            }
            rowValues.put(LINK_SRC_PORT_STATE,
                          OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
        } else {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, srcPortState {}",
                          new Object[]{ lt, linkInfo, linkInfo.getSrcPortState() });
            }
            rowValues.put(LINK_SRC_PORT_STATE, linkInfo.getSrcPortState());
        }
        String dstDpid = HexString.toHexString(lt.getDst());
        rowValues.put(LINK_DST_SWITCH, dstDpid);
        rowValues.put(LINK_DST_PORT, lt.getDstPort());
        if (linkInfo.linkStpBlocked()) {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, dstPortState Blocked",
                          lt, linkInfo);
            }
            rowValues.put(LINK_DST_PORT_STATE,
                          OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
        } else {
            if (log.isTraceEnabled()) {
                log.trace("writeLink, link {}, info {}, dstPortState {}",
                          new Object[]{ lt, linkInfo, linkInfo.getDstPortState() });
            }
            rowValues.put(LINK_DST_PORT_STATE, linkInfo.getDstPortState());
        }
        storageSource.updateRowAsync(LINK_TABLE_NAME, rowValues);
    }

    public Long readLinkValidTime(Link lt) {
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

    /**
     * Removes a link from storage using an asynchronous call.
     * @param lt The LinkTuple to delete.
     */
    void removeLinkFromStorage(Link lt) {
        if (currentRole == Role.SLAVE) {
            return;
        }
        String id = getLinkId(lt);
        storageSource.deleteRowAsync(LINK_TABLE_NAME, id);
    }

    @Override
    public void addListener(ILinkDiscoveryListener listener) {
        linkDiscoveryAware.add(listener);
    }

    /**
     * Register a link discovery aware component
     * @param linkDiscoveryAwareComponent
     */
    public void addLinkDiscoveryAware(ILinkDiscoveryListener linkDiscoveryAwareComponent) {
        // TODO make this a copy on write set or lock it somehow
        this.linkDiscoveryAware.add(linkDiscoveryAwareComponent);
    }

    /**
     * Deregister a link discovery aware component
     * @param linkDiscoveryAwareComponent
     */
    public void removeLinkDiscoveryAware(ILinkDiscoveryListener linkDiscoveryAwareComponent) {
        // TODO make this a copy on write set or lock it somehow
        this.linkDiscoveryAware.remove(linkDiscoveryAwareComponent);
    }

    /**
     * Sets the IStorageSource to use for ITology
     * @param storageSource the storage source to use
     */
    public void setStorageSource(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }

    /**
     * Gets the storage source for this ITopology
     * @return The IStorageSource ITopology is writing to
     */
    public IStorageSourceService getStorageSource() {
        return storageSource;
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
        log.info("XXX: rowsModified = {}", tableName);
        if (tableName.equals(LINK_TABLE_NAME)) {
            if (currentRole == Role.SLAVE) {
                log.info("XXX: got link update in slave mode. Update stuff !!!");
                for(Object key: rowKeys) {
                    IResultSet resultSet = null;
                    try {
                        resultSet = storageSource.getRow(tableName, key);
                        for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                            Map<String, Object> rowValues = it.next().getRow();

                            Long lldpValidTime = null, bddpValidTime = null, firstSeenTime = null;
                            if (rowValues.get(LINK_VALID_TIME) != null) {
                                lldpValidTime = Long.decode((String)rowValues.get(LINK_VALID_TIME));
                                firstSeenTime = lldpValidTime;
                            }
                            if (lldpValidTime == null) {
                                log.info("XXX: read null unicast time");
                                // FIXME: for now just set firstSeenTime to current time.
                                firstSeenTime = System.currentTimeMillis();
                            }
                            String srcDpid = (String)rowValues.get(LINK_SRC_SWITCH);
                            Short srcPort = Short.decode((String)rowValues.get(LINK_SRC_PORT));
                            LinkType type;
                            String typeStr = (String)rowValues.get(LINK_TYPE);
                            if (typeStr.equals("internal")) {
                                type = LinkType.DIRECT_LINK;
                            }
                            else if (typeStr.equals("external")) {
                                type = LinkType.MULTIHOP_LINK;
                            }
                            else if (typeStr.equals("tunnel")) {
                                type = LinkType.TUNNEL;
                            }
                            Integer srcPortState = Integer.valueOf((String)rowValues.get(LINK_SRC_PORT_STATE));
                            String destDpid = (String)rowValues.get(LINK_DST_SWITCH);
                            Short destPort = Short.decode((String)rowValues.get(LINK_DST_PORT));
                            Integer destPortState = Integer.valueOf((String)rowValues.get(LINK_DST_PORT_STATE));
                            log.info("read from cassandra, srcDpid = {}", srcDpid);
                            log.info("read from cassandra, srcPort = {}", srcPort);
                            log.info("read from cassandra, destDpid = {}", destDpid);
                            log.info("read from cassandra, destPort = {}", destPort);
                            Link lt = new Link(HexString.toLong(srcDpid), srcPort, HexString.toLong(destDpid), destPort);
                            LinkInfo newLinkInfo =
                                new LinkInfo(firstSeenTime, lldpValidTime, bddpValidTime, srcPortState, destPortState);
                            addOrUpdateLink(lt, newLinkInfo);
                        }
                    }
                    finally {
                        if (resultSet != null) {
                            resultSet.close();
                        }
                    }
                }
            }
            else {
                log.info("XXX: got link update in active mode. Ignore !!!");
            }
            return;
        }
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
                        if (row.containsKey(SWITCH_CONFIG_CORE_SWITCH)) {
                            new_status = ((String)row.get(SWITCH_CONFIG_CORE_SWITCH)).equals("true");
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
                if (log.isTraceEnabled()) {
                    log.trace("Update for switch which has no entry in switch " +
                            "list (dpid={}), a delete action.", (String)key);
                }
            }
        }

        for (IOFSwitch sw : updated_switches) {
            // Set SWITCH_IS_CORE_SWITCH to it's inverse value
            if (sw.hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH)) {
                sw.removeAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH);
                if (log.isTraceEnabled()) {
                    log.trace("SWITCH_IS_CORE_SWITCH set to False for {}", sw);
                }
                updates.add(new LDUpdate(sw.getId(), SwitchType.BASIC_SWITCH,
                                         UpdateOperation.SWITCH_UPDATED));
            }
            else {
                sw.setAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH, new Boolean(true));
                if (log.isTraceEnabled()) {
                    log.trace("SWITCH_IS_CORE_SWITCH set to True for {}", sw);
                }
                updates.add(new LDUpdate(sw.getId(), SwitchType.CORE_SWITCH,
                                         UpdateOperation.SWITCH_UPDATED));
            }
        }
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(LINK_TABLE_NAME) == false) {
            return;
        }
        if (currentRole != Role.SLAVE) {
            return;
        }
        for(Object obj : rowKeys) {
            if (!(obj instanceof String)) {
                log.error("tried to delete non-string key {}; ignoring", obj);
                continue;
            }
            Link lt = getLinkTupleFromId((String) obj);
            log.info("XXX: Delete link from MASTER {}", (String) obj);
            deleteLinks(Arrays.asList(lt), "Deleted on MASTER");
        }
    }

    // IFloodlightModule classes

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILinkDiscoveryService.class);
        //l.add(ITopologyService.class);
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
        m.put(ILinkDiscoveryService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStorageSourceService.class);
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        storageSource = context.getServiceImpl(IStorageSourceService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);

        // We create this here because there is no ordering guarantee
        this.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
        this.lock = new ReentrantReadWriteLock();
        this.updates = new LinkedBlockingQueue<LDUpdate>();
        this.links = new HashMap<Link, LinkInfo>();
        this.portLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.suppressLLDPs =
                Collections.synchronizedSet(new HashSet<NodePortTuple>());
        this.portBroadcastDomainLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.switchLinks = new HashMap<Long, Set<Link>>();
        this.evHistTopologySwitch =
                new EventHistory<EventHistoryTopologySwitch>("Topology: Switch");
        this.evHistTopologyLink =
                new EventHistory<EventHistoryTopologyLink>("Topology: Link");
        this.evHistTopologyCluster =
                new EventHistory<EventHistoryTopologyCluster>("Topology: Cluster");
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // Create our storage tables
        if (storageSource == null) {
            log.warn("No storage source found.");
            return;
        }

        storageSource.createTable(LINK_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(LINK_TABLE_NAME, LINK_ID);
        storageSource.deleteMatchingRows(LINK_TABLE_NAME, null);
        // Register for storage updates for the switch table
        try {
            storageSource.addListener(SWITCH_CONFIG_TABLE_NAME, this);
            storageSource.addListener(LINK_TABLE_NAME, this);
        } catch (StorageException ex) {
            log.error("Error in installing listener for switch/link table - {}", SWITCH_CONFIG_TABLE_NAME);
        }

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();

        // To be started by the first switch connection
        discoveryTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                try {
                    discoverLinks();
                } catch (StorageException e) {
                    log.error("Storage exception in LLDP send timer; " + 
                            "terminating process", e);
                    floodlightProvider.terminate();
                } catch (Exception e) {
                    log.error("Exception in LLDP send timer.", e);
                } finally {
                    if (!shuttingDown) {
                        discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL,
                                                TimeUnit.SECONDS);
                    }
                }
            }
        });

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

        // Register for the OpenFlow messages we want to receive
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        // Register for switch updates
        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addHAListener(this);
        floodlightProvider.addInfoProvider("summary", this);

        setControllerTLV();
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
                    IPv4.toIPv4Address(((InetSocketAddress)(sw.getChannel().
                            getRemoteAddress())).getAddress().getAddress());
            evTopoSwitch.l4Port   =
                    ((InetSocketAddress)(sw.getChannel().
                            getRemoteAddress())).getPort();
        } else {
            evTopoSwitch.ipv4Addr = 0;
            evTopoSwitch.l4Port = 0;
        }
        evTopoSwitch.reason   = reason;
        evTopoSwitch = evHistTopologySwitch.put(evTopoSwitch, actn);
    }

    private void evHistTopoLink(long srcDpid, long dstDpid, short srcPort,
                                short dstPort, int srcPortState, int dstPortState,
                                ILinkDiscovery.LinkType linkType,
                                EvAction actn, String reason) {
        if (evTopoLink == null) {
            evTopoLink = new EventHistoryTopologyLink();
        }
        evTopoLink.srcSwDpid = srcDpid;
        evTopoLink.dstSwDpid = dstDpid;
        evTopoLink.srcSwport = srcPort & 0xffff;
        evTopoLink.dstSwport = dstPort & 0xffff;
        evTopoLink.srcPortState = srcPortState;
        evTopoLink.dstPortState = dstPortState;
        evTopoLink.reason    = reason;
        switch (linkType) {
            case DIRECT_LINK:
                evTopoLink.linkType = "DIRECT_LINK";
                break;
            case MULTIHOP_LINK:
                evTopoLink.linkType = "MULTIHOP_LINK";
                break;
            case TUNNEL:
                evTopoLink.linkType = "TUNNEL";
                break;
            case INVALID_LINK:
            default:
                evTopoLink.linkType = "Unknown";
                break;
        }
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

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();

        int num_links = 0;
        for (Set<Link> links : switchLinks.values())
            num_links += links.size();
        info.put("# inter-switch links", num_links / 2);

        return info;
    }

    // IHARoleListener

    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    clearAllLinks();
                    if (log.isTraceEnabled()) {
                        log.trace("Sending LLDPs " +
                                "to HA change from SLAVE->MASTER");
                    }
                    clearAllLinks();
                    discoverLinks();
                }
                break;
            case SLAVE:
                break;
        }
        currentRole = newRole;
    }

    @Override
    public void controllerNodeIPsChanged(
                                         Map<String, String> curControllerNodeIPs,
                                         Map<String, String> addedControllerNodeIPs,
                                         Map<String, String> removedControllerNodeIPs) {
        // ignore
    }
}
