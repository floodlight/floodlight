package net.floodlightcontroller.virtualnetwork;

import static org.easymock.EasyMock.*;

import java.util.List;

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
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.test.PacketFactory;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.flowcache.FlowReconcileManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.virtualnetwork.VirtualNetworkFilter;

public class VirtualNetworkFilterTest extends FloodlightTestCase {
    protected VirtualNetworkFilter vns;
    protected MockDeviceManager deviceService;
    
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
    protected OFPacketIn packetInDHCPDiscoveryRequest;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Module loading stuff
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        deviceService = new MockDeviceManager();
        FlowReconcileManager frm = new FlowReconcileManager();
        MockThreadPoolService tps = new MockThreadPoolService();
        ITopologyService topology = createMock(ITopologyService.class);
        vns = new VirtualNetworkFilter();
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        fmc.addService(IDeviceService.class, deviceService);
        fmc.addService(IFlowReconcileService.class, frm);
        fmc.addService(IThreadPoolService.class, tps);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(ITopologyService.class, topology);
        tps.init(fmc);
        frm.init(fmc);
        deviceService.init(fmc);
        restApi.init(fmc);
        getMockFloodlightProvider().init(fmc);
        entityClassifier.init(fmc);
        tps.startUp(fmc);
        vns.init(fmc);
        frm.startUp(fmc);
        deviceService.startUp(fmc);
        restApi.startUp(fmc);
        getMockFloodlightProvider().startUp(fmc);
        vns.startUp(fmc);
        entityClassifier.startUp(fmc);

        topology.addListener(deviceService);
        expectLastCall().times(1);
        replay(topology);
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
        .setDestinationMACAddress("00:11:33:33:44:55") // mac shouldn't matter, can't be other host
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
        assertTrue(vns.vNetsByGuid.get(guid1).name.equals(net1));
        assertTrue(vns.vNetsByGuid.get(guid1).guid.equals(guid1));
        assertTrue(vns.vNetsByGuid.get(guid1).gateway.equals(gw1));
        assertTrue(vns.vNetsByGuid.get(guid1).hosts.size()==0);

        // Test creating network without a gateway
        vns.createNetwork(guid2, net2, null);
        assertTrue(vns.nameToGuid.get(net2).equals(guid2));
        assertTrue(vns.guidToGateway.get(guid2) == null);
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).size() == 1);
        assertTrue(vns.vNetsByGuid.get(guid2).name.equals(net2));
        assertTrue(vns.vNetsByGuid.get(guid2).guid.equals(guid2));
        assertTrue(vns.vNetsByGuid.get(guid2).gateway == null);
        assertTrue(vns.vNetsByGuid.get(guid2).hosts.size()==0);
        
        // Test creating a network that shares the gateway with net1
        vns.createNetwork(guid3, net3, IPv4.toIPv4Address(gw1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid1));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).contains(guid3));
        assertTrue(vns.gatewayToGuid.get(IPv4.toIPv4Address(gw1)).size() == 2);
        assertTrue(vns.nameToGuid.get(net3).equals(guid3));
        assertTrue(vns.guidToGateway.get(guid3).equals(IPv4.toIPv4Address(gw1)));
        assertTrue(vns.vNetsByGuid.get(guid3).name.equals(net3));
        assertTrue(vns.vNetsByGuid.get(guid3).guid.equals(guid3));
        assertTrue(vns.vNetsByGuid.get(guid3).gateway.equals(gw1));
        assertTrue(vns.vNetsByGuid.get(guid3).hosts.size()==0);

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
        assertTrue(vns.vNetsByGuid.get(guid2).gateway.equals(gw1));
        // make sure the new name mapping was learned
        assertTrue(vns.nameToGuid.get("newnet2").equals(guid2));
        assertTrue(vns.vNetsByGuid.get(guid2).name.equals("newnet2"));
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
        assertTrue(vns.vNetsByGuid.get(guid2)==null);
    }
    
    @Test
    public void testAddHost() {
        testModifyNetwork();
        vns.addHost(mac1, guid1, hostPort1);
        assertTrue(vns.macToGuid.get(mac1).equals(guid1));
        assertTrue(vns.portToMac.get(hostPort1).equals(mac1));
        assertTrue(vns.vNetsByGuid.get(guid1).hosts.contains(mac1));
        vns.addHost(mac2, guid1, hostPort2);
        assertTrue(vns.macToGuid.get(mac2).equals(guid1));
        assertTrue(vns.portToMac.get(hostPort2).equals(mac2));
        assertTrue(vns.vNetsByGuid.get(guid1).hosts.contains(mac2));
        vns.addHost(mac3, guid3, hostPort3);
        vns.addHost(mac4, guid3, hostPort4);
        assertTrue(vns.vNetsByGuid.get(guid3).hosts.contains(mac4));
    }
    
    @Test
    public void testDeleteHost() {
        testAddHost();

        String host1Guid = vns.macToGuid.get(mac1);
        vns.deleteHost(mac1, null);
        assertFalse(vns.macToGuid.containsKey(mac1));
        assertFalse(vns.portToMac.containsKey(hostPort1));
        assertFalse(vns.vNetsByGuid.get(host1Guid).hosts.contains(mac1));
        
        String host2Guid = vns.macToGuid.get(vns.portToMac.get(hostPort2));
        vns.deleteHost(null, hostPort2);
        assertFalse(vns.macToGuid.containsKey(mac2));
        assertFalse(vns.portToMac.containsKey(hostPort2));
        assertFalse(vns.vNetsByGuid.get(host2Guid).hosts.contains(mac2));

        String host3Guid = vns.macToGuid.get(mac3);        
        vns.deleteHost(mac3, hostPort3);
        assertFalse(vns.macToGuid.containsKey(mac3));
        assertFalse(vns.portToMac.containsKey(hostPort3));
        assertFalse(vns.vNetsByGuid.get(host3Guid).hosts.contains(mac3));
        
    }
    
    @Test
    public void testForwarding() {
        testAddHost();
        // make sure mac1 can communicate with mac2
        IOFMessageListener listener = getVirtualNetworkListener();
        cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, 
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                               (Ethernet)mac1ToMac2PacketIntestPacket);
        Command ret = listener.receive(sw1, mac1ToMac2PacketIn, cntx);
        assertTrue(ret == Command.CONTINUE);
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
        IOFMessageListener listener = getVirtualNetworkListener();
        cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, 
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                               (Ethernet)mac1ToGwPacketIntestPacket);
        deviceService.learnEntity(((Ethernet)mac1ToGwPacketIntestPacket).getDestinationMAC().toLong(), 
        		null, IPv4.toIPv4Address(gw1), null, null);
        Command ret = listener.receive(sw1, mac1ToGwPacketIn, cntx);
        assertTrue(ret == Command.CONTINUE);
    }
    
    @Test
    public void testDhcp() {
        IOFMessageListener listener = getVirtualNetworkListener();
        Ethernet dhcpPacket = PacketFactory.DhcpDiscoveryRequestEthernet(mac1);
        OFPacketIn dhcpPacketOf = PacketFactory.DhcpDiscoveryRequestOFPacketIn(mac1);
        cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, 
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                           dhcpPacket);
        Command ret = listener.receive(sw1, dhcpPacketOf, cntx);
        assertTrue(ret == Command.CONTINUE);
    }
    
    protected IOFMessageListener getVirtualNetworkListener() {
    	List<IOFMessageListener> listeners = mockFloodlightProvider.getListeners().get(OFType.PACKET_IN);
    	return listeners.get(listeners.indexOf(vns));
    }
}
