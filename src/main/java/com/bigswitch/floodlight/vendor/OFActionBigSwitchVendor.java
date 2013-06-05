package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFActionVendor;

public abstract class OFActionBigSwitchVendor extends OFActionVendor {
    public static int MINIMUM_LENGTH = 12;
    public static int BSN_VENDOR_ID = OFBigSwitchVendorData.BSN_VENDOR_ID;

    protected int subtype;

    protected OFActionBigSwitchVendor(int subtype) {
        super();
        super.setLength((short)MINIMUM_LENGTH);
        super.setVendor(BSN_VENDOR_ID);
        this.subtype = subtype;
    }

    public int getSubtype() {
        return this.subtype;
    }

    public void setSubtype(int subtype) {
        this.subtype = subtype;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.subtype = data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.subtype);
    }

    @Override
    public int hashCode() {
        final int prime = 379;
        int result = super.hashCode();
        result = prime * result + vendor;
        result = prime * result + subtype;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof OFActionBigSwitchVendor)) {
            return false;
        }
        OFActionBigSwitchVendor other = (OFActionBigSwitchVendor) obj;
        if (subtype != other.subtype) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + "; subtype=" + subtype;
    }
}
