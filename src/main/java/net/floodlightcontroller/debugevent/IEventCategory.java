package net.floodlightcontroller.debugevent;

/**
 * EventCategory is used to log events for pre-registered events.
 */
public interface IEventCategory<T> {

    /**
     * Logs the instance of the event thread-locally. Flushing to the global
     * circular buffer for this event is delayed resulting in better
     * performance. This method should typically be used by those events that
     * happen in the packet processing pipeline
     *
     * @param event
     *            an instance of the user-defined event of type T
     */
    public void newEventNoFlush(T event);

    /**
     * Logs the instance of the event thread-locally and immediately flushes to
     * the global circular buffer for this event. This method should typically
     * be used by those events that happen outside the packet processing
     * pipeline
     *
     * @param event
     *            an instance of the user-defined event of type T
     */
    public void newEventWithFlush(T event);

}
