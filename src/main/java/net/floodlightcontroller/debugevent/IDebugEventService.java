package net.floodlightcontroller.debugevent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugevent.DebugEventResource.EventInfoResource;
import net.floodlightcontroller.debugevent.DebugEventService.EventCategory;
import net.floodlightcontroller.debugevent.DebugEventService.EventCategoryBuilder;

public interface IDebugEventService extends IFloodlightService {

    /**
     * Different event types. Events that are meant to be logged on demand need
     * to be separately enabled/disabled.
     */
    public enum EventType {
        ALWAYS_LOG, LOG_ON_DEMAND
    }

    /**
     * Is the Event <b>ACKABLE</b> or <b>NOT_ACKABLE</b>
     */
    public enum AckableEvent {
        ACKABLE, NOT_ACKABLE
    }

    /**
     * Describes the type of field obtained from reflection
     */
    enum EventFieldType {
        DPID, IPv4, MAC, STRING, OBJECT, PRIMITIVE, COLLECTION_IPV4,
        COLLECTION_ATTACHMENT_POINT, COLLECTION_OBJECT, SREF_COLLECTION_OBJECT,
        SREF_OBJECT
    }

    /**
     * EventColumn is the only annotation given to the fields of the event when
     * updating an event.
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
     * Returns an {@link EventCategoryBuilder} that can be used to build a new
     * {@link EventCategory}. Before calling the {@literal build} method, set
     * the following parameters:
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
     * @throws MaxEventsRegistered
     */
    public <T> EventCategoryBuilder<T> buildEvent(Class<T> evClass);

    /**
     * Update the global event stores with values from the thread local stores.
     * This method is not typically intended for use by any module. It's typical
     * usage is from floodlight core for events that happen in the packet
     * processing pipeline. For other rare events, flushEvents should be called.
     */
    public void flushEvents();

    /**
     * Determine if eventName is a registered event for a given moduleName
     */
    public boolean containsModuleEventName(String moduleName,
                                           String eventName);

    /**
     * Determine if any events have been registered for module of name
     * moduleName
     */
    public boolean containsModuleName(String moduleName);

    /**
     * Get event history for all events. This call can be expensive as it
     * formats the event histories for all events.
     *
     * @return a list of all event histories or an empty list if no events have
     *         been registered
     */
    public List<EventInfoResource> getAllEventHistory();

    /**
     * Get event history for all events registered for a given moduleName
     *
     * @return a list of all event histories for all events registered for the
     *         the module or an empty list if there are no events for this
     *         module
     */
    public List<EventInfoResource> getModuleEventHistory(String moduleName);

    /**
     * Get event history for a single event
     *
     * @param moduleName
     *            registered module name
     * @param eventName
     *            registered event name for moduleName
     * @param numOfEvents
     *            last X events
     * @return DebugEventInfo for that event, or null if the moduleEventName
     *         does not correspond to a registered event.
     */
    public EventInfoResource getSingleEventHistory(String moduleName,
                                                   String eventName,
                                                   int numOfEvents);

    /**
     * Wipe out all event history for all registered events
     */
    public void resetAllEvents();

    /**
     * Wipe out all event history for all events registered for a specific
     * module
     *
     * @param moduleName
     *            registered module name
     */
    public void resetAllModuleEvents(String moduleName);

    /**
     * Wipe out event history for a single event
     *
     * @param moduleName
     *            registered module name
     * @param eventName
     *            registered event name for moduleName
     */
    public void resetSingleEvent(String moduleName, String eventName);

    /**
     * Retrieve a list of moduleNames registered for debug events or an empty
     * list if no events have been registered in the system
     */
    public List<String> getModuleList();

    /**
     * Returns a list of all events registered for a specific moduleName or a
     * empty list
     */
    public List<String> getModuleEventList(String moduleName);

    /**
     * Sets the 'ack' for the individual {@link DebugEventService} instance
     * pointed by the <b>eventId</b> and <b>eventInstanceId</b> given in the
     * PATCH request <br/>
     * Returns void
     *
     * @param eventId
     *            unique event queue identifier
     * @param eventInstanceId
     *            unique event identifier
     * @param ack
     *            boolean ack - true or false
     */
    public void setAck(int eventId, long eventInstanceId, boolean ack);

}
