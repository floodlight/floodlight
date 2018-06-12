package net.floodlightcontroller.routing;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.Collection;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 4/22/18
 */
public interface IGatewayService extends IFloodlightService {

    // L3 Routing Service below
    /**
     * Get all virtual gateway instances
     * @return
     */
    Collection<VirtualGatewayInstance> getGatewayInstances();

    /**
     * Get a specific virtual gateway instance based on its name
     * @param name
     * @return
     */
    Optional<VirtualGatewayInstance> getGatewayInstance(String name);


    Optional<VirtualGatewayInstance> getGatewayInstance(DatapathId dpid);


    Optional<VirtualGatewayInstance> getGatewayInstance(NodePortTuple npt);

    /**
     * Get all virtual interfaces associated with a given gateway
     * @param gateway
     * @return
     */
    Collection<VirtualGatewayInterface> getGatewayInterfaces(VirtualGatewayInstance gateway);

    /**
     * Get a specific virtual interface with a given gateway
     * @param name
     * @param gateway
     * @return
     */
    Optional<VirtualGatewayInterface> getGatewayInterface(String name, VirtualGatewayInstance gateway);

    /**
     * Add a virtual gateway instance
     * @param gateway
     */
    void addGatewayInstance(VirtualGatewayInstance gateway);

    /**
     * Add a virtual interface onto gateway
     * @param gateway
     * @param intf
     */
    void addVirtualInterface(VirtualGatewayInstance gateway, VirtualGatewayInterface intf);

    /**
     * Update a specific virtual gateway instance
     * @param name
     * @param newMac
     */
    VirtualGatewayInstance updateVirtualGateway(String name, MacAddress newMac);

    /**
     * Update a specific virtual interface
     * @param gateway
     * @param intf
     */
    void updateVirtualInterface(VirtualGatewayInstance gateway, VirtualGatewayInterface intf);

    /**
     * Delete all existing virtual gateway instances
     */
    void deleteGatewayInstances();

    /**
     * Delete a specific virtual gateway instance
     * @param name
     * @return
     */
    boolean deleteGatewayInstance(String name);

    /**
     * Delete all virtual interfaces on gateway
     * @param gateway
     */
    void removeAllVirtualInterfaces(VirtualGatewayInstance gateway);

    /**
     * Delete a specific virtual interface on gateway
     * @param interfaceName
     * @param gateway
     * @return
     */
    boolean removeVirtualInterface(String interfaceName, VirtualGatewayInstance gateway);


}
