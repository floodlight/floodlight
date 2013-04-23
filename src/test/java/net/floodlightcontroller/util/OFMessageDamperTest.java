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

import net.floodlightcontroller.core.FloodlightContext;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFEchoRequest;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.OFMessageFactory;

import java.io.IOException;
import java.util.EnumSet;

public class OFMessageDamperTest {
    OFMessageFactory factory;
    OFMessageDamper damper;
    FloodlightContext cntx;
    
    OFMessageDamperMockSwitch sw1;
    OFMessageDamperMockSwitch sw2;
    
    OFEchoRequest echoRequst1;
    OFEchoRequest echoRequst1Clone;
    OFEchoRequest echoRequst2;
    OFHello hello1;
    OFHello hello2;
    
    
    
    @Before
    public void setUp() throws IOException {
        factory = BasicFactory.getInstance();
        cntx = new FloodlightContext();
        
        sw1 = new OFMessageDamperMockSwitch();
        sw2 = new OFMessageDamperMockSwitch();
        
        echoRequst1 = (OFEchoRequest)factory.getMessage(OFType.ECHO_REQUEST);
        echoRequst1.setPayload(new byte[] { 1 });
        echoRequst1Clone = (OFEchoRequest)
                factory.getMessage(OFType.ECHO_REQUEST);
        echoRequst1Clone.setPayload(new byte[] { 1 });
        echoRequst2 = (OFEchoRequest)factory.getMessage(OFType.ECHO_REQUEST);
        echoRequst2.setPayload(new byte[] { 2 });
        
        hello1 = (OFHello)factory.getMessage(OFType.HELLO);
        hello1.setXid(1);
        hello2 = (OFHello)factory.getMessage(OFType.HELLO);
        hello2.setXid(2);
        
    }
    
    protected void doWrite(boolean expectWrite, 
                           OFMessageDamperMockSwitch sw, 
                           OFMessage msg,
                           FloodlightContext cntx) throws IOException {
        
        boolean result;
        sw.reset();
        result = damper.write(sw, msg, cntx);
        
        if (expectWrite) {
            assertEquals(true, result);
            sw.assertMessageWasWritten(msg, cntx);
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
        doWrite(true, sw1, echoRequst1, cntx);
        doWrite(false, sw1, echoRequst1, cntx);
        doWrite(false, sw1, echoRequst1Clone, cntx);
        doWrite(true, sw1, echoRequst2, cntx);
        doWrite(false, sw1, echoRequst2, cntx);
        
        // we don't dampen hellos. All should succeed 
        doWrite(true, sw1, hello1, cntx);
        doWrite(true, sw1, hello1, cntx);
        doWrite(true, sw1, hello1, cntx);
        
        // echo request should also be dampened on sw2
        doWrite(true, sw2, echoRequst1, cntx);
        doWrite(false, sw2, echoRequst1, cntx);
        doWrite(true, sw2, echoRequst2, cntx);
        
        
        Thread.sleep(sleepTime);
        doWrite(true, sw1, echoRequst1, cntx);
        doWrite(true, sw2, echoRequst1, cntx);
        
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
        doWrite(true, sw1, echoRequst1, cntx);
        doWrite(false, sw1, echoRequst1, cntx);
        doWrite(false, sw1, echoRequst1Clone, cntx);
        doWrite(true, sw1, echoRequst2, cntx);
        doWrite(false, sw1, echoRequst2, cntx);
        
        // hello should be dampened as well
        doWrite(true, sw1, hello1, cntx);
        doWrite(false, sw1, hello1, cntx);
        doWrite(false, sw1, hello1, cntx);
        
        doWrite(true, sw1, hello2, cntx);
        doWrite(false, sw1, hello2, cntx);
        doWrite(false, sw1, hello2, cntx);
        
        // echo request should also be dampened on sw2
        doWrite(true, sw2, echoRequst1, cntx);
        doWrite(false, sw2, echoRequst1, cntx);
        doWrite(true, sw2, echoRequst2, cntx);
        
        Thread.sleep(sleepTime);
        doWrite(true, sw1, echoRequst1, cntx);
        doWrite(true, sw2, echoRequst1, cntx);
        doWrite(true, sw1, hello1, cntx);
        doWrite(true, sw1, hello2, cntx);
    }
    
}
