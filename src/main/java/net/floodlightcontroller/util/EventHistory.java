/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.util;

import java.util.ArrayList;

/**
 * @author subrata
 *
 */

public class EventHistory<T> {
    public static final int EV_HISTORY_DEFAULT_SIZE = 1024;

    public int     event_history_size;
    public int     current_index;
    public boolean full; // true if all are in use
    public ArrayList<Event> events;

    public int getEvent_history_size() {
        return event_history_size;
    }
    public int getCurrent_index() {
        return current_index;
    }
    public boolean isFull() {
        return full;
    }
    public ArrayList<Event> getEvents() {
        return events;
    }

    public class Event {
        public EventHistoryBaseInfo base_info;
        public T info;
    }

    public enum EvState {
        FREE,             // no valid event written yet
        BEING_MODIFIED,   // event is being updated with new value, skip
        ACTIVE,           // event is active and can be displayed
    }

    public enum EvAction {
        ADDED,   // specific entry added
        REMOVED, // specific entry removed
        UPDATED, // Entry updated
        BLOCKED, // Blocked - used for Attachment Points
        UNBLOCKED,
        CLEARED,  // All entries are removed
        PKT_IN,
        PKT_OUT,
        SWITCH_CONNECTED,
        SWITCH_DISCONNECTED,
        LINK_ADDED,
        LINK_DELETED,
        LINK_PORT_STATE_UPDATED,
        CLUSTER_ID_CHANGED_FOR_CLUSTER,
        CLUSTER_ID_CHANGED_FOR_A_SWITCH,
    }

    // Constructor
    public EventHistory(int maxEvents) {
        events = new ArrayList<Event>(maxEvents);

        for (int idx = 0; idx < maxEvents; idx++) {
            Event evH     = new Event();
            evH.base_info = new EventHistoryBaseInfo();
            evH.info      = null;
            evH.base_info.state = EvState.FREE;
            evH.base_info.idx   = idx;
            events.add(idx, evH);
        }
        
        event_history_size   = maxEvents;
        current_index        = 0;
        full                 = false;
    }

    // Constructor for default size
    public EventHistory() {
        this(EV_HISTORY_DEFAULT_SIZE);
    }

    // Copy constructor - copy latest k items of the event history
    public EventHistory(EventHistory<T> eventHist, int latestK) {
        if (eventHist == null) {
            return;
        }
        int curSize = (eventHist.full)?eventHist.event_history_size:
                                                    eventHist.current_index;
        int size  = (latestK < curSize)?latestK:curSize;
        int evIdx = eventHist.current_index;
        int topSz = (evIdx >= size)?size:evIdx;

        // Need to create a new one since size is different
        events = new ArrayList<Event>(size);

        // Get the top part
        int origIdx = evIdx;
        for (int idx = 0; idx < topSz; idx++) {
            Event evH         = eventHist.events.get(--origIdx);
            evH.base_info.idx = idx;
            events.add(idx, evH);
        }

        // Get the bottom part
        origIdx = eventHist.event_history_size;
        for (int idx = topSz; idx < size; idx++) {
            Event evH = eventHist.events.get(--origIdx);
            evH.base_info.idx = idx;
            events.add(idx, evH);
        }
        
        event_history_size   = size;
        current_index        = 0; // since it is full
        full                 = true;
    }

    // Get an index for writing a new event. This method is synchronized for
    // this event history infra. to be thread-safe. Once the index is obtained
    // by the caller event at the index is updated without any lock
    public synchronized int NextIdx() {
        // curIdx should be in the 0 to evArraySz-1
        if (current_index == (event_history_size-1)) {
            current_index = 0;
            full = true;
            return (event_history_size-1);
        } else {
            current_index++;
            return (current_index-1);
        }
    }

    /**
     * Add an event to the event history
     * Eliminate java garbage cration by reusing the same object T
     * Supplied object t is used to populate the event history array
     * and the current object at that array location is returned to the
     * calling process so that the calling process can use that object
     * for the next event of the same type
     * @param t
     * @param op
     * @return
     */

    public T put(T t, EvAction action) {
        int idx = NextIdx();
        Event evH = events.get(idx);
        evH.base_info.state = EvState.BEING_MODIFIED;
        evH.base_info.time_ms = System.currentTimeMillis();
        evH.base_info.action = action;
        T temp = evH.info;
        evH.info = t;
        evH.base_info.state = EvState.ACTIVE;
        return temp;
    }

    /***
     * Clear the event history, needs to be done under lock
     */
    public void clear() {
        for (int idx = 0; idx < event_history_size; idx++) {
            Event evH = events.get(idx);
            evH.base_info.state = EvState.FREE;
            current_index = 0;
            full = false;
        }
    }
}
