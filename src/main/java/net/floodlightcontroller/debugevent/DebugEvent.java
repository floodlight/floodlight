package net.floodlightcontroller.debugevent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugevent.web.DebugEventRoutable;
import net.floodlightcontroller.restserver.IRestApiService;

import com.google.common.collect.Sets;
/**
 * This class implements a central store for all events used for debugging the
 * system. The basic idea is that given the functionality provided by this class,
 * it should be unnecessary to resort to scraping through system DEBUG/TRACE logs
 * to understand behavior in a running system.
 *
 * @author Saurav
 */
public class DebugEvent implements IFloodlightModule, IDebugEventService {
    protected static Logger log = LoggerFactory.getLogger(DebugEvent.class);

    /**
     *  Every registered event type gets an event id, the value for which is obtained
     *  while holding the lock.
     */
    protected int eventIdCounter = 0;
    protected Object eventIdLock = new Object();

    private static final long MIN_FLUSH_DELAY = 100; //ms
    private static final int PCT_LOCAL_CAP = 10; // % of global capacity
    private static final int MIN_LOCAL_CAPACITY = 10; //elements

    /**
     * Event Information
     */
    public class EventInfo {
        int eventId;
        boolean enabled;
        int bufferCapacity;
        EventType etype;
        String eventDesc;
        String eventName;
        String moduleName;
        String moduleEventName;
        Class<?> eventClass;
        String[] metaData;

        public EventInfo(int eventId, boolean enabled, int bufferCapacity,
                         EventType etype, Class<?> eventClass, String eventDesc,
                         String eventName, String moduleName, String... metaData) {
            this.enabled = enabled;
            this.eventId = eventId;
            this.bufferCapacity = bufferCapacity;
            this.etype = etype;
            this.eventClass = eventClass;
            this.eventDesc = eventDesc;
            this.eventName = eventName;
            this.moduleName = moduleName;
            this.moduleEventName = moduleName + "/" + eventName;
            this.metaData = metaData;
        }

        public int getEventId() { return eventId; }
        public boolean isEnabled() { return enabled; }
        public int getBufferCapacity() { return bufferCapacity; }
        public EventType getEtype() { return etype; }
        public String getEventDesc() { return eventDesc; }
        public String getEventName() { return eventName; }
        public String getModuleName() { return moduleName; }
        public String getModuleEventName() { return moduleEventName; }
        public String[] getMetaData() { return metaData; }
    }

    //******************
    //   Global stores
    //******************

    /**
     * Event history for a particular event-id is stored in a circular buffer
     */
    protected class DebugEventHistory {
        EventInfo einfo;
        CircularBuffer<Event> eventBuffer;

        public DebugEventHistory(EventInfo einfo, int capacity) {
            this.einfo = einfo;
            this.eventBuffer = new CircularBuffer<Event>(capacity);
        }
    }

    /**
     * Global storage for all event types and their corresponding event buffers.
     * A particular event type is accessed by directly indexing into the array
     * with the corresponding event-id.
     */
    protected DebugEventHistory[] allEvents =
                    new DebugEventHistory[MAX_EVENTS];

    /**
     * Global storage for all event ids registered for a module. The map is indexed
     * by the module name and event name and returns the event-ids that correspond to the
     * event types registered by that module (for example module 'linkdiscovery'
     * may register events that have ids 0 and 1 that correspond to link up/down
     * events, and receiving malformed LLDP packets, respectively).
     */
    protected ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>
        moduleEvents = new ConcurrentHashMap<String,
                                ConcurrentHashMap<String, Integer>>();

    /**
     * A collection of event ids that are currently enabled for logging
     */
    protected Set<Integer> currentEvents = Collections.newSetFromMap(
                                       new ConcurrentHashMap<Integer,Boolean>());

    //******************
    // Thread local stores
    //******************

    /**
     * Thread local storage for events
     */
    protected class LocalEventHistory {
        int nextIndex;
        int maxCapacity;
        boolean enabled;
        ArrayList<Event> eventList;
        long lastFlushTime;
        boolean flushNow;

        public LocalEventHistory(boolean enabled, int maxCapacity, boolean flushNow) {
            this.nextIndex = 0;
            this.maxCapacity = maxCapacity;
            this.enabled = enabled;
            this.eventList = new ArrayList<Event>();
            this.flushNow = flushNow;
        }
    }

    /**
     * Thread local event buffers used for maintaining event history local to
     * a thread. Eventually this locally maintained information is flushed
     * into the global event buffers.
     */
    protected final ThreadLocal<LocalEventHistory[]> threadlocalEvents =
            new ThreadLocal<LocalEventHistory[]>() {
        @Override
        protected LocalEventHistory[] initialValue() {
            return new LocalEventHistory[MAX_EVENTS];
        }
    };

    /**
     * Thread local cache for event-ids that are currently active.
     */
    protected final ThreadLocal<Set<Integer>> threadlocalCurrentEvents =
            new ThreadLocal<Set<Integer>>() {
        @Override
        protected Set<Integer> initialValue() {
            return new HashSet<Integer>();
        }
    };

    //*******************************
    //   IEventUpdater
    //*******************************

    protected class EventUpdaterImpl<T> implements IEventUpdater<T> {
        private final int eventId;

        public EventUpdaterImpl(int evId) {
            this.eventId = evId;
        }

        @Override
        public void updateEventNoFlush(Object event) {
            if (!validEventId()) return;
            updateEvent(eventId, false, event);
        }

        @Override
        public void updateEventWithFlush(Object event) {
            if (!validEventId()) return;
            updateEvent(eventId, true, event);
        }

        private boolean validEventId() {
            if (eventId < 0 || eventId >= MAX_EVENTS) {
                throw new IllegalStateException();
            }
            return true;
        }

    }

    //*******************************
    //   IDebugEventService
    //*******************************

    @Override
    public <T> IEventUpdater<T> registerEvent(String moduleName, String eventName,
                                              String eventDescription, EventType et,
                                              Class<T> eventClass, int bufferCapacity,
                                              String... metaData) throws MaxEventsRegistered {
        int eventId = -1;
        synchronized (eventIdLock) {
             eventId = Integer.valueOf(eventIdCounter++);
        }
        if (eventId > MAX_EVENTS-1) {
            throw new MaxEventsRegistered();
        }

        // register event id for moduleName
        if (!moduleEvents.containsKey(moduleName)) {
            moduleEvents.put(moduleName, new ConcurrentHashMap<String, Integer>());
        }
        if (!moduleEvents.get(moduleName).containsKey(eventName)) {
            moduleEvents.get(moduleName).put(eventName, new Integer(eventId));
        } else {
            int existingEventId = moduleEvents.get(moduleName).get(eventName);
            log.error("Duplicate event registration for moduleName {} eventName {}",
                      moduleName, eventName);
            return new EventUpdaterImpl<T>(existingEventId);
        }

        // create storage for event-type
        boolean enabled = (et == EventType.ALWAYS_LOG) ? true : false;
        EventInfo ei = new EventInfo(eventId, enabled, bufferCapacity,
                                     et, eventClass, eventDescription, eventName,
                                     moduleName, metaData);
        allEvents[eventId] = new DebugEventHistory(ei, bufferCapacity);
        if (enabled) {
            currentEvents.add(eventId);
        }

        return new EventUpdaterImpl<T>(eventId);
    }

    private void updateEvent(int eventId, boolean flushNow, Object eventData) {
        if (eventId < 0 || eventId > MAX_EVENTS-1) return;

        LocalEventHistory[] thishist = this.threadlocalEvents.get();
        if (thishist[eventId] == null) {
            // seeing this event for the first time in this thread - create local
            // store by consulting global store
            DebugEventHistory de = allEvents[eventId];
            if (de != null) {
                boolean enabled = de.einfo.enabled;
                int localCapacity = de.einfo.bufferCapacity * PCT_LOCAL_CAP/ 100;
                if (localCapacity < 10)  localCapacity = MIN_LOCAL_CAPACITY;
                thishist[eventId] = new LocalEventHistory(enabled, localCapacity,
                                                          flushNow);
                if (enabled) {
                    Set<Integer> thisset = this.threadlocalCurrentEvents.get();
                    thisset.add(eventId);
                }
            } else {
                log.error("updateEvent seen locally for event {} but no global"
                          + "storage exists for it yet .. not updating", eventId);
                return;
            }
        }

        // update local store if enabled locally for updating
        LocalEventHistory le = thishist[eventId];
        if (le.enabled) {
            long timestamp = System.currentTimeMillis();
            long thisthread = Thread.currentThread().getId();
            String thisthreadname = Thread.currentThread().getName();
            if (le.nextIndex < le.eventList.size()) {
                if (le.eventList.get(le.nextIndex) == null) {
                    le.eventList.set(le.nextIndex, new Event(timestamp, thisthread,
                                                             thisthreadname,
                                                             eventData));
                } else {
                    Event e = le.eventList.get(le.nextIndex);
                    e.timestamp = timestamp;
                    e.threadId = thisthread;
                    e.eventData = eventData;
                }
            } else {
                le.eventList.add(new Event(timestamp, thisthread, thisthreadname, eventData));
            }
            le.nextIndex++;

            if (le.nextIndex >= le.maxCapacity || le.flushNow) {
                // flush this buffer now
                DebugEventHistory de = allEvents[eventId];
                if (de.einfo.enabled) {
                    le.eventList = de.eventBuffer.addAll(le.eventList, le.nextIndex);
                } else {
                    // global buffer is disabled - don't flush, disable locally
                    le.enabled = false;
                    Set<Integer> thisset = this.threadlocalCurrentEvents.get();
                    thisset.remove(eventId);
                }
                le.nextIndex = 0;
                le.lastFlushTime = timestamp;
            }
        }
    }

    @Override
    public void flushEvents() {
        LocalEventHistory[] thishist = this.threadlocalEvents.get();
        Set<Integer> thisset = this.threadlocalCurrentEvents.get();
        long timestamp = System.currentTimeMillis();
        ArrayList<Integer> temp = new ArrayList<Integer>();

        for (int eventId : thisset) {
            LocalEventHistory le = thishist[eventId];
            if (le != null && le.nextIndex > 0 &&
                    (le.flushNow || (timestamp - le.lastFlushTime) > MIN_FLUSH_DELAY)) {
                // flush this buffer now
                DebugEventHistory de = allEvents[eventId];
                if (de.einfo.enabled) {
                    le.eventList = de.eventBuffer.addAll(le.eventList, le.nextIndex);
                } else {
                    // global buffer is disabled - don't flush, disable locally
                    le.enabled = false;
                    temp.add(eventId);
                }
                le.nextIndex = 0;
                le.lastFlushTime = timestamp;
            }
        }
        for (int eId : temp)
            thisset.remove(eId);

        // sync thread local currently enabled set of eventIds with global set.
        Sets.SetView<Integer> sv = Sets.difference(currentEvents, thisset);
        for (int eventId : sv) {
            if (thishist[eventId] != null) {
                thishist[eventId].enabled = true;
                thisset.add(eventId);
            }
        }

    }

    @Override
    public boolean containsModuleEventName(String moduleName, String eventName) {
        if (!moduleEvents.containsKey(moduleName)) return false;
        if (moduleEvents.get(moduleName).containsKey(eventName)) return true;
        return false;
    }

    @Override
    public boolean containsModuleName(String moduleName) {
        return moduleEvents.containsKey(moduleName);
    }

    @Override
    public List<DebugEventInfo> getAllEventHistory() {
        List<DebugEventInfo> moduleEventList = new ArrayList<DebugEventInfo>();
        for (Map<String, Integer> modev : moduleEvents.values()) {
            for (int eventId : modev.values()) {
                DebugEventHistory de = allEvents[eventId];
                if (de != null) {
                    List<Map<String,String>> ret = new ArrayList<Map<String,String>>();
                    for (Event e : de.eventBuffer) {
                        ret.add(e.getFormattedEvent(de.einfo.eventClass,
                                                    de.einfo.moduleEventName));
                    }
                    moduleEventList.add(new DebugEventInfo(de.einfo, ret));
                }
            }
        }
        return moduleEventList;
    }

    @Override
    public List<DebugEventInfo> getModuleEventHistory(String moduleName) {
        if (!moduleEvents.containsKey(moduleName)) return Collections.emptyList();
        List<DebugEventInfo> moduleEventList = new ArrayList<DebugEventInfo>();
        for (int eventId : moduleEvents.get(moduleName).values()) {
            DebugEventHistory de = allEvents[eventId];
            if (de != null) {
                List<Map<String,String>> ret = new ArrayList<Map<String,String>>();
                for (Event e : de.eventBuffer) {
                    ret.add(e.getFormattedEvent(de.einfo.eventClass,
                                                de.einfo.moduleEventName));
                }
                moduleEventList.add(new DebugEventInfo(de.einfo, ret));
            }
        }
        return moduleEventList;
    }

    @Override
    public DebugEventInfo getSingleEventHistory(String moduleName, String eventName,
                                                int last) {
        if (!moduleEvents.containsKey(moduleName)) return null;
        Integer eventId = moduleEvents.get(moduleName).get(eventName);
        if (eventId == null) return null;
        DebugEventHistory de = allEvents[eventId];
        if (de != null) {
            int num = 1;
            List<Map<String,String>> ret = new ArrayList<Map<String,String>>();
            for (Event e : de.eventBuffer) {
                if (num > last)
                    break;
                Map<String, String> temp = e.getFormattedEvent(de.einfo.eventClass,
                                                               de.einfo.moduleEventName);
                temp.put("#", String.valueOf(num++));
                ret.add(temp);
            }
            return new DebugEventInfo(de.einfo, ret);
        }
        return null;
    }

    @Override
    public void resetAllEvents() {
        for (Map<String, Integer> eventMap : moduleEvents.values()) {
            for (Integer evId : eventMap.values()) {
                allEvents[evId].eventBuffer.clear();
            }
        }
    }

    @Override
    public void resetAllModuleEvents(String moduleName) {
        if (!moduleEvents.containsKey(moduleName)) return;
        Map<String, Integer> modEvents = moduleEvents.get(moduleName);
        for (Integer evId : modEvents.values()) {
            allEvents[evId].eventBuffer.clear();
        }
    }

    @Override
    public void resetSingleEvent(String moduleName, String eventName) {
        if (!moduleEvents.containsKey(moduleName)) return;
        Integer eventId = moduleEvents.get(moduleName).get(eventName);
        if (eventId == null) return;
        DebugEventHistory de = allEvents[eventId];
        if (de != null) {
            de.eventBuffer.clear();
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

    //*******************************
    //   IFloodlightModule
    //*******************************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDebugEventService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IDebugEventService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        ArrayList<Class<? extends IFloodlightService>> deps =
                new ArrayList<Class<? extends IFloodlightService>>();
        deps.add(IRestApiService.class);
        return deps;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
        IRestApiService restService =
                context.getServiceImpl(IRestApiService.class);
        restService.addRestletRoutable(new DebugEventRoutable());
        DebugEventAppender.setDebugEventServiceImpl(this);
    }

}
