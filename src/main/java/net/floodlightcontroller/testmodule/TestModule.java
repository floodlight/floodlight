package net.floodlightcontroller.testmodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

public class TestModule implements IFloodlightModule {

	private static IStaticFlowEntryPusherService sfps;
	protected static Logger log;
	
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
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		sfps = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		log = LoggerFactory.getLogger(TestModule.class);
		if (sfps == null) {
			log.error("Static Flow Pusher Service not found!");
		}
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		OFFlowMod.Builder fmb = OFFactories.getFactory(OFVersion.OF_13).buildFlowModify();
		OFActionOutput.Builder aob = OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput();
		List<OFAction> al = new ArrayList<OFAction>();
		al.add(aob.setPort(OFPort.ALL).build());
		fmb.setActions(al);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setIdleTimeout(10);
		fmb.setHardTimeout(60);
		fmb.setOutPort(OFPort.ALL); // let's try and mimic the hub module, but with a flow instead
									// is this really necessary with the OFOutputAction explicitly set?
		
		// we aren't matching anything specifically, so all should be wildcarded by default
		// in on any port, with any header attributes, send out all ports = Hub module
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		sfps.addFlow("test-flow", fmb.build(), DatapathId.of(1)); // This should add the flow, regardless whether or not the switch is connected, I believe. If it connects in a second, it should be pushed.
	}

}
