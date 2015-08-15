package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nonnull;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.jboss.netty.util.Timer;

import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.OFConnection;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.OpenflowPipelineFactory.PipelineHandler;
import net.floodlightcontroller.core.internal.OpenflowPipelineFactory.PipelineHandshakeTimeout;
import net.floodlightcontroller.core.internal.OpenflowPipelineFactory.PipelineIdleReadTimeout;
import net.floodlightcontroller.core.internal.OpenflowPipelineFactory.PipelineIdleWriteTimeout;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.exceptions.OFParseError;
import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFEchoRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFExperimenter;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFeaturesRequest;
import org.projectfloodlight.openflow.protocol.OFHello;
import org.projectfloodlight.openflow.protocol.OFHelloElem;
import org.projectfloodlight.openflow.protocol.OFHelloElemVersionbitmap;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.ver13.OFHelloElemTypeSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFHelloElemTypeSerializerVer14;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.U32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Channel handler deals with the switch connection and dispatches
 *  messages to the higher orders of control.
 * @author Jason Parraga <Jason.Parraga@Bigswitch.com>
 */
class OFChannelHandler extends IdleStateAwareChannelHandler {

	private static final Logger log = LoggerFactory.getLogger(OFChannelHandler.class);

	private final ChannelPipeline pipeline;
	private final INewOFConnectionListener newConnectionListener;
	private final SwitchManagerCounters counters;
	private Channel channel;
	private final Timer timer;
	private volatile OFChannelState state;
	private OFFactory factory;
	private OFFeaturesReply featuresReply;
	private volatile OFConnection connection;
	private final IDebugCounterService debugCounters;
	private final List<U32> ofBitmaps;

	/** transaction Ids to use during handshake. Since only one thread
	 * calls into the OFChannelHandler we don't need atomic.
	 * We will count down
	 */
	private long handshakeTransactionIds = 0x00FFFFFFFFL;


	/**
	 * Default implementation for message handlers in any OFChannelState.
	 *
	 * Individual states must override these if they want a behavior
	 * that differs from the default.
	 */
	public abstract class OFChannelState {

		void processOFHello(OFHello m)
				throws IOException {
			// we only expect hello in the WAIT_HELLO state
			illegalMessageReceived(m);
		}

		void processOFEchoRequest(OFEchoRequest m)
				throws IOException {
			sendEchoReply(m);
		}

		void processOFEchoReply(OFEchoReply m)
				throws IOException {
			// do nothing
		}

		void processOFError(OFErrorMsg m) {
			logErrorDisconnect(m);
		}

		void processOFExperimenter(OFExperimenter m) {
			unhandledMessageReceived(m);
		}

		void processOFFeaturesReply(OFFeaturesReply  m)
				throws IOException {
			// we only expect features reply in the WAIT_FEATURES_REPLY state
			illegalMessageReceived(m);
		}

		void processOFPortStatus(OFPortStatus m) {
			unhandledMessageReceived(m);
		}

		private final boolean channelHandshakeComplete;

		OFChannelState(boolean handshakeComplete) {
			this.channelHandshakeComplete = handshakeComplete;
		}

		void logState() {
			log.debug("{} OFConnection Handshake - enter state {}",
					getConnectionInfoString(), this.getClass().getSimpleName());
		}

		/** enter this state. Can initialize the handler, send
		 *  the necessary messages, etc.
		 * @throws IOException
		 */
		void enterState() throws IOException{
			// Do Nothing
		}

		/**
		 * Get a string specifying the switch connection, state, and
		 * message received. To be used as message for SwitchStateException
		 * or log messages
		 * @param h The channel handler (to get switch information_
		 * @param m The OFMessage that has just been received
		 * @param details A string giving more details about the exact nature
		 * of the problem.
		 * @return
		 */
		// needs to be protected because enum members are acutally subclasses
		protected String getSwitchStateMessage(OFMessage m,
				String details) {
			return String.format("Switch: [%s], State: [%s], received: [%s]"
					+ ", details: %s",
					getConnectionInfoString(),
					this.toString(),
					m.getType().toString(),
					details);
		}

		/**
		 * We have an OFMessage we didn't expect given the current state and
		 * we want to treat this as an error.
		 * We currently throw an exception that will terminate the connection
		 * However, we could be more forgiving
		 * @param h the channel handler that received the message
		 * @param m the message
		 * @throws SwitchStateExeption we always through the execption
		 */
		// needs to be protected because enum members are acutally subclasses
		protected void illegalMessageReceived(OFMessage m) {
			String msg = getSwitchStateMessage(m,
					"Switch should never send this message in the current state");
			throw new SwitchStateException(msg);

		}

		/**
		 * We have an OFMessage we didn't expect given the current state and
		 * we want to ignore the message
		 * @param h the channel handler the received the message
		 * @param m the message
		 */
		protected void unhandledMessageReceived(OFMessage m) {
			counters.unhandledMessage.increment();
			if (log.isDebugEnabled()) {
				String msg = getSwitchStateMessage(m,
						"Ignoring unexpected message");
				log.debug(msg);
			}
		}

		/**
		 * Log an OpenFlow error message from a switch
		 * @param sw The switch that sent the error
		 * @param error The error message
		 */
		@LogMessageDoc(level="ERROR",
				message="Error {error type} {error code} from {switch} " +
						"in state {state}",
						explanation="The switch responded with an unexpected error" +
								"to an OpenFlow message from the controller",
								recommendation="This could indicate improper network operation. " +
										"If the problem persists restarting the switch and " +
										"controller may help."
				)
		protected void logError(OFErrorMsg error) {
			log.error("{} from switch {} in state {}",
					new Object[] {
					error.toString(),
					getConnectionInfoString(),
					this.toString()});
		}

		/**
		 * Log an OpenFlow error message from a switch and disconnect the
		 * channel
		 * @param sw The switch that sent the error
		 * @param error The error message
		 */
		protected void logErrorDisconnect(OFErrorMsg error) {
			logError(error);
			channel.disconnect();
		}

		/**
		 * Process an OF message received on the channel and
		 * update state accordingly.
		 *
		 * The main "event" of the state machine. Process the received message,
		 * send follow up message if required and update state if required.
		 *
		 * Switches on the message type and calls more specific event handlers
		 * for each individual OF message type. If we receive a message that
		 * is supposed to be sent from a controller to a switch we throw
		 * a SwitchStateExeption.
		 *
		 * The more specific handlers can also throw SwitchStateExceptions
		 *
		 * @param h The OFChannelHandler that received the message
		 * @param m The message we received.
		 * @throws SwitchStateException
		 * @throws IOException
		 */
		void processOFMessage(OFMessage m)
				throws IOException {
			// Handle Channel Handshake
			if (!state.channelHandshakeComplete) {
				switch(m.getType()) {
				case HELLO:
					processOFHello((OFHello)m);
					break;
				case ERROR:
					processOFError((OFErrorMsg)m);
					break;
				case FEATURES_REPLY:
					processOFFeaturesReply((OFFeaturesReply)m);
					break;
				case EXPERIMENTER:
					processOFExperimenter((OFExperimenter)m);
					break;
					/* echos can be sent at any time */
				case ECHO_REPLY:
					processOFEchoReply((OFEchoReply)m);
					break;
				case ECHO_REQUEST:
					processOFEchoRequest((OFEchoRequest)m);
					break;
				case PORT_STATUS:
					processOFPortStatus((OFPortStatus)m);
					break;
				default:
					illegalMessageReceived(m);
					break;
				}
			}
			else{
				switch(m.getType()){
				case ECHO_REPLY:
					processOFEchoReply((OFEchoReply)m);
					break;
				case ECHO_REQUEST:
					processOFEchoRequest((OFEchoRequest)m);
					break;
					// Send to SwitchManager and thus higher orders of control
				default:
					sendMessageToConnection(m);
					break;
				}
			}
		}
	}

	/**
	 * Initial state before channel is connected.
	 */
	class InitState extends OFChannelState {

		InitState() {
			super(false);
		}
	}

	/**
	 * We send a HELLO to the switch and wait for a reply.
	 * Once we receive the reply we send an OFFeaturesRequest
	 * Next state is WaitFeaturesReplyState
	 */
	class WaitHelloState extends OFChannelState {

		WaitHelloState() {
			super(false);
		}

		@Override
		void processOFHello(OFHello m) throws IOException {
			OFVersion theirVersion = m.getVersion();
			OFVersion commonVersion = null;
			/* First, check if there's a version bitmap supplied. WE WILL ALWAYS HAVE a controller-provided version bitmap. */
			if (theirVersion.compareTo(OFVersion.OF_13) >= 0 && !m.getElements().isEmpty()) {
				List<U32> bitmaps = new ArrayList<U32>();
				List<OFHelloElem> elements = m.getElements();
				/* Grab all bitmaps supplied */
				for (OFHelloElem e : elements) {
					if (m.getVersion().equals(OFVersion.OF_13) 
							&& e.getType() == OFHelloElemTypeSerializerVer13.VERSIONBITMAP_VAL) {
						bitmaps.addAll(((OFHelloElemVersionbitmap) e).getBitmaps());
					} else if (m.getVersion().equals(OFVersion.OF_14) 
							&& e.getType() == OFHelloElemTypeSerializerVer14.VERSIONBITMAP_VAL) {
						bitmaps.addAll(((OFHelloElemVersionbitmap) e).getBitmaps());
					}
				}
				/* Lookup highest, common supported OpenFlow version */
				commonVersion = computeOFVersionFromBitmap(bitmaps);
				if (commonVersion == null) {
					log.error("Could not negotiate common OpenFlow version for {} with greatest version bitmap algorithm.", channel.getRemoteAddress());
					channel.disconnect();
					return;
				} else {
					log.info("Negotiated OpenFlow version of {} for {} with greatest version bitmap algorithm.", commonVersion.toString(), channel.getRemoteAddress());
					factory = OFFactories.getFactory(commonVersion);
					OFMessageDecoder decoder = pipeline.get(OFMessageDecoder.class);
					decoder.setVersion(commonVersion);
				}
			}
			/* If there's not a bitmap present, choose the lower of the two supported versions. */
			else if (theirVersion.compareTo(factory.getVersion()) < 0) {
				log.info("Negotiated down to switch OpenFlow version of {} for {} using lesser hello header algorithm.", theirVersion.toString(), channel.getRemoteAddress());
				factory = OFFactories.getFactory(theirVersion);
				OFMessageDecoder decoder = pipeline.get(OFMessageDecoder.class);
				decoder.setVersion(theirVersion);
			} /* else The controller's version is < or = the switch's, so keep original controller factory. */
			else if (theirVersion.equals(factory.getVersion())) {
				log.info("Negotiated equal OpenFlow version of {} for {} using lesser hello header algorithm.", factory.getVersion().toString(), channel.getRemoteAddress());
			}
			else {
				log.info("Negotiated down to controller OpenFlow version of {} for {} using lesser hello header algorithm.", factory.getVersion().toString(), channel.getRemoteAddress());
			}

			setState(new WaitFeaturesReplyState());
		}

		@Override
		void enterState() throws IOException {
			sendHelloMessage();
		}
	}

	/**
	 * We are waiting for a features reply message. Once we receive it
	 * we send capture the features reply.
	 * Next state is CompleteState
	 */
	class WaitFeaturesReplyState extends OFChannelState{

		WaitFeaturesReplyState() {
			super(false);
		}
		@Override
		void processOFFeaturesReply(OFFeaturesReply  m)
				throws IOException {
			featuresReply = m;

			// Mark handshake as completed
			setState(new CompleteState());

		}

		@Override
		void processOFHello(OFHello m) throws IOException {
			/*
			 * Brocade switches send a second hello after
			 * the controller responds with its hello. This
			 * might be to confirm the protocol version used,
			 * but isn't defined in the OF specification.
			 * 
			 * We will ignore such hello messages assuming
			 * the version of the hello is correct according
			 * to the algorithm in the spec.
			 * 
			 * TODO Brocade also sets the XID of this second
			 * hello as the same XID the controller used.
			 * Checking for this might help to assure we're
			 * really dealing with the situation we think
			 * we are.
			 */
			if (m.getVersion().equals(factory.getVersion())) {
				log.warn("Ignoring second hello from {} in state {}. Might be a Brocade.", channel.getRemoteAddress(), state.toString());
			} else {
				super.processOFHello(m); /* Versions don't match as they should; abort */
			}
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			log.warn("Ignoring PORT_STATUS message from {} during OpenFlow channel establishment. Ports will be explicitly queried in a later state.", channel.getRemoteAddress());
		}

		@Override
		void enterState() throws IOException {
			sendFeaturesRequest();
		}

		@Override
		void processOFMessage(OFMessage m) throws IOException {
			if (m.getType().equals(OFType.PACKET_IN)) {
				log.warn("Ignoring PACKET_IN message from {} during OpenFlow channel establishment.", channel.getRemoteAddress());
			} else {
				super.processOFMessage(m);
			}
		}
	};

	/**
	 * This state denotes that the channel handshaking is complete.
	 * An OF connection is generated and passed to the switch manager
	 * for handling.
	 */
	class CompleteState extends OFChannelState{

		CompleteState() {
			super(true);
		}

		@Override
		void enterState() throws IOException{

			setSwitchHandshakeTimeout();

			// Handle non 1.3 connections
			if(featuresReply.getVersion().compareTo(OFVersion.OF_13) < 0){
				connection = new OFConnection(featuresReply.getDatapathId(), factory, channel, OFAuxId.MAIN, debugCounters, timer);
			}
			// Handle 1.3 connections
			else{
				connection = new OFConnection(featuresReply.getDatapathId(), factory, channel, featuresReply.getAuxiliaryId(), debugCounters, timer);

				// If this is an aux connection, we set a longer echo idle time
				if (!featuresReply.getAuxiliaryId().equals(OFAuxId.MAIN)) {
					setAuxChannelIdle();
				}
			}
			// Notify the connection broker
			notifyConnectionOpened(connection);

		}
	};

	/**
	 * Creates a handler for interacting with the switch channel
	 *
	 * @param controller
	 *            the controller
	 * @param newConnectionListener
	 *            the class that listens for new OF connections (switchManager)
	 * @param pipeline
	 *            the channel pipeline
	 * @param threadPool
	 *            the thread pool
	 * @param idleTimer
	 *            the hash wheeled timer used to send idle messages (echo).
	 *            passed to constructor to modify in case of aux connection.
	 * @param debugCounters
	 */
	OFChannelHandler(@Nonnull IOFSwitchManager switchManager,
			@Nonnull INewOFConnectionListener newConnectionListener,
			@Nonnull ChannelPipeline pipeline,
			@Nonnull IDebugCounterService debugCounters,
			@Nonnull Timer timer,
			@Nonnull List<U32> ofBitmaps,
			@Nonnull OFFactory defaultFactory) {

		Preconditions.checkNotNull(switchManager, "switchManager");
		Preconditions.checkNotNull(newConnectionListener, "connectionOpenedListener");
		Preconditions.checkNotNull(pipeline, "pipeline");
		Preconditions.checkNotNull(timer, "timer");
		Preconditions.checkNotNull(debugCounters, "debugCounters");

		this.pipeline = pipeline;
		this.debugCounters = debugCounters;
		this.newConnectionListener = newConnectionListener;
		this.counters = switchManager.getCounters();
		this.state = new InitState();
		this.timer = timer;
		this.ofBitmaps = ofBitmaps;
		this.factory = defaultFactory;

		log.debug("constructor on OFChannelHandler {}", String.format("%08x", System.identityHashCode(this)));
	}

	/**
	 * Determine the highest supported version of OpenFlow in common
	 * between both our OFVersion bitmap and the switch's.
	 * 
	 * @param theirs, the version bitmaps of the switch
	 * @return the highest OFVersion in common b/t the two
	 */
	private OFVersion computeOFVersionFromBitmap(List<U32> theirs) {		
		Iterator<U32> theirsItr = theirs.iterator();
		Iterator<U32> oursItr = ofBitmaps.iterator();
		OFVersion version = null;
		int pos = 0;
		int size = 32;
		while (theirsItr.hasNext() && oursItr.hasNext()) {
			int t = theirsItr.next().getRaw();
			int o = oursItr.next().getRaw();

			int common = t & o; /* Narrow down the results to the common bits */
			for (int i = 0; i < size; i++) { /* Iterate over and locate the 1's */
				int tmp = common & (1 << i); /* Select the bit of interest, 0-31 */
				if (tmp != 0) { /* Is the version of this bit in common? */
					for (OFVersion v : OFVersion.values()) { /* Which version does this bit represent? */
						if (v.getWireVersion() == i + (size * pos)) {
							version = v;
						}
					}
				}
			}
			pos++; /* OFVersion position. 1-31 = 1, 32 - 63 = 2, etc. Inc at end so it starts at 0. */
		}
		return version;
	}

	/**
	 * Determines if the entire switch handshake is complete (channel+switch).
	 * If the channel handshake is complete the call is forwarded to the
	 * connection listener/switch manager to be handled by the appropriate
	 * switch handshake handler.
	 *
	 * @return whether or not complete switch handshake is complete
	 */
	public boolean isSwitchHandshakeComplete() {
		if (this.state.channelHandshakeComplete) {
			return connection.getListener().isSwitchHandshakeComplete(connection);
		} else {
			return false;
		}
	}

	/**
	 * Notifies the channel listener that we have a valid baseline connection
	 */
	private final void notifyConnectionOpened(OFConnection connection){
		this.connection = connection;
		this.newConnectionListener.connectionOpened(connection, featuresReply);
	}

	/**
	 * Notifies the channel listener that we our connection has been closed
	 */
	private final void notifyConnectionClosed(OFConnection connection){
		connection.getListener().connectionClosed(connection);
	}

	/**
	 * Notifies the channel listener that we have a valid baseline connection
	 */
	private final void sendMessageToConnection(OFMessage m) {
		connection.messageReceived(m);
	}

	@Override
	@LogMessageDoc(message="New switch connection from {ip address}",
	explanation="A new switch has connected from the " +
			"specified IP address")
	public void channelConnected(ChannelHandlerContext ctx,
			ChannelStateEvent e) throws Exception {
		log.debug("channelConnected on OFChannelHandler {}", String.format("%08x", System.identityHashCode(this)));
		counters.switchConnected.increment();
		channel = e.getChannel();
		log.info("New switch connection from {}",
				channel.getRemoteAddress());
		setState(new WaitHelloState());
	}

	@Override
	@LogMessageDoc(message="Disconnected switch {switch information}",
	explanation="The specified switch has disconnected.")
	public void channelDisconnected(ChannelHandlerContext ctx,
			ChannelStateEvent e) throws Exception {
		// Only handle cleanup connection is even known
		if(this.connection != null){
			// Alert the connection object that the channel has been disconnected
			this.connection.disconnected();
			// Punt the cleanup to the Switch Manager
			notifyConnectionClosed(this.connection);
		}
		log.info("[{}] Disconnected connection", getConnectionInfoString());
	}

	@Override
	@LogMessageDocs({
		@LogMessageDoc(level="ERROR",
				message="Disconnecting switch {switch} due to read timeout",
				explanation="The connected switch has failed to send any " +
						"messages or respond to echo requests",
						recommendation=LogMessageDoc.CHECK_SWITCH),
						@LogMessageDoc(level="ERROR",
						message="Disconnecting switch {switch}: failed to " +
								"complete handshake",
								explanation="The switch did not respond correctly " +
										"to handshake messages",
										recommendation=LogMessageDoc.CHECK_SWITCH),
										@LogMessageDoc(level="ERROR",
										message="Disconnecting switch {switch} due to IO Error: {}",
										explanation="There was an error communicating with the switch",
										recommendation=LogMessageDoc.CHECK_SWITCH),
										@LogMessageDoc(level="ERROR",
										message="Disconnecting switch {switch} due to switch " +
												"state error: {error}",
												explanation="The switch sent an unexpected message",
												recommendation=LogMessageDoc.CHECK_SWITCH),
												@LogMessageDoc(level="ERROR",
												message="Disconnecting switch {switch} due to " +
														"message parse failure",
														explanation="Could not parse a message from the switch",
														recommendation=LogMessageDoc.CHECK_SWITCH),
														@LogMessageDoc(level="ERROR",
														message="Terminating controller due to storage exception",
														explanation=Controller.ERROR_DATABASE,
														recommendation=LogMessageDoc.CHECK_CONTROLLER),
														@LogMessageDoc(level="ERROR",
														message="Could not process message: queue full",
														explanation="OpenFlow messages are arriving faster than " +
																" the controller can process them.",
																recommendation=LogMessageDoc.CHECK_CONTROLLER),
																@LogMessageDoc(level="ERROR",
																message="Error while processing message " +
																		"from switch {switch} {cause}",
																		explanation="An error occurred processing the switch message",
																		recommendation=LogMessageDoc.GENERIC_ACTION)
	})
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		if (e.getCause() instanceof ReadTimeoutException) {

			if (featuresReply.getVersion().compareTo(OFVersion.OF_13) < 0) {
				log.error("Disconnecting switch {} due to read timeout on main cxn.",
						getConnectionInfoString());
				ctx.getChannel().close();
			} else {
				if (featuresReply.getAuxiliaryId().equals(OFAuxId.MAIN)) {
					log.error("Disconnecting switch {} due to read timeout on main cxn.",
							getConnectionInfoString());
					ctx.getChannel().close();
				} else {
					// We only don't disconnect on aux connections
					log.warn("Switch {} encountered read timeout on aux cxn.",
							getConnectionInfoString());
				}
			}
			// Increment counters
			counters.switchDisconnectReadTimeout.increment();

		} else if (e.getCause() instanceof HandshakeTimeoutException) {
			log.error("Disconnecting switch {}: failed to complete handshake. Channel handshake complete : {}",
					getConnectionInfoString(),
					this.state.channelHandshakeComplete);
			counters.switchDisconnectHandshakeTimeout.increment();
			ctx.getChannel().close();
		} else if (e.getCause() instanceof ClosedChannelException) {
			log.debug("Channel for sw {} already closed", getConnectionInfoString());
		} else if (e.getCause() instanceof IOException) {
			log.error("Disconnecting switch {} due to IO Error: {}",
					getConnectionInfoString(), e.getCause().getMessage());
			if (log.isDebugEnabled()) {
				// still print stack trace if debug is enabled
				log.debug("StackTrace for previous Exception: ", e.getCause());
			}
			counters.switchDisconnectIOError.increment();
			ctx.getChannel().close();
		} else if (e.getCause() instanceof SwitchStateException) {
			log.error("Disconnecting switch {} due to switch state error: {}",
					getConnectionInfoString(), e.getCause().getMessage());
			if (log.isDebugEnabled()) {
				// still print stack trace if debug is enabled
				log.debug("StackTrace for previous Exception: ", e.getCause());
			}
			counters.switchDisconnectSwitchStateException.increment();
			ctx.getChannel().close();
		} else if (e.getCause() instanceof OFAuxException) {
			log.error("Disconnecting switch {} due to OF Aux error: {}",
					getConnectionInfoString(), e.getCause().getMessage());
			if (log.isDebugEnabled()) {
				// still print stack trace if debug is enabled
				log.debug("StackTrace for previous Exception: ", e.getCause());
			}
			counters.switchDisconnectSwitchStateException.increment();
			ctx.getChannel().close();
		} else if (e.getCause() instanceof OFParseError) {
			log.error("Disconnecting switch "
					+ getConnectionInfoString() +
					" due to message parse failure",
					e.getCause());
			counters.switchDisconnectParseError.increment();
			ctx.getChannel().close();
		} else if (e.getCause() instanceof RejectedExecutionException) {
			log.warn("Could not process message: queue full");
			counters.rejectedExecutionException.increment();
		} else if (e.getCause() instanceof IllegalArgumentException) {
			log.error("Illegal argument exception with switch {}. {}", getConnectionInfoString(), e.getCause());
			counters.switchSslConfigurationError.increment();
			ctx.getChannel().close();
		} else {
			log.error("Error while processing message from switch "
					+ getConnectionInfoString()
					+ "state " + this.state, e.getCause());
			counters.switchDisconnectOtherException.increment();
			ctx.getChannel().close();
		}
	}

	@Override
	public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
			throws Exception {

		log.debug("channelIdle on OFChannelHandler {}", String.format("%08x", System.identityHashCode(this)));
		OFChannelHandler handler = ctx.getPipeline().get(OFChannelHandler.class);
		handler.sendEchoRequest();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		if (e.getMessage() instanceof List) {
			@SuppressWarnings("unchecked")
			List<OFMessage> msglist = (List<OFMessage>)e.getMessage();
			for (OFMessage ofm : msglist) {
				try {
					// Do the actual packet processing
					state.processOFMessage(ofm);
				}
				catch (Exception ex) {
					// We are the last handler in the stream, so run the
					// exception through the channel again by passing in
					// ctx.getChannel().
					Channels.fireExceptionCaught(ctx.getChannel(), ex);
				}
			}
		}
		else {
			Channels.fireExceptionCaught(ctx.getChannel(),
					new AssertionError("Message received from channel is not a list"));
		}
	}

	/**
	 * Sets the channel pipeline's idle (Echo) timeouts to a longer interval.
	 * This is specifically for aux channels.
	 */
	private void setAuxChannelIdle() {

		IdleStateHandler idleHandler = new IdleStateHandler(
				this.timer,
				PipelineIdleReadTimeout.AUX,
				PipelineIdleWriteTimeout.AUX,
				0);
		pipeline.replace(PipelineHandler.MAIN_IDLE,
				PipelineHandler.AUX_IDLE,
				idleHandler);
	}

	/**
	 * Sets the channel pipeline's handshake timeout to a more appropriate value
	 * for the remaining part of the switch handshake.
	 */
	private void setSwitchHandshakeTimeout() {

		HandshakeTimeoutHandler handler = new HandshakeTimeoutHandler(
				this,
				this.timer,
				PipelineHandshakeTimeout.SWITCH);

		pipeline.replace(PipelineHandler.CHANNEL_HANDSHAKE_TIMEOUT,
				PipelineHandler.SWITCH_HANDSHAKE_TIMEOUT, handler);
	}

	/**
	 * Return a string describing this switch based on the already available
	 * information (DPID and/or remote socket)
	 * @return
	 */
	private String getConnectionInfoString() {

		String channelString;
		if (channel == null || channel.getRemoteAddress() == null) {
			channelString = "?";
		} else {
			channelString = channel.getRemoteAddress().toString();
			if(channelString.startsWith("/"))
				channelString = channelString.substring(1);
		}
		String dpidString;
		if (featuresReply == null) {
			dpidString = "?";
		} else {
			StringBuilder b = new StringBuilder();
			b.append(featuresReply.getDatapathId());
			if(featuresReply.getVersion().compareTo(OFVersion.OF_13) >= 0) {
				b.append("(").append(featuresReply.getAuxiliaryId()).append(")");
			}
			dpidString = b.toString();
		}
		return String.format("[%s from %s]", dpidString, channelString );
	}

	/**
	 * Update the channels state. Only called from the state machine.
	 * @param state
	 * @throws IOException
	 */
	private void setState(OFChannelState state) throws IOException {
		this.state = state;
		state.logState();
		state.enterState();
	}

	/**
	 * Send a features request message to the switch using the handshake
	 * transactions ids.
	 * @throws IOException
	 */
	private void sendFeaturesRequest() throws IOException {
		// Send initial Features Request
		OFFeaturesRequest m = factory.buildFeaturesRequest()
				.setXid(handshakeTransactionIds--)
				.build();
		channel.write(Collections.singletonList(m));
	}

	/**
	 * Send a hello message to the switch using the handshake transactions ids.
	 * @throws IOException
	 */
	private void sendHelloMessage() throws IOException {
		// Send initial hello message

		OFHello.Builder builder = factory.buildHello();

		/* Our highest-configured OFVersion does support version bitmaps, so include it */
		if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
			List<OFHelloElem> he = new ArrayList<OFHelloElem>();
			he.add(factory.buildHelloElemVersionbitmap()
					.setBitmaps(ofBitmaps)
					.build());
			builder.setElements(he);
		}

		OFHello m = builder.setXid(handshakeTransactionIds--)
				.build();
		
		channel.write(Collections.singletonList(m));
		log.debug("Send hello: {}", m);
	}

	private void sendEchoRequest() {
		OFEchoRequest request = factory.buildEchoRequest()
				.setXid(handshakeTransactionIds--)
				.build();
		channel.write(Collections.singletonList(request));
	}

	private void sendEchoReply(OFEchoRequest request) {
		OFEchoReply reply = factory.buildEchoReply()
				.setXid(request.getXid())
				.setData(request.getData())
				.build();
		channel.write(Collections.singletonList(reply));
	}

	OFChannelState getStateForTesting() {
		return state;
	}

	IOFConnectionBackend getConnectionForTesting() {
		return connection;
	}

	ChannelPipeline getPipelineForTesting() {
		return this.pipeline;
	}

}
