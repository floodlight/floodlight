package net.floodlightcontroller.dhcpserver;

import java.util.Collection;
import java.util.Optional;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

public interface IDHCPService extends IFloodlightService {
	public enum OpcodeType {
		/**
		 * DHCP messages are either:
		 *		REQUEST  (client -- 0x01 --> server)
		 *		or REPLY (server -- 0x02 --> client)
		 */
		REQUEST, REPLY
	}

	public enum MessageType {
		/**
		 * DHCP REQUEST messages are either of type:
		 *		DISCOVER (0x01)
		 *		REQUEST (0x03)
		 * 		DECLINE (0x04)
		 *		RELEASE (0x07)
		 *		or INFORM (0x08)
		 * DHCP REPLY messages are either of type:
		 *		OFFER (0x02)
		 *		ACK (0x05)
		 *		or NACK (0x06)
		 **/
		DISCOVER, REQUEST, RELEASE, DECLINE, INFORM, OFFER, ACK, NACK
	}


	public void enableDHCP();
	public void disableDHCP();
	public boolean isDHCPEnabled();

	public Optional<DHCPInstance> getInstance(String name);
	public Optional<DHCPInstance> getInstance(IPv4Address ip);
	public Optional<DHCPInstance> getInstance(NodePortTuple npt);
	public Optional<DHCPInstance> getInstance(VlanVid vid);
	public Collection<DHCPInstance> getInstances();

	public boolean addInstance(DHCPInstance instance);
	public boolean deleteInstance(String name);

}
