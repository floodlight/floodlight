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

package net.floodlightcontroller.learningswitch;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.util.Arrays;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class LearningSwitchTest extends FloodlightTestCase {
    protected OFPacketIn packetIn;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    protected IPacket broadcastPacket;
    protected byte[] broadcastPacketSerialized;
    protected IPacket testPacketReply;
    protected byte[] testPacketReplySerialized;
    private LearningSwitch learningSwitch;
    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Build our test packet
        this.testPacket = new Ethernet()
            .setDestinationMACAddress("00:11:22:33:44:55")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();
        // Build a broadcast packet
        this.broadcastPacket = new Ethernet()
            .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.255.255")
                .setPayload(new UDP()
                        .setSourcePort((short) 5000)
                        .setDestinationPort((short) 5001)
                        .setPayload(new Data(new byte[] {0x01}))));

        this.broadcastPacketSerialized = broadcastPacket.serialize();
        this.testPacketReply = new Ethernet()
            .setDestinationMACAddress("00:44:33:22:11:00")
            .setSourceMACAddress("00:11:22:33:44:55")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                    new IPv4()
                    .setTtl((byte) 128)
                    .setSourceAddress("192.168.1.2")
                    .setDestinationAddress("192.168.1.1")
                    .setPayload(new UDP()
                    .setSourcePort((short) 5001)
                    .setDestinationPort((short) 5000)
                    .setPayload(new Data(new byte[] {0x02}))));
        this.testPacketReplySerialized = testPacketReply.serialize();

        // Build the PacketIn
        this.packetIn = factory.buildPacketIn()
            .setBufferId(OFBufferId.NO_BUFFER)
            .setData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .build();
        
        this.learningSwitch = new LearningSwitch();
        this.mockFloodlightProvider.addOFMessageListener(OFType.PACKET_IN, learningSwitch);

    }

    @Test
    public void testFlood() throws Exception {
        // build our expected flooded packetOut
        OFPacketOut po = factory.buildPacketOut()
            .setActions(Arrays.asList((OFAction)factory.actions().output(OFPort.FLOOD, Integer.MAX_VALUE)))
            .setBufferId(OFBufferId.NO_BUFFER)
            .setData(this.testPacketSerialized)
	        .build();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(DatapathId.of("00:11:22:33:44:55:66:77")).anyTimes();
        mockSwitch.write(po, null);

        // Start recording the replay on the mocks
        replay(mockSwitch);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        // Make sure it's the right listener
        listener.receive(mockSwitch, this.packetIn, parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations
        OFPort result = learningSwitch.getFromPortMap(mockSwitch, MacAddress.of("00:44:33:22:11:00"), VlanVid.ofVlan(42));
        verify(mockSwitch);

        // Verify the MAC table inside the switch
        assertEquals(1, result);
    }

    @Test
    public void testFlowMod() throws Exception {
        // tweak the test packet in since we need a bufferId
        this.packetIn = packetIn.createBuilder().setBufferId(OFBufferId.of(50)).build();

        // build expected flow mods
        OFMessage fm1 = factory.buildFlowAdd()
            .setActions(Arrays.asList((OFAction)factory.actions().output(OFPort.FLOOD, -1)))
            .setBufferId(OFBufferId.NO_BUFFER)
            .setIdleTimeout((short) 5)
            .setMatch(((OFPacketIn) testPacket).getMatch())
            .setOutPort(OFPort.ANY)
            .setCookie(U64.of(1L << 52))
            .setPriority((short) 100)
            .build();
        OFMessage fm2 = factory.buildFlowAdd()
            .setActions(Arrays.asList((OFAction)factory.actions().output(OFPort.of(1), -1)))
            .setBufferId(OFBufferId.NO_BUFFER)
            .setIdleTimeout((short) 5)
            .setMatch(((OFPacketIn) testPacketReply).getMatch())
            .setOutPort(OFPort.ANY)
            .setCookie(U64.of(1L << 52))
            .setPriority((short) 100)
            .build();

        OFActionOutput ofAcOut = factory.actions().output(OFPort.of(2), -1);

        OFPacketOut packetOut = factory.buildPacketOut()
        .setActions(Arrays.asList((OFAction)ofAcOut))
        .setBufferId(OFBufferId.of(50))
        .setInPort(OFPort.of(1))
        .build();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(DatapathId.of(1L)).anyTimes();
        expect(mockSwitch.getBuffers()).andReturn((long)100).anyTimes();
        mockSwitch.write(packetOut, null);
        mockSwitch.write(fm1, null);
        mockSwitch.write(fm2, null);

        // Start recording the replay on the mocks
        replay(mockSwitch);

        // Populate the MAC table
        learningSwitch.addToPortMap(mockSwitch,
                MacAddress.of("00:11:22:33:44:55"), VlanVid.ofVlan(42), OFPort.of(2));

        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn, parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations
        OFPort result = learningSwitch.getFromPortMap(mockSwitch, MacAddress.of("00:44:33:22:11:00"), VlanVid.ofVlan(42));
        verify(mockSwitch);

        // Verify the MAC table inside the switch
        assertEquals(1, result);
    }
}
