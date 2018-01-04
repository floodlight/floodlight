package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * The class representing a DHCP Pool.
 * This class is essentially a list of DHCPBinding objects containing IP, MAC, and lease status information.
 *
 * @author Ryan Izard (rizard@g.clemson.edu)
 * @edited Qing Wang (qw@g.clemson.edu)
 */

public class OldDHCPPool {
    protected static final Logger log = LoggerFactory.getLogger(DHCPPool.class);
    private volatile ArrayList<DHCPBinding> dhcpPool = new ArrayList<>();
    private volatile int size;
    private volatile int availability;
    private volatile boolean POOL_FULL;
    private volatile IPv4Address startingAddress;
    private static final MacAddress unassignedMacAddress = MacAddress.NONE;

    // Need to write this to handle subnets later...
    // This assumes startingIPv4Address can handle size addresses
    /**
     * Constructor for a DHCPPool of DHCPBinding's. Each DHCPBinding object is initialized with a
     * null MAC address and the lease is set to inactive (i.e. false).
     * @param {@code byte[]} startingIPv4Address: The lowest IP address to lease.
     * @param {@code integer} size: (startingIPv4Address + size) is the highest IP address to lease.
     * @return none
     */
    public OldDHCPPool(IPv4Address startingIPv4Address, int size) {
        int ipv4AsInt = startingIPv4Address.getInt();
        this.setPoolSize(size);
        this.setPoolAvailability(size);
        startingAddress = startingIPv4Address;
        for (int i = 0; i < size; i++) {
            dhcpPool.add(new DHCPBinding(IPv4Address.of(ipv4AsInt + i), unassignedMacAddress));
        }

    }

    public IPv4Address getStartIp() {
        return startingAddress;
    }

    private void setPoolFull(boolean full) {
        POOL_FULL = full;
    }

    private boolean isPoolFull() {
        return POOL_FULL;
    }

    private void setPoolSize(int size) {
        this.size = size;
    }

    private int getPoolSize() {
        return size;
    }

    private int getPoolAvailability() {
        return availability;
    }

    private void setPoolAvailability(int size) {
        availability = size;
    }

    /**
     * Gets the DHCPBinding object from the DHCPPool containing {@code byte[]} ip
     * @param {@code byte[]} ip: The IPv4 address to match in a DHCPBinding
     * @return {@code DHCPBinding}: The matching DHCPBinding object or null if ip is not found
     */
    public DHCPBinding getDHCPbindingFromIPv4(IPv4Address ip) {
        if (ip == null) return null;

        for (DHCPBinding binding : dhcpPool) {
            if (binding.getIPv4Address().equals(ip)) {
                return binding;
            }
        }
        return null;
    }
    /**
     * Gets the DHCPBinding object from the DHCPPool containing {@code byte[]} mac
     * @param {@code byte[]} mac: The MAC address to match in in a DHCPBinding
     * @return {@code DHCPBinding}: The matching DHCPBinding object or null if mac is not found
     */
    public DHCPBinding getDHCPbindingFromMAC(MacAddress mac) {
        if (mac == null) return null;

        for (DHCPBinding binding : dhcpPool) {
            if (binding.getMACAddress().equals(mac)) {
                return binding;
            }
        }
        return null;
    }
    /**
     * Check if a particular IPv4 address associate with an existing dhcp lease
     *
     * @param {@code byte[]} ip: The IPv4 address of which to check the lease status
     * @return {@code boolean}: true if lease is leased, false if lease is available or expired
     */
    public boolean isIPLeased(IPv4Address ip) {
        if (ip == null || this.getDHCPbindingFromIPv4(ip) == null) return false;

        LeasingState currentState = this.getDHCPbindingFromIPv4(ip).getCurrLeaseState();
        if (currentState == LeasingState.LEASED || currentState == LeasingState.PERMANENT_LEASED) {
            return true;
        }
        else {
            return false;
        }

    }
    /**
     * Check if a IPv4 address belongs to a DHCP pool
     * @param {@code byte[]} ip
     * @return {@code boolean} : true or false
     */
    public boolean isIPBelongsPool(IPv4Address ip) {
        if (ip == null) return false;

        for (DHCPBinding binding : dhcpPool) {
            if (binding.getIPv4Address().equals(ip)) {
                return true;
            }
        }
        return false;
    }
    /**
     * This method sets attributes for an available DHCP binding as a normal lease.
     * After that, the binding lease status is active.
     * Notice that if you want to renew a lease, you should call renewLease()
     *
     * @param {@code DHCPBinding} binding
     * @param {@code byte[]} mac
     * @param {@code long} time
     */
    public void setNormalLease(DHCPBinding binding, @Nonnull MacAddress mac, long time) {
        if (binding.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
            log.warn("Attempt to set a dhcp binding which is a permanent lease");
            return;
        }

        binding.configureNormalLease(mac, time);

        this.setPoolAvailability(this.getPoolAvailability()-1);
        if (this.getPoolAvailability() == 0) {
            setPoolFull(true);
            log.info("dhcp pool is full!");
        }

    }
    /**
     * This method sets attributes for an available DHCP binding as a fixed/static/permanent lease.
     *
     * After that, this method does not set the lease as active, but instead reserves that IP
     * for only the MAC provided. To set the lease as active, the methods getAnyAvailableLease()
     * or getSpecificAvailableLease() will return the correct binding given the same
     * MAC provided to this method is used to bind the lease later on.
     *
     * @param {@code byte[]}: The IP address to set as static/fixed.
     * @param {@code byte[]}: The MAC address to match to the IP address ip when
     * an address is requested from the MAC mac
     * @return {@code boolean}: True upon success; false upon failure (e.g. no IP found)
     */
    public void setPermanentLease(DHCPBinding binding, @Nonnull MacAddress mac) {
        if (mac == MacAddress.NONE) {
            log.warn("Attempt to use an incorrect MAC address to setup the permanent lease");
            return;
        }

        if (binding.getCurrLeaseState() != LeasingState.PERMANENT_LEASED) {
            log.warn("Attempt to set an lease binding that is not permanent");
            return;
        }

        binding.configurePermanentLease(mac);

        this.setPoolAvailability(this.getPoolAvailability() - 1);
        if (this.getPoolAvailability() == 0) {
            setPoolFull(true);
            log.info("dhcp pool is full right now");
        }

    }
    /**
     * Completely removes the DHCPBinding object with IP address {@code byte[]} ip from the DHCPPool
     *
     * @param {@code byte[]} ip: The IP address to remove from the pool. This address will not be available
     * for lease after removal. Also pool size = pool size -1
     * @return none
     */
    public void removeIPfromPool(@Nonnull IPv4Address ip) {
        if (getDHCPbindingFromIPv4(ip) == null) {
            log.warn("Attempt to remove an incorrect IPv4 address from DHCP pool");
            return;
        }

        // Consider edge case of removing 1st IP address in DHCPPool(ArrayList)
        if (ip.equals(startingAddress)) {
            DHCPBinding lowest = null;

            for (DHCPBinding binding : dhcpPool) {
                if (lowest == null) {
                    lowest = binding;
                } else if (binding.getIPv4Address().equals(startingAddress)) {
                    continue;
                }
                else if (binding.getIPv4Address().compareTo(lowest.getIPv4Address()) > 0) {
                    lowest = binding;
                }
            }

            // lowest is new starting address
            startingAddress = lowest.getIPv4Address();
        }

        dhcpPool.remove(this.getDHCPbindingFromIPv4(ip));
        this.setPoolSize(this.getPoolSize() - 1);
        this.setPoolAvailability(this.getPoolAvailability() - 1);

        if (this.getPoolAvailability() == 0) this.setPoolFull(true);
    }
    /**
     * Adds an IP address to the DHCPPool if the address is not already present. If present, nothing is added to the DHCPPool.
     * After that, pool size = pool size + 1
     *
     * @param {@code byte[]} ip: The IP address to attempt to add to the DHCPPool
     * @return {@code DHCPBinding}: Reference to the DHCPBinding object if successful, null if unsuccessful
     */
    public DHCPBinding addIPtoPool(@Nonnull IPv4Address ip) {
        DHCPBinding binding = null;
        if (this.getDHCPbindingFromIPv4(ip) == null) {
            if (ip.getInt() < startingAddress.getInt()) {
                startingAddress = ip;
            }
            binding = new DHCPBinding(ip, unassignedMacAddress);
            dhcpPool.add(binding);
            this.setPoolSize(this.getPoolSize() + 1);
            this.setPoolAvailability(this.getPoolAvailability() + 1);
            this.setPoolFull(false);
        }

        return binding;
    }
    /**
     * Determines if there are available leases in this DHCPPool.
     * @return {@code boolean}: true if there are addresses available, false if the DHCPPool is full
     */
    public boolean hasAvailableLease() {
        return (!isPoolFull() && getPoolAvailability() > 0);
    }
    /**
     * Display each DHCP binding in the pool
     */
    public void displayDHCPPool() {
        if (getPoolSize() > 0) {
            for(DHCPBinding binding : dhcpPool) {
                log.debug("Current DHCP pool is {}", binding.toString());
            }
        }
        else {
            log.error("DHCP pool size isn't allocate correctly");
        }
    }
    /**
     * Find and returns an lease DHCP binding based on client mac address.
     * This method only responsible for find a DHCP binding but doesn't handle DHCP binding status
     *
     * If this client Mac already registered, the same dhcp binding will return at priority
     * If this is a new client Mac, then the lowest currently inactive address will be returned.
     *
     * @param {@code byte[]): MAC address of the device requesting the lease
     * @return {@code DHCPBinding}: Reference to the chosen lease bhcp binding  if successful, null if unsuccessful
     */
    public DHCPBinding getLeaseForClient(@Nonnull MacAddress mac) {
        if (!this.hasAvailableLease()) return null;

        DHCPBinding leaseBinding = this.getDHCPbindingFromMAC(mac);

		/* Client Mac registered already */
        if (leaseBinding != null) {
            log.debug("Found MAC {} registered in dhcp pool -- return that lease to client.", mac);
            return leaseBinding;
        }

		/* New Client Mac that never registered */
        for (DHCPBinding binding : dhcpPool) {
            if (binding.getCurrLeaseState() == LeasingState.AVAILABLE && binding.getMACAddress().equals(unassignedMacAddress)) {
                leaseBinding = binding;
                log.debug("Registered Mac {} in dhcp pool -- returning an available lease to client with IP {}", mac, leaseBinding.getIPv4Address());
                break;
            }
        }
        return leaseBinding;

    }
    /**
     * Find and returns a specific lease binding based on request desired IP address and client Mac address
     * This method only responsible for find a DHCP binding but doesn't handle DHCP binding status
     *
     *   (1) If the MAC is found in an available, reserved fixed binding, and that binding is not for the provided IP,
     *       the fixed binding associated with the MAC will be returned.
     *
     *   (2) If the IP is found in an available, fixed binding, and that binding also contains the MAC address provided,
     *       then the binding will be returned -- this is true only if the IP and MAC result in the same available, fixed binding.
     *
     *   (3) If the IP is found in the pool and it is available and not fixed, then its binding will be returned.
     *
     *   (4) If the IP provided does not match any available entries or is invalid, null will be returned.
     *       If this is the case, run findLeaseBinding(mac) to resolve.
     *
     *
     * @param {@code byte[]}: The IP address try to obtain for a lease
     * @param {@code byte[]}: The Client MAC address
     * @return {@code DHCPBinding}: Reference to the chosen lease bhcp binding if successful, null if unsuccessful
     */
    // TODO: don't like logic here
    public DHCPBinding getLeaseForClientWithDesiredIP(@Nonnull IPv4Address desiredIp, @Nonnull MacAddress mac) {
        if (!this.hasAvailableLease()) return null;

        DHCPBinding binding1 = this.getDHCPbindingFromIPv4(desiredIp);
        DHCPBinding binding2 = this.getDHCPbindingFromMAC(mac);

        // Found client registered as a permanent release -- must return that permanent binding even if requesting another IP
        if (binding2 != null && binding2.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
            log.info("Reserved Fixed dhcp entry for MAC trumps requested IP {}. Returning binding for MAC {}", desiredIp, mac);
            return binding2;
        }
        // Found desired IP already assigned as a permanent lease associated with client MAC -- return this permanent binding
        else if (binding1 != null && binding1.getCurrLeaseState() == LeasingState.PERMANENT_LEASED && mac.equals(binding1.getMACAddress())) {
            log.info("Found matching fixed dhcp entry for IP with MAC. Returning binding for IP {} with MAC {}", desiredIp, mac);
            return binding1;
        }
        // Client desired IP and it MAC not associated with any permanent lease -- return binding of request desired IP
        else if (binding1 != null && binding1.getCurrLeaseState() == LeasingState.AVAILABLE) {
            log.info("No fixed dhcp entry for IP or MAC found. Returning dynamic binding for IP {}.", desiredIp);
            return binding1;
        }
        // O.W. the binding is fixed for both MAC and IP and this MAC does not match either, so we can't return it as available
        else {
            log.debug("Invalid IP address request or IP is actively leased...check for any available lease to resolve");
            return null;
        }

    }
    /**
     * Tries to renew an IP lease.
     *
     * @param {@code byte[]}: The IP address on which to try and renew a lease
     * @param {@code long}: The time in seconds for which the lease will be valid
     * @return {@code DHCPBinding}: True on success, false if unknown IP address
     */
    public boolean renewLeaseOfIP(@Nonnull IPv4Address ip, int time) {
        if (ip == IPv4Address.NONE) {
            log.warn("Attempt to renew a lease using incorrect IPv4 address");
            return false;
        }

        DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
        if (binding != null) {
            if (binding.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
                log.debug("Failed to renew lease : IP address {} is configured as a permanent dhcp lease", ip);
                return false;
            }

            binding.renewLease(time);
            log.info("IP address {} has successfully renewed", ip);
            return true;
        }
        return false;
    }
    /**
     * Cancel an IP lease.
     *
     * @param {@code byte[]}: The IP address on which to try and cancel a lease
     * @return {@code boolean}: True on success, false if unknown IP address
     */
    public boolean cancelLeaseOfIPv4(@Nonnull IPv4Address ip) {
        if (ip == IPv4Address.NONE) {
            log.warn("Attempt to renew a lease using incorrect IPv4 address");
            return false;
        }

        DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
        if (binding != null) {
            if (binding.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
                log.debug("Failed to renew lease : IP address {} is configured as a permanent dhcp lease", ip);
                return false;
            }

            binding.cancelLease();
            this.setPoolAvailability(this.getPoolAvailability() + 1);
            this.setPoolFull(false);
            log.info("dhcp lease of Mac address {} with IP {} is successfully canceled", binding.getMACAddress(), ip);
            return true;
        }
        return false;
    }
    /**
     * Cancel an IP lease.
     *
     * @param {@code byte[]}: The MAC address on which to try and cancel a lease
     * @return {@code boolean}: True on success, false if unknown IP address
     */
    public boolean cancelLeaseOfMAC(@Nonnull MacAddress mac) {
        if (mac == MacAddress.NONE) {
            log.warn("Attempt to use an incorrect MAC address to setup the permanent lease");
            return false;
        }

        DHCPBinding binding = getDHCPbindingFromMAC(mac);
        if (binding != null) {
            if (binding.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
                log.debug("Failed to renew lease : MAC address {} is configured as a permanent dhcp lease", mac);
                return false;
            }

            binding.cancelLease();
            this.setPoolAvailability(this.getPoolAvailability() + 1);
            this.setPoolFull(false);
            log.info("dhcp lease of Mac address {} with IP {} is successfully canceled", mac, binding.getIPv4Address());
            return true;
        }
        return false;
    }
    /**
     * Make the addresses of expired leases available and reset the lease times.
     *
     * @return {@code ArrayList<DHCPBinding>}: A list of the bindings that are now available
     */
    public List<DHCPBinding> cleanExpiredLeases() {
        List<DHCPBinding> newAvailableLeases = new ArrayList<>();
        for (DHCPBinding binding : dhcpPool) {
            // isLeaseExpired() automatically excludes configured static leases
            if (binding.getCurrLeaseState() == LeasingState.EXPIRED) {
                this.cancelLeaseOfIPv4(binding.getIPv4Address());
                this.setPoolAvailability(this.getPoolAvailability() + 1);
                this.setPoolFull(false);
                newAvailableLeases.add(binding);
            }
        }
        return newAvailableLeases;
    }


}