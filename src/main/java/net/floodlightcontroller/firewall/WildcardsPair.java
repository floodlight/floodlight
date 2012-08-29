package net.floodlightcontroller.firewall;

import org.openflow.protocol.OFMatch;

public class WildcardsPair {
    public int allow = OFMatch.OFPFW_ALL;
    public int drop = OFMatch.OFPFW_ALL;
}
