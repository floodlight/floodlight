package com.bigswitch.floodlight.vendor;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;

import net.floodlightcontroller.core.web.serializers.ByteArrayMACSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jboss.netty.buffer.ChannelBuffer;

public class OFInterfaceVendorData {
    public static int MINIMUM_LENGTH = 32;
    private static int OFP_ETH_ALEN = 6;
    private static int OFP_MAX_PORT_NAME_LEN = 16;

    protected byte[] hardwareAddress;
    protected String name;
    protected int ipv4Addr;
    protected int ipv4AddrMask;

    /**
     * @return the hardwareAddress
     */
    @JsonSerialize(using=ByteArrayMACSerializer.class)
    public byte[] getHardwareAddress() {
        return hardwareAddress;
    }

    /**
     * @param hardwareAddress the hardwareAddress to set
     */
    public void setHardwareAddress(byte[] hardwareAddress) {
        if (hardwareAddress.length != OFP_ETH_ALEN)
            throw new RuntimeException("Hardware address must have length "
                    + OFP_ETH_ALEN);
        this.hardwareAddress = hardwareAddress;
    }

    public int getIpv4Addr() {
        return ipv4Addr;
    }

    public void setIpv4Addr(int ipv4Addr) {
        this.ipv4Addr = ipv4Addr;
    }

    public int getIpv4AddrMask() {
        return ipv4AddrMask;
    }

    public void setIpv4AddrMask(int ipv4AddrMask) {
        this.ipv4AddrMask = ipv4AddrMask;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Write this message's binary format to the specified ByteBuffer
     * @param data
     */
    public void writeTo(ChannelBuffer data) {
        data.writeBytes(hardwareAddress);
        data.writeBytes(new byte[] {0, 0});

        try {
            byte[] name = this.name.getBytes("ASCII");
            if (name.length < OFP_MAX_PORT_NAME_LEN) {
                data.writeBytes(name);
                for (int i = name.length; i < OFP_MAX_PORT_NAME_LEN; ++i) {
                    data.writeByte((byte) 0);
                }
            } else {
                data.writeBytes(name, 0, 15);
                data.writeByte((byte) 0);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        data.writeInt(ipv4Addr);
        data.writeInt(ipv4AddrMask);
    }

    /**
     * Read this message off the wire from the specified ByteBuffer
     * @param data
     */
    public void readFrom(ChannelBuffer data) {
        if (this.hardwareAddress == null)
            this.hardwareAddress = new byte[OFP_ETH_ALEN];
        data.readBytes(this.hardwareAddress);
        data.readBytes(new byte[2]);

        byte[] name = new byte[16];
        data.readBytes(name);
        // find the first index of 0
        int index = 0;
        for (byte b : name) {
            if (0 == b)
                break;
            ++index;
        }
        this.name = new String(Arrays.copyOf(name, index),
                Charset.forName("ascii"));
        ipv4Addr = data.readInt();
        ipv4AddrMask = data.readInt();
    }


}