package net.floodlightcontroller.virtualnetwork.forwarding;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFMessageListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.util.MACAddress;

public class VirtualNetworkFilterTest extends FloodlightTestCase {
    protected VirtualNetworkFilter vns;
    
    protected static String guid1 = "guid1";
    protected static String net1 = "net1";
    protected static String gw1 = "1.1.1.1";
    protected static String guid2 = "guid2";
    protected static String net2 = "net2";
    protected static String guid3 = "guid3";
    protected static String net3 = "net3";
    protected static String gw2 = "2.2.2.2";
    
    protected static MACAddress mac1 = 
            new MACAddress(Ethernet.toMACAddress("00:11:22:33:44:55"));
    protected static MACAddress mac2 = 
            new MACAddress(Ethernet.toMACAddress("00:11:22:33:44:66"));
    protected static MACAddress mac3 = 
            new MACAddress(Ethernet.toMACAddress("00:11:22:33:44:77"));
    protected static MACAddress mac4 = 
            new MACAddress(Ethernet.toMACAddress("00:11:22:33:44:88"));
    protected static String hostPort1 = "port1";
    protected static String hostPort2 = "port2";
    protected static String hostPort3 = "port3";
    protected static String hostPort4 = "port4";
    
    // For testing forwarding behavior
    protected IOFSwitch sw1;
    protected FloodlightContext cntx;
    protected OFPacketIn mac1ToMac2PacketIn;
    protected IPacket mac1ToMac2PacketIntestPacket;
    protected byte[] mac1ToMac2PacketIntestPacketSerialized;
    protected OFPacketIn mac1ToMac4PacketIn;
    protected IPacket mac1ToMac4PacketIntestPacket;
    protected byte[] mac1ToMac4PacketIntestPacketSerialized;
    protected OFPacketIn mac1ToGwPacketIn;
    protected IPacket mac1ToGwPacketIntestPacket;
    protected byte[] mac1ToGwPacketIntestPacketSerialized;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Module loading stuff
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        vns = new VirtualNetworkFilter();
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        restApi.init(fmc);
        getMockFloodlightProvider().init(fmc);
        vns.init(fmc);
        restApi.startUp(fmc);
        getMockFloodlightProvider().startUp(fmc);
        vns.startUp(fmc);
        
        // Mock switches
        //fastWilcards mocked as this constant
        int fastWildcards = 
                OFMatch.OFPFW_IN_PORT | 
                OFMatch.OFPFW_NW_PROTO | 
                OFMatch.OFPFW_TP_SRC | 
                OFMatch.OFPFW_TP_DST | 
                OFMatch.OFPFW_NW_SRC_ALL | 
                OFMatch.OFPFW_NW_DST_ALL |
                OFMatch.OFPFW_NW_TOS;
        sw1 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn((Integer)fastWildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        replay(sw1);
        
        // Mock packets
        // Mock from MAC1 -> MAC2
        mac1ToMac2PacketIntestPacket = new Ethernet()
            .setDestinationMACAddress(mac2.toBytes())
            .setSourceMACAddress(mac1.toBytes())
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));
        mac1ToMac2PacketIntestPacketSerialized = mac1ToMac2PacketIntestPacket.serialize();
        mac1ToMac2PacketIn = 
                ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().
                        getMessage(OFType.PACKET_IN))
                        .setBufferId(-1)
                        .setInPort((short) 1)
                        .setPacketData(mac1ToMac2PacketIntestPacketSerialized)
                        .setReason(OFPacketInReason.NO_MATCH)
                        .setTotalLength((short) mac1ToMac2PacketIntestPacketSerialized.length);
        
        // Mock from MAC1 -> MAC4
        mac1ToMac4PacketIntestPacket = new Ethernet()
        .setDestinationMACAddress(mac4.toBytes())
        .setSourceMACAddress(mac1.toBytes())
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.2")
            .setPayload(new UDP()
                        .setSourcePort((short) 5000)
                        .setDestinationPort((short) 5001)
                        .setPayload(new Data(new byte[] {0x01}))));
        mac1ToMac4PacketIntestPacketSerialized = mac1ToMac4PacketIntestPacket.serialize();
        mac1ToMac4PacketIn = 
            ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().
                    getMessage(OFType.PACKET_IN))
                    .setBufferId(-1)
                    .setInPort((short) 1)
                    .setPacketData(mac1ToMac4PacketIntestPacketSerialized)
                    .setReason(OFPacketInReason.NO_MATCH)
                    .setTotalLength((short) mac1ToMac4PacketIntestPacketSerialized.length);
        
        // Mock from MAC1 to gateway1
        mac1ToGwPacketIntestPacket = new Ethernet()
        .setDestinationMACAddress("00:11:44:33:44:55") // mac shouldn't matter, can't be other host
        .setSourceMACAddress(mac1.toBytes())
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress(gw1)
            .setPayload(new UDP()
                        .setSourcePort((short) 5000)
                        .setDestinationPort((short) 5001)
                        .setPayload(new Data(new byte[] {0x01}))));
        mac1ToGwPacketIntestPacketSerialized = mac1ToGwPacketIntestPacket.serialize();
        mac1ToGwPacketIn = 
            ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().
                    getMessage(OFType.PACKET_IN))
                    .setBufferId(-1)
                    .setInPort((short) 1)
                    .setPacketData(mac1ToGwPacketIntestPacketSerialized)
                    .setReason(OFPacketInReason.NO_MATCH)
                    .setTotalLength((short) mac1ToGwPacketIntestPacketSerialized.length);
    }
    
    @Test
    public void testCreateNetwork() {
        // Test creating a network with all parameters
        vns.createNetwork(guid1, net1, IPv4.toIPv4Address(gw1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid1));
        assertTrue(vns.nameToGuid.get(net1).equals(guid1));
        assertTrue(vns.guidToGateway.get(guid1).equals(IPv4.toIPv4Address(gw1)));
        
        // Test creating network without a gateway
        vns.createNetwork(guid2, net2, null);
        assertTrue(vns.nameToGuid.get(net2).equals(guid2));
        assertTrue(vns.guidToGateway.get(guid2) == null);
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).size() == 1);
        
        // Test creating a network that shares the gateway with net1
        vns.createNetwork(guid3, net3, IPv4.toIPv4Address(gw1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid3));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).size() == 2);
        assertTrue(vns.nameToGuid.get(net3).equals(guid3));
        assertTrue(vns.guidToGateway.get(guid3).equals(IPv4.toIPv4Address(gw1)));
    }
    
    @Test
    public void testModifyNetwork() {
        // Create some networks
        testCreateNetwork();
        // Modify net2 to add a gateway
        vns.createNetwork(guid2, net2, IPv4.toIPv4Address(gw1));
        assertTrue(vns.nameToGuid.get(net2).equals(guid2));
        assertTrue(vns.guidToGateway.get(guid2).equals(IPv4.toIPv4Address(gw1)));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid2));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid3));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).size() == 3);
        // Modify net2 to change it's name
        vns.createNetwork(guid2, "newnet2", null);
        // Make sure the gateway is still there
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid2));
        // make sure the new name mapping was learned
        assertTrue(vns.nameToGuid.get("newnet2").equals(guid2));
        // and the old one was deleted
        assertFalse(vns.nameToGuid.containsKey(net2));
    }
    
    @Test
    public void testDeleteNetwork() {
        testModifyNetwork();
        // Delete newnet2
        vns.deleteNetwork(guid2);
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid3));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).size() == 2);
        assertFalse(vns.nameToGuid.containsKey(net2));
        assertFalse(vns.guidToGateway.containsKey(net2));
    }
    
    @Test
    public void testAddHost() {
        testModifyNetwork();
        vns.addHost(mac1, net1, hostPort1);
        assertTrue(vns.macToGuid.get(mac1).equals(guid1));
        assertTrue(vns.portToMac.get(hostPort1).equals(mac1));
        vns.addHost(mac2, net1, hostPort2);
        assertTrue(vns.macToGuid.get(mac2).equals(guid1));
        assertTrue(vns.portToMac.get(hostPort2).equals(mac2));
        vns.addHost(mac3, net3, hostPort3);
        vns.addHost(mac4, net3, hostPort4);
    }
    
    @Test
    public void testDeleteHost() {
        testAddHost();
        vns.deleteHost(mac1, null);
        assertFalse(vns.macToGuid.containsKey(mac1));
        assertFalse(vns.portToMac.containsKey(hostPort1));
        vns.deleteHost(null, hostPort2);
        assertFalse(vns.macToGuid.containsKey(mac2));
        assertFalse(vns.portToMac.containsKey(hostPort2));
        vns.deleteHost(mac3, hostPort3);
        assertFalse(vns.macToGuid.containsKey(mac3));
        assertFalse(vns.portToMac.containsKey(hostPort3));
    }
    
    @Test
    public void testForwarding() {
        testAddHost();
        // make sure mac1 can communicate with mac2
        IOFMessageListener listener = mockFloodlightProvider.getListeners().
                get(OFType.PACKET_IN).get(0);
        cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, 
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                               (Ethernet)mac1ToMac2PacketIntestPacket);
        Command ret = listener.receive(sw1, mac1ToMac2PacketIn, cntx);
        assertTrue(ret == Command.CONTINUE);
        //reset(sw1);
        // make sure mac1 can't communicate with mac4
        cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, 
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                               (Ethernet)mac1ToMac4PacketIntestPacket);
        ret = listener.receive(sw1, mac1ToMac4PacketIn, cntx);
        assertTrue(ret == Command.STOP);
    }

    @Test
    public void testDefaultGateway() {
        testAddHost();
        IOFMessageListener listener = mockFloodlightProvider.getListeners().
                get(OFType.PACKET_IN).get(0);
        cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, 
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                               (Ethernet)mac1ToGwPacketIntestPacket);
        Command ret = listener.receive(sw1, mac1ToGwPacketIn, cntx);
        assertTrue(ret == Command.CONTINUE);
    }
}
