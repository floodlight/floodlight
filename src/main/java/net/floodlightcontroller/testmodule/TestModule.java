package net.floodlightcontroller.testmodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.types.DatapathId;
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
		OFFlowAdd.Builder fmb = /*sw.getOFFactory()*/OFFactories.getFactory(OFVersion.OF_13).buildFlowAdd();
        //Match.Builder mb = OFFactories.getFactory(OFVersion.OF_13).buildMatch();
        
        //fmb.setBufferId(OFBufferId.NO_BUFFER)
        //fmb.setXid(pi.getXid());
        /*.setMatch(pi.getMatch())*/

        // set actions
        OFActionOutput.Builder actionBuilder = /*sw.getOFFactory()*/OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput();
        actionBuilder.setPort(OFPort.ALL);
        fmb.setActions(Collections.singletonList((OFAction) actionBuilder.build()));
        fmb.setOutPort(OFPort.ALL);
		
		// we aren't matching anything specifically, so all should be wildcarded by default
		// in on any port, with any header attributes, send out all ports = Hub module
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		sfps.addFlow("test-flow", fmb.build(), DatapathId.of(1)); // This should add the flow, regardless whether or not the switch is connected, I believe. If it connects in a second, it should be pushed.
		//sfps.deleteFlow("test-flow");

	}

}
