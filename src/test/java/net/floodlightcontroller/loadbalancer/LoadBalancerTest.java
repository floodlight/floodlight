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
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.loadbalancer.LoadBalancer.IPClient;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.staticentry.StaticEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
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
	protected StaticEntryPusher sfp;
	protected MemoryStorageSource storage;
	protected RestApiServer restApi;
	protected VipsResource vipsResource;
	protected PoolsResource poolsResource;
	protected WRRResource wrrResource;
	protected PoolMemberResource poolMemberResource;
	protected MembersResource membersResource;
	protected MonitorsResource monitorsResource;
	private MockSyncService mockSyncService;
	protected IDebugCounterService debugCounterService;
	protected LBVip vip1, vip2;
	protected LBPool pool1, pool2, pool3;
	protected LBMember member1, member2, member3, member4, member5, member6;
	protected LBMonitor monitor1, monitor2, monitor3;
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
		sfp = new StaticEntryPusher();
		storage = new MemoryStorageSource(); //dependency for sfp
		mockSyncService = new MockSyncService();
		debugCounterService = new MockDebugCounterService();

		fmc.addService(IRestApiService.class, restApi);
		fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
		fmc.addService(IEntityClassifierService.class, entityClassifier);
		fmc.addService(IThreadPoolService.class, tps);
		fmc.addService(IDeviceService.class, deviceManager);
		fmc.addService(ITopologyService.class, topology);
		fmc.addService(IRoutingService.class, routingEngine);
		fmc.addService(IStaticEntryPusherService.class, sfp);
		fmc.addService(ILoadBalancerService.class, lb);
		fmc.addService(IStorageSourceService.class, storage);
		fmc.addService(ISyncService.class, mockSyncService);
		fmc.addService(IDebugCounterService.class, debugCounterService);
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
		wrrResource = new WRRResource();
		poolMemberResource = new PoolMemberResource();
		monitorsResource = new MonitorsResource();

		vip1=null;
		vip2=null;

		pool1=null;
		pool2=null;
		pool3=null;

		member1=null;
		member2=null;
		member3=null;
		member4=null;
		member5=null;
		member6=null;
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

		postData1 = "{\"id\":\"1\",\"name\":\"pool1\",\"protocol\":\"icmp\",\"lb_method\":\"STATISTICS\",\"vip_id\":\"1\"}";
		postData2 = "{\"id\":\"2\",\"name\":\"pool2\",\"protocol\":\"tcp\",\"lb_method\":\"WRR\",\"vip_id\":\"2\"}";
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
	public void testCreateMonitor(){
		String postData1, postData2,postData3;
		IOException error = null;

		postData1 = "{\"id\":\"1\",\"name\":\"monitor1\",\"type\":\"tcp\"}";
		postData2 = "{\"id\":\"2\",\"name\":\"monitor2\",\"type\":\"tcp\"}";
		postData3 = "{\"id\":\"3\",\"name\":\"monitor3\",\"type\":\"udp\"}";

		try {
			monitor1 = monitorsResource.jsonToMonitor(postData1);
		} catch (IOException e) {
			error = e;
		}
		try {
			monitor2 = monitorsResource.jsonToMonitor(postData2);
		} catch (IOException e) {
			error = e;
		}
		try {
			monitor3 = monitorsResource.jsonToMonitor(postData3);
		} catch (IOException e) {
			error = e;
		}

		// verify correct parsing
		assertFalse(monitor1==null);
		assertFalse(monitor2==null);
		assertFalse(monitor3==null);
		assertTrue(error==null);

		lb.createMonitor(monitor1);
		lb.createMonitor(monitor2);
		lb.createMonitor(monitor3);

		// verify correct creation
		assertTrue(lb.monitors.containsKey(monitor1.id));
		assertTrue(lb.monitors.containsKey(monitor2.id));
		assertTrue(lb.monitors.containsKey(monitor3.id));
	}

	@Test
	public void testRemoveMonitor(){
		testCreateMonitor();

		// verify correct initial condition
		assertFalse(monitor1==null);
		assertFalse(monitor2==null);
		assertFalse(monitor3==null);

		lb.removeMonitor(monitor1.id);
		lb.removeMonitor(monitor2.id);
		lb.removeMonitor(monitor3.id);

		// verify correct removal
		assertFalse(lb.monitors.containsKey(monitor1.id));
		assertFalse(lb.monitors.containsKey(monitor2.id));
		assertFalse(lb.monitors.containsKey(monitor3.id));
	}

	@Test
	public void testDissociateMonitor(){
		testCreateVip();
		testCreatePool();
		testCreateMonitor();

		// verify correct initial condition
		assertFalse(vip1==null);
		assertFalse(vip2==null);
		assertFalse(pool1==null);
		assertFalse(pool2==null);
		assertFalse(pool3==null);
		assertFalse(monitor1==null);
		assertFalse(monitor2==null);
		assertFalse(monitor3==null);

		lb.dissociateMonitorWithPool(pool1.id, monitor1.id);
		lb.dissociateMonitorWithPool(pool2.id, monitor2.id);
		lb.dissociateMonitorWithPool(pool3.id, monitor3.id);

		// verify correct dissociation
		assertFalse(lb.pools.get(pool1.id).monitors.contains(monitor1.id));
		assertFalse(lb.pools.get(pool2.id).monitors.contains(monitor2.id));
		assertFalse(lb.pools.get(pool3.id).monitors.contains(monitor3.id));

		// verify monitors not removed
		assertTrue(lb.monitors.containsKey(monitor1.id));
		assertTrue(lb.monitors.containsKey(monitor2.id));
		assertTrue(lb.monitors.containsKey(monitor3.id));

		// verify monitor poolId is null
		assertNull(lb.monitors.get(monitor1.id).poolId);
		assertNull(lb.monitors.get(monitor2.id).poolId);
		assertNull(lb.monitors.get(monitor3.id).poolId);
	}

	@Test
	public void testAssociateMonitor(){
		testCreatePool();
		testCreateMonitor();

		// verify correct initial condition
		assertFalse(pool1==null);
		assertFalse(pool2==null);
		assertFalse(pool3==null);
		assertFalse(monitor1==null);
		assertFalse(monitor2==null);
		assertFalse(monitor3==null);

		lb.associateMonitorWithPool(pool1.id, monitor1);
		lb.associateMonitorWithPool(pool2.id, monitor2);
		lb.associateMonitorWithPool(pool3.id, monitor3);

		// verify correct association
		assertTrue(lb.pools.get(pool1.id).monitors.contains(monitor1.id));
		assertTrue(lb.pools.get(pool2.id).monitors.contains(monitor2.id));
		assertTrue(lb.pools.get(pool3.id).monitors.contains(monitor3.id));
	}

	@Test
	public void testCreateMember() {
		String postData1, postData2, postData3, postData4,postData5,postData6;
		IOException error = null;

		testCreateVip();
		testCreatePool();

		postData1 = "{\"id\":\"1\",\"address\":\"10.0.0.3\",\"port\":\"8\",\"pool_id\":\"1\",\"weight\":\"2\"}";
		postData2 = "{\"id\":\"2\",\"address\":\"10.0.0.4\",\"port\":\"8\",\"pool_id\":\"1\",\"weight\":\"3\"}";
		postData3 = "{\"id\":\"3\",\"address\":\"10.0.0.5\",\"port\":\"100\",\"pool_id\":\"2\",\"weight\":\"4\"}";
		postData4 = "{\"id\":\"4\",\"address\":\"10.0.0.6\",\"port\":\"100\",\"pool_id\":\"2\"}";
		postData5 = "{\"id\":\"5\",\"address\":\"10.0.0.7\",\"port\":\"100\",\"pool_id\":\"1\",\"weight\":\"5\"}";
		postData6 = "{\"id\":\"6\",\"address\":\"10.0.0.8\",\"port\":\"100\",\"pool_id\":\"1\",\"weight\":\"5\"}";


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
		try {
			member5= membersResource.jsonToMember(postData5);
		} catch (IOException e) {
			error = e;
		}
		try {
			member6= membersResource.jsonToMember(postData6);
		} catch (IOException e) {
			error = e;
		}

		// verify correct parsing
		assertFalse(member1==null);
		assertFalse(member2==null);
		assertFalse(member3==null);
		assertFalse(member4==null);
		assertFalse(member5==null);
		assertFalse(member6==null);
		assertTrue(error==null);

		lb.createMember(member1);
		lb.createMember(member2);
		lb.createMember(member3);
		lb.createMember(member4);
		lb.createMember(member5);
		lb.createMember(member6);

		// add the same server a second time
		lb.createMember(member1);

		// verify successful creates
		assertTrue(lb.members.containsKey(member1.id));
		assertTrue(lb.members.containsKey(member2.id));
		assertTrue(lb.members.containsKey(member3.id));
		assertTrue(lb.members.containsKey(member4.id));
		assertTrue(lb.members.containsKey(member5.id));
		assertTrue(lb.members.containsKey(member6.id));

		assertTrue(lb.pools.get(member1.poolId).members.size()==4);
		assertTrue(lb.pools.get(member3.poolId).members.size()==2);

		// member1 should inherit valid vipId from pool
		assertTrue(lb.vips.get(member1.vipId)!=null);

		assertTrue(member1.weight==2);
		assertTrue(member2.weight==3);
		assertTrue(member3.weight==4);
		assertTrue(member5.weight==5);
		assertTrue(member6.weight==5);

		// default weight value
		assertTrue(member4.weight==1);
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
		lb.removeMember(member5.id);
		lb.removeMember(member6.id);

		// verify correct removal
		assertFalse(lb.members.containsKey(member1.id));
		assertFalse(lb.members.containsKey(member2.id));
		assertFalse(lb.members.containsKey(member3.id));
		assertFalse(lb.members.containsKey(member4.id));
		assertFalse(lb.members.containsKey(member5.id));
		assertFalse(lb.members.containsKey(member6.id));

		//verify member cleanup from pool
		assertFalse(lb.pools.get(member1.poolId).members.contains(member1.id));
		assertFalse(lb.pools.get(member2.poolId).members.contains(member2.id));
		assertFalse(lb.pools.get(member3.poolId).members.contains(member3.id));
		assertFalse(lb.pools.get(member4.poolId).members.contains(member4.id));
		assertFalse(lb.pools.get(member5.poolId).members.contains(member5.id));
		assertFalse(lb.pools.get(member6.poolId).members.contains(member6.id));

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

		Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);

		sw1 = EasyMock.createNiceMock(IOFSwitch.class);
		expect(sw1.getId()).andReturn(DatapathId.of(1L)).anyTimes();
		expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
		expect(sw1.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw1.write(capture(wc1))).andReturn(true).anyTimes();

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
		expect(topology.isBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.getClusterId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
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
				.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:01"))
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
				assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(arpReplyPacketOut1), OFMessageUtils.OFMessageIgnoreXid.of(m));
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

		deviceManager.learnEntity(dataLayerSource1,
				VlanVid.ZERO, networkSource1, IPv6Address.NONE,
				DatapathId.of(1), OFPort.of(1));
		deviceManager.learnEntity(dataLayerSource2,
				VlanVid.ZERO, networkSource2, IPv6Address.NONE,
				DatapathId.of(1), OFPort.of(2));
		deviceManager.learnEntity(dataLayerDest1,
				VlanVid.ZERO, networkDest1, IPv6Address.NONE,
				DatapathId.of(1), OFPort.of(3));
		deviceManager.learnEntity(dataLayerDest2,
				VlanVid.ZERO, networkDest2, IPv6Address.NONE,
				DatapathId.of(1), OFPort.of(4));

		// in bound #1
		Path route1 = new Path(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList1 = new ArrayList<NodePortTuple>();
		nptList1.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		nptList1.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		route1.setPath(nptList1);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3))).andReturn(route1).atLeastOnce();

		// outbound #1
		Path route2 = new Path(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList2 = new ArrayList<NodePortTuple>();
		nptList2.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		nptList2.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		route2.setPath(nptList2);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(3), DatapathId.of(1L), OFPort.of(1))).andReturn(route2).atLeastOnce();

		// inbound #2
		Path route3 = new Path(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList3 = new ArrayList<NodePortTuple>();
		nptList3.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
		nptList3.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(4)));
		route3.setPath(nptList3);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(2), DatapathId.of(1L), OFPort.of(4))).andReturn(route3).atLeastOnce();

		// outbound #2
		Path route4 = new Path(DatapathId.of(1L), DatapathId.of(1L));
		List<NodePortTuple> nptList4 = new ArrayList<NodePortTuple>();
		nptList4.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(4)));
		nptList4.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
		route4.setPath(nptList3);
		expect(routingEngine.getPath(DatapathId.of(1L), OFPort.of(4), DatapathId.of(1L), OFPort.of(2))).andReturn(route4).atLeastOnce();

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

		Map<String, OFMessage> map = sfp.getEntries(DatapathId.of(1L));

		assertTrue(map.size()==4);
	}

	@Test
	public void testSetMemberWeight() {		
		testCreateVip();
		testCreatePool();
		testCreateMember();	

		lb.setMemberWeight(member1.id, "5");
		lb.setMemberWeight(member2.id, "2");
		lb.setMemberWeight(member3.id, "2");
		lb.setMemberWeight(member4.id, "9");

		assertTrue(member1.weight==5);
		assertTrue(member2.weight==2);
		assertTrue(member3.weight==2);
		assertTrue(member4.weight==9);

		int inf_limit = lb.setMemberWeight(member1.id,"0");

		int sup_limit = lb.setMemberWeight(member1.id,"11");

		assertTrue(inf_limit == -1);
		assertTrue(sup_limit == -1);
	}

	@Test
	public void testSetPriorityMember() {		
		testCreateVip();
		testCreatePool();
		testCreateMember();

		lb.setPriorityToMember(member1.id,member1.poolId);

		assertTrue(member1.weight==3);
		assertTrue(member2.weight==1);
		assertTrue(member5.weight==1);
		assertTrue(member6.weight==1);
	}

	@Test
	public void testPoolAlgorithms() {	
		testCreateVip();
		testCreatePool();
		testCreateMember();

		IPClient client = lb.new IPClient();

		HashMap<String, U64> membersBandwidth = new HashMap<String, U64>();
		membersBandwidth.put(member1.id,U64.of(4999));
		membersBandwidth.put(member2.id,U64.of(1344));
		membersBandwidth.put(member3.id,U64.ZERO);
		membersBandwidth.put(member4.id,U64.of(230));
		membersBandwidth.put(member5.id,U64.of(2002));
		membersBandwidth.put(member6.id,U64.of(1345));

		HashMap<String, Short> membersWeight = new HashMap<String, Short>();
		HashMap<String, Short> membersStatus = new HashMap<String, Short>();

		String memberPickedStats = pool1.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		String noMembers = pool3.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		membersBandwidth.clear();
		String memberPickedNoData = pool1.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		assertTrue(memberPickedStats.equals("2"));
		assertTrue(memberPickedNoData.equals("1")); // simple round robin
		assertTrue(noMembers==null);
	}

	@Test
	public void testPoolStats() {	
		testCreateVip();
		testCreatePool();
		testCreateMember();

		ArrayList<Long> bytesIn = new ArrayList<Long>();
		bytesIn.add((long) 10);

		ArrayList<Long> bytesOut = new ArrayList<Long>();
		bytesOut.add((long) 20);

		int activeFlows = 30;

		pool1.setPoolStatistics(bytesIn, bytesOut, activeFlows);

		assertTrue(pool1.poolStats.getBytesIn() == 10);
		assertTrue(pool1.poolStats.getBytesOut() == 20);
		assertTrue(pool1.poolStats.getActiveFlows() == 30);

	}

	@Test
	public void testHealthMonitor(){
		testCreateVip();
		testCreatePool();
		testCreateMonitor();
		testAssociateMonitor();

		// create members without changing pools 
		String postData1, postData2, postData3, postData4,postData5,postData6;
		IOException error = null;

		postData1 = "{\"id\":\"1\",\"address\":\"10.0.0.3\",\"port\":\"8\",\"pool_id\":\"1\",\"weight\":\"2\"}";
		postData2 = "{\"id\":\"2\",\"address\":\"10.0.0.4\",\"port\":\"8\",\"pool_id\":\"1\",\"weight\":\"3\"}";
		postData3 = "{\"id\":\"3\",\"address\":\"10.0.0.5\",\"port\":\"100\",\"pool_id\":\"2\",\"weight\":\"4\"}";
		postData4 = "{\"id\":\"4\",\"address\":\"10.0.0.6\",\"port\":\"100\",\"pool_id\":\"2\"}";
		postData5 = "{\"id\":\"5\",\"address\":\"10.0.0.7\",\"port\":\"100\",\"pool_id\":\"1\",\"weight\":\"5\"}";
		postData6 = "{\"id\":\"6\",\"address\":\"10.0.0.8\",\"port\":\"100\",\"pool_id\":\"1\",\"weight\":\"5\"}";


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
		try {
			member5= membersResource.jsonToMember(postData5);
		} catch (IOException e) {
			error = e;
		}
		try {
			member6= membersResource.jsonToMember(postData6);
		} catch (IOException e) {
			error = e;
		}

		// verify correct parsing
		assertFalse(member1==null);
		assertFalse(member2==null);
		assertFalse(member3==null);
		assertFalse(member4==null);
		assertFalse(member5==null);
		assertFalse(member6==null);
		assertTrue(error==null);

		lb.createMember(member1);
		lb.createMember(member2);
		lb.createMember(member3);
		lb.createMember(member4);
		lb.createMember(member5);
		lb.createMember(member6);

		// verify successful creates
		assertTrue(lb.members.containsKey(member1.id));
		assertTrue(lb.members.containsKey(member2.id));
		assertTrue(lb.members.containsKey(member3.id));
		assertTrue(lb.members.containsKey(member4.id));
		assertTrue(lb.members.containsKey(member5.id));
		assertTrue(lb.members.containsKey(member6.id));

		IPClient client = lb.new IPClient();
		LoadBalancer.isMonitoringEnabled = true;

		HashMap<String, U64> membersBandwidth = new HashMap<String, U64>();
		membersBandwidth.put(member1.id,U64.of(5000));
		membersBandwidth.put(member2.id,U64.of(1344));
		membersBandwidth.put(member3.id,U64.ZERO);
		membersBandwidth.put(member4.id,U64.of(230));
		membersBandwidth.put(member5.id,U64.of(2002));
		membersBandwidth.put(member6.id,U64.of(1345));

		HashMap<String, Short> membersWeight = new HashMap<String, Short>();

		HashMap<String, Short> membersStatus = new HashMap<String, Short>();
		membersStatus.put(member1.id,(short) 1);
		membersStatus.put(member2.id,(short) -1);
		membersStatus.put(member3.id,(short) -1);
		membersStatus.put(member4.id,(short) 1);
		membersStatus.put(member5.id,(short) 1);
		membersStatus.put(member6.id,(short) -1);

		String memberPickedStats = pool1.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		String noMembers = pool3.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		membersBandwidth.clear();
		String memberPickedNoData = pool1.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		membersWeight.put(member3.id,(short) 3);
		membersWeight.put(member4.id,(short) 1);

		String weightedPool = pool2.pickMember(client, membersBandwidth, membersWeight,membersStatus);

		// verify pools associated with monitors
		assertTrue(pool1.monitors.contains(monitor1.id));
		assertTrue(pool2.monitors.contains(monitor2.id));
		assertTrue(pool3.monitors.contains(monitor3.id));

		// verify members connected to pool
		assertTrue(!pool1.members.isEmpty());
		assertTrue(!pool2.members.isEmpty());
		assertTrue(pool3.members.isEmpty());

		assertTrue(memberPickedStats.equals("5"));
		assertTrue(memberPickedNoData.equals("1")); // simple round robin
		assertTrue(weightedPool.equals("4"));
		assertTrue(noMembers==null);

	}
}
