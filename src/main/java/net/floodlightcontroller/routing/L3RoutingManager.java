package net.floodlightcontroller.routing;

import net.floodlightcontroller.core.IOFSwitch;
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

    private static Map<String, VirtualGatewayInstance> gatewayInstancesMap = new HashMap<>();

    private static Map<String, VirtualSubnet> virtualSubnets = new HashMap<>();

    private static SubnetMode currentSubnetMode;

    public L3RoutingManager() {
        // Do nothing
    }

    public Collection<VirtualGatewayInstance> getAllVirtualGateways() {
        return gatewayInstancesMap.values();
    }

    public Optional<VirtualGatewayInstance> getVirtualGateway(String name) {
        return gatewayInstancesMap.values().stream()
                .filter(gateways -> gateways.getName().equals(name))
                .findAny();
    }

    public void removeAllVirtualGateways() {
        gatewayInstancesMap.clear();
    }

    public boolean removeVirtualGateway(String name) {
        if (getVirtualGateway(name).isPresent()) {
            gatewayInstancesMap.remove(name);
            return true;
        }
        else {
            return false;
        }
    }

    public void addVirtualGateway(VirtualGatewayInstance gateway) {
        gatewayInstancesMap.put(gateway.getName(), gateway);
    }

    public VirtualGatewayInstance updateVirtualGateway(String name, MacAddress newMac) {
       VirtualGatewayInstance gateway = gatewayInstancesMap.get(name);
       return gateway.getBuilder().setGatewayMac(newMac).build();
    }


    public Optional<Collection<VirtualGatewayInterface>> getGatewayInterfaces(VirtualGatewayInstance gateway) {
        return Optional.of(gateway.getInterfaces());
    }

    public Optional<VirtualGatewayInterface> getGatewayInterface(String interfaceName, VirtualGatewayInstance gateway) {
        return gateway.getInterface(interfaceName);
    }

    public void removeAllVirtualInterfaces(VirtualGatewayInstance gateway) {
        gateway.clearInterfaces();
    }

    public boolean removeVirtualInterface(String interfaceName, VirtualGatewayInstance gateway) {
        if (gateway.getInterface(interfaceName).isPresent()) {
            return gateway.removeInterface(interfaceName);
        }
        else {
            return false;
        }
    }

    public void addVirtualInterface(VirtualGatewayInstance gateway, VirtualGatewayInterface intf) {
        gateway.addInterface(intf);
    }

    public void updateVirtualInterface(VirtualGatewayInstance gateway, VirtualGatewayInterface intf) {
        gateway.addInterface(intf);
    }

    public SubnetMode getCurrentSubnetMode() {
        return currentSubnetMode;
    }

    public static void updateSubnetBuildMode(SubnetMode buildMode) { currentSubnetMode = buildMode; }

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

    public boolean isSameSubnet(IOFSwitch sw1, IOFSwitch sw2) {
        if (currentSubnetMode != SubnetMode.SWITCH) {
            return false;
        }

        boolean foundSameSubnet = false;
        for (Map.Entry<String, VirtualSubnet> entry : virtualSubnets.entrySet()) {
            if (entry.getValue().checkDPIDExist(sw1.getId()) && entry.getValue().checkDPIDExist(sw2.getId())) {
                foundSameSubnet = true;
                break;
            }
        }
        return foundSameSubnet;
    }

    public boolean isSameSubnet(NodePortTuple npt1, NodePortTuple npt2) {
        if (currentSubnetMode != SubnetMode.NodePortTuple) {
            return false;
        }

        boolean foundSameSubnet = false;
        for (Map.Entry<String, VirtualSubnet> entry : virtualSubnets.entrySet()) {
            if (entry.getValue().checkNPTExist(npt1) && entry.getValue().checkNPTExist(npt2)) {
                foundSameSubnet = true;
                break;
            }
        }
        return foundSameSubnet;
    }



}
