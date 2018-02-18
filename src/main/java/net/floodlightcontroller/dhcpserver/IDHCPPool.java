package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 2/10/18
 *
 */
public interface IDHCPPool {

    /**
     * Return the lease IP based on client MAC address
     *
     * @param clientMac
     * @return
     */
    public Optional<IPv4Address> getLeaseIP(MacAddress clientMac);

    /**
     * Return the DHCP binding based on client MAC address
     *
     * @param clientMac
     * @return
     */
    public Optional<DHCPBinding> getLeaseBinding(MacAddress clientMac);

    /**
     * Assign an IPv4 address to client, one available IP from DHCP pool will be returned
     *
     * @param clientMac
     * @param time
     * @return
     */
    public Optional<IPv4Address> assignLeaseToClient(MacAddress clientMac, long time);

    /**
     * Assign an IPv4 address to client based on the Request IP address
     *
     * @param requestIP
     * @param clientMac
     * @param time
     * @return
     */
    public Optional<IPv4Address> assignLeaseToClientWithRequestIP(IPv4Address requestIP, MacAddress clientMac, long time);

    /**
     * Assign a "permanent" IPv4 address to client, based on client MAC address. Because no specific IP address requested,
     * will return an available IP.
     *
     * @param clientMac
     * @return
     */
    public Optional<IPv4Address> assignPermanentLeaseToClient(MacAddress clientMac);

    /**
     * Assign a "permanent" IPv4 address to client, based on client MAC address and request IP address
     *
     * @param requestIP
     * @param clientMac
     * @return
     */
    public Optional<IPv4Address> assignPermanentLeaseToClient(IPv4Address requestIP, MacAddress clientMac);

    /**
     * Cancel the client dhcp lease based on client MAC address
     *
     * @param clientMac
     * @return
     */
    public boolean cancelLeaseOfMac(MacAddress clientMac);

    /**
     * Cancel the client dhcp lease based on the IP address
     *
     * @param ip
     * @return
     */
    public boolean cancelLeaseOfIP(IPv4Address ip);

    /**
     * Renew the lease IP based on client MAC address
     *
     * @param clientMac
     * @param time
     * @return
     */
    public boolean renewLeaseOfMAC(MacAddress clientMac, long time);

    /**
     * Renew the lease IP based on request IP address
     *
     * @param ip
     * @param time
     * @return
     */
    public boolean renewLeaseOfIP(IPv4Address ip, long time);

}
