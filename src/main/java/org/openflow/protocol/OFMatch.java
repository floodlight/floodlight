/**
 *    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
 *    University
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

package org.openflow.protocol;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;

import net.floodlightcontroller.packet.Ethernet;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.serializers.OFMatchJSONSerializer;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U8;

/**
 * Represents an ofp_match structure
 * 
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 */
@JsonSerialize(using = OFMatchJSONSerializer.class)
public class OFMatch implements Cloneable, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    public static int MINIMUM_LENGTH = 40;
    final public static int OFPFW_ALL = ((1 << 22) - 1);

    final public static int OFPFW_IN_PORT = 1 << 0; /* Switch input port. */
    final public static int OFPFW_DL_VLAN = 1 << 1; /* VLAN id. */
    final public static int OFPFW_DL_SRC = 1 << 2; /* Ethernet source address. */
    final public static int OFPFW_DL_DST = 1 << 3; /*
                                                    * Ethernet destination
                                                    * address.
                                                    */
    final public static int OFPFW_DL_TYPE = 1 << 4; /* Ethernet frame type. */
    final public static int OFPFW_NW_PROTO = 1 << 5; /* IP protocol. */
    final public static int OFPFW_TP_SRC = 1 << 6; /* TCP/UDP source port. */
    final public static int OFPFW_TP_DST = 1 << 7; /* TCP/UDP destination port. */

    /*
     * IP source address wildcard bit count. 0 is exact match, 1 ignores the
     * LSB, 2 ignores the 2 least-significant bits, ..., 32 and higher wildcard
     * the entire field. This is the *opposite* of the usual convention where
     * e.g. /24 indicates that 8 bits (not 24 bits) are wildcarded.
     */
    final public static int OFPFW_NW_SRC_SHIFT = 8;
    final public static int OFPFW_NW_SRC_BITS = 6;
    final public static int OFPFW_NW_SRC_MASK = ((1 << OFPFW_NW_SRC_BITS) - 1) << OFPFW_NW_SRC_SHIFT;
    final public static int OFPFW_NW_SRC_ALL = 32 << OFPFW_NW_SRC_SHIFT;

    /* IP destination address wildcard bit count. Same format as source. */
    final public static int OFPFW_NW_DST_SHIFT = 14;
    final public static int OFPFW_NW_DST_BITS = 6;
    final public static int OFPFW_NW_DST_MASK = ((1 << OFPFW_NW_DST_BITS) - 1) << OFPFW_NW_DST_SHIFT;
    final public static int OFPFW_NW_DST_ALL = 32 << OFPFW_NW_DST_SHIFT;

    final public static int OFPFW_DL_VLAN_PCP = 1 << 20; /* VLAN priority. */
    final public static int OFPFW_NW_TOS = 1 << 21; /*
                                                     * IP ToS (DSCP field, 6
                                                     * bits).
                                                     */

    final public static int OFPFW_ALL_SANITIZED = (((1 << 22) - 1)
                                                   & ~OFPFW_NW_SRC_MASK & ~OFPFW_NW_DST_MASK)
                                                  | OFPFW_NW_SRC_ALL
                                                  | OFPFW_NW_DST_ALL;

    /* List of Strings for marshalling and unmarshalling to human readable forms */
    final public static String STR_IN_PORT = "in_port";
    final public static String STR_DL_DST = "dl_dst";
    final public static String STR_DL_SRC = "dl_src";
    final public static String STR_DL_TYPE = "dl_type";
    final public static String STR_DL_VLAN = "dl_vlan";
    final public static String STR_DL_VLAN_PCP = "dl_vlan_pcp";
    final public static String STR_NW_DST = "nw_dst";
    final public static String STR_NW_SRC = "nw_src";
    final public static String STR_NW_PROTO = "nw_proto";
    final public static String STR_NW_TOS = "nw_tos";
    final public static String STR_TP_DST = "tp_dst";
    final public static String STR_TP_SRC = "tp_src";

    protected int wildcards;
    protected short inputPort;
    protected byte[] dataLayerSource;
    protected byte[] dataLayerDestination;
    protected short dataLayerVirtualLan;
    protected byte dataLayerVirtualLanPriorityCodePoint;
    protected short dataLayerType;
    protected byte networkTypeOfService;
    protected byte networkProtocol;
    protected int networkSource;
    protected int networkDestination;
    protected short transportSource;
    protected short transportDestination;

    /**
     * By default, create a OFMatch that matches everything (mostly because it's
     * the least amount of work to make a valid OFMatch)
     */
    public OFMatch() {
        this.wildcards = OFPFW_ALL;
        this.dataLayerDestination = new byte[] { 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0 };
        this.dataLayerSource = new byte[] { 0x0, 0x0, 0x0, 0x0, 0x0, 0x0 };
        this.dataLayerVirtualLan = Ethernet.VLAN_UNTAGGED;
        this.dataLayerVirtualLanPriorityCodePoint = 0;
        this.dataLayerType = 0;
        this.inputPort = 0;
        this.networkProtocol = 0;
        this.networkTypeOfService = 0;
        this.networkSource = 0;
        this.networkDestination = 0;
        this.transportDestination = 0;
        this.transportSource = 0;
    }

    /**
     * Get dl_dst
     * 
     * @return an arrays of bytes
     */
    public byte[] getDataLayerDestination() {
        return this.dataLayerDestination;
    }

    /**
     * Set dl_dst
     * 
     * @param dataLayerDestination
     */
    public OFMatch setDataLayerDestination(byte[] dataLayerDestination) {
        this.dataLayerDestination = dataLayerDestination;
        return this;
    }

    /**
     * Set dl_dst, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFMatch setDataLayerDestination(String mac) {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                              throw new IllegalArgumentException(
                                                                 "expected string with 6 octets, got '"
                                                                         + mac
                                                                         + "'");
        this.dataLayerDestination = bytes;
        return this;
    }

    /**
     * Get dl_src
     * 
     * @return an array of bytes
     */
    public byte[] getDataLayerSource() {
        return this.dataLayerSource;
    }

    /**
     * Set dl_src
     * 
     * @param dataLayerSource
     */
    public OFMatch setDataLayerSource(byte[] dataLayerSource) {
        this.dataLayerSource = dataLayerSource;
        return this;
    }

    /**
     * Set dl_src, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFMatch setDataLayerSource(String mac) {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                              throw new IllegalArgumentException(
                                                                 "expected string with 6 octets, got '"
                                                                         + mac
                                                                         + "'");
        this.dataLayerSource = bytes;
        return this;
    }

    /**
     * Get dl_type
     * 
     * @return ether_type
     */
    public short getDataLayerType() {
        return this.dataLayerType;
    }

    /**
     * Set dl_type
     * 
     * @param dataLayerType
     */
    public OFMatch setDataLayerType(short dataLayerType) {
        this.dataLayerType = dataLayerType;
        return this;
    }

    /**
     * Get dl_vlan
     * 
     * @return vlan tag; VLAN_NONE == no tag
     */
    public short getDataLayerVirtualLan() {
        return this.dataLayerVirtualLan;
    }

    /**
     * Set dl_vlan
     * 
     * @param dataLayerVirtualLan
     */
    public OFMatch setDataLayerVirtualLan(short dataLayerVirtualLan) {
        this.dataLayerVirtualLan = dataLayerVirtualLan;
        return this;
    }

    /**
     * Get dl_vlan_pcp
     * 
     * @return
     */
    public byte getDataLayerVirtualLanPriorityCodePoint() {
        return this.dataLayerVirtualLanPriorityCodePoint;
    }

    /**
     * Set dl_vlan_pcp
     * 
     * @param pcp
     */
    public OFMatch setDataLayerVirtualLanPriorityCodePoint(byte pcp) {
        this.dataLayerVirtualLanPriorityCodePoint = pcp;
        return this;
    }

    /**
     * Get in_port
     * 
     * @return
     */
    public short getInputPort() {
        return this.inputPort;
    }

    /**
     * Set in_port
     * 
     * @param inputPort
     */
    public OFMatch setInputPort(short inputPort) {
        this.inputPort = inputPort;
        return this;
    }

    /**
     * Get nw_dst
     * 
     * @return
     */
    public int getNetworkDestination() {
        return this.networkDestination;
    }

    /**
     * Set nw_dst
     * 
     * @param networkDestination
     */
    public OFMatch setNetworkDestination(int networkDestination) {
        this.networkDestination = networkDestination;
        return this;
    }

    /**
     * Parse this match's wildcard fields and return the number of significant
     * bits in the IP destination field. NOTE: this returns the number of bits
     * that are fixed, i.e., like CIDR, not the number of bits that are free
     * like OpenFlow encodes.
     * 
     * @return a number between 0 (matches all IPs) and 63 ( 32>= implies exact
     *         match)
     */
    public int getNetworkDestinationMaskLen() {
        return Math.max(32 - ((wildcards & OFPFW_NW_DST_MASK) >> OFPFW_NW_DST_SHIFT),
                        0);
    }

    /**
     * Parse this match's wildcard fields and return the number of significant
     * bits in the IP destination field. NOTE: this returns the number of bits
     * that are fixed, i.e., like CIDR, not the number of bits that are free
     * like OpenFlow encodes.
     * 
     * @return a number between 0 (matches all IPs) and 32 (exact match)
     */
    public int getNetworkSourceMaskLen() {
        return Math.max(32 - ((wildcards & OFPFW_NW_SRC_MASK) >> OFPFW_NW_SRC_SHIFT),
                        0);
    }

    /**
     * Get nw_proto
     * 
     * @return
     */
    public byte getNetworkProtocol() {
        return this.networkProtocol;
    }

    /**
     * Set nw_proto
     * 
     * @param networkProtocol
     */
    public OFMatch setNetworkProtocol(byte networkProtocol) {
        this.networkProtocol = networkProtocol;
        return this;
    }

    /**
     * Get nw_src
     * 
     * @return
     */
    public int getNetworkSource() {
        return this.networkSource;
    }

    /**
     * Set nw_src
     * 
     * @param networkSource
     */
    public OFMatch setNetworkSource(int networkSource) {
        this.networkSource = networkSource;
        return this;
    }

    /**
     * Get nw_tos OFMatch stores the ToS bits as top 6-bits, so right shift by 2
     * bits before returning the value
     * 
     * @return : 6-bit DSCP value (0-63)
     */
    public byte getNetworkTypeOfService() {
        return (byte) ((this.networkTypeOfService >> 2) & 0x3f);
    }

    /**
     * Set nw_tos OFMatch stores the ToS bits as top 6-bits, so left shift by 2
     * bits before storing the value
     * 
     * @param networkTypeOfService
     *            : 6-bit DSCP value (0-63)
     */
    public OFMatch setNetworkTypeOfService(byte networkTypeOfService) {
        this.networkTypeOfService = (byte) (networkTypeOfService << 2);
        return this;
    }

    /**
     * Get tp_dst
     * 
     * @return
     */
    public short getTransportDestination() {
        return this.transportDestination;
    }

    /**
     * Set tp_dst
     * 
     * @param transportDestination
     */
    public OFMatch setTransportDestination(short transportDestination) {
        this.transportDestination = transportDestination;
        return this;
    }

    /**
     * Get tp_src
     * 
     * @return
     */
    public short getTransportSource() {
        return this.transportSource;
    }

    /**
     * Set tp_src
     * 
     * @param transportSource
     */
    public OFMatch setTransportSource(short transportSource) {
        this.transportSource = transportSource;
        return this;
    }

    /**
     * Get wildcards
     * 
     * @return
     */
    public int getWildcards() {
        return this.wildcards;
    }

    /**
     * Get wildcards
     * 
     * @return
     */
    public Wildcards getWildcardObj() {
        return Wildcards.of(wildcards);
    }

    /**
     * Set wildcards
     * 
     * @param wildcards
     */
    public OFMatch setWildcards(int wildcards) {
        this.wildcards = wildcards;
        return this;
    }

    /** set the wildcard using the Wildcards convenience object */
    public OFMatch setWildcards(Wildcards wildcards) {
        this.wildcards = wildcards.getInt();
        return this;
    }

    /**
     * Initializes this OFMatch structure with the corresponding data from the
     * specified packet. Must specify the input port, to ensure that
     * this.in_port is set correctly. Specify OFPort.NONE or OFPort.ANY if input
     * port not applicable or available
     * 
     * @param packetData
     *            The packet's data
     * @param inputPort
     *            the port the packet arrived on
     */
    public OFMatch loadFromPacket(byte[] packetData, short inputPort) {
        short scratch;
        int transportOffset = 34;
        ByteBuffer packetDataBB = ByteBuffer.wrap(packetData);
        int limit = packetDataBB.limit();

        this.wildcards = 0; // all fields have explicit entries

        this.inputPort = inputPort;

        if (inputPort == OFPort.OFPP_ALL.getValue())
                                                    this.wildcards |= OFPFW_IN_PORT;

        assert (limit >= 14);
        // dl dst
        this.dataLayerDestination = new byte[6];
        packetDataBB.get(this.dataLayerDestination);
        // dl src
        this.dataLayerSource = new byte[6];
        packetDataBB.get(this.dataLayerSource);
        // dl type
        this.dataLayerType = packetDataBB.getShort();

        if (getDataLayerType() != (short) 0x8100) { // need cast to avoid signed
            // bug
            setDataLayerVirtualLan((short) 0xffff);
            setDataLayerVirtualLanPriorityCodePoint((byte) 0);
        } else {
            // has vlan tag
            scratch = packetDataBB.getShort();
            setDataLayerVirtualLan((short) (0xfff & scratch));
            setDataLayerVirtualLanPriorityCodePoint((byte) ((0xe000 & scratch) >> 13));
            this.dataLayerType = packetDataBB.getShort();
        }

        switch (getDataLayerType()) {
            case 0x0800:
                // ipv4
                // check packet length
                scratch = packetDataBB.get();
                scratch = (short) (0xf & scratch);
                transportOffset = (packetDataBB.position() - 1)
                                  + (scratch * 4);
                // nw tos (dscp)
                scratch = packetDataBB.get();
                setNetworkTypeOfService((byte) ((0xfc & scratch) >> 2));
                // nw protocol
                packetDataBB.position(packetDataBB.position() + 7);
                this.networkProtocol = packetDataBB.get();
                // nw src
                packetDataBB.position(packetDataBB.position() + 2);
                this.networkSource = packetDataBB.getInt();
                // nw dst
                this.networkDestination = packetDataBB.getInt();
                packetDataBB.position(transportOffset);
                break;
            case 0x0806:
                // arp
                int arpPos = packetDataBB.position();
                // opcode
                scratch = packetDataBB.getShort(arpPos + 6);
                setNetworkProtocol((byte) (0xff & scratch));

                scratch = packetDataBB.getShort(arpPos + 2);
                // if ipv4 and addr len is 4
                if (scratch == 0x800 && packetDataBB.get(arpPos + 5) == 4) {
                    // nw src
                    this.networkSource = packetDataBB.getInt(arpPos + 14);
                    // nw dst
                    this.networkDestination = packetDataBB.getInt(arpPos + 24);
                } else {
                    setNetworkSource(0);
                    setNetworkDestination(0);
                }
                break;
            default:
                setNetworkTypeOfService((byte) 0);
                setNetworkProtocol((byte) 0);
                setNetworkSource(0);
                setNetworkDestination(0);
                break;
        }

        switch (getNetworkProtocol()) {
            case 0x01:
                // icmp
                // type
                this.transportSource = U8.f(packetDataBB.get());
                // code
                this.transportDestination = U8.f(packetDataBB.get());
                break;
            case 0x06:
                // tcp
                // tcp src
                this.transportSource = packetDataBB.getShort();
                // tcp dest
                this.transportDestination = packetDataBB.getShort();
                break;
            case 0x11:
                // udp
                // udp src
                this.transportSource = packetDataBB.getShort();
                // udp dest
                this.transportDestination = packetDataBB.getShort();
                break;
            default:
                setTransportDestination((short) 0);
                setTransportSource((short) 0);
                break;
        }
        return this;
    }

    /**
     * Read this message off the wire from the specified ByteBuffer
     * 
     * @param data
     */
    public void readFrom(ChannelBuffer data) {
        this.wildcards = data.readInt();
        this.inputPort = data.readShort();
        this.dataLayerSource = new byte[6];
        data.readBytes(this.dataLayerSource);
        this.dataLayerDestination = new byte[6];
        data.readBytes(this.dataLayerDestination);
        this.dataLayerVirtualLan = data.readShort();
        this.dataLayerVirtualLanPriorityCodePoint = data.readByte();
        data.readByte(); // pad
        this.dataLayerType = data.readShort();
        this.networkTypeOfService = data.readByte();
        this.networkProtocol = data.readByte();
        data.readByte(); // pad
        data.readByte(); // pad
        this.networkSource = data.readInt();
        this.networkDestination = data.readInt();
        this.transportSource = data.readShort();
        this.transportDestination = data.readShort();
    }

    /**
     * Write this message's binary format to the specified ByteBuffer
     * 
     * @param data
     */
    public void writeTo(ChannelBuffer data) {
        data.writeInt(wildcards);
        data.writeShort(inputPort);
        data.writeBytes(this.dataLayerSource);
        data.writeBytes(this.dataLayerDestination);
        data.writeShort(dataLayerVirtualLan);
        data.writeByte(dataLayerVirtualLanPriorityCodePoint);
        data.writeByte((byte) 0x0); // pad
        data.writeShort(dataLayerType);
        data.writeByte(networkTypeOfService);
        data.writeByte(networkProtocol);
        data.writeByte((byte) 0x0); // pad
        data.writeByte((byte) 0x0); // pad
        data.writeInt(networkSource);
        data.writeInt(networkDestination);
        data.writeShort(transportSource);
        data.writeShort(transportDestination);
    }

    @Override
    public int hashCode() {
        final int prime = 131;
        int result = 1;
        result = prime * result + Arrays.hashCode(dataLayerDestination);
        result = prime * result + Arrays.hashCode(dataLayerSource);
        result = prime * result + dataLayerType;
        result = prime * result + dataLayerVirtualLan;
        result = prime * result + dataLayerVirtualLanPriorityCodePoint;
        result = prime * result + inputPort;
        result = prime * result + networkDestination;
        result = prime * result + networkProtocol;
        result = prime * result + networkSource;
        result = prime * result + networkTypeOfService;
        result = prime * result + transportDestination;
        result = prime * result + transportSource;
        result = prime * result + wildcards;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OFMatch)) {
            return false;
        }
        OFMatch other = (OFMatch) obj;
        if (!Arrays.equals(dataLayerDestination, other.dataLayerDestination)) {
            return false;
        }
        if (!Arrays.equals(dataLayerSource, other.dataLayerSource)) {
            return false;
        }
        if (dataLayerType != other.dataLayerType) {
            return false;
        }
        if (dataLayerVirtualLan != other.dataLayerVirtualLan) {
            return false;
        }
        if (dataLayerVirtualLanPriorityCodePoint != other.dataLayerVirtualLanPriorityCodePoint) {
            return false;
        }
        if (inputPort != other.inputPort) {
            return false;
        }
        if (networkDestination != other.networkDestination) {
            return false;
        }
        if (networkProtocol != other.networkProtocol) {
            return false;
        }
        if (networkSource != other.networkSource) {
            return false;
        }
        if (networkTypeOfService != other.networkTypeOfService) {
            return false;
        }
        if (transportDestination != other.transportDestination) {
            return false;
        }
        if (transportSource != other.transportSource) {
            return false;
        }
        if ((wildcards & OFMatch.OFPFW_ALL) != (other.wildcards & OFPFW_ALL)) { // only
            // consider
            // allocated
            // part
            // of
            // wildcards
            return false;
        }
        return true;
    }

    /**
     * Implement clonable interface
     */
    @Override
    public OFMatch clone() {
        try {
            OFMatch ret = (OFMatch) super.clone();
            ret.dataLayerDestination = this.dataLayerDestination.clone();
            ret.dataLayerSource = this.dataLayerSource.clone();
            return ret;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Output a dpctl-styled string, i.e., only list the elements that are not
     * wildcarded A match-everything OFMatch outputs "OFMatch[]"
     * 
     * @return 
     *         "OFMatch[dl_src:00:20:01:11:22:33,nw_src:192.168.0.0/24,tp_dst:80]"
     */
    @Override
    public String toString() {
        String str = "";

        // l1
        if ((wildcards & OFPFW_IN_PORT) == 0)
                                             str += "," + STR_IN_PORT + "="
                                                    + U16.f(this.inputPort);

        // l2
        if ((wildcards & OFPFW_DL_DST) == 0)
                                            str += ","
                                                   + STR_DL_DST
                                                   + "="
                                                   + HexString.toHexString(this.dataLayerDestination);
        if ((wildcards & OFPFW_DL_SRC) == 0)
                                            str += ","
                                                   + STR_DL_SRC
                                                   + "="
                                                   + HexString.toHexString(this.dataLayerSource);
        if ((wildcards & OFPFW_DL_TYPE) == 0)
                                             str += ","
                                                    + STR_DL_TYPE
                                                    + "=0x"
                                                    + Integer.toHexString(U16.f(this.dataLayerType));
        if ((wildcards & OFPFW_DL_VLAN) == 0)
                                             str += ","
                                                    + STR_DL_VLAN
                                                    + "=0x"
                                                    + Integer.toHexString(U16.f(this.dataLayerVirtualLan));
        if ((wildcards & OFPFW_DL_VLAN_PCP) == 0)
                                                 str += ","
                                                        + STR_DL_VLAN_PCP
                                                        + "="
                                                        + Integer.toHexString(U8.f(this.dataLayerVirtualLanPriorityCodePoint));

        // l3
        if (getNetworkDestinationMaskLen() > 0)
                                               str += ","
                                                      + STR_NW_DST
                                                      + "="
                                                      + cidrToString(networkDestination,
                                                                     getNetworkDestinationMaskLen());
        if (getNetworkSourceMaskLen() > 0)
                                          str += ","
                                                 + STR_NW_SRC
                                                 + "="
                                                 + cidrToString(networkSource,
                                                                getNetworkSourceMaskLen());
        if ((wildcards & OFPFW_NW_PROTO) == 0)
                                              str += "," + STR_NW_PROTO
                                                     + "="
                                                     + this.networkProtocol;
        if ((wildcards & OFPFW_NW_TOS) == 0)
                                            str += ","
                                                   + STR_NW_TOS
                                                   + "="
                                                   + this.getNetworkTypeOfService();

        // l4
        if ((wildcards & OFPFW_TP_DST) == 0)
                                            str += ","
                                                   + STR_TP_DST
                                                   + "="
                                                   + this.transportDestination;
        if ((wildcards & OFPFW_TP_SRC) == 0)
                                            str += "," + STR_TP_SRC + "="
                                                   + this.transportSource;
        if ((str.length() > 0) && (str.charAt(0) == ','))
                                                         str = str.substring(1); // trim
                                                                                 // the
                                                                                 // leading
                                                                                 // ","
        // done
        return "OFMatch[" + str + "]";
    }

    /**
     * debug a set of wildcards
     */
    public static String debugWildCards(int wildcards) {
        String str = "";

        // l1
        if ((wildcards & OFPFW_IN_PORT) != 0) str += "|" + STR_IN_PORT;

        // l2
        if ((wildcards & OFPFW_DL_DST) != 0) str += "|" + STR_DL_DST;
        if ((wildcards & OFPFW_DL_SRC) != 0) str += "|" + STR_DL_SRC;
        if ((wildcards & OFPFW_DL_TYPE) != 0) str += "|" + STR_DL_TYPE;
        if ((wildcards & OFPFW_DL_VLAN) != 0) str += "|" + STR_DL_VLAN;
        if ((wildcards & OFPFW_DL_VLAN_PCP) != 0)
                                                 str += "|"
                                                        + STR_DL_VLAN_PCP;

        int nwDstMask = Math.max(32 - ((wildcards & OFPFW_NW_DST_MASK) >> OFPFW_NW_DST_SHIFT),
                                 0);
        int nwSrcMask = Math.max(32 - ((wildcards & OFPFW_NW_SRC_MASK) >> OFPFW_NW_SRC_SHIFT),
                                 0);

        // l3
        if (nwDstMask < 32)
                           str += "|" + STR_NW_DST + "(/" + nwDstMask + ")";

        if (nwSrcMask < 32)
                           str += "|" + STR_NW_SRC + "(/" + nwSrcMask + ")";

        if ((wildcards & OFPFW_NW_PROTO) != 0) str += "|" + STR_NW_PROTO;
        if ((wildcards & OFPFW_NW_TOS) != 0) str += "|" + STR_NW_TOS;

        // l4
        if ((wildcards & OFPFW_TP_DST) != 0) str += "|" + STR_TP_DST;
        if ((wildcards & OFPFW_TP_SRC) != 0) str += "|" + STR_TP_SRC;
        if ((str.length() > 0) && (str.charAt(0) == '|'))
                                                         str = str.substring(1); // trim
                                                                                 // the
                                                                                 // leading
                                                                                 // ","
        // done
        return str;
    }

    private String cidrToString(int ip, int prefix) {
        String str;
        if (prefix >= 32) {
            str = ipToString(ip);
        } else {
            // use the negation of mask to fake endian magic
            int mask = ~((1 << (32 - prefix)) - 1);
            str = ipToString(ip & mask) + "/" + prefix;
        }

        return str;
    }

    /**
     * Set this OFMatch's parameters based on a comma-separated key=value pair
     * dpctl-style string, e.g., from the output of OFMatch.toString() <br>
     * <p>
     * Supported keys/values include <br>
     * <p>
     * <TABLE border=1>
     * <TR>
     * <TD>KEY(s)
     * <TD>VALUE
     * </TR>
     * <TR>
     * <TD>"in_port","input_port"
     * <TD>integer
     * </TR>
     * <TR>
     * <TD>"dl_src","eth_src", "dl_dst","eth_dst"
     * <TD>hex-string
     * </TR>
     * <TR>
     * <TD>"dl_type", "dl_vlan", "dl_vlan_pcp"
     * <TD>integer
     * </TR>
     * <TR>
     * <TD>"nw_src", "nw_dst", "ip_src", "ip_dst"
     * <TD>CIDR-style netmask
     * </TR>
     * <TR>
     * <TD>"tp_src","tp_dst"
     * <TD>integer (max 64k)
     * </TR>
     * </TABLE>
     * <p>
     * The CIDR-style netmasks assume 32 netmask if none given, so:
     * "128.8.128.118/32" is the same as "128.8.128.118"
     * 
     * @param match
     *            a key=value comma separated string, e.g.
     *            "in_port=5,ip_dst=192.168.0.0/16,tp_src=80"
     * @throws IllegalArgumentException
     *             on unexpected key or value
     */

    public void fromString(String match) throws IllegalArgumentException {
        if (match.equals("") || match.equalsIgnoreCase("any")
            || match.equalsIgnoreCase("all") || match.equals("[]"))
                                                                   match = "OFMatch[]";
        String[] tokens = match.split("[\\[,\\]]");
        String[] values;
        int initArg = 0;
        if (tokens[0].equals("OFMatch")) initArg = 1;
        this.wildcards = OFPFW_ALL;
        int i;
        for (i = initArg; i < tokens.length; i++) {
            values = tokens[i].split("=");
            if (values.length != 2)
                                   throw new IllegalArgumentException(
                                                                      "Token "
                                                                              + tokens[i]
                                                                              + " does not have form 'key=value' parsing "
                                                                              + match);
            values[0] = values[0].toLowerCase(); // try to make this case insens
            if (values[0].equals(STR_IN_PORT)
                || values[0].equals("input_port")) {
                this.inputPort = U16.t(Integer.valueOf(values[1]));
                this.wildcards &= ~OFPFW_IN_PORT;
            } else if (values[0].equals(STR_DL_DST)
                       || values[0].equals("eth_dst")) {
                this.dataLayerDestination = HexString.fromHexString(values[1]);
                this.wildcards &= ~OFPFW_DL_DST;
            } else if (values[0].equals(STR_DL_SRC)
                       || values[0].equals("eth_src")) {
                this.dataLayerSource = HexString.fromHexString(values[1]);
                this.wildcards &= ~OFPFW_DL_SRC;
            } else if (values[0].equals(STR_DL_TYPE)
                       || values[0].equals("eth_type")) {
                if (values[1].startsWith("0x"))
                    this.dataLayerType = U16.t(Integer.valueOf(values[1].replaceFirst("0x",
                                                                                      ""),
                                                               16));
                else
                    this.dataLayerType = U16.t(Integer.valueOf(values[1]));
                this.wildcards &= ~OFPFW_DL_TYPE;
            } else if (values[0].equals(STR_DL_VLAN)) {
                if (values[1].startsWith("0x"))
                    this.dataLayerVirtualLan = U16.t(Integer.valueOf(values[1].replaceFirst("0x",
                                                                                            ""),
                                                                     16));
                else
                    this.dataLayerVirtualLan = U16.t(Integer.valueOf(values[1]));
                this.wildcards &= ~OFPFW_DL_VLAN;
            } else if (values[0].equals(STR_DL_VLAN_PCP)) {
                this.dataLayerVirtualLanPriorityCodePoint = U8.t(Short.valueOf(values[1]));
                this.wildcards &= ~OFPFW_DL_VLAN_PCP;
            } else if (values[0].equals(STR_NW_DST)
                       || values[0].equals("ip_dst")) {
                setFromCIDR(values[1], STR_NW_DST);
            } else if (values[0].equals(STR_NW_SRC)
                       || values[0].equals("ip_src")) {
                setFromCIDR(values[1], STR_NW_SRC);
            } else if (values[0].equals(STR_NW_PROTO)) {
                if (values[1].startsWith("0x"))
                    this.networkProtocol = U8.t(Short.valueOf(values[1].replaceFirst("0x",""),16));
                else
                    this.networkProtocol = U8.t(Short.valueOf(values[1]));
                this.wildcards &= ~OFPFW_NW_PROTO;
            } else if (values[0].equals(STR_NW_TOS)) {
                this.setNetworkTypeOfService(U8.t(Short.valueOf(values[1])));
                this.wildcards &= ~OFPFW_NW_TOS;
            } else if (values[0].equals(STR_TP_DST)) {
                this.transportDestination = U16.t(Integer.valueOf(values[1]));
                this.wildcards &= ~OFPFW_TP_DST;
            } else if (values[0].equals(STR_TP_SRC)) {
                this.transportSource = U16.t(Integer.valueOf(values[1]));
                this.wildcards &= ~OFPFW_TP_SRC;
            } else {
                throw new IllegalArgumentException("unknown token "
                                                   + tokens[i] + " parsing "
                                                   + match);
            }
        }
    }

    /**
     * Set the networkSource or networkDestionation address and their wildcards
     * from the CIDR string
     * 
     * @param cidr
     *            "192.168.0.0/16" or "172.16.1.5"
     * @param which
     *            one of STR_NW_DST or STR_NW_SRC
     * @throws IllegalArgumentException
     */
    private
            void
            setFromCIDR(String cidr, String which)
                                                  throws IllegalArgumentException {
        String values[] = cidr.split("/");
        String[] ip_str = values[0].split("\\.");
        int ip = 0;
        ip += Integer.valueOf(ip_str[0]) << 24;
        ip += Integer.valueOf(ip_str[1]) << 16;
        ip += Integer.valueOf(ip_str[2]) << 8;
        ip += Integer.valueOf(ip_str[3]);
        int prefix = 32; // all bits are fixed, by default

        if (values.length >= 2) prefix = Integer.valueOf(values[1]);
        int mask = 32 - prefix;
        if (which.equals(STR_NW_DST)) {
            this.networkDestination = ip;
            this.wildcards = (wildcards & ~OFPFW_NW_DST_MASK)
                             | (mask << OFPFW_NW_DST_SHIFT);
        } else if (which.equals(STR_NW_SRC)) {
            this.networkSource = ip;
            this.wildcards = (wildcards & ~OFPFW_NW_SRC_MASK)
                             | (mask << OFPFW_NW_SRC_SHIFT);
        }
    }

    protected static String ipToString(int ip) {
        return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24)))
               + "." + Integer.toString((ip & 0x00ff0000) >> 16) + "."
               + Integer.toString((ip & 0x0000ff00) >> 8) + "."
               + Integer.toString(ip & 0x000000ff);
    }
}
