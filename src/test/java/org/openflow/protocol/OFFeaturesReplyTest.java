/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol;


import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.util.OFTestCase;


public class OFFeaturesReplyTest extends OFTestCase {
    public void testWriteRead() throws Exception {
        OFFeaturesReply ofr = (OFFeaturesReply) messageFactory
                .getMessage(OFType.FEATURES_REPLY);
        List<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        OFPhysicalPort port = new OFPhysicalPort();
        port.setHardwareAddress(new byte[6]);
        port.setName("eth0");
        ports.add(port);
        ofr.setPorts(ports);
        ChannelBuffer bb = ChannelBuffers.dynamicBuffer();
        bb.clear();
        ofr.writeTo(bb);
        ofr.readFrom(bb);
        TestCase.assertEquals(1, ofr.getPorts().size());
        TestCase.assertEquals("eth0", ofr.getPorts().get(0).getName());

        // test a 15 character name
        ofr.getPorts().get(0).setName("012345678901234");
        bb.clear();
        ofr.writeTo(bb);
        ofr.readFrom(bb);
        TestCase.assertEquals("012345678901234", ofr.getPorts().get(0).getName());

        // test a 16 character name getting truncated
        ofr.getPorts().get(0).setName("0123456789012345");
        bb.clear();
        ofr.writeTo(bb);
        ofr.readFrom(bb);
        TestCase.assertEquals("012345678901234", ofr.getPorts().get(0).getName());
    }
}
