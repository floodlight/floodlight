package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;

import static org.junit.Assert.*;

public class OFBsnPktinSupressionSetRequestVendorDataTest {
    protected static byte[] expectedWireFormat = {
            0x00, 0x00, 0x00, 0x0b, // type == 11
            0x01,                   // enabled
            0x00,                   // pad
            0x00, 0x5a,             // idle timeout
            (byte) 0xf0, (byte) 0xe0,  // hard timeout
            0x12, 0x34,             // priority
            0x33, 0x33, 0x66, 0x66,
            0x77, 0x77, (byte) 0x99, (byte) 0x99  // cookie
    };

    @Test
    public void test() {
        ChannelBuffer buf = ChannelBuffers.buffer(32);

        OFBsnPktinSuppressionSetRequestVendorData vendorData =
                new OFBsnPktinSuppressionSetRequestVendorData(
                                                     true,
                                                     (short)0x5a,
                                                     (short)0xf0e0,
                                                     (short)0x1234,
                                                     0x3333666677779999L);
        assertEquals(11, vendorData.getDataType());

        assertEquals(true, vendorData instanceof OFBigSwitchVendorData);

        vendorData.writeTo(buf);

        ChannelBuffer buf2 = buf.copy();
        assertEquals(20, buf.readableBytes());
        byte fromBuffer[] = new byte[20];
        buf.readBytes(fromBuffer);
        assertArrayEquals(expectedWireFormat, fromBuffer);

        OFBsnPktinSuppressionSetRequestVendorData vendorData2 =
                new OFBsnPktinSuppressionSetRequestVendorData();

        assertEquals(11, vendorData2.getDataType());

        vendorData2.setIdleTimeout((short)1);
        assertEquals((short)1, vendorData2.getIdleTimeout());

        vendorData2.setHardTimeout((short)2);
        assertEquals((short)2, vendorData2.getHardTimeout());

        vendorData2.setPriority((short)3);
        assertEquals((short)3, vendorData2.getPriority());

        vendorData2.setCookie(12345678901234L);
        assertEquals(12345678901234L, vendorData2.getCookie());

        vendorData2.readFrom(buf2, buf2.readableBytes());
        assertEquals(vendorData, vendorData2);
    }


}
