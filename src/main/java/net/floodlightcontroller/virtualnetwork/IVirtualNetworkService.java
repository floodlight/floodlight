package net.floodlightcontroller.virtualnetwork;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;

public interface IVirtualNetworkService extends IFloodlightService {
    /**
     * Creates a new virtual network. This can also be called
     * to modify a virtual network. To update a network you specify the GUID
     * and the fields you want to update.
     * @param network The network name. Must be unique.
     * @param guid The ID of the network. Must be unique.
     * @param gateway The IP address of the network gateway, null if none. Must be unique.
     */
    public void createNetwork(String guid, String network, Integer gateway);
    
    /**
     * Deletes a virtual network.
     * @param guid The ID (not name) of virtual network to delete.
     */
    public void deleteNetwork(String guid);
    
    /**
     * Adds a host to a virtual network. If a mapping already exists the
     * new one will override the old mapping.
     * @param mac The MAC address of the host to add.
     * @param network The network to add the host to.
     * @param port The logical port name to attach the host to. Must be unique.
     */
    public void addHost(MACAddress mac, String network, String port); 
    
    /**
     * Deletes a host from a virtual network. Either the MAC or Port must
     * be specified.
     * @param mac The MAC address to delete.
     * @param port The logical port the host is attached to.
     */
    public void deleteHost(MACAddress mac, String port);
}
