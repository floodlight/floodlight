package net.floodlightcontroller.debugevent;

import static org.junit.Assert.*;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventTest {
    protected static Logger log = LoggerFactory.getLogger(EventTest.class);

    @Test
    public void testFormat() {
        // mac addr should be long not int
        Event e = new Event(1L, 10, new Object[] {1L, 2, "yes", 4, 5});
        assertEquals(true,
            e.toString("dpid=%dpid, id=%d, true=%s, mac=%mac, ip=%ipv4", "test")
                .contains("ClassCastException"));
        // conversion is %ipv4 not %ip
        e = new Event(1L, 10, new Object[] {1L, 2, "yes", 4L, 5});
        assertEquals(true,
            e.toString("dpid=%dpid, id=%d, true=%s, mac=%mac, ip=%ip", "test")
                .contains("UnknownFormatConversion"));
        // less params in format string
        e = new Event(1L, 10, new Object[] {1, 2, "yes", 4, 5});
        assertEquals(true,
            e.toString("dpid=%d, id=%d, true=%s, mac=%d", "test")
                .contains("format string does not match number of params"));
        // more params in format string
        e = new Event(1L, 10, new Object[] {1, 2, "yes", 4, 5});
        assertEquals(true,
            e.toString("dpid=%d, id=%d, true=%s, mac=%d, m=%d, m=%d", "test")
                .contains("format string does not match number of params"));
        // missing conversion
        e = new Event(1L, 10, new Object[] {1, 2, "yes", 4, 5});
        assertEquals(true,
            e.toString("dpid=%d, id=d, true=%s, mac=%d, m=%d", "test")
                .contains("incorrect format string - missing %"));
        // missing comma in format string
        e = new Event(1L, 10, new Object[] {1, 2, "yes", 4, 5});
        assertEquals(true,
                     e.toString("dpid=%d, id=%d true=%s, mac=%d, m=%d", "test")
                     .contains("format string does not match number of params"));
        // illegal format conversion
        e = new Event(1L, 10, new Object[] {1, 2, "yes", 4, 5});
        assertEquals(true,
                     e.toString("dpid=%d, id=%f, true=%s, mac=%d, m=%d", "test")
                     .contains("IllegalFormatConversion"));
        // %dpid conversion
        e = new Event(1L, 10, new Object[] {1L, 2, "yes", 4L, 5});
        assertEquals(true,
                     e.toString("dpid=%dpid, id=%d, true=%s, mac=%d, ip=%ipv4", "test")
                     .contains("00:00:00:00:00:00:00:01"));
        // %mac conversion
        e = new Event(1L, 10, new Object[] {1L, 2, "yes", 4L, 5});
        assertEquals(true,
                     e.toString("dpid=%dpid, id=%d, true=%s, mac=%mac, ip=%ipv4", "test")
                     .contains("00:00:00:00:00:04"));
        // %ipv4 conversion
        e = new Event(1L, 10, new Object[] {1L, 2, "yes", 4L, 5});
        assertEquals(true,
                     e.toString("dpid=%dpid, id=%d, true=%s, mac=%d, ip=%ipv4", "test")
                     .contains("0.0.0.5"));
    }
}
