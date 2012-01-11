package net.floodlightcontroller.util;

public class EventHistoryBaseInfo {
    public int              idx;
    public long             time_ns; // timestamp in nanoseconds
    public EventHistory.EvState          state;
    public EventHistory.EvAction         action;

    // Getters
    public int getIdx() {
        return idx;
    }
    public long getTime_ns() {
        return time_ns;
    }
    public EventHistory.EvState getState() {
        return state;
    }
    public EventHistory.EvAction getAction() {
        return action;
    }
}