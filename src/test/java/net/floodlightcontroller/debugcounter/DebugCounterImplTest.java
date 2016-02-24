package net.floodlightcontroller.debugcounter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;

import com.google.common.collect.ImmutableSet;

/**
 * Test the internal DebugCounterImplClass
 * @author gregor
 *
 */
public class DebugCounterImplTest {
    @Test
    public void test() {
        DebugCounterImpl c1 =
                new DebugCounterImpl("foo", "bar", "The foo bar counter",
                                     Collections.<MetaData>emptyList());
        assertEquals("foo", c1.getModuleName());
        assertEquals("bar", c1.getCounterHierarchy());
        assertEquals("The foo bar counter", c1.getDescription());
        assertTrue(c1.getMetaData().isEmpty());
        assertEquals(0L, c1.getCounterValue());
        c1.increment();
        assertEquals(1L, c1.getCounterValue());
        c1.increment();
        assertEquals(2L, c1.getCounterValue());
        c1.add(4242);
        assertEquals(4244L, c1.getCounterValue());
        c1.add(0);
        assertEquals(4244L, c1.getCounterValue());

        try {
            c1.add(-1);
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertEquals(4244L, c1.getCounterValue());
        try {
            c1.add(-2);
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertEquals(4244L, c1.getCounterValue());
        try {
            c1.add(Long.MIN_VALUE);
            fail("Expected Exception not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertEquals(4244L, c1.getCounterValue());

        c1.reset();
        assertEquals(0L, c1.getCounterValue());


        DebugCounterImpl c2 =
                new DebugCounterImpl("foo", "bar", "The foo bar counter",
                                     ImmutableSet.of(MetaData.WARN));
        assertEquals("foo", c2.getModuleName());
        assertEquals("bar", c2.getCounterHierarchy());
        assertEquals("The foo bar counter", c2.getDescription());
        assertEquals(ImmutableSet.of(MetaData.WARN),
                     c2.getMetaData());
        c2 = new DebugCounterImpl("foo", "bar", "The foo bar counter",
                                   ImmutableSet.of(MetaData.WARN, MetaData.DROP));
        assertEquals("foo", c2.getModuleName());
        assertEquals("bar", c2.getCounterHierarchy());
        assertEquals("The foo bar counter", c2.getDescription());
        assertEquals(ImmutableSet.of(MetaData.WARN, MetaData.DROP),
                     c2.getMetaData());
    }
}
