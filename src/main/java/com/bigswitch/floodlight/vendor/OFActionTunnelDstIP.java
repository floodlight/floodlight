package com.bigswitch.floodlight.vendor;

import net.floodlightcontroller.core.web.serializers.IPv4Serializer;
import net.floodlightcontroller.packet.IPv4;

import org.jboss.netty.buffer.ChannelBuffer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class OFActionTunnelDstIP extends OFActionBigSwitchVendor {
    public final static int MINIMUM_LENGTH_TUNNEL_DST = 16;
    public  final static int SET_TUNNEL_DST_SUBTYPE = 2;

    protected int dstIPAddr;

    public OFActionTunnelDstIP() {
        super(SET_TUNNEL_DST_SUBTYPE);
        super.setLength((short)MINIMUM_LENGTH_TUNNEL_DST);
    }

    public OFActionTunnelDstIP(int dstIPAddr) {
        this();
        this.dstIPAddr = dstIPAddr;
    }

    @JsonSerialize(using=IPv4Serializer.class)
    public int getTunnelDstIP() {
        return this.dstIPAddr;
    }

    public void setTunnelDstIP(int ipAddr) {
        this.dstIPAddr = ipAddr;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.dstIPAddr = data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.dstIPAddr);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + dstIPAddr;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFActionTunnelDstIP other = (OFActionTunnelDstIP) obj;
        if (dstIPAddr != other.dstIPAddr) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(type);
        builder.append("[");
        builder.append("BSN-SET-TUNNEL-DST-IP");
        builder.append(", IP: ");
        builder.append(IPv4.fromIPv4Address(dstIPAddr));
        builder.append("]");
        return builder.toString();
    }
}
