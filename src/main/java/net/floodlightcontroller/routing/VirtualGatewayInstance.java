package net.floodlightcontroller.routing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.netty.util.internal.ConcurrentSet;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.web.serializers.VirtualGatewaySerializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/20/17
 */
@JsonSerialize(using = VirtualGatewaySerializer.class)
public class VirtualGatewayInstance {
    protected static final Logger log = LoggerFactory.getLogger(VirtualGatewayInstance.class);

    private final String name;
    private volatile MacAddress gatewayMac = MacAddress.NONE;
    private volatile List<VirtualGatewayInterface> interfaces = null;
    private volatile VirtualGatewayInstanceBuilder builder = null;
    private Set<DatapathId> switchMembers = null;
    private Set<NodePortTuple> nptMembers = null;
    private Set<IPv4AddressWithMask> subsetMembers = null;

    public String getName() {
        return name;
    }
    public MacAddress getGatewayMac() {
        return gatewayMac;
    }
    public List<VirtualGatewayInterface> getInterfaces() { return interfaces; }
    public Set<DatapathId> getSwitchMembers() { return switchMembers; }
    public Set<NodePortTuple> getNptMembers() { return nptMembers; }
    public Set<IPv4AddressWithMask> getSubsetMembers() { return subsetMembers; }
    public Optional<VirtualGatewayInterface> getInterface(String name) {
        return interfaces.stream()
                .filter(intf -> intf.getInterfaceName().equals(name))
                .findAny();
    }
    public VirtualGatewayInstanceBuilder getBuilder() { return builder; }

    public boolean isSwitchAMember(DatapathId dpid) { return switchMembers.contains(dpid); }
    public boolean isNptAMember(NodePortTuple npt) { return nptMembers.contains(npt); }
    public boolean isSubnetAMember(IPv4AddressWithMask subnet) { return subsetMembers.contains(subnet); }

    public void addInterface(VirtualGatewayInterface vInterface) {
        if (!vInterface.getGatewayName().equals(name)) {
            return;
        }

        if (!interfaces.contains(vInterface)) {
            interfaces.add(vInterface);
        }else {
            interfaces.set(interfaces.indexOf(vInterface), vInterface);
        }
    }

    public void updateInterface(VirtualGatewayInterface vInterface) {
        if (!vInterface.getGatewayName().equals(name)) {
            return;
        }

        if (getInterface(vInterface.getInterfaceName()).isPresent()) {
            VirtualGatewayInterface intf = getInterface(vInterface.getInterfaceName()).get();
            intf.setIp(vInterface.getIp());
            intf.setMask(vInterface.getMask());
        }
    }

    public void removeInterface(String interfaceName) {
        if (!getInterface(interfaceName).isPresent()) {
            return;
        }
        // TODO: updated to map
        interfaces.remove(name);
    }

    public void clearInterfaces() {
        this.interfaces.clear();
    }

    public void addSwitchMember(DatapathId dpid) { this.switchMembers.add(dpid); }

    public void addNptMember(NodePortTuple npt) { this.nptMembers.add(npt); }

    public void addSubnetMember(IPv4AddressWithMask subnet) { this.subsetMembers.add(subnet); }

    public void removeSwitchMember(DatapathId dpid) {
        if (!this.switchMembers.isEmpty()) {
            for (DatapathId id : this.switchMembers) {
                if (id.equals(dpid)) {
                    this.switchMembers.remove(id);
                }
            }
        }
    }

    public void removeNptMember(NodePortTuple npt) {
        if (!this.nptMembers.isEmpty()) {
            for (NodePortTuple nodePortTuple : this.nptMembers) {
                if (nodePortTuple.equals(npt)) {
                    this.nptMembers.remove(npt);
                }
            }
        }
    }

    public void removeSubnetMember(IPv4AddressWithMask iPv4AddressWithMask) {
        if (!this.subsetMembers.isEmpty()) {
            for (IPv4AddressWithMask subnet : this.subsetMembers) {
                if (subnet.equals(iPv4AddressWithMask)) {
                    this.subsetMembers.remove(iPv4AddressWithMask);
                }
            }
        }
    }

    public void removeSwitchFromInstance(DatapathId dpid) {
        if (!this.switchMembers.isEmpty()) {
            for (DatapathId id : this.switchMembers) {
                if (id.equals(dpid)) {
                    this.switchMembers.remove(id);
                }
            }
        }

        if (!this.nptMembers.isEmpty()) {
            for (NodePortTuple npt : this.nptMembers) {
                if (npt.getNodeId().equals(dpid)) {
                    this.nptMembers.remove(npt);
                }
            }
        }
    }

    public void clearSwitchMembers() {
        if (!this.switchMembers.isEmpty()) {
            this.switchMembers.clear();
        }
    }

    public void clearNptMembers() {
        if (!this.nptMembers.isEmpty()) {
            this.nptMembers.clear();
        }
    }

    public void clearSubnetMembers() {
        if (!this.subsetMembers.isEmpty()) {
            this.subsetMembers.clear();
        }
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

    private VirtualGatewayInstance(VirtualGatewayInstanceBuilder builder) {
        this.name = builder.name;
        this.gatewayMac = builder.gatewayMac;
        this.interfaces = builder.interfaces;
        this.switchMembers = builder.switchMembers;
        this.nptMembers = builder.nptMembers;
        this.subsetMembers = builder.subsetMembers;
        this.builder = builder;
    }

    public static VirtualGatewayInstanceBuilder createInstance(@Nonnull final String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Build gateway instance failed : virtual gateway name can not be empty");
        }

        return new VirtualGatewayInstanceBuilder(name);
    }


    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "set")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VirtualGatewayInstanceBuilder {
        private final String name;
        private MacAddress gatewayMac = MacAddress.NONE;
        private List<VirtualGatewayInterface> interfaces;
        private Set<DatapathId> switchMembers;
        private Set<NodePortTuple> nptMembers;
        private Set<IPv4AddressWithMask> subsetMembers;

        // Create virtual gateway instance from REST API
        @JsonCreator
        @JsonIgnoreProperties(ignoreUnknown = true)
        private VirtualGatewayInstanceBuilder(@JsonProperty("gateway-name") String name, @JsonProperty("gateway-mac") String mac) {
            this.name = name;
            this.gatewayMac = MacAddress.of(mac);
            this.interfaces = new ArrayList<>();
            this.switchMembers = new ConcurrentSet<>();
            this.nptMembers = new ConcurrentSet<>();
            this.subsetMembers = new ConcurrentSet<>();
        }

        public VirtualGatewayInstanceBuilder(final String name) {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Build gateway instance failed : virtual gateway name can not be empty");
            }

            this.name = name;
        }

        public VirtualGatewayInstanceBuilder setGatewayMac(@Nonnull MacAddress mac) {
            if (mac.equals(MacAddress.NONE)) {
                throw new IllegalArgumentException("Build gateway instance failed: gateway MAC address can not be empty");
            }
            this.gatewayMac = mac;
            return this;
        }

        public VirtualGatewayInstanceBuilder setInterfaces(@Nonnull List<VirtualGatewayInterface> interfaces) {
            this.interfaces = interfaces;
            return this;
        }

        public VirtualGatewayInstanceBuilder setSwitchMembers(@Nonnull Set<DatapathId> switchMembers) {
            this.switchMembers = switchMembers;
            return this;
        }

        public VirtualGatewayInstanceBuilder setNptMembers(@Nonnull Set<NodePortTuple> nptMembers) {
            this.nptMembers = nptMembers;
            return this;
        }

        public VirtualGatewayInstanceBuilder setSubnetMembers(@Nonnull Set<IPv4AddressWithMask> subnetMembers) {
            this.subsetMembers = subnetMembers;
            return this;
        }

        public VirtualGatewayInstance build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Build gateway instance failed: Gateway instance name can not be null or empty");
            }

            if (gatewayMac == null || gatewayMac.equals(MacAddress.NONE)) {
                throw new IllegalArgumentException("Build gateway instance failed: Gateway instance MAC address can not be null or empty");
            }

            if (this.interfaces == null) {
                this.interfaces = new ArrayList<>();
            }

            if (this.switchMembers == null) {
                this.switchMembers = new ConcurrentSet<>();
            }

            if (this.nptMembers == null) {
                this.nptMembers = new ConcurrentSet<>();
            }

            if (this.subsetMembers == null) {
                this.subsetMembers = new ConcurrentSet<>();
            }

            return new VirtualGatewayInstance(this);
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VirtualGatewayInstance gateway = (VirtualGatewayInstance) o;

        return name != null ? name.equals(gateway.name) : gateway.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }


}
