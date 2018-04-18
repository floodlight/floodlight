package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.VlanVid;

import java.util.Collection;
import java.util.Optional;

public interface IDHCPService extends IFloodlightService {
	/**
	 * DHCP message opcodes: either a DHCP request or a DHCP reply
	 */
	enum OpcodeType {
		/**
		 * DHCP messages are either:
		 *		REQUEST  (client -- 0x01 --> server)
		 *		REPLY 	 (server -- 0x02 --> client)
		 */
		REQUEST, REPLY
	}

	/**
	 * DHCP message types exchanged between client and server
	 */
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

	/**
	 * DHCP client finite state machine
	 */
	enum ClientState {
		INIT, INIT_REBOOT, REBOOTING, SELECTING, REQUESTING, RENEWING, REBINDING, BOUND, UNKNOWN
	}

	/**
	 * Enable DHCP service.
	 */
	void enableDHCP();

	/**
	 * Disable DHCP sservice
	 */
	void disableDHCP();

	/**
	 * Enable DHCP dynamic IP assignment mode. By default, DHCP module will try to give the same IP to client host, if
	 * client host registered before. In this dynamic assignment mode, each time when host send DHCP request, DHCP module
	 * will assign an different IP to client host.
	 */
	void enableDHCPDynamic();

	/**
	 * Disable DHCP dynamic IP assignment mode
	 */
	void disableDHCDynamic();

	/**
	 * Check if DHCP service is enabled or not
	 * @return
	 */
	boolean isDHCPEnabled();

	/**
	 * Check if DHCP dynamic assignment mode is enabled of not
	 * @return
	 */
	boolean isDHCPDynamicEnabled();

	/**
	 * Set the time period that DHCP server check the expired lease
	 * @param timeSec
	 */
	void setCheckExpiredLeasePeriod(long timeSec);

	/**
	 * Get a DHCP instance based on its name
	 * @param name
	 * @return
	 */
	Optional<DHCPInstance> getInstance(String name);

	/**
	 * Get a DHCP instance based on IPv4 address
	 * @param ip
	 * @return
	 */
	Optional<DHCPInstance> getInstance(IPv4Address ip);

	/**
	 * Get a DHCP instance based on node-port-tuple
	 * @param npt
	 * @return
	 */
	Optional<DHCPInstance> getInstance(NodePortTuple npt);

	/**
	 * Get a DHCP instance based on switch DPID
	 * @param dpid
	 * @return
	 */
	Optional<DHCPInstance> getInstance(DatapathId dpid);

	/**
	 * Get a DHCP instance based on VLAN number
	 * @param vid
	 * @return
	 */
	Optional<DHCPInstance> getInstance(VlanVid vid);

	/**
	 * Get all created DHCP instances
	 * @return
	 */
	Collection<DHCPInstance> getInstances();

	/**
	 * Add an DHCP instance
	 * @param instance
	 */
	void addInstance(DHCPInstance instance);

	/**
	 * Update a specific DHCP instance
	 * @param name
	 * @param newInstance
	 * @return
	 */
	DHCPInstance updateInstance(String name, DHCPInstance newInstance);

	/**
	 * Delete a specific DHCP instance
	 * @param name
	 * @return
	 */
	boolean deleteInstance(String name);

	/**
	 * Delete all existing DHCP instance
	 */
	void deleteAllInstances();

}
