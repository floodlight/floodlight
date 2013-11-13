package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch.PortChangeEvent;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.debugcounter.DebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterException;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OrderedCollection;
import net.floodlightcontroller.util.LinkedHashSetWrapper;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFGetConfigReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFSetConfig;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;

import static org.easymock.EasyMock.*;

import static org.junit.Assert.*;


public class OFChannelHandlerTest {
    private static final short CORE_PRIORITY = 4242;
    private static final short ACCESS_PRIORITY = 42;
    private Controller controller;
    private IThreadPoolService threadPool;
    private IDebugCounterService debugCounterService;
    private OFChannelHandler handler;
    private Channel channel;
    private ChannelHandlerContext ctx;
    private MessageEvent messageEvent;
    private ChannelStateEvent channelStateEvent;
    private ChannelPipeline pipeline;


    private Capture<ExceptionEvent> exceptionEventCapture;
    private Capture<List<OFMessage>> writeCapture;

    private OFFeaturesReply featuresReply;

    private Set<Integer> seenXids = null;
    private IStorageSourceService storageSource;
    private IResultSet storageResultSet;
    private IOFSwitch sw;



    @Before
    public void setUpFeaturesReply() {
        featuresReply = (OFFeaturesReply)BasicFactory.getInstance()
                .getMessage(OFType.FEATURES_REPLY);
        featuresReply.setDatapathId(0x42L);
        featuresReply.setBuffers(1);
        featuresReply.setTables((byte)1);
        featuresReply.setCapabilities(3);
        featuresReply.setActions(4);
        List<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        // A dummy port.
        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Eth1");
        p.setPortNumber((short)1);
        ports.add(p);
        featuresReply.setPorts(ports);
    }


    @Before
    public void setUp() throws Exception {
        controller = createMock(Controller.class);
        threadPool = createMock(IThreadPoolService.class);
        ctx = createMock(ChannelHandlerContext.class);
        channelStateEvent = createMock(ChannelStateEvent.class);
        channel = createMock(Channel.class);
        messageEvent = createMock(MessageEvent.class);
        exceptionEventCapture = new Capture<ExceptionEvent>(CaptureType.ALL);
        pipeline = createMock(ChannelPipeline.class);
        writeCapture = new Capture<List<OFMessage>>(CaptureType.ALL);
        sw = createMock(IOFSwitch.class);
        seenXids = null;

        // TODO: should mock IDebugCounterService and make sure
        // the expected counters are updated.
        debugCounterService = new DebugCounter();
        Controller.Counters counters =
                new Controller.Counters();
        counters.createCounters(debugCounterService);
        expect(controller.getCounters()).andReturn(counters).anyTimes();
        replay(controller);
        handler = new OFChannelHandler(controller);
        verify(controller);
        reset(controller);

        resetChannel();

        // thread pool is usually not called, so start empty replay
        replay(threadPool);

        // replay controller. Reset it if you need more specific behavior
        replay(controller);

        // replay switch. Reset it if you need more specific behavior
        replay(sw);

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
        replay(pipeline);
    }

    @After
    public void tearDown() {
        /* ensure no exception was thrown */
        if (exceptionEventCapture.hasCaptured()) {
            Throwable ex = exceptionEventCapture.getValue().getCause();
            throw new AssertionError("Unexpected exception: " +
                       ex.getClass().getName() + "(" + ex + ")");
        }
        assertFalse("Unexpected messages have been captured",
                    writeCapture.hasCaptured());
        // verify all mocks.
        verify(channel);
        verify(messageEvent);
        verify(controller);
        verify(threadPool);
        verify(ctx);
        verify(channelStateEvent);
        verify(pipeline);
        verify(sw);
    }

    /** Reset the channel mock and set basic method call expectations */
    void resetChannel() {
        reset(channel);
        expect(channel.getPipeline()).andReturn(pipeline).anyTimes();
        expect(channel.getRemoteAddress()).andReturn(null).anyTimes();
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
        verify(controller);
        reset(controller);

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

        // mock controller
        controller.flushAll();
        expectLastCall().atLeastOnce();
        replay(controller);
        handler.messageReceived(ctx, messageEvent);
        verify(controller);
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
            seenXids = new HashSet<Integer>();
        for (OFMessage m: msgs)  {
            int xid = m.getXid();
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

        // Message event needs to be list *of OFMessages*
        // TODO: messageReceived can throw exceptions that don't get send
        // back into the channel (e.g., the ClassCastException below).
        // Do we need to care?
        /*
        reset(channel, messageEvent);
        List<String> listOfWrongType = Collections.singletonList("FooBar");
        expect(messageEvent.getMessage()).andReturn(listOfWrongType)
                .atLeastOnce();
        replay(channel, messageEvent);
        handler.messageReceived(ctx, messageEvent);
        verify(channel, messageEvent);
        verifyExceptionCaptured(ClassCastException.class);
        */

        // We don't expect to receive /any/ messages in init state since
        // channelConnected moves us to a different state
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.HELLO);
        sendMessageToHandlerWithControllerReset(Collections.singletonList(m));

        verifyExceptionCaptured(SwitchStateException.class);
        assertEquals(OFChannelHandler.ChannelState.INIT,
                     handler.getStateForTesting());
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
        assertEquals(OFChannelHandler.ChannelState.WAIT_HELLO,
                     handler.getStateForTesting());
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

        OFMessage hello = BasicFactory.getInstance().getMessage(OFType.HELLO);
        sendMessageToHandlerWithControllerReset(Collections.singletonList(hello));

        List<OFMessage> msgs = getMessagesFromCapture();
        assertEquals(1, msgs.size());
        assertEquals(OFType.FEATURES_REQUEST, msgs.get(0).getType());
        verifyUniqueXids(msgs);

        assertEquals(OFChannelHandler.ChannelState.WAIT_FEATURES_REPLY,
                     handler.getStateForTesting());
    }


    /** Move the channel from scratch to WAIT_CONFIG_REPLY state
     * Builds on moveToWaitFeaturesReply
     * adds testing for WAIT_FEATURES_REPLY state
     */
    @Test
    public void moveToWaitConfigReply() throws Exception {
        moveToWaitFeaturesReply();
        resetChannel();
        channel.write(capture(writeCapture));
        expectLastCall().andReturn(null).atLeastOnce();
        replay(channel);

        sendMessageToHandlerWithControllerReset(Collections.<OFMessage>singletonList(featuresReply));

        List<OFMessage> msgs = getMessagesFromCapture();
        assertEquals(3, msgs.size());
        assertEquals(OFType.SET_CONFIG, msgs.get(0).getType());
        OFSetConfig sc = (OFSetConfig)msgs.get(0);
        assertEquals((short)0xffff, sc.getMissSendLength());
        assertEquals(OFType.BARRIER_REQUEST, msgs.get(1).getType());
        assertEquals(OFType.GET_CONFIG_REQUEST, msgs.get(2).getType());
        verifyUniqueXids(msgs);
        assertEquals(OFChannelHandler.ChannelState.WAIT_CONFIG_REPLY,
                     handler.getStateForTesting());
    }

    /** Move the channel from scratch to WAIT_DESCRIPTION_STAT_REPLY state
     * Builds on moveToWaitConfigReply()
     * adds testing for WAIT_CONFIG_REPLY state
     */
    @Test
    public void moveToWaitDescriptionStatReply() throws Exception {
        moveToWaitConfigReply();
        resetChannel();
        channel.write(capture(writeCapture));
        expectLastCall().andReturn(null).atLeastOnce();
        replay(channel);

        OFGetConfigReply cr = (OFGetConfigReply)BasicFactory.getInstance()
                .getMessage(OFType.GET_CONFIG_REPLY);
        cr.setMissSendLength((short)0xffff);

        sendMessageToHandlerWithControllerReset(Collections.<OFMessage>singletonList(cr));

        List<OFMessage> msgs = getMessagesFromCapture();
        assertEquals(1, msgs.size());
        assertEquals(OFType.STATS_REQUEST, msgs.get(0).getType());
        OFStatisticsRequest sr = (OFStatisticsRequest)msgs.get(0);
        assertEquals(OFStatisticsType.DESC, sr.getStatisticType());
        // no idea why an  OFStatisticsRequest even /has/ a getStatistics()
        // methods. It really shouldn't
        assertNull(sr.getStatistics());
        verifyUniqueXids(msgs);
        assertEquals(OFChannelHandler.ChannelState.WAIT_DESCRIPTION_STAT_REPLY,
                     handler.getStateForTesting());
    }


    /** A helper bean that represents the config for a particular switch in
     * the storage source.
     */
    private class MockStorageSourceConfig {
        // the dpid
        public String dpid;
        // true if the given dpid should be present in the storage source
        // if false the storage source will return an empty result
        public boolean isPresent;
        // the value of isCoreSwitch
        public boolean isCoreSwitch;
    }
    /** setup and replay a mock storage source and result set that
     * contains the IsCoreSwitch setting
     */
    private void setupMockStorageSource(MockStorageSourceConfig cfg) {

        storageSource = createMock(IStorageSourceService.class);
        storageResultSet = createMock(IResultSet.class);

        Iterator<IResultSet> it = null;

        if (cfg.isPresent) {
            storageResultSet.getBoolean(Controller.SWITCH_CONFIG_CORE_SWITCH);
            expectLastCall().andReturn(cfg.isCoreSwitch).atLeastOnce();
            it = Collections.singletonList(storageResultSet).iterator();
        } else {
            it = Collections.<IResultSet>emptyList().iterator();
        }

        storageResultSet.close();
        expectLastCall().atLeastOnce();
        expect(storageResultSet.iterator()).andReturn(it).atLeastOnce();
        storageSource.getRow(Controller.SWITCH_CONFIG_TABLE_NAME, cfg.dpid);
        expectLastCall().andReturn(storageResultSet).atLeastOnce();
        replay(storageResultSet, storageSource);
    }

    private void verifyStorageSource() {
        verify(storageSource);
        verify(storageResultSet);
    }

    private static OFStatisticsReply createDescriptionStatsReply() {
        OFStatisticsReply sr = (OFStatisticsReply)BasicFactory.getInstance()
                .getMessage(OFType.STATS_REPLY);
        sr.setStatisticType(OFStatisticsType.DESC);
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        desc.setDatapathDescription("Datapath Description");
        desc.setHardwareDescription("Hardware Description");
        desc.setManufacturerDescription("Manufacturer Description");
        desc.setSerialNumber("Serial Number");
        desc.setSoftwareDescription("Software Description");
        sr.setStatistics(Collections.singletonList(desc));
        return sr;
    }

    /**
     * setup the expectations for the mock switch that are needed
     * after the switch is instantiated in the WAIT_DESCRIPTION_STATS STATE
     * Will reset the switch
     * @throws CounterException
     */
    private void setupSwitchForInstantiationWithReset(String dpid)
            throws Exception {
        reset(sw);
        sw.setChannel(channel);
        expectLastCall().once();
        sw.setFloodlightProvider(controller);
        expectLastCall().once();
        sw.setThreadPoolService(threadPool);
        expectLastCall().once();
        sw.setDebugCounterService(debugCounterService);
        expectLastCall().once();
        sw.setFeaturesReply(featuresReply);
        expectLastCall().once();
        sw.setConnected(true);
        expectLastCall().once();
        sw.getStringId();
        expectLastCall().andReturn(dpid).atLeastOnce();
        sw.isWriteThrottleEnabled();  // used for log message only
        expectLastCall().andReturn(false).anyTimes();
        sw.setAccessFlowPriority(ACCESS_PRIORITY);
        expectLastCall().once();
        sw.setCoreFlowPriority(CORE_PRIORITY);
        expectLastCall().once();
    }

    /** Move the channel from scratch to WAIT_INITIAL_ROLE state
     * for a switch that does not have a sub-handshake
     * Builds on moveToWaitDescriptionStatReply()
     * adds testing for WAIT_DESCRIPTION_STAT_REPLY state
     * @param storageSourceConfig paramterizes the contents of the storage
     * source (for IS_CORE_SWITCH)
     */
    public void doMoveToWaitInitialRole(MockStorageSourceConfig cfg)
            throws Exception {
        moveToWaitDescriptionStatReply();

        // We do not expect a write to the channel for the role request. We add
        // the channel to the controller and the controller would in turn
        // call handler.sendRoleRequest(). Since we mock the controller so
        // we won't see the handler.sendRoleRequest() call and therefore we
        // won't see any calls on the channel.
        resetChannel();
        replay(channel);

        // build the stats reply
        OFStatisticsReply sr = createDescriptionStatsReply();
        OFDescriptionStatistics desc =
                (OFDescriptionStatistics) sr.getFirstStatistics();

        setupMessageEvent(Collections.<OFMessage>singletonList(sr));
        setupMockStorageSource(cfg);

        setupSwitchForInstantiationWithReset(cfg.dpid);
        sw.startDriverHandshake();
        expectLastCall().once();
        sw.isDriverHandshakeComplete();
        expectLastCall().andReturn(true).once();

        if (cfg.isPresent)
            sw.setAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH, cfg.isCoreSwitch);
        replay(sw);

        // mock controller
        reset(controller);
        expect(controller.getDebugCounter()).andReturn(debugCounterService)
                .once();
        controller.flushAll();
        expectLastCall().once();
        expect(controller.getThreadPoolService())
                .andReturn(threadPool).once();
        expect(controller.getOFSwitchInstance(eq(desc)))
                .andReturn(sw).once();
        expect(controller.getCoreFlowPriority())
                .andReturn(CORE_PRIORITY).once();
        expect(controller.getAccessFlowPriority())
                .andReturn(ACCESS_PRIORITY).once();
        controller.addSwitchChannelAndSendInitialRole(handler);
        expectLastCall().once();
        expect(controller.getStorageSourceService())
                .andReturn(storageSource).atLeastOnce();
        replay(controller);

        // send the description stats reply
        handler.messageReceived(ctx, messageEvent);

        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());
        verifyStorageSource();
    }

    /**
     * Move the channel from scratch to WAIT_INITIAL_ROLE state via
     * WAIT_SWITCH_DRIVER_SUB_HANDSHAKE
     * Does extensive testing for the WAIT_SWITCH_DRIVER_SUB_HANDSHAKE state
     *
     */
    @Test
    public void testSwitchDriverSubHandshake()
            throws Exception {
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;

        moveToWaitDescriptionStatReply();

        resetChannel();
        channel.write(capture(writeCapture));
        expectLastCall().andReturn(null).atLeastOnce();
        replay(channel);

        // build the stats reply
        OFStatisticsReply sr = createDescriptionStatsReply();
        OFDescriptionStatistics desc =
                (OFDescriptionStatistics) sr.getFirstStatistics();

        setupMessageEvent(Collections.<OFMessage>singletonList(sr));
        setupMockStorageSource(cfg);

        // Start the sub-handshake. Switch will indicate that it's not
        // complete yet
        setupSwitchForInstantiationWithReset(cfg.dpid);
        sw.startDriverHandshake();
        expectLastCall().once();
        sw.isDriverHandshakeComplete();
        expectLastCall().andReturn(false).once();

        if (cfg.isPresent)
            sw.setAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH, cfg.isCoreSwitch);
        replay(sw);

        // mock controller
        reset(controller);
        expect(controller.getDebugCounter()).andReturn(debugCounterService)
                .once();
        controller.flushAll();
        expectLastCall().once();
        expect(controller.getThreadPoolService())
                .andReturn(threadPool).once();
        expect(controller.getOFSwitchInstance(eq(desc)))
                .andReturn(sw).once();
        expect(controller.getCoreFlowPriority())
                .andReturn(CORE_PRIORITY).once();
        expect(controller.getAccessFlowPriority())
                .andReturn(ACCESS_PRIORITY).once();
        expect(controller.getStorageSourceService())
                .andReturn(storageSource).atLeastOnce();
        replay(controller);

        // send the description stats reply
        handler.messageReceived(ctx, messageEvent);

        assertEquals(OFChannelHandler.ChannelState.WAIT_SWITCH_DRIVER_SUB_HANDSHAKE,
                     handler.getStateForTesting());
        assertFalse("Unexpected message captured", writeCapture.hasCaptured());
        verifyStorageSource();
        verify(sw);


        //-------------------------------------------------
        // Send a message to the handler, it should be passed to the
        // switch's sub-handshake handling.
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.HELLO);
        resetToStrict(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.processDriverHandshakeMessage(m);
        expectLastCall().once();
        expect(sw.isDriverHandshakeComplete()).andReturn(false).once();
        replay(sw);

        sendMessageToHandlerWithControllerReset(Collections.singletonList(m));
        assertEquals(OFChannelHandler.ChannelState.WAIT_SWITCH_DRIVER_SUB_HANDSHAKE,
                     handler.getStateForTesting());
        assertFalse("Unexpected message captured", writeCapture.hasCaptured());
        verify(sw);

        //-------------------------------------------------
        // Send a ECHO_REQUEST. This should be handled by the OFChannelHandler
        // and *not* passed to switch sub-handshake
        // TODO: should this be also passed to the switch handshake instead?
        m = BasicFactory.getInstance().getMessage(OFType.ECHO_REQUEST);
        m.setXid(0x042042);

        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        replay(sw);
        sendMessageToHandlerWithControllerReset(Collections.singletonList(m));
        assertEquals(OFChannelHandler.ChannelState.WAIT_SWITCH_DRIVER_SUB_HANDSHAKE,
                     handler.getStateForTesting());
        List<OFMessage> msgs = getMessagesFromCapture();
        assertEquals(1, msgs.size());
        assertEquals(OFType.ECHO_REPLY, msgs.get(0).getType());
        assertEquals(0x42042, msgs.get(0).getXid());
        verify(sw);


        //-------------------------------------------------
        //-------------------------------------------------
        // Send a message to the handler, it should be passed to the
        // switch's sub-handshake handling. After this message the
        // sub-handshake will be complete
        m = BasicFactory.getInstance().getMessage(OFType.FLOW_REMOVED);
        resetToStrict(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.processDriverHandshakeMessage(m);
        expectLastCall().once();
        expect(sw.isDriverHandshakeComplete()).andReturn(true).once();
        replay(sw);

        verify(controller);
        reset(controller);
        controller.addSwitchChannelAndSendInitialRole(handler);
        expectLastCall().once();
        sendMessageToHandlerNoControllerReset(Collections.singletonList(m));
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());
        assertFalse("Unexpected message captured", writeCapture.hasCaptured());
        verify(sw);

        //-------------------------------------------------

        //-------------------------------------------------
    }

    @Test
    /** Test WaitDescriptionReplyState. No config for switch in storage */
    public void testWaitDescriptionReplyState1() throws Exception {
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
    }

    @Test
    /** Test WaitDescriptionReplyState. switch is core switch */
    public void testWaitDescriptionReplyState2() throws Exception {
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = true;
        cfg.isCoreSwitch = true;
        doMoveToWaitInitialRole(cfg);
    }

    @Test
    /** Test WaitDescriptionReplyState. switch is NOT core switch */
    public void testWaitDescriptionReplyState3() throws Exception {
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = true;
        cfg.isCoreSwitch = true;
        doMoveToWaitInitialRole(cfg);
    }

    /**
     * Helper
     * Verify that the given OFMessage is a correct Nicira RoleRequest message
     * for the given role using the given xid.
     */
    private void verifyRoleRequest(OFMessage m,
                                   int expectedXid,
                                   Role expectedRole) {
        assertEquals(OFType.VENDOR, m.getType());
        OFVendor vendorMsg = (OFVendor)m;
        assertEquals(expectedXid, vendorMsg.getXid());
        assertEquals(OFNiciraVendorData.NX_VENDOR_ID, vendorMsg.getVendor());
        assertTrue("Vendor data is not an instance of OFRoleRequestVendorData"
                   + " its class is: " + vendorMsg.getVendorData().getClass().getName(),
                   vendorMsg.getVendorData() instanceof OFRoleRequestVendorData);
        OFRoleRequestVendorData requestData =
                (OFRoleRequestVendorData)vendorMsg.getVendorData();
        assertEquals(expectedRole.toNxRole(), requestData.getRole());
    }

    /**
     * Setup the mock switch and write capture for a role request, set the
     * role and verify mocks.
     * @param supportsNxRole whether the switch supports role request messages
     * to setup the attribute. This must be null (don't yet know if roles
     * supported: send to check) or true.
     * @param xid The xid to use in the role request
     * @param role The role to send
     * @throws IOException
     */
    private void setupSwitchSendRoleRequestAndVerify(Boolean supportsNxRole,
                                           int xid,
                                           Role role) throws IOException {
        assertTrue("This internal test helper method most not be called " +
                   "with supportsNxRole==false. Test setup broken",
                   supportsNxRole == null || supportsNxRole == true);
        reset(sw);
        expect(sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(supportsNxRole).atLeastOnce();
        expect(sw.getNextTransactionId()).andReturn(xid).once();
        sw.write(capture(writeCapture), EasyMock.<FloodlightContext>anyObject());
        expectLastCall().anyTimes();
        replay(sw);

        handler.sendRoleRequest(role);

        List<OFMessage> msgs = getMessagesFromCapture();
        assertEquals(1, msgs.size());
        verifyRoleRequest(msgs.get(0), xid, role);
        verify(sw);
    }

    /**
     * Setup the mock switch for a role change request where the switch
     * does not support roles.
     *
     * Needs to verify and reset the controller since we need to set
     * an expectation
     */
    private void setupSwitchRoleChangeUnsupported(int xid,
                                                  Role role) {
        boolean supportsNxRole = false;
        reset(sw);
        expect(sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(supportsNxRole).atLeastOnce();
        // TODO: hmmm. While it's not incorrect that we set the attribute
        // again it looks odd. Maybe change
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, supportsNxRole);
        expectLastCall().anyTimes();
        sw.setHARole(role);
        expectLastCall().once();
        if (role == Role.SLAVE) {
            sw.disconnectOutputStream();
            expectLastCall().once();
        } else {
            verify(controller);
            reset(controller);
            controller.switchActivated(sw);
            replay(controller);
        }
        replay(sw);

        handler.sendRoleRequest(role);

        verify(sw);
    }

    /** Return a Nicira RoleReply message for the given role */
    private OFMessage getRoleReply(int xid, Role role) {
        OFVendor vm = (OFVendor)BasicFactory.getInstance()
                .getMessage(OFType.VENDOR);
        vm.setXid(xid);
        vm.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
        OFRoleReplyVendorData replyData = new OFRoleReplyVendorData();
        replyData.setRole(role.toNxRole());
        vm.setVendorData(replyData);
        return vm;
    }

    /** Return an OFError of the given type with the given xid */
    private OFMessage getErrorMessage(OFErrorType type,
                                      int i,
                                      int xid) {
        OFError e = (OFError) BasicFactory.getInstance()
                .getMessage(OFType.ERROR);
        e.setErrorType(type);
        e.setErrorCode((short)i);
        e.setXid(xid);
        return e;
    }


    /** Move the channel from scratch to MASTER state
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     *
     * This method tests only the simple case that the switch supports roles
     * and transitions to MASTER
     */
    @Test
    public void testInitialMoveToMasterWithRole() throws Exception {
        int xid = 42;
        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.MASTER);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
               .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        expectLastCall().once();
        sw.setHARole(Role.MASTER);
        expectLastCall().once();
        replay(sw);

        verify(controller);
        reset(controller);
        controller.switchActivated(sw);
        expectLastCall().once();
        OFMessage reply = getRoleReply(xid, Role.MASTER);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerNoControllerReset(Collections.singletonList(reply));

        assertEquals(OFChannelHandler.ChannelState.MASTER,
                     handler.getStateForTesting());
    }

    /** Move the channel from scratch to SLAVE state
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     *
     * This method tests only the simple case that the switch supports roles
     * and transitions to SLAVE
     */
    @Test
    public void testInitialMoveToSlaveWithRole() throws Exception {
        int xid = 42;
        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.SLAVE);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        expectLastCall().once();
        sw.setHARole(Role.SLAVE);
        expectLastCall().once();
        replay(sw);

        verify(controller);
        reset(controller);
        controller.switchDeactivated(sw);
        expectLastCall().once();
        OFMessage reply = getRoleReply(xid, Role.SLAVE);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerNoControllerReset(Collections.singletonList(reply));

        assertEquals(OFChannelHandler.ChannelState.SLAVE,
                     handler.getStateForTesting());
    }

    /** Move the channel from scratch to MASTER state
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     *
     * This method tests the case that the switch does NOT support roles.
     * The channel handler still needs to send the initial request to find
     * out that whether the switch supports roles.
     */
    @Test
    public void testInitialMoveToMasterNoRole() throws Exception {
        int xid = 43;
        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.MASTER);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
        expectLastCall().once();
        sw.setHARole(Role.MASTER);
        expectLastCall().once();
        replay(sw);

        // FIXME: shouldn't use ordinal(), but OFError is broken

        // Error with incorrect xid and type. Should be ignored.
        OFMessage err = getErrorMessage(OFErrorType.OFPET_BAD_ACTION,
                                        0,
                                        xid+1);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerWithControllerReset(Collections.singletonList(err));
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Error with correct xid. Should trigger state transition
        err = getErrorMessage(OFErrorType.OFPET_BAD_REQUEST,
                              OFError.OFBadRequestCode.OFPBRC_BAD_VENDOR.ordinal(),
                              xid);
        verify(controller);
        reset(controller);
        controller.switchActivated(sw);
        expectLastCall().once();
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerNoControllerReset(Collections.singletonList(err));

        assertEquals(OFChannelHandler.ChannelState.MASTER,
                     handler.getStateForTesting());

    }

    /** Move the channel from scratch to MASTER state
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     *
     * We let the initial role request time out. Role support should be
     * disabled but the switch should be activated.
     */
    @Test
    public void testInitialMoveToMasterTimeout() throws Exception {
        int timeout = 50;
        handler.useRoleChangerWithOtherTimeoutForTesting(timeout);
        int xid = 4343;

        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.MASTER);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
        expectLastCall().once();
        sw.setHARole(Role.MASTER);
        expectLastCall().once();
        replay(sw);

        OFMessage m = BasicFactory.getInstance().getMessage(OFType.ECHO_REPLY);

        Thread.sleep(timeout+5);

        verify(controller);
        reset(controller);
        controller.switchActivated(sw);
        expectLastCall().once();
        sendMessageToHandlerNoControllerReset(Collections.singletonList(m));

        assertEquals(OFChannelHandler.ChannelState.MASTER,
                     handler.getStateForTesting());

    }


    /** Move the channel from scratch to SLAVE state
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     *
     * This method tests the case that the switch does NOT support roles.
     * The channel handler still needs to send the initial request to find
     * out that whether the switch supports roles.
     *
     */
    @Test
    public void testInitialMoveToSlaveNoRole() throws Exception {
        int xid = 44;
        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.SLAVE);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
        expectLastCall().once();
        sw.setHARole(Role.SLAVE);
        expectLastCall().once();
        sw.disconnectOutputStream(); // Make sure we disconnect
        expectLastCall().once();
        replay(sw);


        // FIXME: shouldn't use ordinal(), but OFError is broken

        // Error with incorrect xid and type. Should be ignored.
        OFMessage err = getErrorMessage(OFErrorType.OFPET_BAD_ACTION,
                                        0,
                                        xid+1);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerWithControllerReset(Collections.singletonList(err));
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Error with correct xid. Should trigger state transition
        err = getErrorMessage(OFErrorType.OFPET_BAD_REQUEST,
                              OFError.OFBadRequestCode.OFPBRC_BAD_VENDOR.ordinal(),
                              xid);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerWithControllerReset(Collections.singletonList(err));
    }


    /** Move the channel from scratch to SLAVE state
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     *
     * We let the initial role request time out. The switch should be
     * disconnected
     */
    @Test
    public void testInitialMoveToSlaveTimeout() throws Exception {
        int timeout = 50;
        handler.useRoleChangerWithOtherTimeoutForTesting(timeout);
        int xid = 4444;

        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.SLAVE);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
        expectLastCall().once();
        sw.setHARole(Role.SLAVE);
        expectLastCall().once();
        sw.disconnectOutputStream(); // Make sure we disconnect
        expectLastCall().once();
        replay(sw);

        OFMessage m = BasicFactory.getInstance().getMessage(OFType.ECHO_REPLY);

        Thread.sleep(timeout+5);

        sendMessageToHandlerWithControllerReset(Collections.singletonList(m));
    }


    /** Move channel from scratch to WAIT_INITIAL_STATE, then MASTER,
     * then SLAVE for cases where the switch does not support roles.
     * I.e., the final SLAVE transition should disconnect the switch.
     */
    @Test
    public void testNoRoleInitialToMasterToSlave() throws Exception {
        int xid = 46;
        // First, lets move the state to MASTER without role support
        testInitialMoveToMasterNoRole();
        assertEquals(OFChannelHandler.ChannelState.MASTER,
                     handler.getStateForTesting());

        // try to set master role again. should be a no-op
        setupSwitchRoleChangeUnsupported(xid, Role.MASTER);
        assertEquals(OFChannelHandler.ChannelState.MASTER,
                     handler.getStateForTesting());


        setupSwitchRoleChangeUnsupported(xid, Role.SLAVE);
        assertEquals(OFChannelHandler.ChannelState.SLAVE,
                     handler.getStateForTesting());

    }

    /** Move the channel to MASTER state
     * Expects that the channel is in MASTER or SLAVE state.
     *
     */
    public void changeRoleToMasterWithRequest() throws Exception {
        int xid = 4242;

        assertTrue("This method can only be called when handler is in " +
                   "MASTER or SLAVE role", handler.isHandshakeComplete());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(true, xid, Role.MASTER);

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        expectLastCall().once();
        sw.setHARole(Role.MASTER);
        expectLastCall().once();
        replay(sw);

        verify(controller);
        reset(controller);
        controller.switchActivated(sw);
        expectLastCall().once();
        OFMessage reply = getRoleReply(xid, Role.MASTER);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerNoControllerReset(Collections.singletonList(reply));

        assertEquals(OFChannelHandler.ChannelState.MASTER,
                     handler.getStateForTesting());
    }

    /** Move the channel to SLAVE state
     * Expects that the channel is in MASTER or SLAVE state.
     *
     */
    public void changeRoleToSlaveWithRequest() throws Exception {
        int xid = 2323;

        assertTrue("This method can only be called when handler is in " +
                   "MASTER or SLAVE role", handler.isHandshakeComplete());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(true, xid, Role.SLAVE);

        // prepare mocks and inject the role reply message
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        expectLastCall().once();
        sw.setHARole(Role.SLAVE);
        expectLastCall().once();
        replay(sw);

        verify(controller);
        reset(controller);
        controller.switchDeactivated(sw);
        expectLastCall().once();
        OFMessage reply = getRoleReply(xid, Role.SLAVE);
        // sendMessageToHandler will verify and rest controller mock
        sendMessageToHandlerNoControllerReset(Collections.singletonList(reply));

        assertEquals(OFChannelHandler.ChannelState.SLAVE,
                     handler.getStateForTesting());
    }

    @Test
    public void testMultiRoleChange1() throws Exception {
        testInitialMoveToMasterWithRole();
        changeRoleToMasterWithRequest();
        changeRoleToSlaveWithRequest();
        changeRoleToSlaveWithRequest();
        changeRoleToMasterWithRequest();
        changeRoleToSlaveWithRequest();
    }

    @Test
    public void testMultiRoleChange2() throws Exception {
        testInitialMoveToSlaveWithRole();
        changeRoleToMasterWithRequest();
        changeRoleToSlaveWithRequest();
        changeRoleToSlaveWithRequest();
        changeRoleToMasterWithRequest();
        changeRoleToSlaveWithRequest();
    }

    /** Start from scratch and reply with an unexpected error to the role
     * change request
     * Builds on doMoveToWaitInitialRole()
     * adds testing for WAIT_INITAL_ROLE state
     */
    @Test
    public void testInitialRoleChangeOtherError() throws Exception {
        int xid = 4343;
        // first, move us to WAIT_INITIAL_ROLE_STATE
        MockStorageSourceConfig cfg = new MockStorageSourceConfig();
        cfg.dpid = HexString.toHexString(featuresReply.getDatapathId());
        cfg.isPresent = false;
        doMoveToWaitInitialRole(cfg);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());

        // Set the role
        setupSwitchSendRoleRequestAndVerify(null, xid, Role.MASTER);
        assertEquals(OFChannelHandler.ChannelState.WAIT_INITIAL_ROLE,
                     handler.getStateForTesting());


        // FIXME: shouldn't use ordinal(), but OFError is broken

        OFMessage err = getErrorMessage(OFErrorType.OFPET_BAD_ACTION,
                                        0,
                                        xid);
        verify(sw);
        reset(sw);
        expect(sw.inputThrottled(anyObject(OFMessage.class)))
                .andReturn(false).anyTimes();
        replay(sw);
        sendMessageToHandlerWithControllerReset(Collections.singletonList(err));

        verifyExceptionCaptured(SwitchStateException.class);
    }

    /**
     * Test dispatch of messages while in MASTER role
     */
    @Test
    public void testMessageDispatchMaster() throws Exception {
        testInitialMoveToMasterWithRole();

        // Send packet in. expect dispatch
        OFPacketIn pi = (OFPacketIn)
                BasicFactory.getInstance().getMessage(OFType.PACKET_IN);
        reset(controller);
        controller.handleMessage(sw, pi, null);
        expectLastCall().once();
        sendMessageToHandlerNoControllerReset(
               Collections.<OFMessage>singletonList(pi));
        verify(controller);
        // TODO: many more to go
    }

    /**
     * Test port status message handling while MASTER
     *
     */
    @Test
    public void testPortStatusMessageMaster() throws Exception {
        long dpid = featuresReply.getDatapathId();
        testInitialMoveToMasterWithRole();

        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Port1");
        p.setPortNumber((short)1);
        OFPortStatus ps = (OFPortStatus)
                BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);
        ps.setDesc(p);

        // The events we expect sw.handlePortStatus to return
        // We'll just use the same list for all valid OFPortReasons and add
        // arbitrary events for arbitrary ports that are not necessarily
        // related to the port status message. Our goal
        // here is not to return the correct set of events but the make sure
        // that a) sw.handlePortStatus is called
        //      b) the list of events sw.handlePortStatus returns is sent
        //         as IOFSwitchListener notifications.
        OrderedCollection<PortChangeEvent> events =
                new LinkedHashSetWrapper<PortChangeEvent>();
        ImmutablePort p1 = ImmutablePort.create("eth1", (short)1);
        ImmutablePort p2 = ImmutablePort.create("eth2", (short)2);
        ImmutablePort p3 = ImmutablePort.create("eth3", (short)3);
        ImmutablePort p4 = ImmutablePort.create("eth4", (short)4);
        ImmutablePort p5 = ImmutablePort.create("eth5", (short)5);
        events.add(new PortChangeEvent(p1, PortChangeType.ADD));
        events.add(new PortChangeEvent(p2, PortChangeType.DELETE));
        events.add(new PortChangeEvent(p3, PortChangeType.UP));
        events.add(new PortChangeEvent(p4, PortChangeType.DOWN));
        events.add(new PortChangeEvent(p5, PortChangeType.OTHER_UPDATE));


        for (OFPortReason reason: OFPortReason.values()) {
            ps.setReason(reason.getReasonCode());

            reset(sw);
            expect(sw.inputThrottled(anyObject(OFMessage.class)))
                    .andReturn(false).anyTimes();
            expect(sw.getId()).andReturn(dpid).anyTimes();

            expect(sw.processOFPortStatus(ps)).andReturn(events).once();
            replay(sw);

            reset(controller);
            controller.notifyPortChanged(sw, p1, PortChangeType.ADD);
            controller.notifyPortChanged(sw, p2, PortChangeType.DELETE);
            controller.notifyPortChanged(sw, p3, PortChangeType.UP);
            controller.notifyPortChanged(sw, p4, PortChangeType.DOWN);
            controller.notifyPortChanged(sw, p5, PortChangeType.OTHER_UPDATE);
            sendMessageToHandlerNoControllerReset(
                   Collections.<OFMessage>singletonList(ps));
            verify(sw);
            verify(controller);
        }
    }

    /**
     * Test re-assert MASTER
     *
     */
    @Test
    public void testReassertMaster() throws Exception {
        testInitialMoveToMasterWithRole();

        OFError err = (OFError)
                BasicFactory.getInstance().getMessage(OFType.ERROR);
        err.setXid(42);
        err.setErrorType(OFErrorType.OFPET_BAD_REQUEST);
        err.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);

        reset(controller);
        controller.reassertRole(handler, Role.MASTER);
        expectLastCall().once();
        controller.handleMessage(sw, err, null);
        expectLastCall().once();

        sendMessageToHandlerNoControllerReset(
                Collections.<OFMessage>singletonList(err));

        verify(sw);
        verify(controller);
    }


}
