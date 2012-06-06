package net.floodlightcontroller.virtualnetwork.forwarding;

import java.io.IOException;
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

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.util.HexString;
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
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.virtualnetwork.IVirtualNetworkService;

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
    
    private final short FLOW_MOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
    private final short APP_ID = 20;
    
    // Our dependencies
    IFloodlightProviderService floodlightProvider;
    IRestApiService restApi;
    
    // Our internal state
    protected Map<String, String> nameToGuid; // Logical name -> Network ID
    protected Map<String, Integer> guidToGateway; // Network ID -> Gateway IP
    protected Map<Integer, Set<String>> gatewayToGuid; // Gateway IP -> Network ID
    protected Map<MACAddress, Integer> macToGateway; // Gateway MAC -> Gateway IP
    protected Map<MACAddress, String> macToGuid; // Host MAC -> Network ID
    protected Map<String, MACAddress> portToMac; // Host MAC -> logical port name
    
    protected void addGateway(String guid, Integer ip) {
        if (ip.intValue() != 0) {
            guidToGateway.put(guid, ip);
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
    
    protected void deleteGateway(String guid) {
        Integer gwIp = guidToGateway.remove(guid);
        if (gwIp == null) return;
        Set<String> gSet = gatewayToGuid.get(gwIp);
        gSet.remove(guid);
    }
    
    // IVirtualNetworkService
    
    @Override
    public void createNetwork(String guid, String network, Integer gateway) {
        if (log.isDebugEnabled()) {
            String gw = null;
            try {
                gw = IPv4.fromIPv4Address(gateway);
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
        nameToGuid.put(network, guid);
        // If they don't specify a new gateway the old one will be preserved
        if ((gateway != null) && (gateway != 0))
            addGateway(guid, gateway);
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
            log.warn("Could not delete network with ID {}, network doesn't exist",
                     guid);
        }
        
        if (log.isDebugEnabled()) 
            log.debug("Deleting network with name {} ID {}", name, guid);
        
        nameToGuid.remove(name);
        deleteGateway(guid);
        Collection<MACAddress> deleteList = new ArrayList<MACAddress>();
        for (MACAddress host : macToGuid.keySet()) {
            if (macToGuid.get(host).equals(guid)) {
                deleteList.add(host);
            }
        }
        for (MACAddress mac : deleteList) {
            if (log.isDebugEnabled()) {
                log.debug("Removing host {} from network {}", 
                          HexString.toHexString(mac.toBytes()), guid);
            }
            macToGuid.remove(mac);
            for (Entry<String, MACAddress> entry : portToMac.entrySet()) {
                if (entry.getValue().equals(mac)) {
                    portToMac.remove(entry.getKey());
                    break;
                }
            }
        }
    }

    @Override
    public void addHost(MACAddress mac, String guid, String port) {
        if (guid != null) {
            if (log.isDebugEnabled()) {
                log.debug("Adding {} to network ID {} on port {}",
                          new Object[] {mac, guid, port});
            }
            // We ignore old mappings
            macToGuid.put(mac, guid);
            portToMac.put(port, mac);
        } else {
            log.warn("Could not add MAC {} to network ID {} on port {}, the network does not exist",
                     new Object[] {mac, guid, port});
        }
    }

    @Override
    public void deleteHost(MACAddress mac, String port) {
        if (log.isDebugEnabled()) {
            log.debug("Removing host {} from port {}", mac, port);
        }
        if (mac == null && port == null) return;
        if (port != null) {
            MACAddress host = portToMac.remove(port);
            macToGuid.remove(host);
        } else if (mac != null) {
            if (!portToMac.isEmpty()) {
                for (Entry<String, MACAddress> entry : portToMac.entrySet()) {
                    if (entry.getValue().equals(mac)) {
                        portToMac.remove(entry.getKey());
                        macToGuid.remove(entry.getValue());
                        return;
                    }
                }
            }
        }
    }

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
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)  
                                 throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        
        nameToGuid = new ConcurrentHashMap<String, String>();
        guidToGateway = new ConcurrentHashMap<String, Integer>();
        gatewayToGuid = new ConcurrentHashMap<Integer, Set<String>>();
        macToGuid = new ConcurrentHashMap<MACAddress, String>();
        portToMac = new ConcurrentHashMap<String, MACAddress>();
        macToGateway = new ConcurrentHashMap<MACAddress, Integer>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApi.addRestletRoutable(new VirtualNetworkWebRoutable());
    }

    // IOFMessageListener
    
    @Override
    public String getName() {
        return "virtualizer";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // Link discovery should go before us so we don't block LLDPs
        return (type.equals(OFType.PACKET_IN) && name.equals("linkdiscovery"));
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
        }
        log.warn("Received unexpected message {}", msg);
        return Command.CONTINUE;
    }
    
    protected boolean isDefaultGatewayIp(String srcDevNetwork, IPv4 packet) {
        return guidToGateway.get(srcDevNetwork).equals(packet.getDestinationAddress());
    }
    
    protected boolean oneSameNetwork(MACAddress src, MACAddress dst) {
        String srcNetwork = macToGuid.get(src);
        String dstNetwork = macToGuid.get(dst);
        if (srcNetwork == null) return false;
        if (dstNetwork == null) return false;
        return srcNetwork.equals(dstNetwork);
    }
    
    protected boolean isDhcpPacket(Ethernet frame) {
        IPacket payload = frame.getPayload(); // IP
        if (payload == null) return false;
        IPacket p2 = payload.getPayload(); // TCP or UDP
        if (p2 == null) return false;
        IPacket p3 = p2.getPayload(); // Application
        if ((p3 != null) && (p3 instanceof DHCP)) return true;
        return false;
    }
    
    protected Command processPacketIn(IOFSwitch sw, OFPacketIn msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        Command ret = Command.STOP;
        String srcNetwork = macToGuid.get(eth.getSourceMAC());
        // If the host is on an unknown network we deny it.
        // We make exceptions for ARP in order to handle gateways
        // and for DHCP.
        if (isDhcpPacket(eth)) {
            log.trace("Letting DHCP traffic through");
            ret = Command.CONTINUE;
        } else if (srcNetwork == null && !(eth.getPayload() instanceof ARP)) {
            log.debug("Blocking traffic from host {} because it is not attached to any network.",
                      HexString.toHexString(eth.getSourceMACAddress()));
            ret = Command.STOP;
        } else {
            if (eth.isBroadcast() || eth.isMulticast() || (eth.getPayload() instanceof DHCP)) {
                return Command.CONTINUE;
            }
            
            if (oneSameNetwork(eth.getSourceMAC(), eth.getDestinationMAC())) {
                // if they are on the same network continue
                ret = Command.CONTINUE;
            } else if ((eth.getPayload() instanceof IPv4) 
                    && isDefaultGatewayIp(srcNetwork, (IPv4)eth.getPayload())) {
                // or if the host is talking to the gateway continue
                ret = Command.CONTINUE;
            } else if (eth.getPayload() instanceof ARP){
                // We have to check here if it is an ARP reply from the default gateway
                ARP arp = (ARP) eth.getPayload();
                if (arp.getProtocolType() != ARP.PROTO_TYPE_IP) {
                    ret = Command.CONTINUE;
                } else if (arp.getOpCode() == ARP.OP_REPLY) {
                    int ip = IPv4.toIPv4Address(arp.getSenderProtocolAddress());
                    for (Integer i : gatewayToGuid.keySet()) {
                        if (i.intValue() == ip) {
                            // Learn the default gateway MAC
                            if (log.isDebugEnabled()) {
                                log.debug("Adding {} with IP {} as a gateway",
                                          HexString.toHexString(arp.getSenderHardwareAddress()),
                                          IPv4.fromIPv4Address(ip));
                            }
                            macToGateway.put(new MACAddress(arp.getSenderHardwareAddress()), ip);
                            // Now we see if it's allowed for this packet
                            String hostNet = macToGuid.get(new MACAddress(eth.getDestinationMACAddress()));
                            Set<String> gwGuids = gatewayToGuid.get(ip);
                            if ((gwGuids != null) && (gwGuids.contains(hostNet)))
                                ret = Command.CONTINUE;
                            break;
                        }
                    }
                }
            }
            
            if (ret == Command.CONTINUE) {
                if (log.isTraceEnabled()) {
                    log.trace("Allowing flow between {} and {} on network {}", 
                              new Object[] {eth.getSourceMAC(), eth.getDestinationMAC(), srcNetwork});
                }
            } else if (ret == Command.STOP) {
                // they are on different virtual networks so we drop the flow
                if (log.isTraceEnabled()) {
                    log.trace("Dropping flow between {} and {} because they are on different networks", 
                              new Object[] {eth.getSourceMAC(), eth.getDestinationMAC()});
                }
                doDropFlow(sw, msg, cntx);
            }
        }
        
        return ret;
    }
    
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
        OFFlowMod fm = 
            (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        List<OFAction> actions = new ArrayList<OFAction>(); // no actions = drop
        long cookie = AppCookie.makeCookie(APP_ID, 0);
        fm.setCookie(cookie)
        .setIdleTimeout(FLOW_MOD_DEFAULT_IDLE_TIMEOUT)
        .setHardTimeout((short) 0)
        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
        .setMatch(match)
        .setActions(actions)
        .setLengthU(OFFlowMod.MINIMUM_LENGTH);
        fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
        try {
            if (log.isTraceEnabled()) {
                log.trace("write drop flow-mod srcSwitch={} match={} " + 
                          "pi={} flow-mod={}",
                          new Object[] {sw, match, pi, fm});
            }
            sw.write(fm, cntx);
        } catch (IOException e) {
            log.error("Failure writing drop flow mod", e);
        }
        return;
    }
}
