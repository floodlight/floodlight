package net.floodlightcontroller.util;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Because it's handy.
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class IterableUtils {
	
	/**
	 * Convert an Iterable to a Collection (ArrayList under the hood). All items in the
	 * Iterable will be retained.
	 * @param i
	 * @return
	 */
	public static <T> Collection<T> toCollection(Iterable<T> i) {
		if (i == null) {
			throw new IllegalArgumentException("Iterable 'i' cannot be null");
		}
		Collection<T> c = new ArrayList<T>();
		for (T t : i) {
			c.add(t);
		}
		return c;
	}
}