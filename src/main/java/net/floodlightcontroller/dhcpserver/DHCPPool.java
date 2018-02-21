package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * The class representing a DHCP Pool, which essentially a list of DHCPBinding objects containing IP, MAC, and
 * lease status information
 *
 * The DHCP pool consists fo two DHCP tables
 *   DHCP Repository - all DHCP binding here is available
 *   DHCP Leasing Pool - all DHCP binding here is in lease
 *
 * @author Ryan Izard (rizard@g.clemson.edu)
 * @edited Qing Wang (qw@g.clemson.edu) on 1/3/2018
 *
 */
public class DHCPPool implements IDHCPPool {
	protected static final Logger log = LoggerFactory.getLogger(DHCPPool.class);
	private volatile List<DHCPBinding> dhcpRepository;
	private volatile Map<MacAddress, DHCPBinding> dhcpLeasingPool;
	private static final MacAddress unassignedMacAddress = MacAddress.NONE;
	private volatile IPv4Address startingAddress;
	private int poolSize;

	public DHCPPool(@Nonnull IPv4Address startingAddress, int size) {
		dhcpRepository = new ArrayList<>();
		dhcpLeasingPool = new HashMap<>();
		int ipv4AsInt = startingAddress.getInt();
		this.startingAddress = startingAddress;

		for (int i = 0; i < size; i ++) {
			dhcpRepository.add(new DHCPBinding(IPv4Address.of(ipv4AsInt + i), unassignedMacAddress));
		}

		setPoolSize(size);
	}

	public int getPoolSize() { return poolSize; }

	private int setPoolSize(int size) { return this.poolSize = size; }

	private boolean isClientRegistered(MacAddress clientMac) {
		return dhcpLeasingPool.containsKey(clientMac);
	}

	private boolean isIPInLease(IPv4Address ip) {
		return dhcpLeasingPool.values().stream()
				.anyMatch(binding -> binding.getIPv4Address().equals(ip));
	}

	public boolean isIPBelongsToPool(@Nonnull IPv4Address ip) {
		return (ip.getInt() >= startingAddress.getInt() &&
				ip.getInt() < startingAddress.getInt() + poolSize - 1);
	}

	public Optional<IPv4Address> getLeaseIP(@Nonnull MacAddress clientMac) {
		DHCPBinding binding = dhcpLeasingPool.get(clientMac);
		return binding != null ? Optional.of(binding.getIPv4Address()) : Optional.empty();
	}

	public Optional<DHCPBinding> getLeaseBinding(@Nonnull MacAddress clientMac) {
		return Optional.of(dhcpLeasingPool.get(clientMac));
	}

	public boolean isPoolAvailable() { return !dhcpRepository.isEmpty(); }

	private boolean isIPAvailableInRepo(IPv4Address ip) {
		return dhcpRepository.stream()
				.anyMatch(binding -> binding.getIPv4Address().equals(ip));
	}

	private void moveDHCPBindingToLeasingPool(DHCPBinding binding) {
		dhcpLeasingPool.put(binding.getMACAddress(), binding);
		dhcpRepository.remove(binding);
	}

	private void returnDHCPBindingtoRepository(DHCPBinding binding) {
		dhcpLeasingPool.remove(binding.getMACAddress());
		binding.cancelLease();
		dhcpRepository.add(binding);
	}

	private DHCPBinding createLeaseForClient(@Nonnull MacAddress clientMac, long time) {
		DHCPBinding lease = dhcpRepository.get(0);
		lease.configureNormalLease(clientMac, time);
		moveDHCPBindingToLeasingPool(lease);
		return lease;
	}

	private DHCPBinding createPermanentLeaseForClient(@Nonnull MacAddress clientMac) {
		DHCPBinding lease = dhcpRepository.get(0);
		lease.configurePermanentLease(clientMac);
		moveDHCPBindingToLeasingPool(lease);
		return lease;
	}

	@Nullable
	private DHCPBinding createRequestLeaseForClient(@Nonnull MacAddress clientMac,
													@Nonnull IPv4Address requestIP, long time) {
		Optional<DHCPBinding> lease = dhcpRepository.stream()
				.filter(binding -> binding.getIPv4Address().equals(requestIP))
				.findAny();

		lease.ifPresent(l -> l.configureNormalLease(clientMac, time));
		lease.ifPresent(this::moveDHCPBindingToLeasingPool);

		//lease.get().configureNormalLease(clientMac, time);
		//moveDHCPBindingToLeasingPool(lease.get());
		return lease.get();
	}

	public Optional<IPv4Address> assignLeaseToClient(@Nonnull MacAddress clientMac, long time) {
		// Client registered already
		if (isClientRegistered(clientMac)) {
			DHCPBinding lease = dhcpLeasingPool.get(clientMac);
			if (lease.getCurrLeaseState() != LeasingState.EXPIRED) {
				return Optional.of(lease.getIPv4Address());
			}
			else {
				lease.renewLease(time);
				return Optional.of(lease.getIPv4Address());
			}
		}
		else { // New Client never registered
			if (dhcpRepository.isEmpty()) {
				return Optional.empty();
			}
			DHCPBinding lease = createLeaseForClient(clientMac, time);
			return Optional.of(lease.getIPv4Address());
		}
	}

	public Optional<IPv4Address> assignPermanentLeaseToClient(@Nonnull MacAddress clientMac) {
		// Client registered already
		if (isClientRegistered(clientMac)) {
			DHCPBinding lease = dhcpLeasingPool.get(clientMac);
			if (lease.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
				return Optional.of(lease.getIPv4Address());
			}
			else {
				lease.configurePermanentLease(clientMac);
				return Optional.of(lease.getIPv4Address());
			}
		}
		else { // New Client never registered
			if (dhcpRepository.isEmpty()) {
				return Optional.empty();
			}
			DHCPBinding lease = createPermanentLeaseForClient(clientMac);
			return Optional.of(lease.getIPv4Address());
		}
	}

	public Optional<IPv4Address> assignPermanentLeaseToClient(@Nonnull IPv4Address requestIP, @Nonnull MacAddress clientMac) {
		// Not a valid IP request
		if (isIPInLease(requestIP) || !isIPAvailableInRepo(requestIP) ) {
			log.info("Request static IP address is not available in pool");
			return Optional.empty();
		}

		return assignPermanentLeaseToClient(clientMac);
	}

	public Optional<IPv4Address> assignLeaseToClientWithRequestIP(@Nonnull IPv4Address requestIP, @Nonnull MacAddress clientMac, long time) {
		// Found client registered
		if (isClientRegistered(clientMac)) {
			if (dhcpLeasingPool.get(clientMac).getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
				log.info("Reserved Fixed dhcp entry for MAC trumps requested IP {}. Returning binding for MAC {}",
						requestIP, clientMac);
				return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());
			}
			else {
				if (isIPAvailableInRepo(requestIP)) { // Request IP available, update current lease to the binding with request IP
					returnDHCPBindingtoRepository(dhcpLeasingPool.get(clientMac));
					DHCPBinding lease = createRequestLeaseForClient(clientMac, requestIP, time);
					log.info("Found required IP address available in pool. Updating lease for MAC {} with request IP address {}",
							clientMac, lease.getIPv4Address());
					return Optional.of(lease.getIPv4Address());
				}
				else { // Request IP not available, return the current registered binding
					log.info("Required IP address is not a valid IP address in this DHCP pool. " +
									"Assigning a valid IP address {} instead for MAC {}",
							dhcpLeasingPool.get(clientMac).getIPv4Address(), clientMac);
					return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());
				}
			}
		}
		else {
			// Found client not registered
			if (!isIPAvailableInRepo(requestIP)) { // If Request IP not available
				DHCPBinding lease = createLeaseForClient(clientMac, time);
				log.info("Required IP address is not a valid IP address in this DHCP pool. " +
								"Assigning a valid IP address {} instead for MAC {}",
						dhcpLeasingPool.get(clientMac).getIPv4Address(), clientMac);
				return Optional.of(lease.getIPv4Address());
			}

			// Request IP available
			DHCPBinding lease = createRequestLeaseForClient(clientMac, requestIP, time);
			log.info("Found required IP address available in pool. Updating lease for MAC {} with Request IP address {}",
					clientMac, lease.getIPv4Address());
			return Optional.of(lease.getIPv4Address());
		}
	}

	public boolean cancelLeaseOfMac(@Nonnull MacAddress clientMac) {
		if (!isClientRegistered(clientMac)) {
			log.info("MAC {} not registered yet, no need to cancel dhcp lease", clientMac);
			return false;
		}

		log.info("DHCP lease has been cancel on MAC {} with IP {}", clientMac,
				dhcpLeasingPool.get(clientMac).getIPv4Address());
		returnDHCPBindingtoRepository(dhcpLeasingPool.get(clientMac));
		return true;
	}

	public boolean cancelLeaseOfIP(@Nonnull IPv4Address ip) {
		if(!isIPInLease(ip)){
			log.info("The request IP {} not in lease, no need to cancel", ip);
			return false;
		}

		Optional<DHCPBinding> dhcpBinding = dhcpRepository.stream()
				.filter(binding -> binding.getIPv4Address().equals(ip)).findAny();

		returnDHCPBindingtoRepository(dhcpBinding.get());
		log.info("Request IP {} matches, DHCP lease has been canceled with MAC {}",
				ip, dhcpBinding.get().getMACAddress());
		return true;
	}

	public boolean renewLeaseOfMAC(@Nonnull MacAddress clientMac, long time) {
		if (!isClientRegistered(clientMac)) {
			log.info("MAC {} not registered yet, can't renew lease", clientMac);
			return false;
		}

		DHCPBinding lease = dhcpLeasingPool.get(clientMac);
		if (lease.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
			log.info("No need to renew lease on MAC address {}, it is configured as a permanent dhcp lease", clientMac);
			return false;
		}

		if (lease.getCurrLeaseState() == LeasingState.LEASED) {
			log.info("No need to renew lease on MAC {}, it is still alive", clientMac);
			return false;
		}

		lease.renewLease(time);
		log.info("Renew lease on MAC {}", clientMac);
		return true;

	}

	public boolean renewLeaseOfIP(@Nonnull IPv4Address ip, long time) {
		Optional<DHCPBinding> lease = dhcpLeasingPool.values().stream()
				.filter(binding -> binding.getIPv4Address().equals(ip))
				.findAny();

		if (!lease.isPresent()) {
			log.info("IP {} not registered yet, can't renew lease", ip);
			return false;
		}

		if (lease.get().getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
			log.info("No need to renew lease on IP address {}, it is configured as a permanent dhcp lease", ip);
			return false;
		}

		if (lease.get().getCurrLeaseState() == LeasingState.LEASED) {
			log.info("No need to renew lease on IP {}, it is still alive", ip);
			return false;
		}

		lease.get().renewLease(time);
		log.info("Renew lease on IP {}", ip);
		return true;
	}

	public boolean removeIPEntryfromPool(@Nonnull IPv4Address ip) {
		if (!isIPAvailableInRepo(ip)) {
			log.info("The request IP address {} is not valid, either because it is in use at this point " +
					"or it is not an existing IP in DHCP pool", ip);
			return false;
		}

		if (ip.equals(startingAddress)) {
			log.info("The request IP address {} is the starting address of DHCP pool, " +
					"not allow to remove that address", startingAddress);
			return false;
		}

		DHCPBinding binding = dhcpRepository.stream()
				.filter(binding1 -> binding1.getIPv4Address().equals(ip))
				.findAny().get();
		dhcpRepository.remove(binding);
		log.info("The request IP address {} completely removed from DHCP pool", ip);
		this.setPoolSize(this.getPoolSize() - 1);

		return true;
	}

	public boolean addIPEntrytoPool(@Nonnull IPv4Address ip) {
		if (ip.getInt() >= startingAddress.getInt() && ip.getInt() < startingAddress.getInt() + poolSize -1) {
			log.info("The request IP address {} is already inside DHCP pool", ip);
			return false;
		}

		DHCPBinding binding = new DHCPBinding(ip, unassignedMacAddress);
		dhcpRepository.add(binding);
		this.setPoolSize(this.getPoolSize() + 1);
		return true;
	}

	public void showDHCPLeasingPool() {
		dhcpLeasingPool.values().stream()
				.forEach(binding -> log.info("DHCP binding inside DHCP leasing pool: " + binding.toString()));
	}

	public void showDHCPRepository() {
		dhcpRepository.stream()
				.forEach(binding -> log.info("DHCP binding inside DHCP repository: " + binding.toString()));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DHCPPool dhcpPool = (DHCPPool) o;

		if (poolSize != dhcpPool.poolSize) return false;
		if (dhcpRepository != null ? !dhcpRepository.equals(dhcpPool.dhcpRepository) : dhcpPool.dhcpRepository != null)
			return false;
		if (dhcpLeasingPool != null ? !dhcpLeasingPool.equals(dhcpPool.dhcpLeasingPool) : dhcpPool.dhcpLeasingPool != null)
			return false;
		return startingAddress != null ? startingAddress.equals(dhcpPool.startingAddress) : dhcpPool.startingAddress == null;
	}

	@Override
	public int hashCode() {
		int result = dhcpRepository != null ? dhcpRepository.hashCode() : 0;
		result = 31 * result + (dhcpLeasingPool != null ? dhcpLeasingPool.hashCode() : 0);
		result = 31 * result + (startingAddress != null ? startingAddress.hashCode() : 0);
		result = 31 * result + poolSize;
		return result;
	}

	@Override
	public String toString() {
		return "DHCPPool{" +
				"dhcpRepository=" + dhcpRepository +
				", dhcpLeasingPool=" + dhcpLeasingPool +
				", startingAddress=" + startingAddress +
				", poolSize=" + poolSize +
				'}';
	}
}
