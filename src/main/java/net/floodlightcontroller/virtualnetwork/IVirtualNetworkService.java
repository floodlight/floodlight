package net.floodlightcontroller.virtualnetwork;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;

public interface IVirtualNetworkService extends IFloodlightService {
    /**
     * Creates a new virtual network.
     * @param network The network name. Must be unique.
     * @param guid The ID of the network. Must be unique.
     * @param gateway The IP address of the network gateway, 0 if none. Must be unique.
     */
    public void createNetwork(String network, String guid, Integer gateway);
    
    /**
     * Deletes a virtual network.
     * @param network The virtual network to delete.
     */
    public void deleteNetwork(String network);
    
    /**
     * Adds a host to a virtual network.
     * @param mac The MAC address of the host to add.
     * @param network The network to add the host to..
     */
    public void addHost(MACAddress mac, String network); 
    
    /**
     * Deletes a host from a virtual network.
     * @param mac The MAC address to delete.
     * @param network The network the host belongs to.
     */
    public void deleteHost(MACAddress mac, String network);
}
