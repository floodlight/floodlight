package net.floodlightcontroller.debugcounter;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugcounter.DebugCounter.CounterIndexStore;
import net.floodlightcontroller.debugcounter.DebugCounter.RetCtrInfo;
import net.floodlightcontroller.test.FloodlightTestCase;

public class CounterHierarchyPutTest extends FloodlightTestCase {
    DebugCounter dc;
    protected static Logger log = LoggerFactory.getLogger(CounterHierarchyPutTest.class);

    ConcurrentHashMap<String, ConcurrentHashMap<String, CounterIndexStore>> mctr;
    RetCtrInfo exp;

    @Override
    @Before
    public void setUp() throws Exception {
        dc = new DebugCounter();
        mctr = dc.moduleCounters;
        mctr.put("linkd", new ConcurrentHashMap<String, CounterIndexStore>());
        mctr.get("linkd").put("linkevent", dc.new CounterIndexStore(24, null));
        mctr.put("switch", new ConcurrentHashMap<String, CounterIndexStore>());
        exp = dc.new RetCtrInfo();
    }

    @Test
    public void testHierarchicalPut() {
        String counterName = "100hp";
        RetCtrInfo rci = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = false;
        exp.levels = counterName.split("/");
        printRCI("got ==>", rci);
        printRCI("exp ==>", exp);
        assertEquals(rci, exp);
        // add and then check for first level of hierarchy
        dc.addToModuleCounterHierarchy("switch", 45, rci);
        rci = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 1;
        exp.hierarchical = false;
        exp.ctrIds[0] = 45;
        exp.levels = counterName.split("/");
        printRCI("got ==>", rci);
        printRCI("exp ==>", exp);
        assertEquals(rci, exp);

        counterName = "100hp/pktin";
        rci = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.levels = counterName.split("/");
        exp.ctrIds[0] = 45;
        printRCI("got ==>", rci);
        printRCI("exp ==>", exp);
        assertEquals(rci, exp);
        dc.printAllCounterIds();
        // add and then check for 2nd level of hierarchy
        dc.addToModuleCounterHierarchy("switch", 77, rci);
        rci = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 2;
        exp.hierarchical = true;
        exp.ctrIds[0] = 45;
        exp.ctrIds[1] = 77;
        exp.levels = counterName.split("/");
        printRCI("got ==>", rci);
        printRCI("exp ==>", exp);
        dc.printAllCounterIds();
        assertEquals(rci, exp);

        counterName = "100hp/pktin/drops";
        rci = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.levels = counterName.split("/");
        exp.ctrIds[0] = 45;
        exp.ctrIds[1] = 77;
        exp.foundUptoLevel = 2;
        printRCI("got ==>", rci);
        printRCI("exp ==>", exp);
        assertEquals(rci, exp);
        dc.printAllCounterIds();
        // add and then check for 3rd level of hierarchy
        dc.addToModuleCounterHierarchy("switch", 132, rci);
        rci = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 3;
        exp.hierarchical = true;
        exp.ctrIds[0] = 45;
        exp.ctrIds[1] = 77;
        exp.ctrIds[2] = 132;
        exp.levels = counterName.split("/");
        printRCI("got ==>", rci);
        printRCI("exp ==>", exp);
        dc.printAllCounterIds();
        assertEquals(rci, exp);

    }

    @Test
    public void testOtherTest() {
        Integer[] test = new Integer[2000];
        log.info("it is: {}", test[56]);
    }

    private void printRCI(String hdr, RetCtrInfo rci) {
        log.info(hdr+"found={}, hcy={}, foundUL= {}, idsFound={}, incomingLevels={}",
                 new Object[] {rci.allLevelsFound, rci.hierarchical,
                               rci.foundUptoLevel,
                               rci.ctrIds, rci.levels});

    }

}
