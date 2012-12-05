package org.openflow.protocol;

import org.junit.Test;

public class OFPacketOutTest {

    @Test(expected = IllegalArgumentException.class)
    public void testBothBufferIdAndPayloadSet() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(12);
        packetOut.setPacketData(new byte[] { 1, 2, 3 });
    }

    @Test
    public void testOnlyBufferIdSet() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(12);
        packetOut.setPacketData(null);
        packetOut.setPacketData(new byte[] {});
        packetOut.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeitherBufferIdNorPayloadSet() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        packetOut.setPacketData(null);
        packetOut.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeitherBufferIdNorPayloadSet2() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        packetOut.setPacketData(new byte[] {});
        packetOut.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeitherBufferIdNorPayloadSet3() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.validate();
    }

}
