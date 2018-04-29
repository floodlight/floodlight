package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.dhcpserver.web.DHCPServerWebRoutable;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SDN DHCP Server
 * @author Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 * @edited Qing Wang, qw@g.clemson.edu on 1/3/2018
 * 
 * This module implementing a DHCP DHCPServer. The module can be configured with the REST API.
 * 
 */
public class DHCPServer implements IOFMessageListener, IOFSwitchListener, IFloodlightModule, IDHCPService {
    protected static final Logger log = LoggerFactory.getLogger(DHCPServer.class);
    protected IFloodlightProviderService floodlightProviderService;
    protected IOFSwitchService switchService;
    protected IRestApiService restApiService;
    protected ITopologyService topologyService;
    protected IStaticEntryPusherService staticEntryPusherService;

    private static Map<String, DHCPInstance> dhcpInstanceMap;
    private static volatile boolean enableDHCPService = false;
    private static volatile boolean enableDHCPDynamicService = false;

    private static ScheduledThreadPoolExecutor leasePoliceDispatcher;
    private static long DHCP_SERVER_CHECK_EXPIRED_LEASE_PERIOD_SECONDS = 10; // 10 secs as default

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        OFPort inPort = OFMessageUtils.getInPort((OFPacketIn) msg);

        if (!isDHCPEnabled()) return Command.CONTINUE;
        if (!DHCPServerUtils.isDHCPPacketIn(eth)) return Command.CONTINUE;

        NodePortTuple npt = DHCPServerUtils.getNodePortTuple(sw, inPort);
        VlanVid vid = DHCPServerUtils.getVlanVid((OFPacketIn) msg, eth);

        if (!getInstance(npt).isPresent() && !getInstance(sw.getId()).isPresent() && !getInstance(vid).isPresent()) {
            log.warn("Could not locate DHCP instance for DPID {}, port {} or VLAN {}", new Object[] {sw.getId(), inPort, vid});
            return Command.CONTINUE;
        }

        DHCPInstance instance = null;
        if (getInstance(sw.getId()).isPresent()) {
            instance = getInstance(sw.getId()).get();
        }
        else if (getInstance(npt).isPresent()) {
            instance = getInstance(npt).get();
        }
        else {
            instance = getInstance(vid).get();
        }

        synchronized (instance.getDHCPPool()) {
            if (!instance.getDHCPPool().isPoolAvailable()) {
                log.info("DHCP Pool is full, trying to allocate more space");
                return Command.CONTINUE;
            }
        }

        DHCP dhcPayload = DHCPServerUtils.getDHCPayload(eth);
        IPv4Address srcAddr = ((IPv4) eth.getPayload()).getSourceAddress();
        IPv4Address dstAddr = ((IPv4) eth.getPayload()).getDestinationAddress();
        MacAddress srcMac = eth.getSourceMACAddress();

        switch (DHCPServerUtils.getOpcodeType(dhcPayload)) {
            case REQUEST:
                processDhcpRequest(dhcPayload, sw, inPort, instance, srcAddr, dstAddr, srcMac);
                return Command.STOP;
            default:
                break;
        }

        return Command.CONTINUE;
    }

    private void processDhcpRequest(DHCP dhcpPayload, IOFSwitch sw, OFPort inPort, DHCPInstance instance,
                                    IPv4Address srcAddr, IPv4Address dstAddr, MacAddress srcMac) {
        DHCPMessageHandler handler = new DHCPMessageHandler();
        switch (DHCPServerUtils.getMessageType(dhcpPayload)) {
            case DISCOVER:
                OFPacketOut dhcpOffer = handler.handleDHCPDiscover(sw, inPort, instance, srcAddr, dhcpPayload, enableDHCPDynamicService);
                log.info("DHCP DISCOVER message received from switch {} for client interface {}, handled by dhcp instance {} ... ",
                        new Object[]{sw.getId().toString(), srcMac.toString(), instance.getName()});
                sw.write(dhcpOffer);
                break;

            case REQUEST:
                OFPacketOut dhcpReply = handler.handleDHCPRequest(sw, inPort, instance, dstAddr, dhcpPayload);
                log.info("DHCP REQUEST message received from switch {} for client interface {}, handled by dhcp instance {} ... ",
                        new Object[]{sw.getId().toString(), srcMac.toString(), instance.getName()});
                sw.write(dhcpReply);    // either ACK or NAK
                break;

            case RELEASE:   // clear client IP (e.g. client shut down, etc)
                log.debug("DHCP RELEASE message received from switch {}, start handling... ", sw.getId().toString());
                handler.handleDHCPRelease(instance, dhcpPayload.getClientHardwareAddress());
                break;

            case DECLINE:   // client found assigned IP invalid
                log.debug("DHCP DECLINE message received from switch {}, start handling... ", sw.getId().toString());
                handler.handleDHCPDecline(instance, dhcpPayload.getClientHardwareAddress());
                break;

            case INFORM:    // client request some information
                log.debug("DHCP INFORM message received from switch {}, start handling... ", sw.getId().toString());
                OFPacketOut dhcpAck = handler.handleDHCPInform(sw, inPort, instance, dstAddr, dhcpPayload);
                sw.write(dhcpAck);
                break;

            default:
                break;
        }

    }

    @Override
    public String getName() {
        return "dhcpserver";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
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


//    private DHCPInstance readDHCPConfig(Map<String, String> configOptions, DHCPInstanceBuilder builder) {
//    }


    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        this.floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        this.switchService = context.getServiceImpl(IOFSwitchService.class);
        this.restApiService = context.getServiceImpl(IRestApiService.class);
        this.topologyService = context.getServiceImpl(ITopologyService.class);
        this.staticEntryPusherService = context.getServiceImpl(IStaticEntryPusherService.class);
        dhcpInstanceMap = new HashMap<>();

//        DHCPInstance instance = readDHCPConfig(context.getConfigParams(this), DHCPInstance.createInstance());
//        dhcpInstanceMap.put(instance.getName(), instance);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
        switchService.addOFSwitchListener(this);
        restApiService.addRestletRoutable(new DHCPServerWebRoutable());

        /**
         * Thread for DHCP server that periodically check expired DHCP lease
         *
         * The period of the check for expired lease, in seconds, is specified either in floodlightdefault.properties,
         * or through REST API to setup.
         */
        leasePoliceDispatcher = new ScheduledThreadPoolExecutor(1);
        leasePoliceDispatcher.scheduleAtFixedRate(() -> {
            for (DHCPInstance instance : dhcpInstanceMap.values()) {
                synchronized (instance.getDHCPPool()) {
                    instance.getDHCPPool().checkExpiredLeases();
//                    instance.getDHCPPool().clearExpiredLeases();
                }
            }
        }, 10, DHCP_SERVER_CHECK_EXPIRED_LEASE_PERIOD_SECONDS, TimeUnit.SECONDS);

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
    public void enableDHCPDynamic() {
        enableDHCPDynamicService = true;
    }

    @Override
    public void disableDHCDynamic() {
        enableDHCPDynamicService = false;
    }

    @Override
    public boolean isDHCPDynamicEnabled() {
        return enableDHCPDynamicService;
    }

    @Override
    public boolean isDHCPEnabled() {
        return enableDHCPService;
    }

    @Override
    public void setCheckExpiredLeasePeriod(long timeSec) {
        DHCP_SERVER_CHECK_EXPIRED_LEASE_PERIOD_SECONDS = timeSec;
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
    public Optional<DHCPInstance> getInstance(DatapathId dpid) {
        return dhcpInstanceMap.values().stream()
                .filter(instance -> instance.getSwitchMembers().contains(dpid))
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

    @Override
    public void switchAdded(DatapathId switchId) { }

    @Override
    public void switchRemoved(DatapathId switchId) {
        dhcpInstanceMap.values().stream()
                .forEach(instance -> instance.removeSwitchFromInstance(switchId));
        log.info("Handle switchRemoved. Switch {} removed from dhcp instance", switchId.toString());
    }

    @Override
    public void switchActivated(DatapathId switchId) { }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) { }

    @Override
    public void switchChanged(DatapathId switchId) { }

    @Override
    public void switchDeactivated(DatapathId switchId) { }

}