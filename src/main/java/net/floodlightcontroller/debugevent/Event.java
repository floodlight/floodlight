package net.floodlightcontroller.debugevent;

import java.lang.reflect.Field;

import net.floodlightcontroller.debugevent.DebugEventService.EventCategory;
import net.floodlightcontroller.debugevent.EventResource.EventResourceBuilder;
import net.floodlightcontroller.debugevent.EventResource.Metadata;
import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;

/**
 * Generic Event class used to store different categories of events.
 * DebugEventService uses this class to store each Event instance.
 * Each {@link EventCategory} has a different object of type <b>EventClass</b>.
 * This is used internally for all DebugEvent processing. For display via REST
 * and CLI, it is transformed to {@link EventResource} object.
 */
public class Event {
    private final long eventInstanceId;
    private volatile boolean acked;
    private final long timeMs;
    private final long threadId;
    private final String threadName;
    private final Object eventData;

    public Event(long timeMs, long threadId, String threadName,
                 Object eventData, long eventInstanceId) {
        this.timeMs = timeMs;
        this.threadId = threadId;
        this.threadName = threadName;
        this.eventData = eventData;
        this.eventInstanceId = eventInstanceId;
        this.acked = false;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public Object geteventData() {
        return eventData;
    }

    public long getEventInstanceId() {
        return eventInstanceId;
    }

    public boolean isAcked() {
        return acked;
    }

    public void setAcked(boolean acked) {
        this.acked = acked;
    }

    @Override
    public String toString() {
        return "Event [eventInstanceId=" + eventInstanceId + ", acked="
               + acked + ", timeMs=" + timeMs + ", threadId=" + threadId
               + ", threadName=" + threadName + ", eventData=" + eventData
               + "]";
    }

    public EventResource getFormattedEvent(Class<?> eventClass,
                                           String moduleEventName) {
        if (eventClass == null || !eventClass.equals(eventData.getClass())) {
            EventResourceBuilder edb = new EventResourceBuilder();
            edb.dataFields.add(new Metadata("Error",
                                            "null event data or event-class does not match event-data"));
            return edb.build();
        }

        EventResourceBuilder edb = new EventResourceBuilder();
        edb.setTimeStamp(timeMs);
        edb.setThreadId(threadId);
        edb.setThreadName(threadName);
        edb.setModuleEventName(moduleEventName);
        edb.setEventInstanceId(eventInstanceId);
        edb.setAcked(acked);
        customFormat(eventClass, eventData, edb);
        return edb.build();
    }

    @SuppressWarnings("unchecked")
    private void customFormat(Class<?> clazz, Object eventData,
                              EventResourceBuilder eventDataBuilder) {
        for (Field f : clazz.getDeclaredFields()) {
            EventColumn ec = f.getAnnotation(EventColumn.class);
            if (ec == null) continue;
            f.setAccessible(true);
            try {
                Object obj = f.get(eventData);
                @SuppressWarnings("rawtypes")
                CustomFormatter cf = DebugEventService.customFormatter.get(ec.description());

                if (cf == null) {
                    throw new IllegalArgumentException(
                                                       "CustomFormatter for "
                                                               + ec.description()
                                                               + " does not exist.");
                } else {
                    cf.customFormat(obj, ec.name(), eventDataBuilder);
                }
            } catch (ClassCastException e) {
                eventDataBuilder.dataFields.add(new Metadata("Error",
                                                             e.getMessage()));
            } catch (IllegalArgumentException e) {
                eventDataBuilder.dataFields.add(new Metadata("Error",
                                                             e.getMessage()));
            } catch (IllegalAccessException e) {
                eventDataBuilder.dataFields.add(new Metadata("Error",
                                                             e.getMessage()));
            }
        }
    }

}
