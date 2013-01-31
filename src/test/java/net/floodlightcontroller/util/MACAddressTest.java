/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Sho Shimizu (sho.shimizu@gmail.com)
 */
public class MACAddressTest {
    @Test
    public void testValueOf() {
        MACAddress address = MACAddress.valueOf("00:01:02:03:04:05");
        assertEquals(address,
                MACAddress.valueOf(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05}));
        assertEquals("00:01:02:03:04:05", address.toString());

        address = MACAddress.valueOf("FF:FE:FD:10:20:30");
        assertEquals(address,
                MACAddress.valueOf(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD, 0x10, 0x20, 0x30}));
        assertEquals("FF:FE:FD:10:20:30", address.toString());
        
        address = MACAddress.valueOf("00:11:22:aa:bb:cc");
        assertEquals(address,
                MACAddress.valueOf(new byte[]{0x00, 0x11, 0x22, (byte)0xaa, (byte)0xbb, (byte)0xcc}));
    }

    @Test(expected=NumberFormatException.class)
    public void testIllegalFormat() {
        MACAddress.valueOf("0T:00:01:02:03:04");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testLongStringFields() {
        MACAddress.valueOf("00:01:02:03:04:05:06");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testShortStringFields() {
        MACAddress.valueOf("00:01:02:03:04");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testLongByteFields() {
        MACAddress.valueOf(new byte[]{0x01, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testShortByteField() {
        MACAddress.valueOf(new byte[]{0x01, 0x01, 0x02, 0x03, 0x04});
    }

    //  Test data is imported from net.floodlightcontroller.packet.EthernetTest
    @Test
    public void testToLong() {
        assertEquals(
                281474976710655L,
                MACAddress.valueOf(new byte[]{(byte) 0xff, (byte) 0xff,
                        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}).toLong());

        assertEquals(
                1103823438081L,
                MACAddress.valueOf(new byte[] { (byte) 0x01, (byte) 0x01,
                        (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01 }).toLong());

        assertEquals(
                141289400074368L,
                MACAddress.valueOf(new byte[] { (byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80 }).toLong());

    }
    
    @Test
    public void testIsBroadcast() {
        assertTrue(MACAddress.valueOf("FF:FF:FF:FF:FF:FF").isBroadcast());
        assertFalse(MACAddress.valueOf("11:22:33:44:55:66").isBroadcast());
    }

    @Test
    public void testIsMulticast() {
        assertTrue(MACAddress.valueOf("01:80:C2:00:00:00").isMulticast());
        assertFalse(MACAddress.valueOf("FF:FF:FF:FF:FF:FF").isMulticast());
        assertFalse(MACAddress.valueOf("11:22:33:44:55:66").isBroadcast());
    }
}
