package net.floodlightcontroller.routing;

import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import net.floodlightcontroller.routing.VirtualSubnet.SwitchSubnetBuilder;
import net.floodlightcontroller.routing.VirtualSubnet.NptSubnetBuilder;
import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/26/17
 */
public class L3RoutingManager {

    private static Map<String, VirtualGateway> virtualGateways = new HashMap<>();

    private static Map<String, VirtualSubnet> virtualSubnets = new HashMap<>();

    private static SubnetBuildMode currentSubnetMode;

    public L3RoutingManager() {
        // Do nothing
    }

    public Optional<Collection<VirtualGateway>> getAllVirtualGateways() {
        return Optional.of(virtualGateways.values());
    }

    public Optional<VirtualGateway> getVirtualGateway(String name) {
        return virtualGateways.values().stream()
                .filter(gateways -> gateways.getName().equals(name))
                .findAny();
    }

    public void removeAllVirtualGateways() {
        virtualGateways.clear();
    }

    public boolean removeVirtualGateway(String name) {
        if (getVirtualGateway(name).isPresent()) {
            virtualGateways.remove(name);
            return true;
        }
        else {
            return false;
        }
    }

    public void createVirtualGateway(VirtualGateway gateway) {
        virtualGateways.put(gateway.getName(), gateway);
    }

    public void updateVirtualGateway(String name, MacAddress newMac) {
       VirtualGateway gateway = virtualGateways.get(name);
       gateway.setGatewayMac(newMac);
    }


    public Optional<Collection<VirtualGatewayInterface>> getGatewayInterfaces(VirtualGateway gateway) {
        return Optional.of(gateway.getInterfaces());
    }

    public Optional<VirtualGatewayInterface> getGatewayInterface(String interfaceName, VirtualGateway gateway) {
        return gateway.getInterface(interfaceName);
    }

    public void removeAllVirtualInterfaces(VirtualGateway gateway) {
        gateway.clearInterfaces();
    }

    public boolean removeVirtualInterface(String interfaceName, VirtualGateway gateway) {
        if (gateway.getInterface(interfaceName).isPresent()) {
            gateway.removeInterface(gateway.getInterface(interfaceName).get());
            return true;
        }
        else {
            return false;
        }
    }

    public void createVirtualInterface(VirtualGateway gateway, VirtualGatewayInterface intf) {
        gateway.addInterface(intf);
    }

    public void updateVirtualInterface(VirtualGateway gateway, VirtualGatewayInterface intf) {
        gateway.updateInterface(intf);
    }

    public SubnetBuildMode getCurrentSubnetMode() {
        return currentSubnetMode;
    }

    public static void updateSubnetBuildMode(SubnetBuildMode buildMode) { currentSubnetMode = buildMode; }

    public boolean checkDPIDExist(DatapathId dpid) {
        return virtualSubnets.values().stream()
                .anyMatch(subnet -> subnet.checkDPIDExist(dpid));
    }

    public boolean checkNPTExist(NodePortTuple npt) {
        return virtualSubnets.values().stream()
                .anyMatch(subnet -> subnet.checkNPTExist(npt));
    }

    public void createVirtualSubnet(String name, IPv4Address gatewayIP, DatapathId dpid) {
        SwitchSubnetBuilder switchSubnetBuilder = VirtualSubnet.createSwitchSubnetBuilder();
        switchSubnetBuilder.setName(name);
        switchSubnetBuilder.setGatewayIP(gatewayIP);
        switchSubnetBuilder.setSubnetBySwitch(dpid);
        VirtualSubnet subnet = switchSubnetBuilder.build();
        virtualSubnets.put(subnet.getName(), subnet);
        updateSubnetBuildMode(subnet.getCurrentBuildMode());
    }

    public void createVirtualSubnet(String name, IPv4Address gatewayIP, NodePortTuple npt) {
        NptSubnetBuilder nptSubnetBuilder = VirtualSubnet.createNptSubnetBuilder();
        nptSubnetBuilder.setName(name);
        nptSubnetBuilder.setGatewayIP(gatewayIP);
        nptSubnetBuilder.setSubnetByNPT(npt);
        VirtualSubnet subnet = nptSubnetBuilder.build();
        virtualSubnets.put(subnet.getName(), subnet);
        updateSubnetBuildMode(subnet.getCurrentBuildMode());
    }

    public void updateVirtualSubnet(String name, IPv4Address gatewayIP, DatapathId dpid) {
        if (!checkDPIDExist(dpid)) {
            virtualSubnets.get(name).setGatewayIP(gatewayIP);
            virtualSubnets.get(name).addDPID(dpid);
        }
        else {
            virtualSubnets.get(name).setGatewayIP(gatewayIP);
        }
    }

    public void updateVirtualSubnet(String name, IPv4Address gatewayIP, NodePortTuple npt) {
        if (!checkNPTExist(npt)) {
            virtualSubnets.get(name).setGatewayIP(gatewayIP);
            virtualSubnets.get(name).addNPT(npt);
        }
        else {
            virtualSubnets.get(name).setGatewayIP(gatewayIP);
        }
    }

    public Optional<Collection<VirtualSubnet>> getAllVirtualSubnets(){ return Optional.of(virtualSubnets.values()); }

    public Optional<VirtualSubnet> getVirtualSubnet(String name) {
        return virtualSubnets.values().stream()
                .filter(subnets -> subnets.getName().equals(name))
                .findAny();
    }

    public void removeAllVirtualSubnets() { virtualSubnets.clear(); }

    public boolean removeVirtualSubnet(String name) {
        if (getVirtualSubnet(name).isPresent()) {
            virtualSubnets.remove(name);
            return true;
        }
        else {
            return false;
        }
    }


}
