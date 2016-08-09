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

package net.floodlightcontroller.forwarding;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.IPv6;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingDecision.RoutingAction;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.forwarding.Forwarding;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

import com.google.common.collect.ImmutableList;

public class ForwardingTest extends FloodlightTestCase {
	protected FloodlightContext cntx;
	protected MockDeviceManager deviceManager;
	protected IRoutingService routingEngine;
	protected Forwarding forwarding;
	protected ITopologyService topology;
	protected LinkDiscoveryManager linkService;
	protected MockThreadPoolService threadPool;
	protected IOFSwitch sw1, sw2;
	protected OFFeaturesReply swFeatures;
	protected OFDescStatsReply swDescription;
	protected IDevice srcDevice, dstDevice1, dstDevice2; /* reuse for IPv4 and IPv6 */
	protected OFPacketIn packetIn;
	protected OFPacketIn packetInIPv6;
	protected OFPacketOut packetOut;
	protected OFPacketOut packetOutIPv6;
	protected OFPacketOut packetOutFlooded;
	protected OFPacketOut packetOutFloodedIPv6;
	protected IPacket testPacket;
	protected IPacket testPacketIPv6;
	protected byte[] testPacketSerialized;
	protected byte[] testPacketSerializedIPv6;
	protected int expected_wildcards;
	protected Date currentDate;
	private MockSyncService mockSyncService;
	private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);


	@Override
	public void setUp() throws Exception {
		super.setUp();

		cntx = new FloodlightContext();

		// Module loader setup
		mockFloodlightProvider = getMockFloodlightProvider();
		forwarding = new Forwarding();
		threadPool = new MockThreadPoolService();
		deviceManager = new MockDeviceManager();
		routingEngine = createMock(IRoutingService.class);
		topology = createMock(ITopologyService.class);
		mockSyncService = new MockSyncService();
		linkService = new LinkDiscoveryManager();
		DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();

		FloodlightModuleContext fmc = new FloodlightModuleContext();
		fmc.addService(IFloodlightProviderService.class,
				mockFloodlightProvider);
		fmc.addService(IThreadPoolService.class, threadPool);
		fmc.addService(ITopologyService.class, topology);
		fmc.addService(IRoutingService.class, routingEngine);
		fmc.addService(IDeviceService.class, deviceManager);
		fmc.addService(IEntityClassifierService.class, entityClassifier);
		fmc.addService(ISyncService.class, mockSyncService);
		fmc.addService(IDebugCounterService.class, new MockDebugCounterService());
		fmc.addService(IOFSwitchService.class, getMockSwitchService());
		fmc.addService(ILinkDiscoveryService.class, linkService);

		topology.addListener(anyObject(ITopologyListener.class));
		expectLastCall().anyTimes();
		expect(topology.isBroadcastAllowed(anyObject(DatapathId.class), anyObject(OFPort.class))).andReturn(true).anyTimes();
		replay(topology);

		threadPool.init(fmc);
		mockSyncService.init(fmc);
		linkService.init(fmc);
		deviceManager.init(fmc);
		forwarding.init(fmc);
		entityClassifier.init(fmc);
		threadPool.startUp(fmc);
		mockSyncService.startUp(fmc);
		linkService.startUp(fmc);
		deviceManager.startUp(fmc);
		forwarding.startUp(fmc);
		Forwarding.flowSetIdRegistry.seedFlowSetIdForUnitTest(3);
		entityClassifier.startUp(fmc);
		verify(topology);

		swDescription = factory.buildDescStatsReply().build();
		swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
		// Mock switches
		sw1 = EasyMock.createMock(IOFSwitch.class);
		expect(sw1.getId()).andReturn(DatapathId.of(1L)).anyTimes();
		expect(sw1.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw1.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();
		

		sw2 = EasyMock.createMock(IOFSwitch.class);
		expect(sw2.getId()).andReturn(DatapathId.of(2L)).anyTimes();
		expect(sw2.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw2.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

		expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();

		expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
		
		expect(sw1.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
		expect(sw2.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
		
		expect(sw1.isActive()).andReturn(true).anyTimes();
		expect(sw2.isActive()).andReturn(true).anyTimes();

		// Load the switch map
		Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
		switches.put(DatapathId.of(1L), sw1);
		switches.put(DatapathId.of(2L), sw2);
		getMockSwitchService().setSwitches(switches);

		// Build test packet
		testPacket = new Ethernet()
		.setDestinationMACAddress("00:11:22:33:44:55")
		.setSourceMACAddress("00:44:33:22:11:00")
		.setEtherType(EthType.IPv4)
		.setPayload(
				new IPv4()
				.setTtl((byte) 128)
				.setSourceAddress("192.168.1.1")
				.setDestinationAddress("192.168.1.2")
				.setPayload(new UDP()
				.setSourcePort((short) 5000)
				.setDestinationPort((short) 5001)
				.setPayload(new Data(new byte[] {0x01}))));

		testPacketIPv6 = new Ethernet()
		.setDestinationMACAddress("00:11:22:33:44:55")
		.setSourceMACAddress("00:44:33:22:11:00")
		.setEtherType(EthType.IPv6)
		.setPayload(
				new IPv6()
				.setHopLimit((byte) 128)
				.setSourceAddress(IPv6Address.of(1, 1))
				.setDestinationAddress(IPv6Address.of(2, 2))
				.setNextHeader(IpProtocol.UDP)
				.setPayload(new UDP()
				.setSourcePort((short) 5000)
				.setDestinationPort((short) 5001)
				.setPayload(new Data(new byte[] {0x01}))));

		currentDate = new Date();

		// Mock Packet-in
		testPacketSerialized = testPacket.serialize();
		testPacketSerializedIPv6 = testPacketIPv6.serialize();
		
		packetIn = factory.buildPacketIn()
				.setMatch(factory.buildMatch()
						.setExact(MatchField.IN_PORT, OFPort.of(1))
						.setExact(MatchField.ETH_SRC, MacAddress.of("00:44:33:22:11:00"))
						.setExact(MatchField.ETH_DST, MacAddress.of("00:11:22:33:44:55"))
						.setExact(MatchField.ETH_TYPE, EthType.IPv4)
						.setExact(MatchField.IPV4_SRC, IPv4Address.of("192.168.1.1"))
						.setExact(MatchField.IPV4_DST, IPv4Address.of("192.168.1.2"))
						.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
						.setExact(MatchField.UDP_SRC, TransportPort.of(5000))
						.setExact(MatchField.UDP_DST, TransportPort.of(5001))
						.build())
						.setBufferId(OFBufferId.NO_BUFFER)
						.setData(testPacketSerialized)
						.setReason(OFPacketInReason.NO_MATCH)
						.build();		
		packetInIPv6 = factory.buildPacketIn()
				.setMatch(factory.buildMatch()
						.setExact(MatchField.IN_PORT, OFPort.of(1))
						.setExact(MatchField.ETH_SRC, MacAddress.of("00:44:33:22:11:00"))
						.setExact(MatchField.ETH_DST, MacAddress.of("00:11:22:33:44:55"))
						.setExact(MatchField.ETH_TYPE, EthType.IPv6)
						.setExact(MatchField.IPV6_SRC, IPv6Address.of(1, 1))
						.setExact(MatchField.IPV6_DST, IPv6Address.of(2, 2))
						.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
						.setExact(MatchField.UDP_SRC, TransportPort.of(5000))
						.setExact(MatchField.UDP_DST, TransportPort.of(5001))
						.build())
						.setBufferId(OFBufferId.NO_BUFFER)
						.setData(testPacketSerializedIPv6)
						.setReason(OFPacketInReason.NO_MATCH)
						.build();

		// Mock Packet-out
		List<OFAction> poactions = new ArrayList<OFAction>();
		poactions.add(factory.actions().output(OFPort.of(3), Integer.MAX_VALUE));
		packetOut = factory.buildPacketOut()
				.setBufferId(this.packetIn.getBufferId())
				.setActions(poactions)
				.setInPort(OFPort.of(1))
				.setData(testPacketSerialized)
				.setXid(15)
				.build();
		packetOutIPv6 = factory.buildPacketOut()
				.setBufferId(this.packetInIPv6.getBufferId())
				.setActions(poactions)
				.setInPort(OFPort.of(1))
				.setData(testPacketSerializedIPv6)
				.setXid(15)
				.build();

		// Mock Packet-out with OFPP_FLOOD action (list of ports to flood)
		poactions = new ArrayList<OFAction>();
		poactions.add(factory.actions().output(OFPort.of(10), Integer.MAX_VALUE));
		packetOutFlooded = factory.buildPacketOut()
				.setBufferId(this.packetIn.getBufferId())
				.setInPort(packetIn.getMatch().get(MatchField.IN_PORT))
				.setXid(17)
				.setActions(poactions)
				.setData(testPacketSerialized)
				.build();
		packetOutFloodedIPv6 = factory.buildPacketOut()
				.setBufferId(this.packetInIPv6.getBufferId())
				.setInPort(packetInIPv6.getMatch().get(MatchField.IN_PORT))
				.setXid(17)
				.setActions(poactions)
				.setData(testPacketSerializedIPv6)
				.build();
	}
	
	void removeDeviceFromContext() {
		IFloodlightProviderService.bcStore.
		remove(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IFloodlightProviderService.bcStore.
		remove(cntx,
				IDeviceService.CONTEXT_SRC_DEVICE);
		IFloodlightProviderService.bcStore.
		remove(cntx,
				IDeviceService.CONTEXT_DST_DEVICE);
	}

	static boolean messageListsEqualIgnoreXid(List<OFMessage> c1, List<OFMessage> c2) {
		if (c1 == c2) {
			return true;
		}

		if (c1 == null || c2 == null) {
			return false;
		}

		if (c1.size() != c2.size()) {
			return false;
		}

		Iterator<OFMessage> it1 = c1.iterator();
		Iterator<OFMessage> it2 = c2.iterator();
		while (it1.hasNext()) {
			OFMessage m1 = it1.next();
			OFMessage m2 = it2.next();
			if (m1 == m2) {
				continue;
			}

			if (m1 == null || m2 == null || !m1.equalsIgnoreXid(m2)) {
				return false;
			}
		}

		return true;
	}

	enum DestDeviceToLearn { NONE, DEVICE1 ,DEVICE2 };
	public void learnDevices(DestDeviceToLearn destDeviceToLearn) {
		// Build src and dest devices
		MacAddress dataLayerSource = ((Ethernet)testPacket).getSourceMACAddress();
		MacAddress dataLayerDest =
				((Ethernet)testPacket).getDestinationMACAddress();
		IPv4Address networkSource =
				((IPv4)((Ethernet)testPacket).getPayload()).
				getSourceAddress();
		IPv4Address networkDest =
				((IPv4)((Ethernet)testPacket).getPayload()).
				getDestinationAddress();

		reset(topology);
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1)))
		.andReturn(true)
		.anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(3)))
		.andReturn(true)
		.anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3)))
		.andReturn(true)
		.anyTimes();
		replay(topology);

		srcDevice =
				deviceManager.learnEntity(dataLayerSource, VlanVid.ZERO, 
						networkSource, IPv6Address.NONE,
						DatapathId.of(1), OFPort.of(1));
		IDeviceService.fcStore. put(cntx,
				IDeviceService.CONTEXT_SRC_DEVICE,
				srcDevice);
		if (destDeviceToLearn == DestDeviceToLearn.DEVICE1) {
			dstDevice1 =
					deviceManager.learnEntity(dataLayerDest, VlanVid.ZERO, 
							networkDest, IPv6Address.NONE,
							DatapathId.of(2), OFPort.of(3));
			IDeviceService.fcStore.put(cntx,
					IDeviceService.CONTEXT_DST_DEVICE,
					dstDevice1);
		}
		if (destDeviceToLearn == DestDeviceToLearn.DEVICE2) {
			dstDevice2 =
					deviceManager.learnEntity(dataLayerDest, VlanVid.ZERO, 
							networkDest, IPv6Address.NONE,
							DatapathId.of(1), OFPort.of(3));
			IDeviceService.fcStore.put(cntx,
					IDeviceService.CONTEXT_DST_DEVICE,
					dstDevice2);
		}
		verify(topology);
		
		IFloodlightProviderService.bcStore.
		put(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
				(Ethernet)testPacket);
	}
	
	public void learnDevicesIPv6(DestDeviceToLearn destDeviceToLearn) {
		// Build src and dest devices
		MacAddress dataLayerSource = ((Ethernet)testPacketIPv6).getSourceMACAddress();
		MacAddress dataLayerDest =
				((Ethernet)testPacketIPv6).getDestinationMACAddress();
		IPv6Address networkSource =
				((IPv6)((Ethernet)testPacketIPv6).getPayload()).
				getSourceAddress();
		IPv6Address networkDest =
				((IPv6)((Ethernet)testPacketIPv6).getPayload()).
				getDestinationAddress();

		reset(topology);
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1)))
		.andReturn(true)
		.anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(3)))
		.andReturn(true)
		.anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3)))
		.andReturn(true)
		.anyTimes();
		replay(topology);

		srcDevice =
				deviceManager.learnEntity(dataLayerSource, VlanVid.ZERO, 
						IPv4Address.NONE, networkSource,
						DatapathId.of(1), OFPort.of(1));
		IDeviceService.fcStore.put(cntx,
				IDeviceService.CONTEXT_SRC_DEVICE,
				srcDevice);
		if (destDeviceToLearn == DestDeviceToLearn.DEVICE1) {
			dstDevice1 =
					deviceManager.learnEntity(dataLayerDest, VlanVid.ZERO, 
							IPv4Address.NONE, networkDest,
							DatapathId.of(2), OFPort.of(3));
			IDeviceService.fcStore.put(cntx,
					IDeviceService.CONTEXT_DST_DEVICE,
					dstDevice1);
		}
		if (destDeviceToLearn == DestDeviceToLearn.DEVICE2) {
			dstDevice2 =
					deviceManager.learnEntity(dataLayerDest, VlanVid.ZERO, 
							 IPv4Address.NONE, networkDest,
							DatapathId.of(1), OFPort.of(3));
			IDeviceService.fcStore.put(cntx,
					IDeviceService.CONTEXT_DST_DEVICE,
					dstDevice2);
		}
		verify(topology);
		
		IFloodlightProviderService.bcStore.
		put(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
				(Ethernet)testPacketIPv6);
	}

	@Test
	public void testForwardMultiSwitchPath() throws Exception {
		learnDevices(DestDeviceToLearn.DEVICE1);

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<OFMessage> wc2 = EasyMock.newCapture(CaptureType.ALL);

		Path path = new Path(DatapathId.of(1L), DatapathId.of(2L));
		List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
		nptList.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		nptList.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		nptList.add(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
		nptList.add(new NodePortTuple(DatapathId.of(2L), OFPort.of(3)));
		path.setPath(nptList);
		reset(routingEngine);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(2L), OFPort.of(3))).andReturn(path).atLeastOnce();
		
		// Expected Flow-mods
		Match match = packetIn.getMatch();
		OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);

		OFFlowMod fm1 = factory.buildFlowAdd()
				.setIdleTimeout((short)5)
				.setMatch(match)
				.setActions(actions)
				.setOutPort(action.getPort())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie(U64.of(2L << 52).or(U64.of(4 << Forwarding.FLOWSET_SHIFT)))
				.setPriority(1)
				.build();
		OFFlowMod fm2 = fm1.createBuilder().build();

		expect(sw1.write(capture(wc1))).andReturn(true).anyTimes();
		expect(sw2.write(capture(wc2))).andReturn(true).anyTimes();

		reset(topology);
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.getClusterId(DatapathId.of(2L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L),  OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(2L), OFPort.of(3))).andReturn(true).anyTimes();

		// Reset mocks, trigger the packet in, and validate results
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetIn, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
		assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

		List<OFMessage> msglist = wc1.getValues();

		for (OFMessage m: msglist) {
			if (m instanceof OFFlowMod)
				assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(fm1), OFMessageUtils.OFMessageIgnoreXid.of(m));
			else if (m instanceof OFPacketOut) {
                assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(packetOut), OFMessageUtils.OFMessageIgnoreXid.of(m));
			}
		}

		OFMessage m = wc2.getValue();
		assert (m instanceof OFFlowMod);
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(m), OFMessageUtils.OFMessageIgnoreXid.of(fm2));
		
		removeDeviceFromContext();
	}
	
	@Test
	public void testForwardMultiSwitchPathIPv6() throws Exception {
		learnDevicesIPv6(DestDeviceToLearn.DEVICE1);

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<OFMessage> wc2 = EasyMock.newCapture(CaptureType.ALL);

		Path route = new Path(DatapathId.of(1L), DatapathId.of(2L));
		List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
		nptList.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		nptList.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		nptList.add(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
		nptList.add(new NodePortTuple(DatapathId.of(2L), OFPort.of(3)));
		route.setPath(nptList);
		reset(routingEngine);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(2L), OFPort.of(3))).andReturn(route).atLeastOnce();

		// Expected Flow-mods
		Match match = packetInIPv6.getMatch();
		OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);

		OFFlowMod fm1 = factory.buildFlowAdd()
				.setIdleTimeout((short)5)
				.setMatch(match)
				.setActions(actions)
				.setOutPort(action.getPort())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie(U64.of(2L << 52).or(U64.of(4 << Forwarding.FLOWSET_SHIFT)))
				.setPriority(1)
				.build();
		OFFlowMod fm2 = fm1.createBuilder().build();

		expect(sw1.write(capture(wc1))).andReturn(true).anyTimes();
		expect(sw2.write(capture(wc2))).andReturn(true).anyTimes();

		reset(topology);
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.getClusterId(DatapathId.of(2L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L),  OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(2L), OFPort.of(3))).andReturn(true).anyTimes();
		
		// Reset mocks, trigger the packet in, and validate results
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetInIPv6, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
		assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

		List<OFMessage> msglist = wc1.getValues();

		for (OFMessage m: msglist) {
			if (m instanceof OFFlowMod)
                assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(fm1), OFMessageUtils.OFMessageIgnoreXid.of(m));
			else if (m instanceof OFPacketOut) {
                assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(packetOutIPv6), OFMessageUtils.OFMessageIgnoreXid.of(m));
			}
		}

		OFMessage m = wc2.getValue();
		assert (m instanceof OFFlowMod);
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(m), OFMessageUtils.OFMessageIgnoreXid.of(fm2));
		
		removeDeviceFromContext();
	}

	@Test
	public void testForwardSingleSwitchPath() throws Exception {
		learnDevices(DestDeviceToLearn.DEVICE2);

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<OFMessage> wc2 = EasyMock.newCapture(CaptureType.ALL);

		Path route = new  Path(DatapathId.of(1L), DatapathId.of(1L));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(route).atLeastOnce();

		// Expected Flow-mods
		Match match = packetIn.getMatch();
		OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);
		
		//routingEngine.addRoutingDecisionChangedListener(anyObject(IRoutingDecisionChangedListener.class));

		OFFlowMod fm1 = factory.buildFlowAdd()
				.setIdleTimeout((short)5)
				.setMatch(match)
				.setActions(actions)
				.setOutPort(OFPort.of(3))
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie(U64.of(2L << 52).or(U64.of(4 << Forwarding.FLOWSET_SHIFT)))
				.setPriority(1)
				.build();

		// Record expected packet-outs/flow-mods
		expect(sw1.write(capture(wc1))).andReturn(true).once();
		expect(sw1.write(capture(wc2))).andReturn(true).once();

		reset(topology);
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();

		// Reset mocks, trigger the packet in, and validate results
		reset(routingEngine);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(route).atLeastOnce();
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetIn, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());
		assertTrue(wc2.hasCaptured());

        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(wc1.getValue()), OFMessageUtils.OFMessageIgnoreXid.of(fm1));
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(wc2.getValue()), OFMessageUtils.OFMessageIgnoreXid.of(packetOut));
		
		removeDeviceFromContext();
	}
	
	@Test
	public void testForwardSingleSwitchPathIPv6() throws Exception {
		learnDevicesIPv6(DestDeviceToLearn.DEVICE2);

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<OFMessage> wc2 = EasyMock.newCapture(CaptureType.ALL);

		Path route = new  Path(DatapathId.of(1L), DatapathId.of(1L));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		reset(routingEngine);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(route).atLeastOnce();
		
		// Expected Flow-mods
		Match match = packetInIPv6.getMatch();
		OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);

		OFFlowMod fm1 = factory.buildFlowAdd()
				.setIdleTimeout((short)5)
				.setMatch(match)
				.setActions(actions)
				.setOutPort(OFPort.of(3))
				.setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(U64.of(2L << 52).or(U64.of(4 << Forwarding.FLOWSET_SHIFT)))
				.setPriority(1)
				.build();

		// Record expected packet-outs/flow-mods
		expect(sw1.write(capture(wc1))).andReturn(true).once();
		expect(sw1.write(capture(wc2))).andReturn(true).once();

		reset(topology);
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();
		
		// Reset mocks, trigger the packet in, and validate results
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetInIPv6, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());
		assertTrue(wc2.hasCaptured());

        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(wc1.getValue()), OFMessageUtils.OFMessageIgnoreXid.of(fm1));
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(wc2.getValue()), OFMessageUtils.OFMessageIgnoreXid.of(packetOutIPv6));
		
		removeDeviceFromContext();
	}

	/*TODO OFMessageDamper broken due to XID variability in OFMessages... need to fix @Test */
	/*TODO make an IPv6 test for this once OFMessageDamper fixed */
	public void testFlowModDampening() throws Exception {
		learnDevices(DestDeviceToLearn.DEVICE2);

		reset(topology);
		expect(topology.isAttachmentPointPort(DatapathId.of(anyLong()), OFPort.of(anyShort())))
		.andReturn(true).anyTimes();
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		replay(topology);


		Path route = new  Path(DatapathId.of(1L), DatapathId.of(1L));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(route).atLeastOnce();

		// Expected Flow-mods
		Match match = packetIn.getMatch();
		OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);

		OFFlowMod fm1 = factory.buildFlowAdd()
				.setIdleTimeout((short)5)
				.setMatch(match)
				.setActions(actions)
				.setOutPort(OFPort.of(3))
				.setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(U64.of(2L << 52).or(U64.of(6 << Forwarding.FLOWSET_SHIFT)))
				.setXid(anyLong())
				.build();

		// Record expected packet-outs/flow-mods
		// We will inject the packet_in 3 times and expect 1 flow mod and
		// 3 packet outs due to flow mod dampening
		expect(sw1.write(fm1)).andReturn(true).once();
		// Update new expected XID
		expect(sw1.write(packetOut.createBuilder().setXid(anyLong()).build())).andReturn(true).times(3);

		reset(topology);
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyInt()))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(anyLong()), OFPort.of(anyInt()))).andReturn(true).anyTimes();
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(3))).andReturn(true).anyTimes();

		// Reset mocks, trigger the packet in, and validate results
		replay(sw1, routingEngine, topology);
		forwarding.receive(sw1, this.packetIn, cntx);
		forwarding.receive(sw1, this.packetIn, cntx);
		forwarding.receive(sw1, this.packetIn, cntx);
		verify(sw1, routingEngine);
		
		removeDeviceFromContext();
	}

	@Test
	public void testForwardNoPath() throws Exception {
		learnDevices(DestDeviceToLearn.NONE);

		// Set no destination attachment point or route
		// expect no Flow-mod but expect the packet to be flooded
		reset(routingEngine);

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		
		Set<OFPort> bcastPorts = new HashSet<OFPort>();
		bcastPorts.add(OFPort.of(10));

		// Reset mocks, trigger the packet in, and validate results
		reset(topology);
		expect(topology.getSwitchBroadcastPorts(DatapathId.of(1L))).andReturn(bcastPorts).once();
		expect(topology.isAttachmentPointPort(DatapathId.of(anyLong()),
				OFPort.of(anyShort())))
				.andReturn(true)
				.anyTimes();
		expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();
		expect(sw1.write(capture(wc1))).andReturn(true).once();
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetIn, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(wc1.getValue()), OFMessageUtils.OFMessageIgnoreXid.of(packetOutFlooded));
		
		removeDeviceFromContext();
	}

	@Test
	public void testForwardNoPathIPv6() throws Exception {
		learnDevicesIPv6(DestDeviceToLearn.NONE);
		
		reset(routingEngine);

		// Set no destination attachment point or route
		// expect no Flow-mod but expect the packet to be flooded

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		
		Set<OFPort> bcastPorts = new HashSet<OFPort>();
		bcastPorts.add(OFPort.of(10));

		// Reset mocks, trigger the packet in, and validate results
		reset(topology);
		expect(topology.getSwitchBroadcastPorts(DatapathId.of(1L))).andReturn(bcastPorts).once();
		expect(topology.isAttachmentPointPort(DatapathId.of(anyLong()),
				OFPort.of(anyShort())))
				.andReturn(true)
				.anyTimes();
		expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD))
		.andReturn(true).anyTimes();
		// Reset XID to expected (dependent on prior unit tests)
		expect(sw1.write(capture(wc1))).andReturn(true).once();
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetInIPv6, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(wc1.getValue()), OFMessageUtils.OFMessageIgnoreXid.of(packetOutFloodedIPv6));
		
		removeDeviceFromContext();
	}
	
	/*
	 * TODO Consider adding test cases for other Decision != null paths (I only added FORWARD and none of the paths had test cases)
	 */
	@Test
	public void testForwardDecisionForwardingCookieZero() throws Exception {
		learnDevices(DestDeviceToLearn.DEVICE2);
		
		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<OFMessage> wc2 = EasyMock.newCapture(CaptureType.ALL);

		Path path = new Path(DatapathId.of(1L), DatapathId.of(1L));
		path.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		path.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		reset(routingEngine);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(path).atLeastOnce();
		
		// Expected Flow-mods
		Match match = packetIn.getMatch();
		OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);
		
		RoutingDecision decision = new RoutingDecision(DatapathId.of(1L), OFPort.of(1), dstDevice1, RoutingAction.FORWARD);
		decision.setDescriptor(U64.ZERO);
		decision.addToContext(cntx);
		
		OFFlowMod fm1 = factory.buildFlowAdd()
				.setIdleTimeout((short)5)
				.setMatch(match)
				.setActions(actions)
				.setOutPort(OFPort.of(3))
				.setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(U64.of(2L << 52).or(U64.of(4 << Forwarding.FLOWSET_SHIFT)))
				.setPriority(1)
				.build();

		// Record expected packet-outs/flow-mods
		expect(sw1.write(capture(wc1))).andReturn(true).once();
		expect(sw1.write(capture(wc2))).andReturn(true).once();

		reset(topology);
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();

		// Reset mocks, trigger the packet in, and validate results
		replay(sw1, sw2, routingEngine, topology);
		forwarding.receive(sw1, this.packetIn, cntx);
		verify(sw1, sw2, routingEngine);

		assertTrue(wc1.hasCaptured());
		assertTrue(wc2.hasCaptured());

		assertTrue(wc1.getValue().equalsIgnoreXid(fm1));
		assertTrue(wc2.getValue().equalsIgnoreXid(packetOut));
	}
	
	@Test
    public void testForwardDecisionForwardingCookieNotZero() throws Exception {
        learnDevices(DestDeviceToLearn.DEVICE2);
        
        Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
        Capture<OFMessage> wc2 = EasyMock.newCapture(CaptureType.ALL);

        Path path = new Path(DatapathId.of(1L), DatapathId.of(1L));
        path.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        path.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
        reset(routingEngine);
        expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(path).atLeastOnce();
        
        // Expected Flow-mods
        Match match = packetIn.getMatch();
        OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);
        
        RoutingDecision decision = new RoutingDecision(DatapathId.of(1L), OFPort.of(1), dstDevice1, RoutingAction.FORWARD);
        decision.setDescriptor(U64.of(0x00000000ffffffffL));
        decision.addToContext(cntx);
        //RoutingDecision de2 = (RoutingDecision) RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION); // Same as decision
        //(DatapathId swDipd, OFPort inPort, IDevice srcDevice, RoutingAction action);
        
        OFFlowMod fm1 = factory.buildFlowAdd()
                .setIdleTimeout((short)5)
                .setMatch(match)
                .setActions(actions)
                .setOutPort(OFPort.of(3))
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(U64.of(2L << 52).or(U64.of(4 << Forwarding.FLOWSET_SHIFT).or(U64.of(0xFFffFFL))))
                .setPriority(1)
                .build();

        // Record expected packet-outs/flow-mods
        expect(sw1.write(capture(wc1))).andReturn(true).once();
        expect(sw1.write(capture(wc2))).andReturn(true).once();

        reset(topology);
        expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
        expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();
        expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
        expect(topology.isEdge(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        
        assertTrue(wc1.getValue().equalsIgnoreXid(fm1));
        assertTrue(wc2.getValue().equalsIgnoreXid(packetOut));
    }

	@Test
	public void testForwardDeleteFlowsByDescriptorSingle() throws Exception {
		reset(routingEngine);
		
		Capture<List<OFMessage>> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<List<OFMessage>> wc2 = EasyMock.newCapture(CaptureType.ALL);
		
		List<Masked<U64>> descriptors = new ArrayList<Masked<U64>>();
		descriptors.add(Masked.of(
				U64.of(0x00000000FFffFFffL),
				U64.of(0x00200000FFffFFffL))); // User mask = 0xffFFffFFL which is forwarding.DECISION_MASK/AppCookie.USER_MASK//descriptors.add(Masked.of(U64.of(0x00000000FFffFFffL),U64.of(0x0020000000000000L)));//descriptors.add(Masked.of(U64.of(0xffFFffFFffFFffFFL),U64.of(0x00200000FFffFFffL))); // Mask = 0xffFFffFFffFFffFFL which is the value returned by forwarding.AppCookie.getAppFieldMask()//descriptors.add(Masked.of(U64.of(0xffFFffFFffFFffFFL),U64.of(0x0020000000000000L)));
		
		expect(sw1.getStatus()).andReturn(IOFSwitch.SwitchStatus.MASTER).anyTimes();
		expect(sw2.getStatus()).andReturn(IOFSwitch.SwitchStatus.MASTER).anyTimes();
		
		expect(sw1.write(capture(wc1))).andReturn(ImmutableList.of()).once();
		expect(sw2.write(capture(wc2))).andReturn(ImmutableList.of()).once();
		
		replay(sw1, sw2, routingEngine);
		forwarding.deleteFlowsByDescriptor(descriptors);
		verify(sw1, sw2, routingEngine);
		
		assertTrue(wc1.hasCaptured());
		assertTrue(wc2.hasCaptured());
		
		Masked<U64> masked_cookie = Masked.of(
				AppCookie.makeCookie(Forwarding.FORWARDING_APP_ID, (int)4294967295L),
				AppCookie.getAppFieldMask().or(U64.of(0xffffffL)));
		List<OFMessage> msgs_test = new ArrayList<>();
		msgs_test.add( 	factory.buildFlowDelete()
						.setCookie(masked_cookie.getValue())
						.setCookieMask(masked_cookie.getMask())
						.build());
		
		assertTrue(messageListsEqualIgnoreXid(wc1.getValue(), msgs_test));
		assertTrue(messageListsEqualIgnoreXid(wc2.getValue(), msgs_test));
	}
	
	@Test
	public void testForwardDeleteFlowsByDescriptorMultiple() throws Exception {
		reset(routingEngine);
		
		Capture<List<OFMessage>> wc1 = EasyMock.newCapture(CaptureType.ALL);
		Capture<List<OFMessage>> wc2 = EasyMock.newCapture(CaptureType.ALL);
		
		List<Masked<U64>> descriptors = new ArrayList<Masked<U64>>();
		descriptors.add(Masked.of(
				U64.of(0x0000000000ffFFffL),
				U64.of(0x0020000000ffFFffL))); // User mask = 0xffFFffFFL which is forwarding.DECISION_MASK/AppCookie.USER_MASK
		descriptors.add(Masked.of(
				U64.of(0x0000000000ffFFffL),
				U64.of(0x0020000000000000L)));
		
		expect(sw1.getStatus()).andReturn(IOFSwitch.SwitchStatus.MASTER).anyTimes();
		expect(sw2.getStatus()).andReturn(IOFSwitch.SwitchStatus.MASTER).anyTimes();
		
		expect(sw1.write(capture(wc1))).andReturn(ImmutableList.of()).once();
		expect(sw2.write(capture(wc2))).andReturn(ImmutableList.of()).once();
		
		replay(sw1, sw2, routingEngine);
		forwarding.deleteFlowsByDescriptor(descriptors);
		verify(sw1, sw2, routingEngine);
		
		assertTrue(wc1.hasCaptured());
		assertTrue(wc2.hasCaptured());
		
		// Cookies
		Masked<U64> masked_cookie = Masked.of(	AppCookie.makeCookie(Forwarding.FORWARDING_APP_ID, (int)4294967295L),
												AppCookie.getAppFieldMask().or(U64.of(0xffffffL)));
		Masked<U64> masked_cookie2 = Masked.of(	AppCookie.makeCookie(Forwarding.FORWARDING_APP_ID, 0),
												AppCookie.getAppFieldMask().or(U64.of(0x0L)));
		// Add cookies to a msg set
		List<OFMessage> msgs_test = new ArrayList<OFMessage>();
		msgs_test.add( 	factory.buildFlowDelete()
						.setCookie(masked_cookie.getValue())
						.setCookieMask(masked_cookie.getMask())
						.build());
		msgs_test.add( 	factory.buildFlowDelete()
						.setCookie(masked_cookie2.getValue())
						.setCookieMask(masked_cookie2.getMask())
						.build());
		assertTrue(messageListsEqualIgnoreXid(wc1.getValue(), msgs_test));
		assertTrue(messageListsEqualIgnoreXid(wc2.getValue(), msgs_test));
	}
	
	@Test
	public void testForwardDeleteFlowsByDescriptorNoCookies() throws Exception {
		reset(routingEngine);
		
		List<Masked<U64>> descriptors = new ArrayList<Masked<U64>>();
		
		replay(routingEngine);
		forwarding.deleteFlowsByDescriptor(descriptors);
		verify(routingEngine);
	}
	
	@Test
	public void testForwardDeleteFlowsByDescriptorNoCookiesContainer() throws Exception {
		reset(routingEngine);
		
		List<Masked<U64>> descriptors = null;
		
		replay(routingEngine);
		forwarding.deleteFlowsByDescriptor(descriptors);
		verify(routingEngine);
	}
}