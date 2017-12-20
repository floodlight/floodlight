package net.floodlightcontroller.routing;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 * @author Qing Wang at 12/20/17
 */
public class VirtualGateway {
    private String name;
    private MacAddress gatewayMac;
//    private

    public String getName() {
        return this.name;
    }

    public MacAddress getGatewayMac() {
        return this.gatewayMac;
    }



}
