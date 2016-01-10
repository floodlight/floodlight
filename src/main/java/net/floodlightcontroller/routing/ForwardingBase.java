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
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.util.OFDPAUtils;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.util.TimedCache;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for implementing a forwarding module.  Forwarding is
 * responsible for programming flows to a switch in response to a policy
 * decision.
 */
public abstract class ForwardingBase implements IOFMessageListener {
	protected static Logger log = LoggerFactory.getLogger(ForwardingBase.class);

	protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // TODO: find sweet spot
	protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms

	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	public static int FLOWMOD_DEFAULT_PRIORITY = 1; // 0 is the default table-miss flow in OF1.3+, so we need to use 1
	
	protected static boolean FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = false;
	
	protected static boolean FLOWMOD_DEFAULT_MATCH_VLAN = true;
	protected static boolean FLOWMOD_DEFAULT_MATCH_MAC = true;
	protected static boolean FLOWMOD_DEFAULT_MATCH_IP_ADDR = true;
	protected static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT = true;

	protected static final short FLOWMOD_DEFAULT_IDLE_TIMEOUT_CONSTANT = 5;
	protected static final short FLOWMOD_DEFAULT_HARD_TIMEOUT_CONSTANT = 0;
	
	protected static boolean FLOOD_ALL_ARP_PACKETS = false;

	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IDeviceService deviceManagerService;
	protected IRoutingService routingEngineService;
	protected ITopologyService topologyService;
	protected IDebugCounterService debugCounterService;

	protected OFMessageDamper messageDamper;

	// for broadcast loop suppression
	protected boolean broadcastCacheFeature = true;
	public final int prime1 = 2633;  // for hash calculation
	public final static int prime2 = 4357;  // for hash calculation
	public TimedCache<Long> broadcastCache = new TimedCache<Long>(100, 5*1000);  // 5 seconds interval;

	// flow-mod - for use in the cookie
	public static final int FORWARDING_APP_ID = 2; // TODO: This must be managed
	// by a global APP_ID class
	static {
		AppCookie.registerApp(FORWARDING_APP_ID, "Forwarding");
	}
	public static final U64 appCookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

	// Comparator for sorting by SwitchCluster
	public Comparator<SwitchPort> clusterIdComparator =
			new Comparator<SwitchPort>() {
		@Override
		public int compare(SwitchPort d1, SwitchPort d2) {
			DatapathId d1ClusterId = topologyService.getOpenflowDomainId(d1.getSwitchDPID());
			DatapathId d2ClusterId = topologyService.getOpenflowDomainId(d2.getSwitchDPID());
			return d1ClusterId.compareTo(d2ClusterId);
		}
	};

	protected void init() {
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
				EnumSet.of(OFType.FLOW_MOD),
				OFMESSAGE_DAMPER_TIMEOUT);
	}

	protected void startUp() {
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
	}

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
	public abstract Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, 
			IRoutingDecision decision, FloodlightContext cntx);

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			IRoutingDecision decision = null;
			if (cntx != null) {
				decision = RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
			}

			return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
		default:
			break;
		}
		return Command.CONTINUE;
	}

	/**
	 * Push routes from back to front
	 * @param route Route to push
	 * @param match OpenFlow fields to match on
	 * @param srcSwPort Source switch port for the first hop
	 * @param dstSwPort Destination switch port for final hop
	 * @param cookie The cookie to set in each flow_mod
	 * @param cntx The floodlight context
	 * @param requestFlowRemovedNotification if set to true then the switch would
	 *        send a flow mod removal notification when the flow mod expires
	 * @param flowModCommand flow mod. command to use, e.g. OFFlowMod.OFPFC_ADD,
	 *        OFFlowMod.OFPFC_MODIFY etc.
	 * @return true if a packet out was sent on the first-hop switch of this route
	 */
	public boolean pushRoute(Route route, Match match, OFPacketIn pi,
			DatapathId pinSwitch, U64 cookie, FloodlightContext cntx,
			boolean requestFlowRemovedNotification, OFFlowModCommand flowModCommand) {

		boolean packetOutSent = false;

		List<NodePortTuple> switchPortList = route.getPath();

		for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
			// indx and indx-1 will always have the same switch DPID.
			DatapathId switchDPID = switchPortList.get(indx).getNodeId();
			IOFSwitch sw = switchService.getSwitch(switchDPID);

			if (sw == null) {
				if (log.isWarnEnabled()) {
					log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
				}
				return packetOutSent;
			}
			
			// need to build flow mod based on what type it is. Cannot set command later
			OFFlowMod.Builder fmb;
			switch (flowModCommand) {
			case ADD:
				fmb = sw.getOFFactory().buildFlowAdd();
				break;
			case DELETE:
				fmb = sw.getOFFactory().buildFlowDelete();
				break;
			case DELETE_STRICT:
				fmb = sw.getOFFactory().buildFlowDeleteStrict();
				break;
			case MODIFY:
				fmb = sw.getOFFactory().buildFlowModify();
				break;
			default:
				log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");        
			case MODIFY_STRICT:
				fmb = sw.getOFFactory().buildFlowModifyStrict();
				break;			
			}
			
			OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
			List<OFAction> actions = new ArrayList<OFAction>();	
 			Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());
 			
			// set input and output ports on the switch
			OFPort outPort = switchPortList.get(indx).getPortId();
			OFPort inPort = switchPortList.get(indx - 1).getPortId();
			mb.setExact(MatchField.IN_PORT, inPort);
			aob.setPort(outPort);
			aob.setMaxLen(Integer.MAX_VALUE);
			actions.add(aob.build());
			
			if (FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG || requestFlowRemovedNotification) {
				Set<OFFlowModFlags> flags = new HashSet<>();
				flags.add(OFFlowModFlags.SEND_FLOW_REM);
				fmb.setFlags(flags);
			}
			
			fmb.setMatch(mb.build())
			.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setBufferId(OFBufferId.NO_BUFFER)
			.setCookie(cookie)
			.setOutPort(outPort)
			.setPriority(FLOWMOD_DEFAULT_PRIORITY);
			
			FlowModUtils.setActions(fmb, actions, sw);
			
			try {
				if (log.isTraceEnabled()) {
					log.trace("Pushing Route flowmod routeIndx={} " +
							"sw={} inPort={} outPort={}",
							new Object[] {indx,
							sw,
							fmb.getMatch().get(MatchField.IN_PORT),
							outPort });
				}
				
				if (OFDPAUtils.isOFDPASwitch(sw)) {
					OFDPAUtils.addLearningSwitchFlow(sw, cookie, 
							FLOWMOD_DEFAULT_PRIORITY, 
							FLOWMOD_DEFAULT_HARD_TIMEOUT,
							FLOWMOD_DEFAULT_IDLE_TIMEOUT,
							fmb.getMatch(), 
							null, // TODO how to determine output VLAN for lookup of L2 interface group
							outPort);
				} else {
					messageDamper.write(sw, fmb.build());
				}

				/* Push the packet out the first hop switch */
				if (sw.getId().equals(pinSwitch) &&
						!fmb.getCommand().equals(OFFlowModCommand.DELETE) &&
						!fmb.getCommand().equals(OFFlowModCommand.DELETE_STRICT)) {
					/* Use the buffered packet at the switch, if there's one stored */
					pushPacket(sw, pi, outPort, true, cntx);
					packetOutSent = true;
				}
			} catch (IOException e) {
				log.error("Failure writing flow mod", e);
			}
		}

		return packetOutSent;
	}
	
	/**
	 * Pushes a packet-out to a switch. The assumption here is that
	 * the packet-in was also generated from the same switch. Thus, if the input
	 * port of the packet-in and the outport are the same, the function will not
	 * push the packet-out.
	 * @param sw switch that generated the packet-in, and from which packet-out is sent
	 * @param pi packet-in
	 * @param outport output port
	 * @param useBufferedPacket use the packet buffered at the switch, if possible
	 * @param cntx context of the packet
	 */
	protected void pushPacket(IOFSwitch sw, OFPacketIn pi, OFPort outport, boolean useBufferedPacket, FloodlightContext cntx) {
		if (pi == null) {
			return;
		}

		// The assumption here is (sw) is the switch that generated the
		// packet-in. If the input port is the same as output port, then
		// the packet-out should be ignored.
		if ((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)).equals(outport)) {
			if (log.isDebugEnabled()) {
				log.debug("Attempting to do packet-out to the same " +
						"interface as packet-in. Dropping packet. " +
						" SrcSwitch={}, pi={}",
						new Object[]{sw, pi});
				return;
			}
		}

		if (log.isTraceEnabled()) {
			log.trace("PacketOut srcSwitch={} pi={}",
					new Object[] {sw, pi});
		}

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().output(outport, Integer.MAX_VALUE));
		pob.setActions(actions);

		/* Use packet in buffer if there is a buffer ID set */
		if (useBufferedPacket) {
			pob.setBufferId(pi.getBufferId()); /* will be NO_BUFFER if there isn't one */
		} else {
			pob.setBufferId(OFBufferId.NO_BUFFER);
		}

		if (pob.getBufferId().equals(OFBufferId.NO_BUFFER)) {
			byte[] packetData = pi.getData();
			pob.setData(packetData);
		}

		pob.setInPort((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)));

		try {
			messageDamper.write(sw, pob.build());
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
	public void packetOutMultiPort(byte[] packetData, IOFSwitch sw, 
			OFPort inPort, Set<OFPort> outPorts, FloodlightContext cntx) {
		//setting actions
		List<OFAction> actions = new ArrayList<OFAction>();

		Iterator<OFPort> j = outPorts.iterator();

		while (j.hasNext()) {
			actions.add(sw.getOFFactory().actions().output(j.next(), 0));
		}

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		pob.setActions(actions);

		pob.setBufferId(OFBufferId.NO_BUFFER);
		pob.setInPort(inPort);

		pob.setData(packetData);

		try {
			if (log.isTraceEnabled()) {
				log.trace("write broadcast packet on switch-id={} " +
						"interfaces={} packet-out={}",
						new Object[] {sw.getId(), outPorts, pob.build()});
			}
			messageDamper.write(sw, pob.build());

		} catch (IOException e) {
			log.error("Failure writing packet out", e);
		}
	}

	/**
	 * @see packetOutMultiPort
	 * Accepts a PacketIn instead of raw packet data. Note that the inPort
	 * and switch can be different than the packet in switch/port
	 */
	public void packetOutMultiPort(OFPacketIn pi, IOFSwitch sw,
			OFPort inPort, Set<OFPort> outPorts, FloodlightContext cntx) {
		packetOutMultiPort(pi.getData(), sw, inPort, outPorts, cntx);
	}

	/**
	 * @see packetOutMultiPort
	 * Accepts an IPacket instead of raw packet data. Note that the inPort
	 * and switch can be different than the packet in switch/port
	 */
	public void packetOutMultiPort(IPacket packet, IOFSwitch sw,
			OFPort inPort, Set<OFPort> outPorts, FloodlightContext cntx) {
		packetOutMultiPort(packet.serialize(), sw, inPort, outPorts, cntx);
	}

	public static boolean blockHost(IOFSwitchService switchService,
			SwitchPort sw_tup, MacAddress host_mac, short hardTimeout, U64 cookie) {

		if (sw_tup == null) {
			return false;
		}

		IOFSwitch sw = switchService.getSwitch(sw_tup.getSwitchDPID());
		if (sw == null) {
			return false;
		}

		OFPort inputPort = sw_tup.getPort();
		log.debug("blockHost sw={} port={} mac={}",
				new Object[] { sw, sw_tup.getPort(), host_mac.getLong() });

		// Create flow-mod based on packet-in and src-switch
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();

		Match.Builder mb = sw.getOFFactory().buildMatch();
		List<OFAction> actions = new ArrayList<OFAction>(); // Set no action to drop
		mb.setExact(MatchField.IN_PORT, inputPort);
		if (host_mac.getLong() != -1L) {
			mb.setExact(MatchField.ETH_SRC, host_mac);
		}

		fmb.setCookie(cookie)
		.setHardTimeout(hardTimeout)
		.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
		.setPriority(FLOWMOD_DEFAULT_PRIORITY)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch(mb.build());
		
		FlowModUtils.setActions(fmb, actions, sw);

		log.debug("write drop flow-mod sw={} match={} flow-mod={}",
					new Object[] { sw, mb.build(), fmb.build() });
		// TODO: can't use the message damper since this method is static
		sw.write(fmb.build());
		
		return true;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN) && (name.equals("topology") || name.equals("devicemanager")));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}
}