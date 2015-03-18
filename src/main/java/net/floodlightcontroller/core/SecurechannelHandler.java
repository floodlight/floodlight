/* Floodlight enhancement for SSL is developed as part
 * of internal project at Aricent(India) - www.aricent.com
 * File is created for establishing SSL connection between
 * floodlight controller and OVS.
 * The switch expects the certificates and keys in .PEM format
 * Once the switch keys and controller keys are generated in .PEM format,
 * the controller keys are taken and modified to .JKS format
 * Please refer the documentation at https://floodlight.atlassian.net/wiki/pages/viewpage.action?pageId=4325395
 * for details on generation of certificates
 * created by ruverma@gmail.com and vtyagi@gmail.com
 */


package net.floodlightcontroller.core;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;

import javax.annotation.Nonnull;
//import org.openflow.protocol.factory;
import javax.net.ssl.KeyManagerFactory; 
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.core.internal.INewOFConnectionListener;
import net.floodlightcontroller.core.internal.IOFSwitchManager;
import net.floodlightcontroller.core.internal.OFChannelHandler;
import net.floodlightcontroller.core.internal.OFSwitchManager;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
//import org.jboss.netty.example.securechat.SecureChatServerHandler.Greeter;
import org.jboss.netty.handler.ssl.SslBufferPool;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class  SecurechannelHandler extends OFChannelHandler { 
    
    //protected ThreadPoolExecutor pipelineExecutor;
    public static boolean handshakeStatus;
    private static final Logger log = LoggerFactory.getLogger(SecurechannelHandler.class);  
    public SecurechannelHandler(IOFSwitchManager switchManager,
			INewOFConnectionListener newConnectionListener,
			ChannelPipeline pipeline,
			IDebugCounterService debugCounters,
			Timer timer) {
    	super(switchManager,newConnectionListener,pipeline,debugCounters,timer);
    }
    static final ChannelGroup channels = new DefaultChannelGroup();
    //private final ChannelFutureListener childListener = new ChannelFutureListener();
  
    //private DefaultChannelConfig chConf =new DefaultChannelConfig();
    //private DefaultSocketChannelConfig chConf =new DefaultSocketChannelConfig(new Socket());
	public static String secureStatus ;
	public static String keyStorePath;
	public static String trustStorePath;
	public static String keyStorePassword;
	public static String trustStorePassword;
	private SslBufferPool peerAppData; // clear text message received from the
    // switch
    private SslBufferPool peerNetData; // encrypted message from the switch
    static SSLContext SERVER_CONTEXT;
    
	//public ChannelFuture sslHandshake(int openFlowPort,ServerBootstrap bootstrap,Channel chan) 
    /* method to start the sslhandshaking with the switch*/
	public ChannelFuture sslHandshake(ChannelPipeline pipeline){
	 try{
		SSLContext sc= getServerContext();
		SSLEngine se = sc.createSSLEngine();
        se.setUseClientMode(false);
        se.setNeedClientAuth(false);
      
        se.setEnabledCipherSuites(new String[]{
        		"SSL_RSA_WITH_RC4_128_MD5",
                "SSL_RSA_WITH_RC4_128_SHA",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
                "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
                "SSL_RSA_WITH_DES_CBC_SHA",
                "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
                "TLS_EMPTY_RENEGOTIATION_INFO_SCSV"
        });

        peerNetData=new SslBufferPool();
        peerAppData= new SslBufferPool();
           
        SslHandler sslh1=new SslHandler(se,peerNetData,true);
        
        sslh1.setEnableRenegotiation(false);
                
        pipeline.addFirst("SslHandler",sslh1); 
       
        ChannelFuture handshakeFuture= sslh1.handshake();
                
        SERVER_CONTEXT = sc;
       
        //adding future listener
        handshakeFuture.addListener(new Greeter1(sslh1));
          
      return(handshakeFuture);
	 }
	 catch(Exception e) {
         throw new RuntimeException(e);
	 } 
	 finally{
		 //do nothing
		 } 
    }//end of sslHandshake()
	
	/*method for handling upstream */
	public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            
        	ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
            	if (Boolean.TRUE.equals(evt.getValue()))
            	{
                     ChannelPipeline pipeline= ctx.getPipeline();
                     if(SecurechannelHandler.handshakeStatus == false)
                     {
  		               ChannelFuture handshakeFuture= sslHandshake(pipeline);
                     }
            	}
  		        break;
  		    }
            super.handleUpstream(ctx, e);
        }
        if(e instanceof ChildChannelStateEvent)
        {
        	super.handleUpstream(ctx, e);
        }
        if(e instanceof WriteCompletionEvent)
        {
        	super.handleUpstream(ctx, e);
        }
        if(e instanceof MessageEvent)
        {
        	super.messageReceived(ctx, (MessageEvent) e);
        }
        if(e instanceof ExceptionEvent)
        {
        	//do nothing as exeception will anyways be handled by superclass
        }
   }
	
    /* method to read the certificate files using the passwords used for creation
     * of JKS truststore and JKS keystore
     */
	public static SSLContext getServerContext() {
    	try{
    	KeyStore ks = KeyStore.getInstance("JKS"); 
        ks.load(new FileInputStream(SecurechannelHandler.keyStorePath),(SecurechannelHandler.keyStorePassword).toCharArray());
        
        //truststore instance
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(new FileInputStream(SecurechannelHandler.trustStorePath),(SecurechannelHandler.trustStorePassword).toCharArray());
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, (SecurechannelHandler.keyStorePassword).toCharArray());
        
        TrustManagerFactory tmf= TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        /* WORKING old code temp comment*/  
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(kmf.getKeyManagers(), null, null);
        
        SERVER_CONTEXT = sc;
    	return SERVER_CONTEXT;
    	}
       	catch (Exception e) {
            throw new RuntimeException(e);} 
    finally {
    	//do nothing
    }
    }
 
	private static final class Greeter1 implements ChannelFutureListener {

        private final SslHandler sslHandler;

        Greeter1(SslHandler sslHandler) {
            this.sslHandler = sslHandler;
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                // Once session is secured, send a greeting.
                future.getChannel().write(
                        "Welcome to " + InetAddress.getLocalHost().getHostName() +
                        " secure SSL service!\n");
                future.getChannel().write(
                        "Your session is protected by " +
                        sslHandler.getEngine().getSession().getCipherSuite() +
                        " cipher suite.\n");
                
                log.info("Welcome to " + InetAddress.getLocalHost().getHostName() +
                        " secure SSL service!");
                log.info("Your session is protected by " +
                        sslHandler.getEngine().getSession().getCipherSuite() +
                        " cipher suite.");
                
                handshakeStatus=true;
                
                channels.add(future.getChannel());
            } else {
                future.getChannel().close();
            }
        }
    }
}//end of class def	

	
