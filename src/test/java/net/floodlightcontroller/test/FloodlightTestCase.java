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

package net.floodlightcontroller.test;

import junit.framework.TestCase;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;

/**
 * This class gets a handle on the application context which is used to
 * retrieve Spring beans from during tests
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class FloodlightTestCase extends TestCase {


    protected MockFloodlightProvider mockFloodlightProvider;

    public MockFloodlightProvider getMockFloodlightProvider() {
        return mockFloodlightProvider;
    }

    public void setMockFloodlightProvider(MockFloodlightProvider mockFloodlightProvider) {
        this.mockFloodlightProvider = mockFloodlightProvider;
    }

    public FloodlightContext parseAndAnnotate(OFMessage m) {
        FloodlightContext bc = new FloodlightContext();
        return parseAndAnnotate(bc, m);
    }

    public FloodlightContext parseAndAnnotate(FloodlightContext bc, OFMessage m) {
        if (OFType.PACKET_IN.equals(m.getType())) {
            OFPacketIn pi = (OFPacketIn)m;
            Ethernet eth = new Ethernet();
            eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
            IFloodlightProvider.bcStore.put(bc, 
                    IFloodlightProvider.CONTEXT_PI_PAYLOAD, 
                    eth);
        }
        return bc;
    }
    
    @Override
    public void setUp() {
        mockFloodlightProvider = new MockFloodlightProvider();
    }
 
    /**
     * @return the applicationContext
     */

    public void testSanity() {
        assertTrue(true);
    }
}
