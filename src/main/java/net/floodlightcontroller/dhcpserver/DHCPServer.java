package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.dhcpserver.web.DHCPServerWebRoutable;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * SDN DHCP Server
 * @author Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 * @edited Qing Wang, qw@g.clemson.edu on 1/3/2018
 * 
 * The Floodlight Module implementing a DHCP DHCPServer.
 * This module uses {@code DHCPPool} to manage DHCP leases.
 * It intercepts any DHCP/BOOTP requests from connected hosts and
 * handles the replies. The configuration file:
 * 
 * 		floodlight/src/main/resources/floodlightdefault.properties
 * 
 * contains the DHCP options and parameters that can be set for a single
 * subnet. Multiple subnets can be configured with the REST API.
 * 
 * To allow all DHCP request messages to be sent to the controller,
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
 * On a traditional DHCP server, the machine is configured with different 
 * NICs, each with their own statically-assigned IP address/subnet/mask. 
 * The DHCP server matches the network information of each NIC with the DHCP 
 * server's configured subnets and answers the requests accordingly. To 
 * mirror this behavior on a OF network, we can differentiate between subnets 
 * based on a device's attachment point. We can assign subnets for a device
 * per OpenFlow switch or per port per switch.
 *
 * I welcome any feedback or suggestions for improvement!
 * 
 * 
 */
public class DHCPServer implements IOFMessageListener, IFloodlightModule, IDHCPService {
    protected static final Logger log = LoggerFactory.getLogger(DHCPServer.class);
    protected IFloodlightProviderService floodlightProviderService;
    protected IOFSwitchService switchService;
    protected IRestApiService restApiService;

    private static Map<String, DHCPInstance> dhcpInstanceMap;
    private static volatile boolean enableDHCPService = false;

    /**
     * Garbage collector service for DHCP server
     * It handles expired DHCP lease
     */
    private static ScheduledThreadPoolExecutor leasePoliceDispatcher;

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        OFPort inPort = OFMessageUtils.getInPort((OFPacketIn) msg);

        if (!isDHCPEnabled()) return Command.CONTINUE;
        if (!DHCPServerUtils.isDHCPPacketIn(eth)) return Command.CONTINUE;

        NodePortTuple npt = DHCPServerUtils.getNodePortTuple(sw, inPort);
        VlanVid vid = DHCPServerUtils.getVlanVid((OFPacketIn) msg, eth);

        if (!getInstance(npt).isPresent() && !getInstance(vid).isPresent()) {
            log.error("Could not locate DHCP instance for DPID {}, port {}, VLAN {}", new Object[] {sw.getId(), inPort, vid});
            return Command.CONTINUE;
        }

        DHCPInstance instance = null;
        if (getInstance(npt).isPresent()) {
            instance = getInstance(npt).get();
        }
        else {
            instance = getInstance(vid).get();
        }

        // TODO: double-check sychronized usage here
        // Check DHCP pool availability
        synchronized (instance.getDHCPPool()) {
            if (!instance.getDHCPPool().isPoolAvailable()) {
                log.info("DHCP Pool is full, trying to allocate more space");
                return Command.CONTINUE;
            }
        }

        DHCP dhcPayload = DHCPServerUtils.getDHCPayload(eth);
        IPv4Address srcAddr = ((IPv4) eth.getPayload()).getSourceAddress();
        IPv4Address dstAddr = ((IPv4) eth.getPayload()).getDestinationAddress();

        // TODO: any better design for check expired leases?
        instance.getDHCPPool().checkExpiredLeases();
        switch (DHCPServerUtils.getOpcodeType(dhcPayload)) {
            case REQUEST:
                processDhcpRequest(dhcPayload, sw, inPort, instance, srcAddr, dstAddr);
                break;
            default:
                break;
        }

        return Command.CONTINUE;
    }

    private void processDhcpRequest(DHCP dhcpPayload, IOFSwitch sw, OFPort inPort, DHCPInstance instance,
                                    IPv4Address srcAddr, IPv4Address dstAddr) {
        DHCPMessageHandler handler = new DHCPMessageHandler();
        switch (DHCPServerUtils.getMessageType(dhcpPayload)) {
            case DISCOVER:
                log.debug("DHCP DISCOVER message received, start handling... ");
                OFPacketOut dhcpOffer = handler.handleDHCPDiscover(sw, inPort, instance, srcAddr, dhcpPayload);
                sw.write(dhcpOffer);
                break;

            case REQUEST:
                log.debug("DHCP REQUEST message received, start handling... ");
                OFPacketOut dhcpReply = handler.handleDHCPRequest(sw, inPort, instance, dstAddr, dhcpPayload);
                sw.write(dhcpReply);    // either ACK or NAK
                break;

            case RELEASE:   // clear client IP (e.g. client shut down, etc)
                log.debug("DHCP RELEASE message received, start handling... ");
                handler.handleDHCPRelease(instance, dhcpPayload.getClientHardwareAddress());
                break;

            case DECLINE:   // client found assigned IP invalid
                log.debug("DHCP DECLINE message received, start handling... ");
                handler.handleDHCPDecline(instance, dhcpPayload.getClientHardwareAddress());
                break;

            case INFORM:    // client request some information
                log.debug("DHCP INFORM message received, start handling... ");
                OFPacketOut dhcpAck = handler.handleDHCPInform(sw, inPort, instance, dstAddr, dhcpPayload);
                sw.write(dhcpAck);
                break;

            default:
                break;
        }

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
        if (type == OFType.PACKET_IN && name.equals(Forwarding.class.getSimpleName())) {
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> s =
                new HashSet<Class<? extends IFloodlightService>>();
        s.add(IDHCPService.class);
        return s;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IDHCPService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IDHCPService.class);
        l.add(IRestApiService.class);
        return l;
    }

    /**
    private DHCPInstance readDHCPConfig(Map<String, String> configOptions, DHCPInstanceBuilder builder) {
        try{
            builder.setName(configOptions.get("name"))
                    .setSubnetMask(IPv4Address.of(configOptions.get("subnet-mask")))
                    .setStartIP(IPv4Address.of(configOptions.get("lower-ip-range")))
                    .setEndIP(IPv4Address.of(configOptions.get("upper-ip-range")))
                    .setBroadcastIP(IPv4Address.of(configOptions.get("broadcast-address")))
                    .setRouterIP(IPv4Address.of(configOptions.get("router")))
                    .setDomainName(configOptions.get("domain-name"))
                    .setLeaseTimeSec(Integer.parseInt(configOptions.get("default-lease-time")))
                    .setIPforwarding(Boolean.parseBoolean(configOptions.get("ip-forwarding")))
                    .setServerMac(MacAddress.of(configOptions.get("controller-mac")))
                    .setServerID(IPv4Address.of(configOptions.get("controller-ip")));


        } catch (Exception e) {
            log.error("Could not parse DHCP parameters, check DHCP options in floodlightdefault.properties file.");
        }

        // Optional DHCP parameters below
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
                }
                else {
                    macPos = 1;
                    ipPos = 0;
                }
                builder.setStaticAddresses(MacAddress.of(macIpSplit[macPos]), IPv4Address.of(macIpSplit[ipPos]));
            }
        }

        String nptes = configOptions.get("node-port-tuple");
        if (nptes != null) {
            String[] nptCouples = nptes.split("\\s*;\\s*");

            String[] nodeportSplit;
            int i, nodePos, portPos;
            List<NodePortTuple> nptMembers = new ArrayList<>();
            for (i = 0; i < nptCouples.length; i++) {
                nodeportSplit = nptCouples[i].split("\\s*,\\s*");
                // determine which element is sw ID and which is the port number
                if (nodeportSplit[0].length() > nodeportSplit[1].length()) {
                    nodePos = 0;
                    portPos = 1;
                }
                else {
                    nodePos = 1;
                    portPos = 0;
                }
                nptMembers.add(new NodePortTuple(DatapathId.of(nodeportSplit[nodePos]), OFPort.of(Integer.valueOf(nodeportSplit[portPos]))));
            }
            builder.setNptMembers(new HashSet<>(nptMembers));
        }

        // Separate the servers in the comma-delimited list, O.W client will get incorrect option information
        String dnses = configOptions.get("domain-name-servers");
        if (dnses != null) {
            List<IPv4Address> dnsServerIPs = new ArrayList<>();
            for (String dnsServerIP : dnses.split("\\s*,\\s*")) {
                dnsServerIPs.add(IPv4Address.of(dnsServerIP));
            }
            builder.setDNSServers(dnsServerIPs);
        }

        String ntps = configOptions.get("ntp-servers");
        if (ntps != null) {
            List<IPv4Address> ntpServerIPs = new ArrayList<>();
            for (String ntpServerIP : ntps.split("\\s*,\\s*")) {
                ntpServerIPs.add(IPv4Address.of(ntpServerIP));
            }
            builder.setNTPServers(ntpServerIPs);
        }

        // Separate VLAN IDs in the comma-delimited list, then convert to set
        String vlanvids = configOptions.get("vlanvid");
        if (vlanvids != null) {
            List<VlanVid> vlanMembers = new ArrayList<>();
            for (String vlanvid : vlanvids.split("\\s*,\\s*")) {
                vlanMembers.add(VlanVid.ofVlan(Integer.valueOf(vlanvid)));
            }
            builder.setVlanMembers(new HashSet<>(vlanMembers));
        }

        String enableDHCP = configOptions.get("enable");
        if (enableDHCP != null && !enableDHCP.isEmpty()) {
            enableDHCP();
        }
        else {
            disableDHCP();
        }

        return builder.build();
    }
    */

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        this.floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        this.switchService = context.getServiceImpl(IOFSwitchService.class);
        this.restApiService = context.getServiceImpl(IRestApiService.class);
        dhcpInstanceMap = new HashMap<>();

//        DHCPInstance instance = readDHCPConfig(context.getConfigParams(this), DHCPInstance.createInstance());
//        dhcpInstanceMap.put(instance.getName(), instance);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
        restApiService.addRestletRoutable(new DHCPServerWebRoutable());

        leasePoliceDispatcher = new ScheduledThreadPoolExecutor(1);

    }

    @Override
    public void enableDHCP() {
        enableDHCPService = true;
    }

    @Override
    public void disableDHCP() {
        enableDHCPService = false;
    }

    @Override
    public boolean isDHCPEnabled() {
        return enableDHCPService;
    }

    @Override
    public Optional<DHCPInstance> getInstance(String name) {
        return dhcpInstanceMap.values().stream()
                .filter(instance -> instance.getName().contains(name))
                .findAny();
    }

    @Override
    public Optional<DHCPInstance> getInstance(IPv4Address ip) {
        return dhcpInstanceMap.values().stream()
                .filter(dhcpInstance -> dhcpInstance.getDHCPPool().isIPBelongsToPool(ip))
                .findAny();
    }

    @Override
    public Optional<DHCPInstance> getInstance(NodePortTuple npt) {
        return dhcpInstanceMap.values().stream()
                .filter(instance -> instance.getNptMembers().contains(npt))
                .findAny();
    }

    @Override
    public Optional<DHCPInstance> getInstance(VlanVid vid) {
        return dhcpInstanceMap.values().stream()
                .filter(instance -> instance.getVlanMembers().contains(vid))
                .findAny();
    }

    @Override
    public Collection<DHCPInstance> getInstances() {
        return dhcpInstanceMap.values();
    }

    @Override
    public void addInstance(DHCPInstance instance) {
        dhcpInstanceMap.put(instance.getName(), instance);
    }

    @Override
    public boolean deleteInstance(String name) {
        if (getInstance(name).isPresent()) {
            dhcpInstanceMap.remove(name);
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void deleteAllInstances() {
        dhcpInstanceMap.clear();
    }

    @Override
    public DHCPInstance updateInstance(String name, DHCPInstance newInstance) {
        DHCPInstance old = dhcpInstanceMap.get(name);
        newInstance = old.getBuilder().setSubnetMask(newInstance.getSubnetMask())
                        .setStartIP(newInstance.getStartIPAddress())
                        .setEndIP(newInstance.getEndIPAddress())
                        .setBroadcastIP(newInstance.getBroadcastIP())
                        .setRouterIP(newInstance.getRouterIP())
                        .setDomainName(newInstance.getDomainName())
                        .setLeaseTimeSec(newInstance.getLeaseTimeSec())
                        .setIPforwarding(newInstance.getIpforwarding())
                        .setServerMac(newInstance.getServerMac())
                        .setServerID(newInstance.getServerID())
                        .build();

        return newInstance;
    }
}