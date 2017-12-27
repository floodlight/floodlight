package net.floodlightcontroller.routing;

import net.floodlightcontroller.packet.IPv4;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Qing Wang at 12/20/17
 */
public class VirtualGateway {
    private volatile String name;
    private volatile MacAddress gatewayMac;
    private volatile Map<String, GatewayInterface> interfaces;

    protected static Logger log = LoggerFactory.getLogger(VirtualGateway.class);

    public VirtualGateway(String name, MacAddress gatewayMac) {
        this.name = name;
        this.gatewayMac = gatewayMac;
        this.interfaces = new ConcurrentHashMap<String, GatewayInterface>();
    }

    public String getName() {
        return name;
    }

    public MacAddress getGatewayMac() {
        return gatewayMac;
    }

    public Map<String, GatewayInterface> getInterfaces() {
        return interfaces;
    }

    public void setGatewayName(String name) {
        this.name = name;
    }

    public void setGatewayMac(MacAddress mac) {
        this.gatewayMac = mac;
    }

    public boolean addInterface(String name, IPv4Address ip) {
        if (isIPAvailable(ip)) {
            log.debug("Add new interface with IP address {} and Mac address {}", ip, gatewayMac);
            interfaces.put(name, new GatewayInterface(gatewayMac, ip));
            return true;
        }

        log.debug("Attempt to add new interface with an invalid IP {}, this mostly because this IP already associated with another interface", ip);
        return false;
    }

    public boolean updateInterface(String name, IPv4Address ip) {
        if (interfaces.containsKey(name)) {
            log.debug("Update an existing interface {} with IP address {}", name, ip);
            interfaces.get(name).setIp(ip);
            return true;
        }
        else {
            log.debug("Attempt to update an non-existing interface {} with IP address {}, double check virtual interface configuration", name, ip);
            return false;
        }

    }

    private boolean isIPAvailable(IPv4Address ip) {
        for(Map.Entry<String, GatewayInterface> entry : this.interfaces.entrySet()) {
            if (entry.getValue().getIp().equals(ip)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes a virtual gateway interface based on virtual interface IP address
     * @param {@code IPv4Address}
     * @return boolean: true: removed, false: interface not found
     */
    public boolean removeInterface(IPv4Address ip) {
        GatewayInterface intf = new GatewayInterface(gatewayMac, ip);

        for (Map.Entry<String, GatewayInterface> entry : this.interfaces.entrySet()) {
            if (entry.getValue().equals(intf)) {
                interfaces.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }
    /**
     * Removes a virtual gateway interface based on virtual interface name
     * @param {@code String}
     * @return boolean: true: removed, false: interface not found
     */
    public boolean removeInterface(String name) {
        for (Map.Entry<String, GatewayInterface> entry : this.interfaces.entrySet()) {
            if (entry.getKey().equals(name)) {
                interfaces.remove(name);
                return true;
            }
        }
        return false;
    }

    public void clearInterfaces() {
        this.interfaces.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VirtualGateway that = (VirtualGateway) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (gatewayMac != null ? !gatewayMac.equals(that.gatewayMac) : that.gatewayMac != null) return false;
        return interfaces != null ? interfaces.equals(that.interfaces) : that.interfaces == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (gatewayMac != null ? gatewayMac.hashCode() : 0);
        result = 31 * result + (interfaces != null ? interfaces.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VirtualGateway{" +
                "name='" + name + '\'' +
                ", gatewayMac=" + gatewayMac +
                ", interfaces=" + interfaces +
                '}';
    }

    class GatewayInterface {
        private MacAddress mac;
        private IPv4Address ip;

        protected GatewayInterface(MacAddress mac, IPv4Address ip) {
            this.setMac(mac);
            this.setIp(ip);
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GatewayInterface that = (GatewayInterface) o;

            if (mac != null ? !mac.equals(that.mac) : that.mac != null) return false;
            return ip != null ? ip.equals(that.ip) : that.ip == null;
        }

        @Override
        public int hashCode() {
            int result = mac != null ? mac.hashCode() : 0;
            result = 31 * result + (ip != null ? ip.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "GatewayInterface{" +
                    "mac=" + mac +
                    ", ip=" + ip +
                    '}';
        }

    }

}
