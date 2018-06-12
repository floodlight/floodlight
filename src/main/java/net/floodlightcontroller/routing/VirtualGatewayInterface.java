package net.floodlightcontroller.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.routing.web.serializers.VirtualInterfaceSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
@JsonSerialize(using = VirtualInterfaceSerializer.class)
public class VirtualGatewayInterface {
    private final String name;
    private IPv4Address ip;
    private IPv4AddressWithMask iPv4AddressWithMask;

    @JsonCreator
    public VirtualGatewayInterface(@JsonProperty("interface-name") String name,
                                   @JsonProperty("interface-ip") String ip,
                                   @JsonProperty("interface-mask") String mask) {
        this.name = name;
        this.ip = IPv4Address.of(ip);
        this.iPv4AddressWithMask = IPv4AddressWithMask.of(IPv4Address.of(ip), IPv4Address.of(mask));
    }

    public IPv4Address getIp() { return ip; }

    public IPv4Address getMask() { return iPv4AddressWithMask.getMask(); }

    public String getInterfaceName() { return name; }

    public void setIp(IPv4Address ip) {
        this.ip = ip;
    }

    public void setMask(IPv4Address mask) {
        this.iPv4AddressWithMask = IPv4AddressWithMask.of(ip, mask);
    }

    public boolean containsIP(IPv4Address ip) {
        return iPv4AddressWithMask.contains(ip);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VirtualGatewayInterface that = (VirtualGatewayInterface) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

}
