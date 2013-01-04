package org.openflow.protocol.action;

import org.jboss.netty.buffer.ChannelBuffer;


public class MockVendorAction extends OFActionVendor {
    public static final int VENDOR_ID = 0xdeadbeef;

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private byte[] mockData;

    public byte[] getMockData() {
        return mockData;
    }

    public void setMockData(byte[] mockData) {
        this.mockData = mockData;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);

        int dataLength = getLength() - MINIMUM_LENGTH;
        if(dataLength > 0) {
            mockData = new byte[dataLength];
            data.readBytes(mockData);
        } else {
            mockData = EMPTY_BYTE_ARRAY;
        }

    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeBytes(mockData);
    }


}
