package net.floodlightcontroller.debugevent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.easymock.EasyMock.anyObject;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import net.floodlightcontroller.core.IShutdownListener;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.debugevent.DebugEventResource.EventInfoResource;
import net.floodlightcontroller.debugevent.EventResource.Metadata;
import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import org.projectfloodlight.openflow.types.DatapathId;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugEventTest extends FloodlightTestCase {
    DebugEventService debugEvent;
    protected static Logger log = LoggerFactory.getLogger(DebugEventTest.class);

    @Override
    @Before
    public void setUp() throws Exception {
        debugEvent = new DebugEventService();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        IShutdownService shutdownService =
                EasyMock.createMock(IShutdownService.class);
        shutdownService.registerShutdownListener(anyObject(IShutdownListener.class));
        EasyMock.expectLastCall().once();
        EasyMock.replay(shutdownService);
        fmc.addService(IShutdownService.class, shutdownService);
        debugEvent.startUp(fmc);
        EasyMock.verify(shutdownService);
    }


    @Test
    public void testRegisterAndUpdateEvent() throws Exception {
        assertEquals(0, debugEvent.currentEvents.size());
        IEventCategory<SwitchyEvent> event1 = null;
        IEventCategory<PacketyEvent> event2 = null;
        event1 = debugEvent.buildEvent(SwitchyEvent.class)
                .setModuleName("dbgevtest")
                .setEventName("switchevent")
                .setEventType(EventType.ALWAYS_LOG)
                .setBufferCapacity(100)
                .setAckable(false)
                .register();
        event2 = debugEvent.buildEvent(PacketyEvent.class)
                .setModuleName("dbgevtest")
                .setEventName("pktinevent")
                .setEventType(EventType.ALWAYS_LOG)
                .setBufferCapacity(100)
                .setAckable(false)
                .register();

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

        assertEquals(0, debugEvent.allEvents.get(eventId1).circularEventBuffer.size());
        assertEquals(0, debugEvent.allEvents.get(eventId2).circularEventBuffer.size());

        // update is immediately flushed to global store
        event1.newEventWithFlush(new SwitchyEvent(DatapathId.of(1L), "connected"));
        assertEquals(1, debugEvent.allEvents.get(eventId1).circularEventBuffer.size());

        // update is flushed only when flush is explicitly called
        event2.newEventNoFlush(new PacketyEvent(DatapathId.of(1L), 24L));
        assertEquals(0, debugEvent.allEvents.get(eventId2).circularEventBuffer.size());

        debugEvent.flushEvents();
        assertEquals(1, debugEvent.allEvents.get(eventId1).circularEventBuffer.size());
        assertEquals(1, debugEvent.allEvents.get(eventId2).circularEventBuffer.size());

        EventInfoResource de = debugEvent.getSingleEventHistory("dbgevtest","switchevent", 100);
        assertEquals(1, de.events.size());
        assertTrue(de.events.get(0).getDataFields().contains(new Metadata("dpid", "00:00:00:00:00:00:00:01")));
        assertTrue(de.events.get(0).getDataFields().contains(new Metadata("reason", "connected")));

        EventInfoResource de2 = debugEvent.getSingleEventHistory("dbgevtest","pktinevent", 100);
        assertEquals(1, de2.events.size());
        assertTrue(de2.events.get(0).getDataFields().contains(new Metadata("dpid", "00:00:00:00:00:00:00:01")));
        assertTrue(de2.events.get(0).getDataFields().contains(new Metadata("srcMac", "00:00:00:00:00:18")));
    }

    public class SwitchyEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        DatapathId dpid;

        @EventColumn(name = "reason", description = EventFieldType.STRING)
        String reason;

        public SwitchyEvent(DatapathId dpid, String reason) {
            this.dpid = dpid;
            this.reason = reason;
        }
    }

    public class PacketyEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        DatapathId dpid;

        @EventColumn(name = "srcMac", description = EventFieldType.MAC)
        long mac;

        public PacketyEvent(DatapathId dpid, long mac) {
            this.dpid = dpid;
            this.mac = mac;
        }
    }

    public class IntEvent {
        @EventColumn(name = "index", description = EventFieldType.PRIMITIVE)
        int index;

        public IntEvent(int i) {
            this.index = i;
        }

        @Override
        public String toString() {
            return String.valueOf(index);
        }
    }

    @Test
    public void testEventCyclesWithFlush() throws Exception {
        IEventCategory<IntEvent> ev = null;
        ev = debugEvent.buildEvent(IntEvent.class)
                .setModuleName("test")
                .setEventName("int")
                .setEventDescription("just a test")
                .setEventType(EventType.ALWAYS_LOG)
                .setBufferCapacity(20)
                .setAckable(false)
                .register();

        for (int i=0; i<20; i++)
            ev.newEventWithFlush(new IntEvent(i));
        int i=19;
        EventInfoResource dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (EventResource m : dei.events) {
            assertTrue(m.getDataFields().get(0).getEventData().equals(String.valueOf(i)));
            i--;
        }
        for (int j= 500; j<550; j++)
            ev.newEventWithFlush(new IntEvent(j));
        int k=549;
        dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (EventResource m : dei.events) {
            //log.info("{}", m.get("index"));
            assertTrue(m.getDataFields().get(0).getEventData().equals(String.valueOf(k)));
            k--;
        }
    }


    @Test
    public void testEventCyclesNoFlush() throws Exception {
        IEventCategory<IntEvent> ev = null;
        ev = debugEvent.buildEvent(IntEvent.class)
                .setModuleName("test")
                .setEventName("int")
                .setEventDescription("just a test")
                .setEventType(EventType.ALWAYS_LOG)
                .setBufferCapacity(20)
                .setAckable(false)
                .register();

        // flushes when local buffer fills up
        for (int i=0; i<20; i++)
            ev.newEventNoFlush(new IntEvent(i));
        int i=19;
        EventInfoResource dei = debugEvent.getSingleEventHistory("test","int", 0);
        for (EventResource m : dei.events) {
            assertTrue(m.getDataFields().get(0).getEventData().equals(String.valueOf(i)));
            i--;
        }
        //log.info("done with first bunch");
        // flushes when local buffer fills up or when flushEvents is explicitly called
        for (int j= 500; j<550; j++) {
            ev.newEventNoFlush(new IntEvent(j));
            //if (j == 515)
                //debugEvent.flushEvents();
        }
        debugEvent.flushEvents();

        int k=549;
        dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (EventResource m : dei.events) {
            //log.info("{}", m.get("index"));
            assertTrue(m.getDataFields().get(0).getEventData().equals(String.valueOf(k)));
            k--;
        }
    }

    @Test
    public void testAckEvent() throws Exception{
        IEventCategory<IntEvent> ev = null;
        ev = debugEvent.buildEvent(IntEvent.class)
                .setModuleName("test")
                .setEventName("ack")
                .setEventDescription("just a test")
                .setEventType(EventType.ALWAYS_LOG)
                .setBufferCapacity(20)
                .setAckable(false)
                .register();
        //create a single event
        IntEvent e = new IntEvent(10);
        ev.newEventWithFlush(e);
        EventInfoResource dei = debugEvent.getSingleEventHistory("test","ack", 1);
        debugEvent.setAck(dei.getEventId(),
                          dei.getEvents().get(0).getEventInstanceId(),
                          true);
        dei = debugEvent.getSingleEventHistory("test","ack", 1);
        assertTrue(dei.getEvents().get(0).isAcked());
    }
}