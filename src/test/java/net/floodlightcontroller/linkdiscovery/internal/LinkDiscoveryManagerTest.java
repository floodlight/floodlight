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

import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.LinkTuple;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.TopologyManager;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class LinkDiscoveryManagerTest extends FloodlightTestCase {

    private LinkDiscoveryManager topology;
    protected static Logger log = LoggerFactory.getLogger(LinkDiscoveryManagerTest.class);
    
    public LinkDiscoveryManager getTopology() {
        return topology;
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
        topology = new LinkDiscoveryManager();
        TopologyManager routingEngine = new TopologyManager();
        topology.topologyAware = new ArrayList<ITopologyListener>();
        topology.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
        MockThreadPoolService tp = new MockThreadPoolService();
        cntx.addService(IThreadPoolService.class, tp);
        cntx.addService(IRoutingService.class, routingEngine);
        cntx.addService(ILinkDiscoveryService.class, topology);
        cntx.addService(ITopologyService.class, topology);
        cntx.addService(IStorageSourceService.class, new MemoryStorageSource());
        cntx.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        tp.init(cntx);
        routingEngine.init(cntx);
        topology.init(cntx);
        tp.startUp(cntx);
        routingEngine.startUp(cntx);
        topology.startUp(cntx);
    }

    @Test
    public void testAddOrUpdateLink() throws Exception {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw2, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(), null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertTrue(topology.switchLinks.get(lt.getSrc().getSw()).contains(lt));
        assertNotNull(topology.portLinks.get(lt.getSrc()));
        assertTrue(topology.portLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.portLinks.get(lt.getDst()).contains(lt));
        assertTrue(topology.links.containsKey(lt));
    }

    @Test
    public void testDeleteLink() throws Exception {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw2, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(), null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);
        topology.deleteLinks(Collections.singletonList(lt), "Test");

        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.switchLinks.get(lt.getDst().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }

    @Test
    public void testAddOrUpdateLinkToSelf() throws Exception {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw1, 3);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(), null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertTrue(topology.switchLinks.get(lt.getSrc().getSw()).contains(lt));
        assertNotNull(topology.portLinks.get(lt.getSrc()));
        assertTrue(topology.portLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.portLinks.get(lt.getDst()).contains(lt));
        assertTrue(topology.links.containsKey(lt));
    }

    @Test
    public void testDeleteLinkToSelf() throws Exception {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        replay(sw1);
        LinkTuple lt = new LinkTuple(sw1, 2, sw1, 3);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(), null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);
        topology.deleteLinks(Collections.singletonList(lt), "Test to self");

        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.switchLinks.get(lt.getDst().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }

    @Test
    public void testRemovedSwitch() {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw2, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(), null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);

        // Mock up our expected behavior
        topology.removedSwitch(sw1);

        verify(sw1, sw2);
        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.switchLinks.get(lt.getDst().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }

    @Test
    public void testRemovedSwitchSelf() {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        replay(sw1);
        LinkTuple lt = new LinkTuple(sw1, 2, sw1, 3);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(), null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);

        // Mock up our expected behavior
        topology.removedSwitch(sw1);

        verify(sw1);
        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }

    @Test
    public void testAddUpdateLinks() throws Exception {
        LinkDiscoveryManager topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        //expect(topology.getSwitchClusterId(1L)).andReturn(1L).anyTimes();
        //expect(topology.getSwitchClusterId(2L)).andReturn(1L).anyTimes();
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 1, sw2, 1);
        LinkInfo info;

        info = new LinkInfo(System.currentTimeMillis() - 40000, null,
                                     0, 0);
        topology.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertTrue(topology.switchLinks.get(lt.getSrc().getSw()).contains(lt));
        assertNotNull(topology.portLinks.get(lt.getSrc()));
        assertTrue(topology.portLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.portLinks.get(lt.getDst()).contains(lt));
        assertTrue(topology.links.containsKey(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt) == false);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt) == false);

        topology.timeoutLinks();


        info = new LinkInfo(null, System.currentTimeMillis(), 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.links.get(lt).getUnicastValidTime() == null);
        assertTrue(topology.links.get(lt).getMulticastValidTime() != null);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));


        // Add a link info based on info that woudld be obtained from unicast LLDP
        // Setting the unicast LLDP reception time to be 40 seconds old, so we can use
        // this to test timeout after this test.  Although the info is initialized
        // with LT_OPENFLOW_LINK, the link property should be changed to LT_NON_OPENFLOW
        // by the addOrUpdateLink method.
        info = new LinkInfo(System.currentTimeMillis() - 40000, null, 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt) == false);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt) == false);

        // Expect to timeout the unicast Valid Time, but not the multicast Valid time
        // So the link type should go back to non-openflow link.
        topology.timeoutLinks();
        assertTrue(topology.links.get(lt).getUnicastValidTime() == null);
        assertTrue(topology.links.get(lt).getMulticastValidTime() != null);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));

        // Set the multicastValidTime to be old and see if that also times out.
        info = new LinkInfo(null, System.currentTimeMillis() - 40000, 0, 0);
        topology.addOrUpdateLink(lt, info);
        topology.timeoutLinks();
        assertTrue(topology.links.get(lt) == null);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt) == false);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt) == false);


        // Test again only with multicast LLDP
        info = new LinkInfo(null, System.currentTimeMillis() - 40000, 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.links.get(lt).getUnicastValidTime() == null);
        assertTrue(topology.links.get(lt).getMulticastValidTime() != null);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));

        // Call timeout and check if link is no longer present.
        topology.timeoutLinks();
        assertTrue(topology.links.get(lt) == null);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt) == false);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()) == null ||
                topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt) == false);

        // Start clean and see if loops are also added.
        lt = new LinkTuple(sw1, 1, sw1, 2);
        info = new LinkInfo(null, System.currentTimeMillis() - 40000, 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));


        // Start clean and see if loops are also added.
        lt = new LinkTuple(sw1, 1, sw1, 3);
        info = new LinkInfo(null, System.currentTimeMillis() - 40000, 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));


        // Start clean and see if loops are also added.
        lt = new LinkTuple(sw1, 4, sw1, 5);
        info = new LinkInfo(null, System.currentTimeMillis() - 40000, 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));


        // Start clean and see if loops are also added.
        lt = new LinkTuple(sw1, 3, sw1, 5);
        info = new LinkInfo(null, System.currentTimeMillis() - 40000, 0, 0);
        topology.addOrUpdateLink(lt, info);
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getSrc()).contains(lt));
        assertTrue(topology.portBroadcastDomainLinks.get(lt.getDst()).contains(lt));
    }

}
