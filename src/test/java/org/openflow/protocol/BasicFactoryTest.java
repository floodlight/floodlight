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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.util.U16;

import junit.framework.TestCase;

public class BasicFactoryTest extends TestCase {
    public void testCreateAndParse() throws MessageParseException {
        BasicFactory factory = new BasicFactory();
        OFMessage m = factory.getMessage(OFType.HELLO);
        m.setVersion((byte) 1);
        m.setType(OFType.ECHO_REQUEST);
        m.setLength(U16.t(8));
        m.setXid(0xdeadbeef);
        ChannelBuffer bb = ChannelBuffers.dynamicBuffer();
        ChannelBuffer bb2 = ChannelBuffers.dynamicBuffer();
        m.writeTo(bb);
        bb2.writeBytes(bb, bb.readableBytes()-1);
        TestCase.assertNull(factory.parseMessage(bb2));
        bb2.writeByte(bb.readByte());
        OFMessage message = factory.parseMessage(bb2);
        TestCase.assertNotNull(message);
        TestCase.assertTrue(message.getType() == OFType.ECHO_REQUEST);
    }
}
