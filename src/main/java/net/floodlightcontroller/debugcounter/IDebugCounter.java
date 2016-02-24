package net.floodlightcontroller.debugcounter;

/**
 * A concurrent counter.
 * The counter returned when registering a counter. The counter value
 * is a positive long. The counter cannot be decremented, but it can be
 * reset to 0. The counter does not protect against overflow. If the
 * value exceeds MAX_LONG it will silently overflow to MIN_LONG
 * @author gregor
 *
 */
public interface IDebugCounter {
    /**
     * Increment this counter by 1.
     */
    void increment();

    /**
     * Add the given increment to this counter
     * @param incr
     */
    void add(long incr);

    /**
     * Retrieve the value of the counter.
     */
    long getCounterValue();
    
    /**
     * Retrieve the last-modified date of the counter.
     */
    long getLastModified();

    /**
     * Reset the value of the counter to 0.
     */
	void reset();
}
