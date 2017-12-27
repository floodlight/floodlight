package net.floodlightcontroller.routing;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/26/17
 */
public class L3RoutingManager {

//    private static Map<String, VirtualGateway> virtualGateways;

    private static List<VirtualGateway> virtualGateways;

    public L3RoutingManager() {
        virtualGateways = new ArrayList<>();
    }

    public List<VirtualGateway> getVirtualGateways() { return virtualGateways; }

    public Optional<VirtualGateway> getVirtualGateway(String name) {
        return virtualGateways.stream()
                .filter(virtualGateway -> virtualGateway.getName().equals(name))
                .findAny();
    }

    public void addVirtualGateway(VirtualGateway gateway) {
        virtualGateways.add(gateway);
    }



}
