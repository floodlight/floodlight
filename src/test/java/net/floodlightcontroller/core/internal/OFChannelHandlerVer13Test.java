package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.hamcrest.CoreMatchers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.internal.OFChannelInitializer.PipelineHandler;
import net.floodlightcontroller.core.internal.OFChannelInitializer.PipelineHandshakeTimeout;
import net.floodlightcontroller.core.test.TestEventLoop;
import net.floodlightcontroller.debugcounter.DebugCounterServiceImpl;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBsnSetAuxCxnsReply;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowRemovedReason;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortReason;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U32;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;


public class OFChannelHandlerVer13Test {
	private static final DatapathId dpid = DatapathId.of(0x42L);

	private IOFSwitchManager switchManager;
	private IOFConnectionListener connectionListener;
	private INewOFConnectionListener newConnectionListener;
	private IDebugCounterService debugCounterService;
	private OFChannelHandler handler;
	private Channel channel;
	private Timer timer;
	private ChannelHandlerContext ctx;
	private ChannelPipeline pipeline;
	private final OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

	private Capture<Throwable> exceptionEventCapture;
	private Capture<List<OFMessage>> writeCapture;

	private OFFeaturesReply featuresReply;
	private OFPortDesc portDesc;

	private Set<Long> seenXids = null;

	private Capture<IOFConnectionBackend> newConnection;

	private Capture<OFFeaturesReply> newFeaturesReply;

	private TestEventLoop eventLoop;

	public void setUpFeaturesReply() {
		portDesc = factory.buildPortDesc()
				.setName("Eth1")
				.setPortNo(OFPort.of(1))
				.build();
		featuresReply = factory.buildFeaturesReply()
				.setDatapathId(dpid)
				.setNBuffers(1)
				.setNTables((short)1)
				.setCapabilities(EnumSet.<OFCapabilities>of(OFCapabilities.FLOW_STATS, OFCapabilities.TABLE_STATS))
				.setAuxiliaryId(OFAuxId.MAIN)
				.build();
	}

	@Before
	public void setUp() throws Exception {
		setUpFeaturesReply();
		switchManager = createMock(IOFSwitchManager.class);
		connectionListener = createMock(IOFConnectionListener.class);
		newConnectionListener = createMock(INewOFConnectionListener.class);
		newConnection = EasyMock.newCapture();
		newFeaturesReply = EasyMock.newCapture();
        eventLoop = new TestEventLoop();

		ctx = createMock(ChannelHandlerContext.class);
		channel = createMock(Channel.class);
		timer = new HashedWheelTimer();
		exceptionEventCapture = EasyMock.newCapture(CaptureType.ALL);
		pipeline = createMock(ChannelPipeline.class);
		writeCapture = EasyMock.newCapture(CaptureType.ALL);
		seenXids = null;


		// TODO: should mock IDebugCounterService and make sure
		// the expected counters are updated.
		debugCounterService = new DebugCounterServiceImpl();
		debugCounterService.registerModule(OFConnectionCounters.COUNTER_MODULE);
		SwitchManagerCounters counters =
				new SwitchManagerCounters(debugCounterService);
		expect(switchManager.getCounters()).andReturn(counters).anyTimes();
		replay(switchManager);
		handler = new OFChannelHandler(switchManager, newConnectionListener,
				pipeline, debugCounterService, /* 62 is OF versions 1.0 thru 1.4 in decimal */
				timer, Collections.singletonList(U32.of(62)), OFFactories.getFactory(OFVersion.OF_14));

		verify(switchManager);
		reset(switchManager);

		resetChannel();

		// replay controller. Reset it if you need more specific behavior
		replay(switchManager);

		// Mock ctx and channelStateEvent
		expect(ctx.channel()).andReturn(channel).anyTimes();
		expect(ctx.fireExceptionCaught(capture(exceptionEventCapture))).andReturn(ctx).anyTimes();
		replay(ctx);

		/* Setup an exception event capture on the channel. Right now
		 * we only expect exception events to be send up the channel.
		 * However, it's easy to extend to other events if we need it
		 */
		expect(pipeline.get(OFMessageDecoder.class)).andReturn(new OFMessageDecoder()).anyTimes();
		replay(pipeline);
	}

	@After
	public void tearDown() {
		/* ensure no exception was thrown */
		if (exceptionEventCapture.hasCaptured()) {
			Throwable ex = exceptionEventCapture.getValue();
			ex.printStackTrace();
			Throwables.propagate(ex);
		}
		assertFalse("Unexpected messages have been captured",
				writeCapture.hasCaptured());
		// verify all mocks.
		verify(channel);
		verify(switchManager);
		verify(ctx);
		verify(pipeline);
	}

	/** Reset the channel mock and set basic method call expectations */
	void resetChannel() {
		reset(channel);
		expect(channel.newPromise()).andAnswer(new IAnswer<ChannelPromise>() {
			@Override
			public ChannelPromise answer() throws Throwable {
				return new DefaultChannelPromise(channel);
			}
		}).anyTimes();
		eventLoop = new TestEventLoop();
		expect(channel.eventLoop()).andReturn(eventLoop).anyTimes();
		expect(channel.pipeline()).andReturn(pipeline).anyTimes();
		expect(channel.remoteAddress()).andReturn(null).anyTimes();
	}

	/** reset, setup, and replay the messageEvent mock for the given
	 * messages, mock controller  send message to channel handler
	 *
	 * This method will reset, start replay on controller, and then verify
	 */
	void sendMessageToHandlerWithControllerReset(List<OFMessage> messages)
			throws Exception {
		sendMessageToHandlerNoControllerReset(messages);
	}

	/** reset, setup, and replay the messageEvent mock for the given
	 * messages, mock controller  send message to channel handler
	 *
	 * This method will start replay on controller, and then verify
	 */
	void sendMessageToHandlerNoControllerReset(List<OFMessage> messages)
			throws Exception {
		handler.channelRead(ctx, messages);
	}

	/**
	 * Extract the list of OFMessages that was captured by the Channel.write()
	 * capture. Will check that something was actually captured first. We'll
	 * collapse the messages from multiple writes into a single list of
	 * OFMessages.
	 * Resets the channelWriteCapture.
	 */
	List<OFMessage> getMessagesFromCapture() {
		List<OFMessage> msgs = new ArrayList<OFMessage>();

		assertTrue("No write on channel was captured",
				writeCapture.hasCaptured());
		List<List<OFMessage>> capturedVals = writeCapture.getValues();

		for (List<OFMessage> oneWriteList: capturedVals)
			msgs.addAll(oneWriteList);
		writeCapture.reset();
		return msgs;
	}


	/**
	 * Verify that the given exception event capture (as returned by
	 * getAndInitExceptionCapture) has thrown an exception of the given
	 * expectedExceptionClass.
	 * Resets the capture
	 */
	void verifyExceptionCaptured(Class<? extends Throwable> expectedExceptionClass) {
		assertTrue("Excpected exception not thrown", exceptionEventCapture.hasCaptured());
		Throwable caughtEx = exceptionEventCapture.getValue();
		assertEquals(expectedExceptionClass, caughtEx.getClass());
		exceptionEventCapture.reset();
	}

	/** make sure that the transaction ids in the given messages are
	 * not 0 and differ between each other.
	 * While it's not a defect per se if the xids are we want to ensure
	 * we use different ones for each message we send.
	 */
	void verifyUniqueXids(List<OFMessage> msgs) {
		if (seenXids == null)
			seenXids = new HashSet<Long>();
		for (OFMessage m: msgs)  {
			long xid = m.getXid();
			assertTrue("Xid in messags is 0", xid != 0);
			assertFalse("Xid " + xid + " has already been used",
					seenXids.contains(xid));
			seenXids.add(xid);
		}
	}

	@Test
	public void testNullMsg() throws Exception {
		reset(ctx);
		expect(ctx.fireChannelRead(null)).andReturn(ctx).once();
		replay(ctx, channel);

		// null message is not passed to the handler
		handler.channelRead(ctx, null);
		verify(channel, ctx);
	}

	@Test
	public void testInitState() throws Exception {
		replay(channel);

		// We don't expect to receive /any/ messages in init state since
		// channelConnected moves us to a different state
		OFMessage m = factory.buildHello().build();
		sendMessageToHandlerWithControllerReset(ImmutableList.<OFMessage>of(m));

		verifyExceptionCaptured(SwitchStateException.class);
		assertThat(handler.getStateForTesting(), CoreMatchers.instanceOf(OFChannelHandler.InitState.class));
	}

	/* Move the channel from scratch to WAIT_HELLO state */
	@Test
	public void moveToWaitHello() throws Exception {
		resetChannel();
		expect(channel.writeAndFlush(capture(writeCapture))).andReturn(null).once();
		replay(channel);

		handler.channelActive(ctx);
		eventLoop.runTasks();

		List<OFMessage> msgs = getMessagesFromCapture();
		assertEquals(1, msgs.size());
		assertEquals(OFType.HELLO, msgs.get(0).getType());
		assertThat(handler.getStateForTesting(), CoreMatchers.instanceOf(OFChannelHandler.WaitHelloState.class));
		verifyUniqueXids(msgs);
	}

	/** Move the channel from scratch to WAIT_FEATURES_REPLY state
	 * Builds on moveToWaitHello()
	 * adds testing for WAIT_HELLO state
	 */
	@Test
	public void moveToWaitFeaturesReply() throws Exception {
		moveToWaitHello();
		resetChannel();
		expect(channel.writeAndFlush(capture(writeCapture))).andReturn(null).once();
		replay(channel);

		OFMessage hello = factory.buildHello().build();
		sendMessageToHandlerWithControllerReset(ImmutableList.<OFMessage>of(hello));

		List<OFMessage> msgs = getMessagesFromCapture();
		assertEquals(1, msgs.size());
		assertEquals(OFType.FEATURES_REQUEST, msgs.get(0).getType());
		verifyUniqueXids(msgs);

		assertThat(handler.getStateForTesting(), CoreMatchers.instanceOf(OFChannelHandler.WaitFeaturesReplyState.class));
	}


	/** Move the channel from scratch to WAIT_FEATURES_REPLY state
	 * Builds on moveToWaitHello()
	 * adds testing for WAIT_HELLO state
	 */
	@Test
	public void moveToComplete() throws Exception {
		moveToWaitFeaturesReply();

		reset(pipeline);
		HandshakeTimeoutHandler newHandler = new HandshakeTimeoutHandler(
				handler,
				timer,
				PipelineHandshakeTimeout.SWITCH);

		expect(
				pipeline.replace(EasyMock.eq(PipelineHandler.CHANNEL_HANDSHAKE_TIMEOUT),
						EasyMock.eq(PipelineHandler.SWITCH_HANDSHAKE_TIMEOUT),
						EasyMock.anyObject(HandshakeTimeoutHandler.class))).andReturn(newHandler)
						.once();

		replay(pipeline);

		newConnectionListener.connectionOpened(capture(newConnection), capture(newFeaturesReply));
		expectLastCall().once();
		replay(newConnectionListener);

		sendMessageToHandlerWithControllerReset(Collections.<OFMessage>singletonList(featuresReply));

		assertThat(handler.getStateForTesting(), CoreMatchers.instanceOf(OFChannelHandler.CompleteState.class));
		assertTrue("A connection has been created and set", handler.getConnectionForTesting() != null);
		verify(newConnectionListener);
		assertTrue(newConnection.hasCaptured());
		assertThat(newFeaturesReply.getValue(), equalTo(featuresReply));
	}

	/**
	 * Test dispatch of messages while in Complete state
	 */
	@Test
	public void testMessageDispatchComplete() throws Exception {
		moveToComplete();
		newConnection.getValue().setListener(connectionListener);

		resetChannel();
		expect(channel.writeAndFlush(capture(writeCapture))).andReturn(null).once();
		replay(channel);

		// Send echo request. expect reply
		OFMessage echoRequest = factory.buildEchoRequest().build();
		sendMessageToHandlerWithControllerReset(ImmutableList.<OFMessage>of(echoRequest));

		List<OFMessage> msgs = getMessagesFromCapture();
		assertEquals(1, msgs.size());
		assertEquals(OFType.ECHO_REPLY, msgs.get(0).getType());


		// Send barrier reply. expect dispatch
		OFBarrierReply barrierReply = factory.buildBarrierReply()
				.build();

		resetAndExpectConnectionListener(barrierReply);


		// Send packet in. expect dispatch
		OFFlowRemoved flowRemoved = factory.buildFlowRemoved()
				.setReason(OFFlowRemovedReason.DELETE)
				.build();

		resetAndExpectConnectionListener(flowRemoved);

		// Send get config reply. expect dispatch
		OFGetConfigReply getConfigReply = factory.buildGetConfigReply()
				.build();

		resetAndExpectConnectionListener(getConfigReply);

		// Send packet in. expect dispatch
		OFPacketIn pi = factory.buildPacketIn()
				.setReason(OFPacketInReason.NO_MATCH)
				.build();

		resetAndExpectConnectionListener(pi);

		// Send port status. expect dispatch
		OFPortStatus portStatus = factory.buildPortStatus()
				.setReason(OFPortReason.DELETE)
				.setDesc(portDesc)
				.build();

		resetAndExpectConnectionListener(portStatus);

		// Send queue reply. expect dispatch
		OFQueueGetConfigReply queueReply = factory.buildQueueGetConfigReply()
				.build();

		resetAndExpectConnectionListener(queueReply);

		// Send stat reply. expect dispatch
		OFFlowStatsReply statReply = factory.buildFlowStatsReply()
				.build();

		resetAndExpectConnectionListener(statReply);

		// Send role reply. expect dispatch
		OFRoleReply roleReply = factory.buildRoleReply()
				.setRole(OFControllerRole.ROLE_MASTER)
				.build();

		resetAndExpectConnectionListener(roleReply);

		// Send experimenter. expect dispatch
		OFBsnSetAuxCxnsReply auxReply = factory.buildBsnSetAuxCxnsReply()
				.build();

		resetAndExpectConnectionListener(auxReply);

	}

	public void resetAndExpectConnectionListener(OFMessage m) throws Exception{
		reset(connectionListener);
		connectionListener.messageReceived(handler.getConnectionForTesting(), m);
		expectLastCall().once();
		replay(connectionListener);

		sendMessageToHandlerWithControllerReset(Collections.<OFMessage>singletonList(m));

		verify(connectionListener);
	}
}
