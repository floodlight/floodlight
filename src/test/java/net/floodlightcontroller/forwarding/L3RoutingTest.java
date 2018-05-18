package net.floodlightcontroller.forwarding;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.routing.VirtualGatewayInstance;
import net.floodlightcontroller.routing.VirtualGatewayInterface;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;

import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.junit.Assert.*;


/**
 * @author Qing Wang (qw@g.clemson.edu) at 4/6/18
 */
public class L3RoutingTest extends FloodlightTestCase {

    private FloodlightModuleContext fmc;
    private IOFSwitchService switchService;

    private IOFSwitch sw;
    private String swDPIDStr = "00:00:00:00:00:00:00:01";
    private DatapathId swDPID = DatapathId.of(swDPIDStr);
    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

    private VirtualGatewayInstance gateway;

    private IPacket testPacket;
    private OFPacketIn packetIn;
    private OFPacketIn packetInIpv6;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        fmc = new FloodlightModuleContext();
        switchService = getMockSwitchService();
        fmc.addService(IOFSwitchService.class, switchService);

        sw = EasyMock.createMock(IOFSwitch.class);
        reset(sw);
        expect(sw.getId()).andReturn(swDPID).anyTimes();
        expect(sw.getOFFactory()).andReturn(factory).anyTimes();
        replay(sw);

        // Load mock switches to switch map
        Map<DatapathId, IOFSwitch> switches = new HashMap<>();
        switches.put(swDPID, sw);
        mockSwitchManager.setSwitches(switches);

        // L3 Initialization
        packetIn = buildIPv4PacketIn();
        gateway = initGateway();

    }

    private IPacket buildTestPacket() {
        return new Ethernet()
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
    }

    private OFPacketIn buildIPv4PacketIn() {
        return factory.buildPacketIn()
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
                .setData(buildTestPacket().serialize())
                .setReason(OFPacketInReason.NO_MATCH)
                .build();
    }

    private VirtualGatewayInstance initGateway() {
        VirtualGatewayInstance instance;
        VirtualGatewayInstance.VirtualGatewayInstanceBuilder builder = VirtualGatewayInstance.createInstance("gateway-1");

        Map<String, VirtualGatewayInterface> interfaces = new HashMap<>();
        VirtualGatewayInterface interface1 = new VirtualGatewayInterface("interface-1",
                "10.0.0.1", "255.255.255.0");
        VirtualGatewayInterface interface2 = new VirtualGatewayInterface("interface-2",
                "20.0.0.1", "255.255.255.0");
        interfaces.put(interface1.getInterfaceName(), interface1);
        interfaces.put(interface2.getInterfaceName(), interface2);

        builder.setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff"));
        builder.setInterfaces(interfaces);

        instance = builder.build();
        instance.addSwitchMember(DatapathId.of(1L));
        instance.addNptMember(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
        instance.addSubnetMember(IPv4AddressWithMask.of("192.168.1.0/24"));

        return instance;
    }


    @Test
    public void testGatewayInterfaceIPSelection() throws Exception {
        // "30.0.0.1" is not a configured gateway interface IP address
        assertFalse(gateway.isAGatewayIntf(IPv4Address.of("30.0.0.1")));

        // "10.0.0.1" is a configured gateway interface IP address
        assertTrue(gateway.isAGatewayIntf(IPv4Address.of("10.0.0.1")));

        // If destination IP is "10.0.0.25", the packet should select gateway interface "10.0.0.1" to go
        IPv4Address dstIP = IPv4Address.of("10.0.0.25");
        assertEquals(IPv4Address.of("10.0.0.1"), gateway.findGatewayInft(dstIP).get().getIp());

        // If destination IP is "20.0.0.10", the packet should select gateway interface "20.0.0.1" to go
        IPv4Address dstIP1 = IPv4Address.of("20.0.0.10");
        assertEquals(IPv4Address.of("20.0.0.1"), gateway.findGatewayInft(dstIP1).get().getIp());
    }


    @Test
    public void testBuildGatewayInstance() throws Exception {
        // Create gateway Instance
        VirtualGatewayInstance instance = initGateway();

        assertNotNull(instance);
        assertNotNull(instance.getName());
        assertNotNull(instance.getGatewayMac());
        assertEquals("gateway-1", instance.getName());
        assertEquals(MacAddress.of("aa:bb:cc:dd:ee:ff"), instance.getGatewayMac());
        assertTrue(instance.getSwitchMembers().contains(DatapathId.of(1L)));
        assertEquals(1, instance.getSwitchMembers().size());
        assertTrue(instance.getNptMembers().contains(new NodePortTuple(DatapathId.of(2L), OFPort.of(1))));
        assertEquals(1, instance.getNptMembers().size());
        assertTrue(instance.getSubsetMembers().contains(IPv4AddressWithMask.of("192.168.1.0/24")));
        assertEquals(1, instance.getSubsetMembers().size());
        assertEquals(2, instance.getInterfaces().size());
        assertNotNull(instance.getInterface("interface-1").get());
        assertFalse(instance.getInterface("interface-10").isPresent());


        // Create gateway instance with only necessary fields
        VirtualGatewayInstance instance1 = VirtualGatewayInstance.createInstance("gateway-2")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();
        assertNotNull(instance1);
        assertEquals("gateway-2", instance1.getName());
        assertEquals(MacAddress.of("aa:bb:cc:dd:ee:ff"), instance1.getGatewayMac());
        assertEquals(0, instance1.getInterfaces().size());
        assertEquals(0, instance1.getSwitchMembers().size());
        assertEquals(0, instance1.getNptMembers().size());
        assertEquals(0, instance1.getSubsetMembers().size());

    }


    @Test(expected = IllegalArgumentException.class)
    public void testBuildGatewayInstanceWithMissingFields() throws Exception {
        // Create virtual gateway instance with invalid name
        VirtualGatewayInstance instance1 = VirtualGatewayInstance.createInstance("").build();
        VirtualGatewayInstance instance2 = VirtualGatewayInstance.createInstance(null).build();

        // Create virtual gateway instance with invalid MAC address
        VirtualGatewayInstance instance3 = VirtualGatewayInstance.createInstance("gateway")
                .setGatewayMac(MacAddress.NONE).build();

        // Create virtual gateway instance without configure switches, node-port-tuples or subnets
        VirtualGatewayInstance instance4 = VirtualGatewayInstance.createInstance("gateway")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();
        assertEquals(0, instance4.getSwitchMembers().size());
        assertEquals(0, instance4.getNptMembers().size());
        assertEquals(0, instance4.getSubsetMembers().size());

    }

    @Test
    public void testAddInterface() throws Exception {
        VirtualGatewayInstance instance = VirtualGatewayInstance.createInstance("gateway-1")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();

        // interface-1 should be correctly added
        VirtualGatewayInterface interface1 = new VirtualGatewayInterface("interface-1",
                "10.0.0.1", "255.255.255.0");
        instance.addInterface(interface1);

        assertEquals(1, instance.getInterfaces().size());
        assertEquals(IPv4Address.of("10.0.0.1"), instance.getInterface("interface-1").get().getIp());

    }

    @Test
    public void removeInferface() throws Exception {
        VirtualGatewayInstance gatewayInstance = initGateway();

        // Interface-2 will not be removed because it haven't be added to gateway instance yet
        assertFalse(gatewayInstance.removeInterface("interface-3"));
        assertEquals(2, gatewayInstance.getInterfaces().size());

        // Interface-1 will be removed
        assertTrue(gatewayInstance.removeInterface("interface-1"));
        assertEquals(1, gatewayInstance.getInterfaces().size());

    }


    @Test
    public void testClearInterface() throws Exception {
        VirtualGatewayInstance instance = initGateway();

        // All interface should be removed
        instance.clearInterfaces();
        assertEquals(0, instance.getInterfaces().size());

    }

    @Test
    public void testUpdateInterface() throws Exception {
        VirtualGatewayInstance instance = initGateway();

        // Gateway MAC address should be updated
        instance = instance.getBuilder().setGatewayMac(MacAddress.of("ff:ee:dd:cc:bb:aa")).build();
        assertEquals(MacAddress.of("ff:ee:dd:cc:bb:aa"), instance.getGatewayMac());

        // interface-1 ip should be updated correctly to "30.0.0.1"
        VirtualGatewayInterface newInterface = new VirtualGatewayInterface("interface-1",
                "30.0.0.1", "255.255.255.0");
        instance.addInterface(newInterface);
        assertEquals(IPv4Address.of("30.0.0.1"), instance.getInterface("interface-1").get().getIp());

        // interface-3 ip shouldn't be updated as there is no "interface-3" added to gateway yet
        assertFalse(instance.getInterface("interface-3").isPresent());
        assertEquals(IPv4Address.of("30.0.0.1"), instance.getInterface("interface-1").get().getIp());
        assertEquals(2, instance.getInterfaces().size());

    }

    @Test
    public void testUpdateInterfaceUsingBuilder() throws Exception {
        VirtualGatewayInstance instance = initGateway();

        // New interface list should replace old interface list when use builder to rebuild instance
        Map<String, VirtualGatewayInterface> interfaces = new HashMap<>();
        VirtualGatewayInterface newInterface3 = new VirtualGatewayInterface("interface-3",
                "30.0.0.1", "255.255.255.0");
        VirtualGatewayInterface newInterface4 = new VirtualGatewayInterface("interface-4",
                "40.0.0.1", "255.255.255.0");
        VirtualGatewayInterface newInterface5 = new VirtualGatewayInterface("interface-5",
                "50.0.0.1", "255.255.255.0");
        interfaces.put(newInterface3.getInterfaceName(), newInterface3);
        interfaces.put(newInterface4.getInterfaceName(), newInterface4);
        interfaces.put(newInterface5.getInterfaceName(), newInterface5);

        instance = instance.getBuilder().setInterfaces(interfaces).build();
        assertEquals(3, instance.getInterfaces().size());
        assertTrue(instance.getInterface("interface-3").isPresent());
        assertTrue(instance.getInterface("interface-4").isPresent());
        assertTrue(instance.getInterface("interface-5").isPresent());
        assertEquals(IPv4Address.of("30.0.0.1"), instance.getInterface("interface-3").get().getIp());
        assertEquals(IPv4Address.of("40.0.0.1"), instance.getInterface("interface-4").get().getIp());
        assertEquals(IPv4Address.of("50.0.0.1"), instance.getInterface("interface-5").get().getIp());
        assertFalse(instance.getInterface("interface-1").isPresent());
        assertFalse(instance.getInterface("interface-2").isPresent());

    }


    @Test
    public void testAddOrRemoveSwitchMemberFromInstance() throws Exception {
        VirtualGatewayInstance instance = VirtualGatewayInstance.createInstance("gateway-1")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();

        // Should have two switch member added
        instance.addSwitchMember(DatapathId.of(1L));
        instance.addSwitchMember(DatapathId.of(1L));
        instance.addSwitchMember(DatapathId.of(2L));

        assertEquals(2, instance.getSwitchMembers().size());

        // Remove one switch member, should only have 1 switch member left
        instance.removeSwitchMember(DatapathId.of(1L));
        assertEquals(1, instance.getSwitchMembers().size());

        // Remove all switch members, now zero members left
        instance.addSwitchMember(DatapathId.of(1L));
        instance.clearSwitchMembers();
        assertEquals(0, instance.getSwitchMembers().size());

    }

    @Test
    public void testAddOrRemoveNptMemberFromInstance() throws Exception {
        VirtualGatewayInstance instance = VirtualGatewayInstance.createInstance("gateway-1")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();

        // Should have four npt member added
        instance.addNptMember(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        instance.addNptMember(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
        instance.addNptMember(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
        instance.addNptMember(new NodePortTuple(DatapathId.of(2L), OFPort.of(2)));

        assertEquals(4, instance.getNptMembers().size());

        // Remove one node-port-tuple, should have 3 member left
        instance.removeNptMember(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        assertEquals(3, instance.getNptMembers().size());

        // Remove all node-port-tuple, now zero member left
        instance.clearNptMembers();
        assertEquals(0, instance.getNptMembers().size());

    }

    @Test
    public void testAddOrRemoveSubnetMemberFromInstance() throws Exception {
        VirtualGatewayInstance instance = VirtualGatewayInstance.createInstance("gateway-1")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();

        // Should have two subnet added
        instance.addSubnetMember(IPv4AddressWithMask.of("10.0.0.0/24"));
        instance.addSubnetMember(IPv4AddressWithMask.of("20.0.0.0/24"));

        assertEquals(2, instance.getSubsetMembers().size());

        // Remove one subnet, should have one left
        instance.removeSubnetMember(IPv4AddressWithMask.of("10.0.0.0/24"));
        assertEquals(1, instance.getSubsetMembers().size());

        // Remove all subnet, should have zero left
        instance.clearSubnetMembers();
        assertEquals(0, instance.getSubsetMembers().size());

    }

    @Test
    public void testRemoveSwitchFromInstance() throws Exception {
        VirtualGatewayInstance instance = VirtualGatewayInstance.createInstance("gateway-1")
                .setGatewayMac(MacAddress.of("aa:bb:cc:dd:ee:ff")).build();

        instance.addSwitchMember(DatapathId.of(1L));
        instance.addSwitchMember(DatapathId.of(2L));

        instance.addNptMember(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        instance.addNptMember(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
        instance.addNptMember(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
        instance.addNptMember(new NodePortTuple(DatapathId.of(2L), OFPort.of(2)));

        // Now remove Switch DPID 1L, both switch member list and npt member list should updated
        instance.removeSwitchFromInstance(DatapathId.of(1L));

        assertEquals(1, instance.getSwitchMembers().size());
        assertEquals(2, instance.getNptMembers().size());

    }



}
