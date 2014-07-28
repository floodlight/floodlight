package net.floodlightcontroller.debugevent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.floodlightcontroller.core.IShutdownListener;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugevent.DebugEventResource.EventInfoResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * This class implements a central store for all events used for debugging the
 * system. The basic idea is that given the functionality provided by this
 * class, it should be unnecessary to resort to scraping through system
 * DEBUG/TRACE logs to understand behavior in a running system.
 *
 * @author Saurav
 */
public class DebugEventService implements IFloodlightModule, IDebugEventService {
    protected static final Logger log = LoggerFactory.getLogger(DebugEventService.class);

    /**
     * Every registered event type gets an event id AtomicInt to make the get
     * operation thread-safe
     */
    private final AtomicInteger eventIdCounter = new AtomicInteger();

    /**
     * Unique Event Instance Id for ack-ing
     */
    private final AtomicLong eventInstanceId = new AtomicLong(Long.MAX_VALUE);

    private static final int PCT_LOCAL_CAP = 10; // % of global capacity
    private static final int MIN_LOCAL_CAPACITY = 10; // elements

    /**
     * EnumMap from {@link EventFieldType} to {@link CustomFormatter}
     */
    static final ImmutableMap<EventFieldType, CustomFormatter<?>> customFormatter =
            new ImmutableMap.Builder<EventFieldType, CustomFormatter<?>>()
            .put(EventFieldType.DPID, new CustomFormatterDpid())
            .put(EventFieldType.IPv4, new CustomFormatterIpv4())
              .put(EventFieldType.MAC, new CustomFormatterMac())
              .put(EventFieldType.STRING, new CustomFormatterString())
              .put(EventFieldType.OBJECT, new CustomFormatterObject())
              .put(EventFieldType.PRIMITIVE, new CustomFormatterPrimitive())
              .put(EventFieldType.COLLECTION_IPV4, new CustomFormatterCollectionIpv4())
              .put(EventFieldType.COLLECTION_ATTACHMENT_POINT, new CustomFormatterCollectionAttachmentPoint())
              .put(EventFieldType.COLLECTION_OBJECT, new CustomFormatterCollectionObject())
              .put(EventFieldType.SREF_COLLECTION_OBJECT, new CustomFormatterSrefCollectionObject())
              .put(EventFieldType.SREF_OBJECT, new CustomFormatterSrefObject())
              .build();

    /**
     * Event Information
     */
    public static class EventInfo {
        private final int eventId;
        private final boolean enabled;
        private final int bufferCapacity;
        private int numOfEvents;
        private final EventType etype;
        private final String eventDesc;
        private final String eventName;
        private final String moduleName;
        private final String moduleEventName;
        private final Class<?> eventClass;
        private final boolean ackable;

        public EventInfo(int eventId, boolean enabled, boolean ackable,
                         int bufferCapacity, EventType etype,
                         Class<?> eventClass, String eventDesc,
                         String eventName, String moduleName) {
            this.enabled = enabled;
            this.ackable = ackable;
            this.eventId = eventId;
            this.bufferCapacity = bufferCapacity;
            this.numOfEvents = bufferCapacity;
            this.etype = etype;
            this.eventClass = eventClass;
            this.eventDesc = eventDesc;
            this.eventName = eventName;
            this.moduleName = moduleName;
            this.moduleEventName = moduleName + "/" + eventName;
        }

        public int getEventId() {
            return eventId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isAckable() {
            return ackable;
        }

        public int getBufferCapacity() {
            return bufferCapacity;
        }

        public int getNumOfEvents() {
            return numOfEvents;
        }

        public EventType getEtype() {
            return etype;
        }

        public String getEventDesc() {
            return eventDesc;
        }

        public String getEventName() {
            return eventName;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getModuleEventName() {
            return moduleEventName;
        }
    }

    // ******************
    // Global stores
    // ******************

    /**
     * Event history for a particular event-id is stored in a circular buffer
     */
    protected static class DebugEventHistory {
        EventInfo einfo;
        LinkedBlockingDeque<Event> circularEventBuffer;

        public DebugEventHistory(EventInfo einfo, int capacity) {
            this.einfo = einfo;
            this.circularEventBuffer = new LinkedBlockingDeque<Event>(
                                                                      capacity);
        }
    }

    /**
     * Global storage for all event types and their corresponding event buffers.
     * A particular event type is accessed by directly indexing into the Map
     * with the corresponding event-id. <br/>
     * It is a <b>Map</b> with <br/>
     * <b>Key</b> Integer eventId <br/>
     * <b>Value</b> DebugEventHistory
     */
    protected final ConcurrentHashMap<Integer, DebugEventHistory> allEvents = new ConcurrentHashMap<Integer, DebugEventHistory>();

    /**
     * Global storage for all event ids registered for a module. The map is
     * indexed by the module name and event name and returns the event-ids that
     * correspond to the event types registered by that module (for example
     * module 'linkdiscovery' may register events that have ids 0 and 1 that
     * correspond to link up/down events, and receiving malformed LLDP packets,
     * respectively).
     */
    protected final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> moduleEvents = new ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>();

    /**
     * A collection of event ids that are currently enabled for logging
     */
    protected final Set<Integer> currentEvents = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    // ******************
    // Thread local stores
    // ******************

    /**
     * Thread local storage for events
     */
    protected static class LocalEventHistory {
        private boolean enabled;
        private final int capacity;
        private ArrayList<Event> eventList;

        public LocalEventHistory(boolean enabled, int maxCapacity) {
            this.enabled = enabled;
            this.eventList = new ArrayList<Event>(maxCapacity);
            this.capacity = maxCapacity;
        }

        public boolean add(Event e) {
            if (this.eventList.size() < capacity) {
                this.eventList.add(e);
                return true;
            }
            return false;
        }

        public int drainTo(List<Event> eventList) {
            int size = this.eventList.size();
            Iterator<Event> iter = this.eventList.iterator();
            while (iter.hasNext()) {
                eventList.add(iter.next());
            }
            this.eventList.clear();
            return size;
        }

        public boolean isFull() {
            if (eventList.size() == capacity) return true;
            return false;
        }

        public boolean isEmpty() {
            return this.eventList.isEmpty();
        }
    }

    /**
     * Thread local event buffers used for maintaining event history local to a
     * thread. Eventually this locally maintained information is flushed into
     * the global event buffers. <br/>
     * It is a <b>Map</b> with <br/>
     * <b>Key</b> Integer eventId <br/>
     * <b>Value</b> LocalEventHistory
     */
    protected final ThreadLocal<Map<Integer, LocalEventHistory>> threadlocalEvents = new ThreadLocal<Map<Integer, LocalEventHistory>>() {
        @Override
        protected Map<Integer, LocalEventHistory> initialValue() {
            return new HashMap<Integer, LocalEventHistory>();
        }
    };

    /**
     * Thread local cache for event-ids that are currently active.
     */
    protected final ThreadLocal<Set<Integer>> threadlocalCurrentEvents = new ThreadLocal<Set<Integer>>() {
        @Override
        protected Set<Integer> initialValue() {
            return new HashSet<Integer>();
        }
    };

    // *******************************
    // IEventCategory
    // *******************************

    protected class EventCategory<T> implements IEventCategory<T> {
        private final int eventId;

        public EventCategory(int evId) {
            this.eventId = evId;
        }

        @Override
        public void newEventNoFlush(Object event) {
            if (!validEventId()) return;
            newEvent(eventId, false, event);
        }

        @Override
        public void newEventWithFlush(Object event) {
            if (!validEventId()) return;
            newEvent(eventId, true, event);
        }

        private boolean validEventId() {
            if (eventId < 0) {
                throw new IllegalStateException();
            }
            return true;
        }
    }

    public class EventCategoryBuilder<T> {
        private int eventId;
        private String moduleName;
        private String eventName;
        private String eventDescription;
        private EventType eventType;
        private Class<T> eventClass;
        private int bufferCapacity;
        private boolean ackable;

        public EventCategoryBuilder(Class<T> evClass) {
            this.eventId = eventIdCounter.incrementAndGet();
            this.eventClass = evClass;
        }

        public EventCategoryBuilder<T> setModuleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public EventCategoryBuilder<T> setEventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public EventCategoryBuilder<T> setEventDescription(String eventDescription) {
            this.eventDescription = eventDescription;
            return this;
        }

        public EventCategoryBuilder<T> setEventType(EventType et) {
            this.eventType = et;
            return this;
        }

        public EventCategoryBuilder<T> setBufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }

        public EventCategoryBuilder<T> setAckable(boolean ackable) {
            this.ackable = ackable;
            return this;
        }

        /**
         * Build a new {@link EventCategory<T>} <br/>
         * Requires the following parameters to be set before being called:
         *
         * @param moduleName
         *            module registering event eg. linkdiscovery, virtualrouting.
         * @param eventName
         *            name given to event.
         * @param eventDescription
         *            A descriptive string describing the event.
         * @param eventType
         *            EventType for this event. On-demand events have to be
         *            explicitly enabled using other methods in this API
         * @param eventClass
         *            A user defined class that annotates the fields with
         *            @EventColumn. This class specifies the fields/columns for this
         *            event.
         * @param bufferCapacity
         *            Number of events to store for this event in a circular buffer.
         *            Older events will be discarded once the buffer is full.
         * @param ackable
         *            is the event used as part of ackable-event framework boolean
         *
         * @return IEventCategory with <b>newEvent</b> method that can be used
         * to create instances of event of the given eventClass
         */
        public EventCategory<T> register() {
            // register event id for moduleName
            moduleEvents.putIfAbsent(moduleName,
                                     new ConcurrentHashMap<String, Integer>());
            Integer eventExists = moduleEvents.get(moduleName)
                                              .putIfAbsent(eventName, eventId);
            if (eventExists != null) {
                log.error("Duplicate event registration for moduleName {} eventName {}",
                          moduleName, eventName);
                return new EventCategory<T>(eventExists);
            }

            // create storage for event-type
            boolean enabled = (eventType == EventType.ALWAYS_LOG) ? true : false;
            EventInfo ei = new EventInfo(eventId, enabled, ackable,
                                         bufferCapacity, eventType, eventClass,
                                         eventDescription, eventName, moduleName);
            allEvents.put(eventId, new DebugEventHistory(ei, bufferCapacity));
            if (enabled) {
                currentEvents.add(eventId);
            }

            return new EventCategory<T>(this.eventId);
        }
    }

    // *******************************
    // IDebugEventService
    // *******************************

    @Override
    public <T> EventCategoryBuilder<T> buildEvent(Class<T> evClass) {
        return new EventCategoryBuilder<T>(evClass);
    }

    private void flushLocalToGlobal(int eventId, LocalEventHistory le) {
        DebugEventHistory de = allEvents.get(eventId);
        if (de.einfo.enabled) {
            List<Event> transferEvents = new ArrayList<Event>();
            // drain local buffer to Collection
            int size = le.drainTo(transferEvents);
            // if global buffer doesn't have enough space, clear
            // some space
            int requiredSpace = size
                                - de.circularEventBuffer.remainingCapacity();
            if (requiredSpace > 0) {
                for (int i = 0; i < requiredSpace; i++) {
                    de.circularEventBuffer.removeFirst();
                }
            }
            de.circularEventBuffer.addAll(transferEvents);
        } else {
            le.enabled = false;
            this.threadlocalCurrentEvents.get().remove(eventId);
        }
    }

    private void newEvent(int eventId, boolean flushNow, Object eventData) {
        if (eventId < 0) {
            throw new IllegalStateException("Invalid eventId");
        }

        Map<Integer, LocalEventHistory> thishist = this.threadlocalEvents.get();
        if (!thishist.containsKey(eventId)) {
            // seeing this event for the first time in this thread - create
            // local
            // store by consulting global store
            if (allEvents.containsKey(eventId)) {
                DebugEventHistory de = allEvents.get(eventId);
                boolean enabled = de.einfo.enabled;
                int localCapacity = de.einfo.bufferCapacity * PCT_LOCAL_CAP
                                    / 100;
                if (localCapacity < 10) localCapacity = MIN_LOCAL_CAPACITY;
                thishist.put(eventId, new LocalEventHistory(enabled,
                                                            localCapacity));
                if (enabled) {
                    Set<Integer> thisset = this.threadlocalCurrentEvents.get();
                    thisset.add(eventId);
                }
            } else {
                log.error("updateEvent seen locally for event {} but no global"
                                  + "storage exists for it yet .. not updating",
                          eventId);
                return;
            }
        }

        // update local store if enabled locally for updating
        LocalEventHistory le = thishist.get(eventId);
        if (le.enabled) {
            try {
                le.add(new Event(System.currentTimeMillis(),
                                 Thread.currentThread().getId(),
                                 Thread.currentThread().getName(),
                                 eventData,
                                 /*
                                  * the eventInstanceId is started in reverse
                                  * order so BigDB gets the values in ascending
                                  * order Initialization in startUp()
                                  */
                                 eventInstanceId.decrementAndGet()));

                if (le.isFull() || flushNow) {
                    flushLocalToGlobal(eventId, le);
                }
            } catch (IllegalStateException ise) {
                log.debug("Exception while adding event locally: "
                          + ise.getMessage());
            }
        }
    }

    @Override
    public void flushEvents() {
        Map<Integer, LocalEventHistory> thishist = this.threadlocalEvents.get();
        Set<Integer> thisset = this.threadlocalCurrentEvents.get();

        for (int eventId : thisset) {
            if (thishist.containsKey(eventId)) {
                LocalEventHistory le = thishist.get(eventId);
                if (!le.isEmpty()) {
                    flushLocalToGlobal(eventId, le);
                }
            }
        }

        // sync thread local currently enabled set of eventIds with global set.
        Sets.SetView<Integer> sv = Sets.difference(currentEvents, thisset);
        for (int eventId : sv) {
            if (thishist.containsKey(eventId)) {
                thishist.get(eventId).enabled = true;
                thisset.add(eventId);
            }
        }

    }

    @Override
    public boolean containsModuleEventName(String moduleName,
                                           String eventName) {
        if (!moduleEvents.containsKey(moduleName)) return false;
        if (moduleEvents.get(moduleName).containsKey(eventName))
                                                                return true;
        return false;
    }

    @Override
    public boolean containsModuleName(String moduleName) {
        return moduleEvents.containsKey(moduleName);
    }

    @Override
    public List<EventInfoResource> getAllEventHistory() {
        List<EventInfoResource> moduleEventList = new ArrayList<EventInfoResource>();
        for (Map<String, Integer> modev : moduleEvents.values()) {
            for (int eventId : modev.values()) {
                if (allEvents.containsKey(eventId)) {
                    DebugEventHistory de = allEvents.get(eventId);

                    List<EventResource> eventData = new ArrayList<EventResource>();
                    // take snapshot and iterate
                    Iterator<Event> iter = de.circularEventBuffer.descendingIterator();
                    while (iter.hasNext()) {
                        Event e = iter.next();
                        eventData.add(e.getFormattedEvent(de.einfo.eventClass,
                                                          de.einfo.moduleEventName));
                    }
                    moduleEventList.add(new EventInfoResource(de.einfo,
                                                              eventData));
                }
            }
        }
        traceLogDebugHistory(moduleEventList);
        return moduleEventList;
    }

    @Override
    public List<EventInfoResource> getModuleEventHistory(String moduleName) {
        if (!moduleEvents.containsKey(moduleName))
                                                  return Collections.emptyList();
        List<EventInfoResource> moduleEventList = new ArrayList<EventInfoResource>();
        for (int eventId : moduleEvents.get(moduleName).values()) {
            if (allEvents.containsKey(eventId)) {
                DebugEventHistory de = allEvents.get(eventId);

                List<EventResource> eventData = new ArrayList<EventResource>();
                // take snapshot and iterate
                Iterator<Event> iter = de.circularEventBuffer.descendingIterator();
                while (iter.hasNext()) {
                    Event e = iter.next();
                    eventData.add(e.getFormattedEvent(de.einfo.eventClass,
                                                      de.einfo.moduleEventName));
                }
                moduleEventList.add(new EventInfoResource(de.einfo,
                                                          eventData));
            }
        }
        traceLogDebugHistory(moduleEventList);
        return moduleEventList;
    }

    @Override
    public EventInfoResource getSingleEventHistory(String moduleName,
                                                   String eventName,
                                                   int numOfEvents) {
        if (!moduleEvents.containsKey(moduleName)) return null;
        Integer eventId = moduleEvents.get(moduleName).get(eventName);
        if (eventId == null) return null;

        if (!allEvents.containsKey(eventId)) return null;

        DebugEventHistory de = allEvents.get(eventId);
        if (numOfEvents == 0) numOfEvents = de.einfo.bufferCapacity;

        de.einfo.numOfEvents = numOfEvents;
        int num = 1;
        List<EventResource> eventData = new ArrayList<EventResource>();
        // take snapshot and iterate
        Iterator<Event> iter = de.circularEventBuffer.descendingIterator();
        while (iter.hasNext()) {
            Event e = iter.next();
            if (num > numOfEvents) break;
            eventData.add(e.getFormattedEvent(de.einfo.eventClass,
                                              de.einfo.moduleEventName));
            num++;
        }
        EventInfoResource ret = new EventInfoResource(de.einfo, eventData);
        traceLogDebugHistory(Collections.singletonList(ret));
        return ret;
    }

    @Override
    public void resetAllEvents() {
        for (Map<String, Integer> eventMap : moduleEvents.values()) {
            for (Integer evId : eventMap.values()) {
                allEvents.get(evId).circularEventBuffer.clear();
            }
        }
    }

    @Override
    public void resetAllModuleEvents(String moduleName) {
        if (!moduleEvents.containsKey(moduleName)) return;
        Map<String, Integer> modEvents = moduleEvents.get(moduleName);
        for (Integer evId : modEvents.values()) {
            allEvents.get(evId).circularEventBuffer.clear();
        }
    }

    @Override
    public void resetSingleEvent(String moduleName, String eventName) {
        if (!moduleEvents.containsKey(moduleName)) return;
        Integer eventId = moduleEvents.get(moduleName).get(eventName);
        if (eventId == null) return;

        if (allEvents.containsKey(eventId)) {
            allEvents.get(eventId).circularEventBuffer.clear();
        }
    }

    @Override
    public void setAck(int eventId, long eventInstanceId, boolean ack) {
        if (allEvents.containsKey(eventId)) {
            for (Event e : allEvents.get(eventId).circularEventBuffer) {
                if (e.getEventInstanceId() == eventInstanceId) {
                    e.setAcked(ack);
                }
            }
        }
    }

    @Override
    public List<String> getModuleList() {
        List<String> el = new ArrayList<String>();
        el.addAll(moduleEvents.keySet());
        return el;
    }

    @Override
    public List<String> getModuleEventList(String moduleName) {
        if (!moduleEvents.containsKey(moduleName))
                                                  return Collections.emptyList();
        List<String> el = new ArrayList<String>();
        el.addAll(moduleEvents.get(moduleName).keySet());
        return el;
    }


    private void traceLogDebugHistory(List<EventInfoResource> l) {
        if (!log.isTraceEnabled()) {
            return;
        }
        for (EventInfoResource eir: l) {
            for (EventResource der: eir.getEvents()) {
                log.trace("{}", der);
            }
        }
    }

    private class ShutdownListenenerDelegate implements IShutdownListener {
        @Override
        public void floodlightIsShuttingDown() {
            for (EventInfoResource eir: getAllEventHistory()) {
                for (EventResource der: eir.getEvents()) {
                    log.info("{}", der);
                }
            }
        }
    }

    // *******************************
    // IFloodlightModule
    // *******************************

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDebugEventService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IDebugEventService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
        deps.add(IShutdownService.class);
        return deps;
    }

    @Override
    public void init(FloodlightModuleContext context) {
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        IShutdownService shutdownService =
                context.getServiceImpl(IShutdownService.class);
        shutdownService.registerShutdownListener(new ShutdownListenenerDelegate());
        DebugEventAppender.setDebugEventServiceImpl(this);
    }
}
