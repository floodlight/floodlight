package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.hamcrest.CoreMatchers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.OFConnectionCounters;
import net.floodlightcontroller.core.internal.OpenflowPipelineFactory.PipelineHandler;
import net.floodlightcontroller.core.internal.OpenflowPipelineFactory.PipelineHandshakeTimeout;
import net.floodlightcontroller.debugcounter.DebugCounterServiceImpl;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRole;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleReply;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortReason;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.collect.ImmutableList;


public class OFChannelHandlerVer10Test {
    private IOFSwitchManager switchManager;
    private IOFConnectionListener connectionListener;
    private IDebugCounterService debugCounterService;
    private OFChannelHandler handler;
    private Channel channel;
    private Timer timer;
    private ChannelHandlerContext ctx;
    private MessageEvent messageEvent;
    private ChannelStateEvent channelStateEvent;
    private ChannelPipeline pipeline;
    // FIXME:LOJI: Currently only use OF 1.0
    private final OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);

    private Capture<ExceptionEvent> exceptionEventCapture;
    private Capture<List<OFMessage>> writeCapture;

    private OFPortDesc portDesc;
    private OFFeaturesReply featuresReply;

    private Set<Long> seenXids = null;
    private INewOFConnectionListener newConnectionListener;
    private Capture<IOFConnectionBackend> newConnection;
    private Capture<OFFeaturesReply> newFeaturesReply;

    public void setUpFeaturesReply() {
       portDesc = factory.buildPortDesc()
                .setName("Eth1")
                .setPortNo(OFPort.of(1))
                .build();
        featuresReply = factory.buildFeaturesReply()
                .setDatapathId(DatapathId.of(0x42L))
                .setNBuffers(1)
                .setNTables((short)1)
                .setCapabilities(EnumSet.<OFCapabilities>of(OFCapabilities.FLOW_STATS, OFCapabilities.TABLE_STATS))
                .setActions(EnumSet.<OFActionType>of(OFActionType.SET_VLAN_PCP))
                .setPorts(ImmutableList.<OFPortDesc>of(portDesc))
                .build();
    }


    @Before
    public void setUp() throws Exception {
    	setUpFeaturesReply();
        switchManager = createMock(IOFSwitchManager.class);
        connectionListener = createMock(IOFConnectionListener.class);
        newConnectionListener = createMock(INewOFConnectionListener.class);
        newConnection = new Capture<IOFConnectionBackend>();
        newFeaturesReply = new Capture<OFFeaturesReply>();

        ctx = createMock(ChannelHandlerContext.class);
        channelStateEvent = createMock(ChannelStateEvent.class);
        channel = createMock(Channel.class);
        timer = new HashedWheelTimer();
        messageEvent = createMock(MessageEvent.class);
        exceptionEventCapture = new Capture<ExceptionEvent>(CaptureType.ALL);
        pipeline = createMock(ChannelPipeline.class);
        writeCapture = new Capture<List<OFMessage>>(CaptureType.ALL);
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
                                       pipeline, debugCounterService,
                                       timer);
        verify(switchManager);
        reset(switchManager);

        resetChannel();

        // replay controller. Reset it if you need more specific behavior
        replay(switchManager);

        // Mock ctx and channelStateEvent
        expect(ctx.getChannel()).andReturn(channel).anyTimes();
        expect(channelStateEvent.getChannel()).andReturn(channel).anyTimes();
        replay(ctx, channelStateEvent);

        /* Setup an exception event capture on the channel. Right now
         * we only expect exception events to be send up the channel.
         * However, it's easy to extend to other events if we need it
         */
        pipeline.sendUpstream(capture(exceptionEventCapture));
        expectLastCall().anyTimes();
        expect(pipeline.get(OFMessageDecoder.class)).andReturn(new OFMessageDecoder()).anyTimes();
        replay(pipeline);
    }

    @After
    public void tearDown() {
        /* ensure no exception was thrown */
        if (exceptionEventCapture.hasCaptured()) {
            Throwable ex = exceptionEventCapture.getValue().getCause();
            ex.printStackTrace();
            throw new AssertionError("Unexpected exception: " +
                       ex.getClass().getName() + "(" + ex + ")");
        }
        assertFalse("Unexpected messages have been captured",
                    writeCapture.hasCaptured());
        // verify all mocks.
        verify(channel);
        verify(messageEvent);
        verify(switchManager);
        verify(ctx);
        verify(channelStateEvent);
        verify(pipeline);
    }

    /** Reset the channel mock and set basic method call expectations */
    void resetChannel() {
        reset(channel);
        expect(channel.getPipeline()).andReturn(pipeline).anyTimes();
        expect(channel.getRemoteAddress()).andReturn(InetSocketAddress.createUnresolved("1.1.1.1", 80)).anyTimes();
    }


    /** reset, setup, and replay the messageEvent mock for the given
     * messages
     */
    void setupMessageEvent(List<OFMessage> messages) {
        reset(messageEvent);
        expect(messageEvent.getMessage()).andReturn(messages).atLeastOnce();
        replay(messageEvent);
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
        setupMessageEvent(messages);

        handler.messageReceived(ctx, messageEvent);
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
    void verifyExceptionCaptured(
            Class<? extends Throwable> expectedExceptionClass) {
        assertTrue("Excpected exception not thrown",
                   exceptionEventCapture.hasCaptured());
        Throwable caughtEx = exceptionEventCapture.getValue().getCause();
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
    public void testInitState() throws Exception {
        // Message event needs to be list
        expect(messageEvent.getMessage()).andReturn(null);
        replay(channel, messageEvent);
        handler.messageReceived(ctx, messageEvent);
        verify(channel, messageEvent);
        verifyExceptionCaptured(AssertionError.class);

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
        channel.write(capture(writeCapture));
        expectLastCall().andReturn(null).once();
        replay(channel);
        // replay unused mocks
        replay(messageEvent);

        handler.channelConnected(ctx, channelStateEvent);

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
        channel.write(capture(writeCapture));
        expectLastCall().andReturn(null).atLeastOnce();
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

        reset(newConnectionListener);
        newConnectionListener.connectionOpened(capture(newConnection), capture(newFeaturesReply));
        expectLastCall().once();
        replay(newConnectionListener);

        sendMessageToHandlerWithControllerReset(Collections.<OFMessage>singletonList(featuresReply));

        assertThat(handler.getStateForTesting(), CoreMatchers.instanceOf(OFChannelHandler.CompleteState.class));
        assertTrue("A connection has been created and set", handler.getConnectionForTesting() != null);


    }

    /**
     * Test dispatch of messages while in Complete state
     */
    @Test
    public void testMessageDispatchComplete() throws Exception {
        moveToComplete();
        newConnection.getValue().setListener(connectionListener);

        resetChannel();
        channel.write(capture(writeCapture));
        expectLastCall().andReturn(null).atLeastOnce();
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
        OFNiciraControllerRoleReply roleReply = factory.buildNiciraControllerRoleReply()
                .setRole(OFNiciraControllerRole.ROLE_MASTER)
                .build();

        resetAndExpectConnectionListener(roleReply);

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
