/**
 *    Copyright 2012, Big Switch Networks, Inc.
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

package net.floodlightcontroller.core;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.util.Date;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.core.internal.IOFConnectionListener;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReplyFlags;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Implementation of an openflow connection to switch. Encapsulates a
 * {@link Channel}, and provides message write and request/response handling
 * capabilities.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class OFConnection implements IOFConnection, IOFConnectionBackend{
    private static final Logger logger = LoggerFactory.getLogger(OFConnection.class);
    private final DatapathId dpid;
    private final OFFactory factory;
    private final Channel channel;
    private final OFAuxId auxId;
    private final Timer timer;

    private final Date connectedSince;

    private final Map<Long, Deliverable<?>> xidDeliverableMap;

    protected final static ThreadLocal<List<OFMessage>> localMsgBuffer =
            new ThreadLocal<List<OFMessage>>();

    private static final long DELIVERABLE_TIME_OUT = 60;
    private static final TimeUnit DELIVERABLE_TIME_OUT_UNIT = TimeUnit.SECONDS;


    private final OFConnectionCounters counters;
    private IOFConnectionListener listener;

    public OFConnection(@Nonnull DatapathId dpid,
                        @Nonnull OFFactory factory,
                        @Nonnull Channel channel,
                        @Nonnull OFAuxId auxId,
                        @Nonnull IDebugCounterService debugCounters,
                        @Nonnull Timer timer) {
        Preconditions.checkNotNull(dpid, "dpid");
        Preconditions.checkNotNull(factory, "factory");
        Preconditions.checkNotNull(channel, "channel");
        Preconditions.checkNotNull(timer, "timer");
        Preconditions.checkNotNull(debugCounters);

        this.listener = NullConnectionListener.INSTANCE;
        this.dpid = dpid;
        this.factory = factory;
        this.channel = channel;
        this.auxId = auxId;
        this.connectedSince = new Date();
        this.xidDeliverableMap = new ConcurrentHashMap<>();
        this.counters = new OFConnectionCounters(debugCounters, dpid, this.auxId);
        this.timer = timer;
    }

    @Override
    public void write(OFMessage m) {
        if (!isConnected()) {
            if (logger.isDebugEnabled())
                logger.debug("{}: not connected - dropping message {}", this, m);
            return;
        }
        if (logger.isDebugEnabled())
            logger.debug("{}: send {}", this, m);
        List<OFMessage> msgBuffer = localMsgBuffer.get();
        if (msgBuffer == null) {
            msgBuffer = new ArrayList<OFMessage>();
            localMsgBuffer.set(msgBuffer);
        }

        counters.updateWriteStats(m);
        msgBuffer.add(m);

        if ((msgBuffer.size() >= Controller.BATCH_MAX_SIZE) || ((m.getType() != OFType.PACKET_OUT) && (m.getType() != OFType.FLOW_MOD))) {
            this.write(msgBuffer);
            localMsgBuffer.set(null);
        }
    }

    @Override
    public <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request) {
        if (!isConnected())
            return Futures.immediateFailedFuture(new SwitchDisconnectedException(getDatapathId()));

        DeliverableListenableFuture<R> future = new DeliverableListenableFuture<R>();
        xidDeliverableMap.put(request.getXid(), future);
        listener.messageWritten(this, request);
        write(request);
        return future;
    }

    @Override
    @LogMessageDoc(level = "WARN",
                   message = "Sending OF message that modifies switch "
                           + "state while in the slave role: {switch}",
                   explanation = "An application has sent a message to a switch "
                           + "that is not valid when the switch is in a slave role",
                   recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG)
    public void write(Iterable<OFMessage> msglist) {
        if (!isConnected()) {
            if (logger.isDebugEnabled())
                logger.debug(this.toString() + " : not connected - dropping {} element msglist {} ",
                        Iterables.size(msglist),
                        String.valueOf(msglist).substring(0, 80));
            return;
        }
        for (OFMessage m : msglist) {
            if (logger.isTraceEnabled())
                logger.trace("{}: send {}", this, m);
            counters.updateWriteStats(m);
        }
        this.channel.write(msglist);
    }

    // Notifies the connection object that the channel has been disconnected
    public void disconnected() {
        SwitchDisconnectedException exception = new SwitchDisconnectedException(getDatapathId());
        for (Long xid : xidDeliverableMap.keySet()) {
            // protect against other mechanisms running at the same time
            // (timeout)
            Deliverable<?> removed = xidDeliverableMap.remove(xid);
            if (removed != null) {
                removed.deliverError(exception);
            }
        }
    }

    @Override
    public void disconnect() {
        this.channel.disconnect();
        this.counters.uninstallCounters();
    }

    @Override
    public String toString() {
        String channelString = (channel != null) ? String.valueOf(channel.getRemoteAddress()): "?";
        return "OFConnection [" + getDatapathId() + "(" + getAuxId() + ")" + "@" + channelString + "]";
    }

    @Override
    public Date getConnectedSince() {
        return connectedSince;
    }

    @Override
    public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(
            OFStatsRequest<REPLY> request) {
        if (!isConnected())
            return Futures.immediateFailedFuture(new SwitchDisconnectedException(getDatapathId()));

        final DeliverableListenableFuture<List<REPLY>> future =
                new DeliverableListenableFuture<List<REPLY>>();

        Deliverable<REPLY> deliverable = new Deliverable<REPLY>() {
            private final List<REPLY> results = Collections
                    .synchronizedList(new ArrayList<REPLY>());

            @Override
            public void deliver(REPLY reply) {
                results.add(reply);
                if (!reply.getFlags().contains(OFStatsReplyFlags.REPLY_MORE)) {
                    // done
                    future.deliver(results);
                }
            }

            @Override
            public void deliverError(Throwable cause) {
                future.deliverError(cause);
            }

            @Override
            public boolean isDone() {
                return future.isDone();
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return future.cancel(mayInterruptIfRunning);
            }
        };

        registerDeliverable(request.getXid(), deliverable);
        this.write(request);
        return future;
    }

    private void registerDeliverable(long xid, Deliverable<?> deliverable) {
        this.xidDeliverableMap.put(xid, deliverable);
        timer.newTimeout(new TimeOutDeliverable(xid), DELIVERABLE_TIME_OUT, DELIVERABLE_TIME_OUT_UNIT);
    }

    public boolean handleGenericDeliverable(OFMessage reply) {
        counters.updateReadStats(reply);
        @SuppressWarnings("unchecked")
        Deliverable<OFMessage> deliverable =
                (Deliverable<OFMessage>) this.xidDeliverableMap.get(reply.getXid());
        if (deliverable != null) {
            if(reply instanceof OFErrorMsg) {
                deliverable.deliverError(new OFErrorMsgException((OFErrorMsg) reply));
            } else {
                deliverable.deliver(reply);
            }
            if (deliverable.isDone())
                this.xidDeliverableMap.remove(reply.getXid());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void cancelAllPendingRequests() {
        /*
         * we don't need to be synchronized here. Even if another thread
         * modifies the map while we're cleaning up the future will eventually
         * timeout
         */
        for (Deliverable<?> d : xidDeliverableMap.values()) {
            d.cancel(true);
        }
        xidDeliverableMap.clear();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public void flush() {
        List<OFMessage> msglist = localMsgBuffer.get();
        if ((msglist != null) && (msglist.size() > 0)) {
            this.write(msglist);
            localMsgBuffer.set(null);
        }
    }

    @Override
    public SocketAddress getRemoteInetAddress() {
        return channel.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalInetAddress() {
        return channel.getLocalAddress();
    }

    public boolean deliverResponse(OFMessage m) {
        if (handleGenericDeliverable(m))
            return true;
        else
            return false;
    }

    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public DatapathId getDatapathId() {
        return dpid;
    }

    @Override
    public OFAuxId getAuxId() {
        return auxId;
    }

    Set<Long> getPendingRequestIds() {
        return ImmutableSet.copyOf(xidDeliverableMap.keySet());
    }

    @Override
    public OFFactory getOFFactory() {
        return this.factory;
    }

    /**
     * Timeout class instantiated for deliverables. Will throw a timeout exception
     * if proper responses are not received in time.
     *
     */
    private class TimeOutDeliverable implements TimerTask {
        private final long xid;

        public TimeOutDeliverable(long xid) {
            this.xid = xid;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            Deliverable<?> removed = xidDeliverableMap.remove(xid);
            if (removed != null && !removed.isDone()) {
                removed.deliverError(new TimeoutException(
                        "timeout - did not receive answer for xid " + xid));
            }

        }
    }

    public IOFConnectionListener getListener() {
        return listener;
    }

    /** set the connection listener
     *  <p>
     *  Note: this is assumed to be called from the Connection's IO Thread.
     *
     * @param listener
     */
    @Override
    public void setListener(IOFConnectionListener listener) {
        this.listener = listener;
    }

    public void messageReceived(OFMessage m) {
        // Check if message was a response for a xid waiting at the switch
        if(!deliverResponse(m)){
            listener.messageReceived(this, m);
        }
    }

    /** A dummy connection listener that just logs warn messages. Saves us a few null checks
     * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
     */
    private static class NullConnectionListener implements IOFConnectionListener {
        public final static NullConnectionListener INSTANCE = new NullConnectionListener();

        private NullConnectionListener() { }

        @Override
        public void connectionClosed(IOFConnectionBackend connection) {
            logger.warn("NullConnectionListener for {} - received connectionClosed", connection);
        }

        @Override
        public void messageReceived(IOFConnectionBackend connection, OFMessage m) {
            logger.warn("NullConnectionListener for {} - received messageReceived: {}", connection, m);
        }

        @Override
        public boolean isSwitchHandshakeComplete(IOFConnectionBackend connection) {
            return false;
        }

		@Override
		public void messageWritten(IOFConnectionBackend connection, OFMessage m) {
			// TODO Auto-generated method stub
			
		}

    }


}
