package net.floodlightcontroller.firewall;

import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

public class FirewallRule implements Comparable<FirewallRule> {
    public int ruleid;
    public short src_inport;
    public long src_mac;
    public int src_ip_prefix;
    public int src_ip_bits;
    public short proto_type;
    public short proto_srcport;
    public short proto_dstport;
    public long dst_mac;
    public int dst_ip_prefix;
    public int dst_ip_bits;
    public long switchid;
    public boolean wildcard_src_inport;
    public boolean wildcard_src_mac;
    public boolean wildcard_src_ip;
    public boolean wildcard_proto_type;
    public boolean wildcard_dst_mac;
    public boolean wildcard_dst_ip;
    public boolean wildcard_switchid;
    public int priority = 0;
    public boolean is_denyrule;

    public FirewallRule() {
        this.src_inport = 1;
        this.src_mac = 0;
        this.src_ip_prefix = 0;
        this.src_ip_bits = 32;
        this.proto_type = 0;
        this.proto_srcport = 0;
        this.proto_dstport = 0;
        this.dst_mac = 0;
        this.dst_ip_prefix = 0;
        this.dst_ip_bits = 32;
        this.switchid = -1;
        this.wildcard_src_inport = true;
        this.wildcard_src_mac = true;
        this.wildcard_src_ip = true;
        this.wildcard_proto_type = true;
        this.wildcard_dst_mac = true;
        this.wildcard_dst_ip = true;
        this.wildcard_switchid = true;
        this.priority = 32767;
        this.is_denyrule = false;
        this.ruleid = this.genID();
    }

    /**
     * Generates a unique ID for the instance
     * @return int representing the unique id
     */
    public int genID() {
        int uid = this.hashCode();
        if (uid < 0) {
            uid = Math.abs(uid);
            uid = uid * 15551;
        }
        return uid;
    }

    /**
     * Comparison method for Collections.sort method
     * @param rule the rule to compare with
     * @return number representing the result of comparison
     * 0 if equal
     * negative if less than 'rule'
     * greater than zero if greater priority rule than 'rule'
     */
    public int compareTo(FirewallRule rule) {
        return this.priority - ((FirewallRule)rule).priority;
    }

    /**
     * Determines if this instance is similar to another rule instance
     * @param r the FirewallRule instance to compare with
     * @return true if both rules are similar, i.e. have same fields
     * NOTE: this is different than equals() method that checks for equality
     */
    public boolean isSameAs(FirewallRule r) {
        if (
                this.is_denyrule != r.is_denyrule ||
                this.wildcard_switchid != r.wildcard_switchid ||
                this.wildcard_src_inport != r.wildcard_src_inport ||
                this.wildcard_src_ip != r.wildcard_src_ip ||
                this.wildcard_src_mac != r.wildcard_src_ip ||
                this.wildcard_proto_type != r.wildcard_proto_type ||
                this.wildcard_dst_ip != r.wildcard_dst_ip ||
                this.wildcard_dst_mac != r.wildcard_dst_mac ||
                (this.wildcard_switchid == false && this.switchid != r.switchid) ||
                (this.wildcard_src_inport == false && this.src_inport != r.src_inport) ||
                (this.wildcard_src_ip == false && (this.src_ip_prefix != r.src_ip_prefix || this.src_ip_bits != r.src_ip_bits)) ||
                (this.wildcard_src_mac == false && this.src_mac != r.src_mac) ||
                (this.wildcard_proto_type == false && this.proto_type != r.proto_type) ||
                (this.wildcard_dst_ip == false && (this.dst_ip_prefix != r.dst_ip_prefix || this.dst_ip_bits != r.dst_ip_bits)) ||
                (this.wildcard_dst_mac == false && this.dst_mac != r.dst_mac)
                ) {
            return false;
        }
        return true;
    }
    
    /**
     * Matches this rule to a given flow - incoming packet
     * @param switchDpid the Id of the connected switch
     * @param inPort the switch port where the packet originated from
     * @param packet the Ethernet packet that arrives at the switch
     * @param wildcards the pair of wildcards (allow and deny) given by Firewall module
     * that is used by the Firewall module's matchWithRule method to derive wildcards
     * for the decision to be taken
     * @return true if the rule matches the given packet-in, false otherwise
     */
    public boolean matchesFlow(long switchDpid, short inPort, Ethernet packet, WildcardsPair wildcards) {
        IPacket pkt = (IPacket) packet.getPayload();
        IPv4 pkt_ip = null;
        TCP pkt_tcp = null;
        UDP pkt_udp = null;
        ICMP pkt_icmp = null;
        short proto_src = 0;
        short proto_dst = 0;


        if (pkt instanceof IPv4) {
            pkt_ip = (IPv4) pkt;
            if (pkt_ip.getPayload() instanceof TCP) {
                pkt_tcp = (TCP)pkt_ip.getPayload();
                proto_src = pkt_tcp.getSourcePort();
                proto_dst = pkt_tcp.getDestinationPort();
            } else if (pkt_ip.getPayload() instanceof UDP) {
                pkt_udp = (UDP)pkt_ip.getPayload();
                proto_src = pkt_udp.getSourcePort();
                proto_dst = pkt_udp.getDestinationPort();
            } else if (pkt_ip.getPayload() instanceof ICMP) {
                pkt_icmp = (ICMP)pkt_ip.getPayload();
            }
        }

        // switchID matches?
        if (wildcard_switchid == false && switchid != switchDpid) return false;

        // inport matches?
        if (wildcard_src_inport == false && src_inport != inPort) return false;
        if (is_denyrule) {
            wildcards.allow &= ~OFMatch.OFPFW_IN_PORT;
        } else {
            wildcards.drop &= ~OFMatch.OFPFW_IN_PORT;
        }

        // mac address (src and dst) match?
        if (wildcard_src_mac == false && src_mac != packet.getSourceMAC().toLong()) return false;
        if (is_denyrule) {
            wildcards.allow &= ~OFMatch.OFPFW_DL_SRC;
        } else {
            wildcards.drop &= ~OFMatch.OFPFW_DL_SRC;
        }
        if (wildcard_dst_mac == false && dst_mac != packet.getDestinationMAC().toLong()) return false;
        if (is_denyrule) {
            wildcards.allow &= ~OFMatch.OFPFW_DL_DST;
        } else {
            wildcards.drop &= ~OFMatch.OFPFW_DL_DST;
        }

        // protocol type matches?
        if (wildcard_proto_type == false) {
            if (proto_type == IPv4.PROTOCOL_TCP && pkt_tcp == null) return false;
            if (proto_type == IPv4.PROTOCOL_UDP && pkt_udp == null) return false;
            if (proto_type == IPv4.PROTOCOL_ICMP && pkt_icmp == null) return false;
            if (proto_type == Ethernet.TYPE_ARP && packet.getEtherType() != Ethernet.TYPE_ARP) return false;
            if (is_denyrule) {
                wildcards.allow &= ~OFMatch.OFPFW_DL_TYPE;
                if (proto_type != Ethernet.TYPE_ARP) {
                    wildcards.allow &= ~OFMatch.OFPFW_NW_PROTO;
                }
            } else {
                wildcards.drop &= ~OFMatch.OFPFW_DL_TYPE;
                if (proto_type != Ethernet.TYPE_ARP) {
                    wildcards.drop &= ~OFMatch.OFPFW_NW_PROTO;
                }
            }
        } else {
            // if we have a non-IPv4 packet and packet matches SWITCH, INPORT and MAC criteria (if specified)
            // and the rule has "ANY" specified on protocol, then make decision for this packet/flow
            if (pkt_ip == null) {
                return true;
            }
        }

        // protocol specific fields - for IP packets only

        if (wildcard_proto_type == false && proto_type != Ethernet.TYPE_ARP) {

            // ip addresses (src and dst) match?
            if (wildcard_src_ip == false && this.matchIPAddress(src_ip_prefix, src_ip_bits, pkt_ip.getSourceAddress()) == false) return false;
            if (is_denyrule) {
                wildcards.allow &= ~OFMatch.OFPFW_NW_SRC_ALL;
                wildcards.allow |= (src_ip_bits << OFMatch.OFPFW_NW_SRC_SHIFT);
            } else {
                wildcards.drop &= ~OFMatch.OFPFW_NW_SRC_ALL;
                wildcards.drop |= (src_ip_bits << OFMatch.OFPFW_NW_SRC_SHIFT);
            }
            if (wildcard_dst_ip == false && this.matchIPAddress(dst_ip_prefix, dst_ip_bits, pkt_ip.getDestinationAddress()) == false) return false;
            if (is_denyrule) {
                wildcards.allow &= ~OFMatch.OFPFW_NW_DST_ALL;
                wildcards.allow |= (dst_ip_bits << OFMatch.OFPFW_NW_DST_SHIFT);
            } else {
                wildcards.drop &= ~OFMatch.OFPFW_NW_DST_ALL;
                wildcards.drop |= (dst_ip_bits << OFMatch.OFPFW_NW_DST_SHIFT);
            }

            // TCP/UDP source and destination ports match?
            if (pkt_tcp != null || pkt_udp != null) {
                // does the source port match?
                if (proto_srcport != 0 && proto_srcport != proto_src) return false;
                if (is_denyrule) {
                    wildcards.allow &= ~OFMatch.OFPFW_TP_SRC;
                } else {
                    wildcards.drop &= ~OFMatch.OFPFW_TP_SRC;
                }
                // does the destination port match?
                if (proto_dstport != 0 && proto_dstport != proto_dst) return false;
                if (is_denyrule) {
                    wildcards.allow &= ~OFMatch.OFPFW_TP_DST;
                } else {
                    wildcards.drop &= ~OFMatch.OFPFW_TP_DST;
                }
            }
        }

        return true;
    }
    
    /**
     * Determines if rule's CIDR address matches IP address of the packet
     * @param rulePrefix prefix part of the CIDR address
     * @param ruleBits the size of mask of the CIDR address
     * @param packetAddress the IP address of the incoming packet to match with
     * @return true if CIDR address matches the packet's IP address, false otherwise
     */
    protected boolean matchIPAddress(int rulePrefix, int ruleBits, int packetAddress) {
        boolean matched = true;

        int rule_iprng = 32 - ruleBits;
        int rule_ipint = rulePrefix;
        int pkt_ipint = packetAddress;
        // if there's a subnet range (bits to be wildcarded > 0)
        if (rule_iprng > 0) {
            // right shift bits to remove rule_iprng of LSB that are to be wildcarded
            rule_ipint = rule_ipint >> rule_iprng;
                    pkt_ipint = pkt_ipint >> rule_iprng;
                    // now left shift to return to normal range, except that the rule_iprng number of LSB
                    // are now zeroed
                    rule_ipint = rule_ipint << rule_iprng;
                    pkt_ipint = pkt_ipint << rule_iprng;
        }
        // check if we have a match
        if (rule_ipint != pkt_ipint) matched = false;

        return matched;
    }

    @Override
    public int hashCode() {
        final int prime = 2521;
        int result = super.hashCode();
        result = prime * result + src_inport;
        result = prime * result + (int)src_mac;
        result = prime * result + src_ip_prefix;
        result = prime * result + src_ip_bits;
        result = prime * result + proto_type;
        result = prime * result + proto_srcport;
        result = prime * result + proto_dstport;
        result = prime * result + (int)dst_mac;
        result = prime * result + dst_ip_prefix;
        result = prime * result + dst_ip_bits;
        result = prime * result + (int)switchid;
        result = prime * result + priority;
        result = prime * result + (new Boolean(is_denyrule)).hashCode();
        result = prime * result + (new Boolean(wildcard_switchid)).hashCode();
        result = prime * result + (new Boolean(wildcard_src_inport)).hashCode();
        result = prime * result + (new Boolean(wildcard_src_ip)).hashCode();
        result = prime * result + (new Boolean(wildcard_src_mac)).hashCode();
        result = prime * result + (new Boolean(wildcard_proto_type)).hashCode();
        result = prime * result + (new Boolean(wildcard_dst_ip)).hashCode();
        result = prime * result + (new Boolean(wildcard_dst_mac)).hashCode();
        return result;
    }
}
