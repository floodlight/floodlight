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

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class BSNTest {       
    protected byte[] probePkt = {
		0x00, 0x00, 0x00, 0x00, 0x00, 0x01, // src mac
		0x00, 0x00, 0x00, 0x00, 0x00, 0x04, // dst mac
		(byte) 0x89, 0x42, // BSN type
        0x20, 0x00, 0x06, 0x04, 0x00, 0x01, 0x00, 0x00, // BSN header
		0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // controller id
		0x00, 0x00, 0x00, 0x03, // sequence id
		0x00, 0x00, 0x00, 0x00, 0x00, 0x01, // src mac
		0x00, 0x00, 0x00, 0x00, 0x00, 0x04, // dst mac
		0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06, // switch dpid
		0x00, 0x00, 0x00, 0x01 // port number
    };
    
    protected Ethernet getProbePacket() {
        return (Ethernet) new Ethernet()
            .setSourceMACAddress("00:00:00:00:00:04")
            .setDestinationMACAddress("00:00:00:00:00:01")
            .setEtherType(Ethernet.TYPE_BSN)
            .setPayload(new BSN(BSN.BSN_TYPE_PROBE)
	            .setPayload(new BSNPROBE()
		            .setSequenceId(3)
		            .setSrcMac(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x01})
		            .setDstMac(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x04})
		            .setSrcSwDpid(0x06)
		            .setSrcPortNo(0x01)
		            )
	            );
    }

    @Test
    public void testSerialize() throws Exception {
        Ethernet pkt = getProbePacket();
        byte[] serialized = pkt.serialize();
        assertTrue(Arrays.equals(probePkt, serialized));
    }

    @Test
    public void testDeserialize() throws Exception {
        Ethernet pkt = (Ethernet) new Ethernet().deserialize(probePkt, 0, probePkt.length);
        assertTrue(Arrays.equals(probePkt, pkt.serialize()));

        Ethernet expected = getProbePacket();
        assertEquals(expected, pkt);
    }
}
