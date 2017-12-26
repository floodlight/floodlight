package net.floodlightcontroller.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/26/17
 */
public class L3RoutingManager {

    private static List<VirtualGateway> virtualGateways;


    public L3RoutingManager() {
        virtualGateways = new ArrayList<>();
    }

    public List<VirtualGateway> getVirtualGateways() { return virtualGateways; }

}
