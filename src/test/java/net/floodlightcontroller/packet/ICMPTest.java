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

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/**
 * @author shudong.zhou@bigswitch.com
 */
public class ICMPTest {
    private byte[] pktSerialized = new byte[] {
            // (byte) 0xc8, 0x2a, 0x14, 0x2d, 0x35, (byte) 0xf1,
            // 0x00, 0x0c, 0x29, 0x3b, (byte) 0x95, (byte) 0xf2, 0x08, 0x0,
            0x45, 0x00, 0x00, 0x1f, 0x00, 0x00, 0x40, 0x00, 0x40, 0x01,
            (byte) 0xa3, (byte) 0xcb,
            (byte) 0xc0, (byte) 0xa8, (byte) 0x0a, (byte) 0xe7,
            (byte) 0xc0, (byte) 0xa8, (byte) 0x0a, (byte) 0xdb,
            0x08, 0x00, 0x7f, 0x0a, 0x76, (byte) 0xf2, 0x00, 0x02,
            0x01, 0x01, 0x01 };
    @Test
    public void testSerialize() {
        IPacket packet = new IPv4()
            .setIdentification((short) 0)
            .setFlags((byte) 0x02)
            .setTtl((byte) 64)
            .setSourceAddress("192.168.10.231")
            .setDestinationAddress("192.168.10.219")
            .setPayload(new ICMP()
                            .setIcmpType((byte) 8)
                            .setIcmpCode((byte) 0)
                            .setPayload(new Data(new byte[]
                                        {0x76, (byte) 0xf2, 0x0, 0x2, 0x1, 0x1, 0x1}))
                       );
        byte[] actual = packet.serialize();
        assertTrue(Arrays.equals(pktSerialized, actual));
    }
    
    @Test
    public void testDeserialize() {
        IPacket packet = new IPv4();
        packet.deserialize(pktSerialized, 0, pktSerialized.length);
        byte[] pktSerialized1 = packet.serialize();
        assertTrue(Arrays.equals(pktSerialized, pktSerialized1));
    }
}
