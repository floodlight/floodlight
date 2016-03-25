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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.MockDebugEventService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
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
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
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
        expect(mockSwitch.getId()).andReturn(DatapathId.of(id)).anyTimes();
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
        IDebugCounterService debugCounterService = new MockDebugCounterService();
        IDebugEventService debugEventService = new MockDebugEventService();
        MockThreadPoolService tp = new MockThreadPoolService();
        RestApiServer restApi = new RestApiServer();
        MemoryStorageSource storageService = new MemoryStorageSource();
        cntx.addService(IRestApiService.class, restApi);
        cntx.addService(IThreadPoolService.class, tp);
        cntx.addService(IRoutingService.class, routingEngine);
        cntx.addService(ILinkDiscoveryService.class, ldm);
        cntx.addService(ITopologyService.class, ldm);
        cntx.addService(IStorageSourceService.class, storageService);
        cntx.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        cntx.addService(IDebugCounterService.class, debugCounterService);
        cntx.addService(IDebugEventService.class, debugEventService);
        cntx.addService(IOFSwitchService.class, getMockSwitchService());
        restApi.init(cntx);
        tp.init(cntx);
        routingEngine.init(cntx);
        storageService.init(cntx);
        storageService.startUp(cntx);
        ldm.init(cntx);
        restApi.startUp(cntx);
        tp.startUp(cntx);
        routingEngine.startUp(cntx);
        ldm.startUp(cntx);

        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
        switches.put(DatapathId.of(1L), sw1);
        switches.put(DatapathId.of(2L), sw2);
        getMockSwitchService().setSwitches(switches);
        replay(sw1, sw2);
    }

    @Test
    public void testLinkLatency() throws Exception {
        LinkDiscoveryManager.LATENCY_HISTORY_SIZE = 5;
        LinkDiscoveryManager.LATENCY_UPDATE_THRESHOLD = 0.25;
        
        LinkInfo info = new LinkInfo(new Date(), new Date(), null);

        /*
         * Should retain initial latency until LATENCY_HISTORY_SIZE
         * data points are accumulated.
         */
        assertEquals(U64.of(0), info.addObservedLatency(U64.of(0)));
        assertEquals(U64.of(0), info.addObservedLatency(U64.of(10)));
        assertEquals(U64.of(0), info.addObservedLatency(U64.of(20)));
        assertEquals(U64.of(0), info.addObservedLatency(U64.of(30)));
        assertEquals(U64.of(20), info.addObservedLatency(U64.of(40)));
        
        /*
         * LATENCY_HISTORY_SIZE is maintained. Oldest value is evicted
         * per new value added. New average should be computed each
         * addition, but latency should not change until current latency
         * versus historical average latency differential threshold is
         * exceeded again.
         */
        assertEquals(U64.of(20), info.addObservedLatency(U64.of(20))); /* avg = 24; diff = 4; 4/24 = 1/6 = 17% !>= 25% --> no update */
        assertEquals(U64.of(26), info.addObservedLatency(U64.of(20))); /* avg = 26; diff = 6; 6/20 = 3/10 = 33% >= 25% --> update */
        assertEquals(U64.of(26), info.addObservedLatency(U64.of(20))); /* avg = 26; diff = 0; 0/20 = 0/10 = 0% !>= 25% --> no update */
    }
    
    @Test
    public void testAddOrUpdateLink() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        U64 latency = U64.of(100);
        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(2L), OFPort.of(1), latency);
        LinkInfo info = new LinkInfo(new Date(),
                                     new Date(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        NodePortTuple srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(2));
        NodePortTuple dstNpt = new NodePortTuple(DatapathId.of(2L), OFPort.of(1));

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.getPortLinks().get(srcNpt));
        assertTrue(linkDiscovery.getPortLinks().get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).iterator().next().getLatency().equals(latency));
    }

    @Test
    public void testDeleteLink() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(2L), OFPort.of(1), U64.ZERO);
        LinkInfo info = new LinkInfo(new Date(),
        		new Date(), null);
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

        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(2L), OFPort.of(3), U64.ZERO);
        NodePortTuple srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(2));
        NodePortTuple dstNpt = new NodePortTuple(DatapathId.of(2L), OFPort.of(3));

        LinkInfo info = new LinkInfo(new Date(),
        		new Date(), null);
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

        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(1L), OFPort.of(3), U64.ZERO);
        NodePortTuple srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(2));
        NodePortTuple dstNpt = new NodePortTuple(DatapathId.of(2L), OFPort.of(3));

        LinkInfo info = new LinkInfo(new Date(),
        		new Date(), null);
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

        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(2L), OFPort.of(1), U64.ZERO);
        NodePortTuple srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(2));
        NodePortTuple dstNpt = new NodePortTuple(DatapathId.of(2L), OFPort.of(1));
        LinkInfo info = new LinkInfo(new Date(),
        		new Date(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        IOFSwitch sw1 = getMockSwitchService().getSwitch(DatapathId.of(1L));
        IOFSwitch sw2 = getMockSwitchService().getSwitch(DatapathId.of(2L));
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
        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(1L), OFPort.of(3), U64.ZERO);
        LinkInfo info = new LinkInfo(new Date(),
                                     new Date(), null);
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

        Link lt = new Link(DatapathId.of(1L), OFPort.of(1), DatapathId.of(2L), OFPort.of(1), U64.ZERO);
        NodePortTuple srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(1));
        NodePortTuple dstNpt = new NodePortTuple(DatapathId.of(2L), OFPort.of(1));

        LinkInfo info;

        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            new Date(System.currentTimeMillis() - 40000), null);
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


        info = new LinkInfo(new Date(),/* firstseen */
                            null,/* unicast */
                            new Date());
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);


        // Add a link info based on info that woudld be obtained from unicast LLDP
        // Setting the unicast LLDP reception time to be 40 seconds old, so we can use
        // this to test timeout after this test.  Although the info is initialized
        // with LT_OPENFLOW_LINK, the link property should be changed to LT_NON_OPENFLOW
        // by the addOrUpdateLink method.
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            new Date(System.currentTimeMillis() - 40000), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Expect to timeout the unicast Valid Time, but not the multicast Valid time
        // So the link type should go back to non-openflow link.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);

        // Set the multicastValidTime to be old and see if that also times out.
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            null, new Date(System.currentTimeMillis() - 40000));
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);

        // Test again only with multicast LLDP
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            null, new Date(System.currentTimeMillis() - 40000));
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);

        // Call timeout and check if link is no longer present.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);

        // Start clean and see if loops are also added.
        lt = new Link(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(2), U64.ZERO);
        srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(1));
        dstNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(2));
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            null, new Date(System.currentTimeMillis() - 40000));
        linkDiscovery.addOrUpdateLink(lt, info);


        // Start clean and see if loops are also added.
        lt = new Link(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3), U64.ZERO);
        srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(1));
        dstNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(3));
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            null, new Date(System.currentTimeMillis() - 40000));
        linkDiscovery.addOrUpdateLink(lt, info);

        // Start clean and see if loops are also added.
        lt = new Link(DatapathId.of(1L), OFPort.of(4), DatapathId.of(1L), OFPort.of(5), U64.ZERO);
        srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(4));
        dstNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(5));
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            null, new Date(System.currentTimeMillis() - 40000));
        linkDiscovery.addOrUpdateLink(lt, info);

        // Start clean and see if loops are also added.
        lt = new Link(DatapathId.of(1L), OFPort.of(3), DatapathId.of(1L), OFPort.of(5), U64.ZERO);
        srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(3));
        dstNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(5));
        info = new LinkInfo(new Date(System.currentTimeMillis() - 40000),
                            null, new Date(System.currentTimeMillis() - 40000));
        linkDiscovery.addOrUpdateLink(lt, info);
    }

    @Test
    public void testHARoleChange() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        Link lt = new Link(DatapathId.of(1L), OFPort.of(2), DatapathId.of(2L), OFPort.of(1), U64.ZERO);
        NodePortTuple srcNpt = new NodePortTuple(DatapathId.of(1L), OFPort.of(2));
        NodePortTuple dstNpt = new NodePortTuple(DatapathId.of(2L), OFPort.of(1));
        LinkInfo info = new LinkInfo(new Date(),
        		new Date(), null);
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
        linkDiscovery.switchService = getMockSwitchService();
        Capture<OFMessage> wc;
        Set<OFPort> qPorts;
        OFPortDesc ofpp = OFFactories.getFactory(OFVersion.OF_13).buildPortDesc()
        .setName("eth4242")
        .setPortNo(OFPort.of(4242))
        .setHwAddr(MacAddress.of("5c:16:c7:00:00:01"))
        .setCurr(new HashSet<OFPortFeatures>()) // random
        .build();
        IOFSwitch sw1 = createMockSwitch(1L);

        // Set switch map in floodlightProvider.
        Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
        switches.put(DatapathId.of(1L), sw1);
        getMockSwitchService().setSwitches(switches);

        // Create the set of ports
        List<OFPort> ports = new ArrayList<OFPort>();
        for(short p=1; p<=20; ++p) {
            ports.add(OFPort.of(p));
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(OFPort.of(EasyMock.anyInt()))).andReturn(ofpp).anyTimes();
        expect(sw1.getOFFactory()).andReturn(OFFactories.getFactory(OFVersion.OF_13)).anyTimes();
        expect(sw1.getLatency()).andReturn(U64.ZERO).anyTimes();
        expect(sw1.write(capture(wc))).andReturn(true).anyTimes();
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
        .setEtherType(EthType.IPv4)
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
        pi = OFFactories.getFactory(OFVersion.OF_13).buildPacketIn()
                .setBufferId(OFBufferId.NO_BUFFER)
                .setData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .build();
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
        expect(mockSwitch.getId()).andReturn(DatapathId.of(1L)).anyTimes();
        replay(mockSwitch);

        /* TEST1: See basic packet flow */
        OFPacketIn pi;
        pi = createPacketIn(mac1, mac2, srcIp, dstIp, vlan);
        FloodlightContext cntx = new FloodlightContext();
        Ethernet eth = new Ethernet();
        eth.deserialize(pi.getData(), 0, pi.getData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        Command ret;
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.CONTINUE, ret);

        /* TEST2: Add mac1 to the ignore MAC list and see that the packet is
         * dropped
         */
        ldm.addMACToIgnoreList(MacAddress.of(mac1), 0);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);
        /* Verify that if we send a packet with another MAC it still works */
        pi = createPacketIn(mac2, mac3, srcIp, dstIp, vlan);
        cntx = new FloodlightContext();
        eth = new Ethernet();
        eth.deserialize(pi.getData(), 0, pi.getData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.CONTINUE, ret);

        /* TEST3: Add a MAC range and see if that is ignored */
        ldm.addMACToIgnoreList(MacAddress.of(mac2), 8);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);
        /* Send a packet with source MAC as mac3 and see that that is ignored
         * as well.
         */
        pi = createPacketIn(mac3, mac1, srcIp, dstIp, vlan);
        cntx = new FloodlightContext();
        eth = new Ethernet();
        eth.deserialize(pi.getData(), 0, pi.getData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);

        verify(mockSwitch);
    }
}