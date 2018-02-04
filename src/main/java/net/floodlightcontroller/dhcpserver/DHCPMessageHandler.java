package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.DHCP;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 2/4/18
 */
public class DHCPMessageHandler {

    public OFPacketOut handleDHCPDiscover(IOFSwitch sw, OFPort inPort, DHCPInstance instance,
                                          IPv4Address srcAddr, IPv4Address desiredIPAddr,
                                          DHCP DhcpPayload){



    }

}
