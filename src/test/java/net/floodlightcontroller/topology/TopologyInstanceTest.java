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

package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static net.floodlightcontroller.routing.IRoutingService.PATH_METRIC.HOPCOUNT;
import static net.floodlightcontroller.routing.IRoutingService.PATH_METRIC.LATENCY;
import static org.junit.Assert.*;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockSwitchManager;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.RoutingManager;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.topology.TopologyInstance;
import net.floodlightcontroller.topology.TopologyManager;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyInstanceTest {
    protected static Logger log = LoggerFactory.getLogger(TopologyInstanceTest.class);
    protected TopologyManager topologyManager;
    protected RoutingManager routingManager;
    protected FloodlightModuleContext fmc;
    protected ILinkDiscoveryService linkDiscovery;
    protected MockFloodlightProvider mockFloodlightProvider;

    protected int DIRECT_LINK = 1;
    protected int MULTIHOP_LINK = 2;
    protected int TUNNEL_LINK = 3;

    @Before 
    public void SetUp() throws Exception {
        fmc = new FloodlightModuleContext();
        linkDiscovery = EasyMock.createMock(ILinkDiscoveryService.class);
        mockFloodlightProvider = new MockFloodlightProvider();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IOFSwitchService.class, new MockSwitchManager());
        fmc.addService(ILinkDiscoveryService.class, linkDiscovery);
        fmc.addService(IDebugCounterService.class, new MockDebugCounterService());
        MockThreadPoolService tp = new MockThreadPoolService();
        topologyManager = new TopologyManager();
        routingManager = new RoutingManager();
        fmc.addService(IRoutingService.class, routingManager);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ITopologyService.class, topologyManager);
        topologyManager.init(fmc);
        routingManager.init(fmc);
        routingManager.startUp(fmc);
        tp.init(fmc);
        tp.startUp(fmc);
    }

    protected void verifyClusters(int[][] clusters) {
        List<DatapathId> verifiedSwitches = new ArrayList<DatapathId>();

        // Make sure the expected cluster arrays are sorted so we can
        // use binarySearch to test for membership
        for (int i = 0; i < clusters.length; i++)
            Arrays.sort(clusters[i]);

        TopologyInstance ti = 
                topologyManager.getCurrentInstance();
        Set<DatapathId> switches = ti.getSwitches();

        for (DatapathId sw: switches) {
            if (!verifiedSwitches.contains(sw)) {

                int[] expectedCluster = null;

                for (int j = 0; j < clusters.length; j++) {
                    if (Arrays.binarySearch(clusters[j], (int)sw.getLong()) >= 0) {
                        expectedCluster = clusters[j];
                        break;
                    }
                }
                if (expectedCluster != null) {
                    Set<DatapathId> cluster = ti.getSwitchesInCluster(sw);
                    assertEquals(expectedCluster.length, cluster.size());
                    for (DatapathId sw2: cluster) {
                        assertTrue(Arrays.binarySearch(expectedCluster, (int)sw2.getLong()) >= 0);
                        verifiedSwitches.add(sw2);
                    }
                }
            }
        }
    }
    
    /*protected void 
    verifyExpectedBroadcastPortsInClusters(int [][][] ebp) {
        NodePortTuple npt = null;
        Set<NodePortTuple> expected = new HashSet<NodePortTuple>();
        for(int i=0; i<ebp.length; ++i) {
            int [][] nptList = ebp[i];
            expected.clear();
            for(int j=0; j<nptList.length; ++j) {
                npt = new NodePortTuple(DatapathId.of(nptList[j][0]), OFPort.of(nptList[j][1]));
                expected.add(npt);
            }
            TopologyInstance ti = topologyManager.getCurrentInstance();
            Set<NodePortTuple> computed = ti.getBroadcastNodePortsInCluster(npt.getNodeId());
            log.info("computed: {}", computed);
            log.info("expected: {}", expected);
            if (computed != null)
                assertTrue(computed.equals(expected));
            else if (computed == null)
                assertTrue(expected.isEmpty());
        }
    }*/

    public void createTopologyFromLinks(int [][] linkArray) throws Exception {
        ILinkDiscovery.LinkType type = ILinkDiscovery.LinkType.DIRECT_LINK;

        // Use topologymanager to write this test, it will make it a lot easier.
        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            if (r[4] == DIRECT_LINK)
                type= ILinkDiscovery.LinkType.DIRECT_LINK;
            else if (r[4] == MULTIHOP_LINK)
                type= ILinkDiscovery.LinkType.MULTIHOP_LINK;
            else if (r[4] == TUNNEL_LINK)
                type = ILinkDiscovery.LinkType.TUNNEL;

            topologyManager.addOrUpdateLink(DatapathId.of(r[0]), OFPort.of(r[1]), DatapathId.of(r[2]), OFPort.of(r[3]), U64.ZERO, type);
        }
        topologyManager.createNewInstance();
    }

    private void configureTopology(int [][] linkArray, int [] latency) throws Exception {
        ILinkDiscovery.LinkType type = ILinkDiscovery.LinkType.DIRECT_LINK;

        // Use topologymanager to write this test, it will make it a lot easier.
        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            if (r[4] == DIRECT_LINK)
                type= ILinkDiscovery.LinkType.DIRECT_LINK;
            else if (r[4] == MULTIHOP_LINK)
                type= ILinkDiscovery.LinkType.MULTIHOP_LINK;
            else if (r[4] == TUNNEL_LINK)
                type = ILinkDiscovery.LinkType.TUNNEL;

            //Check for valid latency
            int lat = latency[i];
            if(lat < 0 || lat > 10000)
                lat = 10000;


            topologyManager.addOrUpdateLink(DatapathId.of(r[0]), OFPort.of(r[1]), DatapathId.of(r[2]), OFPort.of(r[3]), U64.of(lat), type);
        }
        topologyManager.createNewInstance();
    }

    public TopologyManager getTopologyManager() {
        return topologyManager;
    }

    @Test
    public void testClusters() throws Exception {
        TopologyManager tm = getTopologyManager();
        {
            int [][] linkArray = { 
                                  {1, 1, 2, 1, DIRECT_LINK}, 
                                  {2, 2, 3, 2, DIRECT_LINK},
                                  {3, 1, 1, 2, DIRECT_LINK},
                                  {2, 3, 4, 2, DIRECT_LINK},
                                  {3, 3, 4, 1, DIRECT_LINK}
            };
            int [][] expectedClusters = {
                                         {1,2,3}, 
                                         {4}
            };
            createTopologyFromLinks(linkArray);
            verifyClusters(expectedClusters);
        }

        {
            int [][] linkArray = { 
                                  {5, 3, 6, 1, DIRECT_LINK} 
            };
            int [][] expectedClusters = {
                                         {1,2,3}, 
                                         {4},
                                         {5},
                                         {6}
            };
            createTopologyFromLinks(linkArray);

            verifyClusters(expectedClusters);
        }

        {
            int [][] linkArray = { 
                                  {6, 1, 5, 3, DIRECT_LINK} 
            };
            int [][] expectedClusters = {
                                         {1,2,3}, 
                                         {4},
                                         {5,6}
            };
            createTopologyFromLinks(linkArray);

            verifyClusters(expectedClusters);
        }

        {
            int [][] linkArray = { 
                                  {4, 2, 2, 3, DIRECT_LINK} 
            };
            int [][] expectedClusters = {
                                         {1,2,3,4},
                                         {5,6}
            };
            createTopologyFromLinks(linkArray);

            verifyClusters(expectedClusters);
        }
        {
            int [][] linkArray = { 
                                  {4, 3, 5, 1, DIRECT_LINK} 
            };
            int [][] expectedClusters = {
                                         {1,2,3,4},
                                         {5,6}
            };
            createTopologyFromLinks(linkArray);

            verifyClusters(expectedClusters);
        }
        {
            int [][] linkArray = { 
                                  {5, 2, 2, 4, DIRECT_LINK} 
            };
            int [][] expectedClusters = {
                                         {1,2,3,4,5,6}
            };
            createTopologyFromLinks(linkArray);

            verifyClusters(expectedClusters);
        }

        //Test 2.
        {
            int [][] linkArray = { 
                                  {3, 2, 2, 2, DIRECT_LINK}, 
                                  {2, 1, 1, 1, DIRECT_LINK},
                                  {1, 2, 3, 1, DIRECT_LINK},
                                  {4, 1, 3, 3, DIRECT_LINK},
                                  {5, 1, 4, 3, DIRECT_LINK},
                                  {2, 4, 5, 2, DIRECT_LINK}
            };
            int [][] expectedClusters = {
                                         {1,2,3,4,5,6}
            };
            createTopologyFromLinks(linkArray);
            verifyClusters(expectedClusters);
        }

        // Test 3. Remove links
        {
            tm.removeLink(DatapathId.of(5), OFPort.of((short)3), DatapathId.of(6), OFPort.of((short)1));
            tm.removeLink(DatapathId.of(6), OFPort.of((short)1), DatapathId.of(5), OFPort.of((short)3));

            int [][] expectedClusters = {
                                         {1,2,3,4,5},
            };
            topologyManager.createNewInstance();
            verifyClusters(expectedClusters);
        }

        // Remove Switch
        {
            tm.removeSwitch(DatapathId.of(4));
            int [][] expectedClusters = {
                                         {1,2,3,5},
            };
            topologyManager.createNewInstance();
            verifyClusters(expectedClusters);
        }
    }

    @Test
    public void testLoopDetectionInSingleIsland() throws Exception {

        int [][] linkArray = {
                              {1, 1, 2, 1, DIRECT_LINK},
                              {2, 1, 1, 1, DIRECT_LINK},
                              {1, 2, 3, 1, DIRECT_LINK},
                              {3, 1, 1, 2, DIRECT_LINK},
                              {2, 2, 3, 2, DIRECT_LINK},
                              {3, 2, 2, 2, DIRECT_LINK},
                              {3, 3, 4, 1, DIRECT_LINK},
                              {4, 1, 3, 3, DIRECT_LINK},
                              {4, 2, 6, 2, DIRECT_LINK},
                              {6, 2, 4, 2, DIRECT_LINK},
                              {4, 3, 5, 1, DIRECT_LINK},
                              {5, 1, 4, 3, DIRECT_LINK},
                              {5, 2, 6, 1, DIRECT_LINK},
                              {6, 1, 5, 2, DIRECT_LINK},

        };
        int [][] expectedClusters = {
                                     {1, 2, 3, 4, 5, 6}
        };
        int [][][] expectedBroadcastPorts = {
                                             {{1,1}, {2,1}, {1,2}, {3,1}, {3,3}, {4,1}, {4,3}, {5,1}, {4,2}, {6,2}},
        };

        createTopologyFromLinks(linkArray);
        topologyManager.createNewInstance();
        verifyClusters(expectedClusters);
        //FIXME verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
    }

    @Test
    public void testLoopDetectionWithIslands() throws Exception {

        //      +-------+             +-------+
        //      |       |   TUNNEL    |       |
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
        //
        //
        //      +-------+
        //      |   1   |   TUNNEL
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
        {
            int [][] linkArray = {
                                  {1, 1, 2, 1, DIRECT_LINK},
                                  {2, 1, 1, 1, DIRECT_LINK},
                                  {1, 2, 3, 1, DIRECT_LINK},
                                  {3, 1, 1, 2, DIRECT_LINK},
                                  {2, 2, 3, 2, DIRECT_LINK},
                                  {3, 2, 2, 2, DIRECT_LINK},

                                  {4, 2, 6, 2, DIRECT_LINK},
                                  {6, 2, 4, 2, DIRECT_LINK},
                                  {4, 3, 5, 1, DIRECT_LINK},
                                  {5, 1, 4, 3, DIRECT_LINK},
                                  {5, 2, 6, 1, DIRECT_LINK},
                                  {6, 1, 5, 2, DIRECT_LINK},

            };

            int [][] expectedClusters = {
                                         {1, 2, 3}, 
                                         {4, 5, 6}
            };
            int [][][] expectedBroadcastPorts = {
                                                 {{1,1}, {2,1}, {1,2}, {3,1}},
                                                 {{4,3}, {5,1}, {4,2}, {6,2}},
            };

            createTopologyFromLinks(linkArray);
            topologyManager.createNewInstance();
            verifyClusters(expectedClusters);
            //FIXME verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
        }

        //      +-------+             +-------+
        //      |       |    TUNNEL   |       |
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
        //          |   TUNNEL
        //          |
        //      +-------+
        //      |   1   |    TUNNEL
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

        {
            int [][] linkArray = {
                                  {3, 3, 4, 1, DIRECT_LINK},
                                  {4, 1, 3, 3, DIRECT_LINK},

            };
            int [][] expectedClusters = {
                                         {1, 2, 3, 4, 5, 6}
            };
            int [][][] expectedBroadcastPorts = {
                                                 {{1,1}, {2,1}, {1,2}, {3,1},
                                                  {3,3}, {4,1}, {4,3}, {5,1},
                                                  {4,2}, {6,2}},
            };

            createTopologyFromLinks(linkArray);
            topologyManager.createNewInstance();
            verifyClusters(expectedClusters);
            //FIXMEverifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
        }
    }

    @Test
    public void testLinkRemovalOnBroadcastDomainPorts() throws Exception {
        {
            int [][] linkArray = {
                                  {1, 1, 2, 1, DIRECT_LINK},
                                  {2, 1, 1, 1, DIRECT_LINK},
                                  {1, 2, 3, 1, DIRECT_LINK},
                                  {3, 1, 1, 2, DIRECT_LINK},
                                  {2, 2, 3, 2, DIRECT_LINK},
                                  {3, 2, 2, 2, DIRECT_LINK},
                                  {1, 1, 3, 2, DIRECT_LINK},
                                  // the last link should make ports
                                  // (1,1) and (3,2) to be broadcast
                                  // domain ports, hence all links
                                  // from these ports must be eliminated.
            };

            int [][] expectedClusters = {
                                         {1, 3}, {2},
            };
            createTopologyFromLinks(linkArray);
            topologyManager.createNewInstance();
            if (topologyManager.getCurrentInstance() instanceof TopologyInstance)
                verifyClusters(expectedClusters);
        }
        {
            int [][] linkArray = {
                                  {1, 2, 3, 2, DIRECT_LINK},
                                  // the last link should make ports
                                  // (1,1) and (3,2) to be broadcast
                                  // domain ports, hence all links
                                  // from these ports must be eliminated.
            };

            int [][] expectedClusters = {
                                         {1}, {3}, {2},
            };
            createTopologyFromLinks(linkArray);
            topologyManager.createNewInstance();
            if (topologyManager.getCurrentInstance() instanceof TopologyInstance)
                verifyClusters(expectedClusters);
        }
    }

    private void verifyRoute(List<Path> r, Integer size)
    {
        ArrayList<Path> paths = new ArrayList<Path>();

        DatapathId one = DatapathId.of(1);
        DatapathId two = DatapathId.of(2);
        DatapathId three = DatapathId.of(3);
        DatapathId four = DatapathId.of(4);
        DatapathId five = DatapathId.of(5);
        DatapathId six = DatapathId.of(6);

        NodePortTuple one1 = new NodePortTuple(one, OFPort.of(1));
        NodePortTuple one2 = new NodePortTuple(one, OFPort.of(2));

        NodePortTuple two1 = new NodePortTuple(two, OFPort.of(1));
        NodePortTuple two2 = new NodePortTuple(two, OFPort.of(2));
        NodePortTuple two3 = new NodePortTuple(two, OFPort.of(3));

        NodePortTuple three1 = new NodePortTuple(three, OFPort.of(1));
        NodePortTuple three2 = new NodePortTuple(three, OFPort.of(2));
        NodePortTuple three3 = new NodePortTuple(three, OFPort.of(3));
        NodePortTuple three4 = new NodePortTuple(three, OFPort.of(4));

        NodePortTuple four1 = new NodePortTuple(four, OFPort.of(1));
        NodePortTuple four2 = new NodePortTuple(four, OFPort.of(2));
        NodePortTuple four3 = new NodePortTuple(four, OFPort.of(3));
        NodePortTuple four4 = new NodePortTuple(four, OFPort.of(4));

        NodePortTuple five1 = new NodePortTuple(five, OFPort.of(1));
        NodePortTuple five2 = new NodePortTuple(five, OFPort.of(2));
        NodePortTuple five3 = new NodePortTuple(five, OFPort.of(3));

        NodePortTuple six1 = new NodePortTuple(six, OFPort.of(1));
        NodePortTuple six2 = new NodePortTuple(six, OFPort.of(2));

        List<NodePortTuple> route0 = new ArrayList<NodePortTuple>();
        route0.add(one1);
        route0.add(two1);
        route0.add(two2);
        route0.add(three1);
        route0.add(three4);
        route0.add(six2);
        Path root0 = new Path(one, six);
        root0.setPath(route0);
        paths.add(root0);
        //log.info("root0: {}", root0);
        //log.info("r.get(0) {}:", r.get(0));


        ArrayList<NodePortTuple> route1 = new ArrayList<NodePortTuple>();
        route1.add(one2);
        route1.add(four1);
        route1.add(four3);
        route1.add(three2);
        route1.add(three4);
        route1.add(six2);
        Path root1 = new Path(one, six);
        root1.setPath(route1);
        //log.info("root1: {}", root1);
        //log.info("r.get(1) {}:", r.get(1));
        paths.add(root1);

        ArrayList<NodePortTuple> route2 = new ArrayList<NodePortTuple>();
        route2.add(one2);
        route2.add(four1);
        route2.add(four4);
        route2.add(five1);
        route2.add(five3);
        route2.add(six1);
        Path root2 = new Path(one, six);
        root2.setPath(route2);
        //log.info("root2: {}", root2);
        //log.info("r.get(2) {}:", r.get(2));
        paths.add(root2);

        ArrayList<NodePortTuple> route3 = new ArrayList<NodePortTuple>();
        route3.add(one1);
        route3.add(two1);
        route3.add(two2);
        route3.add(three1);
        route3.add(three3);
        route3.add(five2);
        route3.add(five3);
        route3.add(six1);
        Path root3 = new Path(one, six);
        root3.setPath(route3);
        //log.info("root3: {}", root3);
        //log.info("r.get(3) {}:", r.get(3));
        paths.add(root3);

        ArrayList<NodePortTuple> route4 = new ArrayList<NodePortTuple>();
        route4.add(one2);
        route4.add(four1);
        route4.add(four3);
        route4.add(three2);
        route4.add(three3);
        route4.add(five2);
        route4.add(five3);
        route4.add(six1);
        Path root4 = new Path(one, six);
        root4.setPath(route4);
        //log.info("root4: {}", root4);
        //log.info("r.get(4) {}:", r.get(4));
        paths.add(root4);

        ArrayList<NodePortTuple> route5 = new ArrayList<NodePortTuple>();
        route5.add(one2);
        route5.add(four1);
        route5.add(four2);
        route5.add(two3);
        route5.add(two2);
        route5.add(three1);
        route5.add(three4);
        route5.add(six2);
        Path root5 = new Path(one, six);
        root5.setPath(route5);
        //log.info("root5: {}", root5);
        //log.info("r.get(5) {}:", r.get(5));
        paths.add(root5);

        ArrayList<NodePortTuple> route6 = new ArrayList<NodePortTuple>();
        route6.add(one2);
        route6.add(four1);
        route6.add(four2);
        route6.add(two3);
        route6.add(two2);
        route6.add(three1);
        route6.add(three3);
        route6.add(five2);
        route6.add(five3);
        route6.add(six1);
        Path root6 = new Path(one, six);
        root6.setPath(route6);
        //log.info("root6: {}", root6);
        //log.info("r.get(6) {}:", r.get(6));
        paths.add(root6);

        //The heart of the validate function.
        //Iterates through list and ensures each entry is present.
        int count = 0;
        int path_length = 7;
        for(int i=0; i<size; i++) {
            for(int j=0; j<path_length; j++) {
                //This may be redundant, but bear with me...
                if(paths.get(j).equals(r.get(i))){
                    assertTrue((paths.get(j)).equals(r.get(i)));
                    count++;
                    break;
                }
            }
        }
        assertTrue(count == size);
    }

    @Test
    public void testgetPathsFast() throws Exception{
        Integer k = 2;
        DatapathId one = DatapathId.of(1);
        DatapathId three = DatapathId.of(3);
        DatapathId six = DatapathId.of(6);

        /*
         * FIRST TOPOLOGY
         * Both link arrays and corresponding latency
         * array used in this unit test. Shown below is
         * a graphical representation of the topology.
         * This topology is entirely weakly-connected
         * and will form single-switch clusters despite
         * each link being an inter-cluster/direct link.
         * It will be detected as a single archipelago
         * though, since some nodes can reach others
         * over the weak links.

                                    -------
                                   |       |
                          -------->|1 '2' 2|--------
                          |        |       |        |
                          |         -------         |
                          |                         V
                       -------                   -------
                      |   1   |                 |   2   |
                      |  '1' 2|---------------->|1 '3'  |
                      |       |                 |       |
                       -------                   -------
         */
        int [][] linkArray = {
                {1, 1, 2, 1, DIRECT_LINK},
                {1, 2, 3, 1, DIRECT_LINK},
                {2, 2, 3, 2, DIRECT_LINK},
        };
        int [] lat = {1,50,1};

        /* 
         * NOTE: Output from the next four log.info should be mirrored!
         * Get paths based on latency.
         */
        topologyManager.setPathMetric(LATENCY);
        configureTopology(linkArray, lat);
        List<Path> lat_paths = routingManager.getPathsFast(one, three, k);
        log.info("Path 1: {}", lat_paths.get(0));
        log.info("Path 2: {}", lat_paths.get(1));

        //Get paths based on hop count
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        configureTopology(linkArray, lat);
        topologyManager.createNewInstance();
        List<Path> hop_paths = routingManager.getPathsFast(one, three, k);
        log.info("Path 1: {}", hop_paths.get(0));
        log.info("Path 2: {}", hop_paths.get(1));

        /*
         * Check if routes equal what the expected output should be.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(LATENCY);
        int [] lat1 = {1,50,1};
        configureTopology(linkArray, lat1);
        topologyManager.createNewInstance();
        List<Path> r1 = routingManager.getPathsFast(one, three, k);
        assertTrue((r1.get(0)).equals(lat_paths.get(0)));
        assertTrue((r1.get(1)).equals(lat_paths.get(1)));

        /*
         * Check output with bottom latency = -100.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(LATENCY);
        int [] lat2 = {1,-100,1};
        configureTopology(linkArray, lat2);
        topologyManager.createNewInstance();
        log.info("Latency = (-100) => Links: {}", topologyManager.getAllLinks());

        /*
         * Check output with bottom latency = 25000.
         */
        topologyManager.clearCurrentTopology();
        int [] lat3 = {1,25000,1};
        configureTopology(linkArray, lat3);
        topologyManager.createNewInstance();
        log.info("Latency = (25000) => Links: {}", topologyManager.getAllLinks());

        /*
         * SECOND TOPOLOGY - Multiple latency arrays used.
         * Shown below is the topology
         
            -------        -------        -------
           |       |      |       |----->|1      |
           |  '1' 1|----->|1 '2' 2|      |  '3' 4|----------|
           |   2   |      |   3   |   |->|2  3   |          |
            -------        -------    |   -------           |
               |              |       |      |              |
               |              |       |      |              |
               |              V       |      V              V
               |           -------    |   -------        -------
               |          |   2  3|---|  |   2   |      |   2   |
               |--------->|1 '4'  |      |  '5' 3|----->|1 '6'  |
                          |      4|----->|1      |      |       |
                           -------        -------        -------
         */
        int [][] linkArray2 = {
                {1, 1, 2, 1, DIRECT_LINK},
                {1, 2, 4, 1, DIRECT_LINK},
                {2, 2, 3, 1, DIRECT_LINK},
                {3, 3, 5, 2, DIRECT_LINK},
                {3, 4, 6, 2, DIRECT_LINK},
                {4, 2, 2, 3, DIRECT_LINK},
                {4, 3, 3, 2, DIRECT_LINK},
                {4, 4, 5, 1, DIRECT_LINK},
                {5, 3, 6, 1, DIRECT_LINK},
        };


        /*
         * What happens when k > total paths in topology?
         * All paths should be found. 7 in this case.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = 1000;
        int [] lat4 = {3,2,4,2,1,1,2,3,2};
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r.size(); i++) {
            log.info("k = (1000) => Route: {}", r.get(i));
        }
        verifyRoute(r, r.size());

        /*
         * Checking algorithm to see if all paths are found.
         * Result should be 7 distinct paths.
         * Total number of paths in topology is SEVEN.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = 7;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r2 = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r2.size(); i++) {
            log.info("k = (7) => Route: {}", r2.get(i));
        }
        verifyRoute(r2, r2.size());

        /*
         * Test output with negative input value.
         * No paths should be output as a result.
         * Based on HOPCOUNT.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = -1;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r3 = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r3.size(); i++) {
            log.info("HOPCOUNT.k = (-1) => Route: {}", r3.get(i));
        }
        verifyRoute(r3, r3.size());

        /*
         * Test output with negative input value.
         * No paths should be output as a result.
         * Based on LATENCY.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(LATENCY);
        k = -1;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r4 = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r4.size(); i++) {
            log.info("LATENCY.k = (-1) => Route: {}", r4.get(i));
        }
        verifyRoute(r4, r4.size());

        /*
         * Simple route checking with less than max routes requested.
         * In this case, only 3 were requested.
         * Based on HOPCOUNT.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = 3;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r5 = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r5.size(); i++) {
            log.info("HOPCOUNT.k = (3) => Route: {}", r5.get(i));
        }
        verifyRoute(r5, r5.size());

        /*
         * Simple route checking with less than max routes requested.
         * In this case, just 4 out of 7 possibilities requested.
         * Based on LATENCY.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(LATENCY);
        k = 4;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r6 = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r6.size(); i++) {
            log.info("LATENCY.k = (4) => Route: {}", r6.get(i));
        }
        verifyRoute(r6, r6.size());

        /*
         * Test output with all latency links set to zero.
         * Should return four of the first paths that yen's algorithm calculated.
         * Order of output here is of no concern.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(LATENCY);
        k = 4;
        int [] lat5 = {0,0,0,0,0,0,0,0,0};
        configureTopology(linkArray2, lat5);
        topologyManager.createNewInstance();
        List<Path> r7 = routingManager.getPathsFast(one, six, k);
        for(int i = 0; i< r7.size(); i++) {
            log.info("Route latency all ZERO: {}", r7.get(i));
        }
        verifyRoute(r7, r7.size());

        /*
         * Check topology with same switch input: 1 -> 1.
         * Should have no output.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = 4;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r8 = routingManager.getPathsFast(one, one, k);
        for(int i = 0; i< r8.size(); i++) {
            log.info("(src == dst) => Route: {}", r8.get(i));
        }
        verifyRoute(r8, r8.size());

        /*
         * Check topology with reverse input: 6 -> 1 instead of 1 -> 6
         * Should have no output since it is impossible to get from 6 to 1.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = 4;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r9 = routingManager.getPathsFast(six, one, k);
        for(int i = 0; i< r9.size(); i++) {
            log.info("Reversed Route (6 -> 1): {}", r9.get(i));
        }
        verifyRoute(r9, r9.size());

        /*
         * Check topology with invalid node numbers.
         * Try to use src == 7
         * Output should indicate no valid route.
         */
        topologyManager.clearCurrentTopology();
        topologyManager.setPathMetric(HOPCOUNT);
        k = 4;
        configureTopology(linkArray2, lat4);
        topologyManager.createNewInstance();
        List<Path> r10 = routingManager.getPathsFast(one, DatapathId.of(7), k);
        for(int i = 0; i< r10.size(); i++) {
            log.info("(src == 7) => Route: {}", r10.get(i));
        }
        verifyRoute(r10, r10.size());
    }
}