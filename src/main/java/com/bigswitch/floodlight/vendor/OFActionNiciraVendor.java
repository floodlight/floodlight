package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFActionVendor;
import org.openflow.vendor.nicira.OFNiciraVendorData;

/**
 * FIXME: this should really be handled by a consistent parse tree for
 * different vendor actions but for the time being this works and gets the
 * job done. 
 * 
 * @author gregor
 *
 */
public class OFActionNiciraVendor extends OFActionVendor {
    public static int MINIMUM_LENGTH = 16;
    public static int NICIRA_VENDOR_ID = OFNiciraVendorData.NX_VENDOR_ID;
    
    protected short subtype;

    protected OFActionNiciraVendor(short subtype) {
        // We don't allow direct instantiation of this class because its 
        // minimum length is 16 and the only way to guarantee this is by 
        // having a subclass that properly adds padding. 
        super();
        super.setLength((short)MINIMUM_LENGTH);
        super.setVendor(NICIRA_VENDOR_ID);
        this.subtype = subtype;
    }
    
    public short getSubtype() {
        return this.subtype;
    }
    
    public void setSubtype(short subtype) {
        this.subtype = subtype;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.subtype = data.readShort();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.subtype);
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
        if (!(obj instanceof OFActionNiciraVendor)) {
            return false;
        }
        OFActionNiciraVendor other = (OFActionNiciraVendor) obj;
        if (subtype != other.subtype) {
            return false;
        }
        return true;
    }
}
