package net.floodlightcontroller.debugevent;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import net.floodlightcontroller.debugevent.DebugEventService.EventInfo;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;

/**
 * All the Immutable Resource classes for REST API.
 */
@Immutable
public class DebugEventResource {

    protected static final Logger logger = LoggerFactory.getLogger(DebugEventResource.class);
    public static final String MODULE_NAME_PREDICATE = "module-name";
    public static final String EVENT_NAME_PREDICATE = "event-name";
    public static final String LAST_PREDICATE = "num-of-events";
    public static final String EVENT_ID = "event-id";
    public static final String EVENT_INSTANCE_ID = "event-instance-id";
    public static final String ACKED = "acked";

    /**
     * Resource class for {@link EventInfo}. Used to create Immutable objects
     * returned in response to REST calls.
     */
    @Immutable
    public static class EventInfoResource implements
        Comparable<EventInfoResource> {

        private final int eventId;
        private final boolean enabled;
        private final int bufferCapacity;
        private final EventType etype;
        private final String eventDesc;
        private final String eventName;
        private final String moduleName;
        private final int numOfEvents;
        private final boolean ackable;
        public final ImmutableList<EventResource> events;

        public EventInfoResource(EventInfo eventInfo,
                                 List<EventResource> events) {
            super();
            this.eventId = eventInfo.getEventId();
            this.enabled = eventInfo.isEnabled();
            this.bufferCapacity = eventInfo.getBufferCapacity();
            this.etype = eventInfo.getEtype();
            this.eventDesc = eventInfo.getEventDesc();
            this.eventName = eventInfo.getEventName();
            this.moduleName = eventInfo.getModuleName();
            this.numOfEvents = eventInfo.getNumOfEvents();
            this.ackable = eventInfo.isAckable();
            this.events = ImmutableList.copyOf(events);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getBufferCapacity() {
            return bufferCapacity;
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

        public int getNumOfEvents() {
            return numOfEvents;
        }

        public boolean isAckable() {
            return ackable;
        }

        public List<EventResource> getEvents() {
            return events;
        }

        public int getEventId() {
            return eventId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (ackable ? 1231 : 1237);
            result = prime * result + bufferCapacity;
            result = prime * result + (enabled ? 1231 : 1237);
            result = prime * result
                     + ((etype == null) ? 0 : etype.hashCode());
            result = prime * result
                     + ((eventDesc == null) ? 0 : eventDesc.hashCode());
            result = prime * result + eventId;
            result = prime * result
                     + ((eventName == null) ? 0 : eventName.hashCode());
            result = prime * result
                     + ((events == null) ? 0 : events.hashCode());
            result = prime * result
                     + ((moduleName == null) ? 0 : moduleName.hashCode());
            result = prime * result + numOfEvents;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            EventInfoResource other = (EventInfoResource) obj;
            if (ackable != other.ackable) return false;
            if (bufferCapacity != other.bufferCapacity) return false;
            if (enabled != other.enabled) return false;
            if (etype != other.etype) return false;
            if (eventDesc == null) {
                if (other.eventDesc != null) return false;
            } else if (!eventDesc.equals(other.eventDesc)) return false;
            if (eventId != other.eventId) return false;
            if (eventName == null) {
                if (other.eventName != null) return false;
            } else if (!eventName.equals(other.eventName)) return false;
            if (events == null) {
                if (other.events != null) return false;
            } else if (!events.equals(other.events)) return false;
            if (moduleName == null) {
                if (other.moduleName != null) return false;
            } else if (!moduleName.equals(other.moduleName)) return false;
            if (numOfEvents != other.numOfEvents) return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("EventInfoResource [eventId=");
            builder.append(eventId);
            builder.append(", enabled=");
            builder.append(enabled);
            builder.append(", bufferCapacity=");
            builder.append(bufferCapacity);
            builder.append(", etype=");
            builder.append(etype);
            builder.append(", eventDesc=");
            builder.append(eventDesc);
            builder.append(", eventName=");
            builder.append(eventName);
            builder.append(", moduleName=");
            builder.append(moduleName);
            builder.append(", numOfEvents=");
            builder.append(numOfEvents);
            builder.append(", ackable=");
            builder.append(ackable);
            builder.append(", events=");
            builder.append(events);
            builder.append("]");
            return builder.toString();
        }

        /**
         * The natural order of this class is ascending on eventId and
         * consistent with equals.
         */
        @Override
        public int compareTo(EventInfoResource o) {
            return ComparisonChain.start().compare(eventId, o.eventId)
                                  .compareFalseFirst(enabled, o.enabled)
                                  .compare(bufferCapacity, o.bufferCapacity)
                                  .compare(etype, o.etype)
                                  .compare(eventDesc, o.eventDesc)
                                  .compare(eventName, o.eventName)
                                  .compare(moduleName, o.moduleName)
                                  .compare(numOfEvents, o.numOfEvents)
                                  .result();
        }
    }
}
