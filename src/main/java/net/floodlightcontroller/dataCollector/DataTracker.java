package net.floodlightcontroller.dataCollector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.channel.Channel;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPAddressWithMask;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IAppHandshakePluginFactory;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.rest.SwitchRepresentation;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.firewall.Firewall;
import net.floodlightcontroller.firewall.FirewallListener;
import net.floodlightcontroller.firewall.FirewallListenerImp;
import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.firewall.IFirewallService;
import net.floodlightcontroller.loadbalancer.ILoadBalancerService;
import net.floodlightcontroller.loadbalancer.LBMember;
import net.floodlightcontroller.loadbalancer.LBPool;
import net.floodlightcontroller.loadbalancer.LBVip;
import net.floodlightcontroller.loadbalancer.LoadBalancerListener;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.secureSDNi.Consumer;
import net.floodlightcontroller.secureSDNi.Producer;
import net.floodlightcontroller.secureSDNi.conf;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class DataTracker implements IFloodlightModule,FirewallListener, IDeviceListener,IOFSwitchListener,LoadBalancerListener{
	protected IFloodlightProviderService floodlightProvider;
	protected List<IDevice> deviceList;
	protected List<IOFSwitch> switchList;
	protected IDeviceService deviceService;
	protected IOFSwitchService switchService;
	protected List<Channel> channel;
	protected SingletonTask mythread;
	protected IThreadPoolService threadPool;
	public static Producer netClient;
	protected conf myConf;
	public static DataTracker datatracker=null;
	public static Firewall firewall;
	protected IOFSwitch sw;
	protected String msg;
	
	protected static Logger logger;
	protected FirewallListenerImp listener;
	protected IFirewallService firewallService;
	protected ILoadBalancerService loadBalancer;
	protected List<IPv4Address> Ips;
	//protected static List<Integer> Ruleids;
	public static Consumer consumer;
	
/*	public static List<Integer> getRuleids() {
		return Ruleids;
	}
	public void setRuleids(List<Integer> ruleids) {
		Ruleids = ruleids;
	}*/
	public static synchronized DataTracker getDatatracker(){
    	if(datatracker==null){
    		datatracker=new DataTracker();
    	}
    	return datatracker;
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
		l.add(IDeviceService.class);
		l.add(IOFSwitchService.class);
		l.add(IThreadPoolService.class);
		l.add(IFirewallService.class);
		l.add(ILoadBalancerService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		deviceList = new LinkedList<IDevice>();
		switchList = new LinkedList<IOFSwitch>();
	    logger = LoggerFactory.getLogger(DataTracker.class);
	    deviceService = context.getServiceImpl(IDeviceService.class);
	    switchService=context.getServiceImpl(IOFSwitchService.class);
	    threadPool=context.getServiceImpl(IThreadPoolService.class);
	    netClient = new Producer();
	    myConf=new conf();
	    firewallService = context.getServiceImpl(IFirewallService.class);
	    loadBalancer = context.getServiceImpl(ILoadBalancerService.class);
	    firewall = new Firewall();
	    listener = new FirewallListenerImp(firewall);
	    Ips = new ArrayList<IPv4Address>();
	    //Ruleids = new ArrayList<Integer>();
	    consumer = new Consumer();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		deviceService.addListener(this);
		switchService.addOFSwitchListener(this);
		firewallService.register(this);
		loadBalancer.register(this);
    }

    @SuppressWarnings("static-access")
	public void writeObject(String msg){
    	for(Channel c:netClient.getCH()){
    		if(c.isConnected() && (myConf.getMode().equals("Notification") ||myConf.getMode().equals("Full"))){
    			c.write(msg);
    			
    		}
		}
    }
    @SuppressWarnings("static-access")
	public void writeObjectService(String msg){
    	for(Channel c:netClient.getCH()){
    		if(c.isConnected() && (myConf.getMode().equals("Service") ||myConf.getMode().equals("Full"))){
    			c.write(msg);
    			
    		}
		}
    }
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return DataTracker.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(String type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(String type, String name) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public void switchDisplay(){

	}
	
	@Override
	public void switchAdded(DatapathId switchId) {
		logger.info("New switch connected "+switchId);
		sw=switchService.getSwitch(switchId);
		msg="New remote switch detected "+switchId.toString()+"\n";
		logger.info("Map all switches "+switchService.getAllSwitchDpids());
		writeObject(msg);
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		logger.info("Switch removed "+switchId);
		//msg="Remote switch removed "+switchId.toString()+"\n";
		//logger.info("Map all switches "+switchService.getAllSwitchDpids());
		//writeObject(msg);
		
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
		logger.info("State changed in Switch "+switchId.toString()+" port:"+ port.getName()+" Type:"+type.toString());
		//msg="Remote switch state changed "+switchId.toString()+" port:"+ port.getName()+" Type:"+type.toString()+"\n";
		//writeObject(msg);
		
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deviceAdded(IDevice device) {
		/*logger.info("New device connected "+device.getMACAddress().toString());	
		String msg="New remote device connected "+device.getMACAddressString().toString()+"\n";
		writeObject(msg);*/
	}

	@Override
	public void deviceRemoved(IDevice device) {
		logger.info("Device removed "+device.getIPv4Addresses().toString());
		//String msg="Remote device removed "+device.getMACAddressString().toString()+"\n";
		//writeObject(msg);
		
	}

	@Override
	public void deviceMoved(IDevice device) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deviceIPV4AddrChanged(IDevice device) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deviceVlanChanged(IDevice device) {
		// TODO Auto-generated method stub
		
	}

	public boolean ipExiste(FirewallRule R){
		IPv4Address srcIp;
		IPv4Address dstIp;
		String srcMsg = ""+R.nw_src_prefix_and_mask;
		String dstMsg = ""+R.nw_dst_prefix_and_mask;
		String msgs = (srcMsg).substring(8);
		String msgd = (dstMsg).substring(8);
		srcMsg = srcMsg.replace(msgs, "");
		dstMsg = dstMsg.replace(msgd, "");
		srcIp =IPv4Address.of(srcMsg);
		dstIp=IPv4Address.of(dstMsg);
		if(!Ips.contains(srcIp) || !Ips.contains(dstIp))
		return false;
		else return true;
	}
	public String composedMessageFw(FirewallRule R){
		String message = "Firewall,"+R.ruleid+","+R.dpid+","+R.in_port+","+R.dl_src+","
				+R.dl_dst+","+R.dl_type+","+R.nw_src_prefix_and_mask+","+R.nw_dst_prefix_and_mask+
				","+R.nw_proto+","+R.tp_src+","+R.tp_dst+","+R.any_in_port+","+R.any_dl_src+
				","+R.any_dl_dst+","+R.any_dl_type+","+R.any_nw_src+","+R.any_nw_dst+","+
				R.any_nw_proto+","+R.any_tp_src+","+R.any_tp_dst+","+R.priority+","+R.action;
		return message;
		
	}
	
	public String composedMessageLBVip(LBVip vip){
		//"id":"1","name":"vip1","protocol":"icmp","address":"10.0.0.100","port":"8"
		String message = "LoadBalancer Vip created,"+vip.getId()+","+vip.getName()+","+vip.getProtocol()+","+vip.getAddress()+","+vip.getPort();
		return message;
	
	}
	public String composeMessageLBPool(LBPool pool){
		//"id":"1","name":"pool1","protocol":"icmp","vip_id":"1"
		String message="LoadBalancer Pool created,"+pool.getId()+","+pool.getName()+","+pool.getProtocol()+","+pool.getVipId();
		return message;
		
	}
	public String composeMessageLBMember(LBMember member){
		//"id":"1","address":"10.0.0.3","port":"8","pool_id":"1"
		String message = "LoadBalancer Member created,"+member.getId()+","+member.getAddress()+","+member.getPort()+","+member.getPoolId();
		return message;
	}
	 
	@SuppressWarnings("static-access")
	@Override
	public void updateNewRule(FirewallRule R) {
		// TODO Auto-generated method stub
		logger.info("New rule is added "+R.dpid+ " Role id "+R.ruleid);
		//Ruleids.add(R.ruleid);
		if(!consumer.getReceivedRulesIds().contains(R.ruleid) && myConf.isFirewall()){
		if(ipExiste(R)){
			//writeObject(ms);
			logger.info("IP existe in this domain ");
		}
		else {
			logger.info("IP does not existe in this domain, request sent to remote controllers");
			writeObjectService(composedMessageFw(R));
		}
		}
	}

	@Override
	public void updateRemoveRule(FirewallRule R) {
		// TODO Auto-generated method stub
		logger.info("Current rule "+R.ruleid+" is removed.");
	}

	@Override
	public void firewallActivated() {
		// TODO Auto-generated method stub
		//String msg = "Firewall is activated.";
		//logger.info(msg);
		//writeObject(msg);
	}

	@Override
	public void getIPV4address(IPv4Address ip) {
		/*if (!Ips.contains(ip)){
			Ips.add(ip);
		logger.info("New device added "+ip);
		String msg="New remote device connected "+ip+"\n";
		writeObject(msg);
		}*/
	}
	@Override
	public void NewVip(LBVip vip) {
		// TODO Auto-generated method stub
		logger.info("New Vip is created id="+vip.getId()+" Address= "+intToIp(vip.getAddress()));
		if(!consumer.getReceivedVipNames().contains(vip.getName()) && myConf.isLoadBalancer())
			writeObjectService(composedMessageLBVip(vip));
		
	}
	@Override
	public void NewPool(LBPool pool) {
		// TODO Auto-generated method stub
		logger.info("New pool is created name "+ pool.getName());
		if(!consumer.getReceivedPoolNames().contains(pool.getName())&& myConf.isLoadBalancer())
			writeObjectService(composeMessageLBPool(pool));
	}
	@Override
	public void NewMember(LBMember member) {
		// TODO Auto-generated method stub
		logger.info("New Member is created "+intToIp(member.getAddress())+" port= "+member.getPort());
		if(!consumer.getReceivedMemberIPs().contains(member.getAddress()) && myConf.isLoadBalancer())
			writeObjectService(composeMessageLBMember(member));
	}
	@Override
	public void LBActive() {
		// TODO Auto-generated method stub
		
	}
	public static String intToIp(int ip){
		String ipStr = 
		  String.format("%d.%d.%d.%d", (ip >> 24 & 0xff),(ip >> 16 & 0xff),(ip >> 8 & 0xff),
		         (ip & 0xff));
		return ipStr;
		
	}

}
