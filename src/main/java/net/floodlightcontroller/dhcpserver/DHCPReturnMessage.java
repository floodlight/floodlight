package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.protocol.OFPacketOut;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 3/24/18
 */
public class DHCPReturnMessage {
    private OFPacketOut dhcpPacketOut;
    private boolean sendFlag;

    public void setDhcpPacketOut(OFPacketOut dhcpPacketOut) {
        this.dhcpPacketOut = dhcpPacketOut;
    }

    public void setSendFlag(boolean sendFlag) {
        this.sendFlag = sendFlag;
    }

    public boolean getSendFlag() {
        return sendFlag;
    }

    public OFPacketOut getDhcpPacketOut() {
        return dhcpPacketOut;
    }

}
