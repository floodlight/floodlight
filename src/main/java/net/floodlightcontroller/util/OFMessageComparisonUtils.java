package net.floodlightcontroller.util;

import java.util.Arrays;

import org.projectfloodlight.openflow.protocol.OFPacketIn;

/**
 * Compare OFMessage-extending objects (e.g. OFFlowMod, OFPacketIn)
 * where the XID does not matter. This is especially useful for
 * unit testing where the XID of the OFMessage might vary whereas
 * the expected OFMessgae must have a set XID.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */

public class OFMessageComparisonUtils {

	/**
	 * Prevent instantiation
	 */
	private OFMessageComparisonUtils() {};
	
	/**
	 * Returns true if each object is deeply-equal in the same manner that
	 * Object's equals() does; otherwise, returns false. It's worth noting
	 * that if you want to include the XID in the comparison, just use the
	 * normal equals() function. This function does the exact same thing, 
	 * but it conditionally ignores the XID.
	 * 
	 * @param th1s; object A to compare
	 * @param that; object B to compare
	 * @param ignoreXid; true to ignore the XID field; false to include the XID
	 * @return true if A and B are deeply-equal; false otherwise
	 */
	public boolean areEqual(OFPacketIn th1s, OFPacketIn that, boolean ignoreXid) {
		if (th1s == that) { // same objects or both null
			return true;
		} else if (this == null ^ that == null) { // only one is null
			return false;
		} // else th1s and that are both non-null
		
		if (!ignoreXid) {
			return th1s.equals(that);
		} else {
			/*
			 * The approach is to check every possible field surrounded by a try-catch.
			 * This will allow for an OFVersion-agnostic check on a higher level where
			 * we don't worry about whether or not a particular OFVersion supports a
			 * particular operation exposed in the common OpenFlowJ-LoxiGen API. The
			 * UnsupportedOperationException will be thrown by both objects if their
			 * OFVersions are the same, which constitutes equality for the unsupported
			 * operation. Supported operations will be supported by both objects and thus
			 * will not throw the exception and will allow for the (deep) equality check.
			 * 
			 * This isn't the prettiest way, but I feel it's the most future-proof way to
			 * implement this check on a high-level without having to re-invent equals() from
			 * each OFVersion excluding the XIDs. Not every getter throws the exception at
			 * this time, although all are surrounded by the try-catch. In future OpenFlow 
			 * versions, one getter might if its unused.
			 */
			
			/* 
			 * Explicitly check the OFVersions. If they're not equal, then the objects certainly
			 * aren't equal either, so we can abort now. If they are equal, then we can check 
			 * all fields and rely on the throws UnsupportedOperationException to handle the 
			 * different OFVersions. OFVersion is an enum, so shallow check is okay.
			 */
			if (th1s.getVersion() != that.getVersion()) {
				return false;
			}
			
			try {
				if (th1s.getBufferId() == null ^ that.getBufferId() == null) {
					return false; 
				} else if (th1s.getBufferId() != null && that.getBufferId() != null
						&& !th1s.getBufferId().equals(that.getBufferId())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getCookie() == null ^ that.getCookie() == null) {
					return false;
				} else if (th1s.getCookie() != null && that.getCookie() != null
						&& !th1s.getCookie().equals(that.getCookie())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getData() == null ^ that.getData() == null) {
					return false;
				} else if (th1s.getData() != null && that.getData() != null
						&& !Arrays.equals(th1s.getData(), that.getData())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getInPhyPort() == null ^ that.getInPhyPort() == null) {
					return false;
				} else if (th1s.getInPhyPort() != null && that.getInPhyPort() != null
						&& !th1s.getInPhyPort().equals(that.getInPhyPort())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getInPort() == null ^ that.getInPort() == null) {
					return false;
				} else if (th1s.getInPort() != null && that.getInPort() != null
						&& !th1s.getInPort().equals(that.getInPort())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getMatch() == null ^ that.getMatch() == null) {
					return false;
				} else if (th1s.getMatch() != null && that.getMatch() != null
						&& !th1s.getMatch().equals(that.getMatch())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getReason() == null ^ that.getReason() == null) {
					return false;
				} else if (th1s.getReason() != null && that.getReason() != null
						&& !th1s.getReason().equals(that.getReason())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getTableId() == null ^ that.getTableId() == null) {
					return false;
				} else if (th1s.getTableId() != null && that.getTableId() != null
						&& !th1s.getTableId().equals(that.getTableId())) {
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getTotalLen() != that.getTotalLen()) { // int
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			try {
				if (th1s.getType() != that.getType()) { // enum
					return false;
				}
			} catch (UnsupportedOperationException e) {};
			
			/*
			 * All checks passed
			 */
			return true;
		}
	}
}
