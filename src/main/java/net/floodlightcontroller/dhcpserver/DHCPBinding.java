package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 * The class representing a DHCP Binding -- MAC and IP. It contains important lease information regarding DHCP binding
 *
 * Lease status of a DHCP binding
 * -- active
 * -- inactive
 *
 * Lease type of a DHCP binding
 * -- dynamic
 * -- permanent/static
 *
 * Lease times of a DHCP binding
 * -- start time (seconds)
 * -- duration time (seconds)
 * 
 * @author Ryan Izard (rizard@g.clemson.edu)
 *
 */

public class DHCPBinding {
	private MacAddress mac = MacAddress.NONE;
	private IPv4Address ip = IPv4Address.NONE;
	private boolean LEASE_STATUS;
	private boolean PERMANENT_LEASE;
	
	private long LEASE_START_TIME_SECONDS;
	private long LEASE_DURATION_SECONDS;
	
	protected DHCPBinding(IPv4Address ip, MacAddress mac) {
		this.setMACAddress(mac);
		this.setIPv4Addresss(ip);
		this.setLeaseStatus(false);
	}
	
	public IPv4Address getIPv4Address() {
		return ip;
	}
	
	public MacAddress getMACAddress() {
		return mac;
	}
	
	private void setIPv4Addresss(IPv4Address ip) {
		this.ip = ip;
	}
	
	public void setMACAddress(MacAddress mac) {
		this.mac = mac;
	}
	
	public void setPermanentLease(boolean staticIP) {
		PERMANENT_LEASE = staticIP;
	}
	
	public boolean isPermanentIPLease() {
		return PERMANENT_LEASE;
	}
	
	public void setLeaseStatus(boolean status) {
		LEASE_STATUS = status;
	}
	
	public boolean isLeaseExpired() {
		long currentTime = System.currentTimeMillis();
		return ((currentTime / 1000) >= (LEASE_START_TIME_SECONDS + LEASE_DURATION_SECONDS));
	}

	public boolean isLeaseAvailable() {
		// If lease status is false, that indicates this lease is available
		return LEASE_STATUS == false;
	}
	
	protected void setLeaseStartTimeSeconds() {
		LEASE_START_TIME_SECONDS = System.currentTimeMillis() / 1000;
	}
	
	protected void setLeaseDurationSeconds(long time) {
		LEASE_DURATION_SECONDS = time;
	}
	
	protected void clearLeaseTimes() {
		LEASE_START_TIME_SECONDS = 0;
		LEASE_DURATION_SECONDS = 0;
	}
	
	protected boolean cancelLease() {
		this.clearLeaseTimes();
		this.setLeaseStatus(false);
		return true;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime
				* result
				+ (int) (LEASE_DURATION_SECONDS ^ (LEASE_DURATION_SECONDS >>> 32));
		result = prime
				* result
				+ (int) (LEASE_START_TIME_SECONDS ^ (LEASE_START_TIME_SECONDS >>> 32));
		result = prime * result + (LEASE_STATUS ? 1231 : 1237);
		result = prime * result + ((mac == null) ? 0 : mac.hashCode());
		result = prime * result + (PERMANENT_LEASE ? 1231 : 1237);
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
		DHCPBinding other = (DHCPBinding) obj;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		if (LEASE_DURATION_SECONDS != other.LEASE_DURATION_SECONDS)
			return false;
		if (LEASE_START_TIME_SECONDS != other.LEASE_START_TIME_SECONDS)
			return false;
		if (LEASE_STATUS != other.LEASE_STATUS)
			return false;
		if (mac == null) {
			if (other.mac != null)
				return false;
		} else if (!mac.equals(other.mac))
			return false;
		if (PERMANENT_LEASE != other.PERMANENT_LEASE)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DHCPBinding{" +
				"MAC=" + mac +
				", IP=" + ip +
				", LEASE_STATUS=" + LEASE_STATUS +
				", PERMANENT_LEASE=" + PERMANENT_LEASE +
				", LEASE_START_TIME_SECONDS=" + LEASE_START_TIME_SECONDS +
				", LEASE_DURATION_SECONDS=" + LEASE_DURATION_SECONDS +
				'}';
	}

}