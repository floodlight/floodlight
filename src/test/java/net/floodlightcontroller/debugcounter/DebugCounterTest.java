package net.floodlightcontroller.debugcounter;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugcounter.DebugCounter.DebugCounterInfo;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterType;
import net.floodlightcontroller.test.FloodlightTestCase;

public class DebugCounterTest extends FloodlightTestCase {
    DebugCounter dc;
    protected static Logger log = LoggerFactory.getLogger(DebugCounterTest.class);
    IDebugCounter S1, S2, S1_pi, S1_pi_d, S1_pi_e, S1_po, L_t;
    List<DebugCounterInfo> dclist;

    @Override
    @Before
    public void setUp() throws Exception {
        dc = new DebugCounter();
        S1 =       dc.registerCounter("switch", "01", "switch01",
                                      CounterType.ALWAYS_COUNT);
        S2 =       dc.registerCounter("switch", "02", "switch02",
                                      CounterType.ALWAYS_COUNT);
        S1_pi =    dc.registerCounter("switch", "01/pktin",
                                      "switch01-pktin",
                                      CounterType.ALWAYS_COUNT);
        S1_pi_d =  dc.registerCounter("switch", "01/pktin/drops",
                                      "switch01-pktin drops for all reasons",
                                      CounterType.ALWAYS_COUNT, "warn");
        S1_pi_e =  dc.registerCounter("switch", "01/pktin/err",
                                      "switch01-pktin errors",
                                      CounterType.ALWAYS_COUNT, "error", "snmp");
        S1_po =    dc.registerCounter("switch", "01/pktout",
                                      "switch01-pktout",
                                      CounterType.ALWAYS_COUNT);
        L_t =      dc.registerCounter("linkd", "tunnel",
                                      "tunnel links",
                                      CounterType.ALWAYS_COUNT);
    }


    @Test
    public void testBasicCounterWorking() {
        dc.printAllCounterIds();
        S1.updateCounterNoFlush();
        assertEquals(S1.getCounterValue(), 0);
        S1.updateCounterWithFlush();
        assertEquals(S1.getCounterValue(), 2);
        S1.updateCounterNoFlush(30);
        assertEquals(S1.getCounterValue(), 2);
        S1.updateCounterWithFlush(10);
        assertEquals(S1.getCounterValue(), 42);
        S1.updateCounterNoFlush();
        assertEquals(S1.getCounterValue(), 42);
        dc.flushCounters();
        assertEquals(S1.getCounterValue(), 43);
    }

    @Test
    public void testCounterHierarchy() {
        S1.updateCounterNoFlush();
        S2.updateCounterNoFlush(2);
        L_t.updateCounterNoFlush(3);
        S1_pi.updateCounterNoFlush(10);
        S1_po.updateCounterNoFlush(20);
        S1_pi_d.updateCounterNoFlush(100);
        S1_pi_e.updateCounterNoFlush(105);
        dc.flushCounters();
        checkCounters(1, 2, 3, 10, 20, 100, 105);
    }

    private void checkCounters(long S1_val, long S2_val, long L_t_val, // 1st level
                               long S1_pi_val, long S1_po_val,         // 2nd level
                               long S1_pi_d_val, long S1_pi_e_val) {   // 3rd level
        assertEquals(S1.getCounterValue(), S1_val);
        assertEquals(S2.getCounterValue(), S2_val);
        assertEquals(L_t.getCounterValue(), L_t_val);
        assertEquals(S1_pi.getCounterValue(), S1_pi_val);
        assertEquals(S1_po.getCounterValue(), S1_po_val);
        assertEquals(S1_pi_d.getCounterValue(), S1_pi_d_val);
        assertEquals(S1_pi_e.getCounterValue(), S1_pi_e_val);
    }

    @Test
    public void testBasicCounterReset() {
        testCounterHierarchy();
        dc.resetCounterHierarchy("linkd", "tunnel");
        checkCounters(1, 2, 0, 10, 20, 100, 105);
        // missing counter
        dc.resetCounterHierarchy("switch", "S2");
        checkCounters(1, 2, 0, 10, 20, 100, 105);
        // missing module
        dc.resetCounterHierarchy("swicth", "02");
        checkCounters(1, 2, 0, 10, 20, 100, 105);
        // the actual counter
        dc.resetCounterHierarchy("switch", "02");
        checkCounters(1, 0, 0, 10, 20, 100, 105);
        // leafs
        dc.resetCounterHierarchy("switch", "01/pktin/err");
        checkCounters(1, 0, 0, 10, 20, 100, 0);
        dc.resetCounterHierarchy("switch", "01/pktin/drops");
        checkCounters(1, 0, 0, 10, 20, 0, 0);
    }

    @Test
    public void testHierarchicalCounterReset1() {
        testCounterHierarchy();
        dc.resetCounterHierarchy("switch", "01/pktin");
        checkCounters(1, 2, 3, 0, 20, 0, 0);
    }
    @Test
    public void testHierarchicalCounterReset2() {
        testCounterHierarchy();
        dc.resetCounterHierarchy("switch", "01");
        checkCounters(0, 2, 3, 0, 0, 0, 0);
    }
    @Test
    public void testHierarchicalCounterReset3() {
        testCounterHierarchy();
        dc.resetAllModuleCounters("switch");
        checkCounters(0, 0, 3, 0, 0, 0, 0);
        dc.resetAllModuleCounters("linkd");
        checkCounters(0, 0, 0, 0, 0, 0, 0);
        testCounterHierarchy();
    }
    @Test
    public void testHierarchicalCounterReset4() {
        testCounterHierarchy();
        dc.resetAllCounters();
        checkCounters(0, 0, 0, 0, 0, 0, 0);
        testCounterHierarchy();
    }

    private void verifyCounters(List<DebugCounterInfo> dclist, Long...longs ) {
        List<Long> a = Arrays.asList(longs.clone());
        for (DebugCounterInfo dci : dclist) {
            assertEquals(true, a.contains(dci.cvalue.get()));
        }
        assertEquals(dclist.size(), longs.length);
    }

    @Test
    public void testBasicCounterGet() {
        testCounterHierarchy();
        dclist = dc.getCounterHierarchy("switch", "02");
        verifyCounters(dclist, 2L);
        dclist = dc.getCounterHierarchy("linkd", "tunnel");
        verifyCounters(dclist, 3L);
        dclist = dc.getCounterHierarchy("switch", "01/pktin/err");
        verifyCounters(dclist, 105L);
        dclist = dc.getCounterHierarchy("switch", "01/pktin/drops");
        verifyCounters(dclist, 100L);
    }

    @Test
    public void testHierarchicalCounterGet() {
        testCounterHierarchy();
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 10L, 100L, 105L);
        dclist = dc.getCounterHierarchy("switch", "01");
        verifyCounters(dclist, 1L, 20L, 10L, 100L, 105L);
        dclist = dc.getModuleCounterValues("switch");
        verifyCounters(dclist, 2L, 1L, 20L, 10L, 100L, 105L);
        dclist = dc.getModuleCounterValues("linkd");
        verifyCounters(dclist, 3L);
        dclist = dc.getAllCounterValues();
        verifyCounters(dclist, 3L, 2L, 1L, 20L, 10L, 100L, 105L);
    }

    @Test
    public void testEnableDisableCounter() throws Exception {
        testCounterHierarchy();
        IDebugCounter S1_pi_u, S1_fm, S1_fm_d;

        S1_pi_u =  dc.registerCounter("switch", "01/pktin/unknowns",
                                      "switch01-pktin unknowns",
                                      CounterType.COUNT_ON_DEMAND, "warn");
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 0L, 10L, 100L, 105L);
        S1_pi_u.updateCounterWithFlush(112);
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 0L, 10L, 100L, 105L);

        dc.enableCtrOnDemand("switch", "01/pktin/unknowns");
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 0L, 10L, 100L, 105L);
        dc.flushCounters(); // required for sync of thread-local store to global store
        S1_pi_u.updateCounterWithFlush(112);
        assertEquals(112L, S1_pi_u.getCounterValue());
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 10L, 100L, 105L, 112L);
        S1_pi_u.updateCounterWithFlush();
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 10L, 100L, 105L, 113L);

        dc.disableCtrOnDemand("switch", "01/pktin/unknowns");
        S1_pi_u.updateCounterWithFlush();
        S1_pi_u.updateCounterWithFlush();
        S1_pi_u.updateCounterWithFlush();
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 10L, 100L, 105L, 0L);

        //cannot disable ALWAYS_COUNT counter
        dc.disableCtrOnDemand("switch", "01/pktin/err");
        S1_pi_e.updateCounterWithFlush();
        dclist = dc.getCounterHierarchy("switch", "01/pktin");
        verifyCounters(dclist, 10L, 100L, 106L, 0L);

        //enable/disable counter inside hierarchy
        S1_fm =  dc.registerCounter("switch", "01/fm",
                                      "switch01-flow-mods",
                                      CounterType.COUNT_ON_DEMAND, "warn");
        S1_fm_d =  dc.registerCounter("switch", "01/fm/dup",
                                      "switch01- duplicate flow mods",
                                      CounterType.ALWAYS_COUNT, "warn");
        S1_fm.updateCounterWithFlush(8000);
        S1_fm_d.updateCounterWithFlush(5000);
        dclist = dc.getCounterHierarchy("switch", "01/fm");
        verifyCounters(dclist, 5000L, 0L);
        dc.enableCtrOnDemand("switch", "01/fm");
        dc.flushCounters();
        S1_fm.updateCounterWithFlush(8000);
        S1_fm_d.updateCounterWithFlush(5000);
        dclist = dc.getCounterHierarchy("switch", "01/fm");
        verifyCounters(dclist, 10000L, 8000L);
        dc.disableCtrOnDemand("switch", "01/fm");
        S1_fm.updateCounterWithFlush(8000);
        S1_fm_d.updateCounterWithFlush(5000);
        dclist = dc.getCounterHierarchy("switch", "01/fm");
        verifyCounters(dclist, 15000L, 0L);
    }

    @Test
    public void testCounterReregistry() throws Exception {
        testCounterHierarchy();
        checkCounters(1, 2, 3, 10, 20, 100, 105);
        S1 =  dc.registerCounter("switch", "01", "switch01",
                                 CounterType.ALWAYS_COUNT);
        checkCounters(0, 2, 3, 0, 0, 0, 0); // switch 1 counter re-setted
        assertEquals(0, S1.getCounterValue());
    }

    @Test
    public void testContains() {
        testCounterHierarchy();
        assertTrue(dc.containsModuleName("switch"));
        assertTrue(dc.containsModuleName("linkd"));
        assertTrue(dc.containsModuleCounterHierarchy("switch", "01"));
        assertTrue(dc.containsModuleCounterHierarchy("switch", "02"));
        assertFalse(dc.containsModuleCounterHierarchy("switch", "03"));
        assertTrue(dc.containsModuleCounterHierarchy("switch", "01/pktin"));
        assertTrue(dc.containsModuleCounterHierarchy("switch", "01/pktout"));
        assertTrue(dc.containsModuleCounterHierarchy("switch", "01/pktin/err"));
        assertTrue(dc.containsModuleCounterHierarchy("switch", "01/pktin/drops"));
        assertFalse(dc.containsModuleCounterHierarchy("switch", "01/pktin/unknowns"));
        assertFalse(dc.containsModuleCounterHierarchy("switch", "01/pktin/errr"));
    }

    @Test
    public void testMetadata() {
        testCounterHierarchy();
        dclist = dc.getCounterHierarchy("switch", "01/pktin/err");
        for (DebugCounterInfo dc : dclist) {
            assertTrue(dc.cinfo.metaData[0].equals("error"));
            assertTrue(dc.cinfo.metaData[1].equals("snmp"));
            log.info("metadata: {}", Arrays.toString(dc.cinfo.getMetaData()));
        }
        verifyCounters(dclist, 105L);

        dclist = dc.getCounterHierarchy("switch", "02");
        for (DebugCounterInfo dc : dclist) {
            assertTrue(dc.cinfo.getMetaData().length == 0);
            log.info("metadata: {}", Arrays.toString(dc.cinfo.getMetaData()));
        }
    }


    @Test
    public void testMissingHierarchy() {

    }
}
