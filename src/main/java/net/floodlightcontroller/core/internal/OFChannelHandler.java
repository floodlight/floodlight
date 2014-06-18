package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch.PortChangeEvent;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.Controller.Counters;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterException;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.util.LoadMonitor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.openflow.protocol.OFBarrierReply;
import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFEchoRequest;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFGetConfigReply;
import org.openflow.protocol.OFGetConfigRequest;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFQueueGetConfigReply;
import org.openflow.protocol.OFSetConfig;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFSwitchConfig;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.OFError.OFBadActionCode;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.OFError.OFFlowModFailedCode;
import org.openflow.protocol.OFError.OFHelloFailedCode;
import org.openflow.protocol.OFError.OFPortModFailedCode;
import org.openflow.protocol.OFError.OFQueueOpFailedCode;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bigswitch.floodlight.vendor.OFBigSwitchVendorData;
import com.bigswitch.floodlight.vendor.OFBsnL2TableSetVendorData;



/**
 * Channel handler deals with the switch connection and dispatches
 * switch messages to the appropriate locations.
 * @author readams
 */
class OFChannelHandler
    extends IdleStateAwareChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(OFChannelHandler.class);

    private static final long DEFAULT_ROLE_TIMEOUT_MS = 10*1000; // 10 sec

    private final Controller controller;
    private final Counters counters;
    private IOFSwitch sw;
    private Channel channel;
    // State needs to be volatile because the HandshakeTimeoutHandler
    // needs to check if the handshake is complete
    private volatile ChannelState state;
    private RoleChanger roleChanger;
    private OFFeaturesReply featuresReply;

    private final ArrayList<OFPortStatus> pendingPortStatusMsg;

    /** transaction Ids to use during handshake. Since only one thread
     * calls into the OFChannelHandler we don't need atomic.
     * We will count down
     */
    private int handshakeTransactionIds = -1;



    /**
     * When we remove a pending role request and set the role on the switch
     * we use this enum to indicate how we arrived at the decision.
     * @author gregor
     */
    private enum RoleRecvStatus {
        /** We receveived a role reply message from the switch */
        RECEIVED_REPLY,
        /** The switch returned an error indicated that roles are not
         * supported*/
        UNSUPPORTED,
        /** The request timed out */
        NO_REPLY;
    }
    /**
     * A utility class to handle role requests and replies for this channel.
     * After a role request is submitted the role changer keeps track of the
     * pending request, collects the reply (if any) and times out the request
     * if necessary.
     *
     * To simplify role handling we only keep track of the /last/ pending
     * role reply send to the switch. If multiple requests are pending and
     * we receive replies for earlier requests we ignore them. However, this
     * way of handling pending requests implies that we could wait forever if
     * a new request is submitted before the timeout triggers. If necessary
     * we could work around that though.
     * @author gregor
     */
    private class RoleChanger {
        // indicates that a request is currently pending
        // needs to be volatile to allow correct double-check idiom
        private volatile boolean requestPending;
        // the transactiong Id of the pending request
        private int pendingXid;
        // the role that's pending
        private Role pendingRole;
        // system time in MS when we send the request
        private long roleSubmitTime;
        // the timeout to use
        private final long roleTimeoutMs;

        public RoleChanger(long roleTimeoutMs) {
            this.requestPending = false;
            this.roleSubmitTime = 0;
            this.pendingXid = -1;
            this.pendingRole = null;
            this.roleTimeoutMs = roleTimeoutMs;
        }

        /**
         * Send NX role request message to the switch requesting the specified
         * role.
         *
         * @param sw switch to send the role request message to
         * @param role role to request
         */
        private int sendNxRoleRequest(Role role)
                throws IOException {

            int xid = sw.getNextTransactionId();
            // Convert the role enum to the appropriate integer constant used
            // in the NX role request message
            int nxRole = role.toNxRole();

            // Construct the role request message
            OFVendor roleRequest = (OFVendor)BasicFactory.getInstance()
                    .getMessage(OFType.VENDOR);
            roleRequest.setXid(xid);
            roleRequest.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
            OFRoleRequestVendorData roleRequestData = new OFRoleRequestVendorData();
            roleRequestData.setRole(nxRole);
            roleRequest.setVendorData(roleRequestData);
            roleRequest.setLengthU(OFVendor.MINIMUM_LENGTH +
                    roleRequestData.getLength());

            // Send it to the switch
            sw.write(Collections.<OFMessage>singletonList(roleRequest),
                     new FloodlightContext());

            return xid;
        }

        /**
         * Send a role request for the given role only if no other role
         * request is currently pending.
         * @param role The role to send to the switch.
         * @throws IOException
         */
        synchronized void sendRoleRequestIfNotPending(Role role)
                throws IOException {
            if (!requestPending)
                sendRoleRequest(role);
            else
                counters.roleNotResentBecauseRolePending.updateCounterWithFlush();
        }

        /**
         * Send a role request with the given role to the switch.
         *
         * Send a role request with the given role to the switch and update
         * the pending request and timestamp.
         *
         * @param role
         * @throws IOException
         */
        synchronized void sendRoleRequest(Role role) throws IOException {
            /*
             * There are three cases to consider for SUPPORTS_NX_ROLE:
             *
             * 1) unset. We have neither received a role reply from the
             *    switch nor has a request timed out. Send a request.
             * 2) TRUE: We've already send a request earlier and received
             *    a reply. The switch supports role and we should send one.
             * 3) FALSE: We have already send a role and received an error.
             *    The switch does not support roles. Don't send a role request,
             *    set the switch's role directly.
             */
            Boolean supportsNxRole = (Boolean)
                    sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE);
            if ((supportsNxRole != null) && !supportsNxRole) {
                setSwitchRole(role, RoleRecvStatus.UNSUPPORTED);
            } else {
                pendingXid = sendNxRoleRequest(role);
                pendingRole = role;
                roleSubmitTime = System.currentTimeMillis();
                requestPending = true;
            }
        }

        /**
         * Deliver a received role reply and set SWITCH_SUPPORTS_NX_ROLE.
         *
         * Check if a request is pending and if the received reply matches the
         * the expected pending reply (we check both role and xid) we set
         * the role for the switch/channel.
         *
         * If a request is pending but doesn't match the reply we ignore it.
         *
         * If no request is pending we disconnect.
         *
         * @param xid
         * @param role
         * @throws SwitchStateException if no request is pending
         */
        synchronized void deliverRoleReply(int xid, Role role) {
            if (!requestPending) {
                // Maybe don't disconnect if the role reply we received is
                // for the same role we are already in.
                String msg = String.format("Switch: [%s], State: [%s], "
                                + "received unexpected RoleReply[%s]. "
                                + "No roles are pending",
                                OFChannelHandler.this.getSwitchInfoString(),
                                OFChannelHandler.this.state.toString(),
                                role);
                throw new SwitchStateException(msg);
            }

            if (pendingXid == xid && pendingRole == role) {
                log.debug("Received role reply message from {}, setting role to {}",
                          getSwitchInfoString(), role);
                counters.roleReplyReceived.updateCounterWithFlush();
                setSwitchRole(role, RoleRecvStatus.RECEIVED_REPLY);
            } else {
                log.debug("Received stale or unexpected role reply from " +
                          "switch {} ({}, xid={}). Ignoring. " +
                          "Waiting for {}, xid={}",
                          new Object[] { getSwitchInfoString(), role, xid,
                                         pendingRole, pendingXid });
            }
        }

        /**
         * Called if we receive an  error message. If the xid matches the
         * pending request we handle it otherwise we ignore it. We also
         * set SWITCH_SUPPORTS_NX_ROLE to false.
         *
         * Note: since we only keep the last pending request we might get
         * error messages for earlier role requests that we won't be able
         * to handle
         * @param xid
         * @return true if the error was handled by us, false otherwise
         * @throws SwitchStateException if the error was for the pending
         * role request but was unexpected
         */
        synchronized boolean deliverError(OFError error) {
            if (!requestPending)
                return false;

            if (pendingXid == error.getXid()) {
                boolean isBadRequestError =
                        (error.getErrorType() == OFError.OFErrorType.
                        OFPET_BAD_REQUEST.getValue());
                if (isBadRequestError) {
                    counters.roleReplyErrorUnsupported.updateCounterWithFlush();
                    setSwitchRole(pendingRole, RoleRecvStatus.UNSUPPORTED);
                } else {
                    // TODO: Is this the right thing to do if we receive
                    // some other error besides a bad request error?
                    // Presumably that means the switch did actually
                    // understand the role request message, but there
                    // was some other error from processing the message.
                    // OF 1.2 specifies a OFPET_ROLE_REQUEST_FAILED
                    // error code, but it doesn't look like the Nicira
                    // role request has that. Should check OVS source
                    // code to see if it's possible for any other errors
                    // to be returned.
                    // If we received an error the switch is not
                    // in the correct role, so we need to disconnect it.
                    // We could also resend the request but then we need to
                    // check if there are other pending request in which
                    // case we shouldn't resend. If we do resend we need
                    // to make sure that the switch eventually accepts one
                    // of our requests or disconnect the switch. This feels
                    // cumbersome.
                    String msg = String.format("Switch: [%s], State: [%s], "
                                    + "Unexpected error %s in respone to our "
                                    + "role request for %s.",
                                    OFChannelHandler.this.getSwitchInfoString(),
                                    OFChannelHandler.this.state.toString(),
                                    getErrorString(error),
                                    pendingRole);
                    throw new SwitchStateException(msg);
                }
                return true;
            }
            return false;
        }

        /**
         * Check if a pending role request has timed out.
         */
        void checkTimeout() {
            if (!requestPending)
                return;
            synchronized(this) {
                if (!requestPending)
                    return;
                long now = System.currentTimeMillis();
                if (now - roleSubmitTime > roleTimeoutMs) {
                    // timeout triggered.
                    counters.roleReplyTimeout.updateCounterWithFlush();
                    setSwitchRole(pendingRole, RoleRecvStatus.NO_REPLY);
                }
            }
        }

        /**
         * Set the role for this switch / channel.
         *
         * If the status indicates that we received a reply we set the role.
         * If the status indicates otherwise we disconnect the switch if
         * the role is SLAVE.
         *
         * "Setting a role" means setting the appropriate ChannelState,
         * setting the flags on the switch and
         * notifying Controller.java about new role of the switch
         *
         * @param role The role to set.
         * @param status How we derived at the decision to set this status.
         */
        synchronized private void setSwitchRole(Role role, RoleRecvStatus status) {
            requestPending = false;
            if (status == RoleRecvStatus.RECEIVED_REPLY)
                sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
            else
                sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
            sw.setHARole(role);

            if (role != Role.SLAVE) {
                OFChannelHandler.this.setState(ChannelState.MASTER);
                // TODO: should we really activate the switch again if it's
                // already master??
                if (log.isDebugEnabled()) {
                    log.debug("Switch {} activated. Role is now MASTER",
                              getSwitchInfoString());
                }
                controller.switchActivated(OFChannelHandler.this.sw);
            } else {
                OFChannelHandler.this.setState(ChannelState.SLAVE);
                if (status != RoleRecvStatus.RECEIVED_REPLY) {
                    if (log.isDebugEnabled()) {
                        log.debug("Disconnecting switch {}. Doesn't support role"
                              + "({}) request and controller is now SLAVE",
                              getSwitchInfoString(), status);
                    }
                    // the disconnect will trigger a switch removed to
                    // controller so no need to signal anything else
                    sw.disconnectOutputStream();
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Switch {} is now SLAVE",
                                  getSwitchInfoString());
                    }
                    controller.switchDeactivated(OFChannelHandler.this.sw);
                }
            }
        }
    }


    /**
     * The state machine for handling the switch/channel state.
     * @author gregor
     */
    enum ChannelState {
        /**
         * Initial state before channel is connected.
         */
        INIT(false) {
            @Override
            void
            processOFMessage(OFChannelHandler h, OFMessage m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFError(OFChannelHandler h, OFError m)
                    throws IOException {
                // need to implement since its abstract but it will never
                // be called
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                unhandledMessageReceived(h, m);
            }
        },

        /**
         * We send a HELLO to the switch and wait for a reply.
         * Once we receive the reply we send an OFFeaturesRequest and
         * a request to clear all FlowMods.
         * Next state is WAIT_FEATURES_REPLY
         */
        WAIT_HELLO(false) {
            @Override
            void processOFHello(OFChannelHandler h, OFHello m)
                    throws IOException {
                h.sendHandShakeMessage(OFType.FEATURES_REQUEST);
                h.setState(WAIT_FEATURES_REPLY);
            }
            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }
            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply  m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }
            @Override
            void processOFError(OFChannelHandler h, OFError m) {
                logErrorDisconnect(h, m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                unhandledMessageReceived(h, m);
            }
        },

        /**
         * We are waiting for a features reply message. Once we receive it
         * we send a SetConfig request, barrier, and GetConfig request.
         * Next stats is WAIT_CONFIG_REPLY or WAIT_SET_L2_TABLE_REPLY
         */
        WAIT_FEATURES_REPLY(false) {
            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                h.featuresReply = m;
                if (m.getTables() > 1) {
                    log.debug("Have {} table for switch {}", m.getTables(),
                              h.getSwitchInfoString());
                    // likely supports L2 table extensions. Send set
                    h.sendHandshakeL2TableSet();
                    // TODO: no L2 SET reply yet, so fire and forget the set
                    // table message and move directly to sendHandshakeConfig
                    h.sendHandshakeSetConfig();
                    h.setState(WAIT_CONFIG_REPLY);
                    //h.setState(WAIT_SET_L2_TABLE_REPLY);
                } else {
                    h.sendHandshakeSetConfig();
                    h.setState(WAIT_CONFIG_REPLY);
                }
            }
            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply  m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }
            @Override
            void processOFError(OFChannelHandler h, OFError m) {
                logErrorDisconnect(h, m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                unhandledMessageReceived(h, m);
            }
        },

        WAIT_SET_L2_TABLE_REPLY(false) {
            @Override void processOFVendor(OFChannelHandler h, OFVendor m)
                    throws IOException {
                // TODO: actually parse the response
                h.sendHandshakeSetConfig();
                h.setState(WAIT_CONFIG_REPLY);
            };

            @Override
            void processOFBarrierReply(OFChannelHandler h, OFBarrierReply m) {
                // do nothing;
            }

            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                // TODO: we could re-set the features reply
                illegalMessageReceived(h, m);
            }
            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply  m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFError(OFChannelHandler h, OFError m) {
                logErrorDisconnect(h, m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                h.pendingPortStatusMsg.add(m);
            }
        },

        /**
         * We are waiting for a config reply message. Once we receive it
         * we send a DescriptionStatsRequest to the switch.
         * Next state: WAIT_DESCRIPTION_STAT_REPLY
         */
        WAIT_CONFIG_REPLY(false) {
            @Override
            @LogMessageDocs({
                @LogMessageDoc(level="WARN",
                        message="Config Reply from {switch} has " +
                                "miss length set to {length}",
                        explanation="The controller requires that the switch " +
                                "use a miss length of 0xffff for correct " +
                                "function",
                        recommendation="Use a different switch to ensure " +
                                "correct function")
            })
            void processOFGetConfigReply(OFChannelHandler h, OFGetConfigReply m)
                    throws IOException {
                if (m.getMissSendLength() == (short)0xffff) {
                    log.trace("Config Reply from switch {} confirms "
                            + "miss length set to 0xffff",
                            h.getSwitchInfoString());
                } else {
                    // FIXME: we can't really deal with switches that don't send
                    // full packets. Shouldn't we drop the connection here?
                    // FIXME: count??
                    log.warn("Config Reply from switch {} has"
                            + "miss length set to {}",
                            h.getSwitchInfoString(),
                            m.getMissSendLength());
                }
                h.sendHandshakeDescriptionStatsRequest();
                h.setState(WAIT_DESCRIPTION_STAT_REPLY);
            }

            @Override
            void processOFBarrierReply(OFChannelHandler h, OFBarrierReply m) {
                // do nothing;
            }

            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                // TODO: we could re-set the features reply
                illegalMessageReceived(h, m);
            }
            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply  m)
                    throws IOException {
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFError(OFChannelHandler h, OFError m) {
                if (m.getErrorType() == OFErrorType.OFPET_BAD_REQUEST.getValue()
                        && m.getErrorCode() ==
                            OFBadRequestCode.OFPBRC_BAD_VENDOR.ordinal()) {
                    log.debug("Switch {} has multiple tables but does not " +
                            "support L2 table extension",
                            h.getSwitchInfoString());
                    return;
                }
                logErrorDisconnect(h, m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                h.pendingPortStatusMsg.add(m);
            }
        },

        /**
         * We are waiting for a OFDescriptionStat message from teh switch.
         * Once we receive any stat message we try to parse it. If it's not
         * a description stats message we disconnect. If its the expected
         * description stats message, we:
         *    - use the switch driver to bind the switch and get an IOFSwitch
         *      instance, setup the switch instance
         *    - setup the IOFSwitch instance
         *    - add switch to FloodlightProvider and send the intial role
         *      request to the switch.
         * Next state: WAIT_INITIAL_ROLE
         * All following states will have a h.sw instance!
         */
        WAIT_DESCRIPTION_STAT_REPLY(false) {
            @LogMessageDoc(message="Switch {switch info} bound to class " +
                "{switch driver}, description {switch description}",
                    explanation="The specified switch has been bound to " +
                            "a switch driver based on the switch description" +
                            "received from the switch")
            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply m) {
                // Read description, if it has been updated
                OFDescriptionStatistics description =
                        new OFDescriptionStatistics();
                ChannelBuffer data =
                        ChannelBuffers.buffer(description.getLength());
                OFStatistics f = m.getFirstStatistics();
                f.writeTo(data);
                description.readFrom(data);
                h.sw = h.controller.getOFSwitchInstance(description);
                // set switch information
                // set features reply and channel first so we a DPID and
                // channel info.
                h.sw.setFeaturesReply(h.featuresReply);
                h.sw.setConnected(true);
                h.sw.setChannel(h.channel);
                h.sw.setFloodlightProvider(h.controller);
                h.sw.setThreadPoolService(h.controller.getThreadPoolService());
                try {
                    h.sw.setDebugCounterService(h.controller.getDebugCounter());
                } catch (CounterException e) {
                    h.counters.switchCounterRegistrationFailed
                            .updateCounterNoFlush();
                    log.warn("Could not register counters for switch {} ",
                              h.getSwitchInfoString(), e);
                }
                h.sw.setAccessFlowPriority(h.controller.getAccessFlowPriority());
                h.sw.setCoreFlowPriority(h.controller.getCoreFlowPriority());
                for (OFPortStatus ps: h.pendingPortStatusMsg)
                    handlePortStatusMessage(h, ps, false);
                h.pendingPortStatusMsg.clear();
                h.readPropertyFromStorage();
                log.info("Switch {} bound to class {}, writeThrottle={}," +
                        " description {}",
                         new Object[] { h.sw, h.sw.getClass(),
                                        h.sw.isWriteThrottleEnabled(),
                                    description });
                h.sw.startDriverHandshake();
                if (h.sw.isDriverHandshakeComplete())
                    h.gotoWaitInitialRoleState();
                else
                    h.setState(WAIT_SWITCH_DRIVER_SUB_HANDSHAKE);
            }

            @Override
            void processOFError(OFChannelHandler h, OFError m) {
                logErrorDisconnect(h, m);
            }

            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                // TODO: we could re-set the features reply
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                h.pendingPortStatusMsg.add(m);
            }
        },

        WAIT_SWITCH_DRIVER_SUB_HANDSHAKE(false) {
            @Override
            void processOFError(OFChannelHandler h, OFError m)
                    throws IOException {
                // will never be called. We override processOFMessage
            }

            @Override
            void processOFMessage(OFChannelHandler h, OFMessage m)
                    throws IOException {
                if (m.getType() == OFType.ECHO_REQUEST)
                    processOFEchoRequest(h, (OFEchoRequest)m);
                else {
                    // FIXME: other message to handle here?
                    h.sw.processDriverHandshakeMessage(m);
                    if (h.sw.isDriverHandshakeComplete()) {
                        h.gotoWaitInitialRoleState();
                    }
                }
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                handlePortStatusMessage(h, m, false);
            }
        },

        /**
         * We are waiting for the intial role reply message (or error
         * indication) from the switch.
         * Next State: MASTER or SLAVE
         */
        WAIT_INITIAL_ROLE(false) {
            @Override
            void processOFError(OFChannelHandler h, OFError m) {
                // role changer will ignore the error if it isn't for it
                boolean didHandle = h.roleChanger.deliverError(m);
                if (!didHandle) {
                    logError(h, m);
                }
            }

            @Override
            void processOFVendor(OFChannelHandler h, OFVendor m)
                    throws IOException {
                Role role = extractNiciraRoleReply(h, m);
                // If role == null it measn the message wasn't really a
                // Nicira role reply. We ignore this case.
                if (role != null)
                    h.roleChanger.deliverRoleReply(m.getXid(), role);
                else
                    unhandledMessageReceived(h, m);
            }

            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                // TODO: we could re-set the features reply
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply m) {
                illegalMessageReceived(h, m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                handlePortStatusMessage(h, m, false);

            }
        },

        /**
         * The switch is in MASTER role. We enter this state after a role
         * reply from the switch is received (or the controller is MASTER
         * and the switch doesn't support roles). The handshake is complete at
         * this point. We only leave this state if the switch disconnects or
         * if we send a role request for SLAVE /and/ receive the role reply for
         * SLAVE.
         */
        MASTER(true) {
            @LogMessageDoc(level="WARN",
                message="Received permission error from switch {} while" +
                         "being master. Reasserting master role.",
                explanation="The switch has denied an operation likely " +
                         "indicating inconsistent controller roles",
                recommendation="This situation can occurs transiently during role" +
                 " changes. If, however, the condition persists or happens" +
                 " frequently this indicates a role inconsistency. " +
                 LogMessageDoc.CHECK_CONTROLLER )
            @Override
            void processOFError(OFChannelHandler h, OFError m)
                    throws IOException {
                // role changer will ignore the error if it isn't for it
                boolean didHandle = h.roleChanger.deliverError(m);
                if (didHandle)
                    return;
                if (m.getErrorType() ==
                        OFErrorType.OFPET_BAD_REQUEST.getValue() &&
                   m.getErrorCode() ==
                        OFBadRequestCode.OFPBRC_EPERM.ordinal()) {
                    // We are the master controller and the switch returned
                    // a permission error. This is a likely indicator that
                    // the switch thinks we are slave. Reassert our
                    // role
                    // FIXME: this could be really bad during role transitions
                    // if two controllers are master (even if its only for
                    // a brief period). We might need to see if these errors
                    // persist before we reassert
                    h.counters.epermErrorWhileSwitchIsMaster.updateCounterWithFlush();
                    log.warn("Received permission error from switch {} while" +
                             "being master. Reasserting master role.",
                             h.getSwitchInfoString());
                    h.controller.reassertRole(h, Role.MASTER);
                }
                else if (m.getErrorType() ==
                        OFErrorType.OFPET_PORT_MOD_FAILED.getValue() &&
                    m.getErrorCode() ==
                        OFFlowModFailedCode.OFPFMFC_ALL_TABLES_FULL.ordinal()) {
                    h.sw.setTableFull(true);
                }
                else {
                    logError(h, m);
                }
                h.dispatchMessage(m);
            }

            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply m) {
                h.sw.deliverStatisticsReply(m);
            }

            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                h.sw.setFeaturesReply(m);
                h.sw.deliverOFFeaturesReply(m);
            }

            @Override
            void processOFVendor(OFChannelHandler h, OFVendor m)
                    throws IOException {
                Role role = extractNiciraRoleReply(h, m);
                // If role == null it means the message wasn't really a
                // Nicira role reply. We ignore just dispatch it to the
                // OFMessage listenersa in this case.
                if (role != null)
                    h.roleChanger.deliverRoleReply(m.getXid(), role);
                else
                    h.dispatchMessage(m);
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                handlePortStatusMessage(h, m, true);
            }

            @Override
            void processOFPacketIn(OFChannelHandler h, OFPacketIn m) throws IOException {
                h.dispatchMessage(m);
            }

            @Override
            void processOFFlowRemoved(OFChannelHandler h,
                                      OFFlowRemoved m) throws IOException {
                h.dispatchMessage(m);
            }
            @Override
            void processOFBarrierReply(OFChannelHandler h, OFBarrierReply m) throws IOException{
                h.dispatchMessage(m);
            }
        },

        /**
         * The switch is in SLAVE role. We enter this state after a role
         * reply from the switch is received. The handshake is complete at
         * this point. We only leave this state if the switch disconnects or
         * if we send a role request for MASTER /and/ receive the role reply for
         * MASTER.
         * TODO: CURRENTLY, WE DO NOT DISPATCH ANY MESSAGE IN SLAVE.
         */
        SLAVE(true) {
            @Override
            void processOFError(OFChannelHandler h, OFError m)
                    throws IOException {
                // role changer will ignore the error if it isn't for it
                boolean didHandle = h.roleChanger.deliverError(m);
                if (!didHandle) {
                    logError(h, m);
                }
            }



            @Override
            void processOFStatisticsReply(OFChannelHandler h,
                                          OFStatisticsReply m) {
                // FIXME.
                h.sw.deliverStatisticsReply(m);
            }

            @Override
            void processOFVendor(OFChannelHandler h, OFVendor m)
                    throws IOException {
                Role role = extractNiciraRoleReply(h, m);
                // If role == null it means the message wasn't really a
                // Nicira role reply. We ignore it.
                if (role != null)
                    h.roleChanger.deliverRoleReply(m.getXid(), role);
                else
                    unhandledMessageReceived(h, m);
            }

            @Override
            void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                    throws IOException {
                // do nothing
            }

            @Override
            void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                    throws IOException {
                // do nothing
            }

            @Override
            @LogMessageDoc(level="WARN",
                message="Received PacketIn from switch {} while" +
                         "being slave. Reasserting slave role.",
                explanation="The switch has receive a PacketIn despite being " +
                         "in slave role indicating inconsistent controller roles",
                recommendation="This situation can occurs transiently during role" +
                         " changes. If, however, the condition persists or happens" +
                         " frequently this indicates a role inconsistency. " +
                         LogMessageDoc.CHECK_CONTROLLER )
            void processOFPacketIn(OFChannelHandler h, OFPacketIn m) throws IOException {
                // we don't expect packetIn while slave, reassert we are slave
                h.counters.packetInWhileSwitchIsSlave.updateCounterNoFlush();
                log.warn("Received PacketIn from switch {} while" +
                         "being slave. Reasserting slave role.", h.sw);
                h.controller.reassertRole(h, Role.SLAVE);
            }
        };

        private final boolean handshakeComplete;
        ChannelState(boolean handshakeComplete) {
            this.handshakeComplete = handshakeComplete;
        }

        /**
         * Is this a state in which the handshake has completed?
         * @return true if the handshake is complete
         */
        public boolean isHandshakeComplete() {
            return handshakeComplete;
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
        protected String getSwitchStateMessage(OFChannelHandler h,
                                                      OFMessage m,
                                                      String details) {
            return String.format("Switch: [%s], State: [%s], received: [%s]"
                                 + ", details: %s",
                                 h.getSwitchInfoString(),
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
        protected void illegalMessageReceived(OFChannelHandler h, OFMessage m) {
            String msg = getSwitchStateMessage(h, m,
                    "Switch should never send this message in the current state");
            throw new SwitchStateException(msg);

        }

        /**
         * We have an OFMessage we didn't expect given the current state and
         * we want to ignore the message
         * @param h the channel handler the received the message
         * @param m the message
         */
        protected void unhandledMessageReceived(OFChannelHandler h,
                                                OFMessage m) {
            h.counters.unhandledMessage.updateCounterNoFlush();
            if (log.isDebugEnabled()) {
                String msg = getSwitchStateMessage(h, m,
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
        protected void logError(OFChannelHandler h, OFError error) {
            log.error("{} from switch {} in state {}",
                      new Object[] {
                          getErrorString(error),
                          h.getSwitchInfoString(),
                          this.toString()});
        }

        /**
         * Log an OpenFlow error message from a switch and disconnect the
         * channel
         * @param sw The switch that sent the error
         * @param error The error message
         */
        protected void logErrorDisconnect(OFChannelHandler h, OFError error) {
            logError(h, error);
            h.channel.disconnect();
        }


        /**
         * Extract the role from an OFVendor message.
         *
         * Extract the role from an OFVendor message if the message is a
         * Nicira role reply. Otherwise return null.
         *
         * @param h The channel handler receiving the message
         * @param vendorMessage The vendor message to parse.
         * @return The role in the message if the message is a Nicira role
         * reply, null otherwise.
         * @throws SwitchStateException If the message is a Nicira role reply
         * but the numeric role value is unknown.
         * FIXME: The message parser should make sure that the Nicira role is
         * actually valid. Why do we need to take care of it ?!?
         */
        protected Role extractNiciraRoleReply(OFChannelHandler h,
                                              OFVendor vendorMessage) {
            int vendor = vendorMessage.getVendor();
            if (vendor != OFNiciraVendorData.NX_VENDOR_ID)
                return null;
            if (! (vendorMessage.getVendorData() instanceof OFRoleReplyVendorData))
                return null;
            OFRoleReplyVendorData roleReplyVendorData =
                    (OFRoleReplyVendorData) vendorMessage.getVendorData();
            Role role = Role.fromNxRole(roleReplyVendorData.getRole());
            if (role == null) {
                String msg = String.format("Switch: [%s], State: [%s], "
                        + "received NX_ROLE_REPLY with invalid role "
                        + "value %d",
                        h.getSwitchInfoString(),
                        this.toString(),
                        roleReplyVendorData.getRole());
                throw new SwitchStateException(msg);
            }
            return role;
        }

        /**
         * Handle a port status message.
         *
         * Handle a port status message by updating the port maps in the
         * IOFSwitch instance and notifying Controller about the change so
         * it can dispatch a switch update.
         *
         * @param h The OFChannelHhandler that received the message
         * @param m The PortStatus message we received
         * @param doNotify if true switch port changed events will be
         * dispatched
         */
        protected void handlePortStatusMessage(OFChannelHandler h,
                                               OFPortStatus m,
                                               boolean doNotify) {
            if (h.sw == null) {
                String msg = getSwitchStateMessage(h, m,
                        "State machine error: switch is null. Should never " +
                        "happen");
                throw new SwitchStateException(msg);
            }
            Collection<PortChangeEvent> changes = h.sw.processOFPortStatus(m);
            if (doNotify) {
                for (PortChangeEvent ev: changes)
                    h.controller.notifyPortChanged(h.sw, ev.port, ev.type);
            }
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
        void processOFMessage(OFChannelHandler h, OFMessage m) throws IOException {
            h.roleChanger.checkTimeout();
            switch(m.getType()) {
                case HELLO:
                    processOFHello(h, (OFHello)m);
                    break;
                case BARRIER_REPLY:
                    processOFBarrierReply(h, (OFBarrierReply)m);
                    break;
                case ECHO_REPLY:
                    processOFEchoReply(h, (OFEchoReply)m);
                    break;
                case ECHO_REQUEST:
                    processOFEchoRequest(h, (OFEchoRequest)m);
                    break;
                case ERROR:
                    processOFError(h, (OFError)m);
                    break;
                case FEATURES_REPLY:
                    processOFFeaturesReply(h, (OFFeaturesReply)m);
                    break;
                case FLOW_REMOVED:
                    processOFFlowRemoved(h, (OFFlowRemoved)m);
                    break;
                case GET_CONFIG_REPLY:
                    processOFGetConfigReply(h, (OFGetConfigReply)m);
                    break;
                case PACKET_IN:
                    processOFPacketIn(h, (OFPacketIn)m);
                    break;
                case PORT_STATUS:
                    processOFPortStatus(h, (OFPortStatus)m);
                    break;
                case QUEUE_GET_CONFIG_REPLY:
                    processOFQueueGetConfigReply(h, (OFQueueGetConfigReply)m);
                    break;
                case STATS_REPLY:
                    processOFStatisticsReply(h, (OFStatisticsReply)m);
                    break;
                case VENDOR:
                    processOFVendor(h, (OFVendor)m);
                    break;
                // The following messages are sent to switches. The controller
                // should never receive them
                case SET_CONFIG:
                case GET_CONFIG_REQUEST:
                case PACKET_OUT:
                case PORT_MOD:
                case QUEUE_GET_CONFIG_REQUEST:
                case BARRIER_REQUEST:
                case STATS_REQUEST:
                case FEATURES_REQUEST:
                case FLOW_MOD:
                    illegalMessageReceived(h, m);
                    break;
            }
        }

        /*-----------------------------------------------------------------
         * Default implementation for message handlers in any state.
         *
         * Individual states must override these if they want a behavior
         * that differs from the default.
         *
         * In general, these handlers simply ignore the message and do
         * nothing.
         *
         * There are some exceptions though, since some messages really
         * are handled the same way in every state (e.g., ECHO_REQUST) or
         * that are only valid in a single state (e.g., HELLO, GET_CONFIG_REPLY
         -----------------------------------------------------------------*/

        void processOFHello(OFChannelHandler h, OFHello m) throws IOException {
            // we only expect hello in the WAIT_HELLO state
            illegalMessageReceived(h, m);
        }

        void processOFBarrierReply(OFChannelHandler h, OFBarrierReply m)
                throws IOException {
            // Silently ignore.
        }

        void processOFEchoRequest(OFChannelHandler h, OFEchoRequest m)
            throws IOException {
            OFEchoReply reply = (OFEchoReply)
                    BasicFactory.getInstance().getMessage(OFType.ECHO_REPLY);
            reply.setXid(m.getXid());
            reply.setPayload(m.getPayload());
            reply.setLengthU(m.getLengthU());
            h.channel.write(Collections.singletonList(reply));
        }

        void processOFEchoReply(OFChannelHandler h, OFEchoReply m)
            throws IOException {
            // Do nothing with EchoReplies !!
        }

        // no default implementation for OFError
        // every state must override it
        abstract void processOFError(OFChannelHandler h, OFError m)
                throws IOException;


        void processOFFeaturesReply(OFChannelHandler h, OFFeaturesReply  m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFFlowRemoved(OFChannelHandler h, OFFlowRemoved m)
            throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFGetConfigReply(OFChannelHandler h, OFGetConfigReply m)
                throws IOException {
            // we only expect config replies in the WAIT_CONFIG_REPLY state
            // TODO: might use two different strategies depending on whether
            // we got a miss length of 64k or not.
            illegalMessageReceived(h, m);
        }

        void processOFPacketIn(OFChannelHandler h, OFPacketIn m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        // bi default implementation. Every state needs to handle it.
        abstract void processOFPortStatus(OFChannelHandler h, OFPortStatus m)
                throws IOException;

        void processOFQueueGetConfigReply(OFChannelHandler h,
                                          OFQueueGetConfigReply m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFStatisticsReply(OFChannelHandler h, OFStatisticsReply m)
                throws IOException {
            unhandledMessageReceived(h, m);
        }

        void processOFVendor(OFChannelHandler h, OFVendor m)
                throws IOException {
            // TODO: it might make sense to parse the vendor message here
            // into the known vendor messages we support and then call more
            // spefic event handlers
            unhandledMessageReceived(h, m);
        }
    }


    /**
     * Create a new unconnecte OFChannelHandler.
     * @param controller
     */
    OFChannelHandler(Controller controller) {
        this.controller = controller;
        this.counters = controller.getCounters();
        this.roleChanger = new RoleChanger(DEFAULT_ROLE_TIMEOUT_MS);
        this.state = ChannelState.INIT;
        this.pendingPortStatusMsg = new ArrayList<OFPortStatus>();
    }

    /**
     * Is this a state in which the handshake has completed?
     * @return true if the handshake is complete
     */
    boolean isHandshakeComplete() {
        return this.state.isHandshakeComplete();
    }

    /**
     * Forwards to RoleChanger. See there.
     * @param role
     */
    void sendRoleRequestIfNotPending(Role role) {
        try {
            roleChanger.sendRoleRequestIfNotPending(role);
        } catch (IOException e) {
             log.error("Disconnecting switch {} due to IO Error: {}",
                              getSwitchInfoString(), e.getMessage());
             channel.close();
        }
    }

    /**
     * Forwards to RoleChanger. See there.
     * @param role
     */
    void sendRoleRequest(Role role) {
        try {
            roleChanger.sendRoleRequest(role);
        } catch (IOException e) {
             log.error("Disconnecting switch {} due to IO Error: {}",
                              getSwitchInfoString(), e.getMessage());
             channel.close();
        }
    }


    @Override
    @LogMessageDoc(message="New switch connection from {ip address}",
                   explanation="A new switch has connected from the " +
                            "specified IP address")
    public void channelConnected(ChannelHandlerContext ctx,
                                 ChannelStateEvent e) throws Exception {
        counters.switchConnected.updateCounterWithFlush();
        channel = e.getChannel();
        log.info("New switch connection from {}",
                 channel.getRemoteAddress());
        sendHandShakeMessage(OFType.HELLO);
        setState(ChannelState.WAIT_HELLO);
    }

    @Override
    @LogMessageDoc(message="Disconnected switch {switch information}",
                   explanation="The specified switch has disconnected.")
    public void channelDisconnected(ChannelHandlerContext ctx,
                                    ChannelStateEvent e) throws Exception {
        controller.removeSwitchChannel(this);
        if (this.sw != null) {
            // TODO: switchDisconnected() will check if we've previously
            // activated the switch. Nevertheless, we might want to check
            // here as well.
            controller.switchDisconnected(this.sw);
            this.sw.setConnected(false);
        }

        log.info("Disconnected switch {}", getSwitchInfoString());
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
            // switch timeout
            log.error("Disconnecting switch {} due to read timeout",
                                 getSwitchInfoString());
            counters.switchDisconnectReadTimeout.updateCounterWithFlush();
            ctx.getChannel().close();
        } else if (e.getCause() instanceof HandshakeTimeoutException) {
            log.error("Disconnecting switch {}: failed to complete handshake",
                      getSwitchInfoString());
            counters.switchDisconnectHandshakeTimeout.updateCounterWithFlush();
            ctx.getChannel().close();
        } else if (e.getCause() instanceof ClosedChannelException) {
            log.debug("Channel for sw {} already closed", getSwitchInfoString());
        } else if (e.getCause() instanceof IOException) {
            log.error("Disconnecting switch {} due to IO Error: {}",
                      getSwitchInfoString(), e.getCause().getMessage());
            if (log.isDebugEnabled()) {
                // still print stack trace if debug is enabled
                log.debug("StackTrace for previous Exception: ", e.getCause());
            }
            counters.switchDisconnectIOError.updateCounterWithFlush();
            ctx.getChannel().close();
        } else if (e.getCause() instanceof SwitchStateException) {
            log.error("Disconnecting switch {} due to switch state error: {}",
                      getSwitchInfoString(), e.getCause().getMessage());
            if (log.isDebugEnabled()) {
                // still print stack trace if debug is enabled
                log.debug("StackTrace for previous Exception: ", e.getCause());
            }
            counters.switchDisconnectSwitchStateException.updateCounterWithFlush();
            ctx.getChannel().close();
        } else if (e.getCause() instanceof MessageParseException) {
            log.error("Disconnecting switch "
                                 + getSwitchInfoString() +
                                 " due to message parse failure",
                                 e.getCause());
            counters.switchDisconnectParseError.updateCounterWithFlush();
            ctx.getChannel().close();
        } else if (e.getCause() instanceof StorageException) {
            log.error("Terminating controller due to storage exception",
                      e.getCause());
            this.controller.terminate();
        } else if (e.getCause() instanceof RejectedExecutionException) {
            log.warn("Could not process message: queue full");
            counters.rejectedExecutionException.updateCounterWithFlush();
        } else {
            log.error("Error while processing message from switch "
                                 + getSwitchInfoString()
                                 + "state " + this.state, e.getCause());
            counters.switchDisconnectOtherException.updateCounterWithFlush();
            ctx.getChannel().close();
        }
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
            throws Exception {
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.ECHO_REQUEST);
        e.getChannel().write(Collections.singletonList(m));
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        if (e.getMessage() instanceof List) {
            @SuppressWarnings("unchecked")
            List<OFMessage> msglist = (List<OFMessage>)e.getMessage();

            LoadMonitor.LoadLevel loadlevel;
            int packets_dropped = 0;
            int packets_allowed = 0;
            int lldps_allowed = 0;

            if (this.controller.overload_drop) {
                loadlevel = this.controller.loadmonitor.getLoadLevel();
            }
            else {
                loadlevel = LoadMonitor.LoadLevel.OK;
            }

            for (OFMessage ofm : msglist) {
                counters.messageReceived.updateCounterNoFlush();
                // Per-switch input throttling
                if (sw != null && sw.inputThrottled(ofm)) {
                    counters.messageInputThrottled.updateCounterNoFlush();
                    continue;
                }
                try {
                    if (this.controller.overload_drop &&
                        !loadlevel.equals(LoadMonitor.LoadLevel.OK)) {
                        switch (ofm.getType()) {
                        case PACKET_IN:
                            switch (loadlevel) {
                            case VERYHIGH:
                                // Drop all packet-ins, including LLDP/BDDPs
                                packets_dropped++;
                                continue;
                            case HIGH:
                                // Drop all packet-ins, except LLDP/BDDPs
                                byte[] data = ((OFPacketIn)ofm).getPacketData();
                                if (data.length > 14) {
                                    if (((data[12] == (byte)0x88) &&
                                         (data[13] == (byte)0xcc)) ||
                                        ((data[12] == (byte)0x89) &&
                                         (data[13] == (byte)0x42))) {
                                        lldps_allowed++;
                                        packets_allowed++;
                                        break;
                                    }
                                }
                                packets_dropped++;
                                continue;
                            default:
                                // Load not high, go ahead and process msg
                                packets_allowed++;
                                break;
                            }
                            break;
                        default:
                            // Process all non-packet-ins
                            packets_allowed++;
                            break;
                        }
                    }

                    // Do the actual packet processing
                    state.processOFMessage(this, ofm);

                }
                catch (Exception ex) {
                    // We are the last handler in the stream, so run the
                    // exception through the channel again by passing in
                    // ctx.getChannel().
                    Channels.fireExceptionCaught(ctx.getChannel(), ex);
                }
            }

            if (loadlevel != LoadMonitor.LoadLevel.OK) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "Overload: Detected {}, packets dropped={}",
                        loadlevel.toString(), packets_dropped);
                    log.debug(
                        "Overload: Packets allowed={} (LLDP/BDDPs allowed={})",
                        packets_allowed, lldps_allowed);
                }
            }
            // Flush all thread local queues etc. generated by this train
            // of messages.
            this.controller.flushAll();
        }
        else {
            Channels.fireExceptionCaught(ctx.getChannel(),
                                         new AssertionError("Message received from Channel is not a list"));
        }
    }


    /**
     * Get a useable error string from the OFError.
     * @param error
     * @return
     */
    public static String getErrorString(OFError error) {
        // TODO: this really should be OFError.toString. Sigh.
        int etint = 0xffff & error.getErrorType();
        if (etint < 0 || etint >= OFErrorType.values().length) {
            return String.format("Unknown error type %d", etint);
        }
        OFErrorType et = OFErrorType.values()[etint];
        switch (et) {
            case OFPET_HELLO_FAILED:
                OFHelloFailedCode hfc =
                    OFHelloFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, hfc);
            case OFPET_BAD_REQUEST:
                OFBadRequestCode brc =
                    OFBadRequestCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, brc);
            case OFPET_BAD_ACTION:
                OFBadActionCode bac =
                    OFBadActionCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, bac);
            case OFPET_FLOW_MOD_FAILED:
                OFFlowModFailedCode fmfc =
                    OFFlowModFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, fmfc);
            case OFPET_PORT_MOD_FAILED:
                OFPortModFailedCode pmfc =
                    OFPortModFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, pmfc);
            case OFPET_QUEUE_OP_FAILED:
                OFQueueOpFailedCode qofc =
                    OFQueueOpFailedCode.values()[0xffff & error.getErrorCode()];
                return String.format("Error %s %s", et, qofc);
            case OFPET_VENDOR_ERROR:
                // no codes known for vendor error
                return String.format("Error %s", et);
        }
        return null;
    }

    private void dispatchMessage(OFMessage m) throws IOException {
        // handleMessage will count
        this.controller.handleMessage(this.sw, m, null);
    }

    /**
     * Return a string describing this switch based on the already available
     * information (DPID and/or remote socket)
     * @return
     */
    private String getSwitchInfoString() {
        if (sw != null)
            return sw.toString();
        String channelString;
        if (channel == null || channel.getRemoteAddress() == null) {
            channelString = "?";
        } else {
            channelString = channel.getRemoteAddress().toString();
        }
        String dpidString;
        if (featuresReply == null) {
            dpidString = "?";
        } else {
            dpidString = HexString.toHexString(featuresReply.getDatapathId());
        }
        return String.format("[%s DPID[%s]]", channelString, dpidString);
    }

    /**
     * Update the channels state. Only called from the state machine.
     * TODO: enforce restricted state transitions
     * @param state
     */
    private void setState(ChannelState state) {
        this.state = state;
    }

    /**
     * Send a message to the switch using the handshake transactions ids.
     * @throws IOException
     */
    private void sendHandShakeMessage(OFType type) throws IOException {
        // Send initial Features Request
        OFMessage m = BasicFactory.getInstance().getMessage(type);
        m.setXid(handshakeTransactionIds--);
        channel.write(Collections.singletonList(m));
    }

    /**
     * Send an setL2TableSet message to the switch.
     */
    private void sendHandshakeL2TableSet() {
        OFVendor l2TableSet = (OFVendor)
                BasicFactory.getInstance().getMessage(OFType.VENDOR);
        l2TableSet.setXid(handshakeTransactionIds--);
        OFBsnL2TableSetVendorData l2TableSetData =
                new OFBsnL2TableSetVendorData(true,
                                              controller.getCoreFlowPriority());
        l2TableSet.setVendor(OFBigSwitchVendorData.BSN_VENDOR_ID);
        l2TableSet.setVendorData(l2TableSetData);
        l2TableSet.setLengthU(OFVendor.MINIMUM_LENGTH +
                              l2TableSetData.getLength());
        channel.write(Collections.singletonList(l2TableSet));
    }


    private void gotoWaitInitialRoleState() {
        // We need to set the new state /before/ we call addSwitchChannel
        // because addSwitchChannel will eventually call setRole
        // which can in turn decide that the switch doesn't support
        // roles and transition the state straight to MASTER.
        setState(ChannelState.WAIT_INITIAL_ROLE);
        controller.addSwitchChannelAndSendInitialRole(this);
    }

    /**
     * Send the configuration requests to tell the switch we want full
     * packets
     * @throws IOException
     */
    private void sendHandshakeSetConfig() throws IOException {
        List<OFMessage> msglist = new ArrayList<OFMessage>(3);

        // Ensure we receive the full packet via PacketIn
        // FIXME: We don't set the reassembly flags.
        OFSetConfig configSet = (OFSetConfig) BasicFactory.getInstance()
                .getMessage(OFType.SET_CONFIG);
        configSet.setMissSendLength((short) 0xffff)
            .setLengthU(OFSwitchConfig.MINIMUM_LENGTH);
        configSet.setXid(handshakeTransactionIds--);
        msglist.add(configSet);

        // Barrier
        OFBarrierRequest barrier = (OFBarrierRequest) BasicFactory.getInstance()
                .getMessage(OFType.BARRIER_REQUEST);
        barrier.setXid(handshakeTransactionIds--);
        msglist.add(barrier);

        // Verify (need barrier?)
        OFGetConfigRequest configReq = (OFGetConfigRequest)
                BasicFactory.getInstance().getMessage(OFType.GET_CONFIG_REQUEST);
        configReq.setXid(handshakeTransactionIds--);
        msglist.add(configReq);
        channel.write(msglist);
    }

    /**
     * send a description state request
     * @throws IOException
     */
    private void sendHandshakeDescriptionStatsRequest() throws IOException {
        // Get Description to set switch-specific flags
        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.DESC);
        req.setXid(handshakeTransactionIds--);

        channel.write(Collections.singletonList(req));
    }


    /**
     * Read switch properties from storage and set switch attributes accordingly
     */
    private void readPropertyFromStorage() {
        // At this time, also set other switch properties from storage
        boolean is_core_switch = false;
        IResultSet resultSet = null;
        try {
            String swid = sw.getStringId();
            resultSet = this.controller.getStorageSourceService()
                    .getRow(Controller.SWITCH_CONFIG_TABLE_NAME, swid);
            for (Iterator<IResultSet> it =
                    resultSet.iterator(); it.hasNext();) {
                is_core_switch = it.next()
                        .getBoolean(Controller.SWITCH_CONFIG_CORE_SWITCH);
                if (log.isDebugEnabled()) {
                    log.debug("Reading SWITCH_IS_CORE_SWITCH " +
                            "config for switch={}, is-core={}",
                            sw, is_core_switch);
                }
            }
        }
        finally {
            if (resultSet != null)
                resultSet.close();
        }
        if (is_core_switch) {
            sw.setAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH,
                            Boolean.valueOf(true));
        }
    }

    ChannelState getStateForTesting() {
        return state;
    }

    void useRoleChangerWithOtherTimeoutForTesting(long roleTimeoutMs) {
        roleChanger = new RoleChanger(roleTimeoutMs);
    }
}
