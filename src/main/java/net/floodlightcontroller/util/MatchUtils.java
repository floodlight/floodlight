package net.floodlightcontroller.util;

import java.util.Iterator;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;

/**
 * Match helper functions. Use with any OpenFlowJ-Loxi Match.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */
public class MatchUtils {
	/**
	 * Create a point-to-point match for two devices at the IP layer.
	 * Takes an existing match (e.g. from a PACKET_IN), and masks all
	 * MatchFields leaving behind:
	 * 		IN_PORT
	 * 		VLAN_VID
	 * 		ETH_TYPE
	 * 		ETH_SRC
	 * 		ETH_DST
	 * 		IPV4_SRC
	 * 		IPV4_DST
	 * 		IP_PROTO (might remove this)
	 * 
	 * If one of the above MatchFields is wildcarded in Match m,
	 * that MatchField will be wildcarded in the returned Match.
	 * 
	 * @param m The match to remove all L4+ MatchFields from
	 * @return A new Match object with all MatchFields masked/wildcared
	 * except for those listed above.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Match maskL4AndUp(Match m) {
		Match.Builder mb = m.createBuilder(); 
		Iterator<MatchField<?>> itr = m.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			MatchField mf = itr.next();
			// restrict MatchFields only to L3 and below: IN_PORT, ETH_TYPE, ETH_SRC, ETH_DST, IPV4_SRC, IPV4_DST, IP_PROTO (this one debatable...)
			// if a MatchField is not in the access list below, it will not be set --> it will be left wildcarded (default)
			if (mf.equals(MatchField.IN_PORT) || mf.equals(MatchField.ETH_TYPE) || mf.equals(MatchField.ETH_SRC) || mf.equals(MatchField.ETH_DST) ||
					mf.equals(MatchField.IPV4_SRC) || mf.equals(MatchField.IPV4_DST) || mf.equals(MatchField.IP_PROTO)) {
				if (m.isExact(mf)) {
					mb.setExact(mf, m.get(mf));
				} else if (m.isPartiallyMasked(mf)) {
					mb.setMasked(mf, m.getMasked(mf));
				} else {
					// it's either exact, masked, or wildcarded
					// itr only contains exact and masked MatchFields
					// we should never get here
				}
			}
		}
		return mb.build();
	}

	/**
	 * Create a builder from an existing Match object. Unlike Match's
	 * createBuilder(), this utility function will preserve all of
	 * Match m's MatchFields, even if new MatchFields are set or modified
	 * with the builder after it is returned to the calling function.
	 * 
	 * All original MatchFields in m will be set if the build() method is 
	 * invoked upon the returned builder. After the builder is returned, if
	 * a MatchField is modified via setExact(), setMasked(), or wildcard(),
	 * the newly modified MatchField will replace the original found in m.
	 * 
	 * @param m; the match to create the builder from
	 * @return Match.Builder; the builder that can be modified, and when built,
	 * will retain all of m's MatchFields, unless you explicitly overwrite them.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Match.Builder createRetentiveBuilder(Match m) {
		/* Builder retains a parent MatchField list, but list will not be used to  
		 * build the new match if the builder's set methods have been invoked; only 
		 * additions will be built, and all parent MatchFields will be ignored,  
		 * even if they were not modified by the new builder. Create a builder, and
		 * walk through m's list of non-wildcarded MatchFields. Set them all in the
		 * new builder by invoking a set method for each. This will make them persist
		 * in the Match built from this builder if the user decides to add or subtract
		 * from the MatchField list.
		 */
		Match.Builder mb = m.createBuilder(); 
		Iterator<MatchField<?>> itr = m.getMatchFields().iterator(); // only get exact or masked fields (not fully wildcarded)
		while(itr.hasNext()) {
			MatchField mf = itr.next();
			if (m.isExact(mf)) {
				mb.setExact(mf, m.get(mf));
			} else if (m.isPartiallyMasked(mf)) {
				mb.setMasked(mf, m.getMasked(mf));
			} else {
				// it's either exact, masked, or wildcarded
				// itr only contains exact and masked MatchFields
				// we should never get here
			}
		}
		return mb;
	}
	
	/**
	 * Create a Match builder the same OF version as Match m. The returned builder
	 * will not retain any MatchField information from Match m and will
	 * essentially return a clean-slate Match builder with no parent history. 
	 * This simple method is included as a wrapper to provide the opposite functionality
	 * of createRetentiveBuilder().
	 * 
	 * @param m; the match to create the builder from
	 * @return Match.Builder; the builder retains no history from the parent Match m
	 */
	public static Match.Builder createForgetfulBuilder(Match m) {
		return OFFactories.getFactory(m.getVersion()).buildMatch();
	}
	
	/**
	 * Create a duplicate Match object from Match m.
	 * 
	 * @param m; the match to copy
	 * @return Match; the new copy of Match m
	 */
	public static Match createCopy(Match m) {
		return m.createBuilder().build(); // will use parent MatchFields to produce the new Match only if the builder is never modified
	}
}
