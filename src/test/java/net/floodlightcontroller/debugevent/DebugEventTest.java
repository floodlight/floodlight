package net.floodlightcontroller.debugevent;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugevent.IDebugEventService.DebugEventInfo;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import net.floodlightcontroller.debugevent.IDebugEventService.MaxEventsRegistered;
import net.floodlightcontroller.test.FloodlightTestCase;

public class DebugEventTest extends FloodlightTestCase {
    DebugEvent debugEvent;
    protected static Logger log = LoggerFactory.getLogger(DebugEventTest.class);

    @Override
    @Before
    public void setUp() throws Exception {
        debugEvent = new DebugEvent();

    }


    @Test
    public void testRegisterAndUpdateEvent() {
        assertEquals(0, debugEvent.currentEvents.size());
        int eventId = -1;
        try {
            eventId = debugEvent.registerEvent("dbgevtest", "switchevent", true,
                                               "switchtest", EventType.ALWAYS_LOG,
                                               100, "Sw=%dpid, reason=%s", null);
        } catch (MaxEventsRegistered e) {
            e.printStackTrace();
        }

        assertEquals(1, debugEvent.currentEvents.size());
        assertEquals(eventId, debugEvent.moduleEvents.get("dbgevtest").
                                             get("switchevent").intValue());
        assertEquals(true, debugEvent.containsModName("dbgevtest"));
        assertEquals(true, debugEvent.containsMEName("dbgevtest-switchevent"));

        assertEquals(0, DebugEvent.allEvents[eventId].eventBuffer.size());
        debugEvent.updateEvent(eventId, new Object[] {1L, "connected"});
        assertEquals(0, DebugEvent.allEvents[eventId].eventBuffer.size());
        debugEvent.flushEvents();
        assertEquals(1, DebugEvent.allEvents[eventId].eventBuffer.size());

        DebugEventInfo de = debugEvent.getSingleEventHistory("dbgevtest-switchevent");
        assertEquals(1, de.events.size());
        assertEquals(true, de.events.get(0)
                         .contains("Sw=00:00:00:00:00:00:00:01, reason=connected"));
    }
}
