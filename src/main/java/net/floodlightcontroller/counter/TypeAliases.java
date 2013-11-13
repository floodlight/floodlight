/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.counter;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to contain some statically initialized data
 * @author readams
 *
 */
public class TypeAliases {
    protected static final Map<String,String> l3TypeAliasMap = 
            new HashMap<String, String>();
    static {
        l3TypeAliasMap.put("0599", "L3_V1Ether");
        l3TypeAliasMap.put("0800", "L3_IPv4");
        l3TypeAliasMap.put("0806", "L3_ARP");
        l3TypeAliasMap.put("8035", "L3_RARP");
        l3TypeAliasMap.put("809b", "L3_AppleTalk");
        l3TypeAliasMap.put("80f3", "L3_AARP");
        l3TypeAliasMap.put("8100", "L3_802_1Q");
        l3TypeAliasMap.put("8137", "L3_Novell_IPX");
        l3TypeAliasMap.put("8138", "L3_Novell");
        l3TypeAliasMap.put("86dd", "L3_IPv6");
        l3TypeAliasMap.put("8847", "L3_MPLS_uni");
        l3TypeAliasMap.put("8848", "L3_MPLS_multi");
        l3TypeAliasMap.put("8863", "L3_PPPoE_DS");
        l3TypeAliasMap.put("8864", "L3_PPPoE_SS");
        l3TypeAliasMap.put("886f", "L3_MSFT_NLB");
        l3TypeAliasMap.put("8870", "L3_Jumbo");
        l3TypeAliasMap.put("889a", "L3_HyperSCSI");
        l3TypeAliasMap.put("88a2", "L3_ATA_Ethernet");
        l3TypeAliasMap.put("88a4", "L3_EtherCAT");
        l3TypeAliasMap.put("88a8", "L3_802_1ad");
        l3TypeAliasMap.put("88ab", "L3_Ether_Powerlink");
        l3TypeAliasMap.put("88cc", "L3_LLDP");
        l3TypeAliasMap.put("88cd", "L3_SERCOS_III");
        l3TypeAliasMap.put("88e5", "L3_802_1ae");
        l3TypeAliasMap.put("88f7", "L3_IEEE_1588");
        l3TypeAliasMap.put("8902", "L3_802_1ag_CFM");
        l3TypeAliasMap.put("8906", "L3_FCoE");
        l3TypeAliasMap.put("9000", "L3_Loop");
        l3TypeAliasMap.put("9100", "L3_Q_in_Q");
        l3TypeAliasMap.put("cafe", "L3_LLT");
    }
    
    protected static final Map<String,String> l4TypeAliasMap = 
            new HashMap<String, String>();
    static {
        l4TypeAliasMap.put("00", "L4_HOPOPT");
        l4TypeAliasMap.put("01", "L4_ICMP");
        l4TypeAliasMap.put("02", "L4_IGAP_IGMP_RGMP");
        l4TypeAliasMap.put("03", "L4_GGP");
        l4TypeAliasMap.put("04", "L4_IP");
        l4TypeAliasMap.put("05", "L4_ST");
        l4TypeAliasMap.put("06", "L4_TCP");
        l4TypeAliasMap.put("07", "L4_UCL");
        l4TypeAliasMap.put("08", "L4_EGP");
        l4TypeAliasMap.put("09", "L4_IGRP");
        l4TypeAliasMap.put("0a", "L4_BBN");
        l4TypeAliasMap.put("0b", "L4_NVP");
        l4TypeAliasMap.put("0c", "L4_PUP");
        l4TypeAliasMap.put("0d", "L4_ARGUS");
        l4TypeAliasMap.put("0e", "L4_EMCON");
        l4TypeAliasMap.put("0f", "L4_XNET");
        l4TypeAliasMap.put("10", "L4_Chaos");
        l4TypeAliasMap.put("11", "L4_UDP");
        l4TypeAliasMap.put("12", "L4_TMux");
        l4TypeAliasMap.put("13", "L4_DCN");
        l4TypeAliasMap.put("14", "L4_HMP");
        l4TypeAliasMap.put("15", "L4_Packet_Radio");
        l4TypeAliasMap.put("16", "L4_XEROX_NS_IDP");
        l4TypeAliasMap.put("17", "L4_Trunk_1");
        l4TypeAliasMap.put("18", "L4_Trunk_2");
        l4TypeAliasMap.put("19", "L4_Leaf_1");
        l4TypeAliasMap.put("1a", "L4_Leaf_2");
        l4TypeAliasMap.put("1b", "L4_RDP");
        l4TypeAliasMap.put("1c", "L4_IRTP");
        l4TypeAliasMap.put("1d", "L4_ISO_TP4");
        l4TypeAliasMap.put("1e", "L4_NETBLT");
        l4TypeAliasMap.put("1f", "L4_MFE");
        l4TypeAliasMap.put("20", "L4_MERIT");
        l4TypeAliasMap.put("21", "L4_DCCP");
        l4TypeAliasMap.put("22", "L4_Third_Party_Connect");
        l4TypeAliasMap.put("23", "L4_IDPR");
        l4TypeAliasMap.put("24", "L4_XTP");
        l4TypeAliasMap.put("25", "L4_Datagram_Delivery");
        l4TypeAliasMap.put("26", "L4_IDPR");
        l4TypeAliasMap.put("27", "L4_TP");
        l4TypeAliasMap.put("28", "L4_ILTP");
        l4TypeAliasMap.put("29", "L4_IPv6_over_IPv4");
        l4TypeAliasMap.put("2a", "L4_SDRP");
        l4TypeAliasMap.put("2b", "L4_IPv6_RH");
        l4TypeAliasMap.put("2c", "L4_IPv6_FH");
        l4TypeAliasMap.put("2d", "L4_IDRP");
        l4TypeAliasMap.put("2e", "L4_RSVP");
        l4TypeAliasMap.put("2f", "L4_GRE");
        l4TypeAliasMap.put("30", "L4_DSR");
        l4TypeAliasMap.put("31", "L4_BNA");
        l4TypeAliasMap.put("32", "L4_ESP");
        l4TypeAliasMap.put("33", "L4_AH");
        l4TypeAliasMap.put("34", "L4_I_NLSP");
        l4TypeAliasMap.put("35", "L4_SWIPE");
        l4TypeAliasMap.put("36", "L4_NARP");
        l4TypeAliasMap.put("37", "L4_Minimal_Encapsulation");
        l4TypeAliasMap.put("38", "L4_TLSP");
        l4TypeAliasMap.put("39", "L4_SKIP");
        l4TypeAliasMap.put("3a", "L4_ICMPv6");
        l4TypeAliasMap.put("3b", "L4_IPv6_No_Next_Header");
        l4TypeAliasMap.put("3c", "L4_IPv6_Destination_Options");
        l4TypeAliasMap.put("3d", "L4_Any_host_IP");
        l4TypeAliasMap.put("3e", "L4_CFTP");
        l4TypeAliasMap.put("3f", "L4_Any_local");
        l4TypeAliasMap.put("40", "L4_SATNET");
        l4TypeAliasMap.put("41", "L4_Kryptolan");
        l4TypeAliasMap.put("42", "L4_MIT_RVDP");
        l4TypeAliasMap.put("43", "L4_Internet_Pluribus");
        l4TypeAliasMap.put("44", "L4_Distributed_FS");
        l4TypeAliasMap.put("45", "L4_SATNET");
        l4TypeAliasMap.put("46", "L4_VISA");
        l4TypeAliasMap.put("47", "L4_IP_Core");
        l4TypeAliasMap.put("4a", "L4_Wang_Span");
        l4TypeAliasMap.put("4b", "L4_Packet_Video");
        l4TypeAliasMap.put("4c", "L4_Backroom_SATNET");
        l4TypeAliasMap.put("4d", "L4_SUN_ND");
        l4TypeAliasMap.put("4e", "L4_WIDEBAND_Monitoring");
        l4TypeAliasMap.put("4f", "L4_WIDEBAND_EXPAK");
        l4TypeAliasMap.put("50", "L4_ISO_IP");
        l4TypeAliasMap.put("51", "L4_VMTP");
        l4TypeAliasMap.put("52", "L4_SECURE_VMTP");
        l4TypeAliasMap.put("53", "L4_VINES");
        l4TypeAliasMap.put("54", "L4_TTP");
        l4TypeAliasMap.put("55", "L4_NSFNET_IGP");
        l4TypeAliasMap.put("56", "L4_Dissimilar_GP");
        l4TypeAliasMap.put("57", "L4_TCF");
        l4TypeAliasMap.put("58", "L4_EIGRP");
        l4TypeAliasMap.put("59", "L4_OSPF");
        l4TypeAliasMap.put("5a", "L4_Sprite_RPC");
        l4TypeAliasMap.put("5b", "L4_Locus_ARP");
        l4TypeAliasMap.put("5c", "L4_MTP");
        l4TypeAliasMap.put("5d", "L4_AX");
        l4TypeAliasMap.put("5e", "L4_IP_within_IP");
        l4TypeAliasMap.put("5f", "L4_Mobile_ICP");
        l4TypeAliasMap.put("61", "L4_EtherIP");
        l4TypeAliasMap.put("62", "L4_Encapsulation_Header");
        l4TypeAliasMap.put("64", "L4_GMTP");
        l4TypeAliasMap.put("65", "L4_IFMP");
        l4TypeAliasMap.put("66", "L4_PNNI");
        l4TypeAliasMap.put("67", "L4_PIM");
        l4TypeAliasMap.put("68", "L4_ARIS");
        l4TypeAliasMap.put("69", "L4_SCPS");
        l4TypeAliasMap.put("6a", "L4_QNX");
        l4TypeAliasMap.put("6b", "L4_Active_Networks");
        l4TypeAliasMap.put("6c", "L4_IPPCP");
        l4TypeAliasMap.put("6d", "L4_SNP");
        l4TypeAliasMap.put("6e", "L4_Compaq_Peer_Protocol");
        l4TypeAliasMap.put("6f", "L4_IPX_in_IP");
        l4TypeAliasMap.put("70", "L4_VRRP");
        l4TypeAliasMap.put("71", "L4_PGM");
        l4TypeAliasMap.put("72", "L4_0_hop");
        l4TypeAliasMap.put("73", "L4_L2TP");
        l4TypeAliasMap.put("74", "L4_DDX");
        l4TypeAliasMap.put("75", "L4_IATP");
        l4TypeAliasMap.put("76", "L4_ST");
        l4TypeAliasMap.put("77", "L4_SRP");
        l4TypeAliasMap.put("78", "L4_UTI");
        l4TypeAliasMap.put("79", "L4_SMP");
        l4TypeAliasMap.put("7a", "L4_SM");
        l4TypeAliasMap.put("7b", "L4_PTP");
        l4TypeAliasMap.put("7c", "L4_ISIS");
        l4TypeAliasMap.put("7d", "L4_FIRE");
        l4TypeAliasMap.put("7e", "L4_CRTP");
        l4TypeAliasMap.put("7f", "L4_CRUDP");
        l4TypeAliasMap.put("80", "L4_SSCOPMCE");
        l4TypeAliasMap.put("81", "L4_IPLT");
        l4TypeAliasMap.put("82", "L4_SPS");
        l4TypeAliasMap.put("83", "L4_PIPE");
        l4TypeAliasMap.put("84", "L4_SCTP");
        l4TypeAliasMap.put("85", "L4_Fibre_Channel");
        l4TypeAliasMap.put("86", "L4_RSVP_E2E_IGNORE");
        l4TypeAliasMap.put("87", "L4_Mobility_Header");
        l4TypeAliasMap.put("88", "L4_UDP_Lite");
        l4TypeAliasMap.put("89", "L4_MPLS");
        l4TypeAliasMap.put("8a", "L4_MANET");
        l4TypeAliasMap.put("8b", "L4_HIP");
        l4TypeAliasMap.put("8c", "L4_Shim6");
        l4TypeAliasMap.put("8d", "L4_WESP");
        l4TypeAliasMap.put("8e", "L4_ROHC");
    }
}
