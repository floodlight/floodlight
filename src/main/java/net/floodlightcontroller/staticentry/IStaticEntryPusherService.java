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

package net.floodlightcontroller.staticentry;

import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IStaticEntryPusherService extends IFloodlightService {
    /**
     * Adds a static flow.
     * @param name Name of the flow mod. Must be unique.
     * @param fm The flow to push.
     * @param swDpid The switch DPID to push it to, in 00:00:00:00:00:00:00:01 notation.
     */
    public void addFlow(String name, OFFlowMod fm, DatapathId swDpid);
    
    /**
     * Adds a static group.
     * @param name Name of the group mod. Must be unique.
     * @param gm The group to push.
     * @param swDpid The switch DPID to push it to, in 00:00:00:00:00:00:00:01 notation.
     */
    public void addGroup(String name, OFGroupMod gm, DatapathId swDpid);
    
    /**
     * Deletes a static flow or group entry
     * @param name The name of the static flow to delete.
     */
    public void deleteEntry(String name);
    
    /**
     * Deletes all static flows and groups for a particular switch
     * @param dpid The DPID of the switch to delete flows for.
     */
    public void deleteEntriesForSwitch(DatapathId dpid);
    
    /**
     * Deletes all flows and groups.
     */
    public void deleteAllEntries();
    
    /**
     * Gets all list of all flows and groups
     */
    public Map<String, Map<String, OFMessage>> getEntries();
    
    /**
     * Gets a list of flows and groups by switch
     */
    public Map<String, OFMessage> getEntries(DatapathId dpid);

}
