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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeAlreadyStarted;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeCompleted;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeNotStarted;
import net.floodlightcontroller.core.IOFSwitch.PortChangeEvent;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.OFSwitchBase;
import net.floodlightcontroller.debugcounter.DebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
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
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;
import org.openflow.protocol.OFPortStatus.OFPortReason;
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
    private ImmutablePort p1a;
    private ImmutablePort p1b;
    private ImmutablePort p2a;
    private ImmutablePort p2b;
    private ImmutablePort p3;
    private final ImmutablePort portFoo1 = ImmutablePort.create("foo", (short)11);
    private final ImmutablePort portFoo2 = ImmutablePort.create("foo", (short)12);
    private final ImmutablePort portBar1 = ImmutablePort.create("bar", (short)11);
    private final ImmutablePort portBar2 = ImmutablePort.create("bar", (short)12);
    private final PortChangeEvent portFoo1Add =
            new PortChangeEvent(portFoo1, PortChangeType.ADD);
    private final PortChangeEvent portFoo2Add =
            new PortChangeEvent(portFoo2, PortChangeType.ADD);
    private final PortChangeEvent portBar1Add =
            new PortChangeEvent(portBar1, PortChangeType.ADD);
    private final PortChangeEvent portBar2Add =
            new PortChangeEvent(portBar2, PortChangeType.ADD);
    private final PortChangeEvent portFoo1Del =
            new PortChangeEvent(portFoo1, PortChangeType.DELETE);
    private final PortChangeEvent portFoo2Del =
            new PortChangeEvent(portFoo2, PortChangeType.DELETE);
    private final PortChangeEvent portBar1Del =
            new PortChangeEvent(portBar1, PortChangeType.DELETE);
    private final PortChangeEvent portBar2Del =
            new PortChangeEvent(portBar2, PortChangeType.DELETE);

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
        IDebugCounterService debugCounter = new DebugCounter();
        sw.setDebugCounterService(debugCounter);
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        switches.put(sw.getId(), sw);
        expect(floodlightProvider.getSwitch(sw.getId())).andReturn(sw).anyTimes();
        expect(floodlightProvider.getOFMessageFactory())
                .andReturn(BasicFactory.getInstance()).anyTimes();
    }

    @Before
    public void setUpPorts() {
        ImmutablePort.Builder bld = new ImmutablePort.Builder();
        // p1a is disabled
        p1a = bld.setName("port1")
                 .setPortNumber((short)1)
                 .setPortStateLinkDown(true)
                 .build();
        assertFalse("Sanity check portEnabled", p1a.isEnabled());

        // p1b is enabled
        // p1b has different feature from p1a
        p1b = bld.addCurrentFeature(OFPortFeatures.OFPPF_1GB_FD)
                 .setPortStateLinkDown(false)
                 .build();
        assertTrue("Sanity check portEnabled", p1b.isEnabled());

        // p2 is disabled
        // p2 has mixed case
        bld = new ImmutablePort.Builder();
        p2a = bld.setName("Port2")
                .setPortNumber((short)2)
                .setPortStateLinkDown(false)
                .addConfig(OFPortConfig.OFPPC_PORT_DOWN)
                .build();
        // p2b only differs in PortFeatures
        p2b = bld.addCurrentFeature(OFPortFeatures.OFPPF_100MB_HD)
                 .build();
        assertFalse("Sanity check portEnabled", p2a.isEnabled());
        // p3 is enabled
        // p3 has mixed case
        bld = new ImmutablePort.Builder();
        p3 = bld.setName("porT3")
                .setPortNumber((short)3)
                .setPortStateLinkDown(false)
                .build();
        assertTrue("Sanity check portEnabled", p3.isEnabled());

    }

    /**
     * By default, high threshold is infinite
     */
    @Test
    public void testNoPacketInThrottle() {
        replay(floodlightProvider);
        /* disable input throttle */
        sw.setThresholds(Integer.MAX_VALUE, 1, 0, 0);
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
        floodlightProvider.addSwitchEvent(anyLong(),
                (String)anyObject(), anyBoolean());
        replay(floodlightProvider);

        int high = 500;
        sw.setThresholds(high, 10, 50, 200);
        // We measure time lapse every 1000 packets
        for (int i = 0; i < 1000; i++) {
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
        floodlightProvider.addSwitchEvent(anyLong(),
                (String)anyObject(), anyBoolean());
        expectLastCall().times(2);
        replay(floodlightProvider);

        sw.setThresholds(100, 10, 50, 200);
        // First, enable throttling
        for (int i = 0; i < 1000; i++) {
            assertFalse(sw.inputThrottleEnabled());
            assertFalse(sw.inputThrottled(pi));
        }
        assertTrue(sw.inputThrottleEnabled());

        sw.setThresholds(Integer.MAX_VALUE, 100000, 50, 200);
        for (int i = 0; i < 999; i++) {
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
        floodlightProvider.addSwitchEvent(anyLong(),
                (String)anyObject(), anyBoolean());
        expectLastCall().times(2);
        replay(floodlightProvider);

        int high = 500;
        int perMac = 50;
        sw.setThresholds(high, 10, perMac, 200);
        // First, enable throttling
        for (int i = 0; i < 1000; i++) {
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
        floodlightProvider.addSwitchEvent(anyLong(),
                (String)anyObject(), anyBoolean());
        expectLastCall().times(2);
        replay(floodlightProvider);

        int high = 500;
        int perPort = 200;
        sw.setThresholds(high, 10, 50, perPort);
        // First, enable throttling
        for (int i = 0; i < 1000; i++) {
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

    /**
     * Test whether two collections contains the same elements, regardless
     * of the order in which the elements appear in the collections
     * @param expected
     * @param actual
     */
    private static <T> void assertCollectionEqualsNoOrder(Collection<T> expected,
                                         Collection<T> actual) {
        String msg = String.format("expected=%s, actual=%s",
                                   expected, actual);
        assertEquals(msg, expected.size(), actual.size());
        for(T e: expected) {
            if (!actual.contains(e)) {
                msg = String.format("Expected element %s not found in " +
                        "actual. expected=%s, actual=%s",
                    e, expected, actual);
                fail(msg);
            }
        }
    }


    /**
     * Test "normal" setPorts() and comparePorts() methods. No name<->number
     * conflicts or exception testing.
     */
    @Test
    public void testBasicSetPortOperations() {
        Collection<ImmutablePort> oldPorts = Collections.emptyList();
        Collection<ImmutablePort> oldEnabledPorts = Collections.emptyList();
        Collection<Short> oldEnabledPortNumbers = Collections.emptyList();
        List<ImmutablePort> ports = new ArrayList<ImmutablePort>();


        Collection<PortChangeEvent> expectedChanges =
                new ArrayList<IOFSwitch.PortChangeEvent>();

        Collection<PortChangeEvent> actualChanges = sw.comparePorts(ports);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertEquals(0, sw.getPorts().size());
        assertEquals(0, sw.getEnabledPorts().size());
        assertEquals(0, sw.getEnabledPortNumbers().size());

        actualChanges = sw.setPorts(ports);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertEquals(0, sw.getPorts().size());
        assertEquals(0, sw.getEnabledPorts().size());
        assertEquals(0, sw.getEnabledPortNumbers().size());

        //---------------------------------------------
        // Add port p1a and p2a
        ports.add(p1a);
        ports.add(p2a);

        PortChangeEvent evP1aAdded =
                new PortChangeEvent(p1a, PortChangeType.ADD);
        PortChangeEvent evP2aAdded =
                new PortChangeEvent(p2a, PortChangeType.ADD);

        expectedChanges.clear();
        expectedChanges.add(evP1aAdded);
        expectedChanges.add(evP2aAdded);

        actualChanges = sw.comparePorts(ports);
        assertEquals(0, sw.getPorts().size());
        assertEquals(0, sw.getEnabledPorts().size());
        assertEquals(0, sw.getEnabledPortNumbers().size());
        assertEquals(2, actualChanges.size());
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);

        actualChanges = sw.setPorts(ports);
        assertEquals(2, actualChanges.size());
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);

        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        assertTrue("enabled ports should be empty",
                   sw.getEnabledPortNumbers().isEmpty());
        assertTrue("enabled ports should be empty",
                   sw.getEnabledPorts().isEmpty());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(null, sw.getPort((short)3));
        assertEquals(null, sw.getPort("port3"));
        assertEquals(null, sw.getPort("PoRt3")); // case insensitive get


        //----------------------------------------------------
        // Set the same ports again. No changes
        oldPorts = sw.getPorts();
        oldEnabledPorts = sw.getEnabledPorts();
        oldEnabledPortNumbers = sw.getEnabledPortNumbers();

        expectedChanges.clear();

        actualChanges = sw.comparePorts(ports);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertEquals(oldPorts, sw.getPorts());
        assertEquals(oldEnabledPorts, sw.getEnabledPorts());
        assertEquals(oldEnabledPortNumbers, sw.getEnabledPortNumbers());

        actualChanges = sw.setPorts(ports);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertEquals(oldPorts, sw.getPorts());
        assertEquals(oldEnabledPorts, sw.getEnabledPorts());
        assertEquals(oldEnabledPortNumbers, sw.getEnabledPortNumbers());
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        assertTrue("enabled ports should be empty",
                   sw.getEnabledPortNumbers().isEmpty());
        assertTrue("enabled ports should be empty",
                   sw.getEnabledPorts().isEmpty());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(null, sw.getPort((short)3));
        assertEquals(null, sw.getPort("port3"));
        assertEquals(null, sw.getPort("PoRt3")); // case insensitive get

        //----------------------------------------------------
        // Remove p1a, add p1b. Should receive a port up
        oldPorts = sw.getPorts();
        oldEnabledPorts = sw.getEnabledPorts();
        oldEnabledPortNumbers = sw.getEnabledPortNumbers();
        ports.clear();
        ports.add(p2a);
        ports.add(p1b);

        // comparePorts
        PortChangeEvent evP1bUp = new PortChangeEvent(p1b, PortChangeType.UP);
        actualChanges = sw.comparePorts(ports);
        assertEquals(oldPorts, sw.getPorts());
        assertEquals(oldEnabledPorts, sw.getEnabledPorts());
        assertEquals(oldEnabledPortNumbers, sw.getEnabledPortNumbers());
        assertEquals(1, actualChanges.size());
        assertTrue("No UP event for port1", actualChanges.contains(evP1bUp));

        // setPorts
        actualChanges = sw.setPorts(ports);
        assertEquals(1, actualChanges.size());
        assertTrue("No UP event for port1", actualChanges.contains(evP1bUp));
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        List<ImmutablePort> enabledPorts = new ArrayList<ImmutablePort>();
        enabledPorts.add(p1b);
        List<Short> enabledPortNumbers = new ArrayList<Short>();
        enabledPortNumbers.add((short)1);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1b, sw.getPort((short)1));
        assertEquals(p1b, sw.getPort("port1"));
        assertEquals(p1b, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(null, sw.getPort((short)3));
        assertEquals(null, sw.getPort("port3"));
        assertEquals(null, sw.getPort("PoRt3")); // case insensitive get

        //----------------------------------------------------
        // Remove p2a, add p2b. Should receive a port modify
        oldPorts = sw.getPorts();
        oldEnabledPorts = sw.getEnabledPorts();
        oldEnabledPortNumbers = sw.getEnabledPortNumbers();
        ports.clear();
        ports.add(p2b);
        ports.add(p1b);

        PortChangeEvent evP2bModified =
                new PortChangeEvent(p2b, PortChangeType.OTHER_UPDATE);

        // comparePorts
        actualChanges = sw.comparePorts(ports);
        assertEquals(oldPorts, sw.getPorts());
        assertEquals(oldEnabledPorts, sw.getEnabledPorts());
        assertEquals(oldEnabledPortNumbers, sw.getEnabledPortNumbers());
        assertEquals(1, actualChanges.size());
        assertTrue("No OTHER_CHANGE event for port2",
                   actualChanges.contains(evP2bModified));

        // setPorts
        actualChanges = sw.setPorts(ports);
        assertEquals(1, actualChanges.size());
        assertTrue("No OTHER_CHANGE event for port2",
                   actualChanges.contains(evP2bModified));
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts = new ArrayList<ImmutablePort>();
        enabledPorts.add(p1b);
        enabledPortNumbers = new ArrayList<Short>();
        enabledPortNumbers.add((short)1);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1b, sw.getPort((short)1));
        assertEquals(p1b, sw.getPort("port1"));
        assertEquals(p1b, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2b, sw.getPort((short)2));
        assertEquals(p2b, sw.getPort("port2"));
        assertEquals(p2b, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(null, sw.getPort((short)3));
        assertEquals(null, sw.getPort("port3"));
        assertEquals(null, sw.getPort("PoRt3")); // case insensitive get


        //----------------------------------------------------
        // Remove p1b, add p1a. Should receive a port DOWN
        // Remove p2b, add p2a. Should receive a port modify
        // Add p3, should receive an add
        oldPorts = sw.getPorts();
        oldEnabledPorts = sw.getEnabledPorts();
        oldEnabledPortNumbers = sw.getEnabledPortNumbers();
        ports.clear();
        ports.add(p2a);
        ports.add(p1a);
        ports.add(p3);

        PortChangeEvent evP1aDown =
                new PortChangeEvent(p1a, PortChangeType.DOWN);
        PortChangeEvent evP2aModified =
                new PortChangeEvent(p2a, PortChangeType.OTHER_UPDATE);
        PortChangeEvent evP3Add =
                new PortChangeEvent(p3, PortChangeType.ADD);
        expectedChanges.clear();
        expectedChanges.add(evP1aDown);
        expectedChanges.add(evP2aModified);
        expectedChanges.add(evP3Add);

        // comparePorts
        actualChanges = sw.comparePorts(ports);
        assertEquals(oldPorts, sw.getPorts());
        assertEquals(oldEnabledPorts, sw.getEnabledPorts());
        assertEquals(oldEnabledPortNumbers, sw.getEnabledPortNumbers());
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);

        // setPorts
        actualChanges = sw.setPorts(ports);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPorts.add(p3);
        enabledPortNumbers.clear();
        enabledPortNumbers.add((short)3);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(p3, sw.getPort((short)3));
        assertEquals(p3, sw.getPort("port3"));
        assertEquals(p3, sw.getPort("PoRt3")); // case insensitive get


        //----------------------------------------------------
        // Remove p1b Should receive a port DELETE
        // Remove p2b Should receive a port DELETE
        oldPorts = sw.getPorts();
        oldEnabledPorts = sw.getEnabledPorts();
        oldEnabledPortNumbers = sw.getEnabledPortNumbers();
        ports.clear();
        ports.add(p3);

        PortChangeEvent evP1aDel =
                new PortChangeEvent(p1a, PortChangeType.DELETE);
        PortChangeEvent evP2aDel =
                new PortChangeEvent(p2a, PortChangeType.DELETE);
        expectedChanges.clear();
        expectedChanges.add(evP1aDel);
        expectedChanges.add(evP2aDel);

        // comparePorts
        actualChanges = sw.comparePorts(ports);
        assertEquals(oldPorts, sw.getPorts());
        assertEquals(oldEnabledPorts, sw.getEnabledPorts());
        assertEquals(oldEnabledPortNumbers, sw.getEnabledPortNumbers());
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);

        // setPorts
        actualChanges = sw.setPorts(ports);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPorts.add(p3);
        enabledPortNumbers.clear();
        enabledPortNumbers.add((short)3);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());

        assertEquals(p3, sw.getPort((short)3));
        assertEquals(p3, sw.getPort("port3"));
        assertEquals(p3, sw.getPort("PoRt3")); // case insensitive get
    }


    /**
     * Test "normal" OFPortStatus handling. No name<->number
     * conflicts or exception testing.
     */
    @Test
    public void testBasicPortStatusOperation() {
        OFPortStatus ps = (OFPortStatus)
                BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);
        List<ImmutablePort> ports = new ArrayList<ImmutablePort>();
        ports.add(p1a);
        ports.add(p2a);


        // Set p1a and p2a as baseline
        PortChangeEvent evP1aAdded =
                new PortChangeEvent(p1a, PortChangeType.ADD);
        PortChangeEvent evP2aAdded =
                new PortChangeEvent(p2a, PortChangeType.ADD);

        Collection<PortChangeEvent> expectedChanges =
                new ArrayList<IOFSwitch.PortChangeEvent>();
        expectedChanges.add(evP1aAdded);
        expectedChanges.add(evP2aAdded);

        Collection<PortChangeEvent> actualChanges = sw.comparePorts(ports);
        assertEquals(0, sw.getPorts().size());
        assertEquals(0, sw.getEnabledPorts().size());
        assertEquals(0, sw.getEnabledPortNumbers().size());
        assertEquals(2, actualChanges.size());
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);

        actualChanges = sw.setPorts(ports);
        assertEquals(2, actualChanges.size());
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);

        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        assertTrue("enabled ports should be empty",
                   sw.getEnabledPortNumbers().isEmpty());
        assertTrue("enabled ports should be empty",
                   sw.getEnabledPorts().isEmpty());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get

        //----------------------------------------------------
        // P1a -> p1b. Should receive a port up
        ports.clear();
        ports.add(p2a);
        ports.add(p1b);

        ps.setReason(OFPortReason.OFPPR_MODIFY.getReasonCode());
        ps.setDesc(p1b.toOFPhysicalPort());

        PortChangeEvent evP1bUp = new PortChangeEvent(p1b, PortChangeType.UP);
        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        expectedChanges.add(evP1bUp);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        List<ImmutablePort> enabledPorts = new ArrayList<ImmutablePort>();
        enabledPorts.add(p1b);
        List<Short> enabledPortNumbers = new ArrayList<Short>();
        enabledPortNumbers.add((short)1);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1b, sw.getPort((short)1));
        assertEquals(p1b, sw.getPort("port1"));
        assertEquals(p1b, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get

        //----------------------------------------------------
        // p2a -> p2b. Should receive a port modify
        ports.clear();
        ports.add(p2b);
        ports.add(p1b);

        PortChangeEvent evP2bModified =
                new PortChangeEvent(p2b, PortChangeType.OTHER_UPDATE);

        ps.setReason(OFPortReason.OFPPR_MODIFY.getReasonCode());
        ps.setDesc(p2b.toOFPhysicalPort());

        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        expectedChanges.add(evP2bModified);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts = new ArrayList<ImmutablePort>();
        enabledPorts.add(p1b);
        enabledPortNumbers = new ArrayList<Short>();
        enabledPortNumbers.add((short)1);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1b, sw.getPort((short)1));
        assertEquals(p1b, sw.getPort("port1"));
        assertEquals(p1b, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2b, sw.getPort((short)2));
        assertEquals(p2b, sw.getPort("port2"));
        assertEquals(p2b, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(null, sw.getPort((short)3));
        assertEquals(null, sw.getPort("port3"));
        assertEquals(null, sw.getPort("PoRt3")); // case insensitive get


        //----------------------------------------------------
        // p1b -> p1a. Via an OFPPR_ADD, Should receive a port DOWN
        ports.clear();
        ports.add(p2b);
        ports.add(p1a);

        // we use an ADD here. We treat ADD and MODIFY the same way
        ps.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
        ps.setDesc(p1a.toOFPhysicalPort());

        PortChangeEvent evP1aDown =
                new PortChangeEvent(p1a, PortChangeType.DOWN);
        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        expectedChanges.add(evP1aDown);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPortNumbers.clear();
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2b, sw.getPort((short)2));
        assertEquals(p2b, sw.getPort("port2"));
        assertEquals(p2b, sw.getPort("PoRt2")); // case insensitive get


        //----------------------------------------------------
        // p2b -> p2a. Via an OFPPR_ADD, Should receive a port MODIFY
        ports.clear();
        ports.add(p2a);
        ports.add(p1a);

        // we use an ADD here. We treat ADD and MODIFY the same way
        ps.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
        ps.setDesc(p2a.toOFPhysicalPort());

        PortChangeEvent evP2aModify =
                new PortChangeEvent(p2a, PortChangeType.OTHER_UPDATE);
        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        expectedChanges.add(evP2aModify);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPortNumbers.clear();
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(p2a, sw.getPort((short)2));
        assertEquals(p2a, sw.getPort("port2"));
        assertEquals(p2a, sw.getPort("PoRt2")); // case insensitive get


        //----------------------------------------------------
        // Remove p2a
        ports.clear();
        ports.add(p1a);

        ps.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
        ps.setDesc(p2a.toOFPhysicalPort());

        PortChangeEvent evP2aDel =
                new PortChangeEvent(p2a, PortChangeType.DELETE);
        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        expectedChanges.add(evP2aDel);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPortNumbers.clear();
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(null, sw.getPort((short)2));
        assertEquals(null, sw.getPort("port2"));
        assertEquals(null, sw.getPort("PoRt2")); // case insensitive get

        //----------------------------------------------------
        // Remove p2a again. Nothing should happen.
        ports.clear();
        ports.add(p1a);

        ps.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
        ps.setDesc(p2a.toOFPhysicalPort());

        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPortNumbers.clear();
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1a, sw.getPort((short)1));
        assertEquals(p1a, sw.getPort("port1"));
        assertEquals(p1a, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(null, sw.getPort((short)2));
        assertEquals(null, sw.getPort("port2"));
        assertEquals(null, sw.getPort("PoRt2")); // case insensitive get


        //----------------------------------------------------
        // Remove p1a
        ports.clear();

        ps.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
        ps.setDesc(p1a.toOFPhysicalPort());

        PortChangeEvent evP1aDel =
                new PortChangeEvent(p1a, PortChangeType.DELETE);
        actualChanges = sw.processOFPortStatus(ps);
        expectedChanges.clear();
        expectedChanges.add(evP1aDel);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPortNumbers.clear();
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(null, sw.getPort((short)1));
        assertEquals(null, sw.getPort("port1"));
        assertEquals(null, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(null, sw.getPort((short)2));
        assertEquals(null, sw.getPort("port2"));
        assertEquals(null, sw.getPort("PoRt2")); // case insensitive get


        //----------------------------------------------------
        // Add p3, should receive an add
        ports.clear();
        ports.add(p3);

        PortChangeEvent evP3Add =
                new PortChangeEvent(p3, PortChangeType.ADD);
        expectedChanges.clear();
        expectedChanges.add(evP3Add);

        ps.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
        ps.setDesc(p3.toOFPhysicalPort());

        actualChanges = sw.processOFPortStatus(ps);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPorts.add(p3);
        enabledPortNumbers.clear();
        enabledPortNumbers.add((short)3);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(null, sw.getPort((short)1));
        assertEquals(null, sw.getPort("port1"));
        assertEquals(null, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(null, sw.getPort((short)2));
        assertEquals(null, sw.getPort("port2"));
        assertEquals(null, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(p3, sw.getPort((short)3));
        assertEquals(p3, sw.getPort("port3"));
        assertEquals(p3, sw.getPort("PoRt3")); // case insensitive get

        //----------------------------------------------------
        // Add p1b, back should receive an add
        ports.clear();
        ports.add(p1b);
        ports.add(p3);

        PortChangeEvent evP1bAdd =
                new PortChangeEvent(p1b, PortChangeType.ADD);
        expectedChanges.clear();
        expectedChanges.add(evP1bAdd);

        // use a modify to add the port
        ps.setReason(OFPortReason.OFPPR_MODIFY.getReasonCode());
        ps.setDesc(p1b.toOFPhysicalPort());

        actualChanges = sw.processOFPortStatus(ps);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPorts.add(p3);
        enabledPorts.add(p1b);
        enabledPortNumbers.clear();
        enabledPortNumbers.add((short)3);
        enabledPortNumbers.add((short)1);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1b, sw.getPort((short)1));
        assertEquals(p1b, sw.getPort("port1"));
        assertEquals(p1b, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(null, sw.getPort((short)2));
        assertEquals(null, sw.getPort("port2"));
        assertEquals(null, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(p3, sw.getPort((short)3));
        assertEquals(p3, sw.getPort("port3"));
        assertEquals(p3, sw.getPort("PoRt3")); // case insensitive get

        //----------------------------------------------------
        // Modify, but nothing really changed
        ports.clear();
        ports.add(p1b);
        ports.add(p3);

        expectedChanges.clear();

        // use a modify to add the port
        ps.setReason(OFPortReason.OFPPR_MODIFY.getReasonCode());
        ps.setDesc(p1b.toOFPhysicalPort());

        actualChanges = sw.processOFPortStatus(ps);
        assertCollectionEqualsNoOrder(expectedChanges, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
        enabledPorts.clear();
        enabledPorts.add(p3);
        enabledPorts.add(p1b);
        enabledPortNumbers.clear();
        enabledPortNumbers.add((short)3);
        enabledPortNumbers.add((short)1);
        assertCollectionEqualsNoOrder(enabledPorts, sw.getEnabledPorts());
        assertCollectionEqualsNoOrder(enabledPortNumbers,
                                   sw.getEnabledPortNumbers());
        assertEquals(p1b, sw.getPort((short)1));
        assertEquals(p1b, sw.getPort("port1"));
        assertEquals(p1b, sw.getPort("PoRt1")); // case insensitive get

        assertEquals(null, sw.getPort((short)2));
        assertEquals(null, sw.getPort("port2"));
        assertEquals(null, sw.getPort("PoRt2")); // case insensitive get

        assertEquals(p3, sw.getPort((short)3));
        assertEquals(p3, sw.getPort("port3"));
        assertEquals(p3, sw.getPort("PoRt3")); // case insensitive get
    }


    /**
     * Test exception handling for setPorts() and comparePorts()
     */
    @Test
    public void testSetPortExceptions() {
        try {
            sw.setPorts(null);
            fail("Excpeted exception not thrown");
        } catch (NullPointerException e) { };

        // two ports with same name
        List<ImmutablePort> ports = new ArrayList<ImmutablePort>();
        ports.add(ImmutablePort.create("port1", (short)1));
        ports.add(ImmutablePort.create("port1", (short)2));
        try {
            sw.setPorts(ports);
            fail("Excpeted exception not thrown");
        } catch (IllegalArgumentException e) { };

        // two ports with same number
        ports.clear();
        ports.add(ImmutablePort.create("port1", (short)1));
        ports.add(ImmutablePort.create("port2", (short)1));
        try {
            sw.setPorts(ports);
            fail("Excpeted exception not thrown");
        } catch (IllegalArgumentException e) { };

        // null port in list
        ports.clear();
        ports.add(ImmutablePort.create("port1", (short)1));
        ports.add(null);
        try {
            sw.setPorts(ports);
            fail("Excpeted exception not thrown");
        } catch (NullPointerException e) { };

        // try getPort(null)
        try {
            sw.getPort(null);
            fail("Excpeted exception not thrown");
        } catch (NullPointerException e) { };

        //--------------------------
        // comparePorts()
        try {
            sw.comparePorts(null);
            fail("Excpeted exception not thrown");
        } catch (NullPointerException e) { };

        // two ports with same name
        ports = new ArrayList<ImmutablePort>();
        ports.add(ImmutablePort.create("port1", (short)1));
        ports.add(ImmutablePort.create("port1", (short)2));
        try {
            sw.comparePorts(ports);
            fail("Excpeted exception not thrown");
        } catch (IllegalArgumentException e) { };

        // two ports with same number
        ports.clear();
        ports.add(ImmutablePort.create("port1", (short)1));
        ports.add(ImmutablePort.create("port2", (short)1));
        try {
            sw.comparePorts(ports);
            fail("Excpeted exception not thrown");
        } catch (IllegalArgumentException e) { };

        // null port in list
        ports.clear();
        ports.add(ImmutablePort.create("port1", (short)1));
        ports.add(null);
        try {
            sw.comparePorts(ports);
            fail("Excpeted exception not thrown");
        } catch (NullPointerException e) { };

        // try getPort(null)
        try {
            sw.getPort(null);
            fail("Excpeted exception not thrown");
        } catch (NullPointerException e) { };

    }

    @Test
    public void testPortStatusExceptions() {
        OFPortStatus ps = (OFPortStatus)
                BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);

        try {
            sw.processOFPortStatus(null);
            fail("Expected exception not thrown");
        } catch (NullPointerException e)  { }

        // illegal reason code
        ps.setReason((byte)0x42);
        ps.setDesc(ImmutablePort.create("p1", (short)1).toOFPhysicalPort());
        try {
            sw.processOFPortStatus(ps);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e)  { }

        // null port
        ps.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
        ps.setDesc(null);
        try {
            sw.processOFPortStatus(ps);
            fail("Expected exception not thrown");
        } catch (NullPointerException e)  { }
    }

    /**
     * Assert that the expected PortChangeEvents have been recevied, asserting
     * the expected ordering.
     *
     * All events in earlyEvents have to appear in actualEvents before any
     * event in lateEvent appears. Events in anytimeEvents can appear at any
     * given time. earlyEvents, lateEvents, and anytimeEvents must be mutually
     * exclusive (their intersection must be none) and their union must
     * contain all elements from actualEvents
     * @param earlyEvents
     * @param lateEvents
     * @param anytimeEvents
     * @param actualEvents
     */
    private static void assertChangeEvents(Collection<PortChangeEvent> earlyEvents,
                                      Collection<PortChangeEvent> lateEvents,
                                      Collection<PortChangeEvent> anytimeEvents,
                                      Collection<PortChangeEvent> actualEvents) {
        String inputDesc = String.format("earlyEvents=%s, lateEvents=%s, " +
                "anytimeEvents=%s, actualEvents=%s",
                earlyEvents, lateEvents, anytimeEvents, actualEvents);
        // Make copies of expected lists, so we can modify them
        Collection<PortChangeEvent> early =
                new ArrayList<PortChangeEvent>(earlyEvents);
        Collection<PortChangeEvent> late =
                new ArrayList<PortChangeEvent>(lateEvents);
        Collection<PortChangeEvent> any =
                new ArrayList<PortChangeEvent>(anytimeEvents);

        // Sanity check: no overlap between early, late, and anytime events
        for (PortChangeEvent ev: early) {
            assertFalse("Test setup error. Early and late overlap",
                        late.contains(ev));
            assertFalse("Test setup error. Early and anytime overlap",
                        any.contains(ev));
        }
        for (PortChangeEvent ev: late) {
            assertFalse("Test setup error. Late and early overlap",
                        early.contains(ev));
            assertFalse("Test setup error. Late and any overlap",
                        any.contains(ev));
        }
        for (PortChangeEvent ev: any) {
            assertFalse("Test setup error. Anytime and early overlap",
                        early.contains(ev));
            assertFalse("Test setup error. Anytime and late overlap",
                        late.contains(ev));
        }

        for (PortChangeEvent a: actualEvents) {
            if (early.remove(a)) {
                continue;
            }
            if (any.remove(a)) {
                continue;
            }
            if (late.remove(a)) {
                if (!early.isEmpty()) {
                    fail(a + " is in late list, but haven't seen all required " +
                         "early events. " + inputDesc);
                } else {
                    continue;
                }
            }
            fail(a + " was not expected. " + inputDesc);
        }
        if (!early.isEmpty())
            fail("Elements left in early: " + early + ". " + inputDesc);
        if (!late.isEmpty())
            fail("Elements left in late: " + late + ". " + inputDesc);
        if (!any.isEmpty())
            fail("Elements left in any: " + any + ". " + inputDesc);
    }

    /**
     * Test setPort() with changing name / number mappings
     * We don't test comparePorts() here. We assume setPorts() and
     * comparePorts() use the same underlying implementation
     */
    @Test
    public void testSetPortNameNumberMappingChange() {

        List<ImmutablePort> ports = new ArrayList<ImmutablePort>();
        Collection<PortChangeEvent> early = new ArrayList<PortChangeEvent>();
        Collection<PortChangeEvent> late = new ArrayList<PortChangeEvent>();
        Collection<PortChangeEvent> anytime = new ArrayList<PortChangeEvent>();
        Collection<PortChangeEvent> actualChanges = null;

        ports.add(portFoo1);
        ports.add(p1a);
        sw.setPorts(ports);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Add portFoo2: name collision
        ports.clear();
        ports.add(portFoo2);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.setPorts(ports);
        early.add(portFoo1Del);
        late.add(portFoo2Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Add portBar2: number collision
        ports.clear();
        ports.add(portBar2);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.setPorts(ports);
        early.add(portFoo2Del);
        late.add(portBar2Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Set to portFoo1, portBar2. No collisions in this step
        ports.clear();
        ports.add(portFoo1);
        ports.add(portBar2);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.setPorts(ports);
        anytime.add(portFoo1Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Add portFoo2: name and number collision
        ports.clear();
        ports.add(portFoo2);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.setPorts(ports);
        early.add(portFoo1Del);
        early.add(portBar2Del);
        late.add(portFoo2Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Set to portFoo2, portBar1. No collisions in this step
        ports.clear();
        ports.add(portFoo2);
        ports.add(portBar1);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.setPorts(ports);
        anytime.add(portBar1Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Add portFoo1, portBar2 name and number collision
        // Also change p1a -> p1b: expect modify for it
        // Also add p3: expect add for it
        PortChangeEvent p1bUp = new PortChangeEvent(p1b, PortChangeType.UP);
        PortChangeEvent p3Add = new PortChangeEvent(p3, PortChangeType.ADD);
        ports.clear();
        ports.add(portFoo1);
        ports.add(portBar2);
        ports.add(p1b);
        ports.add(p3);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.setPorts(ports);
        early.add(portFoo2Del);
        early.add(portBar1Del);
        late.add(portFoo1Add);
        late.add(portBar2Add);
        anytime.add(p1bUp);
        anytime.add(p3Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
    }


    @Test
    public void testPortStatusNameNumberMappingChange() {
        List<ImmutablePort> ports = new ArrayList<ImmutablePort>();
        Collection<PortChangeEvent> early = new ArrayList<PortChangeEvent>();
        Collection<PortChangeEvent> late = new ArrayList<PortChangeEvent>();
        Collection<PortChangeEvent> anytime = new ArrayList<PortChangeEvent>();
        Collection<PortChangeEvent> actualChanges = null;

        // init: add portFoo1, p1a
        ports.add(portFoo1);
        ports.add(p1a);
        sw.setPorts(ports);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        OFPortStatus ps = (OFPortStatus)
                BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);

        // portFoo1 -> portFoo2 via MODIFY : name collision
        ps.setReason(OFPortReason.OFPPR_MODIFY.getReasonCode());
        ps.setDesc(portFoo2.toOFPhysicalPort());
        ports.clear();
        ports.add(portFoo2);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.processOFPortStatus(ps);
        early.add(portFoo1Del);
        late.add(portFoo2Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // portFoo2 -> portBar2 via ADD number collision
        ps.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
        ps.setDesc(portBar2.toOFPhysicalPort());
        ports.clear();
        ports.add(portBar2);
        ports.add(p1a);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.processOFPortStatus(ps);
        early.add(portFoo2Del);
        late.add(portBar2Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Set to portFoo1, portBar2
        ports.clear();
        ports.add(portFoo1);
        ports.add(portBar2);
        sw.setPorts(ports);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // portFoo1 + portBar2 -> portFoo2: name and number collision
        ps.setReason(OFPortReason.OFPPR_MODIFY.getReasonCode());
        ps.setDesc(portFoo2.toOFPhysicalPort());
        ports.clear();
        ports.add(portFoo2);
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.processOFPortStatus(ps);
        early.add(portFoo1Del);
        early.add(portBar2Del);
        late.add(portFoo2Add);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        //----------------------
        // Test DELETEs

        // del portFoo1: name exists (portFoo2), but number doesn't.
        ps.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
        ps.setDesc(portFoo1.toOFPhysicalPort());
        ports.clear();
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.processOFPortStatus(ps);
        anytime.add(portFoo2Del);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // Set to portFoo1
        ports.clear();
        ports.add(portFoo1);
        sw.setPorts(ports);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // del portBar1: number exists (portFoo1), but name doesn't.
        ps.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
        ps.setDesc(portBar1.toOFPhysicalPort());
        ports.clear();
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.processOFPortStatus(ps);
        anytime.add(portFoo1Del);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());


        // Set to portFoo1, portBar2
        ports.clear();
        ports.add(portFoo1);
        ports.add(portBar2);
        sw.setPorts(ports);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());

        // del portFoo2: name and number exists
        ps.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
        ps.setDesc(portFoo2.toOFPhysicalPort());
        ports.clear();
        early.clear();
        late.clear();
        anytime.clear();
        actualChanges = sw.processOFPortStatus(ps);
        anytime.add(portFoo1Del);
        anytime.add(portBar2Del);
        assertChangeEvents(early, late, anytime, actualChanges);
        assertCollectionEqualsNoOrder(ports, sw.getPorts());
    }

    @Test
    public void testSubHandshake() {
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.VENDOR);
        // test execptions before handshake is started
        try {
            sw.processDriverHandshakeMessage(m);
            fail("expected exception not thrown");
        } catch (SwitchDriverSubHandshakeNotStarted e) { /* expected */ }
        try {
            sw.isDriverHandshakeComplete();
            fail("expected exception not thrown");
        } catch (SwitchDriverSubHandshakeNotStarted e) { /* expected */ }

        // start the handshake -- it should immediately complete
        sw.startDriverHandshake();
        assertTrue("Handshake should be complete",
                   sw.isDriverHandshakeComplete());

        // test exceptions after handshake is completed
        try {
            sw.processDriverHandshakeMessage(m);
            fail("expected exception not thrown");
        } catch (SwitchDriverSubHandshakeCompleted e) { /* expected */ }
        try {
            sw.startDriverHandshake();
            fail("Expected exception not thrown");
        } catch (SwitchDriverSubHandshakeAlreadyStarted e) { /* expected */ }
    }

}
