/**
 *    Copyright 2011, Big Switch Networks, Inc. 
 *    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.routing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.TimedCache;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ForwardingBase implements
    IOFMessageListener,
    IDeviceListener {
    protected static Logger log =
            LoggerFactory.getLogger(ForwardingBase.class);

    public static final short FLOWMOD_DEFAULT_HARD_TIMEOUT = 5; // in seconds

    protected IFloodlightProviderService floodlightProvider;
    protected IDeviceService deviceManager;
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected ICounterStoreService counterStore;
    
    // for broadcast loop suppression
    protected boolean broadcastCacheFeature = true;
    public final int prime = 2633;  // for hash calculation
    public TimedCache<Long> broadcastCache =
    		new TimedCache<Long>(100, 5*1000);  // 5 seconds interval;

    // flow-mod - for use in the cookie
    public static final int FORWARDING_APP_ID = 2; // TODO: This must be managed
                                                   // by a global APP_ID class
    public long appCookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);
    
    // Comparator for sorting by SwitchCluster
    public Comparator<SwitchPort> clusterIdComparator =
            new Comparator<SwitchPort>() {
                @Override
                public int compare(SwitchPort d1, SwitchPort d2) {
                    Long d1ClusterId = 
                            topology.getL2DomainId(d1.getSwitchDPID());
                    Long d2ClusterId = 
                            topology.getL2DomainId(d2.getSwitchDPID());
                    return d1ClusterId.compareTo(d2ClusterId);
                }
            };

    /**
     * Adds a listener for devicemanager and registers for PacketIns.
     */
    public void startUp() {
        deviceManager.addListener(this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    /**
     * Returns the application name "forwarding".
     */
    @Override
    public String getName() {
        return "forwarding";
    }

    /**
     * All subclasses must define this function if they want any specific
     * forwarding action
     * 
     * @param sw
     *            Switch that the packet came in from
     * @param pi
     *            The packet that came in
     * @param decision
     *            Any decision made by a policy engine
     */
    public abstract Command
            processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                   IRoutingDecision decision,
                                   FloodlightContext cntx);

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                IRoutingDecision decision = null;
                if (cntx != null)
                     decision =
                             IRoutingDecision.rtStore.get(cntx,
                                                          IRoutingDecision.CONTEXT_DECISION);

                return this.processPacketInMessage(sw,
                                                   (OFPacketIn) msg,
                                                   decision,
                                                   cntx);
        }
        log.error("received an unexpected message {} from switch {}",
                  msg,
                  sw);
        return Command.CONTINUE;
    }

    /**
     * Push routes from back to front
     * @param route Route to push
     * @param match OpenFlow fields to match on
     * @param srcSwPort Source switch port for the first hop
     * @param dstSwPort Destination switch port for final hop
     * @param bufferId BufferId of the original PacketIn
     * @param cookie The cookie to set in each flow_mod
     * @param cntx The floodlight context
     * @param reqeustFlowRemovedNotifn if set to true then the switch would
     * send a flow mod removal notification when the flow mod expires
     * @param doFlush if set to true then the flow mod would be immediately
     *        written to the switch
     * @param flowModCommand flow mod. command to use, e.g. OFFlowMod.OFPFC_ADD,
     *        OFFlowMod.OFPFC_MODIFY etc.
     * @return srcSwitchIincluded True if the source switch is included in this route
     */
    public boolean pushRoute(Route route, OFMatch match, 
                             Integer wildcard_hints,
                             int bufferId,
                             OFPacketIn pi,
                             long pinSwitch,
                             long cookie, 
                             FloodlightContext cntx,
                             boolean reqeustFlowRemovedNotifn,
                             boolean doFlush,
                             short   flowModCommand) {

        boolean srcSwitchIncluded = false;
        OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);
        OFActionOutput action = new OFActionOutput();
        action.setMaxLength((short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        fm.setIdleTimeout((short)5)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(cookie)
            .setCommand(flowModCommand)
            .setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        List<NodePortTuple> switchPortList = route.getPath();

        for (int indx = switchPortList.size()-1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            long switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = floodlightProvider.getSwitches().get(switchDPID);
            if (sw == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to push route, switch at DPID {} " +
                            "not available", switchDPID);
                }
                return srcSwitchIncluded;
            }

            // set the match.
            fm.setMatch(wildcard(match, sw, wildcard_hints));

            // set buffer id if it is the source switch
            if (1 == indx) {
                // Set the flag to request flow-mod removal notifications only for the
                // source switch. The removal message is used to maintain the flow
                // cache. Don't set the flag for ARP messages - TODO generalize check
                if ((reqeustFlowRemovedNotifn)
                        && (match.getDataLayerType() != Ethernet.TYPE_ARP)) {
                    fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
                    match.setWildcards(fm.getMatch().getWildcards());
                }
            }

            short outPort = switchPortList.get(indx).getPortId();
            short inPort = switchPortList.get(indx-1).getPortId();
            // set input and output ports on the switch
            fm.getMatch().setInputPort(inPort);
            ((OFActionOutput)fm.getActions().get(0)).setPort(outPort);

            try {
                counterStore.updatePktOutFMCounterStore(sw, fm);
                if (log.isTraceEnabled()) {
                    log.trace("Pushing Route flowmod routeIndx={} " + 
                            "sw={} inPort={} outPort={}",
                            new Object[] {indx,
                                          sw,
                                          fm.getMatch().getInputPort(),
                                          outPort });
                }
                sw.write(fm, cntx);
                if (doFlush) {
                    sw.flush();
                }

                // Push the packet out the source switch
                if (sw.getId() == pinSwitch) {
                    // TODO: Instead of doing a packetOut here we could also 
                    // send a flowMod with bufferId set.... 
                    pushPacket(sw, match, pi, outPort, cntx);
                    srcSwitchIncluded = true;
                }
            } catch (IOException e) {
                log.error("Failure writing flow mod", e);
            }

            try {
                fm = fm.clone();
            } catch (CloneNotSupportedException e) {
                log.error("Failure cloning flow mod", e);
            }
        }

        return srcSwitchIncluded;
    }

    protected OFMatch wildcard(OFMatch match, IOFSwitch sw,
                               Integer wildcard_hints) {
        if (wildcard_hints != null) {
            return match.clone().setWildcards(wildcard_hints.intValue());
        }
        return match.clone();
    }
    
    /**
     * Pushes a packet-out to a switch. If bufferId != BUFFER_ID_NONE we 
     * assume that the packetOut switch is the same as the packetIn switch
     * and we will use the bufferId 
     * Caller needs to make sure that inPort and outPort differs
     * @param packet    packet data to send
     * @param sw        switch from which packet-out is sent
     * @param bufferId  bufferId
     * @param inPort    input port
     * @param outPort   output port
     * @param cntx      context of the packet
     */
    public void pushPacket(IPacket packet, 
                           IOFSwitch sw,
                           int bufferId,
                           short inPort,
                           short outPort, 
                           FloodlightContext cntx) {
        
        
        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} inPort={} outPort={}", 
                      new Object[] {sw, inPort, outPort});
        }

        OFPacketOut po =
                (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                                .getMessage(OFType.PACKET_OUT);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(outPort, (short) 0xffff));

        po.setActions(actions)
          .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength =
                (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

        // set buffer_id, in_port
        po.setBufferId(bufferId);
        po.setInPort(inPort);

        // set data - only if buffer_id == -1
        if (po.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            if (packet == null) {
                log.error("BufferId is set but no packet data is null. " +
                		"Cannot send packetOut. " +
                        "srcSwitch={} inPort={} outPort={}",
                        new Object[] {sw, inPort, outPort});
                return;
            }
            byte[] packetData = packet.serialize();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }

        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStore(sw, po);
            sw.write(po, cntx);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }

    /**
     * Pushes a packet-out to a switch.  The assumption here is that
     * the packet-in was also generated from the same switch.  Thus, if the input
     * port of the packet-in and the outport are the same, the function will not 
     * push the packet-out.
     * @param sw        switch that generated the packet-in, and from which packet-out is sent
     * @param match     OFmatch
     * @param pi        packet-in
     * @param outport   output port
     * @param cntx      context of the packet
     */
    public void pushPacket(IOFSwitch sw, OFMatch match, OFPacketIn pi, 
                           short outport, FloodlightContext cntx) {
        
        if (pi == null) {
            return;
        }
        
        // The assumption here is (sw) is the switch that generated the 
        // packet-in. If the input port is the same as output port, then
        // the packet-out should be ignored.
        if (pi.getInPort() == outport) {
            if (log.isDebugEnabled()) {
                log.debug("Attempting to do packet-out to the same " + 
                          "interface as packet-in. Dropping packet. " + 
                          " SrcSwitch={}, match = {}, pi={}", 
                          new Object[]{sw, match, pi});
                return;
            }
        }
        
        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} match={} pi={}", 
                      new Object[] {sw, match, pi});
        }

        OFPacketOut po =
                (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                                .getMessage(OFType.PACKET_OUT);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(outport, (short) 0));

        po.setActions(actions)
          .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength =
                (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

        // set buffer_id, in_port
        po.setBufferId(pi.getBufferId());
        po.setInPort(pi.getInPort());

        // set data - only if buffer_id == -1
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }

        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStore(sw, po);
            sw.write(po, cntx);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }

    
    /**
     * Write packetout message to sw with output actions to one or more
     * output ports with inPort/outPorts passed in.
     * @param packetData
     * @param sw
     * @param inPort
     * @param ports
     * @param cntx
     */
    public void packetOutMultiPort(byte[] packetData,
                                   IOFSwitch sw,
                                   short inPort,
                                   Set<Integer> outPorts,
                                   FloodlightContext cntx) {
        //setting actions
        List<OFAction> actions = new ArrayList<OFAction>();

        Iterator<Integer> j = outPorts.iterator();

        while (j.hasNext())
        {
            actions.add(new OFActionOutput(j.next().shortValue(), 
                                           (short) 0));
        }

        OFPacketOut po = 
                (OFPacketOut) floodlightProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_OUT);
        po.setActions(actions);
        po.setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH * 
                outPorts.size()));

        // set buffer-id to BUFFER_ID_NONE, and set in-port to OFPP_NONE
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(inPort);

        // data (note buffer_id is always BUFFER_ID_NONE) and length
        short poLength = (short)(po.getActionsLength() + 
                OFPacketOut.MINIMUM_LENGTH);
        poLength += packetData.length;
        po.setPacketData(packetData);
        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStore(sw, po);
            if (log.isTraceEnabled()) {
                log.trace("write broadcast packet on switch-id={} " + 
                        "interfaces={} packet-out={}",
                        new Object[] {sw.getId(), outPorts, po});
            }
            sw.write(po, cntx);

        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }
    
    /** 
     * @see packetOutMultiPort
     * Accepts a PacketIn instead of raw packet data. Note that the inPort
     * and switch can be different than the packet in switch/port
     */
    public void packetOutMultiPort(OFPacketIn pi,
                                   IOFSwitch sw,
                                   short inPort,
                                   Set<Integer> outPorts,
                                   FloodlightContext cntx) {
        packetOutMultiPort(pi.getPacketData(), sw, inPort, outPorts, cntx);
    }
    
    /** 
     * @see packetOutMultiPort
     * Accepts an IPacket instead of raw packet data. Note that the inPort
     * and switch can be different than the packet in switch/port
     */
    public void packetOutMultiPort(IPacket packet,
                                   IOFSwitch sw,
                                   short inPort,
                                   Set<Integer> outPorts,
                                   FloodlightContext cntx) {
        packetOutMultiPort(packet.serialize(), sw, inPort, outPorts, cntx);
    }

    protected boolean isInBroadcastCache(IOFSwitch sw, OFPacketIn pi,
    		FloodlightContext cntx) {
        // Get the cluster id of the switch.
        // Get the hash of the Ethernet packet.
        if (sw == null) return true;  
        
        // If the feature is disabled, always return false;
        if (!broadcastCacheFeature) return false;

        Ethernet eth = 
            IFloodlightProviderService.bcStore.get(cntx,
            		IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        Long broadcastHash;
        broadcastHash = topology.getL2DomainId(sw.getId())
        		* prime + eth.hashCode();
        if (broadcastCache.update(broadcastHash)) {
            sw.updateBroadcastCache(broadcastHash, pi.getInPort());
            return true;
        } else {
            return false;
        }
    }

    protected boolean isInSwitchBroadcastCache(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        if (sw == null) return true;
        
        // If the feature is disabled, always return false;
        if (!broadcastCacheFeature) return false;
        
        // Get the hash of the Ethernet packet.
        Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // some FORWARD_OR_FLOOD packets are unicast with unknown destination mac
        // if (eth.isBroadcast() || eth.isMulticast())
            return sw.updateBroadcastCache(new Long(eth.hashCode()), pi.getInPort());

        // return false;
    }

    public static boolean
            blockHost(IFloodlightProviderService floodlightProvider,
                      SwitchPort sw_tup, long host_mac,
                      short hardTimeout, long cookie) {

        if (sw_tup == null) {
            return false;
        }

        IOFSwitch sw = 
                floodlightProvider.getSwitches().get(sw_tup.getSwitchDPID());
        if (sw == null) return false;
        int inputPort = sw_tup.getPort();
        log.debug("blockHost sw={} port={} mac={}",
                  new Object[] { sw, sw_tup.getPort(), new Long(host_mac) });

        // Create flow-mod based on packet-in and src-switch
        OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);
        OFMatch match = new OFMatch();
        List<OFAction> actions = new ArrayList<OFAction>(); // Set no action to
                                                            // drop
        match.setDataLayerSource(Ethernet.toByteArray(host_mac))
             .setInputPort((short)inputPort)
             .setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC
                     & ~OFMatch.OFPFW_IN_PORT);
        fm.setCookie(cookie)
          .setHardTimeout((short) hardTimeout)
          .setIdleTimeout((short) 5)
          .setBufferId(OFPacketOut.BUFFER_ID_NONE)
          .setMatch(match)
          .setActions(actions)
          .setLengthU(OFFlowMod.MINIMUM_LENGTH); // +OFActionOutput.MINIMUM_LENGTH);

        try {
            log.debug("write drop flow-mod sw={} match={} flow-mod={}",
                      new Object[] { sw, match, fm });
            sw.write(fm, null);
        } catch (IOException e) {
            log.error("Failure writing deny flow mod", e);
            return false;
        }
        return true;

    }

    @Override
    public void deviceAdded(IDevice device) {
        // NOOP
    }

    @Override
    public void deviceRemoved(IDevice device) {
        // NOOP
    }

    @Override
    public void deviceMoved(IDevice device) {
        // Build flow mod to delete based on destination mac == device mac
        OFMatch match = new OFMatch();
        match.setDataLayerDestination(Ethernet.toByteArray(device.getMACAddress()));
        match.setWildcards(OFMatch.OFPFW_ALL ^ OFMatch.OFPFW_DL_DST);
        long cookie =
                AppCookie.makeCookie(FORWARDING_APP_ID, 0);
        OFMessage fm = ((OFFlowMod) floodlightProvider.getOFMessageFactory()
            .getMessage(OFType.FLOW_MOD))
            .setCommand(OFFlowMod.OFPFC_DELETE)
            .setOutPort((short) OFPort.OFPP_NONE.getValue())
            .setMatch(match)
            .setCookie(cookie)
            .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));

        // Flush to all switches
        for (IOFSwitch outSw : floodlightProvider.getSwitches().values()) {
            try {
                outSw.write(fm, null);
            } catch (IOException e) {
                log.error("Failure sending flow mod delete for moved device",
                          e);
            }
        }
    }

    @Override
    public void deviceIPV4AddrChanged(IDevice device) {

    }

    @Override
    public void deviceVlanChanged(IDevice device) {

    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && 
                (name.equals("topology") || 
                 name.equals("devicemanager")));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

}
