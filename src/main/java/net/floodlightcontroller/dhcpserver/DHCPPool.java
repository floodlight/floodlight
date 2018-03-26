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
 * The DHCP pool consists of two DHCP tables
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

	public int getLeasingPoolSize() { return dhcpLeasingPool.size(); }

	public int getRepositorySize() { return dhcpRepository.size(); }

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

	public Optional<MacAddress> getClientMacByIP(@Nonnull IPv4Address ip) {
		return dhcpLeasingPool.entrySet().stream()
				.filter(clientMac -> Objects.equals(clientMac.getValue().getIPv4Address(), ip))
				.map(Map.Entry::getKey)
				.findAny();
	}

	public Optional<DHCPBinding> getLeaseBinding(@Nonnull MacAddress clientMac) {
		DHCPBinding binding = dhcpLeasingPool.get(clientMac);
		return Optional.ofNullable(binding);
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

	private DHCPBinding createPermanentLeaseForClientWithRequestIP(@Nonnull IPv4Address ip, @Nonnull MacAddress clientMac) {
		Optional<DHCPBinding> lease = dhcpRepository.stream()
							.filter(binding -> binding.getIPv4Address().equals(ip))
							.findAny();

		lease.get().configurePermanentLease(clientMac);
		moveDHCPBindingToLeasingPool(lease.get());
		return lease.get();
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

	@Nonnull
	private DHCPBinding createDynamicLeaseForClient(@Nonnull MacAddress clientMac,
													@Nonnull IPv4Address requestIP, long time) {
		Optional<DHCPBinding> lease = dhcpRepository.stream()
				.filter(binding -> !binding.getIPv4Address().equals(requestIP))
				.findAny();

		lease.ifPresent(l -> l.configureNormalLease(clientMac, time));
		lease.ifPresent(this::moveDHCPBindingToLeasingPool);

		return lease.get();
	}

	public Optional<IPv4Address> assignLeaseToClient(@Nonnull MacAddress clientMac, long timeSec) {
		// Client registered already
		if (isClientRegistered(clientMac)) {
			DHCPBinding lease = dhcpLeasingPool.get(clientMac);
			if (lease.getCurrLeaseState() != LeasingState.EXPIRED) {
				return Optional.of(lease.getIPv4Address());
			}
			else {
				lease.renewLease(timeSec);
				return Optional.of(lease.getIPv4Address());
			}
		}
		else { // New Client never registered
			if (dhcpRepository.isEmpty()) {
				return Optional.empty();
			}
			DHCPBinding lease = createLeaseForClient(clientMac, timeSec);
			return Optional.of(lease.getIPv4Address());
		}
	}

	public Optional<IPv4Address> assignDynamicLeaseToClient(@Nonnull MacAddress clientMac, long timeSec) {
		if (isClientRegistered(clientMac)) {
			DHCPBinding lease = dhcpLeasingPool.get(clientMac);
			if (lease.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
				log.debug("Found Client! Reserved Fixed dhcp entry for MAC trumps dynamic IP assignment. Returning dhcp binding {}",
						clientMac, dhcpLeasingPool.get(clientMac).getIPv4Address());
				return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());
			}

			if (!dhcpRepository.isEmpty()) {
				returnDHCPBindingtoRepository(lease);
				return Optional.of(createLeaseForClient(clientMac, timeSec).getIPv4Address());
			}
			else {
				log.debug("DHCP Pool is full! Can't dynamically re-allocate address for {}", clientMac.toString());
				return Optional.of(lease.getIPv4Address());
			}

		}
		else {
			if (dhcpRepository.isEmpty()) {
				return Optional.empty();
			}
			DHCPBinding lease = createLeaseForClient(clientMac, timeSec);
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
			if (!isPoolAvailable()) {
				return Optional.empty();
			}
			DHCPBinding lease = createPermanentLeaseForClient(clientMac);
			return Optional.of(lease.getIPv4Address());
		}
	}

	public Optional<IPv4Address> assignPermanentLeaseToClientWithRequestIP(@Nonnull IPv4Address requestIP, @Nonnull MacAddress clientMac) {
		// If request IP already in-use by client, directly use it
		if (isClientRegistered(clientMac) && getLeaseIP(clientMac).get().equals(requestIP)) {
			DHCPBinding binding = getLeaseBinding(clientMac).get();
			binding.configurePermanentLease(clientMac);
			return Optional.of(binding.getIPv4Address());
		}

		// Not a valid request IP address in dhcp repository
		if (!isIPAvailableInRepo(requestIP)) {
			log.info("Request static IP address is not available in pool");
			return Optional.empty();
		}

		// A valid request IP address, if client currently use another IP address, update it
		if (isClientRegistered(clientMac)) {
			DHCPBinding currBinding = getLeaseBinding(clientMac).get();
			returnDHCPBindingtoRepository(currBinding);
			return Optional.of(createPermanentLeaseForClientWithRequestIP(requestIP, clientMac).getIPv4Address());
		}
		else {
			return Optional.of(createPermanentLeaseForClientWithRequestIP(requestIP, clientMac).getIPv4Address());
		}

	}

	public Optional<IPv4Address> assignLeaseToClientWithRequestIP(@Nonnull IPv4Address requestIP, @Nonnull MacAddress clientMac,
																  long timeSec, boolean dynamicLease) {
		if (!isPoolAvailable()) {
			if (isClientRegistered(clientMac)) {
				if (dhcpLeasingPool.size() != 0 && dhcpLeasingPool.get(clientMac).getIPv4Address().equals(requestIP)) {
					log.info("DHCP pool is full! Found required IP address {} already in use with Mac {}, do nothing", requestIP, clientMac);
				}
				if (dhcpLeasingPool.size() != 0 && !dhcpLeasingPool.get(clientMac).getIPv4Address().equals(requestIP)) {
					log.info("DHCP pool is full! Can't allocate required IP address {}, use existing dhcp lease {} instead",
							requestIP, new Object[]{clientMac, dhcpLeasingPool.get(clientMac).getIPv4Address()});
				}
				return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());
			}
			else {
				log.info("DHCP Pool is full! Trying to allocate more space");
				return Optional.empty();
			}

		}

		// Client not registered case
		if (!isClientRegistered(clientMac) && isIPAvailableInRepo(requestIP)) {
			DHCPBinding lease = createRequestLeaseForClient(clientMac, requestIP, timeSec);
			log.info("New client! Found required IP address {} available in pool. Assign DHCP lease {} to client",
					requestIP, new Object[]{clientMac, lease.getIPv4Address()});
			return Optional.of(lease.getIPv4Address());
		}
		else if (!isClientRegistered(clientMac) && !isIPAvailableInRepo(requestIP)) {
			DHCPBinding lease = createLeaseForClient(clientMac, timeSec);
			log.info("New Client! Required IP address {} is not a valid IP address in this DHCP pool. " +
							"Assigning a valid dhcp lease {} instead",
					requestIP, new Object[]{clientMac, dhcpLeasingPool.get(clientMac).getIPv4Address()});
			return Optional.of(lease.getIPv4Address());
		}

		// Below client should register already
		if (dhcpLeasingPool.get(clientMac).getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
			log.info("Found Client! Reserved Fixed dhcp entry for MAC trumps requested IP {}. Returning dhcp binding {}",
					requestIP, new Object[]{clientMac, dhcpLeasingPool.get(clientMac).getIPv4Address()});
			return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());
		}


		if (isIPAvailableInRepo(requestIP)) {
			returnDHCPBindingtoRepository(dhcpLeasingPool.get(clientMac));
			DHCPBinding lease = createRequestLeaseForClient(clientMac, requestIP, timeSec);
			log.info("Found Client! Required IP address {} available in pool. Updating lease dhcp binding {}",
					requestIP, new Object[]{clientMac, lease.getIPv4Address()});
			return Optional.of(lease.getIPv4Address());
		}

		if (!dynamicLease && !isIPAvailableInRepo(requestIP) && dhcpLeasingPool.get(clientMac).getIPv4Address().equals(requestIP)) {
			DHCPBinding lease = dhcpLeasingPool.get(clientMac);
			log.info("Found Client! Required IP address {} is same as dhcp pool record, still assign same dhcp binding{} to client",
					requestIP, new Object[]{clientMac, lease.getIPv4Address()});
			return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());
		}
		else if (dynamicLease && !isIPAvailableInRepo(requestIP) && dhcpLeasingPool.get(clientMac).getIPv4Address().equals(requestIP)) {
			DHCPBinding lease = dhcpLeasingPool.get(clientMac);
			returnDHCPBindingtoRepository(dhcpLeasingPool.get(clientMac));

			IPv4Address leaseIP = createDynamicLeaseForClient(clientMac, requestIP, timeSec).getIPv4Address();

			log.info("Found Client! Dynamic lease Enabled! Assign a different dhcp binding{} to client",
					leaseIP, new Object[]{clientMac, lease.getIPv4Address()});

			return Optional.of(leaseIP);
		}

		log.info("Found client! Request IP address {} is not a valid IP address in this DHCP pool. " +
						"Assigning a valid dhcp binding {} instead",
				requestIP, new Object[]{clientMac, dhcpLeasingPool.get(clientMac).getIPv4Address()});
		return Optional.of(dhcpLeasingPool.get(clientMac).getIPv4Address());

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

	public boolean renewLeaseOfMAC(@Nonnull MacAddress clientMac, long timeSec) {
		if (!isClientRegistered(clientMac)) {
			log.info("MAC {} not registered yet, can't renew lease", clientMac);
			return false;
		}

		DHCPBinding lease = dhcpLeasingPool.get(clientMac);
		if (lease.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
			log.info("No need to renew lease on MAC address {}, it is configured as a permanent dhcp lease", clientMac);
			return false;
		}

		lease.renewLease(timeSec);
		log.info("Renew lease on MAC {}", clientMac);
		return true;

	}

	public boolean renewLeaseOfIP(@Nonnull IPv4Address ip, long timeSec) {
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

		lease.get().renewLease(timeSec);
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

	@Override
	public void checkExpiredLeases() {
		for(DHCPBinding binding : dhcpLeasingPool.values()) {
			if (binding.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) continue;
			if (binding.getCurrLeaseState() == LeasingState.LEASED) {
				binding.isBindingTimeout();
			}
		}
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
