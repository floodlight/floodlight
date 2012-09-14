package net.floodlightcontroller.firewall;

import org.openflow.protocol.OFMatch;

public class RuleWildcardsPair {
    public FirewallRule rule;
    public int wildcards = OFMatch.OFPFW_ALL;
}
