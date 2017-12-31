package net.floodlightcontroller.routing;

import org.projectfloodlight.openflow.types.MacAddress;

import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/26/17
 */
public class L3RoutingManager {

    private static Map<String, VirtualGateway> virtualGateways = new HashMap<>();

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



}
