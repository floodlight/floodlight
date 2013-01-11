/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

/**
 * 
 */
package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class IPv4 extends BasePacket {
    public static final byte PROTOCOL_ICMP = 0x1;
    public static final byte PROTOCOL_TCP = 0x6;
    public static final byte PROTOCOL_UDP = 0x11;
    public static Map<Byte, Class<? extends IPacket>> protocolClassMap;

    static {
        protocolClassMap = new HashMap<Byte, Class<? extends IPacket>>();
        protocolClassMap.put(PROTOCOL_ICMP, ICMP.class);
        protocolClassMap.put(PROTOCOL_TCP, TCP.class);
        protocolClassMap.put(PROTOCOL_UDP, UDP.class);
    }

    protected byte version;
    protected byte headerLength;
    protected byte diffServ;
    protected short totalLength;
    protected short identification;
    protected byte flags;
    protected short fragmentOffset;
    protected byte ttl;
    protected byte protocol;
    protected short checksum;
    protected int sourceAddress;
    protected int destinationAddress;
    protected byte[] options;

    protected boolean isTruncated;

    /**
     * Default constructor that sets the version to 4.
     */
    public IPv4() {
        super();
        this.version = 4;
        isTruncated = false;
    }

    /**
     * @return the version
     */
    public byte getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public IPv4 setVersion(byte version) {
        this.version = version;
        return this;
    }

    /**
     * @return the headerLength
     */
    public byte getHeaderLength() {
        return headerLength;
    }

    /**
     * @return the diffServ
     */
    public byte getDiffServ() {
        return diffServ;
    }

    /**
     * @param diffServ the diffServ to set
     */
    public IPv4 setDiffServ(byte diffServ) {
        this.diffServ = diffServ;
        return this;
    }

    /**
     * @return the totalLength
     */
    public short getTotalLength() {
        return totalLength;
    }

    /**
     * @return the identification
     */
    public short getIdentification() {
        return identification;
    }

    public boolean isTruncated() {
        return isTruncated;
    }

    public void setTruncated(boolean isTruncated) {
        this.isTruncated = isTruncated;
    }

    /**
     * @param identification the identification to set
     */
    public IPv4 setIdentification(short identification) {
        this.identification = identification;
        return this;
    }

    /**
     * @return the flags
     */
    public byte getFlags() {
        return flags;
    }

    /**
     * @param flags the flags to set
     */
    public IPv4 setFlags(byte flags) {
        this.flags = flags;
        return this;
    }

    /**
     * @return the fragmentOffset
     */
    public short getFragmentOffset() {
        return fragmentOffset;
    }

    /**
     * @param fragmentOffset the fragmentOffset to set
     */
    public IPv4 setFragmentOffset(short fragmentOffset) {
        this.fragmentOffset = fragmentOffset;
        return this;
    }

    /**
     * @return the ttl
     */
    public byte getTtl() {
        return ttl;
    }

    /**
     * @param ttl the ttl to set
     */
    public IPv4 setTtl(byte ttl) {
        this.ttl = ttl;
        return this;
    }

    /**
     * @return the protocol
     */
    public byte getProtocol() {
        return protocol;
    }

    /**
     * @param protocol the protocol to set
     */
    public IPv4 setProtocol(byte protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * @return the checksum
     */
    public short getChecksum() {
        return checksum;
    }

    /**
     * @param checksum the checksum to set
     */
    public IPv4 setChecksum(short checksum) {
        this.checksum = checksum;
        return this;
    }
    @Override
    public void resetChecksum() {
        this.checksum = 0;
        super.resetChecksum();
    }

    /**
     * @return the sourceAddress
     */
    public int getSourceAddress() {
        return sourceAddress;
    }

    /**
     * @param sourceAddress the sourceAddress to set
     */
    public IPv4 setSourceAddress(int sourceAddress) {
        this.sourceAddress = sourceAddress;
        return this;
    }

    /**
     * @param sourceAddress the sourceAddress to set
     */
    public IPv4 setSourceAddress(String sourceAddress) {
        this.sourceAddress = IPv4.toIPv4Address(sourceAddress);
        return this;
    }

    /**
     * @return the destinationAddress
     */
    public int getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * @param destinationAddress the destinationAddress to set
     */
    public IPv4 setDestinationAddress(int destinationAddress) {
        this.destinationAddress = destinationAddress;
        return this;
    }

    /**
     * @param destinationAddress the destinationAddress to set
     */
    public IPv4 setDestinationAddress(String destinationAddress) {
        this.destinationAddress = IPv4.toIPv4Address(destinationAddress);
        return this;
    }

    /**
     * @return the options
     */
    public byte[] getOptions() {
        return options;
    }

    /**
     * @param options the options to set
     */
    public IPv4 setOptions(byte[] options) {
        if (options != null && (options.length % 4) > 0)
            throw new IllegalArgumentException(
                    "Options length must be a multiple of 4");
        this.options = options;
        return this;
    }

    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     *      -headerLength : 0
     *      -totalLength : 0
     */
    public byte[] serialize() {
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
        }

        int optionsLength = 0;
        if (this.options != null)
            optionsLength = this.options.length / 4;
        this.headerLength = (byte) (5 + optionsLength);

        this.totalLength = (short) (this.headerLength * 4 + ((payloadData == null) ? 0
                : payloadData.length));

        byte[] data = new byte[this.totalLength];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.put((byte) (((this.version & 0xf) << 4) | (this.headerLength & 0xf)));
        bb.put(this.diffServ);
        bb.putShort(this.totalLength);
        bb.putShort(this.identification);
        bb.putShort((short) (((this.flags & 0x7) << 13) | (this.fragmentOffset & 0x1fff)));
        bb.put(this.ttl);
        bb.put(this.protocol);
        bb.putShort(this.checksum);
        bb.putInt(this.sourceAddress);
        bb.putInt(this.destinationAddress);
        if (this.options != null)
            bb.put(this.options);
        if (payloadData != null)
            bb.put(payloadData);

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int accumulation = 0;
            for (int i = 0; i < this.headerLength * 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(10, this.checksum);
        }
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        short sscratch;

        this.version = bb.get();
        this.headerLength = (byte) (this.version & 0xf);
        this.version = (byte) ((this.version >> 4) & 0xf);
        this.diffServ = bb.get();
        this.totalLength = bb.getShort();
        this.identification = bb.getShort();
        sscratch = bb.getShort();
        this.flags = (byte) ((sscratch >> 13) & 0x7);
        this.fragmentOffset = (short) (sscratch & 0x1fff);
        this.ttl = bb.get();
        this.protocol = bb.get();
        this.checksum = bb.getShort();
        this.sourceAddress = bb.getInt();
        this.destinationAddress = bb.getInt();

        if (this.headerLength > 5) {
            int optionsLength = (this.headerLength - 5) * 4;
            this.options = new byte[optionsLength];
            bb.get(this.options);
        }

        IPacket payload;
        if (IPv4.protocolClassMap.containsKey(this.protocol)) {
            Class<? extends IPacket> clazz = IPv4.protocolClassMap.get(this.protocol);
            try {
                payload = clazz.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Error parsing payload for IPv4 packet", e);
            }
        } else {
            payload = new Data();
        }
        int payloadLength = this.totalLength - this.headerLength * 4;
        int remLength = bb.limit()-bb.position();
        if (remLength < payloadLength)
            payloadLength = bb.limit()-bb.position();
        this.payload = payload.deserialize(data, bb.position(), payloadLength);
        this.payload.setParent(this);

        if (this.totalLength > length)
            this.isTruncated = true;
        else
            this.isTruncated = false;

        return this;
    }

    /**
     * Accepts an IPv4 address of the form xxx.xxx.xxx.xxx, ie 192.168.0.1 and
     * returns the corresponding 32 bit integer.
     * @param ipAddress
     * @return
     */
    public static int toIPv4Address(String ipAddress) {
        if (ipAddress == null)
            throw new IllegalArgumentException("Specified IPv4 address must" +
                "contain 4 sets of numerical digits separated by periods");
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) 
            throw new IllegalArgumentException("Specified IPv4 address must" +
                "contain 4 sets of numerical digits separated by periods");

        int result = 0;
        for (int i = 0; i < 4; ++i) {
            int oct = Integer.valueOf(octets[i]);
            if (oct > 255 || oct < 0)
                throw new IllegalArgumentException("Octet values in specified" +
                        " IPv4 address must be 0 <= value <= 255");
            result |=  oct << ((3-i)*8);
        }
        return result;
    }

    /**
     * Accepts an IPv4 address in a byte array and returns the corresponding
     * 32-bit integer value.
     * @param ipAddress
     * @return
     */
    public static int toIPv4Address(byte[] ipAddress) {
        int ip = 0;
        for (int i = 0; i < 4; i++) {
          int t = (ipAddress[i] & 0xff) << ((3-i)*8);
          ip |= t;
        }
        return ip;
    }

    /**
     * Accepts an IPv4 address and returns of string of the form xxx.xxx.xxx.xxx
     * ie 192.168.0.1
     * 
     * @param ipAddress
     * @return
     */
    public static String fromIPv4Address(int ipAddress) {
        StringBuffer sb = new StringBuffer();
        int result = 0;
        for (int i = 0; i < 4; ++i) {
            result = (ipAddress >> ((3-i)*8)) & 0xff;
            sb.append(Integer.valueOf(result).toString());
            if (i != 3)
                sb.append(".");
        }
        return sb.toString();
    }

    /**
     * Accepts a collection of IPv4 addresses as integers and returns a single
     * String useful in toString method's containing collections of IP
     * addresses.
     * 
     * @param ipAddresses collection
     * @return
     */
    public static String fromIPv4AddressCollection(Collection<Integer> ipAddresses) {
        if (ipAddresses == null)
            return "null";
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        for (Integer ip : ipAddresses) {
            sb.append(fromIPv4Address(ip));
            sb.append(",");
        }
        sb.replace(sb.length()-1, sb.length(), "]");
        return sb.toString();
    }

    /**
     * Accepts an IPv4 address of the form xxx.xxx.xxx.xxx, ie 192.168.0.1 and
     * returns the corresponding byte array.
     * @param ipAddress The IP address in the form xx.xxx.xxx.xxx.
     * @return The IP address separated into bytes
     */
    public static byte[] toIPv4AddressBytes(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) 
            throw new IllegalArgumentException("Specified IPv4 address must" +
                "contain 4 sets of numerical digits separated by periods");

        byte[] result = new byte[4];
        for (int i = 0; i < 4; ++i) {
            result[i] = Integer.valueOf(octets[i]).byteValue();
        }
        return result;
    }
    
    /**
     * Accepts an IPv4 address in the form of an integer and
     * returns the corresponding byte array.
     * @param ipAddress The IP address as an integer.
     * @return The IP address separated into bytes.
     */
    public static byte[] toIPv4AddressBytes(int ipAddress) {
    	return new byte[] {
                (byte)(ipAddress >>> 24),
                (byte)(ipAddress >>> 16),
                (byte)(ipAddress >>> 8),
                (byte)ipAddress};
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 2521;
        int result = super.hashCode();
        result = prime * result + checksum;
        result = prime * result + destinationAddress;
        result = prime * result + diffServ;
        result = prime * result + flags;
        result = prime * result + fragmentOffset;
        result = prime * result + headerLength;
        result = prime * result + identification;
        result = prime * result + Arrays.hashCode(options);
        result = prime * result + protocol;
        result = prime * result + sourceAddress;
        result = prime * result + totalLength;
        result = prime * result + ttl;
        result = prime * result + version;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof IPv4))
            return false;
        IPv4 other = (IPv4) obj;
        if (checksum != other.checksum)
            return false;
        if (destinationAddress != other.destinationAddress)
            return false;
        if (diffServ != other.diffServ)
            return false;
        if (flags != other.flags)
            return false;
        if (fragmentOffset != other.fragmentOffset)
            return false;
        if (headerLength != other.headerLength)
            return false;
        if (identification != other.identification)
            return false;
        if (!Arrays.equals(options, other.options))
            return false;
        if (protocol != other.protocol)
            return false;
        if (sourceAddress != other.sourceAddress)
            return false;
        if (totalLength != other.totalLength)
            return false;
        if (ttl != other.ttl)
            return false;
        if (version != other.version)
            return false;
        return true;
    }
}
