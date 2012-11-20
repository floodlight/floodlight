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

import static org.easymock.EasyMock.*;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
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

    public IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        return mockSwitch;
    }

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
                                     System.currentTimeMillis(), null,
                                     0, 0);
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
                                     System.currentTimeMillis(), null,
                                     0, 0);
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
                                     System.currentTimeMillis(), null,
                                     0, 0);
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
                                     System.currentTimeMillis(), null,
                                     0, 0);
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
                                     System.currentTimeMillis(), null,
                                     0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);

        IOFSwitch sw1 = getMockFloodlightProvider().getSwitches().get(1L);
        IOFSwitch sw2 = getMockFloodlightProvider().getSwitches().get(2L);
        // Mock up our expected behavior
        linkDiscovery.removedSwitch(sw1);
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
                                     System.currentTimeMillis(), null,
                                     0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Mock up our expected behavior
        linkDiscovery.removedSwitch(sw1);

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
                            System.currentTimeMillis() - 40000, null,
                                     0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt) == false);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt) == false);

        linkDiscovery.timeoutLinks();


        info = new LinkInfo(System.currentTimeMillis(),/* firstseen */
                            null,/* unicast */
                            System.currentTimeMillis(), 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));


        // Add a link info based on info that woudld be obtained from unicast LLDP
        // Setting the unicast LLDP reception time to be 40 seconds old, so we can use
        // this to test timeout after this test.  Although the info is initialized
        // with LT_OPENFLOW_LINK, the link property should be changed to LT_NON_OPENFLOW
        // by the addOrUpdateLink method.
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            System.currentTimeMillis() - 40000, null, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt) == false);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt) == false);

        // Expect to timeout the unicast Valid Time, but not the multicast Valid time
        // So the link type should go back to non-openflow link.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));

        // Set the multicastValidTime to be old and see if that also times out.
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt) == false);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt) == false);


        // Test again only with multicast LLDP
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));

        // Call timeout and check if link is no longer present.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt) == false);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt) == null ||
                linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt) == false);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 1, 1L, 2);
        srcNpt = new NodePortTuple(1L, 1);
        dstNpt = new NodePortTuple(1L, 2);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));


        // Start clean and see if loops are also added.
        lt = new Link(1L, 1, 1L, 3);
        srcNpt = new NodePortTuple(1L, 1);
        dstNpt = new NodePortTuple(1L, 3);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));


        // Start clean and see if loops are also added.
        lt = new Link(1L, 4, 1L, 5);
        srcNpt = new NodePortTuple(1L, 4);
        dstNpt = new NodePortTuple(1L, 5);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));


        // Start clean and see if loops are also added.
        lt = new Link(1L, 3, 1L, 5);
        srcNpt = new NodePortTuple(1L, 3);
        dstNpt = new NodePortTuple(1L, 5);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000, 0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(srcNpt).contains(lt));
        assertTrue(linkDiscovery.portBroadcastDomainLinks.get(dstNpt).contains(lt));
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
                                     System.currentTimeMillis(), null,
                                     0, 0);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
        
        // check that it clears from memory
        getMockFloodlightProvider().dispatchRoleChanged(null, Role.SLAVE);
        assertTrue(linkDiscovery.switchLinks.isEmpty());
        getMockFloodlightProvider().dispatchRoleChanged(Role.SLAVE, Role.MASTER);
        // check that lldps were sent
        assertTrue(ldm.isSendLLDPsCalled);
        assertTrue(ldm.isClearLinksCalled);
        ldm.reset();
    }

    @Test
    public void testSwitchAdded() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1.setCurrentFeatures(0);
        IOFSwitch sw1 = createMockSwitch(1L);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=10; ++p) {
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

        linkDiscovery.addedSwitch(sw1);
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
}