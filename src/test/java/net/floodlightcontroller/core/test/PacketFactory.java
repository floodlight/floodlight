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

package net.floodlightcontroller.core.test;

import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.DHCPOption;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.factory.BasicFactory;

/**
 * A class to that creates many types of L2/L3/L4 or OpenFlow packets.
 * This is used in testing.
 * @author alexreimers
 *
 */
public class PacketFactory {
    public static String broadcastMac = "ff:ff:ff:ff:ff:ff";
    public static String broadcastIp = "255.255.255.255";
    protected static BasicFactory OFMessageFactory = new BasicFactory();
    
    /**
     * Generates a DHCP request OFPacketIn.
     * @param hostMac The host MAC address of for the request.
     * @return An OFPacketIn that contains a DHCP request packet.
     */
    public static OFPacketIn DhcpDiscoveryRequestOFPacketIn(MACAddress hostMac) {
        byte[] serializedPacket = DhcpDiscoveryRequestEthernet(hostMac).serialize();
        return (((OFPacketIn)OFMessageFactory
                .getMessage(OFType.PACKET_IN))
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setInPort((short) 1)
                .setPacketData(serializedPacket)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short)serializedPacket.length));
    }
    
    /**
     * Generates a DHCP request Ethernet frame.
     * @param hostMac The host MAC address of for the request.
     * @returnAn An Ethernet frame that contains a DHCP request packet.
     */
    public static Ethernet DhcpDiscoveryRequestEthernet(MACAddress hostMac) {
        List<DHCPOption> optionList = new ArrayList<DHCPOption>();
        
        byte[] requestValue = new byte[4];
        requestValue[0] = requestValue[1] = requestValue[2] = requestValue[3] = 0;
        DHCPOption requestOption = 
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_RequestedIP.
                             getValue())
                    .setLength((byte)4)
                    .setData(requestValue);
        
        byte[] msgTypeValue = new byte[1];
        msgTypeValue[0] = 1;    // DHCP request
        DHCPOption msgTypeOption = 
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_MessageType.
                             getValue())
                    .setLength((byte)1)
                    .setData(msgTypeValue);
        
        byte[] reqParamValue = new byte[4];
        reqParamValue[0] = 1;   // subnet mask
        reqParamValue[1] = 3;   // Router
        reqParamValue[2] = 6;   // Domain Name Server
        reqParamValue[3] = 42;  // NTP Server
        DHCPOption reqParamOption = 
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_RequestedParameters.
                             getValue())
                    .setLength((byte)4)
                    .setData(reqParamValue);
        
        byte[] clientIdValue = new byte[7];
        clientIdValue[0] = 1;   // Ethernet
        System.arraycopy(hostMac.toBytes(), 0, 
                         clientIdValue, 1, 6);
        DHCPOption clientIdOption = 
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_ClientID.
                             getValue())
                             .setLength((byte)7)
                             .setData(clientIdValue);
        
        DHCPOption endOption = 
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_END.
                             getValue())
                             .setLength((byte)0)
                             .setData(null);
                                    
        optionList.add(requestOption);
        optionList.add(msgTypeOption);
        optionList.add(reqParamOption);
        optionList.add(clientIdOption);
        optionList.add(endOption);
        
        Ethernet requestPacket = new Ethernet();
        requestPacket.setSourceMACAddress(hostMac.toBytes())
        .setDestinationMACAddress(broadcastMac)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setVersion((byte)4)
                .setDiffServ((byte)0)
                .setIdentification((short)100)
                .setFlags((byte)0)
                .setFragmentOffset((short)0)
                .setTtl((byte)250)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setChecksum((short)0)
                .setSourceAddress(0)
                .setDestinationAddress(broadcastIp)
                .setPayload(
                        new UDP()
                        .setSourcePort(UDP.DHCP_CLIENT_PORT)
                        .setDestinationPort(UDP.DHCP_SERVER_PORT)
                        .setChecksum((short)0)
                        .setPayload(
                                new DHCP()
                                .setOpCode(DHCP.OPCODE_REQUEST)
                                .setHardwareType(DHCP.HWTYPE_ETHERNET)
                                .setHardwareAddressLength((byte)6)
                                .setHops((byte)0)
                                .setTransactionId(0x00003d1d)
                                .setSeconds((short)0)
                                .setFlags((short)0)
                                .setClientIPAddress(0)
                                .setYourIPAddress(0)
                                .setServerIPAddress(0)
                                .setGatewayIPAddress(0)
                                .setClientHardwareAddress(hostMac.toBytes())
                                .setOptions(optionList))));
                
        return requestPacket;
    }
}
