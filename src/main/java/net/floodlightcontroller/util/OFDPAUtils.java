package net.floodlightcontroller.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Broadcom OF-DPA 2.0 specification provides an interesting
 * packet processing pipeline. Most OpenFlow programmers are
 * accustomed to programming Open vSwitch or other software switches,
 * where much if not all of the OpenFlow specification is supported.
 * 
 * Hardware switch vendors wishing to support OpenFlow typically mimic
 * software switches and allow programmers access to uniform flow
 * tables, where the switch will automatically move operations not 
 * supported in hardware to software/the CPU, also known as the
 * "slow path." In order to operate on packets in hardware, switch
 * vendors need to expose some of the low-level details, features, and
 * shortcomings to the programmer. That is where OF-DPA comes in.
 * 
 * On compatible Broadcom-based switches, in conjunction with the Indigo
 * OpenFlow agent running onboard the switch, OpenFlow controllers can
 * leverage complete hardware forwarding of packets in the data plane.
 * This does not necessarily include the ability to perform all actions
 * and matches in hardware; however, it increases the available set of
 * actions and matches and give the controller access to powerful
 * features onboard the switch, such as routing and tunneling.
 * 
 * OFDPAUtils provides an abstraction when programming a Broadcom
 * OF-DPA switch in order to ease conformance to the OF-DPA 2.0 spec
 * from the controller and modules.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFDPAUtils {

	private OFDPAUtils() {}
	
	private static final Logger log = LoggerFactory.getLogger(OFDPAUtils.class);

	private static class OFDPAGroupType { // TODO change these enums to static classes so that we don't rely on the ordering...
		static final int L2_INTERFACE = 0;			/* 0 */
		static final int L2_REWRITE = 1; 			/* 1 */
		static final int L3_UNICAST = 2;				/* 2 */
		static final int L2_MULTICAST = 3; 			/* 3 */
		static final int L2_FLOOD = 4; 				/* 4 */
		static final int L3_INTERFACE = 5; 			/* 5 */
		static final int L3_MULTICAST = 6;			/* 6 */
		static final int L3_ECMP = 7;				/* 7 */
		static final int L2_DATA_CENTER_OVERLAY = 8; /* 8 */
		static final int MPLS_LABEL = 9; 			/* 9 */
		static final int MPLS_FORWARDING = 10;		/* 10 */
		static final int L2_UNFILTERED_INTERFACE = 11; /* 11 */
		static final int L2_LOOPBACK = 12;   		/* 12 */
	}

	private static class L2OverlaySubType {
		static final int L2_OVERLAY_FLOOD_OVER_UNICAST_TUNNELS = 0;
		static final int L2_OVERLAY_FLOOD_OVER_MULTICAST_TUNNELS = 1;
		static final int L2_OVERLAY_MULTICAST_OVER_UNICAST_TUNNELS = 2;
		static final int L2_OVERLAY_MULTICAST_OVER_MULTICAST_TUNNELS = 3;
	}

	private static class MPLSSubType {
		static final int MPLS_INTERFACE = 0;
		static final int MPLS_L2_VPN_LABEL = 1;
		static final int MPLS_L3_VPN_LABEL = 2;
		static final int MPLS_TUNNEL_LABEL_1 = 3;
		static final int MPLS_TUNNEL_LABEL_2 = 4;
	    static final int MPLS_SWAP_LABEL = 5;
		static final int MPLS_FAST_FAILOVER = 6;
		static final int MPLS_ECMP = 8;
		static final int MPLS_L2_TAG = 10;
	}

	public static class Tables {
		public static final TableId INGRESS_PORT = TableId.of(0);
		public static final TableId VLAN = TableId.of(10);
		public static final TableId TERMINATION_MAC = TableId.of(20);
		public static final TableId UNICAST_ROUTING = TableId.of(30);
		public static final TableId MULITCAST_ROUTING = TableId.of(40);
		public static final TableId BRIDGING = TableId.of(50);
		public static final TableId POLICY_ACL = TableId.of(60);
	}

	private static final List<MatchFields> ALLOWED_MATCHES = 
			Collections.unmodifiableList(
					Arrays.asList(
							MatchFields.IN_PORT,
							MatchFields.ETH_SRC, 
							MatchFields.ETH_DST,
							MatchFields.ETH_TYPE,
							MatchFields.VLAN_VID,
							MatchFields.VLAN_PCP,
							MatchFields.TUNNEL_ID,			
							MatchFields.IP_PROTO,
							MatchFields.IPV4_SRC,
							MatchFields.IPV4_DST,
							MatchFields.IP_DSCP,
							MatchFields.IP_ECN,			
							MatchFields.ARP_SPA,
							MatchFields.ICMPV4_CODE,
							MatchFields.ICMPV4_TYPE,
							
							MatchFields.IPV6_SRC,
							MatchFields.IPV6_DST,
							MatchFields.IPV6_FLABEL,
							MatchFields.ICMPV6_CODE,
							MatchFields.ICMPV6_TYPE,
							
							MatchFields.TCP_SRC,
							MatchFields.TCP_DST,
							MatchFields.UDP_SRC,
							MatchFields.UDP_DST,
							MatchFields.SCTP_SRC,
							MatchFields.SCTP_DST
							// TODO fill in rest of ARP and IPV6 stuff here 
							)
					);

	/**
	 * Determine whether or not the provided switch is an OF-DPA switch.
	 * 
	 * @param sw, the switch to check
	 * @return true if the switch is an OF-DPA switch; false otherwise
	 */
	public static boolean isOFDPASwitch(IOFSwitch s) {
		/*
		 * Many switches might have the same table IDs as an OF-DPA switch,
		 * so we can't check the IDs only. Alternatively, we can check the
		 * name, description, etc. to see if it aligns with an OF-DPA-set
		 * description. This isn't fool-proof, but it'll work for now.
		 */
		if (s.getSwitchDescription().getSoftwareDescription().toLowerCase().contains("of-dpa")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Get the MatchFields that an OF-DPA switch supports matching.
	 * Note that this does not specify match codependencies or
	 * mutually exclusive matches.
	 * 
	 * @return, an unmodifiable list of potential MatchFields
	 */
	public static List<MatchFields> getSupportedMatchFields() {
		return ALLOWED_MATCHES;
	}
	
	/**
	 * Examine all the MatchFields in a Match object and pick out
	 * the MatchFields that are not supported by OF-DPA.
	 * 
	 * @param m
	 * @return
	 */
	public static List<MatchFields> checkMatchFields(Match m) {
		List<MatchFields> unsupported = null;
		Iterator<MatchField<?>> mfi = m.getMatchFields().iterator();
		
		while (mfi.hasNext()) {
			MatchField<?> mf = mfi.next();
			if (!getSupportedMatchFields().contains(mf.id)) {
				if (unsupported == null) {
					unsupported = new ArrayList<MatchFields>();
				}
				unsupported.add(mf.id);
			}
		}
		
		return unsupported;
	}

	/**
	 * Based on an intent described by a Match and an output OFPort,
	 * add a flow to an OF-DPA switch, conforming to the pipeline it
	 * exposes. The provided Match must contain at least the destination
	 * MAC address. If you would like to match on an untagged ethernet
	 * frame, omit MatchField.VLAN_VID from the Match object so that it
	 * is wildcarded. To the contrary, if you would like to match on a 
	 * specific VLAN tag, please include this field. It is not possible
	 * to match on both tagged and untagged packets in a single flow.
	 * 
	 * Implementation note: If a VLAN tag is not provided in the Match, the
	 * default VLAN of 1 will be assigned for internal use only; it will
	 * not appear on any packets egress the switch.
	 * 
	 * If the packet is to be sent out on a different VLAN (e.g. for VLAN
	 * translation) then the VlanVid must be specified.
	 * 
	 * An output OFPort must be specified. If the desired action is to 
	 * flood, use OFPort.FLOOD; for sending to the controller, use
	 * OFPort.CONTROLLER; etc.
	 * 
	 * @param s, must be an OF-DPA switch
	 * @param m, must contain at least the destination MAC
	 * @param outVlan, either a valid VLAN ID or ZERO for untagged
	 * @param outPort, either a valid physical port number or ZERO (for drop), ALL, FLOOD, or CONTROLLER
	 * @return true upon success; false if switch is not an OF-DPA switch
	 */
	public static boolean addBridgingFlow(IOFSwitch sw, U64 cookie, int priority, int hardTimeout, int idleTimeout, Match match, VlanVid outVlan, OFPort outPort) {
		if (!isOFDPASwitch(sw)) {
			log.error("Switch {} is not an OF-DPA switch. Not inserting flows.", sw.getId().toString());
			return false;
		}

		/*
		 * Prepare and/or check arguments against requirements.
		 */
		cookie = (cookie == null ? U64.ZERO : cookie);
		priority = (priority < 1 ? 1 : priority);
		hardTimeout = (hardTimeout < 0 ? 0 : hardTimeout);
		idleTimeout = (idleTimeout < 0 ? 0 : idleTimeout);
		if (match == null || !match.isExact(MatchField.ETH_DST)) {
			log.error("OF-DPA 2.0 requires at least the destination MAC be matched in order to forward through its pipeline.");
			return false;
		} else {
			List<MatchFields> mfs = checkMatchFields(match);
			if (mfs != null) {
				log.error("OF-DPA 2.0 does not support matching on the following fields: {}", mfs.toString());
				return false;
			}
		}
		outVlan = (outVlan == null ? VlanVid.ofVlan(0) : outVlan);
		outPort = (outPort == null ? OFPort.ZERO : outPort);

		/*
		 * Ingress flow table (0) will automatically send to the
		 * VLAN flow table (10), so insert nothing here.
		 */

		/*
		 * VLAN flow table (10) will drop by default, so we need to
		 * add a flow that will always direct the packet to the next
		 * flow table, the termination MAC table (20). If a VLAN tag
		 * is not present, then we need to append a tag. The tag to
		 * append will either be the tag specified in outVlan or the
		 * default tag of 1. Only VLANs are handled in this table.
		 */
		ArrayList<OFInstruction> instructions = new ArrayList<OFInstruction>();
		ArrayList<OFAction> actions = new ArrayList<OFAction>();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		/* If VLAN tag not present, add the internal tag of 1 */
		if (!match.isExact(MatchField.VLAN_VID)) {
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.UNTAGGED);
			actions.add(sw.getOFFactory().actions().buildPushVlan().setEthertype(EthType.VLAN_FRAME).build());
			actions.add(sw.getOFFactory().actions().setField(sw.getOFFactory().oxms().vlanVid(OFVlanVidMatch.ofVlan(1))));
			instructions.add(sw.getOFFactory().instructions().applyActions(actions));
		} else {
			mb.setExact(MatchField.VLAN_VID, match.get(MatchField.VLAN_VID));
		}

		/* No matter what, output to the next table */
		instructions.add(sw.getOFFactory().instructions().gotoTable(Tables.TERMINATION_MAC));

		OFFlowAdd fa = sw.getOFFactory().buildFlowAdd()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie(cookie)
				.setHardTimeout(hardTimeout)
				.setIdleTimeout(idleTimeout)
				.setPriority(priority)
				.setInstructions(instructions)
				.setTableId(Tables.VLAN)
				.setMatch(mb.build())
				.build();
		sw.write(fa);

		/*
		 * Termination MAC table (20) will automatically send to the
		 * bridging flow table (50), so also insert nothing here.
		 */

		/*
		 * Unicast routing (30) and multicast routing (40) flow tables
		 * are special use-case tables the application should program 
		 * directly. As such, we won't consider them here.
		 */

		/*
		 * Bridging table (50) should assign a write-action goto-group 
		 * depending on the desired output action (single-port or 
		 * flood). This can also be done in the next table, policy ACL,
		 * which we will do. Bridging must match on the VLAN VID and the
		 * dest MAC address of the packet. It must have a priority greater
		 * than all less-specific flows in the table (i.e. wildcarded
		 * flows). We will reserve priority 0 for a DLF (destination
		 * lookup failure) flow, which would have all fields wildcarded.
		 * The default on miss is to send to the policy ACL table (60),
		 * but this will not occur if our flow is matched, so we need to
		 * do this specifically in each flow we insert.
		 */

		instructions.clear();
		actions.clear();
		
		/* No matter what, output to the next table */
		instructions.add(sw.getOFFactory().instructions().gotoTable(Tables.POLICY_ACL));

		fa = fa.createBuilder()
				.setTableId(Tables.BRIDGING)
				.setMatch(sw.getOFFactory().buildMatch()
						.setExact(MatchField.VLAN_VID, fa.getMatch().get(MatchField.VLAN_VID)) /* might have been set to 1, so grab it here */
						.setExact(MatchField.ETH_DST, match.get(MatchField.ETH_DST))
						.build())
						.setInstructions(instructions)
						.build();
		sw.write(fa);

		/*
		 * Policy ACL table (60) allows for more detailed matches. This
		 * is where we will implement all the matches specified in the Match
		 * object. The write-actions goto group inserted by the bridging 
		 * table (50), or here (60), will be the output action taken upon 
		 * a match, since this is the end of the pipeline. If we want to 
		 * drop a packet for not matching, then no output group will be
		 * assigned to the packet, thus dropping it.
		 * 
		 * A DLF (desintation lookup failure) flow can also be inserted
		 * here to forward packets to the controller.
		 */
		
		/* Set the group to which we want to output. This might be a L2 flood or interface. */
		instructions.clear();
		actions.clear();
		if (outPort.equals(OFPort.ZERO)) {
			/* Don't add a group at all --> DROP */
		} else if (outPort.equals(OFPort.FLOOD) || outPort.equals(OFPort.ALL)) { // TODO how to distinguish in OF-DPA?
			actions.add(
					sw.getOFFactory().actions().group( // TODO Assume there is only one flood group per VLAN
							GroupIds.createL2Flood(U16.ZERO, fa.getMatch().get(MatchField.VLAN_VID).getVlanVid())
							)
					);
			instructions.add(sw.getOFFactory().instructions().writeActions(actions));
		} else if (outPort.equals(OFPort.CONTROLLER)) {
			actions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER, 0xFFffFFff));
			instructions.add(sw.getOFFactory().instructions().applyActions(actions));
		} else { /* assume port is a number valid on the switch */
			actions.add(
					sw.getOFFactory().actions().group(
							GroupIds.createL2Interface(outPort, fa.getMatch().get(MatchField.VLAN_VID).getVlanVid())
							)
					);
			instructions.add(sw.getOFFactory().instructions().writeActions(actions));
		}
		
		/* We're allowed to match on anything in the Match at this point. */
		fa = fa.createBuilder()
				.setTableId(Tables.POLICY_ACL)
				.setMatch(match)
				.setInstructions(instructions)
				.build();
		sw.write(fa);

		return true;
	}

	/**
	 * Generates group IDs according to the OF-DPA 2.0 spec. A create
	 * helper function is provided for each type of group, including
	 * sub-types, which should be used to set a valid group ID in any
	 * group mod sent to any OF-DPA switch. 
	 */
	public static class GroupIds {
		private GroupIds() {}

		public static OFGroup createL2Interface(OFPort p, VlanVid v) { //0
			return OFGroup.of(0 | p.getShortPortNumber() | (v.getVlan() << 16) | (OFDPAGroupType.L2_INTERFACE << 28));
		}
		/**
		 * Only bits 0-27 of id are used. Bits 28-31 are ignored.
		 * @param id
		 * @return
		 */
		public static OFGroup createL2Rewrite(U32 id) { //1
			return OFGroup.of(0 | (id.getRaw() & 0x0FffFFff) | (OFDPAGroupType.L2_REWRITE << 28));
		}
		/**
		 * Only bits 0-27 of id are used. Bits 28-31 are ignored.
		 * @param id
		 * @return
		 */
		public static OFGroup createL3Unicast(U32 id) { //2
			return OFGroup.of(0 | (id.getRaw() & 0x0FffFFff) | (OFDPAGroupType.L3_UNICAST << 28));
		}
		public static OFGroup createL2Multicast(U16 id, VlanVid v) { //3
			return OFGroup.of(0 | id.getRaw() | (v.getVlan() << 16) | (OFDPAGroupType.L2_MULTICAST << 28));
		}
		public static OFGroup createL2Flood(U16 id, VlanVid v) { //4
			return OFGroup.of(0 | id.getRaw() | (v.getVlan() << 16) | (OFDPAGroupType.L2_FLOOD << 28));
		}
		/**
		 * Only bits 0-27 of id are used. Bits 28-31 are ignored.
		 * @param id
		 * @return
		 */
		public static OFGroup createL3Interface(U32 id) { //5
			return OFGroup.of(0 | (id.getRaw() & 0x0FffFFff) | (OFDPAGroupType.L3_INTERFACE << 28));
		}
		public static OFGroup createL3Multicast(U16 id, VlanVid v) { //6
			return OFGroup.of(0 | id.getRaw() | (v.getVlan() << 16) | (OFDPAGroupType.L3_MULTICAST << 28));
		}
		/**
		 * Only bits 0-27 of id are used. Bits 28-31 are ignored.
		 * @param id
		 * @return
		 */
		public static OFGroup createL3ECMP(U32 id) { //7
			return OFGroup.of(0 | (id.getRaw() & 0x0FffFFff) | (OFDPAGroupType.L3_ECMP << 28));
		}
		/**
		 * Only bits 0-9 of index are used. Bits 10-15 are ignored.
		 * @param index
		 * @param tunnelId
		 * @return
		 */
		public static OFGroup createL2DCOFloodOverUnicastTunnels(U16 index, U16 tunnelId) { //8
			return OFGroup.of(0 | (index.getRaw() & 0x03ff) 
					| (tunnelId.getRaw() << 12)
					| (L2OverlaySubType.L2_OVERLAY_FLOOD_OVER_UNICAST_TUNNELS << 10)
					| (OFDPAGroupType.L2_DATA_CENTER_OVERLAY << 28));
		}
		/**
		 * Only bits 0-9 of index are used. Bits 10-15 are ignored.
		 * @param index
		 * @param tunnelId
		 * @return
		 */
		public static OFGroup createL2DCOFloodOverMulticastTunnels(U16 index, U16 tunnelId) { //8
			return OFGroup.of(0 | (index.getRaw() & 0x03ff) 
					| (tunnelId.getRaw() << 12)
					| (L2OverlaySubType.L2_OVERLAY_FLOOD_OVER_MULTICAST_TUNNELS << 10)
					| (OFDPAGroupType.L2_DATA_CENTER_OVERLAY << 28));
		}
		/**
		 * Only bits 0-9 of index are used. Bits 10-15 are ignored.
		 * @param index
		 * @param tunnelId
		 * @return
		 */
		public static OFGroup createL2DCOMulticastOverUnicastTunnels(U16 index, U16 tunnelId) { //8
			return OFGroup.of(0 | (index.getRaw() & 0x03ff) 
					| (tunnelId.getRaw() << 12)
					| (L2OverlaySubType.L2_OVERLAY_MULTICAST_OVER_UNICAST_TUNNELS << 10)
					| (OFDPAGroupType.L2_DATA_CENTER_OVERLAY << 28));
		}
		/**
		 * Only bits 0-9 of index are used. Bits 10-15 are ignored.
		 * @param index
		 * @param tunnelId
		 * @return
		 */
		public static OFGroup createL2DCOMulticastOverMulticastTunnels(U16 index, U16 tunnelId) { //8
			return OFGroup.of(0 | (index.getRaw() & 0x03ff) 
					| (tunnelId.getRaw() << 12)
					| (L2OverlaySubType.L2_OVERLAY_MULTICAST_OVER_MULTICAST_TUNNELS << 10)
					| (OFDPAGroupType.L2_DATA_CENTER_OVERLAY << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSInterfaceLabel(U32 index) { //9
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_INTERFACE << 24)
					| (OFDPAGroupType.MPLS_LABEL << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSL2VPNLabel(U32 index) { //9
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_L2_VPN_LABEL << 24)
					| (OFDPAGroupType.MPLS_LABEL << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSL3VPNLabel(U32 index) { //9
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_INTERFACE << 24)
					| (OFDPAGroupType.MPLS_LABEL << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSTunnelLable1(U32 index) { //9
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_TUNNEL_LABEL_1 << 24)
					| (OFDPAGroupType.MPLS_LABEL << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSTunnelLabel2(U32 index) { //9
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_TUNNEL_LABEL_2 << 24)
					| (OFDPAGroupType.MPLS_LABEL << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSSwapLabel(U32 index) { //9
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_SWAP_LABEL << 24)
					| (OFDPAGroupType.MPLS_LABEL << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSForwardingFastFailover(U32 index) { //10
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_FAST_FAILOVER << 24)
					| (OFDPAGroupType.MPLS_FORWARDING << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSForwardingECMP(U32 index) { //10
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_ECMP << 24)
					| (OFDPAGroupType.MPLS_FORWARDING << 28));
		}
		/**
		 * Only bits 0-23 of index are used. Bits 24-31 are ignored.
		 * @param index
		 * @return
		 */
		public static OFGroup createMPLSForwardingL2Tag(U32 index) { //10
			return OFGroup.of(0 | (index.getRaw() & 0x00ffFFff) 
					| (MPLSSubType.MPLS_L2_TAG << 24)
					| (OFDPAGroupType.MPLS_FORWARDING << 28));
		}
		public static OFGroup createL2UnfilteredInterface(OFPort p) { //11
			return OFGroup.of(0 | p.getShortPortNumber() | (OFDPAGroupType.L2_UNFILTERED_INTERFACE << 28));
		}
		public static OFGroup createL2Loopback(OFPort p) { //12
			return OFGroup.of(0 | p.getShortPortNumber() | (OFDPAGroupType.L2_LOOPBACK << 28));
		}
	}
}
