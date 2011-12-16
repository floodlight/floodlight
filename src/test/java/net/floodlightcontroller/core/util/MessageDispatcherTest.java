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

package net.floodlightcontroller.core.util;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.junit.Test;
import org.openflow.protocol.OFType;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.test.FloodlightTestCase;

public class MessageDispatcherTest extends FloodlightTestCase {

    /**
     * Verify that our callbacks are ordered with respect to the order specified
     * @throws Exception
     */
    @Test
    public void testCallbackOrderingBase() throws Exception {
        testCallbackOrdering(new String[] {"topology"}, new String[] {"topology"});
        testCallbackOrdering(new String[] {"learningswitch","topology"}, new String[] {"learningswitch","topology"});
        testCallbackOrdering(new String[] {"topology","devicemanager","learningswitch"}, new String[] {"topology","devicemanager","learningswitch"});
        testCallbackOrdering(new String[] {"topology","devicemanager","routing","learningswitch"}, new String[] {"topology","devicemanager","routing","learningswitch"});
        testCallbackOrdering(new String[] {"devicemanager","topology","learningswitch","routing"}, new String[] {"topology","devicemanager","routing","learningswitch"});
    }

    protected void testCallbackOrdering(String[] addOrder, String[] verifyOrder) throws Exception {
        ListenerDispatcher<OFType, IOFMessageListener> ld = 
            new ListenerDispatcher<OFType, IOFMessageListener>();

        IOFMessageListener topology = createNiceMock(IOFMessageListener.class);
        IOFMessageListener devicemanager = createNiceMock(IOFMessageListener.class);
        IOFMessageListener routing = createNiceMock(IOFMessageListener.class);
        IOFMessageListener learningswitch = createNiceMock(IOFMessageListener.class);

        expect(topology.getName()).andReturn("topology").anyTimes();
        expect(devicemanager.getName()).andReturn("devicemanager").anyTimes();
        expect(routing.getName()).andReturn("routing").anyTimes();
        expect(learningswitch.getName()).andReturn("learningswitch").anyTimes();

        expect(devicemanager.isCallbackOrderingPrereq(OFType.PACKET_IN, "topology")).andReturn(true).anyTimes();
        expect(routing.isCallbackOrderingPrereq(OFType.PACKET_IN, "topology")).andReturn(true).anyTimes();
        expect(routing.isCallbackOrderingPrereq(OFType.PACKET_IN, "devicemanager")).andReturn(true).anyTimes();
        expect(learningswitch.isCallbackOrderingPrereq(OFType.PACKET_IN, "routing")).andReturn(true).anyTimes();
        expect(learningswitch.isCallbackOrderingPrereq(OFType.PACKET_IN, "devicemanager")).andReturn(true).anyTimes();

        replay(topology, devicemanager, routing, learningswitch);
        for (String o : addOrder) {
            if ("topology".equals(o)) {
                ld.addListener(OFType.PACKET_IN, topology);
            } else if ("devicemanager".equals(o)) {
                ld.addListener(OFType.PACKET_IN, devicemanager);
            } else if ("routing".equals(o)) {
                ld.addListener(OFType.PACKET_IN, routing);
            } else {
                ld.addListener(OFType.PACKET_IN, learningswitch);
            }
        }

        verify(topology, devicemanager, routing, learningswitch);
        for (int i = 0; i < verifyOrder.length; ++i) {
            String o = verifyOrder[i];
            assertEquals(o, ld.getOrderedListeners().get(i).getName());
        }
    }

}
