package net.floodlightcontroller.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.routing.web.serializers.VirtualInterfaceSerializer;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
@JsonSerialize(using = VirtualInterfaceSerializer.class)
public class VirtualGatewayInterface {
    private String name;
    private MacAddress mac;
    private IPv4Address ip;

    @JsonCreator
    protected VirtualGatewayInterface(@JsonProperty("interface-name") String name,
                                      @JsonProperty("interface-mac") String mac,
                                      @JsonProperty("interface-ip") String ip) {
        this.name = name;
        this.mac = MacAddress.of(mac);
        this.ip = IPv4Address.of(ip);
    }

    public MacAddress getMac() {
        return mac;
    }

    public IPv4Address getIp() {
        return ip;
    }

    public String getInterfaceName() { return name; }

    public void setMac(MacAddress mac) {
        this.mac = mac;
    }

    public void setIp(IPv4Address ip) {
        this.ip = ip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VirtualGatewayInterface that = (VirtualGatewayInterface) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (mac != null ? !mac.equals(that.mac) : that.mac != null) return false;
        return ip != null ? ip.equals(that.ip) : that.ip == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (mac != null ? mac.hashCode() : 0);
        result = 31 * result + (ip != null ? ip.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VirtualGatewayInterface{" +
                "name='" + name + '\'' +
                ", mac=" + mac +
                ", ip=" + ip +
                '}';
    }
}
