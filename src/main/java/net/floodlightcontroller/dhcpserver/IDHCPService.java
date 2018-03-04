package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.VlanVid;

import java.util.Collection;
import java.util.Optional;

public interface IDHCPService extends IFloodlightService {
	enum OpcodeType {
		/**
		 * DHCP messages are either:
		 *		REQUEST  (client -- 0x01 --> server)
		 *		REPLY 	 (server -- 0x02 --> client)
		 */
		REQUEST, REPLY
	}

	enum MessageType {
		/**
		 * DHCP REQUEST messages are either of type:
		 *		DISCOVER (0x01)
		 *		REQUEST  (0x03)
		 * 		DECLINE  (0x04)
		 *		RELEASE  (0x07)
		 *		INFORM   (0x08)
		 *
		 * DHCP REPLY messages are either of type:
		 *		OFFER    (0x02)
		 *		ACK    	 (0x05)
		 *		NAK   	 (0x06)
		 **/
		DISCOVER, REQUEST, RELEASE, DECLINE, INFORM, OFFER, ACK, NAK
	}

	enum ClientState {
		INIT_REBOOT, SELECTING, RENEWING, REBINDING, UNKNOWN
	}

	void enableDHCP();
	void disableDHCP();
	boolean isDHCPEnabled();
	void setCheckExpiredLeasePeriod(long timeSec);

	Optional<DHCPInstance> getInstance(String name);
	Optional<DHCPInstance> getInstance(IPv4Address ip);
	Optional<DHCPInstance> getInstance(NodePortTuple npt);
	Optional<DHCPInstance> getInstance(VlanVid vid);
	Collection<DHCPInstance> getInstances();

	void addInstance(DHCPInstance instance);
	DHCPInstance updateInstance(String name, DHCPInstance newInstance);
	boolean deleteInstance(String name);
	void deleteAllInstances();

}
