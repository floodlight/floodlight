package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.Optional;

/**
 *
 * @author Qing Wang (qw@g.clemson.edu) at 2/10/18
 */
public interface IDHCPPool {

    /**
     * Return the lease IP based on client MAC address
     *
     * @param clientMac
     * @return
     */
    Optional<IPv4Address> getLeaseIP(MacAddress clientMac);

    /**
     * Return the DHCP binding based on client MAC address
     *
     * @param clientMac
     * @return
     */
    Optional<DHCPBinding> getLeaseBinding(MacAddress clientMac);

    /**
     * Assign an IPv4 address to client, one available IP from DHCP pool will be returned
     *
     * @param clientMac
     * @param timeSec
     * @return
     */
    Optional<IPv4Address> assignLeaseToClient(MacAddress clientMac, long timeSec);

    /**
     * Assign an IPv4 address to client, one available IP from DHCP pool will be returned
     *
     * When this function called, each time DHCP pool will return a different IP
     *
     * @param clientMac
     * @param timeSec
     * @return
     */
    Optional<IPv4Address> assignDynamicLeaseToClient(MacAddress clientMac, long timeSec);

    /**
     * Assign an IPv4 address to client based on the Request IP address
     *
     * @param requestIP
     * @param clientMac
     * @param timeSec
     * @return
     */
    Optional<IPv4Address> assignLeaseToClientWithRequestIP(IPv4Address requestIP, MacAddress clientMac, long timeSec, boolean dynamicLease);

    /**
     * Assign a "permanent" IPv4 address to client, based on client MAC address. Because no specific IP address requested,
     * will return an available IP.
     *
     * @param clientMac
     * @return
     */
    Optional<IPv4Address> assignPermanentLeaseToClient(MacAddress clientMac);

    /**
     * Assign a "permanent" IPv4 address to client, based on client MAC address and request IP address
     *
     * If request IP is not valid, DHCP server will not try to allocate an available IP. It is client responsibility to
     * specify an valid IP as a permanent IP
     *
     * @param requestIP
     * @param clientMac
     * @return
     */
    Optional<IPv4Address> assignPermanentLeaseToClientWithRequestIP(IPv4Address requestIP, MacAddress clientMac);

    /**
     * Cancel the client dhcp lease based on client MAC address
     *
     * @param clientMac
     * @return
     */
    boolean cancelLeaseOfMac(MacAddress clientMac);

    /**
     * Cancel the client dhcp lease based on the IP address
     *
     * @param ip
     * @return
     */
    boolean cancelLeaseOfIP(IPv4Address ip);

    /**
     * Renew the lease IP based on client MAC address
     *
     * @param clientMac
     * @param timeSec
     * @return
     */
    boolean renewLeaseOfMAC(MacAddress clientMac, long timeSec);

    /**
     * Renew the lease IP based on request IP address
     *
     * @param ip
     * @param timeSec
     * @return
     */
    boolean renewLeaseOfIP(IPv4Address ip, long timeSec);

    /**
     * Check expired leases in DHCP pool
     */
    void checkExpiredLeases();

}
