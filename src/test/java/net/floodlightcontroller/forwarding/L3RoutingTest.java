package net.floodlightcontroller.forwarding;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
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

    private VirtualGatewayInstance initGateway() {
        // For simplicity, could set multiple virtual interface mac as same gateway mac, as they're virtual resources
        VirtualGatewayInstance gw = new VirtualGatewayInstance("gateway-1", "aa:bb:cc:dd:ee:ff");
        VirtualGatewayInterface interface1 = new VirtualGatewayInterface("gateway-1", "interface-1",
                gw.getGatewayMac().toString(), "10.0.0.1", "255.255.255.0");
        VirtualGatewayInterface interface2 = new VirtualGatewayInterface("gateway-1","interface-2",
                gw.getGatewayMac().toString(), "20.0.0.1", "255.255.255.0");

        gw.addInterface(interface1);
        gw.addInterface(interface2);

        return gw;
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

    // TODO: may test IPv6 latter


    @Test
    public void testGatewayInterfaceIPSelection() throws Exception {
        // "30.0.0.1" is not a configured gateway interface IP address
        assertFalse(gateway.isAGatewayInft(IPv4Address.of("30.0.0.1")));

        // "10.0.0.1" is a configured gateway interface IP address
        assertTrue(gateway.isAGatewayInft(IPv4Address.of("10.0.0.1")));

        // If destination IP is "10.0.0.25", the packet should select gateway interface "10.0.0.1" to go
        IPv4Address dstIP = IPv4Address.of("10.0.0.25");
        assertEquals(IPv4Address.of("10.0.0.1"), gateway.findGatewayInft(dstIP).get().getIp());

        // If destination IP is "20.0.0.10", the packet should select gateway interface "20.0.0.1" to go
        IPv4Address dstIP1 = IPv4Address.of("20.0.0.10");
        assertEquals(IPv4Address.of("20.0.0.1"), gateway.findGatewayInft(dstIP1).get().getIp());

    }



}
