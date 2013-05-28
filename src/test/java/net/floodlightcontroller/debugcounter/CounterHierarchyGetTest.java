package net.floodlightcontroller.debugcounter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.debugcounter.DebugCounter.CounterIndexStore;
import net.floodlightcontroller.debugcounter.DebugCounter.RetCtrInfo;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CounterHierarchyGetTest extends FloodlightTestCase {
    DebugCounter dc;
    protected static Logger log = LoggerFactory.getLogger(CounterHierarchyGetTest.class);

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
    public void testBasicCounterGet() {
        RetCtrInfo rci = dc.getCounterId("linkd", "linkevent");
        //exp.levels = new String[IDebugCounterService.MAX_HIERARCHY];
        exp.levels = "linkevent".split("/");
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 24;
        exp.levels[0] = "linkevent";
        assertEquals(exp, rci);
    }

    @Test
    public void testHierarchicalCounterGet1() {
        Map<String, CounterIndexStore> x =  mctr.get("switch");
        x.put("00:00:00:00:00:00:00:01",
              dc.new CounterIndexStore(10, null));

        // 1-level counter exists
        String counterName = "00:00:00:00:00:00:00:01";
        RetCtrInfo rci1 = dc.getCounterId("switch",counterName);
        exp.levels = counterName.split("/");
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 10;
        exp.levels[0] = counterName;
        assertEquals(exp, rci1);

        // 1-level counter does not exist
        counterName = "00:00:00:00:00:00:00:02";
        RetCtrInfo rci2 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.foundUptoLevel = 0;
        exp.hierarchical = false;
        exp.levels = counterName.split("/");
        exp.ctrIds[0] = -1;
        printRCI("got==> ", rci2);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci2);

        // 2-level hierarchical counter does not exist
        counterName = "00:00:00:00:00:00:00:01/pktin";
        RetCtrInfo rci3 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 10;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci3);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci3);

        // 3-level hierarchical counter does not exist
        counterName = "00:00:00:00:00:00:00:01/pktin/drops";
        RetCtrInfo rci4 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 10;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci4);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci4);

        // 4-level hierarchical counter does not exist
        counterName = "00:00:00:00:00:00:00:01/pktin/drops/extra";
        RetCtrInfo rci5 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 10;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci5);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci5);

    }

    @Test
    public void testHierarchicalCounterGet2() {
        Map<String, CounterIndexStore> x =  mctr.get("switch");
        // single level counter
        x.put("00:00:00:00:00:00:00:01",
              dc.new CounterIndexStore(10, null));
        // two level counter
        x.put("00:00:00:00:00:00:00:03",
              dc.new CounterIndexStore(30, new ConcurrentHashMap<String,CounterIndexStore>()));
        x.get("00:00:00:00:00:00:00:03").nextLevel.put("pktin",
                                                       dc.new CounterIndexStore(333, null));

        // 1-level counter exists
        String counterName = "00:00:00:00:00:00:00:01";
        RetCtrInfo rci1 = dc.getCounterId("switch",counterName);
        exp.levels = counterName.split("/");
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 10;
        exp.levels[0] = counterName;
        assertEquals(exp, rci1);

        // 1-level counter does not exist
        counterName = "00:00:00:00:00:00:00:02";
        RetCtrInfo rci2 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = false;
        exp.levels = counterName.split("/");
        exp.foundUptoLevel = 0;
        exp.ctrIds[0] = -1;
        printRCI("got==> ", rci2);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci2);

        // 2-level hierarchical counter does not exist
        counterName = "00:00:00:00:00:00:00:01/pktin";
        RetCtrInfo rci3 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.ctrIds[0] = 10;
        exp.foundUptoLevel = 1;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci3);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci3);

        // 2-level hierarchical counter DOES exist
        counterName = "00:00:00:00:00:00:00:03/pktin";
        RetCtrInfo rci3x = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.hierarchical = true;
        exp.foundUptoLevel = 2;
        exp.ctrIds[0] = 30;
        exp.ctrIds[1] = 333;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci3x);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci3x);

        // 2-level hierarchical counter does NOT exist
        counterName = "00:00:00:00:00:00:00:03/pktout";
        RetCtrInfo rci3y = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 30;
        exp.ctrIds[1] = -1;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci3y);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci3y);

        // 3-level hierarchical counter does not exist
        counterName = "00:00:00:00:00:00:00:03/pktin/drops";
        RetCtrInfo rci4 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = false;
        exp.hierarchical = true;
        exp.foundUptoLevel = 2;
        exp.ctrIds[0] = 30;
        exp.ctrIds[1] = 333;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci4);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci4);

    }

    @Test
    public void testHierarchicalCounterGet3() {
        Map<String, CounterIndexStore> x =  mctr.get("switch");
        // single level counter
        x.put("00:00:00:00:00:00:00:01",
              dc.new CounterIndexStore(10, null));
        // two level counter
        x.put("00:00:00:00:00:00:00:03",
              dc.new CounterIndexStore(30, new ConcurrentHashMap<String,CounterIndexStore>()));
        x.get("00:00:00:00:00:00:00:03").nextLevel.put("pktin",
                                                       dc.new CounterIndexStore(333, null));
        // three level counter
        x.put("00:00:00:00:00:00:00:05",
              dc.new CounterIndexStore(50, new ConcurrentHashMap<String,CounterIndexStore>()));
        x.get("00:00:00:00:00:00:00:05")
            .nextLevel.put("pktin",
                           dc.new CounterIndexStore(555, new ConcurrentHashMap<String,CounterIndexStore>()));
        x.get("00:00:00:00:00:00:00:05").nextLevel.get("pktin").nextLevel.
            put("drops", dc.new CounterIndexStore(1056, null));

        // 1-level counter exists
        String counterName = "00:00:00:00:00:00:00:01";
        RetCtrInfo rci1 = dc.getCounterId("switch",counterName);
        exp.levels = counterName.split("/");
        exp.allLevelsFound = true;
        exp.foundUptoLevel = 1;
        exp.ctrIds[0] = 10;
        exp.levels[0] = counterName;
        assertEquals(exp, rci1);

        // 2-level hierarchical counter DOES exist
        counterName = "00:00:00:00:00:00:00:03/pktin";
        RetCtrInfo rci3x = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.hierarchical = true;
        exp.foundUptoLevel = 2;
        exp.ctrIds[0] = 30;
        exp.ctrIds[1] = 333;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci3x);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci3x);

        // 3-level hierarchical counter DOES exist
        counterName = "00:00:00:00:00:00:00:05/pktin/drops";
        RetCtrInfo rci4 = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.hierarchical = true;
        exp.foundUptoLevel = 3;
        exp.ctrIds[0] = 50;
        exp.ctrIds[1] = 555;
        exp.ctrIds[2] = 1056;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci4);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci4);

        // querying only 2 levels of a 3 level counter
        counterName = "00:00:00:00:00:00:00:05/pktin";
        RetCtrInfo rci4x = dc.getCounterId("switch", counterName);
        exp.allLevelsFound = true;
        exp.hierarchical = true;
        exp.foundUptoLevel = 2;
        exp.ctrIds[0] = 50;
        exp.ctrIds[1] = 555;
        exp.ctrIds[2] = -1;
        exp.levels = counterName.split("/");
        printRCI("got==> ", rci4x);
        printRCI("exp==> ", exp);
        assertEquals(exp, rci4x);
    }


    public void printRCI(String hdr, RetCtrInfo rci) {
        log.info(hdr+"found={}, hcy={}, foundUL= {}, idsFound={}, incomingLevels={}",
                 new Object[] {rci.allLevelsFound, rci.hierarchical,
                               rci.foundUptoLevel,
                               rci.ctrIds, rci.levels});
    }

}
