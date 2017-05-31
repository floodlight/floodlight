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
import net.floodlightcontroller.threadpool.IThreadPoolService;

import java.util.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Created by qing wang on 5/18/17.
 */
@RunWith(Parameterized.class)
public class StatisticsTest extends FloodlightTestCase {

    private FloodlightContext cntx;
    private FloodlightModuleContext fmc;
    private MockThreadPoolService threadpool;
    private IOFSwitchService switchService;
    private StatisticsCollector statsCollector;

    private static OFVersion inputOFVersion;
    private static Long expectedSpeed;

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

    }

    /**
     * This constructor will be called for each row of test data collection
     * @param inputOFVersion
     * @param expectedSpeed
     */
    public StatisticsTest(OFVersion inputOFVersion, Long expectedSpeed) {
        this.inputOFVersion = inputOFVersion;
        this.expectedSpeed = expectedSpeed;
    }

    /**
     * A Collection of Junit Test with various "inputFactory" and "expectedSpeed"
     * @return
     */
    @Parameterized.Parameters(name = "Test {index}: {0}, Port Speed is {1}")
    public static Iterable<Object[]> testData() {
        return Arrays.asList(new Object[][] {
            { OFVersion.OF_11, 100L },
            { OFVersion.OF_12, 100L },
            { OFVersion.OF_13, 100L },
            { OFVersion.OF_14, 100L },
            { OFVersion.OF_15, 100L },
        });
    }

    /**
     * Test getSpeed() method works with different Openflow versions
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentPortSpeed() throws Exception {
        IOFSwitch sw = getSwitchByOFVersion(inputOFVersion);
        NodePortTuple npt = new NodePortTuple(DatapathId.of(1), OFPort.of(1));
        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw.getId(), sw);
        getMockSwitchService().setSwitches(switchMap);

        StatisticsCollector.PortStatsCollector statsSpeedCollector = statsCollector.new PortStatsCollector();
        Long speed = statsSpeedCollector.getSpeed(npt);

        assertEquals(speed, expectedSpeed);

    }

    private IOFSwitch getSwitchByOFVersion(OFVersion inputOFVersion) {
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        OFFactory inputFactory = OFFactories.getFactory(inputOFVersion);

        reset(sw);
        expect(sw.getId()).andReturn(DatapathId.of(1L)).anyTimes();
        expect(sw.getOFFactory()).andReturn(inputFactory).anyTimes();

        switch (inputOFVersion){
            case OF_11:
            case OF_12:
            case OF_13:
                expect(sw.getPort(OFPort.of(1))).andReturn(inputFactory.buildPortDesc()
                        .setPortNo(OFPort.of(1))
                        .setName("eth1")
                        .setCurrSpeed(100L)
                        .build()).anyTimes();
                replay(sw);
                break;

            case OF_14:
            case OF_15:
                expect(sw.getPort(OFPort.of(1))).andReturn(inputFactory.buildPortDesc()
                        .setPortNo(OFPort.of(1))
                        .setName("eth1")
                        .setProperties(Collections.singletonList(inputFactory.buildPortDescPropEthernet().
                                setCurrSpeed(100L).
                                build()))
                        .build()).anyTimes();
                replay(sw);

        }
        return sw;
    }

}
