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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

@JsonSerialize(using=FirewallRuleSerializer.class)
public class FirewallRule implements Comparable<FirewallRule> {
    public int ruleid;

    public DatapathId dpid; 
    public OFPort in_port; 
    public MacAddress dl_src; 
    public MacAddress dl_dst; 
    public EthType dl_type; 
    public IPv4AddressWithMask nw_src_prefix_and_mask; 
    //public int nw_src_maskbits;
    public IPv4AddressWithMask nw_dst_prefix_and_mask;
    //public int nw_dst_maskbits;
    public IpProtocol nw_proto;
    public TransportPort tp_src;
    public TransportPort tp_dst;

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
        this.in_port = OFPort.ZERO; 
        this.dl_src = MacAddress.NONE;
        this.nw_src_prefix_and_mask = IPv4AddressWithMask.NONE;
        //this.nw_src_maskbits = 0; 
        this.dl_dst = MacAddress.NONE;
        this.nw_proto = IpProtocol.NONE;
        this.tp_src = TransportPort.NONE;
        this.tp_dst = TransportPort.NONE;
        this.dl_dst = MacAddress.NONE;
        this.nw_dst_prefix_and_mask = IPv4AddressWithMask.NONE;
        //this.nw_dst_maskbits = 0; 
        this.dpid = DatapathId.NONE;
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
                || (this.wildcard_dl_type == false && !this.dl_type.equals(r.dl_type))
                || this.wildcard_tp_src != r.wildcard_tp_src
                || (this.wildcard_tp_src == false && !this.tp_src.equals(r.tp_src))
                || this.wildcard_tp_dst != r.wildcard_tp_dst
                || (this.wildcard_tp_dst == false && !this.tp_dst.equals(r.tp_dst))
                || this.wildcard_dpid != r.wildcard_dpid
                || (this.wildcard_dpid == false && !this.dpid.equals(r.dpid))
                || this.wildcard_in_port != r.wildcard_in_port
                || (this.wildcard_in_port == false && !this.in_port.equals(r.in_port))
                || this.wildcard_nw_src != r.wildcard_nw_src
                || (this.wildcard_nw_src == false && !this.nw_src_prefix_and_mask.equals(r.nw_src_prefix_and_mask))
                || this.wildcard_dl_src != r.wildcard_dl_src
                || (this.wildcard_dl_src == false && !this.dl_src.equals(r.dl_src))
                || this.wildcard_nw_proto != r.wildcard_nw_proto
                || (this.wildcard_nw_proto == false && !this.nw_proto.equals(r.nw_proto))
                || this.wildcard_nw_dst != r.wildcard_nw_dst
                || (this.wildcard_nw_dst == false && !this.nw_dst_prefix_and_mask.equals(r.nw_dst_prefix_and_mask))
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
    public boolean matchesFlow(DatapathId switchDpid, OFPort inPort, Ethernet packet,
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
        if (wildcard_dpid == false && !dpid.equals(switchDpid))
            return false;

        // in_port matches?
        if (wildcard_in_port == false && !in_port.equals(inPort))
            return false;
        if (action == FirewallRule.FirewallAction.DENY) {
            //wildcards.drop &= ~OFMatch.OFPFW_IN_PORT;
            wildcards.drop.setExact(MatchField.IN_PORT, this.in_port);
        } else {
            //wildcards.allow &= ~OFMatch.OFPFW_IN_PORT;
            wildcards.allow.setExact(MatchField.IN_PORT, this.in_port);
        }

        // mac address (src and dst) match?
        if (wildcard_dl_src == false && !dl_src.equals(packet.getSourceMAC()))
            return false;
        if (action == FirewallRule.FirewallAction.DENY) {
            //wildcards.drop &= ~OFMatch.OFPFW_DL_SRC;
            wildcards.drop.setExact(MatchField.ETH_SRC, this.dl_src);
        } else {
            //wildcards.allow &= ~OFMatch.OFPFW_DL_SRC;
            wildcards.allow.setExact(MatchField.ETH_SRC, this.dl_src);
        }

        if (wildcard_dl_dst == false && !dl_dst.equals(packet.getDestinationMAC()))
            return false;
        if (action == FirewallRule.FirewallAction.DENY) {
            //wildcards.drop &= ~OFMatch.OFPFW_DL_DST;
            wildcards.drop.setExact(MatchField.ETH_DST, this.dl_dst);
        } else {
            //wildcards.allow &= ~OFMatch.OFPFW_DL_DST;
            wildcards.allow.setExact(MatchField.ETH_DST, this.dl_dst);
        }

        // dl_type check: ARP, IP

        // if this is not an ARP rule but the pkt is ARP,
        // return false match - no need to continue protocol specific check
        if (wildcard_dl_type == false) {
            if (dl_type.equals(EthType.ARP)) {
                if (packet.getEtherType() != EthType.ARP.getValue())
                    return false;
                else {
                    if (action == FirewallRule.FirewallAction.DENY) {
                        //wildcards.drop &= ~OFMatch.OFPFW_DL_TYPE;
                        wildcards.drop.setExact(MatchField.ETH_TYPE, this.dl_type);
                    } else {
                        //wildcards.allow &= ~OFMatch.OFPFW_DL_TYPE;
                        wildcards.allow.setExact(MatchField.ETH_TYPE, this.dl_type);
                    }
                }
            } else if (dl_type.equals(EthType.IPv4)) {
                if (packet.getEtherType() != EthType.IPv4.getValue())
                    return false;
                else {
                    if (action == FirewallRule.FirewallAction.DENY) {
                        //wildcards.drop &= ~OFMatch.OFPFW_NW_PROTO;
                        wildcards.drop.setExact(MatchField.IP_PROTO, this.nw_proto);
                    } else {
                        //wildcards.allow &= ~OFMatch.OFPFW_NW_PROTO;
                        wildcards.allow.setExact(MatchField.IP_PROTO, this.nw_proto);
                    }
                    // IP packets, proceed with ip address check
                    pkt_ip = (IPv4) pkt;

                    // IP addresses (src and dst) match?
                    if (wildcard_nw_src == false && this.matchIPAddress(nw_src_prefix_and_mask.getValue().getInt(), nw_src_prefix_and_mask.getMask().getInt(), pkt_ip.getSourceAddress()) == false)
                        return false;
                    if (action == FirewallRule.FirewallAction.DENY) {
                        //wildcards.drop &= ~OFMatch.OFPFW_NW_SRC_ALL;
                        //wildcards.drop |= (nw_src_maskbits << OFMatch.OFPFW_NW_SRC_SHIFT);
                    	wildcards.drop.setMasked(MatchField.IPV4_SRC, nw_src_prefix_and_mask);
                    } else {
                        //wildcards.allow &= ~OFMatch.OFPFW_NW_SRC_ALL;
                        //wildcards.allow |= (nw_src_maskbits << OFMatch.OFPFW_NW_SRC_SHIFT);
                    	wildcards.allow.setMasked(MatchField.IPV4_SRC, nw_src_prefix_and_mask);
                    }

                    if (wildcard_nw_dst == false && this.matchIPAddress(nw_dst_prefix_and_mask.getValue().getInt(), nw_dst_prefix_and_mask.getMask().getInt(), pkt_ip.getDestinationAddress()) == false)
                        return false;
                    if (action == FirewallRule.FirewallAction.DENY) {
                        //wildcards.drop &= ~OFMatch.OFPFW_NW_DST_ALL;
                        //wildcards.drop |= (nw_dst_maskbits << OFMatch.OFPFW_NW_DST_SHIFT);
                    	wildcards.drop.setMasked(MatchField.IPV4_DST, nw_dst_prefix_and_mask);
                    } else {
                        //wildcards.allow &= ~OFMatch.OFPFW_NW_DST_ALL;
                        //wildcards.allow |= (nw_dst_maskbits << OFMatch.OFPFW_NW_DST_SHIFT);
                    	wildcards.allow.setMasked(MatchField.IPV4_DST, nw_dst_prefix_and_mask);
                    }

                    // nw_proto check
                    if (wildcard_nw_proto == false) {
                        if (nw_proto.equals(IpProtocol.TCP)) {
                            if ((short) pkt_ip.getProtocol() != IpProtocol.TCP.getIpProtocolNumber())
                                return false;
                            else {
                                pkt_tcp = (TCP) pkt_ip.getPayload();
                                pkt_tp_src = pkt_tcp.getSourcePort();
                                pkt_tp_dst = pkt_tcp.getDestinationPort();
                            }
                        } else if (nw_proto.equals(IpProtocol.UDP)) {
                            if ((short) pkt_ip.getProtocol() != IpProtocol.UDP.getIpProtocolNumber())
                                return false;
                            else {
                                pkt_udp = (UDP) pkt_ip.getPayload();
                                pkt_tp_src = pkt_udp.getSourcePort();
                                pkt_tp_dst = pkt_udp.getDestinationPort();
                            }
                        } else if (nw_proto.equals(IpProtocol.ICMP)) {
                            if ((short) pkt_ip.getProtocol() != IpProtocol.ICMP.getIpProtocolNumber())
                                return false;
                            else {
                                // nothing more needed for ICMP
                            }
                        }
                        if (action == FirewallRule.FirewallAction.DENY) {
                            //wildcards.drop &= ~OFMatch.OFPFW_NW_PROTO;
                            wildcards.drop.setExact(MatchField.IP_PROTO, this.nw_proto);
                        } else {
                            //wildcards.allow &= ~OFMatch.OFPFW_NW_PROTO;
                            wildcards.allow.setExact(MatchField.IP_PROTO, this.nw_proto);
                        }

                        // TCP/UDP source and destination ports match?
                        if (pkt_tcp != null || pkt_udp != null) {
                            // does the source port match?
                            if (tp_src.getPort() != 0 && tp_src.getPort() != pkt_tp_src)
                                return false;
                            if (action == FirewallRule.FirewallAction.DENY) {
                                //wildcards.drop &= ~OFMatch.OFPFW_TP_SRC;
                                if (pkt_tcp != null) {
                                	wildcards.drop.setExact(MatchField.TCP_SRC, this.tp_src);
                                } else {
                                	wildcards.drop.setExact(MatchField.UDP_SRC, this.tp_src);   
                                }
                            } else {
                                //wildcards.allow &= ~OFMatch.OFPFW_TP_SRC;
                                if (pkt_tcp != null) {
                                	wildcards.allow.setExact(MatchField.TCP_SRC, this.tp_src);
                                } else {
                                	wildcards.allow.setExact(MatchField.UDP_SRC, this.tp_src);   
                                }
                            }

                            // does the destination port match?
                            if (tp_dst.getPort() != 0 && tp_dst.getPort() != pkt_tp_dst)
                                return false;
                            if (action == FirewallRule.FirewallAction.DENY) {
                                //wildcards.drop &= ~OFMatch.OFPFW_TP_DST;
                                if (pkt_tcp != null) {
                                	wildcards.drop.setExact(MatchField.TCP_DST, this.tp_dst);
                                } else {
                                	wildcards.drop.setExact(MatchField.UDP_DST, this.tp_dst);   
                                }
                            } else {
                                //wildcards.allow &= ~OFMatch.OFPFW_TP_DST;
                            	if (pkt_tcp != null) {
                                	wildcards.allow.setExact(MatchField.TCP_DST, this.tp_dst);
                                } else {
                                	wildcards.allow.setExact(MatchField.UDP_DST, this.tp_dst);   
                                }
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
            //wildcards.drop &= ~OFMatch.OFPFW_DL_TYPE;
        	wildcards.drop.setExact(MatchField.ETH_TYPE, this.dl_type);
        } else {
            //wildcards.allow &= ~OFMatch.OFPFW_DL_TYPE;
        	wildcards.allow.setExact(MatchField.ETH_TYPE, this.dl_type);
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
        result = prime * result + (int) dpid.getLong();
        result = prime * result + in_port.getPortNumber();
        result = prime * result + (int) dl_src.getLong();
        result = prime * result + (int) dl_dst.getLong();
        result = prime * result + dl_type.getValue();
        result = prime * result + nw_src_prefix_and_mask.getValue().getInt();
        result = prime * result + nw_src_prefix_and_mask.getMask().getInt();
        result = prime * result + nw_dst_prefix_and_mask.getValue().getInt();
        result = prime * result + nw_dst_prefix_and_mask.getMask().getInt();
        result = prime * result + nw_proto.getIpProtocolNumber();
        result = prime * result + tp_src.getPort();
        result = prime * result + tp_dst.getPort();
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
