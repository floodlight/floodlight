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

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.debugevent.IEventCategory;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.SwitchType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.web.LinkDiscoveryWebRoutable;
import net.floodlightcontroller.notification.INotificationManager;
import net.floodlightcontroller.notification.NotificationManagerFactory;
import net.floodlightcontroller.packet.BSN;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.util.HexString;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class sends out LLDP messages containing the sending switch's datapath
 * id as well as the outgoing port number. Received LLrescDP messages that match
 * a known switch cause a new LinkTuple to be created according to the invariant
 * rules listed below. This new LinkTuple is also passed to routing if it exists
 * to trigger updates. This class also handles removing links that are
 * associated to switch ports that go down, and switches that are disconnected.
 * Invariants: -portLinks and switchLinks will not contain empty Sets outside of
 * critical sections -portLinks contains LinkTuples where one of the src or dst
 * SwitchPortTuple matches the map key -switchLinks contains LinkTuples where
 * one of the src or dst SwitchPortTuple's id matches the switch id -Each
 * LinkTuple will be indexed into switchLinks for both src.id and dst.id, and
 * portLinks for each src and dst -The updates queue is only added to from
 * within a held write lock
 * 
 * @edited Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 */
@LogMessageCategory("Network Topology")
public class LinkDiscoveryManager implements IOFMessageListener,
IOFSwitchListener, IStorageSourceListener, ILinkDiscoveryService,
IFloodlightModule, IInfoProvider {
	protected static final Logger log = LoggerFactory.getLogger(LinkDiscoveryManager.class);
	protected static final INotificationManager notifier =
			NotificationManagerFactory.getNotificationManager(LinkDiscoveryManager.class);

	public static final String MODULE_NAME = "linkdiscovery";

	// Names of table/fields for links in the storage API
	private static final String TOPOLOGY_TABLE_NAME = "controller_topologyconfig";
	private static final String TOPOLOGY_ID = "id";
	private static final String TOPOLOGY_AUTOPORTFAST = "autoportfast";

	private static final String LINK_TABLE_NAME = "controller_link";
	private static final String LINK_ID = "id";
	private static final String LINK_SRC_SWITCH = "src_switch_id";
	private static final String LINK_SRC_PORT = "src_port";
	private static final String LINK_DST_SWITCH = "dst_switch_id";
	private static final String LINK_DST_PORT = "dst_port";
	private static final String LINK_VALID_TIME = "valid_time";
	private static final String LINK_TYPE = "link_type";
	private static final String SWITCH_CONFIG_TABLE_NAME = "controller_switchconfig";

	// Event updaters for debug events
	protected IEventCategory<DirectLinkEvent> eventCategory;

	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IStorageSourceService storageSourceService;
	protected IThreadPoolService threadPoolService;
	protected IRestApiService restApiService;
	protected IDebugCounterService debugCounterService;
	protected IDebugEventService debugEventService;
	protected IShutdownService shutdownService;

	// Role
	protected HARole role;

	// LLDP and BDDP fields
	private static final byte[] LLDP_STANDARD_DST_MAC_STRING =
			HexString.fromHexString("01:80:c2:00:00:0e");
	private static final long LINK_LOCAL_MASK = 0xfffffffffff0L;
	private static final long LINK_LOCAL_VALUE = 0x0180c2000000L;
	protected static int EVENT_HISTORY_SIZE = 1024; // in seconds

	// BigSwitch OUI is 5C:16:C7, so 5D:16:C7 is the multicast version
	// private static final String LLDP_BSN_DST_MAC_STRING =
	// "5d:16:c7:00:00:01";
	private static final String LLDP_BSN_DST_MAC_STRING = "ff:ff:ff:ff:ff:ff";

	// Direction TLVs are used to indicate if the LLDPs were sent
	// periodically or in response to a recieved LLDP
	private static final byte TLV_DIRECTION_TYPE = 0x73;
	private static final short TLV_DIRECTION_LENGTH = 1; // 1 byte
	private static final byte TLV_DIRECTION_VALUE_FORWARD[] = { 0x01 };
	private static final byte TLV_DIRECTION_VALUE_REVERSE[] = { 0x02 };
	private static final LLDPTLV forwardTLV = new LLDPTLV().setType(TLV_DIRECTION_TYPE)
			.setLength(TLV_DIRECTION_LENGTH)
			.setValue(TLV_DIRECTION_VALUE_FORWARD);

	private static final LLDPTLV reverseTLV = new LLDPTLV().setType(TLV_DIRECTION_TYPE)
			.setLength(TLV_DIRECTION_LENGTH)
			.setValue(TLV_DIRECTION_VALUE_REVERSE);

	// Link discovery task details.
	protected SingletonTask discoveryTask;
	protected final int DISCOVERY_TASK_INTERVAL = 1;
	protected final int LINK_TIMEOUT = 35; // timeout as part of LLDP process.
	protected final int LLDP_TO_ALL_INTERVAL = 15; // 15 seconds.
	protected long lldpClock = 0;
	// This value is intentionally kept higher than LLDP_TO_ALL_INTERVAL.
	// If we want to identify link failures faster, we could decrease this
	// value to a small number, say 1 or 2 sec.
	protected final int LLDP_TO_KNOWN_INTERVAL = 20; // LLDP frequency for known
	// links

	protected LLDPTLV controllerTLV;
	protected ReentrantReadWriteLock lock;
	int lldpTimeCount = 0;

	/**
	 * Flag to indicate if automatic port fast is enabled or not. Default is set
	 * to false -- Initialized in the init method as well.
	 */
	protected boolean AUTOPORTFAST_DEFAULT = false;
	protected boolean autoPortFastFeature = AUTOPORTFAST_DEFAULT;

	/**
	 * Map from link to the most recent time it was verified functioning
	 */
	protected Map<Link, LinkInfo> links;

	/**
	 * Map from switch id to a set of all links with it as an endpoint
	 */
	protected Map<DatapathId, Set<Link>> switchLinks;

	/**
	 * Map from a id:port to the set of links containing it as an endpoint
	 */
	protected Map<NodePortTuple, Set<Link>> portLinks;

	protected volatile boolean shuttingDown = false;

	/*
	 * topology aware components are called in the order they were added to the
	 * the array
	 */
	protected ArrayList<ILinkDiscoveryListener> linkDiscoveryAware;
	protected BlockingQueue<LDUpdate> updates;
	protected Thread updatesThread;

	/**
	 * List of ports through which LLDP/BDDPs are not sent.
	 */
	protected Set<NodePortTuple> suppressLinkDiscovery;

	/**
	 * A list of ports that are quarantined for discovering links through them.
	 * Data traffic from these ports are not allowed until the ports are
	 * released from quarantine.
	 */
	protected LinkedBlockingQueue<NodePortTuple> quarantineQueue;
	protected LinkedBlockingQueue<NodePortTuple> maintenanceQueue;
	protected LinkedBlockingQueue<NodePortTuple> toRemoveFromQuarantineQueue;
	protected LinkedBlockingQueue<NodePortTuple> toRemoveFromMaintenanceQueue;
	
	/**
	 * Quarantine task
	 */
	protected SingletonTask bddpTask;
	protected final int BDDP_TASK_INTERVAL = 100; // 100 ms.
	protected final int BDDP_TASK_SIZE = 10; // # of ports per iteration

	private class MACRange {
		MacAddress baseMAC;
		int ignoreBits;
	}
	protected Set<MACRange> ignoreMACSet;

	private IHAListener haListener;

	/**
	 * Debug Counters
	 */
	private IDebugCounter ctrQuarantineDrops;
	private IDebugCounter ctrIgnoreSrcMacDrops;
	private IDebugCounter ctrIncoming;
	private IDebugCounter ctrLinkLocalDrops;
	private IDebugCounter ctrLldpEol;

	private final String PACKAGE = LinkDiscoveryManager.class.getPackage().getName();


	//*********************
	// ILinkDiscoveryService
	//*********************

	@Override
	public OFPacketOut generateLLDPMessage(DatapathId sw, OFPort port,
			boolean isStandard, boolean isReverse) {

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		OFPortDesc ofpPort = iofSwitch.getPort(port);

		if (log.isTraceEnabled()) {
			log.trace("Sending LLDP packet out of swich: {}, port: {}",
					sw.toString(), port);
		}

		// using "nearest customer bridge" MAC address for broadest possible
		// propagation
		// through provider and TPMR bridges (see IEEE 802.1AB-2009 and
		// 802.1Q-2011),
		// in particular the Linux bridge which behaves mostly like a provider
		// bridge
		byte[] chassisId = new byte[] { 4, 0, 0, 0, 0, 0, 0 }; // filled in
		// later
		byte[] portId = new byte[] { 2, 0, 0 }; // filled in later
		byte[] ttlValue = new byte[] { 0, 0x78 };
		// OpenFlow OUI - 00-26-E1
		byte[] dpidTLVValue = new byte[] { 0x0, 0x26, (byte) 0xe1, 0, 0, 0,
				0, 0, 0, 0, 0, 0 };
		LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127)
				.setLength((short) dpidTLVValue.length)
				.setValue(dpidTLVValue);

		byte[] dpidArray = new byte[8];
		ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
		ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);

		DatapathId dpid = sw;
		dpidBB.putLong(dpid.getLong());
		// set the chassis id's value to last 6 bytes of dpid
		System.arraycopy(dpidArray, 2, chassisId, 1, 6);
		// set the optional tlv to the full dpid
		System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

		// TODO: Consider remove this block of code.
		// It's evil to overwrite port object. The the old code always
		// overwrote mac address, we now only overwrite zero macs and
		// log a warning, mostly for paranoia.
		byte[] srcMac = ofpPort.getHwAddr().getBytes();
		byte[] zeroMac = { 0, 0, 0, 0, 0, 0 };
		if (Arrays.equals(srcMac, zeroMac)) {
			log.warn("Port {}/{} has zero hareware address"
					+ "overwrite with lower 6 bytes of dpid",
					dpid.toString(), ofpPort.getPortNo().getPortNumber());
			System.arraycopy(dpidArray, 2, srcMac, 0, 6);
		}

		// set the portId to the outgoing port
		portBB.putShort(port.getShortPortNumber());
		if (log.isTraceEnabled()) {
			log.trace("Sending LLDP out of interface: {}/{}",
					sw.toString(), port);
		}

		LLDP lldp = new LLDP();
		lldp.setChassisId(new LLDPTLV().setType((byte) 1)
				.setLength((short) chassisId.length)
				.setValue(chassisId));
		lldp.setPortId(new LLDPTLV().setType((byte) 2)
				.setLength((short) portId.length)
				.setValue(portId));
		lldp.setTtl(new LLDPTLV().setType((byte) 3)
				.setLength((short) ttlValue.length)
				.setValue(ttlValue));
		lldp.getOptionalTLVList().add(dpidTLV);

		// Add the controller identifier to the TLV value.
		lldp.getOptionalTLVList().add(controllerTLV);
		if (isReverse) {
			lldp.getOptionalTLVList().add(reverseTLV);
		} else {
			lldp.getOptionalTLVList().add(forwardTLV);
		}

		Ethernet ethernet;
		if (isStandard) {
			ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
					.setDestinationMACAddress(LLDP_STANDARD_DST_MAC_STRING)
					.setEtherType(EthType.LLDP);
			ethernet.setPayload(lldp);
		} else {
			BSN bsn = new BSN(BSN.BSN_TYPE_BDDP);
			bsn.setPayload(lldp);

			ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
					.setDestinationMACAddress(LLDP_BSN_DST_MAC_STRING)
					.setEtherType(EthType.of(Ethernet.TYPE_BSN & 0xffff)); /* treat as unsigned */
			ethernet.setPayload(bsn);
		}

		// serialize and wrap in a packet out
		byte[] data = ethernet.serialize();
		OFPacketOut.Builder pob = switchService.getSwitch(sw).getOFFactory().buildPacketOut();
		pob.setBufferId(OFBufferId.NO_BUFFER);
		pob.setInPort(OFPort.ANY);

		// set data and data length
		pob.setData(data);

		return pob.build();
	}

	/**
	 * Get the LLDP sending period in seconds.
	 *
	 * @return LLDP sending period in seconds.
	 */
	public int getLldpFrequency() {
		return LLDP_TO_KNOWN_INTERVAL;
	}

	/**
	 * Get the LLDP timeout value in seconds
	 *
	 * @return LLDP timeout value in seconds
	 */
	public int getLldpTimeout() {
		return LINK_TIMEOUT;
	}

	@Override
	public Map<NodePortTuple, Set<Link>> getPortLinks() {
		return portLinks;
	}

	@Override
	public Set<NodePortTuple> getSuppressLLDPsInfo() {
		return suppressLinkDiscovery;
	}

	/**
	 * Add a switch port to the suppressed LLDP list. Remove any known links on
	 * the switch port.
	 */
	@Override
	public void AddToSuppressLLDPs(DatapathId sw, OFPort port) {
		NodePortTuple npt = new NodePortTuple(sw, port);
		this.suppressLinkDiscovery.add(npt);
		deleteLinksOnPort(npt, "LLDP suppressed.");
	}

	/**
	 * Remove a switch port from the suppressed LLDP list. Discover links on
	 * that switchport.
	 */
	@Override
	public void RemoveFromSuppressLLDPs(DatapathId sw, OFPort port) {
		NodePortTuple npt = new NodePortTuple(sw, port);
		this.suppressLinkDiscovery.remove(npt);
		discover(npt);
	}

	public boolean isShuttingDown() {
		return shuttingDown;
	}

	@Override
	public boolean isTunnelPort(DatapathId sw, OFPort port) {
		return false;
	}

	@Override
	public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info) {
		if (info.getUnicastValidTime() != null) {
			return ILinkDiscovery.LinkType.DIRECT_LINK;
		} else if (info.getMulticastValidTime() != null) {
			return ILinkDiscovery.LinkType.MULTIHOP_LINK;
		}
		return ILinkDiscovery.LinkType.INVALID_LINK;
	}

	@Override
	public Set<OFPort> getQuarantinedPorts(DatapathId sw) {
		Set<OFPort> qPorts = new HashSet<OFPort>();

		Iterator<NodePortTuple> iter = quarantineQueue.iterator();
		while (iter.hasNext()) {
			NodePortTuple npt = iter.next();
			if (npt.getNodeId().equals(sw)) {
				qPorts.add(npt.getPortId());
			}
		}
		return qPorts;
	}

	@Override
	public Map<DatapathId, Set<Link>> getSwitchLinks() {
		return this.switchLinks;
	}

	@Override
	public void addMACToIgnoreList(MacAddress mac, int ignoreBits) {
		MACRange range = new MACRange();
		range.baseMAC = mac;
		range.ignoreBits = ignoreBits;
		ignoreMACSet.add(range);
	}

	@Override
	public boolean isAutoPortFastFeature() {
		return autoPortFastFeature;
	}

	@Override
	public void setAutoPortFastFeature(boolean autoPortFastFeature) {
		this.autoPortFastFeature = autoPortFastFeature;
	}

	@Override
	public void addListener(ILinkDiscoveryListener listener) {
		linkDiscoveryAware.add(listener);
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

	@Override
	public LinkInfo getLinkInfo(Link link) {
		lock.readLock().lock();
		LinkInfo linkInfo = links.get(link);
		LinkInfo retLinkInfo = null;
		if (linkInfo != null) {
			retLinkInfo  = new LinkInfo(linkInfo);
		}
		lock.readLock().unlock();
		return retLinkInfo;
	}

	@Override
	public String getName() {
		return MODULE_NAME;
	}

	//*********************
	//   OFMessage Listener
	//*********************

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			ctrIncoming.increment();
			return this.handlePacketIn(sw.getId(), (OFPacketIn) msg,
					cntx);
		default:
			break;
		}
		return Command.CONTINUE;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	//***********************************
	//  Internal Methods - Packet-in Processing Related
	//***********************************

	protected Command handlePacketIn(DatapathId sw, OFPacketIn pi,
			FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		if (eth.getPayload() instanceof BSN) {
			BSN bsn = (BSN) eth.getPayload();
			if (bsn == null) return Command.STOP;
			if (bsn.getPayload() == null) return Command.STOP;
			// It could be a packet other than BSN LLDP, therefore
			// continue with the regular processing.
			if (bsn.getPayload() instanceof LLDP == false)
				return Command.CONTINUE;
			return handleLldp((LLDP) bsn.getPayload(), sw, inPort, false, cntx);
		} else if (eth.getPayload() instanceof LLDP) {
			return handleLldp((LLDP) eth.getPayload(), sw, inPort, true, cntx);
		} else if (eth.getEtherType().getValue() < 1536 && eth.getEtherType().getValue() >= 17) {
	        long destMac = eth.getDestinationMACAddress().getLong();
	        if ((destMac & LINK_LOCAL_MASK) == LINK_LOCAL_VALUE) {
	            ctrLinkLocalDrops.increment();
	            if (log.isTraceEnabled()) {
	                log.trace("Ignoring packet addressed to 802.1D/Q "
	                        + "reserved address.");
	            }
	            return Command.STOP;
	        }
	    } else if (eth.getEtherType().getValue() < 17) {
	        log.error("Received invalid ethertype of {}.", eth.getEtherType());
	        return Command.STOP;
	    }

		if (ignorePacketInFromSource(eth.getSourceMACAddress())) {
			ctrIgnoreSrcMacDrops.increment();
			return Command.STOP;
		}

		// If packet-in is from a quarantine port, stop processing.
		NodePortTuple npt = new NodePortTuple(sw, inPort);
		if (quarantineQueue.contains(npt)) {
			ctrQuarantineDrops.increment();
			return Command.STOP;
		}

		return Command.CONTINUE;
	}

	private boolean ignorePacketInFromSource(MacAddress srcMAC) {
		Iterator<MACRange> it = ignoreMACSet.iterator();
		while (it.hasNext()) {
			MACRange range = it.next();
			long mask = ~0;
			if (range.ignoreBits >= 0 && range.ignoreBits <= 48) {
				mask = mask << range.ignoreBits;
				if ((range.baseMAC.getLong() & mask) == (srcMAC.getLong() & mask)) {
					return true;
				}
			}
		}
		return false;
	}

	private Command handleLldp(LLDP lldp, DatapathId sw, OFPort inPort,
			boolean isStandard, FloodlightContext cntx) {
		// If LLDP is suppressed on this port, ignore received packet as well
		IOFSwitch iofSwitch = switchService.getSwitch(sw);

		if (!isIncomingDiscoveryAllowed(sw, inPort, isStandard))
			return Command.STOP;

		// If this is a malformed LLDP exit
		if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3) {
			return Command.STOP;
		}

		long myId = ByteBuffer.wrap(controllerTLV.getValue()).getLong();
		long otherId = 0;
		boolean myLLDP = false;
		Boolean isReverse = null;

		ByteBuffer portBB = ByteBuffer.wrap(lldp.getPortId().getValue());
		portBB.position(1);

		OFPort remotePort = OFPort.of(portBB.getShort());
		IOFSwitch remoteSwitch = null;

		// Verify this LLDP packet matches what we're looking for
		for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
			if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
					&& lldptlv.getValue()[0] == 0x0
					&& lldptlv.getValue()[1] == 0x26
					&& lldptlv.getValue()[2] == (byte) 0xe1
					&& lldptlv.getValue()[3] == 0x0) {
				ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
				remoteSwitch = switchService.getSwitch(DatapathId.of(dpidBB.getLong(4)));
			} else if (lldptlv.getType() == 12 && lldptlv.getLength() == 8) {
				otherId = ByteBuffer.wrap(lldptlv.getValue()).getLong();
				if (myId == otherId) myLLDP = true;
			} else if (lldptlv.getType() == TLV_DIRECTION_TYPE
					&& lldptlv.getLength() == TLV_DIRECTION_LENGTH) {
				if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_FORWARD[0])
					isReverse = false;
				else if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_REVERSE[0])
					isReverse = true;
			}
		}

		if (myLLDP == false) {
			// This is not the LLDP sent by this controller.
			// If the LLDP message has multicast bit set, then we need to
			// broadcast the packet as a regular packet (after checking IDs)
			if (isStandard) {
				if (log.isTraceEnabled()) {
					log.trace("Got a standard LLDP=[{}] that was not sent by" +
							" this controller. Not fowarding it.", lldp.toString());
				}
				return Command.STOP;
			} else if (myId < otherId) {
				if (log.isTraceEnabled()) {
					log.trace("Getting BDDP packets from a different controller"
							+ "and letting it go through normal processing chain.");
				}
				return Command.CONTINUE;
			}
			return Command.STOP;
		}

		if (remoteSwitch == null) {
			// Ignore LLDPs not generated by Floodlight, or from a switch that
			// has recently
			// disconnected, or from a switch connected to another Floodlight
			// instance
			if (log.isTraceEnabled()) {
				log.trace("Received LLDP from remote switch not connected to the controller");
			}
			return Command.STOP;
		}

		if (!remoteSwitch.portEnabled(remotePort)) {
			if (log.isTraceEnabled()) {
				log.trace("Ignoring link with disabled source port: switch {} port {} {}",
						new Object[] { remoteSwitch.getId().toString(),
						remotePort,
						remoteSwitch.getPort(remotePort)});
			}
			return Command.STOP;
		}
		if (suppressLinkDiscovery.contains(new NodePortTuple(
				remoteSwitch.getId(),
				remotePort))) {
			if (log.isTraceEnabled()) {
				log.trace("Ignoring link with suppressed src port: switch {} port {} {}",
						new Object[] { remoteSwitch.getId().toString(),
						remotePort,
						remoteSwitch.getPort(remotePort)});
			}
			return Command.STOP;
		}
		if (!iofSwitch.portEnabled(inPort)) {
			if (log.isTraceEnabled()) {
				log.trace("Ignoring link with disabled dest port: switch {} port {} {}",
						new Object[] { sw.toString(),
						inPort.getPortNumber(),
						iofSwitch.getPort(inPort).getPortNo().getPortNumber()});
			}
			return Command.STOP;
		}

		// Store the time of update to this link, and push it out to
		// routingEngine
		Link lt = new Link(remoteSwitch.getId(), remotePort,
				iofSwitch.getId(), inPort);

		if (!isLinkAllowed(lt.getSrc(), lt.getSrcPort(),
				lt.getDst(), lt.getDstPort()))
			return Command.STOP;

		// Continue only if link is allowed.
		Date lastLldpTime = null;
		Date lastBddpTime = null;

		Date firstSeenTime = new Date(System.currentTimeMillis());

		if (isStandard)
			lastLldpTime = new Date(System.currentTimeMillis());
		else
			lastBddpTime = new Date(System.currentTimeMillis());

		LinkInfo newLinkInfo = new LinkInfo(firstSeenTime, lastLldpTime,
				lastBddpTime);

		addOrUpdateLink(lt, newLinkInfo);

		// Check if reverse link exists.
		// If it doesn't exist and if the forward link was seen
		// first seen within a small interval, send probe on the
		// reverse link.
		newLinkInfo = links.get(lt);
		if (newLinkInfo != null && isStandard && isReverse == false) {
			Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
					lt.getSrc(), lt.getSrcPort());
			LinkInfo reverseInfo = links.get(reverseLink);
			if (reverseInfo == null) {
				// the reverse link does not exist.
				if (newLinkInfo.getFirstSeenTime().getTime() > System.currentTimeMillis()
						- LINK_TIMEOUT) {
					this.sendDiscoveryMessage(lt.getDst(), lt.getDstPort(),
							isStandard, true);
				}
			}
		}

		// If the received packet is a BDDP packet, then create a reverse BDDP
		// link as well.
		if (!isStandard) {
			Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
					lt.getSrc(), lt.getSrcPort());

			// srcPortState and dstPort state are reversed.
			LinkInfo reverseInfo = new LinkInfo(firstSeenTime, lastLldpTime,
					lastBddpTime);

			addOrUpdateLink(reverseLink, reverseInfo);
		}

		// Queue removal of the node ports from the quarantine and maintenance queues.
		NodePortTuple nptSrc = new NodePortTuple(lt.getSrc(),
				lt.getSrcPort());
		NodePortTuple nptDst = new NodePortTuple(lt.getDst(),
				lt.getDstPort());
		
		flagToRemoveFromQuarantineQueue(nptSrc);
		flagToRemoveFromMaintenanceQueue(nptSrc);
		flagToRemoveFromQuarantineQueue(nptDst);
		flagToRemoveFromMaintenanceQueue(nptDst);

		// Consume this message
		ctrLldpEol.increment();
		return Command.STOP;
	}

	//***********************************
	//  Internal Methods - Port Status/ New Port Processing Related
	//***********************************
	/**
	 * Process a new port. If link discovery is disabled on the port, then do
	 * nothing. If autoportfast feature is enabled and the port is a fast port,
	 * then do nothing. Otherwise, send LLDP message. Add the port to
	 * quarantine.
	 *
	 * @param sw
	 * @param p
	 */
	private void processNewPort(DatapathId sw, OFPort p) {
		if (isLinkDiscoverySuppressed(sw, p)) {
			// Do nothing as link discovery is suppressed.
			return;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) {
			return;
		}

		NodePortTuple npt = new NodePortTuple(sw, p);
		discover(sw, p);
		addToQuarantineQueue(npt);
	}

	//***********************************
	//  Internal Methods - Discovery Related
	//***********************************

	@LogMessageDoc(level = "ERROR",
			message = "Error in link discovery updates loop",
			explanation = "An unknown error occured while dispatching "
					+ "link update notifications",
					recommendation = LogMessageDoc.GENERIC_ACTION)
	private void doUpdatesThread() throws InterruptedException {
		do {
			LDUpdate update = updates.take();
			List<LDUpdate> updateList = new ArrayList<LDUpdate>();
			updateList.add(update);

			// Add all the pending updates to the list.
			while (updates.peek() != null) {
				updateList.add(updates.remove());
			}

			if (linkDiscoveryAware != null) {
				if (log.isTraceEnabled()) {
					log.trace("Dispatching link discovery update {} {} {} {} {} for {}",
							new Object[] {
							update.getOperation(),
							update.getSrc().toString(),
							update.getSrcPort(),
							update.getDst().toString(),
							update.getDstPort(),
							linkDiscoveryAware });
				}
				try {
					for (ILinkDiscoveryListener lda : linkDiscoveryAware) { // order
						// maintained
						lda.linkDiscoveryUpdate(updateList);
					}
				} catch (Exception e) {
					log.error("Error in link discovery updates loop", e);
				}
			}
		} while (updates.peek() != null);
	}

	protected boolean isLinkDiscoverySuppressed(DatapathId sw, OFPort portNumber) {
		return this.suppressLinkDiscovery.contains(new NodePortTuple(sw,
				portNumber));
	}

	protected void discoverLinks() {

		// timeout known links.
		timeoutLinks();

		// increment LLDP clock
		lldpClock = (lldpClock + 1) % LLDP_TO_ALL_INTERVAL;

		if (lldpClock == 0) {
			if (log.isTraceEnabled())
				log.trace("Sending LLDP out on all ports.");
			discoverOnAllPorts();
		}
	}

	/**
	 * Quarantine Ports.
	 */
	protected class QuarantineWorker implements Runnable {
		@Override
		public void run() {
			try {
				processBDDPLists();
			} catch (Exception e) {
				log.error("Error in quarantine worker thread", e);
			} finally {
				bddpTask.reschedule(BDDP_TASK_INTERVAL,
						TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Add a switch port to the quarantine queue. Schedule the quarantine task
	 * if the quarantine queue was empty before adding this switch port.
	 *
	 * @param npt
	 */
	protected void addToQuarantineQueue(NodePortTuple npt) {
		if (quarantineQueue.contains(npt) == false) {
			quarantineQueue.add(npt);
		}
	}

	/**
	 * Remove a switch port from the quarantine queue.
	 *
	protected void removeFromQuarantineQueue(NodePortTuple npt) {
		// Remove all occurrences of the node port tuple from the list.
		while (quarantineQueue.remove(npt));
	}*/
	protected void flagToRemoveFromQuarantineQueue(NodePortTuple npt) {
		if (toRemoveFromQuarantineQueue.contains(npt) == false) {
			toRemoveFromQuarantineQueue.add(npt);
		}
	}

	/**
	 * Add a switch port to maintenance queue.
	 *
	 * @param npt
	 */
	protected void addToMaintenanceQueue(NodePortTuple npt) {
		if (maintenanceQueue.contains(npt) == false) {
			maintenanceQueue.add(npt);
		}
	}

	/**
	 * Remove a switch port from maintenance queue.
	 *
	 * @param npt
	 *
	protected void removeFromMaintenanceQueue(NodePortTuple npt) {
		// Remove all occurrences of the node port tuple from the queue.
		while (maintenanceQueue.remove(npt));
	} */
	protected void flagToRemoveFromMaintenanceQueue(NodePortTuple npt) {
		if (toRemoveFromMaintenanceQueue.contains(npt) == false) {
			toRemoveFromMaintenanceQueue.add(npt);
		}
	}

	/**
	 * This method processes the quarantine list in bursts. The task is at most
	 * once per BDDP_TASK_INTERVAL. One each call, BDDP_TASK_SIZE number of
	 * switch ports are processed. Once the BDDP packets are sent out through
	 * the switch ports, the ports are removed from the quarantine list.
	 */
	protected void processBDDPLists() {
		int count = 0;
		Set<NodePortTuple> nptList = new HashSet<NodePortTuple>();

		while (count < BDDP_TASK_SIZE && quarantineQueue.peek() != null) {
			NodePortTuple npt;
			npt = quarantineQueue.remove();
			/*
			 * Do not send a discovery message if we already have received one
			 * from another switch on this same port. In other words, if
			 * handleLldp() determines there is a new link between two ports of
			 * two switches, then there is no need to re-discover the link again.
			 * 
			 * By flagging the item in handleLldp() and waiting to remove it 
			 * from the queue when processBDDPLists() runs, we can guarantee a 
			 * PORT_STATUS update is generated and dispatched below by
			 * generateSwitchPortStatusUpdate().
			 */
			if (!toRemoveFromQuarantineQueue.remove(npt)) {
				sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false, false);
			}
			/*
			 * Still add the item to the list though, so that the PORT_STATUS update
			 * is generated below at the end of this function.
			 */
			nptList.add(npt);
			count++;
		}

		count = 0;
		while (count < BDDP_TASK_SIZE && maintenanceQueue.peek() != null) {
			NodePortTuple npt;
			npt = maintenanceQueue.remove();
			/*
			 * Same as above, except we don't care about the PORT_STATUS message; 
			 * we only want to avoid sending the discovery message again.
			 */
			if (!toRemoveFromMaintenanceQueue.remove(npt)) {
				sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false, false);
			}
			count++;
		}

		for (NodePortTuple npt : nptList) {
			generateSwitchPortStatusUpdate(npt.getNodeId(), npt.getPortId());
		}
	}

	private void generateSwitchPortStatusUpdate(DatapathId sw, OFPort port) {
		UpdateOperation operation;

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) return;

		OFPortDesc ofp = iofSwitch.getPort(port);
		if (ofp == null) return;

		Set<OFPortState> srcPortState = ofp.getState();
		boolean portUp = !srcPortState.contains(OFPortState.STP_BLOCK);

		if (portUp) {
			operation = UpdateOperation.PORT_UP;
		} else {
			operation = UpdateOperation.PORT_DOWN;
		}
		
		updates.add(new LDUpdate(sw, port, operation));
	}

	protected void discover(NodePortTuple npt) {
		discover(npt.getNodeId(), npt.getPortId());
	}

	protected void discover(DatapathId sw, OFPort port) {
		sendDiscoveryMessage(sw, port, true, false);
	}

	/**
	 * Check if incoming discovery messages are enabled or not.
	 * @param sw
	 * @param port
	 * @param isStandard
	 * @return
	 */
	protected boolean isIncomingDiscoveryAllowed(DatapathId sw, OFPort port,
			boolean isStandard) {

		if (isLinkDiscoverySuppressed(sw, port)) {
			/* Do not process LLDPs from this port as suppressLLDP is set */
			return false;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) {
			return false;
		}

		if (port == OFPort.LOCAL) return false;

		OFPortDesc ofpPort = iofSwitch.getPort(port);
		if (ofpPort == null) {
			if (log.isTraceEnabled()) {
				log.trace("Null physical port. sw={}, port={}",
						sw.toString(), port.getPortNumber());
			}
			return false;
		}

		return true;
	}

	/**
	 * Check if outgoing discovery messages are enabled or not.
	 * @param sw
	 * @param port
	 * @param isStandard
	 * @param isReverse
	 * @return
	 */
	protected boolean isOutgoingDiscoveryAllowed(DatapathId sw, OFPort port,
			boolean isStandard,
			boolean isReverse) {

		if (isLinkDiscoverySuppressed(sw, port)) {
			/* Dont send LLDPs out of this port as suppressLLDP is set */
			return false;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) {
			return false;
		}

		if (port == OFPort.LOCAL) return false;

		OFPortDesc ofpPort = iofSwitch.getPort(port);
		if (ofpPort == null) {
			if (log.isTraceEnabled()) {
				log.trace("Null physical port. sw={}, port={}",
						sw.toString(), port.getPortNumber());
			}
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Get the actions for packet-out corresponding to a specific port.
	 * This is a placeholder for adding actions if any port-specific
	 * actions are desired.  The default action is simply to output to
	 * the given port.
	 * @param port
	 * @return
	 */
	protected List<OFAction> getDiscoveryActions (IOFSwitch sw, OFPort port){
		// set actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().buildOutput().setPort(port).build());
		return actions;
	}

	/**
	 * Send link discovery message out of a given switch port. The discovery
	 * message may be a standard LLDP or a modified LLDP, where the dst mac
	 * address is set to :ff. TODO: The modified LLDP will updated in the future
	 * and may use a different eth-type.
	 *
	 * @param sw
	 * @param port
	 * @param isStandard
	 *            indicates standard or modified LLDP
	 * @param isReverse
	 *            indicates whether the LLDP was sent as a response
	 */
	@LogMessageDoc(level = "ERROR",
			message = "Failure sending LLDP out port {port} on switch {switch}",
			explanation = "An I/O error occured while sending LLDP message "
					+ "to the switch.",
					recommendation = LogMessageDoc.CHECK_SWITCH)
	protected void sendDiscoveryMessage(DatapathId sw, OFPort port,
			boolean isStandard, boolean isReverse) {

		// Takes care of all checks including null pointer checks.
		if (!isOutgoingDiscoveryAllowed(sw, port, isStandard, isReverse))
			return;

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		OFPortDesc ofpPort = iofSwitch.getPort(port);

		if (log.isTraceEnabled()) {
			log.trace("Sending LLDP packet out of swich: {}, port: {}",
					sw.toString(), port.getPortNumber());
		}
		OFPacketOut po = generateLLDPMessage(sw, port, isStandard, isReverse);
		OFPacketOut.Builder pob = po.createBuilder();

		// Add actions
		List<OFAction> actions = getDiscoveryActions(iofSwitch, ofpPort.getPortNo());
		pob.setActions(actions);
		
		// no need to set length anymore

		// send
		// no more try-catch. switch will silently fail
		iofSwitch.write(pob.build());
		iofSwitch.flush();
	}

	/**
	 * Send LLDPs to all switch-ports
	 */
	protected void discoverOnAllPorts() {
		log.info("Sending LLDP packets out of all the enabled ports");
		// Send standard LLDPs
		for (DatapathId sw : switchService.getAllSwitchDpids()) {
			IOFSwitch iofSwitch = switchService.getSwitch(sw);
			if (iofSwitch == null) continue;
			if (!iofSwitch.isActive()) continue; /* can't do anything if the switch is SLAVE */
			if (iofSwitch.getEnabledPorts() != null) {
				for (OFPortDesc ofp : iofSwitch.getEnabledPorts()) {
					if (isLinkDiscoverySuppressed(sw, ofp.getPortNo())) {
						continue;
					}

					sendDiscoveryMessage(sw, ofp.getPortNo(), true, false);

					// If the switch port is not already in the maintenance
					// queue, add it.
					NodePortTuple npt = new NodePortTuple(sw, ofp.getPortNo());
					addToMaintenanceQueue(npt);
				}
			}
		}
	}

	protected UpdateOperation getUpdateOperation(OFPortState srcPortState, OFPortState dstPortState) {
		boolean added = ((srcPortState != OFPortState.STP_BLOCK) && (dstPortState != OFPortState.STP_BLOCK));

		if (added) {
			return UpdateOperation.LINK_UPDATED;
		} else {
			return UpdateOperation.LINK_REMOVED;
		}
	}

	protected UpdateOperation getUpdateOperation(OFPortState srcPortState) {
		boolean portUp = (srcPortState != OFPortState.STP_BLOCK);

		if (portUp) {
			return UpdateOperation.PORT_UP;
		} else {
			return UpdateOperation.PORT_DOWN;
		}
	}

	//************************************
	// Internal Methods - Link Operations Related
	//************************************

	/**
	 * This method is used to specifically ignore/consider specific links.
	 */
	protected boolean isLinkAllowed(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort) {
		return true;
	}

	private boolean addLink(Link lt, LinkInfo newInfo) {
		NodePortTuple srcNpt, dstNpt;

		srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
		dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

		// index it by switch source
		if (!switchLinks.containsKey(lt.getSrc()))
			switchLinks.put(lt.getSrc(),
					new HashSet<Link>());
		switchLinks.get(lt.getSrc()).add(lt);

		// index it by switch dest
		if (!switchLinks.containsKey(lt.getDst()))
			switchLinks.put(lt.getDst(),
					new HashSet<Link>());
		switchLinks.get(lt.getDst()).add(lt);

		// index both ends by switch:port
		if (!portLinks.containsKey(srcNpt))
			portLinks.put(srcNpt,
					new HashSet<Link>());
		portLinks.get(srcNpt).add(lt);

		if (!portLinks.containsKey(dstNpt))
			portLinks.put(dstNpt,
					new HashSet<Link>());
		portLinks.get(dstNpt).add(lt);

		return true;
	}

	protected boolean updateLink(Link lt, LinkInfo oldInfo, LinkInfo newInfo) {
		boolean linkChanged = false;
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

		Date oldTime = oldInfo.getUnicastValidTime();
		Date newTime = newInfo.getUnicastValidTime();
		// the link has changed its state between openflow and
		// non-openflow
		// if the unicastValidTimes are null or not null
		if (oldTime != null & newTime == null) {
			linkChanged = true;
		} else if (oldTime == null & newTime != null) {
			linkChanged = true;
		}

		return linkChanged;
	}

	@LogMessageDocs({
		@LogMessageDoc(message="Inter-switch link detected:",
				explanation="Detected a new link between two openflow switches," +
				"use show link to find current status"),
				@LogMessageDoc(message="Inter-switch link updated:",
				explanation="Detected a link change between two openflow switches, " +
						"use show link to find current status")
	})
	protected boolean addOrUpdateLink(Link lt, LinkInfo newInfo) {

		boolean linkChanged = false;

		lock.writeLock().lock();
		try {
			// put the new info. if an old info exists, it will be returned.
			LinkInfo oldInfo = links.put(lt, newInfo);
			if (oldInfo != null
					&& oldInfo.getFirstSeenTime().getTime() < newInfo.getFirstSeenTime().getTime())
				newInfo.setFirstSeenTime(oldInfo.getFirstSeenTime());

			if (log.isTraceEnabled()) {
				log.trace("addOrUpdateLink: {} {}",
						lt,
						(newInfo.getMulticastValidTime() != null) ? "multicast"
								: "unicast");
			}

			UpdateOperation updateOperation = null;
			linkChanged = false;

			if (oldInfo == null) {
				addLink(lt, newInfo);
				updateOperation = UpdateOperation.LINK_UPDATED;
				linkChanged = true;

				// Log direct links only. Multi-hop links may be numerous
				// Add all to event history
				LinkType linkType = getLinkType(lt, newInfo);
				if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
					log.info("Inter-switch link detected: {}", lt);
					eventCategory.newEventNoFlush(new DirectLinkEvent(lt.getSrc(),
							lt.getSrcPort(), lt.getDst(), lt.getDstPort(), "direct-link-added::rcvd LLDP"));
				}
				notifier.postNotification("Link added: " + lt.toString());
			} else {
				linkChanged = updateLink(lt, oldInfo, newInfo);
				if (linkChanged) {
					updateOperation = UpdateOperation.LINK_UPDATED;
					LinkType linkType = getLinkType(lt, newInfo);
					if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
						log.info("Inter-switch link updated: {}", lt);
						eventCategory.newEventNoFlush(new DirectLinkEvent(lt.getSrc(),
								lt.getSrcPort(), lt.getDst(), lt.getDstPort(),
								"link-port-state-updated::rcvd LLDP"));
					}
					notifier.postNotification("Link updated: " + lt.toString());
				}
			}

			// Write changes to storage. This will always write the updated
			// valid time, plus the port states if they've changed (i.e. if
			// they weren't set to null in the previous block of code.
			writeLinkToStorage(lt, newInfo);

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

	/**
	 * Delete a link
	 *
	 * @param link
	 *            - link to be deleted.
	 * @param reason
	 *            - reason why the link is deleted.
	 */
	protected void deleteLink(Link link, String reason) {
		if (link == null)
			return;
		List<Link> linkList = new ArrayList<Link>();
		linkList.add(link);
		deleteLinks(linkList, reason);
	}
	/**
	 * Removes links from memory and storage.
	 *
	 * @param links
	 *            The List of @LinkTuple to delete.
	 */
	protected void deleteLinks(List<Link> links, String reason) {
		deleteLinks(links, reason, null);
	}

	/**
	 * Removes links from memory and storage.
	 *
	 * @param links
	 *            The List of @LinkTuple to delete.
	 */
	@LogMessageDoc(message="Inter-switch link removed:",
			explanation="A previously detected link between two openflow switches no longer exists, " +
			"use show link to find current status")
	protected void deleteLinks(List<Link> links, String reason,
			List<LDUpdate> updateList) {

		NodePortTuple srcNpt, dstNpt;
		List<LDUpdate> linkUpdateList = new ArrayList<LDUpdate>();
		lock.writeLock().lock();
		try {
			for (Link lt : links) {
				srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
				dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

				if (switchLinks.containsKey(lt.getSrc())) {
					switchLinks.get(lt.getSrc()).remove(lt);
					if (switchLinks.get(lt.getSrc()).isEmpty())
						this.switchLinks.remove(lt.getSrc());
				}
				if (this.switchLinks.containsKey(lt.getDst())) {
					switchLinks.get(lt.getDst()).remove(lt);
					if (this.switchLinks.get(lt.getDst()).isEmpty())
						this.switchLinks.remove(lt.getDst());
				}

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

				LinkInfo info = this.links.remove(lt);
				LinkType linkType = getLinkType(lt, info);
				linkUpdateList.add(new LDUpdate(lt.getSrc(),
						lt.getSrcPort(),
						lt.getDst(),
						lt.getDstPort(),
						linkType,
						UpdateOperation.LINK_REMOVED));

				// FIXME: link type shows up as invalid now -- thus not checking if
				// link type is a direct link
				eventCategory.newEventWithFlush(new DirectLinkEvent(lt.getSrc(),
						lt.getSrcPort(), lt.getDst(), lt.getDstPort(),
						"link-deleted::" + reason));
				// remove link from storage.
				removeLinkFromStorage(lt);

				// TODO Whenever link is removed, it has to checked if
				// the switchports must be added to quarantine.

				if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
					log.info("Inter-switch link removed: {}", lt);
					notifier.postNotification("Inter-switch link removed: " +
							lt.toString());
				} else if (log.isTraceEnabled()) {
					log.trace("Deleted link {}", lt);
				}
			}
		} finally {
			if (updateList != null) linkUpdateList.addAll(updateList);
			updates.addAll(linkUpdateList);
			lock.writeLock().unlock();
		}
	}

	/**
	 * Delete links incident on a given switch port.
	 *
	 * @param npt
	 * @param reason
	 */
	protected void deleteLinksOnPort(NodePortTuple npt, String reason) {
		List<Link> eraseList = new ArrayList<Link>();
		if (this.portLinks.containsKey(npt)) {
			if (log.isTraceEnabled()) {
				log.trace("handlePortStatus: Switch {} port #{} "
						+ "removing links {}",
						new Object[] {
								npt.getNodeId().toString(),
								npt.getPortId(),
								this.portLinks.get(npt) });
			}
			eraseList.addAll(this.portLinks.get(npt));
			deleteLinks(eraseList, reason);
		}
	}

	/**
	 * Iterates through the list of links and deletes if the last discovery
	 * message reception time exceeds timeout values.
	 */
	protected void timeoutLinks() {
		List<Link> eraseList = new ArrayList<Link>();
		Long curTime = System.currentTimeMillis();
		boolean linkChanged = false;

		// reentrant required here because deleteLink also write locks
		lock.writeLock().lock();
		try {
			Iterator<Entry<Link, LinkInfo>> it = this.links.entrySet()
					.iterator();
			while (it.hasNext()) {
				Entry<Link, LinkInfo> entry = it.next();
				Link lt = entry.getKey();
				LinkInfo info = entry.getValue();

				// Timeout the unicast and multicast LLDP valid times
				// independently.
				if ((info.getUnicastValidTime() != null)
						&& (info.getUnicastValidTime().getTime()
								+ (this.LINK_TIMEOUT * 1000) < curTime)) {
					info.setUnicastValidTime(null);
					linkChanged = true;
				}
				if ((info.getMulticastValidTime() != null)
						&& (info.getMulticastValidTime().getTime()
								+ (this.LINK_TIMEOUT * 1000) < curTime)) {
					info.setMulticastValidTime(null);
					linkChanged = true;
				}
				// Add to the erase list only if the unicast
				// time is null.
				if (info.getUnicastValidTime() == null
						&& info.getMulticastValidTime() == null) {
					eraseList.add(entry.getKey());
				} else if (linkChanged) {
					updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
							lt.getDst(), lt.getDstPort(),
							getLinkType(lt, info),
							UpdateOperation.LINK_UPDATED));
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

	//******************
	// Internal Helper Methods
	//******************
	@LogMessageDoc(level="WARN",
			message="Could not get list of interfaces of local machine to " +
					"encode in TLV: {detail-msg}",
					explanation="Outgoing LLDP packets encode a unique hash to " +
							"identify the local machine. The list of network " +
							"interfaces is used as input and the controller failed " +
							"to query this list",
							recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	protected void setControllerTLV() {
		// Setting the controllerTLVValue based on current nano time,
		// controller's IP address, and the network interface object hash
		// the corresponding IP address.

		final int prime = 7867;

		byte[] controllerTLVValue = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }; // 8
		// byte
		// value.
		ByteBuffer bb = ByteBuffer.allocate(10);

		long result = System.nanoTime();
		try{
			// Use some data specific to the machine this controller is
			// running on. In this case: the list of network interfaces
			Enumeration<NetworkInterface> ifaces =
					NetworkInterface.getNetworkInterfaces();
			if (ifaces != null) {
				result = result * prime + ifaces.hashCode();
			}
		} catch (SocketException e) {
			log.warn("Could not get list of interfaces of local machine to " +
					"encode in TLV: {}", e.toString());
		}
		// set the first 4 bits to 0.
		result = result & (0x0fffffffffffffffL);

		bb.putLong(result);

		bb.rewind();
		bb.get(controllerTLVValue, 0, 8);

		this.controllerTLV = new LLDPTLV().setType((byte) 0x0c)
				.setLength((short) controllerTLVValue.length)
				.setValue(controllerTLVValue);
	}

	//******************
	// IOFSwitchListener
	//******************
	private void handlePortDown(DatapathId switchId, OFPort portNumber) {
		NodePortTuple npt = new NodePortTuple(switchId, portNumber);
		deleteLinksOnPort(npt, "Port Status Changed");
		LDUpdate update = new LDUpdate(switchId, portNumber,
				UpdateOperation.PORT_DOWN);
		updates.add(update);
	}
	/**
	 * We don't react the port changed notifications here. we listen for
	 * OFPortStatus messages directly. Might consider using this notifier
	 * instead
	 */
	@Override
	public void switchPortChanged(DatapathId switchId,
			OFPortDesc port,
			PortChangeType type) {

		switch (type) {
		case UP:
			processNewPort(switchId, port.getPortNo());
			break;
		case DELETE: case DOWN:
			handlePortDown(switchId, port.getPortNo());
			break;
		case OTHER_UPDATE: case ADD:
			// This is something other than port add or delete.
			// Topology does not worry about this.
			// If for some reason the port features change, which
			// we may have to react.
			break;
		}
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// no-op
		// We don't do anything at switch added, but we do only when the
		// switch is activated.
	}

	@Override
	public void switchRemoved(DatapathId sw) {
        List<Link> eraseList = new ArrayList<Link>();
        lock.writeLock().lock();
        try {
            if (switchLinks.containsKey(sw)) {
                if (log.isTraceEnabled()) {
                    log.trace("Handle switchRemoved. Switch {}; removing links {}", sw.toString(), switchLinks.get(sw));
                }

                List<LDUpdate> updateList = new ArrayList<LDUpdate>();
                updateList.add(new LDUpdate(sw, SwitchType.BASIC_SWITCH, UpdateOperation.SWITCH_REMOVED));
                // add all tuples with an endpoint on this switch to erase list
                eraseList.addAll(switchLinks.get(sw));

                // Sending the updateList, will ensure the updates in this
                // list will be added at the end of all the link updates.
                // Thus, it is not necessary to explicitly add these updates
                // to the queue.
                deleteLinks(eraseList, "Switch Removed", updateList);
            } else {
                // Switch does not have any links.
                updates.add(new LDUpdate(sw, SwitchType.BASIC_SWITCH, UpdateOperation.SWITCH_REMOVED));
            }
        } finally {
            lock.writeLock().unlock();
        }

    }


	@Override
	public void switchActivated(DatapathId switchId) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		if (sw.getEnabledPortNumbers() != null) {
			for (OFPort p : sw.getEnabledPortNumbers()) {
				processNewPort(sw.getId(), p);
			}
		}
		LDUpdate update = new LDUpdate(sw.getId(), SwitchType.BASIC_SWITCH, UpdateOperation.SWITCH_UPDATED);
		updates.add(update);
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// no-op
	}


	//*********************
	//   Storage Listener
	//*********************
	/**
	 * Sets the IStorageSource to use for Topology
	 *
	 * @param storageSource
	 *            the storage source to use
	 */
	public void setStorageSource(IStorageSourceService storageSourceService) {
		this.storageSourceService = storageSourceService;
	}

	/**
	 * Gets the storage source for this ITopology
	 *
	 * @return The IStorageSource ITopology is writing to
	 */
	public IStorageSourceService getStorageSource() {
		return storageSourceService;
	}

	@Override
	public void rowsModified(String tableName, Set<Object> rowKeys) {

        if (tableName.equals(TOPOLOGY_TABLE_NAME)) {
            readTopologyConfigFromStorage();
            return;
        }
    }

	@Override
	public void rowsDeleted(String tableName, Set<Object> rowKeys) {
		// Ignore delete events, the switch delete will do the
		// right thing on it's own.
		readTopologyConfigFromStorage();
	}


	//******************************
	// Internal methods - Config Related
	//******************************

	protected void readTopologyConfigFromStorage() {
		IResultSet topologyResult = storageSourceService.executeQuery(TOPOLOGY_TABLE_NAME,
				null, null,
				null);

		if (topologyResult.next()) {
			boolean apf = topologyResult.getBoolean(TOPOLOGY_AUTOPORTFAST);
			autoPortFastFeature = apf;
		} else {
			this.autoPortFastFeature = AUTOPORTFAST_DEFAULT;
		}

		if (autoPortFastFeature)
			log.info("Setting autoportfast feature to ON");
		else
			log.info("Setting autoportfast feature to OFF");
	}

	/**
	 * Deletes all links from storage
	 */
	void clearAllLinks() {
		storageSourceService.deleteRowsAsync(LINK_TABLE_NAME, null);
	}

	/**
	 * Writes a LinkTuple and corresponding LinkInfo to storage
	 *
	 * @param lt
	 *            The LinkTuple to write
	 * @param linkInfo
	 *            The LinkInfo to write
	 */
	protected void writeLinkToStorage(Link lt, LinkInfo linkInfo) {
		LinkType type = getLinkType(lt, linkInfo);

		// Write only direct links. Do not write links to external
		// L2 network.
		// if (type != LinkType.DIRECT_LINK && type != LinkType.TUNNEL) {
		// return;
		// }

		Map<String, Object> rowValues = new HashMap<String, Object>();
		String id = getLinkId(lt);
		rowValues.put(LINK_ID, id);
		rowValues.put(LINK_VALID_TIME, linkInfo.getUnicastValidTime());
		String srcDpid = lt.getSrc().toString();
		rowValues.put(LINK_SRC_SWITCH, srcDpid);
		rowValues.put(LINK_SRC_PORT, lt.getSrcPort());

		if (type == LinkType.DIRECT_LINK)
			rowValues.put(LINK_TYPE, "internal");
		else if (type == LinkType.MULTIHOP_LINK)
			rowValues.put(LINK_TYPE, "external");
		else if (type == LinkType.TUNNEL)
			rowValues.put(LINK_TYPE, "tunnel");
		else
			rowValues.put(LINK_TYPE, "invalid");

		String dstDpid = lt.getDst().toString();
		rowValues.put(LINK_DST_SWITCH, dstDpid);
		rowValues.put(LINK_DST_PORT, lt.getDstPort());

		storageSourceService.updateRowAsync(LINK_TABLE_NAME, rowValues);
	}

	/**
	 * Removes a link from storage using an asynchronous call.
	 *
	 * @param lt
	 *            The LinkTuple to delete.
	 */
	protected void removeLinkFromStorage(Link lt) {
		String id = getLinkId(lt);
		storageSourceService.deleteRowAsync(LINK_TABLE_NAME, id);
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
			resultSet = storageSourceService.executeQuery(LINK_TABLE_NAME,
					columns,
					new OperatorPredicate(
							LINK_ID,
							OperatorPredicate.Operator.EQ,
							id),
							null);
			if (resultSet.next())
				validTime = resultSet.getLong(LINK_VALID_TIME);
		} finally {
			if (resultSet != null) resultSet.close();
		}
		return validTime;
	}

	/**
	 * Gets the storage key for a LinkTuple
	 *
	 * @param lt
	 *            The LinkTuple to get
	 * @return The storage key as a String
	 */
	private String getLinkId(Link lt) {
		return lt.getSrc().toString() + "-" + lt.getSrcPort()
				+ "-" + lt.getDst().toString() + "-"
				+ lt.getDstPort();
	}

	//***************
	// IFloodlightModule
	//***************

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ILinkDiscoveryService.class);
		// l.add(ITopologyService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
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
		l.add(IRestApiService.class);
		l.add(IShutdownService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		storageSourceService = context.getServiceImpl(IStorageSourceService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		debugCounterService = context.getServiceImpl(IDebugCounterService.class);
		debugEventService = context.getServiceImpl(IDebugEventService.class);
		shutdownService = context.getServiceImpl(IShutdownService.class);

		// read our config options
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			String histSize = configOptions.get("eventhistorysize");
			if (histSize != null) {
				EVENT_HISTORY_SIZE = Short.parseShort(histSize);
			}
		} catch (NumberFormatException e) {
			log.warn("Error event history size, using default of {} seconds", EVENT_HISTORY_SIZE);
		}
		log.debug("Event history size set to {}", EVENT_HISTORY_SIZE);

		// Set the autoportfast feature to false.
		this.autoPortFastFeature = AUTOPORTFAST_DEFAULT;

		// We create this here because there is no ordering guarantee
		this.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
		this.lock = new ReentrantReadWriteLock();
		this.updates = new LinkedBlockingQueue<LDUpdate>();
		this.links = new HashMap<Link, LinkInfo>();
		this.portLinks = new HashMap<NodePortTuple, Set<Link>>();
		this.suppressLinkDiscovery = Collections.synchronizedSet(new HashSet<NodePortTuple>());
		this.switchLinks = new HashMap<DatapathId, Set<Link>>();
		this.quarantineQueue = new LinkedBlockingQueue<NodePortTuple>();
		this.maintenanceQueue = new LinkedBlockingQueue<NodePortTuple>();
		this.toRemoveFromQuarantineQueue = new LinkedBlockingQueue<NodePortTuple>();
		this.toRemoveFromMaintenanceQueue = new LinkedBlockingQueue<NodePortTuple>();

		this.ignoreMACSet = Collections.newSetFromMap(
				new ConcurrentHashMap<MACRange,Boolean>());
		this.haListener = new HAListenerDelegate();
		registerLinkDiscoveryDebugCounters();
		registerLinkDiscoveryDebugEvents();
	}

	@Override
	@LogMessageDocs({
		@LogMessageDoc(level = "ERROR",
				message = "No storage source found.",
				explanation = "Storage source was not initialized; cannot initialize "
						+ "link discovery.",
						recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG),
						@LogMessageDoc(level = "ERROR",
						message = "Error in installing listener for "
								+ "switch config table {table}",
								explanation = "Failed to install storage notification for the "
										+ "switch config table",
										recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG),
										@LogMessageDoc(level = "ERROR",
										message = "No storage source found.",
										explanation = "Storage source was not initialized; cannot initialize "
												+ "link discovery.",
												recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG),
												@LogMessageDoc(level = "ERROR",
												message = "Exception in LLDP send timer.",
												explanation = "An unknown error occured while sending LLDP "
														+ "messages to switches.",
														recommendation = LogMessageDoc.CHECK_SWITCH) })
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

		// Initialize role to floodlight provider role.
		this.role = floodlightProviderService.getRole();

		// Create our storage tables
		if (storageSourceService == null) {
			log.error("No storage source found.");
			return;
		}

		storageSourceService.createTable(TOPOLOGY_TABLE_NAME, null);
		storageSourceService.setTablePrimaryKeyName(TOPOLOGY_TABLE_NAME,
				TOPOLOGY_ID);
		readTopologyConfigFromStorage();

		storageSourceService.createTable(LINK_TABLE_NAME, null);
		storageSourceService.setTablePrimaryKeyName(LINK_TABLE_NAME, LINK_ID);
		storageSourceService.deleteMatchingRows(LINK_TABLE_NAME, null);
		// Register for storage updates for the switch table
		try {
			storageSourceService.addListener(SWITCH_CONFIG_TABLE_NAME, this);
			storageSourceService.addListener(TOPOLOGY_TABLE_NAME, this);
		} catch (StorageException ex) {
			log.error("Error in installing listener for "
					+ "switch table {}", SWITCH_CONFIG_TABLE_NAME);
		}

		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();

		// To be started by the first switch connection
		discoveryTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() {
				try {
					discoverLinks();
				} catch (StorageException e) {
					shutdownService.terminate("Storage exception in LLDP send timer. Terminating process " + e, 0);
				} catch (Exception e) {
					log.error("Exception in LLDP send timer.", e);
				} finally {
					if (!shuttingDown) {
						// null role implies HA mode is not enabled.
						if (role == null || role == HARole.ACTIVE) {
							log.trace("Rescheduling discovery task as role = {}",
									role);
							discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL,
									TimeUnit.SECONDS);
						} else {
							log.trace("Stopped LLDP rescheduling due to role = {}.",
									role);
						}
					}
				}
			}
		});

		// null role implies HA mode is not enabled.
		if (role == null || role == HARole.ACTIVE) {
			log.trace("Setup: Rescheduling discovery task. role = {}", role);
			discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL,
					TimeUnit.SECONDS);
		} else {
			log.trace("Setup: Not scheduling LLDP as role = {}.", role);
		}

		// Setup the BDDP task. It is invoked whenever switch port tuples
		// are added to the quarantine list.
		bddpTask = new SingletonTask(ses, new QuarantineWorker());
		bddpTask.reschedule(BDDP_TASK_INTERVAL, TimeUnit.MILLISECONDS);

		updatesThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						doUpdatesThread();
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}, "Topology Updates");
		updatesThread.start();

		// Register for the OpenFlow messages we want to receive
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProviderService.addOFMessageListener(OFType.PORT_STATUS, this);
		// Register for switch updates
		switchService.addOFSwitchListener(this);
		floodlightProviderService.addHAListener(this.haListener);
		floodlightProviderService.addInfoProvider("summary", this);
		if (restApiService != null)
			restApiService.addRestletRoutable(new LinkDiscoveryWebRoutable());
		setControllerTLV();
	}

	// ****************************************************
	// Link Discovery DebugCounters and DebugEvents
	// ****************************************************

	private void registerLinkDiscoveryDebugCounters() throws FloodlightModuleException {
		if (debugCounterService == null) {
			log.error("Debug Counter Service not found.");
		}
		debugCounterService.registerModule(PACKAGE);
		ctrIncoming = debugCounterService.registerCounter(PACKAGE, "incoming",
				"All incoming packets seen by this module");
		ctrLldpEol  = debugCounterService.registerCounter(PACKAGE, "lldp-eol",
				"End of Life for LLDP packets");
		ctrLinkLocalDrops = debugCounterService.registerCounter(PACKAGE, "linklocal-drops",
				"All link local packets dropped by this module");
		ctrIgnoreSrcMacDrops = debugCounterService.registerCounter(PACKAGE, "ignore-srcmac-drops",
				"All packets whose srcmac is configured to be dropped by this module");
		ctrQuarantineDrops = debugCounterService.registerCounter(PACKAGE, "quarantine-drops",
				"All packets arriving on quarantined ports dropped by this module", IDebugCounterService.MetaData.WARN);
	}

	private void registerLinkDiscoveryDebugEvents() throws FloodlightModuleException {
		if (debugEventService == null) {
			log.error("Debug Event Service not found.");
		}

		eventCategory = debugEventService.buildEvent(DirectLinkEvent.class)
				.setModuleName(PACKAGE)
				.setEventName("linkevent")
				.setEventDescription("Direct OpenFlow links discovered or timed-out")
				.setEventType(EventType.ALWAYS_LOG)
				.setBufferCapacity(100)
				.register();
	}

	public class DirectLinkEvent {
		@EventColumn(name = "srcSw", description = EventFieldType.DPID)
		DatapathId srcDpid;

		@EventColumn(name = "srcPort", description = EventFieldType.PRIMITIVE)
		OFPort srcPort;

		@EventColumn(name = "dstSw", description = EventFieldType.DPID)
		DatapathId dstDpid;

		@EventColumn(name = "dstPort", description = EventFieldType.PRIMITIVE)
		OFPort dstPort;

		@EventColumn(name = "reason", description = EventFieldType.STRING)
		String reason;

		public DirectLinkEvent(DatapathId srcDpid, OFPort srcPort, DatapathId dstDpid,
				OFPort dstPort, String reason) {
			this.srcDpid = srcDpid;
			this.srcPort = srcPort;
			this.dstDpid = dstDpid;
			this.dstPort = dstPort;
			this.reason = reason;
		}
	}


	//*********************
	//  IInfoProvider
	//*********************

	@Override
	public Map<String, Object> getInfo(String type) {
		if (!"summary".equals(type)) return null;

		Map<String, Object> info = new HashMap<String, Object>();

		int numDirectLinks = 0;
		for (Set<Link> links : switchLinks.values()) {
			for (Link link : links) {
				LinkInfo linkInfo = this.getLinkInfo(link);
				if (linkInfo != null &&
						linkInfo.getLinkType() == LinkType.DIRECT_LINK) {
					numDirectLinks++;
				}
			}
		}
		info.put("# inter-switch links", numDirectLinks / 2);
		info.put("# quarantine ports", quarantineQueue.size());
		return info;
	}

	//***************
	// IHAListener
	//***************

	private class HAListenerDelegate implements IHAListener {
		@Override
		public void transitionToActive() {
			if (log.isTraceEnabled()) {
				log.trace("Sending LLDPs "
						+ "to HA change from STANDBY->MASTER");
			}
			LinkDiscoveryManager.this.role = HARole.ACTIVE;
			clearAllLinks();
			readTopologyConfigFromStorage();
			log.debug("Role Change to Master: Rescheduling discovery task.");
			discoveryTask.reschedule(1, TimeUnit.MICROSECONDS);
		}

		@Override
		public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
				Map<String, String> addedControllerNodeIPs,
				Map<String, String> removedControllerNodeIPs) {
			// ignore
		}

		@Override
		public String getName() {
			return LinkDiscoveryManager.this.getName();
		}

		@Override
		public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
				String name) {
			return ("topology".equals(name));
		}

		@Override
		public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
				String name) {
			return "tunnelmanager".equals(name);
		}

		@Override
		public void transitionToStandby() {
			//no-op
		}
	}

}
