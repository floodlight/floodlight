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
import static org.projectfloodlight.openflow.protocol.OFVersion.*;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.protocol.OFFactories;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import org.junit.*;

/**
 * Created by qing wang on 5/18/17.
 */

public class StatisticsTest extends FloodlightTestCase {

    private FloodlightContext cntx;
    private FloodlightModuleContext fmc;
    private MockThreadPoolService threadpool;
    private IOFSwitchService switchService;

    private static final OFFactory factory10 = OFFactories.getFactory(OFVersion.OF_10);
    private static final OFFactory factory11 = OFFactories.getFactory(OFVersion.OF_11);
    private static final OFFactory factory12 = OFFactories.getFactory(OFVersion.OF_12);
    private static final OFFactory factory13 = OFFactories.getFactory(OFVersion.OF_13);
    private static final OFFactory factory14 = OFFactories.getFactory(OFVersion.OF_14);
    private static final OFFactory factory15 = OFFactories.getFactory(OFVersion.OF_15);
    private StatisticsCollector statsCollector;
    public static IOFSwitch sw_OF11, sw_OF12, sw_OF13, sw_OF14, sw_OF15;
    private DatapathId switchDPID = DatapathId.of(1L);

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

        threadpool.init(fmc);
        statsCollector.init(fmc);
        threadpool.startUp(fmc);

        // Create OpenFlow11 Mock Switch
        sw_OF11 = EasyMock.createMock(IOFSwitch.class);
        sw_OF11 = getSwitchByOFVersion(factory11);

        // Create OpenFlow12 Mock Switch
        sw_OF12 = EasyMock.createMock(IOFSwitch.class);
        sw_OF12 = getSwitchByOFVersion(factory12);

        // Create OpenFlow13 Mock Switch
        sw_OF13 = EasyMock.createMock(IOFSwitch.class);
        sw_OF13 = getSwitchByOFVersion(factory13);

        // Create OpenFlow14 Mock Switch
        sw_OF14 = EasyMock.createMock(IOFSwitch.class);
        sw_OF14 = getSwitchByOFVersion(factory14);

        // Create OpenFlow15 Mock Switch
        sw_OF15 = EasyMock.createMock(IOFSwitch.class);
        sw_OF15 = getSwitchByOFVersion(factory15);

    }

    private IOFSwitch getSwitchByOFVersion(OFFactory factory) {
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);

        OFVersion openflowVer = factory.getVersion();
        switch (openflowVer) {
            case OF_11:
            case OF_12:
            case OF_13:
                reset(sw);
                expect(sw.getId()).andReturn(switchDPID).anyTimes();
                expect(sw.getOFFactory()).andReturn(factory).anyTimes();
                expect(sw.getPort(OFPort.of(1))).andReturn(factory.buildPortDesc()
                        .setPortNo(OFPort.of(1))
                        .setName("eth1")
                        .setCurrSpeed(100L)
                        .build()).anyTimes();
                replay(sw);
                break;

            case OF_14:
            case OF_15:
                reset(sw);
                expect(sw.getId()).andReturn(switchDPID).anyTimes();
                expect(sw.getOFFactory()).andReturn(factory).anyTimes();
                expect(sw.getPort(OFPort.of(1))).andReturn(factory.buildPortDesc()
                        .setPortNo(OFPort.of(1))
                        .setName("eth1")
                        .setProperties(Collections.singletonList(factory.buildPortDescPropEthernet().setCurrSpeed(100L).build()))
                        .build()).anyTimes();
                replay(sw);
                break;

            default:
                break;

        }

        return sw;
    }


    /**
     * Test getSpeed() method can work with Openflow 1.1
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF11() throws Exception {
        NodePortTuple npt = new NodePortTuple(sw_OF11.getId(), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF11.getId(), sw_OF11);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }

    /**
     * Test getSpeed() method can work with Openflow 1.2
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF12() throws Exception {
        NodePortTuple npt = new NodePortTuple(sw_OF12.getId(), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF12.getId(), sw_OF12);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }

    /**
     * Test getSpeed() method can work with Openflow 1.3
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF13() throws Exception {
        NodePortTuple npt = new NodePortTuple(sw_OF13.getId(), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF13.getId(), sw_OF13);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }

    /**
     * Test getSpeed() method can work with Openflow 1.4
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF14() throws Exception {
        NodePortTuple npt = new NodePortTuple(sw_OF14.getId(), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF14.getId(), sw_OF14);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }

    /**
     * Test getSpeed() method can work with Openflow 1.5
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeedOF15() throws Exception {
        NodePortTuple npt = new NodePortTuple(sw_OF15.getId(), OFPort.of(1));

        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw_OF15.getId(), sw_OF15);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();

        long speed = statsSpeedCollector.getSpeed(npt);
        assertEquals(speed, 100L);

    }

}
