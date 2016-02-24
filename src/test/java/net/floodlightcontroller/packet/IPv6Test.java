package net.floodlightcontroller.packet;

import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;

/**
 * @author Jacob Chappell (jacob.chappell@uky.edu)
 */
public class IPv6Test {
    @Test
    public void testSerializeWithoutPayload() {
        byte[] expected = new byte[] {
                0x64, 0x2B, 0x16, (byte) 0x95, 0x00, 0x00,
                0x11, (byte) 0xE1, (byte) 0xFE, (byte) 0x80,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x7A, (byte) 0xC5, (byte) 0xFF, (byte) 0xFE,
                0x2E, 0x77, 0x35, (byte) 0xFE, (byte) 0x80,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x77, 0x5D, (byte) 0xFF, (byte) 0xFE,
                (byte) 0xC2, 0x30, (byte) 0xFD
        };
        IPv6 packet = (new IPv6())
            .setTrafficClass((byte) 0x42)
            .setFlowLabel(0xB1695)
            .setPayloadLength((short) 0)
            .setNextHeader(IpProtocol.of((short) 0x11))
            .setHopLimit((byte) 0xE1)
            .setSourceAddress(IPv6Address.of("fe80::7a:c5ff:fe2e:7735"))
            .setDestinationAddress(IPv6Address.of("fe80::77:5dff:fec2:30fd"));
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testDeserialize() throws PacketParsingException {
        byte[] spudPacket = {
                0x64, 0x2B, 0x16, (byte) 0x95, 0x00, 0x15,
                0x11, (byte) 0xE1, (byte) 0xFE, (byte) 0x80,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x7A, (byte) 0xC5, (byte) 0xFF, (byte) 0xFE,
                0x2E, 0x77, 0x35, (byte) 0xFE, (byte) 0x80,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x77, 0x5D, (byte) 0xFF, (byte) 0xFE,
                (byte) 0xC2, 0x30, (byte) 0xFD, (byte) 0xD2,
                0x01, 0x05, 0x7A, 0x00, 0x15, (byte) 0xF6,
                (byte) 0xC8, (byte) 0xD8, 0x00, 0x00,
                (byte) 0xD8, 0x4A, (byte) 0xC3, (byte) 0xF2,
                0x02, 0x44, 0x75, (byte) 0x97, 0x69, 0x40
        };
        IPv6 packet = new IPv6();
        packet.deserialize(spudPacket, 0, spudPacket.length);
        byte[] packetSerialized = packet.serialize();
        assertTrue(Arrays.equals(spudPacket, packetSerialized));
    }
}
