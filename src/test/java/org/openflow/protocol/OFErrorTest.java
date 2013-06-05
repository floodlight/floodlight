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

import java.util.List;

import junit.framework.TestCase;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.OFError.OFHelloFailedCode;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.protocol.factory.OFMessageFactory;
import org.openflow.util.OFTestCase;

public class OFErrorTest extends OFTestCase {
    public void testWriteRead() throws Exception {
        OFError msg = (OFError) messageFactory.getMessage(OFType.ERROR);
        msg.setMessageFactory(messageFactory);
        msg.setErrorType(OFErrorType.OFPET_HELLO_FAILED.getValue());
        msg.setErrorCode((short) OFHelloFailedCode.OFPHFC_INCOMPATIBLE
                .ordinal());
        ChannelBuffer bb = ChannelBuffers.dynamicBuffer();
        bb.clear();
        msg.writeTo(bb);
        msg.readFrom(bb);
        TestCase.assertEquals(OFErrorType.OFPET_HELLO_FAILED.getValue(),
                msg.getErrorType());
        TestCase.assertEquals((short) OFHelloFailedCode.OFPHFC_INCOMPATIBLE
                .ordinal(), msg.getErrorType());
        TestCase.assertNull(msg.getOffendingMsg());

        msg.setOffendingMsg(new OFHello());
        bb.clear();
        msg.writeTo(bb);
        msg.readFrom(bb);
        TestCase.assertEquals(OFErrorType.OFPET_HELLO_FAILED.getValue(),
                msg.getErrorType());
        TestCase.assertEquals((short) OFHelloFailedCode.OFPHFC_INCOMPATIBLE
                .ordinal(), msg.getErrorType());
        TestCase.assertNotNull(msg.getOffendingMsg());
        TestCase.assertEquals(OFHello.MINIMUM_LENGTH,
                msg.getOffendingMsg().length);
    }

    public void testGarbageAtEnd() throws MessageParseException {
        // This is a OFError msg (12 bytes), that encaps a OFVendor msg (24
        // bytes)
        // AND some zeros at the end (40 bytes) for a total of 76 bytes
        // THIS is what an NEC sends in reply to Nox's VENDOR request
        byte[] oferrorRaw = { 0x01, 0x01, 0x00, 0x4c, 0x00, 0x00, 0x10,
                (byte) 0xcc, 0x00, 0x01, 0x00, 0x01, 0x01, 0x04, 0x00, 0x18,
                0x00, 0x00, 0x10, (byte) 0xcc, 0x00, 0x00, 0x23, 0x20, 0x00,
                0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00 };
        OFMessageFactory factory = BasicFactory.getInstance();
        ChannelBuffer oferrorBuf = 
                ChannelBuffers.wrappedBuffer(oferrorRaw);
        List<OFMessage> msg = factory.parseMessage(oferrorBuf);
        TestCase.assertNotNull(msg);
        TestCase.assertEquals(msg.size(), 1);
        TestCase.assertEquals(76, msg.get(0).getLengthU());
        ChannelBuffer out = ChannelBuffers.dynamicBuffer();
        msg.get(0).writeTo(out);
        TestCase.assertEquals(76, out.readableBytes());
    }
}
