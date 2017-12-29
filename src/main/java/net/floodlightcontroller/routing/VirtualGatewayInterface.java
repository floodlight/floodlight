package net.floodlightcontroller.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class VirtualGatewayInterface {
    private String name;
    private MacAddress mac;
    private IPv4Address ip;

    @JsonCreator
    protected VirtualGatewayInterface(@JsonProperty("interface-name") String name,
                                      @JsonProperty("interface-mac") MacAddress mac,
                                      @JsonProperty("interface-ip") IPv4Address ip) {
        this.name = name;
        this.mac = mac;
        this.ip = ip;
    }

    public MacAddress getMac() {
        return mac;
    }

    public IPv4Address getIp() {
        return ip;
    }

    public void setMac(MacAddress mac) {
        this.mac = mac;
    }

    public void setIp(IPv4Address ip) {
        this.ip = ip;
    }



}
