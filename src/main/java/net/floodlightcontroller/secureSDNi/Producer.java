# By Benamrane Fouad

package net.floodlightcontroller.secureSDNi;

import static org.jboss.netty.channel.Channels.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Producer implements IFloodlightModule{

	    protected static List<Channel> CH; 
	    protected IFloodlightProviderService floodlightProvider;
	    protected static Logger logger;
	    protected SingletonTask mythread;
	    protected Runnable SS;
	    protected IThreadPoolService threadPool;
	    private ClientBootstrap bootstrap;
	    protected static ChannelFuture future;
	    private static Producer netClient=null;
	    protected conf myConf;
	    protected static ChannelFuture connection;
	   
	    
	    
	    
	    public static synchronized Producer getnetClient(){
	    	if(netClient==null){
	    		netClient=new Producer();
	    	}
	    	return netClient;
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
		            return l;
		}

		@Override
		public void init(FloodlightModuleContext context)
				throws FloodlightModuleException {
			myConf = new conf();
	    	CH= new ArrayList<Channel>();
	        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	        logger = LoggerFactory.getLogger(Producer.class);
	        threadPool=context.getServiceImpl(IThreadPoolService.class);
	   
			
		}

		@Override
		public void startUp(FloodlightModuleContext context)
				throws FloodlightModuleException {
			ScheduledExecutorService ses = threadPool.getScheduledExecutor();
	        mythread = new SingletonTask (ses,new Runnable() {
	            public void run() {
	                try{
	                	logger.info("Waiting 10 sec");
	                    Thread.sleep(10000);
	                    logger.info("Producer :: "+myConf.getMode()+" mode is configured");
	                    producer();
	                    
	                }
	                catch(Exception e){
	                    logger.error("Exception detected "+e.getMessage());
	                }
	        
	    }
	        });
	        mythread.reschedule(1,  TimeUnit.SECONDS);
	        
	    }
	    public void producer() throws IOException{
	    	// Configure the client.
		     bootstrap = new ClientBootstrap( new NioClientSocketChannelFactory(
		                     Executors.newCachedThreadPool(),
		                     Executors.newCachedThreadPool()));

		     // Configure the pipeline factory.
		     bootstrap.setPipelineFactory(new SecureProducerPipelineFactory());

		     // Start the connection attempt.
		     for(String h:myConf.getL()){
		     future = bootstrap.connect(new InetSocketAddress(h, myConf.getPort()));
		     if(!future.awaitUninterruptibly().isSuccess()) {
	 	            logger.info("Producer :: Unable to connect to host " + h + ":" + myConf.getPort());
	 	        }
		     else{ logger.info("Producer :: Successfully connected to " + h + ":" + myConf.getPort());
	            CH.add(future.getChannel());
	            future.getChannel().write("Hello \n");
	        	logger.info("Producer :: Hello sent to remote consumer");
	        }
		     } }
		     // Wait until the connection attempt succeeds or fails.
		     //Channel channel = future.awaitUninterruptibly().getChannel();
		     /*if (!future.isSuccess()) {
		         future.getCause().printStackTrace();
		         bootstrap.releaseExternalResources();
		         return;
		     }
		     */
		     // Read commands from the stdin.
		     /*ChannelFuture lastWriteFuture = null;
		     BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		     for (;;) {
		         String line = in.readLine();
		         if (line == null) {
		             break;
		         }

		         // Sends the received line to the server.
		         lastWriteFuture = channel.write(line + "\r\n");

		         // If user typed the 'bye' command, wait until the server closes
		         // the connection.
		         if (line.toLowerCase().equals("bye")) {
		             channel.getCloseFuture().awaitUninterruptibly();
		             break;
		         }
		     }

		     // Wait until all messages are flushed before closing the channel.
		     if (lastWriteFuture != null) {
		         lastWriteFuture.awaitUninterruptibly();
		     }

		     // Close the connection.  Make sure the close operation ends because
		     // all I/O operations are asynchronous in Netty.
		     channel.close().awaitUninterruptibly();

		     // Shut down all thread pools to exit.
		     bootstrap.releaseExternalResources();*/
	   // }


		public static List<Channel> getCH() {
			return CH;
		}


		public void setCH(List<Channel> cH) {
			CH = cH;
		}


		public static ChannelFuture getFuture() {
			return future;
		}


		public static void setFuture(ChannelFuture future) {
			Producer.future = future;
		}
	    
		public class SecureProducerPipelineFactory implements
		ChannelPipelineFactory {
		
		public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = pipeline();

		// Add SSL handler first to encrypt and decrypt everything.
		// In this example, we use a bogus certificate in the server side
		// and accept any invalid certificates in the client side.
		// You will need something more complicated to identify both
		// and server in the real world.
		if(myConf.isSSL()){
		SSLEngine engine =
		    SecureSslContextFactory.getClientContext().createSSLEngine();
		engine.setUseClientMode(true);

			
		pipeline.addLast("ssl", new SslHandler(engine));
		}
		// On top of the SSL handler, add the text line codec.
		pipeline.addLast("decoder", new StringDecoder());
		pipeline.addLast("encoder", new StringEncoder());

		// and then business logic.
		pipeline.addLast("handler", new SecureProducerHandler());

			return pipeline;
		}

		}
		
		public class SecureProducerHandler extends SimpleChannelUpstreamHandler {
			
			 //protected boolean SSL
			
			    @Override
			    public void handleUpstream(
			            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
			        /*if (e instanceof ChannelStateEvent) {
			            logger.info(e.toString());
			        }
			        super.handleUpstream(ctx, e);*/
			    }

			    @Override
			    public void channelConnected(
			            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
			        // Get the SslHandler from the pipeline
			        // which were added in SecureChatPipelineFactory.
			    	if(myConf.isSSL()){

			        SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);

			        // Begin handshake.
			        sslHandler.handshake();
			       }
			    	logger.info("Producer:: Remote controller"+ctx.getChannel().getRemoteAddress().toString()+" is connected");
			    }

			    @Override
			    public void messageReceived(
			            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
			    	if(!e.getMessage().equals("Hello \n"))
						logger.info("Producer::from "+e.getRemoteAddress().toString()+":"+e.getMessage().toString().replace("\n", ""));
			    		super.messageReceived(ctx, e);
			    }

			    @Override
			    public void exceptionCaught(
			            ChannelHandlerContext ctx, ExceptionEvent e) {
			        logger.warn(
			                "Unexpected exception from downstream.",
			                e.getCause());
			        e.getChannel().close();
			    }
		}


		
}