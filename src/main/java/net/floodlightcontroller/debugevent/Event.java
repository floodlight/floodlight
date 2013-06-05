package net.floodlightcontroller.debugevent;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;

import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.util.HexString;

public class Event {
    long timestamp;
    long threadId;
    Object eventData;
    private String returnString;

    public Event(long timestamp, long threadId, Object eventData) {
        super();
        this.timestamp = timestamp;
        this.threadId = threadId;
        this.eventData = eventData;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public Object geteventData() {
        return eventData;
    }

    public void seteventData(Object eventData) {
        this.eventData = eventData;
    }

    @Override
    public String toString() {
        return "Event [timestamp=" + timestamp + ", threadId=" + threadId
               + ", eventData=" + eventData.toString() + "]";
    }

    public String toString(Class<?> eventClass, String moduleEventName) {
        if (this.returnString != null && eventClass != null &&
                eventClass.equals(eventData.getClass()))
            return this.returnString;

        this.returnString = new StringBuilder().append(moduleEventName)
                        .append(" [")
                        .append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                                        .format(timestamp))
                        .append(", threadId=").append(threadId).append(", ")
                        .append(customFormat(eventClass, eventData))
                        .append("]").toString();
        return this.returnString;
    }

    private String customFormat(Class<?> clazz, Object eventData) {
        if (eventData == null || clazz == null) {
            return "Error: null event data or event class";
        }
        StringBuilder result = new StringBuilder();

        for (Field f : clazz.getDeclaredFields()) {
            EventColumn ec = f.getAnnotation(EventColumn.class);
            if (ec == null) continue;
            f.setAccessible(true);
            try {
                Object obj =  f.get(eventData);
                if (ec.description() == EventFieldType.DPID) {
                    result.append(ec.name()).append("=")
                    .append(HexString.toHexString((Long) obj));
                } else if (ec.description() == EventFieldType.MAC) {
                    result.append(ec.name()).append("=")
                    .append(HexString.toHexString((Long) obj, 6));
                } else if (ec.description() == EventFieldType.IPv4) {
                    result.append(ec.name()).append("=")
                    .append(IPv4.fromIPv4Address((Integer) obj));
                } else {
                    result.append(ec.name()).append("=")
                    .append(obj.toString());
                }
            } catch (ClassCastException e) {
                result.append(e);
            } catch (IllegalArgumentException e) {
                result.append(e);
            } catch (IllegalAccessException e) {
                result.append(e);
            }
            result.append(", ");
        }
        String retval = result.toString();
        int index = retval.lastIndexOf(',');
        return (index > 0) ? retval.substring(0, index) : retval;
    }

}
