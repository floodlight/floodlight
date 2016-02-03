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

import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFMessage;
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
public class CopyOfFaultTolerance implements IFloodlightModule, IOFMessageListener,
		IOFSwitchListener {

	protected static Logger log = LoggerFactory.getLogger(CopyOfFaultTolerance.class);

	// Our dependencies
	protected IFloodlightProviderService floodlightProviderService;
	protected IRestApiService restApiService;

	private IOFSwitchService switchService;

	private static Logger logger;

	@Override
	public String getName() {
		return "loadbalancer";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		/*
		 * return type.equals(OFType.BARRIER_REPLY) ||
		 * type.equals(OFType.ROLE_REPLY);
		 */
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:

			logger.info("Received Packet_In from switch {} ({})", sw.getId()
					.toString(), sw.getControllerRole().toString());

			return Command.CONTINUE;
		case BARRIER_REPLY:
			logger.info("---- Received BARRIER_REPLY ----");
			logger.info("Barrier reply xid: {}", msg.getXid());
			return Command.CONTINUE;
		case ROLE_REPLY:
			logger.info("---- Received ROLE_REPLY ----");
			sendBarrier(sw);
			return Command.CONTINUE;
		default:
			break;
		}
		log.warn("Received unexpected message {}", msg);
		return Command.CONTINUE;
	}

	public static void sendBarrier(final IOFSwitch sw) {

		logger.info("Sending barrier...");
		OFBarrierRequest barReq = sw.getOFFactory().buildBarrierRequest()
				.build();

		// send barrier and save ListenableFuture
		ListenableFuture<OFBarrierReply> future = sw.writeRequest(barReq);

		// add callback to execute when future computation is complete
		Futures.addCallback(future, new FutureCallback<OFBarrierReply>() {

			@Override
			public void onFailure(Throwable arg0) {
				logger.error("Failed to receive BARRIER_REPLY");
			}

			@Override
			public void onSuccess(OFBarrierReply reply) {
				logger.info("Received BARRIER_REPLY. xid: {}", reply.getXid());
			}

		});

		logger.info("BARRIER_REQUEST sent. xid: {}", barReq.getXid());

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

		logger = LoggerFactory.getLogger(CopyOfFaultTolerance.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.BARRIER_REPLY,
				this);
		floodlightProviderService.addOFMessageListener(OFType.ROLE_REPLY, this);
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);

		switchService.addOFSwitchListener(this);
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// knownSwitches.remove(switchId.toString());
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// knownSwitches.add(switchId.toString());

		final IOFSwitch sw = switchService.getSwitch(switchId);

		logger.info("New switch added: {} ", switchId.toString());

		logger.info("Switch role before: {} ", sw.getControllerRole()
				.toString());

		if (sw.getControllerRole() != OFControllerRole.ROLE_EQUAL) {
			sendRoleRequest(sw, OFControllerRole.ROLE_EQUAL);
		}

	}

	public static void sendRoleRequest(final IOFSwitch sw, OFControllerRole role) {

		OFRoleRequest roleReq = sw.getOFFactory().buildRoleRequest()
				.setRole(role).build();
		
		logger.info("Sending role request...");

		// send role request and save ListenableFuture
		ListenableFuture<OFRoleReply> future = sw.writeRequest(roleReq);

		// add callback to execute when future computation is complete
		Futures.addCallback(future, new FutureCallback<OFRoleReply>() {

			@Override
			public void onFailure(Throwable arg0) {
				logger.error("Failed to receive ROLE_REPLY");

			}

			@Override
			public void onSuccess(OFRoleReply reply) {
				logger.info("Received ROLE_REPLY. Current role on switch: {}; "
						+ "Generation ID: {}", reply.getRole().toString(),
						reply.getGenerationId().toString());
				sendBarrier(sw);
			}

		});
		
		logger.info("RoleRequest sent. xid: {}; role: {}", roleReq.getXid(),
				roleReq.getRole());
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