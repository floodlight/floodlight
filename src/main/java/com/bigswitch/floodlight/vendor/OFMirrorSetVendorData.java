package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;

public class OFMirrorSetVendorData extends OFBigSwitchVendorData {
    
    /**
     * Opcode/dataType to set mirroring
     */
    public static final int BSN_SET_MIRRORING = 3;

    protected byte reportMirrorPorts;
    protected byte pad1;
    protected byte pad2;
    protected byte pad3;
    
    public OFMirrorSetVendorData() {
        super(BSN_SET_MIRRORING);
        this.reportMirrorPorts=1;
    }

    public byte getReportMirrorPorts() {
        return reportMirrorPorts;
    }

    public void setReportMirrorPorts(byte report) {
        this.reportMirrorPorts = report;
    }
    
    /**
     * @return the total length vendor date
     */
    @Override
    public int getLength() {
        return super.getLength() + 4; // 4 extra bytes
    }
    
    /**
     * Read the vendor data from the channel buffer
     * @param data: the channel buffer from which we are deserializing
     * @param length: the length to the end of the enclosing message
     */
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        reportMirrorPorts = data.readByte();
        pad1 = data.readByte();
        pad2 = data.readByte();
        pad3 = data.readByte();
    }
    
    /**
     * Write the vendor data to the channel buffer
     */
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeByte(reportMirrorPorts);
        data.writeByte(pad1);
        data.writeByte(pad2);
        data.writeByte(pad3);
    }
    
}
