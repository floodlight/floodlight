package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.types.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/3/18
 */
public class DHCPServerUtils {
    /* Convert int to byte[] */
    public static byte[] intToBytes(int integer) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte) (integer >> 24);
        bytes[2] = (byte) (integer >> 16);
        bytes[1] = (byte) (integer >> 8);
        bytes[0] = (byte) (integer);
        return bytes;
    }

    /* Convert int to byte[] with one byte */
    public static byte[] intToBytesSizeOne(int integer) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) (integer);
        return bytes;
    }

    /* Convert List<IPv4Address> to byte[] */
    public static byte[] IPv4ListToByteArr(List<IPv4Address> IPv4List){
        byte[] byteArray = new byte[IPv4List.size() * 4]; 	// IPv4Address is 4 bytes
        for(int i = 0; i < IPv4List.size(); ++i){
            byte[] IPv4ByteArr = new byte[4];
            int index = i * 4;
            IPv4ByteArr = IPv4List.get(i).getBytes();
            for(int j = 0; j < IPv4ByteArr.length; ++j){
                byteArray[index+j] = IPv4ByteArr[j];
            }
        }
        return byteArray;
    }

    /* Get VLAN VID */
    public static VlanVid getVlanVid(OFPacketIn pi, Ethernet eth) {
        OFPort inPort = OFMessageUtils.getInPort(pi);

        VlanVid vlanVid = null;
        if (OFMessageUtils.getVlan(pi) != OFVlanVidMatch.UNTAGGED) {
            vlanVid = OFMessageUtils.getVlan(pi).getVlanVid();  // VLAN might popped by switch
        }
        else {
            vlanVid = VlanVid.ofVlan(eth.getVlanID());          // VLAN might still be in ethernet packet
        }

        return vlanVid;
    }


    /* Get Node Port Tuple */
    public static NodePortTuple getNodePortTuple(IOFSwitch sw, OFPort inPort) {
        return new NodePortTuple(sw.getId(), inPort);
    }


    /* Determine DHCP Packet-In */
    public static boolean isDHCPPacketIn(Ethernet eth) {
        if( eth.getEtherType() != EthType.IPv4 									    // shallow compare is okay for EthType
                || ((IPv4) eth.getPayload()).getProtocol() != IpProtocol.UDP 		// shallow compare also okay for IpProtocol
                || !isDHCPPacket((UDP)((IPv4) eth.getPayload()).getPayload()))	{	// TransportPort must be deep though
            return false;
        }
        else {
            return true;
        }
    }

    public static boolean isDHCPPacket(UDP udp) {
        return (udp.getDestinationPort().equals(UDP.DHCP_SERVER_PORT)
                || udp.getDestinationPort().equals(UDP.DHCP_CLIENT_PORT))
                && (udp.getSourcePort().equals(UDP.DHCP_SERVER_PORT)
                || udp.getSourcePort().equals(UDP.DHCP_CLIENT_PORT));
    }

    /* Get DHCP Payload */
    public static DHCP getDHCPayload(Ethernet eth) {
        return (DHCP) eth.getPayload().getPayload();
    }

    /* Determine DHCP Opcode Type */
    public static IDHCPService.OpcodeType getOpcodeType(DHCP payload) {
        IDHCPService.OpcodeType opcodeType = null;
        if (payload.getOpCode() == DHCP.DHCPOpCode.OpCode_Request.getCode()) {
            opcodeType = IDHCPService.OpcodeType.REQUEST;
        }
        else if (payload.getOpCode() == DHCP.DHCPOpCode.OpCode_Reply.getCode()) {
            opcodeType = IDHCPService.OpcodeType.REPLY;
        }

        return opcodeType;
    }

    /* Determine DHCP message type */
    public static IDHCPService.MessageType getMessageType(DHCP payload) {
        byte[] dhcpDiscover = DHCPServerUtils.intToBytesSizeOne(1);
        byte[] dhcpOffer = DHCPServerUtils.intToBytesSizeOne(2);
        byte[] dhcpRequest = DHCPServerUtils.intToBytesSizeOne(3);
        byte[] dhcpDecline = DHCPServerUtils.intToBytesSizeOne(4);
        byte[] dhcpAck = DHCPServerUtils.intToBytesSizeOne(5);
        byte[] dhcpNAck = DHCPServerUtils.intToBytesSizeOne(6);
        byte[] dhcpRelease = DHCPServerUtils.intToBytesSizeOne(7);
        byte[] dhcpInform = DHCPServerUtils.intToBytesSizeOne(8);

        IDHCPService.MessageType messageType = null;
        if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpDiscover)) {
            messageType = IDHCPService.MessageType.DISCOVER;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpRequest)) {
            messageType = IDHCPService.MessageType.REQUEST;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpRelease)) {
            messageType = IDHCPService.MessageType.RELEASE;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpDecline)) {
            messageType = IDHCPService.MessageType.DECLINE;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpInform)) {
            messageType = IDHCPService.MessageType.INFORM;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpOffer)) {
            messageType = IDHCPService.MessageType.OFFER;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpAck)) {
            messageType = IDHCPService.MessageType.ACK;
        } else if (Arrays.equals(payload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), dhcpNAck)) {
            messageType = IDHCPService.MessageType.NACK;
        }

        return messageType;
    }



}
