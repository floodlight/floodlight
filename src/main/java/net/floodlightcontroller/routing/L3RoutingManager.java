package net.floodlightcontroller.routing;

import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/26/17
 */
public class L3RoutingManager {

    private static Map<String, VirtualGateway> virtualGateways = new HashMap<>();

    public L3RoutingManager() {
        // Do nothing
    }

    public Optional<Collection<VirtualGateway>> getVirtualGateways() {
        return Optional.of(virtualGateways.values());
    }

    public Optional<VirtualGateway> getVirtualGateway(String name) {
        return virtualGateways.values().stream()
                .filter(gateways -> gateways.getName().equals(name))
                .findAny();
    }

    public void addVirtualGateway(VirtualGateway gateway) {
        virtualGateways.put(gateway.getName(), gateway);
    }



}
