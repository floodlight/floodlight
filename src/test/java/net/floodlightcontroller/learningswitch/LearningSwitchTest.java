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

import java.util.Arrays;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

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
    private   MockFloodlightProvider mockFloodlightProvider;
    private LearningSwitch learningSwitch;
    
    @Before
    public void setUp() {
        super.setUp();

        mockFloodlightProvider = getMockFloodlightProvider();
        learningSwitch = new LearningSwitch();
        learningSwitch.setFloodlightProvider(mockFloodlightProvider);
        mockFloodlightProvider.addOFMessageListener(OFType.PACKET_IN, learningSwitch);
        
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
        this.packetIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testPacketSerialized.length);

        // clear the MAC tables
        //getLearningSwitch().getMacTables().clear();
    }

    protected LearningSwitch getLearningSwitch() {
        return new LearningSwitch();
    }

    @Test
    public void testFlood() throws Exception {
        LearningSwitch learningSwitch = getLearningSwitch();
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();

        // build our expected flooded packetOut
        OFPacketOut po = new OFPacketOut()
            .setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue())}))
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
            .setBufferId(-1)
            .setInPort((short)1)
            .setPacketData(this.testPacketSerialized);
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLengthU()
                + this.testPacketSerialized.length);

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        mockSwitch.addToPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:44:33:22:11:00")), (short) 42, (short) 1);       
        expect(mockSwitch.getFromPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55")), (short) 42)).andReturn(null);        
        //expect(mockSwitch.getOutputStream()).andReturn(mockStream);
        mockSwitch.write(po, null);

        // Start recording the replay on the mocks       
        expect(mockSwitch.getFromPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:44:33:22:11:00")), (short) 42)).andReturn((short) 1);
        replay(mockSwitch);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn, parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations      
        short result = learningSwitch.getFromPortMap(mockSwitch, Ethernet.toLong(Ethernet.toMACAddress("00:44:33:22:11:00")), (short) 42).shortValue();
        verify(mockSwitch);

        // Verify the MAC table inside the switch
        assertEquals(1, result);
    }
    
    @Test
    public void testFlowMod() throws Exception {
        LearningSwitch learningSwitch = getLearningSwitch();
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();

        // tweak the test packet in since we need a bufferId
        this.packetIn.setBufferId(50);

        // build expected flow mods
        OFMessage fm1 = ((OFFlowMod) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD))
            .setActions(Arrays.asList(new OFAction[] {
                    new OFActionOutput().setPort((short) 2).setMaxLength((short) -1)}))
            .setBufferId(50)
            .setCommand(OFFlowMod.OFPFC_ADD)
            .setIdleTimeout((short) 5)
            .setMatch(new OFMatch()
                .loadFromPacket(testPacketSerialized, (short) 1)
                .setWildcards(OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST
                        | OFMatch.OFPFW_NW_TOS))
            .setOutPort(OFPort.OFPP_NONE.getValue())
            .setCookie(1L << 52)
            .setPriority((short) 100)
            .setFlags((short)(1 << 0))
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFMessage fm2 = ((OFFlowMod) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD))
            .setActions(Arrays.asList(new OFAction[] {
                    new OFActionOutput().setPort((short) 1).setMaxLength((short) -1)}))
            .setBufferId(-1)
            .setCommand(OFFlowMod.OFPFC_ADD)
            .setIdleTimeout((short) 5)
            .setMatch(new OFMatch()
                .loadFromPacket(testPacketReplySerialized, (short) 2)
                .setWildcards(OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST
                        | OFMatch.OFPFW_NW_TOS))
            .setOutPort(OFPort.OFPP_NONE.getValue())
            .setCookie(1L << 52)
            .setPriority((short) 100)
            .setFlags((short)(1 << 0))
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        mockSwitch.addToPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55")), (short) 42, (short) 2);
        mockSwitch.addToPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:44:33:22:11:00")), (short) 42, (short) 1);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getFromPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:44:33:22:11:00")), (short) 42)).andReturn((short) 1);
        expect(mockSwitch.getFromPortMap(Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55")), (short) 42)).andReturn((short) 2);
        expect(mockSwitch.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn((Integer) (OFMatch.OFPFW_IN_PORT | OFMatch.OFPFW_NW_PROTO
                | OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_NW_SRC_ALL
                | OFMatch.OFPFW_NW_DST_ALL | OFMatch.OFPFW_NW_TOS));
        //expect(mockSwitch.getOutputStream()).andReturn(mockStream);
        mockSwitch.write(fm1, null);
        //expect(mockSwitch.getOutputStream()).andReturn(mockStream);
        mockSwitch.write(fm2, null);

        // Start recording the replay on the mocks
        replay(mockSwitch);

        // Populate the MAC table
        learningSwitch.addToPortMap(mockSwitch,
                Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55")), (short) 42, (short) 2);
        
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn, parseAndAnnotate(this.packetIn));
        
        // Verify the replay matched our expectations
        short result = learningSwitch.getFromPortMap(mockSwitch, Ethernet.toLong(Ethernet.toMACAddress("00:44:33:22:11:00")), (short) 42).shortValue();
        verify(mockSwitch);

        // Verify the MAC table inside the switch
        assertEquals(1, result);
    }
}
