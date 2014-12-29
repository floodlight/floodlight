package net.floodlightcontroller.core;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.debugcounter.DebugCounterServiceImpl;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFEchoRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFHello;
import org.projectfloodlight.openflow.protocol.OFHelloElem;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFRoleRequest;
import org.projectfloodlight.openflow.protocol.OFRoleRequestFailedCode;
import org.projectfloodlight.openflow.protocol.OFStatsReplyFlags;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.errormsg.OFRoleRequestFailedErrorMsg;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.util.FutureTestUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;

public class OFConnectionTest {
    private static final Logger logger = LoggerFactory.getLogger(OFConnectionTest.class);

    private OFFactory factory;
    private Channel channel;
    private OFConnection conn;
    private DatapathId switchId;
    private Timer timer;

    @Before
    public void setUp() throws Exception {
        factory = OFFactories.getFactory(OFVersion.OF_13);
        switchId = DatapathId.of(1);
        timer = new HashedWheelTimer();
        channel = EasyMock.createNiceMock(Channel.class);        
        IDebugCounterService debugCounterService = new DebugCounterServiceImpl();
        debugCounterService.registerModule(OFConnectionCounters.COUNTER_MODULE);
        conn = new OFConnection(switchId, factory, channel, OFAuxId.MAIN,
                                debugCounterService, timer);
    }

    @Test(timeout = 5000)
    public void testWriteRequestSuccess() throws InterruptedException, ExecutionException {
        Capture<List<OFMessage>> cMsgList = prepareChannelForWriteList();

        OFEchoRequest echoRequest = factory.echoRequest(new byte[] {});
        ListenableFuture<OFEchoReply> future = conn.writeRequest(echoRequest);
        assertThat("Connection should have 1 pending request",
                conn.getPendingRequestIds().size(), equalTo(1));

        assertThat("Should have captured MsgList", cMsgList.getValue(),
                Matchers.<OFMessage> contains(echoRequest));

        assertThat("Future should not be complete yet", future.isDone(), equalTo(false));

        OFEchoReply echoReply = factory.buildEchoReply()
                .setXid(echoRequest.getXid())
                .build();

        assertThat("Connection should have accepted the response",
                conn.deliverResponse(echoReply),
                equalTo(true));
        assertThat("Future should be complete ", future.isDone(), equalTo(true));
        assertThat(future.get(), equalTo(echoReply));
        assertThat("Connection should have no pending requests",
                conn.getPendingRequestIds().isEmpty(), equalTo(true));
    }

    /** write a stats request message that triggers two responses */
    @Test(timeout = 5000)
    public void testWriteStatsRequestSuccess() throws InterruptedException, ExecutionException {
        Capture<List<OFMessage>> cMsgList = prepareChannelForWriteList();

        OFFlowStatsRequest flowStatsRequest = factory.buildFlowStatsRequest().build();
        ListenableFuture<List<OFFlowStatsReply>> future = conn.writeStatsRequest(flowStatsRequest);
        assertThat("Connection should have 1 pending request",
                conn.getPendingRequestIds().size(), equalTo(1));

        assertThat("Should have captured MsgList", cMsgList.getValue(),
                Matchers.<OFMessage> contains(flowStatsRequest));

        assertThat("Future should not be complete yet", future.isDone(), equalTo(false));

        OFFlowStatsReply statsReply1 = factory.buildFlowStatsReply()
                .setXid(flowStatsRequest.getXid())
                .setFlags(Sets.immutableEnumSet(OFStatsReplyFlags.REPLY_MORE))
                .build();

        assertThat("Connection should have accepted the response",
                conn.deliverResponse(statsReply1),
                equalTo(true));
        assertThat("Future should not be complete ", future.isDone(), equalTo(false));

        OFFlowStatsReply statsReply2 = factory.buildFlowStatsReply()
                .setXid(flowStatsRequest.getXid())
                .build();

        assertThat("Connection should have accepted the response",
                conn.deliverResponse(statsReply2),
                equalTo(true));
        assertThat("Future should be complete ", future.isDone(), equalTo(true));

        assertThat(future.get(), Matchers.contains(statsReply1, statsReply2));
        assertThat("Connection should have no pending requests",
                conn.getPendingRequestIds().isEmpty(), equalTo(true));
    }

    private Capture<List<OFMessage>> prepareChannelForWriteList() {
        EasyMock.expect(channel.isConnected()).andReturn(Boolean.TRUE).anyTimes();
        Capture<List<OFMessage>> cMsgList = new Capture<>();
        expect(channel.write(capture(cMsgList))).andReturn(null).once();
        replay(channel);
        return cMsgList;
    }

    /** write a request which triggers an OFErrorMsg response */
    @Test(timeout = 5000)
    public void testWriteRequestOFErrorMsg() throws InterruptedException, ExecutionException {
        Capture<List<OFMessage>> cMsgList = prepareChannelForWriteList();

        OFRoleRequest roleRequest = factory.buildRoleRequest().setRole(OFControllerRole.ROLE_MASTER).build();
        ListenableFuture<OFRoleReply> future = conn.writeRequest(roleRequest);
        assertThat("Connection should have 1 pending request",
                conn.getPendingRequestIds().size(), equalTo(1));

        assertThat("Should have captured MsgList", cMsgList.getValue(),
                Matchers.<OFMessage> contains(roleRequest));

        assertThat("Future should not be complete yet", future.isDone(), equalTo(false));
        OFRoleRequestFailedErrorMsg roleError = factory.errorMsgs().buildRoleRequestFailedErrorMsg()
            .setXid(roleRequest.getXid())
            .setCode(OFRoleRequestFailedCode.STALE)
            .build();

        assertThat("Connection should have accepted the response",
                conn.deliverResponse(roleError),
                equalTo(true));

        OFErrorMsgException e =
                FutureTestUtils.assertFutureFailedWithException(future,
                        OFErrorMsgException.class);
        assertThat(e.getErrorMessage(), CoreMatchers.<OFErrorMsg>equalTo(roleError));
    }

    @Test(timeout = 5000)
    public void testWriteRequestNotConnectedFailure() throws InterruptedException,
            ExecutionException {
        EasyMock.expect(channel.isConnected()).andReturn(Boolean.FALSE).anyTimes();
        replay(channel);

        OFEchoRequest echoRequest = factory.echoRequest(new byte[] {});
        ListenableFuture<OFEchoReply> future = conn.writeRequest(echoRequest);

        SwitchDisconnectedException e =
                FutureTestUtils.assertFutureFailedWithException(future,
                        SwitchDisconnectedException.class);
        assertThat(e.getId(), equalTo(switchId));
        assertThat("Connection should have no pending requests",
                conn.getPendingRequestIds().isEmpty(), equalTo(true));
    }

    @Test(timeout = 5000)
    public void testWriteRequestDisconnectFailure() throws InterruptedException, ExecutionException {
        prepareChannelForWriteList();

        OFEchoRequest echoRequest = factory.echoRequest(new byte[] {});
        ListenableFuture<OFEchoReply> future = conn.writeRequest(echoRequest);

        assertThat("Connection should have 1 pending request", conn.getPendingRequestIds().size(),
                equalTo(1));
        assertThat("Future should not be complete yet", future.isDone(), equalTo(false));

        conn.disconnected();

        SwitchDisconnectedException e =
                FutureTestUtils.assertFutureFailedWithException(future,
                        SwitchDisconnectedException.class);
        assertThat(e.getId(), equalTo(switchId));
        assertThat("Connection should have no pending requests",
                conn.getPendingRequestIds().isEmpty(), equalTo(true));
    }

    /** write a packetOut, which is buffered */
    @Test(timeout = 5000)
    public void testSingleMessageWrite() throws InterruptedException, ExecutionException {
        Capture<List<OFMessage>> cMsgList = prepareChannelForWriteList();

        OFPacketOut packetOut = factory.buildPacketOut()
                .setData(new byte[] { 0x01, 0x02, 0x03, 0x04 })
                .setActions(ImmutableList.<OFAction>of( factory.actions().output(OFPort.of(1), 0)))
                .build();
        
        conn.write(packetOut);
        assertThat("Write should have been flushed", cMsgList.hasCaptured(), equalTo(true));
        
        List<OFMessage> value = cMsgList.getValue();
        logger.info("Captured channel write: "+value);
        assertThat("Should have captured MsgList", cMsgList.getValue(),
                Matchers.<OFMessage> contains(packetOut));
    }

    /** write a list of messages */
    @Test(timeout = 5000)
    public void testMessageWriteList() throws InterruptedException, ExecutionException {
        Capture<List<OFMessage>> cMsgList = prepareChannelForWriteList();

        OFHello hello = factory.hello(ImmutableList.<OFHelloElem>of());
        OFPacketOut packetOut = factory.buildPacketOut()
                .setData(new byte[] { 0x01, 0x02, 0x03, 0x04 })
                .setActions(ImmutableList.<OFAction>of( factory.actions().output(OFPort.of(1), 0)))
                .build();

        conn.write(ImmutableList.of(hello, packetOut));

        assertThat("Write should have been written", cMsgList.hasCaptured(), equalTo(true));
        List<OFMessage> value = cMsgList.getValue();
        logger.info("Captured channel write: "+value);
        assertThat("Should have captured MsgList", cMsgList.getValue(),
                Matchers.<OFMessage> contains(hello, packetOut));
    }

}
