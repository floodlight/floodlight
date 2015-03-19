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
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowPusher implements IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	protected IStorageSourceService storageSource;
	protected IStaticFlowEntryPusherService sfp;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStaticFlowEntryPusherService.class);
        l.add(IStorageSourceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		storageSource = context.getServiceImpl(IStorageSourceService.class);
		sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		logger = LoggerFactory.getLogger(FlowPusher.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {

		HashMap<String,Object> flow = new HashMap<String,Object>();
        
        flow.put(StaticFlowEntryPusher.COLUMN_TP_DST, "80");
        flow.put(StaticFlowEntryPusher.COLUMN_NW_DST, "10.0.0.2");
        flow.put(StaticFlowEntryPusher.COLUMN_NW_SRC, "10.0.0.1");
        flow.put(StaticFlowEntryPusher.COLUMN_PRIORITY, "30001");
        flow.put(StaticFlowEntryPusher.COLUMN_NAME, "flow1");
        flow.put(StaticFlowEntryPusher.COLUMN_ACTIVE, Boolean.toString(true));
        flow.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, "2048");
        flow.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, "6");
        flow.put(StaticFlowEntryPusher.COLUMN_SWITCH, "00:00:00:00:00:00:00:01");
        flow.put(StaticFlowEntryPusher.COLUMN_ACTIONS, "output=controller");
        
        storageSource.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, flow);
        
//        flow.put("tp_dst", "80");
//        flow.put("nw_dst", "10.0.0.3");
//        flow.put("nw_src", "10.0.0.1");
//        flow.put("priority", "30001");
//        flow.put("name", "flow2");
//        flow.put("active", Boolean.toString(true));
//        flow.put("dl_type", "2048");
//        flow.put("nw_proto", "6");
//        flow.put("switch_id", "00:00:00:00:00:00:00:01");
//        flow.put("actions", "output=controller");
//        
//        storageSource.insertRowAsync("controller_staticflowtableentry", flow);
        
//        storageSource.deleteRowAsync("controller_staticflowtableentry", "flow1");
	}

}
