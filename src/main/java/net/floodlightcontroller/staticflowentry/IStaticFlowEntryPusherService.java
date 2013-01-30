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

package net.floodlightcontroller.staticflowentry;

import java.util.Map;

import org.openflow.protocol.OFFlowMod;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IStaticFlowEntryPusherService extends IFloodlightService {
    /**
     * Adds a static flow.
     * @param name Name of the flow mod. Must be unique.
     * @param fm The flow to push.
     * @param swDpid The switch DPID to push it to, in 00:00:00:00:00:00:00:01 notation.
     */
    public void addFlow(String name, OFFlowMod fm, String swDpid);
    
    /**
     * Deletes a static flow
     * @param name The name of the static flow to delete.
     */
    public void deleteFlow(String name);
    
    /**
     * Deletes all static flows for a practicular switch
     * @param dpid The DPID of the switch to delete flows for.
     */
    public void deleteFlowsForSwitch(long dpid);
    
    /**
     * Deletes all flows.
     */
    public void deleteAllFlows();
    
    /**
     * Gets all list of all flows
     */
    public Map<String, Map<String, OFFlowMod>> getFlows();
    
    /**
     * Gets a list of flows by switch
     */
    public Map<String, OFFlowMod> getFlows(String dpid);
}
