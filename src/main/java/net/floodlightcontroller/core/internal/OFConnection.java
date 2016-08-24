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

package net.floodlightcontroller.core.internal;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

import io.netty.channel.Channel;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

import java.util.Date;

import net.floodlightcontroller.core.Deliverable;
import net.floodlightcontroller.core.DeliverableListenableFuture;
import net.floodlightcontroller.core.IOFConnection;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.SwitchDisconnectedException;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.util.IterableUtils;
import net.floodlightcontroller.util.OFMessageUtils;

import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReplyFlags;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.U64;
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

	/** CAREFUL CAREFUL CAREFUL:
	 *
	 * Netty4 does not guarantee order of messages that are written into the channel any more.
	 * To ensure messages do not get reordered, never directly call {@link Channel#write(Object)}.
	 *
	 * Instead, use {@link #write(Iterable)}, which queue up write request on the EventLoop,
	 * to make sure they are handled in order.
	 */
	private final Channel channel;

	private final OFAuxId auxId;
	private final Timer timer;

	private final Date connectedSince;

	private final Map<Long, Deliverable<?>> xidDeliverableMap;

	private static final long DELIVERABLE_TIME_OUT = 60;
	private static final TimeUnit DELIVERABLE_TIME_OUT_UNIT = TimeUnit.SECONDS;

	private final OFConnectionCounters counters;
	private IOFConnectionListener listener;

	private volatile U64 latency;

	/**
	 * Used to write messages to ensure order w/Netty4.
	 * It also ensures we do not reuse the array, since
	 * Netty4 will write the object, not the items.
	 */
	private class WriteMessageTask implements Runnable {
		private final Iterable<OFMessage> msglist;

		public WriteMessageTask(Iterable<OFMessage> msglist) {
			this.msglist = msglist;
		}

		@Override
		public void run() {
			for (OFMessage m : msglist) {
				if (logger.isTraceEnabled())
					logger.trace("{}: send {}", this, m);
				counters.updateWriteStats(m);
			}
			channel.writeAndFlush(msglist);
		}
	}

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
		this.latency = U64.ZERO;
	}

	/**
	 * All write methods chain into this write() to use WriteMessageTask.
	 * 
	 * Write the list of messages to the switch
	 * 
	 * @param msgList list of messages to write
	 * @return list of failed messages; can only fail if channel disconnected
	 */
	@Override
	public Collection<OFMessage> write(final Iterable<OFMessage> msgList) {
		if (!isConnected()) {
			if (logger.isDebugEnabled())
				logger.debug(this.toString() + " : not connected - dropping {} element msglist {} ",
						Iterables.size(msgList),
						String.valueOf(msgList).substring(0, 80));
			return IterableUtils.toCollection(msgList);
		}
		for (OFMessage m : msgList) {			
			if (logger.isTraceEnabled()) {
				logger.trace("{}: send {}", this, m);
				counters.updateWriteStats(m);
			}
		}
		this.channel.eventLoop().execute(new WriteMessageTask(msgList));
		return Collections.emptyList();
	}

	/**
	 * Write the single message to the channel
	 * @param m
	 * @return true upon success; false upon failure; can only fail if channel disconnected
	 */
	@Override
	public boolean write(OFMessage m) {
		return this.write(Collections.singletonList(m)).isEmpty();
	}

	@Override
	public <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request) {
		if (!isConnected()) {
			return Futures.immediateFailedFuture(new SwitchDisconnectedException(getDatapathId()));
		}

		DeliverableListenableFuture<R> future = new DeliverableListenableFuture<R>(request);
		xidDeliverableMap.put(request.getXid(), future);
		listener.messageWritten(this, request);
		this.write(request);
		return future;
	}

	@Override
	public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(
			OFStatsRequest<REPLY> request) {
		if (!isConnected()) {
			return Futures.immediateFailedFuture(new SwitchDisconnectedException(getDatapathId()));
		}

		final DeliverableListenableFuture<List<REPLY>> future =
				new DeliverableListenableFuture<List<REPLY>>(request);

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

			@Override
			public OFMessage getRequest() {
				return future.getRequest();
			}
		};

		registerDeliverable(request.getXid(), deliverable);
		this.write(request);
		return future;
	}

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
		String channelString = (channel != null) ? String.valueOf(channel.remoteAddress()): "?";
		return "OFConnection [" + getDatapathId() + "(" + getAuxId() + ")" + "@" + channelString + "]";
	}

	@Override
	public Date getConnectedSince() {
		return connectedSince;
	}

	private void registerDeliverable(long xid, Deliverable<?> deliverable) {
		this.xidDeliverableMap.put(xid, deliverable);
		setDeliverableTimeout(xid);
	}

	private void setDeliverableTimeout(long xid) {
		timer.newTimeout(new TimeOutDeliverable(xid), DELIVERABLE_TIME_OUT, DELIVERABLE_TIME_OUT_UNIT);
	}

	public boolean handleGenericDeliverable(OFMessage reply) {
		counters.updateReadStats(reply);
		@SuppressWarnings("unchecked")
		Deliverable<OFMessage> deliverable =
		(Deliverable<OFMessage>) this.xidDeliverableMap.get(reply.getXid());
		if (deliverable != null) {
			boolean validReply = true;
			if(reply instanceof OFErrorMsg) {
				deliverable.deliverError(new OFErrorMsgException((OFErrorMsg) reply));
			} else {
				if (OFMessageUtils.isReplyForRequest(deliverable.getRequest(), reply)) {
					deliverable.deliver(reply);
				} else {
					validReply = false;
					setDeliverableTimeout(reply.getXid());
				}
			}
			if (validReply && deliverable.isDone())
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
		return channel.isActive();
	}

	@Override
	public SocketAddress getRemoteInetAddress() {
		return channel.remoteAddress();
	}

	@Override
	public SocketAddress getLocalInetAddress() {
		return channel.localAddress();
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

	@Override
	public U64 getLatency() {
		return this.latency;
	}

	@Override
	public void updateLatency(U64 latency) {
		if (latency == null) {
			logger.error("Latency must be non-null. Ignoring null latency value.");
			return;
		} else if (this.latency.equals(U64.ZERO)) { 
			logger.debug("Recording previously 0ms switch {} latency as {}ms", this.getDatapathId(), latency.getValue());
			this.latency = latency;
			return;
		} else {
			double oldWeight = 0.30;
			this.latency = U64.of((long) (this.latency.getValue() * oldWeight + latency.getValue() * (1 - oldWeight)));
			logger.debug("Switch {} latency updated to {}ms", this.getDatapathId(), this.latency.getValue());
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