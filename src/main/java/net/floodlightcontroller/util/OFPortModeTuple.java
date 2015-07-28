package net.floodlightcontroller.util;

import javax.annotation.Nonnull;

import org.projectfloodlight.openflow.types.OFPort;

/**
 * Make it easier to track a port and it's operating {@link OFPortMode mode}.
 * Objects of this class are immutable.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFPortModeTuple {
	private final OFPort p;
	private final OFPortMode m;
	
	private OFPortModeTuple(@Nonnull OFPort p, @Nonnull OFPortMode m) {
		this.p = p;
		this.m = m;
	}

	public static OFPortModeTuple of(OFPort p, OFPortMode m) {
		if (p == null) {
			throw new NullPointerException("Port cannot be null.");
		}
		if (m == null) {
			throw new NullPointerException("Mode cannot be null.");
		}
		return new OFPortModeTuple(p, m);
	}
	
	public OFPort getPort() {
		return this.p;
	}
	
	public OFPortMode getMode() {
		return this.m;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((m == null) ? 0 : m.hashCode());
		result = prime * result + ((p == null) ? 0 : p.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OFPortModeTuple other = (OFPortModeTuple) obj;
		if (m != other.m)
			return false;
		if (p == null) {
			if (other.p != null)
				return false;
		} else if (!p.equals(other.p))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "OFPortModeTuple [p=" + p + ", m=" + m + "]";
	}
}
