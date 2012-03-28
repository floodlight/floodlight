package net.floodlightcontroller.topology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.topology.TopologyInstance;
import net.floodlightcontroller.topology.TopologyManager;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyInstanceTest {
    protected static Logger log = LoggerFactory.getLogger(TopologyInstanceTest.class);
    protected TopologyManager topologyManager;
    protected FloodlightModuleContext fmc;
    protected MockFloodlightProvider mockFloodlightProvider;

    protected int DIRECT_LINK = 1;
    protected int MULTIHOP_LINK = 2;
    protected int TUNNEL_LINK = 3;

    @Before 
    public void SetUp() throws Exception {
        fmc = new FloodlightModuleContext();
        mockFloodlightProvider = new MockFloodlightProvider();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        MockThreadPoolService tp = new MockThreadPoolService();
        topologyManager  = new TopologyManager();
        fmc.addService(IThreadPoolService.class, tp);
        topologyManager.init(fmc);
        tp.init(fmc);
        tp.startUp(fmc);
    }

    protected void verifyClusters(int[][] clusters) {
        List<Long> verifiedSwitches = new ArrayList<Long>();

        // Make sure the expected cluster arrays are sorted so we can
        // use binarySearch to test for membership
        for (int i = 0; i < clusters.length; i++)
            Arrays.sort(clusters[i]);

        TopologyInstance ti = topologyManager.getCurrentInstance();
        Set<Long> switches = ti.getSwitches();

        for (long sw: switches) {
            if (!verifiedSwitches.contains(sw)) {

                int[] expectedCluster = null;

                for (int j = 0; j < clusters.length; j++) {
                    if (Arrays.binarySearch(clusters[j], (int) sw) >= 0) {
                        expectedCluster = clusters[j];
                        break;
                    }
                }
                if (expectedCluster != null) {
                    Set<Long> cluster = ti.getSwitchesInCluster(sw);
                    assertEquals(expectedCluster.length, cluster.size());
                    for (long sw2: cluster) {
                        assertTrue(Arrays.binarySearch(expectedCluster, (int)sw2) >= 0);
                        verifiedSwitches.add(sw2);
                    }
                }
            }
        }
    }

    protected void verifyExpectedBroadcastPortsInClusters(int [][][] ebp) {
        NodePortTuple npt = null;
        Set<NodePortTuple> expected = new HashSet<NodePortTuple>();
        for(int i=0; i<ebp.length; ++i) {
            int [][] nptList = ebp[i];
            expected.clear();
            for(int j=0; j<nptList.length; ++j) {
                npt = new NodePortTuple((long)nptList[j][0], (short)nptList[j][1]);
                expected.add(npt);
            }
            TopologyInstance ti = topologyManager.getCurrentInstance();
            Set<NodePortTuple> computed = ti.getBroadcastNodePortsInCluster(npt.nodeId);
            if (computed != null)
                assertTrue(computed.equals(expected));
            else if (computed == null)
                assertTrue(expected.isEmpty());
        }
    }

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

            topologyManager.addOrUpdateLink((long)r[0], (short)r[1], (long)r[2], (short)r[3], type);
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
            //tm.recompute();
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
            tm.removeLink((long)5,(short)3,(long)6,(short)1);
            tm.removeLink((long)6,(short)1,(long)5,(short)3);

            int [][] expectedClusters = {
                                         {1,2,3,4,5},
            };
            topologyManager.createNewInstance();
            verifyClusters(expectedClusters);
        }

        // Remove Switch
        {
            tm.removeSwitch(4);
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
        verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
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
                                  {1, 1, 2, 1, TUNNEL_LINK},
                                  {2, 1, 1, 1, TUNNEL_LINK},
                                  {1, 2, 3, 1, DIRECT_LINK},
                                  {3, 1, 1, 2, DIRECT_LINK},
                                  {2, 2, 3, 2, DIRECT_LINK},
                                  {3, 2, 2, 2, DIRECT_LINK},

                                  {4, 2, 6, 2, TUNNEL_LINK},
                                  {6, 2, 4, 2, TUNNEL_LINK},
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
            verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
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
                                  {3, 3, 4, 1, TUNNEL_LINK},
                                  {4, 1, 3, 3, TUNNEL_LINK},

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
            verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
        }
    }
}
