/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.rolechanger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;

import org.projectfloodlight.openflow.protocol.OFAsyncConfigPropPacketInSlave;
import org.projectfloodlight.openflow.protocol.OFAsyncGetReply;
import org.projectfloodlight.openflow.protocol.OFAsyncGetRequest;
import org.projectfloodlight.openflow.protocol.OFAsyncSet;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFExperimenter;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFRoleRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * 
 * 
 * @author
 */
public class RoleChanger implements IFloodlightModule, IOFMessageListener,
		IOFSwitchListener {

	private static OFControllerRole currentRole;

	// Our dependencies
	protected IFloodlightProviderService floodlightProviderService;
	protected IRestApiService restApiService;

	private IOFSwitchService switchService;

	protected OFAsyncGetReply lastAsyncGetReply;

	private static Logger log;

	@Override
	public String getName() {
		return "rolechanger";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:

			log.info("Received Packet_In from switch {} ({})", sw.getId()
					.toString(), sw.getControllerRole().toString());
			
			return Command.CONTINUE;
		case BARRIER_REPLY:
			log.info("---- Received BARRIER_REPLY ----");
			log.info("Barrier reply xid: {}", msg.getXid());
			return Command.CONTINUE;
		case ROLE_REPLY:
			log.info("---- Received ROLE_REPLY ----");
			// sendBarrier(sw);
			return Command.CONTINUE;
		case GET_ASYNC_REPLY:
			log.info("---- Received GET_ASYNC_REPLY ----");
			OFAsyncGetReply asyncReply = (OFAsyncGetReply) msg;
			log.info("{}", asyncReply.toString());
			return Command.CONTINUE;
		case GET_CONFIG_REPLY:
			log.info("---- Received GET_CONFIG_REPLY ----");
			OFGetConfigReply configReply = (OFGetConfigReply) msg;
			log.info("{}", configReply.toString());
			return Command.CONTINUE;

		default:
			break;
		}
		log.warn("Received unexpected message {}", msg);
		return Command.CONTINUE;
	}

	public static void sendBarrier(final IOFSwitch sw, final Semaphore sem) {

		log.info("Sending barrier to switch {}", sw.getId().toString());
		OFBarrierRequest barReq = sw.getOFFactory().buildBarrierRequest()
				.build();

		// send barrier and save ListenableFuture
		ListenableFuture<OFBarrierReply> future = sw.writeRequest(barReq);

		// add callback to execute when future computation is complete
		Futures.addCallback(future, new FutureCallback<OFBarrierReply>() {

			@Override
			public void onFailure(Throwable arg0) {
				log.error("Failed to receive BARRIER_REPLY from switch "
						+ sw.getId());
				sem.release();
			}

			@Override
			public void onSuccess(OFBarrierReply reply) {
				log.info("Received BARRIER_REPLY from switch " + sw.getId()
						+ ". xid: {}", reply.getXid());
				sem.release();
			}

		});

		// log.info("BARRIER_REQUEST sent. xid: {}", barReq.getXid());

	}

	public static void sendRoleRequest(final IOFSwitch sw,
			OFControllerRole role, final Semaphore sem) {

		OFRoleRequest roleReq = sw.getOFFactory().buildRoleRequest()
				.setRole(role).build();

		log.info("Sending role request to switch {}", sw.getId().toString());

		// send role request and save ListenableFuture
		ListenableFuture<OFRoleReply> future = sw.writeRequest(roleReq);

		// add callback to execute when future computation is complete
		Futures.addCallback(future, new FutureCallback<OFRoleReply>() {

			@Override
			public void onFailure(Throwable arg0) {
				log.error("Failed to receive ROLE_REPLY from switch {}", sw
						.getId().toString());
				// sem.release();
			}

			@Override
			public void onSuccess(OFRoleReply reply) {
				log.info("Received ROLE_REPLY. Current role on switch "
						+ sw.getId().toString() + ": {}", reply.getRole()
						.toString());
				// sem.release();
			}

		});

		log.info("RoleRequest sent to switch " + sw.getId().toString()
				+ ". xid: {}; role: {}", roleReq.getXid(), roleReq.getRole());
	}

	private void sendGetAsyncRequest(final IOFSwitch sw, final Semaphore sem) {

		OFAsyncGetRequest req = sw.getOFFactory().buildAsyncGetRequest()
				.build();

		log.info("Sending Async Get Request to switch {}", sw.getId()
				.toString());

		// send role request and save ListenableFuture
		ListenableFuture<OFAsyncGetReply> future = sw.writeRequest(req);

		// add callback to execute when future computation is complete
		Futures.addCallback(future, new FutureCallback<OFAsyncGetReply>() {

			@Override
			public void onFailure(Throwable arg0) {
				log.error(
						"Failed to receive AsyncGetReply ({}) from switch {}: {}",
						arg0.toString(), sw.getId());
				// TODO: default last assync get reply if null
				sem.release();
			}

			@Override
			public void onSuccess(OFAsyncGetReply reply) {
				log.info("Received AsyncGetReply from switch {}: {}",
						sw.getId(), reply.toString());
				lastAsyncGetReply = reply;
				sem.release();
			}

		});

		// log.info("AsyncGetRequest sent to switch {}",
		// sw.getId().toString());

	}

	/**
	 * Sets the async properties in the switch so that slave and master receive
	 * the same async messages.
	 * 
	 * @param sw
	 * @param r
	 */
	protected static void sendSetAsync(IOFSwitch sw, OFAsyncGetReply r) {

		OFAsyncSet setConfig = sw
				.getOFFactory()
				.buildAsyncSet()
				.setPacketInMaskEqualMaster(r.getPacketInMaskEqualMaster())
				.setPacketInMaskSlave(r.getPacketInMaskEqualMaster())
				.setPortStatusMaskEqualMaster(r.getPortStatusMaskEqualMaster())
				.setPortStatusMaskSlave(r.getPortStatusMaskEqualMaster())
				.setFlowRemovedMaskEqualMaster(
						r.getFlowRemovedMaskEqualMaster())
				.setFlowRemovedMaskSlave(r.getFlowRemovedMaskEqualMaster())
				.build();

		log.info("Sending set async {} to switch {}", setConfig.toString(), sw
				.getId().toString());

		sw.write(setConfig);
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProviderService = context
				.getServiceImpl(IFloodlightProviderService.class);

		switchService = context.getServiceImpl(IOFSwitchService.class);

		log = LoggerFactory.getLogger(RoleChanger.class);

		Map<String, String> configOptions = context.getConfigParams(this);

		// set current role for initial role in config file
		currentRole = OFControllerRole.valueOf("ROLE_"
				+ configOptions.get("initialRole"));

		log.info("Initial Role for switches: {}", currentRole);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.BARRIER_REPLY,
				this);
		floodlightProviderService.addOFMessageListener(OFType.ROLE_REPLY, this);
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProviderService.addOFMessageListener(OFType.GET_ASYNC_REPLY,
				this);
		floodlightProviderService.addOFMessageListener(OFType.GET_CONFIG_REPLY,
				this);
		switchService.addOFSwitchListener(this);
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchRemoved(DatapathId switchId) {

	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// send role request to equal when a switch is added to the network view
		// (as active)
		final IOFSwitch sw = switchService.getSwitch(switchId);

		log.info("New switch added: {} ", switchId.toString());

		log.info("Role on switch: {} ", sw.getControllerRole().toString());

		final Semaphore sem = new Semaphore(0);

		if (sw.getControllerRole() != currentRole) {

			sendRoleRequest(sw, currentRole, sem);
			try {

				sendBarrier(sw, sem); // sem = 1 after receive

				// sem unlocked means barrier reply was received
				sem.acquire(); // sem = 0

				sendGetAsyncRequest(sw, sem); // sem = 1 after receive

				// sem unlocked means async reply was received
				sem.acquire(); // sem = 0

				sendSetAsync(sw, lastAsyncGetReply);

				sendGetAsyncRequest(sw, sem); // sem = 1 after receive

			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub

	}
}