package net.floodlightcontroller.dhcpserver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.dhcpserver.web.DHCPInstanceSerializer;
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
@JsonSerialize(using = DHCPInstanceSerializer.class)
@JsonDeserialize(builder = DHCPInstance.DHCPInstanceBuilder.class)
public class DHCPInstance {

	protected static final Logger log = LoggerFactory.getLogger(DHCPInstance.class);

	private final String name;
	private volatile DHCPPool dhcpPool = null;
	private volatile DHCPInstanceBuilder builder = null;

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

	private Map<MacAddress, IPv4Address> staticAddresseses = null;
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

	public void addNptMember(NodePortTuple npt) {
		this.nptMembers.add(npt);
	}

	public DHCPInstanceBuilder getBuilder() {return builder;}

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

		this.builder = builder;
	}

	public static DHCPInstanceBuilder createInstance(@Nonnull final String name){
		if(name.isEmpty()){
			throw new IllegalArgumentException("Build DHCP instance failed : DHCP server name can not be empty");
		}

		return new DHCPInstanceBuilder(name);
	}

	@JsonPOJOBuilder(buildMethodName = "build", withPrefix = "set")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DHCPInstanceBuilder {
		private final String name;
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

		public DHCPInstanceBuilder(final String name) { this.name = name;}

		// Only used for create DHCP instance from REST API
		@JsonCreator
		@JsonIgnoreProperties(ignoreUnknown = true)
		private DHCPInstanceBuilder(@JsonProperty("name") String name,
									@JsonProperty("server-id") String serverID,
									@JsonProperty("server-mac") String serverMac,
									@JsonProperty("broadcast-ip") String broadcastIP,
									@JsonProperty("router-ip") String routerIP,
									@JsonProperty("subnet-mask") String subnetMask,
									@JsonProperty("start-ip") String startIP,
									@JsonProperty("end-ip") String endIP,
									@JsonProperty("lease-time") String leaseTime,
									@JsonProperty("rebind-time") String rebindTime,
									@JsonProperty("renew-time") String renewTime,
									@JsonProperty("ip-forwarding") String ipForwarding,
									@JsonProperty("domain-name") String domainName) {
			this.name = name;
			this.serverID = IPv4Address.of(serverID);
			this.serverMac = MacAddress.of(serverMac);
			this.broadcastIP = IPv4Address.of(broadcastIP);
			this.routerIP = IPv4Address.of(routerIP);
			this.subnetMask = IPv4Address.of(subnetMask);
			this.startIPAddress = IPv4Address.of(startIP);
			this.endIPAddress = IPv4Address.of(endIP);
			this.leaseTimeSec = Integer.parseInt(leaseTime);
			this.rebindTimeSec = (int)(Integer.parseInt(rebindTime) * 0.875);
			this.renewalTimeSec = (int)(Integer.parseInt(renewTime) * 0.5);
			this.ipforwarding = Boolean.valueOf(ipForwarding);
			this.domainName = domainName;

			this.dhcpPool = new DHCPPool(startIPAddress, endIPAddress.getInt() - startIPAddress.getInt() + 1);
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
			this.dhcpPool = new DHCPPool(startIPAddress, endIPAddress.getInt() - startIPAddress.getInt() + 1);

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
				// Setup permanent DHCP binding and remove invalid entry
				for (Map.Entry<MacAddress, IPv4Address> entry : this.staticAddresseses.entrySet()) {
					if (!this.dhcpPool.assignPermanentLeaseToClientWithRequestIP(entry.getValue(), entry.getKey()).isPresent()) {
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

		return name != null ? name.equals(instance.name) : instance.name == null;
	}

	@Override
	public int hashCode() {
		return name != null ? name.hashCode() : 0;
	}

}