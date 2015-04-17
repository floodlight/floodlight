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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.projectfloodlight.openflow.types.EthType;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class LLDPTest {
    protected byte[] pkt = {0x01,0x23,0x20,0x00,0x00,0x01,0x00,0x12,(byte) 0xe2,0x78,0x67,0x78,(byte) 0x88,(byte) 0xcc,0x02,0x07,
            0x04,0x00,0x12,(byte) 0xe2,0x78,0x67,0x64,0x04,0x03,0x02,0x00,0x06,0x06,0x02,0x00,0x78,
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};

    protected IPacket getPacket() {
        return new Ethernet()
        .setPad(true)
        .setDestinationMACAddress("01:23:20:00:00:01")
        .setSourceMACAddress("00:12:e2:78:67:78")
        .setEtherType(EthType.LLDP)
        .setPayload(
                new LLDP()
                .setChassisId(new LLDPTLV().setType((byte) 1).setLength((short) 7).setValue(new byte[] {0x04, 0x00, 0x12, (byte) 0xe2, 0x78, 0x67, 0x64}))
                .setPortId(new LLDPTLV().setType((byte) 2).setLength((short) 3).setValue(new byte[] {0x02, 0x00, 0x06}))
                .setTtl(new LLDPTLV().setType((byte) 3).setLength((short) 2).setValue(new byte[] {0x00, 0x78}))
            
        );
    }

    @Test
    public void testSerialize() throws Exception {
        IPacket ethernet = getPacket();
        assertTrue(Arrays.equals(pkt, ethernet.serialize()));
    }

    @Test
    public void testDeserialize() throws Exception {
        Ethernet ethernet = (Ethernet) new Ethernet().deserialize(pkt, 0, pkt.length);
        ethernet.setPad(true);
        assertTrue(Arrays.equals(pkt, ethernet.serialize()));

        IPacket expected = getPacket();
        assertEquals(expected, ethernet);
    }
}
