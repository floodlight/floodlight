package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class representing a DHCP Pool.
 * This class is essentially a list of DHCPBinding objects containing IP, MAC, and lease status information.
 *
 * @author Ryan Izard (rizard@g.clemson.edu)
 */
public class DHCPPool {
	protected static final Logger log = LoggerFactory.getLogger(DHCPPool.class);
	private volatile static ArrayList<DHCPBinding> DHCP_POOL = new ArrayList<DHCPBinding>();
	private volatile int POOL_SIZE;
	private volatile int POOL_AVAILABILITY;
	private volatile boolean POOL_FULL;
	private volatile IPv4Address STARTING_ADDRESS;
	private final MacAddress UNASSIGNED_MAC = MacAddress.NONE;

	// Need to write this to handle subnets later...
	// This assumes startingIPv4Address can handle size addresses
	/**
	 * Constructor for a DHCPPool of DHCPBinding's. Each DHCPBinding object is initialized with a
	 * null MAC address and the lease is set to inactive (i.e. false).
	 * @param {@code byte[]} startingIPv4Address: The lowest IP address to lease.
	 * @param {@code integer} size: (startingIPv4Address + size) is the highest IP address to lease.
	 * @return none
	 */
	public DHCPPool(IPv4Address startingIPv4Address, int size) {
		int IPv4AsInt = startingIPv4Address.getInt();
		this.setPoolSize(size);
		this.setPoolAvailability(size);
		STARTING_ADDRESS = startingIPv4Address;
		for (int i = 0; i < size; i++) { 
			DHCP_POOL.add(new DHCPBinding(IPv4Address.of(IPv4AsInt + i), UNASSIGNED_MAC));
		}

	}
	
	public IPv4Address getStartIp() {
		return STARTING_ADDRESS;
	}

	private void setPoolFull(boolean full) {
		POOL_FULL = full;
	}

	private boolean isPoolFull() {
		return POOL_FULL;
	}

	private void setPoolSize(int size) {
		POOL_SIZE = size;
	}

	private int getPoolSize() {
		return POOL_SIZE;
	}

	private int getPoolAvailability() {
		return POOL_AVAILABILITY;
	}

	private void setPoolAvailability(int size) {
		POOL_AVAILABILITY = size;
	}

	/**
	 * Gets the DHCPBinding object from the DHCPPool containing {@code byte[]} ip
	 * @param {@code byte[]} ip: The IPv4 address to match in a DHCPBinding
	 * @return {@code DHCPBinding}: The matching DHCPBinding object or null if ip is not found
	 */
	public DHCPBinding getDHCPbindingFromIPv4(IPv4Address ip) {
		if (ip == null) return null;

		for (DHCPBinding binding : DHCP_POOL) {
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

		for (DHCPBinding binding : DHCP_POOL) {
			if (binding.getMACAddress().equals(mac)) {
				return binding;
			}
		}
		return null;
	}
	/**
	 * Gets the lease status of a particular IPv4 address, {@code byte[]} ip
	 * @param {@code byte[]} ip: The IPv4 address of which to check the lease status 
	 * @return {@code boolean}: true if lease is active, false if lease is inactive/expired
	 */
	public boolean isIPv4Leased(IPv4Address ip) {
		if (ip == null) return false;

		if(this.getDHCPbindingFromIPv4(ip) != null) {
			return this.getDHCPbindingFromIPv4(ip).isLeaseAvailable();
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
	public boolean isIPv4BelongsPool(IPv4Address ip) {
		if (ip == null) return false;

		for (DHCPBinding binding : DHCP_POOL) {
			if (binding.getIPv4Address().equals(ip)) {
				return true;
			}
		}
		return false;
	}
	/**
	 * Assigns a MAC address to the IP address of the DHCPBinding object in the DHCPPool object.
	 * This method also sets the lease to active (i.e. true) when the assignment is made.
	 * @param {@code DHCPBinding} binding: The DHCPBinding object in which to set the MAC
	 * @param {@code byte[]} mac: The MAC address to set in the DHCPBinding object
	 * @param {@code long}: The time in seconds for which the lease will be valid
	 * @return none
	 */
	//TODO: Do we still need this method?
	public void setDHCPbinding(DHCPBinding binding, MacAddress mac, int time) {
		int index = DHCP_POOL.indexOf(binding);
		binding.setMACAddress(mac);
		binding.setLeaseStatus(true);
		this.setPoolAvailability(this.getPoolAvailability() - 1);
		DHCP_POOL.set(index, binding);
		if (this.getPoolAvailability() == 0) setPoolFull(true);
		binding.setLeaseStartTimeSeconds();
		binding.setLeaseDurationSeconds(time);
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
	public void setLeaseBinding(DHCPBinding binding, MacAddress mac, long time) {
		if (mac == null) {
			log.warn("Attempt to set a DHCP binding with a non-exist MAC address");
			return;
		}

		if (binding.isPermanentIPLease()) {
			log.warn("Attempt to set a DHCP binding which is a permanent lease");
			return;
		}

		binding.setMACAddress(mac);
		binding.setLeaseStartTimeSeconds();
		binding.setLeaseDurationSeconds(time);
		binding.setLeaseStatus(true);

		this.setPoolAvailability(this.getPoolAvailability()-1);
		if (this.getPoolAvailability() == 0) {
			setPoolFull(true);
			log.info("DHCP pool is full!");
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
	//TODO: Do we still need this method?
	public boolean configureFixedIPLease(IPv4Address ip, MacAddress mac) {
		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) {
			binding.setMACAddress(mac);
			binding.setPermanentLeaseStatus(true);
			binding.setLeaseStatus(false);
			return true;
		} else {
			return false;
		}
	}
	public void setPermanentLeaseBinding(DHCPBinding binding, MacAddress mac) {
		if (mac == null || mac == MacAddress.NONE) {
			log.warn("Attempt to use an incorrect MAC address to setup the permanent lease");
			return;
		}

		if (!binding.isPermanentIPLease()) {
			log.warn("Attempt to set an lease binding that is not permanent");
			return;
		}

		binding.setMACAddress(mac);
		binding.setLeaseStatus(true);

		this.setPoolAvailability(this.getPoolAvailability() - 1);
		if (this.getPoolAvailability() == 0) {
			setPoolFull(true);
			log.info("DHCP pool is full right now");
		}

	}
	/**
	 * Completely removes the DHCPBinding object with IP address {@code byte[]} ip from the DHCPPool
	 * @param {@code byte[]} ip: The IP address to remove from the pool. This address will not be available
	 * for lease after removal. Also pool size = pool size -1
	 * @return none
	 */
	public void removeIPv4FromDHCPPool(IPv4Address ip) {
		if (ip == null || getDHCPbindingFromIPv4(ip) == null) {
			log.warn("Attempt to remove an incorrect IPv4 address from DHCP pool");
			return;
		}

		if (ip.equals(STARTING_ADDRESS)) {
			DHCPBinding lowest = null;

			for (DHCPBinding binding : DHCP_POOL) {
				if (lowest == null) {
					lowest = binding;
				} else if (binding.getIPv4Address().equals(STARTING_ADDRESS)) {
					continue;
				}
				else if (binding.getIPv4Address().compareTo(lowest.getIPv4Address()) > 0) {
					lowest = binding;
				}
			}

			// lowest is new starting address
			STARTING_ADDRESS = lowest.getIPv4Address();
		}

		DHCP_POOL.remove(this.getDHCPbindingFromIPv4(ip));
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
	public DHCPBinding addIPv4ToDHCPPool(IPv4Address ip) {
		DHCPBinding binding = null;

		if (ip == null) {
			log.warn("Attempt to add an incorrect IPv4 address(possibly null) to DHCP pool");
			return binding;
		}

		if (this.getDHCPbindingFromIPv4(ip) == null) {
			if (ip.getInt() < STARTING_ADDRESS.getInt()) {
				STARTING_ADDRESS = ip;
			}
			binding = new DHCPBinding(ip, UNASSIGNED_MAC);
			DHCP_POOL.add(binding);
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
		return (isPoolFull() == false && getPoolAvailability() > 0);
	}
	/**
	 * Display each DHCP binding in the pool
	 */
	public void displayDHCPPool() {
		if (getPoolSize() > 0) {
			for(DHCPBinding binding : DHCP_POOL) {
				System.out.println(binding.toString());
			}
		}
		else {
			log.error("DHCP Pool size isn't allocate correctly");
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
	public DHCPBinding findLeaseBinding(MacAddress mac) {
		if (mac == null) {
			throw new IllegalArgumentException("Failed to get dhcp lease : Mac address can not be null");
		}

		if (!this.hasAvailableLease()) return null;

		DHCPBinding leaseBinding = this.getDHCPbindingFromMAC(mac);

		/* Client Mac registered already */
		if (leaseBinding != null) {
			log.debug("Found MAC {} registered in DHCP pool, returning that DHCP binding for lease.", mac);
			return leaseBinding;
		}

		/* New Client Mac that never registered */
		for (DHCPBinding binding : DHCP_POOL) {
			if (binding.isLeaseAvailable() && binding.getMACAddress().equals(UNASSIGNED_MAC)) {
				leaseBinding = binding;
				break;
			}
		}

		log.debug("Register Mac {} in DHCP pool, returning an available DHCP binding to lease with IP {}", mac, leaseBinding.getIPv4Address());
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
	public DHCPBinding getLeaseOfDesiredIP(IPv4Address desiredIp, MacAddress mac) {
		if (desiredIp == null) {
			log.debug("Attempt to get a dhcp lease using a incorrect IP address");
			return null;
		}
		if (mac == null) {
			log.debug("Attempt to get a dhcp lease using a incorrect MAC address");
			return null;
		}

		if (!this.hasAvailableLease()) return null;

		DHCPBinding binding1 = this.getDHCPbindingFromIPv4(desiredIp);
		DHCPBinding binding2 = this.getDHCPbindingFromMAC(mac);

		// If configured, we must return a reserved fixed binding for a MAC address even if it's requesting another IP
		if (binding2 != null && binding2.isLeaseAvailable() && binding2.isPermanentIPLease() && binding1 != binding2) {
			log.info("Reserved Fixed DHCP entry for MAC trumps requested IP {}. Returning binding for MAC {}", desiredIp, mac);
			return binding2;

			// If configured, we must return a fixed binding for an IP if the binding is fixed to the provided MAC (ideal static request case)
		} else if (binding1 != null && binding1.isLeaseAvailable() && binding1.isPermanentIPLease() && mac.equals(binding1.getMACAddress())) {
			log.info("Found matching fixed DHCP entry for IP with MAC. Returning binding for IP {} with MAC {}", desiredIp, mac);
			return binding1;

			// The IP and MAC are not a part of a reserved fixed binding, so return the binding of the requested IP.
		} else if (binding1 != null && binding1.isLeaseAvailable() && !binding1.isPermanentIPLease()) {
			log.info("No fixed DHCP entry for IP or MAC found. Returning dynamic binding for IP {}.", desiredIp);
			return binding1;

			// Otherwise, the binding is fixed for both MAC and IP and this MAC does not match either, so we can't return it as available
		} else {
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
	public boolean renewLease(IPv4Address ip, int time) {
		if (ip == null || ip == IPv4Address.NONE) {
			log.warn("Attempt to renew a lease using incorrect IPv4 address");
			return false;
		}

		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) {
			if (binding.isPermanentIPLease()) {
				log.debug("Failed to renew lease : IP address {} is configured as a permanent dhcp lease", ip);
				return false;
			}

			binding.setLeaseStartTimeSeconds();
			binding.setLeaseDurationSeconds(time);
			binding.setLeaseStatus(true);

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
	public boolean cancelLeaseOfIPv4(IPv4Address ip) {
		if (ip == null || ip == IPv4Address.NONE) {
			log.warn("Attempt to renew a lease using incorrect IPv4 address");
			return false;
		}

		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) {
			if (binding.isPermanentIPLease()) {
				log.debug("Failed to renew lease : IP address {} is configured as a permanent dhcp lease", ip);
				return false;
			}

			binding.cancelLease();
			binding.setLeaseStatus(false);
			this.setPoolAvailability(this.getPoolAvailability() + 1);
			this.setPoolFull(false);

			log.info("DHCP lease of Mac address {} with IP {} is successfully canceled", binding.getMACAddress(), ip);
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
	public boolean cancelLeaseOfMAC(MacAddress mac) {
		if (mac == null || mac == MacAddress.NONE) {
			log.warn("Attempt to use an incorrect MAC address to setup the permanent lease");
			return false;
		}

		DHCPBinding binding = getDHCPbindingFromMAC(mac);
		if (binding != null) {
			if (binding.isPermanentIPLease()) {
				log.debug("Failed to renew lease : MAC address {} is configured as a permanent DHCP lease", mac);
				return false;
			}

			binding.cancelLease();
			binding.setLeaseStatus(false);
			this.setPoolAvailability(this.getPoolAvailability() + 1);
			this.setPoolFull(false);

			log.info("DHCP lease of Mac address {} with IP {} is successfully canceled", mac, binding.getIPv4Address());
			return true;
		}
		return false;
	}
	/**
	 * Make the addresses of expired leases available and reset the lease times.
	 *
	 * @return {@code ArrayList<DHCPBinding>}: A list of the bindings that are now available
	 */
	public ArrayList<DHCPBinding> cleanExpiredLeases() {
		ArrayList<DHCPBinding> newAvailableLeases = new ArrayList<DHCPBinding>();
		for (DHCPBinding binding : DHCP_POOL) {
			// isLeaseExpired() automatically excludes configured static leases
			if (binding.isLeaseExpired() && binding.isActiveLease()) {
				this.cancelLeaseOfIPv4(binding.getIPv4Address());
				this.setPoolAvailability(this.getPoolAvailability() + 1);
				this.setPoolFull(false);
				newAvailableLeases.add(binding);
			}
		}
		return newAvailableLeases;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DHCPPool dhcpPool = (DHCPPool) o;

		if (POOL_SIZE != dhcpPool.POOL_SIZE) return false;
		if (POOL_AVAILABILITY != dhcpPool.POOL_AVAILABILITY) return false;
		if (POOL_FULL != dhcpPool.POOL_FULL) return false;
		if (STARTING_ADDRESS != null ? !STARTING_ADDRESS.equals(dhcpPool.STARTING_ADDRESS) : dhcpPool.STARTING_ADDRESS != null)
			return false;
		return UNASSIGNED_MAC != null ? UNASSIGNED_MAC.equals(dhcpPool.UNASSIGNED_MAC) : dhcpPool.UNASSIGNED_MAC == null;
	}

	@Override
	public int hashCode() {
		int result = POOL_SIZE;
		result = 31 * result + POOL_AVAILABILITY;
		result = 31 * result + (POOL_FULL ? 1 : 0);
		result = 31 * result + (STARTING_ADDRESS != null ? STARTING_ADDRESS.hashCode() : 0);
		result = 31 * result + (UNASSIGNED_MAC != null ? UNASSIGNED_MAC.hashCode() : 0);
		return result;
	}
	
}