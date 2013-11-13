package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

public class OFBsnPktinSuppressionSetRequestVendorData
        extends OFBigSwitchVendorData {
    protected static Instantiable<OFVendorData> instantiableSingleton =
            new Instantiable<OFVendorData>() {
                @Override
                public OFVendorData instantiate() {
                    return new OFBsnL2TableSetVendorData();
                }
            };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFBsnL2TableSetVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiableSingleton;
    }

    public static final int BSN_PKTIN_SUPPRESSION_SET_REQUEST = 11;
    /*
     * uint8_t enabled;        // 0 to disable the extension, 1 to enable it
     * uint8_t pad;
     * uint16_t idle_timeout;  // idle_timeout for new flows
     * uint16_t hard_timeout;  // idle_timeout for new flows
     * uint16_t priority;      // priority for new flows
     * uint64_t cookie;        // cookie for new flows
     */

    protected boolean suppressionEnabled;
    protected short idleTimeout;
    protected short hardTimeout;
    protected short priority;
    protected long cookie;

    public OFBsnPktinSuppressionSetRequestVendorData() {
        super(BSN_PKTIN_SUPPRESSION_SET_REQUEST);
    }

    public OFBsnPktinSuppressionSetRequestVendorData(boolean suppressionEnabled,
                                                     short idleTimeout,
                                                     short hardTimeout,
                                                     short priority,
                                                     long cookie) {
        super(BSN_PKTIN_SUPPRESSION_SET_REQUEST);
        this.suppressionEnabled = suppressionEnabled;
        this.idleTimeout = idleTimeout;
        this.hardTimeout = hardTimeout;
        this.priority = priority;
        this.cookie = cookie;
    }

    public boolean isSuppressionEnabled() {
        return suppressionEnabled;
    }

    public short getIdleTimeout() {
        return idleTimeout;
    }

    public short getHardTimeout() {
        return hardTimeout;
    }

    public short getPriority() {
        return priority;
    }

    public long getCookie() {
        return cookie;
    }

    public void setSuppressionEnabled(boolean suppressionEnabled) {
        this.suppressionEnabled = suppressionEnabled;
    }

    public void setIdleTimeout(short idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public void setHardTimeout(short hardTimeout) {
        this.hardTimeout = hardTimeout;
    }

    public void setPriority(short priority) {
        this.priority = priority;
    }

    public void setCookie(long cookie) {
        this.cookie = cookie;
    }

    @Override
    public int getLength() {
        return super.getLength() + 16;
    }

    @Override
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        suppressionEnabled = (data.readByte() != 0);
        data.readByte();
        idleTimeout = data.readShort();
        hardTimeout = data.readShort();
        priority = data.readShort();
        cookie = data.readLong();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeByte(suppressionEnabled ? 1 : 0);
        data.writeByte(0); // pad
        data.writeShort(idleTimeout);
        data.writeShort(hardTimeout);
        data.writeShort(priority);
        data.writeLong(cookie);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (int) (cookie ^ (cookie >>> 32));
        result = prime * result + hardTimeout;
        result = prime * result + idleTimeout;
        result = prime * result + priority;
        result = prime * result + (suppressionEnabled ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFBsnPktinSuppressionSetRequestVendorData other = (OFBsnPktinSuppressionSetRequestVendorData) obj;
        if (cookie != other.cookie) return false;
        if (hardTimeout != other.hardTimeout) return false;
        if (idleTimeout != other.idleTimeout) return false;
        if (priority != other.priority) return false;
        if (suppressionEnabled != other.suppressionEnabled) return false;
        return true;
    }
}
