package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionVendor;

import static org.junit.Assert.*;

public class OFActionNiciraTtlDecrementTest {
    protected static byte[] expectedWireFormat = { 
                (byte) 0xff, (byte) 0xff,          // action vendor
                0x00, 0x10,                        // length 
                0x00, 0x00, 0x23, 0x20,            // nicira
                0x00, 0x12,                        // subtype 18 == 0x12
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // pad 
    };
    
    @Test
    public void testAction() {
        ChannelBuffer buf = ChannelBuffers.buffer(32);
        
        OFActionNiciraTtlDecrement act = new OFActionNiciraTtlDecrement();
        
        assertEquals(true, act instanceof OFActionNiciraVendor);
        assertEquals(true, act instanceof OFActionVendor);
        assertEquals(true, act instanceof OFAction);
        
        act.writeTo(buf);
        
        ChannelBuffer buf2 = buf.copy();
        
        assertEquals(16, buf.readableBytes());
        byte fromBuffer[] = new byte[16]; 
        buf.readBytes(fromBuffer);
        assertArrayEquals(expectedWireFormat, fromBuffer);
        
        // Test parsing. TODO: we don't really have the proper parsing
        // infrastructure....
        OFActionNiciraVendor act2 = new OFActionNiciraTtlDecrement();
        act2.readFrom(buf2);
        assertEquals(act, act2);
        assertNotSame(act, act2);
        
        assertEquals(OFActionType.VENDOR, act2.getType());
        assertEquals(16, act2.getLength());
        assertEquals(OFActionNiciraVendor.NICIRA_VENDOR_ID, act2.getVendor());
        assertEquals((short)18, act2.getSubtype());
    }

}
