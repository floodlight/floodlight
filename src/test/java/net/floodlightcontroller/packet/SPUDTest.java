package net.floodlightcontroller.packet;

import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

/**
 * @author Jacob Chappell (jacob.chappell@uky.edu)
 */
public class SPUDTest {
    @Test
    public void testSerializeCommandOpen() {
        byte[] expected = new byte[] {
                (byte) 0xd8, 0x00, 0x00, (byte) 0xd8,
                (byte) 0xb6, 0x40, 0x17, (byte) 0x88,
                0x0a, 0x51, 0x01, 0x07, 0x40
        };
        SPUD packet = (new SPUD())
                .setTubeID(0xb64017880a510107L)
                .setCommand(SPUD.COMMAND_OPEN)
                .setADEC(false)
                .setPDEC(false)
                .setReserved((byte) 0);
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testSerializeCommandDataEmpty() {
        byte[] expected = new byte[] {
                (byte) 0xd8, 0x00, 0x00, (byte) 0xd8,
                (byte) 0xb6, 0x40, 0x17, (byte) 0x88,
                0x0a, 0x51, 0x01, 0x07, 0x00
        };
        SPUD packet = (new SPUD())
                .setTubeID(0xb64017880a510107L)
                .setCommand(SPUD.COMMAND_DATA)
                .setADEC(false)
                .setPDEC(false)
                .setReserved((byte) 0);
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testSerializeCommandDataEmptyWithADEC() {
        byte[] expected = new byte[] {
                (byte) 0xd8, 0x00, 0x00, (byte) 0xd8,
                (byte) 0xb6, 0x40, 0x17, (byte) 0x88,
                0x0a, 0x51, 0x01, 0x07, 0x20
        };
        SPUD packet = (new SPUD())
                .setTubeID(0xb64017880a510107L)
                .setCommand(SPUD.COMMAND_DATA)
                .setADEC(true)
                .setPDEC(false)
                .setReserved((byte) 0);
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testSerializeCommandDataEmptyWithPDEC() {
        byte[] expected = new byte[] {
                (byte) 0xd8, 0x00, 0x00, (byte) 0xd8,
                (byte) 0xb6, 0x40, 0x17, (byte) 0x88,
                0x0a, 0x51, 0x01, 0x07, 0x10
        };
        SPUD packet = (new SPUD())
                .setTubeID(0xb64017880a510107L)
                .setCommand(SPUD.COMMAND_DATA)
                .setADEC(false)
                .setPDEC(true)
                .setReserved((byte) 0);
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testSerializeCommandDataEmptyWithBoth() {
        byte[] expected = new byte[] {
                (byte) 0xd8, 0x00, 0x00, (byte) 0xd8,
                (byte) 0xb6, 0x40, 0x17, (byte) 0x88,
                0x0a, 0x51, 0x01, 0x07, 0x30
        };
        SPUD packet = (new SPUD())
                .setTubeID(0xb64017880a510107L)
                .setCommand(SPUD.COMMAND_DATA)
                .setADEC(true)
                .setPDEC(true)
                .setReserved((byte) 0);
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testDeserialize() throws PacketParsingException {
        byte[] spudPacket =  {
                (byte) 0xd8, 0x00, 0x00, (byte) 0xd8, (byte) 0xb6,
                0x40, 0x17, (byte) 0x88, 0x0a, 0x51, 0x01, 0x07,
                0x00, (byte) 0xa1, 0x00, (byte) 0xa2, 0x68, 0x75,
                0x73, 0x65, 0x72, 0x6e, 0x61, 0x6d, 0x65, 0x65,
                0x4a, 0x61, 0x63, 0x6f, 0x62, 0x67, 0x6d, 0x65,
                0x73, 0x73, 0x61, 0x67, 0x65, 0x73, 0x68, 0x61,
                0x73, 0x20, 0x6a, 0x6f, 0x69, 0x6e, 0x65, 0x64,
                0x20, 0x74, 0x68, 0x65, 0x20, 0x72, 0x6f, 0x6f,
                0x6d
        };
        SPUD packet = new SPUD();
        packet.deserialize(spudPacket, 0, spudPacket.length);
        byte[] packetSerialized = packet.serialize();
        assertTrue(Arrays.equals(spudPacket, packetSerialized));
    }
}
