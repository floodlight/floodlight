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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFEchoRequest;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFHello;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;

import java.io.IOException;
import java.util.EnumSet;

public class OFMessageDamperTest {
    OFFactory factory;
    OFMessageDamper damper;
    
    OFMessageDamperMockSwitch sw1;
    OFMessageDamperMockSwitch sw2;
    
    OFEchoRequest echoRequst1;
    OFEchoRequest echoRequst1Clone;
    OFEchoRequest echoRequst2;
    OFHello hello1;
    OFHello hello2;
    
    
    
    @Before
    public void setUp() throws IOException {
        factory = OFFactories.getFactory(OFVersion.OF_13);
        
        sw1 = new OFMessageDamperMockSwitch();
        sw2 = new OFMessageDamperMockSwitch();
        
        echoRequst1 = factory.buildEchoRequest().setData(new byte[] { 1 }).build();
        echoRequst1Clone = echoRequst1.createBuilder().build();
        echoRequst2 = factory.buildEchoRequest().setData(new byte[] { 2 }).build();
        
        hello1 = factory.buildHello().setXid(1L).build();
        hello2 = factory.buildHello().setXid(2L).build();
    }
    
    protected void doWrite(boolean expectWrite, 
                           OFMessageDamperMockSwitch sw, 
                           OFMessage msg) throws IOException {
        
        boolean result;
        sw.reset();
        result = damper.write(sw, msg);
        
        if (expectWrite) {
            assertEquals(true, result);
            sw.assertMessageWasWritten(msg);
        } else {
            assertEquals(false, result);
            sw.assertNoMessageWritten();
        }
    }
    
    
    @Test
    public void testOneMessageType() throws IOException, InterruptedException {
        int timeout = 50;
        int sleepTime = 60; 
        damper = new OFMessageDamper(100, 
                                     EnumSet.of(OFType.ECHO_REQUEST),
                                     timeout);
        
        
        
        // echo requests should be dampened 
        doWrite(true, sw1, echoRequst1);
        doWrite(false, sw1, echoRequst1);
        doWrite(false, sw1, echoRequst1Clone);
        doWrite(true, sw1, echoRequst2);
        doWrite(false, sw1, echoRequst2);
        
        // we don't dampen hellos. All should succeed 
        doWrite(true, sw1, hello1);
        doWrite(true, sw1, hello1);
        doWrite(true, sw1, hello1);
        
        // echo request should also be dampened on sw2
        doWrite(true, sw2, echoRequst1);
        doWrite(false, sw2, echoRequst1);
        doWrite(true, sw2, echoRequst2);
        
        
        Thread.sleep(sleepTime);
        doWrite(true, sw1, echoRequst1);
        doWrite(true, sw2, echoRequst1);
        
    }
    
    @Test
    public void testTwoMessageTypes() throws IOException, InterruptedException {
        int timeout = 50;
        int sleepTime = 60; 
        damper = new OFMessageDamper(100, 
                                     EnumSet.of(OFType.ECHO_REQUEST, 
                                                OFType.HELLO),
                                     timeout);
        
        
        
        // echo requests should be dampened 
        doWrite(true, sw1, echoRequst1);
        doWrite(false, sw1, echoRequst1);
        doWrite(false, sw1, echoRequst1Clone);
        doWrite(true, sw1, echoRequst2);
        doWrite(false, sw1, echoRequst2);
        
        // hello should be dampened as well
        doWrite(true, sw1, hello1);
        doWrite(false, sw1, hello1);
        doWrite(false, sw1, hello1);
        
        doWrite(true, sw1, hello2);
        doWrite(false, sw1, hello2);
        doWrite(false, sw1, hello2);
        
        // echo request should also be dampened on sw2
        doWrite(true, sw2, echoRequst1);
        doWrite(false, sw2, echoRequst1);
        doWrite(true, sw2, echoRequst2);
        
        Thread.sleep(sleepTime);
        doWrite(true, sw1, echoRequst1);
        doWrite(true, sw2, echoRequst1);
        doWrite(true, sw1, hello1);
        doWrite(true, sw1, hello2);
    }
    
}
