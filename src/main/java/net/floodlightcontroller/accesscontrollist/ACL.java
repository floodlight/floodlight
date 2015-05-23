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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	// service modules needed
	protected IRestApiService restApi;
	protected IDeviceService deviceManager;
	protected IStorageSourceService storageSource;
	protected static Logger logger;
	
	private APManager apManager;

	// variable used
	private int lastRuleId = 1; // rule id counter
	private List<ACLRule> ruleSet;
	private Map<String, Integer> dpid2FlowPriority;
	private Map<Integer, Set<String>> ruleId2Dpid;
	private Map<Integer, Set<String>> ruleId2FlowName;

	/**
	 * used by REST API to query ACL rules
	 */
	@Override
	public List<ACLRule> getRules() {
		return this.ruleSet;
	}

	/**
	 * check if the new rule matches an existing rule
	 */
	private boolean checkRuleMatch(ACLRule newRule) {
		Iterator<ACLRule> iter = ruleSet.iterator();
		while (iter.hasNext()) {
			ACLRule existingRule = iter.next();
			if(newRule.match(existingRule)){
				logger.error("existing rule: " + existingRule);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * used by REST API to add ACL rule
	 * @return if the new ACL rule is added successfully
	 */
	@Override
	public boolean addRule(ACLRule rule) {

		if(checkRuleMatch(rule)){
			return false;
		}
		
		rule.setId(lastRuleId++);
		this.ruleSet.add(rule);
		logger.info("No.{} ACL rule added.", rule.getId());
		enforceAddedRule(rule);
		return true;
	}

	/**
	 * used by REST API to remove ACL rule
	 */
	@Override
	public void removeRule(int ruleid) {

		Iterator<ACLRule> iter = this.ruleSet.iterator();
		while (iter.hasNext()) {
			ACLRule rule = iter.next();
			if (rule.getId() == ruleid) {				iter.remove();
				break;
			}
		}

		logger.info("No.{} ACL rule removed.", ruleid);
		enforceRemovedRule(ruleid);
	}

	/**
	 * used by REST API to clear ACL
	 */
	@Override
	public void removeAllRules() {
		
		this.lastRuleId = 1;
		this.ruleSet = new ArrayList<ACLRule>();
		this.dpid2FlowPriority = new HashMap<String, Integer>();
		this.ruleId2Dpid = new HashMap<Integer, Set<String>>();

		Iterator<Integer> ruleIdIter = ruleId2FlowName.keySet().iterator();
		while (ruleIdIter.hasNext()) {
			int ruleId = ruleIdIter.next();
			Set<String> flowNameSet = ruleId2FlowName.get(ruleId);
			logger.debug("No.{} ACL rule removed.", ruleId);
			for (String flowName : flowNameSet) {
				removeFlow(flowName);
				logger.debug("ACL flow {} removed.", flowName);
			}
		}
		this.ruleId2FlowName = new HashMap<Integer, Set<String>>();
	}

	/**
	 * enforce new added rule
	 */
	private void enforceAddedRule(ACLRule rule) {

		Set<String> dpidSet;
		if (rule.getNw_src() != null) {
			dpidSet = apManager.getDpidSet(rule.getNw_src_prefix(),rule.getNw_src_maskbits());
		} else {
			dpidSet = apManager.getDpidSet(rule.getNw_dst_prefix(),rule.getNw_dst_maskbits());
		}

		Iterator<String> dpidIter = dpidSet.iterator();
		Set<String> nameSet = new HashSet<String>();

		while (dpidIter.hasNext()) {
			String dpid = dpidIter.next();
			String flowName = "ACLRule_" + rule.getId() + "_" + dpid;
			generateFlow(rule, dpid, flowName);
			nameSet.add(flowName);
		}
		ruleId2FlowName.put(rule.getId(), nameSet);
		ruleId2Dpid.put(rule.getId(), dpidSet);
	}

	/**
	 * enforce removed rule
	 */
	private void enforceRemovedRule(int ruleId) {

		Set<String> flowEntryName = ruleId2FlowName.get(ruleId);
		Iterator<String> iter = flowEntryName.iterator();
		while (iter.hasNext()) {
			String name = iter.next();
			removeFlow(name);
			logger.debug("ACL flow " + name + " removed.");
		}

	}
		
	/**
	 * generate and push ACL flow entry
	 */
	private void generateFlow(ACLRule rule, String dpid, String flowName) {

		int priority;
		// get priority for the new flow entry
		if (dpid2FlowPriority.get(dpid) == null) {
			dpid2FlowPriority.put(dpid, 30000);
			priority = 30000;
		} else {
			priority = dpid2FlowPriority.get(dpid);
		}

		if (rule.getNw_src() != null) {
			
			HashMap<String,Object> flow = new HashMap<String,Object>();
	        
			flow.put(StaticFlowEntryPusher.COLUMN_SWITCH, dpid);
			flow.put(StaticFlowEntryPusher.COLUMN_NAME, flowName);
			flow.put(StaticFlowEntryPusher.COLUMN_ACTIVE, Boolean.toString(true));
			flow.put(StaticFlowEntryPusher.COLUMN_COOKIE, "0");
			flow.put(StaticFlowEntryPusher.COLUMN_PRIORITY, Integer.toString(priority));
			dpid2FlowPriority.put(dpid, --priority);
			
			
			flow.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, "2048");
			flow.put(StaticFlowEntryPusher.COLUMN_NW_SRC, rule.getNw_src());
			
			// process for the nw_dst attribute
			if (rule.getNw_dst() != null) {
				flow.put(StaticFlowEntryPusher.COLUMN_NW_DST, rule.getNw_dst());
			}
			if (rule.getNw_proto() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, Integer.toString(rule.getNw_proto()));
			}
			if (rule.getAction() == Action.ALLOW) {
				flow.put(StaticFlowEntryPusher.COLUMN_ACTIONS, "output=controller");
			}
			if (rule.getTp_dst() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_TP_DST, Integer.toString(rule.getTp_dst()));
			}
	        
	        storageSource.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, flow);
			
		} else {
			
			HashMap<String,Object> flow = new HashMap<String,Object>();
	        
			flow.put(StaticFlowEntryPusher.COLUMN_SWITCH, dpid);
			flow.put(StaticFlowEntryPusher.COLUMN_NAME, flowName);
			flow.put(StaticFlowEntryPusher.COLUMN_ACTIVE, Boolean.toString(true));
			flow.put(StaticFlowEntryPusher.COLUMN_COOKIE, "0");
			flow.put(StaticFlowEntryPusher.COLUMN_PRIORITY, Integer.toString(priority));
			dpid2FlowPriority.put(dpid, --priority);
			
			flow.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, "2048");
			flow.put(StaticFlowEntryPusher.COLUMN_NW_DST, rule.getNw_dst());

			if (rule.getNw_proto() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, Integer.toString(rule.getNw_proto()));
			}
			if (rule.getAction() == Action.ALLOW) {
				flow.put(StaticFlowEntryPusher.COLUMN_ACTIONS, "output=controller");
			}
			if (rule.getTp_dst() != 0) {
				flow.put(StaticFlowEntryPusher.COLUMN_TP_DST, Integer.toString(rule.getTp_dst()));
			}
	        
	        storageSource.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, flow);
	        
		}
		logger.info("ACL flow " + flowName + " added in " + dpid);
	}

	/**
	 * remove ACL flow entry
	 */
	private void removeFlow(String name) {
		storageSource.deleteRowAsync("controller_staticflowtableentry", name);
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

		ruleSet = new ArrayList<ACLRule>();
		apManager = new APManager();
		ruleId2FlowName = new HashMap<Integer, Set<String>>();
		ruleId2Dpid =  new HashMap<Integer, Set<String>>();
		dpid2FlowPriority = new HashMap<String, Integer>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// register REST interface
		restApi.addRestletRoutable(new ACLWebRoutable());
		deviceManager.addListener(this);
	}

	/**
	 * listen for new device
	 */
	@Override
	public void deviceAdded(IDevice device) {
		SwitchPort[] switchPort = device.getAttachmentPoints();
		IPv4Address[] ips = device.getIPv4Addresses();
		if(ips.length == 0){
			// A new no-ip device added
			return;
		}
		String dpid = HexString.toHexString(switchPort[0].getSwitchDPID().getLong());
		String ip = IPv4.fromIPv4Address(ips[0].getInt());
		logger.debug("New AP added. [dpid:" + dpid + " ip:" + ip + "]");

		AP ap = new AP(ip,dpid);
		apManager.addAP(ap);
		processAPAdded(ap);
	}

	/**
	 * push ACL flow given the new device
	 */
	private void processAPAdded(AP ap) {

		String dpid = ap.getDpid();
		int ip = IPv4.toIPv4Address(ap.getIp());

		Iterator<ACLRule> iter = this.ruleSet.iterator();
		while (iter.hasNext()) {
			ACLRule rule = iter.next();
			if (rule.getNw_src() != null) {
				if (IPAddressUtil.containIP(rule.getNw_src_prefix(),
						rule.getNw_src_maskbits(), ip)) {
					// check if there is a flow entry in the switch for the rule
					if (ruleId2Dpid.get(rule.getId()).contains(dpid)) {
						continue;
					}
					String flowName = "ACLRule_" + rule.getId() + "_" + dpid;
					ruleId2FlowName.get(rule.getId()).add(flowName);
					ruleId2Dpid.get(rule.getId()).add(dpid);
					generateFlow(rule, dpid, flowName);
				}
			} else {
				if (IPAddressUtil.containIP(rule.getNw_dst_prefix(),
						rule.getNw_dst_maskbits(), ip)) {
					// check if there is a flow entry in the switch for the rule
					if (ruleId2Dpid.get(rule.getId()).contains(dpid)) {
						continue;
					}
					String flowName = "ACLRule_" + rule.getId() + "_" + dpid;
					ruleId2FlowName.get(rule.getId()).add(flowName);
					ruleId2Dpid.get(rule.getId()).add(dpid);
					generateFlow(rule, dpid, flowName);
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
	public void deviceIPV4AddrChanged(IDevice device) {
		
		SwitchPort[] switchPort = device.getAttachmentPoints();
		IPv4Address[] ips = device.getIPv4Addresses();
		
		String dpid = HexString.toHexString(switchPort[0].getSwitchDPID().getLong());
		String ip = null;
		
		// some device may first appear with no IP address(default set to 0.0.0.0), ignore it
		for(IPv4Address i : ips){
			if(i.getInt() != 0){
				ip = IPv4.fromIPv4Address(i.getInt());
				break;
			}
		}
		
		logger.info("New AP added. [dpid:" + dpid + " ip:" + ip + "]");
		AP ap = new AP(ip, dpid);
		apManager.addAP(ap);
		processAPAdded(ap);
	}

	@Override
	public void deviceVlanChanged(IDevice device) {

	}

	@Override
	public String getName() {
		return null;
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
