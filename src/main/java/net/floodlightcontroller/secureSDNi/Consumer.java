/** 
By Benamrane Fouad

*/
package net.floodlightcontroller.secureSDNi;

/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */


import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.dataCollector.DataTracker;
import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.firewall.IFirewallService;
import net.floodlightcontroller.firewall.FirewallRule.FirewallAction;
import net.floodlightcontroller.loadbalancer.ILoadBalancerService;
import net.floodlightcontroller.loadbalancer.LBMember;
import net.floodlightcontroller.loadbalancer.LBPool;
import net.floodlightcontroller.loadbalancer.LBVip;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple SSL chat server modified from {@link TelnetServer}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class Consumer implements IFloodlightModule{
	protected conf myConf;
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	//protected IThreadPoolService threadPool;
	protected SingletonTask mythread;
	protected Runnable SS;
	protected IThreadPoolService threadPoolService;
	protected IFirewallService firewallService;
	protected ILoadBalancerService lbService;
	protected FirewallRule newRule;
	protected LBVip newVip;
	protected LBPool newPool;
	protected LBMember newMember;
	public static DataTracker datatracker;
	public static Consumer consumer = null;
	protected static List<String> receivedVipNames;
	protected static List<String> receivedPoolNames;
	protected static List<Integer> receivedMemberIPs;
	protected static List<Integer> receivedRulesIds;
	static final ChannelGroup channels = new DefaultChannelGroup();
	protected String message;
	public static List<Integer> getReceivedRulesIds() {
		return receivedRulesIds;
	} 
	
	public static List<String> getReceivedVipNames() {
		return receivedVipNames;
	}

	public static List<String> getReceivedPoolNames() {
		return receivedPoolNames;
	}

	public static List<Integer> getReceivedMemberIPs() {
		return receivedMemberIPs;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public static void setReceivedRulesIds(List<Integer> receivedRulesIds) {
		Consumer.receivedRulesIds = receivedRulesIds;
	}

	public static synchronized Consumer getConsumer(){
		if(consumer ==null){
			consumer = new Consumer();
		}
		return consumer;
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
		    l.add(IThreadPoolService.class);
		    l.add(IFirewallService.class);
		    l.add(ILoadBalancerService.class);
		    return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		//threadPool = context.getServiceImpl(IThreadPoolService.class);  
		logger = LoggerFactory.getLogger(Consumer.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		myConf=new conf();
		firewallService = context.getServiceImpl(IFirewallService.class);
		lbService = context.getServiceImpl(ILoadBalancerService.class);
		newRule= new FirewallRule();
		newVip = new LBVip();
		newPool= new LBPool();
		newMember = new LBMember();
		datatracker = new DataTracker();
		receivedRulesIds = new ArrayList<Integer>();
		receivedMemberIPs = new ArrayList<Integer>();
		receivedPoolNames = new ArrayList<String>();
		receivedVipNames = new ArrayList<String>();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		mythread = new SingletonTask (ses, new Runnable() {
			public void run() {
				try{
					Consumer();
				}catch (Exception e) {
					throw new RuntimeException(e);
				}
	}
		});
		
		mythread.reschedule(2,TimeUnit.SECONDS);

	}
	public void Consumer(){
		ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Configure the pipeline factory.
        bootstrap.setPipelineFactory(new SecureConsumerPipelineFactory());

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(myConf.getPort()));
	}
	
	public class SecureConsumerPipelineFactory implements
    ChannelPipelineFactory {
		
		public ChannelPipeline getPipeline() throws Exception {
    ChannelPipeline pipeline = pipeline();

    // Add SSL handler first to encrypt and decrypt everything.
    // In this example, we use a bogus certificate in the server side
    // and accept any invalid certificates in the client side.
    // You will need something more complicated to identify both
    // and server in the real world.
    //
    // Read SecureChatSslContextFactory
    // if you need client certificate authentication.
    if(myConf.isSSL()){
    SSLEngine engine =
        SecureSslContextFactory.getServerContext().createSSLEngine();
    engine.setUseClientMode(false);

    pipeline.addLast("ssl", new SslHandler(engine));
    }
    // On top of the SSL handler, add the text line codec.
    pipeline.addLast("decoder", new StringDecoder());
    pipeline.addLast("encoder", new StringEncoder());

    // and then business logic.
    pipeline.addLast("handler", new DataUpdater());

    return pipeline;
}
}

	
	public class DataUpdater extends SimpleChannelUpstreamHandler {
		final ChannelGroup channels = new DefaultChannelGroup();
	    @Override
	    public void handleUpstream(
	            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
	        if (e instanceof ChannelStateEvent) {
	            logger.info(e.toString());
	        }
	        super.handleUpstream(ctx, e);
	    }

	    @Override
	    public void channelConnected(
	            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
	    	if(myConf.isSSL()){
	        // Get the SslHandler in the current pipeline.
	        // We added it in SecureChatPipelineFactory.
	        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);

	        // Get notified when SSL handshake is done.
	        ChannelFuture handshakeFuture = sslHandler.handshake();
	        handshakeFuture.addListener(new Greeter(sslHandler));
	    	}
	    	logger.info("Consumer::Remote controller: "+e.getChannel().getRemoteAddress().toString()+" is connected!");
	    }

	    @Override
	    public void channelDisconnected(
	            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
	        // Unregister the channel from the global channel list
	        // so the channel does not receive messages anymore.
	        channels.remove(e.getChannel());
	        logger.info("Consumer::Remote controller: "+e.getChannel().getRemoteAddress().toString()+" is disconnected from the server.");
	    }
	    
	    @Override
	    public void messageReceived(
	            ChannelHandlerContext ctx, MessageEvent e) throws Exception {

	    	message=e.getMessage().toString();
			message = message.replace("\n", "");
			if(message.contains("Hello")){
				e.getChannel().write("Hello \n");
				logger.info("Consumer::from "+e.getRemoteAddress().toString()+":"+message);
			}
		
			else if(message.contains("Firewall") && myConf.isFirewall()){
				logger.info("New remote firewall rule received ");
				if(!firewallService.isEnabled())
				{
					firewallService.enableFirewall(true);
				}
					treatMessage(message);
					
				
		}	
			else if(message.contains("LoadBalancer Vip created") && myConf.isLoadBalancer()){
			logger.info("New remote Load Balancer vip received");
			treatMessageVip(message);
		}
			else if(message.contains("LoadBalancer Pool created") && myConf.isLoadBalancer()){
			logger.info("New remote Load Balancer pool received");
			treatMessagePool(message);
		}
			else if(message.contains("LoadBalancer Member created") && myConf.isLoadBalancer()){
			logger.info("New remote Load Balancer Member received");
			treatMessageMember(message);
		}
		else logger.info("Consumer::from "+e.getRemoteAddress().toString()+": "+message);

		}
	    
	    public void treatMessageMember(String Member){
	    	String [] listMessage;
	    	
	    	listMessage = Member.split(",");
	    	String id = listMessage[1];
	    	newMember.setId(id);
	    	int ip = Integer.parseInt(listMessage[2]);
	    	if(!receivedMemberIPs.contains(ip))
	    		receivedMemberIPs.add(ip);
	    	newMember.setAddress(ip);
	    	short port = Short.parseShort(listMessage[3]);
	    	newMember.setPort(port);
	    	String poolId = listMessage[4];
	    	newMember.setPoolId(poolId);
	    	lbService.createMember(newMember);
	    }
		public void treatMessagePool(String pool){
			
			String [] listMessage;
			listMessage = pool.split(",");
			String id = listMessage[1];
			newPool.setId(id);
			String name = listMessage[2];
			if(!receivedPoolNames.contains(name))
	    		receivedPoolNames.add(name);
			newPool.setName(name);
			byte protocol = Byte.parseByte(listMessage[3]);
			newPool.setProtocol(protocol);
			String vipId= listMessage[4];
			newPool.setVipId(vipId);
			lbService.createPool(newPool);
		}
		public void treatMessageVip(String vip){
			String[] listMessage;
			listMessage = vip.split(",");
			String id=listMessage[1];
			newVip.setId(id);
			String name = listMessage[2];
			if(!receivedVipNames.contains(name))
	    		receivedVipNames.add(name);
			newVip.setName(name);
			byte protocol = Byte.parseByte(listMessage[3]);
			newVip.setProtocol(protocol);
			int ip = Integer.parseInt(listMessage[4]);
			newVip.setAddress(ip);
			short port = Short.parseShort(listMessage[5]);
			newVip.setPort(port);
			lbService.createVip(newVip);
		}
		
		@SuppressWarnings("static-access")
		public void treatMessage(String msg){
			String[] listMessage; 
			listMessage = msg.split(",");
			int ID=Integer.parseInt(listMessage[1]);
			newRule.setRuleid(ID);
			if(!receivedRulesIds.contains(ID))
				receivedRulesIds.add(ID);
			/*if(!datatracker.getRuleIds().contains(ID))
			{*/
			DatapathId id = DatapathId.of(listMessage[2]);
			newRule.setDpid(id);
			OFPort port;
			if (listMessage[3].equals("any"))
				port =OFPort.ANY;
			else if (listMessage[3].equals("all"))
				port =OFPort.ALL;
			else if (listMessage[3].equals("flood"))
				port =OFPort.FLOOD;
			else port = OFPort.of(Integer.parseInt(listMessage[3]));
			newRule.setIn_port(port);
			MacAddress Mac = MacAddress.of(listMessage[4]);
			newRule.setDl_src(Mac);
			Mac = MacAddress.of(listMessage[5]);
			newRule.setDl_dst(Mac);
			EthType type = EthType.of(Integer.parseInt(listMessage[6]));
			newRule.setDl_type(type);
			IPv4AddressWithMask ip = IPv4AddressWithMask.of(listMessage[7]);
			newRule.setNw_src_prefix_and_mask(ip);
			ip = IPv4AddressWithMask.of(listMessage[8]);
			newRule.setNw_dst_prefix_and_mask(ip);
			IpProtocol proto = IpProtocol.of(Short.parseShort(listMessage[9]));
			newRule.setNw_proto(proto);
			TransportPort tcpPort = TransportPort.of(Integer.parseInt(listMessage[10]));
			newRule.setTp_src(tcpPort);
			tcpPort = TransportPort.of(Integer.parseInt(listMessage[11]));
			newRule.setTp_dst(tcpPort);
			boolean anyS = stringToBoolean(listMessage[12]);
			newRule.setAny_in_port(anyS);
			anyS = stringToBoolean(listMessage[13]);
			newRule.setAny_dl_src(anyS);
			anyS = stringToBoolean(listMessage[14]);
			newRule.setAny_dl_dst(anyS);
			anyS = stringToBoolean(listMessage[15]);
			newRule.setAny_dl_type(anyS);
			anyS = stringToBoolean(listMessage[16]);
			newRule.setAny_nw_src(anyS);
			anyS = stringToBoolean(listMessage[17]);
			newRule.setAny_nw_dst(anyS);
			anyS = stringToBoolean(listMessage[18]);
			newRule.setAny_nw_proto(anyS);
			anyS = stringToBoolean(listMessage[19]);
			newRule.setAny_tp_src(anyS);
			anyS = stringToBoolean(listMessage[20]);
			newRule.setAny_tp_dst(anyS);
			newRule.setPriority(Integer.parseInt(listMessage[21]));
			FirewallAction action = FirewallAction.valueOf(listMessage[22]);
			newRule.setAction(action);
			firewallService.addRule(newRule);
			//}
			//else logger.info("Rule already existe");
		}
		public boolean stringToBoolean(String msg)
		{
			if(msg.equals("true"))
				return true;
			else return false;
		}


	    @Override
	    public void exceptionCaught(
	            ChannelHandlerContext ctx, ExceptionEvent e) {
	        logger.warn("Unexpected exception from downstream.",
	                e.getCause());
	        e.getChannel().close();
	    }

	    /**
	     * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
	     * @author <a href="http://gleamynode.net/">Trustin Lee</a>
	     * @version $Rev: 2121 $, $Date: 2010-02-02 09:38:07 +0900 (Tue, 02 Feb 2010) $
	     */
	    private final class Greeter implements ChannelFutureListener {

	        private final SslHandler sslHandler;

	        Greeter(SslHandler sslHandler) {
	            this.sslHandler = sslHandler;
	        }

	        public void operationComplete(ChannelFuture future) throws Exception {
	            if (future.isSuccess()) {
	                // Once session is secured, send a greeting.
	            	
	                future.getChannel().write(
	                        "Welcome to " + InetAddress.getLocalHost().getHostName() +
	                        " secure chat service!\n");
	                future.getChannel().write(
	                        "Your session is protected by " +
	                        sslHandler.getEngine().getSession().getCipherSuite() +
	                        " cipher suite.\n");

	                // Register the channel to the global channel list
	                // so the channel received the messages from others.
	                channels.add(future.getChannel());
	                
	            } else {
	                future.getChannel().close();
	            }
	        }
	    }
	}



}



