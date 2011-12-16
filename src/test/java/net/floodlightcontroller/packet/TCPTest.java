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
public class TCPTest {
    private byte[] pktSerialized = new byte[] { 0x45, 0x20,
            0x00, 0x34, 0x1d, (byte) 0x85, 0x00, 0x00, 0x32, 0x06,
            0x31, 0x1e, 0x4a, 0x7d, 0x2d, 0x6d, (byte) 0xc0, (byte) 0xa8,
            0x01, 0x6f, 0x03, (byte) 0xe1, (byte) 0xc0, 0x32, (byte) 0xe3, (byte) 0xad,
            (byte) 0xee, (byte) 0x88, (byte) 0xb7, (byte) 0xda, (byte) 0xd8, 0x24, (byte) 0x80, 0x10,
            0x01, 0x0b, 0x59, 0x33, 0x00, 0x00, 0x01, 0x01,
            0x08, 0x0a, 0x20, (byte) 0x9a, 0x41, 0x04, 0x07, 0x76,
            0x53, 0x1f};
    
    @Test
    public void testSerialize() {
        IPacket packet = new IPv4()
        .setDiffServ((byte) 0x20)
        .setIdentification((short) 0x1d85)
        .setFlags((byte) 0x00)
        .setTtl((byte) 50)
        .setSourceAddress("74.125.45.109")
        .setDestinationAddress("192.168.1.111")
        .setPayload(new TCP()
                        .setSourcePort((short) 993)
                        .setDestinationPort((short) 49202)
                        .setSequence(0xe3adee88)
                        .setAcknowledge(0xb7dad824)
                        .setDataOffset((byte) 8)
                        .setFlags((short) 0x10)
                        .setWindowSize((short) 267)
                        .setOptions(new byte[] {0x01, 0x01, 0x08, 0x0a, 0x20, (byte) 0x9a,
                                                0x41, 0x04, 0x07, 0x76, 0x53, 0x1f})
                        .setPayload(null)
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
