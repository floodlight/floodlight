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

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class Ethernet extends BasePacket {
    private static String HEXES = "0123456789ABCDEF";
    public static final short TYPE_ARP = 0x0806;
    public static final short TYPE_IPv4 = 0x0800;
    public static final short TYPE_LLDP = (short) 0x88cc;
    public static final short VLAN_UNTAGGED = (short)0xffff;
    public static Map<Short, Class<? extends IPacket>> etherTypeClassMap;

    static {
        etherTypeClassMap = new HashMap<Short, Class<? extends IPacket>>();
        etherTypeClassMap.put(TYPE_ARP, ARP.class);
        etherTypeClassMap.put(TYPE_IPv4, IPv4.class);
        etherTypeClassMap.put(TYPE_LLDP, LLDP.class);
    }

    protected byte[] destinationMACAddress;
    protected byte[] sourceMACAddress;
    protected byte priorityCode;
    protected short vlanID;
    protected short etherType;
    protected boolean pad = false;

    /**
     * By default, set Ethernet to untagged
     */
    public Ethernet() {
        super();
        this.vlanID = VLAN_UNTAGGED;
    }
    
    /**
     * @return the destinationMACAddress
     */
    public byte[] getDestinationMACAddress() {
        return destinationMACAddress;
    }

    /**
     * @param destinationMACAddress the destinationMACAddress to set
     */
    public Ethernet setDestinationMACAddress(byte[] destinationMACAddress) {
        this.destinationMACAddress = destinationMACAddress;
        return this;
    }

    /**
     * @param destinationMACAddress the destinationMACAddress to set
     */
    public Ethernet setDestinationMACAddress(String destinationMACAddress) {
        this.destinationMACAddress = Ethernet
                .toMACAddress(destinationMACAddress);
        return this;
    }

    /**
     * @return the sourceMACAddress
     */
    public byte[] getSourceMACAddress() {
        return sourceMACAddress;
    }

    /**
     * @param sourceMACAddress the sourceMACAddress to set
     */
    public Ethernet setSourceMACAddress(byte[] sourceMACAddress) {
        this.sourceMACAddress = sourceMACAddress;
        return this;
    }

    /**
     * @param sourceMACAddress the sourceMACAddress to set
     */
    public Ethernet setSourceMACAddress(String sourceMACAddress) {
        this.sourceMACAddress = Ethernet.toMACAddress(sourceMACAddress);
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
    public short getEtherType() {
        return etherType;
    }

    /**
     * @param etherType the etherType to set
     */
    public Ethernet setEtherType(short etherType) {
        this.etherType = etherType;
        return this;
    }
    
    /**
     * @return True if the Ethernet frame is broadcast, false otherwise
     */
    public boolean isBroadcast() {
        assert(destinationMACAddress.length == 6);
        for (byte b : destinationMACAddress) {
            if (b != -1) // checks if equal to 0xff
                return false;
        }
        return true;
    }
    
    /**
     * @return True is the Ethernet frame is multicast, False otherwise
     */
    public boolean isMulticast() {
        if (this.isBroadcast()) {
            return false;
        }
        return (destinationMACAddress[0] & 0x01) != 0;
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
        bb.put(destinationMACAddress);
        bb.put(sourceMACAddress);
        if (vlanID != VLAN_UNTAGGED) {
            bb.putShort((short) 0x8100);
            bb.putShort((short) ((priorityCode << 13) | (vlanID & 0x0fff)));
        }
        bb.putShort(etherType);
        if (payloadData != null)
            bb.put(payloadData);
        if (pad) {
            Arrays.fill(data, bb.position(), data.length, (byte)0x0);
        }
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        if (this.destinationMACAddress == null)
            this.destinationMACAddress = new byte[6];
        bb.get(this.destinationMACAddress);

        if (this.sourceMACAddress == null)
            this.sourceMACAddress = new byte[6];
        bb.get(this.sourceMACAddress);

        short etherType = bb.getShort();
        if (etherType == (short) 0x8100) {
            short tci = bb.getShort();
            this.priorityCode = (byte) ((tci >> 13) & 0x07);
            this.vlanID = (short) (tci & 0x0fff);
            etherType = bb.getShort();
        } else {
            this.vlanID = VLAN_UNTAGGED;
        }
        this.etherType = etherType;

        IPacket payload;
        if (Ethernet.etherTypeClassMap.containsKey(this.etherType)) {
            Class<? extends IPacket> clazz = Ethernet.etherTypeClassMap.get(this.etherType);
            try {
                payload = clazz.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Error parsing payload for Ethernet packet", e);
            }
        } else {
            payload = new Data();
        }
        this.payload = payload.deserialize(data, bb.position(), bb.limit()-bb.position());
        this.payload.setParent(this);
        return this;
    }

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
     * @param macAddress
     * @return
     */
    public static byte[] toMACAddress(String macAddress) {
        byte[] address = new byte[6];
        
        if (!Ethernet.isMACAddress(macAddress)) {
            throw new IllegalArgumentException(
                    "Specified MAC Address must contain 12 hex digits" +
                    " separated pairwise by :'s.");
        }
        String[] macBytes = macAddress.split(":");
        for (int i = 0; i < 6; ++i) {
            address[i] = (byte) ((HEXES.indexOf(macBytes[i].toUpperCase()
                    .charAt(0)) << 4) | HEXES.indexOf(macBytes[i].toUpperCase()
                    .charAt(1)));
        }

        return address;
    }

    
    /**
     * Accepts a MAC address and returns the corresponding long, where the
     * MAC bytes are set on the lower order bytes of the long.
     * @param macAddress
     * @return a long containing the mac address bytes
     */
    public static long toLong(byte[] macAddress) {
        long mac = 0;
        for (int i = 0; i < 6; i++) {
          long t = (macAddress[i] & 0xffL) << ((5-i)*8);
          mac |= t;
        }
        return mac;
    }
    
    /**
     * Convert a long MAC address to a byte array
     * @param macAddress
     * @return the bytes of the mac address
     */
    public static byte[] toByteArray(long macAddress) {
        return new byte[] {
                (byte)((macAddress >> 40) & 0xff),
                (byte)((macAddress >> 32) & 0xff),
                (byte)((macAddress >> 24) & 0xff),
                (byte)((macAddress >> 16) & 0xff),
                (byte)((macAddress >> 8 ) & 0xff),
                (byte)((macAddress >> 0) & 0xff)
        };
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 7867;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(destinationMACAddress);
        result = prime * result + etherType;
        result = prime * result + (pad ? 1231 : 1237);
        result = prime * result + Arrays.hashCode(sourceMACAddress);
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
        if (!(obj instanceof Ethernet))
            return false;
        Ethernet other = (Ethernet) obj;
        if (!Arrays.equals(destinationMACAddress, other.destinationMACAddress))
            return false;
        if (priorityCode != other.priorityCode)
            return false;
        if (vlanID != other.vlanID)
            return false;
        if (etherType != other.etherType)
            return false;
        if (pad != other.pad)
            return false;
        if (!Arrays.equals(sourceMACAddress, other.sourceMACAddress))
            return false;
        return true;
    }
}
