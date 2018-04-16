package net.floodlightcontroller.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.routing.web.serializers.VirtualGatewaySerializer;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
@JsonSerialize(using = VirtualGatewaySerializer.class)
public class VirtualGateway {
    private final String name;
    private volatile MacAddress gatewayMac;
    private volatile ArrayList<VirtualGatewayInterface> interfaces;
    private volatile ArrayList<VirtualSubnet> subnets;

    protected static Logger log = LoggerFactory.getLogger(VirtualGateway.class);

    @JsonCreator
    @JsonIgnoreProperties(ignoreUnknown = true)
    public VirtualGateway(@JsonProperty("gateway-name") String name, @JsonProperty("gateway-mac") String mac) {
        this.name = name;
        this.gatewayMac = MacAddress.of(mac);
        this.interfaces = new ArrayList<>();
        this.subnets = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public MacAddress getGatewayMac() {
        return gatewayMac;
    }

    public List<VirtualGatewayInterface> getInterfaces() {
        return interfaces;
    }

    public List<VirtualSubnet> getSubnets() { return subnets; }

    public Optional<VirtualGatewayInterface> getInterface(String name) {
        return interfaces.stream()
                .filter(intf -> intf.getInterfaceName().equals(name))
                .findAny();
    }

    public Optional<VirtualSubnet> getSubnet(String name) {
        return subnets.stream()
                .filter(subnet -> subnet.getName().equals(name))
                .findAny();
    }

    public void setGatewayMac(MacAddress mac) {
        this.gatewayMac = mac;
    }

    public void addInterface(VirtualGatewayInterface vInterface) {
        if (!interfaces.contains(vInterface)) {
            interfaces.add(vInterface);
        }else {
            interfaces.set(interfaces.indexOf(vInterface), vInterface);
        }
    }

    public void addSubnet(VirtualSubnet subnet) {
        if (!subnets.contains(subnet)) {
            subnets.add(subnet);
        }
        else {
            subnets.set(subnets.indexOf(subnet), subnet);
        }
    }

    public void removeSubnet(VirtualSubnet subnet) { subnets.remove(subnet); }

    public void removeInterface(VirtualGatewayInterface vInterface) {
        interfaces.remove(vInterface);
    }

    public void clearInterfaces() {
        this.interfaces.clear();
    }

    public void clearSubnets() { this.subnets.clear(); }

    public void updateInterface(VirtualGatewayInterface vInterface) {
        VirtualGatewayInterface intf = getInterface(vInterface.getInterfaceName()).get();
        intf.setIp(vInterface.getIp());
        intf.setMac(vInterface.getMac());
        intf.setMask(vInterface.getMask());
    }

    public void updateSubnet(VirtualSubnet subnet) {
        VirtualSubnet sb = getSubnet(subnet.getName()).get();
//        sb.setGatewayIP();
    }

    public boolean isAGatewayInft(IPv4Address ip) {
        return interfaces.stream()
                .anyMatch(intf -> intf.getIp().equals(ip));
    }

    public Optional<VirtualGatewayInterface> findGatewayInft(IPv4Address ip) {
        return interfaces.stream()
                .filter(intf -> intf.containsIP(ip))
                .findAny();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VirtualGateway gateway = (VirtualGateway) o;

        return name != null ? name.equals(gateway.name) : gateway.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }


}
