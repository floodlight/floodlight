/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.loadbalancer;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyShort;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.util.HexString;
import org.projectfloodlight.openflow.types.DatapathId;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.MockDebugEventService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.OFMessageUtils;

public class LoadBalancerTest extends FloodlightTestCase {
	protected LoadBalancer lb;

	protected FloodlightContext cntx;
	protected FloodlightModuleContext fmc;
	protected MockDeviceManager deviceManager;
	protected MockThreadPoolService tps;
	protected DefaultEntityClassifier entityClassifier;
	protected IRoutingService routingEngine;
	protected ITopologyService topology;
	protected StaticFlowEntryPusher sfp;
	protected MemoryStorageSource storage;
	protected RestApiServer restApi;
	protected VipsResource vipsResource;
	protected PoolsResource poolsResource;
	protected MembersResource membersResource;
	private MockSyncService mockSyncService;
	protected IDebugCounterService debugCounterService;
	protected IDebugEventService debugEventService;
	protected LBVip vip1, vip2;
	protected LBPool pool1, pool2, pool3;
	protected LBMember member1, member2, member3, member4;
	private OFFactory factory;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		factory = OFFactories.getFactory(OFVersion.OF_13);
		
		lb = new LoadBalancer();

		cntx = new FloodlightContext();
		fmc = new FloodlightModuleContext();
		entityClassifier = new DefaultEntityClassifier(); // dependency for device manager
		tps = new MockThreadPoolService(); //dependency for device manager
		deviceManager = new MockDeviceManager();
		topology = createMock(ITopologyService.class);
		routingEngine = createMock(IRoutingService.class);
		restApi = new RestApiServer();
		sfp = new StaticFlowEntryPusher();
		storage = new MemoryStorageSource(); //dependency for sfp
		mockSyncService = new MockSyncService();
		debugCounterService = new MockDebugCounterService();
		debugEventService = new MockDebugEventService();

		fmc.addService(IRestApiService.class, restApi);
		fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
		fmc.addService(IEntityClassifierService.class, entityClassifier);
		fmc.addService(IThreadPoolService.class, tps);
		fmc.addService(IDeviceService.class, deviceManager);
		fmc.addService(ITopologyService.class, topology);
		fmc.addService(IRoutingService.class, routingEngine);
		fmc.addService(IStaticFlowEntryPusherService.class, sfp);
		fmc.addService(ILoadBalancerService.class, lb);
		fmc.addService(IStorageSourceService.class, storage);
		fmc.addService(ISyncService.class, mockSyncService);
		fmc.addService(IDebugCounterService.class, debugCounterService);
		fmc.addService(IDebugEventService.class, debugEventService);
		fmc.addService(IOFSwitchService.class, getMockSwitchService());

		lb.init(fmc);
		//getMockFloodlightProvider().init(fmc);
		entityClassifier.init(fmc);
		tps.init(fmc);
		mockSyncService.init(fmc);
		deviceManager.init(fmc);
		restApi.init(fmc);
		sfp.init(fmc);
		storage.init(fmc);

		topology.addListener(deviceManager);
		expectLastCall().times(1);
		replay(topology);

		lb.startUp(fmc);
		//getMockFloodlightProvider().startUp(fmc);
		entityClassifier.startUp(fmc);
		tps.startUp(fmc);
		mockSyncService.startUp(fmc);
		deviceManager.startUp(fmc);
		restApi.startUp(fmc);
		sfp.startUp(fmc);
		storage.startUp(fmc);

		verify(topology);

		vipsResource = new VipsResource();
		poolsResource = new PoolsResource();
		membersResource = new MembersResource();

		vip1=null;
		vip2=null;

		pool1=null;
		pool2=null;
		pool3=null;

		member1=null;
		member2=null;
		member3=null;
		member4=null;
	}

	@Test
	public void testCreateVip() {
		String postData1, postData2;
		IOException error = null;

		postData1 = "{\"id\":\"1\",\"name\":\"vip1\",\"protocol\":\"icmp\",\"address\":\"10.0.0.100\",\"port\":\"8\"}";
		postData2 = "{\"id\":\"2\",\"name\":\"vip2\",\"protocol\":\"tcp\",\"address\":\"10.0.0.200\",\"port\":\"100\"}";

		try {
			vip1 = vipsResource.jsonToVip(postData1);
		} catch (IOException e) {
			error = e;
		}
		try {
			vip2 = vipsResource.jsonToVip(postData2);
		} catch (IOException e) {
			error = e;
		}

		// verify correct parsing
		assertFalse(vip1==null);
		assertFalse(vip2==null);
		assertTrue(error==null);

		lb.createVip(vip1);
		lb.createVip(vip2);

		// verify correct creation
		assertTrue(lb.vips.containsKey(vip1.id));
		assertTrue(lb.vips.containsKey(vip2.id));
	}

	@Test
	public void testRemoveVip() {

		testCreateVip();

		// verify correct initial condition
		assertFalse(vip1==null);
		assertFalse(vip2==null);

		lb.removeVip(vip1.id);
		lb.removeVip(vip2.id);

		// verify correct removal
		assertFalse(lb.vips.containsKey(vip1.id));
		assertFalse(lb.vips.containsKey(vip2.id));

	}

	@Test
	public void testCreatePool() {
		String postData1, postData2, postData3;
		IOException error = null;

		testCreateVip();

		postData1 = "{\"id\":\"1\",\"name\":\"pool1\",\"protocol\":\"icmp\",\"vip_id\":\"1\"}";
		postData2 = "{\"id\":\"2\",\"name\":\"pool2\",\"protocol\":\"tcp\",\"vip_id\":\"2\"}";
		postData3 = "{\"id\":\"3\",\"name\":\"pool3\",\"protocol\":\"udp\",\"vip_id\":\"3\"}";

		try {
			pool1 = poolsResource.jsonToPool(postData1);
		} catch (IOException e) {
			error = e;
		}
		try {
			pool2 = poolsResource.jsonToPool(postData2);
		} catch (IOException e) {
			error = e;
		}
		try {
			pool3 = poolsResource.jsonToPool(postData3);
		} catch (IOException e) {
			error = e;
		}

		// verify correct parsing
		assertFalse(pool1==null);
		assertFalse(pool2==null);
		assertFalse(pool3==null);
		assertTrue(error==null);

		lb.createPool(pool1);
		lb.createPool(pool2);
		lb.createPool(pool3);

		// verify successful creates; two registered with vips and one not
		assertTrue(lb.pools.containsKey(pool1.id));
		assertTrue(lb.vips.get(pool1.vipId).pools.contains(pool1.id));
		assertTrue(lb.pools.containsKey(pool2.id));
		assertTrue(lb.vips.get(pool2.vipId).pools.contains(pool2.id));
		assertTrue(lb.pools.containsKey(pool3.id));
		assertFalse(lb.vips.containsKey(pool3.vipId));

	}

	@Test
	public void testRemovePool() {
		testCreateVip();
		testCreatePool();

		// verify correct initial condition
		assertFalse(vip1==null);
		assertFalse(vip2==null);
		assertFalse(pool1==null);
		assertFalse(pool2==null);
		assertFalse(pool3==null);

		lb.removePool(pool1.id);
		lb.removePool(pool2.id);
		lb.removePool(pool3.id);

		// verify correct removal
		assertFalse(lb.pools.containsKey(pool1.id));
		assertFalse(lb.pools.containsKey(pool2.id));
		assertFalse(lb.pools.containsKey(pool3.id));

		//verify pool cleanup from vip
		assertFalse(lb.vips.get(pool1.vipId).pools.contains(pool1.id));
		assertFalse(lb.vips.get(pool2.vipId).pools.contains(pool2.id));
	}

	@Test
	public void testCreateMember() {
		String postData1, postData2, postData3, postData4;
		IOException error = null;

		testCreateVip();
		testCreatePool();

		postData1 = "{\"id\":\"1\",\"address\":\"10.0.0.3\",\"port\":\"8\",\"pool_id\":\"1\"}";
		postData2 = "{\"id\":\"2\",\"address\":\"10.0.0.4\",\"port\":\"8\",\"pool_id\":\"1\"}";
		postData3 = "{\"id\":\"3\",\"address\":\"10.0.0.5\",\"port\":\"100\",\"pool_id\":\"2\"}";
		postData4 = "{\"id\":\"4\",\"address\":\"10.0.0.6\",\"port\":\"100\",\"pool_id\":\"2\"}";

		try {
			member1 = membersResource.jsonToMember(postData1);
		} catch (IOException e) {
			error = e;
		}
		try {
			member2 = membersResource.jsonToMember(postData2);
		} catch (IOException e) {
			error = e;
		}
		try {
			member3 = membersResource.jsonToMember(postData3);
		} catch (IOException e) {
			error = e;
		}
		try {
			member4 = membersResource.jsonToMember(postData4);
		} catch (IOException e) {
			error = e;
		}

		// verify correct parsing
		assertFalse(member1==null);
		assertFalse(member2==null);
		assertFalse(member3==null);
		assertFalse(member4==null);
		assertTrue(error==null);

		lb.createMember(member1);
		lb.createMember(member2);
		lb.createMember(member3);
		lb.createMember(member4);

		// add the same server a second time
		lb.createMember(member1);

		// verify successful creates
		assertTrue(lb.members.containsKey(member1.id));
		assertTrue(lb.members.containsKey(member2.id));
		assertTrue(lb.members.containsKey(member3.id));
		assertTrue(lb.members.containsKey(member4.id));

		assertTrue(lb.pools.get(member1.poolId).members.size()==2);
		assertTrue(lb.pools.get(member3.poolId).members.size()==2);

		// member1 should inherit valid vipId from pool
		assertTrue(lb.vips.get(member1.vipId)!=null);
	}

	@Test
	public void testRemoveMember() {
		testCreateVip();
		testCreatePool();
		testCreateMember();

		// verify correct initial condition
		assertFalse(vip1==null);
		assertFalse(vip2==null);
		assertFalse(pool1==null);
		assertFalse(pool2==null);
		assertFalse(pool3==null);
		assertFalse(member1==null);
		assertFalse(member2==null);
		assertFalse(member3==null);
		assertFalse(member4==null);

		lb.removeMember(member1.id);
		lb.removeMember(member2.id);
		lb.removeMember(member3.id);
		lb.removeMember(member4.id);

		// verify correct removal
		assertFalse(lb.members.containsKey(member1.id));
		assertFalse(lb.members.containsKey(member2.id));
		assertFalse(lb.members.containsKey(member3.id));
		assertFalse(lb.members.containsKey(member4.id));

		//verify member cleanup from pool
		assertFalse(lb.pools.get(member1.poolId).members.contains(member1.id));
		assertFalse(lb.pools.get(member2.poolId).members.contains(member2.id));
		assertFalse(lb.pools.get(member3.poolId).members.contains(member3.id));
		assertFalse(lb.pools.get(member4.poolId).members.contains(member4.id));

	}

	@Test
	public void testTwoSubsequentIcmpRequests() throws Exception {
		testCreateVip();
		testCreatePool();
		testCreateMember();

		IOFSwitch sw1;

		IPacket arpRequest1, arpReply1, icmpPacket1, icmpPacket2;

		byte[] arpRequest1Serialized;
		byte[] arpReply1Serialized;
		byte[] icmpPacket1Serialized, icmpPacket2Serialized;

		OFPacketIn arpRequestPacketIn1;
		OFPacketIn icmpPacketIn1, icmpPacketIn2;

		OFPacketOut arpReplyPacketOut1;

		Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);

		sw1 = EasyMock.createNiceMock(IOFSwitch.class);
		expect(sw1.getId()).andReturn(DatapathId.of(1L)).anyTimes();
		expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
		expect(sw1.getOFFactory()).andReturn(factory).anyTimes();
		sw1.write(capture(wc1));
		expectLastCall().anyTimes();
		sw1.flush();
		expectLastCall().anyTimes();
		
		replay(sw1);
		sfp.switchAdded(DatapathId.of(1L));
		
		verify(sw1);

		/* Test plan:
		 * - two clients and two servers on sw1 port 1, 2, 3, 4
		 * - mock arp request received towards vip1 from (1L, 1)
		 * - proxy arp got pushed out to (1L, 1)- check sw1 getting the packetout
		 * - mock icmp request received towards vip1 from (1L, 1)
		 * - device manager list of devices queried to identify source and dest devices
		 * - routing engine queried to get inbound and outbound routes
		 * - check getRoute calls and responses
		 * - sfp called to install flows
		 * - check sfp calls
		 */

		// Build topology
		reset(topology);
		expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.getL2DomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(2))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(4))).andReturn(true).anyTimes();
		replay(topology);

		// Build arp packets
		arpRequest1 = new Ethernet()
		.setSourceMACAddress("00:00:00:00:00:01")
		.setDestinationMACAddress("ff:ff:ff:ff:ff:ff")
		.setEtherType(EthType.ARP)
		.setVlanID((short) 0)
		.setPriorityCode((byte) 0)
		.setPayload(
				new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte) 6)
				.setProtocolAddressLength((byte) 4)
				.setOpCode(ARP.OP_REQUEST)
				.setSenderHardwareAddress(MacAddress.of("00:00:00:00:00:01"))
				.setSenderProtocolAddress(IPv4Address.of("10.0.0.1"))
				.setTargetHardwareAddress(MacAddress.of("00:00:00:00:00:00"))
				.setTargetProtocolAddress(IPv4Address.of("10.0.0.100")));

		arpRequest1Serialized = arpRequest1.serialize();

		arpRequestPacketIn1 = factory.buildPacketIn()
				.setMatch(factory.buildMatch().setExact(MatchField.IN_PORT, OFPort.of(1)).build())
				.setBufferId(OFBufferId.NO_BUFFER)
				.setData(arpRequest1Serialized)
				.setReason(OFPacketInReason.NO_MATCH)
				.build();

		IFloodlightProviderService.bcStore.put(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
				(Ethernet) arpRequest1);

		// Mock proxy arp packet-out
		arpReply1 = new Ethernet()
		.setSourceMACAddress(LBVip.LB_PROXY_MAC)
		.setDestinationMACAddress(HexString.fromHexString("00:00:00:00:00:01"))
		.setEtherType(EthType.ARP)
		.setVlanID((short) 0)
		.setPriorityCode((byte) 0)
		.setPayload(
				new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte) 6)
				.setProtocolAddressLength((byte) 4)
				.setOpCode(ARP.OP_REPLY)
				.setSenderHardwareAddress(MacAddress.of(LBVip.LB_PROXY_MAC))
				.setSenderProtocolAddress(IPv4Address.of("10.0.0.100"))
				.setTargetHardwareAddress(MacAddress.of("00:00:00:00:00:01"))
				.setTargetProtocolAddress(IPv4Address.of("10.0.0.1")));

		arpReply1Serialized = arpReply1.serialize();

		List<OFAction> poactions = new ArrayList<OFAction>();
		poactions.add(factory.actions().output(arpRequestPacketIn1.getMatch().get(MatchField.IN_PORT), Integer.MAX_VALUE));
		arpReplyPacketOut1 = factory.buildPacketOut()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setInPort(OFPort.ANY)
				.setActions(poactions)
				.setData(arpReply1Serialized)
				.setXid(22)
				.build();
		sw1.write(arpReplyPacketOut1);
		
		lb.receive(sw1, arpRequestPacketIn1, cntx);
		verify(sw1, topology);

		assertTrue(wc1.hasCaptured());  // wc1 should get packetout

		List<OFMessage> msglist1 = wc1.getValues();

		for (OFMessage m: msglist1) {
			if (m instanceof OFPacketOut)
				assertTrue(OFMessageUtils.equalsIgnoreXid(arpReplyPacketOut1, m));
			else
				assertTrue(false); // unexpected message
		}

		//
		// Skip arpRequest2 test - in reality this will happen, but for unit test the same logic
		// is already validated with arpRequest1 test above
		//
		
		// Keep the StaticFlowEntryPusher happy with a switch in the switch service
		Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>(1);
		switches.put(DatapathId.of(1), sw1);
		getMockSwitchService().setSwitches(switches);


		// Build icmp packets
		icmpPacket1 = new Ethernet()
		.setSourceMACAddress("00:00:00:00:00:01")
		.setDestinationMACAddress(LBVip.LB_PROXY_MAC)
		.setEtherType(EthType.IPv4)
		.setVlanID((short) 0)
		.setPriorityCode((byte) 0)
		.setPayload(
				new IPv4()
				.setSourceAddress("10.0.0.1")
				.setDestinationAddress("10.0.0.100")
				.setProtocol(IpProtocol.ICMP)
				.setPayload(new ICMP()
				.setIcmpCode((byte) 0)
				.setIcmpType((byte) 0)));

		icmpPacket1Serialized = icmpPacket1.serialize();

		icmpPacketIn1 = OFFactories.getFactory(OFVersion.OF_13).buildPacketIn()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(OFFactories.getFactory(OFVersion.OF_13).buildMatch().setExact(MatchField.IN_PORT, OFPort.of(1)).build())
				.setData(icmpPacket1Serialized)
				.setReason(OFPacketInReason.NO_MATCH)
				.build();
		icmpPacket2 = new Ethernet()
		.setSourceMACAddress("00:00:00:00:00:02")
		.setDestinationMACAddress(LBVip.LB_PROXY_MAC)
		.setEtherType(EthType.IPv4)
		.setVlanID((short) 0)
		.setPriorityCode((byte) 0)
		.setPayload(
				new IPv4()
				.setSourceAddress("10.0.0.2")
				.setDestinationAddress("10.0.0.100")
				.setProtocol(IpProtocol.ICMP)
				.setPayload(new ICMP()
				.setIcmpCode((byte) 0)
				.setIcmpType((byte) 0)));

		icmpPacket2Serialized = icmpPacket2.serialize();

		icmpPacketIn2 = OFFactories.getFactory(OFVersion.OF_13).buildPacketIn()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setMatch(OFFactories.getFactory(OFVersion.OF_13).buildMatch().setExact(MatchField.IN_PORT, OFPort.of(2)).build())
				.setData(icmpPacket2Serialized)
				.setReason(OFPacketInReason.NO_MATCH)
				.build();
		MacAddress dataLayerSource1 = ((Ethernet)icmpPacket1).getSourceMACAddress();
		IPv4Address networkSource1 = ((IPv4)((Ethernet)icmpPacket1).getPayload()).getSourceAddress();
		MacAddress dataLayerDest1 = MacAddress.of("00:00:00:00:00:03");
		IPv4Address networkDest1 = IPv4Address.of("10.0.0.3");
		MacAddress dataLayerSource2 = ((Ethernet)icmpPacket2).getSourceMACAddress();
		IPv4Address networkSource2 = ((IPv4)((Ethernet)icmpPacket2).getPayload()).getSourceAddress();
		MacAddress dataLayerDest2 = MacAddress.of("00:00:00:00:00:04");
		IPv4Address networkDest2 = IPv4Address.of("10.0.0.4");

		deviceManager.learnEntity(dataLayerSource1.getLong(),
				null, networkSource1.getInt(),
				1L, 1);
		deviceManager.learnEntity(dataLayerSource2.getLong(),
				null, networkSource2.getInt(),
				1L, 2);
		deviceManager.learnEntity(dataLayerDest1.getLong(),
				null, networkDest1.getInt(),
				1L, 3);
		deviceManager.learnEntity(dataLayerDest2.getLong(),
				null, networkDest2.getInt(),
				1L, 4);

		// in bound #1
		Route route1 = new Route(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList1 = new ArrayList<NodePortTuple>();
		nptList1.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		nptList1.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		route1.setPath(nptList1);
		expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3), U64.of(0))).andReturn(route1).atLeastOnce();

		// outbound #1
		Route route2 = new Route(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList2 = new ArrayList<NodePortTuple>();
		nptList2.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		nptList2.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		route2.setPath(nptList2);
		expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(3), DatapathId.of(1L), OFPort.of(1), U64.of(0))).andReturn(route2).atLeastOnce();

		// inbound #2
		Route route3 = new Route(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList3 = new ArrayList<NodePortTuple>();
		nptList3.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
		nptList3.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(4)));
		route3.setPath(nptList3);
		expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(2), DatapathId.of(1L), OFPort.of(4), U64.of(0))).andReturn(route3).atLeastOnce();

		// outbound #2
		Route route4 = new Route(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList4 = new ArrayList<NodePortTuple>();
		nptList4.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(4)));
		nptList4.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
		route4.setPath(nptList3);
		expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(4), DatapathId.of(1L), OFPort.of(2), U64.of(0))).andReturn(route4).atLeastOnce();

		replay(routingEngine);

		wc1.reset();

		IFloodlightProviderService.bcStore.put(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
				(Ethernet) icmpPacket1);
		lb.receive(sw1, icmpPacketIn1, cntx);

		IFloodlightProviderService.bcStore.put(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
				(Ethernet) icmpPacket2);
		lb.receive(sw1, icmpPacketIn2, cntx);

		assertTrue(wc1.hasCaptured());  // wc1 should get packetout

		List<OFMessage> msglist2 = wc1.getValues();

		assertTrue(msglist2.size()==2); // has inbound and outbound packetouts
		// TODO: not seeing flowmods yet ...

		Map<String, OFFlowMod> map = sfp.getFlows(DatapathId.of(1L));

		assertTrue(map.size()==4);
	}


}
