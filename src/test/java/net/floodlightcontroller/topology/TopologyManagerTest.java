package net.floodlightcontroller.topology;

import static org.junit.Assert.*;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.MockThreadPoolService;
import net.floodlightcontroller.topology.TopologyManager;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyManagerTest {
    protected static Logger log = LoggerFactory.getLogger(TopologyManagerTest.class);
    TopologyManager topologyManager;
    FloodlightModuleContext fmc;
    protected MockFloodlightProvider mockFloodlightProvider;
    
    @Before 
    public void SetUp() throws Exception {
        mockFloodlightProvider = new MockFloodlightProvider();
        fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        MockThreadPoolService tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);
        topologyManager  = new TopologyManager();
        tp.init(fmc);
        topologyManager.init(fmc);
        tp.startUp(fmc);
    }

    public TopologyManager getTopologyManager() {
        return topologyManager;
    }

    @Test
    public void basicTest1() {
        TopologyManager tm = getTopologyManager();
        tm.addOrUpdateLink((long)1, (short)1, (long)2, (short)1, ILinkDiscovery.LinkType.DIRECT_LINK);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelLinks().size()==0);

        tm.addOrUpdateLink((long)1, (short)2, (long)2, (short)2, ILinkDiscovery.LinkType.MULTIHOP_LINK);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelLinks().size()==0);

        tm.addOrUpdateLink((long)1, (short)3, (long)2, (short)3, ILinkDiscovery.LinkType.TUNNEL);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==3);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==3);
        assertTrue(tm.getSwitchPortLinks().size()==6);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelLinks().size()==2);

        tm.removeLink((long)1, (short)2, (long)2, (short)2);
        log.info("# of switchports. {}", tm.getSwitchPorts().get((long)1).size());
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelLinks().size()==2);

        tm.removeLink((long)1, (short)1, (long)2, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelLinks().size()==2);

        tm.removeLink((long)1, (short)3, (long)2, (short)3);
        assertTrue(tm.getSwitchPorts().size() == 0); 
        assertTrue(tm.getSwitchPortLinks().size()==0);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelLinks().size()==0);
    }

    @Test
    public void basicTest2() {
        TopologyManager tm = getTopologyManager();
        tm.addOrUpdateLink((long)1, (short)1, (long)2, (short)1, ILinkDiscovery.LinkType.DIRECT_LINK);
        tm.addOrUpdateLink((long)2, (short)2, (long)3, (short)1, ILinkDiscovery.LinkType.MULTIHOP_LINK);
        tm.addOrUpdateLink((long)3, (short)2, (long)1, (short)2, ILinkDiscovery.LinkType.TUNNEL);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==6);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelLinks().size()==2);

        tm.removeLink((long)1, (short)1, (long)2, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelLinks().size()==2);

        // nonexistent link // no null pointer exceptions.
        tm.removeLink((long)3, (short)1, (long)2, (short)2);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelLinks().size()==2);

        tm.removeLink((long)3, (short)2, (long)1, (short)2);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1)==null);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelLinks().size()==0);

        tm.removeLink((long)2, (short)2, (long)3, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 0);  // for two nodes.
        assertTrue(tm.getSwitchPortLinks().size()==0);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelLinks().size()==0);
    }
}
