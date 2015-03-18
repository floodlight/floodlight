package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.jboss.netty.util.Timer;

import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IOFConnection;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.PortChangeEvent;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.OFSwitchAppHandshakePlugin.PluginResultType;

import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFDescStatsRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFErrorType;
import org.projectfloodlight.openflow.protocol.OFExperimenter;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowModFailedCode;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFGetConfigRequest;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRole;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleReply;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleRequest;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFRoleRequest;
import org.projectfloodlight.openflow.protocol.OFSetConfig;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequestFlags;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.errormsg.OFBadRequestErrorMsg;
import org.projectfloodlight.openflow.protocol.errormsg.OFFlowModFailedErrorMsg;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Switch handler deals with the switch connection and dispatches
 * switch messages to the appropriate locations. These messages
 * are typically received by the channel handler first and piped here.
 *
 * @author Jason Parraga <jason.parraga@bigswitch.com>
 */
public class OFSwitchHandshakeHandler implements IOFConnectionListener {
	private static final Logger log = LoggerFactory.getLogger(OFSwitchHandshakeHandler.class);

	private final IOFSwitchManager switchManager;
	private final RoleManager roleManager;
	private final IOFConnectionBackend mainConnection;
	private final SwitchManagerCounters switchManagerCounters;
	private IOFSwitchBackend sw;
	private final Map<OFAuxId, IOFConnectionBackend> auxConnections;
	private volatile OFSwitchHandshakeState state;
	private RoleChanger roleChanger;
	// Default to 1.3 - This is overwritten by the features reply
	private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
	private final OFFeaturesReply featuresReply;
	private final Timer timer;

	private final ArrayList<OFPortStatus> pendingPortStatusMsg;

	/** transaction Ids to use during handshake. Since only one thread
	 * calls into the OFChannelHandler we don't need atomic.
	 * We will count down
	 */
	private long handshakeTransactionIds = 0x00FFFFFFFFL;

	/* Exponential backoff of master role assertion */
	private final long MAX_ASSERT_TIME_INTERVAL_NS = TimeUnit.SECONDS.toNanos(120);
	private final long DEFAULT_ROLE_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(10);

	protected OFPortDescStatsReply portDescStats;

	/**
	 * When we remove a pending role request and set the role on the switch
	 * we use this enum to indicate how we arrived at the decision.
	 * @author gregor
	 */
	private enum RoleRecvStatus {
		/** We received a role reply message from the switch */
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
		// the transaction Id of the pending request
		private long pendingXid;
		// the role that's pending
		private OFControllerRole pendingRole;
		// system time in NS when we send the request
		private long roleSubmitTimeNs;
		// the timeout to use
		private final long roleTimeoutNs;
		private long lastAssertTimeNs;
		private long assertTimeIntervalNs = TimeUnit.SECONDS.toNanos(1);

		public RoleChanger(long roleTimeoutNs) {
			this.roleTimeoutNs = roleTimeoutNs;
			// System.nanoTime() may be negative -- prime the roleSubmitTime as
			// "long ago in the past" to be robust against it.
			this.roleSubmitTimeNs = System.nanoTime() - (2 * roleTimeoutNs);
			this.lastAssertTimeNs = System.nanoTime() - (2 * assertTimeIntervalNs);
			this.requestPending = false;
			this.pendingXid = -1;
			this.pendingRole = null;
		}

		/**
		 * Send Nicira role request message to the switch requesting the
		 * specified role.
		 *
		 * @param role role to request
		 */
		private long sendNiciraRoleRequest(OFControllerRole role){

			long xid;
			// Construct the role request message
			if(factory.getVersion().compareTo(OFVersion.OF_12) < 0) {
				OFNiciraControllerRoleRequest.Builder builder =
						factory.buildNiciraControllerRoleRequest();
				xid = factory.nextXid();
				builder.setXid(xid);

				OFNiciraControllerRole niciraRole = NiciraRoleUtils.ofRoleToNiciraRole(role);
				builder.setRole(niciraRole);
				OFNiciraControllerRoleRequest roleRequest = builder.build();
				// Send it to the switch
				mainConnection.write(roleRequest);
			} else {
				// send an OF 1.2+ role request
				OFRoleRequest roleRequest = factory.buildRoleRequest()
						// we don't use the generation id scheme for now,
						// switch initializes to 0, we keep it at 0
						.setGenerationId(U64.of(0))
						.setRole(role)
						.build();
				xid = roleRequest.getXid();
				mainConnection.write(roleRequest);
			}
			return xid;
		}

		/**
		 * Send a role request for the given role only if no other role
		 * request is currently pending.
		 * @param role The role to send to the switch.
		 * @throws IOException
		 */
		@LogMessageDoc(level="WARN",
				message="Reasserting master role on switch {SWITCH}, " +
						"likely a configruation error with multiple masters",
						explanation="The controller keeps getting permission error " +
								"from switch, likely due to switch connected to another " +
								"controller also in master mode",
								recommendation=LogMessageDoc.CHECK_SWITCH)
		synchronized void sendRoleRequestIfNotPending(OFControllerRole role)
				throws IOException {
			long now = System.nanoTime();
			if (now - lastAssertTimeNs < assertTimeIntervalNs) {
				return;
			}

			lastAssertTimeNs = now;
			if (assertTimeIntervalNs < MAX_ASSERT_TIME_INTERVAL_NS) { // 2 minutes max
				assertTimeIntervalNs <<= 1;
			} else if (role == OFControllerRole.ROLE_MASTER){
				log.warn("Reasserting master role on switch {}, " +
						"likely a switch config error with multiple masters",
						role, sw);
			}
			if (!requestPending)
				sendRoleRequest(role);
			else
				switchManagerCounters.roleNotResentBecauseRolePending.increment();
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
		synchronized void sendRoleRequest(OFControllerRole role) throws IOException {
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
				pendingXid = sendNiciraRoleRequest(role);
				pendingRole = role;
				this.roleSubmitTimeNs = System.nanoTime();
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
		synchronized void deliverRoleReply(long xid, OFControllerRole role) {
			if (!requestPending) {
				// Maybe don't disconnect if the role reply we received is
				// for the same role we are already in.
				String msg = String.format("Switch: [%s], State: [%s], "
						+ "received unexpected RoleReply[%s]. "
						+ "No roles are pending",
						OFSwitchHandshakeHandler.this.getSwitchInfoString(),
						OFSwitchHandshakeHandler.this.state.toString(),
						role);
				throw new SwitchStateException(msg);
			}

			if (pendingXid == xid && pendingRole == role) {
				log.debug("[{}] Received role reply message setting role to {}",
						getDpid(), role);
				switchManagerCounters.roleReplyReceived.increment();
				setSwitchRole(role, RoleRecvStatus.RECEIVED_REPLY);
			} else {
				log.debug("[{}] Received stale or unexpected role reply " +
						"{}, xid={}. Ignoring. " +
						"Waiting for {}, xid={}",
						new Object[] { getDpid(), role, xid,
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
		synchronized boolean deliverError(OFErrorMsg error) {
			if (!requestPending)
				return false;

			if (pendingXid == error.getXid()) {
				if (error.getErrType() == OFErrorType.BAD_REQUEST) {
					switchManagerCounters.roleReplyErrorUnsupported.increment();
					setSwitchRole(pendingRole, RoleRecvStatus.UNSUPPORTED);
				} else {
					// TODO: Is this the right thing to do if we receive
					// some other error besides a bad request error?
					// Presumably that means the switch did actually
					// understand the role request message, but there
					// was some other error from processing the message.
					// OF 1.2 specifies a ROLE_REQUEST_FAILED
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
							OFSwitchHandshakeHandler.this.getSwitchInfoString(),
							OFSwitchHandshakeHandler.this.state.toString(),
							error.toString(),
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
				long now = System.nanoTime();
				if (now - this.roleSubmitTimeNs > roleTimeoutNs) {
					// timeout triggered.
					switchManagerCounters.roleReplyTimeout.increment();
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
		synchronized private void setSwitchRole(OFControllerRole role, RoleRecvStatus status) {
			requestPending = false;
			if (status == RoleRecvStatus.RECEIVED_REPLY)
				sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
			else
				sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
			sw.setControllerRole(role);

			if (role != OFControllerRole.ROLE_SLAVE) {
				OFSwitchHandshakeHandler.this.setState(new MasterState());
			} else {
				if (status != RoleRecvStatus.RECEIVED_REPLY) {
					if (log.isDebugEnabled()) {
						log.debug("Disconnecting switch {}. Doesn't support role"
								+ "({}) request and controller is now SLAVE",
								getSwitchInfoString(), status);
					}
					// the disconnect will trigger a switch removed to
					// controller so no need to signal anything else
					sw.disconnect();
				} else {
					OFSwitchHandshakeHandler.this.setState(new SlaveState());
				}
			}
		}
	}

	/**
	 * Removes all present flows and adds an initial table-miss flow to each
	 * and every table on the switch. This replaces the default behavior of
	 * forwarding table-miss packets to the controller. The table-miss flows
	 * inserted will forward all packets that do not match a flow to the 
	 * controller for processing.
	 * 
	 * Adding the default flow only applies to OpenFlow 1.3+ switches, which 
	 * remove the default forward-to-controller behavior of flow tables.
	 */
	private void clearAndSetDefaultFlows() {
		/*
		 * No tables for OF1.0, so omit that field for flow deletion.
		 */
		if (this.sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) == 0) {
			OFFlowDelete deleteFlows = this.factory.buildFlowDelete()
					.build();
			this.sw.write(deleteFlows);
		} else { /* All other OFVersions support multiple tables and groups. */
			OFFlowDelete deleteFlows = this.factory.buildFlowDelete()
					.setTableId(TableId.ALL)
					.build();
			this.sw.write(deleteFlows);
			
			/*
			 * Clear all groups.
			 * We have to do this for all types manually as of Loxi 0.9.0.
			 */
			OFGroupDelete delgroup = this.sw.getOFFactory().buildGroupDelete()
				.setGroup(OFGroup.ALL)
				.setGroupType(OFGroupType.ALL)
				.build();
			this.sw.write(delgroup);
			delgroup.createBuilder()
				.setGroupType(OFGroupType.FF)
				.build();
			this.sw.write(delgroup);
			delgroup.createBuilder()
				.setGroupType(OFGroupType.INDIRECT)
				.build();
			this.sw.write(delgroup);
			delgroup.createBuilder()
				.setGroupType(OFGroupType.SELECT)
				.build();
			this.sw.write(delgroup);
		}
		
		/*
		 * Only for OF1.3+, insert the default forward-to-controller flow for
		 * each table. This is priority=0 with no Match.
		 */
		if (this.sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
			ArrayList<OFAction> actions = new ArrayList<OFAction>(1);
			actions.add(factory.actions().output(OFPort.CONTROLLER, 0xffFFffFF));
			ArrayList<OFMessage> flows = new ArrayList<OFMessage>();
			for (int tableId = 0; tableId < this.sw.getTables(); tableId++) {
				OFFlowAdd defaultFlow = this.factory.buildFlowAdd()
						.setTableId(TableId.of(tableId))
						.setPriority(0)
						.setActions(actions)
						.build();
				flows.add(defaultFlow);
			}
			this.sw.write(flows);
		}
	}

	/**
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
	 */
	public abstract class OFSwitchHandshakeState {

		void processOFBarrierReply(OFBarrierReply m) {
			// do nothing
		}

		void processOFError(OFErrorMsg m) {
			logErrorDisconnect(m);
		}

		void processOFFlowRemoved(OFFlowRemoved m) {
			unhandledMessageReceived(m);
		}

		void processOFGetConfigReply(OFGetConfigReply m) {
			// we only expect config replies in the WAIT_CONFIG_REPLY state
			// TODO: might use two different strategies depending on whether
			// we got a miss length of 64k or not.
			illegalMessageReceived(m);
		}

		void processOFPacketIn(OFPacketIn m) {
			unhandledMessageReceived(m);
		}

		// By default add port status messages to a pending list
		void processOFPortStatus(OFPortStatus m) {
			pendingPortStatusMsg.add(m);
		}

		void processOFQueueGetConfigReply(OFQueueGetConfigReply m) {
			unhandledMessageReceived(m);
		}

		void processOFStatsReply(OFStatsReply m) {
			switch(m.getStatsType()) {
			case PORT_DESC:
				processPortDescStatsReply((OFPortDescStatsReply) m);
				break;
			default:
				unhandledMessageReceived(m);
			}
		}

		void processOFExperimenter(OFExperimenter m) {
			unhandledMessageReceived(m);
		}

		void processPortDescStatsReply(OFPortDescStatsReply m) {
			unhandledMessageReceived(m);
		}

		void processOFRoleReply(OFRoleReply m) {
			unhandledMessageReceived(m);
		}

		private final boolean handshakeComplete;
		OFSwitchHandshakeState(boolean handshakeComplete) {
			this.handshakeComplete = handshakeComplete;
		}

		void logState() {
			if(log.isDebugEnabled())
				log.debug("[{}] - Switch Handshake - enter state {}", mainConnection.getDatapathId(), this.getClass().getSimpleName());
		}

		/** enter this state. Can initialize the handler, send
		 *  the necessary messages, etc.
		 */
		void enterState(){
		}

		/**
		 * Is this a state in which the handshake has completed?
		 * @return true if the handshake is complete
		 */
		public boolean isHandshakeComplete() {
			return handshakeComplete;
		}

		/**
		 * Used to notify the WAIT OF AUX state that
		 * a new connection has been added
		 * @param connection
		 */
		public void auxConnectionOpened(IOFConnectionBackend connection) {
			// Should only be handled in wait of aux
			log.debug("[{}] - Switch Handshake - unhandled aux connection event",
					getDpid());
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
					getSwitchInfoString(),
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
			switchManagerCounters.unhandledMessage.increment();
			if (log.isDebugEnabled()) {
				String msg = getSwitchStateMessage(m,
						"Ignoring unexpected message");
				log.debug(msg);
			}
		}

		/**
		 * Log an OpenFlow error message from a switch
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
					getSwitchInfoString(),
					this.toString()});
		}

		/**
		 * Log an OpenFlow error message from a switch and disconnect the
		 * channel
		 * @param error The error message
		 */
		protected void logErrorDisconnect(OFErrorMsg error) {
			logError(error);
			mainConnection.disconnect();
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
		 */
		protected OFControllerRole extractNiciraRoleReply(OFMessage vendorMessage) {
			if (!(vendorMessage instanceof OFNiciraControllerRoleReply))
				return null;
			OFNiciraControllerRoleReply roleReply =
					(OFNiciraControllerRoleReply) vendorMessage;
			return NiciraRoleUtils.niciraToOFRole(roleReply);
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
		protected void handlePortStatusMessage(OFPortStatus m, boolean doNotify) {
			if (sw == null) {
				String msg = getSwitchStateMessage(m, "State machine error: switch is null. Should never happen");
				throw new SwitchStateException(msg);
			}
			Collection<PortChangeEvent> changes = sw.processOFPortStatus(m);
			if (doNotify) {
				for (PortChangeEvent ev: changes)
					switchManager.notifyPortChanged(sw, ev.port, ev.type);
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
		void processOFMessage(OFMessage m) {
			roleChanger.checkTimeout();
			switch(m.getType()) {
			case BARRIER_REPLY:
				processOFBarrierReply((OFBarrierReply) m);
				break;
			case ERROR:
				processOFError((OFErrorMsg) m);
				break;
			case FLOW_REMOVED:
				processOFFlowRemoved((OFFlowRemoved) m);
				break;
			case GET_CONFIG_REPLY:
				processOFGetConfigReply((OFGetConfigReply) m);
				break;
			case PACKET_IN:
				processOFPacketIn((OFPacketIn) m);
				break;
			case PORT_STATUS:
				processOFPortStatus((OFPortStatus) m);
				break;
			case QUEUE_GET_CONFIG_REPLY:
				processOFQueueGetConfigReply((OFQueueGetConfigReply) m);
				break;
			case STATS_REPLY:
				processOFStatsReply((OFStatsReply) m);
				break;
			case ROLE_REPLY:
				processOFRoleReply((OFRoleReply) m);
				break;
			case EXPERIMENTER:
				processOFExperimenter((OFExperimenter) m);
				break;
			default:
				illegalMessageReceived(m);
				break;
			}
		}
	}

	/**
	 * Initial state before channel is connected. Should not handle any messages.
	 */
	public class InitState extends OFSwitchHandshakeState {

		InitState() {
			super(false);
		}

		@Override
		public void logState() {
			log.debug("[{}] - Switch Handshake - Initiating from {}",
					getDpid(), mainConnection.getRemoteInetAddress());
		}
	}

	/**
	 * We are waiting for a features reply message. Once we receive it
	 * we send a SetConfig request, barrier, and GetConfig request.
	 * Next stats is WAIT_CONFIG_REPLY or WAIT_SET_L2_TABLE_REPLY
	 */
	public class WaitPortDescStatsReplyState extends OFSwitchHandshakeState {
		WaitPortDescStatsReplyState() {
			super(false);
		}

		@Override
		void enterState(){
			sendPortDescRequest();
		}

		@Override
		void processPortDescStatsReply(OFPortDescStatsReply  m) {
			portDescStats = m;
			setState(new WaitConfigReplyState());
		}

		@Override
		void processOFExperimenter(OFExperimenter m) {
			unhandledMessageReceived(m);
		}
	}

	/**
	 * We are waiting for a config reply message. Once we receive it
	 * we send a DescriptionStatsRequest to the switch.
	 * Next state: WAIT_DESCRIPTION_STAT_REPLY
	 */
	public class WaitConfigReplyState extends OFSwitchHandshakeState {

		WaitConfigReplyState() {
			super(false);
		}

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
		void processOFGetConfigReply(OFGetConfigReply m) {
			if (m.getMissSendLen() == 0xffff) {
				log.trace("Config Reply from switch {} confirms "
						+ "miss length set to 0xffff",
						getSwitchInfoString());
			} else {
				// FIXME: we can't really deal with switches that don't send
				// full packets. Shouldn't we drop the connection here?
				// FIXME: count??
				log.warn("Config Reply from switch {} has"
						+ "miss length set to {}",
						getSwitchInfoString(),
						m.getMissSendLen());
			}
			setState(new WaitDescriptionStatReplyState());
		}

		@Override
		void processOFStatsReply(OFStatsReply  m) {
			illegalMessageReceived(m);
		}

		@Override
		void processOFError(OFErrorMsg m) {
			/*
			 * HP ProCurve switches do not support
			 * the ofpt_barrier_request message.
			 * 
			 * Look for an error from a bad ofpt_barrier_request,
			 * log a warning, but proceed.
			 */
			if (m.getErrType() == OFErrorType.BAD_REQUEST &&
					((OFBadRequestErrorMsg) m).getCode() == OFBadRequestCode.BAD_TYPE &&
					((OFBadRequestErrorMsg) m).getData().getParsedMessage().get() instanceof OFBarrierRequest) {
					log.warn("Switch does not support Barrier Request messages. Could be an HP ProCurve.");
			} else {
				logErrorDisconnect(m);
			}
		} 

		@Override
		void enterState() {
			sendHandshakeSetConfig();
		}
	}
	
	/**
	 * We are waiting for a OFDescriptionStat message from the switch.
	 * Once we receive any stat message we try to parse it. If it's not
	 * a description stats message we disconnect. If its the expected
	 * description stats message, we:
	 *    - use the switch driver to bind the switch and get an IOFSwitch
	 *      instance, setup the switch instance
	 *    - setup the IOFSwitch instance
	 *    - add switch to FloodlightProvider and send the initial role
	 *      request to the switch.
	 *
	 * Next state: WaitOFAuxCxnsReplyState (if OF1.3), else
	 *     WaitInitialRoleState or WaitSwitchDriverSubHandshake
	 *
	 * All following states will have a h.sw instance!
	 */
	public class WaitDescriptionStatReplyState extends OFSwitchHandshakeState{

		WaitDescriptionStatReplyState() {
			super(false);
		}

		@LogMessageDoc(message="Switch {switch info} bound to class " +
				"{switch driver}, description {switch description}",
				explanation="The specified switch has been bound to " +
						"a switch driver based on the switch description" +
				"received from the switch")
		@Override
		void processOFStatsReply(OFStatsReply m) {
			// Read description, if it has been updated
			if (m.getStatsType() != OFStatsType.DESC) {
				illegalMessageReceived(m);
				return;
			}

			OFDescStatsReply descStatsReply = (OFDescStatsReply) m;
			SwitchDescription description = new SwitchDescription(descStatsReply);
			sw = switchManager.getOFSwitchInstance(mainConnection, description, factory, featuresReply.getDatapathId());
			switchManager.switchAdded(sw);
			// set switch information
			// set features reply and channel first so we a DPID and
			// channel info.
			sw.setFeaturesReply(featuresReply);
			if (portDescStats != null) {
				sw.setPortDescStats(portDescStats);
			}

			// Handle pending messages now that we have a sw object
			handlePendingPortStatusMessages(description);

			sw.startDriverHandshake();
			if (sw.isDriverHandshakeComplete()) {
				setState(new WaitAppHandshakeState());
			} else {
				setState(new WaitSwitchDriverSubHandshakeState());
			}
		}

		void handlePendingPortStatusMessages(SwitchDescription description){
			for (OFPortStatus ps: pendingPortStatusMsg) {
				handlePortStatusMessage(ps, false);
			}
			pendingPortStatusMsg.clear();
			log.info("Switch {} bound to class {}, description {}", new Object[] { sw, sw.getClass(), description });
		}

		@Override
		void enterState() {
			sendHandshakeDescriptionStatsRequest();
		}
	}

	public class WaitSwitchDriverSubHandshakeState extends OFSwitchHandshakeState {

		WaitSwitchDriverSubHandshakeState() {
			super(false);
		}

		@Override
		void processOFMessage(OFMessage m) {
			// FIXME: other message to handle here?
			sw.processDriverHandshakeMessage(m);
			if (sw.isDriverHandshakeComplete()) {
				setState(new WaitAppHandshakeState());
			}
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			handlePortStatusMessage(m, false);
		}
	}

	public class WaitAppHandshakeState extends OFSwitchHandshakeState {

		private final Iterator<IAppHandshakePluginFactory> pluginIterator;
		private OFSwitchAppHandshakePlugin plugin;

		WaitAppHandshakeState() {
			super(false);
			this.pluginIterator = switchManager.getHandshakePlugins().iterator();
		}

		@Override
		void processOFMessage(OFMessage m) {
			if(m.getType() == OFType.PORT_STATUS){
				OFPortStatus status = (OFPortStatus) m;
				handlePortStatusMessage(status, false);
			}
			else if(plugin != null){
				this.plugin.processOFMessage(m);
			}
			else{
				super.processOFMessage(m);
			}
		}

		/**
		 * Called by handshake plugins to signify that they have finished their
		 * sub handshake.
		 *
		 * @param result
		 *            the result of the sub handshake
		 */
		void exitPlugin(PluginResult result) {

			// Proceed
			if (result.getResultType() == PluginResultType.CONTINUE) {
				if (log.isDebugEnabled()) {
					log.debug("Switch " + getSwitchInfoString() + " app handshake plugin {} returned {}."
							+ " Proceeding normally..",
							this.plugin.getClass().getSimpleName(), result);
				}

				enterNextPlugin();

				// Stop
			} else if (result.getResultType() == PluginResultType.DISCONNECT) {
				log.error("Switch " + getSwitchInfoString() + " app handshake plugin {} returned {}. "
						+ "Disconnecting switch.",
						this.plugin.getClass().getSimpleName(), result);
				mainConnection.disconnect();
			} else if (result.getResultType() == PluginResultType.QUARANTINE) {
				log.warn("Switch " + getSwitchInfoString() + " app handshake plugin {} returned {}. "
						+ "Putting switch into quarantine state.",
						this.plugin.getClass().getSimpleName(),
						result);
				setState(new QuarantineState(result.getReason()));
			}
		}

		@Override
		public void enterState() {
			enterNextPlugin();
		}

		/**
		 * Initialize the plugin and begin.
		 *
		 * @param plugin the of switch app handshake plugin
		 */
		public void enterNextPlugin() {
			if(this.pluginIterator.hasNext()){
				this.plugin = pluginIterator.next().createPlugin();
				this.plugin.init(this, sw, timer);
				this.plugin.enterPlugin();
			}
			// No more plugins left...
			else{
				setState(new WaitInitialRoleState());
			}
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			handlePortStatusMessage(m, false);
		}

		OFSwitchAppHandshakePlugin getCurrentPlugin() {
			return plugin;
		}

	}

	/**
	 * Switch is in a quarantine state. Essentially the handshake is complete.
	 */
	public class QuarantineState extends OFSwitchHandshakeState {

		private final String quarantineReason;

		QuarantineState(String reason) {
			super(true);
			this.quarantineReason = reason;
		}

		@Override
		public void enterState() {
			setSwitchStatus(SwitchStatus.QUARANTINED);
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			handlePortStatusMessage(m, false);
		}

		public String getQuarantineReason() {
			return this.quarantineReason;
		}
	}

	/**
	 * We are waiting for the initial role reply message (or error indication)
	 * from the switch. Next State: MASTER or SLAVE
	 */
	public class WaitInitialRoleState extends OFSwitchHandshakeState {

		WaitInitialRoleState() {
			super(false);
		}

		@Override
		void processOFError(OFErrorMsg m) {
			// role changer will ignore the error if it isn't for it
			boolean didHandle = roleChanger.deliverError(m);
			if (!didHandle) {
				logError(m);
			}
		}

		@Override
		void processOFExperimenter(OFExperimenter m) {
			OFControllerRole role = extractNiciraRoleReply(m);
			// If role == null it measn the message wasn't really a
			// Nicira role reply. We ignore this case.
			if (role != null) {
				roleChanger.deliverRoleReply(m.getXid(), role);
			} else {
				unhandledMessageReceived(m);
			}
		}

		@Override
		void processOFRoleReply(OFRoleReply m) {
			roleChanger.deliverRoleReply(m.getXid(), m.getRole());
		}

		@Override
		void processOFStatsReply(OFStatsReply m) {
			illegalMessageReceived(m);
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			handlePortStatusMessage(m, false);
		}

		@Override
		void enterState(){
			sendRoleRequest(roleManager.getOFControllerRole());
		}
	}

	/**
	 * The switch is in MASTER role. We enter this state after a role
	 * reply from the switch is received (or the controller is MASTER
	 * and the switch doesn't support roles). The handshake is complete at
	 * this point. We only leave this state if the switch disconnects or
	 * if we send a role request for SLAVE /and/ receive the role reply for
	 * SLAVE.
	 */
	public class MasterState extends OFSwitchHandshakeState {

		MasterState() {
			super(true);
		}

		@Override
		void enterState() {
			setSwitchStatus(SwitchStatus.MASTER);
			clearAndSetDefaultFlows();
		}

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
		void processOFError(OFErrorMsg m) {
			// role changer will ignore the error if it isn't for it
			boolean didHandle = roleChanger.deliverError(m);
			if (didHandle)
				return;
			if ((m.getErrType() == OFErrorType.BAD_REQUEST) &&
					(((OFBadRequestErrorMsg)m).getCode() == OFBadRequestCode.EPERM)) {
				// We are the master controller and the switch returned
				// a permission error. This is a likely indicator that
				// the switch thinks we are slave. Reassert our
				// role
				// FIXME: this could be really bad during role transitions
				// if two controllers are master (even if its only for
				// a brief period). We might need to see if these errors
				// persist before we reassert
				switchManagerCounters.epermErrorWhileSwitchIsMaster.increment();
				log.warn("Received permission error from switch {} while" +
						"being master. Reasserting master role.",
						getSwitchInfoString());
				reassertRole(OFControllerRole.ROLE_MASTER);
			}
			else if ((m.getErrType() == OFErrorType.FLOW_MOD_FAILED) &&
					(((OFFlowModFailedErrorMsg)m).getCode() == OFFlowModFailedCode.ALL_TABLES_FULL)) {
				sw.setTableFull(true);
			}
			else {
				logError(m);
			}
			dispatchMessage(m);
		}

		@Override
		void processOFExperimenter(OFExperimenter m) {
			OFControllerRole role = extractNiciraRoleReply(m);
			// If role == null it means the message wasn't really a
			// Nicira role reply. We ignore just dispatch it to the
			// OFMessage listenersa in this case.
			if (role != null) {
				roleChanger.deliverRoleReply(m.getXid(), role);
			} else {
				dispatchMessage(m);
			}
		}


		@Override
		void processOFRoleReply(OFRoleReply m) {
			roleChanger.deliverRoleReply(m.getXid(), m.getRole());
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			handlePortStatusMessage(m, true);
		}

		@Override
		void processOFPacketIn(OFPacketIn m) {
			dispatchMessage(m);
		}

		@Override
		void processOFFlowRemoved(OFFlowRemoved m) {
			dispatchMessage(m);
		}
	}

	/**
	 * The switch is in SLAVE role. We enter this state after a role
	 * reply from the switch is received. The handshake is complete at
	 * this point. We only leave this state if the switch disconnects or
	 * if we send a role request for MASTER /and/ receive the role reply for
	 * MASTER.
	 * TODO: CURRENTLY, WE DO NOT DISPATCH ANY MESSAGE IN SLAVE.
	 */
	public class SlaveState extends OFSwitchHandshakeState {

		SlaveState() {
			super(true);
		}

		@Override
		void enterState() {
			setSwitchStatus(SwitchStatus.SLAVE);
		}

		@Override
		void processOFError(OFErrorMsg m) {
			// role changer will ignore the error if it isn't for it
			boolean didHandle = roleChanger.deliverError(m);
			if (!didHandle) {
				logError(m);
			}
		}

		@Override
		void processOFStatsReply(OFStatsReply m) {
		}

		@Override
		void processOFPortStatus(OFPortStatus m) {
			handlePortStatusMessage(m, true);
		}

		@Override
		void processOFExperimenter(OFExperimenter m) {
			OFControllerRole role = extractNiciraRoleReply(m);
			// If role == null it means the message wasn't really a
			// Nicira role reply. We ignore it.
			if (role != null) {
				roleChanger.deliverRoleReply(m.getXid(), role);
			} else {
				unhandledMessageReceived(m);
			}
		}

		@Override
		void processOFRoleReply(OFRoleReply m) {
			roleChanger.deliverRoleReply(m.getXid(), m.getRole());
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
		void processOFPacketIn(OFPacketIn m) {
			// we don't expect packetIn while slave, reassert we are slave
			switchManagerCounters.packetInWhileSwitchIsSlave.increment();
			log.warn("Received PacketIn from switch {} while" +
					"being slave. Reasserting slave role.", sw);
			reassertRole(OFControllerRole.ROLE_SLAVE);
		}
	};


	/**
	 * Create a new unconnected OFChannelHandler.
	 * @param controller
	 * @param broker
	 * @throws SwitchHandshakeHandlerException
	 */
	OFSwitchHandshakeHandler(@Nonnull IOFConnectionBackend connection,
			@Nonnull OFFeaturesReply featuresReply,
			@Nonnull IOFSwitchManager switchManager,
			@Nonnull RoleManager roleManager,
			@Nonnull Timer timer) {
		Preconditions.checkNotNull(connection, "connection");
		Preconditions.checkNotNull(featuresReply, "featuresReply");
		Preconditions.checkNotNull(switchManager, "switchManager");
		Preconditions.checkNotNull(roleManager, "roleManager");
		Preconditions.checkNotNull(timer, "timer");
		Preconditions.checkArgument(connection.getAuxId().equals(OFAuxId.MAIN),
				"connection must be MAIN connection but is %s", connection);

		this.switchManager = switchManager;
		this.roleManager = roleManager;
		this.mainConnection = connection;
		this.auxConnections = new ConcurrentHashMap<OFAuxId, IOFConnectionBackend>();
		this.featuresReply = featuresReply;
		this.timer = timer;
		this.switchManagerCounters = switchManager.getCounters();
		this.factory = OFFactories.getFactory(featuresReply.getVersion());
		this.roleChanger = new RoleChanger(DEFAULT_ROLE_TIMEOUT_NS);
		setState(new InitState());
		this.pendingPortStatusMsg = new ArrayList<OFPortStatus>();

		connection.setListener(this);
	}

	/**
	 * This begins the switch handshake. We start where the OFChannelHandler
	 * left off, right after receiving the OFFeaturesReply.
	 */
	public void beginHandshake() {
		Preconditions.checkState(state instanceof InitState, "must be in InitState");

		if (this.featuresReply.getNTables() > 1) {
			log.debug("Have {} table(s) for switch {}", this.featuresReply.getNTables(),
					getSwitchInfoString());
		}

		if (this.featuresReply.getVersion().compareTo(OFVersion.OF_13) < 0) {
			setState(new WaitConfigReplyState());
		} else {
			// OF 1.3. Ask for Port Descriptions
			setState(new WaitPortDescStatsReplyState());
		}
	}

	public DatapathId getDpid(){
		return this.featuresReply.getDatapathId();
	}

	public OFAuxId getOFAuxId(){
		return this.featuresReply.getAuxiliaryId();
	}

	/**
	 * Is this a state in which the handshake has completed?
	 * @return true if the handshake is complete
	 */
	public boolean isHandshakeComplete() {
		return this.state.isHandshakeComplete();
	}

	/**
	 * Forwards to RoleChanger. See there.
	 * @param role
	 */
	void sendRoleRequestIfNotPending(OFControllerRole role) {
		try {
			roleChanger.sendRoleRequestIfNotPending(role);
		} catch (IOException e) {
			log.error("Disconnecting switch {} due to IO Error: {}",
					getSwitchInfoString(), e.getMessage());
			mainConnection.disconnect();
		}
	}


	/**
	 * Forwards to RoleChanger. See there.
	 * @param role
	 */
	void sendRoleRequest(OFControllerRole role) {
		try {
			roleChanger.sendRoleRequest(role);
		} catch (IOException e) {
			log.error("Disconnecting switch {} due to IO Error: {}",
					getSwitchInfoString(), e.getMessage());
			mainConnection.disconnect();
		}
	}

	/**
	 * Dispatches the message to the controller packet pipeline
	 */
	private void dispatchMessage(OFMessage m) {
		this.switchManager.handleMessage(this.sw, m, null);
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
		if (mainConnection == null || mainConnection.getRemoteInetAddress() == null) {
			channelString = "?";
		} else {
			channelString = mainConnection.getRemoteInetAddress().toString();
		}
		String dpidString;
		if (featuresReply == null) {
			dpidString = "?";
		} else {
			dpidString = featuresReply.getDatapathId().toString();
		}
		return String.format("[%s DPID[%s]]", channelString, dpidString);
	}

	/**
	 * Update the channels state. Only called from the state machine.
	 * TODO: enforce restricted state transitions
	 * @param state
	 */
	private void setState(OFSwitchHandshakeState state) {
		this.state = state;
		state.logState();
		state.enterState();
	}

	public void processOFMessage(OFMessage m) {
		state.processOFMessage(m);
	}

	/**
	 * Send the configuration requests to tell the switch we want full
	 * packets
	 * @throws IOException
	 */
	private void sendHandshakeSetConfig() {
		// Ensure we receive the full packet via PacketIn
		// FIXME: We don't set the reassembly flags.
		OFSetConfig configSet = factory.buildSetConfig()
				.setXid(handshakeTransactionIds--)
				.setMissSendLen(0xffff)
				.build();

		// Barrier
		OFBarrierRequest barrier = factory.buildBarrierRequest()
				.setXid(handshakeTransactionIds--)
				.build();

		// Verify (need barrier?)
		OFGetConfigRequest configReq = factory.buildGetConfigRequest()
				.setXid(handshakeTransactionIds--)
				.build();
		List<OFMessage> msgList = ImmutableList.<OFMessage>of(configSet, barrier, configReq);
		mainConnection.write(msgList);
	}

	protected void sendPortDescRequest() {
		mainConnection.write(factory.portDescStatsRequest(ImmutableSet.<OFStatsRequestFlags>of()));
	}

	/**
	 * send a description state request
	 */
	private void sendHandshakeDescriptionStatsRequest() {
		// Send description stats request to set switch-specific flags
		OFDescStatsRequest descStatsRequest = factory.buildDescStatsRequest()
				.setXid(handshakeTransactionIds--)
				.build();
		mainConnection.write(descStatsRequest);
	}

	OFSwitchHandshakeState getStateForTesting() {
		return state;
	}

	void reassertRole(OFControllerRole role){
		this.roleManager.reassertRole(this, HARole.ofOFRole(role));
	}

	void useRoleChangerWithOtherTimeoutForTesting(long roleTimeoutMs) {
		roleChanger = new RoleChanger(TimeUnit.MILLISECONDS.toNanos(roleTimeoutMs));
	}

	/**
	 * Called by the switch manager when new aux connections have connected.
	 * This alerts the state machine of an aux connection.
	 *
	 * @param connection
	 *            the aux connection
	 */
	public synchronized void auxConnectionOpened(IOFConnectionBackend connection) {
		if(log.isDebugEnabled())
			log.debug("[{}] - Switch Handshake - new aux connection {}", this.getDpid(), connection.getAuxId());

		// Handle new Auxiliary connections if the main connection has completed (i.e. in ACTIVE or STANDBY state)
		if (this.getState().equals("ACTIVE") || this.getState().equals("STANDBY")) {
			auxConnections.put(connection.getAuxId(), connection);
			connection.setListener(OFSwitchHandshakeHandler.this);
			log.info("Auxiliary connection {} added for {}.", connection.getAuxId().getValue(), connection.getDatapathId().toString());
		} else {
			log.info("Auxiliary connection {} initiated for {} before main connection handshake complete. Ignorning aux connection attempt.", connection.getAuxId().getValue(), connection.getDatapathId().toString());
		}
	}

	/**
	 * Gets the main connection
	 *
	 * @return the main connection
	 */
	public IOFConnectionBackend getMainConnection() {
		return this.mainConnection;
	}

	/**
	 * Determines if this handshake handler is responsible for the supplied
	 * connection.
	 *
	 * @param connection
	 *            an OF connection
	 * @return true if the handler has the connection
	 */
	public boolean hasConnection(IOFConnectionBackend connection) {
		if (this.mainConnection.equals(connection)
				|| this.auxConnections.get(connection.getAuxId()) == connection) {
			return true;
		} else {
			return false;
		}
	}

	void cleanup() {
		for (IOFConnectionBackend conn : this.auxConnections.values()) {
			conn.disconnect();
		}

		this.mainConnection.disconnect();
	}

	public String getState() {
		return this.state.getClass().getSimpleName();
	}

	public String getQuarantineReason() {
		if(this.state instanceof QuarantineState) {
			QuarantineState qs = (QuarantineState) this.state;
			return qs.getQuarantineReason();
		}
		return null;
	}

	/**
	 * Gets the current connections that this switch handshake handler is
	 * responsible for. Used primarily by the REST API.
	 * @return an immutable list of IOFConnections
	 */
	public ImmutableList<IOFConnection> getConnections() {
		ImmutableList.Builder<IOFConnection> builder = ImmutableList.builder();

		builder.add(mainConnection);
		builder.addAll(auxConnections.values());

		return builder.build();
	}


	/** IOFConnectionListener */
	@Override
	public void connectionClosed(IOFConnectionBackend connection) {
		// Disconnect handler's remaining connections
		cleanup();

		// Only remove the switch handler when the main connection is
		// closed
		if (connection == this.mainConnection) {
			switchManager.handshakeDisconnected(connection.getDatapathId());
			if(sw != null) {
				log.debug("[{}] - main connection {} closed - disconnecting switch",
						connection);

				setSwitchStatus(SwitchStatus.DISCONNECTED);
				switchManager.switchDisconnected(sw);
			}
		}
	}

	@Override
	public void messageReceived(IOFConnectionBackend connection, OFMessage m) {
		processOFMessage(m);
	}

	@Override
	public boolean isSwitchHandshakeComplete(IOFConnectionBackend connection) {
		return state.isHandshakeComplete();
	}

	public void setSwitchStatus(SwitchStatus status) {
		if(sw != null) {
			SwitchStatus oldStatus = sw.getStatus();
			if(oldStatus != status) {
				log.debug("[{}] SwitchStatus change to {} requested, switch is in status " + oldStatus,
						mainConnection.getDatapathId(), status);
				sw.setStatus(status);
				switchManager.switchStatusChanged(sw, oldStatus, status);
			} else {
				log.warn("[{}] SwitchStatus change to {} requested, switch is already in status",
						mainConnection.getDatapathId(), status);
			}
		} else {
			log.warn("[{}] SwitchStatus change to {} requested, but switch is not allocated yet",
					mainConnection.getDatapathId(), status);
		}
	}

}
