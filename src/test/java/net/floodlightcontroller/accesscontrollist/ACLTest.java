/**
 *    Copyright 2015, Big Switch Networks, Inc.
 *    Originally created by Pengfei Lu, Network and Cloud Computing Laboratory, Dalian University of Technology, China 
 *    Advisers: Keqiu Li and Heng Qi 
 *    This work is supported by the State Key Program of National Natural Science of China(Grant No. 61432002) 
 *    and Prospective Research Project on Future Networks in Jiangsu Future Networks Innovation Institute.
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

package net.floodlightcontroller.accesscontrollist;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.floodlightcontroller.accesscontrollist.ACL;
import net.floodlightcontroller.accesscontrollist.ACLRule;
import net.floodlightcontroller.accesscontrollist.IACLService;
import net.floodlightcontroller.accesscontrollist.ACLRule.Action;
import net.floodlightcontroller.accesscontrollist.util.IPAddressUtil;
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
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;

public class ACLTest extends FloodlightTestCase {

	protected FloodlightContext cntx;
	protected IOFSwitch sw;
	
	private MockDebugEventService debugEventService; // dependency for device manager
	private DefaultEntityClassifier entityClassifier; // dependency for device manager
	private MockThreadPoolService tps; // dependency for device manager
	private ITopologyService topology; // dependency for device manager
	private MockDeviceManager deviceManager;

	private MockDebugCounterService debugCounterService;
	private MemoryStorageSource storageService;

	private RestApiServer restApi;
	private ACL acl;

	public static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";

	@Override
	@Before
	public void setUp() throws Exception {

		super.setUp();
		cntx = new FloodlightContext();
		mockFloodlightProvider = getMockFloodlightProvider();
		mockSwitchManager = getMockSwitchService();

		debugEventService = new MockDebugEventService();
		entityClassifier = new DefaultEntityClassifier();
		tps = new MockThreadPoolService();
		deviceManager = new MockDeviceManager();
		topology = createMock(ITopologyService.class);
		debugCounterService = new MockDebugCounterService();
		storageService = new MemoryStorageSource();
		restApi = new RestApiServer();
		acl = new ACL();

		// Mock switches
		DatapathId dpid = DatapathId.of(TestSwitch1DPID);
		sw = EasyMock.createNiceMock(IOFSwitch.class);
		expect(sw.getId()).andReturn(dpid).anyTimes();
		expect(sw.getOFFactory()).andReturn(
				OFFactories.getFactory(OFVersion.OF_13)).anyTimes();
		replay(sw);
		// Load the switch map
		Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
		switches.put(dpid, sw);
		mockSwitchManager.setSwitches(switches);

		FloodlightModuleContext fmc = new FloodlightModuleContext();
		fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
		fmc.addService(IOFSwitchService.class, mockSwitchManager);
		fmc.addService(IDebugCounterService.class, debugCounterService);
		fmc.addService(IStorageSourceService.class, storageService);
		fmc.addService(IDebugEventService.class, debugEventService);
		fmc.addService(IEntityClassifierService.class, entityClassifier);
		fmc.addService(IThreadPoolService.class, tps);
		fmc.addService(IDeviceService.class, deviceManager);
		fmc.addService(ITopologyService.class, topology);
		fmc.addService(IRestApiService.class, restApi);
		fmc.addService(IACLService.class, acl);

		topology.addListener(deviceManager);
		expectLastCall().times(1);
		replay(topology);
		
		debugCounterService.init(fmc);
		entityClassifier.init(fmc);
		tps.init(fmc);
		deviceManager.init(fmc);
		storageService.init(fmc);
		restApi.init(fmc);
		acl.init(fmc);
		
		debugCounterService.startUp(fmc);
		deviceManager.startUp(fmc);
		entityClassifier.startUp(fmc);
		tps.startUp(fmc);
		storageService.startUp(fmc);
		acl.startUp(fmc);
		verify(topology);

		storageService.createTable(StaticFlowEntryPusher.TABLE_NAME, null);
		storageService.setTablePrimaryKeyName(StaticFlowEntryPusher.TABLE_NAME,
				StaticFlowEntryPusher.COLUMN_NAME);

	}

	@Test
	public void testAddRule() {
		
		reset(topology);
//		expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
//		expect(topology.getOpenflowDomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(1))).andReturn(true).anyTimes();
		replay(topology);

		int[] cidr = new int[2];
		ACLRule rule1, rule2, rule3, rule4;
		IResultSet resultSet;
		Iterator<IResultSet> it;
		Map<String, Object> row;

		// a new AP[dpid:00:00:00:00:00:00:00:01 port:1 ip:10.0.0.1] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:01"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.1"), IPv6Address.NONE, DatapathId.of(1), OFPort.of(1));
		
		// a new AP[dpid:00:00:00:00:00:00:00:02 port:1 ip:10.0.0.3] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:03"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.3"), IPv6Address.NONE, DatapathId.of(2), OFPort.of(1));
		
		// rule1 indicates host(10.0.0.0/28) can not access TCP port 80 in host(10.0.0.254/32)
		rule1 = new ACLRule();
		rule1.setNw_src("10.0.0.0/28");
		cidr = IPAddressUtil.parseCIDR("10.0.0.0/28");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		rule1.setNw_dst("10.0.0.254/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.254/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule1.setAction(Action.DENY);

		assertEquals(acl.addRule(rule1), true);
		assertEquals(acl.getRules().size(), 1);

		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:01");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:02");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		// rule2 matches rule1
		rule2 = new ACLRule();
		rule2.setNw_src("10.0.0.1/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		rule2.setNw_dst("10.0.0.254/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.254/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(6);
		rule2.setTp_dst(80);
		rule2.setAction(Action.DENY);

		// there will be no extra flow entry
		assertEquals(acl.addRule(rule1), false);
		assertEquals(acl.getRules().size(), 1);
		
		// rule3 indicates that no ICMP packets can reach host[10.0.0.3/32]
		rule3 = new ACLRule();
		rule3.setNw_dst("10.0.0.3/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.3/32");
		rule3.setNw_dst_prefix(cidr[0]);
		rule3.setNw_dst_maskbits(cidr[1]);
		rule3.setNw_proto(1);
		rule3.setAction(Action.DENY);

		assertEquals(acl.addRule(rule3), true);
		assertEquals(acl.getRules().size(), 2);
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_2_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:02");
			assertEquals(row.get("priority").toString(),"29999");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src"), null);
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.3/32");
			assertEquals(row.get("ip_proto").toString(),"1");
			assertEquals(row.get("tp_dst"), null);
			assertEquals(row.get("actions"), null);
		}
		
		// rule4 indicates that host(10.0.0.1/32) can access host(10.0.0.3/32)
		rule4 = new ACLRule();
		rule4.setNw_src("10.0.0.1/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule4.setNw_src_prefix(cidr[0]);
		rule4.setNw_src_maskbits(cidr[1]);
		rule4.setNw_dst("10.0.0.3/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.3/32");
		rule4.setNw_dst_prefix(cidr[0]);
		rule4.setNw_dst_maskbits(cidr[1]);
		rule4.setAction(Action.ALLOW);

		assertEquals(acl.addRule(rule4), true);
		assertEquals(acl.getRules().size(), 3);
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_3_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:01");
			assertEquals(row.get("priority").toString(),"29999");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(), "10.0.0.1/32");
			assertEquals(row.get("ipv4_dst").toString(), "10.0.0.3/32");
			assertEquals(row.get("ip_proto"), null);
			assertEquals(row.get("tp_dst"), null);
			assertEquals(row.get("actions"), "output=controller");
		}
		
	}
	
	@Test
	public void testDeviceAdded() {
		
		reset(topology);
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(2))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(2))).andReturn(true).anyTimes();
		replay(topology);
		
		int[] cidr = new int[2];
		ACLRule rule1, rule2;
		IResultSet resultSet;
		Iterator<IResultSet> it;
		Map<String, Object> row;

		// rule1 indicates host(10.0.0.0/28) can not access TCP port 80 in host(10.0.0.254/32)
		rule1 = new ACLRule();
		rule1.setNw_src("10.0.0.0/28");
		cidr = IPAddressUtil.parseCIDR("10.0.0.0/28");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		rule1.setNw_dst("10.0.0.254/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.254/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule1.setAction(Action.DENY);

		assertEquals(acl.addRule(rule1), true);
		assertEquals(acl.getRules().size(), 1);
		
		// a new AP[dpid:00:00:00:00:00:00:00:01 port:1 ip:10.0.0.1] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:01"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.1"), IPv6Address.NONE, DatapathId.of(1), OFPort.of(1));
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:01");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		// a new AP[dpid:00:00:00:00:00:00:00:01 port:2 ip:10.0.0.2] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:02"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.2"), IPv6Address.NONE, DatapathId.of(1), OFPort.of(2));
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		int count = 0;
		while(it.hasNext()){
			row = it.next().getRow();
			count++;
		}
		// there is no extra flow entry added
		assertEquals(count, 1);
		
		// rule2 indicates that no ICMP packets can reach host[10.0.0.3/32]
		rule2 = new ACLRule();
		rule2.setNw_dst("10.0.0.3/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.3/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(1);
		rule2.setAction(Action.DENY);

		assertEquals(acl.addRule(rule2), true);
		assertEquals(acl.getRules().size(), 2);

		// a new AP[dpid:00:00:00:00:00:00:00:02 port:1 ip:10.0.0.3] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:03"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.3"), IPv6Address.NONE, DatapathId.of(2), OFPort.of(1));
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_2_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:02");
			assertEquals(row.get("priority").toString(),"29999");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src"), null);
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.3/32");
			assertEquals(row.get("ip_proto").toString(),"1");
			assertEquals(row.get("tp_dst"), null);
			assertEquals(row.get("actions"), null);
		}
	}
	
	@Test
	public void testDeviceIPV4AddrChanged() {
		
		reset(topology);
		expect(topology.getOpenflowDomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(1))).andReturn(true).anyTimes();
		replay(topology);

		int[] cidr = new int[2];
		ACLRule rule1;
		IResultSet resultSet;
		Iterator<IResultSet> it;
		Map<String, Object> row;
	
		// a new AP[dpid:00:00:00:00:00:00:00:01 port:1] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:01"),
				VlanVid.ZERO, IPv4Address.NONE, IPv6Address.NONE, DatapathId.of(1), OFPort.of(1));
		
		// rule1 indicates host(10.0.0.0/28) can not access TCP port 80 in host(10.0.0.254/32)
		rule1 = new ACLRule();
		rule1.setNw_src("10.0.0.0/28");
		cidr = IPAddressUtil.parseCIDR("10.0.0.0/28");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		rule1.setNw_dst("10.0.0.254/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.254/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule1.setAction(Action.DENY);

		assertEquals(acl.addRule(rule1), true);
		assertEquals(acl.getRules().size(), 1);

		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		assertEquals(it.hasNext(), false);
		
		// a new AP[dpid:00:00:00:00:00:00:00:01 port:1 ip:10.0.0.1] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:01"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.1"), IPv6Address.NONE, DatapathId.of(1), OFPort.of(1));
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:01");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
	}
	
	@Test
	public void testDeleteRule(){
		reset(topology);
//		expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
//		expect(topology.getOpenflowDomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(1))).andReturn(true).anyTimes();
		replay(topology);

		int[] cidr = new int[2];
		ACLRule rule1;
		IResultSet resultSet;
		Iterator<IResultSet> it;
		Map<String, Object> row;

		// a new AP[dpid:00:00:00:00:00:00:00:01 port:1 ip:10.0.0.1] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:01"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.1"), IPv6Address.NONE, DatapathId.of(1), OFPort.of(1));
		
		// a new AP[dpid:00:00:00:00:00:00:00:02 port:1 ip:10.0.0.3] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:03"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.3"), IPv6Address.NONE, DatapathId.of(2), OFPort.of(1));
		
		// rule1 indicates host(10.0.0.0/28) can not access TCP port 80 in host(10.0.0.254/32)
		rule1 = new ACLRule();
		rule1.setNw_src("10.0.0.0/28");
		cidr = IPAddressUtil.parseCIDR("10.0.0.0/28");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		rule1.setNw_dst("10.0.0.254/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.254/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule1.setAction(Action.DENY);

		assertEquals(acl.addRule(rule1), true);
		assertEquals(acl.getRules().size(), 1);

		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:01");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:02");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		// remove rule1 and all relevant flow entries will be removed
		acl.removeRule(1);
		assertEquals(acl.getRules().size(),0);
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		assertEquals(it.hasNext(), false);

		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		assertEquals(it.hasNext(), false);
		
	}
	
	@Test
	public void testDeleteAllRules(){
		reset(topology);
//		expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
//		expect(topology.getOpenflowDomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(1))).andReturn(true).anyTimes();
		replay(topology);

		int[] cidr = new int[2];
		ACLRule rule1, rule2;
		IResultSet resultSet;
		Iterator<IResultSet> it;
		Map<String, Object> row;

		// a new AP[dpid:00:00:00:00:00:00:00:01 port:1 ip:10.0.0.1] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:01"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.1"), IPv6Address.NONE, DatapathId.of(1), OFPort.of(1));
		
		// a new AP[dpid:00:00:00:00:00:00:00:02 port:1 ip:10.0.0.3] appears
		deviceManager.learnEntity(MacAddress.of("00:00:00:00:00:03"),
				VlanVid.ZERO, IPv4Address.of("10.0.0.3"), IPv6Address.NONE, DatapathId.of(2), OFPort.of(1));
		
		// rule1 indicates host(10.0.0.0/28) can not access TCP port 80 in host(10.0.0.254/32)
		rule1 = new ACLRule();
		rule1.setNw_src("10.0.0.0/28");
		cidr = IPAddressUtil.parseCIDR("10.0.0.0/28");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		rule1.setNw_dst("10.0.0.254/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.254/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule1.setAction(Action.DENY);

		assertEquals(acl.addRule(rule1), true);
		assertEquals(acl.getRules().size(), 1);

		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:01");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:02");
			assertEquals(row.get("priority").toString(),"30000");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src").toString(),"10.0.0.0/28");
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.254/32");
			assertEquals(row.get("ip_proto").toString(),"6");
			assertEquals(row.get("tp_dst").toString(),"80");
			assertEquals(row.get("actions"), null);
		}
		
		// rule3 indicates that no ICMP packets can reach host[10.0.0.3/32]
		rule2 = new ACLRule();
		rule2.setNw_dst("10.0.0.3/32");
		cidr = IPAddressUtil.parseCIDR("10.0.0.3/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(1);
		rule2.setAction(Action.DENY);

		assertEquals(acl.addRule(rule2), true);
		assertEquals(acl.getRules().size(), 2);
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_2_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		while(it.hasNext()){
			row = it.next().getRow();
			assertEquals(row.get("switch").toString(),"00:00:00:00:00:00:00:02");
			assertEquals(row.get("priority").toString(),"29999");
			assertEquals(row.get("eth_type").toString(),"2048");
			assertEquals(row.get("ipv4_src"), null);
			assertEquals(row.get("ipv4_dst").toString(),"10.0.0.3/32");
			assertEquals(row.get("ip_proto").toString(),"1");
			assertEquals(row.get("tp_dst"), null);
			assertEquals(row.get("actions"), null);
		}
		
		// remove all rules and all relevant flow entries will be removed
		acl.removeAllRules();
		assertEquals(acl.getRules().size(),0);
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:01");
		it = resultSet.iterator();
		assertEquals(it.hasNext(), false);

		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_1_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		assertEquals(it.hasNext(), false);
		
		resultSet = storageService.getRow(
				StaticFlowEntryPusher.TABLE_NAME, "ACLRule_2_00:00:00:00:00:00:00:02");
		it = resultSet.iterator();
		assertEquals(it.hasNext(), false);
		
	}
}
