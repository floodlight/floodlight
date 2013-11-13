package net.floodlightcontroller.debugevent;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugevent.IDebugEventService.DebugEventInfo;
import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
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
    public void testRegisterAndUpdateEvent() throws Exception {
        assertEquals(0, debugEvent.currentEvents.size());
        IEventUpdater<SwitchyEvent> event1 = null;
        IEventUpdater<PacketyEvent> event2 = null;
        event1 = debugEvent.registerEvent("dbgevtest", "switchevent",
                                           "switchtest", EventType.ALWAYS_LOG,
                                           SwitchyEvent.class, 100);
        event2 = debugEvent.registerEvent("dbgevtest", "pktinevent",
                                           "pktintest", EventType.ALWAYS_LOG,
                                           PacketyEvent.class, 100);

        assertEquals(2, debugEvent.currentEvents.size());
        assertTrue(null != debugEvent.moduleEvents.get("dbgevtest").
                                                     get("switchevent"));
        int eventId1 = debugEvent.moduleEvents.get("dbgevtest").
                                                     get("switchevent");
        assertTrue(null != debugEvent.moduleEvents.get("dbgevtest").
                                                     get("pktinevent"));
        int eventId2 = debugEvent.moduleEvents.get("dbgevtest").
                                                     get("pktinevent");
        assertEquals(true, debugEvent.containsModuleName("dbgevtest"));
        assertEquals(true, debugEvent.containsModuleEventName("dbgevtest","switchevent"));
        assertEquals(true, debugEvent.containsModuleEventName("dbgevtest","pktinevent"));

        assertEquals(0, debugEvent.allEvents[eventId1].eventBuffer.size());
        assertEquals(0, debugEvent.allEvents[eventId2].eventBuffer.size());

        // update is immediately flushed to global store
        event1.updateEventWithFlush(new SwitchyEvent(1L, "connected"));
        assertEquals(1, debugEvent.allEvents[eventId1].eventBuffer.size());

        // update is flushed only when flush is explicitly called
        event2.updateEventNoFlush(new PacketyEvent(1L, 24L));
        assertEquals(0, debugEvent.allEvents[eventId2].eventBuffer.size());

        debugEvent.flushEvents();
        assertEquals(1, debugEvent.allEvents[eventId1].eventBuffer.size());
        assertEquals(1, debugEvent.allEvents[eventId2].eventBuffer.size());

        DebugEventInfo de = debugEvent.getSingleEventHistory("dbgevtest","switchevent", 100);
        assertEquals(1, de.events.size());
        assertEquals(true, de.events.get(0).get("dpid").equals("00:00:00:00:00:00:00:01"));
        assertEquals(true, de.events.get(0).get("reason").equals("connected"));

        DebugEventInfo de2 = debugEvent.getSingleEventHistory("dbgevtest","pktinevent", 100);
        assertEquals(1, de2.events.size());
        assertEquals(true, de2.events.get(0).get("dpid").equals("00:00:00:00:00:00:00:01"));
        assertEquals(true, de2.events.get(0).get("srcMac").equals("00:00:00:00:00:18"));
    }

    public class SwitchyEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        long dpid;

        @EventColumn(name = "reason", description = EventFieldType.STRING)
        String reason;

        public SwitchyEvent(long dpid, String reason) {
            this.dpid = dpid;
            this.reason = reason;
        }
    }

    public class PacketyEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        long dpid;

        @EventColumn(name = "srcMac", description = EventFieldType.MAC)
        long mac;

        public PacketyEvent(long dpid, long mac) {
            this.dpid = dpid;
            this.mac = mac;
        }
    }
}
