package net.floodlightcontroller.debugcounter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugcounter.DebugCounter.CounterIndexStore;
import net.floodlightcontroller.debugcounter.DebugCounter.RetCtrInfo;
import net.floodlightcontroller.test.FloodlightTestCase;

public class CounterHierarchyBelowTest extends FloodlightTestCase {
    DebugCounter dc;
    protected static Logger log = LoggerFactory.getLogger(CounterHierarchyBelowTest.class);

    ConcurrentHashMap<String, ConcurrentHashMap<String, CounterIndexStore>> mctr;
    ArrayList<Integer> exp;


    @Override
    @Before
    public void setUp() throws Exception {
        dc = new DebugCounter();
        mctr = dc.moduleCounters;

        mctr.put("switch", new ConcurrentHashMap<String, CounterIndexStore>());
        RetCtrInfo rci = dc.getCounterId("switch", "01");
        dc.addToModuleCounterHierarchy("switch", 4, rci);
        rci = dc.getCounterId("switch", "01/pktin");
        dc.addToModuleCounterHierarchy("switch", 42, rci);
        rci = dc.getCounterId("switch", "01/pktout");
        dc.addToModuleCounterHierarchy("switch", 47, rci);
        rci = dc.getCounterId("switch", "01/pktin/drops");
        dc.addToModuleCounterHierarchy("switch", 427, rci);
        rci = dc.getCounterId("switch", "01/pktin/err");
        dc.addToModuleCounterHierarchy("switch", 428, rci);

        rci = dc.getCounterId("switch", "02");
        dc.addToModuleCounterHierarchy("switch", 8, rci);

        mctr.put("linkd", new ConcurrentHashMap<String, CounterIndexStore>());
        rci = dc.getCounterId("linkd", "tunnel");
        dc.addToModuleCounterHierarchy("linkd", 2, rci);
        mctr.put("sinkd", new ConcurrentHashMap<String, CounterIndexStore>());
        rci = dc.getCounterId("sinkd", "tunnel");
        dc.addToModuleCounterHierarchy("sinkd", 5, rci);

        exp = new ArrayList<Integer>();
        List<Integer> temp =  Arrays.asList(4, 42, 47, 427, 428, 8, 2, 5);
        exp.addAll(temp);
    }

    private void isEqual(ArrayList<Integer> a, ArrayList<Integer> b) {
        if (a.size() != b.size() || !b.containsAll(a)) assertTrue(false);
    }


    @Test
    public void testHierarchyAll() {
        RetCtrInfo rci = dc.new RetCtrInfo();
        ArrayList<Integer> retval = new ArrayList<Integer>();

        for (String moduleName : mctr.keySet()) {
            ArrayList<Integer> ids = dc.getHierarchyBelow(moduleName, rci);
            retval.addAll(ids);
        }
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }


    @Test
    public void testHierarchy0() {
        RetCtrInfo rci = dc.getCounterId("switch", "");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(2, 5);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy0a() {
        RetCtrInfo rci = dc.getCounterId("linkd", "");
        ArrayList<Integer> retval = dc.getHierarchyBelow("linkd", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 427, 428, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy1() {
        RetCtrInfo rci = dc.getCounterId("switch", "01");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(4, 8, 2, 5);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy1a() {
        RetCtrInfo rci = dc.getCounterId("switch", "02");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 427, 428, 2, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy1b() {
        RetCtrInfo rci = dc.getCounterId("sinkd", "tunnel");
        ArrayList<Integer> retval = dc.getHierarchyBelow("sinkd", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 427, 428, 2, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy2() {
        RetCtrInfo rci = dc.getCounterId("switch", "01/pktin");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 2, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy2a() {
        RetCtrInfo rci = dc.getCounterId("switch", "01/pktout");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 427, 428, 2, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy2b() {
        RetCtrInfo rci = dc.getCounterId("switch", "02/pktin");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 427, 428, 2, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }

    @Test
    public void testHierarchy3() {
        RetCtrInfo rci = dc.getCounterId("switch", "01/pktin/drops");
        ArrayList<Integer> retval = dc.getHierarchyBelow("switch", rci);
        List<Integer> temp  = Arrays.asList(4, 42, 47, 427, 428, 2, 5, 8);
        exp.removeAll(temp);
        log.info("got==> {}, exp=> {}", retval, exp);
        isEqual(retval, exp);
    }



}
