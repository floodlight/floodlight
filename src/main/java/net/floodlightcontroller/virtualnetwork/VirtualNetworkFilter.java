/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.virtualnetwork;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.ForwardingBase;

/**
 * A simple Layer 2 (MAC based) network virtualization module. This module allows
 * you to create simple L2 networks (host + gateway) and will drop traffic if
 * they are not on the same virtual network.
 *
 * LIMITATIONS
 * - This module does not allow overlapping of IPs or MACs
 * - You can only have 1 gateway per virtual network (can be shared)
 * - There is filtering of multicast/broadcast traffic
 * - All DHCP traffic will be allowed, regardless of unicast/broadcast
 *
 * @author alexreimers
 */
public class VirtualNetworkFilter
implements IFloodlightModule, IVirtualNetworkService, IOFMessageListener {
	protected static Logger log = LoggerFactory.getLogger(VirtualNetworkFilter.class);

	private static final short APP_ID = 20;
	static {
		AppCookie.registerApp(APP_ID, "VirtualNetworkFilter");
	}

	// Our dependencies
	IFloodlightProviderService floodlightProviderService;
	IRestApiService restApiService;
	IDeviceService deviceService;

	// Our internal state
	protected Map<String, VirtualNetwork> vNetsByGuid; // List of all created virtual networks
	protected Map<String, String> nameToGuid; // Logical name -> Network ID
	protected Map<String, IPv4Address> guidToGateway; // Network ID -> Gateway IP
	protected Map<IPv4Address, Set<String>> gatewayToGuid; // Gateway IP -> Network ID
	protected Map<MacAddress, IPv4Address> macToGateway; // Gateway MAC -> Gateway IP
	protected Map<MacAddress, String> macToGuid; // Host MAC -> Network ID
	protected Map<String, MacAddress> portToMac; // Host MAC -> logical port name

	// Device Listener impl class
	protected DeviceListenerImpl deviceListener;

	/**
	 * Adds a gateway to a virtual network.
	 * @param guid The ID (not name) of the network.
	 * @param ip The IP addresses of the gateway.
	 */
	protected void addGateway(String guid, IPv4Address ip) {
		if (ip.getInt() != 0) {
			if (log.isDebugEnabled()) {
				log.debug("Adding {} as gateway for GUID {}", ip.toString(), guid);
			}

			guidToGateway.put(guid, ip);
			if (vNetsByGuid.get(guid) != null)
				vNetsByGuid.get(guid).setGateway(ip.toString());
			if (gatewayToGuid.containsKey(ip)) {
				Set<String> gSet = gatewayToGuid.get(ip);
				gSet.add(guid);
			} else {
				Set<String> gSet = Collections.synchronizedSet(new HashSet<String>());
				gSet.add(guid);
				gatewayToGuid.put(ip, gSet);
			}
		}
	}

	/**
	 * Deletes a gateway for a virtual network.
	 * @param guid The ID (not name) of the network to delete
	 * the gateway for.
	 */
	protected void deleteGateway(String guid) {
		IPv4Address gwIp = guidToGateway.remove(guid);
		if (gwIp == null) return;
		Set<String> gSet = gatewayToGuid.get(gwIp);
		gSet.remove(guid);
		if (vNetsByGuid.get(guid) != null)
			vNetsByGuid.get(guid).setGateway(null);
	}

	// IVirtualNetworkService

	@Override
	public void createNetwork(String guid, String network, IPv4Address gateway) {
		if (log.isDebugEnabled()) {
			String gw = null;
			try {
				gw = gateway.toString();
			} catch (Exception e) {
				// fail silently
			}
			log.debug("Creating network {} with ID {} and gateway {}",
					new Object[] {network, guid, gw});
		}

		if (!nameToGuid.isEmpty()) {
			// We have to iterate all the networks to handle name/gateway changes
			for (Entry<String, String> entry : nameToGuid.entrySet()) {
				if (entry.getValue().equals(guid)) {
					nameToGuid.remove(entry.getKey());
					break;
				}
			}
		}
		if(network != null)
			nameToGuid.put(network, guid);
		if (vNetsByGuid.containsKey(guid))
			vNetsByGuid.get(guid).setName(network); //network already exists, just updating name
		else
			vNetsByGuid.put(guid, new VirtualNetwork(network, guid)); //new network

		// If they don't specify a new gateway the old one will be preserved
		if ((gateway != null) && (gateway.getInt() != 0)) {
			addGateway(guid, gateway);
			if (vNetsByGuid.get(guid) != null)
				vNetsByGuid.get(guid).setGateway(gateway.toString());
		}
	}

	@Override
	public void deleteNetwork(String guid) {
		String name = null;
		if (nameToGuid.isEmpty()) {
			log.warn("Could not delete network with ID {}, network doesn't exist",
					guid);
			return;
		}
		for (Entry<String, String> entry : nameToGuid.entrySet()) {
			if (entry.getValue().equals(guid)) {
				name = entry.getKey();
				break;
			}
			log.warn("Could not delete network with ID {}, network doesn't exist", guid);
		}

		if (log.isDebugEnabled())
			log.debug("Deleting network with name {} ID {}", name, guid);

		nameToGuid.remove(name);
		deleteGateway(guid);
		if (vNetsByGuid.get(guid) != null){
			vNetsByGuid.get(guid).clearHosts();
			vNetsByGuid.remove(guid);
		}
		Collection<MacAddress> deleteList = new ArrayList<MacAddress>();
		for (MacAddress host : macToGuid.keySet()) {
			if (macToGuid.get(host).equals(guid)) {
				deleteList.add(host);
			}
		}
		for (MacAddress mac : deleteList) {
			if (log.isDebugEnabled()) {
				log.debug("Removing host {} from network {}", mac.toString(), guid);
			}
			macToGuid.remove(mac);
			for (Entry<String, MacAddress> entry : portToMac.entrySet()) {
				if (entry.getValue().equals(mac)) {
					portToMac.remove(entry.getKey());
					break;
				}
			}
		}
	}

	@Override
	public void addHost(MacAddress mac, String guid, String port) {
		if (guid != null) {
			if (log.isDebugEnabled()) {
				log.debug("Adding {} to network ID {} on port {}",
						new Object[] {mac, guid, port});
			}
			// We ignore old mappings
			macToGuid.put(mac, guid);
			portToMac.put(port, mac);
			if (vNetsByGuid.get(guid) != null)
				vNetsByGuid.get(guid).addHost(port, mac);
		} else {
			log.warn("Could not add MAC {} to network ID {} on port {}, the network does not exist",
					new Object[] {mac.toString(), guid, port});
		}
	}

	@Override
	public void deleteHost(MacAddress mac, String port) {
		if (log.isDebugEnabled()) {
			log.debug("Removing host {} from port {}", mac, port);
		}
		if (mac == null && port == null) return;
		if (port != null) {
			MacAddress host = portToMac.remove(port);
			if (host != null && vNetsByGuid.get(macToGuid.get(host)) != null)
				vNetsByGuid.get(macToGuid.get(host)).removeHost(host);
			if (host != null)
				macToGuid.remove(host);
		} else if (mac != null) {
			if (!portToMac.isEmpty()) {
				for (Entry<String, MacAddress> entry : portToMac.entrySet()) {
					if (entry.getValue().equals(mac)) {
						if (vNetsByGuid.get(macToGuid.get(entry.getValue())) != null)
							vNetsByGuid.get(macToGuid.get(entry.getValue())).removeHost(entry.getValue());
						portToMac.remove(entry.getKey());
						macToGuid.remove(entry.getValue());
						return;
					}
				}
			}
		}
	}

	// IFloodlightModule

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IVirtualNetworkService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m =
		new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		m.put(IVirtualNetworkService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		l.add(IDeviceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);

		vNetsByGuid = new ConcurrentHashMap<String, VirtualNetwork>();
		nameToGuid = new ConcurrentHashMap<String, String>();
		guidToGateway = new ConcurrentHashMap<String, IPv4Address>();
		gatewayToGuid = new ConcurrentHashMap<IPv4Address, Set<String>>();
		macToGuid = new ConcurrentHashMap<MacAddress, String>();
		portToMac = new ConcurrentHashMap<String, MacAddress>();
		macToGateway = new ConcurrentHashMap<MacAddress, IPv4Address>();
		deviceListener = new DeviceListenerImpl();

	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		restApiService.addRestletRoutable(new VirtualNetworkWebRoutable());
		deviceService.addListener(this.deviceListener);
	}

	// IOFMessageListener

	@Override
	public String getName() {
		return "virtualizer";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// Link discovery should go before us so we don't block LLDPs
		return (type.equals(OFType.PACKET_IN) &&
				(name.equals("linkdiscovery") || (name.equals("devicemanager"))));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// We need to go before forwarding
		return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			return processPacketIn(sw, (OFPacketIn)msg, cntx);
		default:
			break;
		}
		log.warn("Received unexpected message {}", msg);
		return Command.CONTINUE;
	}

	/**
	 * Checks whether the frame is destined to or from a gateway.
	 * @param frame The ethernet frame to check.
	 * @return True if it is to/from a gateway, false otherwise.
	 */
	protected boolean isDefaultGateway(Ethernet frame) {
		if (macToGateway.containsKey(frame.getSourceMACAddress()))
			return true;

		IPv4Address gwIp = macToGateway.get(frame.getDestinationMACAddress());
		if (gwIp != null) {
			MacAddress host = frame.getSourceMACAddress();
			String srcNet = macToGuid.get(host);
			if (srcNet != null) {
				IPv4Address gwIpSrcNet = guidToGateway.get(srcNet);
				if ((gwIpSrcNet != null) && (gwIp.equals(gwIpSrcNet)))
					return true;
			}
		}

		return false;
	}

	/**
	 * Checks to see if two MAC Addresses are on the same network.
	 * @param m1 The first MAC.
	 * @param m2 The second MAC.
	 * @return True if they are on the same virtual network,
	 *          false otherwise.
	 */
	protected boolean oneSameNetwork(MacAddress m1, MacAddress m2) {
		String net1 = macToGuid.get(m1);
		String net2 = macToGuid.get(m2);
		if (net1 == null) return false;
		if (net2 == null) return false;
		return net1.equals(net2);
	}

	/**
	 * Checks to see if an Ethernet frame is a DHCP packet.
	 * @param frame The Ethernet frame.
	 * @return True if it is a DHCP frame, false otherwise.
	 */
	protected boolean isDhcpPacket(Ethernet frame) {
		IPacket payload = frame.getPayload(); // IP
		if (payload == null) return false;
		IPacket p2 = payload.getPayload(); // TCP or UDP
		if (p2 == null) return false;
		IPacket p3 = p2.getPayload(); // Application
		if ((p3 != null) && (p3 instanceof DHCP)) return true;
		return false;
	}

	/**
	 * Processes an OFPacketIn message and decides if the OFPacketIn should be dropped
	 * or the processing should continue.
	 * @param sw The switch the PacketIn came from.
	 * @param msg The OFPacketIn message from the switch.
	 * @param cntx The FloodlightContext for this message.
	 * @return Command.CONTINUE if processing should be continued, Command.STOP otherwise.
	 */
	protected Command processPacketIn(IOFSwitch sw, OFPacketIn msg, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		Command ret = Command.STOP;
		String srcNetwork = macToGuid.get(eth.getSourceMACAddress());
		// If the host is on an unknown network we deny it.
		// We make exceptions for ARP and DHCP.
		if (eth.isBroadcast() || eth.isMulticast() || isDefaultGateway(eth) || isDhcpPacket(eth)) {
			ret = Command.CONTINUE;
		} else if (srcNetwork == null) {
			log.trace("Blocking traffic from host {} because it is not attached to any network.",
					eth.getSourceMACAddress().toString());
			ret = Command.STOP;
		} else if (oneSameNetwork(eth.getSourceMACAddress(), eth.getDestinationMACAddress())) {
			// if they are on the same network continue
			ret = Command.CONTINUE;
		}

		if (log.isTraceEnabled())
			log.trace("Results for flow between {} and {} is {}",
					new Object[] {eth.getSourceMACAddress(), eth.getDestinationMACAddress(), ret});
		/*
		 * TODO - figure out how to still detect gateways while using
		 * drop mods
        if (ret == Command.STOP) {
            if (!(eth.getPayload() instanceof ARP))
                doDropFlow(sw, msg, cntx);
        }
		 */
		return ret;
	}

	/**
	 * Writes a FlowMod to a switch that inserts a drop flow.
	 * @param sw The switch to write the FlowMod to.
	 * @param pi The corresponding OFPacketIn. Used to create the OFMatch structure.
	 * @param cntx The FloodlightContext that gets passed to the switch.
	 */
	protected void doDropFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		if (log.isTraceEnabled()) {
			log.trace("doDropFlow pi={} srcSwitch={}",
					new Object[] { pi, sw });
		}

		if (sw == null) {
			log.warn("Switch is null, not installing drop flowmod for PacketIn {}", pi);
			return;
		}

		// Create flow-mod based on packet-in and src-switch
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowModify();
		List<OFAction> actions = new ArrayList<OFAction>(); // no actions = drop
		U64 cookie = AppCookie.makeCookie(APP_ID, 0);
		fmb.setCookie(cookie)
		.setIdleTimeout(ForwardingBase.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
		.setHardTimeout(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch(pi.getMatch())
		.setActions(actions);

		if (log.isTraceEnabled()) {
			log.trace("write drop flow-mod srcSwitch={} match={} " +
					"pi={} flow-mod={}",
					new Object[] {sw, pi.getMatch(), pi, fmb.build()});
		}
		sw.write(fmb.build());
		return;
	}

	@Override
	public Collection <VirtualNetwork> listNetworks() {
		return vNetsByGuid.values();
	}

	// IDeviceListener
	class DeviceListenerImpl implements IDeviceListener{
		@Override
		public void deviceAdded(IDevice device) {
			if (device.getIPv4Addresses() == null) return;
			for (IPv4Address i : device.getIPv4Addresses()) {
				if (gatewayToGuid.containsKey(i)) {
					MacAddress mac = device.getMACAddress();
					if (log.isDebugEnabled())
						log.debug("Adding MAC {} with IP {} a a gateway",
								mac.toString(),
								i.toString());
					macToGateway.put(mac, i);
				}
			}
		}

		@Override
		public void deviceRemoved(IDevice device) {
			// if device is a gateway remove
			MacAddress mac = device.getMACAddress();
			if (macToGateway.containsKey(mac)) {
				if (log.isDebugEnabled())
					log.debug("Removing MAC {} as a gateway", mac.toString());
				macToGateway.remove(mac);
			}
		}

		@Override
		public void deviceIPV4AddrChanged(IDevice device) {
			// add or remove entry as gateway
			deviceAdded(device);
		}

		@Override
		public void deviceMoved(IDevice device) {
			// ignore
		}

		@Override
		public void deviceVlanChanged(IDevice device) {
			// ignore
		}

		@Override
		public String getName() {
			return VirtualNetworkFilter.this.getName();
		}

		@Override
		public boolean isCallbackOrderingPrereq(String type, String name) {
			return false;
		}

		@Override
		public boolean isCallbackOrderingPostreq(String type, String name) {
			// We need to go before forwarding
			return false;
		}
	}
}
