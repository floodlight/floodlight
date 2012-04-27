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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.openflow.protocol.OFType;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.test.FloodlightTestCase;

public class MessageDispatcherTest extends FloodlightTestCase {

    IOFMessageListener createLMock(String name) {
        IOFMessageListener mock = createNiceMock(IOFMessageListener.class);
        expect(mock.getName()).andReturn(name).anyTimes();
        return mock;
    }
    
    void addPrereqs(IOFMessageListener mock, String... deps) {
        for (String dep : deps) {
            expect(mock.isCallbackOrderingPrereq(OFType.PACKET_IN, dep)).andReturn(true).anyTimes();
        }
    }
    
    void testOrdering(ArrayList<IOFMessageListener> inputListeners) {
        ListenerDispatcher<OFType, IOFMessageListener> ld = 
                new ListenerDispatcher<OFType, IOFMessageListener>();
        
        for (IOFMessageListener l : inputListeners) {
            ld.addListener(OFType.PACKET_IN, l);
        }
        for (IOFMessageListener l : inputListeners) {
            verify(l);
        }
        
        List<IOFMessageListener> result = ld.getOrderedListeners();
        System.out.print("Ordering: ");
        for (IOFMessageListener l : result) {
            System.out.print(l.getName());
            System.out.print(",");
        }
        System.out.print("\n");

        for (int ind_i = 0; ind_i < result.size(); ind_i++) {
            IOFMessageListener i = result.get(ind_i);
            for (int ind_j = ind_i+1; ind_j < result.size(); ind_j++) {
                IOFMessageListener j = result.get(ind_j);
                
                boolean orderwrong = 
                        (i.isCallbackOrderingPrereq(OFType.PACKET_IN, j.getName()) ||
                         j.isCallbackOrderingPostreq(OFType.PACKET_IN, i.getName()));
                assertFalse("Invalid order: " + 
                            ind_i + " (" + i.getName() + ") " + 
                            ind_j + " (" + j.getName() + ") ", orderwrong);
            }
        }
    }
    
    void randomTestOrdering(ArrayList<IOFMessageListener> mocks) {
        Random rand = new Random(0);
        ArrayList<IOFMessageListener> random = 
                new ArrayList<IOFMessageListener>();
        random.addAll(mocks);
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < random.size(); j++) {
                int ind = rand.nextInt(mocks.size()-1);
                IOFMessageListener tmp = random.get(j);
                random.set(j, random.get(ind));
                random.set(ind, tmp);
            }
            testOrdering(random);
        }
    }
    
    @Test
    public void testCallbackOrderingSimple() throws Exception {
        ArrayList<IOFMessageListener> mocks = 
                new ArrayList<IOFMessageListener>();
        for (int i = 0; i < 10; i++) {
            mocks.add(createLMock(""+i));
        }
        for (int i = 1; i < 10; i++) {
            addPrereqs(mocks.get(i), ""+(i-1));
        }
        for (IOFMessageListener l : mocks) {
            replay(l);
        }
        randomTestOrdering(mocks);
    }

    @Test
    public void testCallbackOrderingPartial() throws Exception {
        ArrayList<IOFMessageListener> mocks = 
                new ArrayList<IOFMessageListener>();
        for (int i = 0; i < 10; i++) {
            mocks.add(createLMock(""+i));
        }
        for (int i = 1; i < 5; i++) {
            addPrereqs(mocks.get(i), ""+(i-1));
        }
        for (int i = 6; i < 10; i++) {
            addPrereqs(mocks.get(i), ""+(i-1));
        }
        for (IOFMessageListener l : mocks) {
            replay(l);
        }
        randomTestOrdering(mocks);
    }
    

    @Test
    public void testCallbackOrderingPartial2() throws Exception {
        ArrayList<IOFMessageListener> mocks = 
                new ArrayList<IOFMessageListener>();
        for (int i = 0; i < 10; i++) {
            mocks.add(createLMock(""+i));
        }
        for (int i = 2; i < 5; i++) {
            addPrereqs(mocks.get(i), ""+(i-1));
        }
        for (int i = 6; i < 9; i++) {
            addPrereqs(mocks.get(i), ""+(i-1));
        }
        for (IOFMessageListener l : mocks) {
            replay(l);
        }
        randomTestOrdering(mocks);
    }
}
