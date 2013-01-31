/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by Amer Tahir
 *    
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may 
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *    
 *         http://www.apache.org/licenses/LICENSE-2.0 
 *    
 *    Unless required by applicable law or agreed to in writing, software 
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.firewall;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

@JsonSerialize(using=FirewallRuleSerializer.class)
public class FirewallRule implements Comparable<FirewallRule> {
    public int ruleid;

    public long dpid; 
    public short in_port; 
    public long dl_src; 
    public long dl_dst; 
    public short dl_type; 
    public int nw_src_prefix; 
    public int nw_src_maskbits;
    public int nw_dst_prefix;
    public int nw_dst_maskbits;
    public short nw_proto;
    public short tp_src;
    public short tp_dst;

    public boolean wildcard_dpid;
    public boolean wildcard_in_port; 
    public boolean wildcard_dl_src;
    public boolean wildcard_dl_dst;
    public boolean wildcard_dl_type;
    public boolean wildcard_nw_src;
    public boolean wildcard_nw_dst;
    public boolean wildcard_nw_proto;
    public boolean wildcard_tp_src;
    public boolean wildcard_tp_dst;

    public int priority = 0;

    public FirewallAction action;

    public enum FirewallAction {
        /*
         * DENY: Deny rule
         * ALLOW: Allow rule
         */
        DENY, ALLOW
    }

    public FirewallRule() {
        this.in_port = 0; 
        this.dl_src = 0;
        this.nw_src_prefix = 0;
        this.nw_src_maskbits = 0; 
        this.dl_dst = 0;
        this.nw_proto = 0;
        this.tp_src = 0;
        this.tp_dst = 0;
        this.dl_dst = 0;
        this.nw_dst_prefix = 0;
        this.nw_dst_maskbits = 0; 
        this.dpid = -1;
        this.wildcard_dpid = true; 
        this.wildcard_in_port = true; 
        this.wildcard_dl_src = true; 
        this.wildcard_dl_dst = true; 
        this.wildcard_dl_type = true; 
        this.wildcard_nw_src = true; 
        this.wildcard_nw_dst = true; 
        this.wildcard_nw_proto = true; 
        this.wildcard_tp_src = true; 
        this.wildcard_tp_dst = true; 
        this.priority = 0; 
        this.action = FirewallAction.ALLOW; 
        this.ruleid = 0; 
    }

    /**
     * Generates a unique ID for the instance
     * 
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
     * 
     * @param rule
     *            the rule to compare with
     * @return number representing the result of comparison 0 if equal negative
     *         if less than 'rule' greater than zero if greater priority rule
     *         than 'rule'
     */
    @Override
    public int compareTo(FirewallRule rule) {
        return this.priority - rule.priority;
    }

    /**
     * Determines if this instance matches an existing rule instance
     * 
     * @param r
     *            : the FirewallRule instance to compare with
     * @return boolean: true if a match is found
     **/
    public boolean isSameAs(FirewallRule r) {
        if (this.action != r.action
                || this.wildcard_dl_type != r.wildcard_dl_type
                || (this.wildcard_dl_type == false && this.dl_type != r.dl_type)
                || this.wildcard_tp_src != r.wildcard_tp_src
                || (this.wildcard_tp_src == false && this.tp_src != r.tp_src)
                || this.wildcard_tp_dst != r.wildcard_tp_dst
                || (this.wildcard_tp_dst == false &&this.tp_dst != r.tp_dst)
                || this.wildcard_dpid != r.wildcard_dpid
                || (this.wildcard_dpid == false && this.dpid != r.dpid)
                || this.wildcard_in_port != r.wildcard_in_port
                || (this.wildcard_in_port == false && this.in_port != r.in_port)
                || this.wildcard_nw_src != r.wildcard_nw_src
                || (this.wildcard_nw_src == false && (this.nw_src_prefix != r.nw_src_prefix || this.nw_src_maskbits != r.nw_src_maskbits))
                || this.wildcard_dl_src != r.wildcard_dl_src
                || (this.wildcard_dl_src == false && this.dl_src != r.dl_src)
                || this.wildcard_nw_proto != r.wildcard_nw_proto
                || (this.wildcard_nw_proto == false && this.nw_proto != r.nw_proto)
                || this.wildcard_nw_dst != r.wildcard_nw_dst
                || (this.wildcard_nw_dst == false && (this.nw_dst_prefix != r.nw_dst_prefix || this.nw_dst_maskbits != r.nw_dst_maskbits))
                || this.wildcard_dl_dst != r.wildcard_dl_dst                
                || (this.wildcard_dl_dst == false && this.dl_dst != r.dl_dst)) {
            return false;
        }
        return true;
    }

    /**
     * Matches this rule to a given flow - incoming packet
     * 
     * @param switchDpid
     *            the Id of the connected switch
     * @param inPort
     *            the switch port where the packet originated from
     * @param packet
     *            the Ethernet packet that arrives at the switch
     * @param wildcards
     *            the pair of wildcards (allow and deny) given by Firewall
     *            module that is used by the Firewall module's matchWithRule
     *            method to derive wildcards for the decision to be taken
     * @return true if the rule matches the given packet-in, false otherwise
     */
    public boolean matchesFlow(long switchDpid, short inPort, Ethernet packet,
            WildcardsPair wildcards) {
        IPacket pkt = packet.getPayload();

        // dl_type type
        IPv4 pkt_ip = null;

        // nw_proto types
        TCP pkt_tcp = null;
        UDP pkt_udp = null;

        // tp_src and tp_dst (tp port numbers)
        short pkt_tp_src = 0;
        short pkt_tp_dst = 0;

        // switchID matches?
        if (wildcard_dpid == false && dpid != switchDpid)
            return false;

        // in_port matches?
        if (wildcard_in_port == false && in_port != inPort)
            return false;
        if (action == FirewallRule.FirewallAction.DENY) {
            wildcards.drop &= ~OFMatch.OFPFW_IN_PORT;
        } else {
            wildcards.allow &= ~OFMatch.OFPFW_IN_PORT;
        }

        // mac address (src and dst) match?
        if (wildcard_dl_src == false
                && dl_src != packet.getSourceMAC().toLong())
            return false;
        if (action == FirewallRule.FirewallAction.DENY) {
            wildcards.drop &= ~OFMatch.OFPFW_DL_SRC;
        } else {
            wildcards.allow &= ~OFMatch.OFPFW_DL_SRC;
        }

        if (wildcard_dl_dst == false
                && dl_dst != packet.getDestinationMAC().toLong())
            return false;
        if (action == FirewallRule.FirewallAction.DENY) {
            wildcards.drop &= ~OFMatch.OFPFW_DL_DST;
        } else {
            wildcards.allow &= ~OFMatch.OFPFW_DL_DST;
        }

        // dl_type check: ARP, IP

        // if this is not an ARP rule but the pkt is ARP,
        // return false match - no need to continue protocol specific check
        if (wildcard_dl_type == false) {
            if (dl_type == Ethernet.TYPE_ARP) {
                if (packet.getEtherType() != Ethernet.TYPE_ARP)
                    return false;
                else {
                    if (action == FirewallRule.FirewallAction.DENY) {
                        wildcards.drop &= ~OFMatch.OFPFW_DL_TYPE;
                    } else {
                        wildcards.allow &= ~OFMatch.OFPFW_DL_TYPE;
                    }
                }
            } else if (dl_type == Ethernet.TYPE_IPv4) {
                if (packet.getEtherType() != Ethernet.TYPE_IPv4)
                    return false;
                else {
                    if (action == FirewallRule.FirewallAction.DENY) {
                        wildcards.drop &= ~OFMatch.OFPFW_NW_PROTO;
                    } else {
                        wildcards.allow &= ~OFMatch.OFPFW_NW_PROTO;
                    }
                    // IP packets, proceed with ip address check
                    pkt_ip = (IPv4) pkt;

                    // IP addresses (src and dst) match?
                    if (wildcard_nw_src == false
                            && this.matchIPAddress(nw_src_prefix,
                                    nw_src_maskbits, pkt_ip.getSourceAddress()) == false)
                        return false;
                    if (action == FirewallRule.FirewallAction.DENY) {
                        wildcards.drop &= ~OFMatch.OFPFW_NW_SRC_ALL;
                        wildcards.drop |= (nw_src_maskbits << OFMatch.OFPFW_NW_SRC_SHIFT);
                    } else {
                        wildcards.allow &= ~OFMatch.OFPFW_NW_SRC_ALL;
                        wildcards.allow |= (nw_src_maskbits << OFMatch.OFPFW_NW_SRC_SHIFT);
                    }

                    if (wildcard_nw_dst == false
                            && this.matchIPAddress(nw_dst_prefix,
                                    nw_dst_maskbits,
                                    pkt_ip.getDestinationAddress()) == false)
                        return false;
                    if (action == FirewallRule.FirewallAction.DENY) {
                        wildcards.drop &= ~OFMatch.OFPFW_NW_DST_ALL;
                        wildcards.drop |= (nw_dst_maskbits << OFMatch.OFPFW_NW_DST_SHIFT);
                    } else {
                        wildcards.allow &= ~OFMatch.OFPFW_NW_DST_ALL;
                        wildcards.allow |= (nw_dst_maskbits << OFMatch.OFPFW_NW_DST_SHIFT);
                    }

                    // nw_proto check
                    if (wildcard_nw_proto == false) {
                        if (nw_proto == IPv4.PROTOCOL_TCP) {
                            if (pkt_ip.getProtocol() != IPv4.PROTOCOL_TCP)
                                return false;
                            else {
                                pkt_tcp = (TCP) pkt_ip.getPayload();
                                pkt_tp_src = pkt_tcp.getSourcePort();
                                pkt_tp_dst = pkt_tcp.getDestinationPort();
                            }
                        } else if (nw_proto == IPv4.PROTOCOL_UDP) {
                            if (pkt_ip.getProtocol() != IPv4.PROTOCOL_UDP)
                                return false;
                            else {
                                pkt_udp = (UDP) pkt_ip.getPayload();
                                pkt_tp_src = pkt_udp.getSourcePort();
                                pkt_tp_dst = pkt_udp.getDestinationPort();
                            }
                        } else if (nw_proto == IPv4.PROTOCOL_ICMP) {
                            if (pkt_ip.getProtocol() != IPv4.PROTOCOL_ICMP)
                                return false;
                            else {
                                // nothing more needed for ICMP
                            }
                        }
                        if (action == FirewallRule.FirewallAction.DENY) {
                            wildcards.drop &= ~OFMatch.OFPFW_NW_PROTO;
                        } else {
                            wildcards.allow &= ~OFMatch.OFPFW_NW_PROTO;
                        }

                        // TCP/UDP source and destination ports match?
                        if (pkt_tcp != null || pkt_udp != null) {
                            // does the source port match?
                            if (tp_src != 0 && tp_src != pkt_tp_src)
                                return false;
                            if (action == FirewallRule.FirewallAction.DENY) {
                                wildcards.drop &= ~OFMatch.OFPFW_TP_SRC;
                            } else {
                                wildcards.allow &= ~OFMatch.OFPFW_TP_SRC;
                            }

                            // does the destination port match?
                            if (tp_dst != 0 && tp_dst != pkt_tp_dst)
                                return false;
                            if (action == FirewallRule.FirewallAction.DENY) {
                                wildcards.drop &= ~OFMatch.OFPFW_TP_DST;
                            } else {
                                wildcards.allow &= ~OFMatch.OFPFW_TP_DST;
                            }
                        }
                    }

                }
            } else {
                // non-IP packet - not supported - report no match
                return false;
            }
        }
        if (action == FirewallRule.FirewallAction.DENY) {
            wildcards.drop &= ~OFMatch.OFPFW_DL_TYPE;
        } else {
            wildcards.allow &= ~OFMatch.OFPFW_DL_TYPE;
        }

        // all applicable checks passed
        return true;
    }

    /**
     * Determines if rule's CIDR address matches IP address of the packet
     * 
     * @param rulePrefix
     *            prefix part of the CIDR address
     * @param ruleBits
     *            the size of mask of the CIDR address
     * @param packetAddress
     *            the IP address of the incoming packet to match with
     * @return true if CIDR address matches the packet's IP address, false
     *         otherwise
     */
    protected boolean matchIPAddress(int rulePrefix, int ruleBits,
            int packetAddress) {
        boolean matched = true;

        int rule_iprng = 32 - ruleBits;
        int rule_ipint = rulePrefix;
        int pkt_ipint = packetAddress;
        // if there's a subnet range (bits to be wildcarded > 0)
        if (rule_iprng > 0) {
            // right shift bits to remove rule_iprng of LSB that are to be
            // wildcarded
            rule_ipint = rule_ipint >> rule_iprng;
            pkt_ipint = pkt_ipint >> rule_iprng;
            // now left shift to return to normal range, except that the
            // rule_iprng number of LSB
            // are now zeroed
            rule_ipint = rule_ipint << rule_iprng;
            pkt_ipint = pkt_ipint << rule_iprng;
        }
        // check if we have a match
        if (rule_ipint != pkt_ipint)
            matched = false;

        return matched;
    }

    @Override
    public int hashCode() {
        final int prime = 2521;
        int result = super.hashCode();
        result = prime * result + (int) dpid;
        result = prime * result + in_port;
        result = prime * result + (int) dl_src;
        result = prime * result + (int) dl_dst;
        result = prime * result + dl_type;
        result = prime * result + nw_src_prefix;
        result = prime * result + nw_src_maskbits;
        result = prime * result + nw_dst_prefix;
        result = prime * result + nw_dst_maskbits;
        result = prime * result + nw_proto;
        result = prime * result + tp_src;
        result = prime * result + tp_dst;
        result = prime * result + action.ordinal();
        result = prime * result + priority;
        result = prime * result + (new Boolean(wildcard_dpid)).hashCode();
        result = prime * result + (new Boolean(wildcard_in_port)).hashCode();
        result = prime * result + (new Boolean(wildcard_dl_src)).hashCode();
        result = prime * result + (new Boolean(wildcard_dl_dst)).hashCode();
        result = prime * result + (new Boolean(wildcard_dl_type)).hashCode();
        result = prime * result + (new Boolean(wildcard_nw_src)).hashCode();
        result = prime * result + (new Boolean(wildcard_nw_dst)).hashCode();
        result = prime * result + (new Boolean(wildcard_nw_proto)).hashCode();
        result = prime * result + (new Boolean(wildcard_tp_src)).hashCode();
        result = prime * result + (new Boolean(wildcard_tp_dst)).hashCode();
        return result;
    }
}
