package net.floodlightcontroller.util;

import org.projectfloodlight.openflow.protocol.OFMessage;

/**
 * Tools to help work with OFMessages.
 * 
 * Compare OFMessage-extending objects (e.g. OFFlowMod, OFPacketIn)
 * where the XID does not matter. This is especially useful for
 * unit testing where the XID of the OFMessage might vary whereas
 * the expected OFMessgae must have a set XID.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */

public class OFMessageUtils {

	/**
	 * Prevent instantiation
	 */
	private OFMessageUtils() {};
	
	/**
	 * Returns true if each object is deeply-equal in the same manner that
	 * Object's equals() does with the exception of the XID field, which is
	 * ignored; otherwise, returns false.
	 * 
	 * NOTE: This function is VERY INEFFICIENT and creates a new OFMessage
	 * object in order to the the comparison minus the XID. It is advised
	 * that you use it sparingly and ideally only within unit tests.
	 * 
	 * @param a; object A to compare
	 * @param b; object B to compare
	 * @return true if A and B are deeply-equal; false otherwise
	 */
	public static boolean equalsIgnoreXid(OFMessage a, OFMessage b) {
		OFMessage.Builder mb = b.createBuilder().setXid(a.getXid());
		return a.equals(mb.build());
	}
}
