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

package net.floodlightcontroller.core.web;

import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRole;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleReply;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.google.common.util.concurrent.ListenableFuture;

public class SwitchRoleResource extends ServerResource {

	protected static Logger log = LoggerFactory.getLogger(SwitchRoleResource.class);

	private static final String STR_ROLE_PREFIX = "ROLE_"; /* enum OFControllerRole is ROLE_XXX, so trim the ROLE_ when printing */
	private static final String STR_ROLE_MASTER = "MASTER"; /* these are all assumed uppercase within this class */
	private static final String STR_ROLE_SLAVE = "SLAVE";
	private static final String STR_ROLE_EQUAL = "EQUAL";
	private static final String STR_ROLE_OTHER = "OTHER";

	@Get("json")
	public Map<String, String> getRole() {
		IOFSwitchService switchService =
				(IOFSwitchService)getContext().getAttributes().
				get(IOFSwitchService.class.getCanonicalName());

		String switchId = (String) getRequestAttributes().get(CoreWebRoutable.STR_SWITCH_ID);
		HashMap<String, String> model = new HashMap<String, String>();

		if (switchId.equalsIgnoreCase(CoreWebRoutable.STR_ALL)) {
			for (IOFSwitch sw: switchService.getAllSwitchMap().values()) {
				switchId = sw.getId().toString();
				model.put(switchId, sw.getControllerRole().toString().replaceFirst(STR_ROLE_PREFIX, "")); 
			}
			return model;
		} else {
			try {
				DatapathId dpid = DatapathId.of(switchId);
				IOFSwitch sw = switchService.getSwitch(dpid);
				if (sw == null) {
					model.put("ERROR", "Switch Manager could not locate switch DPID " + dpid.toString());
					return model;
				} else {
					model.put(dpid.toString(), sw.getControllerRole().toString().replaceFirst(STR_ROLE_PREFIX, ""));
					return model;
				}
			} catch (Exception e) {
				model.put("ERROR", "Could not parse switch DPID " + switchId);
				return model;
			}	
		}
	}

	/* for some reason @Post("json") isn't working here... */
	@Post
	public Map<String, String> setRole(String json) {
		IOFSwitchService switchService =
				(IOFSwitchService)getContext().getAttributes().
				get(IOFSwitchService.class.getCanonicalName());
		Map<String, String> retValue = new HashMap<String, String>();

		String switchId = (String) getRequestAttributes().get(CoreWebRoutable.STR_SWITCH_ID);

		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp = null;
		String role = null;

		try {
			try {
				jp = f.createJsonParser(json);
			} catch (IOException e) {
				e.printStackTrace();
			}


			jp.nextToken();
			if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
				throw new IOException("Expected START_OBJECT");
			}

			while (jp.nextToken() != JsonToken.END_OBJECT) {
				if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
					throw new IOException("Expected FIELD_NAME");
				}

				String n = jp.getCurrentName().toLowerCase();
				jp.nextToken();

				switch (n) {
				case CoreWebRoutable.STR_ROLE:
					role = jp.getText();

					if (switchId.equalsIgnoreCase(CoreWebRoutable.STR_ALL)) {
						for (IOFSwitch sw: switchService.getAllSwitchMap().values()) {
							List<SetConcurrentRoleThread> activeThreads = new ArrayList<SetConcurrentRoleThread>(switchService.getAllSwitchMap().size());
							List<SetConcurrentRoleThread> pendingRemovalThreads = new ArrayList<SetConcurrentRoleThread>();
							SetConcurrentRoleThread t;
							t = new SetConcurrentRoleThread(sw, parseRole(role));
							activeThreads.add(t);
							t.start();

							// Join all the threads after the timeout. Set a hard timeout
							// of 12 seconds for the threads to finish. If the thread has not
							// finished the switch has not replied yet and therefore we won't
							// add the switch's stats to the reply.
							for (int iSleepCycles = 0; iSleepCycles < 12; iSleepCycles++) {
								for (SetConcurrentRoleThread curThread : activeThreads) {
									if (curThread.getState() == State.TERMINATED) {
										retValue.put(curThread.getSwitch().getId().toString(), 
												(curThread.getRoleReply() == null ? 
														"Error communicating with switch. Role not changed." 
														: curThread.getRoleReply().getRole().toString().replaceFirst(STR_ROLE_PREFIX, "")
														)
												);
										pendingRemovalThreads.add(curThread);
									}
								}

								// remove the threads that have completed the queries to the switches
								for (SetConcurrentRoleThread curThread : pendingRemovalThreads) {
									activeThreads.remove(curThread);
								}
								// clear the list so we don't try to double remove them
								pendingRemovalThreads.clear();

								// if we are done finish early so we don't always get the worst case
								if (activeThreads.isEmpty()) {
									break;
								}

								// sleep for 1 s here
								try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									log.error("Interrupted while waiting for role replies", e);
									retValue.put("ERROR", "Thread sleep interrupted while waiting for role replies.");
								}

							}
						}
					} else {
						/* Must be a specific switch DPID then. */
						try {
							DatapathId dpid = DatapathId.of(switchId);
							IOFSwitch sw = switchService.getSwitch(dpid);
							if (sw == null) {
								retValue.put("ERROR", "Switch Manager could not locate switch DPID " + dpid.toString());
							} else {
								OFRoleReply reply = setSwitchRole(sw, parseRole(role));
								retValue.put(sw.getId().toString(), 
										(reply == null ? 
												"Error communicating with switch. Role not changed." 
												: reply.getRole().toString().replaceFirst(STR_ROLE_PREFIX, "")
												)
										);
							}
						} catch (Exception e) {
							retValue.put("ERROR", "Could not parse switch DPID " + switchId);
						}	
					}
					break;
				default:
					retValue.put("ERROR", "Unrecognized JSON key.");
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			retValue.put("ERROR", "Caught IOException while parsing JSON POST request in role request.");
		}
		return retValue;
	}

	protected class SetConcurrentRoleThread extends Thread {
		private OFRoleReply switchReply;
		private OFControllerRole role;
		private IOFSwitch sw;

		public SetConcurrentRoleThread(IOFSwitch sw, OFControllerRole role) {
			this.sw = sw;
			this.switchReply = null;
			this.role = role;
		}

		public OFRoleReply getRoleReply() {
			return switchReply;
		}

		public IOFSwitch getSwitch() {
			return sw;
		}

		public OFControllerRole getRole() {
			return role;
		}

		@Override
		public void run() {
			switchReply = setSwitchRole(sw, role);
		}
	}

	private static OFRoleReply setSwitchRole(IOFSwitch sw, OFControllerRole role) {
		try {	
			if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_12) < 0) {
				OFNiciraControllerRole nrole;
				switch (role) {
				case ROLE_EQUAL:
					nrole = OFNiciraControllerRole.ROLE_OTHER;
					log.warn("Assuming EQUAL as OTHER for Nicira role request.");
					break;
				case ROLE_MASTER:
					nrole = OFNiciraControllerRole.ROLE_MASTER;
					break;
				case ROLE_SLAVE:
					nrole = OFNiciraControllerRole.ROLE_SLAVE;
					break;
				case ROLE_NOCHANGE:
					log.error("Nicira extension does not support NOCHANGE role. Thus, we won't change the role.");
					return OFFactories.getFactory(OFVersion.OF_13).buildRoleReply().setRole(sw.getControllerRole()).setGenerationId(U64.ZERO).build();
				default:
					log.error("Impossible to have anything other than MASTER, OTHER, or SLAVE for Nicira role.");
					return OFFactories.getFactory(OFVersion.OF_13).buildRoleReply().setRole(sw.getControllerRole()).setGenerationId(U64.ZERO).build();
				}

				ListenableFuture<OFNiciraControllerRoleReply> future = sw.writeRequest(sw.getOFFactory().buildNiciraControllerRoleRequest()
						.setRole(nrole)
						.build());
				OFNiciraControllerRoleReply nreply = future.get(10, TimeUnit.SECONDS);
				if (nreply != null) {
					/* Turn the OFControllerRoleReply into a OFNiciraControllerRoleReply */
					switch (nreply.getRole()) {
					case ROLE_MASTER:
						return OFFactories.getFactory(OFVersion.OF_13).buildRoleReply().setRole(OFControllerRole.ROLE_MASTER).setGenerationId(U64.ZERO).build();
					case ROLE_OTHER:
						return OFFactories.getFactory(OFVersion.OF_13).buildRoleReply().setRole(OFControllerRole.ROLE_EQUAL).setGenerationId(U64.ZERO).build();
					case ROLE_SLAVE:
						return OFFactories.getFactory(OFVersion.OF_13).buildRoleReply().setRole(OFControllerRole.ROLE_SLAVE).setGenerationId(U64.ZERO).build();
					default:
						log.error("Impossible to have anything other than MASTER, OTHER, or SLAVE for Nicira role: {}.", nreply.getRole().toString());
						break;
					}
				} else {
					log.error("Did not receive Nicira role reply for switch {}.", sw.getId().toString());
				}
			} else {
				ListenableFuture<OFRoleReply> future = sw.writeRequest(sw.getOFFactory().buildRoleRequest()
						.setGenerationId(U64.ZERO)
						.setRole(role)
						.build());
				return future.get(10, TimeUnit.SECONDS);
			}
		} catch (Exception e) {
			log.error("Failure setting switch {} role to {}.", sw.toString(), role.toString());
			log.error(e.getMessage());
		}
		return null;
	}

	private static OFControllerRole parseRole(String role) {
		if (role == null || role.isEmpty()) {
			return OFControllerRole.ROLE_NOCHANGE;
		} 

		role = role.toUpperCase();		

		if (role.contains(STR_ROLE_MASTER)) {
			return OFControllerRole.ROLE_MASTER;
		} else if (role.contains(STR_ROLE_SLAVE)) {
			return OFControllerRole.ROLE_SLAVE;
		} else if (role.contains(STR_ROLE_EQUAL) || role.contains(STR_ROLE_OTHER)) {
			return OFControllerRole.ROLE_EQUAL;
		} else {
			return OFControllerRole.ROLE_NOCHANGE;
		}
	}
}
