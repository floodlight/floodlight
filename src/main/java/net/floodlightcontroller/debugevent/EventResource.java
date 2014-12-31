package net.floodlightcontroller.debugevent;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import java.util.Date;

import com.google.common.collect.ImmutableList;

/**
 * Event Instance of a particular event category. This is only for REST purpose.
 * Actual {@link Event} class consists of Object of Event. Here we parse it into
 * its string form using {@link getCustomFormatter} and return for REST and CLI.
 */
@Immutable
public class EventResource {
    private final Date timestamp;
    private final long threadId;
    private final String threadName;
    private final String moduleEventName;
    private final ImmutableList<Metadata> dataFields;
    // NOTE: this is only for CLI purpose. cascaded fields show up as
    // different tables in CLI which is not very useful
    private final String dataString;
    long eventInstanceId;
    boolean acked;

    private EventResource(EventResourceBuilder evInstanceBuilder) {
        this.timestamp = evInstanceBuilder.timestamp;
        this.threadId = evInstanceBuilder.threadId;
        this.threadName = evInstanceBuilder.threadName;
        this.moduleEventName = evInstanceBuilder.moduleEventName;
        this.dataFields = ImmutableList.copyOf(evInstanceBuilder.dataFields);
        this.dataString = this.dataFields.toString();
        this.eventInstanceId = evInstanceBuilder.eventInstanceId;
        this.acked = evInstanceBuilder.acked;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getModuleEventName() {
        return moduleEventName;
    }

    public List<Metadata> getDataFields() {
        return dataFields;
    }

    public String getDataString() {
        return dataString;
    }

    public long getEventInstanceId() {
        return eventInstanceId;
    }

    public boolean isAcked() {
        return acked;
    }

    public static class EventResourceBuilder {
        private Date timestamp;
        private long threadId;
        private String threadName;
        private String moduleEventName;
        protected List<Metadata> dataFields;
        long eventInstanceId;
        boolean acked;

        public EventResourceBuilder() {
            this.dataFields = new ArrayList<Metadata>();
        }

        public EventResource build() {
            return new EventResource(this);
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimeStamp(long timeMs) {
            this.timestamp = new Date(timeMs);
        }

        public long getThreadId() {
            return threadId;
        }

        public void setThreadId(long threadId) {
            this.threadId = threadId;
        }

        public String getThreadName() {
            return threadName;
        }

        public void setThreadName(String threadName) {
            this.threadName = threadName;
        }

        public String getModuleEventName() {
            return moduleEventName;
        }

        public void setModuleEventName(String moduleEventName) {
            this.moduleEventName = moduleEventName;
        }

        public List<Metadata> getDataFields() {
            return dataFields;
        }

        public void setDataFields(List<Metadata> dataFields) {
            this.dataFields = dataFields;
        }

        public long getEventInstanceId() {
            return eventInstanceId;
        }

        public void setEventInstanceId(long eventInstanceId) {
            this.eventInstanceId = eventInstanceId;
        }

        public boolean isAcked() {
            return acked;
        }

        public void setAcked(boolean acked) {
            this.acked = acked;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((dataFields == null) ? 0 : dataFields.hashCode());
        result = prime * result
                 + ((dataString == null) ? 0 : dataString.hashCode());
        result = prime
                 * result
                 + ((moduleEventName == null) ? 0
                                             : moduleEventName.hashCode());
        result = prime * result + (int) (threadId ^ (threadId >>> 32));
        result = prime * result
                 + ((threadName == null) ? 0 : threadName.hashCode());
        result = prime * result
                 + ((timestamp == null) ? 0 : timestamp.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        EventResource other = (EventResource) obj;
        if (dataFields == null) {
            if (other.dataFields != null) return false;
        } else if (!dataFields.equals(other.dataFields)) return false;
        if (dataString == null) {
            if (other.dataString != null) return false;
        } else if (!dataString.equals(other.dataString)) return false;
        if (moduleEventName == null) {
            if (other.moduleEventName != null) return false;
        } else if (!moduleEventName.equals(other.moduleEventName))
                                                                  return false;
        if (threadId != other.threadId) return false;
        if (threadName == null) {
            if (other.threadName != null) return false;
        } else if (!threadName.equals(other.threadName)) return false;
        if (timestamp == null) {
            if (other.timestamp != null) return false;
        } else if (!timestamp.equals(other.timestamp)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(moduleEventName);
        builder.append(" ");
        builder.append(timestamp);
        builder.append(", threadId=");
        builder.append(threadId);
        builder.append(", threadName=\"");
        builder.append(threadName);
        builder.append("\", dataFields=");
        builder.append(dataFields);
        builder.append(", eventInstanceId=");
        builder.append(eventInstanceId);
        builder.append(", acked=");
        builder.append(acked);
        return builder.toString();
    }

    /**
     * Actual {@link Event} has Object of Event. The fields of that Object are
     * converted to {@literal String} and returned as {@link Metadata} with
     * {@link EventResource}
     */
    @Immutable
    public static class Metadata {
        private final String eventClass;
        private final String eventData;

        public Metadata(String eventClass, String eventData) {
            this.eventClass = eventClass;
            this.eventData = eventData;
        }

        public String getEventClass() {
            return eventClass;
        }

        public String getEventData() {
            return eventData;
        }

        @Override
        public String toString() {
            return this.eventClass + ":" + this.eventData;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                     + ((eventClass == null) ? 0 : eventClass.hashCode());
            result = prime * result
                     + ((eventData == null) ? 0 : eventData.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Metadata other = (Metadata) obj;
            if (eventClass == null) {
                if (other.eventClass != null) return false;
            } else if (!eventClass.equals(other.eventClass)) return false;
            if (eventData == null) {
                if (other.eventData != null) return false;
            } else if (!eventData.equals(other.eventData)) return false;
            return true;
        }
    }
}
