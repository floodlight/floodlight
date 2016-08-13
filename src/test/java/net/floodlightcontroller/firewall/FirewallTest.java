/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by Amer Tahir
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

package net.floodlightcontroller.firewall;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.reset;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

/**
 * Unit test for stateless firewall implemented as a Google Summer of Code project.
 *
 * @author Amer Tahir
 */
public class FirewallTest extends FloodlightTestCase {
	protected IRoutingService routingService;
    protected FloodlightContext cntx;
    protected OFPacketIn packetIn;
    protected IOFSwitch sw;
    protected IPacket tcpPacket;
    protected IPacket broadcastARPPacket;
    protected IPacket ARPReplyPacket;
    protected IPacket broadcastIPPacket;
    protected IPacket tcpPacketReply;
    protected IPacket broadcastMalformedPacket;
    private Firewall firewall;
    private MockDebugCounterService debugCounterService;
    public static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";
    private static final short APP_ID = 30;
    static {
                AppCookie.registerApp(APP_ID, "Firewall");
        }
    private static final U64 DENY_BCAST_COOKIE = AppCookie.makeCookie(APP_ID, 0xaaaaaaL);
    private static final U64 ALLOW_BCAST_COOKIE = AppCookie.makeCookie(APP_ID, 0x555555L);
    private static final U64 RULE_MISS_COOKIE = AppCookie.makeCookie(APP_ID, 0xffffffL);


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        cntx = new FloodlightContext();
        mockFloodlightProvider = getMockFloodlightProvider();
        mockSwitchManager = getMockSwitchService();
        debugCounterService = new MockDebugCounterService(); 
        firewall = new Firewall();
        MemoryStorageSource storageService = new MemoryStorageSource();
        RestApiServer restApi = new RestApiServer();
        routingService = createMock(IRoutingService.class);

        // Mock switches
        DatapathId dpid = DatapathId.of(TestSwitch1DPID);
        sw = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(dpid).anyTimes();
        expect(sw.getOFFactory()).andReturn(OFFactories.getFactory(OFVersion.OF_13)).anyTimes();
        replay(sw);
        // Load the switch map
        Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
        switches.put(dpid, sw);
        mockSwitchManager.setSwitches(switches);

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IDebugCounterService.class, debugCounterService);
        fmc.addService(IOFSwitchService.class, mockSwitchManager);
        fmc.addService(IFirewallService.class, firewall);
        fmc.addService(IStorageSourceService.class, storageService);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IRoutingService.class, routingService);

        debugCounterService.init(fmc);
        storageService.init(fmc);
        restApi.init(fmc);
        firewall.init(fmc);
        debugCounterService.startUp(fmc);
        storageService.startUp(fmc);
        firewall.startUp(fmc);

        // Build our test packet
        this.tcpPacket = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(EthType.IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new TCP()
                .setSourcePort((short) 81)
                .setDestinationPort((short) 80)
                .setPayload(new Data(new byte[] {0x01}))));

        // Build a broadcast ARP packet
        this.broadcastARPPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(EthType.ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setOpCode(ARP.OP_REQUEST)
                .setHardwareAddressLength((byte)6)
                .setProtocolAddressLength((byte)4)
                .setSenderHardwareAddress(MacAddress.of("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4Address.of("192.168.1.1"))
                .setTargetHardwareAddress(MacAddress.of("00:00:00:00:00:00"))
                .setTargetProtocolAddress(IPv4Address.of("192.168.1.2"))
                .setPayload(new Data(new byte[] {0x01})));

        // Build a ARP packet
        this.ARPReplyPacket = new Ethernet()
        .setDestinationMACAddress("00:44:33:22:11:00")
        .setSourceMACAddress("00:11:22:33:44:55")
        .setVlanID((short) 42)
        .setEtherType(EthType.ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setOpCode(ARP.OP_REQUEST)
                .setHardwareAddressLength((byte)6)
                .setProtocolAddressLength((byte)4)
                .setSenderHardwareAddress(MacAddress.of("00:11:22:33:44:55"))
                .setSenderProtocolAddress(IPv4Address.of("192.168.1.2"))
                .setTargetHardwareAddress(MacAddress.of("00:44:33:22:11:00"))
                .setTargetProtocolAddress(IPv4Address.of("192.168.1.1"))
                .setPayload(new Data(new byte[] {0x01})));

        // Build a broadcast IP packet
        this.broadcastIPPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(EthType.IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.255")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));

        // Build a malformed broadcast packet
        this.broadcastMalformedPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
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

        this.tcpPacketReply = new Ethernet()
        .setDestinationMACAddress("00:44:33:22:11:00")
        .setSourceMACAddress("00:11:22:33:44:55")
        .setVlanID((short) 42)
        .setEtherType(EthType.IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.2")
                .setDestinationAddress("192.168.1.1")
                .setPayload(new TCP()
                .setSourcePort((short) 80)
                .setDestinationPort((short) 81)
                .setPayload(new Data(new byte[] {0x02}))));
    }

    protected void setPacketIn(IPacket packet) {
        byte[] serializedPacket = packet.serialize();
        // Build the PacketIn
        this.packetIn = OFFactories.getFactory(OFVersion.OF_13).buildPacketIn()
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(OFFactories.getFactory(OFVersion.OF_13).buildMatch().setExact(MatchField.IN_PORT, OFPort.of(1)).build())
                .setData(serializedPacket)
                .setReason(OFPacketInReason.NO_MATCH)
                .build();
        // Add the packet to the context store
        IFloodlightProviderService.bcStore.
        put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                (Ethernet)packet);
    }
    
    public boolean compareU64ListsOrdered(List<Masked<U64>> a,List<Masked<U64>> b){
    	Object[] aArray = a.toArray();
    	Object[] bArray = b.toArray();
    	if (aArray.length != bArray.length) return false;
    	for(int i = 0; i<aArray.length; i++){
    		if(aArray[i].equals(bArray[i]) == false){
    			return false;
    		}
    	}
    	return true;
    }
    
    @Test
    public void enableFirewall() throws Exception{
		Capture<ArrayList<Masked<U64>>> wc1 = EasyMock.newCapture(CaptureType.ALL);
		routingService.handleRoutingDecisionChange(capture(wc1));
		ArrayList<Masked<U64>> test_changes = new ArrayList<Masked<U64>>();

		replay(routingService);
		firewall.enableFirewall(true);
		verify(routingService);
		test_changes.add(Masked.of(Firewall.DEFAULT_COOKIE, AppCookie.getAppFieldMask()));

		assertTrue(compareU64ListsOrdered(wc1.getValue(),test_changes));
    }

    @Test
    public void testNoRules() throws Exception {
        // enable firewall first
        firewall.enableFirewall(true);
        // simulate a packet-in event
        this.setPacketIn(tcpPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        assertEquals(0, firewall.rules.size());

        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        // no rules to match, so firewall should deny
        assertEquals(decision.getRoutingAction(), IRoutingDecision.RoutingAction.DROP);
        assertEquals(RULE_MISS_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testReadRulesFromStorage() throws Exception {
        Capture<ArrayList<Masked<U64>>> wc1 = EasyMock.newCapture(CaptureType.ALL);
        routingService.handleRoutingDecisionChange(capture(wc1));
        ArrayList<Masked<U64>> test_changes = new ArrayList<Masked<U64>>();
        U64 singleRuleMask = AppCookie.getAppFieldMask().or(AppCookie.getUserFieldMask());
        // add 2 rules first
        FirewallRule rule = new FirewallRule();
        rule.in_port = OFPort.of(2);
        rule.dl_src = MacAddress.of("00:00:00:00:00:01");
        rule.dl_dst = MacAddress.of("00:00:00:00:00:02");
        rule.priority = 1;
        rule.action = FirewallRule.FirewallAction.DROP;
        replay(routingService);
        firewall.addRule(rule);
        verify(routingService);
        test_changes.add(Masked.of(AppCookie.makeCookie(APP_ID, rule.ruleid), singleRuleMask));
        test_changes.add(Masked.of(RULE_MISS_COOKIE, singleRuleMask));
        assertEquals(compareU64ListsOrdered(wc1.getValue(),test_changes),true);
        reset(routingService);
        // next rule
        wc1 = EasyMock.newCapture(CaptureType.ALL);
        test_changes = new ArrayList<Masked<U64>>();
        routingService.handleRoutingDecisionChange(capture(wc1));
        rule = new FirewallRule();
        rule.in_port = OFPort.of(3);
        rule.dl_src = MacAddress.of("00:00:00:00:00:02");
        rule.dl_dst = MacAddress.of("00:00:00:00:00:01");
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        rule.tp_dst = TransportPort.of(80);
        rule.priority = 2;
        rule.action = FirewallRule.FirewallAction.ALLOW;
        replay(routingService);
        firewall.addRule(rule);
        verify(routingService);
        test_changes.add(Masked.of(AppCookie.makeCookie(APP_ID, rule.ruleid), singleRuleMask));
        test_changes.add(Masked.of(RULE_MISS_COOKIE, singleRuleMask));
        assertTrue(compareU64ListsOrdered(wc1.getValue(),test_changes));
        reset(routingService);
        
        List<FirewallRule> rules = firewall.readRulesFromStorage();
        
        // verify rule 1
        FirewallRule r = rules.get(0);
        assertEquals(r.in_port, OFPort.of(2));
        assertEquals(r.priority, 1);
        assertEquals(r.dl_src, MacAddress.of("00:00:00:00:00:01"));
        assertEquals(r.dl_dst, MacAddress.of("00:00:00:00:00:02"));
        assertEquals(r.action, FirewallRule.FirewallAction.DROP);
        // verify rule 2
        r = rules.get(1);
        assertEquals(r.in_port, OFPort.of(3));
        assertEquals(r.priority, 2);
        assertEquals(r.dl_src, MacAddress.of("00:00:00:00:00:02"));
        assertEquals(r.dl_dst, MacAddress.of("00:00:00:00:00:01"));
        assertEquals(r.nw_proto, IpProtocol.TCP);
        assertEquals(r.tp_dst, TransportPort.of(80));
        assertEquals(r.any_nw_proto, false);
        assertEquals(r.action, FirewallRule.FirewallAction.ALLOW);
    }

    @Test
    public void testRuleInsertionIntoStorage() throws Exception {
        // add TCP rule
        FirewallRule rule = new FirewallRule();
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        rule.priority = 1;
        firewall.addRule(rule);

        List<Map<String, Object>> rulesFromStorage = firewall.getStorageRules();
        assertEquals(1, rulesFromStorage.size());
        assertEquals(Integer.parseInt((String)rulesFromStorage.get(0).get("ruleid")), rule.ruleid);
    }

    @Test
    public void testRuleDeletion() throws Exception {
        // add TCP rule
        FirewallRule rule = new FirewallRule();
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        rule.priority = 1;
        firewall.addRule(rule);
        int rid = rule.ruleid;
        reset(routingService);

    	Capture<ArrayList<Masked<U64>>> wc1 = EasyMock.newCapture(CaptureType.ALL);
    	routingService.handleRoutingDecisionChange(capture(wc1));
        ArrayList<Masked<U64>> test_changes = new ArrayList<Masked<U64>>();
        U64 singleRuleMask = AppCookie.getAppFieldMask().or(AppCookie.getUserFieldMask());

        List<Map<String, Object>> rulesFromStorage = firewall.getStorageRules();
        assertEquals(1, rulesFromStorage.size());
        assertEquals(Integer.parseInt((String)rulesFromStorage.get(0).get("ruleid")), rid);

        // delete rule
        replay(routingService);
        firewall.deleteRule(rid);
        verify(routingService);
        test_changes.add(Masked.of(AppCookie.makeCookie(APP_ID, rule.ruleid), singleRuleMask));
        rulesFromStorage = firewall.getStorageRules();
        assertEquals(0, rulesFromStorage.size());
        assertTrue(compareU64ListsOrdered(wc1.getValue(),test_changes));
        reset(routingService);
    }

    @Test
    public void testFirewallDisabled() throws Exception {
        // firewall isn't enabled by default
        // so, it shouldn't make any decision

        // add TCP rule
        FirewallRule rule = new FirewallRule();
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        rule.priority = 1;
        firewall.addRule(rule);

        this.setPacketIn(tcpPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        assertEquals(1, firewall.rules.size());

        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertNull(decision);
    }

    @Test
    public void testSimpleAllowRule() throws Exception {
        // enable firewall first
        firewall.enableFirewall(true);

        // add TCP rule
        FirewallRule rule = new FirewallRule();
        rule.dl_type = EthType.IPv4;
        rule.any_dl_type = false;
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        // source is IP 192.168.1.2
        rule.nw_src_prefix_and_mask = IPv4AddressWithMask.of("192.168.1.2/32");
        rule.any_nw_src = false;
        // dest is network 192.168.1.0/24
        rule.nw_dst_prefix_and_mask = IPv4AddressWithMask.of("192.168.1.0/24");
        rule.any_nw_dst = false;
        rule.priority = 1;
        firewall.addRule(rule);
        U64 TCP_COOKIE = AppCookie.makeCookie(APP_ID, rule.ruleid);

        // simulate a packet-in events

        this.setPacketIn(tcpPacketReply);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD, decision.getRoutingAction());
        assertEquals(TCP_COOKIE, decision.getDescriptor());

        // clear decision
        IRoutingDecision.rtStore.remove(cntx, IRoutingDecision.CONTEXT_DECISION);

        this.setPacketIn(tcpPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(IRoutingDecision.RoutingAction.DROP, decision.getRoutingAction());
        assertEquals(RULE_MISS_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testOverlappingRules() throws Exception {
        firewall.enableFirewall(true);

        // add TCP port 80 (destination only) allow rule
        FirewallRule rule = new FirewallRule();
        rule.dl_type = EthType.IPv4;
        rule.any_dl_type = false;
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        rule.tp_dst = TransportPort.of(80);
        rule.priority = 1;
        firewall.addRule(rule);
        U64 TCP_COOKIE = AppCookie.makeCookie(APP_ID, rule.ruleid);

        // add block all rule
        rule = new FirewallRule();
        rule.action = FirewallRule.FirewallAction.DROP;
        rule.priority = 2;
        firewall.addRule(rule);
        U64 BLOCK_ALL_COOKIE = AppCookie.makeCookie(APP_ID, rule.ruleid);

        assertEquals(2, firewall.rules.size());

        // packet destined to TCP port 80 - should be allowed

        this.setPacketIn(tcpPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(decision.getRoutingAction(), IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
        assertEquals(TCP_COOKIE, decision.getDescriptor());

        // clear decision
        IRoutingDecision.rtStore.remove(cntx, IRoutingDecision.CONTEXT_DECISION);

        // packet destined for port 81 - should be denied

        this.setPacketIn(tcpPacketReply);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(decision.getRoutingAction(), IRoutingDecision.RoutingAction.DROP);
        assertEquals(BLOCK_ALL_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testARP() throws Exception {
        // enable firewall first
        firewall.enableFirewall(true);

        // no rules inserted so all traffic other than broadcast and ARP-request-broadcast should be blocked

        // simulate an ARP broadcast packet-in event

        this.setPacketIn(broadcastARPPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        // broadcast-ARP traffic should be allowed
        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(IRoutingDecision.RoutingAction.MULTICAST, decision.getRoutingAction());
        assertEquals(ALLOW_BCAST_COOKIE, decision.getDescriptor());

        // clear decision
        IRoutingDecision.rtStore.remove(cntx, IRoutingDecision.CONTEXT_DECISION);

        // simulate an ARP reply packet-in event

        this.setPacketIn(ARPReplyPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        // ARP reply traffic should be denied
        decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(decision.getRoutingAction(), IRoutingDecision.RoutingAction.DROP);
        assertEquals(RULE_MISS_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testIPBroadcast() throws Exception {
        // enable firewall first
        firewall.enableFirewall(true);

        // set subnet mask for IP broadcast
        firewall.setSubnetMask("255.255.255.0");

        // no rules inserted so all traffic other than broadcast and ARP-request-broadcast should be blocked

        // simulate a packet-in event

        this.setPacketIn(broadcastIPPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        // broadcast traffic should be allowed
        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(IRoutingDecision.RoutingAction.MULTICAST, decision.getRoutingAction());
        assertEquals(ALLOW_BCAST_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testMalformedIPBroadcast() throws Exception {
        // enable firewall first
        firewall.enableFirewall(true);

        // no rules inserted so all traffic other than broadcast and ARP-request-broadcast should be blocked

        // simulate a packet-in event

        this.setPacketIn(broadcastMalformedPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        // malformed broadcast traffic should NOT be allowed
        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(decision.getRoutingAction(), IRoutingDecision.RoutingAction.DROP);
        assertEquals(DENY_BCAST_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testLayer2Rule() throws Exception {
        // enable firewall first
        firewall.enableFirewall(true);

        // add L2 rule
        FirewallRule rule = new FirewallRule();
        rule.dl_src = MacAddress.of("00:44:33:22:11:00");
        rule.any_dl_src = false;
        rule.dl_dst = MacAddress.of("00:11:22:33:44:55");
        rule.any_dl_dst = false;
        rule.priority = 1;
        firewall.addRule(rule);
        U64 L2_LAYER_COOKIE = AppCookie.makeCookie(APP_ID, rule.ruleid);

        // add TCP deny all rule
        rule = new FirewallRule();
        rule.nw_proto = IpProtocol.TCP;
        rule.any_nw_proto = false;
        rule.priority = 2;
        rule.action = FirewallRule.FirewallAction.DROP;
        firewall.addRule(rule);

        // simulate a packet-in event

        this.setPacketIn(tcpPacket);
        firewall.receive(sw, this.packetIn, cntx);
        verify(sw);

        IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(decision.getRoutingAction(), IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
        assertEquals(L2_LAYER_COOKIE, decision.getDescriptor());
    }

    @Test
    public void testDuplicateLayer2Rule() {
        FirewallRule rule1 = new FirewallRule();
        rule1.dl_src = MacAddress.of("00:44:33:22:11:00");
        rule1.any_dl_src = false;
        rule1.dl_dst = MacAddress.of("00:11:22:33:44:55");
        rule1.any_dl_dst = false;
        rule1.any_dpid = false;
        rule1.dpid = DatapathId.of(0x0102030405060708L);
        rule1.any_in_port = false;
        rule1.in_port = OFPort.LOCAL;
        rule1.priority = 1;
        rule1.ruleid = rule1.genID();
        // Rule same as itself.
        assertTrue(rule1.isSameAs(rule1));

        FirewallRule rule2 = new FirewallRule();
        rule2.dl_src = MacAddress.of("00:44:33:22:11:00");
        rule2.any_dl_src = false;
        rule2.dl_dst = MacAddress.of("00:11:22:33:44:55");
        rule2.any_dl_dst = false;
        rule2.any_dpid = false;
        rule2.dpid = DatapathId.of(0x0102030405060708L);
        rule2.any_in_port = false;
        rule2.in_port = OFPort.LOCAL;
        rule2.priority = 1;
        rule2.ruleid = rule2.genID();
        // Separate object instances, but otherwise identical rule.
        assertTrue(rule1.isSameAs(rule2));

        // Change dl_src, rules no longer "same"
        MacAddress tmp = rule2.dl_src;
        rule2.dl_src = MacAddress.of("08:01:02:03:04:05");
        rule2.ruleid = rule2.genID();
        assertFalse(rule1.isSameAs(rule2));

        // Restore dl_src, rules should be "same" again
        rule2.dl_src = tmp;
        rule2.ruleid = rule2.genID();
        assertTrue(rule1.isSameAs(rule2));

        // Change dl_dst, rules no longer "same"
        rule2.dl_dst = MacAddress.of("00:01:02:03:04:05");
        assertFalse(rule1.isSameAs(rule2));

    }
    
    /* Testing to make sure that the cookies are properly formatted with the correct info before hitting the firewall. */
    @Test
    public void cookieAddedSuccessfully() {
    	assertEquals("DENY_BCAST_COOKIE app_id is not correct", APP_ID, AppCookie.extractApp(DENY_BCAST_COOKIE));
    	
    	assertEquals("DENY_BCAST_COOKIE user_id is not correct", 0xaaaaaaL, AppCookie.extractUser(DENY_BCAST_COOKIE));
    	assertEquals("ALLOW_BCAST_COOKIE app_id is not correct", APP_ID, AppCookie.extractApp(DENY_BCAST_COOKIE));
    	assertEquals("ALLOW_BCAST_COOKIE user_id is not correct", 0x555555L, AppCookie.extractUser(ALLOW_BCAST_COOKIE));
      	assertEquals("RULE_MISS_COOKIE app_id is not correct", APP_ID, AppCookie.extractApp(DENY_BCAST_COOKIE));
      	assertEquals("RULE_MISS_COOKIE user_id is not correct", 0xffffffL, AppCookie.extractUser(RULE_MISS_COOKIE));
    }
}
