package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

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

	private final IPv4Address ip;

	private MacAddress mac = MacAddress.NONE;
	private LeasingState currentState;
	private long startTimeSec;
	private long durationTimeSec;
	
	protected DHCPBinding(IPv4Address ip, MacAddress mac) {
		this.ip = ip;
		this.setMACAddress(mac);
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
		this.setMACAddress(mac);
		this.currentState = LeasingState.PERMANENT_LEASED;
	}

	public void configureNormalLease(@Nonnull MacAddress mac, long durationTimeSec) {
		this.setMACAddress(mac);
		this.currentState = LeasingState.LEASED;
		this.setLeaseDuration(durationTimeSec);
	}

	public boolean isBindingTimeout() {
		long currentTime = System.nanoTime();
		if ((currentTime / 1000000000) >= (this.startTimeSec + this.durationTimeSec)) {
			this.currentState = LeasingState.EXPIRED;
			return true;
		}
		else {
			return false;
		}

	}

	public void setLeaseDuration(long durationTime) {
		this.startTimeSec = System.nanoTime() / 1000000000;
		this.durationTimeSec = durationTime;
	}

	public void cancelLease() {
		this.startTimeSec = 0;
		this.durationTimeSec = 0;
		this.setMACAddress(MacAddress.NONE);
		this.currentState = LeasingState.AVAILABLE;
	}

	public void renewLease(long durationTime) {
		this.setLeaseDuration(durationTime);
		this.currentState = LeasingState.LEASED;
	}
	
	private void setMACAddress(MacAddress mac) {
		this.mac = mac;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DHCPBinding that = (DHCPBinding) o;

		return ip != null ? ip.equals(that.ip) : that.ip == null;
	}

	@Override
	public int hashCode() {
		return ip != null ? ip.hashCode() : 0;
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