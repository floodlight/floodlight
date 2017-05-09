# By Benamrane Fouad

package net.floodlightcontroller.dataCollector;


import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.secureSDNi.Producer;
import net.floodlightcontroller.secureSDNi.conf;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class FlowCollector implements IOFMessageListener, IFloodlightModule{
	
	protected IFloodlightProviderService floodlightPro;
	protected static Logger logger;
	protected List<OFType> flowList;
	public static Producer netClient;
	protected SingletonTask mythread;
	protected IThreadPoolService threadPool;
	protected String message;
	OFMessage writtenMessage;
	protected conf myConf;
	@SuppressWarnings("static-access")
	public void writeObject(String msg){
    	for(Channel c:netClient.getCH()){
    		if(c.isConnected()){
    			c.write(msg);
    			
    		}
		}
	}
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		    l.add(IFloodlightProviderService.class);
		    return l;
	}


	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightPro = context.getServiceImpl(IFloodlightProviderService.class);
		threadPool=context.getServiceImpl(IThreadPoolService.class);
		logger = LoggerFactory.getLogger(FlowCollector.class);
	    flowList = new LinkedList<OFType>();
	    netClient = new Producer();
	    myConf=new conf();
	    
		
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightPro.addOFMessageListener(OFType.PACKET_IN, this);
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return FlowCollector.class.getSimpleName();
	}

	

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		/*flowList.add(type);
		logger.info("New flow detected "+type.toString());
		message="New flow detected "+type.toString();
		writeObject(message);*/
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		/*flowList.add(type);
		logger.info("New flow detected "+type.toString());
		message="New flow detected "+type.toString();
		writeObject(message);*/
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		//message = "";
		//flowList.add(msg.getType());
		if(myConf.getMode().equals("Full")){
		message="\n New remote event received "+msg.getType()+" from "+sw.getId()+" , \n";
		logger.info("New event received "+msg.getType()+" from "+sw.getId());
		writeObject(message);
        //message = "";
		}
		return Command.STOP;
		
	}
}