package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;

public class OFActionNiciraTtlDecrement extends OFActionNiciraVendor {
    public static int MINIMUM_LENGTH_TTL_DECREMENT = 16;
    public static final short TTL_DECREMENT_SUBTYPE = 18;
    
    
    public OFActionNiciraTtlDecrement() {
        super(TTL_DECREMENT_SUBTYPE);
        super.setLength((short)MINIMUM_LENGTH_TTL_DECREMENT);
    }
    
    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        data.skipBytes(6);  // pad
    }
    
    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeZero(6);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(type);
        builder.append("[");
        builder.append("NICIRA-TTL-DECR");
        builder.append("]");
        return builder.toString();
    }
}
