package net.floodlightcontroller.packet;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class PacketTest {
    protected IPacket pkt1, pkt2, pkt3, pkt4;
    protected IPacket dummyPkt;
    protected IPacket[] packets;
    
    @Before
    public void setUp() {
        this.pkt1 = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setSourceMACAddress("00:44:33:22:11:00")
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
        
        this.pkt2 = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:01")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setVlanID((short)5)
        .setPayload(
                    new ARP()
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareAddressLength((byte) 6)
                    .setProtocolAddressLength((byte) 4)
                    .setOpCode(ARP.OP_REPLY)
                    .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:01"))
                    .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                    .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                    .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        
        
        this.pkt3 = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:01")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                    new ARP()
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareAddressLength((byte) 6)
                    .setProtocolAddressLength((byte) 4)
                    .setOpCode(ARP.OP_REPLY)
                    .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:01"))
                    .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                    .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                    .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        
        this.pkt4 = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:11:33:55:77:01")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                    new IPv4()
                    .setTtl((byte) 128)
                    .setSourceAddress("192.168.10.1")
                    .setDestinationAddress("192.168.255.255")
                    .setPayload(new UDP()
                    .setSourcePort((short) 5000)
                    .setDestinationPort((short) 5001)
                    .setPayload(new Data(new byte[] {0x01}))));
        
        this.dummyPkt =  new IPv4()
        .setTtl((byte) 32)
        .setSourceAddress("1.2.3.4")
        .setDestinationAddress("5.6.7.8");
        
        this.packets = new IPacket[] { pkt1, pkt2, pkt3, pkt4 };
    }
    
    protected void doTestClone(IPacket pkt) {
        if (pkt.getPayload() != null)
            doTestClone(pkt.getPayload());
        IPacket newPkt = (IPacket)pkt.clone();
        assertSame(pkt.getClass(), newPkt.getClass());
        assertNotSame(pkt, newPkt);
        assertSame(pkt.getParent(), newPkt.getParent());
        assertEquals(pkt, newPkt);
        assertEquals(pkt.getPayload(), newPkt.getPayload());
        if (pkt.getPayload() != null)
            assertNotSame(pkt.getPayload(), newPkt.getPayload());
        
        if (pkt instanceof Ethernet) {
            Ethernet eth = (Ethernet)pkt;
            Ethernet newEth = (Ethernet)newPkt;
            newEth.setDestinationMACAddress(new byte[] { 1,2,3,4,5,6});
            assertEquals(false, newEth.getDestinationMAC()
                                .equals(eth.getDestinationMAC()));
            assertEquals(false, newPkt.equals(pkt));
        }
        if (pkt instanceof ARP) {
            ARP arp = (ARP)pkt;
            ARP newArp = (ARP)newPkt;
            newArp.setSenderProtocolAddress(new byte[] {1,2,3,4});
            assertEquals(false, newArp.getSenderProtocolAddress()
                                .equals(arp.getSenderProtocolAddress()));
            assertEquals(false, newPkt.equals(pkt));
        }
        
        byte[] dummyData = dummyPkt.serialize().clone();
        newPkt = (IPacket)pkt.clone();
        newPkt.deserialize(dummyData, 0, dummyData.length);
        assertEquals(false, newPkt.equals(pkt));
    }
    
    @Test
    public void testClone() {
        for (IPacket pkt: packets) {
            doTestClone(pkt);
        }
    }
    
}
