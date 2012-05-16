package net.floodlightcontroller.util;


import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(using=EventHistoryBaseInfoJSONSerializer.class)
public class EventHistoryBaseInfo {
    public int              idx;
    public long             time_ms; // timestamp in milliseconds
    public EventHistory.EvState          state;
    public EventHistory.EvAction         action;

    // Getters
    public int getIdx() {
        return idx;
    }
    public long getTime_ms() {
        return time_ms;
    }
    public EventHistory.EvState getState() {
        return state;
    }
    public EventHistory.EvAction getAction() {
        return action;
    }
}