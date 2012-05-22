package net.floodlightcontroller.virtualnetwork.forwarding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.virtualnetwork.IVirtualNetworkService;

/**
 * A simple Layer 2 (MAC based) network virtualization module. This module allows
 * you to create simple L2 networks (host + gateway) and will drop traffic if
 * they are not on the same virtual network. This module does not support overlapping
 * MAC address or IP address space. It also limits you to one default gateway per
 * virtual network. It also must work in conjunction with the forwarding module.
 * @author alexreimers
 */
public class VirtualNetworkFilter 
    implements IFloodlightModule, IVirtualNetworkService, IOFMessageListener {
    protected static Logger log = LoggerFactory.getLogger(VirtualNetworkFilter.class);
    
    private final short FLOW_MOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
    private final short APP_ID = 10; // TODO - check this
    
    // Our dependencies
    IFloodlightProviderService floodlightProvider;
    IRestApiService restApi;
    
    // Our internal state
    protected Map<String, String> nameToGuid; // Logical name -> Network ID
    protected Map<String, Integer> guidToGateway; // Network ID -> Gateway IP
    protected Map<Integer, String> gatewayToGuid; // Gateway IP -> Network ID
    protected Map<MACAddress, String> macToGuid; // Host MAC -> Network ID
    
    
    protected void addGateway(String guid, Integer ip) {
        if (ip.intValue() != 0) {
            guidToGateway.put(guid, ip);
            gatewayToGuid.put(ip, guid);
        }
    }
    
    protected void deleteGateway(String guid) {
        Integer gwIp = guidToGateway.remove(guid);
        if (gwIp == null) return;
        gatewayToGuid.remove(gwIp);
    }
    
    // IVirtualNetworkService
    
    @Override
    public void createNetwork(String network, String guid, Integer gateway) {
        if (log.isDebugEnabled()) 
            log.debug("Creating network {} with ID {} and gateway {}", 
                      new Object[] {network, guid, IPv4.fromIPv4Address(gateway)});
        if (!nameToGuid.containsKey(network)) {
            nameToGuid.put(network, guid);
            addGateway(guid, gateway);
        } else {
            if (log.isDebugEnabled())
                log.debug("Network {} already exists, ignoring", network);
        }
    }

    @Override
    public void deleteNetwork(String name) {
        if (log.isDebugEnabled()) log.debug("Deleting network name {}", name);
        if (nameToGuid.containsKey(name)) {
            String guid = nameToGuid.remove(name);
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
            }
        }
    }

    @Override
    public void addHost(MACAddress mac, String network) {
        String guid = nameToGuid.get(network);
        if (guid != null) {
            if (log.isDebugEnabled()) {
                log.debug("Adding {} to network {}", mac, network);
            }
            macToGuid.put(mac, guid); // TODO what if a mapping exists?
        } else {
            // TODO - throw an exception
        }
    }

    @Override
    public void deleteHost(MACAddress mac, String network) {
        if (macToGuid.remove(mac) != null) {
            if (log.isDebugEnabled()) {
                log.debug("Removing {} from network {}", mac, network);
            }
        } else {
            log.warn("Tried to remove {} from network {}, but no mapping was found",
                     mac, network);
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
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
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
        gatewayToGuid = new ConcurrentHashMap<Integer, String>();
        macToGuid = new ConcurrentHashMap<MACAddress, String>();
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
        // We don't care who goes before us
        return false;
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
    
    
    protected Command processPacketIn(IOFSwitch sw, OFPacketIn msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        if (eth.isBroadcast() || eth.isMulticast()) {
            // TODO - handle this shit somehow
            return Command.CONTINUE;
        }

        Command ret = Command.STOP;
        String srcNetwork = macToGuid.get(eth.getSourceMAC());
        if (oneSameNetwork(eth.getSourceMAC(), eth.getDestinationMAC())) {
            // if they are on the same network continue
            ret = Command.CONTINUE;
        } else if ((eth.getPayload() instanceof IPv4) 
                && isDefaultGatewayIp(srcNetwork, (IPv4)eth.getPayload())) {
            // or if the host is talking to the gateway continue
            ret = Command.CONTINUE;
        }
        
        if (ret == Command.CONTINUE) {
            if (log.isTraceEnabled()) {
                log.trace("Allowing flow between {} and {} on network {}", 
                          new Object[] {eth.getSourceMAC(), eth.getDestinationMAC(), srcNetwork});
            }
        } else if (ret == Command.STOP) {
            // they are on different virtual networks so we drop the flow
            doDropFlow(sw, msg, cntx);
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
        match.loadFromPacket(pi.getPacketData(), pi.getInPort(), sw.getId());
        List<OFAction> actions = new ArrayList<OFAction>(); // no actions = drop
        long cookie = AppCookie.makeCookie(APP_ID, 0);
        fm.setCookie(cookie)
        .setIdleTimeout(FLOW_MOD_DEFAULT_IDLE_TIMEOUT)
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
