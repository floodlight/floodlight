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

package net.floodlightcontroller.topology.internal;

import static org.easymock.EasyMock.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;

import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPhysicalPort;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.dijkstra.RoutingImpl;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.topology.ITopologyAware;
import net.floodlightcontroller.topology.LinkInfo;
import net.floodlightcontroller.topology.LinkTuple;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class TopologyImplTest extends FloodlightTestCase {
    private TopologyImpl topology;

    public TopologyImpl getTopology() {
        return topology;
    }

    public IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        return mockSwitch;
    }

    @Before
    public void setUp() {
        super.setUp();
        topology = new TopologyImpl();
        topology.setFloodlightProvider(getMockFloodlightProvider());
        topology.setStorageSource(new MemoryStorageSource());
        RoutingImpl routingEngine = new RoutingImpl();
        topology.setRoutingEngine(routingEngine);
        HashSet<ITopologyAware> topologyAware = new HashSet<ITopologyAware>();
        topologyAware.add(routingEngine);
        topology.setTopologyAware(topologyAware);
        topology.startUp();
    }

    @Test
    public void testAddOrUpdateLink() throws Exception {
        TopologyImpl topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw2, 1);
        topology.addOrUpdateLink(lt, 0, 0);

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
        TopologyImpl topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw2, 1);
        topology.addOrUpdateLink(lt, 0, 0);
        topology.deleteLinks(Collections.singletonList(lt));

        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.switchLinks.get(lt.getDst().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }

    @Test
    public void testAddOrUpdateLinkToSelf() throws Exception {
        TopologyImpl topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw1, 3);
        topology.addOrUpdateLink(lt, 0, 0);

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
        TopologyImpl topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        replay(sw1);
        LinkTuple lt = new LinkTuple(sw1, 2, sw1, 3);
        topology.addOrUpdateLink(lt, 0, 0);
        topology.deleteLinks(Collections.singletonList(lt));

        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.switchLinks.get(lt.getDst().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }

    @Test
    public void testRemovedSwitch() {
        TopologyImpl topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        LinkTuple lt = new LinkTuple(sw1, 2, sw2, 1);
        topology.addOrUpdateLink(lt, 0, 0);

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
        TopologyImpl topology = getTopology();
        IOFSwitch sw1 = createMockSwitch(1L);
        replay(sw1);
        LinkTuple lt = new LinkTuple(sw1, 2, sw1, 3);
        topology.addOrUpdateLink(lt, 0, 0);

        // Mock up our expected behavior
        topology.removedSwitch(sw1);

        verify(sw1);
        // check invariants hold
        assertNull(topology.switchLinks.get(lt.getSrc().getSw()));
        assertNull(topology.portLinks.get(lt.getSrc()));
        assertNull(topology.portLinks.get(lt.getDst()));
        assertTrue(topology.links.isEmpty());
    }
    
    private void createLinks(TopologyImpl topology, IOFSwitch[] switches, int[][] linkInfoArray) {
        for (int i = 0; i < linkInfoArray.length; i++) {
            int[] linkInfo = linkInfoArray[i];
            LinkTuple lt = new LinkTuple(switches[linkInfo[0]-1], linkInfo[1], switches[linkInfo[3]-1], linkInfo[4]);
            topology.addOrUpdateLink(lt, linkInfo[2], linkInfo[5]);
        }
    }
    
    private void verifyClusters(TopologyImpl topology, IOFSwitch[] switches, int[][] clusters) {
        // Keep track of which switches we've already checked for cluster membership
        List<IOFSwitch> verifiedSwitches = new ArrayList<IOFSwitch>();
        
        // Make sure the expected cluster arrays are sorted so we can
        // use binarySearch to test for membership
        for (int i = 0; i < clusters.length; i++)
            Arrays.sort(clusters[i]);
        
        for (int i = 0; i < switches.length; i++) {
            IOFSwitch sw = switches[i];
            if (!verifiedSwitches.contains(sw)) {
                long id = sw.getId();
                int[] expectedCluster = null;
                
                for (int j = 0; j < clusters.length; j++) {
                    if (Arrays.binarySearch(clusters[j], (int)id) >= 0) {
                        expectedCluster = clusters[j];
                        break;
                    }
                }
                if (expectedCluster != null) {
                    Set<IOFSwitch> cluster = topology.getSwitchesInCluster(sw);
                    assertEquals(expectedCluster.length, cluster.size());
                    for (IOFSwitch sw2: cluster) {
                        long id2 = sw2.getId();
                        assertTrue(Arrays.binarySearch(expectedCluster, (int)id2) >= 0);
                        verifiedSwitches.add(sw2);
                    }
                }
            }
        }
    }
    
    @Test
    public void testCluster() {
        
        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |2  3  4|
        //      +-------+       +-----+-------+
        //          |           |         |   |
        //          |           |         |   |
        //      +-------+-------+         |   |
        //      |   1  2|                 |   |
        //      |   3   |                 |   |
        //      |   3   |                 |   |
        //      +-------+                 |   |
        //          |                     |   |
        //          |                     |   |
        //      +-------+-----------------+   |             
        //      |   1  2|                     |
        //      |   4   |                     |
        //      |   3   |      +--------------+
        //      +-------+      |
        //          |          |         
        //          |          |         
        //      +-------+------+      +-------+
        //      |   1  2|             |       |
        //      |   5  3|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+
        
        TopologyImpl topology = getTopology();
        
        // Create several switches
        IOFSwitch[] switches = new IOFSwitch[6];
        for (int i = 0; i < 6; i++) {
            switches[i] = createMockSwitch((long)i+1);
            switches[i].setSwitchClusterId((long)i+1);
            replay(switches[i]);
        }

        // Create links among the switches
        int linkInfoArray1[][] = {
                // SrcSw#, SrcPort#, SrcPortState, DstSw#, DstPort#, DstPortState
                { 1, 1, 0, 2, 1, 0},
                { 2, 1, 0, 1, 1, 0},
                { 1, 2, 0, 3, 1, 0},
                { 3, 1, 0, 1, 2, 0},
                { 2, 2, 0, 3, 2, 0},
                { 3, 2, 0, 2, 2, 0},
                { 2, 3, 0, 4, 2, 0},
                { 4, 2, 0, 2, 3, 0},
                { 3, 3, 0, 4, 1, 0},
                { 4, 1, 0, 3, 3, 0},
                { 5, 3, 0, 6, 1, 0},
                { 6, 1, 0, 5, 3, 0},
        };
        createLinks(topology, switches, linkInfoArray1);
        
        int expectedClusters1[][] = {
                {1,2,3,4},
                {5,6}
        };
        verifyClusters(topology, switches, expectedClusters1);
        
        int linkInfoArray2[][] = {
            { 4, 3, 0, 5, 1, 0},
            { 5, 1, 0, 4, 3, 0},
            { 2, 4, 0, 5, 2, 0},
            { 5, 2, 0, 2, 4, 0},
        };
        createLinks(topology, switches, linkInfoArray2);
        int expectedClusters2[][] = {
                {1,2,3,4,5,6},
        };
        verifyClusters(topology, switches, expectedClusters2);
        
        OFPortStatus portStatus = new OFPortStatus();
        portStatus.setReason((byte)OFPortStatus.OFPortReason.OFPPR_MODIFY.ordinal());
        OFPhysicalPort physicalPort = new OFPhysicalPort();
        physicalPort.setPortNumber((short)3);
        physicalPort.setConfig(0);
        physicalPort.setState(OFPhysicalPort.OFPortState.OFPPS_STP_BLOCK.getValue());
        portStatus.setDesc(physicalPort);
        topology.handlePortStatus(switches[4], portStatus);
        
        int expectedClusters3[][] = {
                {1,2,3,4,5},
                {6}
        };
        verifyClusters(topology, switches, expectedClusters3);
        
        physicalPort.setState(OFPhysicalPort.OFPortState.OFPPS_STP_FORWARD.getValue());
        topology.handlePortStatus(switches[4], portStatus);
        verifyClusters(topology, switches, expectedClusters2);
        
        topology.removedSwitch(switches[3]);
        int expectedClusters4[][] = {
                {1,2,3,5,6}
        };
        verifyClusters(topology, switches, expectedClusters4);
        
        portStatus.setReason((byte)OFPortStatus.OFPortReason.OFPPR_DELETE.ordinal());
        physicalPort.setPortNumber((short)4);
        topology.handlePortStatus(switches[1], portStatus);
        physicalPort.setPortNumber((short)2);
        topology.handlePortStatus(switches[4], portStatus);

        int expectedClusters5[][] = {
                {1,2,3},
                {5,6}
        };
        verifyClusters(topology, switches, expectedClusters5);
    }
    
    @Test
    public void testSwitchClusterMerge() {
        // We create 3 switches, merge them one by one then ensure the clusters are merged
        TopologyImpl topology = getTopology();
        IOFSwitch sw2 = createMockSwitch(2L);
        IOFSwitch sw3 = createMockSwitch(3L);
        
        Capture<Long> sw2ClusterIdCapture = new Capture<Long>(CaptureType.LAST);
        Capture<Long> sw3ClusterIdCapture = new Capture<Long>(CaptureType.LAST);
        
        sw3.setSwitchClusterId(capture(sw3ClusterIdCapture));
        expectLastCall().anyTimes();
        sw2.setSwitchClusterId(capture(sw2ClusterIdCapture));
        expectLastCall().anyTimes();
        replay(sw2, sw3);
        
        LinkTuple lt = new LinkTuple(sw2, (short)2, sw3, (short)1);
        topology.addOrUpdateLink(lt, 
                OFPortState.OFPPS_STP_FORWARD.getValue(), 
                OFPortState.OFPPS_STP_FORWARD.getValue());
        lt = new LinkTuple(sw3, (short)1, sw2, (short)2);
        topology.addOrUpdateLink(lt, 
                OFPortState.OFPPS_STP_FORWARD.getValue(), 
                OFPortState.OFPPS_STP_FORWARD.getValue());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        verify(sw2);
        verify(sw3);
        
        assertTrue(sw2ClusterIdCapture.hasCaptured());
        assertEquals(new Long(2L), sw2ClusterIdCapture.getValue());
        assertTrue(sw3ClusterIdCapture.hasCaptured());
        assertEquals(new Long(2L), sw3ClusterIdCapture.getValue());
        
        sw2ClusterIdCapture.reset();
        sw3ClusterIdCapture.reset();
        reset(sw2, sw3);
        
        IOFSwitch sw1 = createMockSwitch(1L);
        Capture<Long> sw1ClusterIdCapture = new Capture<Long>(CaptureType.LAST);
        
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw3.getId()).andReturn(3L).anyTimes();
        
        sw1.setSwitchClusterId(capture(sw1ClusterIdCapture));
        expectLastCall().anyTimes();
        sw3.setSwitchClusterId(capture(sw3ClusterIdCapture));
        expectLastCall().anyTimes();
        sw2.setSwitchClusterId(capture(sw2ClusterIdCapture));
        expectLastCall().anyTimes();
        
        replay(sw1, sw2, sw3);
        
        lt = new LinkTuple(sw1, (short)1, sw2, (short)1);
        topology.addOrUpdateLink(lt, 
                OFPortState.OFPPS_STP_FORWARD.getValue(), 
                OFPortState.OFPPS_STP_FORWARD.getValue());
        lt = new LinkTuple(sw2, (short)1, sw1, (short)1);
        topology.addOrUpdateLink(lt, 
                OFPortState.OFPPS_STP_FORWARD.getValue(), 
                OFPortState.OFPPS_STP_FORWARD.getValue());
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        verify(sw1);
        verify(sw2);
        verify(sw3);
        
        assertTrue(sw1ClusterIdCapture.hasCaptured());
        assertEquals(new Long(1L), sw2ClusterIdCapture.getValue());
        assertTrue(sw2ClusterIdCapture.hasCaptured());
        assertEquals(new Long(1L), sw2ClusterIdCapture.getValue());
        assertTrue(sw3ClusterIdCapture.hasCaptured());
        assertEquals(new Long(1L), sw3ClusterIdCapture.getValue());
    }
    
    private void verifyBroadcastTree(TopologyImpl topology, IOFSwitch[] switches, int[][] linkInfoArray) {
        Map<LinkTuple, LinkInfo> thisLinkInfos = new HashMap<LinkTuple, LinkInfo>();
        for (int i = 0; i < linkInfoArray.length; i++) {
            int[] linkInfo = linkInfoArray[i];
            LinkTuple lt = new LinkTuple(switches[linkInfo[0]-1], linkInfo[1], switches[linkInfo[3]-1], linkInfo[4]);
            LinkInfo lkInfo = new LinkInfo(System.currentTimeMillis(), linkInfo[2], linkInfo[5]);
            lkInfo.setBroadcastState(linkInfo[6]==0 ? LinkInfo.PortBroadcastState.PBS_FORWARD : 
                LinkInfo.PortBroadcastState.PBS_BLOCK);
            thisLinkInfos.put(lt, lkInfo);
        }
        
        for (Map.Entry<LinkTuple, LinkInfo> entry : topology.getLinks().entrySet()) {
            assertTrue(thisLinkInfos.containsKey(entry.getKey()));
            LinkInfo l1 = thisLinkInfos.get(entry.getKey());
            LinkInfo l2 = entry.getValue();
            assertNotNull(l1);
            assertNotNull(l2);
            l1.setValidTime(l2.getValidTime());
            boolean value = l1.equals(l2);
            assertTrue(value);
        }
    }
    
    @Test
    public void testLoopDetectionInSingleIsland() {
        
        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |   2   |
        //      +-------+             +-------+
        //          |                     |            
        //          |                     |            
        //      +-------+                 |
        //      |   1   |                 |   
        //      |   3  2|-----------------+                    
        //      |   3   |                    
        //      +-------+                    
        //          |                        
        //          |                        
        //      +-------+                                
        //      |   1   |                     
        //      |   4  2|----------------+                     
        //      |   3   |                |
        //      +-------+                |
        //          |                    |         
        //          |                    |         
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+
        
        //
        TopologyImpl topology = getTopology();
        
        // Create several switches
        IOFSwitch[] switches = new IOFSwitch[6];
        for (int i = 0; i < 6; i++) {
            switches[i] = createMockSwitch((long)i+1);
            switches[i].setSwitchClusterId((long)i+1);
            replay(switches[i]);
        }

        // Create links among the switches
        int linkInfoArray[][] = {
                // SrcSw#, SrcPort#, SrcPortState, DstSw#, DstPort#, DstPortState
                { 1, 1, 0, 2, 1, 0, 0},
                { 2, 1, 0, 1, 1, 0, 0},
                { 1, 2, 0, 3, 1, 0, 0},
                { 3, 1, 0, 1, 2, 0, 0},
                { 2, 2, 0, 3, 2, 0, 1},
                { 3, 2, 0, 2, 2, 0, 1},
                { 3, 3, 0, 4, 1, 0, 0},
                { 4, 1, 0, 3, 3, 0, 0},
                { 4, 2, 0, 6, 2, 0, 0},
                { 6, 2, 0, 4, 2, 0, 0},
                { 4, 3, 0, 5, 1, 0, 0},
                { 5, 1, 0, 4, 3, 0, 0},
                { 5, 2, 0, 6, 1, 0, 1},
                { 6, 1, 0, 5, 2, 0, 1},
        };
        createLinks(topology, switches, linkInfoArray);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        int expectedClusters[][] = {
                {1,2,3,4,5,6},
        };
        verifyClusters(topology, switches, expectedClusters);
        verifyBroadcastTree(topology, switches, linkInfoArray);
    }
    
    @Test
    public void testLoopDetectionInMultiIsland() {
        
        // +-------+             +-------+      +-------+             +-------+
        // |       |             |       |      |       |             |       |
        // |   1  1|-------------|1  2   |      |3  7  1|-------------|1  8   |
        // |   2   |             |   2   |      |   2   |             |   2   |
        // +-------+             +-------+      +-------+             +-------+
        //     |                     |              |                     |     
        //     |                     |              |                     |          
        // +-------+                 |          +-------+             +-------+
        // |   1   |                 |          |   1   |             |   1   |             
        // |   3  2|-----------------+          |   9  2|-------------|2  10  |    
        // |   3   |                            |   3   |             |   3   |       
        // +-------+                            +-------+             +-------+      
        //                                  
        //                                  
        //      +-------+                                
        //      |   1   |                     
        //      |   4  2|----------------+                     
        //      |   3   |                |
        //      +-------+                |
        //          |                    |         
        //          |                    |         
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+
        
        //
        TopologyImpl topology = getTopology();
        
        // Create several switches
        IOFSwitch[] switches = new IOFSwitch[10];
        for (int i = 0; i < 10; i++) {
            switches[i] = createMockSwitch((long)i+1);
            switches[i].setSwitchClusterId((long)i+1);
            replay(switches[i]);
        }

        // Create links among the switches
        int linkInfoArray[][] = {
                // SrcSw#, SrcPort#, SrcPortState, DstSw#, DstPort#, DstPortState
                { 1, 1, 0, 2, 1, 0, 0},
                { 2, 1, 0, 1, 1, 0, 0},
                { 1, 2, 0, 3, 1, 0, 0},
                { 3, 1, 0, 1, 2, 0, 0},
                { 2, 2, 0, 3, 2, 0, 1},
                { 3, 2, 0, 2, 2, 0, 1},
                { 4, 2, 0, 6, 2, 0, 0},
                { 6, 2, 0, 4, 2, 0, 0},
                { 4, 3, 0, 5, 1, 0, 0},
                { 5, 1, 0, 4, 3, 0, 0},
                { 5, 2, 0, 6, 1, 0, 1},
                { 6, 1, 0, 5, 2, 0, 1},
                { 7, 1, 0, 8, 1, 0, 0},
                { 8, 1, 0, 7, 1, 0, 0},
                { 7, 2, 0, 9, 1, 0, 0},
                { 9, 1, 0, 7, 2, 0, 0},
                { 9, 2, 0, 10, 2, 0, 0},
                { 10, 2, 0, 9, 2, 0, 0},
                { 8, 2, 0, 10, 1, 0, 1},
                { 10, 1, 0, 8, 2, 0, 1},
        };
        createLinks(topology, switches, linkInfoArray);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        int expectedClusters[][] = {
                {1,2,3},
                {4,5,6},
                {7,8,9,10},
        };
        verifyClusters(topology, switches, expectedClusters);
        verifyBroadcastTree(topology, switches, linkInfoArray);
    }
    
    @Test
    public void testLoopDetectionWithIslandMerge() {
        
        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |   2   |
        //      +-------+             +-------+
        //          |                     |            
        //          |                     |            
        //      +-------+                 |
        //      |   1   |                 |   
        //      |   3  2|-----------------+                    
        //      |   3   |                    
        //      +-------+                    
        //          |                        
        //          |                        
        //      +-------+                                
        //      |   1   |                     
        //      |   4  2|----------------+                     
        //      |   3   |                |
        //      +-------+                |
        //          |                    |         
        //          |                    |         
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+
        
        //
        TopologyImpl topology = getTopology();
        
        // Create several switches
        IOFSwitch[] switches = new IOFSwitch[6];
        for (int i = 0; i < 6; i++) {
            switches[i] = createMockSwitch((long)i+1);
            switches[i].setSwitchClusterId((long)i+1);
            replay(switches[i]);
        }

        // Create links among the switches
        int linkInfoArray[][] = {
                // SrcSw#, SrcPort#, SrcPortState, DstSw#, DstPort#, DstPortState
                { 1, 1, 0, 2, 1, 0, 0},
                { 2, 1, 0, 1, 1, 0, 0},
                { 1, 2, 0, 3, 1, 0, 0},
                { 3, 1, 0, 1, 2, 0, 0},
                { 2, 2, 0, 3, 2, 0, 1},
                { 3, 2, 0, 2, 2, 0, 1},
                { 4, 2, 0, 6, 2, 0, 0},
                { 6, 2, 0, 4, 2, 0, 0},
                { 4, 3, 0, 5, 1, 0, 0},
                { 5, 1, 0, 4, 3, 0, 0},
                { 5, 2, 0, 6, 1, 0, 1},
                { 6, 1, 0, 5, 2, 0, 1},
        };
        createLinks(topology, switches, linkInfoArray);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        int expectedClusters[][] = {
                {1,2,3},
                {4,5,6},
        };
        verifyClusters(topology, switches, expectedClusters);
        verifyBroadcastTree(topology, switches, linkInfoArray);
        
        int linkInfoArray2[][] = {
                { 3, 3, 0, 4, 1, 0, 0},
                { 4, 1, 0, 3, 3, 0, 0},
        };
        createLinks(topology, switches, linkInfoArray2);
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        int expectedClusters2[][] = {
                {1,2,3,4,5,6},
        };
        int linkInfoArray3[][] = {
                // SrcSw#, SrcPort#, SrcPortState, DstSw#, DstPort#, DstPortState
                { 1, 1, 0, 2, 1, 0, 0},
                { 2, 1, 0, 1, 1, 0, 0},
                { 1, 2, 0, 3, 1, 0, 0},
                { 3, 1, 0, 1, 2, 0, 0},
                { 2, 2, 0, 3, 2, 0, 1},
                { 3, 2, 0, 2, 2, 0, 1},
                { 3, 3, 0, 4, 1, 0, 0},
                { 4, 1, 0, 3, 3, 0, 0},
                { 4, 2, 0, 6, 2, 0, 0},
                { 6, 2, 0, 4, 2, 0, 0},
                { 4, 3, 0, 5, 1, 0, 0},
                { 5, 1, 0, 4, 3, 0, 0},
                { 5, 2, 0, 6, 1, 0, 1},
                { 6, 1, 0, 5, 2, 0, 1},
        };
        
        verifyClusters(topology, switches, expectedClusters2);
        verifyBroadcastTree(topology, switches, linkInfoArray3);
    }
}
