package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.floodlightcontroller.core.types.NodePortTuple;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DHCPInstance {
	private static final Logger log = LoggerFactory.getLogger(DHCPInstance.class);
	
	private String name = null;
	private volatile DHCPPool pool = null;
	private Set<NodePortTuple> memberPorts = null;
	private Set<VlanVid> memberVlans = null;
	
	private IPv4Address serverIp = IPv4Address.NONE;
	private MacAddress serverMac = MacAddress.NONE;
	private IPv4Address subnetMask = IPv4Address.NONE;
	private IPv4Address broadcastIp = IPv4Address.NONE;
	private IPv4Address routerIp = IPv4Address.NONE;
	private List<IPv4Address> ntpIps = null;
	private List<IPv4Address> dnsIps = null;
	
	private String domainName = null;
	private boolean ipForwarding = false;
	
	private int leaseTimeSec = 0;
	public static final int HOLD_LEASE_TIME_SEC = 10;
	
	/* Computed */
	private int rebindTimeSec = 0;
	private int renewalTimeSec = 0;
	
	private DHCPInstance() {			
		ipForwarding = false;
	}
	
	public static DHCPInstanceBuilder createBuilder() {
		return new DHCPInstanceBuilder();
	}
	
	public String getName() {
		return name;
	}
	public IPv4Address getServerIp() {
		return serverIp;
	}
	public MacAddress getServerMac() {
		return serverMac;
	}
	public IPv4Address getSubnetMask() {
		return subnetMask;
	}
	public IPv4Address getBroadcastIp() {
		return broadcastIp;
	}
	public IPv4Address getRouterIp() {
		return routerIp;
	}
	public List<IPv4Address> getNtpIps() {
		return Collections.unmodifiableList(ntpIps);
	}
	public List<IPv4Address> getDnsIps() {
		return Collections.unmodifiableList(dnsIps);
	}
	public String getDomainName() {
		return domainName;
	}
	public boolean getIpForwarding() {
		return ipForwarding;
	}
	public int getLeaseTimeSec() {
		return leaseTimeSec;
	}
	public int getRenewalTimeSec() {
		return renewalTimeSec;
	}
	public int getRebindTimeSec() {
		return rebindTimeSec;
	}
	public int getHoldTimeSec() {
		return HOLD_LEASE_TIME_SEC;
	}
	public DHCPPool getPool() {
		return pool;
	}
	public Set<NodePortTuple> getMemberPorts() {
		return Collections.unmodifiableSet(memberPorts);
	}
	public Set<VlanVid> getMemberVlans() {
		return Collections.unmodifiableSet(memberVlans);
	}
	
	public static class DHCPInstanceBuilder {
		private DHCPInstance tempInstance = null;
		private Map<MacAddress, IPv4Address> tempStaticAddresses = null;
		private IPv4Address tempStart = IPv4Address.NONE;
		private IPv4Address tempStop = IPv4Address.NONE;
		
		private DHCPInstanceBuilder() {
			tempInstance = new DHCPInstance();
			tempStaticAddresses = new HashMap<MacAddress, IPv4Address>();
		}
		
		public DHCPInstanceBuilder setName(String name) {
			if (name == null || name.isEmpty()) {
				throw new IllegalArgumentException("DHCP server name cannot be null or empty");
			}
			tempInstance.name = name;
			return this;
		}
		public DHCPInstanceBuilder setServer(MacAddress mac, IPv4Address ip) {
			if (ip == null || mac == null) {
				throw new IllegalArgumentException("DHCP server IP/Mac cannot be null");
			}
			tempInstance.serverIp = ip;
			tempInstance.serverMac = mac;
			return this;
		}
		public DHCPInstanceBuilder setSubnetMask(IPv4Address mask) {
			if (mask == null) {
				throw new IllegalArgumentException("DHCP server subnet mask cannot be null");
			}
			tempInstance.subnetMask = mask;
			return this;
		}
		public DHCPInstanceBuilder setBroadcastIp(IPv4Address bcast) {
			if (bcast == null) {
				throw new IllegalArgumentException("DHCP server broadcast IP cannot be null");
			}
			tempInstance.broadcastIp = bcast;
			return this;
		}
		public DHCPInstanceBuilder setStartIp(IPv4Address start) {
			if (start == null) {
				throw new IllegalArgumentException("DHCP pool start IP cannot be null");
			}
			tempStart = start;
			return this;
		}
		public DHCPInstanceBuilder setStopIp(IPv4Address stop) {
			if (stop == null) {
				throw new IllegalArgumentException("DHCP pool stop IP cannot be null");
			}
			tempStop = stop;
			return this;
		}
		public DHCPInstanceBuilder setRouterIp(IPv4Address router) {
			if (router == null) {
				throw new IllegalArgumentException("DHCP server router/gateway IP cannot be null");
			}
			tempInstance.routerIp = router;
			return this;
		}
		public DHCPInstanceBuilder setNtpIps(List<IPv4Address> ntps) {
			if (ntps == null) {
				throw new IllegalArgumentException("DHCP server NTP IP list cannot be null");
			}
			tempInstance.ntpIps = ntps;
			return this;
		}
		public DHCPInstanceBuilder setDnsIps(List<IPv4Address> dnss) {
			if (dnss == null) {
				throw new IllegalArgumentException("DHCP server DNS IP list cannot be null");
			}
			tempInstance.dnsIps = dnss;
			return this;
		}
		public DHCPInstanceBuilder setDomainName(String dn) {
			if (dn == null) {
				throw new IllegalArgumentException("DHCP server domain name cannot be null");
			}
			tempInstance.domainName = dn;
			return this;
		}
		public DHCPInstanceBuilder setIpForwarding(boolean on) {
			tempInstance.ipForwarding = on;
			return this;
		}
		public DHCPInstanceBuilder setLeaseTimeSec(int s) {
			if (s <= 0) {
				throw new IllegalArgumentException("DHCP server lease time must be > 0");
			}
			if (s < 3600) {
				log.warn("DHCP server lease time set less than one hour at {} seconds", s);
			}
			tempInstance.leaseTimeSec = s;
			return this;
		}
		public DHCPInstanceBuilder addReservedStaticAddress(MacAddress rsvMac, IPv4Address rsvIp) {
			tempStaticAddresses.put(rsvMac, rsvIp);
			return this;
		}
		public DHCPInstanceBuilder setMemberPorts(Set<NodePortTuple> ports) {
			if (ports == null) {
				throw new IllegalArgumentException("DHCP server member ports cannot be null");
			}
			tempInstance.memberPorts = ports;
			return this;
		}
		public DHCPInstanceBuilder setMemberVlans(Set<VlanVid> vlans) {
			if (vlans == null) {
				throw new IllegalArgumentException("DHCP server member VLANs cannot be null");
			}
			tempInstance.memberVlans = vlans;
			return this;
		}
		
		public DHCPInstance build() {
			DHCPInstance instanceToReturn = new DHCPInstance();

			if (tempStart.compareTo(tempStop) > 0) {
				throw new IllegalArgumentException("Start IP must be less than stop IP");
			}
			instanceToReturn.pool = new DHCPPool(tempStart, tempStop.getInt() - tempStart.getInt() + 1);
			for (Entry<MacAddress, IPv4Address> entry : tempStaticAddresses.entrySet()) {
				instanceToReturn.pool.configureFixedIPLease(entry.getValue(), entry.getKey());
			}
			
			if (tempInstance.leaseTimeSec <= 0) {
				throw new IllegalArgumentException("DHCP server lease time must be > 0");
			}
			instanceToReturn.leaseTimeSec = tempInstance.leaseTimeSec;
			instanceToReturn.rebindTimeSec = (int) (instanceToReturn.leaseTimeSec / 0.875);
			instanceToReturn.renewalTimeSec = instanceToReturn.leaseTimeSec / 2;
			
			// TODO check to make sure these are in the same subnet
			instanceToReturn.broadcastIp = tempInstance.broadcastIp; /* IPv4/MacAddress are immutable */
			instanceToReturn.serverMac = tempInstance.serverMac;
			instanceToReturn.serverIp = tempInstance.serverIp;
			instanceToReturn.routerIp = tempInstance.routerIp;
			instanceToReturn.subnetMask = tempInstance.subnetMask;
			
			instanceToReturn.domainName = tempInstance.domainName;
			
			if (tempInstance.dnsIps == null) {
				instanceToReturn.dnsIps = new ArrayList<IPv4Address>();
			} else {
				instanceToReturn.dnsIps = new ArrayList<IPv4Address>(tempInstance.dnsIps);
			}
			if (tempInstance.ntpIps == null) {
				instanceToReturn.ntpIps = new ArrayList<IPv4Address>();
			} else {
				instanceToReturn.ntpIps = new ArrayList<IPv4Address>(tempInstance.ntpIps);
			}
			if (tempInstance.memberPorts == null) {
				instanceToReturn.memberPorts = new HashSet<NodePortTuple>();
			} else {
				instanceToReturn.memberPorts = new HashSet<NodePortTuple>(tempInstance.memberPorts);
			}
			if (tempInstance.memberVlans == null) {
				instanceToReturn.memberVlans = new HashSet<VlanVid>();
			} else {
				instanceToReturn.memberVlans = new HashSet<VlanVid>(tempInstance.memberVlans);
			}
			
			instanceToReturn.ipForwarding = tempInstance.ipForwarding;
			
			return instanceToReturn;
		}	
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((broadcastIp == null) ? 0 : broadcastIp.hashCode());
		result = prime * result + ((dnsIps == null) ? 0 : dnsIps.hashCode());
		result = prime * result
				+ ((domainName == null) ? 0 : domainName.hashCode());
		result = prime * result + (ipForwarding ? 1231 : 1237);
		result = prime * result + leaseTimeSec;
		result = prime * result
				+ ((memberPorts == null) ? 0 : memberPorts.hashCode());
		result = prime * result
				+ ((memberVlans == null) ? 0 : memberVlans.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((ntpIps == null) ? 0 : ntpIps.hashCode());
		result = prime * result + ((pool == null) ? 0 : pool.hashCode());
		result = prime * result + rebindTimeSec;
		result = prime * result + renewalTimeSec;
		result = prime * result
				+ ((routerIp == null) ? 0 : routerIp.hashCode());
		result = prime * result
				+ ((serverIp == null) ? 0 : serverIp.hashCode());
		result = prime * result
				+ ((serverMac == null) ? 0 : serverMac.hashCode());
		result = prime * result
				+ ((subnetMask == null) ? 0 : subnetMask.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DHCPInstance other = (DHCPInstance) obj;
		if (broadcastIp == null) {
			if (other.broadcastIp != null)
				return false;
		} else if (!broadcastIp.equals(other.broadcastIp))
			return false;
		if (dnsIps == null) {
			if (other.dnsIps != null)
				return false;
		} else if (!dnsIps.equals(other.dnsIps))
			return false;
		if (domainName == null) {
			if (other.domainName != null)
				return false;
		} else if (!domainName.equals(other.domainName))
			return false;
		if (ipForwarding != other.ipForwarding)
			return false;
		if (leaseTimeSec != other.leaseTimeSec)
			return false;
		if (memberPorts == null) {
			if (other.memberPorts != null)
				return false;
		} else if (!memberPorts.equals(other.memberPorts))
			return false;
		if (memberVlans == null) {
			if (other.memberVlans != null)
				return false;
		} else if (!memberVlans.equals(other.memberVlans))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (ntpIps == null) {
			if (other.ntpIps != null)
				return false;
		} else if (!ntpIps.equals(other.ntpIps))
			return false;
		if (pool == null) {
			if (other.pool != null)
				return false;
		} else if (!pool.equals(other.pool))
			return false;
		if (rebindTimeSec != other.rebindTimeSec)
			return false;
		if (renewalTimeSec != other.renewalTimeSec)
			return false;
		if (routerIp == null) {
			if (other.routerIp != null)
				return false;
		} else if (!routerIp.equals(other.routerIp))
			return false;
		if (serverIp == null) {
			if (other.serverIp != null)
				return false;
		} else if (!serverIp.equals(other.serverIp))
			return false;
		if (serverMac == null) {
			if (other.serverMac != null)
				return false;
		} else if (!serverMac.equals(other.serverMac))
			return false;
		if (subnetMask == null) {
			if (other.subnetMask != null)
				return false;
		} else if (!subnetMask.equals(other.subnetMask))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DHCPInstance [name=" + name + ", pool=" + pool
				+ ", memberPorts=" + memberPorts + ", memberVlans="
				+ memberVlans + ", serverIp=" + serverIp + ", serverMac="
				+ serverMac + ", subnetMask=" + subnetMask + ", broadcastIp="
				+ broadcastIp + ", routerIp=" + routerIp + ", ntpIps=" + ntpIps
				+ ", dnsIps=" + dnsIps + ", domainName=" + domainName
				+ ", ipForwarding=" + ipForwarding + ", leaseTimeSec="
				+ leaseTimeSec + ", rebindTimeSec=" + rebindTimeSec
				+ ", renewalTimeSec=" + renewalTimeSec + "]";
	}
}