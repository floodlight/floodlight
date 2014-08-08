package net.floodlightcontroller.debugevent;

import net.floodlightcontroller.debugevent.EventResource.EventResourceBuilder;

/**
 * Format Event object based on its class and store accordingly in
 * {@link EventResource}
 */
public interface CustomFormatter<T> {

    public abstract EventResourceBuilder
            customFormat(T obj, String name, EventResourceBuilder edb);
}
