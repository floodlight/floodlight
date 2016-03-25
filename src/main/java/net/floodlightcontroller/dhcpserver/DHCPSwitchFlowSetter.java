package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
// Adding a comment to test a new commit
public class DHCPSwitchFlowSetter implements IFloodlightModule, IOFSwitchListener {
	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;
	protected IOFSwitchService switchService;
	
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
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStaticFlowEntryPusherService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		log = LoggerFactory.getLogger(DHCPServer.class);
		sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
	}

	@Override
	public void switchAdded(DatapathId dpid) {
		/* Insert static flows on all ports of the switch to redirect
		 * DHCP client --> DHCP DHCPServer traffic to the controller.
		 * DHCP client's operate on UDP port 67
		 */
		IOFSwitch sw = switchService.getSwitch(dpid);
		
		//fix concurrency flaw
		if (sw == null){
			return;
		}
		
		OFFlowAdd.Builder flow = sw.getOFFactory().buildFlowAdd();
		Match.Builder match = sw.getOFFactory().buildMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput.Builder action = sw.getOFFactory().actions().buildOutput();
		for (OFPortDesc port : sw.getPorts()) {
			match.setExact(MatchField.IN_PORT, port.getPortNo());
			match.setExact(MatchField.ETH_TYPE, EthType.IPv4);
			match.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
			match.setExact(MatchField.UDP_SRC, UDP.DHCP_CLIENT_PORT);
			action.setMaxLen(0xffFFffFF);
			action.setPort(OFPort.CONTROLLER);
			actionList.add(action.build());
			
			flow.setBufferId(OFBufferId.NO_BUFFER);
			flow.setHardTimeout(0);
			flow.setIdleTimeout(0);
			flow.setOutPort(OFPort.CONTROLLER);
			flow.setActions(actionList);
			flow.setMatch(match.build());
			flow.setPriority(32767);
			sfp.addFlow("dhcp-port---" + port.getPortNo().getPortNumber() + "---(" + port.getName() + ")", flow.build(), sw.getId());
		}		
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
	}

	@Override
	public void switchActivated(DatapathId switchId) {
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
	}

	@Override
	public void switchChanged(DatapathId switchId) {
	}
}