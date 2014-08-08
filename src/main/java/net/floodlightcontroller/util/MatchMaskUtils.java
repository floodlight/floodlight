package net.floodlightcontroller.util;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;

/**
 * Apply certain, routine masks to existing matches.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */
public class MatchMaskUtils {
	/**
	 * Create a point-to-point match for two devices at the IP layer.
	 * Takes an existing match (e.g. from a PACKET_IN), and masks all
	 * MatchFields leaving behind:
	 * 		IN_PORT
	 * 		VLAN_VID
	 * 		ETH_SRC
	 * 		ETH_DST
	 * 		IPV4_SRC
	 * 		IPV4_DST
	 * 
	 * @param m The match to remove all L4+ MatchFields from
	 * @return A new Match object with all MatchFields masked/wildcared
	 * except for those listed above.
	 */
	public static Match maskL4AndUp(Match m) {
		// cannot create builder from existing match; will retain all MatchFields set
		Match.Builder mb = OFFactories.getFactory(m.getVersion()).buildMatch(); 
		mb.setExact(MatchField.IN_PORT, m.get(MatchField.IN_PORT))
		.setExact(MatchField.VLAN_VID, m.get(MatchField.VLAN_VID))
		.setExact(MatchField.ETH_SRC, m.get(MatchField.ETH_SRC))
		.setExact(MatchField.ETH_DST, m.get(MatchField.ETH_DST));
		if (m.get(MatchField.IPV4_SRC) != null) {
			mb.setExact(MatchField.IPV4_SRC, m.get(MatchField.IPV4_SRC));
		}
		if (m.get(MatchField.IPV4_DST) != null) {
			mb.setExact(MatchField.IPV4_DST, m.get(MatchField.IPV4_DST));
		}
		return mb.build();
	}
	
	
}
