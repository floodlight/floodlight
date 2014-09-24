package net.floodlightcontroller.debugcounter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;

import com.google.common.collect.Lists;

public class DebugCounterServiceTest {
    private DebugCounterServiceImpl counterService;

    @Before
    public void setUp() {
        counterService = new DebugCounterServiceImpl();
    }

    private void
    verifyCounters(List<CounterExpectation> expectedCounters,
                   List<DebugCounterResource> actualCounters) {
        List<String> expectedNames = new ArrayList<>();
        List<String> actualNames = new ArrayList<>();

        for (CounterExpectation ce: expectedCounters) {
            expectedNames.add(ce.getModuleName() + "/"
                             + ce.getCounterHierarchy());
        }
        for (DebugCounterResource cr: actualCounters) {
            actualNames.add(cr.getModuleName() + "/"
                           + cr.getCounterHierarchy());
        }
        assertEquals(expectedNames, actualNames);

        Iterator<CounterExpectation> expectedIter = expectedCounters.iterator();
        Iterator<DebugCounterResource> actualIter = actualCounters.iterator();

        while(expectedIter.hasNext()) {
            // we already know that expected and actual have the same length
            CounterExpectation ce = expectedIter.next();
            DebugCounterResource cr = actualIter.next();
            String curFullName = ce.getModuleName() + "/"
                    + ce.getCounterHierarchy();
            assertEquals(curFullName,
                         ce.getModuleName(), cr.getModuleName());
            assertEquals(curFullName,
                         ce.getCounterHierarchy(), cr.getCounterHierarchy());
            assertEquals(curFullName,
                         ce.getDescription(), cr.getCounterDesc());
            assertEquals(curFullName,
                         ce.getValue(), cr.getCounterValue().longValue());
            assertEquals(curFullName,
                         ce.getMetaData(), cr.getMetadata());

        }
    }


    private void verifyCountersEmpty(Iterable<DebugCounterResource> actual) {
        assertTrue(Lists.newArrayList(actual).isEmpty());
    }

    @Test
    public void testExceptions() {
        //
        // Module registration
        //
        try {
            counterService.registerModule(null);
            fail("Expected Exception not thrown");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            counterService.registerModule("");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            counterService.registerModule("bar/baz");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }

        //
        // counter registration
        //
        try {
            counterService.registerCounter(null, "bar", "Description");
            fail("Expected Exception not thrown");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            counterService.registerCounter("", "bar", "Description");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            counterService.registerCounter("foo", "bar", "Description");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Add the module to get past this exception
        counterService.registerModule("foo");
        try {
            counterService.registerCounter("foo", null, "Description");
            fail("Expected Exception not thrown");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            counterService.registerCounter("foo", "", "Description");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            counterService.registerCounter("foo", "bar/baz", "Description");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            // test with null array for varargs
            counterService.registerCounter("foo", "bar", "Description",
                                           (MetaData[])null);
            fail("Expected Exception not thrown");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            // test with null element for varargs
            counterService.registerCounter("foo", "bar", "Description",
                                           (MetaData)null);
            fail("Expected Exception not thrown");
        } catch (NullPointerException e) {
            // expected
        }

        List<DebugCounterResource> counters =
                Lists.newArrayList(counterService.getAllCounterValues());
        assertTrue(counters.isEmpty());

        counterService.registerCounter("foo", "bar", "Desc");
        try {
            counterService.registerCounter("foo", "bar/bar/baz", "Desc");
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }

    }

    private static class CounterExpectation {
        private String moduleName;
        private String counterHierarchy;
        private String description;
        private final Set<MetaData> metaData = EnumSet.noneOf(MetaData.class);
        private long value;

        static CounterExpectation create() {
            return new CounterExpectation();
        }
        String getModuleName() {
            return moduleName;
        }
        CounterExpectation moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }
        String getCounterHierarchy() {
            return counterHierarchy;
        }
        CounterExpectation counterHierarchy(String counterHierarchy) {
            this.counterHierarchy = counterHierarchy;
            return this;
        }
        String getDescription() {
            return description;
        }
        CounterExpectation description(String description) {
            this.description = description;
            return this;
        }
        long getValue() {
            return value;
        }
        CounterExpectation value(long value) {
            this.value = value;
            return this;
        }
        Set<MetaData> getMetaData() {
            return metaData;
        }
        CounterExpectation addMetaData(MetaData m) {
            this.metaData.add(m);
            return this;
        }
    }


    @Test
    public void test() {
        verifyCountersEmpty(counterService.getAllCounterValues());
        List<CounterExpectation> expectedCounters;

        assertTrue(counterService.registerModule("moduleB"));
        assertFalse(counterService.registerModule("moduleB"));
        assertTrue(counterService.registerModule("moduleA"));
        assertFalse(counterService.registerModule("moduleA"));
        assertTrue(counterService.registerModule("moduleC"));
        assertTrue(counterService.registerModule("moduleD"));

        IDebugCounter cAaac =
                counterService.registerCounter("moduleA", "aac",
                                               "the aac counter",
                                               MetaData.WARN);
        CounterExpectation eAaac = CounterExpectation.create()
                .moduleName("moduleA")
                .counterHierarchy("aac")
                .description("the aac counter")
                .addMetaData(MetaData.WARN);
        expectedCounters = Lists.newArrayList(eAaac);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaac.increment();
        eAaac.value(1);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaac.increment();
        eAaac.value(2);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        IDebugCounter cAaaa =
                counterService.registerCounter("moduleA", "aaa",
                                               "the aaa counter",
                                               MetaData.ERROR,
                                               MetaData.DROP);
        CounterExpectation eAaaa = CounterExpectation.create()
                .moduleName("moduleA")
                .counterHierarchy("aaa")
                .description("the aaa counter")
                .addMetaData(MetaData.DROP)
                .addMetaData(MetaData.ERROR);
        expectedCounters = Lists.newArrayList(eAaaa, eAaac);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaa.increment();
        eAaaa.value(1);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        IDebugCounter cAaab =
                counterService.registerCounter("moduleA", "aab",
                                               "the aab counter");
        CounterExpectation eAaab = CounterExpectation.create()
                .moduleName("moduleA")
                .counterHierarchy("aab")
                .description("the aab counter");
        expectedCounters = Lists.newArrayList(eAaaa, eAaab, eAaac);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaab.increment();
        eAaab.value(1);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());


        IDebugCounter cAaaaFoo =
                counterService.registerCounter("moduleA", "aaa/foo",
                                               "the aaa/foo counter");
        CounterExpectation eAaaaFoo = CounterExpectation.create()
                .moduleName("moduleA")
                .counterHierarchy("aaa/foo")
                .description("the aaa/foo counter");
        expectedCounters = Lists.newArrayList(eAaaa, eAaaaFoo, eAaab, eAaac);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaaFoo.increment();
        cAaaaFoo.increment();
        eAaaaFoo.value(2);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());


        IDebugCounter cAaaaBar =
                counterService.registerCounter("moduleA", "aaa/bar",
                                               "the aaa/bar counter");
        CounterExpectation eAaaaBar = CounterExpectation.create()
                .moduleName("moduleA")
                .counterHierarchy("aaa/bar")
                .description("the aaa/bar counter");
        expectedCounters = Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                              eAaab, eAaac);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaaBar.add(42);
        eAaaaBar.value(42);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());


        IDebugCounter cBfoo =
                counterService.registerCounter("moduleB", "foo",
                                               "the foo counter of B");
        CounterExpectation eBfoo = CounterExpectation.create()
                .moduleName("moduleB")
                .counterHierarchy("foo")
                .description("the foo counter of B");
        expectedCounters = Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                              eAaab, eAaac, eBfoo);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cBfoo.increment();
        cBfoo.increment();
        cBfoo.increment();
        eBfoo.value(3);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());


        IDebugCounter cCfoobar =
                counterService.registerCounter("moduleC", "foobar",
                                               "the foobar counter of C");
        CounterExpectation eCfoobar = CounterExpectation.create()
                .moduleName("moduleC")
                .counterHierarchy("foobar")
                .description("the foobar counter of C");
        expectedCounters = Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                              eAaab, eAaac, eBfoo, eCfoobar);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cCfoobar.increment();
        cCfoobar.increment();
        cCfoobar.increment();
        cCfoobar.increment();
        eCfoobar.value(4);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        IDebugCounter cCfoobar2 =
                counterService.registerCounter("moduleC", "foobar",
                                               "the foobar counter of C");
        assertSame(cCfoobar, cCfoobar2);
        eCfoobar.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());



        IDebugCounter cBfooBar =
                counterService.registerCounter("moduleB", "foo/bar",
                                               "the foo/bar counter of B");
        CounterExpectation eBfooBar = CounterExpectation.create()
                .moduleName("moduleB")
                .counterHierarchy("foo/bar")
                .description("the foo/bar counter of B");
        expectedCounters = Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                              eAaab, eAaac, eBfoo,
                                              eBfooBar, eCfoobar);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cBfooBar.increment();
        cBfooBar.increment();
        cBfooBar.increment();
        cBfooBar.increment();
        cBfooBar.increment();
        eBfooBar.value(5);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());



        IDebugCounter cAaaaFooBar =
                counterService.registerCounter("moduleA", "aaa/foo/bar",
                                               "the aaa/foo/bar counter");
        CounterExpectation eAaaaFooBar = CounterExpectation.create()
                .moduleName("moduleA")
                .counterHierarchy("aaa/foo/bar")
                .description("the aaa/foo/bar counter");
        expectedCounters = Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                              eAaaaFooBar,
                                              eAaab, eAaac, eBfoo,
                                              eBfooBar, eCfoobar);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaaFooBar.increment();
        cAaaaFooBar.increment();
        cAaaaFooBar.add(21);
        eAaaaFooBar.value(23);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());


        IDebugCounter cAaaaFooBar2 =
                counterService.registerCounter("moduleA", "aaa/foo/bar",
                                               "the aaa/foo/bar counter");
        assertSame(cAaaaFooBar, cAaaaFooBar2);
        eAaaaFooBar.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaaFooBar.add(21);
        eAaaaFooBar.value(21);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        //
        // Test methods that read the counter hierarchy
        //

        // eAaaa,
        // eAaaaBar,
        // eAaaaFoo,
        // eAaaaFooBar,
        // eAaab,
        // eAaac,
        // eBfoo,
        // eBfooBar,
        // eCfoobar
        verifyCounters(Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                          eAaaaFooBar, eAaab, eAaac),
                       counterService.getModuleCounterValues("moduleA"));
        verifyCounters(Lists.newArrayList(eBfoo, eBfooBar),
                       counterService.getModuleCounterValues("moduleB"));
        verifyCounters(Lists.newArrayList(eCfoobar),
                       counterService.getModuleCounterValues("moduleC"));
        verifyCountersEmpty(counterService.getModuleCounterValues("moduleD"));
        verifyCountersEmpty(counterService.getModuleCounterValues("FOOBAR"));


        verifyCounters(Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                          eAaaaFooBar),
                       counterService.getCounterHierarchy("moduleA", "aaa"));
        verifyCounters(Lists.newArrayList(eAaaaBar),
                       counterService.getCounterHierarchy("moduleA", "aaa/bar"));
        verifyCounters(Lists.newArrayList(eAaaaFoo, eAaaaFooBar),
                       counterService.getCounterHierarchy("moduleA", "aaa/foo"));
        verifyCounters(Lists.newArrayList(eAaaaFooBar),
                       counterService.getCounterHierarchy("moduleA", "aaa/foo/bar"));
        verifyCountersEmpty(
                counterService.getCounterHierarchy("moduleA", "aaa/foo/bar/XXX"));
        verifyCountersEmpty(
                counterService.getCounterHierarchy("moduleA", "XXX"));

        verifyCounters(Lists.newArrayList(eBfoo, eBfooBar),
                       counterService.getCounterHierarchy("moduleB", "foo"));
        verifyCounters(Lists.newArrayList(eBfooBar),
                       counterService.getCounterHierarchy("moduleB", "foo/bar"));
        verifyCountersEmpty(
                counterService.getCounterHierarchy("moduleB", "foo/baz"));

        //
        // Test reset. We already tested that re-registering a single counter
        // resets the counter.
        //

        // eAaaa,
        // eAaaaBar,
        // eAaaaFoo,
        // eAaaaFooBar,
        // eAaab,
        // eAaac,
        // eBfoo,
        // eBfooBar,
        // eCfoobar
        IDebugCounter cAaaaFoo2 =
                counterService.registerCounter("moduleA", "aaa/foo",
                                               "the aaa/foo counter");
        assertSame(cAaaaFoo, cAaaaFoo2);
        eAaaaFoo.value(0);
        eAaaaFooBar.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaaFoo.add(100);
        cAaaaFooBar.add(200);
        eAaaaFoo.value(100);
        eAaaaFooBar.value(200);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        counterService.resetCounterHierarchy("moduleA", "aaa/foo");
        eAaaaFoo.value(0);
        eAaaaFooBar.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        cAaaaFoo.add(100);
        cAaaaFooBar.add(200);
        eAaaaFoo.value(100);
        eAaaaFooBar.value(200);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        counterService.resetCounterHierarchy("moduleA", "aaa");
        eAaaa.value(0);
        eAaaaBar.value(0);
        eAaaaFoo.value(0);
        eAaaaFooBar.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        eAaaa.value(10);
        eAaaaBar.value(15);
        eAaaaFoo.value(20);
        eAaaaFooBar.value(30);
        cAaaa.add(10);
        cAaaaBar.add(15);
        cAaaaFoo.add(20);
        cAaaaFooBar.add(30);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        counterService.resetAllModuleCounters("moduleA");
        eAaaa.value(0);
        eAaaaBar.value(0);
        eAaaaFoo.value(0);
        eAaaaFooBar.value(0);
        eAaab.value(0);
        eAaac.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
        eAaaa.value(10);
        eAaaaBar.value(15);
        eAaaaFoo.value(20);
        eAaaaFooBar.value(30);
        eAaab.value(40);
        eAaac.value(50);
        cAaaa.add(10);
        cAaaaBar.add(15);
        cAaaaFoo.add(20);
        cAaaaFooBar.add(30);
        cAaab.add(40);
        cAaac.add(50);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());

        // re-check getters
        verifyCounters(Lists.newArrayList(eAaaa, eAaaaBar, eAaaaFoo,
                                          eAaaaFooBar),
                       counterService.getCounterHierarchy("moduleA", "aaa"));
        verifyCounters(Lists.newArrayList(eAaaaBar),
                       counterService.getCounterHierarchy("moduleA", "aaa/bar"));
        verifyCounters(Lists.newArrayList(eAaaaFoo, eAaaaFooBar),
                       counterService.getCounterHierarchy("moduleA", "aaa/foo"));

        counterService.resetAllCounters();
        eAaaa.value(0);
        eAaaaBar.value(0);
        eAaaaFoo.value(0);
        eAaaaFooBar.value(0);
        eAaab.value(0);
        eAaac.value(0);
        eBfoo.value(0);
        eBfooBar.value(0);
        eCfoobar.value(0);
        verifyCounters(expectedCounters, counterService.getAllCounterValues());
    }

}
