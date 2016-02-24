package net.floodlightcontroller.testmodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

public class TestModule implements IFloodlightModule, IOFSwitchListener {

	private static IStaticFlowEntryPusherService sfps;
	private static IOFSwitchService switchService;
	private static Logger log;
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IStaticFlowEntryPusherService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		sfps = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		switchService.addOFSwitchListener(this);
		log = LoggerFactory.getLogger(TestModule.class);
		if (sfps == null) {
			log.error("Static Flow Pusher Service not found!");
		}
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		

	}

	@Override
	public void switchAdded(DatapathId switchId) {
		OFFactory factory = switchService.getSwitch(switchId).getOFFactory();
		
		/*OFFlowAdd.Builder fab = factory.buildFlowAdd();
		fab.setMatch(factory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setMasked(MatchField.IPV4_SRC, IPv4Address.of("10.0.123.1"), IPv4Address.of("255.255.0.255"))
				.build());
		fab.setBufferId(OFBufferId.NO_BUFFER);
		if (switchId.equals(DatapathId.of(1)))
		switchService.getSwitch(switchId).write(fab.build());*/
		
		/*OFMeterStatsRequest req;
		ListenableFuture<OFMeterStatsReply> reply = switchService.getActiveSwitch(switchId).writeRequest(req);
		try {
			for (OFMeterStats entry : reply.get().getEntries()) {
				U64 byteInCount = entry.getByteInCount();
				for (OFMeterBandStats mbs : entry.getBandStats()) {
					U64 byteBandCount = mbs.getByteBandCount();
					U64 pktBandCount = mbs.getPacketBandCount();
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}*/
		
		OFFactory f =factory;
        Match.Builder mb =f.buildMatch();
        mb.setExact(MatchField.ETH_SRC, MacAddress.of(2));
        Match m=mb.build();
        ArrayList<OFAction> actionList = new ArrayList<OFAction>();
        OFActions actions = f.actions();
        OFActionPushVlan vlan =actions.pushVlan(EthType.of(0x8100));
        actionList.add(vlan);


        OFOxms oxms =f.oxms();
        OFActionSetField vlanid=actions.buildSetField().setField(oxms.buildVlanVid().setValue(OFVlanVidMatch.ofVlan(10)).build()).build();
        actionList.add(vlanid);
        OFInstructions inst=f.instructions(); 
        OFInstructionApplyActions apply=inst.buildApplyActions().setActions(actionList).build();
        ArrayList<OFInstruction> instList= new ArrayList<OFInstruction>();
        instList.add(apply);
        OFFlowMod.Builder fmb = factory.buildFlowAdd();
        OFFlowMod msg = fmb.setPriority(32769)
        .setMatch(m)
        .setInstructions(instList)
        .setOutPort(OFPort.of(1))
        .build();

        switchService.getSwitch(switchId).write(msg);
		
		
		/*
		 * An attempt at meters, but they aren't supported anywhere, yet... 
		 * OFMeterBand mb = factory.meterBands().buildDrop()
				.setRate(1000)
				.setBurstSize(1000)
				.build();
		ArrayList<OFMeterBand> mbl = new ArrayList<OFMeterBand>();
		mbl.add(mb);
				
		OFMeterMod mm = factory.buildMeterMod()
				.setMeters(mbl)
				.setMeterId(1)
				.setCommand(OFMeterModCommandSerializerVer13.ADD_VAL) 
				.build(); 
		// This is a bug. You should be able to directly do OFMeterModCommand.ADD */
		
		/*HashSet<OFTableConfig> tblCfg = new HashSet<OFTableConfig>();
		tblCfg.add(OFTableConfig.TABLE_MISS_CONTROLLER);
		
		ArrayList<OFTableModProp> tabModPropList = new ArrayList<OFTableModProp>();
		OFTableModProp propEvic = switchService.getActiveSwitch(switchId).getOFFactory().tableDesc(TableId.ALL, arg1)
		tabModPropList.add(propEvic);
		OFTableMod tm = switchService.getActiveSwitch(switchId).getOFFactory().buildTableMod()
				.setProperties(pro)
		
		switchService.getActiveSwitch(switchId).write(mm);*/
		
		/*OFFlowAdd.Builder fmb = factory.buildFlowAdd();
		List<OFAction> actions = new ArrayList<OFAction>();
        Match.Builder mb = factory.buildMatch();
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        OFInstructionApplyActions.Builder applyActInstBldr = factory.instructions().buildApplyActions();
        OFInstructionWriteActions.Builder writeActInstBldr = factory.instructions().buildWriteActions();
        OFInstructionMeter.Builder mtrBldr = factory.instructions().buildMeter();
        OFInstructionClearActions clrAct = factory.instructions().clearActions(); // no builder available (there's nothing to set anyway)
        OFInstructionGotoTable.Builder gotoTblBldr = factory.instructions().buildGotoTable();
        /*OFMeterBandDrop dropMeter = factory.meterBands().buildDrop().setBurstSize(100).setRate(200).build();
        List<OFMeterBand> meterBandEntries = new ArrayList<OFMeterBand>();
        OFMeterBandStats meterBandStats = factory.buildMeterBandStats().setPacketBandCount(U64.of(64)).setByteBandCount(U64.of(1024)).build();
        meterBandEntries.add(meterBandStats);
        OFMeterMod meterMod = factory.buildMeterMod().setCommand(OFMeterModCommand.ADD.ordinal()).setMeters(meterBandEntries).setMeterId(10).build();
        factory.buildmeter*/
        
		/*try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
        
        // set a bunch of matches. Test for an OF1.0 and OF1.3 switch. See what happens if they are incorrectly applied.
        /* L2 and ICMP TESTS  mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
        mb.setExact(MatchField.ETH_SRC, MacAddress.BROADCAST);
        mb.setExact(MatchField.ETH_DST, MacAddress.BROADCAST);
        mb.setExact(MatchField.IPV4_SRC, IPv4Address.of("127.1.1.1"));
        mb.setExact(MatchField.IPV4_DST, IPv4Address.of("128.2.2.2"));
        mb.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
        mb.setExact(MatchField.ICMPV4_CODE, ICMPv4Code.of((short)1));
        mb.setExact(MatchField.ICMPV4_TYPE, ICMPv4Type.ECHO); 
        OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
        actions.add(factory.actions().output(OFPort.of(1), Integer.MAX_VALUE));
        //actions.add(factory.actions().setField(factory.oxms().icmpv4Code(ICMPv4Code.of((short)1))));
        //actions.add(factory.actions().setField(factory.oxms().icmpv4Type(ICMPv4Type.ALTERNATE_HOST_ADDRESS))); */
 
        
        /* ARP TESTS  mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
        mb.setExact(MatchField.ARP_OP, ArpOpcode.REQUEST);
        mb.setExact(MatchField.ARP_SHA, MacAddress.BROADCAST);
        mb.setExact(MatchField.ARP_SPA, IPv4Address.of("130.127.39.241"));
        mb.setExact(MatchField.ARP_THA, MacAddress.BROADCAST);
        mb.setExact(MatchField.ARP_TPA, IPv4Address.of("130.127.39.241")); 
        OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
        actions.add(factory.actions().output(OFPort.LOCAL, Integer.MAX_VALUE));
        actions.add(factory.actions().setField(factory.oxms().arpOp(ArpOpcode.REPLY)));
        actions.add(factory.actions().setField(factory.oxms().arpSha(MacAddress.BROADCAST)));
        actions.add(factory.actions().setField(factory.oxms().arpTha(MacAddress.BROADCAST)));
        actions.add(factory.actions().setField(factory.oxms().arpSpa(IPv4Address.of("255.255.255.255"))));
        actions.add(factory.actions().setField(factory.oxms().arpTpa(IPv4Address.of("255.255.255.255")))); 
        fmb.setTableId(TableId.of(16)); */
        
        /* TP, IP OPT, VLAN TESTS   mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
        mb.setExact(MatchField.VLAN_PCP, VlanPcp.of((byte) 1)); // might as well test these now too
        //mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(512));
        mb.setExact(MatchField.MPLS_LABEL, U32.of(32));
        //mb.setExact(MatchField.MPLS_TC, U8.of((short)64));
        mb.setExact(MatchField.IP_ECN, IpEcn.ECN_10); // and these
        mb.setExact(MatchField.IP_DSCP, IpDscp.DSCP_16);
        mb.setExact(MatchField.IP_PROTO, IpProtocol.SCTP); // with tcp, udp, sctp
        mb.setExact(MatchField.SCTP_SRC, TransportPort.of(22));
        mb.setExact(MatchField.SCTP_DST, TransportPort.of(80)); 
        OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
        actions.add(factory.actions().output(OFPort.of(1), Integer.MAX_VALUE));
        actions.add(factory.actions().setField(factory.oxms().ethSrc(MacAddress.BROADCAST)));
        actions.add(factory.actions().setField(factory.oxms().ethDst(MacAddress.BROADCAST)));
        actions.add(factory.actions().setField(factory.oxms().ipv4Src(IPv4Address.of("127.0.1.2"))));
        actions.add(factory.actions().setField(factory.oxms().ipv4Dst(IPv4Address.of("128.0.3.4")))); 
        actions.add(factory.actions().setField(factory.oxms().sctpSrc(TransportPort.of(22))));
        actions.add(factory.actions().setField(factory.oxms().sctpDst(TransportPort.of(80))));
        actions.add(factory.actions().setField((factory.oxms().ipDscp(IpDscp.DSCP_11))));
        actions.add(factory.actions().setField((factory.oxms().ipEcn(IpEcn.ECN_10))));

        fmb.setTableId(TableId.of(7));
        // these test non-set-field actions
        //actions.add(factory.actions().copyTtlOut());
        //actions.add(factory.actions().pushVlan(EthType.IPv4));
        //actions.add(factory.actions().pushVlan(EthType.IPv4));
        //actions.add(factory.actions().setField(factory.oxms().ipProto(IpProtocol.TCP))); // can't set protocol...makes sense */
        
        /* MPLS TESTS mb.setExact(MatchField.ETH_TYPE, EthType.MPLS_MULTICAST);
        mb.setExact(MatchField.MPLS_LABEL, U32.of(18));
        mb.setExact(MatchField.MPLS_TC, U8.of((short)4));
        actions.add(factory.actions().output(OFPort.LOCAL, Integer.MAX_VALUE));
        actions.add(factory.actions().setField(factory.oxms().mplsLabel(U32.ZERO)));
        actions.add(factory.actions().setField(factory.oxms().mplsTc(U8.ZERO))); */
        
        /* METADATA TEST 
        mb.setExact(MatchField.METADATA, OFMetadata.ofRaw(1)); 
        //fmb.setActions(actions); // this will automatically create the apply actions instruction
        applyActInstBldr.setActions(actions);
        //mtrBldr.setMeterId(1);
        instructions.add(applyActInstBldr.build());
        //instructions.add(mtrBldr.build());
        fmb.setInstructions(instructions);
        fmb.setMatch(mb.build()); 
		        
		sfps.addFlow("test-flow", fmb.build(), switchId);
		//sfps.deleteFlow("test-flow"); */
		
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
	}

}
