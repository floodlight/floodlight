package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.DHCP.DHCPOptionCode;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.DHCPOption;

/**
 * SDN DHCP Server
 * @author Ryan Izard, rizard@g.clemson.edu
 * 
 * 
 * The Floodlight Module implementing a DHCP DHCPServer.
 * This module uses {@code DHCPPool} to manage DHCP leases.
 * It intercepts any DHCP/BOOTP requests from connected hosts and
 * handles the replies. The configuration file:
 * 
 * 		floodlight/src/main/resources/floodlightdefault.properties
 * 
 * contains the DHCP options and parameters that can be set. To allow
 * all DHCP request messages to be sent to the controller (Floodlight),
 * the DHCPSwitchFlowSetter module (in this same package) and the
 * Forwarding module (loaded by default) should also be loaded in
 * Floodlight. When the first DHCP request is received on a particular
 * port of an OpenFlow switch, the request will by default be sent to
 * the control plane to the controller for processing. The DHCPServer
 * module will intercept the message before it makes it to the Forwarding
 * module and process the packet. Now, because we don't want to hog all
 * the DHCP messages (in case there is another module that is using them)
 * we forward the packets down to other modules using Command.CONTINUE.
 * As a side effect, the forwarding module will insert flows in the OF
 * switch for our DHCP traffic even though we've already processed it.
 * In order to allow all future DHCP messages from that same port to be
 * sent to the controller (and not follow the Forwarding module's flows),
 * we need to proactively insert flows for all DHCP client traffic on
 * UDP port 67 to the controller. These flows will allow all DHCP traffic
 * to be intercepted on that same port and sent to the DHCP server running
 * on the Floodlight controller.
 * 
 * Currently, this DHCP server only supports a single subnet; however,
 * work is ongoing to use connected OF switches and ports to allow
 * the user to configure multiple subnets. On a traditional DHCP server,
 * the machine is configured with different NICs, each with their own
 * statically-assigned IP address/subnet/mask. The DHCP server matches
 * the network information of each NIC with the DHCP server's configured
 * subnets and answers the requests accordingly. To mirror this behavior
 * on a OpenFlow network, we can differentiate between subnets based on a
 * device's attachment point. We can assign subnets for a device per
 * OpenFlow switch or per port per switch. This is the next step for
 * this implementations of a SDN DHCP server.
 *
 * I welcome any feedback or suggestions for improvement!
 * 
 * 
 */
public class DHCPServer implements IOFMessageListener, IFloodlightModule  {
	protected static Logger log;
	protected static IFloodlightProviderService floodlightProvider;
	protected static IOFSwitchService switchService;

	// The garbage collector service for the DHCP server
	// Handle expired leases by adding the IP back to the address pool
	private static ScheduledThreadPoolExecutor leasePoliceDispatcher;
	//private static ScheduledFuture<?> leasePoliceOfficer;
	private static Runnable leasePolicePatrol;

	// Contains the pool of IP addresses their bindings to MAC addresses
	// Tracks the lease status and duration of DHCP bindings
	private static volatile DHCPPool theDHCPPool;

	/** START CONFIG FILE VARIABLES **/

	// These variables are set using the floodlightdefault.properties file
	// Refer to startup() for a list of the expected names in the config file

	// The IP and MAC addresses of the controller/DHCP server
	private static MacAddress CONTROLLER_MAC;
	private static IPv4Address CONTROLLER_IP;

	private static IPv4Address DHCP_SERVER_DHCP_SERVER_IP; // Same as CONTROLLER_IP but in byte[] form
	private static IPv4Address DHCP_SERVER_SUBNET_MASK;
	private static IPv4Address DHCP_SERVER_BROADCAST_IP;
	private static IPv4Address DHCP_SERVER_IP_START;
	private static IPv4Address DHCP_SERVER_IP_STOP;
	private static int DHCP_SERVER_ADDRESS_SPACE_SIZE; // Computed in startUp()
	private static IPv4Address DHCP_SERVER_ROUTER_IP = null;
	private static byte[] DHCP_SERVER_NTP_IP_LIST = null;
	private static byte[] DHCP_SERVER_DNS_IP_LIST = null;
	private static byte[] DHCP_SERVER_DN = null;
	private static byte[] DHCP_SERVER_IP_FORWARDING = null;
	private static int DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS;
	private static int DHCP_SERVER_HOLD_LEASE_TIME_SECONDS;
	private static int DHCP_SERVER_REBIND_TIME_SECONDS; // Computed in startUp()
	private static int DHCP_SERVER_RENEWAL_TIME_SECONDS; // Computed in startUp()
	private static long DHCP_SERVER_LEASE_POLICE_PATROL_PERIOD_SECONDS;

	/** END CONFIG FILE VARIABLES **/

	/**
	 * DHCP messages are either:
	 *		REQUEST (client --0x01--> server)
	 *		or REPLY (server --0x02--> client)
	 */
	public static byte DHCP_OPCODE_REQUEST = intToBytes(1)[0];
	public static byte DHCP_OPCODE_REPLY = intToBytes(2)[0];

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
	public static byte[] DHCP_MSG_TYPE_DISCOVER = intToBytesSizeOne(1);
	public static byte[] DHCP_MSG_TYPE_OFFER = intToBytesSizeOne(2);
	public static byte[] DHCP_MSG_TYPE_REQUEST = intToBytesSizeOne(3);
	public static byte[] DHCP_MSG_TYPE_DECLINE = intToBytesSizeOne(4);
	public static byte[] DHCP_MSG_TYPE_ACK = intToBytesSizeOne(5);
	public static byte[] DHCP_MSG_TYPE_NACK = intToBytesSizeOne(6);
	public static byte[] DHCP_MSG_TYPE_RELEASE = intToBytesSizeOne(7);
	public static byte[] DHCP_MSG_TYPE_INFORM = intToBytesSizeOne(8);

	/**
	 * DHCP messages contain options requested by the client and
	 * provided by the server. The options requested by the client are
	 * provided in a list (option 0x37 below) and the server elects to
	 * answer some or all of these options and may provide additional
	 * options as necessary for the DHCP client to obtain a lease.
	 *		OPTION NAME			HEX		DEC 		
	 * 		Subnet Mask			0x01	1
	 * 		Router IP			0x03	3
	 * 		DNS Server IP		0x06	6
	 * 		Domain Name			0x0F	15
	 * 		IP Forwarding		0x13	19
	 * 		Broadcast IP		0x1C	28
	 * 		NTP Server IP		0x2A	42
	 * 		NetBios Name IP		0x2C	44
	 * 		NetBios DDS IP		0x2D	45
	 * 		NetBios Node Type	0x2E	46
	 * 		NetBios Scope ID	0x2F	47
	 * 		Requested IP		0x32	50
	 * 		Lease Time (s)		0x33	51
	 * 		Msg Type (above)	0x35	53
	 * 		DHCP Server IP		0x36	54
	 * 		Option List (this)	0x37	55
	 * 		Renewal Time (s)	0x3A	58
	 * 		Rebind Time (s)		0x3B	59
	 * 		End Option List		0xFF	255
	 * 
	 * NetBios options are not currently implemented in this server but can be added
	 * via the configuration file.
	 **/
	public static byte DHCP_REQ_PARAM_OPTION_CODE_SN = intToBytes(1)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_ROUTER = intToBytes(3)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_DNS = intToBytes(6)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_DN = intToBytes(15)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING = intToBytes(19)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP = intToBytes(28)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NTP_IP = intToBytes(42)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_NAME_IP = intToBytes(44)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_DDS_IP = intToBytes(45)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_NODE_TYPE = intToBytes(46)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_SCOPE_ID = intToBytes(47)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP = intToBytes(50)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME = intToBytes(51)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE = intToBytes(53)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER = intToBytes(54)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS = intToBytes(55)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME = intToBytes(58)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME = intToBytes(59)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_END = intToBytes(255)[0];

	// Used for composing DHCP REPLY messages
	public static final MacAddress BROADCAST_MAC = MacAddress.BROADCAST;
	public static final IPv4Address BROADCAST_IP = IPv4Address.NO_MASK; /* no_mask is all 1's */
	public static final IPv4Address UNASSIGNED_IP = IPv4Address.FULL_MASK; /* full_mask is all 0's */
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		log = LoggerFactory.getLogger(DHCPServer.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		// Read our config options for the DHCP DHCPServer
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			DHCP_SERVER_SUBNET_MASK = IPv4Address.of(configOptions.get("subnet-mask"));
			DHCP_SERVER_IP_START = IPv4Address.of(configOptions.get("lower-ip-range"));
			DHCP_SERVER_IP_STOP = IPv4Address.of(configOptions.get("upper-ip-range"));
			DHCP_SERVER_ADDRESS_SPACE_SIZE = DHCP_SERVER_IP_STOP.getInt() - DHCP_SERVER_IP_START.getInt() + 1;
			DHCP_SERVER_BROADCAST_IP = IPv4Address.of(configOptions.get("broadcast-address"));
			DHCP_SERVER_ROUTER_IP = IPv4Address.of(configOptions.get("router"));
			DHCP_SERVER_DN = configOptions.get("domain-name").getBytes();
			DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS = Integer.parseInt(configOptions.get("default-lease-time"));
			DHCP_SERVER_HOLD_LEASE_TIME_SECONDS = Integer.parseInt(configOptions.get("hold-lease-time"));
			DHCP_SERVER_RENEWAL_TIME_SECONDS = (int) (DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS / 2.0);
			DHCP_SERVER_REBIND_TIME_SECONDS = (int) (DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS * 0.875);
			DHCP_SERVER_LEASE_POLICE_PATROL_PERIOD_SECONDS = Long.parseLong(configOptions.get("lease-gc-period"));
			DHCP_SERVER_IP_FORWARDING = intToBytesSizeOne(Integer.parseInt(configOptions.get("ip-forwarding")));

			CONTROLLER_MAC = MacAddress.of(configOptions.get("controller-mac"));
			CONTROLLER_IP = IPv4Address.of(configOptions.get("controller-ip"));
			DHCP_SERVER_DHCP_SERVER_IP = CONTROLLER_IP;

			// NetBios and other options can be added to this function here as needed in the future
		} catch(IllegalArgumentException ex) {
			log.error("Incorrect DHCP Server configuration options", ex);
			throw ex;
		} catch(NullPointerException ex) {
			log.error("Incorrect DHCP Server configuration options", ex);
			throw ex;
		}
		// Create our new DHCPPool object with the specific address size
		theDHCPPool = new DHCPPool(DHCP_SERVER_IP_START, DHCP_SERVER_ADDRESS_SPACE_SIZE, log);

		// Any addresses that need to be set as static/fixed can be permanently added to the pool with a set MAC
		String staticAddresses = configOptions.get("reserved-static-addresses");
		if (staticAddresses != null) {
			String[] macIpCouples = staticAddresses.split("\\s*;\\s*");
			int i;
			String[] macIpSplit;
			int ipPos, macPos;
			for (i = 0; i < macIpCouples.length; i++) {
				macIpSplit = macIpCouples[i].split("\\s*,\\s*");
				// Determine which element is the MAC and which is the IP
				// i.e. which order have they been typed in in the config file?
				if (macIpSplit[0].length() > macIpSplit[1].length()) {
					macPos = 0;
					ipPos = 1;
				} else {
					macPos = 1;
					ipPos = 0;
				}
				if (theDHCPPool.configureFixedIPLease(IPv4Address.of(macIpSplit[ipPos]), MacAddress.of(macIpSplit[macPos]))) {
					String ip = theDHCPPool.getDHCPbindingFromIPv4(IPv4Address.of(macIpSplit[ipPos])).getIPv4Address().toString();
					String mac = theDHCPPool.getDHCPbindingFromIPv4(IPv4Address.of(macIpSplit[ipPos])).getMACAddress().toString();
					log.info("Configured fixed address of " + ip + " for device " + mac);
				} else {
					log.error("Could not configure fixed address " + macIpSplit[ipPos] + " for device " + macIpSplit[macPos]);
				}
			}
		}

		// The order of the DNS and NTP servers should be most reliable to least
		String dnses = configOptions.get("domain-name-servers");
		String ntps = configOptions.get("ntp-servers");

		// Separate the servers in the comma-delimited list
		// TODO If the list is null then we need to not include this information with the options request,
		// otherwise the client will get incorrect option information
		if (dnses != null) {
			DHCP_SERVER_DNS_IP_LIST = IPv4.toIPv4AddressBytes(dnses.split("\\s*,\\s*")[0].toString());
		}
		if (ntps != null) {
			DHCP_SERVER_NTP_IP_LIST = IPv4.toIPv4AddressBytes(ntps.split("\\s*,\\s*")[0].toString());
		}

		// Monitor bindings for expired leases and clean them up
		leasePoliceDispatcher = new ScheduledThreadPoolExecutor(1);
		leasePolicePatrol = new DHCPLeasePolice();
		/*leasePoliceOfficer = */
		leasePoliceDispatcher.scheduleAtFixedRate(leasePolicePatrol, 10, 
				DHCP_SERVER_LEASE_POLICE_PATROL_PERIOD_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public String getName() {
		return DHCPServer.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// We will rely on forwarding to forward out any DHCP packets that are not
		// destined for our DHCP server. This is to allow an environment where
		// multiple DHCP servers operate cooperatively
		if (type == OFType.PACKET_IN && name.equals(Forwarding.class.getSimpleName())) {
			return true;
		} else {
			return false;
		}
	}

	public static byte[] intToBytes(int integer) {
		byte[] bytes = new byte[4];
		bytes[3] = (byte) (integer >> 24);
		bytes[2] = (byte) (integer >> 16);
		bytes[1] = (byte) (integer >> 8);
		bytes[0] = (byte) (integer);
		return bytes;
	}

	public static byte[] intToBytesSizeOne(int integer) {
		byte[] bytes = new byte[1];
		bytes[0] = (byte) (integer);
		return bytes;
	}

	public void sendDHCPOffer(IOFSwitch sw, OFPort inPort, MacAddress chaddr, IPv4Address dstIPAddr, 
			IPv4Address yiaddr, IPv4Address giaddr, int xid, ArrayList<Byte> requestOrder) {
		// Compose DHCP OFFER
		/** (2) DHCP Offer
		 * -- UDP src port = 67
		 * -- UDP dst port = 68
		 * -- IP src addr = DHCP DHCPServer's IP
		 * -- IP dst addr = 255.255.255.255
		 * -- Opcode = 0x02
		 * -- XID = transactionX
		 * -- ciaddr = blank
		 * -- yiaddr = offer IP
		 * -- siaddr = DHCP DHCPServer IP
		 * -- giaddr = blank
		 * -- chaddr = Client's MAC
		 * -- Options:
		 * --	Option 53 = DHCP Offer
		 * --	Option 1 = SN Mask IP
		 * --	Option 3 = Router IP
		 * --	Option 51 = Lease time (s)
		 * --	Option 54 = DHCP DHCPServer IP
		 * --	Option 6 = DNS servers
		 **/
		OFPacketOut.Builder DHCPOfferPacket = sw.getOFFactory().buildPacketOut();
		DHCPOfferPacket.setBufferId(OFBufferId.NO_BUFFER);

		Ethernet ethDHCPOffer = new Ethernet();
		ethDHCPOffer.setSourceMACAddress(CONTROLLER_MAC);
		ethDHCPOffer.setDestinationMACAddress(chaddr);
		ethDHCPOffer.setEtherType(EthType.IPv4);

		IPv4 ipv4DHCPOffer = new IPv4();
		if (dstIPAddr.equals(IPv4Address.NONE)) {
			ipv4DHCPOffer.setDestinationAddress(BROADCAST_IP);
		} else { // Client has IP and dhcpc must have crashed
			ipv4DHCPOffer.setDestinationAddress(dstIPAddr);
		}
		ipv4DHCPOffer.setSourceAddress(CONTROLLER_IP);
		ipv4DHCPOffer.setProtocol(IpProtocol.UDP);
		ipv4DHCPOffer.setTtl((byte) 64);

		UDP udpDHCPOffer = new UDP();
		udpDHCPOffer.setDestinationPort(UDP.DHCP_CLIENT_PORT);
		udpDHCPOffer.setSourcePort(UDP.DHCP_SERVER_PORT);

		DHCP dhcpDHCPOffer = new DHCP();
		dhcpDHCPOffer.setOpCode(DHCP_OPCODE_REPLY);
		dhcpDHCPOffer.setHardwareType((byte) 1);
		dhcpDHCPOffer.setHardwareAddressLength((byte) 6);
		dhcpDHCPOffer.setHops((byte) 0);
		dhcpDHCPOffer.setTransactionId(xid);
		dhcpDHCPOffer.setSeconds((short) 0);
		dhcpDHCPOffer.setFlags((short) 0);
		dhcpDHCPOffer.setClientIPAddress(UNASSIGNED_IP);
		dhcpDHCPOffer.setYourIPAddress(yiaddr);
		dhcpDHCPOffer.setServerIPAddress(CONTROLLER_IP);
		dhcpDHCPOffer.setGatewayIPAddress(giaddr);
		dhcpDHCPOffer.setClientHardwareAddress(chaddr);

		List<DHCPOption> dhcpOfferOptions = new ArrayList<DHCPOption>();
		DHCPOption newOption;

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE);
		newOption.setData(DHCP_MSG_TYPE_OFFER);
		newOption.setLength((byte) 1);
		dhcpOfferOptions.add(newOption);

		for (Byte specificRequest : requestOrder) {
			if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_SN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_SN);
				newOption.setData(DHCP_SERVER_SUBNET_MASK.getBytes());
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_ROUTER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_ROUTER);
				newOption.setData(DHCP_SERVER_ROUTER_IP.getBytes());
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DN);
				newOption.setData(DHCP_SERVER_DN);
				newOption.setLength((byte) DHCP_SERVER_DN.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DNS) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DNS);
				newOption.setData(DHCP_SERVER_DNS_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_DNS_IP_LIST.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP);
				newOption.setData(DHCP_SERVER_BROADCAST_IP.getBytes());
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
				newOption.setData(DHCP_SERVER_DHCP_SERVER_IP.getBytes());
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
				newOption.setData(DHCP_SERVER_NTP_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_NTP_IP_LIST.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_REBIND_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_RENEWAL_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING);
				newOption.setData(DHCP_SERVER_IP_FORWARDING);
				newOption.setLength((byte) 1);
				dhcpOfferOptions.add(newOption);
			} else {
				//log.debug("Setting specific request for OFFER failed");
			}
		}

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_END);
		newOption.setLength((byte) 0);
		dhcpOfferOptions.add(newOption);

		dhcpDHCPOffer.setOptions(dhcpOfferOptions);

		ethDHCPOffer.setPayload(ipv4DHCPOffer.setPayload(udpDHCPOffer.setPayload(dhcpDHCPOffer)));

		DHCPOfferPacket.setInPort(OFPort.ANY);

		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(sw.getOFFactory().actions().output(inPort, 0xffFFffFF));
		DHCPOfferPacket.setActions(actions);

		DHCPOfferPacket.setData(ethDHCPOffer.serialize());

		log.debug("Sending DHCP OFFER");
		sw.write(DHCPOfferPacket.build());
	}

	public void sendDHCPAck(IOFSwitch sw, OFPort inPort, MacAddress chaddr, IPv4Address dstIPAddr, 
			IPv4Address yiaddr, IPv4Address giaddr, int xid, ArrayList<Byte> requestOrder) {
		/** (4) DHCP ACK
		 * -- UDP src port = 67
		 * -- UDP dst port = 68
		 * -- IP src addr = DHCP DHCPServer's IP
		 * -- IP dst addr = 255.255.255.255
		 * -- Opcode = 0x02
		 * -- XID = transactionX
		 * -- ciaddr = blank
		 * -- yiaddr = offer IP
		 * -- siaddr = DHCP DHCPServer IP
		 * -- giaddr = blank
		 * -- chaddr = Client's MAC
		 * -- Options:
		 * --	Option 53 = DHCP ACK
		 * --	Option 1 = SN Mask IP
		 * --	Option 3 = Router IP
		 * --	Option 51 = Lease time (s)
		 * --	Option 54 = DHCP DHCPServer IP
		 * --	Option 6 = DNS servers
		 **/
		OFPacketOut.Builder DHCPACKPacket = sw.getOFFactory().buildPacketOut();
		DHCPACKPacket.setBufferId(OFBufferId.NO_BUFFER);

		Ethernet ethDHCPAck = new Ethernet();
		ethDHCPAck.setSourceMACAddress(CONTROLLER_MAC);
		ethDHCPAck.setDestinationMACAddress(chaddr);
		ethDHCPAck.setEtherType(EthType.IPv4);

		IPv4 ipv4DHCPAck = new IPv4();
		if (dstIPAddr.equals(IPv4Address.NONE)) {
			ipv4DHCPAck.setDestinationAddress(BROADCAST_IP);
		} else { // Client has IP and dhclient must have crashed
			ipv4DHCPAck.setDestinationAddress(dstIPAddr);
		}
		ipv4DHCPAck.setSourceAddress(CONTROLLER_IP);
		ipv4DHCPAck.setProtocol(IpProtocol.UDP);
		ipv4DHCPAck.setTtl((byte) 64);

		UDP udpDHCPAck = new UDP();
		udpDHCPAck.setDestinationPort(UDP.DHCP_CLIENT_PORT);
		udpDHCPAck.setSourcePort(UDP.DHCP_SERVER_PORT);

		DHCP dhcpDHCPAck = new DHCP();
		dhcpDHCPAck.setOpCode(DHCP_OPCODE_REPLY);
		dhcpDHCPAck.setHardwareType((byte) 1);
		dhcpDHCPAck.setHardwareAddressLength((byte) 6);
		dhcpDHCPAck.setHops((byte) 0);
		dhcpDHCPAck.setTransactionId(xid);
		dhcpDHCPAck.setSeconds((short) 0);
		dhcpDHCPAck.setFlags((short) 0);
		dhcpDHCPAck.setClientIPAddress(UNASSIGNED_IP);
		dhcpDHCPAck.setYourIPAddress(yiaddr);
		dhcpDHCPAck.setServerIPAddress(CONTROLLER_IP);
		dhcpDHCPAck.setGatewayIPAddress(giaddr);
		dhcpDHCPAck.setClientHardwareAddress(chaddr);

		List<DHCPOption> dhcpAckOptions = new ArrayList<DHCPOption>();
		DHCPOption newOption;

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE);
		newOption.setData(DHCP_MSG_TYPE_ACK);
		newOption.setLength((byte) 1);
		dhcpAckOptions.add(newOption);

		for (Byte specificRequest : requestOrder) {
			if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_SN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_SN);
				newOption.setData(DHCP_SERVER_SUBNET_MASK.getBytes());
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_ROUTER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_ROUTER);
				newOption.setData(DHCP_SERVER_ROUTER_IP.getBytes());
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DN);
				newOption.setData(DHCP_SERVER_DN);
				newOption.setLength((byte) DHCP_SERVER_DN.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DNS) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DNS);
				newOption.setData(DHCP_SERVER_DNS_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_DNS_IP_LIST.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP);
				newOption.setData(DHCP_SERVER_BROADCAST_IP.getBytes());
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
				newOption.setData(DHCP_SERVER_DHCP_SERVER_IP.getBytes());
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
				newOption.setData(DHCP_SERVER_NTP_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_NTP_IP_LIST.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_REBIND_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_RENEWAL_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING);
				newOption.setData(DHCP_SERVER_IP_FORWARDING);
				newOption.setLength((byte) 1);
				dhcpAckOptions.add(newOption);
			}else {
				log.debug("Setting specific request for ACK failed");
			}
		}

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_END);
		newOption.setLength((byte) 0);
		dhcpAckOptions.add(newOption);

		dhcpDHCPAck.setOptions(dhcpAckOptions);

		ethDHCPAck.setPayload(ipv4DHCPAck.setPayload(udpDHCPAck.setPayload(dhcpDHCPAck)));

		DHCPACKPacket.setInPort(OFPort.ANY);

		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(sw.getOFFactory().actions().output(inPort, 0xffFFffFF));
		DHCPACKPacket.setActions(actions);

		DHCPACKPacket.setData(ethDHCPAck.serialize());

		log.debug("Sending DHCP ACK");
		sw.write(DHCPACKPacket.build());
	}

	public void sendDHCPNack(IOFSwitch sw, OFPort inPort, MacAddress chaddr, IPv4Address giaddr, int xid) {
		OFPacketOut.Builder DHCPOfferPacket = sw.getOFFactory().buildPacketOut();
		DHCPOfferPacket.setBufferId(OFBufferId.NO_BUFFER);

		Ethernet ethDHCPOffer = new Ethernet();
		ethDHCPOffer.setSourceMACAddress(CONTROLLER_MAC);
		ethDHCPOffer.setDestinationMACAddress(chaddr);
		ethDHCPOffer.setEtherType(EthType.IPv4);

		IPv4 ipv4DHCPOffer = new IPv4();
		ipv4DHCPOffer.setDestinationAddress(BROADCAST_IP);
		ipv4DHCPOffer.setSourceAddress(CONTROLLER_IP);
		ipv4DHCPOffer.setProtocol(IpProtocol.UDP);
		ipv4DHCPOffer.setTtl((byte) 64);

		UDP udpDHCPOffer = new UDP();
		udpDHCPOffer.setDestinationPort(UDP.DHCP_CLIENT_PORT);
		udpDHCPOffer.setSourcePort(UDP.DHCP_SERVER_PORT);

		DHCP dhcpDHCPOffer = new DHCP();
		dhcpDHCPOffer.setOpCode(DHCP_OPCODE_REPLY);
		dhcpDHCPOffer.setHardwareType((byte) 1);
		dhcpDHCPOffer.setHardwareAddressLength((byte) 6);
		dhcpDHCPOffer.setHops((byte) 0);
		dhcpDHCPOffer.setTransactionId(xid);
		dhcpDHCPOffer.setSeconds((short) 0);
		dhcpDHCPOffer.setFlags((short) 0);
		dhcpDHCPOffer.setClientIPAddress(UNASSIGNED_IP);
		dhcpDHCPOffer.setYourIPAddress(UNASSIGNED_IP);
		dhcpDHCPOffer.setServerIPAddress(CONTROLLER_IP);
		dhcpDHCPOffer.setGatewayIPAddress(giaddr);
		dhcpDHCPOffer.setClientHardwareAddress(chaddr);

		List<DHCPOption> dhcpOfferOptions = new ArrayList<DHCPOption>();
		DHCPOption newOption;

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE);
		newOption.setData(DHCP_MSG_TYPE_NACK);
		newOption.setLength((byte) 1);
		dhcpOfferOptions.add(newOption);

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
		newOption.setData(DHCP_SERVER_DHCP_SERVER_IP.getBytes());
		newOption.setLength((byte) 4);
		dhcpOfferOptions.add(newOption);

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_END);
		newOption.setLength((byte) 0);
		dhcpOfferOptions.add(newOption);

		dhcpDHCPOffer.setOptions(dhcpOfferOptions);

		ethDHCPOffer.setPayload(ipv4DHCPOffer.setPayload(udpDHCPOffer.setPayload(dhcpDHCPOffer)));

		DHCPOfferPacket.setInPort(OFPort.ANY);

		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(sw.getOFFactory().actions().output(inPort, 0xffFFffFF));
		DHCPOfferPacket.setActions(actions);

		DHCPOfferPacket.setData(ethDHCPOffer.serialize());

		log.info("Sending DHCP NACK");
		sw.write(DHCPOfferPacket.build());
	}

	public ArrayList<Byte> getRequestedParameters(DHCP DHCPPayload, boolean isInform) {
		ArrayList<Byte> requestOrder = new ArrayList<Byte>();
		byte[] requests = DHCPPayload.getOption(DHCPOptionCode.OptionCode_RequestedParameters).getData();
		boolean requestedLeaseTime = false;
		boolean requestedRebindTime = false;
		boolean requestedRenewTime = false;
		for (byte specificRequest : requests) {
			if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_SN) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_SN);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_ROUTER) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_ROUTER);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_DN) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_DN);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_DNS) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_DNS);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				requestedLeaseTime = true;
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				requestedRebindTime = true;
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				requestedRenewTime = true;
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING);
				log.debug("requested IP FORWARDING");
			} else {
				//log.debug("Requested option 0x" + Byte.toString(specificRequest) + " not available");
			}
		}
		
		// We need to add these in regardless if the request list includes them
		if (!isInform) {
			if (!requestedLeaseTime) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				log.debug("added option LEASE TIME");
			}
			if (!requestedRenewTime) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				log.debug("added option RENEWAL TIME");
			}
			if (!requestedRebindTime) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				log.debug("added option REBIND TIME");
			}
		}
		return requestOrder;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		OFPacketIn pi = (OFPacketIn) msg;

		if (!theDHCPPool.hasAvailableAddresses()) {
			log.info("DHCP Pool is full! Consider increasing the pool size.");
			return Command.CONTINUE;
		}

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if (eth.getEtherType() == EthType.IPv4) { /* shallow compare is okay for EthType */
			log.debug("Got IPv4 Packet");
			IPv4 IPv4Payload = (IPv4) eth.getPayload();
			IPv4Address IPv4SrcAddr = IPv4Payload.getSourceAddress();

			if (IPv4Payload.getProtocol() == IpProtocol.UDP) { /* shallow compare also okay for IpProtocol */
				log.debug("Got UDP Packet");
				UDP UDPPayload = (UDP) IPv4Payload.getPayload();

				if ((UDPPayload.getDestinationPort().equals(UDP.DHCP_SERVER_PORT) /* TransportPort must be deep though */
						|| UDPPayload.getDestinationPort().equals(UDP.DHCP_CLIENT_PORT))
						&& (UDPPayload.getSourcePort().equals(UDP.DHCP_SERVER_PORT)
						|| UDPPayload.getSourcePort().equals(UDP.DHCP_CLIENT_PORT)))
				{
					log.debug("Got DHCP Packet");
					// This is a DHCP packet that we need to process
					DHCP DHCPPayload = (DHCP) UDPPayload.getPayload();
					OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

					/* DHCP/IPv4 Header Information */
					int xid = 0;
					IPv4Address yiaddr = IPv4Address.NONE;
					IPv4Address giaddr = IPv4Address.NONE;
					MacAddress chaddr = null;
					IPv4Address desiredIPAddr = null;
					ArrayList<Byte> requestOrder = new ArrayList<Byte>();
					if (DHCPPayload.getOpCode() == DHCP_OPCODE_REQUEST) {
						/**  * (1) DHCP Discover
						 * -- UDP src port = 68
						 * -- UDP dst port = 67
						 * -- IP src addr = 0.0.0.0
						 * -- IP dst addr = 255.255.255.255
						 * -- Opcode = 0x01
						 * -- XID = transactionX
						 * -- All addresses blank:
						 * --	ciaddr (client IP)
						 * --	yiaddr (your IP)
						 * --	siaddr (DHCPServer IP)
						 * --	giaddr (GW IP)
						 * -- chaddr = Client's MAC
						 * -- Options:
						 * --	Option 53 = DHCP Discover
						 * --	Option 50 = possible IP request
						 * --	Option 55 = parameter request list
						 * --		(1) SN Mask
						 * --		(3) Router
						 * --		(15) Domain Name
						 * --		(6) DNS
						 **/
						if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_DISCOVER)) {
							log.debug("DHCP DISCOVER Received");
							xid = DHCPPayload.getTransactionId();
							yiaddr = DHCPPayload.getYourIPAddress();
							// Will have GW IP if a relay agent was used
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = DHCPPayload.getClientHardwareAddress();
							List<DHCPOption> options = DHCPPayload.getOptions();
							for (DHCPOption option : options) {
								if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP) {
									desiredIPAddr = IPv4Address.of(option.getData());
									log.debug("Got requested IP");
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS) {
									log.debug("Got requested param list");
									requestOrder = getRequestedParameters(DHCPPayload, false); 		
								}
							}

							// Process DISCOVER message and prepare an OFFER with minimum-hold lease
							// A HOLD lease should be a small amount of time sufficient for the client to respond
							// with a REQUEST, at which point the ACK will set the least time to the DEFAULT
							synchronized (theDHCPPool) {
								if (!theDHCPPool.hasAvailableAddresses()) {
									log.info("DHCP Pool is full! Consider increasing the pool size.");
									log.info("Device with MAC " + chaddr.toString() + " was not granted an IP lease");
									return Command.CONTINUE;
								}
								DHCPBinding lease = theDHCPPool.getSpecificAvailableLease(desiredIPAddr, chaddr);

								if (lease != null) {
									log.debug("Checking new lease with specific IP");
									theDHCPPool.setDHCPbinding(lease, chaddr, DHCP_SERVER_HOLD_LEASE_TIME_SECONDS);
									yiaddr = lease.getIPv4Address();
									log.debug("Got new lease for " + yiaddr.toString());
								} else {
									log.debug("Checking new lease for any IP");
									lease = theDHCPPool.getAnyAvailableLease(chaddr);
									theDHCPPool.setDHCPbinding(lease, chaddr, DHCP_SERVER_HOLD_LEASE_TIME_SECONDS);
									yiaddr = lease.getIPv4Address();
									log.debug("Got new lease for " + yiaddr.toString());
								}
							}

							sendDHCPOffer(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);
						} // END IF DISCOVER

						/** (3) DHCP Request
						 * -- UDP src port = 68
						 * -- UDP dst port = 67
						 * -- IP src addr = 0.0.0.0
						 * -- IP dst addr = 255.255.255.255
						 * -- Opcode = 0x01
						 * -- XID = transactionX
						 * -- ciaddr = blank
						 * -- yiaddr = blank
						 * -- siaddr = DHCP DHCPServer IP
						 * -- giaddr = GW IP
						 * -- chaddr = Client's MAC
						 * -- Options:
						 * --	Option 53 = DHCP Request
						 * --	Option 50 = IP requested (from offer)
						 * --	Option 54 = DHCP DHCPServer IP
						 **/
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_REQUEST)) {
							log.debug(": DHCP REQUEST received");
							IPv4SrcAddr = IPv4Payload.getSourceAddress();
							xid = DHCPPayload.getTransactionId();
							yiaddr = DHCPPayload.getYourIPAddress();
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = DHCPPayload.getClientHardwareAddress();

							List<DHCPOption> options = DHCPPayload.getOptions();
							for (DHCPOption option : options) {
								if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP) {
									desiredIPAddr = IPv4Address.of(option.getData());
									if (!desiredIPAddr.equals(theDHCPPool.getDHCPbindingFromMAC(chaddr).getIPv4Address())) {
										// This client wants a different IP than what we have on file, so cancel its HOLD lease now (if we have one)
										theDHCPPool.cancelLeaseOfMAC(chaddr);
										return Command.CONTINUE;
									}
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
									if (!IPv4Address.of(option.getData()).equals(DHCP_SERVER_DHCP_SERVER_IP)) {
										// We're not the DHCPServer the client wants to use, so cancel its HOLD lease now and ignore the client
										theDHCPPool.cancelLeaseOfMAC(chaddr);
										return Command.CONTINUE;
									}
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS) {
									requestOrder = getRequestedParameters(DHCPPayload, false);
								}
							}
							// Process REQUEST message and prepare an ACK with default lease time
							// This extends the hold lease time to that of a normal lease
							boolean sendACK = true;
							synchronized (theDHCPPool) {
								if (!theDHCPPool.hasAvailableAddresses()) {
									log.info("DHCP Pool is full! Consider increasing the pool size.");
									log.info("Device with MAC " + chaddr.toString() + " was not granted an IP lease");
									return Command.CONTINUE;
								}
								DHCPBinding lease;
								// Get any binding, in use now or not
								if (desiredIPAddr != null) {
									lease = theDHCPPool.getDHCPbindingFromIPv4(desiredIPAddr);
								} else {
									lease = theDHCPPool.getAnyAvailableLease(chaddr);
								}
								// This IP is not in our allocation range
								if (lease == null) {
									log.info("The IP " + desiredIPAddr.toString() + " is not in the range " 
											+ DHCP_SERVER_IP_START.toString() + " to " + DHCP_SERVER_IP_STOP.toString());
									log.info("Device with MAC " + chaddr.toString() + " was not granted an IP lease");
									sendACK = false;
									// Determine if the IP in the binding we just retrieved is okay to allocate to the MAC requesting it
								} else if (!lease.getMACAddress().equals(chaddr) && lease.isActiveLease()) {
									log.debug("Tried to REQUEST an IP that is currently assigned to another MAC");
									log.debug("Device with MAC " + chaddr.toString() + " was not granted an IP lease");
									sendACK = false;
									// Check if we want to renew the MAC's current lease
								} else if (lease.getMACAddress().equals(chaddr) && lease.isActiveLease()) {
									log.debug("Renewing lease for MAC " + chaddr.toString());
									theDHCPPool.renewLease(lease.getIPv4Address(), DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS);
									yiaddr = lease.getIPv4Address();
									log.debug("Finalized renewed lease for " + yiaddr.toString());
									// Check if we want to create a new lease for the MAC
								} else if (!lease.isActiveLease()){
									log.debug("Assigning new lease for MAC " + chaddr.toString());
									theDHCPPool.setDHCPbinding(lease, chaddr, DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS);
									yiaddr = lease.getIPv4Address();
									log.debug("Finalized new lease for " + yiaddr.toString());
								} else {
									log.debug("Don't know how we got here");
									return Command.CONTINUE;
								}
							}
							if (sendACK) {
								sendDHCPAck(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);							
							} else {
								sendDHCPNack(sw, inPort, chaddr, giaddr, xid);
							}
						} // END IF REQUEST
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_RELEASE)) {
							if (DHCPPayload.getServerIPAddress() != CONTROLLER_IP) {
								log.info("DHCP RELEASE message not for our DHCP server");
								// Send the packet out the port it would normally go out via the Forwarding module
								// Execution jumps to return Command.CONTINUE at end of receive()
							} else {
								log.debug("Got DHCP RELEASE. Cancelling remaining time on DHCP lease");
								synchronized(theDHCPPool) {
									if (theDHCPPool.cancelLeaseOfMAC(DHCPPayload.getClientHardwareAddress())) {
										log.info("Cancelled DHCP lease of " + DHCPPayload.getClientHardwareAddress().toString());
										log.info("IP " + theDHCPPool.getDHCPbindingFromMAC(DHCPPayload.getClientHardwareAddress()).getIPv4Address().toString()
												+ " is now available in the DHCP address pool");
									} else {
										log.debug("Lease of " + DHCPPayload.getClientHardwareAddress().toString()
												+ " was already inactive");
									}
								}
							}
						} // END IF RELEASE
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_DECLINE)) {
							log.debug("Got DHCP DECLINE. Cancelling HOLD time on DHCP lease");
							synchronized(theDHCPPool) {
								if (theDHCPPool.cancelLeaseOfMAC(DHCPPayload.getClientHardwareAddress())) {
									log.info("Cancelled DHCP lease of " + DHCPPayload.getClientHardwareAddress().toString());
									log.info("IP " + theDHCPPool.getDHCPbindingFromMAC(DHCPPayload.getClientHardwareAddress()).getIPv4Address().toString()
											+ " is now available in the DHCP address pool");
								} else {
									log.info("HOLD Lease of " + DHCPPayload.getClientHardwareAddress().toString()
											+ " has already expired");
								}
							}
						} // END IF DECLINE
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_INFORM)) {
							log.debug("Got DHCP INFORM. Retreiving requested parameters from message");
							IPv4SrcAddr = IPv4Payload.getSourceAddress();
							xid = DHCPPayload.getTransactionId();
							yiaddr = DHCPPayload.getYourIPAddress();
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = DHCPPayload.getClientHardwareAddress();

							// Get the requests from the INFORM message. True for inform -- we don't want to include lease information
							requestOrder = getRequestedParameters(DHCPPayload, true);
							
							// Process INFORM message and send an ACK with requested information
							sendDHCPAck(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);							
						} // END IF INFORM
					} // END IF DHCP OPCODE REQUEST 
					else if (DHCPPayload.getOpCode() == DHCP_OPCODE_REPLY) {
						// Do nothing right now. The DHCP DHCPServer isn't supposed to receive replies but ISSUE them instead
						log.debug("Got an OFFER/ACK (REPLY)...this shouldn't happen unless there's another DHCP Server somewhere");
					} else {
						log.debug("Got DHCP packet, but not a known DHCP packet opcode");
					}
				} // END IF DHCP packet
			} // END IF UDP packet
		} // END IF IPv4 packet
		return Command.CONTINUE;
	} // END of receive(pkt)

	/**
	 * DHCPLeasePolice is a simple class that is instantiated and invoked
	 * as a runnable thread. The objective is to clean up the expired DHCP
	 * leases on a set time interval. Most DHCP leases are hours in length,
	 * so the granularity of our check can be on the order of minutes (IMHO).
	 * The period of the check for expired leases, in seconds, is specified
	 * in the configuration file:
	 * 
	 * 		floodlight/src/main/resources/floodlightdefault.properties
	 * 
	 * as option:
	 * 
	 * 		net.floodlightcontroller.dhcpserver.DHCPServer.lease-gc-period = <seconds>
	 * 
	 * where gc stands for "garbage collection".
	 * 
	 * @author Ryan Izard, rizard@g.clemson.edu
	 *
	 */
	class DHCPLeasePolice implements Runnable {
		@Override
		public void run() {
			log.info("Cleaning any expired DHCP leases...");
			ArrayList<DHCPBinding> newAvailableBindings;
			synchronized(theDHCPPool) {
				// Loop through lease pool and check all leases to see if they are expired
				// If a lease is expired, then clean it up and make the binding available
				newAvailableBindings = theDHCPPool.cleanExpiredLeases();
			}
			for (DHCPBinding binding : newAvailableBindings) {
				log.info("MAC " + binding.getMACAddress().toString() + " has expired");
				log.info("Lease now available for IP " + binding.getIPv4Address().toString());
			}
		}
	} // END DHCPLeasePolice Class
} // END DHCPServer Class