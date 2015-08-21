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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class Ethernet extends BasePacket {
    private static String HEXES = "0123456789ABCDEF";
    public static final short TYPE_ARP = 0x0806;
    public static final short TYPE_RARP = (short) 0x8035;
    public static final short TYPE_IPv4 = 0x0800;
    public static final short TYPE_IPv6 = (short) 0x86DD;
    public static final short TYPE_LLDP = (short) 0x88cc;
    public static final short TYPE_BSN = (short) 0x8942;
    public static final short VLAN_UNTAGGED = VlanVid.ZERO.getVlan(); // untagged vlan must be 0x0000 for loxi. We can use the convenient ZERO field
    public static final short DATALAYER_ADDRESS_LENGTH = 6; // bytes
    public static Map<Short, Class<? extends IPacket>> etherTypeClassMap;

    static {
        etherTypeClassMap = new HashMap<Short, Class<? extends IPacket>>();
        etherTypeClassMap.put(TYPE_ARP, ARP.class);
        etherTypeClassMap.put(TYPE_RARP, ARP.class);
        etherTypeClassMap.put(TYPE_IPv4, IPv4.class);
        etherTypeClassMap.put(TYPE_IPv6, IPv6.class);
        etherTypeClassMap.put(TYPE_LLDP, LLDP.class);
        etherTypeClassMap.put(TYPE_BSN, BSN.class);
    }

    protected MacAddress destinationMACAddress;
    protected MacAddress sourceMACAddress;
    protected byte priorityCode;
    protected short vlanID;
    protected EthType etherType;
    protected boolean pad = false;

    /**
     * By default, set Ethernet to untagged
     */
    public Ethernet() {
        super();
        this.vlanID = VLAN_UNTAGGED;
    }

    /**
     * @return the destination MAC
     */
    public MacAddress getDestinationMACAddress() {
        return destinationMACAddress;
    }

    /**
     * @param destinationMACAddress the destination MAC to set
     */
    public Ethernet setDestinationMACAddress(byte[] destinationMACAddress) {
        this.destinationMACAddress = MacAddress.of(destinationMACAddress);
        return this;
    }

    /**
     * @param destinationMACAddress the destination MAC to set
     */
    public Ethernet setDestinationMACAddress(MacAddress destinationMACAddress) {
        this.destinationMACAddress = destinationMACAddress;
        return this;
    }

    /**
     * @param destinationMACAddress the destination MAC to set
     */
    public Ethernet setDestinationMACAddress(String destinationMACAddress) {
        this.destinationMACAddress = MacAddress.of(destinationMACAddress);
        return this;
    }

    /**
     * @return the source MACAddress
     */
    public MacAddress getSourceMACAddress() {
        return sourceMACAddress;
    }

    /**
     * @param sourceMACAddress the source MAC to set
     */
    public Ethernet setSourceMACAddress(byte[] sourceMACAddress) {
        this.sourceMACAddress = MacAddress.of(sourceMACAddress);
        return this;
    }

    /**
     * @param sourceMACAddress the source MAC to set
     */
    public Ethernet setSourceMACAddress(MacAddress sourceMACAddress) {
        this.sourceMACAddress = sourceMACAddress;
        return this;
    }

    /**
     * @param sourceMACAddress the source MAC to set
     */
    public Ethernet setSourceMACAddress(String sourceMACAddress) {
        this.sourceMACAddress = MacAddress.of(sourceMACAddress);
        return this;
    }

    /**
     * @return the priorityCode
     */
    public byte getPriorityCode() {
        return priorityCode;
    }

    /**
     * @param priorityCode the priorityCode to set
     */
    public Ethernet setPriorityCode(byte priorityCode) {
        this.priorityCode = priorityCode;
        return this;
    }

    /**
     * @return the vlanID
     */
    public short getVlanID() {
        return vlanID;
    }

    /**
     * @param vlanID the vlanID to set
     */
    public Ethernet setVlanID(short vlanID) {
        this.vlanID = vlanID;
        return this;
    }

    /**
     * @return the etherType
     */
    public EthType getEtherType() {
        return etherType;
    }

    /**
     * @param etherType the etherType to set
     */
    public Ethernet setEtherType(EthType etherType) {
        this.etherType = etherType;
        return this;
    }

    /**
     * @return True if the Ethernet frame is broadcast, false otherwise
     */
    public boolean isBroadcast() {
        assert(destinationMACAddress.getLength() == 6);
        return destinationMACAddress.isBroadcast();
    }

    /**
     * @return True is the Ethernet frame is multicast, False otherwise
     */
    public boolean isMulticast() {
        return destinationMACAddress.isMulticast();
    }
    /**
     * Pad this packet to 60 bytes minimum, filling with zeros?
     * @return the pad
     */
    public boolean isPad() {
        return pad;
    }

    /**
     * Pad this packet to 60 bytes minimum, filling with zeros?
     * @param pad the pad to set
     */
    public Ethernet setPad(boolean pad) {
        this.pad = pad;
        return this;
    }

    public byte[] serialize() {
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
        }
        int length = 14 + ((vlanID == VLAN_UNTAGGED) ? 0 : 4) +
                          ((payloadData == null) ? 0 : payloadData.length);
        if (pad && length < 60) {
            length = 60;
        }
        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(destinationMACAddress.getBytes());
        bb.put(sourceMACAddress.getBytes());
        if (vlanID != VLAN_UNTAGGED) {
            bb.putShort((short) EthType.VLAN_FRAME.getValue());
            bb.putShort((short) ((priorityCode << 13) | (vlanID & 0x0fff)));
        }
        bb.putShort((short) etherType.getValue());
        if (payloadData != null)
            bb.put(payloadData);
        if (pad) {
            Arrays.fill(data, bb.position(), data.length, (byte)0x0);
        }
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        if (length <= 16)  // Ethernet packet minimum should be 60, this is reasonable
            return null;
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        if (this.destinationMACAddress == null)
            this.destinationMACAddress = MacAddress.of(new byte[6]);
        byte[] dstAddr = new byte[MacAddress.NONE.getLength()];
        bb.get(dstAddr);
        this.destinationMACAddress = MacAddress.of(dstAddr);

        if (this.sourceMACAddress == null)
            this.sourceMACAddress = MacAddress.of(new byte[6]);
        byte[] srcAddr = new byte[MacAddress.NONE.getLength()];
        bb.get(srcAddr);
        this.sourceMACAddress = MacAddress.of(srcAddr);

        /*
         * The ethertype is represented as 2 bytes in the packet header;
         * however, EthType can only parse an int. Negative shorts (1 in
         * the most sig place b/c 2's complement) are still valid ethertypes,
         * but it doesn't appear this way unless we treat the sign bit as
         * part of an unsigned number. If we natively cast the short to an
         * integer, it will sign extend into the extra bytes when in fact
         * it should still be a positive number. We can bitmask to force the
         * upper bytes to remain 0's. TODO this should be incorporated into
         * loxigen.
         */
        EthType etherType = EthType.of(bb.getShort() & 0xffff);
        if (etherType == EthType.VLAN_FRAME) { /* In loxigen, EthType will give the same objects for a given ethertype --> shallow compare. */
            short tci = bb.getShort();
            this.priorityCode = (byte) ((tci >> 13) & 0x07);
            this.vlanID = (short) (tci & 0x0fff);
            etherType = EthType.of(bb.getShort() & 0xffff);
        } else {
            this.vlanID = VLAN_UNTAGGED;
        }
        this.etherType = etherType;

        IPacket payload;
        if (Ethernet.etherTypeClassMap.containsKey((short) this.etherType.getValue())) {
            Class<? extends IPacket> clazz = Ethernet.etherTypeClassMap.get((short) this.etherType.getValue());
            try {
                payload = clazz.newInstance();
                this.payload = payload.deserialize(data, bb.position(), bb.limit() - bb.position());
            } catch (PacketParsingException e) {
                if (log.isTraceEnabled()) {
                    log.trace("Failed to parse ethernet packet {}->{}" +
                            " payload as {}, treat as plain ethernet packet",
                            new Object[] {this.sourceMACAddress,
                                          this.destinationMACAddress,
                                          clazz.getClass().getName()});
                    log.trace("Exception from parsing {}", e);
                }
                this.payload = new Data(data);
            } catch (InstantiationException e) {
                if (log.isTraceEnabled()) {
                    log.trace("Fail to instantiate class {}, {}",
                              clazz.getClass().getName(), e);
                }
                this.payload = new Data(data);
            } catch (IllegalAccessException e) {
                if (log.isTraceEnabled()) {
                    log.trace("Fail to access class for instantiation {}, {}",
                              clazz.getClass().getName(), e);
                }
                this.payload = new Data(data);
            } catch (RuntimeException e) {
                if (log.isTraceEnabled()) {
                    log.trace("Runtime exception during packet parsing {}", e);
                }
                this.payload = new Data(data);
            }
        } else {
            this.payload = new Data(data);
        }
        this.payload.setParent(this);
        return this;
    }

    /**
     * Checks to see if a string is a valid MAC address.
     * @param macAddress
     * @return True if macAddress is a valid MAC, False otherwise
     */
    public static boolean isMACAddress(String macAddress) {
        String[] macBytes = macAddress.split(":");
        if (macBytes.length != 6)
            return false;
        for (int i = 0; i < 6; ++i) {
            if (HEXES.indexOf(macBytes[i].toUpperCase().charAt(0)) == -1 ||
                HEXES.indexOf(macBytes[i].toUpperCase().charAt(1)) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Accepts a MAC address of the form 00:aa:11:bb:22:cc, case does not
     * matter, and returns a corresponding byte[].
     * @param macAddress The MAC address to convert into a bye array
     * @return The macAddress as a byte array
     */
    public static byte[] toMACAddress(String macAddress) {
        return MacAddress.of(macAddress).getBytes();
    }


    /**
     * Accepts a MAC address and returns the corresponding long, where the
     * MAC bytes are set on the lower order bytes of the long.
     * @param macAddress
     * @return a long containing the mac address bytes
     */
    public static long toLong(byte[] macAddress) {
        return MacAddress.of(macAddress).getLong();
    }

    /**
     * Convert a long MAC address to a byte array
     * @param macAddress
     * @return the bytes of the mac address
     */
    public static byte[] toByteArray(long macAddress) {
        return MacAddress.of(macAddress).getBytes();
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime
				* result
				+ ((destinationMACAddress == null) ? 0 : destinationMACAddress
						.hashCode());
		result = prime * result
				+ ((etherType == null) ? 0 : etherType.hashCode());
		result = prime * result + (pad ? 1231 : 1237);
		result = prime * result + priorityCode;
		result = prime
				* result
				+ ((sourceMACAddress == null) ? 0 : sourceMACAddress.hashCode());
		result = prime * result + vlanID;
		return result;
	}

    @Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Ethernet other = (Ethernet) obj;
		if (destinationMACAddress == null) {
			if (other.destinationMACAddress != null)
				return false;
		} else if (!destinationMACAddress.equals(other.destinationMACAddress))
			return false;
		if (etherType == null) {
			if (other.etherType != null)
				return false;
		} else if (!etherType.equals(other.etherType))
			return false;
		if (pad != other.pad)
			return false;
		if (priorityCode != other.priorityCode)
			return false;
		if (sourceMACAddress == null) {
			if (other.sourceMACAddress != null)
				return false;
		} else if (!sourceMACAddress.equals(other.sourceMACAddress))
			return false;
		if (vlanID != other.vlanID)
			return false;
		return true;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#toString(java.lang.Object)
     */
    @Override
    public String toString() {

        StringBuffer sb = new StringBuffer("\n");

        IPacket pkt = this.getPayload();

        if (pkt instanceof ARP)
            sb.append("arp");
        else if (pkt instanceof LLDP)
            sb.append("lldp");
        else if (pkt instanceof ICMP)
            sb.append("icmp");
        else if (pkt instanceof IPv4)
            sb.append("ip");
        else if (pkt instanceof DHCP)
            sb.append("dhcp");
        else  sb.append(this.getEtherType().toString());

        sb.append("\ndl_vlan: ");
        if (this.getVlanID() == Ethernet.VLAN_UNTAGGED)
            sb.append("untagged");
        else
            sb.append(this.getVlanID());
        sb.append("\ndl_vlan_pcp: ");
        sb.append(this.getPriorityCode());
        sb.append("\ndl_src: ");
        sb.append(this.getSourceMACAddress().toString());
        sb.append("\ndl_dst: ");
        sb.append(this.getDestinationMACAddress().toString());


        if (pkt instanceof ARP) {
            ARP p = (ARP) pkt;
            sb.append("\nnw_src: ");
            sb.append(p.getSenderProtocolAddress().toString());
            sb.append("\nnw_dst: ");
            sb.append(p.getTargetProtocolAddress().toString());
        }
        else if (pkt instanceof LLDP) {
            sb.append("lldp packet");
        }
        else if (pkt instanceof ICMP) {
            ICMP icmp = (ICMP) pkt;
            sb.append("\nicmp_type: ");
            sb.append(icmp.getIcmpType());
            sb.append("\nicmp_code: ");
            sb.append(icmp.getIcmpCode());
        }
        else if (pkt instanceof IPv4) {
            IPv4 p = (IPv4) pkt;
            sb.append("\nnw_src: ");
            sb.append(p.getSourceAddress().toString());
            sb.append("\nnw_dst: ");
            sb.append(p.getDestinationAddress().toString());
            sb.append("\nnw_tos: ");
            sb.append(p.getDiffServ());
            sb.append("\nnw_proto: ");
            sb.append(p.getProtocol());
        }
        else if (pkt instanceof IPv6) {
        	IPv6 p = (IPv6) pkt;
        	sb.append("\nnw_src: ");
            sb.append(p.getSourceAddress().toString());
            sb.append("\nnw_dst: ");
            sb.append(p.getDestinationAddress().toString());
            sb.append("\nnw_tclass: ");
            sb.append(p.getTrafficClass());
            sb.append("\nnw_proto: ");
            sb.append(p.getNextHeader().toString());
        }
        else if (pkt instanceof DHCP) {
            sb.append("\ndhcp packet");
        }
        else if (pkt instanceof Data) {
            sb.append("\ndata packet");
        }
        else if (pkt instanceof LLC) {
            sb.append("\nllc packet");
        }
        else if (pkt instanceof BPDU) {
            sb.append("\nbpdu packet");
        }
        else sb.append("\nunknown packet");

        return sb.toString();
    }
}