package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class representing a DHCP instance. One DHCP instance is responsible for managing one subnet.
 * One DHCP instance contains a DHCP pool.
 *
 * @author Ryan Izard (rizard@g.clemson.edu)
 * @edited Qing Wang (qw@g.clemson.edu) on 1/3/2018.
 *
 */
public class DHCPInstance {

	protected static final Logger log = LoggerFactory.getLogger(DHCPInstance.class);

	private String name = null;
	private volatile DHCPPool dhcpPool = null;

	private IPv4Address serverID = IPv4Address.NONE;
	private MacAddress serverMac = MacAddress.NONE;
	private IPv4Address broadcastIP = IPv4Address.NONE;
	private IPv4Address routerIP = IPv4Address.NONE;
	private IPv4Address subnetMask = IPv4Address.NONE;
	private IPv4Address startIPAddress = IPv4Address.NONE;
	private IPv4Address endIPAddress = IPv4Address.NONE;
	private int leaseTimeSec = 0;
	private int rebindTimeSec = 0;
	private int renewalTimeSec = 0;

	private List<IPv4Address> dnsServers = null;
	private List<IPv4Address> ntpServers = null;
	private boolean ipforwarding = false;
	private String domainName = null;

	private Map<MacAddress, IPv4Address> staticAddresseses;
	private Set<MacAddress> clientMembers = null;
	private Set<VlanVid> vlanMembers = null;
	private Set<NodePortTuple> nptMembers = null;

	public String getName() { return name; }
	public DHCPPool getDHCPPool() { return dhcpPool; }
	public IPv4Address getServerID() { return serverID; }
	public MacAddress getServerMac() { return serverMac; }
	public IPv4Address getBroadcastIP() { return broadcastIP; }
	public IPv4Address getRouterIP() { return routerIP; }
	public IPv4Address getSubnetMask() { return subnetMask; }
	public IPv4Address getStartIPAddress() { return startIPAddress; }
	public IPv4Address getEndIPAddress() { return endIPAddress; }
	public int getLeaseTimeSec() { return leaseTimeSec; }
	public int getRebindTimeSec() { return rebindTimeSec; }
	public int getRenewalTimeSec() { return renewalTimeSec; }

	public List<IPv4Address> getDNSServers() { return dnsServers; }
	public List<IPv4Address> getNtpServers() { return ntpServers; }
	public boolean getIpforwarding() { return ipforwarding; }
	public String getDomainName() { return domainName; }

	public Map<MacAddress, IPv4Address> getStaticAddresseses() { return staticAddresseses; };
	public Set<NodePortTuple> getNptMembers() { return nptMembers; }
	public Set<VlanVid> getVlanMembers() { return vlanMembers; }
	public Set<MacAddress> getClientMembers() { return clientMembers; }


	private DHCPInstance(DHCPInstanceBuilder builder) {
		this.name = builder.name;
		this.dhcpPool = builder.dhcpPool;
		this.serverID = builder.serverID;
		this.serverMac = builder.serverMac;
		this.broadcastIP = builder.broadcastIP;
		this.routerIP = builder.routerIP;
		this.subnetMask = builder.subnetMask;
		this.startIPAddress = builder.startIPAddress;
		this.endIPAddress = builder.endIPAddress;
		this.leaseTimeSec = builder.leaseTimeSec;
		this.rebindTimeSec = builder.rebindTimeSec;
		this.renewalTimeSec = builder.renewalTimeSec;

		this.dnsServers = builder.dnsServers;
		this.ntpServers = builder.ntpServers;
		this.ipforwarding = builder.ipforwarding;
		this.domainName = builder.domainName;

		this.staticAddresseses = builder.staticAddresseses;
		this.vlanMembers = builder.vlanMembers;
		this.nptMembers = builder.nptMembers;
		this.clientMembers = builder.clientMembers;
	}

	public static DHCPInstanceBuilder createBuilder(){
		return new DHCPInstanceBuilder();
	}

	public static class DHCPInstanceBuilder {
		private String name;
		private DHCPPool dhcpPool;
		private IPv4Address serverID;
		private MacAddress serverMac;
		private IPv4Address broadcastIP;
		private IPv4Address routerIP;
		private IPv4Address subnetMask;
		private IPv4Address startIPAddress;
		private IPv4Address endIPAddress;
		private int leaseTimeSec;
		private int rebindTimeSec;
		private int renewalTimeSec;

		private List<IPv4Address> dnsServers;
		private List<IPv4Address> ntpServers;
		private boolean ipforwarding;
		private String domainName;

		private Map<MacAddress, IPv4Address> staticAddresseses = new ConcurrentHashMap<>();
		private Set<MacAddress> clientMembers;
		private Set<VlanVid> vlanMembers;
		private Set<NodePortTuple> nptMembers;

		public DHCPInstanceBuilder setName(@Nonnull String name) {
			if(name.isEmpty()){
				throw new IllegalArgumentException("Build DHCP instance failed : DHCP server name can not be empty");
			}
			this.name = name;
			return this;
		}

		public DHCPInstanceBuilder setServerID(@Nonnull IPv4Address serverID) {
			if(serverID == IPv4Address.NONE){
				throw new IllegalArgumentException("Build DHCP instance failed : DHCP server IP address can not be empty");
			}
			this.serverID = serverID;
			return this;
		}

		public DHCPInstanceBuilder setServerMac(@Nonnull MacAddress serverMac) {
			if(serverMac == MacAddress.NONE){
				throw new IllegalArgumentException("Build DHCP instance failed : DHCP server Mac address can not be empty");
			}
			this.serverMac = serverMac;
			return this;
		}

		public DHCPInstanceBuilder setBroadcastIP(@Nonnull IPv4Address broadcastIP) {
			if(broadcastIP == IPv4Address.NONE){
				throw new IllegalArgumentException("Build DHCP instance failed : Broadcast IP address can not be empty");
			}
			this.broadcastIP = broadcastIP;
			return this;
		}

		public DHCPInstanceBuilder setRouterIP(@Nonnull IPv4Address routerIP) {
			this.routerIP = routerIP;
			return this;
		}

		public DHCPInstanceBuilder setSubnetMask(@Nonnull IPv4Address subnetMask) {
			this.subnetMask = subnetMask;
			return this;
		}

		public DHCPInstanceBuilder setStartIP(@Nonnull IPv4Address start) {
			if(start == IPv4Address.NONE){
				throw new IllegalArgumentException("Build DHCP instance failed : DHCP Pool Starter IP address can not be empty");
			}
			this.startIPAddress = start;
			return this;
		}

		public DHCPInstanceBuilder setEndIP(@Nonnull IPv4Address end) {
			if(end == IPv4Address.NONE){
				throw  new IllegalArgumentException("Build DHCP instance failed : DHCP Pool Stopper IP address can not be empty");
			}
			this.endIPAddress = end;
			return this;
		}

		public DHCPInstanceBuilder setLeaseTimeSec(int timeSec) {
			if(timeSec < 0){
				throw  new IllegalArgumentException("Build DHCP instance failed : DHCP server lease time can not be less than 0");
			}
			this.leaseTimeSec = timeSec;
			return this;
		}

		public DHCPInstanceBuilder setDNSServers(@Nonnull List<IPv4Address> dnsServers) {
			this.dnsServers = dnsServers;
			return this;
		}

		public DHCPInstanceBuilder setNTPServers(@Nonnull List<IPv4Address> ntpServers) {
			this.ntpServers = ntpServers;
			return this;
		}

		public DHCPInstanceBuilder setIPforwarding(boolean ipforwarding) {
			this.ipforwarding = ipforwarding;
			return this;
		}

		public DHCPInstanceBuilder setDomainName(@Nonnull String name) {
			if(name.isEmpty()){
				throw  new IllegalArgumentException("Build DHCP instance failed : DHCP Server Domain Name can not be empty");
			}
			this.domainName = name;
			return this;
		}

		public DHCPInstanceBuilder setStaticAddresses(@Nonnull MacAddress mac, @Nonnull IPv4Address ip) {
			if(mac == MacAddress.NONE || ip == IPv4Address.NONE){
				throw new IllegalArgumentException("BUild DHCP instance faild : DHCP static address can not be empty");
			}
			// map structure naturally exclude same mac has multiple IP address entry
			this.staticAddresseses.put(mac, ip);
			return this;
		}

		public DHCPInstanceBuilder setVlanMembers(@Nonnull Set<VlanVid> vlanMembers) {
			this.vlanMembers = vlanMembers;
			return this;
		}

		public DHCPInstanceBuilder setNptMembers(@Nonnull Set<NodePortTuple> nptMembers) {
			this.nptMembers = nptMembers;
			return this;
		}

		public DHCPInstanceBuilder setClientMembers(@Nonnull Set<MacAddress> clientMembers) {
			this.clientMembers = clientMembers;
			return this;
		}

		public DHCPInstance build() {
			if (startIPAddress == null || endIPAddress == null) {
				throw new IllegalArgumentException("Build DHCP instance failed : starter IP address and end IP address can not be null");
			}

			if (startIPAddress.compareTo(endIPAddress) >= 0) {
				throw new IllegalArgumentException("Build DHCP instance failed : Starter IP must be less than Stopper IP in order to create a DHCP pool");
			}

			if (name == null) {
				throw new IllegalArgumentException("Build DHCP instance failed : DHCP instance name can not be null");
			}

			this.rebindTimeSec = (int)(leaseTimeSec * 0.875);
			this.renewalTimeSec = (int)(leaseTimeSec * 0.5);
			this.dhcpPool = new DHCPPool(startIPAddress, endIPAddress.getInt() - startIPAddress.getInt()+1);

			// fill in missing optional config parameters to empty instead of null
			if (this.dnsServers == null) {
				this.dnsServers = new ArrayList<>();
			}
			if (this.ntpServers == null) {
				this.ntpServers = new ArrayList<>();
			}
			if (this.clientMembers == null) {
				this.clientMembers = new HashSet<>();
			}
			if (this.vlanMembers == null) {
				this.vlanMembers = new HashSet<>();
			}
			if (this.nptMembers == null) {
				this.nptMembers = new HashSet<>();
			}
			if (this.staticAddresseses != null) {
				// Remove invalid entry
				for (Map.Entry<MacAddress, IPv4Address> entry : this.staticAddresseses.entrySet()) {
					if (!this.dhcpPool.assignPermanentLeaseToClient(entry.getValue(), entry.getKey()).isPresent()) {
						staticAddresseses.remove(entry.getKey());
					}
				}
			}

			return new DHCPInstance(this);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DHCPInstance instance = (DHCPInstance) o;

		if (leaseTimeSec != instance.leaseTimeSec) return false;
		if (rebindTimeSec != instance.rebindTimeSec) return false;
		if (renewalTimeSec != instance.renewalTimeSec) return false;
		if (ipforwarding != instance.ipforwarding) return false;
		if (name != null ? !name.equals(instance.name) : instance.name != null) return false;
		if (dhcpPool != null ? !dhcpPool.equals(instance.dhcpPool) : instance.dhcpPool != null) return false;
		if (serverID != null ? !serverID.equals(instance.serverID) : instance.serverID != null) return false;
		if (serverMac != null ? !serverMac.equals(instance.serverMac) : instance.serverMac != null) return false;
		if (broadcastIP != null ? !broadcastIP.equals(instance.broadcastIP) : instance.broadcastIP != null)
			return false;
		if (routerIP != null ? !routerIP.equals(instance.routerIP) : instance.routerIP != null) return false;
		if (subnetMask != null ? !subnetMask.equals(instance.subnetMask) : instance.subnetMask != null) return false;
		if (startIPAddress != null ? !startIPAddress.equals(instance.startIPAddress) : instance.startIPAddress != null)
			return false;
		if (endIPAddress != null ? !endIPAddress.equals(instance.endIPAddress) : instance.endIPAddress != null)
			return false;
		if (dnsServers != null ? !dnsServers.equals(instance.dnsServers) : instance.dnsServers != null) return false;
		if (ntpServers != null ? !ntpServers.equals(instance.ntpServers) : instance.ntpServers != null) return false;
		if (domainName != null ? !domainName.equals(instance.domainName) : instance.domainName != null) return false;
		if (staticAddresseses != null ? !staticAddresseses.equals(instance.staticAddresseses) : instance.staticAddresseses != null)
			return false;
		if (clientMembers != null ? !clientMembers.equals(instance.clientMembers) : instance.clientMembers != null)
			return false;
		if (vlanMembers != null ? !vlanMembers.equals(instance.vlanMembers) : instance.vlanMembers != null)
			return false;
		return nptMembers != null ? nptMembers.equals(instance.nptMembers) : instance.nptMembers == null;
	}

	@Override
	public int hashCode() {
		int result = name != null ? name.hashCode() : 0;
		result = 31 * result + (dhcpPool != null ? dhcpPool.hashCode() : 0);
		result = 31 * result + (serverID != null ? serverID.hashCode() : 0);
		result = 31 * result + (serverMac != null ? serverMac.hashCode() : 0);
		result = 31 * result + (broadcastIP != null ? broadcastIP.hashCode() : 0);
		result = 31 * result + (routerIP != null ? routerIP.hashCode() : 0);
		result = 31 * result + (subnetMask != null ? subnetMask.hashCode() : 0);
		result = 31 * result + (startIPAddress != null ? startIPAddress.hashCode() : 0);
		result = 31 * result + (endIPAddress != null ? endIPAddress.hashCode() : 0);
		result = 31 * result + leaseTimeSec;
		result = 31 * result + rebindTimeSec;
		result = 31 * result + renewalTimeSec;
		result = 31 * result + (dnsServers != null ? dnsServers.hashCode() : 0);
		result = 31 * result + (ntpServers != null ? ntpServers.hashCode() : 0);
		result = 31 * result + (ipforwarding ? 1 : 0);
		result = 31 * result + (domainName != null ? domainName.hashCode() : 0);
		result = 31 * result + (staticAddresseses != null ? staticAddresseses.hashCode() : 0);
		result = 31 * result + (clientMembers != null ? clientMembers.hashCode() : 0);
		result = 31 * result + (vlanMembers != null ? vlanMembers.hashCode() : 0);
		result = 31 * result + (nptMembers != null ? nptMembers.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "DHCPInstance{" +
				"name='" + name + '\'' +
				", dhcpPool=" + dhcpPool +
				", serverID=" + serverID +
				", serverMac=" + serverMac +
				", broadcastIP=" + broadcastIP +
				", routerIP=" + routerIP +
				", subnetMask=" + subnetMask +
				", startIPAddress=" + startIPAddress +
				", endIPAddress=" + endIPAddress +
				", leaseTimeSec=" + leaseTimeSec +
				", rebindTimeSec=" + rebindTimeSec +
				", renewalTimeSec=" + renewalTimeSec +
				", dnsServers=" + dnsServers +
				", ntpServers=" + ntpServers +
				", ipforwarding=" + ipforwarding +
				", domainName='" + domainName + '\'' +
				", staticAddresseses=" + staticAddresseses +
				", clientMembers=" + clientMembers +
				", vlanMembers=" + vlanMembers +
				", nptMembers=" + nptMembers +
				'}';
	}
}