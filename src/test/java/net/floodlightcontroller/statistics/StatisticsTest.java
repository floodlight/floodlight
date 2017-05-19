package net.floodlightcontroller.statistics;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.easymock.EasyMock;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.protocol.OFFactories;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.junit.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Created by qw on 5/18/17.
 */


public class StatisticsTest extends FloodlightTestCase {

    private FloodlightContext cntx;
    private FloodlightModuleContext fmc;
//    private static IRestApiService restApiService;
    private IThreadPoolService threadPoolService;
    private MockThreadPoolService threadpool;
    private IOFSwitchService switchService;
    private static final OFFactory factory13 = OFFactories.getFactory(OFVersion.OF_13);
    private static final OFFactory factory14 = OFFactories.getFactory(OFVersion.OF_14);

    protected StatisticsCollector statsCollector;

    private IOFSwitch sw_OF13, sw_OF14;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        cntx = new FloodlightContext();
        fmc = new FloodlightModuleContext();

        // Module loader setup
        threadpool = new MockThreadPoolService();
        switchService = getMockSwitchService();
        statsCollector = new StatisticsCollector();

        fmc.addService(IThreadPoolService.class, threadpool);
        fmc.addService(IOFSwitchService.class, switchService);
//        fmc.addService(IRestApiService.class, restApiService);

        threadpool.init(fmc);
        statsCollector.init(fmc);
        threadpool.startUp(fmc);
//        statsCollector.startUp(fmc);


        //TODO: Pull the common code from testGetCurrPortSpeedOF13/OF14, make the IOFSwitch OF13/OF14 as a parameter

    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF13() throws Exception{
        // Create OpenFlow13 Mock Switch
        sw_OF13 = EasyMock.createMock(IOFSwitch.class);
        reset(sw_OF13);
        expect(sw_OF13.getId()).andReturn(DatapathId.of(1L)).anyTimes();
        expect(sw_OF13.getOFFactory()).andReturn(factory13).anyTimes();

        OFPortDesc portDesc13 = factory13.buildPortDesc()
                .setPortNo(OFPort.of(1))
                .setName("eth1")
                .setCurrSpeed(100L)
                .build();

        expect(sw_OF13.getPort(OFPort.of(1))).andReturn(portDesc13).anyTimes();
        replay(sw_OF13);

        NodePortTuple npt = new NodePortTuple(DatapathId.of(1L), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF13.getId(), sw_OF13);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }


    /**
     *
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF14() throws Exception {
        // Create OpenFlow14 Mock Switch
        sw_OF14 = EasyMock.createMock(IOFSwitch.class);
        reset(sw_OF14);
        expect(sw_OF14.getId()).andReturn(DatapathId.of(1L)).anyTimes();
        expect(sw_OF14.getOFFactory()).andReturn(factory14).anyTimes();


        OFPortDesc portDesc14 = factory14.buildPortDesc()
                .setPortNo(OFPort.of(1))
                .setName("eth1")
                .setProperties(Collections.singletonList(factory14.buildPortDescPropEthernet().setCurrSpeed(100L).build()))
                .build();

        expect(sw_OF14.getPort(OFPort.of(1))).andReturn(portDesc14).anyTimes();
        replay(sw_OF14);

        NodePortTuple npt = new NodePortTuple(DatapathId.of(1L), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF14.getId(), sw_OF14);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }

}
