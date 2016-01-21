package net.floodlightcontroller.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
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

	/* These cannot be changed, since they're final (public == okay for the ones we want to inform about) */
	public static final int PRIORITY = 1000;
	public static final int DLF_PRIORITY = 0;
	public static final int HARD_TIMEOUT = 0;
	public static final int IDLE_TIMEOUT = 0;
	public static final U64 APP_COOKIE = U64.of(Long.parseLong("00FFDDBBAA", 16)); /* OF-DPA sub B for P :-) */

	private static class OFDPAGroupType {
		private static final int L2_INTERFACE = 0;				/* 0 */
		private static final int L2_REWRITE = 1; 				/* 1 */
		private static final int L3_UNICAST = 2;				/* 2 */
		private static final int L2_MULTICAST = 3; 				/* 3 */
		private static final int L2_FLOOD = 4; 					/* 4 */
		private static final int L3_INTERFACE = 5; 				/* 5 */
		private static final int L3_MULTICAST = 6;				/* 6 */
		private static final int L3_ECMP = 7;					/* 7 */
		private static final int L2_DATA_CENTER_OVERLAY = 8; 	/* 8 */
		private static final int MPLS_LABEL = 9; 				/* 9 */
		private static final int MPLS_FORWARDING = 10;			/* 10 */
		private static final int L2_UNFILTERED_INTERFACE = 11;	/* 11 */
		private static final int L2_LOOPBACK = 12;   			/* 12 */
	}

	private static class L2OverlaySubType {
		private static final int L2_OVERLAY_FLOOD_OVER_UNICAST_TUNNELS = 0;
		private static final int L2_OVERLAY_FLOOD_OVER_MULTICAST_TUNNELS = 1;
		private static final int L2_OVERLAY_MULTICAST_OVER_UNICAST_TUNNELS = 2;
		private static final int L2_OVERLAY_MULTICAST_OVER_MULTICAST_TUNNELS = 3;
	}

	private static class MPLSSubType {
		private static final int MPLS_INTERFACE = 0;
		private static final int MPLS_L2_VPN_LABEL = 1;
		private static final int MPLS_L3_VPN_LABEL = 2;
		private static final int MPLS_TUNNEL_LABEL_1 = 3;
		private static final int MPLS_TUNNEL_LABEL_2 = 4;
		private static final int MPLS_SWAP_LABEL = 5;
		private static final int MPLS_FAST_FAILOVER = 6;
		private static final int MPLS_ECMP = 8;
		private static final int MPLS_L2_TAG = 10;
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
	 * Add the OF-DPA groups and flows necessary to facilitate future forwarding/learning 
	 * switch flows. The switch provided must be an OFDPA 2.0 compliant switch.
	 * Use VLAN tag of null or VlanVid.ZERO for untagged. VLAN tag 1 may not be used; it is 
	 * reserved as an internal VLAN for untagged ports on no VLAN (like a home switch).
	 * 
	 * This function will add the flows that permit all packets in the VLAN specified and
	 * on the ports specified to reach the bridging table, where a DLF flow will forward 
	 * them to the controller if another higher priority L2 flow does not match in the
	 * bridging table. All ethertypes will match this DLF flow.
	 * 
	 * Use {@link OFDPAUtils#addLearningSwitchFlow(IOFSwitch, U64, int, int, int, Match, VlanVid, OFPort) this function }
	 * to insert learning switch flows at a later point based on packets forwarded to the
	 * controller from the DLF flow.
	 * 
	 * @param sw
	 * @param vlan
	 * @param ports
	 * @return
	 */
	public static boolean addLearningSwitchPrereqs(@Nonnull IOFSwitch sw, VlanVid vlan, @Nonnull List<OFPortModeTuple> ports) {
		/*
		 * Both of these must complete. If the first fails, the second will not be executed.
		 */
		return addLearningSwitchPrereqGroups(sw, vlan, ports) && addLearningSwitchPrereqFlows(sw, vlan, ports);
	}

	/**
	 * Note: Prefer {@link OFDPAUtils#addLearningSwitchPrereqs(IOFSwitch, VlanVid, List) this function}
	 * over this function unless you know what you are doing.
	 * 
	 * Add the OFDPA groups necessary to facilitate future forwarding/learning 
	 * switch flows. The switch provided must be an OFDPA 2.0 compliant switch.
	 * If a VLAN tag is provided, it will be expected that all ports in the 
	 * accompanying list of ports are tagged and not access. Use VLAN tag of 
	 * null or VlanVid.ZERO for untagged. VLAN tag 1 may not be used; it is 
	 * reserved as an internal VLAN.
	 * 
	 * @param sw
	 * @param vlan
	 * @param ports
	 * @return
	 */
	private static boolean addLearningSwitchPrereqGroups(@Nonnull IOFSwitch sw, VlanVid vlan, @Nonnull List<OFPortModeTuple> ports) {
		if (sw == null) {
			throw new NullPointerException("Switch cannot be null.");
		}
		if (vlan == null) {
			vlan = VlanVid.ZERO; /* set untagged */
		} else if (vlan.equals(VlanVid.ofVlan(1))) {
			throw new IllegalArgumentException("VLAN cannot be 1. VLAN 1 is an reserved VLAN for internal use inside the OFDPA switch.");
		}
		if (ports == null) {
			throw new NullPointerException("List of ports cannot be null. Must specify at least 2 valid switch ports.");
		} else if (ports.size() < 2) {
			throw new IllegalArgumentException("List of ports must contain at least 2 valid switch ports.");
		} else {
			/* verify ports are valid switch ports */
			for (OFPortModeTuple p : ports) {
				if (sw.getOFFactory().getVersion().equals(OFVersion.OF_10) && (sw.getPort(p.getPort()) == null || p.getPort().getShortPortNumber() > 0xFF00)) {
					throw new IllegalArgumentException("Port " + p.getPort().getPortNumber() + " is not a valid port on switch " + sw.getId().toString());
				} else if (!sw.getOFFactory().getVersion().equals(OFVersion.OF_10) && (sw.getPort(p.getPort()) == null || U32.of(p.getPort().getPortNumber()).compareTo(U32.of(0xffFFff00)) != -1)) {
					throw new IllegalArgumentException("Port " + p.getPort().getPortNumber() + " is not a valid port on switch " + sw.getId().toString());
				}
			}
		}

		/*
		 * For each output port, add an L2 interface group.
		 */		
		for (OFPortModeTuple p : ports) {
			List<OFAction> actions = new ArrayList<OFAction>();
			if (vlan.equals(VlanVid.ZERO) || p.getMode() == OFPortMode.ACCESS) { /* if it's untagged (internal=1) or access, pop the tag */
				actions.add(sw.getOFFactory().actions().popVlan());
			}
			actions.add(sw.getOFFactory().actions().output(p.getPort(), 0xffFFffFF));

			OFGroupAdd ga = sw.getOFFactory().buildGroupAdd()
					.setGroup(GroupIds.createL2Interface(p.getPort(), vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan))
					.setGroupType(OFGroupType.INDIRECT)
					.setBuckets(Collections.singletonList(
							sw.getOFFactory().buildBucket()
							.setActions(actions)
							.build()))
							.build();
			sw.write(ga);
		}

		/*
		 * For the VLAN provided (or internal VLAN 1 if untagged),
		 * add an L2 flood group.
		 */
		List<OFBucket> bucketList = new ArrayList<OFBucket>(ports.size());
		for (OFPortModeTuple p : ports) {
			List<OFAction> actions = new ArrayList<OFAction>();
			actions.add(sw.getOFFactory().actions().group(GroupIds.createL2Interface(p.getPort(), vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan)));
			bucketList.add(sw.getOFFactory().buildBucket().setActions(actions).build());
		}
		OFGroupAdd ga = sw.getOFFactory().buildGroupAdd() /* use the VLAN ID as the group ID */
				.setGroup(GroupIds.createL2Flood(U16.of((vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan).getVlan()), vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan))
				.setGroupType(OFGroupType.ALL)
				.setBuckets(bucketList)
				.build();
		sw.write(ga);

		return true;
	}

	/**
	 * Note: Prefer {@link OFDPAUtils#addLearningSwitchPrereqs(IOFSwitch, VlanVid, List) this function}
	 * over this function unless you know what you are doing.
	 * 
	 * Add the OFDPA flows necessary to facilitate future forwarding/learning 
	 * switch flows. The switch provided must be an OFDPA 2.0 compliant switch.
	 * If a VLAN tag is provided, it will be expected that all ports in the 
	 * accompanying list of ports are tagged and not access. Use VLAN tag of 
	 * null or VlanVid.ZERO for untagged. VLAN tag 1 may not be used; it is 
	 * reserved as an internal VLAN.
	 * 
	 * @param sw
	 * @param vlan
	 * @param ports
	 * @return
	 */
	private static boolean addLearningSwitchPrereqFlows(@Nonnull IOFSwitch sw, VlanVid vlan, @Nonnull List<OFPortModeTuple> ports) {
		if (sw == null) {
			throw new NullPointerException("Switch cannot be null.");
		}
		if (vlan == null) {
			vlan = VlanVid.ZERO; /* set untagged */
		} else if (vlan.equals(VlanVid.ofVlan(1))) {
			throw new IllegalArgumentException("VLAN cannot be 1. VLAN 1 is an reserved VLAN for internal use inside the OFDPA switch.");
		}
		if (ports == null) {
			throw new NullPointerException("List of ports cannot be null. Must specify at least 2 valid switch ports.");
		} else if (ports.size() < 2) {
			throw new IllegalArgumentException("List of ports must contain at least 2 valid switch ports.");
		} else {
			/* verify ports are valid switch ports */
			for (OFPortModeTuple p : ports) {
				if (sw.getOFFactory().getVersion().equals(OFVersion.OF_10) && (sw.getPort(p.getPort()) == null || p.getPort().getShortPortNumber() > 0xFF00)) {
					throw new IllegalArgumentException("Port " + p.getPort().getPortNumber() + " is not a valid port on switch " + sw.getId().toString());
				} else if (!sw.getOFFactory().getVersion().equals(OFVersion.OF_10) && (sw.getPort(p.getPort()) == null || U32.of(p.getPort().getPortNumber()).compareTo(U32.of(0xffFFff00)) != -1)) {
					throw new IllegalArgumentException("Port " + p.getPort().getPortNumber() + " is not a valid port on switch " + sw.getId().toString());
				}
			}
		}

		/*
		 * VLAN flow table (10) will drop by default, so we need to
		 * add a flow that will always direct the packet to the next
		 * flow table, the termination MAC table (20). If a VLAN tag
		 * is not present, then we need to append a tag. The tag to
		 * append will either be the tag specified in outVlan or the
		 * default tag of 1. Only VLANs are handled in this table.
		 */
		ArrayList<OFInstruction> instructions = new ArrayList<OFInstruction>();
		ArrayList<OFAction> applyActions = new ArrayList<OFAction>();
		ArrayList<OFAction> writeActions = new ArrayList<OFAction>();
		Match.Builder mb = sw.getOFFactory().buildMatch();
		OFFlowAdd.Builder fab = sw.getOFFactory().buildFlowAdd();

		/* These are common to all flows for VLAN flow table. */
		fab.setBufferId(OFBufferId.NO_BUFFER)
		.setCookie(APP_COOKIE)
		.setHardTimeout(HARD_TIMEOUT)
		.setIdleTimeout(IDLE_TIMEOUT)
		.setPriority(PRIORITY)
		.setTableId(Tables.VLAN);

		/*
		 * For each port, if it's an access port, must first push flow for tagged,
		 * THEN push flow for untagged. If it's trunk and access, do the same.
		 * If it's just trunk, then only push the tagged flow.
		 */
		for (OFPortModeTuple p : ports) {
			/* OF-DPA requires match on VLAN tag for both trunk and access ports */
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid((vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan)));

			/* No matter what, we need to match on the ingress port */
			mb.setExact(MatchField.IN_PORT, p.getPort());

			/* We have to do this for OF-DPA. It's like adding the VLAN to the switch on that port. */
			/* Happens automatically: actions.add(sw.getOFFactory().actions().buildPushVlan().setEthertype(EthType.VLAN_FRAME).build()); */
			applyActions.add(sw.getOFFactory().actions().setField(sw.getOFFactory().oxms().vlanVid(OFVlanVidMatch.ofVlanVid((vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan)))));
			instructions.add(sw.getOFFactory().instructions().applyActions(applyActions));
			/* No matter what, output to the next table */
			instructions.add(sw.getOFFactory().instructions().gotoTable(Tables.TERMINATION_MAC));

			fab.setInstructions(instructions)
			.setMatch(mb.build())
			.build();
			sw.write(fab.build());
			if (log.isDebugEnabled()) {
				log.debug("Writing tagged prereq flow to VLAN flow table {}", fab.build().toString());
			}

			/* Don't forget to empty out our containers for the next iteration (or below). */
			instructions.clear();
			applyActions.clear();
			mb = sw.getOFFactory().buildMatch();

			/* Here, if the port is access, add another untagged flow */
			if (vlan.equals(VlanVid.ZERO) || p.getMode() == OFPortMode.ACCESS) {
				mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.UNTAGGED); //TODO verify this
				mb.setExact(MatchField.IN_PORT, p.getPort());

				applyActions.add(sw.getOFFactory().actions().setField(sw.getOFFactory().oxms().vlanVid(OFVlanVidMatch.ofVlanVid((vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan)))));
				instructions.add(sw.getOFFactory().instructions().applyActions(applyActions));
				instructions.add(sw.getOFFactory().instructions().gotoTable(Tables.TERMINATION_MAC));

				fab.setInstructions(instructions)
				.setMatch(mb.build())
				.build();
				sw.write(fab.build());
				if (log.isDebugEnabled()) {
					log.debug("Writing untagged prereq flow to VLAN flow table {}", fab.build().toString());
				}

				/* Don't forget to empty out our containers for the next iteration (or below). */
				instructions.clear();
				applyActions.clear();
				mb = sw.getOFFactory().buildMatch();
			}
		}

		/* Termination MAC table auto-forwards to bridging table (50) */

		/*
		 * We will insert a DLF flow to send to controller in the bridging table (50).
		 */
		writeActions.add(sw.getOFFactory().actions().group(OFDPAUtils.GroupIds.createL2Flood(
				U16.of((vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan).getVlan()) /* ID */, 
				vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan))); /* bogus action */
		applyActions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER, 0xffFFffFF)); /* real, intended action */
		instructions.add(sw.getOFFactory().instructions().writeActions(writeActions));
		instructions.add(sw.getOFFactory().instructions().applyActions(applyActions));
		instructions.add(sw.getOFFactory().instructions().gotoTable(Tables.POLICY_ACL)); /* must go to policy ACL otherwise dropped; bogus though */
		fab = fab.setMatch(sw.getOFFactory().buildMatch()
				.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : vlan)) /* must match on just VLAN; dst MAC wildcarded */
				.build())
				.setInstructions(instructions)
				.setPriority(DLF_PRIORITY) /* lower priority */
				.setTableId(Tables.BRIDGING);
		sw.write(fab.build());
		if (log.isDebugEnabled()) {
			log.debug("Writing DLF flow to bridging table {}", fab.build().toString());
		}

		return true;
	}

	/**
	 * Note: Must have called {@link OFDPAUtils#addLearningSwitchPrereqs(IOFSwitch, VlanVid, List) this function } prior to calling 
	 * this function. It is assumed you have done the aforementioned with the same VLAN and ports, otherwise you will likely
	 * get a very grumpy OF-DPA switch.
	 * 
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
	public static boolean addLearningSwitchFlow(IOFSwitch sw, U64 cookie, int priority, int hardTimeout, int idleTimeout, Match match, VlanVid outVlan, OFPort outPort) {
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
			log.error("OF-DPA 2.0 requires the destination MAC be matched in order to forward through its pipeline.");
			return false;
		} else if (match == null || !match.isExact(MatchField.VLAN_VID)) {
			log.error("OF-DPA 2.0 requires the VLAN be matched in order to forward through its pipeline.");
			return false;
		} else {
			List<MatchFields> mfs = checkMatchFields(match);
			if (mfs != null) {
				log.error("OF-DPA 2.0 does not support matching on the following fields: {}", mfs.toString());
				return false;
			}
		}
		outVlan = (outVlan == null ? VlanVid.ZERO : outVlan);
		outPort = (outPort == null ? OFPort.ZERO : outPort);

		/*
		 * Add flow to bridging table that matches on dst MAC and outputs
		 * to the known port where the next hop or destination resides.
		 */
		ArrayList<OFInstruction> instructions = new ArrayList<OFInstruction>();
		ArrayList<OFAction> actions = new ArrayList<OFAction>();
		
		actions.add(sw.getOFFactory().actions().group(GroupIds.createL2Interface(outPort, (outVlan.equals(VlanVid.ZERO) ? VlanVid.ofVlan(1) : outVlan))));
		instructions.add(sw.getOFFactory().instructions().writeActions(actions));
		instructions.add(sw.getOFFactory().instructions().gotoTable(Tables.POLICY_ACL)); /* must go here or dropped */

		OFFlowAdd fa = sw.getOFFactory().buildFlowAdd()
				.setMatch(sw.getOFFactory().buildMatch()
						.setExact(MatchField.VLAN_VID, match.get(MatchField.VLAN_VID))
						.setExact(MatchField.ETH_DST, match.get(MatchField.ETH_DST))
						.build())
						.setPriority(priority)
						.setIdleTimeout(idleTimeout)
						.setHardTimeout(hardTimeout)
						.setBufferId(OFBufferId.NO_BUFFER)
						.setCookie(OFDPAUtils.APP_COOKIE)
						.setTableId(OFDPAUtils.Tables.BRIDGING)
						.setInstructions(instructions)
						.build();
		log.debug("Writing learning switch flow to bridging table: {}", fa);
		sw.write(fa);

		/*
		 * Policy ACL table (60) allows for more detailed matches. The 
		 * write-actions goto group inserted by the bridging table (50) 
		 * will be the output action taken upon a match. The policy ACL
		 * table allows for (optional) additional matches to take place.
		 * If we want to drop a packet instead of forwarding it, then 
		 * the output group must be cleared in a flow that matches on
		 * the destination MAC and VLAN (same as bridging).
		 */

		/* 
		 * For now, let's assume we only match on VLAN and destination MAC.
		 * If that's the case, we should be able to do L2 forwarding b/t
		 * ports residing on the same VLAN.
		 * 
		 * We're allowed to match on anything in the Match (supplied as an argument to this function) at this point. 
		fa = sw.getOFFactory().buildFlowAdd()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie(cookie)
				.setHardTimeout(hardTimeout)
				.setIdleTimeout(idleTimeout)
				.setPriority(priority)
				.setTableId(Tables.POLICY_ACL)
				.setMatch(match)
				.setInstructions(instructions)
				.build();
		log.debug("Writing learning switch flow to policy ACL table: {}", fa);
		sw.write(fa); */

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
					| (MPLSSubType.MPLS_L3_VPN_LABEL << 24)
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