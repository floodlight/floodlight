package net.floodlightcontroller.util;

import java.util.Arrays;

/**
 * The class representing MAC address.
 *
 * @author Sho Shimizu (sho.shimizu@gmail.com)
 */
public class MACAddress {
    public static final int MAC_ADDRESS_LENGTH = 6;
    private byte[] address = new byte[MAC_ADDRESS_LENGTH];

    public MACAddress(byte[] address) {
        this.address = Arrays.copyOf(address, MAC_ADDRESS_LENGTH);
    }

    /**
     * Returns a MAC address instance representing the value of the specified {@code String}.
     * @param address the String representation of the MAC Address to be parsed.
     * @return a MAC Address instance representing the value of the specified {@code String}.
     * @throws IllegalArgumentException if the string cannot be parsed as a MAC address.
     */
    public static MACAddress valueOf(String address) {
        String[] elements = address.split(":");
        if (elements.length != MAC_ADDRESS_LENGTH) {
            throw new IllegalArgumentException(
                    "Specified MAC Address must contain 12 hex digits" +
                    " separated pairwise by :'s.");
        }

        byte[] addressInBytes = new byte[MAC_ADDRESS_LENGTH];
        for (int i = 0; i < MAC_ADDRESS_LENGTH; i++) {
            String element = elements[i];
            addressInBytes[i] = (byte)Integer.parseInt(element, 16);
        }

        return new MACAddress(addressInBytes);
    }

    /**
     * Returns a MAC address instance representing the specified {@code byte} array.
     * @param address the byte array to be parsed.
     * @return a MAC address instance representing the specified {@code byte} array.
     * @throws IllegalArgumentException if the byte array cannot be parsed as a MAC address.
     */
    public static MACAddress valueOf(byte[] address) {
        if (address.length != MAC_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("the length is not " + MAC_ADDRESS_LENGTH);
        }

        return new MACAddress(address);
    }

    /**
     * Returns a MAC address instance representing the specified {@code long} value.
     * The lower 48 bits of the long value are used to parse as a MAC address.
     * @param address the long value to be parsed. The lower 48 bits are used for a MAC address.
     * @return a MAC address instance representing the specified {@code long} value.
     * @throws IllegalArgumentException if the long value cannot be parsed as a MAC address.
     */
    public static MACAddress valueOf(long address) {
        byte[] addressInBytes = new byte[] {
                (byte)((address >> 40) & 0xff),
                (byte)((address >> 32) & 0xff),
                (byte)((address >> 24) & 0xff),
                (byte)((address >> 16) & 0xff),
                (byte)((address >> 8 ) & 0xff),
                (byte)((address >> 0) & 0xff)
        };

        return new MACAddress(addressInBytes);
    }

    /**
     * Returns the length of the {@code MACAddress}.
     * @return the length of the {@code MACAddress}.
     */
    public int length() {
        return address.length;
    }

    /**
     * Returns the value of the {@code MACAddress} as a {@code byte} array.
     * @return the numeric value represented by this object after conversion to type {@code byte} array.
     */
    public byte[] toBytes() {
        return Arrays.copyOf(address, address.length);
    }

    /**
     * Returns the value of the {@code MACAddress} as a {@code long}.
     * @return the numeric value represented by this object after conversion to type {@code long}.
     */
    public long toLong() {
        long mac = 0;
        for (int i = 0; i < 6; i++) {
            long t = (address[i] & 0xffL) << ((5 - i) * 8);
            mac |= t;
        }
        return mac;
    }

    /**
     * Returns {@code true} if the MAC address is the broadcast address.
     * @return {@code true} if the MAC address is the broadcast address.
     */
    public boolean isBroadcast() {
        for (byte b : address) {
            if (b != -1) // checks if equal to 0xff
                return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if the MAC address is the multicast address.
     * @return {@code true} if the MAC address is the multicast address.
     */
    public boolean isMulticast() {
        if (isBroadcast()) {
            return false;
        }
        return (address[0] & 0x01) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof MACAddress)) {
            return false;
        }

        MACAddress other = (MACAddress)o;
        return Arrays.equals(this.address, other.address);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.address);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (byte b: address) {
            if (builder.length() > 0) {
                builder.append(":");
            }
            builder.append(String.format("%02X", b & 0xFF));
        }
        return builder.toString();
    }
}
