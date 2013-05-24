package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;

public class OFBsnL2TableVendorData extends OFBigSwitchVendorData {
    /*
     * uint8_t l2_table_enable;    // 1 == enabled, 0 == disabled
     * uint8_t pad;
     * uint16_t l2_table_priority;  // priority of all flows in L2 table
     * uint8_t pad[4];
     */
    protected boolean l2TableEnabled;
    protected short l2TablePriority;
    
    
    public OFBsnL2TableVendorData(int dataType) {
        super(dataType);
        this.l2TableEnabled = false;
        this.l2TablePriority = (short)0;
    }


    public OFBsnL2TableVendorData(int dataType, boolean l2TableEnabled,
                                  short l2TablePriority) {
        super(dataType);
        this.l2TableEnabled = l2TableEnabled;
        this.l2TablePriority = l2TablePriority;
    }


    public boolean isL2TableEnabled() {
        return l2TableEnabled;
    }


    public short getL2TablePriority() {
        return l2TablePriority;
    }


    public void setL2TableEnabled(boolean l2TableEnabled) {
        this.l2TableEnabled = l2TableEnabled;
    }


    public void setL2TablePriority(short l2TablePriority) {
        this.l2TablePriority = l2TablePriority;
    }
    
    
    @Override
    public int getLength() {
        return super.getLength() + 8; // 8 additional bytes
    }
    
    /*
     * (non-Javadoc)
     * @see com.bigswitch.floodlight.vendor.OFBigSwitchVendorData#readFrom(org.jboss.netty.buffer.ChannelBuffer, int)
     */
    @Override 
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        l2TableEnabled = (data.readByte() == 0) ? false : true;
        data.readByte();  // pad
        l2TablePriority = data.readShort();
        data.readInt();   // 4 bad bytes
    }
    
    /*
     * (non-Javadoc)
     * @see com.bigswitch.floodlight.vendor.OFBigSwitchVendorData#writeTo(org.jboss.netty.buffer.ChannelBuffer)
     */
    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeByte(isL2TableEnabled() ? 1 : 0);
        data.writeByte(0);  // pad
        data.writeShort(l2TablePriority);
        data.writeInt(0);   // 4 pad bytes
    }
}
