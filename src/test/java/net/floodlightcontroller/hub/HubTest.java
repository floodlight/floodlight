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

package net.floodlightcontroller.hub;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.capture;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.util.OFMessageUtils;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class HubTest extends FloodlightTestCase {
    protected OFPacketIn packetIn;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    private MockFloodlightProvider mockFloodlightProvider;
    private Hub hub;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mockFloodlightProvider = getMockFloodlightProvider();
        hub = new Hub();
        mockFloodlightProvider.addOFMessageListener(OFType.PACKET_IN, hub);
        hub.setFloodlightProvider(mockFloodlightProvider);
        
        // Build our test packet
        this.testPacket = new Ethernet()
            .setDestinationMACAddress("00:11:22:33:44:55")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setEtherType(EthType.IPv4)
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

        // Build the PacketIn
        this.packetIn = (OFPacketIn) OFFactories.getFactory(OFVersion.OF_13).buildPacketIn()
            .setBufferId(OFBufferId.NO_BUFFER)
            .setMatch(OFFactories.getFactory(OFVersion.OF_13).buildMatch()
            		.setExact(MatchField.IN_PORT, OFPort.of(1))
            		.build())
            .setData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLen((short) this.testPacketSerialized.length).build();
    }

    @Test
    public void testFloodNoBufferId() throws Exception {
        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        EasyMock.expect(mockSwitch.getOFFactory()).andReturn(OFFactories.getFactory(OFVersion.OF_13)).anyTimes();
        
        // build our expected flooded packetOut
    	OFActionOutput ao = OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput().setPort(OFPort.FLOOD).build();
    	List<OFAction> al = new ArrayList<OFAction>();
    	al.add(ao);
        OFPacketOut po = OFFactories.getFactory(OFVersion.OF_13).buildPacketOut()
            .setActions(al)
            .setBufferId(OFBufferId.NO_BUFFER)
            .setXid(1)
            .setInPort(OFPort.of(1))
            .setData(this.testPacketSerialized).build();
        
        Capture<OFMessage> wc1 = EasyMock.newCapture(CaptureType.ALL);
        
        expect(mockSwitch.write(capture(wc1))).andReturn(true).anyTimes();

        // Start recording the replay on the mocks
        replay(mockSwitch);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn,
                         parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations
        verify(mockSwitch);
        
        assertTrue(wc1.hasCaptured());
        OFMessage m = wc1.getValue();
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(m), OFMessageUtils.OFMessageIgnoreXid.of(po));
    }

    @Test
    public void testFloodBufferId() throws Exception {
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        this.packetIn = this.packetIn.createBuilder()
        		.setBufferId(OFBufferId.of(10))
        		.setXid(1)
        		.build();

        OFActionOutput ao = OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput().setPort(OFPort.FLOOD).build();
    	List<OFAction> al = new ArrayList<OFAction>();
    	al.add(ao);
        // build our expected flooded packetOut
        OFPacketOut po = OFFactories.getFactory(OFVersion.OF_13).buildPacketOut()
        	.setActions(al)
            .setXid(1)
            .setBufferId(OFBufferId.of(10))
            .setInPort(OFPort.of(1))
            .build();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        EasyMock.expect(mockSwitch.getOFFactory()).andReturn(OFFactories.getFactory(OFVersion.OF_13)).anyTimes();
        Capture<OFPacketOut> wc1 = EasyMock.newCapture(CaptureType.ALL);
        expect(mockSwitch.write(capture(wc1))).andReturn(true).anyTimes();

        // Start recording the replay on the mocks
        replay(mockSwitch);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn,
                         parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations
        verify(mockSwitch);
        
        assertTrue(wc1.hasCaptured());
        OFMessage m = wc1.getValue();
        assertEquals(po, m);
    }
}
