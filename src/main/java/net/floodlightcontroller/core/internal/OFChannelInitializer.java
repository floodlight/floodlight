/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.core.internal;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.types.U32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.internal.IOFSwitchManager;
import net.floodlightcontroller.core.internal.HandshakeTimeoutHandler;
import net.floodlightcontroller.core.internal.INewOFConnectionListener;
import net.floodlightcontroller.core.internal.OFChannelHandler;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.Timer;

/**
 * Creates a ChannelPipeline for a server-side openflow channel
 * @author readams, sovietaced, rizard, andi-bigswitch
 */
public class OFChannelInitializer extends ChannelInitializer<Channel> {
	private static final Logger log = LoggerFactory.getLogger(OFChannelInitializer.class);

	private IOFSwitchManager switchManager;
	private INewOFConnectionListener connectionListener;
	private Timer timer;
	private IDebugCounterService debugCounters;
	private String keyStore;
	private String keyStorePassword;
	private OFFactory defaultFactory;
	private List<U32> ofBitmaps;

	public OFChannelInitializer(IOFSwitchManager switchManager,
			INewOFConnectionListener connectionListener,
			IDebugCounterService debugCounters,
			Timer timer,
			List<U32> ofBitmaps,
			OFFactory defaultFactory,
			String keyStore, 
			String keyStorePassword) {
		super();
		this.switchManager = switchManager;
		this.connectionListener = connectionListener;
		this.timer = timer;
		this.debugCounters = debugCounters;
		this.defaultFactory = defaultFactory;
		this.ofBitmaps = ofBitmaps;
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		OFChannelHandler handler = new OFChannelHandler(
				switchManager,
				connectionListener,
				pipeline,
				debugCounters,
				timer,
				ofBitmaps,
				defaultFactory);

		if (keyStore != null && keyStorePassword != null) {
			try {
				/* Set up factories and stores. */
				TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				KeyStore tmpKS = null;
				tmFactory.init(tmpKS);

				/* Use keystore/pass defined in properties file. */
				KeyStore ks = KeyStore.getInstance("JKS");
				ks.load(new FileInputStream(keyStore), keyStorePassword.toCharArray());

				KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(ks, keyStorePassword.toCharArray());

				KeyManager[] km = kmf.getKeyManagers();
				TrustManager[] tm = tmFactory.getTrustManagers();

				/* Set up SSL prereqs for Netty. */
				SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(km, tm, null);
				SSLEngine sslEngine = sslContext.createSSLEngine();

				/* We are the server and we will create secure sessions. */
				sslEngine.setUseClientMode(false);
				sslEngine.setEnableSessionCreation(true);

				/* These are redundant (default), but for clarity... */
				sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols()); 
				sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
				
				/* First, decrypt w/handler+engine; then, proceed with rest of handlers. */
				pipeline.addLast(PipelineHandler.SSL_TLS_ENCODER_DECODER, new SslHandler(sslEngine));
				log.info("SSL OpenFlow socket initialized and handler ready for switch.");
			} catch (Exception e) { /* There are lots of possible exceptions to catch, so this should get them all. */
				log.error("Exception initializing SSL OpenFlow socket: {}", e.getMessage());
				throw e; /* If we wanted secure but didn't get it, we should bail. */
			}
		}
		
		pipeline.addLast(PipelineHandler.OF_MESSAGE_DECODER,
				new OFMessageDecoder());
		pipeline.addLast(PipelineHandler.OF_MESSAGE_ENCODER,
				new OFMessageEncoder());
		pipeline.addLast(PipelineHandler.MAIN_IDLE,
				new IdleStateHandler(PipelineIdleReadTimeout.MAIN,
						PipelineIdleWriteTimeout.MAIN,
						0));
		pipeline.addLast(PipelineHandler.READ_TIMEOUT, new ReadTimeoutHandler(30));
		pipeline.addLast(PipelineHandler.CHANNEL_HANDSHAKE_TIMEOUT,
				new HandshakeTimeoutHandler(
						handler,
						timer,
						PipelineHandshakeTimeout.CHANNEL));

		pipeline.addLast(PipelineHandler.CHANNEL_HANDLER, handler);
	}

	public static class PipelineHandler {
		public final static String CHANNEL_HANDSHAKE_TIMEOUT = "channelhandshaketimeout";
		public final static String SWITCH_HANDSHAKE_TIMEOUT = "switchhandshaketimeout";
		public final static String CHANNEL_HANDLER = "channelhandler";
		public final static String MAIN_IDLE = "mainidle";
		public final static String AUX_IDLE = "auxidle";
		public final static String OF_MESSAGE_DECODER = "ofmessagedecoder";
		public final static String OF_MESSAGE_ENCODER = "ofmessageencoder";
		public final static String READ_TIMEOUT = "readtimeout";
		public final static String SSL_TLS_ENCODER_DECODER = "ofsecurechannelencoderdecoder";    }

	/**
	 * Timeouts for parts of the handshake, in seconds
	 */
	 public static class PipelineHandshakeTimeout {
		 final static int CHANNEL = 10;
		 public final static int SWITCH = 30;
	 }

	 /**
	  * Timeouts for writes on connections, in seconds
	  */
	 public static class PipelineIdleWriteTimeout {
		 final static int MAIN = 2;
		 public final static int AUX = 15;
	 }

	 /**
	  * Timeouts for reads on connections, in seconds
	  */
	 public static class PipelineIdleReadTimeout {
		 final static int MAIN = 3 * PipelineIdleWriteTimeout.MAIN;
		 public final static int AUX = 3 * PipelineIdleWriteTimeout.AUX;
	 }
}