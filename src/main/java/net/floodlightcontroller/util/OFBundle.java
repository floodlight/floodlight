package net.floodlightcontroller.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.LogicalOFMessageCategory;

import org.projectfloodlight.openflow.protocol.OFBundleAddMsg;
import org.projectfloodlight.openflow.protocol.OFBundleCtrlMsg;
import org.projectfloodlight.openflow.protocol.OFBundleCtrlType;
import org.projectfloodlight.openflow.protocol.OFBundleFlags;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.BundleId;
import org.python.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class OFBundle {

	public static final Set<OFBundleFlags> ALL_BUNDLE_FLAGS = Sets
			.immutableEnumSet(OFBundleFlags.ATOMIC, OFBundleFlags.ORDERED);

	public static final Set<OFBundleFlags> ATOMIC_BUNDLE_FLAGS = Sets
			.immutableEnumSet(OFBundleFlags.ATOMIC);

	public static final Set<OFBundleFlags> ORDERED_BUNDLE_FLAGS = Sets
			.immutableEnumSet(OFBundleFlags.ORDERED);

	public static final Set<OFBundleFlags> NO_FLAGS = Collections.emptySet();

	/**
	 * 
	 * @param factory
	 * @param type
	 * @return
	 */
	public static OFBundleCtrlMsg createBundleCtrlMsg(OFFactory factory,
			OFBundleCtrlType type, int id, Set<OFBundleFlags> flags) {
		return factory.buildBundleCtrlMsg().setBundleCtrlType(type)
				.setBundleId(BundleId.of(id)).setFlags(flags).build();
	}

	public static OFBundleAddMsg createBundleAddMsg(OFFactory factory, int id,
			Set<OFBundleFlags> flags, OFMessage msg) {
		return factory.buildBundleAddMsg().setBundleId(BundleId.of(id))
				.setFlags(flags).setData(msg).setXid(msg.getXid()).build();
	}

	protected static Logger log = LoggerFactory.getLogger(OFBundle.class);

	private static AtomicInteger currentBundleId = new AtomicInteger(0);

	private final int id;
	private final Set<OFBundleFlags> flags;
	private List<OFMessage> bundleMsgs;
	private final IOFSwitch sw;
	private boolean commited;
	private boolean closed;

	private final long startTime;
	private long lastAddTime;

	/**
	 * Creates an OFBundle object with a new bundle id and sends an OPEN_REQUEST
	 * to the switch.
	 * 
	 * Uses no flags in the bundle.
	 * 
	 * @param sw
	 *            the switch
	 */
	public OFBundle(IOFSwitch sw) throws IllegalArgumentException {
		this(sw, null, null);
	}

	/**
	 * Creates an OFBundle object with a new bundle id and sends an OPEN_REQUEST
	 * to the switch.
	 * 
	 * @param sw
	 *            the switch
	 * @param flags
	 *            flags to be used in this bundle
	 */
	public OFBundle(IOFSwitch sw, Set<OFBundleFlags> flags)
			throws IllegalArgumentException {
		this(sw, flags, null);
	}

	/**
	 * Creates an OFBundle object with a new bundle id and sends an OPEN_REQUEST
	 * to the switch.
	 * 
	 * Registers a callback for when the controller receives a OPEN_REPLY
	 * message
	 * 
	 * @param sw
	 *            the switch
	 * @param flags
	 *            flags to be used in this bundle
	 * @param callback
	 *            callback function to be executed when the OPEN_REQUEST_REPLY
	 *            arrives at the controller.
	 */
	public OFBundle(IOFSwitch sw, Set<OFBundleFlags> flags,
			FutureCallback<OFBundleCtrlMsg> callback)
			throws IllegalArgumentException {

		if (flags == null)
			flags = NO_FLAGS;

		if (sw == null) {
			throw new IllegalArgumentException("sw cannot be null");
		} else if (flags == null) {
			throw new IllegalArgumentException("flags cannot be null");
		} else if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_14) < 0) {
			throw new IllegalArgumentException(
					"Bundles not supported in this OF version ("
							+ sw.getOFFactory().getVersion().toString() + ")");
		} else if (!(flags.equals(ALL_BUNDLE_FLAGS)
				|| flags.equals(ATOMIC_BUNDLE_FLAGS)
				|| flags.equals(ORDERED_BUNDLE_FLAGS) || flags.equals(NO_FLAGS))) {
			throw new IllegalArgumentException("Unknown Set of bundle flags ("
					+ flags.toString() + ")");
		}

		log.info("Creating new bundle...");

		this.commited = false;
		this.closed = false;
		this.id = currentBundleId.incrementAndGet();
		this.flags = flags;
		this.bundleMsgs = new ArrayList<OFMessage>();
		this.sw = sw;

		this.startTime = System.currentTimeMillis();

		OFBundleCtrlMsg open = createBundleCtrlMsg(sw.getOFFactory(),
				OFBundleCtrlType.OPEN_REQUEST, id, flags);

		ListenableFuture<OFBundleCtrlMsg> future = sw.writeRequest(open);

		if (callback != null) {
			Futures.addCallback(future, callback);
		} else {
			Futures.addCallback(future, new DebugReplies(
					OFBundleCtrlType.OPEN_REQUEST, null));
		}

		log.info("Sent Open Request for Bundle " + getBundleId()
				+ " in switch " + sw.getId().toString() + "[" + open.toString()
				+ "]");

	}

	public int getBundleId() {
		return id;
	}

	public Set<OFBundleFlags> getFlags() {
		return flags;
	}

	public IOFSwitch getSwitch() {
		return sw;
	}

	public boolean isCommited() {
		return commited;
	}

	public boolean isClosed() {
		return closed;
	}

	public List<OFMessage> getMsgsInBundle() {
		return bundleMsgs;
	}

	public Collection<OFMessage> add(Iterable<OFMessage> msglist) {
		synchronized (this) {
			if (closed || commited)
				return null;

			List<OFMessage> list = new ArrayList<OFMessage>();
			for (OFMessage m : msglist) {
				list.add(createBundleAddMsg(sw.getOFFactory(), id, flags, m));
			}

			Collection<OFMessage> result = sw.getConnectionByCategory(
					LogicalOFMessageCategory.MAIN).write(list);

			if (result.isEmpty()) {
				log.info("Msgs added to bundle {} " + " in switch {}:" + list,
						getBundleId(), sw.getId());
				bundleMsgs.addAll(list);
			} else {
				for (OFMessage m : list) {
					if (!result.contains(m)) {
						bundleMsgs.add(m);
						log.info("Msg added to bundle {} " + " in switch {}:"
								+ m.toString(), getBundleId(), sw.getId()
								.toString());
					} else {
						log.info("Error adding msg to bundle {} "
								+ " in switch {}:" + m.toString(),
								getBundleId(), sw.getId().toString());
					}
				}
			}
			lastAddTime = System.currentTimeMillis();
			return result;
		}
	}

	public boolean add(OFMessage msg) {
		synchronized (this) {
			if (commited)
				return false;

			OFBundleAddMsg add = createBundleAddMsg(sw.getOFFactory(), id,
					flags, msg);

			boolean write = sw.getConnectionByCategory(
					LogicalOFMessageCategory.MAIN).write(add);

			if (write) {
				bundleMsgs.add(msg);
				log.info("Msg " + msg.getXid() + " added to bundle "
						+ getBundleId() + " in switch " + sw.getId().toString()
						+ "[" + add.toString() + "]");
			} else {
				log.warn("Error adding msg to bundle");
			}
			lastAddTime = System.currentTimeMillis();
			return write;
		}
	}

	public void close() {
		close(null, false);
	}

	public void close(FutureCallback<OFBundleCtrlMsg> callback) {
		close(callback, false);
	}

	public void closeAndCommit() {
		close(null, true);
	}

	public void closeAndCommit(FutureCallback<OFBundleCtrlMsg> callback) {
		close(callback, true);
	}

	private void close(final FutureCallback<OFBundleCtrlMsg> callback,
			boolean commit) {
		synchronized (this) {
			OFBundleCtrlMsg close = createBundleCtrlMsg(sw.getOFFactory(),
					OFBundleCtrlType.CLOSE_REQUEST, id, flags);

			ListenableFuture<OFBundleCtrlMsg> future = sw.writeRequest(close);
			log.info("Sent Close Request for Bundle " + getBundleId()
					+ " in switch " + sw.getId().toString() + "["
					+ close.toString() + "]");
			closed = true;
			if (!commit) { // send close only
				if (callback != null) {
					Futures.addCallback(future, callback);
				} else {
					Futures.addCallback(future, new DebugReplies(
							OFBundleCtrlType.CLOSE_REQUEST, this));
				}
			} else { // send commit when close reply arrives
				Futures.addCallback(future,
						new FutureCallback<OFBundleCtrlMsg>() {
							@Override
							public void onFailure(Throwable arg0) {
								log.error("Error receiving close reply from switch");
							}

							@Override
							public void onSuccess(OFBundleCtrlMsg arg0) {
								commit(callback);
							}

						});

				log.info("Sent commit request for bundle " + getBundleId()
						+ " to switch " + sw.getId().toString());
			}
		}
	}

	public void commit() {
		commit(null);
	}

	public void commit(FutureCallback<OFBundleCtrlMsg> callback) {
		OFBundleCtrlMsg commit = createBundleCtrlMsg(sw.getOFFactory(),
				OFBundleCtrlType.COMMIT_REQUEST, id, flags);
		final long commitTime = System.currentTimeMillis();
		ListenableFuture<OFBundleCtrlMsg> future = sw.writeRequest(commit);

		if (callback != null) {
			Futures.addCallback(future, callback);
		}

		Futures.addCallback(future, new FutureCallback<OFBundleCtrlMsg>() {
			@Override
			public void onFailure(Throwable arg0) {
				log.error("Failed to commit bundle");
			}

			@Override
			public void onSuccess(OFBundleCtrlMsg arg0) {
				// log.info("!! Commit succesfull !!");
				commited = true;
				long endTime = System.currentTimeMillis();
				long elapsedTime = endTime - startTime;
				long waitTime = commitTime - lastAddTime;
				log.info("Bundle " + getBundleId() + " elapsed time: {}; "
						+ "time waiting for commit without adds: " + waitTime
						+ "; " + "# commited messages: {}", elapsedTime,
						getMsgsInBundle().size());
			}
		});
	}

	private class DebugReplies implements FutureCallback<OFBundleCtrlMsg> {
		private String switchId;
		private OFBundleCtrlType type;
		private OFBundle bundle;

		private DebugReplies(OFBundleCtrlType type, OFBundle bundle) {
			this.switchId = sw.getId().toString();
			this.type = type;
			this.bundle = bundle;
		}

		@Override
		public void onFailure(Throwable arg0) {
			log.error("Failed to receive reply for " + type + " in bundle "
					+ bundle.getBundleId() + "from switch " + switchId
					+ ". Original request: " + arg0.toString());
		}

		@Override
		public void onSuccess(OFBundleCtrlMsg reply) {
			log.info("Received Bundle Control Message "
					+ reply.getBundleCtrlType() + " from switch " + switchId
					+ ". Bundle ID: " + reply.getBundleId() + "; Flags: "
					+ reply.getFlags());
		}
	}
}