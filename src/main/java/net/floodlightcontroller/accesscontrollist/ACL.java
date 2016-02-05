/**
 *    Copyright 2015, Big Switch Networks, Inc.
 *    Originally created by Pengfei Lu, Network and Cloud Computing Laboratory, Dalian University of Technology, China 
 *    Advisers: Keqiu Li and Heng Qi 
 *    This work is supported by the State Key Program of National Natural Science of China(Grant No. 61432002) 
 *    and Prospective Research Project on Future Networks in Jiangsu Future Networks Innovation Institute.
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

package net.floodlightcontroller.accesscontrollist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.floodlightcontroller.accesscontrollist.ACLRule.Action;
import net.floodlightcontroller.accesscontrollist.ap.AP;
import net.floodlightcontroller.accesscontrollist.ap.APManager;
import net.floodlightcontroller.accesscontrollist.util.IPAddressUtil;
import net.floodlightcontroller.accesscontrollist.web.ACLWebRoutable;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ACL implements IACLService, IFloodlightModule, IDeviceListener {

	protected IRestApiService restApi;
	protected IDeviceService deviceManager;
	protected IStorageSourceService storageSource;
	protected static Logger logger;

	private APManager apManager;
	private int lastRuleId = 1; // rule id counter
	private Map<Integer, ACLRule> aclRules;
	private Map<String, Integer> dpid2FlowPriority;
	private Map<Integer, Set<String>> ruleId2Dpid;
	private Map<Integer, Set<String>> ruleId2FlowName;
	private Map<Integer, List<Integer>> deny2Allow;

	private final int DEFAULT_PRIORITY = 30000;

	/**
	 * Checks if an existing ACL rule already works in a given switch.
	 */
	private boolean checkIfRuleWorksInSwitch(int ruleId, String dpid) {
		return ruleId2Dpid.containsKey(ruleId)
				&& ruleId2Dpid.get(ruleId).contains(dpid);
	}

	/**
	 * Adds a new mapping from ACL rule to ACL flow.
	 */
	private void addRuleToFlowMapping(int ruleId, String flowName) {
		if (!ruleId2FlowName.containsKey(ruleId)) {
			ruleId2FlowName.put(ruleId, new HashSet<String>());
		}
		ruleId2FlowName.get(ruleId).add(flowName);
	}

	/**
	 * Adds a new mapping from ACL rule to switch.
	 */
	private void addRuleToSwitchMapping(int ruleId, String dpid) {
		if (!ruleId2Dpid.containsKey(ruleId)) {
			ruleId2Dpid.put(ruleId, new HashSet<String>());
		}
		ruleId2Dpid.get(ruleId).add(dpid);
	}

	/**
	 * Gets the current priority for new ACL flow by device id.
	 */
	private int getPriorityBySwitch(String dpid) {
		if (!dpid2FlowPriority.containsKey(dpid)) {
			dpid2FlowPriority.put(dpid, DEFAULT_PRIORITY - 1);
			return DEFAULT_PRIORITY;
		} else {
			int priority = dpid2FlowPriority.get(dpid);
			dpid2FlowPriority.put(dpid, priority - 1);
			return priority;
		}
	}

	@Override
	public List<ACLRule> getRules() {
		return new ArrayList<ACLRule>(aclRules.values());
	}

	/**
	 * Checks if the new ACL rule matches an existing rule. If existing allowing
	 * rules matches the new denying rule, store the mappings.
	 * 
	 * @return true if the new ACL rule matches an existing rule, false
	 *         otherwise
	 */
	private boolean checkRuleMatch(ACLRule newRule) {
		List<Integer> allowRuleList = new ArrayList<>();
		for (ACLRule existingRule : getRules()) {
			if (newRule.match(existingRule)) {
				return true;
			}

			if (existingRule.getAction() == Action.ALLOW
					&& newRule.getAction() == Action.DENY) {
				if (existingRule.match(newRule)) {
					allowRuleList.add(existingRule.getId());
				}
			}
		}
		deny2Allow.put(newRule.getId(), allowRuleList);
		return false;
	}

	@Override
	public boolean addRule(ACLRule rule) {
		rule.setId(lastRuleId++);
		if (checkRuleMatch(rule)) {
			lastRuleId--;
			return false;
		}
		aclRules.put(rule.getId(), rule);
		logger.info("ACL rule(id:{}) is added.", rule.getId());
		if (rule.getAction() != Action.ALLOW) {
			enforceAddedRule(rule);
		}
		return true;
	}

	@Override
	public void removeRule(int ruleId) {
		aclRules.remove(ruleId);
		logger.info("ACL rule(id:{}) is removed.", ruleId);
		enforceRemovedRule(ruleId);
	}

	@Override
	public void removeAllRules() {
		this.lastRuleId = 1;
		this.aclRules = new TreeMap<>();
		this.dpid2FlowPriority = new HashMap<>();
		this.ruleId2Dpid = new HashMap<>();
		this.deny2Allow = new HashMap<>();

		for (Set<String> flowNameSet : ruleId2FlowName.values()) {
			for (String flowName : flowNameSet) {
				storageSource.deleteRowAsync("controller_staticflowtableentry",
						flowName);
				logger.debug("ACL flow(id:{}) is removed.", flowName);
			}
		}
		this.ruleId2FlowName = new HashMap<>();
	}

	/**
	 * Enforces denying ACL rule by ACL flow.
	 */
	private void enforceAddedRule(ACLRule denyRule) {
		Set<String> dpidSet;
		if (denyRule.getNw_src() != null) {
			dpidSet = apManager.getDpidSet(denyRule.getNw_src_prefix(),
					denyRule.getNw_src_maskbits());
		} else {
			dpidSet = apManager.getDpidSet(denyRule.getNw_dst_prefix(),
					denyRule.getNw_dst_maskbits());
		}

		for (String dpid : dpidSet) {
			String flowName;
			List<Integer> allowRuleList = deny2Allow.get(denyRule.getId());
			for (int allowRuleId : allowRuleList) {
				flowName = "ACLRule_" + allowRuleId + "_" + dpid;
				generateFlow(aclRules.get(allowRuleId), dpid, flowName);
			}
			flowName = "ACLRule_" + denyRule.getId() + "_" + dpid;
			generateFlow(denyRule, dpid, flowName);
		}
	}

	/**
	 * Enforces removing an existing ACL rule.
	 */
	private void enforceRemovedRule(int ruleId) {
		if (ruleId2FlowName.containsKey(ruleId)) {
			for (String flowName : ruleId2FlowName.get(ruleId)) {
				storageSource.deleteRowAsync("controller_staticflowtableentry",
						flowName);
				logger.debug("ACL flow(id:{}) is removed.", flowName);
			}
			ruleId2FlowName.remove(ruleId);
		}
		ruleId2Dpid.remove(ruleId);
		deny2Allow.remove(ruleId);
	}

	/**
	 * Generates ACL flow rule according to ACL rule 
	 * and installs it into switch.
	 */
	private void generateFlow(ACLRule rule, String dpid, String flowName) {
		if (rule == null || checkIfRuleWorksInSwitch(rule.getId(), dpid)) {
			return;
		}

		int priority = getPriorityBySwitch(dpid);
		if (rule.getNw_src() != null) {

			HashMap<String, Object> flow = new HashMap<String, Object>();

			flow.put(StaticFlowEntryPusher.COLUMN_SWITCH, dpid);
			flow.put(StaticFlowEntryPusher.COLUMN_NAME, flowName);
			flow.put(StaticFlowEntryPusher.COLUMN_ACTIVE,
					Boolean.toString(true));
			flow.put(StaticFlowEntryPusher.COLUMN_COOKIE, "0");
			flow.put(StaticFlowEntryPusher.COLUMN_PRIORITY,
					Integer.toString(priority));

			flow.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, "2048");
			flow.put(StaticFlowEntryPusher.COLUMN_NW_SRC, rule.getNw_src());

			if (rule.getNw_dst() != null) {
				flow.put(StaticFlowEntryPusher.COLUMN_NW_DST, rule.getNw_dst());
			}
			if (rule.getNw_proto() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_NW_PROTO,
						Integer.toString(rule.getNw_proto()));
			}
			if (rule.getAction() == Action.ALLOW) {
				flow.put(StaticFlowEntryPusher.COLUMN_ACTIONS,
						"output=controller");
			}
			if (rule.getTp_dst() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_TP_DST,
						Integer.toString(rule.getTp_dst()));
			}

			storageSource
					.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, flow);

		} else {

			HashMap<String, Object> flow = new HashMap<String, Object>();

			flow.put(StaticFlowEntryPusher.COLUMN_SWITCH, dpid);
			flow.put(StaticFlowEntryPusher.COLUMN_NAME, flowName);
			flow.put(StaticFlowEntryPusher.COLUMN_ACTIVE,
					Boolean.toString(true));
			flow.put(StaticFlowEntryPusher.COLUMN_COOKIE, "0");
			flow.put(StaticFlowEntryPusher.COLUMN_PRIORITY,
					Integer.toString(priority));

			flow.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, "2048");
			flow.put(StaticFlowEntryPusher.COLUMN_NW_DST, rule.getNw_dst());

			if (rule.getNw_proto() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_NW_PROTO,
						Integer.toString(rule.getNw_proto()));
			}
			if (rule.getAction() == Action.ALLOW) {
				flow.put(StaticFlowEntryPusher.COLUMN_ACTIONS,
						"output=controller");
			}
			if (rule.getTp_dst() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_TP_DST,
						Integer.toString(rule.getTp_dst()));
			}

			storageSource
					.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, flow);

		}
		addRuleToSwitchMapping(rule.getId(), dpid);
		addRuleToFlowMapping(rule.getId(), flowName);
		logger.debug("ACL flow(id:{}) is added in {}.", flowName, dpid);
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IACLService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IACLService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IRestApiService.class);
		l.add(IDeviceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApi = context.getServiceImpl(IRestApiService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		logger = LoggerFactory.getLogger(ACL.class);
		storageSource = context.getServiceImpl(IStorageSourceService.class);

		aclRules = new TreeMap<>();
		apManager = new APManager();
		ruleId2FlowName = new HashMap<>();
		ruleId2Dpid = new HashMap<>();
		dpid2FlowPriority = new HashMap<>();
		deny2Allow = new HashMap<>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// register REST interface
		restApi.addRestletRoutable(new ACLWebRoutable());
		deviceManager.addListener(this);
	}

	@Override
	public void deviceAdded(IDevice device) {
		SwitchPort[] switchPort = device.getAttachmentPoints(); 
		if (switchPort.length == 0) {
                        //Device manager does not yet know an attachment point for a device (Bug Fix) 
                        return;
                }
		IPv4Address[] ips = device.getIPv4Addresses();
		if (ips.length == 0) {
			// A new no-ip device added
			return;
		}

		String dpid = HexString.toHexString(switchPort[0].getSwitchDPID()
				.getLong());
		String ip = IPv4.fromIPv4Address(ips[0].getInt());
		logger.debug("AP(dpid:{},ip:{}) is added", dpid, ip);

		AP ap = new AP(ip, dpid);
		apManager.addAP(ap);
		processAPAdded(ap);
	}

	/**
	 * Generates new ACL flow when a new device appears
	 * and existing ACL rules denies its traffic.
	 */
	private void processAPAdded(AP ap) {
		String dpid = ap.getDpid();
		int ip = IPv4.toIPv4Address(ap.getIp());

		for (ACLRule rule : getRules()) {
			if (rule.getAction() != Action.ALLOW) {
				if (rule.getNw_src() != null) {
					if (IPAddressUtil.containIP(rule.getNw_src_prefix(),
							rule.getNw_src_maskbits(), ip)) {
						if (checkIfRuleWorksInSwitch(rule.getId(), dpid)) {
							continue;
						}
						String flowName = "ACLRule_" + rule.getId() + "_"
								+ dpid;
						generateFlow(rule, dpid, flowName);
					}
				} else {
					if (IPAddressUtil.containIP(rule.getNw_dst_prefix(),
							rule.getNw_dst_maskbits(), ip)) {
						if (checkIfRuleWorksInSwitch(rule.getId(), dpid)) {
							continue;
						}
						String flowName = "ACLRule_" + rule.getId() + "_"
								+ dpid;
						generateFlow(rule, dpid, flowName);
					}
				}
			}
		}
	}

	@Override
	public void deviceRemoved(IDevice device) {

	}

	@Override
	public void deviceMoved(IDevice device) {

	}

	@Override
	public void deviceIPV6AddrChanged(IDevice device) {
		logger.debug("IPv6 not implemented in ACL. Device changed: {}", device.toString());
	}
	
	@Override
	public void deviceIPV4AddrChanged(IDevice device) {

		SwitchPort[] switchPort = device.getAttachmentPoints();
		IPv4Address[] ips = device.getIPv4Addresses();

		String dpid = HexString.toHexString(switchPort[0].getSwitchDPID()
				.getLong());
		String ip = null;
		// some device may first appear with no IP address(default set to
		// 0.0.0.0), ignore it
		for (IPv4Address i : ips) {
			if (i.getInt() != 0) {
				ip = IPv4.fromIPv4Address(i.getInt());
				break;
			}
		}

		logger.debug("AP(dpid:{},ip:{}) is added", dpid, ip);
		AP ap = new AP(ip, dpid);
		apManager.addAP(ap);
		processAPAdded(ap);
	}

	@Override
	public void deviceVlanChanged(IDevice device) {

	}

	@Override
	public String getName() {
		return "ACL manager";
	}

	@Override
	public boolean isCallbackOrderingPrereq(String type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(String type, String name) {
		return false;
	}

}
