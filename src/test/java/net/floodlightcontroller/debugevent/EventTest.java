package net.floodlightcontroller.debugevent;

import static org.junit.Assert.*;

import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventTest {
    protected static Logger log = LoggerFactory.getLogger(EventTest.class);

    @Test
    public void testFormat() {
        River r = new River("ganges", 42);

        Event e = new Event(1L, 32,
                      new RiverEvent(1L, (short)10, true, "big river", 5, 4L, r));

        String expected = "dpid=00:00:00:00:00:00:00:01, portId=10, valid=true, " +
                            "desc=big river, ip=0.0.0.5, mac=00:00:00:00:00:04, " +
                                "obj=ganges/42";

        //log.info("{} \n expected {}", e.toString(RiverEvent.class, "test"), expected);
        assertEquals(true, e.toString(RiverEvent.class, "test").contains(expected));

        // ensure timestamp comes in ISO8601 time
        assertEquals(true, e.toString(RiverEvent.class, "test2")
                     .contains("1969-12-31T16:00:00.001-0800")); //1L
        // change the timestamp - the call should return cached value
        e.setTimestamp(2L);
        assertEquals(true, e.toString(RiverEvent.class, "test2")
                     .contains("1969-12-31T16:00:00.001-0800")); //1L

        // ensure that cached value is not returned for incorrect class
        assertEquals(false, e.toString(River.class, "test").contains(expected));
        assertEquals(false, e.toString(River.class, "test")
                                 .contains("IllegalArgumentException"));
        assertEquals(true, e.toString(null, "test")
                     .contains("Error: null event data or event class"));

    }

    @Test
    public void testIncorrectAnnotation() {
        Event e = new Event(1L, 32,
                            new LakeEvent(199)); // dpid cannot be int
        assertEquals(true, e.toString(LakeEvent.class, "test")
                                 .contains("ClassCastException"));

        Event e2 = new Event(1L, 32,
                            new LakeEvent2(199)); // mac cannot be int
        assertEquals(true, e2.toString(LakeEvent2.class, "test")
                     .contains("ClassCastException"));

    }

    class RiverEvent  {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        long dpid;

        @EventColumn(name = "portId", description = EventFieldType.PRIMITIVE)
        short srcPort;

        @EventColumn(name = "valid", description = EventFieldType.PRIMITIVE)
        boolean isValid;

        @EventColumn(name = "desc", description = EventFieldType.STRING)
        String desc;

        @EventColumn(name = "ip", description = EventFieldType.IPv4)
        int ipAddr;

        @EventColumn(name = "mac", description = EventFieldType.MAC)
        long macAddr;

        @EventColumn(name = "obj", description = EventFieldType.OBJECT)
        River amazon;

        // Instances of RiverEvent ensure that that any internal object
        // (eg. River instances) has been copied before it is given to DebugEvents.
        public RiverEvent(long dpid, short srcPort, boolean isValid,
                            String desc, int ip, long mac, River passedin) {
            this.dpid = dpid;
            this.srcPort = srcPort;
            this.isValid = isValid;
            this.desc = desc;
            this.ipAddr = ip;
            this.macAddr = mac;
            this.amazon = new River(passedin); // invoke copy constructor
        }
    }

    // Object of the River class will be passed in as part of the EventExample instance
    // The user needs to ensure that the River class has a copy constructor
    // and it overrides the toString method.
    class River {
        String r1;
        long r2;

        public River(String r1, long r2) {
            this.r1 = r1;
            this.r2 = r2;
        }
        // should have copy constructor
        public River(River passedin) {
            this.r1 = passedin.r1;
            this.r2 = passedin.r2;
        }
        // needs to override toString method
        @Override
        public String toString() {
            return (r1 + "/" + r2);
        }
    }


    class LakeEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        int dpid;

        public LakeEvent(int dpid) {
            this.dpid = dpid;
        }
    }

    class LakeEvent2 {
        @EventColumn(name = "mac", description = EventFieldType.MAC)
        int mac;

        public LakeEvent2(int mac) {
            this.mac = mac;
        }
    }
}
