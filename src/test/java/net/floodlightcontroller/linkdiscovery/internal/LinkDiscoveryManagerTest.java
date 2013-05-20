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

package net.floodlightcontroller.linkdiscovery.internal;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.topology.TopologyManager;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class LinkDiscoveryManagerTest extends FloodlightTestCase {

    private TestLinkDiscoveryManager ldm;
    protected static Logger log = LoggerFactory.getLogger(LinkDiscoveryManagerTest.class);

    public class TestLinkDiscoveryManager extends LinkDiscoveryManager {
        public boolean isSendLLDPsCalled = false;
        public boolean isClearLinksCalled = false;

        @Override
        protected void discoverOnAllPorts() {
            isSendLLDPsCalled = true;
            super.discoverOnAllPorts();
        }

        public void reset() {
            isSendLLDPsCalled = false;
            isClearLinksCalled = false;
        }

        @Override
        protected void clearAllLinks() {
            isClearLinksCalled = true;
            super.clearAllLinks();
        }
    }

    public LinkDiscoveryManager getLinkDiscoveryManager() {
        return ldm;
    }

    private IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        return mockSwitch;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        FloodlightModuleContext cntx = new FloodlightModuleContext();
        ldm = new TestLinkDiscoveryManager();
        TopologyManager routingEngine = new TopologyManager();
        ldm.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
        MockThreadPoolService tp = new MockThreadPoolService();
        RestApiServer restApi = new RestApiServer();
        cntx.addService(IRestApiService.class, restApi);
        cntx.addService(IThreadPoolService.class, tp);
        cntx.addService(IRoutingService.class, routingEngine);
        cntx.addService(ILinkDiscoveryService.class, ldm);
        cntx.addService(ITopologyService.class, ldm);
        cntx.addService(IStorageSourceService.class, new MemoryStorageSource());
        cntx.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        restApi.init(cntx);
        tp.init(cntx);
        routingEngine.init(cntx);
        ldm.init(cntx);
        restApi.startUp(cntx);
        tp.startUp(cntx);
        routingEngine.startUp(cntx);
        ldm.startUp(cntx);

        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        getMockFloodlightProvider().setSwitches(switches);
        replay(sw1, sw2);
    }

    @Test
    public void testAddOrUpdateLink() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);


        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
    }

    @Test
    public void testDeleteLink() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.deleteLinks(Collections.singletonList(lt), "Test");

        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.switchLinks.get(lt.getDst()));
        assertNull(linkDiscovery.portLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.portLinks.get(lt.getDst()));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testAddOrUpdateLinkToSelf() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 3);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 3);

        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
    }

    @Test
    public void testDeleteLinkToSelf() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 1L, 3);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 3);

        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.deleteLinks(Collections.singletonList(lt), "Test to self");

        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.switchLinks.get(lt.getDst()));
        assertNull(linkDiscovery.portLinks.get(srcNpt));
        assertNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testRemovedSwitch() {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 1);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        IOFSwitch sw1 = getMockFloodlightProvider().getSwitch(1L);
        IOFSwitch sw2 = getMockFloodlightProvider().getSwitch(2L);
        // Mock up our expected behavior
        linkDiscovery.switchRemoved(sw1.getId());
        verify(sw1, sw2);

        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.switchLinks.get(lt.getDst()));
        assertNull(linkDiscovery.portLinks.get(srcNpt));
        assertNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testRemovedSwitchSelf() {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        IOFSwitch sw1 = createMockSwitch(1L);
        replay(sw1);
        Link lt = new Link(1L, 2, 1L, 3);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Mock up our expected behavior
        linkDiscovery.switchRemoved(sw1.getId());

        verify(sw1);
        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.portLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.portLinks.get(lt.getDst()));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testAddUpdateLinks() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 1, 2L, 1);
        NodePortTuple srcNpt = new NodePortTuple(1L, 1);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);

        LinkInfo info;

        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            System.currentTimeMillis() - 40000, null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));

        linkDiscovery.timeoutLinks();


        info = new LinkInfo(System.currentTimeMillis(),/* firstseen */
                            null,/* unicast */
                            System.currentTimeMillis());
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);


        // Add a link info based on info that woudld be obtained from unicast LLDP
        // Setting the unicast LLDP reception time to be 40 seconds old, so we can use
        // this to test timeout after this test.  Although the info is initialized
        // with LT_OPENFLOW_LINK, the link property should be changed to LT_NON_OPENFLOW
        // by the addOrUpdateLink method.
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            System.currentTimeMillis() - 40000, null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Expect to timeout the unicast Valid Time, but not the multicast Valid time
        // So the link type should go back to non-openflow link.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);

        // Set the multicastValidTime to be old and see if that also times out.
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);

        // Test again only with multicast LLDP
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);

        // Call timeout and check if link is no longer present.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 1, 1L, 2);
        srcNpt = new NodePortTuple(1L, 1);
        dstNpt = new NodePortTuple(1L, 2);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);


        // Start clean and see if loops are also added.
        lt = new Link(1L, 1, 1L, 3);
        srcNpt = new NodePortTuple(1L, 1);
        dstNpt = new NodePortTuple(1L, 3);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 4, 1L, 5);
        srcNpt = new NodePortTuple(1L, 4);
        dstNpt = new NodePortTuple(1L, 5);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 3, 1L, 5);
        srcNpt = new NodePortTuple(1L, 3);
        dstNpt = new NodePortTuple(1L, 5);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);
    }

    @Test
    public void testHARoleChange() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        Link lt = new Link(1L, 2, 2L, 1);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));

        /* FIXME: what's the right thing to do here:
        // check that it clears from memory
        getMockFloodlightProvider().dispatchRoleChanged(Role.SLAVE);
        assertTrue(linkDiscovery.switchLinks.isEmpty());
        getMockFloodlightProvider().dispatchRoleChanged(Role.MASTER);
        // check that lldps were sent
        assertTrue(ldm.isSendLLDPsCalled);
        assertTrue(ldm.isClearLinksCalled);
        ldm.reset();
        */
    }

    @Test
    public void testSwitchAdded() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort ofpp = new OFPhysicalPort();
        ofpp.setName("eth4242");
        ofpp.setPortNumber((short)4242);
        ofpp.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        ofpp.setCurrentFeatures(0);
        ImmutablePort p1 = ImmutablePort.fromOFPhysicalPort(ofpp);
        IOFSwitch sw1 = createMockSwitch(1L);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=20; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        replay(sw1);

        linkDiscovery.switchActivated(sw1.getId());
        verify(sw1);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }

    private OFPacketIn createPacketIn(String srcMAC, String dstMAC,
                                      String srcIp, String dstIp, short vlan) {
        IPacket testPacket = new Ethernet()
        .setDestinationMACAddress(dstMAC)
        .setSourceMACAddress(srcMAC)
        .setVlanID(vlan)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress(srcIp)
                .setDestinationAddress(dstIp)
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        byte[] testPacketSerialized = testPacket.serialize();
        OFPacketIn pi;
        // build out input packet
        pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);
        return pi;
    }

    @Test
    public void testIgnoreSrcMAC() throws Exception {
        String mac1 = "00:11:22:33:44:55";
        String mac2 = "00:44:33:22:11:00";
        String mac3 = "00:44:33:22:11:02";
        String srcIp = "192.168.1.1";
        String dstIp = "192.168.1.2";
        short vlan = 42;

        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        replay(mockSwitch);

        /* TEST1: See basic packet flow */
        OFPacketIn pi;
        pi = createPacketIn(mac1, mac2, srcIp, dstIp, vlan);
        FloodlightContext cntx = new FloodlightContext();
        Ethernet eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        Command ret;
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.CONTINUE, ret);

        /* TEST2: Add mac1 to the ignore MAC list and see that the packet is
         * dropped
         */
        ldm.addMACToIgnoreList(HexString.toLong(mac1), 0);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);
        /* Verify that if we send a packet with another MAC it still works */
        pi = createPacketIn(mac2, mac3, srcIp, dstIp, vlan);
        cntx = new FloodlightContext();
        eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.CONTINUE, ret);

        /* TEST3: Add a MAC range and see if that is ignored */
        ldm.addMACToIgnoreList(HexString.toLong(mac2), 8);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);
        /* Send a packet with source MAC as mac3 and see that that is ignored
         * as well.
         */
        pi = createPacketIn(mac3, mac1, srcIp, dstIp, vlan);
        cntx = new FloodlightContext();
        eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);

        verify(mockSwitch);
    }
}