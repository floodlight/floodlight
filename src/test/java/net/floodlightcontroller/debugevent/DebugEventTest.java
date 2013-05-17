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
        int eventId1 = -1, eventId2 = -1 ;
        try {
            eventId1 = debugEvent.registerEvent("dbgevtest", "switchevent", true,
                                               "switchtest", EventType.ALWAYS_LOG,
                                               100, "Sw=%dpid, reason=%s", null);
            eventId2 = debugEvent.registerEvent("dbgevtest", "pktinevent", false,
                                               "pktintest", EventType.ALWAYS_LOG,
                                               100, "Sw=%d, reason=%s", null);
        } catch (MaxEventsRegistered e) {
            e.printStackTrace();
        }

        assertEquals(2, debugEvent.currentEvents.size());
        assertEquals(eventId1, debugEvent.moduleEvents.get("dbgevtest").
                                             get("switchevent").intValue());
        assertEquals(eventId2, debugEvent.moduleEvents.get("dbgevtest").
                     get("pktinevent").intValue());
        assertEquals(true, debugEvent.containsModuleName("dbgevtest"));
        assertEquals(true, debugEvent.containsModuleEventName("dbgevtest","switchevent"));
        assertEquals(true, debugEvent.containsModuleEventName("dbgevtest","pktinevent"));

        assertEquals(0, DebugEvent.allEvents[eventId1].eventBuffer.size());
        assertEquals(0, DebugEvent.allEvents[eventId2].eventBuffer.size());

        // update is immediately flushed to global store
        debugEvent.updateEvent(eventId1, new Object[] {1L, "connected"});
        assertEquals(1, DebugEvent.allEvents[eventId1].eventBuffer.size());

        // update is flushed only when flush is explicity called
        debugEvent.updateEvent(eventId2, new Object[] {1L, "switch sent pkt-in"});
        assertEquals(0, DebugEvent.allEvents[eventId2].eventBuffer.size());

        debugEvent.flushEvents();
        assertEquals(1, DebugEvent.allEvents[eventId1].eventBuffer.size());
        assertEquals(1, DebugEvent.allEvents[eventId2].eventBuffer.size());

        DebugEventInfo de = debugEvent.getSingleEventHistory("dbgevtest","switchevent");
        assertEquals(1, de.events.size());
        assertEquals(true, de.events.get(0)
                         .contains("Sw=00:00:00:00:00:00:00:01, reason=connected"));

        DebugEventInfo de2 = debugEvent.getSingleEventHistory("dbgevtest","pktinevent");
        assertEquals(1, de2.events.size());
        assertEquals(true, de2.events.get(0)
                     .contains("Sw=1, reason=switch sent pkt-in"));
    }
}
