package net.floodlightcontroller.debugevent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugevent.DebugEvent.EventInfo;

public interface IDebugEventService extends IFloodlightService {

    /**
     * Different event types. Events that are meant to be logged on demand
     * need to be separately enabled/disabled.
     */
    public enum EventType {
        ALWAYS_LOG,
        LOG_ON_DEMAND
    }

    /**
     * Describes the type of field obtained from reflection
     */
    enum EventFieldType {
        DPID, IPv4, MAC, STRING, OBJECT, PRIMITIVE, LIST_IPV4,
        LIST_ATTACHMENT_POINT, LIST_OBJECT, SREF_LIST_OBJECT, SREF_OBJECT,
        FLOW_MOD_FLAGS
    }

    /**
     * EventColumn is the only annotation given to the fields of the event
     * when updating an event.
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EventColumn {
        String name() default "param";
        EventFieldType description() default EventFieldType.PRIMITIVE;
    }

    /**
     * Debug Event Qualifiers
     */
    public static final String EV_MDATA_WARN = "warn";
    public static final String EV_MDATA_ERROR = "error";

    /**
     *  A limit on the maximum number of events that can be created
     */
    public static final int MAX_EVENTS = 2000;

    /**
     * Public class for information returned in response to rest API calls.
     */
    public class DebugEventInfo {
        EventInfo eventInfo;
        List<Map<String,String>> events;

        public DebugEventInfo(EventInfo eventInfo,
                              List<Map<String, String>> eventHistory) {
            this.eventInfo = eventInfo;
            this.events = eventHistory;
        }

        public EventInfo getEventInfo() {
            return eventInfo;
        }

        public List<Map<String,String>> getEvents() {
            return events;
        }
    }

    /**
    * exception thrown when MAX_EVENTS have been registered
    */
    public class MaxEventsRegistered extends Exception {
        private static final long serialVersionUID = 2609587082227510262L;
    }

    /**
     * Register an event for debugging.
     *
     * @param moduleName       module registering event eg. linkdiscovery, virtualrouting.
     * @param eventName        name given to event.
     * @param eventDescription A descriptive string describing the event.
     * @param eventType        EventType for this event. On-demand events have to
     *                         be explicitly enabled using other methods in this API
     * @param eventClass       A user defined class that annotates the fields
     *                         with @EventColumn. This class specifies the
     *                         fields/columns for this event.
     * @param bufferCapacity   Number of events to store for this event in a circular
     *                         buffer. Older events will be discarded once the
     *                         buffer is full.
     * @param metaData         variable arguments that qualify an event
     *                         eg. EV_MDATA_WARN, EV_MDATA_ERROR etc. See Debug Event Qualifiers
     * @return                 IEventUpdater with update methods that can be used to
     *                         update an event of the given eventClass
     * @throws MaxEventsRegistered
     */
    public <T> IEventUpdater<T> registerEvent(String moduleName, String eventName,
                                              String eventDescription,
                                              EventType eventType,
                                              Class<T> eventClass,
                                              int bufferCapacity,
                                              String... metaData)
                                                      throws MaxEventsRegistered;

    /**
     * Update the global event stores with values from the thread local stores. This
     * method is not typically intended for use by any module. It's typical usage is from
     * floodlight core for events that happen in the packet processing pipeline.
     * For other rare events, flushEvents should be called.
     */
    public void flushEvents();

    /**
     * Determine if eventName is a registered event for a given moduleName
     */
    public boolean containsModuleEventName(String moduleName, String eventName);

    /**
     * Determine if any events have been registered for module of name moduleName
     */
    public boolean containsModuleName(String moduleName);

    /**
     * Get event history for all events. This call can be expensive as it
     * formats the event histories for all events.
     *
     * @return  a list of all event histories or an empty list if no events have
     *          been registered
     */
    public List<DebugEventInfo> getAllEventHistory();

    /**
     * Get event history for all events registered for a given moduleName
     *
     * @return  a list of all event histories for all events registered for the
     *          the module or an empty list if there are no events for this module
     */
    public List<DebugEventInfo> getModuleEventHistory(String moduleName);

    /**
     * Get event history for a single event
     *
     * @param  moduleName  registered module name
     * @param  eventName   registered event name for moduleName
     * @param  last        last X events
     * @return DebugEventInfo for that event, or null if the moduleEventName
     *         does not correspond to a registered event.
     */
    public DebugEventInfo getSingleEventHistory(String moduleName, String eventName, int last);

    /**
     * Wipe out all event history for all registered events
     */
    public void resetAllEvents();

    /**
     * Wipe out all event history for all events registered for a specific module
     *
     * @param moduleName  registered module name
     */
    public void resetAllModuleEvents(String moduleName);

    /**
     * Wipe out event history for a single event
     * @param  moduleName  registered module name
     * @param  eventName   registered event name for moduleName
     */
    public void resetSingleEvent(String moduleName, String eventName);

    /**
     * Retrieve a list of moduleNames registered for debug events or an empty
     * list if no events have been registered in the system
     */
    public List<String> getModuleList();

    /**
     * Returns a list of all events registered for a specific moduleName
     * or a empty list
     */
    public List<String> getModuleEventList(String moduleName);

}
