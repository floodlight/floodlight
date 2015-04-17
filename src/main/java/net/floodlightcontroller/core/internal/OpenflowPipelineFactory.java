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

import javax.annotation.Nonnull;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugcounter.IDebugCounterService;

/**
 * Creates a ChannelPipeline for a server-side openflow channel
 * @author readams, sovietaced
 */
public class OpenflowPipelineFactory
implements ChannelPipelineFactory, ExternalResourceReleasable {
	private static final Logger log = LoggerFactory.getLogger(OpenflowPipelineFactory.class);
	protected IOFSwitchManager switchManager;
	protected INewOFConnectionListener connectionListener;
	protected Timer timer;
	protected IdleStateHandler idleHandler;
	protected ReadTimeoutHandler readTimeoutHandler;
	protected IDebugCounterService debugCounters;
	private String keyStore;
	private String keyStorePassword;

	private void init(IOFSwitchManager switchManager, Timer timer,
			INewOFConnectionListener connectionListener,
			IDebugCounterService debugCounters) {
		this.switchManager = switchManager;
		this.connectionListener = connectionListener;
		this.timer = timer;
		this.debugCounters = debugCounters;
		this.idleHandler = new IdleStateHandler(
				timer,
				PipelineIdleReadTimeout.MAIN,
				PipelineIdleWriteTimeout.MAIN,
				0);
		this.readTimeoutHandler = new ReadTimeoutHandler(timer, 30);
	}

	public OpenflowPipelineFactory(IOFSwitchManager switchManager, Timer timer,
			INewOFConnectionListener connectionListener,
			IDebugCounterService debugCounters) {
		super();
		init(switchManager,timer, connectionListener, debugCounters);
		this.keyStore = null;
		this.keyStorePassword = null;
	}

	public OpenflowPipelineFactory(IOFSwitchManager switchManager, Timer timer,
			INewOFConnectionListener connectionListener,
			IDebugCounterService debugCounters,
			@Nonnull String keyStore, @Nonnull String keyStorePassword) {
		super();
		init(switchManager,timer, connectionListener, debugCounters);   
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
	}

	@Override
	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = Channels.pipeline();
		OFChannelHandler handler = new OFChannelHandler(switchManager,
				connectionListener,
				pipeline,
				debugCounters,
				timer);

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

		/* SSL handler will have been added first if we're using it. */
		pipeline.addLast(PipelineHandler.OF_MESSAGE_DECODER,
				new OFMessageDecoder());
		pipeline.addLast(PipelineHandler.OF_MESSAGE_ENCODER,
				new OFMessageEncoder());
		pipeline.addLast(PipelineHandler.MAIN_IDLE, idleHandler);
		pipeline.addLast(PipelineHandler.READ_TIMEOUT, readTimeoutHandler);
		pipeline.addLast(PipelineHandler.CHANNEL_HANDSHAKE_TIMEOUT,
				new HandshakeTimeoutHandler(
						handler,
						timer,
						PipelineHandshakeTimeout.CHANNEL));
		pipeline.addLast(PipelineHandler.CHANNEL_HANDLER, handler);
		return pipeline;
	}

	@Override
	public void releaseExternalResources() {
		timer.stop();
	}

	public static class PipelineHandler {
		final static String CHANNEL_HANDSHAKE_TIMEOUT = "channelhandshaketimeout";
		final static String SWITCH_HANDSHAKE_TIMEOUT = "switchhandshaketimeout";
		final static String CHANNEL_HANDLER = "channelhandler";
		final static String MAIN_IDLE = "mainidle";
		final static String AUX_IDLE = "auxidle";
		final static String OF_MESSAGE_DECODER = "ofmessagedecoder";
		final static String OF_MESSAGE_ENCODER = "ofmessageencoder";
		final static String READ_TIMEOUT = "readtimeout";
		final static String SSL_TLS_ENCODER_DECODER = "ofsecurechannelencoderdecoder";
	}

	/**
	 * Timeouts for parts of the handshake, in seconds
	 */
	public static class PipelineHandshakeTimeout {
		final static int CHANNEL = 10;
		final static int SWITCH = 30;
	}

	/**
	 * Timeouts for writes on connections, in seconds
	 */
	public static class PipelineIdleWriteTimeout {
		final static int MAIN = 2;
		final static int AUX = 15;
	}

	/**
	 * Timeouts for reads on connections, in seconds
	 */
	public static class PipelineIdleReadTimeout {
		final static int MAIN = 3 * PipelineIdleWriteTimeout.MAIN;
		final static int AUX = 3 * PipelineIdleWriteTimeout.AUX;
	}
}
