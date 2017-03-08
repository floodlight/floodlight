package net.floodlightcontroller.cpanalyzer;

import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.IPv6;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.util.OFMessageUtils;

/**
 * A simple module for debugging the control plane. Simply fill in the stuff you
 * want printed for the message type you're interested in, if more than just
 * packet-in/packet-out.
 * 
 * Note that some types (like echos) are handled in the lower-level handshake
 * handler, so you won't be able to receive them here. Only types passed up to
 * modules or from modules written to switches will be shown.
 * 
 * To view the logs, enable TRACE logging for net.floodlightcontroller.cpanalyzer.
 * 
 * @author rizard
 *
 */
public class CPAnalyzer implements ICPAnalyzerService, IFloodlightModule, IOFMessageListener {
    private static final Logger log = LoggerFactory.getLogger(CPAnalyzer.class);

    @Override
    public String getName() {
        return "cpanalyzer";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false; /* none should receive before us */
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return true; /* all should receive after us */
    }

    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        if (log.isTraceEnabled()) {
            switch (msg.getType()) {
            case PACKET_IN:
                Ethernet eth = (Ethernet) cntx.getStorage().get(Controller.CONTEXT_PI_PAYLOAD);
                if (eth == null) {
                    break;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("PacketIn from ").append("sw=").append(sw.getId().toString()).append(", pt=")
                        .append(OFMessageUtils.getInPort((OFPacketIn) msg).getPortNumber());
                log.trace(displayFrame(eth, sb).toString());
                break;
            case PACKET_OUT:
                final OFPacketOut po = (OFPacketOut) msg;
                sb = new StringBuilder();
                sb.append("PacketOut to ").append("sw=").append(sw.getId().toString()).append(", pt=")
                        .append(po.getActions().toString());
                eth = new Ethernet();
                eth.deserialize(po.getData(), 0, po.getData().length);

                log.trace(displayFrame(eth, sb).toString());
                break;
            default:
                break;
            }
        }
        return Command.CONTINUE;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableSet.of(ICPAnalyzerService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(ICPAnalyzerService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableSet.of(IFloodlightProviderService.class); /* for registration */
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        final IFloodlightProviderService flps = context.getServiceImpl(IFloodlightProviderService.class);
        if (flps != null) {
            for (OFType t : OFType.values()) {
                flps.addOFMessageListener(t, this);
            }
        }
    }

    private static StringBuilder displayFrame(final Ethernet eth, final StringBuilder sb) {
        sb.append("\n\t").append("L2 src=").append(eth.getSourceMACAddress().toString()).append(", dst=")
                .append(eth.getDestinationMACAddress().toString()).append(", vlan=").append(eth.getVlanID())
                .append(", ethtype=").append(eth.getEtherType().toString());
        final IPacket l3 = eth.getPayload();
        if (l3 instanceof IPv4) {
            final IPv4 ipv4 = (IPv4) l3;
            sb.append("\n\t").append("L3 src=").append(ipv4.getSourceAddress().toString()).append(", dst=")
                    .append(ipv4.getDestinationAddress().toString()).append(", ttl=").append(ipv4.getTtl())
                    .append(", proto=").append(ipv4.getProtocol().toString());
        } else if (l3 instanceof IPv6) {
            final IPv6 ipv6 = (IPv6) l3;
            sb.append("\n\t").append("L3 src=").append(ipv6.getSourceAddress().toString()).append(", dst=")
                    .append(ipv6.getDestinationAddress().toString()).append(", label=").append(ipv6.getFlowLabel())
                    .append(", proto=").append(ipv6.getNextHeader().toString());
        } else if (l3 instanceof LLDP) {
            final LLDP lldp = (LLDP) l3;
            sb.append("\n\t").append("L3 chid=").append(String.valueOf(lldp.getChassisId().getValue()))
                    .append(", ptid=").append(String.valueOf(lldp.getPortId().getValue()));
        } else if (l3 instanceof ARP) {
            final ARP arp = (ARP) l3;
            sb.append("\n\t").append("L3 smac=").append(arp.getSenderHardwareAddress().toString()).append(", sip=")
                    .append(arp.getSenderProtocolAddress().toString()).append(", tmac=")
                    .append(arp.getTargetHardwareAddress().toString()).append(", sip=")
                    .append(arp.getTargetProtocolAddress().toString());
        } else {
            sb.append("\n\t").append("L3 deserialized=").append(l3.toString());
        }
        return sb.append("/n/n");
    }
}
