package net.floodlightcontroller.testmodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFOxmClass;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFSetConfig;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwSrc;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmEthSrc;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.ICMPv4Code;
import org.projectfloodlight.openflow.types.ICMPv4Type;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFValueType;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U8;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.types.VlanVid;
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
		OFFlowAdd.Builder fmb = factory.buildFlowAdd();
		List<OFAction> actions = new ArrayList<OFAction>();
        Match.Builder mb = factory.buildMatch();
        
		/*try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
        
        //TODO @Ryan set a bunch of matches. Test for an OF1.0 and OF1.3 switch. See what happens if they are incorrectly applied.
        /* L2 and ICMP TESTS mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
        mb.setExact(MatchField.ETH_SRC, MacAddress.BROADCAST);
        mb.setExact(MatchField.ETH_DST, MacAddress.BROADCAST);
        mb.setExact(MatchField.IPV4_SRC, IPv4Address.of("127.1.1.1"));
        mb.setExact(MatchField.IPV4_DST, IPv4Address.of("128.2.2.2"));
        mb.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
        mb.setExact(MatchField.ICMPV4_CODE, ICMPv4Code.of((short)1));
        mb.setExact(MatchField.ICMPV4_TYPE, ICMPv4Type.ECHO); */
        
        /* ARP TESTS mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
        mb.setExact(MatchField.ARP_OP, ArpOpcode.REQUEST);
        mb.setExact(MatchField.ARP_SHA, MacAddress.BROADCAST);
        mb.setExact(MatchField.ARP_SPA, IPv4Address.of("130.127.39.241"));
        mb.setExact(MatchField.ARP_THA, MacAddress.BROADCAST);
        mb.setExact(MatchField.ARP_TPA, IPv4Address.of("130.127.39.241")); */
        
        /* TP, IP OPT, VLAN TESTS */ mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
        mb.setExact(MatchField.VLAN_PCP, VlanPcp.of((byte) 1)); // might as well test these now too
        mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(512));
        mb.setExact(MatchField.MPLS_LABEL, U32.of(32));
        mb.setExact(MatchField.MPLS_TC, U8.of((short)64));
        mb.setExact(MatchField.IP_ECN, IpEcn.ECN_10); // and these
        mb.setExact(MatchField.IP_DSCP, IpDscp.DSCP_16);
        mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP); // with tcp, udp, sctp
        mb.setExact(MatchField.UDP_SRC, TransportPort.of(22));
        mb.setExact(MatchField.UDP_DST, TransportPort.of(80)); 
        
        /* MPLS TESTS mb.setExact(MatchField.ETH_TYPE, EthType.MPLS_MULTICAST);
        mb.setExact(MatchField.MPLS_LABEL, U32.of(18));
        mb.setExact(MatchField.MPLS_TC, U8.of((short)4));*/
        
        /* METADATA TEST 
        mb.setExact(MatchField.METADATA, OFMetadata.ofRaw(1)); */

        
        //TODO @Ryan set a bunch of actions. "" "" """ """"""
        OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
        actions.add(factory.actions().output(OFPort.of(1), Integer.MAX_VALUE));
        actions.add(factory.actions().setField(factory.oxms().ethSrc(MacAddress.BROADCAST)));
        actions.add(factory.actions().setField(factory.oxms().ethDst(MacAddress.BROADCAST)));
        actions.add(factory.actions().setField(factory.oxms().ipv4Src(IPv4Address.of("127.0.1.2"))));
        actions.add(factory.actions().setField(factory.oxms().ipv4Src(IPv4Address.of("128.0.3.4"))));


        fmb.setActions(actions);
        fmb.setMatch(mb.build());
		        
		sfps.addFlow("test-flow", fmb.build(), switchId);
		//sfps.deleteFlow("test-flow");
		
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
