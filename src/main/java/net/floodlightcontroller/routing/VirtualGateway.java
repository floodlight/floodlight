package net.floodlightcontroller.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Qing Wang at 12/20/17
 */
public class VirtualGateway {
    private volatile String name;
    private volatile MacAddress gatewayMac;
    private volatile ArrayList<VirtualGatewayInterface> interfaces;

    protected static Logger log = LoggerFactory.getLogger(VirtualGateway.class);

    @JsonCreator
    @JsonIgnoreProperties(ignoreUnknown = true)
    public VirtualGateway(@JsonProperty("gateway-name") String name) {
        this.name = name;
        this.gatewayMac = MacAddress.NONE; // should always be controller MAC address
        this.interfaces = new ArrayList<>();
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

    public void setGatewayName(String name) {
        this.name = name;
    }

    public void setGatewayMac(MacAddress mac) {
        this.gatewayMac = mac;
    }

    public void addInterface(VirtualGatewayInterface vInterface) {
        if (!interfaces.contains(vInterface)) {
            interfaces.add(vInterface);
        }
    }

    public void removeInterface(VirtualGatewayInterface vInterface) {
        interfaces.remove(vInterface);
    }

    public void clearInterfaces() {
        this.interfaces.clear();
    }


}
