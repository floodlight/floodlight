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

import junit.framework.TestCase;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.OFMessageFactory;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.statistics.OFVendorStatistics;
import org.openflow.util.OFTestCase;

public class OFStatisticsRequestTest extends OFTestCase {
    public void testOFFlowStatisticsRequest() throws Exception {
        byte[] packet = new byte[] { 0x01, 0x10, 0x00, 0x38, 0x00, 0x00, 0x00,
                0x16, 0x00, 0x01, 0x00, 0x00, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                (byte) 0xff, 0x00, (byte) 0xff, (byte) 0xff };

        OFMessageFactory factory = new BasicFactory();
        ChannelBuffer packetBuf = ChannelBuffers.wrappedBuffer(packet);
        OFMessage msg = factory.parseMessage(packetBuf);
        TestCase.assertNotNull(msg);
        TestCase.assertTrue(msg instanceof OFStatisticsRequest);
        OFStatisticsRequest sr = (OFStatisticsRequest) msg;
        TestCase.assertEquals(OFStatisticsType.FLOW, sr.getStatisticType());
        TestCase.assertEquals(1, sr.getStatistics().size());
        TestCase.assertTrue(sr.getStatistics().get(0) instanceof OFFlowStatisticsRequest);
    }

    public void testOFStatisticsRequestVendor() throws Exception {
        byte[] packet = new byte[] { 0x01, 0x10, 0x00, 0x50, 0x00, 0x00, 0x00,
                0x63, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x4c, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x38, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x20,
                (byte) 0xe0, 0x00, 0x11, 0x00, 0x0c, 0x29, (byte) 0xc5,
                (byte) 0x95, 0x57, 0x02, 0x25, 0x5c, (byte) 0xca, 0x00, 0x02,
                (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2d, 0x00, 0x50, 0x04,
                0x00, 0x00, 0x00, 0x00, (byte) 0xff, 0x00, 0x00, 0x00,
                (byte) 0xff, (byte) 0xff, 0x4e, 0x20 };

        OFMessageFactory factory = new BasicFactory();
        ChannelBuffer packetBuf = ChannelBuffers.wrappedBuffer(packet);
        OFMessage msg = factory.parseMessage(packetBuf);
        TestCase.assertNotNull(msg);
        TestCase.assertTrue(msg instanceof OFStatisticsRequest);
        OFStatisticsRequest sr = (OFStatisticsRequest) msg;
        TestCase.assertEquals(OFStatisticsType.VENDOR, sr.getStatisticType());
        TestCase.assertEquals(1, sr.getStatistics().size());
        TestCase.assertTrue(sr.getStatistics().get(0) instanceof OFVendorStatistics);
        TestCase.assertEquals(68, ((OFVendorStatistics)sr.getStatistics().get(0)).getLength());
    }
}
