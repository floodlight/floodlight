package net.floodlightcontroller.core;

/**
 * This is a dummy marker. IListener enforces call ordering by type. However,
 * for IHAListeners we only have a single order. So we use this type as a
 * placeholder to satisfy the generic requirement.
 * @author gregor
 *
 */
public enum HAListenerTypeMarker {
}
