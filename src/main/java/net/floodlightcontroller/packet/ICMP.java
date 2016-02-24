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

package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.IpProtocol;

/**
 * Implements ICMP packet format
 * @author shudong.zhou@bigswitch.com
 */
public class ICMP extends BasePacket {
    protected byte icmpType;
    protected byte icmpCode;
    protected short checksum;

    // The value is the number of bytes of padding
    public static final Map<Byte, Short> paddingMap;

    public static final byte ECHO_REPLY = 0x0;
    public static final byte ECHO_REQUEST = 0x8;
    public static final byte TIME_EXCEEDED = 0xB;
    public static final byte DESTINATION_UNREACHABLE = 0x3;

    public static final byte CODE_PORT_UNREACHABLE = 0x3;

    static {
        paddingMap = new HashMap<Byte, Short>();
        ICMP.paddingMap.put(ICMP.ECHO_REPLY, (short) 0);
        ICMP.paddingMap.put(ICMP.ECHO_REQUEST, (short) 0);
        ICMP.paddingMap.put(ICMP.TIME_EXCEEDED, (short) 4);
        ICMP.paddingMap.put(ICMP.DESTINATION_UNREACHABLE, (short) 4);
    }

    /**
     * @return the icmpType
     */
    public byte getIcmpType() {
        return icmpType;
    }

    /**
     * @param icmpType to set
     */
    public ICMP setIcmpType(byte icmpType) {
        this.icmpType = icmpType;
        return this;
    }

    /**
     * @return the icmp code
     */
    public byte getIcmpCode() {
        return icmpCode;
    }

    /**
     * @param icmpCode code to set
     */
    public ICMP setIcmpCode(byte icmpCode) {
        this.icmpCode = icmpCode;
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
    public ICMP setChecksum(short checksum) {
        this.checksum = checksum;
        return this;
    }

    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     *      -length : 0
     */
    @Override
    public byte[] serialize() {
        short padding = 0;
        if (paddingMap.containsKey(this.icmpType))
            padding = paddingMap.get(this.icmpType);

        int length = 4 + padding;
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
            length += payloadData.length;
        }

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.put(this.icmpType);
        bb.put(this.icmpCode);
        bb.putShort(this.checksum);
        for (int i = 0; i < padding; i++)
            bb.put((byte) 0);

        if (payloadData != null)
            bb.put(payloadData);

        if (this.parent != null && this.parent instanceof IPv4)
            ((IPv4)this.parent).setProtocol(IpProtocol.ICMP);

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int accumulation = 0;

            for (int i = 0; i < length / 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            // pad to an even number of shorts
            if (length % 2 > 0) {
                accumulation += (bb.get() & 0xff) << 8;
            }

            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(2, this.checksum);
        }
        return data;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5807;
        int result = super.hashCode();
        result = prime * result + icmpType;
        result = prime * result + icmpCode;
        result = prime * result + checksum;
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
        if (!(obj instanceof ICMP))
            return false;
        ICMP other = (ICMP) obj;
        if (icmpType != other.icmpType)
            return false;
        if (icmpCode != other.icmpCode)
            return false;
        if (checksum != other.checksum)
            return false;
        return true;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length)
            throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        this.icmpType = bb.get();
        this.icmpCode = bb.get();
        this.checksum = bb.getShort();

        short padding = 0;
        if (paddingMap.containsKey(this.icmpType))
            padding = paddingMap.get(this.icmpType);

        bb.position(bb.position() + padding);

        this.payload = new Data();
        this.payload = payload.deserialize(data, bb.position(), bb.limit()-bb.position());
        this.payload.setParent(this);
        return this;
    }
}
