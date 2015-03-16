package net.floodlightcontroller.accesscontrollist.ap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.floodlightcontroller.accesscontrollist.util.IPAddressUtil;
import net.floodlightcontroller.packet.IPv4;

public class APManager {

	private Set<AP> apSet = new HashSet<AP>();

	public void addAP(AP ap) {
		this.apSet.add(ap);
	}

	/**
	 * get dpid set relating to the given CIDR IP
	 */
	public Set<String> getDpidSet(int cidrPrefix, int cidrMaskBits) {
		Set<String> dpidSet = new HashSet<String>();

		Iterator<AP> iter = apSet.iterator();
		if (cidrMaskBits != 32) {
			while (iter.hasNext()) {
				AP ap = iter.next();
				if (IPAddressUtil.containIP(cidrPrefix, cidrMaskBits,
						IPv4.toIPv4Address(ap.getIp()))) {
					dpidSet.add(ap.getDpid());
				}
			}
		} else {
			while (iter.hasNext()) {
				AP ap = iter.next();
				if (IPAddressUtil.containIP(cidrPrefix, cidrMaskBits,
						IPv4.toIPv4Address(ap.getIp()))) {
					dpidSet.add(ap.getDpid());
					return dpidSet;
				}
			}
		}
		return dpidSet;
	}

}
