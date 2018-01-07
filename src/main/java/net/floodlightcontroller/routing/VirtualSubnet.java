package net.floodlightcontroller.routing;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.web.serializers.VirtualSubnetSerializer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/31/17
 */
@JsonSerialize(using = VirtualSubnetSerializer.class)
public class VirtualSubnet {
    protected static final Logger log = LoggerFactory.getLogger(VirtualSubnet.class);

    private volatile String name = null;
    private volatile IPv4Address gatewayIP = IPv4Address.NONE;
    private volatile IPv4Address SubnetMask = IPv4Address.NONE;
    private volatile ArrayList<DatapathId> subnetDPIDs = new ArrayList<>();
    private volatile ArrayList<NodePortTuple> subnetNPTs = new ArrayList<>();
    private SubnetBuildMode currentBuildMode;

    public String getName() {
        return name;
    }

    public IPv4Address getSubnetMask() {
        return SubnetMask;
    }

    public IPv4Address getGatewayIP() { return gatewayIP; }

    public List<DatapathId> getSubnetDPIDs() { return subnetDPIDs; }

    public List<NodePortTuple> getSubnetNPTs() { return subnetNPTs; }

    public SubnetBuildMode getCurrentBuildMode() { return currentBuildMode; }

    public boolean checkDPIDExist(DatapathId dpid) {
        return subnetDPIDs.stream().anyMatch(DPID -> DPID.equals(dpid));
    }

    public boolean checkNPTExist(NodePortTuple npt) { return subnetNPTs.stream().anyMatch(NPT -> NPT.equals(npt)); }

    public void setGatewayIP(IPv4Address gatewayIP) { this.gatewayIP = gatewayIP; }

    public void addDPID(DatapathId dpid) {
        subnetDPIDs.add(dpid);
    }

    public void addNPT(NodePortTuple npt) {
        subnetNPTs.add(npt);
    }

    public void setSubnetMask(IPv4Address subnetMask) { SubnetMask = subnetMask; }

    public static SwitchSubnetBuilder createSwitchSubnetBuilder() { return new SwitchSubnetBuilder(); }

    public static NptSubnetBuilder createNptSubnetBuilder() { return new NptSubnetBuilder(); }

    private VirtualSubnet(SwitchSubnetBuilder builder) {
        this.name = builder.name;
        this.gatewayIP = builder.gatewayIP;
        this.subnetDPIDs.add(builder.subnetDPID);
        currentBuildMode = SubnetBuildMode.SWITCH;
    }

    private VirtualSubnet(NptSubnetBuilder builder) {
        this.name = builder.name;
        this.gatewayIP = builder.gatewayIP;
        this.subnetNPTs.add(builder.subnetNPT);
        currentBuildMode = SubnetBuildMode.NodePortTuple;
    }

    public static class SwitchSubnetBuilder {
        private String name;
        private IPv4Address gatewayIP;
        private DatapathId subnetDPID ;

        public SwitchSubnetBuilder setName(@Nonnull String name) {
            this.name = name;
            return this;
        }

        public SwitchSubnetBuilder setGatewayIP(@Nonnull IPv4Address ip) {
            this.gatewayIP = ip;
            return this;
        }

        public SwitchSubnetBuilder setSubnetBySwitch(@Nonnull DatapathId dpid) {
            this.subnetDPID = dpid;
            return this;
        }

        public VirtualSubnet build() {
            if (name == null || gatewayIP == null) {
                throw new IllegalArgumentException("Build subnet instance failed: missing fields");
            }
            return new VirtualSubnet(this);
        }

    }

    public static class NptSubnetBuilder {
        private String name;
        private IPv4Address gatewayIP;
        private NodePortTuple subnetNPT;

        public NptSubnetBuilder setName(@Nonnull String name) {
            this.name = name;
            return this;
        }

        public NptSubnetBuilder setGatewayIP(@Nonnull IPv4Address ip) {
            this.gatewayIP = ip;
            return this;
        }

        public NptSubnetBuilder setSubnetByNPT(@Nonnull NodePortTuple npt) {
            this.subnetNPT = npt;
            return this;
        }

        public VirtualSubnet build() {
            if (name == null || gatewayIP == null) {
                throw new IllegalArgumentException("Build subnet instance failed: missing fields");
            }
            return new VirtualSubnet(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VirtualSubnet that = (VirtualSubnet) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (gatewayIP != null ? !gatewayIP.equals(that.gatewayIP) : that.gatewayIP != null) return false;
        if (SubnetMask != null ? !SubnetMask.equals(that.SubnetMask) : that.SubnetMask != null) return false;
        if (subnetDPIDs != null ? !subnetDPIDs.equals(that.subnetDPIDs) : that.subnetDPIDs != null) return false;
        if (subnetNPTs != null ? !subnetNPTs.equals(that.subnetNPTs) : that.subnetNPTs != null) return false;
        return currentBuildMode == that.currentBuildMode;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (gatewayIP != null ? gatewayIP.hashCode() : 0);
        result = 31 * result + (SubnetMask != null ? SubnetMask.hashCode() : 0);
        result = 31 * result + (subnetDPIDs != null ? subnetDPIDs.hashCode() : 0);
        result = 31 * result + (subnetNPTs != null ? subnetNPTs.hashCode() : 0);
        result = 31 * result + (currentBuildMode != null ? currentBuildMode.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VirtualSubnet{" +
                "name='" + name + '\'' +
                ", gatewayIP=" + gatewayIP +
                ", SubnetMask=" + SubnetMask +
                ", subnetDPIDs=" + subnetDPIDs +
                ", subnetNPTs=" + subnetNPTs +
                ", currentBuildMode=" + currentBuildMode +
                '}';
    }
}
