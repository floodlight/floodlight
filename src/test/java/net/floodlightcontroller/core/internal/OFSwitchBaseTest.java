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

package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchBase;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;

public class OFSwitchBaseTest {
    private static final String srcMac = "00:44:33:22:11:00";
    IFloodlightProviderService floodlightProvider;
    Map<Long, IOFSwitch> switches;
    private OFMessage blockMessage;
    private OFPacketIn pi;
    private IPacket testPacket;
    private byte[] testPacketSerialized;

    private class OFSwitchTest extends OFSwitchBase {
        public OFSwitchTest(IFloodlightProviderService fp) {
            super();
            stringId = "whatever";
            datapathId = 1L;
            floodlightProvider = fp;
        }

        @Override
        public void setSwitchProperties(OFDescriptionStatistics description) {
            // TODO Auto-generated method stub
        }

        @Override
        public OFPortType getPortType(short port_num) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isFastPort(short port_num) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public List<Short> getUplinkPorts() {
            // TODO Auto-generated method stub
            return null;
        }

        public void write(OFMessage msg, FloodlightContext cntx) {
            blockMessage = msg;
        }

        public void setThresholds(int high, int low, int host, int port) {
            sw.setInputThrottleThresholds(high, low, host, port);
        }

        public boolean inputThrottleEnabled() {
            return packetInThrottleEnabled;
        }

        @Override
        public String toString() {
            return "OFSwitchTest";
        }
    }
    private OFSwitchTest sw;

    @Before
    public void setUp() throws Exception {
        blockMessage = null;
        // Build our test packet
        testPacket = new Ethernet()
        .setSourceMACAddress(srcMac)
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        testPacketSerialized = testPacket.serialize();

        pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);
        floodlightProvider = createMock(IFloodlightProviderService.class);
        sw = new OFSwitchTest(floodlightProvider);
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        switches.put(sw.getId(), sw);
        expect(floodlightProvider.getSwitches()).andReturn(switches).anyTimes();
        expect(floodlightProvider.getOFMessageFactory())
                .andReturn(BasicFactory.getInstance()).anyTimes();
        replay(floodlightProvider);
    }

    /**
     * By default, high threshold is infinite
     */
    @Test
    public void testNoPacketInThrottle() {
        for (int i = 0; i < 200; i++) {
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(blockMessage == null);
        assertFalse(sw.inputThrottleEnabled());
    }

    /**
     * The test sends packet in at infinite rate (< 1ms),
     * so throttling should be enabled on 100th packet, when the first
     * rate measurement is done.
     */
    @Test
    public void testPacketInStartThrottle() {
        int high = 500;
        sw.setThresholds(high, 10, 50, 200);
        // We measure time lapse every 100 packets
        for (int i = 0; i < 100; i++) {
            assertFalse(sw.inputThrottleEnabled());
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(sw.inputThrottleEnabled());
        assertTrue(sw.inputThrottled(pi));
        assertTrue(sw.inputThrottled(pi));
        assertTrue(blockMessage == null);
    }

    /**
     * With throttling enabled, raise the low water mark threshold,
     * verify throttling stops.
     * @throws InterruptedException
     */
    @Test
    public void testPacketInStopThrottle() throws InterruptedException {
        sw.setThresholds(100, 10, 50, 200);
        // First, enable throttling
        for (int i = 0; i < 100; i++) {
            assertFalse(sw.inputThrottleEnabled());
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(sw.inputThrottleEnabled());

        sw.setThresholds(Integer.MAX_VALUE, 100000, 50, 200);
        for (int i = 0; i < 99; i++) {
            assertTrue(sw.inputThrottled(pi));
            assertTrue(sw.inputThrottleEnabled());
        }
        // Sleep for 2 msec, next packet should disable throttling
        Thread.sleep(2);
        assertFalse(sw.inputThrottled(pi));
        assertFalse(sw.inputThrottleEnabled());
   }

    /**
     * With throttling enabled, if rate of unique flows from a host
     * exceeds set threshold, a flow mod should be emitted to block host
     */
    @Test
    public void testPacketInBlockHost() {
        int high = 500;
        int perMac = 50;
        sw.setThresholds(high, 10, perMac, 200);
        // First, enable throttling
        for (int i = 0; i < 100; i++) {
            assertFalse(sw.inputThrottleEnabled());
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(sw.inputThrottleEnabled());
        assertTrue(blockMessage == null);

        // Build unique flows with the same source mac
        for (int j = 0; j < perMac - 1; j++) {
            testPacketSerialized[5]++;
            pi.setPacketData(testPacketSerialized);
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(blockMessage == null);
        testPacketSerialized[5]++;
        pi.setPacketData(testPacketSerialized);
        assertFalse(sw.inputThrottled(pi));

        // Verify the message is a flowmod with a hard timeout and srcMac
        assertTrue(blockMessage != null);
        assertTrue(blockMessage instanceof OFFlowMod);
        OFFlowMod fm = (OFFlowMod) blockMessage;
        assertTrue(fm.getHardTimeout() == 5);
        OFMatch match = fm.getMatch();
        assertTrue((match.getWildcards() & OFMatch.OFPFW_DL_SRC) == 0);
        assertTrue(Arrays.equals(match.getDataLayerSource(),
                HexString.fromHexString(srcMac)));

        // Verify non-unique OFMatches are throttled
        assertTrue(sw.inputThrottled(pi));
    }

    /**
     * With throttling enabled, if rate of unique flows from a port
     * exceeds set threshold, a flow mod should be emitted to block port
     */
    @Test
    public void testPacketInBlockPort() {
        int high = 500;
        int perPort = 200;
        sw.setThresholds(high, 10, 50, perPort);
        // First, enable throttling
        for (int i = 0; i < 100; i++) {
            assertFalse(sw.inputThrottleEnabled());
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(sw.inputThrottleEnabled());
        assertTrue(blockMessage == null);

        // Build unique flows with different source mac
        for (int j = 0; j < perPort - 1; j++) {
            testPacketSerialized[11]++;
            pi.setPacketData(testPacketSerialized);
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(blockMessage == null);
        testPacketSerialized[11]++;
        pi.setPacketData(testPacketSerialized);
        assertFalse(sw.inputThrottled(pi));

        // Verify the message is a flowmod with a hard timeout and per port
        assertTrue(blockMessage != null);
        assertTrue(blockMessage instanceof OFFlowMod);
        OFFlowMod fm = (OFFlowMod) blockMessage;
        assertTrue(fm.getHardTimeout() == 5);
        OFMatch match = fm.getMatch();
        assertTrue((match.getWildcards() & OFMatch.OFPFW_DL_SRC) != 0);
        assertTrue((match.getWildcards() & OFMatch.OFPFW_IN_PORT) == 0);
        assertTrue(match.getInputPort() == 1);

        // Verify non-unique OFMatches are throttled
        assertTrue(sw.inputThrottled(pi));
    }

}
