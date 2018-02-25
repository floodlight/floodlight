package net.floodlightcontroller.dhcpserver;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.PacketFactory;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.types.*;

import javax.xml.crypto.Data;
import java.util.*;

import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.expect;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 2/22/18
 */
public class DHCPMessageHandlerTest extends FloodlightTestCase {

    private FloodlightModuleContext fmc;
    private IOFSwitchService switchService;

    private IOFSwitch sw;
    private String swDPIDStr = "00:00:00:00:00:00:00:01";
    private DatapathId swDPID = DatapathId.of(swDPIDStr);
    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

    private MacAddress clientMac = MacAddress.of(1);
    private DHCPMessageHandler handler = new DHCPMessageHandler();
    private Ethernet dhcpEth;
    private OFPacketIn dhcpPacketIn;
    private DHCP dhcpPayload;


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

        // Load mock switches to the switch map
        Map<DatapathId, IOFSwitch> switches = new HashMap<>();
        switches.put(swDPID, sw);
        mockSwitchManager.setSwitches(switches);

        dhcpEth = PacketFactory.DhcpDiscoveryRequestEthernet(clientMac);
        dhcpPacketIn = PacketFactory.DhcpDiscoveryRequestOFPacketIn(sw, clientMac);
        dhcpPayload = DHCPServerUtils.getDHCPayload(dhcpEth);

    }

    private DHCPInstance initInstance() {
        return DHCPInstance.createBuilder().setName("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of(1))
                .setBroadcastIP(IPv4Address.of("192.168.1.255"))
                .setRouterIP(IPv4Address.of("10.0.0.1"))
                .setSubnetMask(IPv4Address.of("255.255.255.0"))
                .setStartIP(IPv4Address.of("10.0.0.2"))
                .setEndIP(IPv4Address.of("10.0.0.10"))
                .setLeaseTimeSec(10)
                .setDNSServers(Arrays.asList(IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))
                .setNTPServers(Arrays.asList(IPv4Address.of("10.0.0.3"), IPv4Address.of("10.0.0.4")))
                .setIPforwarding(true)
                .setDomainName("testDomainName")
                .setClientMembers(Sets.newHashSet(MacAddress.of("00:11:22:33:44:55"), MacAddress.of("55:44:33:22:11:00")))
                .setVlanMembers(Sets.newHashSet(VlanVid.ofVlan(100), VlanVid.ofVlan(200)))
                .setNptMembers(Sets.newHashSet(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)), new NodePortTuple(DatapathId.of(2L), OFPort.of(2))))
                .build();

    }

    /* Tests for buildDHCPOfferMessage() */
    @Test
    public void testBuildDHCPOfferMessage() throws Exception {
        DHCPInstance instance = initInstance();
        IPv4Address yiaddr = instance.getDHCPPool().assignLeaseToClient(clientMac, instance.getLeaseTimeSec()).get();
        MacAddress chaddr = dhcpPayload.getClientHardwareAddress();


        List<Byte> requestOrder = handler.getRequestedParameters(dhcpPayload, false);
        DHCP dhcpOffer = handler.buildDHCPOfferMessage(instance, clientMac, yiaddr, instance.getRouterIP(),
                                        dhcpPayload.getTransactionId(), requestOrder);


        // DHCP header should equal to the instance setup
        assertEquals(DHCP.DHCPOpCode.OpCode_Reply.getValue(), dhcpOffer.getOpCode());
        assertEquals(chaddr, dhcpOffer.getClientHardwareAddress());
        assertEquals(dhcpPayload.getTransactionId(), dhcpOffer.getTransactionId());
        assertEquals(IPv4Address.of("0.0.0.0"), dhcpOffer.getClientIPAddress());    // Client IP should be "0.0.0.0" in DHCP offer message
        assertEquals(instance.getServerID(), dhcpOffer.getServerIPAddress());

        // lease time, renew time and rebinding time in DHCP offer message should be equal to DHCP instance setup
        assertArrayEquals(DHCPServerUtils.intToBytes(instance.getLeaseTimeSec()),
                            dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_LeaseTime).getData());

        assertArrayEquals(DHCPServerUtils.intToBytes(instance.getRenewalTimeSec()),
                            dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_RenewalTime).getData());

        assertArrayEquals(DHCPServerUtils.intToBytes(instance.getRebindTimeSec()),
                            dhcpOffer.getOption(DHCP.DHCPOptionCode.OPtionCode_RebindingTime).getData());


        // The lease IP address for client in DHCP offer message should be equal to DHCP instance setup
        assertEquals(yiaddr, dhcpOffer.getYourIPAddress());

        // The router IP address for client's subnet in DHCP offer message should be equal to DHCP instance setup
        assertArrayEquals(instance.getRouterIP().getBytes(),
                            dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_Router).getData());

        // The subnet mask for client in DHCP offer message should be equal to DHCP instance setup
        assertArrayEquals(instance.getSubnetMask().getBytes(),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_SubnetMask).getData());

    }


    @Test
    public void testBuildDHCPOfferMessageWhenClientRequestAnIP() throws Exception {
        DHCPInstance instance = initInstance();
        IPv4Address yiaddr = instance.getDHCPPool().assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.5"), clientMac, instance.getLeaseTimeSec()).get();
        MacAddress chaddr = dhcpPayload.getClientHardwareAddress();

        List<Byte> requestOrder = handler.getRequestedParameters(dhcpPayload, false);
        DHCP dhcpOffer = handler.buildDHCPOfferMessage(instance, clientMac, yiaddr, instance.getRouterIP(),
                dhcpPayload.getTransactionId(), requestOrder);


        // DHCP header should equal to the instance setup
        assertEquals(DHCP.DHCPOpCode.OpCode_Reply.getValue(), dhcpOffer.getOpCode());
        assertEquals(chaddr, dhcpOffer.getClientHardwareAddress());
        assertEquals(dhcpPayload.getTransactionId(), dhcpOffer.getTransactionId());
        assertEquals(IPv4Address.of("0.0.0.0"), dhcpOffer.getClientIPAddress());    // Client IP should be "0.0.0.0" in DHCP offer message
        assertEquals(instance.getServerID(), dhcpOffer.getServerIPAddress());

        // lease time, renew time and rebinding time in DHCP offer message should be equal to DHCP instance setup
        assertArrayEquals(DHCPServerUtils.intToBytes(instance.getLeaseTimeSec()),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_LeaseTime).getData());

        assertArrayEquals(DHCPServerUtils.intToBytes(instance.getRenewalTimeSec()),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_RenewalTime).getData());

        assertArrayEquals(DHCPServerUtils.intToBytes(instance.getRebindTimeSec()),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OPtionCode_RebindingTime).getData());


        // The lease IP address for client in DHCP offer message should be equal to DHCP instance setup
        assertEquals(yiaddr, dhcpOffer.getYourIPAddress());

        // The router IP address for client's subnet in DHCP offer message should be equal to DHCP instance setup
        assertArrayEquals(instance.getRouterIP().getBytes(),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_Router).getData());

        // Try to set another router IP for client's subnet and test
        instance.getBuilder().setRouterIP(IPv4Address.of("10.0.0.10")).build();
        assertArrayEquals(instance.getRouterIP().getBytes(),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_Router).getData());

        // The subnet mask for client in DHCP offer message should be equal to DHCP instance setup
        assertArrayEquals(instance.getSubnetMask().getBytes(),
                dhcpOffer.getOption(DHCP.DHCPOptionCode.OptionCode_SubnetMask).getData());

    }

    @Test
    public void testBuildDHCPOfferMessageWhenConfigureStaticIP() throws Exception {
        DHCPInstance instance = initInstance();
        instance = instance.getBuilder()
                .setStaticAddresses(MacAddress.of(9), IPv4Address.of("10.0.0.9"))
                .setStaticAddresses(MacAddress.of(10), IPv4Address.of("10.0.0.10"))
                .build();

        // Client registered as static DHCP binding but request another IP, return pre-configured static IP as lease IP
        IPv4Address yiaddr = instance.getDHCPPool().assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.5"), MacAddress.of(9), instance.getLeaseTimeSec()).get();

        List<Byte> requestOrder = handler.getRequestedParameters(dhcpPayload, false);
        DHCP dhcpOffer = handler.buildDHCPOfferMessage(instance, clientMac, yiaddr, instance.getRouterIP(),
                dhcpPayload.getTransactionId(), requestOrder);

        assertEquals(IPv4Address.of("10.0.0.9"), dhcpOffer.getYourIPAddress());


        // Client registered as static DHCP binding and request static IP, directly return pre-configured static IP as lease IP
        IPv4Address yiaddr1 = instance.getDHCPPool().assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.9"), MacAddress.of(9), instance.getLeaseTimeSec()).get();
        List<Byte> requestOrder1 = handler.getRequestedParameters(dhcpPayload, false);
        DHCP dhcpOffer1 = handler.buildDHCPOfferMessage(instance, clientMac, yiaddr1, instance.getRouterIP(),
                dhcpPayload.getTransactionId(), requestOrder1);

        assertEquals(IPv4Address.of("10.0.0.9"), dhcpOffer1.getYourIPAddress());

        // Client not registered as static DHCP binding and request an static IP, will return an available IP but not the static one
        IPv4Address yiaddr2 = instance.getDHCPPool().assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.9"), MacAddress.of(1), instance.getLeaseTimeSec()).get();
        List<Byte> requestOrde2 = handler.getRequestedParameters(dhcpPayload, false);
        DHCP dhcpOffer2 = handler.buildDHCPOfferMessage(instance, clientMac, yiaddr2, instance.getRouterIP(),
                dhcpPayload.getTransactionId(), requestOrde2);

        assertNotEquals(IPv4Address.of("10.0.0.9"), dhcpOffer2.getYourIPAddress());

    }

    // Any case that return lease IP is not exist?

    /* Tests for handleDHCPDiscover() */
    @Test
    public void testHandleDHCPDiscover() throws Exception {
        DHCPInstance instance = initInstance();
        IPv4Address clientIP = IPv4Address.NONE;

        OFPacketOut dhcpOffer = handler.handleDHCPDiscover(sw, OFPort.of(1), instance, clientIP, dhcpPayload);

        OFActionOutput output = sw.getOFFactory().actions().buildOutput()
                                .setMaxLen(0xffFFffFF)
                                .setPort(OFPort.of(1))
                                .build();

        assertEquals(output, dhcpOffer.getActions().get(0));

    }

    /* Tests for handleDHCPRequest() */
    @Test
    public void handleRequestWhenClientIsInitRebootState() throws Exception {
        MacAddress chaddr = dhcpPayload.getClientHardwareAddress();
        IPv4Address ciaddr = IPv4Address.NONE;

        // Send Ack is true when client "request IP" is correct
        DHCPInstance instance = initInstance();
        IPv4Address requestIP = IPv4Address.of("10.0.0.5");
        instance.getDHCPPool().assignLeaseToClientWithRequestIP(requestIP, chaddr, 60);
        boolean sendAck = handler.handleInitReboot(instance, requestIP, instance.getRouterIP(), chaddr, ciaddr);

        assertTrue(sendAck);

        // Send Ack fails if client request IP is incorrect
        DHCPInstance instance1 = initInstance();
        IPv4Address requestIP1 = IPv4Address.of("192.168.0.1");
        instance1.getDHCPPool().assignLeaseToClientWithRequestIP(requestIP1, chaddr, 60);
        boolean sendAck1 = handler.handleInitReboot(instance1, requestIP, instance1.getRouterIP(), chaddr, ciaddr);

        assertFalse(sendAck1);

        // Send Ack fails if client is on different subnet
        DHCPInstance instance2 = initInstance();
        IPv4Address requestIP2 = IPv4Address.of("10.0.0.2");
        instance2.getDHCPPool().assignLeaseToClientWithRequestIP(requestIP2, chaddr, 60);
        boolean sendAck2 = handler.handleInitReboot(instance2, requestIP, IPv4Address.of("192.168.0.1"), chaddr, ciaddr);

        assertFalse(sendAck2);

        // Send Ack fails if client IP is not zero
        DHCPInstance instance3 = initInstance();
        IPv4Address requestIP3 = IPv4Address.of("10.0.0.2");
        instance3.getDHCPPool().assignLeaseToClientWithRequestIP(requestIP3, chaddr, 60);
        boolean sendAck3 = handler.handleInitReboot(instance2, requestIP, IPv4Address.of("192.168.0.1"), chaddr, IPv4Address.of("10.0.0.1"));

        assertFalse(sendAck3);

    }

    @Test
    public void handRequestWhenClientIsSelectingState() throws Exception {



    }



}
