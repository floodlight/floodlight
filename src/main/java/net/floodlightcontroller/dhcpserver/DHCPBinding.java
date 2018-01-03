package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class representing a DHCP Binding -- MAC and IP.
 * It contains important lease information regarding DHCP binding
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
 * @edited Qing Wang (qw@g.clemson.edu)
 *
 */

public class DHCPBinding {
	protected static final Logger log = LoggerFactory.getLogger(DHCPBinding.class);
	private MacAddress mac = MacAddress.NONE;
	private IPv4Address ip = IPv4Address.NONE;
	private LeasingState currentState;

	private long startTimeSec;
	private long durationTimeSec;
	
	protected DHCPBinding(IPv4Address ip, MacAddress mac) {
		this.setMACAddress(mac);
		this.setIPv4Address(ip);
		this.currentState = LeasingState.AVAILABLE;
	}

	public IPv4Address getIPv4Address() {
		return ip;
	}
	
	public MacAddress getMACAddress() {
		return mac;
	}

	public LeasingState getCurrLeaseState() {
		return this.currentState;
	}

	public void configurePermanentLease(@Nonnull MacAddress mac) {
		setMACAddress(mac);
		currentState = LeasingState.PERMANENT_LEASED;
	}

	public void configureNormalLease(@Nonnull MacAddress mac, long durationTimeSec) {
		setMACAddress(mac);
		currentState = LeasingState.LEASED;
		setLeaseDuration(durationTimeSec);
	}

	public boolean checkForTimeout() {
		long currentTime = System.currentTimeMillis();
		if ((currentTime / 1000) >= (startTimeSec + durationTimeSec)) {
			currentState = LeasingState.EXPIRED;
			return true;
		}
		else {
			return false;
		}

	}

	public void setLeaseDuration(long durationTime) {
		startTimeSec = System.currentTimeMillis() / 1000;
		durationTimeSec = durationTime;
	}

	public void cancelLease() {
		startTimeSec = 0;
		durationTimeSec = 0;
		setMACAddress(MacAddress.NONE);
		currentState = LeasingState.AVAILABLE;
	}

	public void renewLease(long durationTime) {
		setLeaseDuration(durationTime);
		currentState = LeasingState.LEASED;
	}

	private void setIPv4Address(IPv4Address ip) {
		this.ip = ip;
	}
	
	private void setMACAddress(MacAddress mac) {
		this.mac = mac;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DHCPBinding binding = (DHCPBinding) o;

		if (startTimeSec != binding.startTimeSec) return false;
		if (durationTimeSec != binding.durationTimeSec) return false;
		if (mac != null ? !mac.equals(binding.mac) : binding.mac != null) return false;
		if (ip != null ? !ip.equals(binding.ip) : binding.ip != null) return false;
		return currentState == binding.currentState;
	}

	@Override
	public int hashCode() {
		int result = mac != null ? mac.hashCode() : 0;
		result = 31 * result + (ip != null ? ip.hashCode() : 0);
		result = 31 * result + (currentState != null ? currentState.hashCode() : 0);
		result = 31 * result + (int) (startTimeSec ^ (startTimeSec >>> 32));
		result = 31 * result + (int) (durationTimeSec ^ (durationTimeSec >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "DHCPBinding{" +
				"mac=" + mac +
				", ip=" + ip +
				", currentState=" + currentState +
				", startTimeSec=" + startTimeSec +
				", durationTimeSec=" + durationTimeSec +
				'}';
	}

}