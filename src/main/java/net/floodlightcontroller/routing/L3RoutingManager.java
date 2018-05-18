package net.floodlightcontroller.routing;

import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;
import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/26/17
 */
public class L3RoutingManager extends RoutingManager {

    private static Map<String, VirtualGatewayInstance> gatewayInstancesMap = new HashMap<>();

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

    public Optional<VirtualGatewayInstance> getVirtualGateway(DatapathId dpid) {
        return gatewayInstancesMap.values().stream()
                .filter(gateways -> gateways.getSwitchMembers().contains(dpid))
                .findAny();
    }

    public Optional<VirtualGatewayInstance> getVirtualGateway(NodePortTuple npt) {
        return gatewayInstancesMap.values().stream()
                .filter(gateways -> gateways.getNptMembers().contains(npt))
                .findAny();
    }

    public Optional<VirtualGatewayInstance> getVirtualGateway(IPv4AddressWithMask subnet) {
        return gatewayInstancesMap.values().stream()
                .filter(gateways -> gateways.getSubsetMembers().contains(subnet))
                .findAny();
    }

    public void removeAllVirtualGateways() { gatewayInstancesMap.clear(); }

    public boolean removeVirtualGateway(String name) {
        Optional<VirtualGatewayInstance> instance = getVirtualGateway(name);
        if (instance.isPresent()) {
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


    public Collection<VirtualGatewayInterface> getGatewayInterfaces(VirtualGatewayInstance gateway) {
        return gateway.getInterfaces();
    }

    public Optional<VirtualGatewayInterface> getGatewayInterface(String interfaceName, VirtualGatewayInstance gateway) {
        return gateway.getInterface(interfaceName);
    }

    public void removeAllVirtualInterfaces(VirtualGatewayInstance gateway) {
        gateway.clearInterfaces();
    }

    public boolean removeVirtualInterface(String interfaceName, VirtualGatewayInstance gateway) {
        Optional<VirtualGatewayInterface> vInterface = gateway.getInterface(interfaceName);
        if (vInterface.isPresent()) {
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


}
