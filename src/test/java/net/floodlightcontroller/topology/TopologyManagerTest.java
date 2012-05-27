package net.floodlightcontroller.topology;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.TopologyManager;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyManagerTest extends FloodlightTestCase {
    protected static Logger log = LoggerFactory.getLogger(TopologyManagerTest.class);
    TopologyManager tm;
    FloodlightModuleContext fmc;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        MockThreadPoolService tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);
        tm  = new TopologyManager();
        tp.init(fmc);
        tm.init(fmc);
        tp.startUp(fmc);
    }

    @Test
    public void testBasic1() throws Exception {
        tm.addOrUpdateLink((long)1, (short)1, (long)2, (short)1, ILinkDiscovery.LinkType.DIRECT_LINK);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==0);

        tm.addOrUpdateLink((long)1, (short)2, (long)2, (short)2, ILinkDiscovery.LinkType.MULTIHOP_LINK);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==0);

        tm.addOrUpdateLink((long)1, (short)3, (long)2, (short)3, ILinkDiscovery.LinkType.TUNNEL);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==3);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==3);
        assertTrue(tm.getSwitchPortLinks().size()==6);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==2);

        tm.removeLink((long)1, (short)2, (long)2, (short)2);
        log.info("# of switchports. {}", tm.getSwitchPorts().get((long)1).size());
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==2);

        tm.removeLink((long)1, (short)1, (long)2, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==2);

        tm.removeLink((long)1, (short)3, (long)2, (short)3);
        assertTrue(tm.getSwitchPorts().size() == 0); 
        assertTrue(tm.getSwitchPortLinks().size()==0);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==0);
    }

    @Test
    public void testBasic2() throws Exception {
        tm.addOrUpdateLink((long)1, (short)1, (long)2, (short)1, ILinkDiscovery.LinkType.DIRECT_LINK);
        tm.addOrUpdateLink((long)2, (short)2, (long)3, (short)1, ILinkDiscovery.LinkType.MULTIHOP_LINK);
        tm.addOrUpdateLink((long)3, (short)2, (long)1, (short)2, ILinkDiscovery.LinkType.TUNNEL);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==6);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==2);

        tm.removeLink((long)1, (short)1, (long)2, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==2);

        // nonexistent link // no null pointer exceptions.
        tm.removeLink((long)3, (short)1, (long)2, (short)2);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==2);

        tm.removeLink((long)3, (short)2, (long)1, (short)2);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1)==null);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==0);

        tm.removeLink((long)2, (short)2, (long)3, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 0);  // for two nodes.
        assertTrue(tm.getSwitchPortLinks().size()==0);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==0);
    }

    @Test
    public void testHARoleChange() throws Exception {
        testBasic2();
        getMockFloodlightProvider().dispatchRoleChanged(null, Role.SLAVE);
        assert(tm.switchPorts.isEmpty());
        assert(tm.switchPortLinks.isEmpty());
        assert(tm.portBroadcastDomainLinks.isEmpty());
        assert(tm.tunnelLinks.isEmpty());
    }
}
